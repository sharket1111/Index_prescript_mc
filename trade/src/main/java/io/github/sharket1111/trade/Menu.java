package io.github.sharket1111.trade;

import org.bukkit.Bukkit;
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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * A 9x3 chest-style menu. Its items cannot be taken out and nothing can be
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
		this.player = player;
		this.inventory = Bukkit.createInventory(this, SIZE, Component.text(title));
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

	/** Gives items, dropping what does not fit at the player's feet. */
	final void give(ItemStack stack) {
		player.getInventory().addItem(stack).values().forEach(rest -> player.getWorld().dropItem(player.getLocation(), rest));
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
