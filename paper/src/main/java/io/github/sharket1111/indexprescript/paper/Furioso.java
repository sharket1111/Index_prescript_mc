package io.github.sharket1111.indexprescript.paper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
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
import io.papermc.paper.entity.TeleportFlag;


/**
 * Furioso-Replica: a nine-coin flurry in which Caduceus reforms into a new
 * weapon for every strike while the target is held in place. Coins 1-8 hit
 * random foes within 6 blocks of the target; the ninth, a scythe, hits the
 * target itself. If the main target dies, a random foe within 6 blocks of it
 * becomes the new main target and the flurry carries on. Following Furioso [Lacrimosa-Crescendo]: before each of
 * coins 1-8 the wielder appears beside its victim in a dark streak and strikes
 * with that coin's own look; blue rings then orbit the wielder beside the
 * target while star bursts flicker, a white flash goes off, and the ninth
 * coin, a guaranteed scythe, cuts one great diagonal line before the screen
 * darkens.
 *
 * Skill text mapped to Minecraft:
 * - prescript mark: damage +10% (it is only ever cast through prescripts)
 * - own Poise + target Sinking, per 6: +1 final power (max 2); each power is damage +25%
 * - on use: gain Poise 3 / count 3; no [Hermes] gain this turn
 * - every hit: inflict Sinking 1 or gain Poise 1, alternating
 * - coin 9: scythe, critical damage +90%, Sinking count +3
 * - attack end: Burn 5, Bind 3, Fragile 3 next turn; consume every [Hermes]
 *
 * [Shin (心) - Rien], shown as a golden glow around the wielder, is reckoned
 * when the skill starts:
 * - per 15% of health lost: Damage Up 1 (max 5), each damage +5%
 * - per 40% of health lost: +1 power (max 2)
 * - Poise gained by this skill: potency +1, count +1
 * - Sanity above the target's: damage +1% per 2 points of difference (max 30%)
 * [Mang] forms if the wielder's Sanity is 0 or more, shown as a glowing golden
 * ring wound round the weapon: damage +40% and final power +4 (each +10%);
 * at attack end it costs 20 Sanity. Sanity is read from health (see {@link Sanity}),
 * so [Mang] forms at 5 hearts or more and its cost is taken from health.
 */
final class Furioso {
	// Timed after Furioso [Lacrimosa-Crescendo] at about 0.7x speed: eight strikes
	// spaced like the original, a long gathering flurry, a flash, then the last line.
	private static final int[] COIN_TICKS = {11, 21, 29, 42, 53, 67, 91, 99, 158};
	/** Ticks before a strike at which the wielder appears beside its victim. */
	private static final int APPEAR_LEAD = 3;
	private static final int FOLLOW_UP_TICK = 78;
	private static final int WINDUP_TICK = 104;
	private static final int FLASH_TICK = 154;
	private static final int END_TICK = 172;
	private static final int LAST_COIN = COIN_TICKS.length - 1;
	private static final double DASH_RADIUS = 2.2;
	private static final double SPREAD_RADIUS = 6.0;

	private static final double COIN_RATIO = 0.6;
	private static final double PRESCRIPT_MARK_BONUS = 1.10;
	private static final double POWER_DAMAGE_BONUS = 0.25;
	private static final int POWER_STEP = 6;
	private static final int MAX_POWER = 2;
	private static final double FINAL_CRIT_MULTIPLIER = (1.2 + 0.9) / 1.2;
	private static final double SHIN_DAMAGE_UP_STEP = 0.15;
	private static final int SHIN_MAX_DAMAGE_UP = 5;
	private static final double DAMAGE_UP_BONUS = 0.05;
	private static final double SHIN_POWER_STEP = 0.40;
	private static final int SHIN_MAX_POWER = 2;
	private static final double SHIN_MAX_SANITY_BONUS = 0.30;
	private static final double MANG_DAMAGE = 1.40;
	private static final int MANG_FINAL_POWER = 4;
	private static final double FINAL_POWER_BONUS = 0.10;
	private static final int MANG_SANITY_COST = 20;

	private static final Map<UUID, Furioso> ACTIVE = new ConcurrentHashMap<>();

	private final Player player;
	private final World world;
	private final Location origin;
	/** Mobs frozen in place as main target, with whether they had AI before. */
	private final Map<LivingEntity, Boolean> frozen = new LinkedHashMap<>();
	private LivingEntity target;
	private Location anchor;
	private double baseAngle;
	private final Set<LivingEntity> struck = new LinkedHashSet<>();
	private CaduceusForm lastForm;
	private LivingEntity nextVictim;
	private LivingEntity lastVictim;
	private int tick;
	private Cinematic camera;
	private int shinDamageUp;
	private int shinPower;
	private boolean mang;

