package io.github.sharket1111.trade;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import net.kyori.adventure.text.format.NamedTextColor;

/** The group's roles in rank order, each with its permissions; adding a role. */
final class GroupRolesMenu extends Menu {
	private static final int ADD_SLOT = 22;
	private final Map<Integer, String> bySlot = new HashMap<>();
	private final UUID groupId;

	GroupRolesMenu(Player player, UUID groupId) {
		super(player, "거래 - 역할 관리");
		this.groupId = groupId;
	}

	static Material icon(Groups.Role role) {
		return switch (role.id) {
			case Groups.LEADER -> Material.GOLDEN_HELMET;
			case Groups.DEPUTY -> Material.IRON_HELMET;
			case Groups.MEMBER -> Material.LEATHER_HELMET;
			default -> Material.CHAINMAIL_HELMET;
		};
	}

	static List<String> permLines(Groups.Role role) {
		List<String> lines = new ArrayList<>();
		for (Groups.Perm perm : Groups.Perm.values()) {
			boolean on = role.id.equals(Groups.LEADER) && !perm.perk() || role.perms.contains(perm);
			lines.add((on ? "✔ " : "✘ ") + perm.label);
		}
		return lines;
	}

	@Override
	void draw() {
		bySlot.clear();
		inventory.setItem(BACK_SLOT, TradeItems.back());
		Groups.Group group = Groups.member(groupId, player.getUniqueId());
		if (group == null) {
			return;
		}
		int slot = 0;
		for (Groups.Role role : group.roles) {
			long members = group.memberRoles.values().stream().filter(role.id::equals).count();
			List<String> lore = new ArrayList<>();
			lore.add("순위 " + (group.rank(role) + 1) + " · 그룹원 " + members + "명");
			lore.addAll(permLines(role));
			lore.add("클릭: 자세히");
			inventory.setItem(slot, TradeItems.button(icon(role), role.name, NamedTextColor.LIGHT_PURPLE, lore.toArray(String[]::new)));
			bySlot.put(slot, role.id);
			slot++;
		}
		boolean edit = group.has(player.getUniqueId(), Groups.Perm.EDIT_ROLES);
		inventory.setItem(ADD_SLOT, TradeItems.button(edit ? Material.EMERALD : Material.GRAY_DYE, "역할 만들기",
				edit ? NamedTextColor.GREEN : NamedTextColor.GRAY, "새 역할은 맨 아래 역할 바로 위 순위가 됩니다. (순위는 바꿀 수 있음)",
				"역할은 " + Groups.MAX_ROLES + "개까지", edit ? "클릭: 이름을 채팅으로 정합니다." : "'역할 편집' 권한이 필요합니다."));
	}

	@Override
	void click(int slot, ClickType click) {
		if (slot == BACK_SLOT) {
			clickSound();
			new GroupHomeMenu(player, groupId).open();
			return;
		}
		Groups.Group group = Groups.member(groupId, player.getUniqueId());
		if (group == null) {
			return;
		}
		if (bySlot.containsKey(slot)) {
			clickSound();
			new GroupRoleMenu(player, groupId, bySlot.get(slot)).open();
		} else if (slot == ADD_SLOT) {
			add(group);
		}
	}

	private void add(Groups.Group group) {
		UUID me = player.getUniqueId();
		if (!group.has(me, Groups.Perm.EDIT_ROLES)) {
			tell("'역할 편집' 권한이 있는 역할만 할 수 있습니다.", NamedTextColor.RED);
			failSound();
			return;
		}
		if (group.roles.size() >= Groups.MAX_ROLES) {
			tell("역할은 " + Groups.MAX_ROLES + "개까지 만들 수 있습니다.", NamedTextColor.RED);
			failSound();
			return;
		}
		ChatPrompt.ask(player, "새 역할의 이름을 입력하세요. (1~12글자)", name -> {
			Groups.Group current = Groups.member(groupId, me);
			if (current != group || !current.has(me, Groups.Perm.EDIT_ROLES) || current.roles.size() >= Groups.MAX_ROLES) {
				new GroupRolesMenu(player, groupId).open();
				return;
			}
			String why = GroupRoleMenu.badRoleName(current, name, null);
			if (why != null) {
				tell(why, NamedTextColor.RED);
				failSound();
				new GroupRolesMenu(player, groupId).open();
				return;
			}
			Groups.Role role = new Groups.Role("r" + Long.toString(System.currentTimeMillis(), 36), name,
					java.util.EnumSet.of(Groups.Perm.NO_PVP, Groups.Perm.DISCOUNT));
			current.roles.add(current.roles.size() - 1, role);
			Groups.save();
			tell("역할 '" + name + "'을(를) 만들었습니다.", NamedTextColor.GREEN);
			successSound();
			new GroupRoleMenu(player, groupId, role.id).open();
		}, () -> new GroupRolesMenu(player, groupId).open());
	}
}
