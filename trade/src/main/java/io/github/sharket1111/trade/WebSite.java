package io.github.sharket1111.trade;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * The server's own web page, served by a small built-in web server on a port
 * of its own (web.yml): the page itself (web/index.html in the jar) and a
 * JSON API it reads.
 *
 * Anyone with the address sees the public part: players online, the market,
 * the buy board and the price table. A player's own part (money, mailbox,
 * purchases and sales, listings, buy requests, land, groups) needs a login
 * ({@link WebAccounts}): the Minecraft name and a password set in game with
 * {@code /trade 웹 비밀번호}, or the quick personal link {@code /trade 웹}
 * gives. {@code /trade 웹 끊기} logs out everywhere. The page only reads the
 * game; besides logging in it can only change the player's own password.
 *
 * Bukkit is not thread-safe, so the web threads never touch the game: the
 * public snapshot is rebuilt on the main thread every few seconds, and a
 * player's own data is gathered on the main thread when it is asked for.
 */
final class WebSite {
	private static final int REFRESH_TICKS = 100;
	private static final int HISTORY_LIMIT = 60;
	private static final Gson GSON = new Gson();
	private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

	private static Plugin plugin;
	private static HttpServer server;
	private static boolean enabled;
	private static int port;
	private static String address = "";
	private static int linkHours = 24;
	private static int sessionDays = 14;
	private static final int MAX_BODY = 4096;
	private static volatile String publicJson = "{}";
	private static volatile String valuesJson = "[]";
	private static byte[] indexPage = new byte[0];
	private static byte[] namesJson = new byte[0];

	private WebSite() {
	}

	// --- setup -----------------------------------------------------------------------

	static void start(Plugin owner) {
		plugin = owner;
		File file = new File(owner.getDataFolder(), "web.yml");
		YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
		if (!file.exists()) {
			yaml.options().setHeader(List.of(
					"PlayerTrade web page: players online, the market, buy requests, prices,",
					"and each player's own money, mailbox, history and land through /trade 웹.",
					"enabled: turn it on once 'port' is a port your host gives this server",
					"  (Shockbyte: panel > Ports > Add Additional Port).",
					"address: the address players type to reach the server (IP or domain), used in links.",
					"link-hours: how long a quick link from /trade 웹 works.",
					"session-days: how long a login with name and password lasts on a device.",
					"trade-enabled: whether logged-in players may buy and send money from the page.",
					"max-per-action / max-per-day: the most a player may spend from the page at once / in a day."));
			yaml.set("enabled", false);
			yaml.set("port", 0);
			yaml.set("address", "");
			yaml.set("link-hours", 24);
			yaml.set("session-days", 14);
			yaml.set("trade-enabled", true);
			yaml.set("max-per-action", 100000);
			yaml.set("max-per-day", 300000);
			try {
				owner.getDataFolder().mkdirs();
				yaml.save(file);
			} catch (IOException e) {
				owner.getLogger().log(Level.WARNING, "Could not write web.yml", e);
			}
		}
		enabled = yaml.getBoolean("enabled", false);
		port = yaml.getInt("port", 0);
		address = yaml.getString("address", "").trim();
		linkHours = Math.max(1, yaml.getInt("link-hours", 24));
		sessionDays = Math.max(1, yaml.getInt("session-days", 14));
		WebActions.configure(yaml.getBoolean("trade-enabled", true), yaml.getLong("max-per-action", 100000), yaml.getLong("max-per-day", 300000));
		WebAccounts.load(owner);
		WebNotices.load(owner);
		if (!enabled) {
			return;
		}
		if (port <= 0 || port > 65535) {
			owner.getLogger().warning("web.yml: set 'port' to the extra port your host gave you; the web page stays off.");
			enabled = false;
			return;
		}
		indexPage = resource("/web/index.html");
		namesJson = resource("/web/ko.json");
		valuesJson = values();
		WebData.start(owner);
		refresh();
		Bukkit.getScheduler().runTaskTimer(owner, WebSite::refresh, REFRESH_TICKS, REFRESH_TICKS);
		try {
			server = HttpServer.create(new InetSocketAddress(port), 0);
			server.createContext("/", WebSite::handle);
			server.setExecutor(Executors.newFixedThreadPool(4));
			server.start();
			owner.getLogger().info("Web page at " + url());
		} catch (IOException e) {
			owner.getLogger().warning("Could not start the web page on port " + port + ": " + e.getMessage());
			enabled = false;
		}
	}

