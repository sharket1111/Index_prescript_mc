package io.github.sharket1111.indexprescript.paper;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Display;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Tremor (진동), as in Limbus Company: a potency and a count, kept in
 * {@link CombatState}. Tremor does nothing by itself and its count drops by
 * one each turn. A Tremor Burst (진동 폭발) deals stagger damage equal to the
 * potency, toward the same stagger threshold the weapons use, and uses up one
 * count. The burst plays the original's look: golden ripples spreading out
 * from the body in rings, a resonant boom, and "진동 폭발" with the potency
 * rising over the target.
 */
final class Tremor {
	private static final Color GOLD = Color.fromRGB(0xFFD860);
	private static final Color AMBER = Color.fromRGB(0xF09A28);
	private static final Color PALE = Color.fromRGB(0xFFF4C8);
	private static final TextColor TEXT = TextColor.color(0xFFD25A);
	private static final int RIPPLES = 3;
	private static final int RIPPLE_GAP = 2;
	private static final int RIPPLE_TICKS = 8;
	private static final int POPUP_TICKS = 26;

	private Tremor() {
	}

	/** Tremor {@code potency} on {@code target}. */
	static void inflict(LivingEntity target, int potency) {
		if (potency > 0 && target.isValid() && !target.isDead()) {
			CombatState.of(target).addTremor(potency, CaduceusListener.now());
			ripple(target, 0.6, 1, GOLD);
		}
	}

	/** Tremor count +{@code count} on {@code target}. */
	static void addCount(LivingEntity target, int count) {
		if (count > 0 && target.isValid() && !target.isDead()) {
			CombatState.of(target).addTremorCount(count, CaduceusListener.now());
			ripple(target, 0.5, 1, AMBER);
		}
	}

	/**
	 * Tremor Burst: stagger damage equal to the target's Tremor potency, and one
	 * count used up. Returns true if it staggered the target. Nothing happens
	 * on a target without Tremor.
	 */
	static boolean burst(LivingEntity target) {
		if (!target.isValid() || target.isDead()) {
			return false;
		}
		long now = CaduceusListener.now();
		CombatState state = CombatState.of(target);
		int potency = state.tremorPotency(now);
		if (potency <= 0) {
			return false;
		}
		state.consumeTremor(now);
		float threshold = (float) Math.max(10.0, maxHealth(target) * 0.5);
		boolean staggered = state.addStagger(potency, threshold, now);
		if (staggered) {
			Stagger.apply(target, now);
			Effects.staggered(target);
		} else {
			Stagger.gauge(target, state.staggerBuildUp(now) / threshold);
		}
		play(target, potency);
		return staggered;
	}

	// --- the look -------------------------------------------------------------------------

	private static void play(LivingEntity target, int potency) {
		World world = target.getWorld();
		Location body = target.getLocation().add(0, target.getHeight() * 0.55, 0);
		world.playSound(body, Sound.BLOCK_BELL_RESONATE, SoundCategory.PLAYERS, 1.4F, 0.7F);
		world.playSound(body, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 1.2F, 0.5F);
		world.playSound(body, Sound.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 0.35F, 1.9F);
		world.spawnParticle(Particle.END_ROD, body, 18, 0.2, 0.3, 0.2, 0.18);
		world.spawnParticle(Particle.DUST, body, 30, 0.25, 0.4, 0.25, 0.0, new Particle.DustOptions(PALE, 1.8F));
		// The ripples: rings spreading out round the body one after another, then along the ground.
		new BukkitRunnable() {
			int tick;

			@Override
			public void run() {
				if (!target.isValid() || tick > (RIPPLES - 1) * RIPPLE_GAP + RIPPLE_TICKS) {
					cancel();
					return;
				}
				Location centre = target.getLocation().add(0, target.getHeight() * 0.55, 0);
				for (int k = 0; k < RIPPLES; k++) {
					int age = tick - k * RIPPLE_GAP;
					if (age < 0 || age >= RIPPLE_TICKS) {
						continue;
					}
					double radius = 0.4 + age * (0.3 + potency * 0.015);
					ring(centre, radius, k % 2 == 0 ? GOLD : AMBER, 1.5F - age * 0.1F, false);
					if (age % 2 == 0) {
						ring(target.getLocation().add(0, 0.1, 0), radius * 1.2, AMBER, 1.3F, false);
					}
					if (k == 0 && age < 4) {
						ring(centre, radius * 0.8, PALE, 1.2F, true);
					}
				}
				tick++;
			}
		}.runTaskTimer(IndexPrescriptPlugin.instance(), 0L, 1L);
		popup(target, potency);
	}

	/** A ring of dust: flat round the body, or standing across it. */
	private static void ring(Location centre, double radius, Color colour, float size, boolean upright) {
		World world = centre.getWorld();
		int points = (int) Math.max(14, radius * 16);
		Particle.DustOptions dust = new Particle.DustOptions(colour, Math.max(0.6F, size));
		for (int i = 0; i < points; i++) {
			double angle = Math.PI * 2 * i / points;
			Location at = upright ? centre.clone().add(Math.cos(angle) * radius, Math.sin(angle) * radius, 0)
					: centre.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
			world.spawnParticle(Particle.DUST, at, 1, 0.0, 0.0, 0.0, 0.0, dust);
		}
	}

	/** A small swell of ripples for Tremor being inflicted. */
	private static void ripple(LivingEntity target, double radius, int count, Color colour) {
		Location centre = target.getLocation().add(0, target.getHeight() * 0.55, 0);
		for (int i = 0; i < count; i++) {
			ring(centre, radius + i * 0.3, colour, 1.0F, false);
		}
		target.getWorld().playSound(centre, Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.6F, 0.6F);
	}

	/** "진동 폭발" and the potency, rising over the target and fading. */
	private static void popup(LivingEntity target, int potency) {
		Location at = target.getLocation().add(0, target.getHeight() + 0.5, 0);
		TextDisplay text = at.getWorld().spawn(at, TextDisplay.class, display -> {
			display.setPersistent(false);
			display.setBillboard(Display.Billboard.CENTER);
			display.setSeeThrough(true);
			display.setShadowed(true);
			display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
			display.setTeleportDuration(2);
			display.text(Component.text("진동 폭발 ", TEXT).decorate(TextDecoration.BOLD)
					.append(Component.text(String.valueOf(potency), TextColor.color(0xFFF4C8)).decorate(TextDecoration.BOLD)));
			display.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(1.3F, 1.3F, 1.3F), new Quaternionf()));
		});
		new BukkitRunnable() {
			int tick;

			@Override
			public void run() {
				if (!text.isValid() || tick >= POPUP_TICKS) {
					text.remove();
					cancel();
					return;
				}
				text.teleport(text.getLocation().add(0, 0.035, 0));
				if (tick > POPUP_TICKS - 8) {
					text.setTextOpacity((byte) Math.max(26, 255 - (tick - (POPUP_TICKS - 8)) * 28));
				}
				tick++;
			}
		}.runTaskTimer(IndexPrescriptPlugin.instance(), 1L, 1L);
	}

	private static double maxHealth(LivingEntity entity) {
		AttributeInstance max = entity.getAttribute(Attribute.MAX_HEALTH);
		return max == null ? 20.0 : max.getValue();
	}
}
