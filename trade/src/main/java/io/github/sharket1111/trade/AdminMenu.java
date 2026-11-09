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
	private static final int CURRENCY_SLOT = 4;
	static final int NAME_MAX = 10;
	static final int SYMBOL_MAX = 5;

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
		inventory.setItem(CURRENCY_SLOT, TradeItems.glint(TradeItems.button(Material.GOLD_INGOT, "화폐 이름: " + Money.name()
				+ " (단위 " + Money.symbol() + ")", NamedTextColor.GOLD, "클릭: 화폐 이름과 단위를 채팅으로 바꿉니다.",
				"예: 이름 '원', 단위 '원' → 1,250 원", "명령어: /trade 화폐 <이름> [단위]")));
		inventory.setItem(BACK_SLOT, TradeItems.back());
	}

	/** Asks for the currency's new name, then its symbol ("-" keeps it). */
	private void renameCurrency() {
		ChatPrompt.ask(player, "새 화폐 이름을 입력하세요. (지금: " + Money.name() + ", 1~" + NAME_MAX + "글자)", name -> {
			String problem = Money.badName(name, NAME_MAX);
			if (problem != null) {
				tell(problem, NamedTextColor.RED);
				failSound();
				open();
				return;
			}
			ChatPrompt.ask(player, "새 단위를 입력하세요. (지금: " + Money.symbol() + ", 1~" + SYMBOL_MAX + "글자, '-'는 그대로)", symbol -> {
				String keep = symbol.equals("-") ? null : symbol;
				String bad = keep == null ? null : Money.badName(keep, SYMBOL_MAX);
				if (bad != null) {
					tell(bad, NamedTextColor.RED);
					failSound();
					open();
					return;
				}
				Money.rename(name, keep);
				tell("화폐를 '" + Money.name() + "'(단위 " + Money.symbol() + ")(으)로 바꿨습니다. 예: " + Money.format(1250), NamedTextColor.GREEN);
				successSound();
				open();
			}, this::open);
		}, this::open);
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
			case CURRENCY_SLOT -> renameCurrency();
			default -> {
				return;
			}
		}
		clickSound();
	}
}
