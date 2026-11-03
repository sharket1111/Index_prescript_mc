package io.github.sharket1111.trade;

import java.util.function.Predicate;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * A chest-style menu (9x3, or 9x6 for long lists). Its items cannot be taken out and nothing can be
 * put in; clicks on it are handed to {@link #click}. Bedrock players
 * (Floodgate) cannot right-click on a touch screen, so where a button asks
 * for a right-click any click works for them.
 */
abstract class Menu implements InventoryHolder {
	static final int SIZE = 27;
	static final int BACK_SLOT = 18;
	private static final String FLOODGATE_PREFIX = "00000000-0000-0000-";

	protected final Player player;
	protected final Inventory inventory;

	Menu(Player player, String title) {
		this(player, title, 3);
	}

	/** A menu of {@code rows} rows (3 or 6). */
	Menu(Player player, String title, int rows) {
		this.player = player;
		this.inventory = Bukkit.createInventory(this, rows * 9, Component.text(title));
	}

	/** The player closed this menu (also when another menu replaces it). */
	void closed() {
	}

	@Override
	public final Inventory getInventory() {
		return inventory;
	}

	/** Fills the menu; called on opening and whenever it changes. */
	abstract void draw();

	/** A click on one of the menu's own slots. */
	abstract void click(int slot, ClickType click);

	/**
	 * A click in the player's own inventory below the menu. By default it works
	 * as usual, except that nothing may be moved into the menu.
	 */
	void clickOwn(InventoryClickEvent event) {
		InventoryAction action = event.getAction();
		if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY || action == InventoryAction.COLLECT_TO_CURSOR) {
			event.setCancelled(true);
		}
	}

	final void open() {
		inventory.clear();
		draw();
		player.openInventory(inventory);
	}

	final void redraw() {
		inventory.clear();
		draw();
	}

	/** A right-click, or any click from a Bedrock player. */
	final boolean confirmed(ClickType click) {
		return click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT
				|| bedrock() && (click == ClickType.LEFT || click == ClickType.SHIFT_LEFT);
	}

	final boolean bedrock() {
		return player.getUniqueId().toString().startsWith(FLOODGATE_PREFIX);
	}

	/** Refuses (with a message) the free handy items, which may not be sold, traded or stored for others. */
	final boolean refuseTool(ItemStack stack) {
		if (TradeTools.toolOf(stack) == null) {
			return false;
		}
		tell("무료 아이템(거래 메뉴, 웨이포인트 책)은 거래하거나 맡길 수 없습니다.", NamedTextColor.RED);
		failSound();
		return true;
	}

	final void tell(String message, NamedTextColor color) {
		player.sendMessage(Component.text(message, color));
	}

	final void clickSound() {
		player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.MASTER, 0.5F, 1.0F);
	}

	final void failSound() {
		player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.PLAYERS, 0.8F, 1.0F);
	}

	final void successSound() {
		player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.8F, 1.2F);
	}

	/** Gives trade diamonds in full stacks, dropping what does not fit. */
	static void giveTradeDiamonds(Player to, int count) {
		int max = Material.DIAMOND.getMaxStackSize();
		for (int given = 0; given < count; given += max) {
			to.getInventory().addItem(TradeItems.tradeDiamond(Math.min(max, count - given))).values()
					.forEach(rest -> to.getWorld().dropItem(to.getLocation(), rest));
		}
	}

	/** Gives items, dropping what does not fit at the player's feet. */
	final void give(ItemStack stack) {
		player.getInventory().addItem(stack).values().forEach(rest -> player.getWorld().dropItem(player.getLocation(), rest));
	}

	/** Removes up to {@code wanted} matching items from the inventory's storage; returns how many. */
	final int take(Predicate<ItemStack> matches, int wanted) {
		PlayerInventory inv = player.getInventory();
		ItemStack[] contents = inv.getStorageContents();
		int left = wanted;
		for (int slot = 0; slot < contents.length && left > 0; slot++) {
			ItemStack stack = contents[slot];
			if (stack == null || !matches.test(stack)) {
				continue;
			}
			int taken = Math.min(left, stack.getAmount());
			stack.setAmount(stack.getAmount() - taken);
			inv.setItem(slot, stack.getAmount() > 0 ? stack : null);
			left -= taken;
		}
		return wanted - left;
	}

	final void giveStacks(ItemStack template, int count) {
		int max = template.getMaxStackSize();
		for (int given = 0; given < count; given += max) {
			ItemStack stack = template.clone();
			stack.setAmount(Math.min(max, count - given));
			give(stack);
		}
	}

	/** How many matching items the player carries. */
	final int count(Predicate<ItemStack> matches) {
		int count = 0;
		for (ItemStack stack : player.getInventory().getStorageContents()) {
			if (stack != null && matches.test(stack)) {
				count += stack.getAmount();
			}
		}
		return count;
	}

	static final class Events implements Listener {
		@EventHandler(priority = EventPriority.HIGHEST)
		public void onClick(InventoryClickEvent event) {
			if (!(event.getView().getTopInventory().getHolder(false) instanceof Menu menu)) {
				return;
			}
			if (event.getClickedInventory() == event.getView().getTopInventory()) {
				// Menu items stay where they are.
				event.setCancelled(true);
				menu.click(event.getRawSlot(), event.getClick());
			} else if (event.getClickedInventory() != null) {
				menu.clickOwn(event);
			}
		}

		/** Hands over money from sales made while the seller was away, and points out new mail. */
		@EventHandler
		public void onJoin(PlayerJoinEvent event) {
			Market.payOut(event.getPlayer());
			Mailbox.notifyOnJoin(event.getPlayer());
		}

		@EventHandler
		public void onClose(InventoryCloseEvent event) {
			if (event.getInventory().getHolder(false) instanceof Menu menu && event.getPlayer() == menu.player) {
				menu.closed();
			}
		}

		@EventHandler
		public void onQuit(PlayerQuitEvent event) {
			DirectTrade.leave(event.getPlayer());
		}

		@EventHandler(priority = EventPriority.HIGHEST)
		public void onDrag(InventoryDragEvent event) {
			if (!(event.getView().getTopInventory().getHolder(false) instanceof Menu)) {
				return;
			}
			int size = event.getView().getTopInventory().getSize();
			if (event.getRawSlots().stream().anyMatch(slot -> slot < size)) {
				event.setCancelled(true);
			}
		}
	}
}
