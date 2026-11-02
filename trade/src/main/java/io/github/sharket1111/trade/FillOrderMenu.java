package io.github.sharket1111.trade;

import java.util.function.Supplier;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.format.NamedTextColor;

/** Filling someone's buy request: the items go to them, their held diamonds to the seller. */
final class FillOrderMenu extends Menu {
	private static final int ITEM_SLOT = 4;
	private static final int PRICE_SLOT = 13;
	private static final int SELL_SLOT = 22;
	private final BuyOrders.Order order;
	private final Supplier<Menu> back;

	FillOrderMenu(Player player, BuyOrders.Order order, Supplier<Menu> back) {
		super(player, "거래 - 삽니다 주문에 팔기");
		this.order = order;
		this.back = back;
	}

	@Override
	void draw() {
		ItemStack shown = order.want.clone();
		shown.setAmount(Math.max(1, Math.min(shown.getMaxStackSize(), order.amount)));
		inventory.setItem(ITEM_SLOT, shown);
		int have = count(order::matches);
		inventory.setItem(PRICE_SLOT, TradeItems.glint(TradeItems.button(Material.DIAMOND, order.price,
				"받는 값: 거래 전용 다이아 " + order.price + "개", NamedTextColor.AQUA, "필요한 개수: " + order.amount + "개",
				"내가 가진 개수: " + have + "개", "주문한 사람: " + order.buyerName)));
		inventory.setItem(BACK_SLOT, TradeItems.back());
		inventory.setItem(SELL_SLOT, have >= order.amount
				? TradeItems.button(Material.LIME_CONCRETE, "팔기", NamedTextColor.GREEN, order.amount + "개를 넘기고 다이아 " + order.price + "개를 받습니다.")
				: TradeItems.button(Material.GRAY_CONCRETE, "아이템이 부족합니다", NamedTextColor.RED, (order.amount - have) + "개가 더 필요합니다."));
	}

	@Override
	void click(int slot, ClickType click) {
		if (slot == BACK_SLOT) {
			clickSound();
			back.get().open();
			return;
		}
		if (slot != SELL_SLOT) {
			return;
		}
		if (!BuyOrders.exists(order)) {
			tell("이미 끝난 주문입니다.", NamedTextColor.RED);
			failSound();
			back.get().open();
			return;
		}
		if (count(order::matches) < order.amount) {
			tell("아이템이 부족합니다.", NamedTextColor.RED);
			failSound();
			redraw();
			return;
		}
		BuyOrders.remove(order);
		take(order::matches, order.amount);
		int max = order.want.getMaxStackSize();
		for (int given = 0; given < order.amount; given += max) {
			ItemStack part = order.want.clone();
			part.setAmount(Math.min(max, order.amount - given));
			Mailbox.deliver(order.buyer, part, player.getName() + "님이 채운 삽니다 주문");
		}
		giveStacks(TradeItems.tradeDiamond(1), order.price);
		History.recordRaw(order.buyer, order.buyerName, player.getUniqueId(), player.getName(), shownItem(), order.price);
		tell("주문에 " + order.amount + "개를 팔고 거래 전용 다이아 " + order.price + "개를 받았습니다.", NamedTextColor.GREEN);
		successSound();
		back.get().open();
	}

	private ItemStack shownItem() {
		ItemStack item = order.want.clone();
		item.setAmount(Math.max(1, Math.min(item.getMaxStackSize(), order.amount)));
		return item;
	}
}
