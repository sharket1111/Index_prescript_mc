package io.github.sharket1111.indexprescript.paper;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * Matthias's Laevatein skills, each a timed strike sequence after the in-game
 * animation. Limbus keywords are mapped to Minecraft: Burn is fire duration
 * (potency x 0.6 s, each count +0.5 s), Bind is Slowness, Paralysis is Weakness
 * for a turn, Bleed is Wither (potency seconds, each count +0.5 s), and stagger
 * recovery heals. [Big Brother] means having a Grudge Tattoo. Coin damage runs
 * through Matthias's passives; at half health or less it is raised by +40%
 * (+60% for Summary Execution).
 */
final class LaevateinSkill {
	enum Kind {
		/** Stage 0: Stomp. */
		STOMP,
		/** Stage 1: "Not Getting Heated Up?!" */
		HEATED,
		/** Stage 2: "Time to Melt and Die". */
		MELT,
		/** Stage 3: Stab [Laevatein]. */
		STAB,
		/** Counter: Rule Violation. */
		RULE_VIOLATION,
		/** Counter at full release: Summary Execution [Laevatein]. */
		EXECUTION
	}

	private static final Map<UUID, LaevateinSkill> ACTIVE = new ConcurrentHashMap<>();
	private static final int TURN = CombatState.TURN_TICKS;

	private final Player player;
	private final LivingEntity target;
	private final Kind kind;
	private final Location anchor;
	private final boolean hadAi;
	private final boolean repeat;
	private Cinematic camera;
	private int selfBurn;
	private int tick;

	private LaevateinSkill(Player player, LivingEntity target, Kind kind, boolean repeat) {
		this.player = player;
		this.target = target;
		this.kind = kind;
		this.anchor = target.getLocation();
		this.hadAi = target.hasAI();
		this.repeat = repeat;
	}

	static boolean active(Player player) {
		return ACTIVE.containsKey(player.getUniqueId());
	}

	static void start(Player player, LivingEntity target, Kind kind, int stage) {
		if (ACTIVE.containsKey(player.getUniqueId())) {
			return;
		}
		// Rule Violation repeats once if the wielder already had [Big Brother] when struck.
		boolean repeat = kind == Kind.RULE_VIOLATION && Matthias.hasTattoo(player);
		LaevateinSkill skill = new LaevateinSkill(player, target, kind, repeat);
		ACTIVE.put(player.getUniqueId(), skill);
		skill.open(stage);
	}

	static void tickAll() {
		ACTIVE.values().removeIf(skill -> {
			try {
				return !skill.tick();
			} catch (RuntimeException e) {
				IndexPrescriptPlugin.instance().getLogger().log(Level.WARNING, "Laevatein skill failed", e);
				skill.finish();
				return true;
			}
		});
	}

	private void open(int stage) {
		if (!(target instanceof Player)) {
			target.setAI(false);
		}
		if (kind == Kind.STAB || kind == Kind.EXECUTION) {
			camera = Cinematic.start(player, player.getLocation().add(0, 2, 0));
		}
		// From release 2 on, the blade's heat burns the wielder too: 1 s, then 2 s at full release.
		// A filmed wielder is a spectator, which cannot burn, so the burn waits until the camera ends.
		selfBurn = stage >= 2 ? (stage >= 3 ? 40 : 20) : 0;
		if (camera == null) {
			burnSelf();
		}
		if (kind == Kind.HEATED || kind == Kind.MELT || kind == Kind.RULE_VIOLATION) {
			Matthias.addTattoo(player);
		}
		LaevateinEffects.aura(actor(), kind == Kind.EXECUTION ? 8 : 4);
		LaevateinEffects.auraSound(actor());
	}

	private int endTick() {
		return switch (kind) {
			case STOMP -> 18;
			case HEATED -> 26;
			case MELT -> 28;
			case STAB -> 34;
			case RULE_VIOLATION -> repeat ? 24 : 12;
			case EXECUTION -> 48;
		};
	}

	private boolean tick() {
		if (!player.isOnline() || player.isDead() || !target.isValid() || target.isDead()
				|| !Items.isLaevatein(player.getInventory().getItemInMainHand())) {
			finish();
			return false;
		}
		hold();
		shoot();
		if (tick % 4 == 0) {
			LaevateinEffects.aura(actor(), 1);
		}
		switch (kind) {
			case STOMP -> stomp();
			case HEATED -> heated();
			case MELT -> melt();
			case STAB -> stab();
			case RULE_VIOLATION -> ruleViolation(tick % 12);
			case EXECUTION -> execution();
		}
		if (tick >= endTick()) {
			finish();
			return false;
		}
		tick++;
		return true;
	}

