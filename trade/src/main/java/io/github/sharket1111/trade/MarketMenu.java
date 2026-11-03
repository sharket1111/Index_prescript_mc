package io.github.sharket1111.trade;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * The whole market on one screen (9x6): every listing, sorted newest first or
 * by price, filtered by category, 45 to a page. Players buy from here; in
 * admin mode a right-click takes a listing down and mails it back to its seller.
 */
final class MarketMenu extends Menu {
	private static final int PAGE_SIZE = 45;
	private static final int BACK = 45;
	private static final int CATEGORY_SLOT = 46;
	private static final int SORT_SLOT = 47;
	private static final int PREV_SLOT = 48;
	private static final int PAGE_SLOT = 49;
	private static final int NEXT_SLOT = 50;
	private static final int WALLET_SLOT = 53;
	private static final String[] SORTS = {"최신순", "가격 낮은 순", "가격 높은 순"};

	private final int page;
	private final int sort;
	private final Category category;
	private final boolean admin;
	private final Map<Integer, Listing> bySlot = new HashMap<>();
	private int pages = 1;

	MarketMenu(Player player, int page, int sort, Category category, boolean admin) {
		super(player, admin ? "거래 - 판매 물품 관리" : "거래 - 시장", 6);
		this.page = page;
		this.sort = sort;
		this.category = category;
		this.admin = admin;
	}

	private List<Listing> listings() {
		List<Listing> list = new ArrayList<>();
		for (Listing listing : Market.all()) {
			if (category.matches(listing.item)) {
				list.add(listing);
			}
		}
		switch (sort) {
			case 1 -> list.sort(Comparator.comparingInt((Listing l) -> l.price));
			case 2 -> list.sort(Comparator.comparingInt((Listing l) -> l.price).reversed());
			default -> list.sort(Comparator.comparingLong((Listing l) -> l.createdAt).reversed());
		}
		return list;
	}

	@Override
	void draw() {
		bySlot.clear();
		List<Listing> list = listings();
		pages = Math.max(1, (list.size() + PAGE_SIZE - 1) / PAGE_SIZE);
		int first = Math.min(page, pages - 1) * PAGE_SIZE;
		for (int i = 0; i < PAGE_SIZE && first + i < list.size(); i++) {
			Listing listing = list.get(first + i);
			inventory.setItem(i, shown(listing));
			bySlot.put(i, listing);
		}
		if (list.isEmpty()) {
			inventory.setItem(22, TradeItems.button(Material.BARRIER, "판매 중인 물품이 없습니다", NamedTextColor.GRAY));
		}
		inventory.setItem(BACK, TradeItems.back());
		inventory.setItem(CATEGORY_SLOT, TradeItems.button(category.icon, "분류: " + category.label, NamedTextColor.YELLOW, "클릭: 다음 분류"));
		inventory.setItem(SORT_SLOT, TradeItems.button(Material.HOPPER, "정렬: " + SORTS[sort], NamedTextColor.YELLOW, "클릭: 다음 정렬"));
		inventory.setItem(PAGE_SLOT, TradeItems.button(Material.PAPER, (Math.min(page, pages - 1) + 1) + " / " + pages + " 페이지",
				NamedTextColor.WHITE, "물품 " + list.size() + "개"));
		if (page > 0) {
			inventory.setItem(PREV_SLOT, TradeItems.button(Material.SPECTRAL_ARROW, "이전 페이지", NamedTextColor.WHITE));
		}
		if (page < pages - 1) {
			inventory.setItem(NEXT_SLOT, TradeItems.button(Material.SPECTRAL_ARROW, "다음 페이지", NamedTextColor.WHITE));
		}
		if (!admin) {
			inventory.setItem(WALLET_SLOT, TradeItems.glint(TradeItems.button(Material.DIAMOND,
					"내 거래 전용 다이아: " + count(TradeItems::isTradeDiamond) + "개", NamedTextColor.AQUA)));
		}
	}

	private ItemStack shown(Listing listing) {
		ItemStack shown = listing.item.clone();
		boolean own = listing.seller.equals(player.getUniqueId());
		shown.editMeta(meta -> {
			List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
			lore.add(Component.empty());
			lore.add(TradeItems.plain("가격: 거래 전용 다이아 " + listing.price + "개", NamedTextColor.AQUA));
			lore.add(TradeItems.plain("판매자: " + Market.name(listing.seller) + (own ? " (나)" : ""), NamedTextColor.YELLOW));
			if (admin) {
				lore.add(TradeItems.plain(bedrock() ? "터치: 판매 취소(판매자 우편함으로)" : "우클릭: 판매 취소(판매자 우편함으로)", NamedTextColor.RED));
			} else if (!own) {
				lore.add(TradeItems.plain("클릭: 구매하기", NamedTextColor.GRAY));
			}
			meta.lore(lore);
		});
		return shown;
	}

	private MarketMenu with(int newPage, int newSort, Category newCategory) {
		return new MarketMenu(player, newPage, newSort, newCategory, admin);
	}

	@Override
	void click(int slot, ClickType click) {
		if (slot == BACK) {
			clickSound();
			(admin ? new AdminMenu(player) : new MainMenu(player)).open();
		} else if (slot == CATEGORY_SLOT) {
			clickSound();
			with(0, sort, category.next()).open();
		} else if (slot == SORT_SLOT) {
			clickSound();
			with(0, (sort + 1) % SORTS.length, category).open();
		} else if (slot == PREV_SLOT && page > 0) {
			with(page - 1, sort, category).open();
		} else if (slot == NEXT_SLOT && page < pages - 1) {
			with(page + 1, sort, category).open();
		} else if (bySlot.containsKey(slot)) {
			Listing listing = bySlot.get(slot);
			if (Market.get(listing.id) == null) {
				tell("이미 팔렸거나 내려간 물품입니다.", NamedTextColor.RED);
				redraw();
			} else if (admin) {
				if (confirmed(click)) {
					Market.remove(listing);
					if (Land.deedChunk(listing.item) == null) {
						Mailbox.deliver(listing.seller, listing.item.clone(), "관리자가 판매를 취소한 물품");
					}
					tell("판매를 취소하고 물품을 판매자 우편함으로 보냈습니다.", NamedTextColor.YELLOW);
					successSound();
					redraw();
				}
			} else if (!listing.seller.equals(player.getUniqueId())) {
				clickSound();
				new PurchaseMenu(player, listing, () -> with(page, sort, category)).open();
			}
		}
	}
}