	private Furioso(Player player, LivingEntity target) {
		this.player = player;
		this.target = target;
		this.world = target.getWorld();
		this.anchor = target.getLocation();
		this.origin = player.getLocation();
		this.baseAngle = Math.atan2(origin.getZ() - anchor.getZ(), origin.getX() - anchor.getX());
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
		freeze(target);
		// [Shin] is reckoned from the health lost when the skill starts.
		double maxHealth = player.getAttribute(Attribute.MAX_HEALTH) == null ? 20.0 : player.getAttribute(Attribute.MAX_HEALTH).getValue();
		double lost = Math.max(0.0, 1.0 - player.getHealth() / maxHealth);
		shinDamageUp = Math.min(SHIN_MAX_DAMAGE_UP, (int) (lost / SHIN_DAMAGE_UP_STEP));
		shinPower = Math.min(SHIN_MAX_POWER, (int) (lost / SHIN_POWER_STEP));
		mang = Sanity.of(player) >= 0;
		gainPoise(3, now);
		PrescriptTracker.blockHermes(player, now + END_TICK + CombatState.TURN_TICKS);

		camera = Cinematic.start(player, anchor.clone().add(0, 2, 0));
		Effects.auraBurst(actor());
		Effects.shinBurst(actor());
		if (mang) {
			Effects.mangForm(actor());
		}
		sound(actor().getLocation(), Sound.ITEM_BUCKET_FILL_LAVA, 1.0F, 0.6F);
		sound(actor().getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0F, 0.5F);
	}

