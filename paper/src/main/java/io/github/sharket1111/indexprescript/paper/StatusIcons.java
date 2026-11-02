package io.github.sharket1111.indexprescript.paper;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Shows plugin statuses in the vanilla effect HUD by borrowing a harmless
 * vanilla effect that the resource pack renames and re-icons: Luck stands for
 * Poise, its level the Poise potency and its time how long the Poise count
 * lasts. (Luck only touches loot tables; Bedrock has no Luck, so Bedrock
 * players do not see it.)
 */
final class StatusIcons {
	private static final int INTERVAL = 5;
	/** Refresh the shown time only when it drifts by more than this. */
	private static final int TIME_SLACK = 15;

	private StatusIcons() {
	}

	/** Called every tick. */
	static void tick() {
		long now = CaduceusListener.now();
		if (now % INTERVAL != 0) {
			return;
		}
		for (Player player : Bukkit.getOnlinePlayers()) {
			showPoise(player, now);
		}
	}

	private static void showPoise(Player player, long now) {
		CombatState state = CombatState.of(player);
		int potency = state.poisePotency(now);
		int ticks = state.poiseTicksLeft(now);
		PotionEffect shown = player.getPotionEffect(PotionEffectType.LUCK);
		if (potency <= 0 || ticks <= 0) {
			// Ours has no particles; this also clears one left over from before a restart.
			if (shown != null && !shown.hasParticles() && !shown.isAmbient()) {
				player.removePotionEffect(PotionEffectType.LUCK);
			}
			return;
		}
		if (shown != null && shown.getAmplifier() == potency - 1 && Math.abs(shown.getDuration() - ticks) <= TIME_SLACK) {
			return;
		}
		player.addPotionEffect(new PotionEffect(PotionEffectType.LUCK, ticks, potency - 1, false, false, true));
	}
}
