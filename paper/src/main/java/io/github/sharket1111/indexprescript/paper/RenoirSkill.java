package io.github.sharket1111.indexprescript.paper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import io.papermc.paper.entity.TeleportFlag;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * The Brand Manager's hammer skills (fan-made, after Limbus Company), each a
 * timed sequence after the in-game animation, filmed from a separate camera
 * (Bedrock players see their own body act it out).
 *
 * Sever Birth (탄생을 끊으리라): nails fall on the foe and burst in a pillar of
 * light (coin I, a [Promised Nail]), a golden sweep (coin II), then a leap and
 * a great overhead slam in a dome of golden dust (coin III, Preservation +4).
 *
 * Strike Again and Again, Sever Birth (거듭 내리쳐, 탄생을 끊으리라): great
 * nails rain on the field and into every foe within 8 blocks of the target
 * (coins I and II, a nail each), the wielder leaps high (close-up), then
 * slams down in a white burst (coin III, Preservation +4); foes with 3+ nails
 * take extra damage (3% of the hit per nail, up to 90%) and lose their nails.
 * Using either spends all of the Black God's Voice (Strike Again needs 100, Sever Birth 50).
 *
 * Coin power is +15% damage per point, as elsewhere in the plugin.
 */
final class RenoirSkill {
	enum Kind {
		/** 못박히라 (Voice under 30), 못생기라 (30-59), 탄생을 끊으리라 (60-99), 거듭 내리쳐 (100). */
		NAIL, BORN, SEVER, AGAIN
	}

	private static final Map<UUID, RenoirSkill> ACTIVE = new ConcurrentHashMap<>();
	private static final double AGAIN_RADIUS = 8.0;
	private static final double COIN_POWER = 0.15;
	private static final Particle.DustOptions GOLD = PromisedNails.GOLD;
	private static final Particle.DustOptions WHITE = new Particle.DustOptions(Color.fromRGB(0xFFFFFF), 2.2F);
	private static final Particle.DustOptions PALE_GOLD = new Particle.DustOptions(Color.fromRGB(0xFFF0B0), 1.6F);

	private final Player player;
	private LivingEntity target;
	private final Kind kind;
	private final Location center;
	private final Map<LivingEntity, Location> held = new LinkedHashMap<>();
	private final Map<LivingEntity, Boolean> hadAi = new LinkedHashMap<>();
	private final double bonus;
	private Cinematic camera;
	private double cameraSide = Double.NaN;
	private Vector leapFrom;
	private ItemDisplay hammer;
	private int tick;

	private RenoirSkill(Player player, LivingEntity target, Kind kind) {
		this.player = player;
		this.target = target;
		this.kind = kind;
		this.center = target.getLocation();
		for (LivingEntity foe : kind == Kind.AGAIN ? foesAround(player, target) : List.of(target)) {
			held.put(foe, foe.getLocation());
		}
		this.bonus = damageBonus();
	}

	static boolean active(Player player) {
		return ACTIVE.containsKey(player.getUniqueId());
	}

	static void start(Player player, LivingEntity target, Kind kind) {
		if (ACTIVE.containsKey(player.getUniqueId())) {
			return;
		}
		SkillCard card = switch (kind) {
			case NAIL -> SkillCard.NAIL;
			case BORN -> SkillCard.BORN;
			case SEVER -> SkillCard.SEVER;
			case AGAIN -> SkillCard.AGAIN;
		};
		Clash.request(player, target, card,
				outcome -> launch(player, outcome.target(), kind));
	}

	private static void launch(Player player, LivingEntity target, Kind kind) {
		if (ACTIVE.containsKey(player.getUniqueId()) || !player.isOnline() || !RenoirHammer.holding(player)) {
			return;
		}
		RenoirSkill skill = new RenoirSkill(player, target, kind);
		ACTIVE.put(player.getUniqueId(), skill);
		skill.open();
	}

	static void tickAll() {
		ACTIVE.values().removeIf(skill -> {
			try {
				return !skill.tick();
			} catch (RuntimeException e) {
				IndexPrescriptPlugin.instance().getLogger().log(Level.WARNING, "Renoir hammer skill failed", e);
				skill.finish();
				return true;
			}
		});
	}

	static void endAll() {
		ACTIVE.values().forEach(RenoirSkill::finish);
		ACTIVE.clear();
	}

	/** The target and every foe within 8 blocks of it. */
	private static List<LivingEntity> foesAround(Player player, LivingEntity target) {
		List<LivingEntity> foes = new ArrayList<>();
		foes.add(target);
		for (Entity entity : target.getNearbyEntities(AGAIN_RADIUS, AGAIN_RADIUS, AGAIN_RADIUS)) {
			if (RenoirHammer.foe(player, entity) && entity.getLocation().distanceSquared(target.getLocation()) <= AGAIN_RADIUS * AGAIN_RADIUS) {
				foes.add((LivingEntity) entity);
			}
		}
		return foes;
	}

	/**
	 * On use: +1 coin power per 6 nails in the foes (up to 2); with Preservation
	 * potency 3+, +1 coin power (and Sever Birth +40% damage); Strike Again
	 * deals +50% per foe short of three.
	 */
	private double damageBonus() {
		int nails = 0;
		for (LivingEntity foe : held.keySet()) {
			nails += PromisedNails.count(foe);
		}
		// 못박히라 and 못생기라: per 4 nails +1 final power (max 3); the others per 6, max 2.
		boolean small = kind == Kind.NAIL || kind == Kind.BORN;
		int power = small ? Math.min(3, nails / 4) : Math.min(2, nails / 6);
		double multiplier = 1.0;
		if (RenoirHammer.potency(player) >= 3) {
			power++;
			multiplier *= switch (kind) {
				case SEVER, NAIL -> 1.4;
				case BORN -> 1.3;
				default -> 1.0;
			};
		}
		multiplier *= 1.0 + power * COIN_POWER;
		if (kind == Kind.AGAIN && held.size() < 3) {
			multiplier *= 1.0 + 0.5 * (3 - held.size());
		}
		return multiplier;
	}

