package io.github.sharket1111.indexprescript.paper;

import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * Weapons Caduceus solidifies from liquid metal around its brass grip, each
 * modelled on its Limbus Company skill: damage type, coin damage bonus and
 * on-hit effect.
 */
enum CaduceusForm {
	// "Closer to a bust, but better than a fork." Damage is unreliable; builds Poise.
	HATCHET("hatchet", DamageKind.BLUNT, 8.0, 0.0),
	// "Cold sweat when it shows up where I need to swing sideways." Weak in a crowd; inflicts Sinking.
	STILETTO("stiletto", DamageKind.PIERCE, 7.0, 0.0),
	// "Plain, sometimes too plain." Raises the wielder's attack level.
	BASTARD_SWORD("bastard_sword", DamageKind.SLASH, 7.5, 0.075),
	// "Sharper and longer than a stiletto." Lowers the target's defense level.
	RAPIER("rapier", DamageKind.PIERCE, 7.0, 0.075),
	// "Simple but certain. It will surely break them." Deals stagger damage.
	HAMMER("hammer", DamageKind.BLUNT, 7.5, 0.075),
	// "Center of gravity matters; use its weight." Bonus from falling; inflicts Slash Fragile.
	GREATSWORD("greatsword", DamageKind.SLASH, 8.0, 0.15),
	// "Self-propelled, pierces everyone in the way." Lunges and pierces the line behind; Pierce Fragile.
	LANCE("lance", DamageKind.PIERCE, 7.5, 0.15),
	// "Flesh comes off and they bleed, weakening with pain and exhaustion." Bleed, weakness, pull; Blunt Fragile.
	WHIP("whip", DamageKind.BLUNT, 6.5, 0.15),
	// "Hardest to handle, but it cuts the best." Always critical.
	SCYTHE("scythe", DamageKind.SLASH, 6.5, 0.30);

	/** Attack damage of the bare rod (an iron sword); forms scale relative to it. */
	static final double ROD_DAMAGE = 6.0;

	private static final CaduceusForm[] VALUES = values();
	private static final double CRIT_MULTIPLIER = 1.2;
	private static final double POISE_CRIT_CHANCE = 0.05;
	private static final double ATTACK_LEVEL_DAMAGE = 1.0;
	private static final double DEFENSE_DOWN_PER_LEVEL = 0.075;
	private static final double FRAGILE_PER_STACK = 0.10;
	private static final double STAGGERED_MULTIPLIER = 1.5;
	private static final double SINKING_DAMAGE_PER_POTENCY = 0.5;
	private static final float STAGGER_PER_HIT = 5.0F;
	private static final double GREATSWORD_MAX_FALL_BONUS = 8.0;
	private static final double CROWD_RANGE = 2.5;
	private static final double LANCE_PIERCE_LENGTH = 4.0;
	private static final double LANCE_PIERCE_RATIO = 0.5;

	private final String id;
	private final DamageKind kind;
	private final double baseDamage;
	private final double coinBonus;

	CaduceusForm(String id, DamageKind kind, double baseDamage, double coinBonus) {
		this.id = id;
		this.kind = kind;
		this.baseDamage = baseDamage;
		this.coinBonus = coinBonus;
	}

	String id() {
		return id;
	}

	record Hit(double damage, boolean crit) {
	}

	static CaduceusForm of(ItemStack stack) {
		String id = Items.modelString(stack);
		if (id == null) {
			return null;
		}
		for (CaduceusForm form : VALUES) {
			if (form.id.equals(id)) {
				return form;
			}
		}
		return null;
	}