	/** Advances one server tick; returns false once the sequence has ended. */
	private boolean tick() {
		if (!player.isOnline() || player.isDead() || player.getWorld() != world
				|| (!target.isValid() || target.isDead()) && !retarget()) {
			finish();
			return false;
		}
		holdTarget();
		shoot();
		Effects.shin(actor(), tick);
		if (mang) {
			Effects.mang(actor(), tick);
		}
		player.setFallDistance(0.0F);
		CaduceusListener.keepFormed(player, 30);

		if (tick == WINDUP_TICK) {
			windup();
		}
		if (tick > WINDUP_TICK && tick < COIN_TICKS[LAST_COIN]) {
			flurry(tick - WINDUP_TICK);
		}
		if (tick == FLASH_TICK) {
			Effects.flash(target);
		}
		if (tick == FOLLOW_UP_TICK && lastVictim != null && lastVictim.isValid()) {
			// After the ring coin a second great arc sweeps past, as in the original.
			Effects.arc(actor(), lastVictim, 200, 250, -15);
			Effects.star(lastVictim, 1.4);
			sound(lastVictim.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8F, 1.3F);
		}
		for (int coin = 0; coin < LAST_COIN; coin++) {
			if (COIN_TICKS[coin] - APPEAR_LEAD == tick) {
				appear();
			}
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
			target.teleport(back, TeleportFlag.EntityState.RETAIN_PASSENGERS);
		}
		target.setVelocity(new Vector());
		if (tick % 10 == 0) {
			target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 9));
		}
	}

	/** The wielder settles beside the main target while liquid metal gathers for the final coin. */
	private void windup() {
		Vector side = new Vector(Math.cos(baseAngle), 0, Math.sin(baseAngle)).multiply(1.8);
		dashTo(anchor.toVector().add(side), target);
		Effects.gather(target);
		sound(actor().getLocation(), Sound.ITEM_BUCKET_FILL_LAVA, 1.0F, 0.5F);
	}

	/**
	 * The gathering before the last coin: blue rings orbit the wielder while
	 * star bursts flicker around the main target, quickening toward the end.
	 */
	private void flurry(int step) {
		Effects.orbitRings(actor(), step);
		int every = step < 25 ? 5 : 3;
		if (step % every == 0) {
			Effects.flicker(target, 1.8);
			sound(target.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6F, 0.6F + step * 0.02F);
		}
	}

	/** Picks the next coin's victim and appears beside it in a dark streak. */
	private void appear() {
		nextVictim = pickVictim();
		dashThrough(nextVictim);
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
		List<LivingEntity> pool = foes();
		pool.add(target);
		return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
	}

	/**
	 * When the main target dies the flurry goes on: a random foe within
	 * {@link #SPREAD_RADIUS} blocks of where it stood becomes the new main
	 * target, held in place like the first. Returns false if there is none.
	 */
	private boolean retarget() {
		List<LivingEntity> foes = foes();
		if (foes.isEmpty()) {
			return false;
		}
		target = foes.get(ThreadLocalRandom.current().nextInt(foes.size()));
		anchor = target.getLocation();
		Location here = actor().getLocation();
		baseAngle = Math.atan2(here.getZ() - anchor.getZ(), here.getX() - anchor.getX());
		freeze(target);
		Effects.star(target, 0.8);
		return true;
	}

	private void freeze(LivingEntity mob) {
		if (!(mob instanceof Player) && !frozen.containsKey(mob)) {
			frozen.put(mob, mob.hasAI());
			mob.setAI(false);
		}
	}

	/** Living foes within {@link #SPREAD_RADIUS} blocks of the main target's spot, other than the main target. */
	private List<LivingEntity> foes() {
		List<LivingEntity> pool = new ArrayList<>();
		LivingEntity actor = actor();
		for (Entity entity : world.getNearbyEntities(anchor, SPREAD_RADIUS, SPREAD_RADIUS, SPREAD_RADIUS)) {
			if (!(entity instanceof LivingEntity living) || entity == target || entity == player || entity == actor
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
		return pool;
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
		LivingEntity victim = last || nextVictim == null || !nextVictim.isValid() || nextVictim.isDead() ? target : nextVictim;
		nextVictim = null;
		lastVictim = victim;
		struck.add(victim);
		faceVictim(victim);
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
		int power = Math.min(MAX_POWER, (self.poisePotency(now) + other.sinkingPotency(now)) / POWER_STEP) + shinPower;
		CaduceusForm.Hit hit = form.resolve(player, victim, now);
		double damage = hit.damage() * (last ? 1.0 : COIN_RATIO) * PRESCRIPT_MARK_BONUS * (1.0 + power * POWER_DAMAGE_BONUS);
		if (last) {
			damage *= FINAL_CRIT_MULTIPLIER;
		}
		damage *= 1.0 + shinDamageUp * DAMAGE_UP_BONUS;
		int sanityGap = Sanity.of(player) - Sanity.of(victim);
		if (sanityGap > 0) {
			damage *= 1.0 + Math.min(SHIN_MAX_SANITY_BONUS, sanityGap / 2 * 0.01);
		}
		if (mang) {
			damage *= MANG_DAMAGE * (1.0 + MANG_FINAL_POWER * FINAL_POWER_BONUS);
		}
		damage *= IndexMark.multiplier(player, victim);
		// Coins land faster than the hurt cooldown, so it is reset for each one.
		Damage.deal(victim, damage, player, true);
		IndexMark.strike(player, victim);

		if (coin % 2 == 0) {
			other.addSinking(1, 1, now);
		} else {
			gainPoise(1, now);
		}

		Effects.form(actor());
		if (last) {
			other.addSinking(0, 3, now);
			Effects.finalCut(actor(), victim);
			player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, END_TICK - COIN_TICKS[LAST_COIN] + 20, 0));
		} else {
			coinLook(coin, victim);
		}
		if (hit.crit()) {
			Effects.crit(victim);
		}
		sound(victim.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.9F, 0.8F + coin * 0.08F);
	}

	/**
	 * Each of coins 1-8 has its own look, after the original: a crescent, a
	 * vertical cut, a horizontal dash line, a bursting blow, a wide sweep, a
	 * ring with an explosion, a crossing dash and cut, and a last crescent.
	 */
	private void coinLook(int coin, LivingEntity victim) {
		LivingEntity actor = actor();
		switch (coin) {
			case 0 -> {
				Effects.arc(actor, victim, -40, 200, 20);
				Effects.star(victim, 1.3);
			}
			case 1 -> {
				Effects.slash(actor, victim, 88, 4.5);
				Effects.star(victim, 1.0);
			}
			case 2 -> {
				Effects.slash(actor, victim, 4, 6.0);
				Effects.star(victim, 0.8);
			}
			case 3 -> {
				Effects.burst(victim);
				Effects.star(victim, 1.6);
			}
			case 4 -> {
				Effects.arc(actor, victim, -150, 270, -10);
				Effects.star(victim, 1.1);
			}
			case 5 -> {
				Effects.ring(victim);
				Effects.star(victim, 1.2);
			}
			case 6 -> {
				Effects.slash(actor, victim, -3, 6.0);
				Effects.slash(actor, victim, 84, 4.5);
				Effects.star(victim, 1.0);
			}
			default -> {
				Effects.arc(actor, victim, 140, 220, 30);
				Effects.star(victim, 1.5);
			}
		}
	}

	/** Turns the wielder toward the victim for the swing. */
	private void faceVictim(LivingEntity victim) {
		LivingEntity actor = actor();
		Location here = actor.getLocation();
		Vector look = victim.getLocation().toVector().add(new Vector(0, victim.getHeight() * 0.5, 0))
				.subtract(here.toVector().add(new Vector(0, actor.getEyeHeight(), 0)));
		if (look.lengthSquared() > 1.0E-4) {
			here.setDirection(look);
			actor.teleport(here);
		}
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

	/** Poise from this skill, raised by [Shin]: potency +1, count +1. */
	private void gainPoise(int amount, long now) {
		CombatState.of(player).addPoise(amount + 1, amount + 1, now);
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
		frozen.forEach((mob, hadAi) -> {
			if (mob.isValid()) {
				mob.setAI(hadAi);
			}
		});
		// Attack end: everything struck burns and is bound and made fragile.
		for (LivingEntity victim : struck) {
			if (victim.isValid() && !victim.isDead()) {
				victim.setFireTicks(Math.max(victim.getFireTicks(), 100));
				victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, CombatState.TURN_TICKS * 2, 2));
				CombatState.of(victim).setFragileAll(3, now, CombatState.TURN_TICKS * 2);
			}
		}
		PrescriptTracker.consumeHermes(player, now + CombatState.TURN_TICKS);
		if (mang) {
			Sanity.lose(player, MANG_SANITY_COST);
		}
		CaduceusListener.keepFormed(player, 20);
	}

	private static void sound(Location at, Sound sound, float volume, float pitch) {
		at.getWorld().playSound(at, sound, SoundCategory.PLAYERS, volume, pitch);
	}
}
