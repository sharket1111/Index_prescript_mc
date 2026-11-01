package io.github.sharket1111.indexprescript.paper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;


/**
 * Furioso-Replica: a nine-coin flurry in which Caduceus reforms into a new
 * weapon for every strike while the target is held in place. Coins 1-8 hit
 * random foes within 6 blocks of the target; the ninth, a scythe, hits the
 * target itself. Following the
 * in-game animation: for coins 1-8 the wielder dashes back and forth through
 * the target, each strike leaving a blue sweeping arc and a white star flash;
 * blue rings then swirl around the target, and the ninth coin, a guaranteed
 * scythe, cuts one great diagonal line before the screen darkens.
 *
 * Skill text mapped to Minecraft:
 * - prescript mark: damage +10% (it is only ever cast through prescripts)
 * - own Poise + target Sinking, per 6: +1 final power (max 2); each power is damage +25%
 * - on use: gain Poise 3 / count 3; no [Hermes] gain this turn
 * - every hit: inflict Sinking 1 or gain Poise 1, alternating
 * - coin 9: scythe, critical damage +90%, Sinking count +3
 * - attack end: Burn 5, Bind 3, Fragile 3 next turn; consume every [Hermes]
 */
final class Furioso {
	private static final int[] COIN_TICKS = {10, 17, 24, 31, 38, 45, 52, 59, 80};
	private static final int WINDUP_TICK = 62;
	private static final int END_TICK = 100;
	private static final int LAST_COIN = COIN_TICKS.length - 1;
	private static final double DASH_RADIUS = 2.2;
	private static final double SPREAD_RADIUS = 6.0;
	// Odd coins add a white line through the target; consecutive lines cross.
	private static final double[] LINE_ANGLES = {35, -35, 70, -60, 15, -20, 55, -75};

	private static final double COIN_RATIO = 0.6;
	private static final double PRESCRIPT_MARK_BONUS = 1.10;
	private static final double POWER_DAMAGE_BONUS = 0.25;
	private static final int POWER_STEP = 6;
	private static final int MAX_POWER = 2;
	private static final double FINAL_CRIT_MULTIPLIER = (1.2 + 0.9) / 1.2;

	private static final Map<UUID, Furioso> ACTIVE = new ConcurrentHashMap<>();

	private final Player player;
	private final LivingEntity target;
	private final World world;
	private final Location anchor;
	private final Location origin;
	private final double baseAngle;
	private final boolean hadAi;
	private final Set<LivingEntity> struck = new LinkedHashSet<>();
	private CaduceusForm lastForm;
	private int tick;
	private Cinematic camera;

	private Furioso(Player player, LivingEntity target) {
		this.player = player;
		this.target = target;
		this.world = target.getWorld();
		this.anchor = target.getLocation();
		this.origin = player.getLocation();
		this.baseAngle = Math.atan2(origin.getZ() - anchor.getZ(), origin.getX() - anchor.getX());
		this.hadAi = target.hasAI();
	}

	static boolean active(Player player) {
		return ACTIVE.containsKey(player.getUniqueId());
	}

	static void start(Player player, LivingEntity target) {
		Furioso furioso = new Furioso(player, target);
		if (ACTIVE.putIfAbsent(player.getUniqueId(), furioso) == null) {
			furioso.open();
		}
	}

	static void tickAll() {
		ACTIVE.values().removeIf(furioso -> !furioso.tick());
	}

	private void open() {
		long now = CaduceusListener.now();
		if (!(target instanceof Player)) {
			target.setAI(false);
		}
		CombatState.of(player).addPoise(3, 3, now);
		PrescriptTracker.blockHermes(player, now + END_TICK + CombatState.TURN_TICKS);

		camera = Cinematic.start(player, anchor.clone().add(0, 2, 0));
		Effects.auraBurst(actor());
		sound(actor().getLocation(), Sound.ITEM_BUCKET_FILL_LAVA, 1.0F, 0.6F);
		sound(actor().getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0F, 0.5F);
	}

