package io.github.sharket1111.trade;

import java.util.function.Supplier;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Confirming a purchase. The price leaves the buyer's balance, the item goes
 * to the buyer, and the seller is paid (told at once if online, otherwise on
 * their next join).
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

	/** The price this buyer pays: less the group discount if they and the seller share a group. */
	private int price() {
		return Groups.discounted(listing.price, Groups.discount(player.getUniqueId(), listing.seller));
	}

	@Override
	void draw() {
		long have = Money.balance(player.getUniqueId());
		int price = price();
		int discount = Groups.discount(player.getUniqueId(), listing.seller);
		inventory.setItem(ITEM_SLOT, listing.item.clone());
		inventory.setItem(PRICE_SLOT, TradeItems.glint(TradeItems.button(Material.GOLD_INGOT,
				"가격: " + Money.format(price), NamedTextColor.GOLD,
				discount > 0 ? "그룹 할인 " + discount + "% (원래 " + Money.format(listing.price) + ")" : "판매자: " + Market.name(listing.seller),
				discount > 0 ? "판매자: " + Market.name(listing.seller) + " (같은 그룹)" : "",
				"내 " + Money.name() + ": " + Money.format(have))));
		inventory.setItem(BACK_SLOT, TradeItems.back());
		boolean enough = have >= price;
		inventory.setItem(BUY_SLOT, enough
				? TradeItems.button(Material.LIME_CONCRETE, "구매하기", NamedTextColor.GREEN,
						Money.format(price) + "을(를) 내고 삽니다.")
				: TradeItems.button(Material.GRAY_CONCRETE, Money.name() + "가 부족합니다", NamedTextColor.RED,
						Money.format(price - have) + "이(가) 더 필요합니다."));
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
		String chunk = Land.deedChunk(listing.item);
		if (chunk != null) {
			Land.Claim claim = Land.claimByKey(chunk);
			if (claim == null || !claim.owner.equals(listing.seller)) {
				// The seller no longer owns it: the listing is void.
				Market.remove(listing);
				tell("판매자가 더 이상 이 땅의 주인이 아니어서 판매가 취소되었습니다.", NamedTextColor.RED);
				failSound();
				back.get().open();
				return;
			}
		}
		int price = price();
		if (!Money.take(player.getUniqueId(), price)) {
			tell(Money.name() + "가 부족합니다.", NamedTextColor.RED);
			failSound();
			redraw();
			return;
		}
		Market.remove(listing);
		History.record(listing, player.getUniqueId(), player.getName(), price);
		if (chunk != null) {
			Land.transfer(chunk, player);
		} else {
			give(listing.item.clone());
		}
		Component what = describe();
		Market.pay(listing.seller, price, what, player.getName());
		player.sendMessage(what.append(Component.text("을(를) " + Money.format(price) + "에 샀습니다.")).color(NamedTextColor.GREEN));
		successSound();
		back.get().open();
	}

	/** The item's name (in each reader's language) and amount, for messages. */
	private Component describe() {
		return listing.item.effectiveName().append(Component.text(" x" + listing.item.getAmount()));
	}
}