	static void stop() {
		if (server != null) {
			server.stop(0);
			server = null;
		}
	}

	private static byte[] resource(String path) {
		try (InputStream in = WebSite.class.getResourceAsStream(path)) {
			return in == null ? new byte[0] : in.readAllBytes();
		} catch (IOException e) {
			return new byte[0];
		}
	}

	private static String url() {
		String host = !address.isEmpty() ? address : !Bukkit.getIp().isEmpty() ? Bukkit.getIp() : "서버주소";
		return "http://" + host + ":" + port + "/";
	}

	// --- personal links ----------------------------------------------------------------

	/** /trade 웹 [비밀번호|끊기]: a quick login link, setting the web password, or logging out everywhere. */
	static void command(Player player, String sub) {
		switch (sub) {
			case "끊기", "logout", "revoke" -> {
				WebAccounts.endAll(player.getUniqueId());
				player.sendMessage(Component.text("모든 기기에서 웹 로그아웃했습니다.", NamedTextColor.YELLOW));
			}
			case "비밀번호", "password" -> askPassword(player, null);
			case "비밀번호삭제" -> {
				WebAccounts.removePassword(player.getUniqueId());
				player.sendMessage(Component.text("웹 비밀번호를 지웠습니다. 이제 이름과 비밀번호로는 로그인할 수 없습니다.", NamedTextColor.YELLOW));
			}
			default -> link(player);
		}
	}

	/** The page's address and the commands, for the trade menu's web button. */
	static String address() {
		return enabled ? url() : null;
	}

	/** A quick login link, valid for {@code link-hours}. */
	static void link(Player player) {
		if (!enabled) {
			player.sendMessage(Component.text("웹 페이지가 꺼져 있습니다. 관리자가 plugins/PlayerTrade/web.yml에서 켜야 합니다.", NamedTextColor.RED));
			return;
		}
		String key = WebAccounts.newSession(player.getUniqueId(), TimeUnit.HOURS.toMillis(linkHours));
		String link = url() + "#k=" + key;
		player.sendMessage(Component.text("웹 페이지 바로 로그인 (" + linkHours + "시간, 나만 쓰세요): ", NamedTextColor.GOLD)
				.append(Component.text("[열기]", NamedTextColor.AQUA).clickEvent(ClickEvent.openUrl(link))
						.hoverEvent(HoverEvent.showText(Component.text(link)))));
		player.sendMessage(Component.text(WebAccounts.hasPassword(player.getUniqueId())
				? "주소 " + url() + " 에서 닉네임과 웹 비밀번호로도 로그인할 수 있습니다."
				: "웹 비밀번호를 정하면 링크 없이 닉네임과 비밀번호로 로그인할 수 있습니다: /trade 웹 비밀번호", NamedTextColor.GRAY));
	}

	/**
	 * Asks for a new web password twice (chat, or an anvil in the touch mode;
	 * never as a command, which the server log would keep), then sets it.
	 */
	static void askPassword(Player player, Runnable back) {
		Runnable done = back == null ? () -> { } : back;
		player.sendMessage(Component.text("웹 비밀번호는 이 서버 웹 페이지에만 쓰세요. 다른 사이트나 마인크래프트 계정 비밀번호와 다르게 정하세요."
				+ " (웹 페이지가 http라 암호화되지 않습니다)", NamedTextColor.YELLOW));
		ChatPrompt.ask(player, "새 웹 비밀번호를 입력하세요. (" + WebAccounts.MIN_PASSWORD + "~" + WebAccounts.MAX_PASSWORD + "글자)", first -> {
			String problem = WebAccounts.badPassword(player.getName(), first);
			if (problem != null) {
				player.sendMessage(Component.text(problem, NamedTextColor.RED));
				done.run();
				return;
			}
			ChatPrompt.ask(player, "확인을 위해 한 번 더 입력하세요.", second -> {
				if (!second.equals(first)) {
					player.sendMessage(Component.text("두 번 입력한 비밀번호가 다릅니다. 다시 해 주세요.", NamedTextColor.RED));
				} else {
					WebAccounts.setPassword(player.getUniqueId(), player.getName(), first);
					player.sendMessage(Component.text("웹 비밀번호를 정했습니다. 웹 페이지에서 닉네임 '" + player.getName()
							+ "'과(와) 이 비밀번호로 로그인하세요." + (enabled ? " " + url() : ""), NamedTextColor.GREEN));
				}
				done.run();
			}, done);
		}, done);
	}

