package io.github.sharket1111.trade;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** The server shop: goods from shop.yml sold for money (right-click to buy). */
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
				lore.add(TradeItems.plain(product.price == 0 ? "가격: 무료" : "가격: " + Money.format(product.price), NamedTextColor.GOLD));
				lore.add(TradeItems.plain(bedrock() ? "두 번 터치: 구매" : "우클릭: 구매", NamedTextColor.GRAY));
				meta.lore(lore);
			});
			inventory.setItem(i, icon);
		}
		if (products.isEmpty()) {
			inventory.setItem(13, TradeItems.button(Material.BARRIER, "판매 중인 상품이 없습니다", NamedTextColor.GRAY));
		}
		inventory.setItem(BACK_SLOT, TradeItems.back());
		inventory.setItem(WALLET_SLOT, Money.wallet(player));
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
		long have = Money.balance(player.getUniqueId());
		if (!Money.take(player.getUniqueId(), product.price)) {
			tell(Money.subject() + " 부족합니다. (" + Money.format(product.price - have) + " 더 필요)", NamedTextColor.RED);
			failSound();
			return;
		}
		tell(product.price == 0 ? product.name() + "을(를) 받았습니다." : product.name() + "을(를) " + Money.format(product.price) + "에 샀습니다.",
				NamedTextColor.GREEN);
		successSound();
		product.deliver(player);
		if (!product.type.equals("ender_chest")) {
			redraw();
		}
	}
}
