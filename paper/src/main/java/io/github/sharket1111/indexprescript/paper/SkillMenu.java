package io.github.sharket1111.indexprescript.paper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Level;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * The hotbar menu every weapon's skills (and the E.G.O) are picked from, like
 * Mimicry's combat pages: the real hotbar is put aside, each slot shows a
 * choice, a number key picks it, 9 or waiting 5 seconds puts the hotbar back.
 *
 * Right-clicks arrive again and again while the button is held, so a
 * right-click never picks: one click opens the menu and never fires the skill
 * that lands under the cursor too.
 * The hotbar is kept on the player too, so a crash mid-choice gives it back.
 */
final class SkillMenu {
	/** A slot of the menu: its icon, and what picking it does (null: shown only). */
	record Entry(ItemStack icon, Runnable action) {
	}

	private static final int CHOOSE_TICKS = 100;
	/** A right-click on an icon this soon after opening is the same click that opened it. */
	private static final int PICK_GUARD = 6;
	private static final int OPEN_DEBOUNCE = 6;
	static final int CANCEL_SLOT = 8;
	private static final NamespacedKey ICON = new NamespacedKey(Items.NAMESPACE, "menu_icon");
	private static final NamespacedKey BACKUP = new NamespacedKey(Items.NAMESPACE, "menu_hotbar_backup");
	/** Hotbars saved by the older Mimicry-only menu, given back the same way. */
	private static final NamespacedKey OLD_BACKUP = new NamespacedKey(Items.NAMESPACE, "mimicry_hotbar_backup");
	private static final NamespacedKey OLD_ICON = new NamespacedKey(Items.NAMESPACE, "mimicry_icon");

	private static final class Menu {
		final String key;
		final ItemStack[] saved;
		final Supplier<List<Entry>> entries;
		final long opened;
		final int heldSlot;
		long until;
		List<Entry> shown = List.of();

		Menu(String key, ItemStack[] saved, Supplier<List<Entry>> entries, long opened, int heldSlot) {
			this.heldSlot = heldSlot;
			this.key = key;
			this.saved = saved;
			this.entries = entries;
			this.opened = opened;
			this.until = opened + CHOOSE_TICKS;
		}
	}

	private static final Map<UUID, Menu> OPEN = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_OPEN = new ConcurrentHashMap<>();

	private SkillMenu() {
	}

	static boolean choosing(Player player) {
		return OPEN.containsKey(player.getUniqueId());
	}

	/** Whether the menu open is the one called {@code key} (e.g. "weapon", "ego"). */
	static boolean choosing(Player player, String key) {
		Menu menu = OPEN.get(player.getUniqueId());
		return menu != null && menu.key.equals(key);
	}

	static boolean isIcon(ItemStack stack) {
		if (stack == null || stack.isEmpty() || !stack.hasItemMeta()) {
			return false;
		}
		var data = stack.getItemMeta().getPersistentDataContainer();
		return data.has(ICON, PersistentDataType.INTEGER) || data.has(OLD_ICON, PersistentDataType.INTEGER);
	}

	// --- icons ---------------------------------------------------------------------------

	static Component line(String text, TextColor colour) {
		return Component.text(text, colour).decoration(TextDecoration.ITALIC, false);
	}

	/** An icon: {@code model} is an item model id (e.g. "minecraft:iron_sword"), {@code amount} shows as the stack's count. */
	static ItemStack icon(String model, int amount, Component name, List<Component> lore) {
		ItemStack stack = ItemStack.of(Material.PAPER);
		stack.editMeta(meta -> {
			meta.setMaxStackSize(99);
			meta.itemName(name);
			meta.lore(lore);
			meta.setRarity(ItemRarity.EPIC);
			meta.addItemFlags(ItemFlag.values());
			meta.setItemModel(NamespacedKey.fromString(model));
		});
		stack.setAmount(Math.max(1, Math.min(99, amount)));
		return stack;
	}

	/** A greyed-out icon for a choice that cannot be picked now, with why. */
	static ItemStack locked(String name, String why, List<Component> lore) {
		List<Component> all = new ArrayList<>(lore);
		all.add(line(why, NamedTextColor.RED));
		return icon("minecraft:barrier", 1, line(name, NamedTextColor.DARK_GRAY), all);
	}

	private static ItemStack tag(ItemStack stack, int slot) {
		ItemStack copy = stack.clone();
		copy.editMeta(meta -> meta.getPersistentDataContainer().set(ICON, PersistentDataType.INTEGER, slot));
		return copy;
	}

	// --- opening and closing -----------------------------------------------------------------

