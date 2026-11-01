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
 * Laevatein's release visuals, after the in-game animations: purple lightning
 * and aura, grey sweeping arcs, the casing shattering in a white burst, and
 * burning crescent slashes ending in fiery explosions.
 */
final class LaevateinEffects {
	private static final Particle.DustOptions PURPLE = dust(0xB040FF, 1.1F);
	private static final Particle.DustOptions PURPLE_DIM = dust(0x5A1E8C, 1.3F);
	private static final Particle.DustOptions GREY = dust(0xC8C8D2, 1.2F);
	private static final Particle.DustOptions WHITE = dust(0xFFFFFF, 1.3F);
	private static final Particle.DustOptions ORANGE = dust(0xFF8A1E, 1.4F);
	private static final Particle.DustOptions RED = dust(0xE0301E, 1.5F);
	private static final Particle.DustOptions GOLD = dust(0xFFD24A, 0.8F);
	private static final double ARC_RADIUS = 2.3;

	private LaevateinEffects() {
	}

	private static Particle.DustOptions dust(int rgb, float size) {
		return new Particle.DustOptions(Color.fromRGB(rgb), size);
	}

	private static void dustAt(World world, Particle.DustOptions dust, Vector p, int count, double spread) {
		world.spawnParticle(Particle.DUST, p.getX(), p.getY(), p.getZ(), count, spread, spread, spread, 0.0, dust);
	}

	private static void spawn(World world, Particle particle, Vector p, int count, double spread, double speed) {
		world.spawnParticle(particle, p.getX(), p.getY(), p.getZ(), count, spread, spread, spread, speed);
	}

	private static Vector center(Entity entity) {
		return entity.getLocation().toVector().add(new Vector(0, entity.getHeight() * 0.5, 0));
	}

	private static void sound(Location at, Sound sound, float volume, float pitch) {
		at.getWorld().playSound(at, sound, SoundCategory.PLAYERS, volume, pitch);
	}

	private static Vector facingRight(Entity attacker, Entity target) {
		Vector toward = target.getLocation().toVector().subtract(attacker.getLocation().toVector()).setY(0);
		if (toward.lengthSquared() < 1.0E-4) {
			toward = attacker.getLocation().getDirection().setY(0);
		}
		toward.normalize();
		return new Vector(-toward.getZ(), 0.0, toward.getX());
	}

	/** Small embers at the blade once a release is ready; called periodically. */
	static void ready(LivingEntity holder) {
		Vector look = holder.getEyeLocation().getDirection();
		Vector right = new Vector(-look.getZ(), 0, look.getX()).normalize();
		Vector blade = holder.getEyeLocation().toVector().add(look.multiply(0.9)).add(right.multiply(0.4)).add(new Vector(0, -0.35, 0));
		spawn(holder.getWorld(), Particle.FLAME, blade, 1, 0.12, 0.01);
		spawn(holder.getWorld(), Particle.SMOKE, blade, 1, 0.1, 0.01);
	}

	static void readySound(LivingEntity holder) {
		sound(holder.getLocation(), Sound.ITEM_FIRECHARGE_USE, 0.7F, 0.7F);
	}

	/** Purple aura and crackling lightning rising around the wielder. */
	static void aura(LivingEntity holder, int bolts) {
		World world = holder.getWorld();
		ThreadLocalRandom random = ThreadLocalRandom.current();
		Location base = holder.getLocation();
		for (int b = 0; b < bolts; b++) {
			double angle = random.nextDouble() * Math.PI * 2;
			Vector p = new Vector(base.getX() + Math.cos(angle) * 0.8, base.getY(), base.getZ() + Math.sin(angle) * 0.8);
			for (int i = 0; i < 8; i++) {
				p.add(new Vector((random.nextDouble() - 0.5) * 0.35, 0.28, (random.nextDouble() - 0.5) * 0.35));
				dustAt(world, PURPLE, p, 1, 0.0);
			}
			spawn(world, Particle.ELECTRIC_SPARK, p, 2, 0.1, 0.05);
		}
		dustAt(world, PURPLE_DIM, center(holder), 6, 0.7);
		spawn(world, Particle.WITCH, center(holder), 3, 0.6, 0.0);
	}

	static void auraSound(LivingEntity holder) {
		sound(holder.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.3F, 1.8F);
	}