	// --- serving ---------------------------------------------------------------------

	private static void handle(HttpExchange exchange) throws IOException {
		try (exchange) {
			String path = exchange.getRequestURI().getPath();
			if ("POST".equals(exchange.getRequestMethod())) {
				switch (path) {
					case "/api/login" -> login(exchange);
					case "/api/buy" -> action(exchange, (player, body) -> WebActions.buy(player, text(body, "id")));
					case "/api/pay" -> action(exchange, (player, body) -> WebActions.pay(player, text(body, "to"), number(body, "amount")));
					case "/api/order/cancel" -> action(exchange, (player, body) -> WebActions.cancelOrder(player, text(body, "id")));
					case "/api/admin/balance" -> admin(exchange, (player, body) -> WebActions.balance(player, text(body, "name"), text(body, "mode"),
							number(body, "amount")));
					case "/api/admin/giveall" -> admin(exchange, (player, body) -> WebActions.giveAll(player, number(body, "amount"),
							body != null && body.has("everyone") && body.get("everyone").getAsBoolean(), text(body, "message")));
					case "/api/admin/notice" -> admin(exchange, (player, body) -> {
						if (text(body, "body").isBlank() && text(body, "title").isBlank()) {
							return WebActions.error("내용을 입력하세요.");
						}
						WebNotices.post(text(body, "title"), text(body, "body"), WebActions.name(player) + "(웹)", false);
						return WebActions.ok("공지를 올렸습니다.");
					});
					case "/api/admin/notice/delete" -> admin(exchange, (player, body) -> WebNotices.delete(text(body, "id"))
							? WebActions.ok("공지를 지웠습니다.") : WebActions.error("이미 없는 공지입니다."));
					case "/api/admin/shop" -> admin(exchange, (player, body) -> WebActions.shopPrice(player, text(body, "id"), number(body, "price")));
					case "/api/logout" -> logout(exchange);
					case "/api/password" -> password(exchange);
					default -> send(exchange, 404, "text/plain", "Not found".getBytes(StandardCharsets.UTF_8));
				}
				return;
			}
			if (!"GET".equals(exchange.getRequestMethod())) {
				send(exchange, 405, "text/plain", "GET or POST only".getBytes(StandardCharsets.UTF_8));
				return;
			}
			switch (path) {
				case "/", "/index.html" -> send(exchange, 200, "text/html", indexPage);
				case "/ko.json" -> send(exchange, 200, "application/json", namesJson);
				case "/api/public" -> json(exchange, 200, publicJson);
				case "/api/values" -> json(exchange, 200, valuesJson);
				case "/api/me" -> me(exchange);
				case "/api/stats" -> json(exchange, 200, WebData.stats());
				case "/api/map" -> json(exchange, 200, WebData.map());
				case "/api/notices" -> json(exchange, 200, GSON.toJson(notices()));
				case "/api/admin" -> admin(exchange, (player, body) -> WebActions.adminData());
				default -> send(exchange, 404, "text/plain", "Not found".getBytes(StandardCharsets.UTF_8));
			}
		} catch (RuntimeException e) {
			plugin.getLogger().log(Level.WARNING, "Web request failed", e);
		}
	}

	/** Something a logged-in player does: run on the main thread with their id and the request body. */
	private interface Action {
		String run(UUID player, JsonObject body);
	}