	/** Stomp: dash in and stamp down twice. Coin power +1 (here +15%) if the target already bleeds. */
	private void stomp() {
		double bonus = target.hasPotionEffect(PotionEffectType.WITHER) ? 1.15 : 1.0;
		switch (tick) {
			case 4 -> dashToTarget();
			case 6 -> coin(4.0 * bonus, 0, () -> LaevateinEffects.stomp(target, 1.2), () -> bleed(3));
			case 12 -> coin(4.0 * bonus, 0, () -> {
				LaevateinEffects.stomp(target, 2.0);
				LaevateinEffects.smoke(target);
			}, () -> {
				bleedCount(2);
				if (Matthias.hasTattoo(player)) {
					bind(2);
				}
			});
			default -> {
			}
		}
	}

	/** "Not Getting Heated Up?!": a burning sweep, a golden thrust, then a great flame crescent. */
	private void heated() {
		switch (tick) {
			case 2 -> LaevateinEffects.lightningStrike(actor());
			case 6 -> coin(4.5, 1.0, () -> LaevateinEffects.flameArc(actor(), target, -90, 20), () -> {
				burn(2);
				bind(2);
			});
			case 12 -> coin(4.5 + 1.5, 1.0, () -> LaevateinEffects.thrust(actor(), target), () -> burn(2));
			case 18 -> coin(4.5, 1.0, () -> {
				LaevateinEffects.flameArc(actor(), target, 70, -35);
				LaevateinEffects.explosion(target, 1.0);
			}, () -> {
				burn(2);
				paralysis(3);
			});
			default -> {
			}
		}
	}

	/** "Time to Melt and Die": a pink streak, a burning sweep with a thrust, then a huge flame crescent. */
	private void melt() {
		switch (tick) {
			case 2 -> LaevateinEffects.aura(actor(), 8);
			case 6 -> coin(5.5, 1.0, () -> LaevateinEffects.pinkArc(actor(), target, -100, 15), () -> {
				burn(2);
				bind(2);
			});
			case 12 -> coin(5.5 + 1.5, 1.0, () -> {
				LaevateinEffects.flameArc(actor(), target, 80, -20);
				LaevateinEffects.thrust(actor(), target);
			}, () -> burn(2));
			case 20 -> coin(5.5, 1.0, () -> {
				LaevateinEffects.flameArc(actor(), target, -110, 40);
				LaevateinEffects.explosion(target, 1.6);
			}, () -> {
				burn(2);
				paralysis(3);
			});
			default -> {
			}
		}
	}

	/** Stab [Laevatein]: five burning strikes ending in a wide crescent. */
	private void stab() {
		switch (tick) {
			case 6 -> coin(5.0 + 1.0, 0, () -> LaevateinEffects.pinkArc(actor(), target, -90, 20), null);
			case 10 -> coin(5.0 + 1.0, 0, () -> LaevateinEffects.flameArc(actor(), target, 80, -25), null);
			case 14 -> coin(5.0, 0, () -> LaevateinEffects.shatter(target), () -> burn(10));
			case 18 -> coin(5.0, 0, () -> LaevateinEffects.explosion(target, 1.4), () -> burnCount(4));
			case 26 -> coin(5.0, 0, () -> {
				LaevateinEffects.flameArc(actor(), target, -120, 45);
				LaevateinEffects.pinkArc(actor(), target, 60, -30);
				LaevateinEffects.explosion(target, 2.0);
			}, () -> burn(10));
			default -> {
			}
		}
	}

	/** Rule Violation: two grey counter sweeps; Bleed, then stagger recovery. Plays twice when repeating. */
	private void ruleViolation(int phase) {
		switch (phase) {
			case 3 -> coin(4.0, 0, () -> {
				LaevateinEffects.greyArc(actor(), target, -100, 20);
				LaevateinEffects.pinkArc(actor(), target, 60, -15);
			}, () -> {
				bleed(4);
				if (Matthias.hasTattoo(player)) {
					bleedCount(1);
				}
			});
			case 8 -> coin(4.0, 0, () -> {
				LaevateinEffects.greyArc(actor(), target, 80, -25);
				LaevateinEffects.smoke(target);
			}, () -> {
				heal(2.0);
				if (Matthias.hasTattoo(player)) {
					bleedCount(1);
				}
			});
			default -> {
			}
		}
	}

	/** Summary Execution [Laevatein]: burning cuts, a pink blast, dark wings, a golden thrust and a final white sweep. */
	private void execution() {
		switch (tick) {
			case 4 -> LaevateinEffects.lightningStrike(target);
			case 8 -> coin(6.0, 0, () -> LaevateinEffects.flameArc(actor(), target, -90, 20), () -> burn(5));
			case 14 -> coin(6.0 + 1.0, 0, () -> LaevateinEffects.flameArc(actor(), target, 70, -30), () -> burnCount(3));
			case 20 -> coin(6.0 + 1.0, 0, () -> {
				LaevateinEffects.pinkArc(actor(), target, -120, 40);
				LaevateinEffects.explosion(target, 2.0);
			}, () -> burn(10));
			case 28 -> LaevateinEffects.wings(actor());
			case 30 -> coin(6.0, 0, () -> LaevateinEffects.inferno(target), () -> burn(10));
			case 40 -> coin(6.0 + 2.0, 0, () -> {
				LaevateinEffects.thrust(actor(), target);
				LaevateinEffects.greyArc(actor(), target, -110, 35);
				LaevateinEffects.shatter(target);
			}, () -> {
				if (Matthias.hasTattoo(player)) {
					paralysis(5);
				}
			});
			default -> {
			}
		}
	}

