package io.github.sharket1111.indexprescript.paper;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * [Mang]'s glowing golden rings, wound round the held weapon. Mang is the
 * Strength effect (shown as Mang, with a ring icon, by the resource pack):
 * anyone with Strength has as many rings as its level (up to six), whether
 * it came from a skill, /ip mang or a potion, and showing Mang gives Strength.
 *
 * The rings are part of the weapon's model (tools/mang_models.py): the
 * weapon in the holder's main hand carries a second custom model data
 * string ("mang1".."mang6") that picks the variant with that many rings.
 * Because the rings belong to the item, they sit on the blade in first
 * person, in third person and in everyone else's view, and follow every swing.
 *
 * The string only ever stays on the weapon in hand while Mang lasts: it is
 * taken off when Strength ends, when the weapon leaves the hand (another
 * slot, dropped, a chest opened, death, leaving) and by a sweep of the
 * inventory. Bedrock players cannot see the Java model, so they see golden
 * motes instead.
 */
final class MangRing {
	static final int MAX_RINGS = 6;
	private static final int SWEEP_TICKS = 10;
	/** Rings each holder had last tick, to unwind them and to flash when more appear. */
	private static final Map<UUID, Integer> LEVELS = new ConcurrentHashMap<>();
	/** Holders that are not players (a skill's stand-in), checked each tick alongside the players. */
	private static final Map<UUID, LivingEntity> OTHERS = new ConcurrentHashMap<>();
	private static int clock;

	private MangRing() {
	}

	/** How many rings {@code holder} has: their Strength level, up to six, or 0. */
	static int count(LivingEntity holder) {
		PotionEffect strength = holder.getPotionEffect(PotionEffectType.STRENGTH);
		return strength == null ? 0 : Math.min(MAX_RINGS, strength.getAmplifier() + 1);
	}

	static boolean showing(LivingEntity holder) {
		return count(holder) > 0;
	}

	/** Shows one ring on {@code holder}'s weapon for {@code ticks} ticks, or until hidden if 0. */
	static void show(LivingEntity holder, int ticks) {
		show(holder, ticks, 1);
	}

