package io.github.sharket1111.trade;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** Picks another player from heads, 18 to a page: online players, or everyone who has played here. */
final class PlayerPicker extends Menu {
	interface Choice {
		void pick(UUID picked, String name);
	}

	private static final int PAGE_SIZE = 18;
	private static final int PREV_SLOT = 21;
	private static final int NEXT_SLOT = 23;
	private final String title;
	private final boolean onlineOnly;
	private final boolean includeSelf;
	private final int page;
	private final Supplier<Menu> back;
	private final Choice choice;
	private final Map<Integer, OfflinePlayer> bySlot = new HashMap<>();
	private int pages = 1;

	PlayerPicker(Player player, String title, boolean onlineOnly, boolean includeSelf, int page, Supplier<Menu> back, Choice choice) {
		super(player, title);
		this.title = title;
		this.onlineOnly = onlineOnly;
		this.includeSelf = includeSelf;
		this.page = page;
		this.back = back;
		this.choice = choice;
	}

	private List<OfflinePlayer> candidates() {
		List<OfflinePlayer> list = new ArrayList<>();
		if (onlineOnly) {
			list.addAll(Bukkit.getOnlinePlayers());
		} else {
			for (OfflinePlayer known : Bukkit.getOfflinePlayers()) {
				if (known.getName() != null) {
					list.add(known);
				}
			}
			for (Player online : Bukkit.getOnlinePlayers()) {
				if (list.stream().noneMatch(p -> p.getUniqueId().equals(online.getUniqueId()))) {
					list.add(online);
				}
			}
		}
		if (!includeSelf) {
			list.removeIf(p -> p.getUniqueId().equals(player.getUniqueId()));
		}
		list.sort(Comparator.comparing((OfflinePlayer p) -> !p.isOnline())
				.thenComparing(p -> p.getName() == null ? "" : p.getName().toLowerCase(java.util.Locale.ROOT)));
		return list;
	}

	@Override
	void draw() {
		bySlot.clear();
		List<OfflinePlayer> list = candidates();
		pages = Math.max(1, (list.size() + PAGE_SIZE - 1) / PAGE_SIZE);
		int first = Math.min(page, pages - 1) * PAGE_SIZE;
		for (int i = 0; i < PAGE_SIZE && first + i < list.size(); i++) {
			OfflinePlayer target = list.get(first + i);
			ItemStack head = ItemStack.of(Material.PLAYER_HEAD);
			head.editMeta(SkullMeta.class, meta -> {
				meta.setOwningPlayer(target);
				meta.itemName(Component.text(target.getName() == null ? "?" : target.getName(), NamedTextColor.YELLOW));
				meta.lore(List.of(TradeItems.plain(target.isOnline() ? "접속 중" : "접속 안 함",
						target.isOnline() ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY)));
			});
			inventory.setItem(i, head);
			bySlot.put(i, target);
		}
		if (list.isEmpty()) {
			inventory.setItem(13, TradeItems.button(Material.BARRIER, onlineOnly ? "접속한 다른 플레이어가 없습니다" : "플레이어가 없습니다",
					NamedTextColor.GRAY));
		}
		inventory.setItem(BACK_SLOT, TradeItems.back());
		if (page > 0) {
			inventory.setItem(PREV_SLOT, TradeItems.button(Material.SPECTRAL_ARROW, "이전 페이지", NamedTextColor.WHITE));
		}
		if (page < pages - 1) {
			inventory.setItem(NEXT_SLOT, TradeItems.button(Material.SPECTRAL_ARROW, "다음 페이지", NamedTextColor.WHITE));
		}
	}

	@Override
	void click(int slot, ClickType click) {
		if (slot == BACK_SLOT) {
			clickSound();
			back.get().open();
		} else if (slot == PREV_SLOT && page > 0) {
			new PlayerPicker(player, title, onlineOnly, includeSelf, page - 1, back, choice).open();
		} else if (slot == NEXT_SLOT && page < pages - 1) {
			new PlayerPicker(player, title, onlineOnly, includeSelf, page + 1, back, choice).open();
		} else if (bySlot.containsKey(slot)) {
			clickSound();
			OfflinePlayer target = bySlot.get(slot);
			choice.pick(target.getUniqueId(), target.getName() == null ? "?" : target.getName());
		}
	}
}