	/**
	 * One coin: swing, play the effect, deal damage (plus [Big Brother]'s extra
	 * damage when given), then apply the on-hit keywords.
	 */
	private void coin(double damage, double bigBrotherExtra, Runnable effect, Runnable onHit) {
		actor().swingMainHand();
		effect.run();
		double total = damage + (Matthias.hasTattoo(player) ? bigBrotherExtra : 0.0);
		if (health(player) <= maxHealth(player) * 0.5) {
			total *= kind == Kind.EXECUTION ? 1.6 : 1.4;
		}
		Damage.deal(target, Matthias.scale(player, total), player, true);
		if (onHit != null && target.isValid() && !target.isDead()) {
			onHit.run();
		}
	}

	private void burn(int potency) {
		target.setFireTicks(Math.max(target.getFireTicks(), potency * 12));
	}

	private void burnCount(int count) {
		target.setFireTicks(target.getFireTicks() + count * 10);
	}

	private void bind(int potency) {
		target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, TURN, potency - 1));
	}

	private void paralysis(int potency) {
		target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, TURN, potency - 1));
	}

	private void bleed(int potency) {
		PotionEffect current = target.getPotionEffect(PotionEffectType.WITHER);
		int duration = Math.max(current == null ? 0 : current.getDuration(), potency * 20);
		target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, duration, 0));
	}

	private void bleedCount(int count) {
		PotionEffect current = target.getPotionEffect(PotionEffectType.WITHER);
		int duration = (current == null ? 0 : current.getDuration()) + count * 10;
		target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, duration, 0));
	}

	private void heal(double amount) {
		player.setHealth(Math.min(maxHealth(player), player.getHealth() + amount));
	}

	private static double health(LivingEntity entity) {
		return entity.getHealth();
	}

	private static double maxHealth(LivingEntity entity) {
		AttributeInstance attribute = entity.getAttribute(Attribute.MAX_HEALTH);
		return attribute == null ? 20.0 : attribute.getValue();
	}

	/** Stomp closes in: step to just in front of the target if there is room. */
	private void dashToTarget() {
		LivingEntity actor = actor();
		Vector from = actor.getLocation().toVector();
		Vector to = target.getLocation().toVector();
		Vector dir = from.clone().subtract(to).setY(0);
		if (dir.lengthSquared() < 1.0E-4) {
			return;
		}
		Vector spot = to.clone().add(dir.normalize().multiply(1.4));
		Block feet = player.getWorld().getBlockAt(spot.getBlockX(), (int) Math.floor(anchor.getY()), spot.getBlockZ());
		if (!feet.isPassable() || !feet.getRelative(0, 1, 0).isPassable()) {
			return;
		}
		Location destination = new Location(player.getWorld(), spot.getX(), anchor.getY(), spot.getZ());
		destination.setDirection(to.clone().subtract(spot));
		actor.teleport(destination);
		LaevateinEffects.aura(actor(), 3);
	}

	/** Stab and Summary Execution are filmed from a separate camera orbiting the clash. */
	private void shoot() {
		if (camera == null) {
			return;
		}
		Vector from = actor().getLocation().toVector();
		Vector to = target.getLocation().toVector();
		Vector focus = from.clone().midpoint(to).add(new Vector(0, 1.0, 0));
		double side = Math.atan2(from.getZ() - to.getZ(), from.getX() - to.getX()) + Math.PI / 2;
		boolean impact = kind == Kind.STAB ? tick >= 26 && tick < 30 : (tick >= 30 && tick < 34) || (tick >= 40 && tick < 44);
		camera.frame(Cinematic.orbit(focus, side + tick * 0.04, 5.0, 1.8), focus, impact ? 0.6 : 0.0);
	}

	private void hold() {
		Location here = target.getLocation();
		if (here.distanceSquared(anchor) > 0.01) {
			Location back = anchor.clone();
			back.setYaw(here.getYaw());
			back.setPitch(here.getPitch());
			target.teleport(back);
		}
		target.setVelocity(new Vector());
	}

	private void burnSelf() {
		if (selfBurn > 0 && player.isOnline()) {
			player.setFireTicks(Math.max(player.getFireTicks(), selfBurn));
		}
	}

	/** The body that dashes and swings: the camera's stand-in while one runs, else the player. */
	private LivingEntity actor() {
		return camera != null ? camera.actor() : player;
	}

	private void finish() {
		if (camera != null) {
			camera.end();
			camera = null;
			burnSelf();
		}
		if (!(target instanceof Player)) {
			target.setAI(hadAi);
		}
	}
}
