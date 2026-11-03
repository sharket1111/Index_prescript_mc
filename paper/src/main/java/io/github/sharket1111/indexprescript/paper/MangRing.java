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

/**
 * [Mang]'s glowing golden rings, wound round the held weapon. The rings are
 * part of the weapon's model (tools/mang_models.py): while Mang is shown, the
 * weapon in the holder's main hand carries a second custom model data string
 * ("mang1".."mang6") that picks the variant with that many rings. Because the
 * rings belong to the item, they sit on the blade in first person, in third
 * person and in everyone else's view, and follow every swing.
 *
 * The string only ever stays on the weapon in hand while Mang lasts: it is
 * taken off when Mang ends, when the weapon leaves the hand (another slot,
 * dropped, a chest opened, death, leaving) and by a sweep of the inventory.
 * Bedrock players cannot see the Java model, so they see golden motes instead.
 */
final class MangRing {
	static final int MAX_RINGS = 6;
	private static final int SWEEP_TICKS = 10;
	private static final Map<UUID, Ring> RINGS = new ConcurrentHashMap<>();
	private static int clock;

	private static final class Ring {
		final LivingEntity holder;
		final long until;
		final int count;
		int age;

		Ring(LivingEntity holder, long until, int count) {
			this.holder = holder;
			this.until = until;
			this.count = count;
		}
	}

	private MangRing() {
	}

	/** Shows one ring on {@code holder}'s weapon for {@code ticks} ticks, or until hidden if 0. */
	static void show(LivingEntity holder, int ticks) {
		show(holder, ticks, 1);
	}

	/** Shows {@code count} rings (1-6) on {@code holder}'s weapon for {@code ticks} ticks, or until hidden if 0. */
	static void show(LivingEntity holder, int ticks, int count) {
		count = Math.max(1, Math.min(MAX_RINGS, count));
		long until = ticks > 0 ? CaduceusListener.now() + ticks : Long.MAX_VALUE;
		RINGS.put(holder.getUniqueId(), new Ring(holder, until, count));
		wind(holder, count);
		Effects.mangForm(holder);
	}

	static void hide(LivingEntity holder) {
		if (RINGS.remove(holder.getUniqueId()) != null) {
			unwind(holder);
		}
	}

	static boolean showing(LivingEntity holder) {
		return RINGS.containsKey(holder.getUniqueId());
	}

	/** How many rings {@code holder} has, or 0. */
	static int count(LivingEntity holder) {
		Ring ring = RINGS.get(holder.getUniqueId());
		return ring == null ? 0 : ring.count;
	}

	/** Puts the rings back on a weapon just put in {@code holder}'s hand (e.g. a new form), if Mang is shown. */
	static void refresh(LivingEntity holder) {
		Ring ring = RINGS.get(holder.getUniqueId());
		if (ring != null) {
			wind(holder, ring.count);
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

	/** Called every tick: keeps the rings on the weapon in hand, and only there. */
	static void tick() {
		long now = CaduceusListener.now();
		Iterator<Ring> it = RINGS.values().iterator();
		while (it.hasNext()) {
			Ring ring = it.next();
			if (now >= ring.until || !ring.holder.isValid() || ring.holder.isDead()) {
				it.remove();
				unwind(ring.holder);
				continue;
			}
			wind(ring.holder, ring.count);
			if (ring.age % 4 == 0) {
				Effects.mang(ring.holder, ring.age, Effects.weaponFrame(ring.holder));
			}
			ring.age++;
		}
		if (++clock % SWEEP_TICKS == 0) {
			for (Player player : Bukkit.getOnlinePlayers()) {
				strip(player, showing(player) ? player.getInventory().getHeldItemSlot() : -1);
			}
		}
	}

	static void hideAll() {
		for (Ring ring : RINGS.values()) {
			unwind(ring.holder);
		}
		RINGS.clear();
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
			hide(event.getPlayer());
		}

		@EventHandler
		public void onQuit(PlayerQuitEvent event) {
			hide(event.getPlayer());
			strip(event.getPlayer(), -1);
		}

		@EventHandler
		public void onJoin(PlayerJoinEvent event) {
			strip(event.getPlayer(), -1);
		}
	}
}
