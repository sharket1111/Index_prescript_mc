package io.github.sharket1111.indexprescript.paper;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * Sanity (SP), -45 to 45, starting at 0. Only players have it; everything else
 * counts as 0. Rien gains it by completing prescripts (+5) and slaying foes
 * with Caduceus (+3); Furioso-Replica's [Mang] costs 20. It resets on death.
 */
final class Sanity {
	static final int MIN = -45;
	static final int MAX = 45;
	private static final Map<UUID, Integer> VALUES = new ConcurrentHashMap<>();

	private Sanity() {
	}

	static int of(Entity entity) {
		return entity instanceof Player ? VALUES.getOrDefault(entity.getUniqueId(), 0) : 0;
	}

	static void add(Player player, int amount) {
		set(player, of(player) + amount);
	}

	static void set(Player player, int value) {
		VALUES.put(player.getUniqueId(), Math.max(MIN, Math.min(MAX, value)));
	}

	static void reset(Player player) {
		VALUES.remove(player.getUniqueId());
	}
}
