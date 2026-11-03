package io.github.sharket1111.trade;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * The server's shared storage: {@link #SIZE} slots anyone can put items in,
 * saved to storage.yml. Each stored item remembers who put it in and who
 * else may take it out: everyone, chosen players, members of a group, or
 * members of a group from a role up. The one who put it in (and admins) can
 * always take it and change who may.
 */
final class SharedStorage {
	static final int SIZE = 54;
	static final int MAX_RULES = 6;
	static final String EVERYONE = "everyone";

	static final class Entry {
		final ItemStack item;
		final UUID owner;
		final String ownerName;
		/** "everyone", "player:&lt;id&gt;", "group:&lt;id&gt;" or "role:&lt;group id&gt;:&lt;role id&gt;". */
		final List<String> rules = new ArrayList<>();

		Entry(ItemStack item, UUID owner, String ownerName) {
			this.item = item;
			this.owner = owner;
			this.ownerName = ownerName;
		}

		boolean everyone() {
			return rules.contains(EVERYONE);
		}
	}

	private static Plugin plugin;
	private static File file;
	private static final Entry[] SLOTS = new Entry[SIZE];

	private SharedStorage() {
	}

	static Entry get(int slot) {
		return slot >= 0 && slot < SIZE ? SLOTS[slot] : null;
	}

	/** Puts an item in the first free slot; returns the slot, or -1 if full. */
	static int put(ItemStack item, Player owner) {
		for (int slot = 0; slot < SIZE; slot++) {
			if (SLOTS[slot] == null) {
				SLOTS[slot] = new Entry(item.clone(), owner.getUniqueId(), owner.getName());
				save();
				return slot;
			}
		}
		return -1;
	}

	static void clear(int slot) {
		SLOTS[slot] = null;
		save();
	}

	/** Whether {@code player} may take the item out (and, if {@code manage}, change who may). */
	static boolean may(Player player, Entry entry, boolean manage) {
		if (entry.owner.equals(player.getUniqueId()) || player.hasPermission(AdminMenu.PERMISSION)) {
			return true;
		}
		if (manage) {
			return false;
		}
		UUID me = player.getUniqueId();
		for (String rule : entry.rules) {
			String[] parts = rule.split(":");
			try {
				switch (parts[0]) {
					case EVERYONE -> {
						return true;
					}
					case "player" -> {
						if (UUID.fromString(parts[1]).equals(me)) {
							return true;
						}
					}
					case "group" -> {
						if (Groups.member(UUID.fromString(parts[1]), me) != null) {
							return true;
						}
					}
					case "role" -> {
						Groups.Group group = Groups.member(UUID.fromString(parts[1]), me);
						Groups.Role role = group == null ? null : group.role(parts[2]);
						if (role != null && group.rankOf(me) <= group.rank(role)) {
							return true;
						}
					}
					default -> {
					}
				}
			} catch (RuntimeException ignored) {
				// a rule naming something malformed
			}
		}
		return false;
	}

	/** A rule in words: who it lets take the item. */
	static String describe(String rule) {
		String[] parts = rule.split(":");
		try {
			return switch (parts[0]) {
				case EVERYONE -> "모든 사람";
				case "player" -> {
					String name = Bukkit.getOfflinePlayer(UUID.fromString(parts[1])).getName();
					yield "플레이어 " + (name == null ? "?" : name);
				}
				case "group" -> {
					Groups.Group group = Groups.byId(UUID.fromString(parts[1]));
					yield "그룹 " + (group == null ? "(없어진 그룹)" : group.name) + " 전체";
				}
				case "role" -> {
					Groups.Group group = Groups.byId(UUID.fromString(parts[1]));
					Groups.Role role = group == null ? null : group.role(parts[2]);
					yield group == null || role == null ? "(없어진 역할)" : "그룹 " + group.name + "의 " + role.name + " 이상";
				}
				default -> rule;
			};
		} catch (RuntimeException e) {
			return rule;
		}
	}

	static void load(Plugin owner) {
		plugin = owner;
		file = new File(owner.getDataFolder(), "storage.yml");
		java.util.Arrays.fill(SLOTS, null);
		if (!file.exists()) {
			return;
		}
		YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
		ConfigurationSection root = yaml.getConfigurationSection("slots");
		if (root == null) {
			return;
		}
		for (String key : root.getKeys(false)) {
			ConfigurationSection s = root.getConfigurationSection(key);
			try {
				int slot = Integer.parseInt(key);
				if (slot < 0 || slot >= SIZE) {
					continue;
				}
				ItemStack item = ItemStack.deserializeBytes(Base64.getDecoder().decode(s.getString("item", "")));
				Entry entry = new Entry(item, UUID.fromString(s.getString("owner")), s.getString("owner-name", "?"));
				entry.rules.addAll(s.getStringList("rules"));
				SLOTS[slot] = entry;
			} catch (RuntimeException e) {
				owner.getLogger().log(Level.WARNING, "Skipping a broken storage slot " + key, e);
			}
		}
	}

	static void save() {
		YamlConfiguration yaml = new YamlConfiguration();
		for (int slot = 0; slot < SIZE; slot++) {
			Entry entry = SLOTS[slot];
			if (entry == null) {
				continue;
			}
			String path = "slots." + slot;
			yaml.set(path + ".item", Base64.getEncoder().encodeToString(entry.item.serializeAsBytes()));
			yaml.set(path + ".owner", entry.owner.toString());
			yaml.set(path + ".owner-name", entry.ownerName);
			yaml.set(path + ".rules", entry.rules);
		}
		try {
			file.getParentFile().mkdirs();
			yaml.save(file);
		} catch (IOException e) {
			plugin.getLogger().log(Level.SEVERE, "Could not save storage.yml", e);
		}
	}
}
