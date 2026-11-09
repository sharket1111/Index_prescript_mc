package io.github.sharket1111.trade;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
 * purchases and sales, listings, buy requests, land, groups) needs the
 * personal link {@code /trade 웹} gives them: a random key valid for
 * {@code link-hours}, kept in memory only (a restart or {@code /trade 웹 끊기}
 * ends every link). The page only reads; nothing can be bought or changed
 * from it.
 *
 * Bukkit is not thread-safe, so the web threads never touch the game: the
 * public snapshot is rebuilt on the main thread every few seconds, and a
 * player's own data is gathered on the main thread when it is asked for.
 */
final class WebSite {
	private static final int REFRESH_TICKS = 100;
	private static final int HISTORY_LIMIT = 60;
	private static final SecureRandom RANDOM = new SecureRandom();
	private static final Gson GSON = new Gson();
	private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

	private record Link(UUID player, String name, long expires) {
	}

	private static Plugin plugin;
	private static HttpServer server;
	private static boolean enabled;
	private static int port;
	private static String address = "";
	private static int linkHours = 24;
	private static volatile String publicJson = "{}";
	private static volatile String valuesJson = "[]";
	private static byte[] indexPage = new byte[0];
	private static byte[] namesJson = new byte[0];
	private static final Map<String, Link> LINKS = new ConcurrentHashMap<>();

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
					"link-hours: how long a personal link from /trade 웹 works."));
			yaml.set("enabled", false);
			yaml.set("port", 0);
			yaml.set("address", "");
			yaml.set("link-hours", 24);
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
		LINKS.clear();
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

	/** /trade 웹: a personal link to the page; {@code revoke} ends every link the player has. */
	static void command(Player player, boolean revoke) {
		if (revoke) {
			LINKS.values().removeIf(link -> link.player.equals(player.getUniqueId()));
			player.sendMessage(Component.text("내 웹 링크를 모두 끊었습니다.", NamedTextColor.YELLOW));
			return;
		}
		if (!enabled) {
			player.sendMessage(Component.text("웹 페이지가 꺼져 있습니다. 관리자가 plugins/PlayerTrade/web.yml에서 켜야 합니다.", NamedTextColor.RED));
			return;
		}
		long now = System.currentTimeMillis();
		LINKS.values().removeIf(link -> link.expires < now);
		byte[] bytes = new byte[18];
		RANDOM.nextBytes(bytes);
		String key = HexFormat.of().formatHex(bytes);
		LINKS.put(key, new Link(player.getUniqueId(), player.getName(), now + TimeUnit.HOURS.toMillis(linkHours)));
		String link = url() + "#k=" + key;
		player.sendMessage(Component.text("내 정보 웹 페이지 (" + linkHours + "시간 동안, 나만 쓰세요): ", NamedTextColor.GOLD)
				.append(Component.text("[열기]", NamedTextColor.AQUA).clickEvent(ClickEvent.openUrl(link))
						.hoverEvent(HoverEvent.showText(Component.text(link)))));
		player.sendMessage(Component.text("링크를 남에게 주면 내 정보가 보입니다. 끊으려면 /trade 웹 끊기", NamedTextColor.GRAY));
	}

	// --- serving ---------------------------------------------------------------------

	private static void handle(HttpExchange exchange) throws IOException {
		try (exchange) {
			if (!"GET".equals(exchange.getRequestMethod())) {
				send(exchange, 405, "text/plain", "GET only".getBytes(StandardCharsets.UTF_8));
				return;
			}
			String path = exchange.getRequestURI().getPath();
			switch (path) {
				case "/", "/index.html" -> send(exchange, 200, "text/html", indexPage);
				case "/ko.json" -> send(exchange, 200, "application/json", namesJson);
				case "/api/public" -> json(exchange, 200, publicJson);
				case "/api/values" -> json(exchange, 200, valuesJson);
				case "/api/me" -> me(exchange);
				default -> send(exchange, 404, "text/plain", "Not found".getBytes(StandardCharsets.UTF_8));
			}
		} catch (RuntimeException e) {
			plugin.getLogger().log(Level.WARNING, "Web request failed", e);
		}
	}

	private static void me(HttpExchange exchange) throws IOException {
		String key = exchange.getRequestHeaders().getFirst("X-Key");
		Link link = key == null ? null : LINKS.get(key.trim());
		if (link == null || link.expires < System.currentTimeMillis()) {
			if (link != null) {
				LINKS.remove(key.trim());
			}
			json(exchange, 401, "{\"error\":\"링크가 만료되었습니다. 게임에서 /trade 웹 으로 새 링크를 받으세요.\"}");
			return;
		}
		Future<String> future = Bukkit.getScheduler().callSyncMethod(plugin, () -> mine(link));
		try {
			json(exchange, 200, future.get(5, TimeUnit.SECONDS));
		} catch (Exception e) {
			json(exchange, 503, "{\"error\":\"서버가 바빠서 정보를 가져오지 못했습니다. 잠시 뒤 다시 시도하세요.\"}");
		}
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

	private static void refresh() {
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
			row.addProperty("seller", Market.name(listing.seller));
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
		root.addProperty("updated", System.currentTimeMillis());
		publicJson = GSON.toJson(root);
	}

	private static JsonObject currency() {
		JsonObject currency = new JsonObject();
		currency.addProperty("name", Money.name());
		currency.addProperty("symbol", Money.symbol());
		return currency;
	}

	private static JsonObject order(BuyOrders.Order order) {
		JsonObject row = new JsonObject();
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
	private static String mine(Link link) {
		UUID id = link.player;
		JsonObject root = new JsonObject();
		String name = Bukkit.getOfflinePlayer(id).getName();
		root.addProperty("name", name != null ? name : link.name);
		root.addProperty("online", Bukkit.getPlayer(id) != null);
		root.addProperty("balance", Money.balance(id));
		root.add("currency", currency());
		root.addProperty("expires", link.expires);
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
