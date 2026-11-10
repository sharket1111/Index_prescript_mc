package io.github.sharket1111.trade;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.scheduler.BukkitTask;

import net.kyori.adventure.text.format.NamedTextColor;

/** One auction: bid on it, or (the seller, before any bid) take it back. Redrawn every second. */
final class AuctionView extends Menu {
	private static final int ITEM_SLOT = 4;
	private static final int AMOUNT_SLOT = 13;
	private static final int[] STEP_SLOTS = {9, 10, 11, 15, 16, 17};
	private static final int INPUT_SLOT = 14;
	private static final int CONFIRM_SLOT = 22;
	private static final int CANCEL_SLOT = 26;

	private final Auctions.Auction auction;
	private final boolean fromMine;
	private long amount;
	private BukkitTask ticker;

	AuctionView(Player player, Auctions.Auction auction, boolean fromMine) {
		super(player, "거래 - 경매 입찰");
		this.auction = auction;
		this.fromMine = fromMine;
		this.amount = auction.minimum();
	}

	private boolean mine() {
		return auction.seller.equals(player.getUniqueId());
	}

	@Override
	void draw() {
		if (Auctions.get(auction.id) == null) {
			inventory.setItem(ITEM_SLOT, auction.item.clone());
			inventory.setItem(AMOUNT_SLOT, TradeItems.button(Material.BARRIER, "끝난 경매입니다", NamedTextColor.GRAY));
			inventory.setItem(BACK_SLOT, TradeItems.back());
			return;
		}
		amount = Math.max(amount, auction.minimum());
		inventory.setItem(ITEM_SLOT, AuctionMenu.display(auction, player, null));
		inventory.setItem(BACK_SLOT, TradeItems.back());
		if (mine()) {
			inventory.setItem(AMOUNT_SLOT, TradeItems.button(Material.CLOCK, "남은 시간: " + Auctions.left(auction.endsAt), NamedTextColor.AQUA,
					auction.bidder == null ? "아직 입찰이 없습니다." : "현재 " + auction.bidderName + "님이 " + Money.format(auction.bid)));
			if (auction.bidder == null) {
				inventory.setItem(CANCEL_SLOT, TradeItems.button(Material.BARRIER, "경매 취소", NamedTextColor.RED,
						"입찰이 없을 때만 취소할 수 있어요.", "물품은 인벤토리(또는 우편함)로 돌아옵니다.", bedrock() ? "두 번 눌러 취소" : "우클릭으로 취소"));
			}
		} else if (player.getUniqueId().equals(auction.bidder)) {
			inventory.setItem(AMOUNT_SLOT, TradeItems.glint(TradeItems.button(Material.EMERALD, "내가 최고 입찰자입니다 (" + Money.format(auction.bid) + ")",
					NamedTextColor.GREEN, "남은 시간: " + Auctions.left(auction.endsAt), "누가 더 높게 입찰하면 돈을 돌려받아요.")));
		} else {
			inventory.setItem(AMOUNT_SLOT, TradeItems.glint(TradeItems.button(Material.GOLD_INGOT, "입찰 금액: " + Money.format(amount), NamedTextColor.GOLD,
					"클릭: 채팅으로 직접 입력", "최소 " + Money.format(auction.minimum()), "내 " + Money.name() + ": " + Money.format(Money.balance(player.getUniqueId())))));
			for (int i = 0; i < STEP_SLOTS.length; i++) {
				inventory.setItem(STEP_SLOTS[i], TradeItems.stepButton(MONEY_STEPS[i], "입찰 금액: " + Money.format(amount)));
			}
			inventory.setItem(INPUT_SLOT, typeButton());
			inventory.setItem(CONFIRM_SLOT, TradeItems.button(Material.LIME_CONCRETE, Money.format(amount) + " 입찰하기", NamedTextColor.GREEN,
					"돈은 지금 빠지고, 더 높은 입찰이 오면 돌려받아요.", "남은 시간: " + Auctions.left(auction.endsAt)));
		}
		if (ticker == null) {
			ticker = Bukkit.getScheduler().runTaskTimer(TradePlugin.getPlugin(TradePlugin.class), () -> {
				if (player.getOpenInventory().getTopInventory() == inventory) {
					redraw();
				}
			}, 20, 20);
		}
	}

	@Override
	void closed() {
		if (ticker != null) {
			ticker.cancel();
			ticker = null;
		}
	}

	@Override
	void click(int slot, ClickType click) {
		if (slot == BACK_SLOT) {
			clickSound();
			new AuctionMenu(player, 0, fromMine).open();
			return;
		}
		if (Auctions.get(auction.id) == null) {
			return;
		}
		if (mine()) {
			if (slot == CANCEL_SLOT && auction.bidder == null && confirmed(click)) {
				String why = Auctions.cancel(player.getUniqueId(), auction);
				if (why != null) {
					tell(why, NamedTextColor.RED);
					failSound();
				} else {
					tell("경매를 취소하고 물품을 돌려받았습니다.", NamedTextColor.YELLOW);
					successSound();
				}
				new AuctionMenu(player, 0, true).open();
			}
			return;
		}
		if (player.getUniqueId().equals(auction.bidder)) {
			return;
		}
		for (int i = 0; i < STEP_SLOTS.length; i++) {
			if (slot == STEP_SLOTS[i]) {
				amount = Math.max(auction.minimum(), Math.min(Auctions.MAX_PRICE, amount + MONEY_STEPS[i]));
				clickSound();
				redraw();
				return;
			}
		}
		if (slot == AMOUNT_SLOT || slot == INPUT_SLOT) {
			askAmount("입찰 금액을 입력하세요.", auction.minimum(), Auctions.MAX_PRICE, value -> amount = value);
		} else if (slot == CONFIRM_SLOT) {
			String why = Auctions.bid(player.getUniqueId(), player.getName(), auction, amount);
			if (why != null) {
				tell(why, NamedTextColor.RED);
				failSound();
			} else {
				tell(Money.format(amount) + " 입찰했습니다. 남은 시간 " + Auctions.left(auction.endsAt), NamedTextColor.GREEN);
				successSound();
			}
			redraw();
		}
	}
}
