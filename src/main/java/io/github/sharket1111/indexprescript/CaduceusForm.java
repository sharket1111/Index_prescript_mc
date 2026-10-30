package io.github.sharket1111.indexprescript;

import java.util.List;
import java.util.Optional;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Weapons Caduceus solidifies from liquid metal around its brass grip, each
 * modelled on its Limbus Company skill: damage type, coin damage bonus and
 * on-hit effect. The active form is stored as the first custom model data
 * string, which also selects the item model (assets/index_prescript/items/caduceus.json).
 */
public enum CaduceusForm {
	// "Closer to a bust, but better than a fork." Damage is unreliable; builds Poise.
	HATCHET("hatchet", DamageKind.BLUNT, 8.0F, 0.0F),
	// "Cold sweat when it shows up where I need to swing sideways." Weak in a crowd; inflicts Sinking.
	STILETTO("stiletto", DamageKind.PIERCE, 7.0F, 0.0F),
	// "Plain, sometimes too plain." Raises the wielder's attack level.
	BASTARD_SWORD("bastard_sword", DamageKind.SLASH, 7.5F, 0.075F),
	// "Sharper and longer than a stiletto." Lowers the target's defense level.
	RAPIER("rapier", DamageKind.PIERCE, 7.0F, 0.075F),
	// "Simple but certain. It will surely break them." Deals stagger damage.
	HAMMER("hammer", DamageKind.BLUNT, 7.5F, 0.075F),
	// "Center of gravity matters; use its weight." Bonus from falling; inflicts Slash Fragile.
	GREATSWORD("greatsword", DamageKind.SLASH, 8.0F, 0.15F),
	// "Self-propelled, pierces everyone in the way." Lunges and pierces the line behind; Pierce Fragile.
	LANCE("lance", DamageKind.PIERCE, 7.5F, 0.15F),
	// "Flesh comes off and they bleed, weakening with pain and exhaustion." Bleed, weakness, pull; Blunt Fragile.
	WHIP("whip", DamageKind.BLUNT, 6.5F, 0.15F),
	// "Hardest to handle, but it cuts the best." Always critical.
	SCYTHE("scythe", DamageKind.SLASH, 6.5F, 0.30F);

	/** Attack damage of the bare rod (player base 1 + netherite 4 + item 1); forms scale relative to it. */
	static final float ROD_DAMAGE = 6.0F;

	private static final CaduceusForm[] VALUES = values();
	private static final float CRIT_MULTIPLIER = 1.2F;
	private static final float POISE_CRIT_CHANCE = 0.05F;
	private static final float ATTACK_LEVEL_DAMAGE = 1.0F;
	private static final float DEFENSE_DOWN_PER_LEVEL = 0.075F;
	private static final float FRAGILE_PER_STACK = 0.10F;
	private static final float STAGGERED_MULTIPLIER = 1.5F;
	private static final float SINKING_DAMAGE_PER_POTENCY = 0.5F;
	private static final float STAGGER_PER_HIT = 5.0F;
	private static final float GREATSWORD_MAX_FALL_BONUS = 8.0F;
	private static final double CROWD_RANGE = 2.5;
	private static final double LANCE_PIERCE_LENGTH = 4.0;
	private static final float LANCE_PIERCE_RATIO = 0.5F;

	private final String id;
	private final DamageKind kind;
	private final float baseDamage;
	private final float coinBonus;

	CaduceusForm(String id, DamageKind kind, float baseDamage, float coinBonus) {
		this.id = id;
		this.kind = kind;
		this.baseDamage = baseDamage;
		this.coinBonus = coinBonus;
	}

	/** Thrusting weapons, animated with a stab instead of a swing. */
	public boolean piercing() {
		return kind == DamageKind.PIERCE;
	}

	public Component displayName() {
		return Component.translatable("form.index_prescript." + id);
	}

	/** Result of resolving one attack: final damage and whether it was critical. */
	record Hit(float damage, boolean crit) {
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

