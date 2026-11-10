package io.github.sharket1111.trade;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import net.kyori.adventure.text.format.NamedTextColor;

/** The first screen of /trade: pick a tab. */
final class MainMenu extends Menu {
	private static final int SELL_SLOT = 1;
	private static final int WALLET_SLOT = 8;
	private static final int MODE_SLOT = 18;
	private static final int WEB_SLOT = 0;
	private static final int MARKET_SLOT = 3;
	private static final int SELLERS_SLOT = 4;
	private static final int GOODS_SLOT = 5;
	private static final int ORDERS_SLOT = 7;
	private static final int AUCTION_SLOT = 6;
	private static final int DIRECT_SLOT = 10;
	private static final int PAY_SLOT = 12;
	private static final int SERVER_SHOP_SLOT = 14;
	private static final int MAIL_SLOT = 16;
	private static final int LAND_SLOT = 20;
	private static final int HISTORY_SLOT = 22;
	private static final int GROUP_SLOT = 24;
	private static final int STORAGE_SLOT = 13;
	private static final int ADMIN_SLOT = 26;

	MainMenu(Player player) {
		super(player, "거래");
	}

	@Override
	void draw() {
		Money.convertDiamonds(player);
		inventory.setItem(SELL_SLOT, TradeItems.button(Material.GOLD_BLOCK, "아이템 판매", NamedTextColor.GOLD,
				"아이템을 서버에 팔아 " + Money.object() + " 얻습니다.", "광물, 작물, 몹 전리품, 블록 등"));
		inventory.setItem(WALLET_SLOT, Money.wallet(player));
		String site = WebSite.address();
		boolean hasPassword = WebAccounts.hasPassword(player.getUniqueId());
		inventory.setItem(WEB_SLOT, TradeItems.button(Material.SPYGLASS, "웹 사이트", NamedTextColor.AQUA,
				site == null ? "관리자가 아직 웹 사이트를 켜지 않았습니다." : site,
				"내 골드, 우편함, 거래 내역, 땅을 웹에서 봅니다.",
				hasPassword ? "웹 비밀번호: 정해짐" : "웹 비밀번호: 아직 없음",
				"클릭: 로그인 링크 / 비밀번호 / 로그아웃"));
		boolean touch = bedrock();
		inventory.setItem(MODE_SLOT, TradeItems.glint(TradeItems.button(touch ? Material.LIGHT_BLUE_CONCRETE : Material.ORANGE_CONCRETE,
				"화면 모드: " + (touch ? "배드락 (터치)" : "자바"), NamedTextColor.AQUA,
				touch ? "숫자는 상자 안 키패드로 입력합니다." : "숫자는 채팅으로 입력합니다.",
				touch ? "모든 조작이 터치 한 번이고, 삭제·구매처럼" : "삭제·구매처럼 조심할 조작은 우클릭입니다.",
				touch ? "조심할 조작은 같은 버튼을 두 번 누릅니다." : "",
				UiMode.chosen(player) ? "" : "(접속한 기기에 맞춰 자동으로 정해졌습니다)",
				"클릭: " + (touch ? "자바" : "배드락 (터치)") + " 모드로 바꾸기")));
		inventory.setItem(MARKET_SLOT, TradeItems.button(Material.EMERALD, "시장", NamedTextColor.GREEN,
				"모든 판매 물품을 한눈에 보고 삽니다.", "가격순 정렬, 분류별 보기"));
		inventory.setItem(SELLERS_SLOT, TradeItems.button(Material.PLAYER_HEAD, "판매자별 구매", NamedTextColor.GREEN,
				"플레이어를 골라 그 플레이어의", "물품을 보고 삽니다."));
		inventory.setItem(GOODS_SLOT, TradeItems.button(Material.CHEST, "내 물품", NamedTextColor.GOLD,
				"다른 플레이어에게 팔 물품을", "가격을 정해 등록하고 관리합니다."));
		int auctions = Auctions.all().size();
		inventory.setItem(AUCTION_SLOT, TradeItems.button(Material.MACE, "경매장 (" + auctions + ")", NamedTextColor.LIGHT_PURPLE,
				"입찰해서 사고, 내 물건을 경매에 올려요.", "/trade 경매"));
		inventory.setItem(ORDERS_SLOT, TradeItems.button(Material.WRITABLE_BOOK, "삽니다 게시판", NamedTextColor.GOLD,
				"사고 싶은 물건을 올리거나,", "다른 사람의 주문에 팝니다."));
		inventory.setItem(DIRECT_SLOT, TradeItems.button(Material.SHIELD, "직접 거래", NamedTextColor.LIGHT_PURPLE,
				"접속한 플레이어와 1:1로 물건을 교환합니다.", "받은 거래 요청: " + DirectTrade.requestsFor(player).size() + "개"));
		inventory.setItem(PAY_SLOT, TradeItems.button(Material.GOLD_INGOT, "송금", NamedTextColor.YELLOW,
				Money.object() + " 다른 플레이어에게 보냅니다."));
		inventory.setItem(SERVER_SHOP_SLOT, TradeItems.button(Material.BELL, "서버 상점", NamedTextColor.LIGHT_PURPLE,
				"귀환권, 경험치 같은 물품을", Money.means() + " 삽니다."));
		int mail = Mailbox.of(player.getUniqueId()).size();
		inventory.setItem(MAIL_SLOT, TradeItems.button(mail > 0 ? Material.CHEST_MINECART : Material.MINECART, "우편함 (" + mail + ")",
				mail > 0 ? NamedTextColor.GOLD : NamedTextColor.GRAY, "받지 않은 물건을 받습니다."));
		inventory.setItem(LAND_SLOT, TradeItems.button(Material.GRASS_BLOCK, "내 땅", NamedTextColor.GREEN,
				"서 있는 청크가 내 땅이면", "플레이어별 권한(건축/파괴/상호작용)을 정합니다."));
		inventory.setItem(HISTORY_SLOT, TradeItems.button(Material.BOOK, "기록", NamedTextColor.YELLOW,
				"내가 산 물품과 판 물품의", "기록을 봅니다."));
		inventory.setItem(STORAGE_SLOT, TradeItems.button(Material.BARREL, "공용 보관함", NamedTextColor.GOLD,
				"서버 모두가 쓰는 54칸 보관함입니다.", "넣은 아이템마다 가져갈 수 있는 사람을 정합니다."));
		int groups = Groups.of(player.getUniqueId()).size();
		int invites = Groups.invitesFor(player.getUniqueId()).size();
		inventory.setItem(GROUP_SLOT, TradeItems.button(Material.CYAN_BANNER, "그룹 (" + groups + ")",
				NamedTextColor.AQUA, "그룹을 만들고, 그룹원·역할·접두사·할인을 관리합니다.",
				invites > 0 ? "받은 초대: " + invites + "개" : "역할별 권한, 그룹원 공격 차단, 그룹 할인"));
		if (player.hasPermission(AdminMenu.PERMISSION)) {
			inventory.setItem(ADMIN_SLOT, TradeItems.button(Material.COMMAND_BLOCK, "관리자", NamedTextColor.RED,
					"판매 물품/주문 관리, 기록 조회, 서버 상점 편집"));
		}
	}

