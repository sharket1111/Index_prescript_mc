package io.github.sharket1111.trade;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Every waypoint of every group the player is in, on one screen (opened with
 * the waypoint book): a row per group. Clicking one teleports there for a
 * trade diamond, if the player's role in that group may.
 */
final class WaypointListMenu extends Menu {
	private static final int BACK = 49;
	private final Map<Integer, UUID> groupBySlot = new HashMap<>();
	private final Map<Integer, Groups.Waypoint> waypointBySlot = new HashMap<>();

	WaypointListMenu(Player player) {
		super(player, "그룹 웨이포인트 (텔레포트: 다이아 " + Groups.TELEPORT_PRICE + "개)", 6);
	}

	@Override
	void draw() {
		groupBySlot.clear();
		waypointBySlot.clear();
		UUID me = player.getUniqueId();
		int row = 0;
		for (Groups.Group group : Groups.of(me)) {
			if (group.waypoints.isEmpty()) {
				continue;
			}
			if (row >= 5) {
				break;
			}
			int base = row * 9;
			boolean view = group.has(me, Groups.Perm.WAYPOINT_VIEW);
			boolean use = group.has(me, Groups.Perm.WAYPOINT_USE);
			inventory.setItem(base, TradeItems.button(Material.CYAN_BANNER, group.name, NamedTextColor.AQUA,
					use ? "웨이포인트를 눌러 텔레포트합니다." : "이 그룹에서는 사용 권한이 없습니다."));
			for (int i = 0; i < group.waypoints.size() && i < 8; i++) {
				Groups.Waypoint waypoint = group.waypoints.get(i);
				int slot = base + 1 + i;
				inventory.setItem(slot, TradeItems.button(use ? Material.LODESTONE : Material.GRAY_DYE, waypoint.name,
						use ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.GRAY, "그룹: " + group.name,
						view ? "좌표: " + waypoint.coords() : "좌표: 비공개",
						use ? "클릭: 텔레포트 (거래 전용 다이아 " + Groups.TELEPORT_PRICE + "개)" : "사용 권한이 없습니다"));
				groupBySlot.put(slot, group.id);
				waypointBySlot.put(slot, waypoint);
			}
			row++;
		}
		if (row == 0) {
			inventory.setItem(22, TradeItems.button(Material.BARRIER, "웨이포인트가 없습니다", NamedTextColor.GRAY,
					"속한 그룹에서 웨이포인트를 만들면 여기에 나옵니다.", "(/trade → 그룹 → 그룹 웨이포인트)"));
		}
		inventory.setItem(BACK, TradeItems.button(Material.CYAN_BANNER, "그룹 메뉴", NamedTextColor.WHITE, "내 그룹 목록으로 갑니다."));
	}

	@Override
	void click(int slot, ClickType click) {
		if (slot == BACK) {
			clickSound();
			new GroupMenu(player).open();
			return;
		}
		Groups.Waypoint waypoint = waypointBySlot.get(slot);
		Groups.Group group = Groups.member(groupBySlot.get(slot), player.getUniqueId());
		if (waypoint == null || group == null || !group.waypoints.contains(waypoint)) {
			return;
		}
		GroupWaypointMenu.teleport(this, player, group, waypoint);
	}
}
