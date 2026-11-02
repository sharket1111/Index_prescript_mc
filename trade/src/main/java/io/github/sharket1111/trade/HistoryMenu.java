package io.github.sharket1111.trade;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

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
	/** Whose records: the viewer's own, or another player's for an admin. */
	private final UUID subject;
	private final String subjectName;
	private int pages = 1;

	HistoryMenu(Player player, int page, boolean sales) {
		this(player, player.getUniqueId(), null, page, sales);
	}

	HistoryMenu(Player player, UUID subject, String subjectName, int page, boolean sales) {
		super(player, (subjectName == null ? "거래 - " : "관리 - " + subjectName + " ") + (sales ? "판매 기록" : "구매 기록"));
		this.page = page;
		this.sales = sales;
		this.subject = subject;
		this.subjectName = subjectName;
	}

	private HistoryMenu with(int newPage, boolean newSales) {
		return new HistoryMenu(player, subject, subjectName, newPage, newSales);
	}

	@Override
	void draw() {
		List<History.Entry> entries = History.of(subject, sales);
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
			(subjectName == null ? new MainMenu(player) : new AdminMenu(player)).open();
		} else if (slot == TOGGLE_SLOT) {
			clickSound();
			with(0, !sales).open();
		} else if (slot == PREV_SLOT && page > 0) {
			clickSound();
			with(page - 1, sales).open();
		} else if (slot == NEXT_SLOT && page < pages - 1) {
			clickSound();
			with(page + 1, sales).open();
		}
	}
}
