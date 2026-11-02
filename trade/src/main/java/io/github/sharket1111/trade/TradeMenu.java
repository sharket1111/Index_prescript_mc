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
import org.bukkit.inventory.PlayerInventory;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * The trade menu: a 9x3 chest-style screen. Nothing can be taken out of it
 * or put into it. Right-clicking the diamond turns one plain diamond from the
 * player's inventory into a trade diamond. Bedrock players (Floodgate) cannot
 * right-click on a touch screen, so any click on it works for them.
 */
final class TradeMenu implements InventoryHolder {
	private static final int ROWS = 3;
	private static final int EXCHANGE_SLOT = 13;
	private static final String FLOODGATE_PREFIX = "00000000-0000-0000-";

	private final Inventory inventory;

	private TradeMenu() {
		inventory = Bukkit.createInventory(this, ROWS * 9, Component.text("거래"));
		inventory.setItem(EXCHANGE_SLOT, TradeItems.exchangeButton());
	}

	@Override
	public Inventory getInventory() {
		return inventory;
	}

	static void open(Player player) {
		player.openInventory(new TradeMenu().inventory);
	}

	private static boolean bedrock(Player player) {
		return player.getUniqueId().toString().startsWith(FLOODGATE_PREFIX);
	}

	/** Turns one plain diamond from the player's inventory into a trade diamond. */
	private static void exchange(Player player) {
		PlayerInventory inv = player.getInventory();
		ItemStack[] contents = inv.getStorageContents();
		for (int slot = 0; slot < contents.length; slot++) {
			ItemStack stack = contents[slot];
			if (!TradeItems.isPlainDiamond(stack)) {
				continue;
			}
			stack.setAmount(stack.getAmount() - 1);
			inv.setItem(slot, stack.getAmount() > 0 ? stack : null);
			inv.addItem(TradeItems.tradeDiamond()).values()
					.forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
			player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.8F, 1.2F);
			return;
		}
		player.sendMessage(Component.text("인벤토리에 다이아몬드가 없습니다.", NamedTextColor.RED));
		player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.PLAYERS, 0.8F, 1.0F);
	}

	static final class Events implements Listener {
		@EventHandler(priority = EventPriority.HIGHEST)
		public void onClick(InventoryClickEvent event) {
			if (!(event.getView().getTopInventory().getHolder(false) instanceof TradeMenu)
					|| !(event.getWhoClicked() instanceof Player player)) {
				return;
			}
			if (event.getClickedInventory() == event.getView().getTopInventory()) {
				// Menu items stay where they are.
				event.setCancelled(true);
				boolean use = event.getClick() == ClickType.RIGHT
						|| bedrock(player) && (event.getClick() == ClickType.LEFT || event.getClick() == ClickType.SHIFT_LEFT);
				if (event.getRawSlot() == EXCHANGE_SLOT && use) {
					exchange(player);
				}
				return;
			}
			// From the player's own inventory, nothing may move into the menu.
			InventoryAction action = event.getAction();
			if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY || action == InventoryAction.COLLECT_TO_CURSOR) {
				event.setCancelled(true);
			}
		}

		@EventHandler(priority = EventPriority.HIGHEST)
		public void onDrag(InventoryDragEvent event) {
			if (!(event.getView().getTopInventory().getHolder(false) instanceof TradeMenu)) {
				return;
			}
			int size = event.getView().getTopInventory().getSize();
			if (event.getRawSlots().stream().anyMatch(slot -> slot < size)) {
				event.setCancelled(true);
			}
		}
	}
}
