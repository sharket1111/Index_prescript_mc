package io.github.sharket1111.trade;

import java.util.function.Predicate;

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
 * put in; clicks on it are handed to {@link #click}. In the Bedrock (touch)
 * mode ({@link UiMode}) there is no right-click: where a button asks for one,
 * a second tap on the same button does it, and numbers are entered on a
 * keypad ({@link KeypadMenu}) instead of in chat.
 */
abstract class Menu implements InventoryHolder {
	static final int SIZE = 27;
	static final int BACK_SLOT = 18;
	/** A tap arms a careful action in the touch mode; a second tap on the same button within this many ticks does it. */
	private static final long ARM_TICKS = 60;

	protected final Player player;
	protected final Inventory inventory;
	/** The slot of the click being handled (for the touch mode's second tap). */
	private int clickedSlot = -1;
	private int armedSlot = -1;
	private long armedAt;

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
	 * Slots of the menu the player may put items into and take them out of
	 * freely (the sell box); none by default.
	 */
	boolean free(int slot) {
		return false;
	}

	/** Something in the free slots may have changed (called a tick later). */
	void freeChanged() {
	}

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

	/**
	 * For actions that need care: a right-click (Java mode), or in the touch
	 * mode a second tap on the same button (the first one only arms it).
	 */
	final boolean confirmed(ClickType click) {
		if (!bedrock()) {
			return click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT;
		}
		long now = player.getWorld().getFullTime();
		if (armedSlot == clickedSlot && clickedSlot >= 0 && now - armedAt <= ARM_TICKS) {
			armedSlot = -1;
			return true;
		}
		armedSlot = clickedSlot;
		armedAt = now;
		player.sendActionBar(Component.text("한 번 더 누르면 실행합니다.", NamedTextColor.YELLOW));
		clickSound();
		return false;
	}

	/** Whether the player uses the Bedrock (touch) mode. */
	final boolean bedrock() {
		return UiMode.touch(player);
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

	/** Step buttons for money: -1000 ... +1000. */
	static final int[] MONEY_STEPS = {-1000, -100, -10, 10, 100, 1000};

	/** The button that asks for an amount in chat. */
	static org.bukkit.inventory.ItemStack typeButton() {
		return TradeItems.button(org.bukkit.Material.NAME_TAG, "직접 입력", NamedTextColor.YELLOW, "채팅으로 금액을 입력합니다.",
				"예: 2500, 1.5만, 3천");
	}

	/**
	 * Asks for an amount in chat (the menu closes), then reopens the menu with
	 * it set; "취소" or a wrong number reopens it unchanged.
	 */
	final void askAmount(String question, long min, long max, java.util.function.LongConsumer set) {
		if (bedrock()) {
			new KeypadMenu(player, question, min, max, Money::format, value -> {
				set.accept(value);
				open();
			}, this::open, null).open();
			return;
		}
		ChatPrompt.ask(player, question + " (" + Money.format(min) + " ~ " + Money.format(max) + ")", text -> {
			Long value = parseAmount(text);
			if (value == null) {
				tell("숫자로 입력하세요: " + text, NamedTextColor.RED);
			} else {
				set.accept(Math.max(min, Math.min(max, value)));
			}
			open();
		}, this::open);
	}

	/** As {@link #askAmount}, for a plain number with {@code unit} after it (개, %, 청크...). */
	final void askNumber(String question, long min, long max, String unit, java.util.function.LongConsumer set) {
		if (bedrock()) {
			new KeypadMenu(player, question, min, max, value -> value + unit, value -> {
				set.accept(value);
				open();
			}, this::open, null).open();
			return;
		}
		ChatPrompt.ask(player, question + " (" + min + unit + " ~ " + max + unit + ")", text -> {
			Long value = parseAmount(text.replace(unit, ""));
			if (value == null) {
				tell("숫자로 입력하세요: " + text, NamedTextColor.RED);
			} else {
				set.accept(Math.max(min, Math.min(max, value)));
			}
			open();
		}, this::open);
	}

	/** "2500", "2,500", "1.5만", "3천", "2500G" as a whole amount, or null. */
	static Long parseAmount(String text) {
		String t = text.replace(",", "").replace(" ", "").replace(Money.name(), "").replace("G", "").replace("g", "");
		double unit = 1;
		if (t.endsWith("만")) {
			unit = 10_000;
			t = t.substring(0, t.length() - 1);
		} else if (t.endsWith("천")) {
			unit = 1_000;
			t = t.substring(0, t.length() - 1);
		} else if (t.endsWith("k") || t.endsWith("K")) {
			unit = 1_000;
			t = t.substring(0, t.length() - 1);
		}
		try {
			double value = Double.parseDouble(t) * unit;
			return Double.isFinite(value) && value >= 0 && value < 1.0E15 ? Math.round(value) : null;
		} catch (NumberFormatException e) {
			return null;
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

	private static void later(Menu menu) {
		Bukkit.getScheduler().runTask(TradePlugin.getPlugin(TradePlugin.class), menu::freeChanged);
	}

	static final class Events implements Listener {
		@EventHandler(priority = EventPriority.HIGHEST)
		public void onClick(InventoryClickEvent event) {
			if (!(event.getView().getTopInventory().getHolder(false) instanceof Menu menu)) {
				return;
			}
			if (event.getClickedInventory() == event.getView().getTopInventory()) {
				if (menu.free(event.getRawSlot())) {
					// The sell box: items move as in any chest.
					later(menu);
					return;
				}
				// Menu items stay where they are.
				event.setCancelled(true);
				menu.clickedSlot = event.getRawSlot();
				menu.click(event.getRawSlot(), event.getClick());
			} else if (event.getClickedInventory() != null) {
				menu.clickOwn(event);
				if (!event.isCancelled()) {
					later(menu);
				}
			}
		}

		/** Hands over money from sales made while the seller was away, and points out new mail. */
		@EventHandler
		public void onJoin(PlayerJoinEvent event) {
			Money.onJoin(event.getPlayer());
			Mailbox.notifyOnJoin(event.getPlayer());
		}

		@EventHandler
		public void onClose(InventoryCloseEvent event) {
			if (event.getInventory().getHolder(false) instanceof Menu menu && event.getPlayer() == menu.player) {
				menu.closed();
			}
			// Old trade diamonds taken out of a chest become money straight away.
			if (event.getPlayer() instanceof org.bukkit.entity.Player player) {
				Bukkit.getScheduler().runTask(TradePlugin.getPlugin(TradePlugin.class), () -> {
					if (player.isOnline()) {
						Money.convertDiamonds(player);
					}
				});
			}
		}

		/** ...and so do ones picked up from the ground. */
		@EventHandler(ignoreCancelled = true)
		public void onPickup(org.bukkit.event.entity.EntityPickupItemEvent event) {
			if (event.getEntity() instanceof org.bukkit.entity.Player player && TradeItems.isTradeDiamond(event.getItem().getItemStack())) {
				Bukkit.getScheduler().runTask(TradePlugin.getPlugin(TradePlugin.class), () -> {
					if (player.isOnline()) {
						Money.convertDiamonds(player);
					}
				});
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
			Menu menu = (Menu) event.getView().getTopInventory().getHolder(false);
			int size = event.getView().getTopInventory().getSize();
			if (event.getRawSlots().stream().anyMatch(slot -> slot < size && !menu.free(slot))) {
				event.setCancelled(true);
			} else {
				later(menu);
			}
		}
	}
}
