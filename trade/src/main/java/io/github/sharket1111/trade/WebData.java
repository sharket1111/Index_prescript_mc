package io.github.sharket1111.trade;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * The web page's slower data, rebuilt on the main thread every
 * {@link #REFRESH_TICKS} and served as ready JSON: the rankings (richest,
 * top sellers, play time, most land), what each item has really sold for
 * between players (recent trades and the 30-day average per item), and the
 * land map (every claimed and protected chunk).
 */
final class WebData {
	private static final int REFRESH_TICKS = 20 * 60;
	private static final int TOP = 10;
	private static final int RECENT_TRADES = 40;
	private static final long AVERAGE_WINDOW = TimeUnit.DAYS.toMillis(30);
	private static final Gson GSON = new Gson();

	private static volatile String statsJson = "{}";
	private static volatile String mapJson = "{}";
	private static volatile String economyJson = "{}";
	/** Each board's full order: board -> player -> {rank (1 = top), value}. */
	private static volatile Map<String, Map<UUID, long[]>> positions = Map.of();
	/** Every player the plugin knows, by lower-case name (for profiles of players the server has not cached). */
	private static volatile Map<String, UUID> names = Map.of();
	private static File supplyFile;
	private static final Map<String, Long> SUPPLY = new java.util.TreeMap<>();
	/** Average unit price over the last 30 days, by item id (for the market's "cheaper than usual"). */
	private static volatile Map<String, Double> averages = Map.of();

	private WebData() {
	}

	static void start(Plugin plugin) {
		supplyFile = new File(plugin.getDataFolder(), "economy-history.yml");
		if (supplyFile.exists()) {
			YamlConfiguration yaml = YamlConfiguration.loadConfiguration(supplyFile);
			for (String day : yaml.getKeys(false)) {
				SUPPLY.put(day, yaml.getLong(day));
			}
		}
		refresh(plugin);
		Bukkit.getScheduler().runTaskTimer(plugin, () -> refresh(plugin), REFRESH_TICKS, REFRESH_TICKS);
	}

	static String stats() {
		return statsJson;
	}

	static String map() {
		return mapJson;
	}

	static Double average(String itemId) {
		return averages.get(itemId);
	}

	static String economy() {
		return economyJson;
	}

	/** {rank, value} of {@code player} on {@code board} (rich, sellers, land, playtime), or null. */
	static long[] position(String board, UUID player) {
		Map<UUID, long[]> order = positions.get(board);
		return order == null ? null : order.get(player);
	}

	static UUID byName(String name) {
		return names.get(name.toLowerCase(java.util.Locale.ROOT));
	}

	private static void refresh(Plugin plugin) {
		try {
			statsJson = buildStats();
			mapJson = buildMap();
			economyJson = buildEconomy();
		} catch (RuntimeException e) {
			plugin.getLogger().log(Level.WARNING, "Could not update the web page's rankings and map", e);
		}
	}

	/** A player's name, falling back to the names saved with their trades and land when the server has none cached. */
	private static String name(UUID id) {
		String name = WebActions.name(id);
		if (!"?".equals(name)) {
			return name;
		}
		for (History.Entry entry : History.all()) {
			if (id.equals(entry.seller()) && entry.sellerName() != null) {
				return entry.sellerName();
			}
			if (id.equals(entry.buyer()) && entry.buyerName() != null) {
				return entry.buyerName();
			}
		}
		for (Land.Claim claim : Land.all()) {
			if (claim.owner.equals(id)) {
				return claim.ownerName;
			}
		}
		return name;
	}

	private static JsonArray top(String board, Map<UUID, Long> values, Map<String, Map<UUID, long[]>> order) {
		List<Map.Entry<UUID, Long>> sorted = new ArrayList<>(values.entrySet());
		sorted.removeIf(entry -> entry.getValue() <= 0);
		sorted.sort(Map.Entry.<UUID, Long>comparingByValue().reversed());
		Map<UUID, long[]> ranks = new HashMap<>();
		for (int i = 0; i < sorted.size(); i++) {
			ranks.put(sorted.get(i).getKey(), new long[] {i + 1, sorted.get(i).getValue()});
		}
		order.put(board, ranks);
		JsonArray out = new JsonArray();
		for (int i = 0; i < sorted.size() && i < TOP; i++) {
			JsonObject row = new JsonObject();
			row.addProperty("uuid", sorted.get(i).getKey().toString());
			row.addProperty("name", name(sorted.get(i).getKey()));
			row.addProperty("value", sorted.get(i).getValue());
			out.add(row);
		}
		return out;
	}

