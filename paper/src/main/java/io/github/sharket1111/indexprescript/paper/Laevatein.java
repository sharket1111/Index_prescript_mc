package io.github.sharket1111.indexprescript.paper;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;

/**
 * Matthias's Laevatein. The original releases it by spending stacks with
 * certain skills; here each stage unlocks after a number of hits dealt or
 * taken (5, 7, then 9), shown as the stack size of a [Seal Release] icon held
 * in the off hand while Laevatein is wielded. Right-click (or swap-hands on
 * Java) with a ready Laevatein and an enemy nearby releases one stage in a
 * strike sequence modelled on the in-game animation.
 */
final class Laevatein implements Listener {
	private static final int[] HITS_TO_RELEASE = {5, 7, 9};
	private static final int MAX_STAGE = Items.LAEVATEIN_STAGES.size() - 1;
	private static final int TARGET_RANGE = 6;
	private static final int READY_EFFECT_INTERVAL = 4;
	private static final int CLEANUP_INTERVAL = 5;
	private static final int ERROR_LOG_INTERVAL = 200;
	private static final NamespacedKey HITS = new NamespacedKey(Items.NAMESPACE, "laevatein_hits");

	private static final Map<UUID, Boolean> CHARGED = new ConcurrentHashMap<>();
	private static final Map<UUID, Release> RELEASING = new ConcurrentHashMap<>();
	private static long lastError = Long.MIN_VALUE / 2;

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

	/** Called every tick: the off-hand icon and the ready embers. */
	static void tick() {
		RELEASING.values().removeIf(release -> {
			try {
				return !release.tick();
			} catch (RuntimeException e) {
				IndexPrescriptPlugin.instance().getLogger().log(Level.WARNING, "Laevatein release failed", e);
				return true;
			}
		});
		long now = CaduceusListener.now();
		for (Player player : Bukkit.getOnlinePlayers()) {
			try {
				tick(player, now);
			} catch (RuntimeException e) {
				if (now - lastError > ERROR_LOG_INTERVAL) {
					lastError = now;
					IndexPrescriptPlugin.instance().getLogger().log(Level.WARNING, "Laevatein tick failed for " + player.getName(), e);
				}
			}
		}
	}

	private static void tick(Player player, long now) {
		PlayerInventory inventory = player.getInventory();
		ItemStack held = inventory.getItemInMainHand();
		if (Items.isLaevatein(held)) {
			if (held.getAmount() > 1) {
				held.setAmount(1); // left over from the old stack-size display
			}
			showIcon(player, inventory, held);
			if (!RELEASING.containsKey(player.getUniqueId()) && ready(held) && now % READY_EFFECT_INTERVAL == 0) {
				LaevateinEffects.ready(player);
			}
		} else if (Items.isLaevateinIcon(inventory.getItemInOffHand())) {
			inventory.setItemInOffHand(null);
		}
		if (now % CLEANUP_INTERVAL == 0) {
			removeStrayIcons(inventory);
		}
	}

	/**
	 * Keeps the [Seal Release] icon in the off hand while Laevatein is held; its
	 * stack size is the hit count and it glints once a release is ready. Whatever
	 * was in the off hand moves to a free inventory slot (no icon if there is none).
	 */
	private static void showIcon(Player player, PlayerInventory inventory, ItemStack held) {
		ItemStack off = inventory.getItemInOffHand();
		if (!Items.isLaevateinIcon(off)) {
			if (!off.isEmpty()) {
				int free = inventory.firstEmpty();
				if (free < 0) {
					return;
				}
				inventory.setItem(free, off);
			}
			off = Items.laevateinIcon(player);
		}
		int count = iconCount(held);
		boolean glint = ready(held);
		off.setAmount(count);
		ItemStack shown = off;
		if (shown.getItemMeta().hasEnchantmentGlintOverride() != glint) {
			shown.editMeta(meta -> meta.setEnchantmentGlintOverride(glint ? Boolean.TRUE : null));
		}
		if (!shown.equals(inventory.getItemInOffHand())) {
			inventory.setItemInOffHand(shown);
		}
	}

	static int iconCount(ItemStack held) {
		return Items.laevateinStage(held) < MAX_STAGE ? Math.max(1, hits(held)) : 1;
	}

	/** The icon only ever lives in the off hand. */
	private static void removeStrayIcons(PlayerInventory inventory) {
		ItemStack[] storage = inventory.getStorageContents();
		for (int slot = 0; slot < storage.length; slot++) {
			if (Items.isLaevateinIcon(storage[slot])) {
				inventory.setItem(slot, null);
			}
		}
	}

