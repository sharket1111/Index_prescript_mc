package io.github.sharket1111.trade;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import net.kyori.adventure.text.format.NamedTextColor;

/** The group discount: how much members with the discount perk save on other members' goods. */
final class GroupSettingsMenu extends Menu {
	private static final int VALUE_SLOT = 13;
	private static final int[] STEP_SLOTS = {10, 11, 15, 16};
	private static final int[] STEPS = {-10, -5, 5, 10};

	private final java.util.UUID groupId;

	GroupSettingsMenu(Player player, java.util.UUID groupId) {
		super(player, "거래 - 그룹 할인율");
		this.groupId = groupId;
	}

	private Groups.Group group() {
		Groups.Group group = Groups.member(groupId, player.getUniqueId());
		return group != null && group.has(player.getUniqueId(), Groups.Perm.SETTINGS) ? group : null;
	}

	@Override
	void draw() {
		inventory.setItem(BACK_SLOT, TradeItems.back());
		Groups.Group group = group();
		if (group == null) {
			inventory.setItem(VALUE_SLOT, TradeItems.button(Material.BARRIER, "'그룹 설정' 권한이 없습니다", NamedTextColor.GRAY));
			return;
		}
		inventory.setItem(VALUE_SLOT, TradeItems.glint(TradeItems.button(Material.GOLD_INGOT, Math.max(1, group.discount),
				"그룹 할인율: " + group.discount + "%", NamedTextColor.GOLD,
				"'그룹 할인' 권한이 있는 역할의 그룹원이", "다른 그룹원의 물품을 살 때 이만큼 깎입니다.",
				"(판매자는 깎인 값을 받습니다, 0~" + Groups.MAX_DISCOUNT + "%)",
				"예: " + Money.format(1000) + "짜리 → " + Money.format(Groups.discounted(1000, group.discount)),
				"클릭: 채팅으로 직접 입력")));
		for (int i = 0; i < STEP_SLOTS.length; i++) {
			inventory.setItem(STEP_SLOTS[i], TradeItems.stepButton(STEPS[i], "지금: " + group.discount + "%"));
		}
	}

	@Override
	void click(int slot, ClickType click) {
		if (slot == BACK_SLOT) {
			clickSound();
			new GroupHomeMenu(player, groupId).open();
			return;
		}
		Groups.Group group = group();
		if (group == null) {
			return;
		}
		if (slot == VALUE_SLOT) {
			askNumber("그룹 할인율을 입력하세요.", 0, Groups.MAX_DISCOUNT, "%", value -> {
				Groups.Group now = group();
				if (now != null) {
					now.discount = (int) value;
					Groups.save();
				}
			});
			return;
		}
		for (int i = 0; i < STEP_SLOTS.length; i++) {
			if (slot == STEP_SLOTS[i]) {
				group.discount = Math.max(0, Math.min(Groups.MAX_DISCOUNT, group.discount + STEPS[i]));
				Groups.save();
				clickSound();
				redraw();
				return;
			}
		}
	}
}
