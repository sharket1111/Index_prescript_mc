package io.github.sharket1111.trade;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** One group member: their role, and (for those who outrank them) giving a role or kicking. */
final class GroupMemberMenu extends Menu {
	private static final int INFO_SLOT = 4;
	private static final int ROLE_SLOT = 11;
	private static final int KICK_SLOT = 15;
	private final UUID groupId;
	private final UUID target;

	GroupMemberMenu(Player player, UUID groupId, UUID target) {
		super(player, "거래 - 그룹원");
		this.groupId = groupId;
		this.target = target;
	}

	@Override
	void draw() {
		inventory.setItem(BACK_SLOT, TradeItems.back());
		Groups.Group group = Groups.member(groupId, player.getUniqueId());
		if (group == null || !group.memberRoles.containsKey(target)) {
			inventory.setItem(13, TradeItems.button(Material.BARRIER, "그룹원이 아닙니다", NamedTextColor.GRAY));
			return;
		}
		UUID me = player.getUniqueId();
		Groups.Role role = group.roleOf(target);
		inventory.setItem(INFO_SLOT, TradeItems.button(Material.PLAYER_HEAD, group.nameOf(target), NamedTextColor.YELLOW,
				"역할: " + role.name));
		boolean above = !target.equals(me) && group.outranks(me, target);
		boolean assign = above && group.has(me, Groups.Perm.ASSIGN_ROLES);
		boolean kick = above && group.has(me, Groups.Perm.KICK);
		inventory.setItem(ROLE_SLOT, TradeItems.button(assign ? Material.NAME_TAG : Material.GRAY_DYE, "역할 지정",
				assign ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.GRAY, "지금: " + role.name,
				assign ? "클릭: 내 역할보다 낮은 역할 중에서 고릅니다." : "'역할 지정' 권한과 더 높은 역할이 필요합니다."));
		inventory.setItem(KICK_SLOT, TradeItems.button(kick ? Material.IRON_DOOR : Material.GRAY_DYE, "추방",
				kick ? NamedTextColor.RED : NamedTextColor.GRAY,
				kick ? (bedrock() ? "누르면 내보냅니다." : "우클릭: 내보내기") : "'추방' 권한과 더 높은 역할이 필요합니다."));
	}

	@Override
	void click(int slot, ClickType click) {
		if (slot == BACK_SLOT) {
			clickSound();
			GroupMemberPicker.members(player, groupId).open();
			return;
		}
		Groups.Group group = Groups.member(groupId, player.getUniqueId());
		UUID me = player.getUniqueId();
		if (group == null || !group.memberRoles.containsKey(target) || target.equals(me) || !group.outranks(me, target)) {
			return;
		}
		if (slot == ROLE_SLOT && group.has(me, Groups.Perm.ASSIGN_ROLES)) {
			clickSound();
			new GroupRolePicker(player, groupId, "거래 - 줄 역할", () -> new GroupMemberMenu(player, groupId, target),
					role -> group.outranks(me, role), role -> {
						Groups.Group current = Groups.member(groupId, me);
						if (current == group && current.memberRoles.containsKey(target) && current.outranks(me, target)
								&& current.outranks(me, role) && current.has(me, Groups.Perm.ASSIGN_ROLES)) {
							current.memberRoles.put(target, role.id);
							Groups.save();
							tell(current.nameOf(target) + "님의 역할을 '" + role.name + "'(으)로 정했습니다.", NamedTextColor.GREEN);
							successSound();
							Player online = Bukkit.getPlayer(target);
							if (online != null) {
								online.sendMessage(Component.text("그룹 '" + current.name + "'에서 역할이 '" + role.name + "'(으)로 바뀌었습니다.",
										NamedTextColor.GOLD));
							}
						}
						new GroupMemberMenu(player, groupId, target).open();
					}).open();
		} else if (slot == KICK_SLOT && group.has(me, Groups.Perm.KICK)) {
			if (!confirmed(click)) {
				tell("실수를 막기 위해 우클릭해야 합니다.", NamedTextColor.GRAY);
				return;
			}
			String name = group.nameOf(target);
			Groups.removeMember(group, target);
			Groups.save();
			tell(name + "님을 그룹에서 내보냈습니다.", NamedTextColor.YELLOW);
			successSound();
			Player online = Bukkit.getPlayer(target);
			if (online != null) {
				online.sendMessage(Component.text("그룹 '" + group.name + "'에서 추방되었습니다.", NamedTextColor.RED));
			}
			GroupMemberPicker.members(player, groupId).open();
		}
	}
}
