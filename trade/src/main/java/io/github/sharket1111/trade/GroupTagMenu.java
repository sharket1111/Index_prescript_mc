package io.github.sharket1111.trade;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import net.kyori.adventure.text.format.NamedTextColor;

/** Choosing which of the player's groups' tags goes before their name, or none. */
final class GroupTagMenu extends Menu {
	private static final int NONE_SLOT = 22;
	private final Map<Integer, UUID> bySlot = new HashMap<>();

	GroupTagMenu(Player player) {
		super(player, "거래 - 내 이름 앞 접두사");
	}

	@Override
	void draw() {
		bySlot.clear();
		inventory.setItem(BACK_SLOT, TradeItems.back());
		UUID me = player.getUniqueId();
		Groups.Group shown = Groups.shownTag(me);
		int slot = 0;
		for (Groups.Group group : Groups.of(me)) {
			if (slot >= 9) {
				break;
			}
			boolean has = !group.tag.isEmpty();
			boolean on = group == shown;
			inventory.setItem(slot, on
					? TradeItems.glint(TradeItems.button(Material.LIME_BANNER, group.plainTag() + player.getName(), NamedTextColor.GREEN,
							"그룹: " + group.name, "표시 중"))
					: TradeItems.button(has ? Material.CYAN_BANNER : Material.GRAY_BANNER,
							has ? group.plainTag() + player.getName() : group.name + " (접두사 없음)",
							has ? NamedTextColor.AQUA : NamedTextColor.GRAY, "그룹: " + group.name,
							has ? "클릭: 이 접두사 달기" : "그룹에서 접두사를 정하면 고를 수 있습니다."));
			if (has) {
				bySlot.put(slot, group.id);
			}
			slot++;
		}
		inventory.setItem(NONE_SLOT, TradeItems.button(shown == null ? Material.LIME_DYE : Material.BARRIER, "접두사 없음",
				shown == null ? NamedTextColor.GREEN : NamedTextColor.WHITE, shown == null ? "표시 중" : "클릭: 이름만 표시"));
	}

	@Override
	void click(int slot, ClickType click) {
		if (slot == BACK_SLOT) {
			clickSound();
			new GroupMenu(player).open();
			return;
		}
		UUID me = player.getUniqueId();
		if (slot == NONE_SLOT) {
			Groups.showTag(me, null);
		} else if (bySlot.containsKey(slot)) {
			Groups.Group group = Groups.member(bySlot.get(slot), me);
			if (group == null) {
				return;
			}
			Groups.showTag(me, group);
		} else {
			return;
		}
		successSound();
		redraw();
	}
}
