package io.github.sharket1111.trade;

import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import net.kyori.adventure.text.format.NamedTextColor;

/**
 * One role: its permissions, turned on and off by those with 'edit roles'
 * whose own role is higher; renaming; moving it up or down in rank (never
 * to or above the mover's own role, and never above the leader's); deleting
 * (its members get the member role). The leader's role always runs the
 * group; only its perks can change.
 */
final class GroupRoleMenu extends Menu {
	private static final int INFO_SLOT = 4;
	private static final int[] PERM_SLOTS = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
	private static final int BACK = 45;
	private static final int UP_SLOT = 47;
	private static final int RENAME_SLOT = 49;
	private static final int DOWN_SLOT = 51;
	private static final int DELETE_SLOT = 53;
	private final java.util.UUID groupId;
	private final String roleId;

	GroupRoleMenu(Player player, java.util.UUID groupId, String roleId) {
		super(player, "거래 - 역할", 6);
		this.groupId = groupId;
		this.roleId = roleId;
	}

	/** Whether the player may move this role {@code by} places (-1 up, 1 down). */
	private boolean movable(Groups.Group group, Groups.Role role, int by) {
		int to = group.rank(role) + by;
		if (role.id.equals(Groups.LEADER) || to <= 0 || to >= group.roles.size() || !editable(group, role)) {
			return false;
		}
		// Nobody lifts a role to their own rank or above (the leader outranks every role).
		return player.getUniqueId().equals(group.leader) || to > group.rankOf(player.getUniqueId());
	}

	static String badRoleName(Groups.Group group, String name, Groups.Role except) {
		if (name.isEmpty() || name.length() > 12) {
			return "역할 이름은 1~12글자로 지어 주세요.";
		}
		if (name.contains("&") || name.contains("§")) {
			return "역할 이름에는 색 코드를 쓸 수 없습니다.";
		}
		for (Groups.Role role : group.roles) {
			if (role != except && role.name.equalsIgnoreCase(name)) {
				return "이미 있는 역할 이름입니다.";
			}
		}
		return null;
	}

	/** Whether the player may change this role (only perks, for the leader's own role). */
	private boolean editable(Groups.Group group, Groups.Role role) {
		UUID me = player.getUniqueId();
		if (!group.has(me, Groups.Perm.EDIT_ROLES)) {
			return false;
		}
		return role.id.equals(Groups.LEADER) ? me.equals(group.leader) : group.outranks(me, role);
	}

	@Override
	void draw() {
		inventory.setItem(BACK, TradeItems.back());
		Groups.Group group = Groups.member(groupId, player.getUniqueId());
		Groups.Role role = group == null ? null : group.role(roleId);
		if (role == null) {
			inventory.setItem(13, TradeItems.button(Material.BARRIER, "없는 역할입니다", NamedTextColor.GRAY));
			return;
		}
		boolean edit = editable(group, role);
		boolean leaderRole = role.id.equals(Groups.LEADER);
		long members = group.memberRoles.values().stream().filter(role.id::equals).count();
		inventory.setItem(INFO_SLOT, TradeItems.button(GroupRolesMenu.icon(role), role.name, NamedTextColor.LIGHT_PURPLE,
				"순위 " + (group.rank(role) + 1) + " · 그룹원 " + members + "명",
				edit ? "아래 권한을 눌러 켜고 끕니다." : "보기만 할 수 있습니다. ('역할 편집' 권한과 더 높은 역할 필요)"));
		Groups.Perm[] all = Groups.Perm.values();
		for (int i = 0; i < all.length && i < PERM_SLOTS.length; i++) {
			Groups.Perm perm = all[i];
			boolean fixed = leaderRole && !perm.perk();
			boolean on = fixed || role.perms.contains(perm);
			inventory.setItem(PERM_SLOTS[i], TradeItems.button(on ? Material.LIME_WOOL : Material.RED_WOOL,
					perm.label + ": " + (on ? "켜짐" : "꺼짐"), on ? NamedTextColor.GREEN : NamedTextColor.RED, perm.help,
					fixed ? "그룹장은 항상 가집니다." : edit ? "클릭: 바꾸기" : ""));
		}
		if (!leaderRole) {
			boolean up = movable(group, role, -1);
			boolean down = movable(group, role, 1);
			inventory.setItem(UP_SLOT, TradeItems.button(up ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE,
					"순위 올리기", up ? NamedTextColor.GREEN : NamedTextColor.GRAY, "지금 순위: " + (group.rank(role) + 1),
					up ? "클릭: 한 칸 위로" : "더 올릴 수 없습니다. (내 역할보다 높일 수 없음)"));
			inventory.setItem(DOWN_SLOT, TradeItems.button(down ? Material.ORANGE_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE,
					"순위 내리기", down ? NamedTextColor.GOLD : NamedTextColor.GRAY, "지금 순위: " + (group.rank(role) + 1),
					down ? "클릭: 한 칸 아래로" : "더 내릴 수 없습니다."));
		}
		if (edit) {
			inventory.setItem(RENAME_SLOT, TradeItems.button(Material.OAK_SIGN, "역할 이름 바꾸기", NamedTextColor.GOLD,
					"지금: " + role.name, "클릭: 채팅으로 입력"));
			if (!leaderRole && !role.id.equals(Groups.MEMBER)) {
				inventory.setItem(DELETE_SLOT, TradeItems.button(Material.LAVA_BUCKET, "역할 지우기", NamedTextColor.RED,
						"이 역할의 그룹원은 '" + group.roles.getLast().name + "'이(가) 됩니다.", bedrock() ? "두 번 누르면 지웁니다." : "우클릭: 지우기"));
			}
		}
	}

