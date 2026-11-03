package io.github.sharket1111.trade;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Settings for one item in the shared storage, for the one who put it in (or
 * an admin): who else may take it (everyone, players, a group, a group's role
 * and up) and taking it back out.
 */
final class StorageItemMenu extends Menu {
	private static final int ITEM_SLOT = 4;
	private static final int EVERYONE_SLOT = 10;
	private static final int PLAYER_SLOT = 11;
	private static final int GROUP_SLOT = 12;
	private static final int ROLE_SLOT = 13;
	private static final int TAKE_SLOT = 16;
	private static final int[] RULE_SLOTS = {20, 21, 22, 23, 24, 25};
	private final int slot;
	/** The item this menu was opened for, to notice if the slot now holds another. */
	private final SharedStorage.Entry entry;

	StorageItemMenu(Player player, int slot) {
		super(player, "공용 보관함 - 가져갈 수 있는 사람");
		this.slot = slot;
		this.entry = SharedStorage.get(slot);
	}

	private boolean valid() {
		return entry != null && SharedStorage.get(slot) == entry && SharedStorage.may(player, entry, true);
	}

	@Override
	void draw() {
		inventory.setItem(BACK_SLOT, TradeItems.back());
		if (!valid()) {
			inventory.setItem(13, TradeItems.button(Material.BARRIER, "이미 꺼내졌거나 바뀐 아이템입니다", NamedTextColor.GRAY));
			return;
		}
		inventory.setItem(ITEM_SLOT, entry.item.clone());
		boolean all = entry.everyone();
		inventory.setItem(EVERYONE_SLOT, TradeItems.button(all ? Material.LIME_WOOL : Material.RED_WOOL,
				"모든 사람: " + (all ? "가져갈 수 있음" : "안 됨"), all ? NamedTextColor.GREEN : NamedTextColor.RED, "클릭: 바꾸기"));
		boolean room = entry.rules.size() < SharedStorage.MAX_RULES;
		inventory.setItem(PLAYER_SLOT, TradeItems.button(Material.PLAYER_HEAD, "플레이어 추가", NamedTextColor.YELLOW,
				"고른 플레이어가 가져갈 수 있게 합니다.", room ? "클릭: 고르기" : "규칙이 가득 찼습니다."));
		inventory.setItem(GROUP_SLOT, TradeItems.button(Material.CYAN_BANNER, "그룹 추가", NamedTextColor.AQUA,
				"내 그룹의 그룹원 모두가 가져갈 수 있게 합니다.", room ? "클릭: 고르기" : "규칙이 가득 찼습니다."));
		inventory.setItem(ROLE_SLOT, TradeItems.button(Material.NAME_TAG, "역할 추가", NamedTextColor.LIGHT_PURPLE,
				"내 그룹에서 고른 역할 이상인 그룹원이", "가져갈 수 있게 합니다.", room ? "클릭: 고르기" : "규칙이 가득 찼습니다."));
		inventory.setItem(TAKE_SLOT, TradeItems.button(Material.HOPPER, "꺼내기", NamedTextColor.GOLD, "내 인벤토리로 가져옵니다."));
		int i = 0;
		for (String rule : entry.rules) {
			if (rule.equals(SharedStorage.EVERYONE) || i >= RULE_SLOTS.length) {
				continue;
			}
			inventory.setItem(RULE_SLOTS[i++], TradeItems.button(Material.PAPER, SharedStorage.describe(rule), NamedTextColor.WHITE,
					"클릭: 이 규칙 지우기"));
		}
		if (i == 0 && !all) {
			inventory.setItem(RULE_SLOTS[2], TradeItems.button(Material.GRAY_DYE, "맡긴 사람만 가져갈 수 있습니다", NamedTextColor.GRAY,
					"위 버튼으로 가져갈 수 있는 사람을 더합니다."));
		}
	}

	private List<String> ruleList() {
		return entry.rules.stream().filter(r -> !r.equals(SharedStorage.EVERYONE)).toList();
	}

	private void addRule(String rule) {
		if (valid() && !entry.rules.contains(rule) && entry.rules.size() < SharedStorage.MAX_RULES) {
			entry.rules.add(rule);
			SharedStorage.save();
			StorageMenu.redrawAll();
			successSound();
		}
		new StorageItemMenu(player, slot).open();
	}

	@Override
	void click(int clicked, ClickType click) {
		if (clicked == BACK_SLOT) {
			clickSound();
			new StorageMenu(player).open();
			return;
		}
		if (!valid()) {
			return;
		}
		boolean room = entry.rules.size() < SharedStorage.MAX_RULES;
		switch (clicked) {
			case EVERYONE_SLOT -> {
				if (!entry.rules.remove(SharedStorage.EVERYONE)) {
					entry.rules.add(SharedStorage.EVERYONE);
				}
				SharedStorage.save();
				StorageMenu.redrawAll();
				clickSound();
				redraw();
			}
			case PLAYER_SLOT -> {
				if (!room) {
					failSound();
					return;
				}
				clickSound();
				new PlayerPicker(player, "공용 보관함 - 가져갈 수 있는 플레이어", false, false, 0,
						() -> new StorageItemMenu(player, slot), (id, name) -> addRule("player:" + id)).open();
			}
			case GROUP_SLOT, ROLE_SLOT -> {
				if (!room) {
					failSound();
					return;
				}
				List<Groups.Group> groups = Groups.of(player.getUniqueId());
				if (groups.isEmpty()) {
					tell("속한 그룹이 없습니다.", NamedTextColor.RED);
					failSound();
					return;
				}
				clickSound();
				boolean role = clicked == ROLE_SLOT;
				List<ItemStack> icons = groups.stream().map(g -> TradeItems.button(Material.CYAN_BANNER, g.name, NamedTextColor.AQUA,
						role ? "클릭: 이 그룹의 역할 고르기" : "클릭: 이 그룹 전체 허용")).toList();
				new ChoiceMenu(player, role ? "공용 보관함 - 역할의 그룹" : "공용 보관함 - 그룹", icons,
						() -> new StorageItemMenu(player, slot), index -> {
							Groups.Group group = groups.get(index);
							if (!role) {
								addRule("group:" + group.id);
								return;
							}
							new GroupRolePicker(player, group.id, "공용 보관함 - 이 역할 이상", () -> new StorageItemMenu(player, slot),
									r -> true, r -> addRule("role:" + group.id + ":" + r.id)).open();
						}).open();
			}
			case TAKE_SLOT -> {
				if (StorageMenu.takeOut(player, slot)) {
					new StorageMenu(player).open();
				} else {
					failSound();
				}
			}
			default -> {
				List<String> rules = ruleList();
				for (int i = 0; i < RULE_SLOTS.length && i < rules.size(); i++) {
					if (clicked == RULE_SLOTS[i]) {
						entry.rules.remove(rules.get(i));
						SharedStorage.save();
						StorageMenu.redrawAll();
						clickSound();
						redraw();
						return;
					}
				}
			}
		}
	}
}