	/** A purple bolt dropping onto the target. */
	static void lightningStrike(Entity target) {
		World world = target.getWorld();
		ThreadLocalRandom random = ThreadLocalRandom.current();
		Vector c = center(target);
		Vector p = c.clone().add(new Vector(0, 7, 0));
		while (p.getY() > c.getY()) {
			p.add(new Vector((random.nextDouble() - 0.5) * 0.6, -0.3, (random.nextDouble() - 0.5) * 0.6));
			dustAt(world, PURPLE, p, 1, 0.02);
			spawn(world, Particle.END_ROD, p, 1, 0.0, 0.0);
		}
		spawn(world, Particle.ELECTRIC_SPARK, c, 20, 0.5, 0.3);
		dustAt(world, PURPLE_DIM, c, 15, 0.8);
		sound(target.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.6F, 1.4F);
	}

	/** Sweeping arc around the target in the given colour. */
	private static void arc(Entity attacker, Entity target, double startDeg, double sweepDeg, double tiltDeg,
			Particle.DustOptions main, Particle.DustOptions accent, Particle extra) {
		World world = target.getWorld();
		Vector c = center(target);
		Vector right = facingRight(attacker, target);
		Vector forward = new Vector(right.getZ(), 0.0, -right.getX());
		double tilt = Math.toRadians(tiltDeg);
		int i = 0;
		for (double d = 0; d <= sweepDeg; d += 4, i++) {
			double r = Math.toRadians(startDeg + d);
			Vector p = c.clone().add(right.clone().multiply(Math.cos(r) * ARC_RADIUS))
					.add(forward.clone().multiply(Math.sin(r) * ARC_RADIUS * 0.55))
					.add(new Vector(0, Math.cos(r) * Math.sin(tilt) * ARC_RADIUS * 0.6, 0));
			dustAt(world, i % 4 == 0 && accent != null ? accent : main, p, 1, 0.04);
			if (extra != null && i % 2 == 0) {
				spawn(world, extra, p, 1, 0.03, 0.01);
			}
		}
	}