	private void open() {
		for (LivingEntity foe : held.keySet()) {
			if (!(foe instanceof Player)) {
				hadAi.put(foe, foe.hasAI());
				foe.setAI(false);
			}
		}
		camera = Cinematic.start(player, player.getLocation().add(0, 2, 0));
		boolean big = kind == Kind.AGAIN;
		if (big) {
			TotalDamage.start(player);
		}
		SkillOverlay.show(player, big ? SkillOverlay.Size.BIG : SkillOverlay.Size.SMALL);
		String line = switch (kind) {
			case NAIL -> "못박히라!";
			case BORN -> "못생기라!";
			default -> Messages.get(player, big ? "renoir.again" : "renoir.sever");
		};
		Hud.message(player, Component.text(line, NamedTextColor.GOLD));
		World world = actor().getWorld();
		Location at = actor().getLocation().add(0, 1, 0);
		world.spawnParticle(Particle.DUST, at, big ? 60 : 25, 0.6, 0.8, 0.6, 0.0, GOLD);
		world.spawnParticle(Particle.END_ROD, at, big ? 20 : 8, 0.4, 0.6, 0.4, 0.05);
		world.playSound(at, Sound.BLOCK_BELL_RESONATE, SoundCategory.PLAYERS, 1.0F, big ? 0.6F : 0.9F);
		world.playSound(at, Sound.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.8F, big ? 0.7F : 1.3F);
	}

	private int endTick() {
		return switch (kind) {
			case NAIL -> NAIL_END;
			case BORN -> BORN_END;
			case SEVER -> SEVER_END;
			case AGAIN -> AGAIN_END;
		};
	}

	private boolean tick() {
		if (!player.isOnline() || player.isDead() || !RenoirHammer.holding(player)) {
			finish();
			return false;
		}
		held.keySet().removeIf(foe -> !foe.isValid() || foe.isDead());
		if (!target.isValid() || target.isDead()) {
			if (kind != Kind.AGAIN || held.isEmpty()) {
				finish();
				return false;
			}
			target = held.keySet().iterator().next();
		}
		hold();
		switch (kind) {
			case NAIL -> nail();
			case BORN -> born();
			case SEVER -> sever();
			case AGAIN -> again();
		}
		if (tick >= endTick()) {
			finish();
			return false;
		}
		tick++;
		return true;
	}

	// --- 못박히라 / 못생기라 ------------------------------------------------------
	// No scene of these was sent; they are paced like the others' lesser coins.

	private static final int NAIL_COIN_1 = 14;
	private static final int NAIL_COIN_2 = 34;
	private static final int NAIL_END = 56;

	/** 못박히라: a golden sweep under falling nails, then an overhead blow; Preservation +6. */
	private void nail() {
		shootSide(3.2, tick >= NAIL_COIN_2 && tick < NAIL_COIN_2 + 5 ? 0.5 : 0.0);
		if (tick == 0) {
			face();
		} else if (tick == 6) {
			stepIn(1.6);
		} else if (tick == NAIL_COIN_1 - 4) {
			PromisedNails.rain(target.getLocation(), 1.4, 2.0F, false);
		} else if (tick == NAIL_COIN_1 && Clash.coin(player, 0)) {
			coin(4.0, () -> sweep(actor(), target, -40, 0));
		} else if (tick == NAIL_COIN_2) {
			if (Clash.coin(player, 1)) {
				coin(5.0, () -> {
					sweep(actor(), target, 90, 80);
					pillar(target.getLocation());
				});
				RenoirHammer.addPreservation(player, 6);
			}
		} else if (tick > NAIL_COIN_2 && tick < NAIL_END - 6 && tick % 4 == 0) {
			settle(target.getLocation(), 1.8);
		}
		// The great hammer comes over the shoulder for the second blow.
		swingHammer(NAIL_COIN_2 - 10, NAIL_COIN_2);
	}

	private static final int BORN_COIN_1 = 18;
	private static final int BORN_LEAP = 30;
	private static final int BORN_COIN_2 = 46;
	private static final int BORN_END = 72;

	/** 못생기라: a [Promised Nail] driven in with a pillar of light, then a leap and a slam in golden dust; Preservation +8. */
	private void born() {
		shootSide(3.4, tick >= BORN_COIN_2 && tick < BORN_COIN_2 + 6 ? 0.6 : 0.0);
		if (tick == 0) {
			face();
		} else if (tick == 8) {
			stepIn(1.6);
		} else if (tick == BORN_COIN_1 && Clash.coin(player, 0)) {
			RenoirHammer.driveNail(player, target);
			coin(5.0, () -> pillar(target.getLocation()));
		} else if (tick == BORN_LEAP - 2) {
			pose(Pose.SNEAKING);
		} else if (tick == BORN_LEAP) {
			pose(Pose.STANDING);
			startLeap();
		} else if (tick > BORN_LEAP && tick < BORN_COIN_2) {
			leap(tick - BORN_LEAP, BORN_COIN_2 - BORN_LEAP, 2.4, 1.4);
		} else if (tick == BORN_COIN_2) {
			land(1.4);
			if (Clash.coin(player, 1)) {
				coin(7.0, () -> {
					sweep(actor(), target, 90, 80);
					dome(target.getLocation(), 2.4);
				});
				RenoirHammer.addPreservation(player, 8);
			}
		} else if (tick > BORN_COIN_2 && tick < BORN_END - 8 && tick % 3 == 0) {
			settle(target.getLocation(), 2.2);
		}
		swingHammer(BORN_LEAP + 4, BORN_COIN_2);
	}

