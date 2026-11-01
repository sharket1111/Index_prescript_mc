package io.github.sharket1111.indexprescript.paper;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;

/**
 * Matthias's Laevatein. The original releases it by spending stacks with
 * certain skills; here each stage unlocks after a number of charged hits
 * (5, 7, then 9), and pressing swap-hands with a target in sight releases
 * one stage in a strike sequence modelled on the in-game animation.
 */
final class Laevatein implements Listener {
	private static final int[] HITS_TO_RELEASE = {5, 7, 9};
	private static final int MAX_STAGE = Items.LAEVATEIN_STAGES.size() - 1;
	private static final float CHARGED_THRESHOLD = 0.9F;
	private static final int TARGET_RANGE = 6;
	private static final int READY_EFFECT_INTERVAL = 4;
	private static final NamespacedKey HITS = new NamespacedKey(Items.NAMESPACE, "laevatein_hits");

	private static final Map<UUID, Boolean> CHARGED = new ConcurrentHashMap<>();
	private static final Map<UUID, Release> RELEASING = new ConcurrentHashMap<>();

	static int hits(ItemStack stack) {
		return stack.getItemMeta().getPersistentDataContainer().getOrDefault(HITS, PersistentDataType.INTEGER, 0);
	}

	static void setHits(ItemStack stack, int hits) {
		stack.editMeta(meta -> meta.getPersistentDataContainer().set(HITS, PersistentDataType.INTEGER, hits));
	}

	/** Whether the player's current swing started at (near) full attack charge. */
	static boolean charged(Player player) {
		return Boolean.TRUE.equals(CHARGED.get(player.getUniqueId()));
	}

	/** Enough hits for the next stage. */
	static boolean ready(ItemStack stack) {
		int stage = Items.laevateinStage(stack);
		return stage < MAX_STAGE && hits(stack) >= HITS_TO_RELEASE[stage];
	}

	/** Called every tick. */
	static void tick() {
		RELEASING.values().removeIf(release -> !release.tick());
		long now = CaduceusListener.now();
		if (now % READY_EFFECT_INTERVAL != 0) {
			return;
		}
		for (Player player : Bukkit.getOnlinePlayers()) {
			ItemStack held = player.getInventory().getItemInMainHand();
			if (Items.isLaevatein(held) && ready(held) && !RELEASING.containsKey(player.getUniqueId())) {
				LaevateinEffects.ready(player);
			}
		}
	}

	@EventHandler(ignoreCancelled = true)
	public void onPreAttack(PrePlayerAttackEntityEvent event) {
		Player player = event.getPlayer();
		if (!Items.isLaevatein(player.getInventory().getItemInMainHand())) {
			return;
		}
		if (RELEASING.containsKey(player.getUniqueId())) {
			event.setCancelled(true);
			return;
		}
		CHARGED.put(player.getUniqueId(), player.getAttackCooldown() >= CHARGED_THRESHOLD);
	}

	/** Charged hits count toward the next release. */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onHit(EntityDamageByEntityEvent event) {
		if (Damage.dealing() || event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK
				|| !(event.getDamager() instanceof Player player) || !(event.getEntity() instanceof LivingEntity)) {
			return;
		}
		ItemStack held = player.getInventory().getItemInMainHand();
		if (!Items.isLaevatein(held) || !Boolean.TRUE.equals(CHARGED.remove(player.getUniqueId()))) {
			return;
		}
		int stage = Items.laevateinStage(held);
		if (stage >= MAX_STAGE) {
			return;
		}
		int hits = Math.min(hits(held) + 1, HITS_TO_RELEASE[stage]);
		setHits(held, hits);
		if (hits == HITS_TO_RELEASE[stage]) {
			LaevateinEffects.readySound(player);
		}
	}

	/** Swap-hands with a ready Laevatein and a target in sight releases the next stage instead of swapping. */
	@EventHandler(ignoreCancelled = true)
	public void onSwap(PlayerSwapHandItemsEvent event) {
		Player player = event.getPlayer();
		ItemStack held = player.getInventory().getItemInMainHand();
		if (!Items.isLaevatein(held)) {
			return;
		}
		if (RELEASING.containsKey(player.getUniqueId())) {
			event.setCancelled(true);
			return;
		}
		if (!ready(held)) {
			return;
		}
		LivingEntity target = findTarget(player);
		if (target == null) {
			return;
		}
		event.setCancelled(true);
		Release release = new Release(player, target, Items.laevateinStage(held) + 1);
		RELEASING.put(player.getUniqueId(), release);
		release.start();
	}