	@Override
	void click(int slot, ClickType click) {
		if (slot == BACK) {
			clickSound();
			new GroupRolesMenu(player, groupId).open();
			return;
		}
		Groups.Group group = Groups.member(groupId, player.getUniqueId());
		Groups.Role role = group == null ? null : group.role(roleId);
		if (role == null || !editable(group, role)) {
			return;
		}
		Groups.Perm[] all = Groups.Perm.values();
		for (int i = 0; i < all.length && i < PERM_SLOTS.length; i++) {
			if (slot == PERM_SLOTS[i]) {
				Groups.Perm perm = all[i];
				if (role.id.equals(Groups.LEADER) && !perm.perk()) {
					failSound();
					return;
				}
				if (!role.perms.remove(perm)) {
					role.perms.add(perm);
				}
				Groups.save();
				clickSound();
				redraw();
				return;
			}
		}
		if (slot == UP_SLOT || slot == DOWN_SLOT) {
			int by = slot == UP_SLOT ? -1 : 1;
			if (movable(group, role, by) && Groups.move(group, role, by)) {
				clickSound();
				redraw();
			} else {
				failSound();
			}
			return;
		}
		if (slot == RENAME_SLOT) {
			ChatPrompt.ask(player, "새 역할 이름을 입력하세요. (1~12글자, 지금: " + role.name + ")", name -> {
				Groups.Group current = Groups.member(groupId, player.getUniqueId());
				Groups.Role now = current == null ? null : current.role(roleId);
				if (now != null && editable(current, now)) {
					String why = badRoleName(current, name, now);
					if (why != null) {
						tell(why, NamedTextColor.RED);
						failSound();
					} else {
						now.name = name;
						Groups.save();
						tell("역할 이름을 '" + name + "'(으)로 바꿨습니다.", NamedTextColor.GREEN);
						successSound();
					}
				}
				new GroupRoleMenu(player, groupId, roleId).open();
			}, () -> new GroupRoleMenu(player, groupId, roleId).open());
		} else if (slot == DELETE_SLOT && !role.id.equals(Groups.LEADER) && !role.id.equals(Groups.MEMBER)) {
			if (!confirmed(click)) {
				tell("실수를 막기 위해 우클릭해야 합니다.", NamedTextColor.GRAY);
				return;
			}
			group.roles.remove(role);
			group.memberRoles.replaceAll((id, r) -> r.equals(role.id) ? Groups.MEMBER : r);
			Land.roleDeleted(group, role.id);
			Groups.save();
			tell("역할 '" + role.name + "'을(를) 지웠습니다.", NamedTextColor.YELLOW);
			successSound();
			new GroupRolesMenu(player, groupId).open();
		}
	}
}