	private static void action(HttpExchange exchange, Action action) throws IOException {
		UUID player = WebAccounts.session(exchange.getRequestHeaders().getFirst("X-Key"));
		if (player == null) {
			json(exchange, 401, error("로그인이 끝났습니다. 다시 로그인하세요."));
			return;
		}
		JsonObject body = "POST".equals(exchange.getRequestMethod()) ? body(exchange) : null;
		Future<String> future = Bukkit.getScheduler().callSyncMethod(plugin, () -> action.run(player, body));
		try {
			String answer = future.get(5, TimeUnit.SECONDS);
			json(exchange, answer.contains("\"error\"") ? 400 : 200, answer);
		} catch (Exception e) {
			json(exchange, 503, error("서버가 바빠서 처리하지 못했습니다. 잠시 뒤 다시 시도하세요."));
		}
	}

	/** An admin's action: as {@link #action}, for players with the trade admin permission (or ops). */
	private static void admin(HttpExchange exchange, Action action) throws IOException {
		action(exchange, (player, body) -> WebActions.isAdmin(player) ? action.run(player, body) : WebActions.error("관리자만 쓸 수 있습니다."));
	}

	private static long number(JsonObject body, String field) {
		try {
			return body != null && body.has(field) ? body.get(field).getAsLong() : 0;
		} catch (RuntimeException e) {
			return 0;
		}
	}

	private static JsonArray notices() {
		JsonArray out = new JsonArray();
		for (WebNotices.Notice notice : WebNotices.all()) {
			JsonObject row = new JsonObject();
			row.addProperty("id", notice.id());
			row.addProperty("title", notice.title());
			row.addProperty("body", notice.body());
			row.addProperty("author", notice.author());
			row.addProperty("time", notice.time());
			row.addProperty("giveAll", notice.giveAll());
			out.add(row);
		}
		return out;
	}

	private static void me(HttpExchange exchange) throws IOException {
		String key = exchange.getRequestHeaders().getFirst("X-Key");
		UUID player = WebAccounts.session(key);
		if (player == null) {
			json(exchange, 401, error("로그인이 끝났습니다. 다시 로그인하세요."));
			return;
		}
		long expires = WebAccounts.expires(key);
		Future<String> future = Bukkit.getScheduler().callSyncMethod(plugin, () -> mine(player, expires));
		try {
			json(exchange, 200, future.get(5, TimeUnit.SECONDS));
		} catch (Exception e) {
			json(exchange, 503, "{\"error\":\"서버가 바빠서 정보를 가져오지 못했습니다. 잠시 뒤 다시 시도하세요.\"}");
		}
	}

	private static String error(String message) {
		JsonObject out = new JsonObject();
		out.addProperty("error", message);
		return GSON.toJson(out);
	}

	/** The request's JSON body (at most {@link #MAX_BODY} bytes), or null. */
	private static JsonObject body(HttpExchange exchange) throws IOException {
		byte[] bytes = exchange.getRequestBody().readNBytes(MAX_BODY + 1);
		if (bytes.length > MAX_BODY) {
			return null;
		}
		try {
			return GSON.fromJson(new String(bytes, StandardCharsets.UTF_8), JsonObject.class);
		} catch (RuntimeException e) {
			return null;
		}
	}

	private static String text(JsonObject body, String field) {
		return body != null && body.has(field) && body.get(field).isJsonPrimitive() ? body.get(field).getAsString() : "";
	}

	private static String address(HttpExchange exchange) {
		return exchange.getRemoteAddress() == null ? "?" : exchange.getRemoteAddress().getAddress().getHostAddress();
	}

	/** POST /api/login {name, password}: a session key for the page. */
	private static void login(HttpExchange exchange) throws IOException {
		JsonObject body = body(exchange);
		String name = text(body, "name");
		String password = text(body, "password");
		if (name.isBlank() || password.isEmpty() || password.length() > WebAccounts.MAX_PASSWORD) {
			json(exchange, 400, error("닉네임과 비밀번호를 입력하세요."));
			return;
		}
		WebAccounts.Login result = WebAccounts.login(name, password, address(exchange));
		if (result.player() == null) {
			json(exchange, 401, error(result.error()));
			return;
		}
		String key = WebAccounts.newSession(result.player(), TimeUnit.DAYS.toMillis(sessionDays));
		JsonObject out = new JsonObject();
		out.addProperty("key", key);
		out.addProperty("name", WebAccounts.nameOf(result.player()));
		json(exchange, 200, GSON.toJson(out));
	}

