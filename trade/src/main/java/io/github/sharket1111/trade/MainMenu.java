package io.github.sharket1111.trade;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import net.kyori.adventure.text.format.NamedTextColor;

/** The first screen of /trade: pick a tab. */
final class MainMenu extends Menu {
	private static final int EXCHANGE_SLOT = 11;
	private static final int GOODS_SLOT = 15;

	MainMenu(Player player) {
		super(player, "거래");
	}

	@Override
	void draw() {
		inventory.setItem(EXCHANGE_SLOT, TradeItems.button(Material.DIAMOND, "다이아 교환", NamedTextColor.AQUA,
				"일반 다이아몬드와 거래 전용 다이아를", "서로 바꿉니다."));
		inventory.setItem(GOODS_SLOT, TradeItems.button(Material.CHEST, "물품", NamedTextColor.GOLD,
				"다른 플레이어에게 팔 물품을", "가격을 정해 등록하고 관리합니다."));
	}

	@Override
	void click(int slot, ClickType click) {
		if (slot == EXCHANGE_SLOT) {
			clickSound();
			new ExchangeMenu(player).open();
		} else if (slot == GOODS_SLOT) {
			clickSound();
			new GoodsMenu(player).open();
		}
	}
}
