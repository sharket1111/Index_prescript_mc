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

/**
 * The "buying" board: requests to buy an item, with the money offered held
 * by the plugin until someone fills the request or it is withdrawn. Saved to
 * orders.yml.
 */
final class BuyOrders {
	static final int MAX_PER_PLAYER = 10;
	static final int MAX_AMOUNT = 64 * 9;
	static final int MAX_PRICE = 100_000_000;

	static final class Order {
		final UUID id;
		final UUID buyer;
		final String buyerName;
		/** One of the wanted item; a match must be similar to it. */
		final ItemStack want;
		final int amount;
		final int price;
		final long createdAt;

		Order(UUID id, UUID buyer, String buyerName, ItemStack want, int amount, int price, long createdAt) {
			this.id = id;
			this.buyer = buyer;
			this.buyerName = buyerName;
			this.want = want;
			this.amount = amount;
			this.price = price;
			this.createdAt = createdAt;
		}

		boolean matches(ItemStack stack) {
			return stack != null && stack.isSimilar(want);
		}
	}

	private static Plugin plugin;
	private static File file;
	private static final Map<UUID, Order> ORDERS = new LinkedHashMap<>();

	private BuyOrders() {
	}

	static void load(Plugin owner) {
		plugin = owner;
		file = new File(owner.getDataFolder(), "orders.yml");
		ORDERS.clear();
		if (!file.exists()) {
			return;
		}
		ConfigurationSection root = YamlConfiguration.loadConfiguration(file).getConfigurationSection("orders");
		if (root == null) {
			return;
		}
		for (String key : root.getKeys(false)) {
			ConfigurationSection s = root.getConfigurationSection(key);
			try {
				Order order = new Order(UUID.fromString(key), UUID.fromString(s.getString("buyer")), s.getString("buyer-name", "?"),
						ItemStack.deserializeBytes(Base64.getDecoder().decode(s.getString("want", ""))), s.getInt("amount"),
						Money.needsMigration() ? (int) Math.min(MAX_PRICE, s.getInt("price") * Money.DIAMOND_RATE) : s.getInt("price"),
						s.getLong("created"));
				ORDERS.put(order.id, order);
			} catch (RuntimeException e) {
				owner.getLogger().log(Level.WARNING, "Skipping a broken buy request " + key, e);
			}
		}
	}

	static void save() {
		YamlConfiguration yaml = new YamlConfiguration();
		for (Order order : ORDERS.values()) {
			String path = "orders." + order.id;
			yaml.set(path + ".buyer", order.buyer.toString());
			yaml.set(path + ".buyer-name", order.buyerName);
			yaml.set(path + ".want", Base64.getEncoder().encodeToString(order.want.serializeAsBytes()));
			yaml.set(path + ".amount", order.amount);
			yaml.set(path + ".price", order.price);
			yaml.set(path + ".created", order.createdAt);
		}
		try {
			file.getParentFile().mkdirs();
			yaml.save(file);
		} catch (IOException e) {
			plugin.getLogger().log(Level.SEVERE, "Could not save orders.yml", e);
		}
	}

	/** Every request, newest first. */
	static List<Order> all() {
		List<Order> list = new ArrayList<>(ORDERS.values());
		list.sort(Comparator.comparingLong((Order o) -> o.createdAt).reversed());
		return list;
	}

	static List<Order> of(UUID buyer) {
		return all().stream().filter(o -> o.buyer.equals(buyer)).toList();
	}

	static boolean exists(Order order) {
		return ORDERS.containsKey(order.id);
	}

	static void add(Order order) {
		ORDERS.put(order.id, order);
		save();
	}

	static void remove(Order order) {
		ORDERS.remove(order.id);
		save();
	}
}