	/** Advances one server tick; returns false once the sequence has ended. */
	private boolean tick() {
		if (!player.isOnline() || player.isDead() || player.getWorld() != world || !target.isValid() || target.isDead()) {
			finish();
			return false;
		}
		holdTarget();
		shoot();
		player.setFallDistance(0.0F);
		CaduceusListener.keepFormed(player, 30);

		if (tick == WINDUP_TICK) {
			windup();
		}
		if (tick >= WINDUP_TICK && tick < COIN_TICKS[LAST_COIN]) {
			Effects.vortex(target, tick - WINDUP_TICK);
		}
		for (int coin = 0; coin < COIN_TICKS.length; coin++) {
			if (COIN_TICKS[coin] == tick) {
				strike(coin);
			}
		}
		if (tick >= END_TICK) {
			finish();
			return false;
		}
		tick++;
		return true;
	}

	private void holdTarget() {
		Location here = target.getLocation();
		if (here.distanceSquared(anchor) > 0.01) {
			Location back = anchor.clone();
			back.setYaw(here.getYaw());
			back.setPitch(here.getPitch());
			target.teleport(back);
		}
		target.setVelocity(new Vector());
		if (tick % 10 == 0) {
			target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 9));
		}
	}

	/** The wielder steps back to where it began while liquid metal gathers for the final coin. */
	private void windup() {
		dashTo(origin.toVector(), target);
		Effects.gather(target);
		sound(actor().getLocation(), Sound.ITEM_BUCKET_FILL_LAVA, 1.0F, 0.5F);
	}

	/**
	 * Dashes through {@code victim} to its far side from where the wielder
	 * stands, with some jitter, so the strikes criss-cross like the animation.
	 */
	private void dashThrough(LivingEntity victim) {
		Location at = victim.getLocation();
		Location here = actor().getLocation();
		double away = Math.atan2(at.getZ() - here.getZ(), at.getX() - here.getX());
		if (here.distanceSquared(at) < 0.25) {
			away = baseAngle + Math.PI;
		}
		double angle = away + (ThreadLocalRandom.current().nextDouble() - 0.5) * 1.4;
		dashTo(new Vector(at.getX() + Math.cos(angle) * DASH_RADIUS, at.getY(), at.getZ() + Math.sin(angle) * DASH_RADIUS), victim);
	}

	/**
	 * Furioso is a wide flurry: coins 1-8 each strike a random living thing
	 * within {@link #SPREAD_RADIUS} blocks of the main target (the main target
	 * included). The wielder, its stand-in, armour stands and players who
	 * cannot be hurt are left out.
	 */
	private LivingEntity pickVictim() {
		List<LivingEntity> pool = new ArrayList<>();
		pool.add(target);
		LivingEntity actor = actor();
		for (Entity entity : target.getNearbyEntities(SPREAD_RADIUS, SPREAD_RADIUS, SPREAD_RADIUS)) {
			if (!(entity instanceof LivingEntity living) || entity == player || entity == actor
					|| entity instanceof ArmorStand || entity instanceof Mannequin
					|| !living.isValid() || living.isDead() || living.isInvulnerable()
					|| living.getLocation().distanceSquared(anchor) > SPREAD_RADIUS * SPREAD_RADIUS) {
				continue;
			}
			if (living instanceof Player other && (other.getGameMode() == GameMode.CREATIVE || other.getGameMode() == GameMode.SPECTATOR)) {
				continue;
			}
			pool.add(living);
		}
		return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
	}

	/** Teleports the wielder to {@code spot} facing {@code facing}, if there is room there. */
	private void dashTo(Vector spot, LivingEntity facing) {
		Block feet = world.getBlockAt(spot.getBlockX(), spot.getBlockY(), spot.getBlockZ());
		if (!feet.isPassable() || !feet.getRelative(0, 1, 0).isPassable()) {
			return;
		}
		LivingEntity actor = actor();
		Vector from = actor.getLocation().toVector();
		Location destination = new Location(world, spot.getX(), spot.getY(), spot.getZ());
		Vector look = facing.getLocation().toVector().add(new Vector(0, facing.getHeight() * 0.5, 0))
				.subtract(spot.clone().add(new Vector(0, actor.getEyeHeight(), 0)));
		destination.setDirection(look);
		actor.teleport(destination);
		Effects.dashTrail(world, from, spot);
	}

	private void strike(int coin) {
		long now = CaduceusListener.now();
		boolean last = coin == LAST_COIN;
		CaduceusForm form = last ? CaduceusForm.SCYTHE : CaduceusForm.random(lastForm);
		lastForm = form;
		// Coins 1-8 are sprayed around the area; the ninth always cuts the main target.
		LivingEntity victim = last ? target : pickVictim();
		struck.add(victim);

		if (!last) {
			dashThrough(victim);
		}
		ItemStack stack = player.getInventory().getItemInMainHand();
		if (Items.isCaduceus(stack)) {
			Items.setForm(stack, form);
		}
		if (camera != null) {
			camera.syncHeld();
		}
		actor().swingMainHand();

		CombatState self = CombatState.of(player);
		CombatState other = CombatState.of(victim);
		int power = Math.min(MAX_POWER, (self.poisePotency(now) + other.sinkingPotency(now)) / POWER_STEP);
		CaduceusForm.Hit hit = form.resolve(player, victim, now);
		double damage = hit.damage() * (last ? 1.0 : COIN_RATIO) * PRESCRIPT_MARK_BONUS * (1.0 + power * POWER_DAMAGE_BONUS);
		if (last) {
			damage *= FINAL_CRIT_MULTIPLIER;
		}
		// Coins land faster than the hurt cooldown, so it is reset for each one.
		Damage.deal(victim, damage, player, true);

		if (coin % 2 == 0) {
			other.addSinking(1, 1, now);
		} else {
			self.addPoise(1, 1, now);
		}

		Effects.form(actor());
		if (last) {
			other.addSinking(0, 3, now);
			Effects.finalCut(actor(), victim);
			player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, END_TICK - COIN_TICKS[LAST_COIN] + 20, 0));
		} else {
			ThreadLocalRandom random = ThreadLocalRandom.current();
			Effects.arc(actor(), victim, random.nextInt(360), 160 + random.nextInt(60), random.nextInt(60) - 30);
			Effects.star(victim, 1.0);
			if (coin % 2 == 1) {
				Effects.slash(actor(), victim, LINE_ANGLES[coin], 5.0);
			}
		}
		if (hit.crit()) {
			Effects.crit(victim);
		}
		sound(victim.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.9F, 0.8F + coin * 0.08F);
	}

	/**
	 * Camera path: circles the target while the wielder dashes through it,
	 * pulls back and rises as the rings gather, then rushes in for the final
	 * cut with a shake. Every coin gives a small jolt.
	 */
	private void shoot() {
		if (camera == null) {
			return;
		}
		Vector focus = anchor.toVector().add(new Vector(0, 1.0, 0));
		double start = baseAngle + Math.PI / 2;
		double windupAngle = start + WINDUP_TICK * 0.05;
		int finalTick = COIN_TICKS[LAST_COIN];
		Vector position;
		double shake = 0;
		if (tick < WINDUP_TICK) {
			position = Cinematic.orbit(focus, start + tick * 0.05, 7.5, 2.2);
		} else if (tick < finalTick) {
			double t = (tick - WINDUP_TICK) / (double) (finalTick - WINDUP_TICK);
			position = Cinematic.orbit(focus, windupAngle + t * 0.6, 7.5 + 0.5 * t, 2.2 + 1.8 * t);
		} else {
			double t = Math.min(1.0, (tick - finalTick) / 6.0);
			position = Cinematic.orbit(focus, windupAngle + 0.6, 8.0 - 4.0 * t, 4.0 - 2.8 * t);
			if (tick - finalTick < 6) {
				shake = 0.6;
			}
		}
		for (int coinTick : COIN_TICKS) {
			if (coinTick == tick && shake == 0) {
				shake = 0.25;
			}
		}
		camera.frame(position, focus, shake);
	}

	/** The body that dashes and swings: the camera's stand-in while one runs, else the player. */
	private LivingEntity actor() {
		return camera != null ? camera.actor() : player;
	}

	private void finish() {
		if (camera != null) {
			camera.end();
			camera = null;
		}
		long now = CaduceusListener.now();
		if (!(target instanceof Player)) {
			target.setAI(hadAi);
		}
		// Attack end: everything struck burns and is bound and made fragile.
		for (LivingEntity victim : struck) {
			if (victim.isValid() && !victim.isDead()) {
				victim.setFireTicks(Math.max(victim.getFireTicks(), 100));
				victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, CombatState.TURN_TICKS * 2, 2));
				CombatState.of(victim).setFragileAll(3, now, CombatState.TURN_TICKS * 2);
			}
		}
		PrescriptTracker.consumeHermes(player, now + CombatState.TURN_TICKS);
		CaduceusListener.keepFormed(player, 20);
	}

	private static void sound(Location at, Sound sound, float volume, float pitch) {
		at.getWorld().playSound(at, sound, SoundCategory.PLAYERS, volume, pitch);
	}
}