	// --- Sever Birth -------------------------------------------------------------

	/*
	 * Timings (ticks) measured from the in-game scene: the nails fall at 0.5 s, the
	 * pillar of light at 1.35 s, the golden sweep at 2.65 s, the leap (cut-in) from
	 * 3.9 s, the hammer overhead at 4.55 s and down at 5.1 s, the dome until 6.85 s.
	 */
	private static final int SEVER_NAILS = 10;
	private static final int SEVER_COIN_1 = 27;
	private static final int SEVER_STEP = 46;
	private static final int SEVER_COIN_2 = 53;
	private static final int SEVER_CROUCH = 76;
	private static final int SEVER_LEAP = 78;
	private static final int SEVER_SLAM = 102;
	private static final int SEVER_END = 137;

	private void sever() {
		shootSide(3.4, severShake());
		if (tick == 0) {
			face();
		} else if (tick == SEVER_NAILS) {
			// Two nails fall: one drives into the foe, the other strikes beside it.
			RenoirHammer.driveNail(player, target);
			PromisedNails.rain(target.getLocation(), 1.2, 2.4F, false);
		} else if (tick == SEVER_COIN_1 && Clash.coin(player, 0)) {
			coin(4.0, () -> pillar(target.getLocation()));
		} else if (tick == SEVER_STEP) {
			stepIn(1.6);
		} else if (tick == SEVER_COIN_2 && Clash.coin(player, 1)) {
			coin(4.0, () -> sweep(actor(), target, -30, 0));
		} else if (tick == SEVER_CROUCH) {
			pose(Pose.SNEAKING);
		} else if (tick == SEVER_LEAP) {
			startLeap();
		} else if (tick > SEVER_LEAP && tick < SEVER_SLAM) {
			if (tick == SEVER_LEAP + 3) {
				pose(Pose.STANDING);
			}
			leap(tick - SEVER_LEAP, SEVER_SLAM - SEVER_LEAP, 2.8, 1.4);
		} else if (tick == SEVER_SLAM) {
			land(1.4);
			if (Clash.coin(player, 2)) {
				coin(7.0, () -> {
					sweep(actor(), target, 90, 80);
					dome(target.getLocation(), 2.6);
				});
				RenoirHammer.addPreservation(player, 4);
			}
		} else if (tick > SEVER_SLAM && tick < SEVER_END - 8 && tick % 3 == 0) {
			settle(target.getLocation(), 2.4);
		}
		// The hammer: raised through the leap, overhead from 4.55 s, brought down onto the foe.
		swingHammer(SEVER_LEAP + 4, SEVER_SLAM);
	}

	private double severShake() {
		return tick >= SEVER_COIN_1 && tick < SEVER_COIN_1 + 3 || tick >= SEVER_SLAM && tick < SEVER_SLAM + 6 ? 0.6 : 0.0;
	}

	// --- Strike Again and Again --------------------------------------------------

	/*
	 * Timings (ticks) measured from the in-game scene: the field flashes and the
	 * nails rain from 0.95 s, coin I at 2.2 s, the second nails and coin II at
	 * 3.75 s, the leap (cut-in) from 4.95 s, the close-up from 5.5 s with the
	 * hammer raised, the hammer brought down from 6.85 s, the slam at 7 s, the
	 * field white until 7.85 s, the end at 9 s.
	 */
	private static final int AGAIN_FLASH = 19;
	private static final int AGAIN_NAIL_1 = 24;
	private static final int AGAIN_COIN_1 = 44;
	private static final int AGAIN_NAIL_2 = 60;
	private static final int AGAIN_STEP = 70;
	private static final int AGAIN_COIN_2 = 75;
	private static final int AGAIN_CROUCH = 92;
	private static final int AGAIN_LEAP = 99;
	private static final int AGAIN_CLOSE = 110;
	private static final int AGAIN_DOWN = 137;
	private static final int AGAIN_SLAM = 140;
	private static final int AGAIN_END = 180;