	/** POST /api/logout: ends this device's session. */
	private static void logout(HttpExchange exchange) throws IOException {
		WebAccounts.endSession(exchange.getRequestHeaders().getFirst("X-Key"));
		json(exchange, 200, "{}");
	}

	/** POST /api/password {old, new}: changes the password (every other session ends); answers with a new key. */
	private static void password(HttpExchange exchange) throws IOException {
		UUID player = WebAccounts.session(exchange.getRequestHeaders().getFirst("X-Key"));
		if (player == null) {
			json(exchange, 401, error("로그인이 끝났습니다. 다시 로그인하세요."));
			return;
		}
		JsonObject body = body(exchange);
		String old = text(body, "old");
		String fresh = text(body, "new");
		String name = WebAccounts.nameOf(player);
		if (name == null) {
			json(exchange, 400, error("아직 웹 비밀번호가 없습니다. 게임에서 /trade 웹 비밀번호 로 먼저 정하세요."));
			return;
		}
		if (!WebAccounts.verify(player, old)) {
			json(exchange, 403, error("지금 비밀번호가 맞지 않습니다."));
			return;
		}
		String problem = WebAccounts.badPassword(name, fresh);
		if (problem != null) {
			json(exchange, 400, error(problem));
			return;
		}
		WebAccounts.setPassword(player, name, fresh);
		JsonObject out = new JsonObject();
		out.addProperty("key", WebAccounts.newSession(player, TimeUnit.DAYS.toMillis(sessionDays)));
		json(exchange, 200, GSON.toJson(out));
	}

	private static void json(HttpExchange exchange, int status, String body) throws IOException {
		exchange.getResponseHeaders().add("Cache-Control", "no-store");
		send(exchange, status, "application/json", body.getBytes(StandardCharsets.UTF_8));
	}

	private static void send(HttpExchange exchange, int status, String type, byte[] body) throws IOException {
		exchange.getResponseHeaders().add("Content-Type", type + "; charset=utf-8");
		exchange.getResponseHeaders().add("X-Content-Type-Options", "nosniff");
		exchange.getResponseHeaders().add("Referrer-Policy", "no-referrer");
		exchange.sendResponseHeaders(status, body.length);
		try (OutputStream out = exchange.getResponseBody()) {
			out.write(body);
		}
	}

	// --- what the page shows (built on the main thread) --------------------------------

	/** Rebuilds the public snapshot; a failure keeps the last one and is logged, never thrown into the server. */
	private static void refresh() {
		try {
			publicJson = snapshot();
		} catch (RuntimeException e) {
			plugin.getLogger().log(Level.WARNING, "Could not update the web page's public data", e);
		}
	}

	private static String snapshot() {
		JsonObject root = new JsonObject();
		JsonObject srv = new JsonObject();
		srv.addProperty("online", Bukkit.getOnlinePlayers().size());
		srv.addProperty("max", Bukkit.getMaxPlayers());
		srv.addProperty("motd", PLAIN.serialize(Bukkit.motd()));
		srv.addProperty("version", Bukkit.getMinecraftVersion());
		JsonArray players = new JsonArray();
		for (Player player : Bukkit.getOnlinePlayers()) {
			players.add(player.getName());
		}
		srv.add("players", players);
		root.add("server", srv);
		root.add("currency", currency());
		JsonArray market = new JsonArray();
		for (Listing listing : Market.all()) {
			JsonObject row = new JsonObject();
			row.add("item", item(listing.item));
			row.addProperty("price", listing.price);
			row.addProperty("id", listing.id.toString());
			row.addProperty("seller", Market.name(listing.seller));
			row.addProperty("sellerId", listing.seller.toString());
			Double average = WebData.average(listing.item.getType().getKey().getKey());
			if (average != null) {
				row.addProperty("avg", average);
			}
			row.addProperty("time", listing.createdAt);
			String chunk = Land.deedChunk(listing.item);
			if (chunk != null) {
				row.addProperty("land", chunk);
			}
			market.add(row);
		}
		root.add("market", market);
		JsonArray orders = new JsonArray();
		for (BuyOrders.Order order : BuyOrders.all()) {
			orders.add(order(order));
		}
		root.add("orders", orders);
		root.add("notices", notices());
		root.addProperty("updated", System.currentTimeMillis());
		return GSON.toJson(root);
	}

