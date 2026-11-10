package io.github.sharket1111.trade;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * The auction house: running auctions (ending soonest first), 18 a page,
 * redrawn every second so the time left counts down. Clicking one opens it
 * to bid; clicking an item in the inventory below starts an auction of it.
 */
final class AuctionMenu extends Menu {
	private static final int PAGE_SIZE = 18;
	private static final int FILTER_SLOT = 20;
	private static final int PREV_SLOT = 21;
	private static final int INFO_SLOT = 22;
	private static final int NEXT_SLOT = 23;
	private final int page;
	private final boolean mineOnly;
	private final Map<Integer, Auctions.Auction> bySlot = new HashMap<>();
	private int pages = 1;
	private BukkitTask ticker;

	AuctionMenu(Player player, int page, boolean mineOnly) {
		super(player, mineOnly ? "거래 - 경매 (내 경매·입찰)" : "거래 - 경매장");
		this.page = page;
		this.mineOnly = mineOnly;
	}

	@Override
	void draw() {
		bySlot.clear();
		List<Auctions.Auction> list = new ArrayList<>(Auctions.all());
		if (mineOnly) {
			list.removeIf(a -> !a.seller.equals(player.getUniqueId()) && !player.getUniqueId().equals(a.bidder));
		}
		pages = Math.max(1, (list.size() + PAGE_SIZE - 1) / PAGE_SIZE);
		int first = Math.min(page, pages - 1) * PAGE_SIZE;
		for (int i = 0; i < PAGE_SIZE && first + i < list.size(); i++) {
			Auctions.Auction auction = list.get(first + i);
			inventory.setItem(i, display(auction, player, "클릭: " + (auction.seller.equals(player.getUniqueId()) ? "자세히 보기" : "입찰하기")));
			bySlot.put(i, auction);
		}
		if (list.isEmpty()) {
			inventory.setItem(13, TradeItems.button(Material.BARRIER, mineOnly ? "내 경매나 입찰이 없습니다" : "진행 중인 경매가 없습니다",
					NamedTextColor.GRAY));
		}
		inventory.setItem(BACK_SLOT, TradeItems.back());
		inventory.setItem(FILTER_SLOT, TradeItems.button(mineOnly ? Material.ENDER_EYE : Material.ENDER_PEARL,
				mineOnly ? "전체 경매 보기" : "내 경매·입찰만 보기", NamedTextColor.AQUA));
		inventory.setItem(INFO_SLOT, TradeItems.button(Material.MACE, "경매 올리기 (" + Auctions.of(player.getUniqueId()).size() + "/"
				+ Auctions.MAX_PER_PLAYER + ")", NamedTextColor.LIGHT_PURPLE, "아래 내 인벤토리에서 아이템을 클릭하면", "시작가와 기간을 정해 경매에 올립니다.",
				"입찰한 돈은 바로 빠지고, 더 높은 입찰이 오면 돌려받아요.", "마지막 1분에 입찰이 오면 1분 연장됩니다."));
		if (page > 0) {
			inventory.setItem(PREV_SLOT, TradeItems.button(Material.SPECTRAL_ARROW, "이전 페이지", NamedTextColor.WHITE));
		}
		if (page < pages - 1) {
			inventory.setItem(NEXT_SLOT, TradeItems.button(Material.SPECTRAL_ARROW, "다음 페이지", NamedTextColor.WHITE));
		}
		startTicker();
	}

	private void startTicker() {
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

	/** An auction's item with its price, bids, time left and {@code action} below its own description. */
	static ItemStack display(Auctions.Auction auction, Player viewer, String action) {
		ItemStack shown = auction.item.clone();
		shown.editMeta(meta -> {
			List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
			lore.add(Component.empty());
			lore.add(TradeItems.plain((auction.bidder == null ? "시작가: " : "현재가: ") + Money.format(auction.current()), NamedTextColor.GOLD));
			lore.add(TradeItems.plain(auction.bidder == null ? "아직 입찰 없음" : "입찰 " + auction.bids + "회 · 최고 " + auction.bidderName,
					NamedTextColor.YELLOW));
			lore.add(TradeItems.plain("남은 시간: " + Auctions.left(auction.endsAt), NamedTextColor.AQUA));
			lore.add(TradeItems.plain("판매자: " + auction.sellerName, NamedTextColor.GRAY));
			if (auction.seller.equals(viewer.getUniqueId())) {
				lore.add(TradeItems.plain("내 경매", NamedTextColor.GREEN));
			} else if (viewer.getUniqueId().equals(auction.bidder)) {
				lore.add(TradeItems.plain("내가 최고 입찰자입니다", NamedTextColor.GREEN));
			}
			if (action != null) {
				lore.add(TradeItems.plain(action, NamedTextColor.GRAY));
			}
			meta.lore(lore);
		});
		return shown;
	}

	@Override
	void click(int slot, ClickType click) {
		if (slot == BACK_SLOT) {
			clickSound();
			new MainMenu(player).open();
		} else if (slot == FILTER_SLOT) {
			clickSound();
			new AuctionMenu(player, 0, !mineOnly).open();
		} else if (slot == PREV_SLOT && page > 0) {
			new AuctionMenu(player, page - 1, mineOnly).open();
		} else if (slot == NEXT_SLOT && page < pages - 1) {
			new AuctionMenu(player, page + 1, mineOnly).open();
		} else if (bySlot.containsKey(slot)) {
			Auctions.Auction auction = bySlot.get(slot);
			if (Auctions.get(auction.id) == null) {
				tell("이미 끝난 경매입니다.", NamedTextColor.RED);
				redraw();
				return;
			}
			clickSound();
			new AuctionView(player, auction, mineOnly).open();
		}
	}

	/** Picking an item from the player's own inventory to auction it. */
	@Override
	void clickOwn(InventoryClickEvent event) {
		event.setCancelled(true);
		ItemStack picked = event.getCurrentItem();
		if (picked == null || picked.isEmpty()) {
			return;
		}
		String why = Auctions.refuse(player.getUniqueId(), picked);
		if (why != null) {
			tell(why, NamedTextColor.RED);
			failSound();
			return;
		}
		clickSound();
		new AuctionEditor(player, event.getSlot(), picked.clone()).open();
	}
}
