package io.github.sharket1111.indexprescript;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Liquid-metal visuals: black metal splashing out along the blade as it
 * solidifies, with pale blue glowing droplets scattering around it.
 */
final class CaduceusEffects {
	private static final DustParticleOptions BLUE_DROPLET = new DustParticleOptions(0x8FDCFF, 0.7F);
	private static final DustParticleOptions PALE_DROPLET = new DustParticleOptions(0xE6F6FF, 0.5F);
	private static final DustParticleOptions WHITE_STREAK = new DustParticleOptions(0xF4FAFF, 1.0F);
	private static final DustParticleOptions AURA = new DustParticleOptions(0x0B0B10, 1.4F);
	private static final double BLADE_REACH = 2.4;
	private static final double SLASH_LENGTH = 5.0;
	private static final double SLASH_STEP = 0.18;
	private static final double CRESCENT_RADIUS = 2.6;
	private static final int BLADE_STEPS = 8;

	private CaduceusEffects() {
	}

	/** Weapon solidifying: splash along the swing line plus scattered droplets. */
	static void form(ServerLevel level, LivingEntity holder) {
		Vec3 look = holder.getLookAngle();
		Vec3 right = new Vec3(-look.z, 0.0, look.x).normalize();
		Vec3 hand = holder.getEyePosition().add(look.scale(0.5)).add(right.scale(0.35)).add(0.0, -0.45, 0.0);

		for (int i = 0; i <= BLADE_STEPS; i++) {
			Vec3 p = hand.add(look.scale(BLADE_REACH * i / BLADE_STEPS));
			level.sendParticles(ParticleTypes.SQUID_INK, p.x, p.y, p.z, 2, 0.12, 0.12, 0.12, 0.04);
			if (i % 2 == 0) {
				level.sendParticles(BLUE_DROPLET, p.x, p.y, p.z, 2, 0.25, 0.2, 0.25, 0.0);
			}
		}

		Vec3 mid = hand.add(look.scale(BLADE_REACH * 0.5));
		level.sendParticles(ParticleTypes.GLOW_SQUID_INK, mid.x, mid.y, mid.z, 6, 0.5, 0.35, 0.5, 0.06);
		level.sendParticles(PALE_DROPLET, mid.x, mid.y, mid.z, 10, 0.7, 0.45, 0.7, 0.0);
		level.sendParticles(ParticleTypes.SQUID_INK, hand.x, hand.y, hand.z, 6, 0.1, 0.1, 0.1, 0.12);

		level.playSound(null, holder.blockPosition(), SoundEvents.BUCKET_FILL_LAVA, SoundSource.PLAYERS, 0.6F, 1.6F);
		level.playSound(null, holder.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.5F, 1.8F);
	}

	/** Splash at the point of impact. */
	static void impact(ServerLevel level, Entity target) {
		double y = target.getY(0.5);
		level.sendParticles(ParticleTypes.SQUID_INK, target.getX(), y, target.getZ(), 8, 0.3, 0.3, 0.3, 0.05);
		level.sendParticles(BLUE_DROPLET, target.getX(), y, target.getZ(), 6, 0.35, 0.35, 0.35, 0.0);
	}