	/**
	 * Computes this attack's damage at full charge, consuming the statuses the
	 * hit triggers (the target's Sinking, the wielder's Poise on a critical).
	 */
	Hit resolve(ServerLevel level, LivingEntity attacker, Entity target) {
		long now = level.getGameTime();
		RandomSource random = attacker.getRandom();
		CombatState self = CombatState.of(attacker);

		float damage = baseDamage * (1.0F + coinBonus);
		switch (this) {
			case HATCHET -> damage *= 0.75F + random.nextFloat() * 0.35F;
			case STILETTO -> {
				if (crowded(level, attacker, target)) {
					damage *= 0.75F;
				}
			}
			case GREATSWORD -> damage += Math.min((float) attacker.fallDistance, GREATSWORD_MAX_FALL_BONUS);
			default -> {
			}
		}
		damage += self.attackLevel(now) * ATTACK_LEVEL_DAMAGE;

		if (target instanceof LivingEntity living) {
			CombatState other = CombatState.of(living);
			damage *= 1.0F + other.defenseDown(now) * DEFENSE_DOWN_PER_LEVEL + other.fragile(kind, now) * FRAGILE_PER_STACK;
			if (other.staggered(now)) {
				damage *= STAGGERED_MULTIPLIER;
			}
			damage += other.consumeSinking(now) * SINKING_DAMAGE_PER_POTENCY;
		}

		boolean crit = this == SCYTHE || random.nextFloat() < self.poisePotency(now) * POISE_CRIT_CHANCE;
		if (crit) {
			damage *= CRIT_MULTIPLIER;
			self.consumePoise(now);
		}
		return new Hit(damage, crit);
	}

	/** On-hit effect. Returns true if the hit staggered the target. */
	boolean onHit(ServerLevel level, LivingEntity attacker, LivingEntity target) {
		long now = level.getGameTime();
		CombatState self = CombatState.of(attacker);
		CombatState other = CombatState.of(target);
		switch (this) {
			case HATCHET -> self.addPoise(2, 2, now);
			case STILETTO -> {
				other.addSinking(2, 2, now);
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, CombatState.TURN_TICKS), attacker);
			}
			case BASTARD_SWORD -> self.addAttackLevel(now);
			case RAPIER -> other.addDefenseDown(now);
			case HAMMER -> {
				if (other.addStagger(STAGGER_PER_HIT, Math.max(10.0F, target.getMaxHealth() * 0.5F), now)) {
					stagger(level, attacker, target);
					return true;
				}
			}
			case GREATSWORD -> {
				other.addFragile(DamageKind.SLASH, now);
				level.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY(0.5), target.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
			}
			case LANCE -> {
				other.addFragile(DamageKind.PIERCE, now);
				lunge(level, attacker, target);
			}
			case WHIP -> {
				other.addFragile(DamageKind.BLUNT, now);
				target.addEffect(new MobEffectInstance(MobEffects.WITHER, 80), attacker);
				target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80), attacker);
				Motion.push(target, attacker.position().subtract(target.position()).normalize().scale(0.8), 0.25);
			}
			case SCYTHE -> level.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY(0.5), target.getZ(), 3, 1.0, 0.2, 1.0, 0.0);
		}
		return false;
	}

	private static boolean crowded(ServerLevel level, LivingEntity attacker, Entity target) {
		return !level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(CROWD_RANGE),
				entity -> entity != attacker && entity != target && entity.isAlive()).isEmpty();
	}

	private static void stagger(ServerLevel level, LivingEntity attacker, LivingEntity target) {
		target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, CombatState.TURN_TICKS, 6), attacker);
		target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, CombatState.TURN_TICKS, 1), attacker);
		level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(1.0), target.getZ(), 20, 0.4, 0.3, 0.4, 0.3);
		level.playSound(null, target.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.6F, 1.4F);
	}

	/** Lance accelerates forward and pierces everyone standing in the line behind the target. */
	private void lunge(ServerLevel level, LivingEntity attacker, LivingEntity target) {
		Vec3 dir = attacker.getLookAngle();
		Vec3 flat = new Vec3(dir.x, 0.0, dir.z).normalize();
		Motion.push(attacker, flat.scale(0.6), 0.05);

		Vec3 start = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
		Vec3 end = start.add(dir.scale(LANCE_PIERCE_LENGTH));
		AABB line = new AABB(start, end).inflate(0.8);
		DamageSource source = attacker instanceof Player player
				? level.damageSources().playerAttack(player)
				: level.damageSources().mobAttack(attacker);
		float damage = baseDamage * (1.0F + coinBonus) * LANCE_PIERCE_RATIO;
		for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, line,
				e -> e != attacker && e != target && e.isAlive())) {
			entity.hurtServer(level, source, damage);
		}
		for (int i = 1; i <= 6; i++) {
			Vec3 p = start.add(dir.scale(LANCE_PIERCE_LENGTH * i / 6));
			level.sendParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 2, 0.1, 0.1, 0.1, 0.05);
		}
	}
}
