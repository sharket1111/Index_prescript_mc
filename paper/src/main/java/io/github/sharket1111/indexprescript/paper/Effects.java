package io.github.sharket1111.indexprescript.paper;

import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

/**
 * Liquid-metal visuals: black metal splashing as Caduceus solidifies, pale
 * blue glowing droplets, and the Furioso-Replica slashes, arcs and flashes.
 */
final class Effects {
	private static final Particle.DustOptions BLUE_DROPLET = dust(0x8FDCFF, 0.7F);
	private static final Particle.DustOptions PALE_DROPLET = dust(0xE6F6FF, 0.5F);
	private static final Particle.DustOptions WHITE_STREAK = dust(0xF4FAFF, 1.0F);
	private static final Particle.DustOptions AURA = dust(0x0B0B10, 1.4F);
	private static final Particle.DustOptions BLUE_ARC = dust(0x5AB4FF, 1.2F);
	private static final Particle.DustOptions BLUE_ARC_DIM = dust(0x2F5F9F, 0.9F);
	private static final double BLADE_REACH = 2.4;
	private static final int BLADE_STEPS = 8;
	private static final double SLASH_STEP = 0.18;
	private static final double ARC_RADIUS = 2.2;
	private static final double FINAL_CUT_LENGTH = 14.0;

	private Effects() {
	}

	private static Particle.DustOptions dust(int rgb, float size) {
		return new Particle.DustOptions(Color.fromRGB(rgb), size);
	}

	private static void spawn(World world, Particle particle, Vector p, int count, double spread, double speed) {
		world.spawnParticle(particle, p.getX(), p.getY(), p.getZ(), count, spread, spread, spread, speed);
	}

	private static void dustAt(World world, Particle.DustOptions dust, Vector p, int count, double spread) {
		world.spawnParticle(Particle.DUST, p.getX(), p.getY(), p.getZ(), count, spread, spread, spread, 0.0, dust);
	}

	private static Vector center(Entity entity) {
		return entity.getLocation().toVector().add(new Vector(0.0, entity.getHeight() * 0.5, 0.0));
	}

	private static void sound(Location at, Sound sound, float volume, float pitch) {
		at.getWorld().playSound(at, sound, SoundCategory.PLAYERS, volume, pitch);
	}

	/** Weapon solidifying: splash along the swing line plus scattered droplets. */
	static void form(LivingEntity holder) {
		World world = holder.getWorld();
		Vector look = holder.getEyeLocation().getDirection();
		Vector right = new Vector(-look.getZ(), 0.0, look.getX()).normalize();
		Vector hand = holder.getEyeLocation().toVector().add(look.clone().multiply(0.5)).add(right.multiply(0.35)).add(new Vector(0, -0.45, 0));
		for (int i = 0; i <= BLADE_STEPS; i++) {
			Vector p = hand.clone().add(look.clone().multiply(BLADE_REACH * i / BLADE_STEPS));
			spawn(world, Particle.SQUID_INK, p, 2, 0.12, 0.04);
			if (i % 2 == 0) {
				dustAt(world, BLUE_DROPLET, p, 2, 0.22);
			}
		}
		Vector mid = hand.clone().add(look.clone().multiply(BLADE_REACH * 0.5));
		spawn(world, Particle.GLOW_SQUID_INK, mid, 6, 0.45, 0.06);
		dustAt(world, PALE_DROPLET, mid, 10, 0.6);
		spawn(world, Particle.SQUID_INK, hand, 6, 0.1, 0.12);
		sound(holder.getLocation(), Sound.ITEM_BUCKET_FILL_LAVA, 0.6F, 1.6F);
		sound(holder.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5F, 1.8F);
	}

	static void impact(Entity target) {
		Vector c = center(target);
		spawn(target.getWorld(), Particle.SQUID_INK, c, 8, 0.3, 0.05);
		dustAt(target.getWorld(), BLUE_DROPLET, c, 6, 0.35);
	}