	private void again() {
		framesAgain();
		if (tick >= AGAIN_FLASH && tick <= AGAIN_COIN_2 && tick % 2 == 0) {
			int drops = tick < AGAIN_FLASH + 10 ? 2 : 3;
			for (int i = 0; i < drops; i++) {
				PromisedNails.rain(center, AGAIN_RADIUS, 3.6F, true);
			}
		}
		if (tick == 0) {
			face();
		} else if (tick == AGAIN_FLASH) {
			flash(center);
		} else if (tick == AGAIN_NAIL_1 || tick == AGAIN_NAIL_2) {
			for (LivingEntity foe : List.copyOf(held.keySet())) {
				if (PromisedNails.count(foe) < PromisedNails.MAX) {
					RenoirHammer.driveNail(player, foe);
				}
			}
		} else if (tick == AGAIN_COIN_1 && Clash.coin(player, 0)) {
			for (LivingEntity foe : List.copyOf(held.keySet())) {
				hit(foe, 4.0, () -> pillar(foe.getLocation()));
			}
			shockRing(center, 4.0);
			actor().swingMainHand();
		} else if (tick == AGAIN_STEP) {
			// The second nails strike; coin II lands with the hammer's sweep after them.
			for (LivingEntity foe : held.keySet()) {
				pillar(foe.getLocation());
			}
			shockRing(center, 7.0);
			stepIn(1.8);
		} else if (tick > AGAIN_STEP + 1 && tick < AGAIN_COIN_2) {
			spin(tick - AGAIN_STEP - 2);
		} else if (tick == AGAIN_COIN_2 && Clash.coin(player, 1)) {
			face();
			actor().swingMainHand();
			sweep(actor(), target, -100, 0);
			sweep(actor(), target, -100, 20);
			whirl(actor());
			for (LivingEntity foe : List.copyOf(held.keySet())) {
				hit(foe, 4.0, null);
			}
		} else if (tick == AGAIN_CROUCH) {
			pose(Pose.SNEAKING);
		} else if (tick == AGAIN_LEAP) {
			pose(Pose.STANDING);
			startLeap();
		} else if (tick > AGAIN_LEAP && tick < AGAIN_DOWN) {
			rise(tick - AGAIN_LEAP);
		} else if (tick >= AGAIN_DOWN && tick < AGAIN_SLAM) {
			// Coming down out of the air with the hammer.
			fall(tick - AGAIN_DOWN, AGAIN_SLAM - AGAIN_DOWN);
		} else if (tick == AGAIN_SLAM) {
			land(1.5);
			if (Clash.coin(player, 2)) {
				slam();
			}
		} else if (tick > AGAIN_SLAM && tick < AGAIN_END - 4 && tick % 4 == 0) {
			settle(center, AGAIN_RADIUS * 0.6);
		}
		// The hammer: raised behind the head in the close-up, then brought down for the slam.
		swingHammer(AGAIN_CLOSE + 8, AGAIN_SLAM);
	}

	/** Strike Again's descent from the top of the leap, onto the ground before the target. */
	private void fall(int frame, int frames) {
		double t = (frame + 1) / (double) (frames + 1);
		Location here = actor().getLocation();
		Location ground = held.getOrDefault(target, target.getLocation());
		Vector goal = target.getLocation().toVector().subtract(towardTarget().multiply(1.5));
		Vector spot = here.toVector().multiply(1 - t).add(goal.multiply(t));
		place(spot, Math.max(0.0, (here.getY() - ground.getY()) * (1 - t)));
		PromisedNails.glow(actor().getLocation().add(0, 1, 0), 3, 0.3);
	}

	/** Coin III: the white burst. Foes with 3+ nails take 3% more per nail (up to 90%) and lose them all. */
	private void slam() {
		whiteBurst(target.getLocation());
		actor().swingMainHand();
		for (LivingEntity foe : List.copyOf(held.keySet())) {
			int nails = PromisedNails.count(foe);
			double extra = nails >= 3 ? Math.min(0.9, nails * 0.03) : 0.0;
			hit(foe, 9.0 * (1.0 + extra), null);
			if (nails >= 3) {
				PromisedNails.spend(foe);
			}
		}
		RenoirHammer.addPreservation(player, 4);
	}

	/**
	 * Strike Again's camera: a high wide shot of the field during the rain of
	 * nails, a close-up of the wielder rising (the cut-in), then the wide shot
	 * again, shaking, for the slam.
	 */
	private void framesAgain() {
		if (camera == null) {
			return;
		}
		LivingEntity actor = actor();
		Vector focus = target.getLocation().toVector().midpoint(actor.getLocation().toVector()).add(new Vector(0, 1.0, 0));
		if (Double.isNaN(cameraSide)) {
			Vector from = actor.getLocation().toVector();
			Vector to = target.getLocation().toVector();
			cameraSide = Math.atan2(from.getZ() - to.getZ(), from.getX() - to.getX()) + Math.PI / 2;
		}
		if (tick >= AGAIN_CLOSE && tick < AGAIN_DOWN) {
			// Close-up: in front of the wielder's face, a little below, looking up at it.
			Location eye = actor.getEyeLocation();
			Vector facing = eye.getDirection().setY(0);
			if (facing.lengthSquared() < 1.0E-4) {
				facing = new Vector(1, 0, 0);
			}
			facing.normalize();
			Vector side = new Vector(-facing.getZ(), 0, facing.getX());
			Vector at = eye.toVector().add(facing.clone().multiply(2.2)).add(side.multiply(0.5)).add(new Vector(0, -0.5, 0));
			camera.frame(at, eye.toVector(), 0.0);
			return;
		}
		boolean impact = tick >= AGAIN_FLASH && tick < AGAIN_FLASH + 3 || tick >= AGAIN_COIN_1 && tick < AGAIN_COIN_1 + 4
				|| tick >= AGAIN_STEP && tick < AGAIN_STEP + 2 || tick >= AGAIN_COIN_2 && tick < AGAIN_COIN_2 + 4
				|| tick >= AGAIN_SLAM && tick < AGAIN_SLAM + 10;
		// Closer in for the hammer's sweep and the swing down, wide for the rain and the slam.
		double distance = tick >= AGAIN_STEP && tick < AGAIN_CROUCH ? 4.8 : tick >= AGAIN_DOWN && tick < AGAIN_SLAM ? 5.0
				: tick < AGAIN_CLOSE ? 7.5 : 6.5;
		camera.frame(Cinematic.orbit(focus, cameraSide, distance, 3.0), focus, impact ? 0.8 : 0.0);
	}

