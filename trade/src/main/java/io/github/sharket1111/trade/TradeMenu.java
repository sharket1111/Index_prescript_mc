package io.github.sharket1111.trade;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * The trade menu: a 9x3 chest-style screen. Nothing can be taken out of it
 * or put into it. The middle row holds the exchange: -10, -1, the diamond
 * (its stack size is the chosen amount, 1-64), +1, +10; below it the button
 * that exchanges every diamond. Right-clicking the diamond turns that many
 * plain diamonds from the player's inventory into trade diamonds. Bedrock
 * players (Floodgate) cannot right-click on a touch screen, so any click works
 * for them. The chosen amount is remembered per player until they leave.
 */
final class TradeMenu implements InventoryHolder {
	private static final int ROWS = 3;
	private static final int EXCHANGE_SLOT = 13;
	private static final int ALL_SLOT = 22;
	private static final int[] AMOUNT_SLOTS = {10, 11, 15, 16};
	private static final int[] AMOUNT_DELTAS = {-10, -1, 1, 10};
	private static final int MIN_AMOUNT = 1;
	private static final int MAX_AMOUNT = 64;
	private static final String FLOODGATE_PREFIX = "00000000-0000-0000-";
	private static final Map<UUID, Integer> AMOUNTS = new ConcurrentHashMap<>();

	private final Inventory inventory;
	private final Player player;

	private TradeMenu(Player player) {
		this.player = player;
		inventory = Bukkit.createInventory(this, ROWS * 9, Component.text("거래"));
		inventory.setItem(ALL_SLOT, TradeItems.exchangeAllButton());
		refresh();
	}

	@Override
	public Inventory getInventory() {
		return inventory;
	}

	static void open(Player player) {
		player.openInventory(new TradeMenu(player).inventory);
	}

	private int amount() {
		return AMOUNTS.getOrDefault(player.getUniqueId(), MIN_AMOUNT);
	}

	private void refresh() {
		int amount = amount();
		inventory.setItem(EXCHANGE_SLOT, TradeItems.exchangeButton(amount));
		for (int i = 0; i < AMOUNT_SLOTS.length; i++) {
			inventory.setItem(AMOUNT_SLOTS[i], TradeItems.amountButton(AMOUNT_DELTAS[i], amount));
		}
	}

	private void changeAmount(int delta) {
		int amount = Math.max(MIN_AMOUNT, Math.min(MAX_AMOUNT, amount() + delta));
		AMOUNTS.put(player.getUniqueId(), amount);
		refresh();
		player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.MASTER, 0.5F, delta > 0 ? 1.3F : 0.9F);
	}

	private static boolean bedrock(Player player) {
		return player.getUniqueId().toString().startsWith(FLOODGATE_PREFIX);
	}

	private static int countPlainDiamonds(PlayerInventory inv) {
		int count = 0;
		for (ItemStack stack : inv.getStorageContents()) {
			if (TradeItems.isPlainDiamond(stack)) {
				count += stack.getAmount();
			}
		}
		return count;
	}

	/** Turns up to {@code wanted} plain diamonds from the player's inventory into trade diamonds. */
	private void exchange(int wanted) {
		PlayerInventory inv = player.getInventory();
		int count = Math.min(wanted, countPlainDiamonds(inv));
		if (count <= 0) {
			player.sendMessage(Component.text("인벤토리에 다이아몬드가 없습니다.", NamedTextColor.RED));
			player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.PLAYERS, 0.8F, 1.0F);
			return;
		}
		int left = count;
		ItemStack[] contents = inv.getStorageContents();
		for (int slot = 0; slot < contents.length && left > 0; slot++) {
			ItemStack stack = contents[slot];
			if (!TradeItems.isPlainDiamond(stack)) {
				continue;
			}
			int take = Math.min(left, stack.getAmount());
			stack.setAmount(stack.getAmount() - take);
			inv.setItem(slot, stack.getAmount() > 0 ? stack : null);
			left -= take;
		}
		int stackSize = Material.DIAMOND.getMaxStackSize();
		for (int given = 0; given < count; given += stackSize) {
			ItemStack diamonds = TradeItems.tradeDiamond();
			diamonds.setAmount(Math.min(stackSize, count - given));
			inv.addItem(diamonds).values().forEach(rest -> player.getWorld().dropItem(player.getLocation(), rest));
		}
		if (count < wanted && wanted != Integer.MAX_VALUE) {
			player.sendMessage(Component.text("다이아몬드가 " + count + "개뿐이라 " + count + "개만 바꿨습니다.", NamedTextColor.YELLOW));
		} else {
			player.sendMessage(Component.text("다이아몬드 " + count + "개를 거래 전용 다이아로 바꿨습니다.", NamedTextColor.AQUA));
		}
		player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.8F, 1.2F);
	}

	private void onMenuClick(int slot, ClickType click) {
		boolean use = click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT
				|| bedrock(player) && (click == ClickType.LEFT || click == ClickType.SHIFT_LEFT);
		if (slot == EXCHANGE_SLOT && use) {
			exchange(amount());
			return;
		}
		if (slot == ALL_SLOT && use) {
			exchange(Integer.MAX_VALUE);
			return;
		}
		for (int i = 0; i < AMOUNT_SLOTS.length; i++) {
			if (slot == AMOUNT_SLOTS[i] && (click.isLeftClick() || click.isRightClick())) {
				changeAmount(AMOUNT_DELTAS[i]);
				return;
			}
		}
	}

	static final class Events implements Listener {
		@EventHandler(priority = EventPriority.HIGHEST)
		public void onClick(InventoryClickEvent event) {
			if (!(event.getView().getTopInventory().getHolder(false) instanceof TradeMenu menu)) {
				return;
			}
			if (event.getClickedInventory() == event.getView().getTopInventory()) {
				// Menu items stay where they are.
				event.setCancelled(true);
				menu.onMenuClick(event.getRawSlot(), event.getClick());
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

		@EventHandler
		public void onQuit(PlayerQuitEvent event) {
			AMOUNTS.remove(event.getPlayer().getUniqueId());
		}
	}
}