	static void greyArc(Entity attacker, Entity target, double startDeg, double tiltDeg) {
		arc(attacker, target, startDeg, 220, tiltDeg, GREY, WHITE, null);
		sound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0F, 0.7F);
	}

	/** Burning crescent slash with purple patterning. */
	static void flameArc(Entity attacker, Entity target, double startDeg, double tiltDeg) {
		arc(attacker, target, startDeg, 200, tiltDeg, ORANGE, PURPLE, Particle.FLAME);
		spawn(target.getWorld(), Particle.LAVA, center(target), 3, 0.4, 0.0);
		sound(target.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 0.9F, 0.8F);
		sound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0F, 0.6F);
	}

	/** The casing breaking open in a white burst of shards. */
	static void shatter(Entity target) {
		World world = target.getWorld();
		Vector c = center(target);
		dustAt(world, WHITE, c, 60, 1.4);
		dustAt(world, GREY, c, 30, 1.0);
		spawn(world, Particle.END_ROD, c, 25, 0.3, 0.35);
		spawn(world, Particle.CLOUD, c, 15, 0.6, 0.08);
		sound(target.getLocation(), Sound.BLOCK_CHAIN_BREAK, 1.0F, 0.6F);
		sound(target.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.0F, 0.6F);
	}

	static void explosion(Entity target, double size) {
		World world = target.getWorld();
		Vector c = center(target);
		spawn(world, Particle.EXPLOSION, c, (int) (3 * size), 0.6 * size, 0.0);
		spawn(world, Particle.FLAME, c, (int) (25 * size), 0.5 * size, 0.12);
		dustAt(world, RED, c, (int) (15 * size), 0.7 * size);
		sound(target.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, (float) Math.min(1.0, 0.5 * size), 1.0F);
	}

	/** Final release: an enormous fire burst. */
	static void inferno(Entity target) {
		World world = target.getWorld();
		Vector c = center(target);
		spawn(world, Particle.EXPLOSION_EMITTER, c, 1, 0.0, 0.0);
		spawn(world, Particle.FLAME, c, 140, 1.2, 0.28);
		spawn(world, Particle.LAVA, c, 30, 1.0, 0.0);
		dustAt(world, ORANGE, c, 60, 2.0);
		dustAt(world, RED, c, 40, 1.6);
		dustAt(world, PURPLE_DIM, c, 30, 2.2);
		sound(target.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0F, 0.7F);
		sound(target.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.8F, 0.8F);
	}

	/** Full [Middle - Grudge]: dark red and purple embers swirling around the wielder. */
	static void grudgeAura(LivingEntity holder) {
		World world = holder.getWorld();
		ThreadLocalRandom random = ThreadLocalRandom.current();
		Location base = holder.getLocation();
		for (int i = 0; i < 3; i++) {
			double angle = random.nextDouble() * Math.PI * 2;
			Vector p = new Vector(base.getX() + Math.cos(angle) * 0.7, base.getY() + random.nextDouble() * holder.getHeight(),
					base.getZ() + Math.sin(angle) * 0.7);
			dustAt(world, i == 0 ? PURPLE : RED, p, 1, 0.03);
		}
	}

	static void grudgeFull(LivingEntity holder) {
		dustAt(holder.getWorld(), RED, center(holder), 25, 0.6);
		dustAt(holder.getWorld(), PURPLE, center(holder), 15, 0.7);
		sound(holder.getLocation(), Sound.ITEM_FIRECHARGE_USE, 0.9F, 0.5F);
		sound(holder.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.3F, 0.6F);
	}

	/** [Super Grit]: golden glints around the wielder. */
	static void grit(LivingEntity holder) {
		dustAt(holder.getWorld(), GOLD, center(holder), 3, 0.5);
	}

	/** Powerful attack at full Grudge. */
	static void powerful(LivingEntity attacker, Entity target) {
		flameArc(attacker, target, -100, 30);
		explosion(target, 1.6);
	}

	private static final Particle.DustOptions PINK = dust(0xFF4FA8, 1.4F);
	private static final Particle.DustOptions GOLD_LINE = dust(0xFFD84A, 1.1F);
	private static final Particle.DustOptions SHADOW = dust(0x111014, 1.8F);

	/** Stomp: a dust shockwave ring on the ground around the target. */
	static void stomp(Entity target, double radius) {
		World world = target.getWorld();
		Location base = target.getLocation();
		org.bukkit.block.data.BlockData ground = base.clone().subtract(0, 0.5, 0).getBlock().getBlockData();
		for (int i = 0; i < 24; i++) {
			double a = i * Math.PI / 12;
			Vector p = new Vector(base.getX() + Math.cos(a) * radius, base.getY() + 0.1, base.getZ() + Math.sin(a) * radius);
			spawn(world, Particle.CLOUD, p, 1, 0.05, 0.02);
			if (!ground.getMaterial().isAir()) {
				world.spawnParticle(Particle.BLOCK, p.getX(), p.getY(), p.getZ(), 3, 0.1, 0.05, 0.1, 0.0, ground);
			}
		}
		dustAt(world, GREY, center(target), 12, 0.6);
		spawn(world, Particle.ELECTRIC_SPARK, center(target), 8, 0.4, 0.2);
		sound(base, Sound.ENTITY_GENERIC_EXPLODE, 0.5F, 1.5F);
		sound(base, Sound.BLOCK_ANVIL_LAND, 0.6F, 0.6F);
	}

	/** A straight golden thrust from the wielder through the target. */
	static void thrust(Entity attacker, Entity target) {
		World world = target.getWorld();
		Vector from = center(attacker);
		Vector dir = center(target).subtract(from);
		double length = dir.length() + 2.5;
		dir.normalize();
		for (double t = 0.6; t <= length; t += 0.2) {
			Vector p = from.clone().add(dir.clone().multiply(t));
			dustAt(world, GOLD_LINE, p, 1, 0.01);
			if (((int) (t * 5)) % 3 == 0) {
				spawn(world, Particle.END_ROD, p, 1, 0.0, 0.0);
			}
		}
		// a ring at the point of impact
		Vector c = center(target);
		Vector up = Math.abs(dir.getY()) < 0.9 ? new Vector(0, 1, 0) : new Vector(1, 0, 0);
		Vector a = dir.clone().crossProduct(up).normalize();
		Vector b = dir.clone().crossProduct(a).normalize();
		for (int i = 0; i < 16; i++) {
			double r = i * Math.PI / 8;
			dustAt(world, GOLD_LINE, c.clone().add(a.clone().multiply(Math.cos(r) * 0.7)).add(b.clone().multiply(Math.sin(r) * 0.7)), 1, 0.0);
		}
		sound(target.getLocation(), Sound.ITEM_TRIDENT_HIT, 1.0F, 0.8F);
	}

	/** Pink-and-purple burning crescent. */
	static void pinkArc(Entity attacker, Entity target, double startDeg, double tiltDeg) {
		arc(attacker, target, startDeg, 210, tiltDeg, PINK, PURPLE, Particle.FLAME);
		sound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0F, 0.9F);
	}

	/** Dark wings unfolding behind the wielder. */
	static void wings(LivingEntity holder) {
		World world = holder.getWorld();
		Vector look = holder.getLocation().getDirection().setY(0).normalize();
		Vector right = new Vector(-look.getZ(), 0, look.getX());
		Vector back = holder.getLocation().toVector().add(new Vector(0, 1.4, 0)).subtract(look.clone().multiply(0.4));
		for (int side = -1; side <= 1; side += 2) {
			for (double t = 0; t <= 2.6; t += 0.12) {
				Vector p = back.clone().add(right.clone().multiply(side * t)).add(new Vector(0, Math.sin(t * 1.2) * 0.9, 0));
				dustAt(world, SHADOW, p, 2, 0.08);
				if (t > 1.2) {
					dustAt(world, PURPLE_DIM, p.clone().add(new Vector(0, -0.4, 0)), 1, 0.1);
				}
			}
		}
		sound(holder.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 1.0F, 0.6F);
	}

	/** Counter stance: a purple ring around the wielder. */
	static void stance(LivingEntity holder) {
		World world = holder.getWorld();
		Location base = holder.getLocation();
		for (int i = 0; i < 20; i++) {
			double a = i * Math.PI / 10;
			dustAt(world, PURPLE, new Vector(base.getX() + Math.cos(a) * 1.0, base.getY() + 0.2, base.getZ() + Math.sin(a) * 1.0), 1, 0.0);
		}
		sound(base, Sound.ITEM_SHIELD_BLOCK, 0.8F, 0.7F);
	}

	static void smoke(Entity target) {
		spawn(target.getWorld(), Particle.LARGE_SMOKE, center(target), 10, 0.6, 0.02);
	}

	/** A chain of grey links with gold glints from the wielder to {@code to}. */
	static void chain(Entity from, Entity to) {
		World world = from.getWorld();
		Vector a = center(from);
		Vector b = center(to);
		Vector dir = b.clone().subtract(a);
		double length = dir.length();
		if (length < 0.1) {
			return;
		}
		dir.normalize();
		int i = 0;
		for (double t = 0.4; t <= length; t += 0.18, i++) {
			Vector p = a.clone().add(dir.clone().multiply(t)).add(new Vector(0, Math.sin(t * 0.9) * 0.15, 0));
			dustAt(world, i % 5 == 0 ? GOLD : GREY, p, 1, 0.0);
		}
		sound(to.getLocation(), Sound.BLOCK_CHAIN_PLACE, 0.8F, 0.7F);
	}

	/** A heavy kick: a burst at the target's middle with a dull thud. */
	static void kick(Entity target) {
		World world = target.getWorld();
		Vector c = center(target).add(new Vector(0, -0.2, 0));
		spawn(world, Particle.SWEEP_ATTACK, c, 1, 0.0, 0.0);
		spawn(world, Particle.CRIT, c, 20, 0.3, 0.4);
		spawn(world, Particle.FLAME, c, 15, 0.3, 0.08);
		spawn(world, Particle.CLOUD, c, 8, 0.3, 0.05);
		dustAt(world, ORANGE, c, 12, 0.4);
		sound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.0F, 0.6F);
		sound(target.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 0.7F, 0.7F);
	}

	/** The drop kick landing on the hilt: a shockwave of purple shards and smoke. */
	static void dropKick(Entity target) {
		World world = target.getWorld();
		Vector c = center(target);
		spawn(world, Particle.EXPLOSION, c, 2, 0.3, 0.0);
		spawn(world, Particle.CLOUD, c, 25, 0.8, 0.1);
		spawn(world, Particle.END_ROD, c, 15, 0.2, 0.3);
		dustAt(world, PURPLE, c, 40, 1.2);
		dustAt(world, WHITE, c, 30, 1.0);
		dustAt(world, SHADOW, c, 20, 0.9);
		sound(target.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0F, 0.5F);
		sound(target.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.8F, 1.2F);
		sound(target.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.8F, 0.7F);
	}

	/** The closing cut: a wide white crescent edged with pink. */
	static void whiteCrescent(Entity attacker, Entity target) {
		arc(attacker, target, -130, 260, 10, WHITE, PINK, Particle.END_ROD);
		arc(attacker, target, -120, 240, -15, GREY, null, null);
		sound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0F, 0.5F);
		sound(target.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_3, 0.8F, 1.2F);
	}

	/** Embers rising from the embedded blade. */
	static void embers(Entity blade) {
		Vector p = blade.getLocation().toVector();
		spawn(blade.getWorld(), Particle.FLAME, p, 2, 0.15, 0.01);
		spawn(blade.getWorld(), Particle.SMALL_FLAME, p, 2, 0.2, 0.01);
	}

	static void throwSound(Entity thrower) {
		sound(thrower.getLocation(), Sound.ITEM_TRIDENT_THROW, 1.0F, 0.6F);
		sound(thrower.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 0.7F, 0.7F);
	}

	static void recallSound(Entity holder) {
		sound(holder.getLocation(), Sound.ITEM_TRIDENT_RETURN, 1.0F, 0.7F);
		sound(holder.getLocation(), Sound.BLOCK_CHAIN_BREAK, 0.8F, 0.8F);
	}
}
