package io.github.sharket1111.trade;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.logging.Level;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Posts server news to a Discord channel through a webhook (discord.yml):
 * notices, give-alls, big trades, auctions, and the server starting and
 * stopping. Messages go out on a background thread in order; a failure is
 * logged once in a while and never touches the game.
 */
final class Discord {
	/** Embed colors: pastel blue for news, green for money, purple for auctions, grey for the server. */
	static final int BLUE = 0x8FC3F7;
	static final int GREEN = 0x5CC48A;
	static final int PURPLE = 0xA99BF5;
	static final int GREY = 0x8D9AAC;

	private static final Gson GSON = new Gson();
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
	private static final java.util.concurrent.ExecutorService QUEUE = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
		Thread thread = new Thread(r, "PlayerTrade-Discord");
		thread.setDaemon(true);
		return thread;
	});

	private static Plugin plugin;
	private static String webhook = "";
	private static String botName = "서버 장터";
	private static boolean notices = true;
	private static boolean giveAlls = true;
	private static boolean auctions = true;
	private static boolean serverState = true;
	private static long bigTrade = 10_000;
	private static long lastWarning;

	private Discord() {
	}

	static void load(Plugin owner) {
		plugin = owner;
		File file = new File(owner.getDataFolder(), "discord.yml");
		YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
		if (!file.exists()) {
			yaml.options().setHeader(List.of(
					"PlayerTrade: server news in a Discord channel.",
					"webhook: the channel's webhook URL (Discord: channel settings > Integrations > Webhooks > New Webhook > Copy URL).",
					"  Keep it secret: anyone with it can post in the channel. Empty = off.",
					"name: the name the messages are posted under.",
					"notices / give-all / auctions / server: which news to post.",
					"big-trade: post player trades (market, buy board, auctions) of at least this much; 0 = none.",
					"Test it in game with /trade 디스코드 (admins)."));
			yaml.set("webhook", "");
			yaml.set("name", "서버 장터");
			yaml.set("notices", true);
			yaml.set("give-all", true);
			yaml.set("auctions", true);
			yaml.set("server", true);
			yaml.set("big-trade", 10000);
			try {
				owner.getDataFolder().mkdirs();
				yaml.save(file);
			} catch (IOException e) {
				owner.getLogger().log(Level.WARNING, "Could not write discord.yml", e);
			}
		}
		webhook = yaml.getString("webhook", "").trim();
		botName = yaml.getString("name", "서버 장터");
		notices = yaml.getBoolean("notices", true);
		giveAlls = yaml.getBoolean("give-all", true);
		auctions = yaml.getBoolean("auctions", true);
		serverState = yaml.getBoolean("server", true);
		bigTrade = yaml.getLong("big-trade", 10_000);
		if (!webhook.isEmpty() && !webhook.startsWith("https://")) {
			owner.getLogger().warning("discord.yml: 'webhook' must be the https:// URL Discord gives; Discord posts stay off.");
			webhook = "";
		}
	}

	static boolean enabled() {
		return !webhook.isEmpty();
	}

	// --- news ----------------------------------------------------------------------------

	static void notice(String title, String body, String author, boolean giveAll) {
		if (giveAll ? giveAlls : notices) {
			post(embed((giveAll ? "💰 " : "📢 ") + title, body, giveAll ? GREEN : BLUE, author));
		}
	}

	/** A player trade: posted when it is at least {@code big-trade}. */
	static void trade(String what, long price, String seller, String buyer, String how) {
		if (bigTrade > 0 && price >= bigTrade) {
			post(embed("🔥 큰 거래", "**" + what + "**\n" + seller + " → " + buyer + " · **" + Money.format(price) + "**", GREEN, how));
		}
	}

	static void auctionStarted(String what, String seller, long start, long endsAt) {
		if (auctions) {
			post(embed("🔨 새 경매", "**" + what + "**\n시작가 **" + Money.format(start) + "** · 판매자 " + seller
					+ "\n마감 <t:" + endsAt / 1000 + ":R>", PURPLE, null));
		}
	}

	static void auctionEnded(String what, String seller, String winner, long price) {
		if (auctions) {
			post(embed("🔨 경매 " + (winner != null ? "낙찰" : "유찰"), "**" + what + "**\n"
					+ (winner != null ? winner + "님이 **" + Money.format(price) + "**에 낙찰" : "입찰자가 없어 " + seller + "님에게 돌아갔습니다."),
					PURPLE, null));
		}
	}

	static void serverStarted() {
		if (serverState) {
			String site = WebSite.address();
			post(embed("🟢 서버가 켜졌어요", site != null ? "웹 장터: " + site : "", GREY, null));
		}
	}

	/** Sent before the plugin shuts down, waiting up to three seconds so it is not lost. */
	static void serverStopping() {
		if (serverState && enabled()) {
			try {
				QUEUE.submit(() -> send(embed("🔴 서버가 꺼졌어요", "", GREY, null))).get(3, java.util.concurrent.TimeUnit.SECONDS);
			} catch (Exception e) {
				// Shutting down anyway.
			}
		}
	}

	/** /trade 디스코드: a test message; answers what happened. */
	static String test(String admin) {
		if (!enabled()) {
			return "디스코드 연동이 꺼져 있습니다. plugins/PlayerTrade/discord.yml 의 webhook 에 웹후크 주소를 넣고 서버를 재시작하세요.";
		}
		post(embed("✅ 연결 테스트", admin + "님이 보낸 테스트 메시지입니다.", BLUE, null));
		return "디스코드로 테스트 메시지를 보냈습니다. 채널에 안 보이면 웹후크 주소를 확인하세요.";
	}

	// --- sending -------------------------------------------------------------------------

	private static JsonObject embed(String title, String description, int color, String footer) {
		JsonObject embed = new JsonObject();
		embed.addProperty("title", cut(title, 250));
		if (description != null && !description.isEmpty()) {
			embed.addProperty("description", cut(description, 3500));
		}
		embed.addProperty("color", color);
		embed.addProperty("timestamp", Instant.now().toString());
		if (footer != null && !footer.isEmpty()) {
			JsonObject foot = new JsonObject();
			foot.addProperty("text", cut(footer, 200));
			embed.add("footer", foot);
		}
		return embed;
	}

	private static String cut(String text, int max) {
		return text.length() > max ? text.substring(0, max) : text;
	}

	private static void post(JsonObject embed) {
		if (enabled()) {
			QUEUE.execute(() -> send(embed));
		}
	}

	private static void send(JsonObject embed) {
		JsonObject body = new JsonObject();
		body.addProperty("username", botName);
		JsonArray embeds = new JsonArray();
		embeds.add(embed);
		body.add("embeds", embeds);
		// No pings from player-written text.
		JsonObject mentions = new JsonObject();
		mentions.add("parse", new JsonArray());
		body.add("allowed_mentions", mentions);
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(webhook)).timeout(Duration.ofSeconds(10))
					.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body))).build();
			HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() == 429) {
				// Rate limited: wait as asked, once.
				Thread.sleep(Math.min(5000, retryAfter(response)));
				HTTP.send(request, HttpResponse.BodyHandlers.discarding());
			} else if (response.statusCode() >= 300) {
				warn("Discord answered " + response.statusCode() + " (check the webhook URL in discord.yml)");
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (IOException | RuntimeException e) {
			warn("Could not post to Discord: " + e.getMessage());
		}
	}

	private static long retryAfter(HttpResponse<String> response) {
		try {
			return (long) (Double.parseDouble(response.headers().firstValue("Retry-After").orElse("1")) * 1000);
		} catch (NumberFormatException e) {
			return 1000;
		}
	}

	/** At most one warning in ten minutes, so a broken webhook does not flood the log. */
	private static void warn(String message) {
		long now = System.currentTimeMillis();
		if (now - lastWarning > 600_000) {
			lastWarning = now;
			plugin.getLogger().warning(message);
		}
	}
}
