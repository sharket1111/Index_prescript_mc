package io.github.sharket1111.trade;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.format.NamedTextColor;

/**
 * One player's view of a direct trade (9x6): their own offer on the left
 * (4x4), the other's on the right, and below the cancel button, their
 * confirmation and the other's. Clicking an item in the inventory below puts
 * it up; clicking one of their offered items takes it back.
 */
final class TradeView extends Menu {
	private static final int CANCEL_SLOT = 45;
	private static final int CONFIRM_SLOT = 48;
	private static final int OTHER_SLOT = 50;
	private static final int INFO_SLOT = 53;
	private final DirectTrade.Session session;

	TradeView(Player player, DirectTrade.Session session) {
		super(player, "거래 - " + session.other(player).getName() + "님과 직접 거래", 6);
		this.session = session;
	}

	private static int mySlot(int i) {
		return (i / 4) * 9 + i % 4;
	}

	private static int otherSlot(int i) {
		return (i / 4) * 9 + 5 + i % 4;
	}

	@Override
	void draw() {
		ItemStack[] mine = session.offerOf(player);
		ItemStack[] theirs = session.offerOfOther(player);
		for (int i = 0; i < DirectTrade.OFFER_SLOTS; i++) {
			inventory.setItem(mySlot(i), mine[i]);
			inventory.setItem(otherSlot(i), theirs[i]);
		}
		ItemStack divider = TradeItems.button(Material.BLACK_STAINED_GLASS_PANE, " ", NamedTextColor.DARK_GRAY);
		for (int row = 0; row < 5; row++) {
			inventory.setItem(row * 9 + 4, divider);
		}
		ItemStack floor = TradeItems.button(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.DARK_GRAY);
		for (int slot = 36; slot < 45; slot++) {
			if (slot != 40) {
				inventory.setItem(slot, floor);
			}
		}
		Player other = session.other(player);
		inventory.setItem(CANCEL_SLOT, TradeItems.button(Material.BARRIER, "거래 취소", NamedTextColor.RED, "올린 물건은 모두 돌려받습니다."));
		inventory.setItem(CONFIRM_SLOT, session.confirmed(player)
				? TradeItems.button(Material.LIME_CONCRETE, "확인함 (클릭: 확인 취소)", NamedTextColor.GREEN, "상대도 확인하면 교환됩니다.")
				: TradeItems.button(Material.RED_CONCRETE, "확인하기", NamedTextColor.YELLOW, "양쪽이 모두 확인하면 교환됩니다.",
						"어느 쪽이든 물건을 바꾸면 확인이 풀립니다."));
		inventory.setItem(OTHER_SLOT, session.confirmed(other)
				? TradeItems.button(Material.LIME_DYE, other.getName() + ": 확인함", NamedTextColor.GREEN)
				: TradeItems.button(Material.GRAY_DYE, other.getName() + ": 아직 확인 안 함", NamedTextColor.GRAY));
		inventory.setItem(INFO_SLOT, TradeItems.button(Material.BOOK, "사용법", NamedTextColor.GOLD,
				"왼쪽 4x4: 내가 줄 물건 / 오른쪽 4x4: 받을 물건",
				"아래 내 인벤토리의 아이템을 클릭하면 올리고,", "내가 올린 아이템을 클릭하면 되돌립니다."));
	}

	@Override
	void click(int slot, ClickType click) {
		if (session.done) {
			return;
		}
		if (slot == CANCEL_SLOT) {
			session.cancel(player);
			player.closeInventory();
			return;
		}
		if (slot == CONFIRM_SLOT) {
			clickSound();
			session.toggleConfirm(player);
			return;
		}
		ItemStack[] mine = session.offerOf(player);
		for (int i = 0; i < DirectTrade.OFFER_SLOTS; i++) {
			if (mySlot(i) == slot && mine[i] != null) {
				ItemStack back = mine[i];
				mine[i] = null;
				give(back);
				clickSound();
				session.changed();
				return;
			}
		}
	}

	/** Puts an item from the player's inventory up for the trade. */
	@Override
	void clickOwn(InventoryClickEvent event) {
		event.setCancelled(true);
		if (session.done) {
			return;
		}
		ItemStack picked = event.getCurrentItem();
		if (picked == null || picked.isEmpty()) {
			return;
		}
		if (refuseTool(picked)) {
			return;
		}
		ItemStack[] mine = session.offerOf(player);
		for (int i = 0; i < DirectTrade.OFFER_SLOTS; i++) {
			if (mine[i] == null) {
				mine[i] = picked.clone();
				event.getClickedInventory().setItem(event.getSlot(), null);
				clickSound();
				session.changed();
				return;
			}
		}
		tell("더 올릴 자리가 없습니다.", NamedTextColor.RED);
		failSound();
	}

	/** Closing the window cancels the trade. */
	@Override
	void closed() {
		if (!session.done) {
			session.cancel(player);
		}
	}
}
