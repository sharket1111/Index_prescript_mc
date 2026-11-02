package io.github.sharket1111.trade;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** The record tab: the player's purchases, or their sales, newest first, 18 to a page. */
final class HistoryMenu extends Menu {
	private static final int PAGE_SIZE = 18;
	private static final int PREV_SLOT = 21;
	private static final int TOGGLE_SLOT = 22;
	private static final int NEXT_SLOT = 23;
	private final int page;
	private final boolean sales;
	private int pages = 1;

	HistoryMenu(Player player, int page, boolean sales) {
		super(player, sales ? "거래 - 판매 기록" : "거래 - 구매 기록");
		this.page = page;
		this.sales = sales;
	}

	@Override
	void draw() {
		List<History.Entry> entries = History.of(player.getUniqueId(), sales);
		pages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
		int first = Math.min(page, pages - 1) * PAGE_SIZE;
		for (int i = 0; i < PAGE_SIZE && first + i < entries.size(); i++) {
			inventory.setItem(i, shown(entries.get(first + i)));
		}
		if (entries.isEmpty()) {
			inventory.setItem(13, TradeItems.button(Material.BARRIER, sales ? "판매 기록이 없습니다" : "구매 기록이 없습니다",
					NamedTextColor.GRAY));
		}
		inventory.setItem(BACK_SLOT, TradeItems.back());
		inventory.setItem(TOGGLE_SLOT, sales
				? TradeItems.button(Material.WRITABLE_BOOK, "구매 기록 보기", NamedTextColor.YELLOW, "지금은 판매 기록을 보고 있습니다.")
				: TradeItems.button(Material.WRITABLE_BOOK, "판매 기록 보기", NamedTextColor.YELLOW, "지금은 구매 기록을 보고 있습니다."));
		if (page > 0) {
			inventory.setItem(PREV_SLOT, TradeItems.button(Material.SPECTRAL_ARROW, "이전 페이지", NamedTextColor.WHITE));
		}
		if (page < pages - 1) {
			inventory.setItem(NEXT_SLOT, TradeItems.button(Material.SPECTRAL_ARROW, "다음 페이지", NamedTextColor.WHITE,
					(page + 1) + " / " + pages));
		}
	}

	private ItemStack shown(History.Entry entry) {
		ItemStack shown = entry.item().clone();
		String when = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(entry.time()));
		shown.editMeta(meta -> {
			List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
			lore.add(Component.empty());
			lore.add(TradeItems.plain(sales ? "구매자: " + entry.buyerName() : "판매자: " + entry.sellerName(), NamedTextColor.YELLOW));
			lore.add(TradeItems.plain("가격: 거래 전용 다이아 " + entry.price() + "개", NamedTextColor.AQUA));
			lore.add(TradeItems.plain("날짜: " + when, NamedTextColor.GRAY));
			meta.lore(lore);
		});
		return shown;
	}

	@Override
	void click(int slot, ClickType click) {
		if (slot == BACK_SLOT) {
			clickSound();
			new MainMenu(player).open();
		} else if (slot == TOGGLE_SLOT) {
			clickSound();
			new HistoryMenu(player, 0, !sales).open();
		} else if (slot == PREV_SLOT && page > 0) {
			clickSound();
			new HistoryMenu(player, page - 1, sales).open();
		} else if (slot == NEXT_SLOT && page < pages - 1) {
			clickSound();
			new HistoryMenu(player, page + 1, sales).open();
		}
	}
}