	private static JsonObject currency() {
		JsonObject currency = new JsonObject();
		currency.addProperty("name", Money.name());
		currency.addProperty("symbol", Money.symbol());
		return currency;
	}

	private static JsonObject order(BuyOrders.Order order) {
		JsonObject row = new JsonObject();
		row.addProperty("id", order.id.toString());
		ItemStack shown = order.want.clone();
		shown.setAmount(1);
		row.add("item", item(shown));
		row.addProperty("amount", order.amount);
		row.addProperty("price", order.price);
		row.addProperty("buyer", order.buyerName);
		row.addProperty("time", order.createdAt);
		return row;
	}

	/** An item as the page shows it: its id (for the Korean name), its own name if it has one, amount, enchantments, wear. */
	private static JsonObject item(ItemStack stack) {
		JsonObject out = new JsonObject();
		out.addProperty("id", stack.getType().getKey().getKey());
		out.addProperty("amount", stack.getAmount());
		if (stack.hasItemMeta()) {
			ItemMeta meta = stack.getItemMeta();
			if (meta.hasCustomName()) {
				out.addProperty("name", PLAIN.serialize(meta.customName()));
			} else if (meta.hasItemName()) {
				out.addProperty("name", PLAIN.serialize(meta.itemName()));
			}
			Map<Enchantment, Integer> enchants = meta instanceof EnchantmentStorageMeta book ? book.getStoredEnchants() : meta.getEnchants();
			if (!enchants.isEmpty()) {
				JsonArray list = new JsonArray();
				enchants.forEach((enchantment, level) -> {
					JsonArray pair = new JsonArray();
					pair.add(enchantment.getKey().getKey());
					pair.add(level);
					list.add(pair);
				});
				out.add("ench", list);
			}
			if (meta instanceof Damageable damageable && damageable.hasDamage() && stack.getType().getMaxDurability() > 0) {
				out.addProperty("wear", Math.round(100.0 * damageable.getDamage() / stack.getType().getMaxDurability()));
			}
		}
		return out;
	}

	/** The price table: every sellable item with what one sells for (gear at half, as the sell box pays). */
	private static String values() {
		JsonArray rows = new JsonArray();
		for (Material material : Material.values()) {
			if (material.isLegacy() || !material.isItem()) {
				continue;
			}
			Double value = ItemValues.value(material);
			if (value == null) {
				continue;
			}
			boolean gear = material.getMaxDurability() > 0;
			JsonArray row = new JsonArray();
			row.add(material.getKey().getKey());
			row.add(Math.round((gear ? value * 0.5 : value) * 100.0) / 100.0);
			row.add(gear ? 1 : 0);
			rows.add(row);
		}
		JsonObject root = new JsonObject();
		root.add("currency", currency());
		root.add("items", rows);
		return GSON.toJson(root);
	}

