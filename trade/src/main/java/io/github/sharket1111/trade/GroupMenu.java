package io.github.sharket1111.trade;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * The group tab. Without a group: founding one (for trade diamonds) and the
 * invitations received. In a group: its members, inviting, roles, name, tag,
 * discount, handing over the lead, and leaving or disbanding.
 */
final class GroupMenu extends Menu {
	private static final int INFO_SLOT = 4;
	private static final int CREATE_SLOT = 11;
	private static final int RECEIVED_SLOT = 15;
	private static final int MEMBERS_SLOT = 10;
	private static final int INVITE_SLOT = 11;
	private static final int ROLES_SLOT = 12;
	private static final int SENT_SLOT = 13;
	private static final int RENAME_SLOT = 14;
	private static final int TAG_SLOT = 15;
	private static final int SETTINGS_SLOT = 16;
	private static final int LEAD_SLOT = 24;
	private static final int LEAVE_SLOT = 26;

	GroupMenu(Player player) {
		super(player, "거래 - 그룹");
	}

	private Groups.Group group() {
		return Groups.of(player.getUniqueId());
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
			inventory.setItem(INFO_SLOT, TradeItems.button(Material.WHITE_BANNER, "속한 그룹이 없습니다", NamedTextColor.GRAY,
					"그룹을 만들거나, 초대를 받아 들어갈 수 있습니다."));
			inventory.setItem(CREATE_SLOT, TradeItems.glint(TradeItems.button(Material.CYAN_BANNER, "그룹 만들기", NamedTextColor.AQUA,
					"거래 전용 다이아 " + Groups.PRICE + "개가 듭니다.", "그룹 이름을 채팅으로 정합니다.",
					"내 거래 전용 다이아: " + count(TradeItems::isTradeDiamond) + "개")));
			int invites = Groups.invitesFor(player.getUniqueId()).size();
			inventory.setItem(RECEIVED_SLOT, TradeItems.button(invites > 0 ? Material.WRITABLE_BOOK : Material.BOOK,
					"받은 초대 (" + invites + ")", invites > 0 ? NamedTextColor.GOLD : NamedTextColor.GRAY, "그룹 초대를 수락하거나 거절합니다."));
			return;
		}
		UUID me = player.getUniqueId();
		Groups.Role role = group.roleOf(me);
		String tag = group.tag.isEmpty() ? "없음" : group.plainTag();
		inventory.setItem(INFO_SLOT, TradeItems.glint(TradeItems.button(Material.CYAN_BANNER, group.name, NamedTextColor.AQUA,
				"접두사: " + tag, "그룹장: " + group.nameOf(group.leader), "내 역할: " + role.name,
				"그룹원: " + group.memberRoles.size() + "/" + Groups.MAX_MEMBERS + "명", "그룹 할인율: " + group.discount + "%")));
		inventory.setItem(MEMBERS_SLOT, TradeItems.button(Material.PLAYER_HEAD, "그룹원", NamedTextColor.YELLOW,
				"그룹원과 역할을 봅니다.", "역할 지정, 추방도 여기서 합니다."));
		inventory.setItem(INVITE_SLOT, TradeItems.button(Material.EMERALD, "초대하기", NamedTextColor.GREEN,
				"플레이어를 골라 초대합니다.", allowed(group, Groups.Perm.INVITE)));
		inventory.setItem(ROLES_SLOT, TradeItems.button(Material.NAME_TAG, "역할 관리", NamedTextColor.LIGHT_PURPLE,
				"역할(" + group.roles.size() + "/" + Groups.MAX_ROLES + ")과 역할별 권한을 봅니다.", "만들기, 이름/권한 바꾸기, 지우기"));
		inventory.setItem(SENT_SLOT, TradeItems.button(Material.PAPER, "보낸 초대 (" + group.invites.size() + ")", NamedTextColor.WHITE,
				"아직 수락하지 않은 초대를 보고 취소합니다."));
		inventory.setItem(RENAME_SLOT, TradeItems.button(Material.OAK_SIGN, "그룹 이름 바꾸기", NamedTextColor.GOLD,
				"지금: " + group.name, allowed(group, Groups.Perm.RENAME)));
		inventory.setItem(TAG_SLOT, TradeItems.button(Material.GOLDEN_HELMET, "접두사 정하기", NamedTextColor.GOLD,
				"그룹원 이름 앞에 붙는 표시입니다.", "예: [SRT]" + player.getName(), "지금: " + tag, allowed(group, Groups.Perm.RENAME)));
		inventory.setItem(SETTINGS_SLOT, TradeItems.button(Material.COMPARATOR, "그룹 할인율", NamedTextColor.GOLD,
				"그룹 할인 권한이 있는 그룹원이", "다른 그룹원의 물품을 살 때 깎아 주는 비율", "지금: " + group.discount + "%",
				allowed(group, Groups.Perm.SETTINGS)));
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
			new MainMenu(player).open();
			return;
		}
		Groups.Group group = group();
		if (group == null) {
			if (slot == CREATE_SLOT) {
				create();
			} else if (slot == RECEIVED_SLOT) {
				clickSound();
				new GroupInvitesMenu(player, false).open();
			}
			return;
		}
		switch (slot) {
			case MEMBERS_SLOT -> {
				clickSound();
				GroupMemberPicker.members(player).open();
			}
			case INVITE_SLOT -> invite(group);
			case ROLES_SLOT -> {
				clickSound();
				new GroupRolesMenu(player).open();
			}
			case SENT_SLOT -> {
				clickSound();
				new GroupInvitesMenu(player, true).open();
			}
			case RENAME_SLOT -> rename(group);
			case TAG_SLOT -> tag(group);
			case SETTINGS_SLOT -> {
				if (refuse(group, Groups.Perm.SETTINGS)) {
					return;
				}
				clickSound();
				new GroupSettingsMenu(player).open();
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

	private void create() {
		if (count(TradeItems::isTradeDiamond) < Groups.PRICE) {
			tell("그룹을 만들려면 거래 전용 다이아 " + Groups.PRICE + "개가 필요합니다.", NamedTextColor.RED);
			failSound();
			return;
		}
		ChatPrompt.ask(player, "새 그룹의 이름을 입력하세요. (2~16글자)", name -> {
			String why = Groups.badName(name, null);
			if (why == null && Groups.of(player.getUniqueId()) != null) {
				why = "이미 그룹에 속해 있습니다.";
			}
			if (why == null && count(TradeItems::isTradeDiamond) < Groups.PRICE) {
				why = "거래 전용 다이아가 부족합니다.";
			}
			if (why != null) {
				tell(why, NamedTextColor.RED);
				failSound();
				new GroupMenu(player).open();
				return;
			}
			take(TradeItems::isTradeDiamond, Groups.PRICE);
			Groups.create(player, name);
			tell("그룹 '" + name + "'을(를) 만들었습니다. 거래 전용 다이아 " + Groups.PRICE + "개를 냈습니다.", NamedTextColor.GREEN);
			successSound();
			new GroupMenu(player).open();
		}, () -> new GroupMenu(player).open());
	}

	private void invite(Groups.Group group) {
		if (refuse(group, Groups.Perm.INVITE)) {
			return;
		}
		clickSound();
		new PlayerPicker(player, "거래 - 초대할 플레이어", false, false, 0, () -> new GroupMenu(player), (id, name) -> {
			Groups.Group current = group();
			if (current == null || !current.has(player.getUniqueId(), Groups.Perm.INVITE)) {
				new GroupMenu(player).open();
				return;
			}
			if (Groups.of(id) != null) {
				tell(name + "님은 이미 " + (Groups.of(id) == current ? "이 그룹원입니다." : "다른 그룹에 속해 있습니다."), NamedTextColor.RED);
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
					invited.sendMessage(Component.text(player.getName() + "님이 그룹 '" + current.name + "'에 초대했습니다. /trade → 그룹 → 받은 초대에서 수락하세요.",
							NamedTextColor.GOLD));
				}
			}
			new GroupMenu(player).open();
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
			new GroupMenu(player).open();
		}, () -> new GroupMenu(player).open());
	}

	private void tag(Groups.Group group) {
		if (refuse(group, Groups.Perm.RENAME)) {
			return;
		}
		ChatPrompt.ask(player, "접두사를 입력하세요. 예: SRT → [SRT]" + player.getName()
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
					new GroupMenu(player).open();
					return;
				}
				current.tag = text;
			}
			Groups.save();
			Groups.refreshGroup(current);
			tell(current.tag.isEmpty() ? "접두사를 지웠습니다." : "접두사를 " + current.plainTag() + "(으)로 정했습니다.", NamedTextColor.GREEN);
			successSound();
			new GroupMenu(player).open();
		}, () -> new GroupMenu(player).open());
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
		new GroupMemberPicker(player, "거래 - 그룹장을 넘길 그룹원", () -> new GroupMenu(player), id -> {
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
			new GroupMenu(player).open();
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
