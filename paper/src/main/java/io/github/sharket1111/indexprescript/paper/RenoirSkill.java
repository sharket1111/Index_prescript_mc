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
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.util.Vector;

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
		SEVER, AGAIN
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
		int power = Math.min(2, nails / 6);
		double multiplier = 1.0;
		if (RenoirHammer.potency(player) >= 3) {
			power++;
			if (kind == Kind.SEVER) {
				multiplier *= 1.4;
			}
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
		player.sendActionBar(Component.text(Messages.get(player, big ? "renoir.again" : "renoir.sever"), NamedTextColor.GOLD));
		World world = actor().getWorld();
		Location at = actor().getLocation().add(0, 1, 0);
		world.spawnParticle(Particle.DUST, at, big ? 60 : 25, 0.6, 0.8, 0.6, 0.0, GOLD);
		world.spawnParticle(Particle.END_ROD, at, big ? 20 : 8, 0.4, 0.6, 0.4, 0.05);
		world.playSound(at, Sound.BLOCK_BELL_RESONATE, SoundCategory.PLAYERS, 1.0F, big ? 0.6F : 0.9F);
		world.playSound(at, Sound.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.8F, big ? 0.7F : 1.3F);
	}

	private int endTick() {
		return kind == Kind.SEVER ? 58 : 120;
	}

	private boolean tick() {
		if (!player.isOnline() || player.isDead() || !RenoirHammer.holding(player)) {
			finish();
			return false;
		}
		held.keySet().removeIf(foe -> !foe.isValid() || foe.isDead());
		if (!target.isValid() || target.isDead()) {
			if (kind == Kind.SEVER || held.isEmpty()) {
				finish();
				return false;
			}
			target = held.keySet().iterator().next();
		}
		hold();
		if (kind == Kind.SEVER) {
			sever();
		} else {
			again();
		}
		if (tick >= endTick()) {
			finish();
			return false;
		}
		tick++;
		return true;
	}

	// --- Sever Birth -------------------------------------------------------------

	private void sever() {
		shootSide(3.4, severShake());
		switch (tick) {
			case 0 -> face();
			case 2 -> {
				// Two nails fall: one drives into the foe, the other strikes beside it.
				RenoirHammer.driveNail(player, target);
				PromisedNails.rain(target.getLocation(), 1.2, 2.4F, false);
			}
			case 8 -> coin(4.0, () -> pillar(target.getLocation()));
			case 13 -> stepIn(1.6);
			case 18 -> coin(4.0, () -> sweep(actor(), target, -30, 0));
			case 24 -> pose(Pose.SNEAKING);
			case 26 -> startLeap();
			case 27, 28, 29, 30, 31, 32, 33, 34, 35 -> leap(tick - 26, 9, 2.6, 1.4);
			case 36 -> {
				pose(Pose.STANDING);
				land(1.4);
				coin(7.0, () -> {
					sweep(actor(), target, 90, 80);
					dome(target.getLocation(), 2.6);
				});
				RenoirHammer.addPreservation(player, 4);
			}
			default -> {
				if (tick > 36 && tick < 52 && tick % 3 == 0) {
					settle(target.getLocation(), 2.4);
				}
			}
		}
	}

	private double severShake() {
		return tick >= 8 && tick < 11 || tick >= 36 && tick < 41 ? 0.6 : 0.0;
	}

	// --- Strike Again and Again --------------------------------------------------

	private void again() {
		framesAgain();
		if (tick >= 4 && tick <= 34 && tick % 2 == 0) {
			int drops = tick < 12 ? 2 : 3;
			for (int i = 0; i < drops; i++) {
				PromisedNails.rain(center, AGAIN_RADIUS, 3.6F, true);
			}
		}
		switch (tick) {
			case 0 -> face();
			case 2 -> flash(center);
			case 10, 24 -> {
				for (LivingEntity foe : List.copyOf(held.keySet())) {
					if (PromisedNails.count(foe) < PromisedNails.MAX) {
						RenoirHammer.driveNail(player, foe);
					}
				}
			}
			case 16 -> {
				for (LivingEntity foe : List.copyOf(held.keySet())) {
					hit(foe, 4.0, () -> pillar(foe.getLocation()));
				}
				shockRing(center, 4.0);
				actor().swingMainHand();
			}
			case 30 -> {
				// The second nails strike; coin II lands with the hammer's sweep after them.
				for (LivingEntity foe : held.keySet()) {
					pillar(foe.getLocation());
				}
				shockRing(center, 7.0);
			}
			case 32 -> stepIn(1.8);
			case 33, 34, 35 -> spin(tick - 33);
			case 36 -> {
				face();
				actor().swingMainHand();
				sweep(actor(), target, -100, 0);
				sweep(actor(), target, -100, 20);
				whirl(actor());
				for (LivingEntity foe : List.copyOf(held.keySet())) {
					hit(foe, 4.0, null);
				}
			}
			case 40 -> pose(Pose.SNEAKING);
			case 42 -> startLeap();
			case 66 -> {
				pose(Pose.STANDING);
				land(1.5);
			}
			case 68 -> slam();
			default -> {
				if (tick > 42 && tick < 66) {
					rise(tick - 42);
				} else if (tick > 68 && tick < 104 && tick % 4 == 0) {
					settle(center, AGAIN_RADIUS * 0.6);
				}
			}
		}
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
		if (tick >= 46 && tick < 66) {
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
		boolean impact = tick >= 16 && tick < 19 || tick >= 30 && tick < 32 || tick >= 36 && tick < 40 || tick >= 68 && tick < 76;
		// Closer in for the hammer's sweep, wide for the rain and the slam.
		double distance = tick >= 31 && tick < 42 ? 4.8 : tick < 46 ? 7.5 : 6.0;
		camera.frame(Cinematic.orbit(focus, cameraSide, distance, 3.0), focus, impact ? 0.8 : 0.0);
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
