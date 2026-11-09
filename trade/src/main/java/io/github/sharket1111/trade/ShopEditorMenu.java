package io.github.sharket1111.trade;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Editing the server shop (admins): clicking a product opens its price and
 * removal; clicking an item in the inventory adds a copy of it as a product.
 */
final class ShopEditorMenu extends Menu {
	private static final int INFO_SLOT = 22;

	ShopEditorMenu(Player player) {
		super(player, "관리 - 서버 상점 편집");
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
				lore.add(TradeItems.plain("가격: " + Money.format(product.price), NamedTextColor.GOLD));
				lore.add(TradeItems.plain("클릭: 가격 수정 / 삭제", NamedTextColor.GRAY));
				meta.lore(lore);
			});
			inventory.setItem(i, icon);
		}
		inventory.setItem(BACK_SLOT, TradeItems.back());
		inventory.setItem(INFO_SLOT, TradeItems.button(Material.BOOK, "상품 추가 (" + products.size() + "/" + ShopConfig.MAX_PRODUCTS + ")",
				NamedTextColor.GOLD, "아래 내 인벤토리의 아이템을 클릭하면", "그 아이템(개수 포함)을 상품으로 추가합니다.",
				"변경 내용은 shop.yml에 저장됩니다."));
	}

	@Override
	void click(int slot, ClickType click) {
		if (slot == BACK_SLOT) {
			clickSound();
			new AdminMenu(player).open();
			return;
		}
		List<ShopConfig.Product> products = ShopConfig.products();
		if (slot >= 0 && slot < products.size() && slot < ShopConfig.MAX_PRODUCTS) {
			clickSound();
			new ProductEditor(player, products.get(slot), null).open();
		}
	}

	@Override
	void clickOwn(InventoryClickEvent event) {
		event.setCancelled(true);
		ItemStack picked = event.getCurrentItem();
		if (picked == null || picked.isEmpty()) {
			return;
		}
		if (ShopConfig.products().size() >= ShopConfig.MAX_PRODUCTS) {
			tell("상품은 " + ShopConfig.MAX_PRODUCTS + "개까지 둘 수 있습니다.", NamedTextColor.RED);
			failSound();
			return;
		}
		clickSound();
		new ProductEditor(player, null, picked.clone()).open();
	}
}
