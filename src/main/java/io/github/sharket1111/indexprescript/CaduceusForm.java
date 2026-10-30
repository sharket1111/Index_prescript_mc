package io.github.sharket1111.indexprescript;

import java.util.List;
import java.util.Optional;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.phys.Vec3;

/**
 * Weapons Caduceus solidifies from liquid metal around its brass grip.
 * The active form is stored as the first custom model data string, which
 * also selects the item model (assets/index_prescript/items/caduceus.json).
 */
public enum CaduceusForm {
	HATCHET("hatchet", 3.0F),
	STILETTO("stiletto", 2.0F),
	BASTARD_SWORD("bastard_sword", 5.0F),
	RAPIER("rapier", 2.0F),
	HAMMER("hammer", 6.0F),
	GREATSWORD("greatsword", 7.0F),
	LANCE("lance", 4.0F),
	WHIP("whip", 2.0F),
	SCYTHE("scythe", 3.0F);

	private static final CaduceusForm[] VALUES = values();
	private static final float SCYTHE_SWEEP_DAMAGE = 4.0F;
	private static final double SCYTHE_SWEEP_RANGE = 3.0;

	private final String id;
	private final float bonusDamage;

	CaduceusForm(String id, float bonusDamage) {
		this.id = id;
		this.bonusDamage = bonusDamage;
	}

	public float bonusDamage() {
		return bonusDamage;
	}

	public Component displayName() {
		return Component.translatable("form.index_prescript." + id);
	}

	public static Optional<CaduceusForm> of(ItemStack stack) {
		CustomModelData data = stack.get(DataComponents.CUSTOM_MODEL_DATA);
		if (data == null || data.strings().isEmpty()) {
			return Optional.empty();
		}
		String id = data.strings().getFirst();
		for (CaduceusForm form : VALUES) {
			if (form.id.equals(id)) {
				return Optional.of(form);
			}
		}
		return Optional.empty();
	}

	public void applyTo(ItemStack stack) {
		stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(id), List.of()));
	}

	public static void clear(ItemStack stack) {
		stack.remove(DataComponents.CUSTOM_MODEL_DATA);
	}

	/** Picks a random form, never repeating the previous one. */
	public static CaduceusForm random(RandomSource random, CaduceusForm previous) {
		if (previous == null) {
			return VALUES[random.nextInt(VALUES.length)];
		}
		int index = random.nextInt(VALUES.length - 1);
		return VALUES[index >= previous.ordinal() ? index + 1 : index];
	}

	/** Form-specific effect after a successful hit. */
	public void onHit(ServerLevel level, LivingEntity attacker, LivingEntity target) {
		switch (this) {
			case HATCHET -> target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60), attacker);
			case STILETTO -> target.addEffect(new MobEffectInstance(MobEffects.WITHER, 60), attacker);
			case BASTARD_SWORD -> level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.5), target.getZ(), 10, 0.3, 0.3, 0.3, 0.2);
			case RAPIER -> attacker.addEffect(new MobEffectInstance(MobEffects.SPEED, 40, 1));
			case HAMMER -> target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 2), attacker);
			case GREATSWORD -> level.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY(0.5), target.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
			case LANCE -> impulse(attacker, target, -1.4, 0.35);
			case WHIP -> impulse(attacker, target, 0.8, 0.25);
			case SCYTHE -> reap(level, attacker, target);
		}
	}

	/** Pushes the target towards the attacker (positive strength) or away from it (negative). */
	private static void impulse(LivingEntity attacker, LivingEntity target, double strength, double lift) {
		Vec3 toAttacker = attacker.position().subtract(target.position()).normalize().scale(strength);
		target.push(toAttacker.x, lift, toAttacker.z);
	}

	private static void reap(ServerLevel level, LivingEntity attacker, LivingEntity target) {
		DamageSource source = attacker instanceof Player player
				? level.damageSources().playerAttack(player)
				: level.damageSources().mobAttack(attacker);
		List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(SCYTHE_SWEEP_RANGE),
				entity -> entity != attacker && entity != target && entity.isAlive());
		for (LivingEntity entity : nearby) {
			entity.hurtServer(level, source, SCYTHE_SWEEP_DAMAGE);
		}
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY(0.5), target.getZ(), 3, 1.0, 0.2, 1.0, 0.0);
	}
}
