package io.github.sharket1111.trade;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Land: chunks players own, and protected zones set by admins. Saved to land.yml.
 * - An owned chunk lets its owner do anything; everyone else gets the
 *   permissions the owner gave them by name, or else the owner's defaults for
 *   everyone (none, unless changed). Anyone may walk in.
 * - In a protected zone only admins build, break or interact; anyone may walk in.
 * Chunks within {@link #SPAWN_RADIUS} chunks of chunk 0,0 cannot be bought.
 */
final class Land {
	enum Perm {
		BUILD("건축"),
		BREAK("파괴"),
		INTERACT("상호작용");

		final String label;

		Perm(String label) {
			this.label = label;
		}
	}

	static final int SPAWN_RADIUS = 10;
	static final int MAX_MEMBERS = 16;

	static final class Claim {
		final String key;
		final UUID owner;
		final String ownerName;
		final Map<UUID, EnumSet<Perm>> members = new LinkedHashMap<>();
		final Map<UUID, String> memberNames = new LinkedHashMap<>();
		EnumSet<Perm> everyone = EnumSet.noneOf(Perm.class);
		/** The owner's group the group settings below belong to, or null for none. */
		UUID group;
		/** Permissions for every member of the owner's group, or null to treat them like everyone. */
		EnumSet<Perm> groupAll;
		/** Permissions by role in the owner's group (before the whole-group setting). */
		final Map<String, EnumSet<Perm>> roles = new LinkedHashMap<>();

		Claim(String key, UUID owner, String ownerName) {
			this.key = key;
			this.owner = owner;
			this.ownerName = ownerName;
		}
	}

	private static Plugin plugin;
	private static NamespacedKey deedKey;
	private static File file;
	private static final Map<String, Claim> CLAIMS = new LinkedHashMap<>();
	private static final Set<String> ZONES = new LinkedHashSet<>();

	private Land() {
	}

	static String key(String world, int chunkX, int chunkZ) {
		return world + ";" + chunkX + ";" + chunkZ;
	}

	static String key(Location location) {
		return key(location.getWorld().getName(), location.getBlockX() >> 4, location.getBlockZ() >> 4);
	}

	static void load(Plugin owner) {
		plugin = owner;
		deedKey = new NamespacedKey(owner, "land_deed");
		file = new File(owner.getDataFolder(), "land.yml");
		CLAIMS.clear();
		ZONES.clear();
		if (!file.exists()) {
			return;
		}
		YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
		ZONES.addAll(yaml.getStringList("zones"));
		ConfigurationSection root = yaml.getConfigurationSection("claims");
		if (root == null) {
			return;
		}
		for (String key : root.getKeys(false)) {
			ConfigurationSection s = root.getConfigurationSection(key);
			try {
				Claim claim = new Claim(key, UUID.fromString(s.getString("owner")), s.getString("owner-name", "?"));
				claim.everyone = perms(s.getStringList("everyone"));
				ConfigurationSection members = s.getConfigurationSection("members");
				if (members != null) {
					for (String id : members.getKeys(false)) {
						UUID uuid = UUID.fromString(id);
						claim.members.put(uuid, perms(members.getStringList(id + ".perms")));
						claim.memberNames.put(uuid, members.getString(id + ".name", "?"));
					}
				}
				if (s.isString("group")) {
					claim.group = UUID.fromString(s.getString("group"));
					if (s.isList("group-all")) {
						claim.groupAll = perms(s.getStringList("group-all"));
					}
					ConfigurationSection roles = s.getConfigurationSection("roles");
					if (roles != null) {
						for (String role : roles.getKeys(false)) {
							claim.roles.put(role, perms(roles.getStringList(role)));
						}
					}
				}
				CLAIMS.put(key, claim);
			} catch (RuntimeException e) {
				owner.getLogger().log(Level.WARNING, "Skipping a broken land claim " + key, e);
			}
		}
	}

	private static EnumSet<Perm> perms(List<String> names) {
		EnumSet<Perm> set = EnumSet.noneOf(Perm.class);
		for (String name : names) {
			try {
				set.add(Perm.valueOf(name.toUpperCase(Locale.ROOT)));
			} catch (IllegalArgumentException ignored) {
				// unknown permission name
			}
		}
		return set;
	}

	private static List<String> names(Set<Perm> perms) {
		return perms.stream().map(Enum::name).toList();
	}

	static void save() {
		YamlConfiguration yaml = new YamlConfiguration();
		yaml.set("zones", new ArrayList<>(ZONES));
		for (Claim claim : CLAIMS.values()) {
			// Keys contain ';' only, which YAML paths accept; '.' would not be.
			String path = "claims." + claim.key;
			yaml.set(path + ".owner", claim.owner.toString());
			yaml.set(path + ".owner-name", claim.ownerName);
			yaml.set(path + ".everyone", names(claim.everyone));
			claim.members.forEach((id, perms) -> {
				yaml.set(path + ".members." + id + ".name", claim.memberNames.getOrDefault(id, "?"));
				yaml.set(path + ".members." + id + ".perms", names(perms));
			});
			if (claim.group != null) {
				yaml.set(path + ".group", claim.group.toString());
				if (claim.groupAll != null) {
					yaml.set(path + ".group-all", names(claim.groupAll));
				}
				claim.roles.forEach((role, perms) -> yaml.set(path + ".roles." + role, names(perms)));
			}
		}
		try {
			file.getParentFile().mkdirs();
			yaml.save(file);
		} catch (IOException e) {
			plugin.getLogger().log(Level.SEVERE, "Could not save land.yml", e);
		}
	}

	static Claim claimAt(Location location) {
		return CLAIMS.get(key(location));
	}

	static boolean zoneAt(Location location) {
		return ZONES.contains(key(location));
	}

	static boolean nearSpawn(Location location) {
		return Math.abs(location.getBlockX() >> 4) <= SPAWN_RADIUS && Math.abs(location.getBlockZ() >> 4) <= SPAWN_RADIUS;
	}

	static long claimsOf(UUID owner) {
		return CLAIMS.values().stream().filter(c -> c.owner.equals(owner)).count();
	}

	/** Why the chunk at {@code location} cannot be bought, or null if it can. */
	static String whyNotBuyable(Location location) {
		if (nearSpawn(location)) {
			return "스폰(0,0) 주변 " + SPAWN_RADIUS + "청크 안은 살 수 없습니다.";
		}
		if (zoneAt(location)) {
			return "이 청크는 보호구역입니다.";
		}
		Claim claim = claimAt(location);
		if (claim != null) {
			return "이 청크는 이미 " + claim.ownerName + "님의 땅입니다.";
		}
		return null;
	}

	/**
	 * The land deed shown for a chunk put up for sale: buying the listing
	 * hands the chunk over (the deed itself is never handed out).
	 */
	static ItemStack deed(Claim claim) {
		String[] parts = claim.key.split(";");
		ItemStack deed = ItemStack.of(Material.MAP);
		deed.editMeta(meta -> {
			meta.itemName(Component.text("땅 문서 (" + parts[0] + " 청크 " + parts[1] + ", " + parts[2] + ")", NamedTextColor.GREEN));
			int x = Integer.parseInt(parts[1]) * 16;
			int z = Integer.parseInt(parts[2]) * 16;
			meta.lore(List.of(
					TradeItems.plain("블록 좌표 X " + x + " ~ " + (x + 15) + ", Z " + z + " ~ " + (z + 15), NamedTextColor.GRAY),
					TradeItems.plain("사면 이 청크가 내 땅이 됩니다. (권한은 초기화)", NamedTextColor.GRAY)));
			meta.setEnchantmentGlintOverride(true);
			meta.getPersistentDataContainer().set(deedKey, PersistentDataType.STRING, claim.key);
		});
		return deed;
	}

	/** The chunk a deed stands for, or null if the item is not a deed. */
	static String deedChunk(ItemStack stack) {
		if (stack == null || !stack.hasItemMeta()) {
			return null;
		}
		return stack.getItemMeta().getPersistentDataContainer().get(deedKey, PersistentDataType.STRING);
	}

	static Claim claimByKey(String key) {
		return CLAIMS.get(key);
	}

	/** Hands a chunk to a new owner; permissions start over. */
	static void transfer(String key, Player newOwner) {
		CLAIMS.put(key, new Claim(key, newOwner.getUniqueId(), newOwner.getName()));
		save();
	}

	static void claim(Player owner, Location location) {
		CLAIMS.put(key(location), new Claim(key(location), owner.getUniqueId(), owner.getName()));
		save();
	}

	static boolean admin(Player player) {
		return player.hasPermission(AdminMenu.PERMISSION);
	}

	/** Whether {@code player} may do {@code perm} at {@code location}. */
	static boolean allowed(Player player, Location location, Perm perm) {
		if (admin(player)) {
			return true;
		}
		if (zoneAt(location)) {
			return false;
		}
		Claim claim = claimAt(location);
		if (claim == null || claim.owner.equals(player.getUniqueId())) {
			return true;
		}
		EnumSet<Perm> perms = claim.members.get(player.getUniqueId());
		if (perms == null) {
			perms = groupPerms(claim, player.getUniqueId());
		}
		return (perms != null ? perms : claim.everyone).contains(perm);
	}

	/** The owner's group, if the claim's group settings belong to it. */
	static Groups.Group ownerGroup(Claim claim) {
		Groups.Group group = Groups.of(claim.owner);
		return group != null && group.id.equals(claim.group) ? group : null;
	}

	/** What a fellow member of the owner's group may do: by their role, else as the whole group; null if neither is set. */
	private static EnumSet<Perm> groupPerms(Claim claim, UUID player) {
		Groups.Group group = ownerGroup(claim);
		if (group == null || Groups.of(player) != group) {
			return null;
		}
		EnumSet<Perm> byRole = claim.roles.get(group.roleOf(player).id);
		return byRole != null ? byRole : claim.groupAll;
	}

	/** Makes {@code claim}'s group settings belong to its owner's current group, starting over if it changed. */
	static void bindGroup(Claim claim) {
		Groups.Group group = Groups.of(claim.owner);
		UUID id = group == null ? null : group.id;
		if (id == null || !id.equals(claim.group)) {
			claim.group = id;
			claim.groupAll = null;
			claim.roles.clear();
		}
	}

	/** {@code player} joined or left a group: group settings on their land no longer apply. */
	static void groupChanged(UUID player) {
		boolean changed = false;
		for (Claim claim : CLAIMS.values()) {
			if (claim.owner.equals(player) && claim.group != null) {
				Groups.Group group = Groups.of(player);
				if (group == null || !group.id.equals(claim.group)) {
					claim.group = null;
					claim.groupAll = null;
					claim.roles.clear();
					changed = true;
				}
			}
		}
		if (changed) {
			save();
		}
	}

	/** A role was deleted: land settings for it go. */
	static void roleDeleted(Groups.Group group, String role) {
		boolean changed = false;
		for (Claim claim : CLAIMS.values()) {
			if (group.id.equals(claim.group) && claim.roles.remove(role) != null) {
				changed = true;
			}
		}
		if (changed) {
			save();
		}
	}

	/** Whether anything at {@code location} is protected from explosions and the like. */
	static boolean guarded(Location location) {
		return zoneAt(location) || claimAt(location) != null;
	}

	/** Sets or clears protected zones in a square of {@code radius} chunks around {@code center}; returns how many changed. */
	static int setZones(Location center, int radius, boolean protect) {
		String world = center.getWorld().getName();
		int cx = center.getBlockX() >> 4;
		int cz = center.getBlockZ() >> 4;
		int changed = 0;
		for (int x = cx - radius; x <= cx + radius; x++) {
			for (int z = cz - radius; z <= cz + radius; z++) {
				String key = key(world, x, z);
				if (protect && !CLAIMS.containsKey(key) && ZONES.add(key)) {
					changed++;
				} else if (!protect && ZONES.remove(key)) {
					changed++;
				}
			}
		}
		save();
		return changed;
	}

	static int zoneCount() {
		return ZONES.size();
	}

	/** Outlines the chunk at {@code location} for {@code viewer} for ten seconds. */
	static void showBorder(Player viewer, Location location, Color color) {
		int minX = (location.getBlockX() >> 4) << 4;
		int minZ = (location.getBlockZ() >> 4) << 4;
		Particle.DustOptions dust = new Particle.DustOptions(color, 1.5F);
		for (int second = 0; second < 10; second++) {
			Bukkit.getScheduler().runTaskLater(plugin, () -> {
				if (!viewer.isOnline() || viewer.getWorld() != location.getWorld()) {
					return;
				}
				double y = viewer.getLocation().getY() + 1.0;
				for (double t = 0; t <= 16; t += 0.5) {
					for (double dy = -1; dy <= 2; dy += 1.5) {
						viewer.spawnParticle(Particle.DUST, minX + t, y + dy, minZ, 1, dust);
						viewer.spawnParticle(Particle.DUST, minX + t, y + dy, minZ + 16, 1, dust);
						viewer.spawnParticle(Particle.DUST, minX, y + dy, minZ + t, 1, dust);
						viewer.spawnParticle(Particle.DUST, minX + 16, y + dy, minZ + t, 1, dust);
					}
				}
			}, second * 20L);
		}
	}
}
