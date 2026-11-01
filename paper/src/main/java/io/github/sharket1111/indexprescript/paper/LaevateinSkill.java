package io.github.sharket1111.indexprescript.paper;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import io.papermc.paper.entity.TeleportFlag;

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
	private static final int[] EXECUTION_IMPACTS = {6, 16, 27, 40, 52};

	private final Player player;
	private final LivingEntity target;
	private final Kind kind;
	private final Location anchor;
	private final boolean hadAi;
	private final boolean repeat;
	private Cinematic camera;
	private ItemDisplay thrown;
	private Vector thrownDir;
	private Vector kickFrom;
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
			case EXECUTION -> 64;
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
	/**
	 * Summary Execution, after the in-game motion: a rising burning slash and a
	 * chained sweep, a kick, the blade hurled into the target, a drop kick that
	 * drives it deeper, the blade pulled back on its chains, and a final white
	 * crescent. Ticks: coins at 6, 16, 27, 40 and 52.
	 */
	private void execution() {
		switch (tick) {
			case 2 -> {
				LaevateinEffects.lightningStrike(target);
				dashToTarget();
			}
			case 6 -> coin(6.0, 0, () -> LaevateinEffects.flameArc(actor(), target, -90, 60), () -> burn(5));
			case 10 -> {
				actor().swingMainHand();
				LaevateinEffects.pinkArc(actor(), target, -150, -5);
				LaevateinEffects.chain(actor(), target);
			}
			// Kick: chamber the leg, then plant the foot in the target.
			case 13 -> pose(Pose.SNEAKING);
			case 16 -> {
				pose(Pose.STANDING);
				stepToward(0.6);
				hit(6.0 + 1.0, 0, () -> LaevateinEffects.kick(target), () -> burnCount(3));
			}
			// The blade is raised and hurled into the target.
			case 20 -> {
				actor().swingMainHand();
				LaevateinEffects.wings(actor());
			}
			case 24 -> throwBlade();
			case 27 -> hit(6.0 + 1.0, 0, () -> {
				LaevateinEffects.thrust(actor(), target);
				LaevateinEffects.explosion(target, 2.0);
			}, () -> burn(10));
			// Backstep for a run-up, then a drop kick into the hilt.
			case 29 -> stepBack();
			case 32 -> pose(Pose.SNEAKING);
			case 34, 35, 36, 37, 38, 39 -> dropKickFrame(tick - 33);
			case 40 -> {
				hit(6.0, 0, () -> {
					LaevateinEffects.dropKick(target);
					driveBlade();
				}, () -> burn(10));
				pose(Pose.STANDING);
				stepBackFrom(1.6);
			}
			// The chains pull the blade back to the hand.
			case 46 -> recallBlade();
			case 49 -> catchBlade();
			case 52 -> coin(6.0 + 2.0, 0, () -> {
				LaevateinEffects.whiteCrescent(actor(), target);
				LaevateinEffects.shatter(target);
			}, () -> {
				if (Matthias.hasTattoo(player)) {
					paralysis(5);
				}
			});
			default -> {
			}
		}
		if (tick >= 44 && tick <= 47) {
			LaevateinEffects.chain(actor(), thrown != null ? thrown : target);
		}
		if (thrown != null && tick > 24 && tick < 46) {
			LaevateinEffects.embers(thrown);
		}
	}

	/** Sets the stand-in's pose; the player's own pose is left to the client. */
	private void pose(Pose pose) {
		LivingEntity actor = actor();
		if (actor != player && Mannequin.validPoses().contains(pose)) {
			actor.setPose(pose, true);
		}
	}

	/** Direction from the actor to the target along the ground. */
	private Vector towardTarget() {
		Vector dir = target.getLocation().toVector().subtract(actor().getLocation().toVector()).setY(0);
		return dir.lengthSquared() < 1.0E-4 ? actor().getLocation().getDirection().setY(0).normalize() : dir.normalize();
	}

	/** Moves the actor to {@code spot} at the target's ground level facing {@code facing}, if there is room. */
	private boolean placeActor(Vector spot, double height, Vector facing) {
		Block feet = target.getWorld().getBlockAt(spot.getBlockX(), (int) Math.floor(anchor.getY() + height), spot.getBlockZ());
		if (!feet.isPassable() || !feet.getRelative(0, 1, 0).isPassable()) {
			return false;
		}
		Location destination = new Location(target.getWorld(), spot.getX(), anchor.getY() + height, spot.getZ());
		destination.setDirection(facing);
		actor().teleport(destination);
		return true;
	}

	private void stepToward(double distance) {
		Vector dir = towardTarget();
		Vector spot = actor().getLocation().toVector().add(dir.clone().multiply(distance));
		if (spot.distanceSquared(target.getLocation().toVector()) > 0.8) {
			placeActor(spot, 0, dir);
		}
	}

	/** Leaps back from the target for the run-up, as far as there is room (up to 4 blocks). */
	private void stepBack() {
		Vector from = actor().getLocation().toVector();
		for (double distance = 4.0; distance >= 2.0; distance -= 0.5) {
			if (stepBackFrom(distance)) {
				Effects.dashTrail(target.getWorld(), from, actor().getLocation().toVector());
				break;
			}
		}
		kickFrom = actor().getLocation().toVector();
	}

	private boolean stepBackFrom(double distance) {
		Vector dir = towardTarget();
		Vector spot = target.getLocation().toVector().subtract(dir.clone().multiply(distance));
		return placeActor(spot, 0, dir);
	}

	/**
	 * One frame of the drop kick: the body flies feet first along an arc to the
	 * target. A swimming pose lays the body flat with the head toward its facing,
	 * so the actor faces away from the target to lead with the feet.
	 */
	private void dropKickFrame(int frame) {
		if (kickFrom == null) {
			kickFrom = actor().getLocation().toVector();
		}
		Vector to = target.getLocation().toVector();
		Vector dir = to.clone().subtract(kickFrom).setY(0);
		if (dir.lengthSquared() < 1.0E-4) {
			return;
		}
		double length = dir.length();
		dir.normalize();
		double t = frame / 6.0;
		Vector spot = kickFrom.clone().add(dir.clone().multiply(Math.max(0.0, length - 0.9) * t));
		pose(Pose.SWIMMING);
		Vector from = actor().getLocation().toVector();
		boolean standIn = actor() != player;
		Vector facing = standIn ? dir.clone().multiply(-1) : dir;
		placeActor(spot, 0.5 + Math.sin(Math.PI * t) * 1.0, facing);
		Effects.dashTrail(target.getWorld(), from, actor().getLocation().toVector());
		if (player.isOnline()) {
			player.setFallDistance(0.0F);
		}
	}

	/** The point in front of the actor where a held blade would be. */
	private Location handPoint() {
		Location eye = actor().getEyeLocation();
		Vector dir = towardTarget();
		Vector right = new Vector(-dir.getZ(), 0, dir.getX());
		return eye.clone().add(dir.multiply(0.5)).add(right.multiply(0.35)).subtract(0, 0.5, 0);
	}

	/** A display of the current blade flying point first along {@code dir}. */
	private void aimBlade(Location at, Vector dir) {
		Vector flat = dir.clone().setY(0);
		double yaw = Math.toDegrees(Math.atan2(flat.getZ(), flat.getX()));
		double dip = Math.atan2(dir.getY(), Math.max(1.0E-4, flat.length()));
		at.setYaw((float) yaw);
		at.setPitch(0.0F);
		thrown.teleport(at);
		// The blade model points up and to the right; turn its tip to the local +X axis, then dip it.
		thrown.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f((float) (-Math.PI / 4 + dip), 0, 0, 1),
				new Vector3f(1.6F, 1.6F, 1.6F), new AxisAngle4f()));
	}

	private void throwBlade() {
		ItemStack held = player.getInventory().getItemInMainHand();
		Location from = handPoint();
		thrown = target.getWorld().spawn(from, ItemDisplay.class, display -> {
			display.setPersistent(false);
			display.setItemStack(held.clone());
			display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
			display.setTeleportDuration(3);
		});
		if (actor() != player) {
			actor().getEquipment().setItemInMainHand(null);
		}
		Vector toward = target.getLocation().toVector().add(new Vector(0, target.getHeight() * 0.55, 0)).subtract(from.toVector());
		thrownDir = toward.clone().normalize();
		aimBlade(from.clone(), thrownDir);
		LaevateinEffects.throwSound(actor());
		// Next tick it flies (interpolated) to stick out of the target.
		Location stuck = target.getLocation().add(0, target.getHeight() * 0.55, 0).subtract(thrownDir.clone().multiply(0.6));
		Bukkit.getScheduler().runTaskLater(IndexPrescriptPlugin.instance(), () -> {
			if (thrown != null && thrown.isValid()) {
				aimBlade(stuck, thrownDir);
			}
		}, 1L);
	}

	/** The drop kick hammers the blade deeper into the target. */
	private void driveBlade() {
		if (thrown != null && thrownDir != null) {
			Location deeper = thrown.getLocation().add(thrownDir.clone().multiply(0.45));
			aimBlade(deeper, thrownDir);
		}
	}

	private void recallBlade() {
		if (thrown != null) {
			Location hand = handPoint();
			aimBlade(hand, thrown.getLocation().toVector().subtract(hand.toVector()));
			LaevateinEffects.recallSound(actor());
		}
	}

	private void catchBlade() {
		removeBlade();
		if (camera != null) {
			camera.syncHeld();
		}
	}

	private void removeBlade() {
		if (thrown != null) {
			thrown.remove();
			thrown = null;
		}
	}

	/**
	 * One coin: swing, play the effect, deal damage (plus [Big Brother]'s extra
	 * damage when given), then apply the on-hit keywords.
	 */
	private void coin(double damage, double bigBrotherExtra, Runnable effect, Runnable onHit) {
		actor().swingMainHand();
		hit(damage, bigBrotherExtra, effect, onHit);
	}

	/** A coin without the arm swing, for kicks and the thrown blade. */
	private void hit(double damage, double bigBrotherExtra, Runnable effect, Runnable onHit) {
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
		boolean impact = kind == Kind.STAB ? tick >= 26 && tick < 30 : executionImpact();
		camera.frame(Cinematic.orbit(focus, side + tick * 0.04, 5.0, 1.8), focus, impact ? 0.6 : 0.0);
	}

	private boolean executionImpact() {
		for (int at : EXECUTION_IMPACTS) {
			if (tick >= at && tick < at + 4) {
				return true;
			}
		}
		return false;
	}

	private void hold() {
		Location here = target.getLocation();
		if (here.distanceSquared(anchor) > 0.01) {
			Location back = anchor.clone();
			back.setYaw(here.getYaw());
			back.setPitch(here.getPitch());
			target.teleport(back, TeleportFlag.EntityState.RETAIN_PASSENGERS);
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
		removeBlade();
		if (actor() == player && player.isOnline()) {
			player.setFallDistance(0.0F);
		}
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