	// --- the hammer swing --------------------------------------------------------

	private static final float HAMMER_SCALE = 1.3F;
	/** The grip on the hammer model (blocks from its centre, before scaling): low on the handle. */
	private static final float HAMMER_GRIP = -0.7F;
	private static final float HAMMER_RAISED = 60F;
	private static final float[] HAMMER_DOWN = {20F, -40F, -95F, -130F};

	/**
	 * The great hammer, seen swung: from {@code raiseFrom} it comes up behind the
	 * head (8 ticks) and is held there, then in the last 3 ticks before
	 * {@code slamAt} it comes down over the shoulder, its head on the ground
	 * before the wielder at {@code slamAt}; it stays there a moment after.
	 * Angles are from upright, positive tilting back; it pivots at the right shoulder.
	 */
	private void swingHammer(int raiseFrom, int slamAt) {
		int down = slamAt - (HAMMER_DOWN.length - 1);
		if (tick < raiseFrom || tick > slamAt + 6) {
			dropHammer();
			return;
		}
		float angle;
		if (tick >= down) {
			angle = HAMMER_DOWN[Math.min(HAMMER_DOWN.length - 1, tick - down)];
		} else {
			double t = Math.min(1.0, (tick - raiseFrom) / 8.0);
			double eased = 1 - (1 - t) * (1 - t);
			angle = (float) (-100 + (HAMMER_RAISED + 100) * eased + (t >= 1 ? Math.sin(tick * 0.4) * 3 : 0));
		}
		showHammer(angle);
		if (tick == down) {
			Location at = actor().getLocation().add(0, 2, 0);
			at.getWorld().playSound(at, Sound.ITEM_MACE_SMASH_AIR, SoundCategory.PLAYERS, 1.2F, 0.6F);
			at.getWorld().playSound(at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.0F, 0.5F);
		}
		if (tick > down && tick <= slamAt) {
			// A golden trail along the hammer head's arc.
			Location head = hammerHead(angle);
			head.getWorld().spawnParticle(Particle.DUST, head, 8, 0.25, 0.25, 0.25, 0.0, GOLD);
			head.getWorld().spawnParticle(Particle.END_ROD, head, 2, 0.2, 0.2, 0.2, 0.01);
		}
	}

	private Location shoulder() {
		LivingEntity actor = actor();
		Vector forward = towardTarget();
		Vector right = new Vector(-forward.getZ(), 0, forward.getX());
		return actor.getLocation().add(0, actor.getHeight() * 0.8, 0).add(right.multiply(0.35));
	}

	private Quaternionf hammerRotation(float angle) {
		Vector forward = towardTarget();
		// The model's head lies along its X axis: turned so X points at the target, then tilted round the side axis.
		return new Quaternionf().rotateY((float) Math.atan2(-forward.getZ(), forward.getX())).rotateZ((float) Math.toRadians(angle));
	}

	/** Where the hammer's head is at {@code angle}. */
	private Location hammerHead(float angle) {
		Vector3f head = hammerRotation(angle).transform(new Vector3f(0, (0.94F - HAMMER_GRIP) * HAMMER_SCALE, 0));
		return shoulder().add(head.x, head.y, head.z);
	}

	private void showHammer(float angle) {
		Location at = shoulder();
		at.setYaw(0);
		at.setPitch(0);
		if (hammer == null || !hammer.isValid()) {
			ItemStack held = player.getInventory().getItemInMainHand().clone();
			hammer = at.getWorld().spawn(at, ItemDisplay.class, d -> {
				d.setPersistent(false);
				d.setItemStack(held);
				d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
				d.setTeleportDuration(1);
				d.setBrightness(new Display.Brightness(15, 15));
			});
			LivingEntity actor = actor();
			if (actor != player) {
				// The stand-in's own hammer is put away while the great one swings.
				actor.getEquipment().setItemInMainHand(null);
			}
		} else {
			hammer.teleport(at);
		}
		Quaternionf rotation = hammerRotation(angle);
		Vector3f grip = rotation.transform(new Vector3f(0, HAMMER_GRIP * HAMMER_SCALE, 0)).negate();
		hammer.setInterpolationDelay(0);
		hammer.setInterpolationDuration(1);
		hammer.setTransformation(new Transformation(grip, rotation, new Vector3f(HAMMER_SCALE, HAMMER_SCALE, HAMMER_SCALE), new Quaternionf()));
	}

	private void dropHammer() {
		if (hammer == null) {
			return;
		}
		hammer.remove();
		hammer = null;
		LivingEntity actor = actor();
		if (actor != player && actor.isValid()) {
			actor.getEquipment().setItemInMainHand(player.getInventory().getItemInMainHand().clone());
		}
	}

	// --- moves -------------------------------------------------------------------

	/** Sets the stand-in's pose; the player's own pose is left to the client. */
	private void pose(Pose pose) {
		LivingEntity actor = actor();
		if (actor != player && Mannequin.validPoses().contains(pose)) {
			actor.setPose(pose, true);
		}
	}

	private Vector towardTarget() {
		Vector dir = target.getLocation().toVector().subtract(actor().getLocation().toVector()).setY(0);
		return dir.lengthSquared() < 1.0E-4 ? actor().getLocation().getDirection().setY(0).normalize() : dir.normalize();
	}

	private void face() {
		Location here = actor().getLocation();
		here.setDirection(towardTarget());
		actor().teleport(here);
	}

