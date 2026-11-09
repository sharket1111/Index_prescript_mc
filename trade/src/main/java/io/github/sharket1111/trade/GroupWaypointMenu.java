package io.github.sharket1111.trade;

import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import net.kyori.adventure.text.format.NamedTextColor;

/** One group waypoint: teleporting there (for a trade diamond), and for managers renaming, moving and deleting it. */
final class GroupWaypointMenu extends Menu {
	private static final int INFO_SLOT = 4;
	private static final int TELEPORT_SLOT = 11;
	private static final int RENAME_SLOT = 13;
	private static final int MOVE_SLOT = 15;
	private static final int DELETE_SLOT = 26;
	private final UUID groupId;
	private final Groups.Waypoint waypoint;

	GroupWaypointMenu(Player player, UUID groupId, Groups.Waypoint waypoint) {
		super(player, "거래 - 웨이포인트");
		this.groupId = groupId;
		this.waypoint = waypoint;
	}

	static String badName(String name) {
		if (name.isEmpty() || name.length() > 16) {
			return "웨이포인트 이름은 1~16글자로 지어 주세요.";
		}
		if (name.contains("&") || name.contains("§")) {
			return "웨이포인트 이름에는 색 코드를 쓸 수 없습니다.";
		}
		return null;
	}

	/** The group, if the player is in it and the waypoint still belongs to it. */
	private Groups.Group group() {
		Groups.Group group = Groups.member(groupId, player.getUniqueId());
		return group != null && group.waypoints.contains(waypoint) ? group : null;
	}

	private GroupWaypointMenu again() {
		return new GroupWaypointMenu(player, groupId, waypoint);
	}

	@Override
	void draw() {
		inventory.setItem(BACK_SLOT, TradeItems.back());
		Groups.Group group = group();
		if (group == null) {
			inventory.setItem(13, TradeItems.button(Material.BARRIER, "없어진 웨이포인트입니다", NamedTextColor.GRAY));
			return;
		}
		UUID me = player.getUniqueId();
		boolean view = group.has(me, Groups.Perm.WAYPOINT_VIEW);
		boolean use = group.has(me, Groups.Perm.WAYPOINT_USE);
		boolean manage = group.has(me, Groups.Perm.WAYPOINT_MANAGE);
		inventory.setItem(INFO_SLOT, TradeItems.button(Material.LODESTONE, waypoint.name, NamedTextColor.AQUA,
				"그룹: " + group.name, view ? "좌표: " + waypoint.coords() : "좌표: 비공개 ('웨이포인트 좌표 보기' 권한 필요)"));
		inventory.setItem(TELEPORT_SLOT, TradeItems.button(use ? Material.ENDER_PEARL : Material.GRAY_DYE, "텔레포트",
				use ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.GRAY, Money.format(Groups.TELEPORT_PRICE),
				"내 " + Money.name() + ": " + Money.format(Money.balance(player.getUniqueId())),
				use ? "클릭: 이동" : "'웨이포인트 사용' 권한이 필요합니다."));
		if (manage) {
			inventory.setItem(RENAME_SLOT, TradeItems.button(Material.OAK_SIGN, "이름 바꾸기", NamedTextColor.GOLD,
					"지금: " + waypoint.name, "클릭: 채팅으로 입력"));
			inventory.setItem(MOVE_SLOT, TradeItems.button(Material.COMPASS, "여기로 옮기기", NamedTextColor.GOLD,
					"웨이포인트를 지금 서 있는 곳으로 옮깁니다. (무료)", bedrock() ? "두 번 누르면 옮깁니다." : "우클릭: 옮기기"));
			inventory.setItem(DELETE_SLOT, TradeItems.button(Material.LAVA_BUCKET, "지우기", NamedTextColor.RED,
					"낸 돈은 돌려받지 못합니다.", "다시 만들면 그 자리의 가격을 냅니다.", bedrock() ? "두 번 누르면 지웁니다." : "우클릭: 지우기"));
		}
	}