	/** Critical hit: sparks and a heavy strike sound. */
	static void crit(ServerLevel level, Entity target) {
		level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.6), target.getZ(), 14, 0.35, 0.35, 0.35, 0.25);
		level.sendParticles(ParticleTypes.GLOW_SQUID_INK, target.getX(), target.getY(0.6), target.getZ(), 3, 0.3, 0.3, 0.3, 0.05);
		level.playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.8F, 1.3F);
	}

	/** Translucent black aura around a wielder with full [Hermes]. */
	static void aura(ServerLevel level, LivingEntity holder) {
		RandomSource random = holder.getRandom();
		for (int i = 0; i < 4; i++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double radius = 0.7 + random.nextDouble() * 0.4;
			double x = holder.getX() + Math.cos(angle) * radius;
			double z = holder.getZ() + Math.sin(angle) * radius;
			double y = holder.getY() + random.nextDouble() * holder.getBbHeight();
			level.sendParticles(AURA, x, y, z, 1, 0.05, 0.1, 0.05, 0.0);
		}
		level.sendParticles(ParticleTypes.SMOKE, holder.getX(), holder.getY(0.5), holder.getZ(), 1, 0.4, 0.6, 0.4, 0.005);
	}

	/** The aura erupting as Furioso-Replica begins. */
	static void auraBurst(ServerLevel level, LivingEntity holder) {
		level.sendParticles(AURA, holder.getX(), holder.getY(0.5), holder.getZ(), 40, 0.8, 0.9, 0.8, 0.0);
		level.sendParticles(ParticleTypes.SQUID_INK, holder.getX(), holder.getY(0.5), holder.getZ(), 20, 0.5, 0.6, 0.5, 0.15);
		level.sendParticles(ParticleTypes.GLOW_SQUID_INK, holder.getX(), holder.getY(0.5), holder.getZ(), 8, 0.6, 0.6, 0.6, 0.1);
	}

	/** Liquid metal drawn in around the target before the final coin. */
	static void gather(ServerLevel level, Entity target) {
		level.sendParticles(ParticleTypes.SQUID_INK, target.getX(), target.getY(0.5), target.getZ(), 30, 1.5, 1.0, 1.5, 0.0);
		level.sendParticles(BLUE_DROPLET, target.getX(), target.getY(0.5), target.getZ(), 20, 1.2, 0.9, 1.2, 0.0);
	}

	/**
	 * A long white slash through the target, tilted {@code angleDeg} from the
	 * horizontal in the plane facing the attacker.
	 */
	static void slash(ServerLevel level, LivingEntity attacker, Entity target, double angleDeg, float scale) {
		Vec3 center = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
		Vec3 right = facingRight(attacker, target);
		double a = Math.toRadians(angleDeg);
		Vec3 dir = right.scale(Math.cos(a)).add(0.0, Math.sin(a), 0.0);
		double half = SLASH_LENGTH * 0.5 * scale;
		for (double t = -half; t <= half; t += SLASH_STEP) {
			Vec3 p = center.add(dir.scale(t));
			level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
			level.sendParticles(WHITE_STREAK, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
		}
		level.sendParticles(BLUE_DROPLET, center.x, center.y, center.z, 8, 0.6, 0.6, 0.6, 0.0);
		level.sendParticles(ParticleTypes.SQUID_INK, center.x, center.y, center.z, 4, 0.3, 0.3, 0.3, 0.08);
	}

	/** Final coin: a scythe crescent around the target and a great diagonal cut. */
	static void reap(ServerLevel level, LivingEntity attacker, Entity target) {
		Vec3 center = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
		Vec3 right = facingRight(attacker, target);
		Vec3 toward = attacker.position().subtract(target.position()).multiply(1.0, 0.0, 1.0).normalize();
		for (double a = -100; a <= 100; a += 4) {
			double r = Math.toRadians(a);
			double radius = CRESCENT_RADIUS * (1.0 - Math.abs(a) / 400.0);
			Vec3 p = center.add(right.scale(Math.sin(r) * radius)).add(toward.scale(-Math.cos(r) * radius * 0.6)).add(0.0, Math.sin(r) * 0.6, 0.0);
			level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 2, 0.03, 0.03, 0.03, 0.0);
			level.sendParticles(WHITE_STREAK, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
		}
		slash(level, attacker, target, 50, 1.8F);
		slash(level, attacker, target, -40, 1.4F);
		level.sendParticles(ParticleTypes.GLOW_SQUID_INK, center.x, center.y, center.z, 16, 1.0, 0.8, 1.0, 0.15);
		level.sendParticles(BLUE_DROPLET, center.x, center.y, center.z, 30, 1.4, 1.0, 1.4, 0.0);
		level.playSound(null, target.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.8F, 0.7F);
		level.playSound(null, target.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 0.7F);
	}

	private static Vec3 facingRight(Entity attacker, Entity target) {
		Vec3 toward = target.position().subtract(attacker.position()).multiply(1.0, 0.0, 1.0);
		if (toward.lengthSqr() < 1.0E-4) {
			toward = attacker.getLookAngle().multiply(1.0, 0.0, 1.0);
		}
		toward = toward.normalize();
		return new Vec3(-toward.z, 0.0, toward.x);
	}

	/** Weapon melting back into the rod: black metal dripping off the hand. */
	static void melt(ServerLevel level, Entity holder) {
		double y = holder.getY(0.55);
		level.sendParticles(ParticleTypes.SQUID_INK, holder.getX(), y, holder.getZ(), 6, 0.25, 0.15, 0.25, 0.01);
		level.sendParticles(PALE_DROPLET, holder.getX(), y, holder.getZ(), 4, 0.3, 0.2, 0.3, 0.0);
	}
}