	/** Moves the actor to {@code spot}, {@code height} above the target's ground, facing the target, if there is room. */
	private boolean place(Vector spot, double height) {
		Location ground = held.getOrDefault(target, target.getLocation());
		Block feet = ground.getWorld().getBlockAt(spot.getBlockX(), (int) Math.floor(ground.getY() + height), spot.getBlockZ());
		if (height < 0.5 && (!feet.isPassable() || !feet.getRelative(0, 1, 0).isPassable())) {
			return false;
		}
		Location destination = new Location(ground.getWorld(), spot.getX(), ground.getY() + height, spot.getZ());
		destination.setDirection(target.getLocation().toVector().subtract(spot).setY(0).lengthSquared() < 1.0E-4
				? actor().getLocation().getDirection() : target.getLocation().toVector().subtract(spot).setY(0));
		actor().teleport(destination);
		return true;
	}

	/** Steps to {@code distance} blocks from the target. */
	private void stepIn(double distance) {
		Vector from = actor().getLocation().toVector();
		Vector spot = target.getLocation().toVector().subtract(towardTarget().multiply(distance));
		if (place(spot, 0)) {
			Effects.dashTrail(target.getWorld(), from, actor().getLocation().toVector());
		}
	}

	private void startLeap() {
		leapFrom = actor().getLocation().toVector();
		Location at = actor().getLocation();
		at.getWorld().spawnParticle(Particle.CLOUD, at, 10, 0.4, 0.05, 0.4, 0.02);
		at.getWorld().playSound(at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 0.8F, 0.6F);
	}

	/** Sever Birth's jump: up and over to {@code end} blocks before the target. */
	private void leap(int frame, int frames, double height, double end) {
		if (leapFrom == null) {
			startLeap();
		}
		double t = frame / (double) frames;
		Vector goal = target.getLocation().toVector().subtract(towardTarget().multiply(end));
		Vector spot = leapFrom.clone().multiply(1 - t).add(goal.clone().multiply(t));
		place(spot, Math.sin(Math.PI * Math.min(1.0, t * 1.3)) * height + 0.2);
		PromisedNails.glow(actor().getLocation().add(0, 1, 0), 2, 0.3);
	}

