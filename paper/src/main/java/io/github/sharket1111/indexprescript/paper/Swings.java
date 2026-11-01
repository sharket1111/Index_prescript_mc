package io.github.sharket1111.indexprescript.paper;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;

/**
 * Decides whether an attack counts as a full swing. Java players have an
 * attack cooldown, but Bedrock players joining through Geyser do not: their
 * cooldown reads as nearly empty on every hit. So a swing also counts when
 * enough time has passed since the last counted one, which keeps spam
 * clicking from stacking effects on either edition.
 */
final class Swings {
	private static final float FULL_COOLDOWN = 0.9F;
	private static final int MIN_INTERVAL_TICKS = 10;
	private static final Map<UUID, Long> LAST = new ConcurrentHashMap<>();

	private Swings() {
	}

	/** Call once per attack; returns true (and starts a new interval) if it counts as a full swing. */
	static boolean accept(Player player) {
		long now = CaduceusListener.now();
		Long last = LAST.get(player.getUniqueId());
		boolean full = player.getAttackCooldown() >= FULL_COOLDOWN || last == null || now - last >= MIN_INTERVAL_TICKS;
		if (full) {
			LAST.put(player.getUniqueId(), now);
		}
		return full;
	}
}
