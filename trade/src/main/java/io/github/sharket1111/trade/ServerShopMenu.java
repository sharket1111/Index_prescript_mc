package io.github.sharket1111.trade;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** The server shop: goods from shop.yml sold for trade diamonds (right-click to buy). */
final class ServerShopMenu extends Menu {
	private static final int WALLET_SLOT = 22;

	ServerShopMenu(Player player) {
		super(player, "거래 - 서버 상점");
	}

	@Override
	void draw() {
		List<ShopConfig.Product> products = ShopConfig.products();
		for (int i = 0; i < products.size() && i < ShopConfig.MAX_PRODUCTS; i++) {
			ShopConfig.Product product = products.get(i);
			ItemStack icon = product.icon();
			icon.editMeta(meta -> {
				List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
				lore.add(Component.empty());
				lore.add(TradeItems.plain("가격: 거래 전용 다이아 " + product.price + "개", NamedTextColor.AQUA));
				lore.add(TradeItems.plain(bedrock() ? "터치: 구매" : "우클릭: 구매", NamedTextColor.GRAY));
				meta.lore(lore);
			});
			inventory.setItem(i, icon);
		}
		if (products.isEmpty()) {
			inventory.setItem(13, TradeItems.button(Material.BARRIER, "판매 중인 상품이 없습니다", NamedTextColor.GRAY));
		}
		inventory.setItem(BACK_SLOT, TradeItems.back());
		inventory.setItem(WALLET_SLOT, TradeItems.glint(TradeItems.button(Material.DIAMOND,
				"내 거래 전용 다이아: " + count(TradeItems::isTradeDiamond) + "개", NamedTextColor.AQUA)));
	}

	@Override
	void click(int slot, ClickType click) {
		if (slot == BACK_SLOT) {
			clickSound();
			new MainMenu(player).open();
			return;
		}
		List<ShopConfig.Product> products = ShopConfig.products();
		if (slot < 0 || slot >= products.size() || slot >= ShopConfig.MAX_PRODUCTS || !confirmed(click)) {
			return;
		}
		ShopConfig.Product product = products.get(slot);
		String why = product.whyNot(player);
		if (why != null) {
			tell(why, NamedTextColor.RED);
			failSound();
			return;
		}
		int have = count(TradeItems::isTradeDiamond);
		if (have < product.price) {
			tell("거래 전용 다이아가 부족합니다. (" + (product.price - have) + "개 더 필요)", NamedTextColor.RED);
			failSound();
			return;
		}
		take(TradeItems::isTradeDiamond, product.price);
		tell(product.name() + "을(를) 거래 전용 다이아 " + product.price + "개에 샀습니다.", NamedTextColor.GREEN);
		successSound();
		product.deliver(player);
		if (!product.type.equals("ender_chest")) {
			redraw();
		}
	}
}
