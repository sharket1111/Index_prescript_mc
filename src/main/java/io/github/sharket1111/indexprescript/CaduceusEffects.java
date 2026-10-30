package io.github.sharket1111.indexprescript;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
	private static final double BLADE_REACH = 2.4;
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

	/** Weapon melting back into the rod: black metal dripping off the hand. */
	static void melt(ServerLevel level, Entity holder) {
		double y = holder.getY(0.55);
		level.sendParticles(ParticleTypes.SQUID_INK, holder.getX(), y, holder.getZ(), 6, 0.25, 0.15, 0.25, 0.01);
		level.sendParticles(PALE_DROPLET, holder.getX(), y, holder.getZ(), 4, 0.3, 0.2, 0.3, 0.0);
	}
}
