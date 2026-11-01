package io.github.sharket1111.indexprescript.paper;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

/**
 * Sanity (SP, -45 to 45) is read from health: 5 hearts (10 health) is 0, full
 * health is 45 and no health is -45, linear in between on each side. For
 * creatures with 10 health or less, half their health is 0 instead. Changing
 * Sanity changes health to match.
 */
final class Sanity {
	static final int MIN = -45;
	static final int MAX = 45;
	private static final double ZERO_HEALTH = 10.0;

	private Sanity() {
	}

	private static double maxHealth(LivingEntity entity) {
		AttributeInstance attribute = entity.getAttribute(Attribute.MAX_HEALTH);
		return attribute == null ? 20.0 : attribute.getValue();
	}

	/** The health at which Sanity is 0: 5 hearts, or half of a smaller maximum. */
	private static double pivot(double max) {
		return max > ZERO_HEALTH * 2 ? ZERO_HEALTH : max / 2.0;
	}

	static int of(Entity entity) {
		if (!(entity instanceof LivingEntity living)) {
			return 0;
		}
		double max = maxHealth(living);
		double pivot = pivot(max);
		double health = living.getHealth();
		double sanity = health >= pivot ? (health - pivot) / (max - pivot) * MAX : (health - pivot) / pivot * -MIN;
		return (int) Math.round(Math.max(MIN, Math.min(MAX, sanity)));
	}

	/** The health that matches {@code sanity} for this creature. */
	static double healthFor(LivingEntity entity, int sanity) {
		double max = maxHealth(entity);
		double pivot = pivot(max);
		int clamped = Math.max(MIN, Math.min(MAX, sanity));
		return clamped >= 0 ? pivot + clamped / (double) MAX * (max - pivot) : pivot + clamped / (double) -MIN * pivot;
	}

	/** Sets health to match {@code sanity}, leaving at least half a heart. */
	static void set(LivingEntity entity, int sanity) {
		entity.setHealth(Math.max(1.0, Math.min(maxHealth(entity), healthFor(entity, sanity))));
	}

	/** Lowers Sanity, and so health, by {@code amount}; never below half a heart. */
	static void lose(LivingEntity entity, int amount) {
		double health = healthFor(entity, of(entity) - amount);
		entity.setHealth(Math.max(1.0, Math.min(entity.getHealth(), health)));
	}
}