	/** Strike Again's jump: high above the target, hanging there for the close-up. */
	private void rise(int frame) {
		if (leapFrom == null) {
			startLeap();
		}
		double t = Math.min(1.0, frame / 8.0);
		Vector goal = target.getLocation().toVector().subtract(towardTarget().multiply(1.5));
		Vector spot = leapFrom.clone().multiply(1 - t).add(goal.clone().multiply(t));
		double height = 6.0 * Math.sin(Math.PI / 2 * t) + (frame > 8 ? Math.sin(frame * 0.3) * 0.08 : 0.0);
		place(spot, height);
		Location at = actor().getLocation().add(0, 1, 0);
		World world = at.getWorld();
		// The cut-in: gold swirling behind the wielder.
		for (int i = 0; i < 4; i++) {
			double a = frame * 0.5 + i * Math.PI / 2;
			world.spawnParticle(Particle.DUST, at.clone().add(Math.cos(a) * 1.4, Math.sin(frame * 0.2 + i) * 0.6, Math.sin(a) * 1.4),
					2, 0.05, 0.05, 0.05, 0.0, PALE_GOLD);
		}
		if (frame % 6 == 0) {
			world.spawnParticle(Particle.END_ROD, at, 4, 0.5, 0.6, 0.5, 0.02);
			world.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.8F, 0.5F + frame * 0.03F);
		}
	}

	/**
	 * Winding up the sweep: the body turns away and round (a quarter turn a
	 * frame) with the hammer swung back, golden sparks trailing it.
	 */
	private void spin(int frame) {
		LivingEntity actor = actor();
		Location at = actor.getLocation();
		Vector toward = towardTarget();
		double base = Math.toDegrees(Math.atan2(-toward.getX(), toward.getZ()));
		at.setYaw((float) (base + 90 * (frame + 1)));
		at.setPitch(0);
		actor.teleport(at);
		if (frame == 0) {
			actor.swingMainHand();
		}
		Location hammer = at.clone().add(0, 1.2, 0).add(at.getDirection().multiply(-1.2));
		actor.getWorld().spawnParticle(Particle.DUST, hammer, 6, 0.2, 0.2, 0.2, 0.0, GOLD);
		actor.getWorld().playSound(at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 0.5F, 0.5F + frame * 0.15F);
	}

	/** The sweep's wake: sweep marks all round the wielder and a ring of golden dust. */
	private static void whirl(LivingEntity actor) {
		Location at = actor.getLocation().add(0, 1.0, 0);
		World world = actor.getWorld();
		for (int i = 0; i < 8; i++) {
			double a = i * Math.PI / 4;
			world.spawnParticle(Particle.SWEEP_ATTACK, at.clone().add(Math.cos(a) * 2.0, 0, Math.sin(a) * 2.0), 1, 0, 0, 0, 0.0);
		}
		ringAt(actor.getLocation(), 2.6, PALE_GOLD);
		world.playSound(at, Sound.ITEM_MACE_SMASH_AIR, SoundCategory.PLAYERS, 1.0F, 0.8F);
		world.playSound(at, Sound.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 0.6F, 0.9F);
	}

	/** Comes down to the ground {@code distance} blocks before the target. */
	private void land(double distance) {
		Vector spot = target.getLocation().toVector().subtract(towardTarget().multiply(distance));
		if (!place(spot, 0)) {
			place(actor().getLocation().toVector(), 0);
		}
	}

	private void coin(double damage, Runnable effect) {
		actor().swingMainHand();
		hit(target, damage, effect);
	}

	private void hit(LivingEntity foe, double damage, Runnable effect) {
		if (camera != null) {
			camera.jolt(0.5);
		}
		if (effect != null) {
			effect.run();
		}
		if (foe.isValid() && !foe.isDead()) {
			Damage.deal(foe, damage * bonus, player, true);
		}
	}

	private void hold() {
		for (Map.Entry<LivingEntity, Location> entry : held.entrySet()) {
			LivingEntity foe = entry.getKey();
			Location anchor = entry.getValue();
			Location here = foe.getLocation();
			if (here.distanceSquared(anchor) > 0.01) {
				Location back = anchor.clone();
				back.setYaw(here.getYaw());
				back.setPitch(here.getPitch());
				foe.teleport(back, TeleportFlag.EntityState.RETAIN_PASSENGERS);
			}
			foe.setVelocity(new Vector());
		}
	}

	/** Sever Birth's camera: fixed to one side of the clash, square to it. */
	private void shootSide(double distance, double shake) {
		if (camera == null) {
			return;
		}
		Vector from = actor().getLocation().toVector();
		Vector to = target.getLocation().toVector();
		Vector focus = from.clone().midpoint(to).add(new Vector(0, 1.2, 0));
		if (Double.isNaN(cameraSide)) {
			cameraSide = Math.atan2(from.getZ() - to.getZ(), from.getX() - to.getX()) + Math.PI / 2;
		}
		camera.frame(Cinematic.orbit(focus, cameraSide, distance, 1.6), focus, shake);
	}

	private LivingEntity actor() {
		return camera != null ? camera.actor() : player;
	}

	private void finish() {
		dropHammer();
		if (kind == Kind.AGAIN) {
			TotalDamage.finish(player);
		} else {
			SkillOverlay.hide(player);
		}
		if (camera != null) {
			camera.end();
			camera = null;
		}
		if (player.isOnline()) {
			player.setFallDistance(0.0F);
		}
		hadAi.forEach((foe, ai) -> {
			if (foe.isValid()) {
				foe.setAI(ai);
			}
		});
		hadAi.clear();
	}

	// --- effects -----------------------------------------------------------------

	/** A pillar of light where a nail strikes. */
	private static void pillar(Location at) {
		World world = at.getWorld();
		for (double y = 0; y < 5; y += 0.25) {
			world.spawnParticle(Particle.END_ROD, at.clone().add(0, y, 0), 1, 0.06, 0.0, 0.06, 0.0);
			world.spawnParticle(Particle.DUST, at.clone().add(0, y, 0), 1, 0.2, 0.0, 0.2, 0.0, GOLD);
		}
		world.spawnParticle(Particle.ELECTRIC_SPARK, at.clone().add(0, 1, 0), 20, 0.4, 0.6, 0.4, 0.2);
		world.playSound(at, Sound.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 0.8F, 1.6F);
		world.playSound(at, Sound.ITEM_TRIDENT_HIT, SoundCategory.PLAYERS, 1.0F, 0.7F);
	}

	/**
	 * A golden crescent of the hammer's path in front of {@code attacker}:
	 * {@code tiltDeg} 0 sweeps across, 90 comes straight down.
	 */
	private static void sweep(Entity attacker, Entity target, double startDeg, double tiltDeg) {
		Location from = attacker.getLocation().add(0, 1.1, 0);
		Vector forward = target.getLocation().toVector().subtract(from.toVector()).setY(0);
		if (forward.lengthSquared() < 1.0E-4) {
			forward = from.getDirection().setY(0);
		}
		forward.normalize();
		Vector side = new Vector(-forward.getZ(), 0, forward.getX());
		Vector up = new Vector(0, 1, 0);
		double tilt = Math.toRadians(tiltDeg);
		Vector across = side.clone().multiply(Math.cos(tilt)).add(up.clone().multiply(Math.sin(tilt)));
		World world = attacker.getWorld();
		for (int i = 0; i <= 18; i++) {
			double a = Math.toRadians(startDeg + i * 10);
			for (double r = 1.4; r <= 2.4; r += 0.5) {
				Vector p = from.toVector().add(forward.clone().multiply(Math.cos(a) * r)).add(across.clone().multiply(Math.sin(a) * r));
				world.spawnParticle(Particle.DUST, p.toLocation(world), 1, 0.02, 0.02, 0.02, 0.0, r > 2 ? PALE_GOLD : GOLD);
			}
		}
		world.playSound(from, Sound.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.0F, 0.6F);
		world.playSound(from, Sound.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 0.5F, 1.2F);
	}

	/** Sever Birth's slam: a dome of golden dust thrown up round the foe. */
	private static void dome(Location at, double radius) {
		World world = at.getWorld();
		ThreadLocalRandom random = ThreadLocalRandom.current();
		for (int i = 0; i < 90; i++) {
			double a = random.nextDouble(Math.PI * 2);
			double b = random.nextDouble(Math.PI / 2);
			Vector p = new Vector(Math.cos(a) * Math.cos(b), Math.sin(b), Math.sin(a) * Math.cos(b)).multiply(radius);
			world.spawnParticle(Particle.DUST, at.clone().add(p), 1, 0.05, 0.05, 0.05, 0.0, i % 3 == 0 ? PALE_GOLD : GOLD);
		}
		world.spawnParticle(Particle.END_ROD, at.clone().add(0, 0.5, 0), 30, radius * 0.4, 0.6, radius * 0.4, 0.12);
		world.spawnParticle(Particle.EXPLOSION, at.clone().add(0, 0.5, 0), 3, 0.5, 0.3, 0.5, 0.0);
		world.spawnParticle(Particle.BLOCK, at, 40, radius * 0.4, 0.1, radius * 0.4, 0.0, at.clone().add(0, -0.5, 0).getBlock().getBlockData());
		ringAt(at, radius, GOLD);
		world.playSound(at, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, SoundCategory.PLAYERS, 1.2F, 0.8F);
		world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.8F, 1.2F);
	}

	/** Golden sparks drifting down after a slam. */
	private static void settle(Location at, double radius) {
		at.getWorld().spawnParticle(Particle.DUST, at.clone().add(0, 1.5, 0), 8, radius * 0.5, 1.0, radius * 0.5, 0.0, PALE_GOLD);
		at.getWorld().spawnParticle(Particle.END_ROD, at.clone().add(0, 2.0, 0), 2, radius * 0.5, 0.8, radius * 0.5, 0.01);
	}

	/** Strike Again's opening: a flash of light over the field. */
	private static void flash(Location at) {
		World world = at.getWorld();
		world.spawnParticle(Particle.END_ROD, at.clone().add(0, 3, 0), 60, 3.0, 2.0, 3.0, 0.1);
		world.spawnParticle(Particle.DUST, at.clone().add(0, 2, 0), 80, 4.0, 1.5, 4.0, 0.0, PALE_GOLD);
		world.playSound(at, Sound.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 1.2F, 0.5F);
		world.playSound(at, Sound.BLOCK_BELL_USE, SoundCategory.PLAYERS, 1.2F, 0.5F);
	}

	/** A golden shock ring spreading over the ground. */
	private static void shockRing(Location at, double radius) {
		for (double r = 1.0; r <= radius; r += 1.0) {
			ringAt(at, r, r == radius ? PALE_GOLD : GOLD);
		}
		at.getWorld().playSound(at, Sound.ITEM_MACE_SMASH_GROUND, SoundCategory.PLAYERS, 1.0F, 0.7F);
	}

	private static void ringAt(Location at, double radius, Particle.DustOptions dust) {
		World world = at.getWorld();
		int points = (int) (radius * 14);
		for (int i = 0; i < points; i++) {
			double a = i * Math.PI * 2 / points;
			world.spawnParticle(Particle.DUST, at.clone().add(Math.cos(a) * radius, 0.15, Math.sin(a) * radius), 1, 0, 0, 0, 0.0, dust);
		}
	}

	/** Strike Again's slam: a white burst over the whole field. */
	private static void whiteBurst(Location at) {
		World world = at.getWorld();
		world.spawnParticle(Particle.EXPLOSION_EMITTER, at.clone().add(0, 0.5, 0), 1);
		world.spawnParticle(Particle.EXPLOSION, at.clone().add(0, 1, 0), 12, 3.0, 1.0, 3.0, 0.0);
		ThreadLocalRandom random = ThreadLocalRandom.current();
		for (int i = 0; i < 160; i++) {
			Vector dir = new Vector(random.nextGaussian(), Math.abs(random.nextGaussian()) * 0.6, random.nextGaussian()).normalize();
			world.spawnParticle(Particle.DUST, at.clone().add(dir.multiply(random.nextDouble(1.0, AGAIN_RADIUS))), 1, 0.1, 0.1, 0.1, 0.0,
					i % 4 == 0 ? GOLD : WHITE);
		}
		world.spawnParticle(Particle.END_ROD, at.clone().add(0, 1, 0), 80, 2.5, 1.5, 2.5, 0.35);
		for (double r = 1.5; r <= AGAIN_RADIUS; r += 1.5) {
			ringAt(at, r, WHITE);
		}
		world.playSound(at, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, SoundCategory.PLAYERS, 1.6F, 0.6F);
		world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.4F, 0.7F);
		world.playSound(at, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, 0.8F, 1.4F);
	}

	// --- small cues used by the hammer -------------------------------------------

	/** A smash spent {@code nails} nails in {@code foe}. */
	static void nailSmash(LivingEntity foe, int nails) {
		Location at = foe.getLocation();
		at.getWorld().spawnParticle(Particle.DUST, at.clone().add(0, 0.2, 0), 10 + nails * 4, 0.6, 0.1, 0.6, 0.0, GOLD);
		ringAt(at, 1.2 + nails * 0.1, PALE_GOLD);
		at.getWorld().playSound(at, Sound.BLOCK_BELL_USE, SoundCategory.PLAYERS, 0.9F, 0.6F + nails * 0.05F);
	}

	/** The Black God's Voice reached 50: the strong skill is ready. */
	static void voiceFull(Player player) {
		Location at = player.getLocation().add(0, 1, 0);
		player.getWorld().spawnParticle(Particle.DUST, at, 20, 0.4, 0.6, 0.4, 0.0, GOLD);
		player.playSound(at, Sound.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 0.8F, 0.6F);
	}

	/** Preservation potency rose. */
	static void potencyUp(LivingEntity holder) {
		Location at = holder.getLocation().add(0, 1, 0);
		holder.getWorld().spawnParticle(Particle.END_ROD, at, 10, 0.3, 0.5, 0.3, 0.04);
		holder.getWorld().playSound(at, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 1.0F, 0.8F);
	}
}
