package io.github.sharket1111.trade;

import java.util.function.Supplier;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Confirming a purchase. The price in trade diamonds leaves the buyer's
 * inventory, the item goes to the buyer, and the seller is paid at once if
 * online or else into their mailbox.
 */
final class PurchaseMenu extends Menu {
	private static final int ITEM_SLOT = 4;
	private static final int PRICE_SLOT = 13;
	private static final int BUY_SLOT = 22;
	private final Listing listing;
	private final Supplier<Menu> back;

	PurchaseMenu(Player player, Listing listing, Supplier<Menu> back) {
		super(player, "거래 - 구매 확인");
		this.listing = listing;
		this.back = back;
	}

	@Override
	void draw() {
		int have = count(TradeItems::isTradeDiamond);
		inventory.setItem(ITEM_SLOT, listing.item.clone());
		inventory.setItem(PRICE_SLOT, TradeItems.glint(TradeItems.button(Material.DIAMOND, listing.price,
				"가격: 거래 전용 다이아 " + listing.price + "개", NamedTextColor.AQUA,
				"판매자: " + Market.name(listing.seller), "내 거래 전용 다이아: " + have + "개")));
		inventory.setItem(BACK_SLOT, TradeItems.back());
		boolean enough = have >= listing.price;
		inventory.setItem(BUY_SLOT, enough
				? TradeItems.button(Material.LIME_CONCRETE, "구매하기", NamedTextColor.GREEN,
						"거래 전용 다이아 " + listing.price + "개를 내고 삽니다.")
				: TradeItems.button(Material.GRAY_CONCRETE, "거래 전용 다이아가 부족합니다", NamedTextColor.RED,
						(listing.price - have) + "개가 더 필요합니다."));
	}

	@Override
	void click(int slot, ClickType click) {
		if (slot == BACK_SLOT) {
			clickSound();
			back.get().open();
		} else if (slot == BUY_SLOT) {
			buy();
		}
	}

	private void buy() {
		if (Market.get(listing.id) == null) {
			tell("이미 팔렸거나 내려간 물품입니다.", NamedTextColor.RED);
			failSound();
			back.get().open();
			return;
		}
		if (listing.seller.equals(player.getUniqueId())) {
			tell("자신의 물품은 살 수 없습니다.", NamedTextColor.RED);
			failSound();
			return;
		}
		int price = listing.price;
		if (count(TradeItems::isTradeDiamond) < price) {
			tell("거래 전용 다이아가 부족합니다.", NamedTextColor.RED);
			failSound();
			redraw();
			return;
		}
		take(TradeItems::isTradeDiamond, price);
		Market.remove(listing);
		History.record(listing, player.getUniqueId(), player.getName());
		give(listing.item.clone());
		Component what = describe();
		Market.pay(listing.seller, price, what, player.getName());
		player.sendMessage(what.append(Component.text("을(를) 거래 전용 다이아 " + price + "개에 샀습니다.")).color(NamedTextColor.GREEN));
		successSound();
		back.get().open();
	}

	/** The item's name (in each reader's language) and amount, for messages. */
	private Component describe() {
		return listing.item.effectiveName().append(Component.text(" x" + listing.item.getAmount()));
	}
}
