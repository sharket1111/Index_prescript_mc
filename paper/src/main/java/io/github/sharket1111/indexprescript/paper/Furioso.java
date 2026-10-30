package io.github.sharket1111.indexprescript.paper;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;

/**
 * Furioso-Replica: a nine-coin flurry in which Caduceus reforms into a new
 * weapon for every strike while the target is held in place. Following the
 * in-game animation: for coins 1-8 the wielder dashes back and forth through
 * the target, each strike leaving a blue sweeping arc and a white star flash;
 * blue rings then swirl around the target, and the ninth coin, a guaranteed
 * scythe, cuts one great diagonal line before the screen darkens. A running
 * TOTAL of the damage dealt shows on the action bar.
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
	private CaduceusForm lastForm;
	private double total;
	private int tick;

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

		player.showTitle(Title.title(
				Component.text("Furioso-Replica", NamedTextColor.DARK_AQUA, TextDecoration.ITALIC),
				Component.text(Messages.get(player, "message.furioso_subtitle"), NamedTextColor.GRAY),
				Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1500), Duration.ofMillis(500))));

		Effects.auraBurst(player);
		sound(player.getLocation(), Sound.ITEM_BUCKET_FILL_LAVA, 1.0F, 0.6F);
		sound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0F, 0.5F);
	}

	/** Advances one server tick; returns false once the sequence has ended. */
	private boolean tick() {
		if (!player.isOnline() || player.isDead() || player.getWorld() != world || !target.isValid() || target.isDead()) {
			finish();
			return false;
		}
		holdTarget();
		player.setFallDistance(0.0F);
		CaduceusListener.keepFormed(player, 30);
		PrescriptTracker.holdOverlay(player);

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
		dashTo(origin.toVector());
		Effects.gather(target);
		sound(player.getLocation(), Sound.ITEM_BUCKET_FILL_LAVA, 1.0F, 0.5F);
	}

	/**
	 * Dashes through the target to the far side on even coins and back on odd
	 * ones, with some jitter, so the strikes criss-cross like the animation.
	 */
	private void dashFor(int coin) {
		double side = coin % 2 == 0 ? Math.PI : 0.0;
		double angle = baseAngle + side + (ThreadLocalRandom.current().nextDouble() - 0.5) * 1.4;
		dashTo(new Vector(anchor.getX() + Math.cos(angle) * DASH_RADIUS, anchor.getY(), anchor.getZ() + Math.sin(angle) * DASH_RADIUS));
	}

	/** Teleports the wielder to {@code spot} facing the target, if there is room there. */
	private void dashTo(Vector spot) {
		Block feet = world.getBlockAt(spot.getBlockX(), spot.getBlockY(), spot.getBlockZ());
		if (!feet.isPassable() || !feet.getRelative(0, 1, 0).isPassable()) {
			return;
		}
		Vector from = player.getLocation().toVector();
		Location destination = new Location(world, spot.getX(), spot.getY(), spot.getZ());
		Vector look = target.getLocation().toVector().add(new Vector(0, target.getHeight() * 0.5, 0))
				.subtract(spot.clone().add(new Vector(0, player.getEyeHeight(), 0)));
		destination.setDirection(look);
		player.teleport(destination);
		Effects.dashTrail(world, from, spot);
	}

	private void strike(int coin) {
		long now = CaduceusListener.now();
		boolean last = coin == LAST_COIN;
		CaduceusForm form = last ? CaduceusForm.SCYTHE : CaduceusForm.random(lastForm);
		lastForm = form;

		if (!last) {
			dashFor(coin);
		}
		ItemStack stack = player.getInventory().getItemInMainHand();
		if (Items.isCaduceus(stack)) {
			Items.setForm(stack, form, player);
		}
		player.swingMainHand();

		CombatState self = CombatState.of(player);
		CombatState other = CombatState.of(target);
		int power = Math.min(MAX_POWER, (self.poisePotency(now) + other.sinkingPotency(now)) / POWER_STEP);
		CaduceusForm.Hit hit = form.resolve(player, target, now);
		double damage = hit.damage() * (last ? 1.0 : COIN_RATIO) * PRESCRIPT_MARK_BONUS * (1.0 + power * POWER_DAMAGE_BONUS);
		if (last) {
			damage *= FINAL_CRIT_MULTIPLIER;
		}
		// Coins land faster than the hurt cooldown, so it is reset for each one.
		Damage.deal(target, damage, player, true);
		total += damage;
		player.sendActionBar(Component.text(Messages.get(player, "message.furioso_total", Math.round(total)),
				hit.crit() ? NamedTextColor.YELLOW : NamedTextColor.WHITE));

		if (coin % 2 == 0) {
			other.addSinking(1, 1, now);
		} else {
			self.addPoise(1, 1, now);
		}

		Effects.form(player);
		if (last) {
			other.addSinking(0, 3, now);
			Effects.finalCut(player, target);
			player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, END_TICK - COIN_TICKS[LAST_COIN] + 20, 0));
		} else {
			ThreadLocalRandom random = ThreadLocalRandom.current();
			Effects.arc(player, target, random.nextInt(360), 160 + random.nextInt(60), random.nextInt(60) - 30);
			Effects.star(target, 1.0);
			if (coin % 2 == 1) {
				Effects.slash(player, target, LINE_ANGLES[coin], 5.0);
			}
		}
		if (hit.crit()) {
			Effects.crit(target);
		}
		sound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.9F, 0.8F + coin * 0.08F);
	}

	private void finish() {
		long now = CaduceusListener.now();
		if (!(target instanceof Player)) {
			target.setAI(hadAi);
		}
		if (target.isValid() && !target.isDead()) {
			target.setFireTicks(Math.max(target.getFireTicks(), 100));
			target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, CombatState.TURN_TICKS * 2, 2));
			CombatState.of(target).setFragileAll(3, now, CombatState.TURN_TICKS * 2);
		}
		PrescriptTracker.consumeHermes(player, now + CombatState.TURN_TICKS);
		CaduceusListener.keepFormed(player, 20);
	}

	private static void sound(Location at, Sound sound, float volume, float pitch) {
		at.getWorld().playSound(at, sound, SoundCategory.PLAYERS, volume, pitch);
	}
}
