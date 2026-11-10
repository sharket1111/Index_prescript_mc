package io.github.sharket1111.trade;

import java.io.File;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * The last known inventory and ender chest of each player
 * (inventories/<uuid>.yml), so the web page can show them while the player
 * is away. Saved when a player leaves and every five minutes while online;
 * read-only for the page.
 */
final class InventorySnapshots {
	private static final long EVERY_TICKS = 20L * 60 * 5;

	/** storage: 36 slots (0-8 hotbar), gear: helmet, chestplate, leggings, boots, off hand; ender: 27 slots. */
	record Snapshot(ItemStack[] storage, ItemStack[] gear, ItemStack[] ender, long time) {
	}

	private static Plugin plugin;
	private static File folder;
	private static final Map<UUID, Snapshot> CACHE = new ConcurrentHashMap<>();

	private InventorySnapshots() {
	}

	static void start(Plugin owner) {
		plugin = owner;
		folder = new File(owner.getDataFolder(), "inventories");
		owner.getServer().getPluginManager().registerEvents(new Events(), owner);
		Bukkit.getScheduler().runTaskTimer(owner, () -> Bukkit.getOnlinePlayers().forEach(InventorySnapshots::save), EVERY_TICKS, EVERY_TICKS);
	}

	/** Everyone online, now (on shutdown). */
	static void saveAll() {
		if (plugin != null) {
			Bukkit.getOnlinePlayers().forEach(player -> write(player.getUniqueId(), take(player)));
		}
	}

	/** What a player carries right now. */
	static Snapshot take(Player player) {
		ItemStack[] armor = player.getInventory().getArmorContents();
		ItemStack[] gear = {armor[3], armor[2], armor[1], armor[0], player.getInventory().getItemInOffHand()};
		return new Snapshot(copy(player.getInventory().getStorageContents()), copy(gear), copy(player.getEnderChest().getContents()),
				System.currentTimeMillis());
	}

	private static ItemStack[] copy(ItemStack[] items) {
		ItemStack[] out = new ItemStack[items.length];
		for (int i = 0; i < items.length; i++) {
			out[i] = items[i] == null || items[i].isEmpty() ? null : items[i].clone();
		}
		return out;
	}

	/** Takes a snapshot on the main thread and writes it off it. */
	static void save(Player player) {
		Snapshot snapshot = take(player);
		CACHE.put(player.getUniqueId(), snapshot);
		UUID id = player.getUniqueId();
		Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> write(id, snapshot));
	}

	private static synchronized void write(UUID id, Snapshot snapshot) {
		CACHE.put(id, snapshot);
		YamlConfiguration yaml = new YamlConfiguration();
		yaml.set("time", snapshot.time());
		put(yaml, "storage", snapshot.storage());
		put(yaml, "gear", snapshot.gear());
		put(yaml, "ender", snapshot.ender());
		try {
			folder.mkdirs();
			yaml.save(new File(folder, id + ".yml"));
		} catch (IOException e) {
			plugin.getLogger().log(Level.WARNING, "Could not save the inventory of " + id, e);
		}
	}

	private static void put(YamlConfiguration yaml, String path, ItemStack[] items) {
		yaml.set(path + ".size", items.length);
		for (int i = 0; i < items.length; i++) {
			if (items[i] != null) {
				yaml.set(path + "." + i, Base64.getEncoder().encodeToString(items[i].serializeAsBytes()));
			}
		}
	}

	private static ItemStack[] get(YamlConfiguration yaml, String path, int size) {
		ItemStack[] items = new ItemStack[yaml.getInt(path + ".size", size)];
		for (int i = 0; i < items.length; i++) {
			String data = yaml.getString(path + "." + i);
			if (data != null) {
				try {
					items[i] = ItemStack.deserializeBytes(Base64.getDecoder().decode(data));
				} catch (RuntimeException e) {
					// An item this version cannot read: shown as empty.
				}
			}
		}
		return items;
	}

	/** The last saved snapshot of a player, or null if there is none. */
	static Snapshot of(UUID id) {
		Snapshot cached = CACHE.get(id);
		if (cached != null) {
			return cached;
		}
		File file = new File(folder, id + ".yml");
		if (!file.exists()) {
			return null;
		}
		YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
		Snapshot snapshot = new Snapshot(get(yaml, "storage", 36), get(yaml, "gear", 5), get(yaml, "ender", 27), yaml.getLong("time"));
		CACHE.put(id, snapshot);
		return snapshot;
	}

	static final class Events implements Listener {
		@EventHandler
		public void onQuit(PlayerQuitEvent event) {
			save(event.getPlayer());
		}
	}
}
