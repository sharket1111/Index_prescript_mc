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

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** All listings, saved to listings.yml after every change. */
final class Market {
	static final int MAX_LISTINGS_PER_PLAYER = 18;
	static final int MIN_PRICE = 1;
	static final int MAX_PRICE = 100_000_000;

	private static Plugin plugin;
	private static File file;
	private static final Map<UUID, Listing> LISTINGS = new LinkedHashMap<>();
	/** Old: trade diamonds owed to sellers who were away when their goods sold (moved onto balances once). */
	private static final Map<UUID, Integer> EARNINGS = new LinkedHashMap<>();

	private Market() {
	}

	static void load(Plugin owner) {
		plugin = owner;
		file = new File(owner.getDataFolder(), "listings.yml");
		LISTINGS.clear();
		EARNINGS.clear();
		if (!file.exists()) {
			return;
		}
		YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
		ConfigurationSection earnings = yaml.getConfigurationSection("earnings");
		if (earnings != null) {
			for (String key : earnings.getKeys(false)) {
				EARNINGS.put(UUID.fromString(key), earnings.getInt(key));
			}
		}
		ConfigurationSection root = yaml.getConfigurationSection("listings");
		if (root == null) {
			return;
		}
		for (String key : root.getKeys(false)) {
			ConfigurationSection s = root.getConfigurationSection(key);
			try {
				ItemStack item = ItemStack.deserializeBytes(Base64.getDecoder().decode(s.getString("item", "")));
				Listing listing = new Listing(UUID.fromString(key), UUID.fromString(s.getString("seller")),
						s.getString("seller-name", "?"), item, price(s.getInt("price", MIN_PRICE)), s.getLong("created"));
				LISTINGS.put(listing.id, listing);
			} catch (RuntimeException e) {
				owner.getLogger().log(Level.WARNING, "Skipping a broken listing " + key, e);
			}
		}
	}

	/** A price read from the file: still in diamonds before the switch to money. */
	private static int price(int saved) {
		return Money.needsMigration() ? (int) Math.min(MAX_PRICE, saved * Money.DIAMOND_RATE) : saved;
	}

	/** On the first start with money: earnings still owed in diamonds go onto the sellers' balances. */
	static void migrate() {
		EARNINGS.forEach((seller, diamonds) -> Money.give(seller, diamonds * Money.DIAMOND_RATE, "접속하지 않은 동안 팔린 물품의 대금"));
		EARNINGS.clear();
		save();
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

	/** Sellers with at least one listing, other than {@code viewer}, by name. */
	static List<UUID> sellers(UUID viewer) {
		List<UUID> sellers = new ArrayList<>();
		for (Listing listing : LISTINGS.values()) {
			if (!listing.seller.equals(viewer) && !sellers.contains(listing.seller)) {
				sellers.add(listing.seller);
			}
		}
		sellers.sort(Comparator.comparing(id -> name(id).toLowerCase(java.util.Locale.ROOT)));
		return sellers;
	}

	/** A seller's current name, or the one saved with their listings. */
	static String name(UUID seller) {
		String name = Bukkit.getOfflinePlayer(seller).getName();
		if (name != null) {
			return name;
		}
		for (Listing listing : LISTINGS.values()) {
			if (listing.seller.equals(seller)) {
				return listing.sellerName;
			}
		}
		return "?";
	}

	/** Pays a seller: told at once if online, otherwise on their next join. */
	static void pay(UUID seller, int amount, Component what, String buyer) {
		Money.give(seller, amount, Component.text(buyer + "님이 ").append(what)
				.append(Component.text("을(를) 사서 " + Money.format(amount) + "을(를) 받았습니다.")).color(NamedTextColor.GREEN),
				buyer + "님이 산 물품의 대금");
	}

	/** Every listing, oldest first. */
	static List<Listing> all() {
		List<Listing> list = new ArrayList<>(LISTINGS.values());
		list.sort(Comparator.comparingLong(l -> l.createdAt));
		return list;
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
