package io.github.sharket1111.trade;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/** Every purchase, newest last, saved to history.yml. The oldest go past {@link #MAX_ENTRIES}. */
final class History {
	private static final int MAX_ENTRIES = 5000;

	record Entry(UUID buyer, String buyerName, UUID seller, String sellerName, ItemStack item, int price, long time) {
	}

	private static Plugin plugin;
	private static File file;
	private static final List<Entry> ENTRIES = new ArrayList<>();

	private History() {
	}

	static void load(Plugin owner) {
		plugin = owner;
		file = new File(owner.getDataFolder(), "history.yml");
		ENTRIES.clear();
		if (!file.exists()) {
			return;
		}
		YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
		for (Map<?, ?> map : yaml.getMapList("purchases")) {
			try {
				ENTRIES.add(new Entry(UUID.fromString(String.valueOf(map.get("buyer"))), String.valueOf(map.get("buyer-name")),
						UUID.fromString(String.valueOf(map.get("seller"))), String.valueOf(map.get("seller-name")),
						ItemStack.deserializeBytes(Base64.getDecoder().decode(String.valueOf(map.get("item")))),
						((Number) map.get("price")).intValue(), ((Number) map.get("time")).longValue()));
			} catch (RuntimeException e) {
				owner.getLogger().log(Level.WARNING, "Skipping a broken history entry", e);
			}
		}
	}

	private static void save() {
		YamlConfiguration yaml = new YamlConfiguration();
		List<Map<String, Object>> list = new ArrayList<>();
		for (Entry entry : ENTRIES) {
			list.add(Map.of(
					"buyer", entry.buyer().toString(),
					"buyer-name", entry.buyerName(),
					"seller", entry.seller().toString(),
					"seller-name", entry.sellerName(),
					"item", Base64.getEncoder().encodeToString(entry.item().serializeAsBytes()),
					"price", entry.price(),
					"time", entry.time()));
		}
		yaml.set("purchases", list);
		try {
			file.getParentFile().mkdirs();
			yaml.save(file);
		} catch (IOException e) {
			plugin.getLogger().log(Level.SEVERE, "Could not save history.yml", e);
		}
	}

	static void record(Listing listing, UUID buyer, String buyerName) {
		ENTRIES.add(new Entry(buyer, buyerName, listing.seller, Market.name(listing.seller), listing.item.clone(), listing.price,
				System.currentTimeMillis()));
		while (ENTRIES.size() > MAX_ENTRIES) {
			ENTRIES.remove(0);
		}
		save();
	}

	/** A player's purchases ({@code sales} false) or sales, newest first. */
	static List<Entry> of(UUID player, boolean sales) {
		List<Entry> found = new ArrayList<>();
		for (int i = ENTRIES.size() - 1; i >= 0; i--) {
			Entry entry = ENTRIES.get(i);
			if ((sales ? entry.seller() : entry.buyer()).equals(player)) {
				found.add(entry);
			}
		}
		return found;
	}
}