	static void crit(Entity target) {
		Vector c = center(target);
		spawn(target.getWorld(), Particle.CRIT, c, 14, 0.35, 0.25);
		spawn(target.getWorld(), Particle.GLOW_SQUID_INK, c, 3, 0.3, 0.05);
		sound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.8F, 1.3F);
	}

	static void melt(Entity holder) {
		Vector c = holder.getLocation().toVector().add(new Vector(0, holder.getHeight() * 0.55, 0));
		spawn(holder.getWorld(), Particle.SQUID_INK, c, 6, 0.2, 0.01);
		dustAt(holder.getWorld(), PALE_DROPLET, c, 4, 0.25);
	}

	static void staggered(LivingEntity target) {
		Vector c = target.getLocation().toVector().add(new Vector(0, target.getHeight(), 0));
		spawn(target.getWorld(), Particle.CRIT, c, 20, 0.4, 0.3);
		sound(target.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.6F, 1.4F);
	}

	static void sweep(Entity target, int count, double spread) {
		spawn(target.getWorld(), Particle.SWEEP_ATTACK, center(target), count, spread, 0.0);
	}

	/** Translucent black aura around a wielder with full [Hermes]. */
	static void aura(LivingEntity holder) {
		World world = holder.getWorld();
		ThreadLocalRandom random = ThreadLocalRandom.current();
		Location base = holder.getLocation();
		for (int i = 0; i < 4; i++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double radius = 0.7 + random.nextDouble() * 0.4;
			Vector p = new Vector(base.getX() + Math.cos(angle) * radius, base.getY() + random.nextDouble() * holder.getHeight(),
					base.getZ() + Math.sin(angle) * radius);
			dustAt(world, AURA, p, 1, 0.06);
		}
		spawn(world, Particle.SMOKE, center(holder), 1, 0.5, 0.005);
	}

	static void auraBurst(LivingEntity holder) {
		Vector c = center(holder);
		dustAt(holder.getWorld(), AURA, c, 40, 0.85);
		spawn(holder.getWorld(), Particle.SQUID_INK, c, 20, 0.55, 0.15);
		spawn(holder.getWorld(), Particle.GLOW_SQUID_INK, c, 8, 0.6, 0.1);
	}

	static void gather(Entity target) {
		Vector c = center(target);
		spawn(target.getWorld(), Particle.SQUID_INK, c, 30, 1.3, 0.0);
		dustAt(target.getWorld(), BLUE_DROPLET, c, 20, 1.1);
	}

	private static Vector facingRight(Entity attacker, Entity target) {
		Vector toward = target.getLocation().toVector().subtract(attacker.getLocation().toVector()).setY(0);
		if (toward.lengthSquared() < 1.0E-4) {
			toward = attacker.getLocation().getDirection().setY(0);
		}
		toward.normalize();
		return new Vector(-toward.getZ(), 0.0, toward.getX());
	}

	/** A long white line through the target, tilted from the horizontal in the plane facing the attacker. */
	static void slash(Entity attacker, Entity target, double angleDeg, double length) {
		World world = target.getWorld();
		Vector c = center(target);
		double a = Math.toRadians(angleDeg);
		Vector dir = facingRight(attacker, target).multiply(Math.cos(a)).add(new Vector(0, Math.sin(a), 0));
		for (double t = -length * 0.5; t <= length * 0.5; t += SLASH_STEP) {
			Vector p = c.clone().add(dir.clone().multiply(t));
			spawn(world, Particle.END_ROD, p, 1, 0.0, 0.0);
			dustAt(world, WHITE_STREAK, p, 1, 0.02);
		}
	}

	/** Sweeping blue arc around the target, the trail of a swing. */
	static void arc(Entity attacker, Entity target, double startDeg, double sweepDeg, double tiltDeg) {
		World world = target.getWorld();
		Vector c = center(target);
		Vector right = facingRight(attacker, target);
		Vector forward = new Vector(right.getZ(), 0.0, -right.getX());
		double tilt = Math.toRadians(tiltDeg);
		for (double d = 0; d <= sweepDeg; d += 5) {
			double r = Math.toRadians(startDeg + d);
			double fade = d / sweepDeg;
			Vector p = c.clone().add(right.clone().multiply(Math.cos(r) * ARC_RADIUS))
					.add(forward.clone().multiply(Math.sin(r) * ARC_RADIUS * 0.55))
					.add(new Vector(0, Math.cos(r) * Math.sin(tilt) * ARC_RADIUS * 0.6, 0));
			dustAt(world, fade > 0.5 ? BLUE_ARC : BLUE_ARC_DIM, p, 1, 0.03);
			if (fade > 0.75) {
				spawn(world, Particle.END_ROD, p, 1, 0.0, 0.0);
			}
		}
	}

	/** White star-shaped flash at the point of impact, with blue shards flying off. */
	static void star(Entity target, double size) {
		World world = target.getWorld();
		Vector c = center(target);
		double spin = ThreadLocalRandom.current().nextDouble() * Math.PI;
		for (int spoke = 0; spoke < 8; spoke++) {
			double a = spin + spoke * Math.PI / 4;
			double len = (spoke % 2 == 0 ? 1.0 : 0.5) * size;
			Vector dir = new Vector(Math.cos(a), Math.sin(a), Math.sin(a + spin) * 0.5).normalize();
			for (double t = 0.1; t <= len; t += 0.15) {
				dustAt(world, WHITE_STREAK, c.clone().add(dir.clone().multiply(t)), 1, 0.0);
			}
		}
		spawn(world, Particle.END_ROD, c, (int) (6 * size), 0.1, 0.12);
		dustAt(world, BLUE_DROPLET, c, (int) (10 * size), 0.5 * size);
		spawn(world, Particle.GLOW_SQUID_INK, c, 3, 0.3, 0.12);
	}

	static void dashTrail(World world, Vector from, Vector to) {
		Vector step = to.clone().subtract(from);
		double length = step.length();
		if (length < 0.1) {
			return;
		}
		Vector dir = step.multiply(1.0 / length);
		for (double t = 0; t <= length; t += 0.3) {
			Vector p = from.clone().add(dir.clone().multiply(t)).add(new Vector(0, 1.0, 0));
			spawn(world, Particle.SQUID_INK, p, 1, 0.15, 0.0);
			dustAt(world, BLUE_ARC_DIM, p, 1, 0.2);
		}
	}

	/** Final coin: blinding star, a scythe crescent, and one great diagonal line across the field. */
	static void finalCut(Entity attacker, Entity target) {
		star(target, 2.5);
		arc(attacker, target, -110, 220, 25);
		slash(attacker, target, -32, FINAL_CUT_LENGTH);
		Vector c = center(target);
		spawn(target.getWorld(), Particle.GLOW_SQUID_INK, c, 20, 1.1, 0.2);
		dustAt(target.getWorld(), BLUE_DROPLET, c, 40, 1.4);
		sound(target.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.8F, 0.7F);
		sound(target.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0F, 0.7F);
	}

	/** A bursting blow: an orange and blue explosion at the victim. */
	static void burst(Entity target) {
		World world = target.getWorld();
		Vector c = center(target);
		spawn(world, Particle.EXPLOSION, c, 2, 0.3, 0.0);
		spawn(world, Particle.FLAME, c, 25, 0.5, 0.12);
		dustAt(world, BLUE_DROPLET, c, 25, 0.9);
		dustAt(world, BLUE_ARC, c, 15, 0.6);
		sound(target.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.6F, 1.4F);
	}

	/** A flat blue ring around the victim with an orange blast at its middle. */
	static void ring(Entity target) {
		World world = target.getWorld();
		Location base = target.getLocation();
		double y = base.getY() + target.getHeight() * 0.5;
		for (int i = 0; i < 48; i++) {
			double a = i * Math.PI / 24;
			for (double radius = 1.8; radius <= 2.4; radius += 0.3) {
				dustAt(world, i % 6 == 0 ? WHITE_STREAK : BLUE_ARC,
						new Vector(base.getX() + Math.cos(a) * radius, y, base.getZ() + Math.sin(a) * radius), 1, 0.0);
			}
		}
		Vector c = center(target);
		spawn(world, Particle.EXPLOSION, c, 1, 0.0, 0.0);
		spawn(world, Particle.FLAME, c, 20, 0.4, 0.1);
		sound(base, Sound.ENTITY_GENERIC_EXPLODE, 0.5F, 1.2F);
	}

	/** Tilted blue rings orbiting the wielder during the final gathering; call every tick. */
	static void orbitRings(Entity holder, int tick) {
		World world = holder.getWorld();
		Vector c = center(holder);
		double[] tilts = {0.0, 0.6, -0.6};
		for (int ring = 0; ring < tilts.length; ring++) {
			double radius = 1.5 + ring * 0.35;
			double phase = tick * 0.4 * (ring % 2 == 0 ? 1 : -1);
			for (int i = 0; i < 4; i++) {
				double a = phase + i * Math.PI / 2;
				double x = Math.cos(a) * radius;
				double z = Math.sin(a) * radius;
				Vector p = c.clone().add(new Vector(x, z * Math.sin(tilts[ring]), z * Math.cos(tilts[ring])));
				dustAt(world, i == 0 ? WHITE_STREAK : BLUE_ARC, p, 1, 0.0);
			}
		}
	}

	/** A small star burst at a random point near the target. */
	static void flicker(Entity target, double spread) {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		World world = target.getWorld();
		Vector p = center(target).add(new Vector((random.nextDouble() - 0.5) * 2 * spread, (random.nextDouble() - 0.3) * spread,
				(random.nextDouble() - 0.5) * 2 * spread));
		for (int spoke = 0; spoke < 4; spoke++) {
			double a = spoke * Math.PI / 4 + random.nextDouble();
			Vector dir = new Vector(Math.cos(a), Math.sin(a), 0);
			for (double t = -0.6; t <= 0.6; t += 0.15) {
				dustAt(world, WHITE_STREAK, p.clone().add(dir.clone().multiply(t)), 1, 0.0);
			}
		}
		spawn(world, Particle.END_ROD, p, 2, 0.05, 0.05);
	}

	/** The blinding white flash before the last coin. */
	static void flash(Entity target) {
		World world = target.getWorld();
		Vector c = center(target);
		spawn(world, Particle.END_ROD, c, 80, 0.4, 0.45);
		dustAt(world, WHITE_STREAK, c, 80, 2.0);
		dustAt(world, PALE_DROPLET, c, 60, 2.8);
		sound(target.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.0F, 0.6F);
		sound(target.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0F, 1.6F);
	}

	private static final Particle.DustOptions SHIN_GOLD = dust(0xFFD24A, 1.0F);
	private static final Particle.DustOptions SHIN_PALE = dust(0xFFF2B0, 0.7F);
	private static final Particle.DustOptions MANG_GOLD = dust(0xFFE14D, 0.8F);

	/** [Shin]: a golden glow rising around the wielder; call every tick. */
	static void shin(LivingEntity holder, int tick) {
		if (tick % 2 != 0) {
			return;
		}
		World world = holder.getWorld();
		ThreadLocalRandom random = ThreadLocalRandom.current();
		Location base = holder.getLocation();
		for (int i = 0; i < 4; i++) {
			double a = random.nextDouble() * Math.PI * 2;
			double r = 0.45 + random.nextDouble() * 0.35;
			Vector p = new Vector(base.getX() + Math.cos(a) * r, base.getY() + random.nextDouble() * holder.getHeight() * 1.1,
					base.getZ() + Math.sin(a) * r);
			dustAt(world, i == 0 ? SHIN_PALE : SHIN_GOLD, p, 1, 0.02);
		}
		if (tick % 6 == 0) {
			spawn(world, Particle.END_ROD, center(holder), 1, 0.35, 0.02);
		}
		if (tick % 10 == 0) {
			for (int i = 0; i < 16; i++) {
				double a = i * Math.PI / 8;
				dustAt(world, SHIN_GOLD, new Vector(base.getX() + Math.cos(a) * 0.8, base.getY() + 0.05, base.getZ() + Math.sin(a) * 0.8), 1, 0.0);
			}
		}
	}

	/** [Shin] flaring up as the skill starts. */
	static void shinBurst(LivingEntity holder) {
		Vector c = center(holder);
		dustAt(holder.getWorld(), SHIN_GOLD, c, 40, 0.7);
		spawn(holder.getWorld(), Particle.END_ROD, c, 12, 0.3, 0.08);
		sound(holder.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.8F, 1.4F);
	}

	/** Where the held weapon's ring sits: along the blade, held up and forward from the right hand. */
	private static Vector[] weaponFrame(LivingEntity holder) {
		Location at = holder.getLocation();
		Vector forward = at.getDirection().setY(0);
		if (forward.lengthSquared() < 1.0E-4) {
			forward = new Vector(0, 0, 1);
		}
		forward.normalize();
		Vector right = new Vector(-forward.getZ(), 0, forward.getX());
		Vector hand = at.toVector().add(new Vector(0, holder.getHeight() * 0.5, 0)).add(right.clone().multiply(0.38)).add(forward.clone().multiply(0.25));
		Vector axis = forward.clone().add(new Vector(0, 1, 0)).normalize();
		return new Vector[] {hand.add(axis.clone().multiply(0.75)), axis};
	}

	/** [Mang]: a glowing golden ring wound round the weapon, slowly turning; call every tick. */
	static void mang(LivingEntity holder, int tick) {
		Vector[] frame = weaponFrame(holder);
		Vector c = frame[0];
		Vector axis = frame[1];
		Vector a = axis.clone().crossProduct(new Vector(0, 1, 0));
		if (a.lengthSquared() < 1.0E-4) {
			a = new Vector(1, 0, 0);
		}
		a.normalize();
		Vector b = axis.clone().crossProduct(a).normalize();
		World world = holder.getWorld();
		double spin = tick * 0.3;
		for (int i = 0; i < 14; i++) {
			double t = spin + i * Math.PI / 7;
			Vector p = c.clone().add(a.clone().multiply(Math.cos(t) * 0.32)).add(b.clone().multiply(Math.sin(t) * 0.32));
			dustAt(world, MANG_GOLD, p, 1, 0.0);
		}
		if (tick % 5 == 0) {
			spawn(world, Particle.END_ROD, c.clone().add(a.clone().multiply(Math.cos(spin) * 0.32)), 1, 0.0, 0.0);
		}
	}

	/** [Mang] forming: the ring flashes into being round the weapon. */
	static void mangForm(LivingEntity holder) {
		Vector c = weaponFrame(holder)[0];
		dustAt(holder.getWorld(), MANG_GOLD, c, 30, 0.35);
		spawn(holder.getWorld(), Particle.END_ROD, c, 8, 0.1, 0.06);
		sound(holder.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0F, 1.2F);
		sound(holder.getLocation(), Sound.BLOCK_BELL_RESONATE, 0.6F, 1.6F);
	}
}
