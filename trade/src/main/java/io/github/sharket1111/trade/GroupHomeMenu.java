package io.github.sharket1111.trade;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * One of the player's groups: its members, inviting, roles, name, tag,
 * discount, handing over the lead, and leaving or disbanding.
 */
final class GroupHomeMenu extends Menu {
	private static final int INFO_SLOT = 4;
	private static final int MEMBERS_SLOT = 10;
	private static final int INVITE_SLOT = 11;
	private static final int ROLES_SLOT = 12;
	private static final int SENT_SLOT = 13;
	private static final int RENAME_SLOT = 14;
	private static final int TAG_SLOT = 15;
	private static final int SETTINGS_SLOT = 16;
	private static final int SHOW_TAG_SLOT = 22;
	private static final int WAYPOINT_SLOT = 20;
	private static final int LEAD_SLOT = 24;
	private static final int LEAVE_SLOT = 26;
	private final UUID groupId;

	GroupHomeMenu(Player player, UUID groupId) {
		super(player, "거래 - 그룹");
		this.groupId = groupId;
	}

	private Groups.Group group() {
		return Groups.member(groupId, player.getUniqueId());
	}

	private GroupHomeMenu again() {
		return new GroupHomeMenu(player, groupId);
	}

	private boolean can(Groups.Group group, Groups.Perm perm) {
		return group.has(player.getUniqueId(), perm);
	}

	private String allowed(Groups.Group group, Groups.Perm perm) {
		return can(group, perm) ? "클릭: 열기" : "권한이 없습니다 (" + perm.label + ")";
	}

	@Override
	void draw() {
		inventory.setItem(BACK_SLOT, TradeItems.back());
		Groups.Group group = group();
		if (group == null) {
			inventory.setItem(INFO_SLOT, TradeItems.button(Material.BARRIER, "이 그룹에 속해 있지 않습니다", NamedTextColor.GRAY));
			return;
		}
		UUID me = player.getUniqueId();
		Groups.Role role = group.roleOf(me);
		String tag = group.tag.isEmpty() ? "없음" : group.plainTag();
		boolean shown = Groups.shownTag(me) == group;
		inventory.setItem(INFO_SLOT, TradeItems.glint(TradeItems.button(Material.CYAN_BANNER, group.name, NamedTextColor.AQUA,
				"접두사: " + tag, "그룹장: " + group.nameOf(group.leader), "내 역할: " + role.name,
				"그룹원: " + group.memberRoles.size() + "/" + Groups.MAX_MEMBERS + "명", "그룹 할인율: " + group.discount + "%")));
		inventory.setItem(MEMBERS_SLOT, TradeItems.button(Material.PLAYER_HEAD, "그룹원", NamedTextColor.YELLOW,
				"그룹원과 역할을 봅니다.", "역할 지정, 추방도 여기서 합니다."));
		inventory.setItem(INVITE_SLOT, TradeItems.button(Material.EMERALD, "초대하기", NamedTextColor.GREEN,
				"플레이어를 골라 초대합니다.", allowed(group, Groups.Perm.INVITE)));
		inventory.setItem(ROLES_SLOT, TradeItems.button(Material.NAME_TAG, "역할 관리", NamedTextColor.LIGHT_PURPLE,
				"역할(" + group.roles.size() + "/" + Groups.MAX_ROLES + ")과 역할별 권한, 순위를 봅니다.",
				"만들기, 이름/권한/순위 바꾸기, 지우기"));
		inventory.setItem(SENT_SLOT, TradeItems.button(Material.PAPER, "보낸 초대 (" + group.invites.size() + ")", NamedTextColor.WHITE,
				"아직 수락하지 않은 초대를 보고 취소합니다."));
		inventory.setItem(RENAME_SLOT, TradeItems.button(Material.OAK_SIGN, "그룹 이름 바꾸기", NamedTextColor.GOLD,
				"지금: " + group.name, allowed(group, Groups.Perm.RENAME)));
		inventory.setItem(TAG_SLOT, TradeItems.button(Material.GOLDEN_HELMET, "그룹 접두사 정하기", NamedTextColor.GOLD,
				"이 그룹의 접두사입니다. 예: [SRT]" + player.getName(), "지금: " + tag, allowed(group, Groups.Perm.RENAME)));
		inventory.setItem(SETTINGS_SLOT, TradeItems.button(Material.COMPARATOR, "그룹 할인율", NamedTextColor.GOLD,
				"그룹 할인 권한이 있는 그룹원이", "다른 그룹원의 물품을 살 때 깎아 주는 비율", "지금: " + group.discount + "%",
				allowed(group, Groups.Perm.SETTINGS)));
		inventory.setItem(SHOW_TAG_SLOT, shown
				? TradeItems.glint(TradeItems.button(Material.LIME_DYE, "내 이름 앞에 표시 중", NamedTextColor.GREEN,
						"이 그룹의 접두사가 내 이름 앞에 붙어 있습니다.", "클릭: 접두사 표시 끄기"))
				: TradeItems.button(Material.GRAY_DYE, "내 이름 앞에 이 접두사 달기", NamedTextColor.WHITE,
						group.tag.isEmpty() ? "이 그룹은 아직 접두사가 없습니다." : "클릭: " + tag + player.getName() + "(으)로 표시",
						"이름 앞에는 그룹 하나의 접두사만 붙습니다."));
		inventory.setItem(WAYPOINT_SLOT, TradeItems.button(Material.LODESTONE, "그룹 웨이포인트 (" + group.waypoints.size() + "/"
				+ Groups.WAYPOINT_PRICES.length + ")", NamedTextColor.AQUA, "그룹 웨이포인트로 텔레포트합니다. (다이아 " + Groups.TELEPORT_PRICE + "개)",
				"좌표는 권한이 있는 역할만 볼 수 있습니다."));
		if (me.equals(group.leader)) {
			inventory.setItem(LEAD_SLOT, TradeItems.button(Material.GOLDEN_APPLE, "그룹장 넘기기", NamedTextColor.YELLOW,
					"다른 그룹원에게 그룹장을 넘깁니다.", "나는 부대표가 됩니다."));
			inventory.setItem(LEAVE_SLOT, TradeItems.button(Material.TNT, "그룹 해체", NamedTextColor.RED,
					"그룹을 없앱니다. 되돌릴 수 없습니다.", "만들 때 낸 다이아는 돌려받지 못합니다.",
					bedrock() ? "누르면 해체" : "우클릭: 해체"));
		} else {
			inventory.setItem(LEAVE_SLOT, TradeItems.button(Material.OAK_DOOR, "그룹 나가기", NamedTextColor.RED,
					bedrock() ? "누르면 나갑니다." : "우클릭: 나가기"));
		}
	}

