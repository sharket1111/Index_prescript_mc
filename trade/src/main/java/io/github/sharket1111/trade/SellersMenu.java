package io.github.sharket1111.trade;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** The buy tab: other players who have goods for sale, 18 to a page. */
final class SellersMenu extends Menu {
	private static final int PAGE_SIZE = 18;
	private static final int PREV_SLOT = 21;
	private static final int NEXT_SLOT = 23;
	private final int page;
	private final Map<Integer, UUID> bySlot = new HashMap<>();
	private int pages = 1;

	SellersMenu(Player player, int page) {
		super(player, "거래 - 구매");
		this.page = page;
	}

	@Override
	void draw() {
		bySlot.clear();
		List<UUID> sellers = Market.sellers(player.getUniqueId());
		pages = Math.max(1, (sellers.size() + PAGE_SIZE - 1) / PAGE_SIZE);
		int first = Math.min(page, pages - 1) * PAGE_SIZE;
		for (int i = 0; i < PAGE_SIZE && first + i < sellers.size(); i++) {
			UUID seller = sellers.get(first + i);
			inventory.setItem(i, head(seller));
			bySlot.put(i, seller);
		}
		if (sellers.isEmpty()) {
			inventory.setItem(13, TradeItems.button(Material.BARRIER, "등록된 물품이 없습니다", NamedTextColor.GRAY,
					"다른 플레이어가 물품 탭에서 물품을 등록하면", "여기에 나타납니다."));
		}
		inventory.setItem(BACK_SLOT, TradeItems.back());
		if (page > 0) {
			inventory.setItem(PREV_SLOT, TradeItems.button(Material.SPECTRAL_ARROW, "이전 페이지", NamedTextColor.WHITE));
		}
		if (page < pages - 1) {
			inventory.setItem(NEXT_SLOT, TradeItems.button(Material.SPECTRAL_ARROW, "다음 페이지", NamedTextColor.WHITE));
		}
	}

	private static ItemStack head(UUID seller) {
		ItemStack head = ItemStack.of(Material.PLAYER_HEAD);
		int goods = Market.of(seller).size();
		boolean online = Bukkit.getPlayer(seller) != null;
		head.editMeta(SkullMeta.class, meta -> {
			meta.setOwningPlayer(Bukkit.getOfflinePlayer(seller));
			meta.itemName(Component.text(Market.name(seller), NamedTextColor.YELLOW));
			meta.lore(List.of(
					TradeItems.plain("등록 물품 " + goods + "개", NamedTextColor.GRAY),
					TradeItems.plain(online ? "접속 중" : "접속 안 함", online ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY),
					TradeItems.plain("클릭: 물품 보기", NamedTextColor.GRAY)));
		});
		return head;
	}

	@Override
	void click(int slot, ClickType click) {
		if (slot == BACK_SLOT) {
			clickSound();
			new MainMenu(player).open();
		} else if (slot == PREV_SLOT && page > 0) {
			clickSound();
			new SellersMenu(player, page - 1).open();
		} else if (slot == NEXT_SLOT && page < pages - 1) {
			clickSound();
			new SellersMenu(player, page + 1).open();
		} else if (bySlot.containsKey(slot)) {
			clickSound();
			new ShopMenu(player, bySlot.get(slot)).open();
		}
	}
}