	/**
	 * Opens the menu {@code key} with up to 8 choices; {@code entries} is asked again whenever
	 * the menu is refreshed (cooldowns ticking down...). Returns false if it could not open.
	 */
	static boolean open(Player player, String key, Supplier<List<Entry>> entries, Component hint) {
		long now = CaduceusListener.now();
		Menu current = OPEN.get(player.getUniqueId());
		if (current != null) {
			// The other menu (weapon / E.G.O) gives way; the same one stays.
			if (current.key.equals(key)) {
				return true;
			}
			close(player);
		}
		if (now - LAST_OPEN.getOrDefault(player.getUniqueId(), Long.MIN_VALUE / 2) < OPEN_DEBOUNCE
				|| player.getGameMode() == GameMode.SPECTATOR || player.isDead()) {
			return false;
		}
		LAST_OPEN.put(player.getUniqueId(), now);
		PlayerInventory inventory = player.getInventory();
		ItemStack[] saved = new ItemStack[9];
		for (int slot = 0; slot < 9; slot++) {
			ItemStack stack = inventory.getItem(slot);
			saved[slot] = stack == null || stack.isEmpty() || isIcon(stack) ? null : stack.clone();
		}
		backup(player, saved);
		Menu menu = new Menu(key, saved, entries, now, inventory.getHeldItemSlot());
		// The cursor waits on 9 (cancel): pressing the slot already held sends nothing, so 1-8 must all be other slots.
		inventory.setHeldItemSlot(CANCEL_SLOT);
		OPEN.put(player.getUniqueId(), menu);
		lay(player, menu);
		player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, SoundCategory.PLAYERS, 1.0F, 0.8F);
		Hud.message(player, hint);
		return true;
	}

	/** Lays the icons again (after a cooldown changed, a resource came in...). */
	static void refresh(Player player) {
		Menu menu = OPEN.get(player.getUniqueId());
		if (menu != null) {
			lay(player, menu);
		}
	}

	/** Every hotbar slot gets an icon, so nothing picked up lands in the hotbar meanwhile. */
	private static void lay(Player player, Menu menu) {
		List<Entry> entries;
		try {
			entries = menu.entries.get();
		} catch (RuntimeException e) {
			IndexPrescriptPlugin.instance().getLogger().log(Level.WARNING, "Menu failed", e);
			entries = List.of();
		}
		menu.shown = entries;
		PlayerInventory inventory = player.getInventory();
		for (int slot = 0; slot < 9; slot++) {
			ItemStack icon;
			if (slot == CANCEL_SLOT) {
				icon = icon("minecraft:barrier", 1, line("9. 취소", NamedTextColor.GRAY), List.of());
			} else if (slot < entries.size() && entries.get(slot) != null) {
				icon = entries.get(slot).icon();
			} else {
				icon = icon("minecraft:light_gray_stained_glass_pane", 1, line(" ", NamedTextColor.DARK_GRAY), List.of());
			}
			inventory.setItem(slot, tag(icon, slot));
		}
	}

	static void close(Player player) {
		Menu menu = OPEN.remove(player.getUniqueId());
		if (menu != null) {
			restore(player, menu.saved);
			player.getInventory().setHeldItemSlot(menu.heldSlot);
		}
	}

	private static void pick(Player player, int slot, boolean byClick) {
		Menu menu = OPEN.get(player.getUniqueId());
		if (menu == null) {
			return;
		}
		if (byClick && CaduceusListener.now() - menu.opened < PICK_GUARD) {
			return;
		}
		if (slot == CANCEL_SLOT) {
			close(player);
			return;
		}
		Entry entry = slot >= 0 && slot < menu.shown.size() ? menu.shown.get(slot) : null;
		if (entry == null || entry.action() == null) {
			return;
		}
		close(player);
		entry.action().run();
	}

	/** Every tick: menus left open too long close. */
	static void tick() {
		long now = CaduceusListener.now();
		for (Map.Entry<UUID, Menu> open : List.copyOf(OPEN.entrySet())) {
			Player player = org.bukkit.Bukkit.getPlayer(open.getKey());
			if (player == null || now >= open.getValue().until) {
				if (player != null) {
					close(player);
				} else {
					OPEN.remove(open.getKey());
				}
			} else if (now % 10 == 0) {
				// Cooldowns and resources change: the icons follow.
				lay(player, open.getValue());
			}
		}
	}

	static void closeAll() {
		for (UUID id : List.copyOf(OPEN.keySet())) {
			Player player = org.bukkit.Bukkit.getPlayer(id);
			if (player != null) {
				close(player);
			}
		}
		OPEN.clear();
	}

	// --- keeping the hotbar safe ---------------------------------------------------------------

	private static void restore(Player player, ItemStack[] saved) {
		PlayerInventory inventory = player.getInventory();
		List<ItemStack> stray = new ArrayList<>();
		for (int slot = 0; slot < 9; slot++) {
			ItemStack current = inventory.getItem(slot);
			if (current != null && !current.isEmpty() && !isIcon(current)) {
				stray.add(current);
			}
			inventory.setItem(slot, saved[slot]);
		}
		purge(player);
		for (ItemStack stack : stray) {
			inventory.addItem(stack).values().forEach(rest -> player.getWorld().dropItem(player.getLocation(), rest));
		}
		player.getPersistentDataContainer().remove(BACKUP);
	}

	private static void purge(Player player) {
		PlayerInventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getSize(); slot++) {
			if (isIcon(inventory.getItem(slot))) {
				inventory.setItem(slot, null);
			}
		}
		if (isIcon(player.getItemOnCursor())) {
			player.setItemOnCursor(null);
		}
	}

	private static void backup(Player player, ItemStack[] saved) {
		List<byte[]> bytes = new ArrayList<>();
		for (ItemStack stack : saved) {
			bytes.add(stack == null ? new byte[0] : stack.serializeAsBytes());
		}
		player.getPersistentDataContainer().set(BACKUP, PersistentDataType.LIST.byteArrays(), bytes);
	}

	/** After a crash mid-choice: the hotbar saved on the player comes back. */
	private static void recover(Player player) {
		for (NamespacedKey key : List.of(BACKUP, OLD_BACKUP)) {
			List<byte[]> bytes = player.getPersistentDataContainer().get(key, PersistentDataType.LIST.byteArrays());
			if (bytes == null) {
				continue;
			}
			ItemStack[] saved = new ItemStack[9];
			for (int slot = 0; slot < Math.min(9, bytes.size()); slot++) {
				byte[] data = bytes.get(slot);
				try {
					saved[slot] = data.length == 0 ? null : ItemStack.deserializeBytes(data);
				} catch (RuntimeException e) {
					IndexPrescriptPlugin.instance().getLogger().log(Level.WARNING, "Could not restore a hotbar slot", e);
				}
			}
			restore(player, saved);
			player.getPersistentDataContainer().remove(key);
			return;
		}
		purge(player);
	}

	// --- events ------------------------------------------------------------------------------

	static final class Events implements Listener {
		/** Right-clicking an icon in hand picks it (not the click that opened the menu). */
		@EventHandler(priority = EventPriority.LOWEST)
		public void onUse(org.bukkit.event.player.PlayerInteractEvent event) {
			if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND || !isIcon(event.getItem())) {
				return;
			}
			event.setCancelled(true);
			Player player = event.getPlayer();
			// Holding the button sends right-clicks again and again: picking is by number key only.
			if (!choosing(player)) {
				purge(player);
			}
		}

		@EventHandler(priority = EventPriority.LOWEST)
		public void onUseOn(org.bukkit.event.player.PlayerInteractEntityEvent event) {
			Player player = event.getPlayer();
			if (event.getHand() == org.bukkit.inventory.EquipmentSlot.HAND && isIcon(player.getInventory().getItemInMainHand())) {
				event.setCancelled(true);
			}
		}

		/** While choosing, a number key (or the scroll wheel) picks that slot instead of moving. */
		@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
		public void onSwitch(PlayerItemHeldEvent event) {
			Player player = event.getPlayer();
			if (!choosing(player)) {
				return;
			}
			event.setCancelled(true);
			pick(player, event.getNewSlot(), false);
		}

		@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
		public void onClick(InventoryClickEvent event) {
			if (event.getWhoClicked() instanceof Player player && (choosing(player) || isIcon(event.getCurrentItem()) || isIcon(event.getCursor()))) {
				event.setCancelled(true);
			}
		}

		@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
		public void onDrag(InventoryDragEvent event) {
			if (event.getWhoClicked() instanceof Player player && (choosing(player) || isIcon(event.getOldCursor()))) {
				event.setCancelled(true);
			}
		}

		@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
		public void onDrop(PlayerDropItemEvent event) {
			if (isIcon(event.getItemDrop().getItemStack())) {
				event.setCancelled(true);
				if (choosing(event.getPlayer())) {
					close(event.getPlayer());
				}
			}
		}

		/** F while a menu is open closes it (the E.G.O listener opens its own after). */
		@EventHandler(priority = EventPriority.LOWEST)
		public void onSwapHands(PlayerSwapHandItemsEvent event) {
			if (isIcon(event.getMainHandItem()) || isIcon(event.getOffHandItem())) {
				event.setCancelled(true);
			}
		}

		@EventHandler(priority = EventPriority.HIGHEST)
		public void onDeath(PlayerDeathEvent event) {
			Player player = event.getPlayer();
			event.getDrops().removeIf(SkillMenu::isIcon);
			Menu menu = OPEN.remove(player.getUniqueId());
			if (menu == null) {
				return;
			}
			if (event.getKeepInventory()) {
				restore(player, menu.saved);
			} else {
				for (ItemStack stack : menu.saved) {
					if (stack != null) {
						event.getDrops().add(stack);
					}
				}
				purge(player);
				player.getPersistentDataContainer().remove(BACKUP);
			}
		}

		@EventHandler
		public void onQuit(PlayerQuitEvent event) {
			close(event.getPlayer());
			LAST_OPEN.remove(event.getPlayer().getUniqueId());
		}

		@EventHandler
		public void onJoin(PlayerJoinEvent event) {
			recover(event.getPlayer());
		}
	}
}
