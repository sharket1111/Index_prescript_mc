package io.github.sharket1111.indexprescript.paper;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

/**
 * The bosses' skill effects (Mirror Dungeon): each coin of a boss's skill plays
 * a move (MdModels: m_slash, m_thrust, m_smash...) and, in the boss's own
 * colours and particle, the effect that goes with it: an arc for a slash, a
 * line for a thrust, a ring on the ground for a smash or stomp, a burst for a
 * punch or kick, a muzzle flash and a streak for a shot, a pillar and falling
 * sparks for a cast. A little gathers at the boss as it winds up; the last coin
 * of a great skill ends in a larger blast.
 */
final class MdFx {
	private MdFx() {
	}

	private static Particle particle(String name) {
		try {
			return Particle.valueOf(name);
		} catch (IllegalArgumentException e) {
			return Particle.CRIT;
		}
	}

	private static Particle.DustOptions dust(int rgb, float size) {
		return new Particle.DustOptions(Color.fromRGB(rgb & 0xFFFFFF), size);
	}

	private static String kind(String clip) {
		return switch (clip) {
			case "m_slash", "m_backslash" -> "arc";
			case "m_sweep", "m_spin" -> "circle";
			case "m_thrust", "m_lowthrust" -> "line";
			case "m_smash", "m_stomp", "m_flourish" -> "ground";
			case "m_punch_r", "m_punch_l", "m_kick" -> "impact";
			case "m_shoot" -> "shot";
			case "m_cast", "m_cast2" -> "pillar";
			default -> "arc";
		};
	}

	/** Four ticks before the blow: the boss's colours gather at it (more, and a rising sound, before a great skill's last coin). */
	static void windup(MdModels.Fx fx, LivingEntity mob, LivingEntity victim, String clip, boolean great) {
		World w = mob.getWorld();
		Location at = mob.getLocation().add(0, mob.getHeight() * 0.6, 0);
		w.spawnParticle(Particle.DUST, at, great ? 30 : 8, 0.5, 0.6, 0.5, 0, dust(fx.color2(), 1.0F));
		w.spawnParticle(particle(fx.particle()), at, great ? 16 : 4, 0.4, 0.5, 0.4, 0.02);
		if (great) {
			double r = 2.6;
			for (int i = 0; i < 24; i++) {
				double a = Math.PI * 2 * i / 24;
				w.spawnParticle(Particle.DUST, mob.getLocation().add(Math.cos(a) * r, 0.15, Math.sin(a) * r), 1, 0, 0, 0, 0, dust(fx.color(), 1.6F));
			}
			w.playSound(at, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.HOSTILE, 0.8F, 1.4F);
		} else if (kind(clip).equals("pillar")) {
			w.playSound(at, Sound.ENTITY_ILLUSIONER_CAST_SPELL, SoundCategory.HOSTILE, 0.6F, 1.3F);
		}
	}