	private static LivingEntity findTarget(Player player) {
		if (player.getTargetEntity(TARGET_RANGE) instanceof LivingEntity living && !living.isDead()) {
			return living;
		}
		Vector look = player.getEyeLocation().getDirection();
		LivingEntity best = null;
		double bestDistance = Double.MAX_VALUE;
		for (Entity entity : player.getNearbyEntities(TARGET_RANGE, TARGET_RANGE, TARGET_RANGE)) {
			if (!(entity instanceof LivingEntity living) || living.isDead()) {
				continue;
			}
			Vector to = living.getLocation().toVector().subtract(player.getLocation().toVector());
			double distance = to.length();
			if (distance <= TARGET_RANGE && to.normalize().dot(look) > 0.6 && distance < bestDistance) {
				best = living;
				bestDistance = distance;
			}
		}
		return best;
	}

	/**
	 * One release sequence. The blade changes to the new stage at the moment
	 * the casing breaks or the fire bursts; the target is held in place.
	 */
	private static final class Release {
		private final Player player;
		private final LivingEntity target;
		private final int stage;
		private final Location anchor;
		private final boolean hadAi;
		private int tick;

		Release(Player player, LivingEntity target, int stage) {
			this.player = player;
			this.target = target;
			this.stage = stage;
			this.anchor = target.getLocation();
			this.hadAi = target.hasAI();
		}

		void start() {
			if (!(target instanceof Player)) {
				target.setAI(false);
			}
			LaevateinEffects.aura(player, 6);
			LaevateinEffects.auraSound(player);
		}

		private int endTick() {
			return switch (stage) {
				case 1 -> 36;
				case 2 -> 44;
				default -> 54;
			};
		}

		boolean tick() {
			if (!player.isOnline() || player.isDead() || !target.isValid() || target.isDead()
					|| !Items.isLaevatein(player.getInventory().getItemInMainHand())) {
				finish();
				return false;
			}
			hold();
			if (tick % 3 == 0) {
				LaevateinEffects.aura(player, stage);
			}
			switch (stage) {
				case 1 -> stageOne();
				case 2 -> stageTwo();
				default -> stageThree();
			}
			if (tick >= endTick()) {
				finish();
				return false;
			}
			tick++;
			return true;
		}

		/** "Unwrapping one package": grey sweeps, the casing shatters, then a burning slash. */
		private void stageOne() {
			switch (tick) {
				case 6 -> strike(4.0, 0, () -> LaevateinEffects.greyArc(player, target, -100, 15));
				case 12 -> strike(4.0, 0, () -> LaevateinEffects.greyArc(player, target, 80, -20));
				case 20 -> {
					release();
					strike(6.0, 0, () -> LaevateinEffects.shatter(target));
				}
				case 28 -> strike(5.0, 60, () -> LaevateinEffects.flameArc(player, target, -90, 25));
				default -> {
				}
			}
		}

		/** "The second wrapping": three burning crescents, then a great explosion. */
		private void stageTwo() {
			switch (tick) {
				case 8 -> strike(5.0, 60, () -> LaevateinEffects.flameArc(player, target, -100, 20));
				case 16 -> strike(5.0, 60, () -> {
					LaevateinEffects.flameArc(player, target, 70, -25);
					LaevateinEffects.explosion(target, 1.0);
				});
				case 24 -> strike(5.0, 60, () -> LaevateinEffects.flameArc(player, target, -60, 40));
				case 32 -> {
					release();
					strike(9.0, 100, () -> LaevateinEffects.explosion(target, 2.5));
				}
				default -> {
				}
			}
		}

		/** "Brought out this far": lightning falls, two burning slashes, then an inferno. */
		private void stageThree() {
			switch (tick) {
				case 6, 14 -> strike(4.0, 0, () -> LaevateinEffects.lightningStrike(target));
				case 22 -> strike(6.0, 80, () -> LaevateinEffects.flameArc(player, target, -100, 20));
				case 30 -> strike(6.0, 80, () -> LaevateinEffects.flameArc(player, target, 80, -30));
				case 38 -> {
					release();
					strike(14.0, 160, () -> LaevateinEffects.inferno(target));
				}
				default -> {
				}
			}
		}

		private void strike(double damage, int fireTicks, Runnable effect) {
			player.swingMainHand();
			effect.run();
			Matthias.addTattoo(player);
			Damage.deal(target, Matthias.scale(player, damage), player, true);
			if (fireTicks > 0 && target.isValid()) {
				target.setFireTicks(Math.max(target.getFireTicks(), fireTicks));
			}
		}

		/** The blade takes its new form and the hit count starts over. */
		private void release() {
			ItemStack held = player.getInventory().getItemInMainHand();
			if (Items.isLaevatein(held)) {
				Items.setLaevateinStage(held, stage);
				setHits(held, 0);
			}
			Matthias.grantGrit(player, stage);
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

		private void finish() {
			if (!(target instanceof Player)) {
				target.setAI(hadAi);
			}
		}
	}
}