	/** A player's own part of the page. */
	private static String mine(UUID id, long expires) {
		JsonObject root = new JsonObject();
		String name = Bukkit.getOfflinePlayer(id).getName();
		root.addProperty("name", name != null ? name : String.valueOf(WebAccounts.nameOf(id)));
		root.addProperty("hasPassword", WebAccounts.hasPassword(id));
		root.addProperty("uuid", id.toString());
		root.addProperty("admin", WebActions.isAdmin(id));
		root.add("limits", WebActions.limits(id));
		JsonObject stats = new JsonObject();
		org.bukkit.OfflinePlayer offline = Bukkit.getOfflinePlayer(id);
		try {
			stats.addProperty("minutes", offline.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE) / 20L / 60L);
			stats.addProperty("deaths", offline.getStatistic(org.bukkit.Statistic.DEATHS));
			stats.addProperty("playerKills", offline.getStatistic(org.bukkit.Statistic.PLAYER_KILLS));
			stats.addProperty("mobKills", offline.getStatistic(org.bukkit.Statistic.MOB_KILLS));
		} catch (RuntimeException e) {
			// No statistics yet.
		}
		stats.addProperty("firstPlayed", offline.getFirstPlayed());
		stats.addProperty("lastSeen", offline.getLastSeen());
		root.add("stats", stats);
		Player online = Bukkit.getPlayer(id);
		if (online != null) {
			root.add("inventory", slots(online.getInventory().getStorageContents()));
			ItemStack[] worn = online.getInventory().getArmorContents();
			ItemStack[] gear = java.util.Arrays.copyOf(worn, worn.length + 1);
			gear[worn.length] = online.getInventory().getItemInOffHand();
			root.add("gear", slots(gear));
			root.add("enderChest", slots(online.getEnderChest().getContents()));
		}
		root.addProperty("online", Bukkit.getPlayer(id) != null);
		root.addProperty("balance", Money.balance(id));
		root.add("currency", currency());
		root.addProperty("expires", expires);
		JsonArray mail = new JsonArray();
		for (Mailbox.Mail entry : Mailbox.of(id)) {
			JsonObject row = new JsonObject();
			row.add("item", item(entry.item()));
			row.addProperty("note", entry.note());
			row.addProperty("time", entry.time());
			mail.add(row);
		}
		root.add("mail", mail);
		root.add("bought", history(id, false));
		root.add("sold", history(id, true));
		JsonArray listings = new JsonArray();
		for (Listing listing : Market.of(id)) {
			JsonObject row = new JsonObject();
			row.add("item", item(listing.item));
			row.addProperty("price", listing.price);
			row.addProperty("time", listing.createdAt);
			listings.add(row);
		}
		root.add("listings", listings);
		JsonArray orders = new JsonArray();
		for (BuyOrders.Order order : BuyOrders.of(id)) {
			orders.add(order(order));
		}
		root.add("orders", orders);
		JsonArray land = new JsonArray();
		for (Land.Claim claim : Land.ownedBy(id)) {
			String[] parts = claim.key.split(";");
			JsonObject row = new JsonObject();
			row.addProperty("world", parts[0]);
			int cx = Integer.parseInt(parts[1]);
			int cz = Integer.parseInt(parts[2]);
			row.addProperty("x", cx * 16);
			row.addProperty("z", cz * 16);
			row.addProperty("members", claim.members.size());
			JsonArray everyone = new JsonArray();
			claim.everyone.forEach(perm -> everyone.add(perm.label));
			row.add("everyone", everyone);
			land.add(row);
		}
		root.add("land", land);
		JsonArray groups = new JsonArray();
		for (Groups.Group group : Groups.of(id)) {
			JsonObject row = new JsonObject();
			row.addProperty("name", group.name);
			Groups.Role role = group.roleOf(id);
			row.addProperty("role", role == null ? "" : role.name);
			row.addProperty("members", group.memberRoles.size());
			groups.add(row);
		}
		root.add("groups", groups);
		return GSON.toJson(root);
	}

	/** Slots of an inventory: an item per slot, or null for an empty one. */
	private static JsonArray slots(ItemStack[] contents) {
		JsonArray out = new JsonArray();
		for (ItemStack stack : contents) {
			if (stack == null || stack.isEmpty()) {
				out.add(com.google.gson.JsonNull.INSTANCE);
			} else {
				out.add(item(stack));
			}
		}
		return out;
	}

	private static JsonArray history(UUID id, boolean sales) {
		JsonArray rows = new JsonArray();
		for (History.Entry entry : History.of(id, sales)) {
			if (rows.size() >= HISTORY_LIMIT) {
				break;
			}
			JsonObject row = new JsonObject();
			row.add("item", item(entry.item()));
			row.addProperty("price", entry.price());
			row.addProperty("with", sales ? entry.buyerName() : entry.sellerName());
			row.addProperty("time", entry.time());
			rows.add(row);
		}
		return rows;
	}
}