	@Override
	void click(int slot, ClickType click) {
		if (slot == BACK_SLOT) {
			clickSound();
			new GroupMenu(player).open();
			return;
		}
		Groups.Group group = group();
		if (group == null) {
			return;
		}
		switch (slot) {
			case MEMBERS_SLOT -> {
				clickSound();
				GroupMemberPicker.members(player, groupId).open();
			}
			case INVITE_SLOT -> invite(group);
			case ROLES_SLOT -> {
				clickSound();
				new GroupRolesMenu(player, groupId).open();
			}
			case SENT_SLOT -> {
				clickSound();
				new GroupInvitesMenu(player, groupId).open();
			}
			case RENAME_SLOT -> rename(group);
			case TAG_SLOT -> tag(group);
			case SETTINGS_SLOT -> {
				if (refuse(group, Groups.Perm.SETTINGS)) {
					return;
				}
				clickSound();
				new GroupSettingsMenu(player, groupId).open();
			}
			case SHOW_TAG_SLOT -> {
				boolean shown = Groups.shownTag(player.getUniqueId()) == group;
				if (!shown && group.tag.isEmpty()) {
					tell("이 그룹은 아직 접두사가 없습니다.", NamedTextColor.RED);
					failSound();
					return;
				}
				Groups.showTag(player.getUniqueId(), shown ? null : group);
				successSound();
				redraw();
			}
			case WAYPOINT_SLOT -> {
				clickSound();
				new GroupWaypointsMenu(player, groupId).open();
			}
			case LEAD_SLOT -> handOver(group);
			case LEAVE_SLOT -> leave(group, click);
			default -> {
			}
		}
	}

	private boolean refuse(Groups.Group group, Groups.Perm perm) {
		if (can(group, perm)) {
			return false;
		}
		tell("'" + perm.label + "' 권한이 있는 역할만 할 수 있습니다.", NamedTextColor.RED);
		failSound();
		return true;
	}

	private void invite(Groups.Group group) {
		if (refuse(group, Groups.Perm.INVITE)) {
			return;
		}
		clickSound();
		new PlayerPicker(player, "거래 - 초대할 플레이어", false, false, 0, this::again, (id, name) -> {
			Groups.Group current = group();
			if (current == null || !current.has(player.getUniqueId(), Groups.Perm.INVITE)) {
				again().open();
				return;
			}
			if (current.memberRoles.containsKey(id)) {
				tell(name + "님은 이미 이 그룹원입니다.", NamedTextColor.RED);
				failSound();
			} else if (Groups.of(id).size() >= Groups.MAX_GROUPS) {
				tell(name + "님은 이미 그룹 " + Groups.MAX_GROUPS + "개에 속해 있습니다.", NamedTextColor.RED);
				failSound();
			} else if (current.memberRoles.size() + current.invites.size() >= Groups.MAX_MEMBERS) {
				tell("그룹원과 초대 중인 사람을 합쳐 " + Groups.MAX_MEMBERS + "명까지입니다.", NamedTextColor.RED);
				failSound();
			} else if (Groups.invitesFor(id).size() >= Groups.MAX_INVITES && !current.invites.contains(id)) {
				tell(name + "님은 받은 초대가 너무 많습니다.", NamedTextColor.RED);
				failSound();
			} else {
				current.invites.add(id);
				Groups.save();
				tell(name + "님을 초대했습니다.", NamedTextColor.GREEN);
				successSound();
				Player invited = Bukkit.getPlayer(id);
				if (invited != null) {
					invited.sendMessage(Component.text(player.getName() + "님이 그룹 '" + current.name
							+ "'에 초대했습니다. /trade → 그룹 → 받은 초대에서 수락하세요.", NamedTextColor.GOLD));
				}
			}
			again().open();
		}).open();
	}

