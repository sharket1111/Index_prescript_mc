package io.github.sharket1111.indexprescript.paper;

import org.bukkit.entity.LivingEntity;

/** Extra damage dealt by the plugin itself, kept out of the Caduceus damage handler. */
final class Damage {
	private static boolean dealing;

	private Damage() {
	}

	/** True while the plugin is dealing its own damage (the resulting events must not be rescaled). */
	static boolean dealing() {
		return dealing;
	}

	static void deal(LivingEntity target, double amount, LivingEntity source, boolean ignoreHurtCooldown) {
		if (ignoreHurtCooldown) {
			target.setNoDamageTicks(0);
		}
		dealing = true;
		try {
			target.damage(amount, source);
		} finally {
			dealing = false;
		}
	}
}
