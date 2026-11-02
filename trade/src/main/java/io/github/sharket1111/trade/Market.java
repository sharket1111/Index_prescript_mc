package io.github.sharket1111.trade;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/** All listings, saved to listings.yml after every change. */
final class Market {
	static final int MAX_LISTINGS_PER_PLAYER = 18;
	static final int MIN_PRICE = 1;
	static final int MAX_PRICE = 9999;

	private static Plugin plugin;
	private static File file;
	private static final Map<UUID, Listing> LISTINGS = new LinkedHashMap<>();

	private Market() {
	}

	static void load(Plugin owner) {
		plugin = owner;
		file = new File(owner.getDataFolder(), "listings.yml");
		LISTINGS.clear();
		if (!file.exists()) {
			return;
		}
		YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
		ConfigurationSection root = yaml.getConfigurationSection("listings");
		if (root == null) {
			return;
		}
		for (String key : root.getKeys(false)) {
			ConfigurationSection s = root.getConfigurationSection(key);
			try {
				ItemStack item = ItemStack.deserializeBytes(Base64.getDecoder().decode(s.getString("item", "")));
				Listing listing = new Listing(UUID.fromString(key), UUID.fromString(s.getString("seller")),
						s.getString("seller-name", "?"), item, s.getInt("price", MIN_PRICE), s.getLong("created"));
				LISTINGS.put(listing.id, listing);
			} catch (RuntimeException e) {
				owner.getLogger().log(Level.WARNING, "Skipping a broken listing " + key, e);
			}
		}
	}

	static void save() {
		YamlConfiguration yaml = new YamlConfiguration();
		for (Listing listing : LISTINGS.values()) {
			String path = "listings." + listing.id;
			yaml.set(path + ".seller", listing.seller.toString());
			yaml.set(path + ".seller-name", listing.sellerName);
			yaml.set(path + ".price", listing.price);
			yaml.set(path + ".created", listing.createdAt);
			yaml.set(path + ".item", Base64.getEncoder().encodeToString(listing.item.serializeAsBytes()));
		}
		try {
			file.getParentFile().mkdirs();
			yaml.save(file);
		} catch (IOException e) {
			plugin.getLogger().log(Level.SEVERE, "Could not save listings.yml", e);
		}
	}

	static List<Listing> of(UUID seller) {
		List<Listing> mine = new ArrayList<>();
		for (Listing listing : LISTINGS.values()) {
			if (listing.seller.equals(seller)) {
				mine.add(listing);
			}
		}
		mine.sort(Comparator.comparingLong(l -> l.createdAt));
		return mine;
	}

	static Listing get(UUID id) {
		return LISTINGS.get(id);
	}

	static void add(Listing listing) {
		LISTINGS.put(listing.id, listing);
		save();
	}

	static void remove(Listing listing) {
		LISTINGS.remove(listing.id);
		save();
	}
}