	private static String buildStats() {
		JsonObject root = new JsonObject();
		JsonObject ranks = new JsonObject();
		Map<String, Map<UUID, long[]>> order = new HashMap<>();
		ranks.add("rich", top("rich", Money.balances(), order));
		Map<UUID, Long> sales = new HashMap<>();
		List<History.Entry> history = History.all();
		for (History.Entry entry : history) {
			sales.merge(entry.seller(), (long) entry.price(), Long::sum);
		}
		ranks.add("sellers", top("sellers", sales, order));
		Map<UUID, Long> land = new HashMap<>();
		for (Land.Claim claim : Land.all()) {
			land.merge(claim.owner, 1L, Long::sum);
		}
		ranks.add("land", top("land", land, order));
		// Play time: everyone who has the trade plugin's data or is online (reading every player file would be slow).
		Set<UUID> known = new HashSet<>(Money.balances().keySet());
		known.addAll(land.keySet());
		known.addAll(sales.keySet());
		Bukkit.getOnlinePlayers().forEach(player -> known.add(player.getUniqueId()));
		Map<UUID, Long> minutes = new HashMap<>();
		for (UUID id : known) {
			OfflinePlayer player = Bukkit.getOfflinePlayer(id);
			if (player.hasPlayedBefore() || player.isOnline()) {
				try {
					minutes.put(id, player.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20L / 60L);
				} catch (RuntimeException e) {
					// No statistics yet.
				}
			}
		}
		ranks.add("playtime", top("playtime", minutes, order));
		root.add("ranks", ranks);
		positions = order;
		Map<String, UUID> known2 = new HashMap<>();
		for (History.Entry entry : history) {
			known.add(entry.buyer());
		}
		Market.all().forEach(listing -> known.add(listing.seller));
		Auctions.all().forEach(auction -> known.add(auction.seller));
		for (UUID id : known) {
			String name = name(id);
			if (!"?".equals(name)) {
				known2.put(name.toLowerCase(java.util.Locale.ROOT), id);
			}
		}
		names = known2;
		// Trade prices: unit price of every sale, by item.
		Map<String, List<double[]>> byItem = new LinkedHashMap<>();
		for (History.Entry entry : history) {
			if (Land.deedChunk(entry.item()) != null) {
				continue;
			}
			int amount = Math.max(1, entry.item().getAmount());
			byItem.computeIfAbsent(entry.item().getType().getKey().getKey(), key -> new ArrayList<>())
					.add(new double[] {entry.time(), (double) entry.price() / amount});
		}
		long now = System.currentTimeMillis();
		Map<String, Double> averageMap = new HashMap<>();
		JsonObject trades = new JsonObject();
		for (Map.Entry<String, List<double[]>> item : byItem.entrySet()) {
			List<double[]> points = item.getValue();
			points.sort(Comparator.comparingDouble(p -> p[0]));
			double sum = 0;
			int count = 0;
			for (double[] point : points) {
				if (now - point[0] <= AVERAGE_WINDOW) {
					sum += point[1];
					count++;
				}
			}
			JsonObject row = new JsonObject();
			if (count > 0) {
				double average = Math.round(sum / count * 100.0) / 100.0;
				row.addProperty("avg", average);
				if (count >= 2) {
					averageMap.put(item.getKey(), average);
				}
			}
			row.addProperty("count", points.size());
			JsonArray recent = new JsonArray();
			for (int i = Math.max(0, points.size() - RECENT_TRADES); i < points.size(); i++) {
				JsonArray point = new JsonArray();
				point.add((long) points.get(i)[0]);
				point.add(Math.round(points.get(i)[1] * 100.0) / 100.0);
				recent.add(point);
			}
			row.add("recent", recent);
			trades.add(item.getKey(), row);
		}
		averages = averageMap;
		root.add("trades", trades);
		root.addProperty("updated", now);
		return GSON.toJson(root);
	}