	private void rename(Groups.Group group) {
		if (refuse(group, Groups.Perm.RENAME)) {
			return;
		}
		ChatPrompt.ask(player, "새 그룹 이름을 입력하세요. (2~16글자, 지금: " + group.name + ")", name -> {
			Groups.Group current = group();
			if (current == null || !current.has(player.getUniqueId(), Groups.Perm.RENAME)) {
				return;
			}
			String why = Groups.badName(name, current);
			if (why != null) {
				tell(why, NamedTextColor.RED);
				failSound();
			} else {
				current.name = name;
				Groups.save();
				tell("그룹 이름을 '" + name + "'(으)로 바꿨습니다.", NamedTextColor.GREEN);
				successSound();
			}
			again().open();
		}, () -> again().open());
	}

	private void tag(Groups.Group group) {
		if (refuse(group, Groups.Perm.RENAME)) {
			return;
		}
		ChatPrompt.ask(player, "그룹 접두사를 입력하세요. 예: SRT → [SRT]" + player.getName()
				+ "  (1~8글자, &a 같은 색 코드 가능, '없음'을 입력하면 지웁니다)", text -> {
			Groups.Group current = group();
			if (current == null || !current.has(player.getUniqueId(), Groups.Perm.RENAME)) {
				return;
			}
			if (text.equals("없음") || text.equals("-")) {
				current.tag = "";
			} else {
				String why = Groups.badTag(text);
				if (why != null) {
					tell(why, NamedTextColor.RED);
					failSound();
					again().open();
					return;
				}
				current.tag = text;
			}
			Groups.save();
			Groups.refreshGroup(current);
			tell(current.tag.isEmpty() ? "접두사를 지웠습니다." : "그룹 접두사를 " + current.plainTag() + "(으)로 정했습니다.", NamedTextColor.GREEN);
			successSound();
			again().open();
		}, () -> again().open());
	}

	private void handOver(Groups.Group group) {
		if (!player.getUniqueId().equals(group.leader)) {
			return;
		}
		if (group.memberRoles.size() < 2) {
			tell("넘겨받을 다른 그룹원이 없습니다.", NamedTextColor.RED);
			failSound();
			return;
		}
		clickSound();
		new GroupMemberPicker(player, "거래 - 그룹장을 넘길 그룹원", groupId, this::again, id -> {
			Groups.Group current = group();
			if (current != null && player.getUniqueId().equals(current.leader) && current.memberRoles.containsKey(id)
					&& !id.equals(current.leader)) {
				current.leader = id;
				current.memberRoles.put(id, Groups.LEADER);
				current.memberRoles.put(player.getUniqueId(), current.role(Groups.DEPUTY) != null ? Groups.DEPUTY : Groups.MEMBER);
				Groups.save();
				tell(current.nameOf(id) + "님에게 그룹장을 넘겼습니다.", NamedTextColor.GREEN);
				successSound();
				Player heir = Bukkit.getPlayer(id);
				if (heir != null) {
					heir.sendMessage(Component.text("그룹 '" + current.name + "'의 그룹장이 되었습니다.", NamedTextColor.GOLD));
				}
			}
			again().open();
		}).open();
	}

	private void leave(Groups.Group group, ClickType click) {
		if (!confirmed(click)) {
			tell("실수를 막기 위해 우클릭해야 합니다.", NamedTextColor.GRAY);
			return;
		}
		if (player.getUniqueId().equals(group.leader)) {
			for (UUID member : group.memberRoles.keySet()) {
				Player online = Bukkit.getPlayer(member);
				if (online != null && online != player) {
					online.sendMessage(Component.text("그룹 '" + group.name + "'이(가) 해체되었습니다.", NamedTextColor.RED));
				}
			}
			Groups.disband(group);
			tell("그룹 '" + group.name + "'을(를) 해체했습니다.", NamedTextColor.YELLOW);
		} else {
			Groups.removeMember(group, player.getUniqueId());
			Groups.save();
			tell("그룹 '" + group.name + "'에서 나왔습니다.", NamedTextColor.YELLOW);
		}
		successSound();
		new GroupMenu(player).open();
	}
}