	private static void startRelease(Player player, LivingEntity target) {
		Release release = new Release(player, target, Items.laevateinStage(player.getInventory().getItemInMainHand()) + 1);
		RELEASING.put(player.getUniqueId(), release);
		release.start();
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
		CHARGED.put(player.getUniqueId(), Swings.accept(player));
	}

	/** Full swings that land count toward the next release. */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onHit(EntityDamageByEntityEvent event) {
		if (Damage.dealing() || event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK
				|| !(event.getDamager() instanceof Player player) || !(event.getEntity() instanceof LivingEntity)) {
			return;
		}
		if (Boolean.TRUE.equals(CHARGED.remove(player.getUniqueId()))) {
			addHit(player);
		}
	}

	/** Being hit by any mob or player while wielding Laevatein counts too. */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onHurt(EntityDamageByEntityEvent event) {
		if (event.getEntity() instanceof Player player && event.getFinalDamage() > 0 && attacker(event.getDamager()) != null
				&& attacker(event.getDamager()) != player) {
			addHit(player);
		}
	}

	private static LivingEntity attacker(Entity damager) {
		if (damager instanceof Projectile projectile && projectile.getShooter() instanceof LivingEntity shooter) {
			return shooter;
		}
		return damager instanceof LivingEntity living ? living : null;
	}

	private static void addHit(Player player) {
		ItemStack held = player.getInventory().getItemInMainHand();
		if (!Items.isLaevatein(held) || RELEASING.containsKey(player.getUniqueId())) {
			return;
		}
		int stage = Items.laevateinStage(held);
		if (stage >= MAX_STAGE || hits(held) >= HITS_TO_RELEASE[stage]) {
			return;
		}
		int hits = hits(held) + 1;
		setHits(held, hits);
		if (hits == HITS_TO_RELEASE[stage]) {
			LaevateinEffects.readySound(player);
		}
	}

	/** Releases the next stage if the held Laevatein is ready and an enemy is near. */
	private static void tryRelease(Player player) {
		ItemStack held = player.getInventory().getItemInMainHand();
		if (!Items.isLaevatein(held) || RELEASING.containsKey(player.getUniqueId()) || !ready(held)) {
			return;
		}
		LivingEntity target = findTarget(player);
		if (target != null) {
			startRelease(player, target);
		}
	}

	/** Right-click with Laevatein releases it (works on Java and Bedrock). */
	@EventHandler
	public void onUse(PlayerInteractEvent event) {
		if (event.getHand() != EquipmentSlot.HAND
				|| (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)
				|| !Items.isLaevatein(event.getItem())) {
			return;
		}
		tryRelease(event.getPlayer());
	}

	/** Swap-hands (F) never moves Laevatein or the icon; it releases instead when ready. */
	@EventHandler(ignoreCancelled = true)
	public void onSwap(PlayerSwapHandItemsEvent event) {
		PlayerInventory inventory = event.getPlayer().getInventory();
		if (!Items.isLaevatein(inventory.getItemInMainHand()) && !Items.isLaevateinIcon(inventory.getItemInOffHand())) {
			return;
		}
		event.setCancelled(true);
		tryRelease(event.getPlayer());
	}

	@EventHandler(ignoreCancelled = true)
	public void onDrop(PlayerDropItemEvent event) {
		if (Items.isLaevateinIcon(event.getItemDrop().getItemStack())) {
			event.setCancelled(true);
		}
	}

	@EventHandler(ignoreCancelled = true)
	public void onClick(InventoryClickEvent event) {
		boolean offhandIcon = event.getClick() == ClickType.SWAP_OFFHAND
				&& Items.isLaevateinIcon(event.getWhoClicked().getInventory().getItemInOffHand());
		if (offhandIcon || Items.isLaevateinIcon(event.getCurrentItem()) || Items.isLaevateinIcon(event.getCursor())) {
			event.setCancelled(true);
		}
	}

	@EventHandler(ignoreCancelled = true)
	public void onDrag(InventoryDragEvent event) {
		if (Items.isLaevateinIcon(event.getOldCursor())) {
			event.setCancelled(true);
		}
	}

	@EventHandler
	public void onDeath(PlayerDeathEvent event) {
		event.getDrops().removeIf(Items::isLaevateinIcon);
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
			if (distance > TARGET_RANGE) {
				continue;
			}
			// Enemies in front count as half as far, so they win over ones behind.
			double score = to.normalize().dot(look) > 0.6 ? distance * 0.5 : distance;
			if (score < bestDistance) {
				best = living;
				bestDistance = score;
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
