package io.github.sharket1111.trade;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import net.kyori.adventure.text.format.NamedTextColor;

/** The admin tab (permission playertrade.admin, ops by default). */
final class AdminMenu extends Menu {
	static final String PERMISSION = "playertrade.admin";
	private static final int LISTINGS_SLOT = 10;
	private static final int ORDERS_SLOT = 12;
	private static final int HISTORY_SLOT = 14;
	private static final int SHOP_SLOT = 16;
	private static final int PROTECT_SLOT = 22;

	AdminMenu(Player player) {
		super(player, "거래 - 관리자");
	}

	@Override
	void draw() {
		inventory.setItem(LISTINGS_SLOT, TradeItems.button(Material.CHEST, "판매 물품 관리", NamedTextColor.GOLD,
				"모든 판매 물품을 보고, 문제 있는 물품의", "판매를 취소합니다. (물품은 판매자 우편함으로)"));
		inventory.setItem(ORDERS_SLOT, TradeItems.button(Material.WRITABLE_BOOK, "삽니다 주문 관리", NamedTextColor.GOLD,
				"주문을 삭제합니다. (맡긴 돈은 주문자에게 환불)"));
		inventory.setItem(HISTORY_SLOT, TradeItems.button(Material.BOOK, "거래 기록 조회", NamedTextColor.GOLD,
				"플레이어를 골라 구매/판매 기록을 봅니다."));
		inventory.setItem(SHOP_SLOT, TradeItems.button(Material.BELL, "서버 상점 편집", NamedTextColor.GOLD,
				"상품 추가, 가격 수정, 삭제 (shop.yml에 저장)"));
		inventory.setItem(PROTECT_SLOT, TradeItems.button(Material.BEDROCK, "보호구역", NamedTextColor.GOLD,
				"서 있는 곳 주변 청크를 보호구역으로 지정/해제합니다."));
		inventory.setItem(BACK_SLOT, TradeItems.back());
	}

	@Override
	void click(int slot, ClickType click) {
		if (!player.hasPermission(PERMISSION)) {
			player.closeInventory();
			return;
		}
		switch (slot) {
			case BACK_SLOT -> new MainMenu(player).open();
			case LISTINGS_SLOT -> new MarketMenu(player, 0, 0, Category.ALL, true).open();
			case ORDERS_SLOT -> new BuyOrdersMenu(player, 0, false, true).open();
			case HISTORY_SLOT -> new PlayerPicker(player, "관리 - 기록을 볼 플레이어", false, true, 0, () -> new AdminMenu(player),
					(id, name) -> new HistoryMenu(player, id, name, 0, false).open()).open();
			case SHOP_SLOT -> new ShopEditorMenu(player).open();
			case PROTECT_SLOT -> new ProtectMenu(player, 0).open();
			default -> {
				return;
			}
		}
		clickSound();
	}
}
