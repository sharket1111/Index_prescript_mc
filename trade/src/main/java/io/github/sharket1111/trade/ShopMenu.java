package io.github.sharket1111.trade;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** One seller's goods; clicking one opens the purchase screen. */
final class ShopMenu extends Menu {
	private static final int WALLET_SLOT = 22;
	private final UUID seller;
	private final Map<Integer, Listing> bySlot = new HashMap<>();

	ShopMenu(Player player, UUID seller) {
		super(player, "거래 - " + Market.name(seller) + "의 물품");
		this.seller = seller;
	}

	@Override
	void draw() {
		bySlot.clear();
		List<Listing> goods = Market.of(seller);
		for (int i = 0; i < goods.size() && i < Market.MAX_LISTINGS_PER_PLAYER; i++) {
			Listing listing = goods.get(i);
			inventory.setItem(i, forSale(listing));
			bySlot.put(i, listing);
		}
		if (goods.isEmpty()) {
			inventory.setItem(13, TradeItems.button(Material.BARRIER, "남은 물품이 없습니다", NamedTextColor.GRAY));
		}
		inventory.setItem(BACK_SLOT, TradeItems.back());
		inventory.setItem(WALLET_SLOT, wallet());
	}

	private ItemStack wallet() {
		return TradeItems.glint(TradeItems.button(Material.DIAMOND, "내 거래 전용 다이아: " + count(TradeItems::isTradeDiamond) + "개",
				NamedTextColor.AQUA, "다이아 교환 탭에서 일반 다이아몬드를", "거래 전용 다이아로 바꿀 수 있습니다."));
	}

	private static ItemStack forSale(Listing listing) {
		ItemStack shown = listing.item.clone();
		shown.editMeta(meta -> {
			List<Component> lore = meta.hasLore() ? new java.util.ArrayList<>(meta.lore()) : new java.util.ArrayList<>();
			lore.add(Component.empty());
			lore.add(TradeItems.plain("가격: 거래 전용 다이아 " + listing.price + "개", NamedTextColor.AQUA));
			lore.add(TradeItems.plain("클릭: 구매하기", NamedTextColor.GRAY));
			meta.lore(lore);
		});
		return shown;
	}

	@Override
	void click(int slot, ClickType click) {
		if (slot == BACK_SLOT) {
			clickSound();
			new SellersMenu(player, 0).open();
			return;
		}
		Listing listing = bySlot.get(slot);
		if (listing == null) {
			return;
		}
		if (Market.get(listing.id) == null) {
			tell("이미 팔렸거나 내려간 물품입니다.", NamedTextColor.RED);
			failSound();
			redraw();
			return;
		}
		clickSound();
		new PurchaseMenu(player, listing, () -> new ShopMenu(player, seller)).open();
	}
}