	/** Money in circulation and how much players trade, for the economy tab. */
	private static String buildEconomy() {
		Map<UUID, Long> balances = Money.balances();
		List<Long> amounts = new ArrayList<>(balances.values());
		amounts.removeIf(v -> v <= 0);
		amounts.sort(null);
		long supply = 0;
		for (long v : amounts) {
			supply += v;
		}
		long top10 = 0;
		for (int i = amounts.size() - 1; i >= 0 && i >= amounts.size() - 10; i--) {
			top10 += amounts.get(i);
		}
		JsonObject root = new JsonObject();
		root.addProperty("supply", supply);
		root.addProperty("holders", amounts.size());
		root.addProperty("average", amounts.isEmpty() ? 0 : supply / amounts.size());
		root.addProperty("median", amounts.isEmpty() ? 0 : amounts.get(amounts.size() / 2));
		root.addProperty("top10Share", supply == 0 ? 0 : Math.round(1000.0 * top10 / supply) / 10.0);
		// Money in circulation, one value a day (today's keeps updating).
		String today = java.time.LocalDate.now().toString();
		if (!Long.valueOf(supply).equals(SUPPLY.get(today))) {
			SUPPLY.put(today, supply);
			while (SUPPLY.size() > 120) {
				SUPPLY.remove(SUPPLY.keySet().iterator().next());
			}
			YamlConfiguration yaml = new YamlConfiguration();
			SUPPLY.forEach(yaml::set);
			try {
				yaml.save(supplyFile);
			} catch (java.io.IOException e) {
				// Kept in memory; tried again next minute.
			}
		}
		JsonArray supplyDays = new JsonArray();
		SUPPLY.forEach((day, value) -> {
			JsonArray row = new JsonArray();
			row.add(day);
			row.add(value);
			supplyDays.add(row);
		});
		root.add("supplyDays", supplyDays);
		// Trades a day for the last 30 days, and the most traded items.
		java.time.LocalDate first = java.time.LocalDate.now().minusDays(29);
		long[] count = new long[30];
		long[] volume = new long[30];
		Map<String, long[]> items = new HashMap<>();
		long weekCount = 0;
		long weekVolume = 0;
		for (History.Entry entry : History.all()) {
			java.time.LocalDate day = java.time.Instant.ofEpochMilli(entry.time()).atZone(java.time.ZoneId.systemDefault()).toLocalDate();
			long index = java.time.temporal.ChronoUnit.DAYS.between(first, day);
			if (index < 0 || index >= 30) {
				continue;
			}
			count[(int) index]++;
			volume[(int) index] += entry.price();
			if (index >= 23) {
				weekCount++;
				weekVolume += entry.price();
			}
			if (Land.deedChunk(entry.item()) == null) {
				long[] item = items.computeIfAbsent(entry.item().getType().getKey().getKey(), k -> new long[2]);
				item[0] += entry.item().getAmount();
				item[1] += entry.price();
			}
		}
		JsonArray days = new JsonArray();
		for (int i = 0; i < 30; i++) {
			JsonArray row = new JsonArray();
			row.add(first.plusDays(i).toString());
			row.add(count[i]);
			row.add(volume[i]);
			days.add(row);
		}
		root.add("days", days);
		root.addProperty("weekTrades", weekCount);
		root.addProperty("weekVolume", weekVolume);
		List<Map.Entry<String, long[]>> topItems = new ArrayList<>(items.entrySet());
		topItems.sort((a, b) -> Long.compare(b.getValue()[1], a.getValue()[1]));
		JsonArray itemRows = new JsonArray();
		for (int i = 0; i < topItems.size() && i < 10; i++) {
			JsonObject row = new JsonObject();
			row.addProperty("id", topItems.get(i).getKey());
			row.addProperty("amount", topItems.get(i).getValue()[0]);
			row.addProperty("volume", topItems.get(i).getValue()[1]);
			itemRows.add(row);
		}
		root.add("topItems", itemRows);
		long held = 0;
		for (Auctions.Auction auction : Auctions.all()) {
			if (auction.bidder != null) {
				held += auction.bid;
			}
		}
		root.addProperty("auctions", Auctions.all().size());
		root.addProperty("auctionHeld", held);
		root.addProperty("listings", Market.all().size());
		root.addProperty("updated", System.currentTimeMillis());
		return GSON.toJson(root);
	}

	private static String buildMap() {
		Set<String> forSale = new HashSet<>();
		for (Listing listing : Market.all()) {
			String chunk = Land.deedChunk(listing.item);
			if (chunk != null) {
				forSale.add(chunk);
			}
		}
		JsonObject root = new JsonObject();
		JsonArray claims = new JsonArray();
		for (Land.Claim claim : Land.all()) {
			String[] parts = claim.key.split(";");
			JsonArray row = new JsonArray();
			row.add(parts[0]);
			row.add(Integer.parseInt(parts[1]));
			row.add(Integer.parseInt(parts[2]));
			row.add(name(claim.owner));
			row.add(claim.owner.toString());
			row.add(forSale.contains(claim.key) ? 1 : 0);
			claims.add(row);
		}
		root.add("claims", claims);
		JsonArray zones = new JsonArray();
		for (String key : Land.zones()) {
			String[] parts = key.split(";");
			JsonArray row = new JsonArray();
			row.add(parts[0]);
			row.add(Integer.parseInt(parts[1]));
			row.add(Integer.parseInt(parts[2]));
			zones.add(row);
		}
		root.add("zones", zones);
		JsonArray worlds = new JsonArray();
		Bukkit.getWorlds().forEach(world -> worlds.add(world.getName()));
		root.add("worlds", worlds);
		root.addProperty("spawnRadius", Land.SPAWN_RADIUS);
		return GSON.toJson(root);
	}
}
