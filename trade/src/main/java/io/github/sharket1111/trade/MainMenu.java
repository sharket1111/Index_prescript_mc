package io.github.sharket1111.trade;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import net.kyori.adventure.text.format.NamedTextColor;

/** The first screen of /trade: pick a tab. */
final class MainMenu extends Menu {
	private static final int EXCHANGE_SLOT = 11;
	private static final int SHOP_SLOT = 12;
	private static final int SERVER_SHOP_SLOT = 13;
	private static final int GOODS_SLOT = 14;
	private static final int HISTORY_SLOT = 15;

	MainMenu(Player player) {
		super(player, "거래");
	}

	@Override
	void draw() {
		inventory.setItem(EXCHANGE_SLOT, TradeItems.button(Material.DIAMOND, "다이아 교환", NamedTextColor.AQUA,
				"일반 다이아몬드와 거래 전용 다이아를", "서로 바꿉니다."));
		inventory.setItem(SHOP_SLOT, TradeItems.button(Material.EMERALD, "구매", NamedTextColor.GREEN,
				"다른 플레이어를 골라 그 플레이어의", "물품을 보고 거래 전용 다이아로 삽니다."));
		inventory.setItem(SERVER_SHOP_SLOT, TradeItems.button(Material.BELL, "서버 상점", NamedTextColor.LIGHT_PURPLE,
				"귀환권, 경험치 같은 물품을", "거래 전용 다이아로 삽니다."));
		inventory.setItem(GOODS_SLOT, TradeItems.button(Material.CHEST, "물품", NamedTextColor.GOLD,
				"다른 플레이어에게 팔 물품을", "가격을 정해 등록하고 관리합니다."));
		inventory.setItem(HISTORY_SLOT, TradeItems.button(Material.BOOK, "기록", NamedTextColor.YELLOW,
				"내가 산 물품과 판 물품의", "기록을 봅니다."));
	}

	@Override
	void click(int slot, ClickType click) {
		if (slot == EXCHANGE_SLOT) {
			clickSound();
			new ExchangeMenu(player).open();
		} else if (slot == SHOP_SLOT) {
			clickSound();
			new SellersMenu(player, 0).open();
		} else if (slot == SERVER_SHOP_SLOT) {
			clickSound();
			new ServerShopMenu(player).open();
		} else if (slot == GOODS_SLOT) {
			clickSound();
			new GoodsMenu(player).open();
		} else if (slot == HISTORY_SLOT) {
			clickSound();
			new HistoryMenu(player, 0, false).open();
		}
	}
}