	/** Picks a random form, never repeating the previous one. */
	static CaduceusForm random(CaduceusForm previous) {
		ThreadLocalRandom random = ThreadLocalRandom.current();
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
	Hit resolve(LivingEntity attacker, Entity target, long now) {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		CombatState self = CombatState.of(attacker);

		double damage = baseDamage * (1.0 + coinBonus);
		switch (this) {
			case HATCHET -> damage *= 0.75 + random.nextDouble() * 0.35;
			case STILETTO -> {
				if (crowded(attacker, target)) {
					damage *= 0.75;
				}
			}
			case GREATSWORD -> damage += Math.min(attacker.getFallDistance(), GREATSWORD_MAX_FALL_BONUS);
			default -> {
			}
		}
		damage += self.attackLevel(now) * ATTACK_LEVEL_DAMAGE;

		if (target instanceof LivingEntity living) {
			CombatState other = CombatState.of(living);
			damage *= 1.0 + other.defenseDown(now) * DEFENSE_DOWN_PER_LEVEL + other.fragile(kind, now) * FRAGILE_PER_STACK;
			if (other.staggered(now)) {
				damage *= STAGGERED_MULTIPLIER;
			}
			damage += other.consumeSinking(now) * SINKING_DAMAGE_PER_POTENCY;
		}

		boolean crit = this == SCYTHE || random.nextDouble() < self.poisePotency(now) * POISE_CRIT_CHANCE;
		if (crit) {
			damage *= CRIT_MULTIPLIER;
			self.consumePoise(now);
		}
		return new Hit(damage, crit);
	}

	/** On-hit effect. Returns true if the hit staggered the target. */
	boolean onHit(LivingEntity attacker, LivingEntity target, long now) {
		CombatState self = CombatState.of(attacker);
		CombatState other = CombatState.of(target);
		switch (this) {
			case HATCHET -> self.addPoise(2, 2, now);
			case STILETTO -> {
				other.addSinking(2, 2, now);
				target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, CombatState.TURN_TICKS, 0));
			}
			case BASTARD_SWORD -> self.addAttackLevel(now);
			case RAPIER -> other.addDefenseDown(now);
			case HAMMER -> {
				if (other.addStagger(STAGGER_PER_HIT, (float) Math.max(10.0, maxHealth(target) * 0.5), now)) {
					target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, CombatState.TURN_TICKS, 6));
					target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, CombatState.TURN_TICKS, 1));
					Effects.staggered(target);
					return true;
				}
			}
			case GREATSWORD -> {
				other.addFragile(DamageKind.SLASH, now);
				Effects.sweep(target, 1, 0.0);
			}
			case LANCE -> {
				other.addFragile(DamageKind.PIERCE, now);
				lunge(attacker, target);
			}
			case WHIP -> {
				other.addFragile(DamageKind.BLUNT, now);
				target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 80, 0));
				target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 80, 0));
				Vector pull = attacker.getLocation().toVector().subtract(target.getLocation().toVector()).setY(0).normalize().multiply(0.8);
				impulseNextTick(target, pull.setY(0.25));
			}
			case SCYTHE -> Effects.sweep(target, 3, 1.0);
		}
		return false;
	}

	private static double maxHealth(LivingEntity entity) {
		AttributeInstance attribute = entity.getAttribute(Attribute.MAX_HEALTH);
		return attribute == null ? 20.0 : attribute.getValue();
	}

	private static boolean crowded(LivingEntity attacker, Entity target) {
		return target.getNearbyEntities(CROWD_RANGE, CROWD_RANGE, CROWD_RANGE).stream()
				.anyMatch(e -> e instanceof LivingEntity living && e != attacker && !living.isDead());
	}

	/** Velocity changes are applied a tick later so vanilla knockback doesn't overwrite them. */
	private static void impulseNextTick(Entity entity, Vector impulse) {
		Bukkit.getScheduler().runTask(IndexPrescriptPlugin.instance(), () -> {
			if (entity.isValid()) {
				entity.setVelocity(entity.getVelocity().add(impulse));
			}
		});
	}

	/** Lance accelerates forward and pierces everyone standing in the line behind the target. */
	private void lunge(LivingEntity attacker, LivingEntity target) {
		Vector dir = attacker.getEyeLocation().getDirection();
		impulseNextTick(attacker, dir.clone().setY(0).normalize().multiply(0.6).setY(0.05));

		Vector start = target.getLocation().toVector().add(new Vector(0, target.getHeight() * 0.5, 0));
		double damage = baseDamage * (1.0 + coinBonus) * LANCE_PIERCE_RATIO;
		for (Entity entity : target.getNearbyEntities(LANCE_PIERCE_LENGTH + 1, 2.0, LANCE_PIERCE_LENGTH + 1)) {
			if (!(entity instanceof LivingEntity living) || entity == attacker || living.isDead()) {
				continue;
			}
			Vector offset = entity.getLocation().toVector().add(new Vector(0, entity.getHeight() * 0.5, 0)).subtract(start);
			double along = offset.dot(dir);
			double across = offset.clone().subtract(dir.clone().multiply(along)).length();
			if (along > 0 && along <= LANCE_PIERCE_LENGTH && across <= 1.0) {
				Damage.deal(living, damage, attacker, false);
			}
		}
	}
}
