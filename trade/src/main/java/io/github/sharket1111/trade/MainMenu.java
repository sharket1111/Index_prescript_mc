package io.github.sharket1111.trade;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import net.kyori.adventure.text.format.NamedTextColor;

/** The first screen of /trade: pick a tab. */
final class MainMenu extends Menu {
	private static final int EXCHANGE_SLOT = 1;
	private static final int MARKET_SLOT = 3;
	private static final int SELLERS_SLOT = 4;
	private static final int GOODS_SLOT = 5;
	private static final int ORDERS_SLOT = 7;
	private static final int DIRECT_SLOT = 10;
	private static final int PAY_SLOT = 12;
	private static final int SERVER_SHOP_SLOT = 14;
	private static final int MAIL_SLOT = 16;
	private static final int LAND_SLOT = 20;
	private static final int HISTORY_SLOT = 22;
	private static final int ADMIN_SLOT = 26;

	MainMenu(Player player) {
		super(player, "거래");
	}

	@Override
	void draw() {
		inventory.setItem(EXCHANGE_SLOT, TradeItems.button(Material.DIAMOND, "다이아 교환", NamedTextColor.AQUA,
				"일반 다이아몬드와 거래 전용 다이아를", "서로 바꿉니다."));
		inventory.setItem(MARKET_SLOT, TradeItems.button(Material.EMERALD, "시장", NamedTextColor.GREEN,
				"모든 판매 물품을 한눈에 보고 삽니다.", "가격순 정렬, 분류별 보기"));
		inventory.setItem(SELLERS_SLOT, TradeItems.button(Material.PLAYER_HEAD, "판매자별 구매", NamedTextColor.GREEN,
				"플레이어를 골라 그 플레이어의", "물품을 보고 삽니다."));
		inventory.setItem(GOODS_SLOT, TradeItems.button(Material.CHEST, "내 물품", NamedTextColor.GOLD,
				"다른 플레이어에게 팔 물품을", "가격을 정해 등록하고 관리합니다."));
		inventory.setItem(ORDERS_SLOT, TradeItems.button(Material.WRITABLE_BOOK, "삽니다 게시판", NamedTextColor.GOLD,
				"사고 싶은 물건을 올리거나,", "다른 사람의 주문에 팝니다."));
		inventory.setItem(DIRECT_SLOT, TradeItems.button(Material.SHIELD, "직접 거래", NamedTextColor.LIGHT_PURPLE,
				"접속한 플레이어와 1:1로 물건을 교환합니다.", "받은 거래 요청: " + DirectTrade.requestsFor(player).size() + "개"));
		inventory.setItem(PAY_SLOT, TradeItems.button(Material.GOLD_INGOT, "송금", NamedTextColor.YELLOW,
				"거래 전용 다이아를 다른 플레이어에게 보냅니다."));
		inventory.setItem(SERVER_SHOP_SLOT, TradeItems.button(Material.BELL, "서버 상점", NamedTextColor.LIGHT_PURPLE,
				"귀환권, 경험치 같은 물품을", "거래 전용 다이아로 삽니다."));
		int mail = Mailbox.of(player.getUniqueId()).size();
		inventory.setItem(MAIL_SLOT, TradeItems.button(mail > 0 ? Material.CHEST_MINECART : Material.MINECART, "우편함 (" + mail + ")",
				mail > 0 ? NamedTextColor.GOLD : NamedTextColor.GRAY, "받지 않은 물건과 다이아를 받습니다."));
		inventory.setItem(LAND_SLOT, TradeItems.button(Material.GRASS_BLOCK, "내 땅", NamedTextColor.GREEN,
				"서 있는 청크가 내 땅이면", "플레이어별 권한(건축/파괴/상호작용)을 정합니다."));
		inventory.setItem(HISTORY_SLOT, TradeItems.button(Material.BOOK, "기록", NamedTextColor.YELLOW,
				"내가 산 물품과 판 물품의", "기록을 봅니다."));
		if (player.hasPermission(AdminMenu.PERMISSION)) {
			inventory.setItem(ADMIN_SLOT, TradeItems.button(Material.COMMAND_BLOCK, "관리자", NamedTextColor.RED,
					"판매 물품/주문 관리, 기록 조회, 서버 상점 편집"));
		}
	}

	@Override
	void click(int slot, ClickType click) {
		Menu next = switch (slot) {
			case EXCHANGE_SLOT -> new ExchangeMenu(player);
			case MARKET_SLOT -> new MarketMenu(player, 0, 0, Category.ALL, false);
			case SELLERS_SLOT -> new SellersMenu(player, 0);
			case GOODS_SLOT -> new GoodsMenu(player);
			case ORDERS_SLOT -> new BuyOrdersMenu(player, 0, false, false);
			case DIRECT_SLOT -> new DirectTradeMenu(player);
			case SERVER_SHOP_SLOT -> new ServerShopMenu(player);
			case MAIL_SLOT -> new MailMenu(player, 0);
			case LAND_SLOT -> new LandMenu(player);
			case HISTORY_SLOT -> new HistoryMenu(player, 0, false);
			case ADMIN_SLOT -> player.hasPermission(AdminMenu.PERMISSION) ? new AdminMenu(player) : null;
			default -> null;
		};
		if (slot == PAY_SLOT) {
			clickSound();
			PayMenu.open(player);
		} else if (next != null) {
			clickSound();
			next.open();
		}
	}
}