	@Override
	void click(int slot, ClickType click) {
		if (slot == BACK_SLOT) {
			clickSound();
			new GroupWaypointsMenu(player, groupId).open();
			return;
		}
		Groups.Group group = group();
		if (group == null) {
			return;
		}
		UUID me = player.getUniqueId();
		if (slot == TELEPORT_SLOT) {
			teleport(group);
			return;
		}
		if (!group.has(me, Groups.Perm.WAYPOINT_MANAGE)) {
			return;
		}
		switch (slot) {
			case RENAME_SLOT -> ChatPrompt.ask(player, "새 웨이포인트 이름을 입력하세요. (1~16글자, 지금: " + waypoint.name + ")", name -> {
				Groups.Group current = group();
				if (current != null && current.has(me, Groups.Perm.WAYPOINT_MANAGE)) {
					String why = badName(name);
					if (why != null) {
						tell(why, NamedTextColor.RED);
						failSound();
					} else {
						waypoint.name = name;
						Groups.save();
						tell("웨이포인트 이름을 '" + name + "'(으)로 바꿨습니다.", NamedTextColor.GREEN);
						successSound();
					}
				}
				again().open();
			}, () -> again().open());
			case MOVE_SLOT -> {
				if (!confirmed(click)) {
					tell("실수를 막기 위해 우클릭해야 합니다.", NamedTextColor.GRAY);
					return;
				}
				waypoint.moveTo(player.getLocation());
				Groups.save();
				tell("웨이포인트 '" + waypoint.name + "'을(를) 지금 위치로 옮겼습니다.", NamedTextColor.GREEN);
				successSound();
				redraw();
			}
			case DELETE_SLOT -> {
				if (!confirmed(click)) {
					tell("실수를 막기 위해 우클릭해야 합니다.", NamedTextColor.GRAY);
					return;
				}
				group.waypoints.remove(waypoint);
				Groups.save();
				tell("웨이포인트 '" + waypoint.name + "'을(를) 지웠습니다.", NamedTextColor.YELLOW);
				successSound();
				new GroupWaypointsMenu(player, groupId).open();
			}
			default -> {
			}
		}
	}

	private void teleport(Groups.Group group) {
		teleport(this, player, group, waypoint);
	}

	/** Teleports {@code player} (looking at {@code menu}) to a waypoint, for {@link Groups#TELEPORT_PRICE}, if their role may. */
	static void teleport(Menu menu, Player player, Groups.Group group, Groups.Waypoint waypoint) {
		if (!group.has(player.getUniqueId(), Groups.Perm.WAYPOINT_USE)) {
			menu.tell("'웨이포인트 사용' 권한이 있는 역할만 텔레포트할 수 있습니다.", NamedTextColor.RED);
			menu.failSound();
			return;
		}
		Location to = waypoint.location();
		if (to == null) {
			menu.tell("웨이포인트가 있는 월드를 찾을 수 없습니다.", NamedTextColor.RED);
			menu.failSound();
			return;
		}
		if (!Money.take(player.getUniqueId(), Groups.TELEPORT_PRICE)) {
			menu.tell("텔레포트하려면 " + Money.format(Groups.TELEPORT_PRICE) + "이(가) 필요합니다.", NamedTextColor.RED);
			menu.failSound();
			return;
		}
		player.closeInventory();
		String name = waypoint.name;
		player.teleportAsync(to).thenAccept(done -> {
			if (done) {
				player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.8F, 1.0F);
				player.sendMessage(net.kyori.adventure.text.Component.text("'" + name + "'(으)로 이동했습니다. ("
						+ Money.format(Groups.TELEPORT_PRICE) + ")", NamedTextColor.GREEN));
			} else {
				Money.add(player.getUniqueId(), Groups.TELEPORT_PRICE);
				player.sendMessage(net.kyori.adventure.text.Component.text("텔레포트하지 못해 돈을 돌려드렸습니다.", NamedTextColor.RED));
			}
		});
	}
}
