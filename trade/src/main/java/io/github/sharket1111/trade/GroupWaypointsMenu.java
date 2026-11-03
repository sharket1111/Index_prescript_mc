package io.github.sharket1111.trade;

import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import net.kyori.adventure.text.format.NamedTextColor;

/**
 * A group's waypoints (up to five, each dearer than the last). Coordinates
 * show only to roles allowed to see them; clicking one opens it (teleport,
 * and for managers rename, move, delete). Managers buy new ones here, set
 * where they stand.
 */
final class GroupWaypointsMenu extends Menu {
	private static final int[] WAYPOINT_SLOTS = {2, 3, 4, 5, 6};
	private static final int CREATE_SLOT = 22;
	private final UUID groupId;

	GroupWaypointsMenu(Player player, UUID groupId) {
		super(player, "거래 - 그룹 웨이포인트");
		this.groupId = groupId;
	}

	private Groups.Group group() {
		return Groups.member(groupId, player.getUniqueId());
	}

	/** What the next waypoint costs, or -1 if the group has all it may. */
	static int nextPrice(Groups.Group group) {
		int count = group.waypoints.size();
		return count < Groups.WAYPOINT_PRICES.length ? Groups.WAYPOINT_PRICES[count] : -1;
	}

	@Override
	void draw() {
		inventory.setItem(BACK_SLOT, TradeItems.back());
		Groups.Group group = group();
		if (group == null) {
			inventory.setItem(13, TradeItems.button(Material.BARRIER, "이 그룹에 속해 있지 않습니다", NamedTextColor.GRAY));
			return;
		}
		UUID me = player.getUniqueId();
		boolean view = group.has(me, Groups.Perm.WAYPOINT_VIEW);
		boolean use = group.has(me, Groups.Perm.WAYPOINT_USE);
		for (int i = 0; i < WAYPOINT_SLOTS.length; i++) {
			if (i < group.waypoints.size()) {
				Groups.Waypoint waypoint = group.waypoints.get(i);
				inventory.setItem(WAYPOINT_SLOTS[i], TradeItems.button(Material.LODESTONE, waypoint.name, NamedTextColor.AQUA,
						view ? "좌표: " + waypoint.coords() : "좌표: 비공개",
						use ? "텔레포트: 거래 전용 다이아 " + Groups.TELEPORT_PRICE + "개" : "사용 권한이 없습니다", "클릭: 열기"));
			} else {
				inventory.setItem(WAYPOINT_SLOTS[i], TradeItems.button(Material.GRAY_STAINED_GLASS_PANE, "빈 자리 " + (i + 1),
						NamedTextColor.DARK_GRAY, "만들 때 가격: 거래 전용 다이아 " + Groups.WAYPOINT_PRICES[i] + "개"));
			}
		}
		int price = nextPrice(group);
		boolean manage = group.has(me, Groups.Perm.WAYPOINT_MANAGE);
		if (price < 0) {
			inventory.setItem(CREATE_SLOT, TradeItems.button(Material.GRAY_DYE, "웨이포인트가 가득 찼습니다", NamedTextColor.GRAY,
					"그룹당 " + Groups.WAYPOINT_PRICES.length + "개까지 만들 수 있습니다."));
		} else {
			inventory.setItem(CREATE_SLOT, TradeItems.glint(TradeItems.button(manage ? Material.EMERALD : Material.GRAY_DYE,
					"웨이포인트 만들기 (" + group.waypoints.size() + "/" + Groups.WAYPOINT_PRICES.length + ")",
					manage ? NamedTextColor.GREEN : NamedTextColor.GRAY, "지금 서 있는 곳에 만듭니다.",
					"가격: 거래 전용 다이아 " + price + "개", "내 거래 전용 다이아: " + count(TradeItems::isTradeDiamond) + "개",
					manage ? "클릭: 이름을 채팅으로 정합니다." : "'웨이포인트 관리' 권한이 필요합니다.")));
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
		for (int i = 0; i < WAYPOINT_SLOTS.length && i < group.waypoints.size(); i++) {
			if (slot == WAYPOINT_SLOTS[i]) {
				clickSound();
				new GroupWaypointMenu(player, groupId, group.waypoints.get(i)).open();
				return;
			}
		}
		if (slot == CREATE_SLOT) {
			create(group);
		}
	}

	private String whyNotCreate(Groups.Group group) {
		if (!group.has(player.getUniqueId(), Groups.Perm.WAYPOINT_MANAGE)) {
			return "'웨이포인트 관리' 권한이 있는 역할만 만들 수 있습니다.";
		}
		int price = nextPrice(group);
		if (price < 0) {
			return "웨이포인트는 그룹당 " + Groups.WAYPOINT_PRICES.length + "개까지입니다.";
		}
		if (count(TradeItems::isTradeDiamond) < price) {
			return "거래 전용 다이아 " + price + "개가 필요합니다.";
		}
		return null;
	}

	private void create(Groups.Group group) {
		String why = whyNotCreate(group);
		if (why != null) {
			tell(why, NamedTextColor.RED);
			failSound();
			return;
		}
		ChatPrompt.ask(player, "웨이포인트 이름을 입력하세요. (1~16글자, 지금 서 있는 곳에 만들어집니다)", name -> {
			Groups.Group current = group();
			String problem = current == null ? "이 그룹에 속해 있지 않습니다." : whyNotCreate(current);
			if (problem == null) {
				problem = GroupWaypointMenu.badName(name);
			}
			if (problem != null) {
				tell(problem, NamedTextColor.RED);
				failSound();
				new GroupWaypointsMenu(player, groupId).open();
				return;
			}
			int price = nextPrice(current);
			take(TradeItems::isTradeDiamond, price);
			current.waypoints.add(new Groups.Waypoint(name, player.getLocation()));
			Groups.save();
			tell("웨이포인트 '" + name + "'을(를) 만들었습니다. 거래 전용 다이아 " + price + "개를 냈습니다.", NamedTextColor.GREEN);
			successSound();
			new GroupWaypointsMenu(player, groupId).open();
		}, () -> new GroupWaypointsMenu(player, groupId).open());
	}
}
