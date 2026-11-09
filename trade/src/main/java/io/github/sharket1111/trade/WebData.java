package io.github.sharket1111.trade;

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
	/** Average unit price over the last 30 days, by item id (for the market's "cheaper than usual"). */
	private static volatile Map<String, Double> averages = Map.of();

	private WebData() {
	}

	static void start(Plugin plugin) {
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

	private static void refresh(Plugin plugin) {
		try {
			statsJson = buildStats();
			mapJson = buildMap();
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

	private static JsonArray top(Map<UUID, Long> values) {
		List<Map.Entry<UUID, Long>> sorted = new ArrayList<>(values.entrySet());
		sorted.removeIf(entry -> entry.getValue() <= 0);
		sorted.sort(Map.Entry.<UUID, Long>comparingByValue().reversed());
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
		ranks.add("rich", top(Money.balances()));
		Map<UUID, Long> sales = new HashMap<>();
		List<History.Entry> history = History.all();
		for (History.Entry entry : history) {
			sales.merge(entry.seller(), (long) entry.price(), Long::sum);
		}
		ranks.add("sellers", top(sales));
		Map<UUID, Long> land = new HashMap<>();
		for (Land.Claim claim : Land.all()) {
			land.merge(claim.owner, 1L, Long::sum);
		}
		ranks.add("land", top(land));
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
		ranks.add("playtime", top(minutes));
		root.add("ranks", ranks);
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
