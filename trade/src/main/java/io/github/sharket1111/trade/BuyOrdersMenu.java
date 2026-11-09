package io.github.sharket1111.trade;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * The buying board (9x6): requests to buy, 45 to a page. Clicking someone
 * else's request sells them the items; clicking one's own withdraws it (the
 * diamonds come back). Clicking an item in the inventory starts a request for
 * that item. In admin mode a right-click removes any request and refunds it.
 */
final class BuyOrdersMenu extends Menu {
	private static final int PAGE_SIZE = 45;
	private static final int BACK = 45;
	private static final int NEW_SLOT = 47;
	private static final int PREV_SLOT = 48;
	private static final int MINE_SLOT = 49;
	private static final int NEXT_SLOT = 50;
	private static final int WALLET_SLOT = 53;
	private final int page;
	private final boolean mineOnly;
	private final boolean admin;
	private final Map<Integer, BuyOrders.Order> bySlot = new HashMap<>();
	private int pages = 1;

	BuyOrdersMenu(Player player, int page, boolean mineOnly, boolean admin) {
		super(player, admin ? "거래 - 삽니다 주문 관리" : "거래 - 삽니다 게시판", 6);
		this.page = page;
		this.mineOnly = mineOnly;
		this.admin = admin;
	}

	@Override
	void draw() {
		bySlot.clear();
		List<BuyOrders.Order> list = mineOnly ? BuyOrders.of(player.getUniqueId()) : BuyOrders.all();
		pages = Math.max(1, (list.size() + PAGE_SIZE - 1) / PAGE_SIZE);
		int first = Math.min(page, pages - 1) * PAGE_SIZE;
		for (int i = 0; i < PAGE_SIZE && first + i < list.size(); i++) {
			BuyOrders.Order order = list.get(first + i);
			inventory.setItem(i, shown(order));
			bySlot.put(i, order);
		}
		if (list.isEmpty()) {
			inventory.setItem(22, TradeItems.button(Material.BARRIER, "올라온 주문이 없습니다", NamedTextColor.GRAY));
		}
		inventory.setItem(BACK, TradeItems.back());
		if (!admin) {
			inventory.setItem(NEW_SLOT, TradeItems.button(Material.WRITABLE_BOOK, "주문 올리기 (" + BuyOrders.of(player.getUniqueId()).size()
					+ "/" + BuyOrders.MAX_PER_PLAYER + ")", NamedTextColor.GOLD,
					"아래 내 인벤토리에서 사고 싶은 아이템을 클릭하면", "개수와 가격을 정해 주문을 올립니다.",
					"주문할 때 가격만큼 " + Money.name() + "를 맡깁니다."));
			inventory.setItem(MINE_SLOT, TradeItems.button(Material.NAME_TAG, mineOnly ? "보기: 내 주문만" : "보기: 전체 주문",
					NamedTextColor.YELLOW, "클릭: 전환"));
			inventory.setItem(WALLET_SLOT, Money.wallet(player));
		}
		if (page > 0) {
			inventory.setItem(PREV_SLOT, TradeItems.button(Material.SPECTRAL_ARROW, "이전 페이지", NamedTextColor.WHITE));
		}
		if (page < pages - 1) {
			inventory.setItem(NEXT_SLOT, TradeItems.button(Material.SPECTRAL_ARROW, "다음 페이지", NamedTextColor.WHITE));
		}
	}

	private ItemStack shown(BuyOrders.Order order) {
		ItemStack shown = order.want.clone();
		shown.setAmount(Math.max(1, Math.min(shown.getMaxStackSize(), order.amount)));
		boolean own = order.buyer.equals(player.getUniqueId());
		shown.editMeta(meta -> {
			List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
			lore.add(Component.empty());
			lore.add(TradeItems.plain("삽니다: " + order.amount + "개", NamedTextColor.GOLD));
			lore.add(TradeItems.plain("주는 값: " + Money.format(order.price), NamedTextColor.GOLD));
			lore.add(TradeItems.plain("주문한 사람: " + order.buyerName + (own ? " (나)" : ""), NamedTextColor.YELLOW));
			if (admin) {
				lore.add(TradeItems.plain(bedrock() ? "터치: 주문 삭제(환불)" : "우클릭: 주문 삭제(환불)", NamedTextColor.RED));
			} else {
				lore.add(TradeItems.plain(own ? "클릭: 주문 취소(돌려받기)" : "클릭: 팔기", NamedTextColor.GRAY));
			}
			meta.lore(lore);
		});
		return shown;
	}

	@Override
	void click(int slot, ClickType click) {
		if (slot == BACK) {
			clickSound();
			(admin ? new AdminMenu(player) : new MainMenu(player)).open();
		} else if (slot == MINE_SLOT && !admin) {
			new BuyOrdersMenu(player, 0, !mineOnly, false).open();
		} else if (slot == PREV_SLOT && page > 0) {
			new BuyOrdersMenu(player, page - 1, mineOnly, admin).open();
		} else if (slot == NEXT_SLOT && page < pages - 1) {
			new BuyOrdersMenu(player, page + 1, mineOnly, admin).open();
		} else if (bySlot.containsKey(slot)) {
			BuyOrders.Order order = bySlot.get(slot);
			if (!BuyOrders.exists(order)) {
				tell("이미 끝난 주문입니다.", NamedTextColor.RED);
				redraw();
			} else if (admin) {
				if (confirmed(click)) {
					BuyOrders.remove(order);
					Money.give(order.buyer, order.price, "관리자가 삭제한 삽니다 주문의 환불");
					tell("주문을 삭제하고 맡긴 " + Money.name() + "를 주문자에게 돌려보냈습니다.", NamedTextColor.YELLOW);
					successSound();
					redraw();
				}
			} else if (order.buyer.equals(player.getUniqueId())) {
				BuyOrders.remove(order);
				Money.add(player.getUniqueId(), order.price);
				tell("주문을 취소하고 " + Money.format(order.price) + "을(를) 돌려받았습니다.", NamedTextColor.YELLOW);
				successSound();
				redraw();
			} else {
				clickSound();
				new FillOrderMenu(player, order, () -> new BuyOrdersMenu(player, page, mineOnly, false)).open();
			}
		}
	}

	/** Picking the item to ask for from the inventory (it stays there). */
	@Override
	void clickOwn(InventoryClickEvent event) {
		event.setCancelled(true);
		if (admin) {
			return;
		}
		ItemStack picked = event.getCurrentItem();
		if (picked == null || picked.isEmpty()) {
			return;
		}
		if (refuseTool(picked)) {
			return;
		}
		if (BuyOrders.of(player.getUniqueId()).size() >= BuyOrders.MAX_PER_PLAYER) {
			tell("주문은 " + BuyOrders.MAX_PER_PLAYER + "개까지 올릴 수 있습니다.", NamedTextColor.RED);
			failSound();
			return;
		}
		clickSound();
		new BuyOrderEditor(player, picked.clone()).open();
	}
}
