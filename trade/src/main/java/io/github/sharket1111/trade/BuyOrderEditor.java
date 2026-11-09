package io.github.sharket1111.trade;

import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.format.NamedTextColor;

/** Setting how many of an item to ask for and what to pay; posting it holds the money. */
final class BuyOrderEditor extends Menu {
	private static final int ITEM_SLOT = 4;
	private static final int[] AMOUNT_SLOTS = {1, 2, 3, 5, 6, 7};
	private static final int[] AMOUNT_STEPS = {-64, -10, -1, 1, 10, 64};
	private static final int PRICE_SLOT = 13;
	private static final int[] PRICE_SLOTS = {9, 10, 11, 15, 16, 17};
	private static final int[] PRICE_STEPS = MONEY_STEPS;
	private static final int INPUT_SLOT = 14;
	private static final int POST_SLOT = 22;
	private final ItemStack want;
	private int amount;
	private int price = 100;

	BuyOrderEditor(Player player, ItemStack picked) {
		super(player, "거래 - 삽니다 주문 올리기");
		this.amount = picked.getAmount();
		this.want = picked.clone();
		this.want.setAmount(1);
	}

	@Override
	void draw() {
		ItemStack shown = want.clone();
		shown.setAmount(Math.max(1, Math.min(shown.getMaxStackSize(), amount)));
		inventory.setItem(ITEM_SLOT, shown);
		for (int i = 0; i < AMOUNT_SLOTS.length; i++) {
			inventory.setItem(AMOUNT_SLOTS[i], TradeItems.stepButton(AMOUNT_STEPS[i], "살 개수: " + amount + "개"));
		}
		inventory.setItem(PRICE_SLOT, TradeItems.glint(TradeItems.button(Material.GOLD_INGOT, "주는 값: " + Money.format(price),
				NamedTextColor.GOLD, "살 개수: " + amount + "개 (위 버튼)", "주는 값: 양옆 버튼이나 '직접 입력'")));
		for (int i = 0; i < PRICE_SLOTS.length; i++) {
			inventory.setItem(PRICE_SLOTS[i], TradeItems.stepButton(PRICE_STEPS[i], "주는 값: " + Money.format(price)));
		}
		inventory.setItem(INPUT_SLOT, typeButton());
		inventory.setItem(BACK_SLOT, TradeItems.back());
		long have = Money.balance(player.getUniqueId());
		inventory.setItem(POST_SLOT, have >= price
				? TradeItems.button(Material.LIME_CONCRETE, "주문 올리기", NamedTextColor.GREEN, amount + "개를 " + Money.format(price) + "에 삽니다.",
						"올리면 " + Money.format(price) + "을(를) 맡깁니다.")
				: TradeItems.button(Material.GRAY_CONCRETE, Money.name() + "가 부족합니다", NamedTextColor.RED, "내 " + Money.name() + ": " + Money.format(have)));
	}

	@Override
	void click(int slot, ClickType click) {
		if (slot == BACK_SLOT) {
			clickSound();
			new BuyOrdersMenu(player, 0, false, false).open();
			return;
		}
		for (int i = 0; i < AMOUNT_SLOTS.length; i++) {
			if (slot == AMOUNT_SLOTS[i]) {
				amount = Math.max(1, Math.min(BuyOrders.MAX_AMOUNT, amount + AMOUNT_STEPS[i]));
				clickSound();
				redraw();
				return;
			}
		}
		for (int i = 0; i < PRICE_SLOTS.length; i++) {
			if (slot == PRICE_SLOTS[i]) {
				price = Math.max(1, Math.min(BuyOrders.MAX_PRICE, price + PRICE_STEPS[i]));
				clickSound();
				redraw();
				return;
			}
		}
		if (slot == INPUT_SLOT) {
			askAmount("주는 값을 입력하세요.", 1, BuyOrders.MAX_PRICE, value -> price = (int) value);
			return;
		}
		if (slot != POST_SLOT) {
			return;
		}
		if (BuyOrders.of(player.getUniqueId()).size() >= BuyOrders.MAX_PER_PLAYER) {
			tell("주문은 " + BuyOrders.MAX_PER_PLAYER + "개까지 올릴 수 있습니다.", NamedTextColor.RED);
			failSound();
			return;
		}
		if (!Money.take(player.getUniqueId(), price)) {
			tell(Money.name() + "가 부족합니다.", NamedTextColor.RED);
			failSound();
			return;
		}
		BuyOrders.add(new BuyOrders.Order(UUID.randomUUID(), player.getUniqueId(), player.getName(), want.clone(), amount, price,
				System.currentTimeMillis()));
		tell("삽니다 주문을 올렸습니다: " + amount + "개, " + Money.format(price), NamedTextColor.GREEN);
		successSound();
		new BuyOrdersMenu(player, 0, true, false).open();
	}

	@Override
	void clickOwn(InventoryClickEvent event) {
		event.setCancelled(true);
	}
}