	/**
	 * Gives {@code holder} Mang with {@code count} rings (1-6) for {@code ticks}
	 * ticks, or until hidden if 0: Strength of that level, icon shown, no swirls.
	 */
	static void show(LivingEntity holder, int ticks, int count) {
		count = Math.max(1, Math.min(MAX_RINGS, count));
		holder.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, ticks > 0 ? ticks : PotionEffect.INFINITE_DURATION,
				count - 1, false, false, true));
		if (!(holder instanceof Player)) {
			OTHERS.put(holder.getUniqueId(), holder);
		}
		update(holder);
	}

	/** Ends Mang: takes away Strength and the rings. */
	static void hide(LivingEntity holder) {
		holder.removePotionEffect(PotionEffectType.STRENGTH);
		update(holder);
		OTHERS.remove(holder.getUniqueId());
	}

	/** Ends Mang given until hidden (by a skill), leaving Strength from anything else (a potion) alone. */
	static void hideEndless(LivingEntity holder) {
		PotionEffect strength = holder.getPotionEffect(PotionEffectType.STRENGTH);
		if (strength != null && strength.isInfinite()) {
			hide(holder);
		}
	}

	/** Puts the rings back on a weapon just put in {@code holder}'s hand (e.g. a new form), if Mang is shown. */
	static void refresh(LivingEntity holder) {
		int count = count(holder);
		if (count > 0) {
			wind(holder, count);
		}
	}

	/** Matches the rings to the holder's Strength now: winds, rewinds or unwinds them. */
	private static void update(LivingEntity holder) {
		int count = holder.isValid() && !holder.isDead() ? count(holder) : 0;
		Integer before = count > 0 ? LEVELS.put(holder.getUniqueId(), count) : LEVELS.remove(holder.getUniqueId());
		int was = before == null ? 0 : before;
		if (count > 0) {
			wind(holder, count);
			if (count > was) {
				Effects.mangForm(holder);
			}
		} else if (was > 0) {
			unwind(holder);
		}
	}

	/** Gives the weapon in the main hand {@code count} rings, if it does not have them already. */
	private static void wind(LivingEntity holder, int count) {
		EntityEquipment equipment = holder.getEquipment();
		if (equipment == null) {
			return;
		}
		ItemStack held = equipment.getItemInMainHand();
		if (!Items.isWeapon(held) || ("mang" + count).equals(Items.mangString(held))) {
			return;
		}
		if (holder instanceof Player player && player.getOpenInventory().getTopInventory().getType() != InventoryType.CRAFTING) {
			// A chest or other container is open: the weapon could be moved into it with the rings on.
			return;
		}
		Items.setMang(held, count);
		equipment.setItemInMainHand(held);
	}

	private static void unwind(LivingEntity holder) {
		if (holder instanceof Player player) {
			strip(player, -1);
			return;
		}
		EntityEquipment equipment = holder.getEquipment();
		if (equipment != null) {
			ItemStack held = equipment.getItemInMainHand();
			if (Items.mangString(held) != null) {
				Items.setMang(held, 0);
				equipment.setItemInMainHand(held);
			}
		}
	}

	/** Takes the rings off every item the player carries, except in slot {@code keep} (-1 for none). */
	private static void strip(Player player, int keep) {
		PlayerInventory inventory = player.getInventory();
		ItemStack[] contents = inventory.getContents();
		for (int slot = 0; slot < contents.length; slot++) {
			ItemStack stack = contents[slot];
			if (slot != keep && stack != null && Items.mangString(stack) != null) {
				Items.setMang(stack, 0);
				inventory.setItem(slot, stack);
			}
		}
		ItemStack cursor = player.getItemOnCursor();
		if (Items.mangString(cursor) != null) {
			Items.setMang(cursor, 0);
			player.setItemOnCursor(cursor);
		}
	}

	/** Called every tick: keeps each holder's rings matched to their Strength, on the weapon in hand only. */
	static void tick() {
		clock++;
		for (Player player : Bukkit.getOnlinePlayers()) {
			tickHolder(player);
		}
		Iterator<LivingEntity> it = OTHERS.values().iterator();
		while (it.hasNext()) {
			LivingEntity holder = it.next();
			tickHolder(holder);
			if (!holder.isValid() || holder.isDead() || count(holder) == 0) {
				it.remove();
			}
		}
		if (clock % SWEEP_TICKS == 0) {
			for (Player player : Bukkit.getOnlinePlayers()) {
				strip(player, showing(player) ? player.getInventory().getHeldItemSlot() : -1);
			}
		}
	}

	private static void tickHolder(LivingEntity holder) {
		update(holder);
		if (clock % 4 == 0 && LEVELS.containsKey(holder.getUniqueId())) {
			Effects.mang(holder, clock, Effects.weaponFrame(holder));
		}
	}

	static void hideAll() {
		for (Player player : Bukkit.getOnlinePlayers()) {
			strip(player, -1);
		}
		for (LivingEntity holder : OTHERS.values()) {
			unwind(holder);
		}
		OTHERS.clear();
		LEVELS.clear();
	}

	/** Takes the rings off a weapon leaving the holder's hand, so they never stay on a stored item. */
	static final class Events implements Listener {
		@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
		public void onDrop(PlayerDropItemEvent event) {
			ItemStack stack = event.getItemDrop().getItemStack();
			if (Items.mangString(stack) != null) {
				Items.setMang(stack, 0);
				event.getItemDrop().setItemStack(stack);
			}
		}

		/** Switching slots: the rings move to the new weapon (next tick) and leave the old one now. */
		@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
		public void onSwitch(PlayerItemHeldEvent event) {
			strip(event.getPlayer(), event.getNewSlot());
		}

		@EventHandler(priority = EventPriority.LOWEST)
		public void onOpen(InventoryOpenEvent event) {
			if (event.getPlayer() instanceof Player player) {
				strip(player, -1);
			}
		}

		@EventHandler(priority = EventPriority.LOWEST)
		public void onDeath(PlayerDeathEvent event) {
			for (ItemStack stack : event.getDrops()) {
				if (Items.mangString(stack) != null) {
					Items.setMang(stack, 0);
				}
			}
			LEVELS.remove(event.getPlayer().getUniqueId());
		}

		@EventHandler
		public void onQuit(PlayerQuitEvent event) {
			LEVELS.remove(event.getPlayer().getUniqueId());
			strip(event.getPlayer(), -1);
		}

		@EventHandler
		public void onJoin(PlayerJoinEvent event) {
			strip(event.getPlayer(), -1);
		}
	}
}