	/** The blow lands on {@code victim}. */
	static void hit(MdModels.Fx fx, LivingEntity mob, LivingEntity victim, String clip, boolean great, boolean heads) {
		World w = mob.getWorld();
		Location from = mob.getLocation().add(0, mob.getHeight() * 0.6, 0);
		Location to = victim.getLocation().add(0, victim.getHeight() * 0.5, 0);
		Vector dir = to.toVector().subtract(from.toVector());
		double dist = Math.max(0.5, dir.length());
		dir.normalize();
		Vector side = new Vector(-dir.getZ(), 0, dir.getX());
		Particle own = particle(fx.particle());
		Particle.DustOptions main = dust(fx.color(), 1.5F);
		Particle.DustOptions second = dust(fx.color2(), 1.1F);
		float pitch = heads ? 1.1F : 0.8F;
		switch (kind(clip)) {
			case "arc" -> {
				// A crescent across the victim, tilted one way or the other.
				double tilt = clip.equals("m_backslash") ? -0.7 : 0.7;
				for (int i = -8; i <= 8; i++) {
					double f = i / 8.0;
					Location p = to.clone().add(side.clone().multiply(f * 1.8)).add(0, f * tilt * 1.2, 0).subtract(dir.clone().multiply(0.4 + 0.5 * f * f));
					w.spawnParticle(Particle.DUST, p, 2, 0.05, 0.05, 0.05, 0, Math.abs(i) < 5 ? main : second);
				}
				w.spawnParticle(Particle.SWEEP_ATTACK, to, 1);
				w.spawnParticle(own, to, 6, 0.3, 0.3, 0.3, 0.05);
				w.playSound(to, Sound.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 0.9F, pitch);
			}
			case "circle" -> {
				// A ring swept round the boss at chest height.
				double r = Math.min(4.0, dist + 0.8);
				for (int i = 0; i < 32; i++) {
					double a = Math.PI * 2 * i / 32;
					w.spawnParticle(Particle.DUST, from.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r), 1, 0.03, 0.03, 0.03, 0, i % 4 == 0 ? second : main);
				}
				w.spawnParticle(Particle.SWEEP_ATTACK, to, 2, 0.4, 0.2, 0.4, 0);
				w.spawnParticle(own, to, 8, 0.4, 0.3, 0.4, 0.05);
				w.playSound(to, Sound.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 1.0F, pitch * 0.8F);
			}
			case "line" -> {
				// A streak through the victim and a little beyond.
				for (double d = 0.6; d < dist + 1.6; d += 0.25) {
					w.spawnParticle(Particle.DUST, from.clone().add(dir.clone().multiply(d)), 1, 0.02, 0.02, 0.02, 0, d > dist - 0.6 ? second : main);
				}
				w.spawnParticle(Particle.CRIT, to, 12, 0.2, 0.3, 0.2, 0.4);
				w.spawnParticle(own, to, 6, 0.2, 0.3, 0.2, 0.08);
				w.playSound(to, Sound.ITEM_TRIDENT_HIT, SoundCategory.HOSTILE, 0.9F, pitch);
			}
			case "ground" -> {
				ring(w, victim.getLocation(), 1.2, 3.0, fx, 3);
				w.spawnParticle(own, victim.getLocation().add(0, 0.3, 0), 14, 0.6, 0.2, 0.6, 0.08);
				w.spawnParticle(Particle.DUST, to, 16, 0.5, 0.6, 0.5, 0, main);
				w.playSound(to, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, SoundCategory.HOSTILE, 0.7F, pitch * 0.7F);
			}
			case "impact" -> {
				w.spawnParticle(Particle.DUST, to, 18, 0.25, 0.3, 0.25, 0, main);
				w.spawnParticle(Particle.DUST, to, 8, 0.5, 0.5, 0.5, 0, second);
				w.spawnParticle(Particle.CRIT, to, 10, 0.2, 0.2, 0.2, 0.5);
				w.spawnParticle(own, to, 5, 0.2, 0.2, 0.2, 0.06);
				w.playSound(to, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, SoundCategory.HOSTILE, 1.0F, pitch);
			}
			case "shot" -> {
				Location muzzle = from.clone().add(dir.clone().multiply(1.2));
				w.spawnParticle(Particle.FLASH, muzzle, 1, 0, 0, 0, 0, Color.fromRGB(fx.color2() & 0xFFFFFF));
				w.spawnParticle(Particle.SMOKE, muzzle, 8, 0.1, 0.1, 0.1, 0.03);
				for (double d = 1.2; d < dist + 4; d += 0.3) {
					w.spawnParticle(Particle.DUST, from.clone().add(dir.clone().multiply(d)), 1, 0, 0, 0, 0, d < dist ? second : main);
				}
				w.spawnParticle(own, to, 10, 0.2, 0.3, 0.2, 0.1);
				w.playSound(muzzle, Sound.ENTITY_FIREWORK_ROCKET_BLAST, SoundCategory.HOSTILE, 1.0F, 0.7F);
				w.playSound(muzzle, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 0.3F, 1.8F);
			}
			default -> {
				// A pillar on the victim and sparks falling into it.
				Location base = victim.getLocation();
				for (double y = 0; y < 4.2; y += 0.25) {
					w.spawnParticle(Particle.DUST, base.clone().add(0, y, 0), 2, 0.18, 0.05, 0.18, 0, y < 2.4 ? main : second);
				}
				w.spawnParticle(own, base.clone().add(0, 3.2, 0), 14, 0.5, 0.8, 0.5, 0.02);
				ring(w, base, 0.8, 1.6, fx, 2);
				w.playSound(to, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.HOSTILE, 1.0F, pitch);
				w.playSound(to, Sound.ENTITY_EVOKER_CAST_SPELL, SoundCategory.HOSTILE, 0.5F, 1.4F);
			}
		}
		if (great) {
			// A great skill's last coin: a wide ring, a column, a burst, a clap of thunder.
			Location base = victim.getLocation();
			ring(w, base, 1.5, 5.5, fx, 5);
			for (double y = 0; y < 6; y += 0.2) {
				w.spawnParticle(Particle.DUST, base.clone().add(0, y, 0), 3, 0.3, 0.05, 0.3, 0, dust(y % 0.8 < 0.4 ? fx.color() : fx.color2(), 2.0F));
			}
			w.spawnParticle(own, to, 60, 0.9, 1.0, 0.9, 0.18);
			w.spawnParticle(Particle.DUST, to, 50, 1.2, 1.0, 1.2, 0, dust(fx.color(), 2.2F));
			w.spawnParticle(Particle.EXPLOSION, to, 1);
			w.playSound(to, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.HOSTILE, 0.7F, 0.9F);
			w.playSound(to, Sound.ENTITY_WITHER_BREAK_BLOCK, SoundCategory.HOSTILE, 0.5F, 0.7F);
		}
	}

	/** A ring on the ground widening over a few ticks. */
	private static void ring(World w, Location centre, double from, double to, MdModels.Fx fx, int steps) {
		for (int s = 0; s < steps; s++) {
			double r = from + (to - from) * s / Math.max(1, steps - 1);
			int step = s;
			Bukkit.getScheduler().runTaskLater(IndexPrescriptPlugin.instance(), () -> {
				int n = (int) Math.max(12, r * 10);
				for (int i = 0; i < n; i++) {
					double a = Math.PI * 2 * i / n;
					w.spawnParticle(Particle.DUST, centre.clone().add(Math.cos(a) * r, 0.12, Math.sin(a) * r), 1, 0, 0, 0, 0, dust(step % 2 == 0 ? fx.color() : fx.color2(), 1.5F));
				}
			}, s * 2L);
		}
	}

	/** A boss arrives: its colours burst round it. */
	static void arrive(MdModels.Fx fx, LivingEntity mob) {
		World w = mob.getWorld();
		ring(w, mob.getLocation(), 1.0, 4.0, fx, 4);
		w.spawnParticle(particle(fx.particle()), mob.getLocation().add(0, mob.getHeight() * 0.5, 0), 40, 0.6, 0.9, 0.6, 0.1);
		w.playSound(mob.getLocation(), Sound.ENTITY_WARDEN_EMERGE, SoundCategory.HOSTILE, 0.6F, 1.4F);
	}
}