	/** The web site's choices: a quick login link, setting the password, logging out everywhere. */
	private void webMenu() {
		boolean hasPassword = WebAccounts.hasPassword(player.getUniqueId());
		new ChoiceMenu(player, "거래 - 웹 사이트", java.util.List.of(
				TradeItems.button(Material.ENDER_EYE, "바로 로그인 링크 받기", NamedTextColor.AQUA, "채팅에 나만 쓰는 [열기] 링크가 뜹니다.",
						"비밀번호 없이 바로 내 정보가 열립니다."),
				TradeItems.button(Material.TRIPWIRE_HOOK, hasPassword ? "웹 비밀번호 바꾸기" : "웹 비밀번호 정하기", NamedTextColor.GOLD,
						"웹 사이트에서 닉네임과 이 비밀번호로 로그인합니다.", "다른 곳에서 쓰는 비밀번호와 다르게 정하세요."),
				TradeItems.button(Material.BARRIER, "모든 기기에서 로그아웃", NamedTextColor.RED, "웹에 로그인된 모든 기기와 링크를 끊습니다.")),
				() -> new MainMenu(player), choice -> {
					switch (choice) {
						case 0 -> {
							player.closeInventory();
							WebSite.link(player);
						}
						case 1 -> WebSite.askPassword(player, () -> new MainMenu(player).open());
						default -> {
							WebSite.command(player, "끊기");
							new MainMenu(player).open();
						}
					}
				}).open();
	}

	@Override
	void click(int slot, ClickType click) {
		Menu next = switch (slot) {
			case SELL_SLOT -> new SellMenu(player);
			case MARKET_SLOT -> new MarketMenu(player, 0, 0, Category.ALL, false);
			case SELLERS_SLOT -> new SellersMenu(player, 0);
			case GOODS_SLOT -> new GoodsMenu(player);
			case ORDERS_SLOT -> new BuyOrdersMenu(player, 0, false, false);
			case AUCTION_SLOT -> new AuctionMenu(player, 0, false);
			case DIRECT_SLOT -> new DirectTradeMenu(player);
			case SERVER_SHOP_SLOT -> new ServerShopMenu(player);
			case MAIL_SLOT -> new MailMenu(player, 0);
			case LAND_SLOT -> new LandMenu(player);
			case HISTORY_SLOT -> new HistoryMenu(player, 0, false);
			case GROUP_SLOT -> new GroupMenu(player);
			case STORAGE_SLOT -> new StorageMenu(player);
			case ADMIN_SLOT -> player.hasPermission(AdminMenu.PERMISSION) ? new AdminMenu(player) : null;
			default -> null;
		};
		if (slot == WEB_SLOT) {
			clickSound();
			webMenu();
			return;
		}
		if (slot == MODE_SLOT) {
			UiMode.set(player, !bedrock());
			player.sendMessage(net.kyori.adventure.text.Component.text("화면 모드를 " + (bedrock() ? "배드락 (터치)" : "자바")
					+ "(으)로 바꿨습니다.", NamedTextColor.AQUA));
			successSound();
			redraw();
			return;
		}
		if (slot == PAY_SLOT) {
			clickSound();
			PayMenu.open(player);
		} else if (next != null) {
			clickSound();
			next.open();
		}
	}
}
