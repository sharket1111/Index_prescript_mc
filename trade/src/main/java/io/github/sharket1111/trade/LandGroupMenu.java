package io.github.sharket1111.trade;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Land permissions for the owner's groups, on the chunk they stand in. First
 * the owner picks one of their groups (skipped with only one); then one
 * setting for every member of it, and one for each role (which comes first).
 * A player named on their own in the land tab goes by that instead; someone
 * in several of the owner's groups gets everything any of them allows.
 */
final class LandGroupMenu extends Menu {
	private static final int GROUP_SLOT = 0;
	private final Location at;
	/** The group being set, or null while picking one. */
	private final UUID groupId;
	private final Map<Integer, String> bySlot = new HashMap<>();
	private final Map<Integer, UUID> groupBySlot = new HashMap<>();

	LandGroupMenu(Player player, Location at) {
		this(player, at, onlyGroup(player));
	}

	LandGroupMenu(Player player, Location at, UUID groupId) {
		super(player, "거래 - 땅 그룹 권한");
		this.at = at;
		this.groupId = groupId;
	}

	private static UUID onlyGroup(Player player) {
		List<Groups.Group> groups = Groups.of(player.getUniqueId());
		return groups.size() == 1 ? groups.getFirst().id : null;
	}

	private Land.Claim claim() {
		Land.Claim claim = Land.claimAt(at);
		return claim != null && claim.owner.equals(player.getUniqueId()) ? claim : null;
	}

	private static String[] lore(EnumSet<Land.Perm> perms, String unset) {
		List<String> lines = new ArrayList<>();
		if (perms == null) {
			lines.add(unset);
		} else {
			lines.addAll(LandMenu.permLines(perms));
		}
		lines.add("클릭: 수정");
		return lines.toArray(String[]::new);
	}

	@Override
	void draw() {
		bySlot.clear();
		groupBySlot.clear();
		inventory.setItem(BACK_SLOT, TradeItems.back());
		Land.Claim claim = claim();
		if (claim == null) {
			inventory.setItem(13, TradeItems.button(Material.BARRIER, "내 땅 안에서 열어 주세요", NamedTextColor.GRAY));
			return;
		}
		if (groupId == null) {
			int slot = 0;
			for (Groups.Group group : Groups.of(player.getUniqueId())) {
				Land.GroupLand land = claim.groups.get(group.id);
				boolean set = land != null && !land.empty();
				inventory.setItem(slot, TradeItems.button(Material.CYAN_BANNER, group.name, NamedTextColor.AQUA,
						set ? "이 땅에 설정이 있습니다." : "설정 없음", "클릭: 이 그룹의 권한 정하기"));
				groupBySlot.put(slot, group.id);
				slot++;
			}
			if (slot == 0) {
				inventory.setItem(13, TradeItems.button(Material.BARRIER, "속한 그룹이 없습니다", NamedTextColor.GRAY));
			}
			return;
		}
		Groups.Group group = Groups.member(groupId, player.getUniqueId());
		Land.GroupLand land = Land.groupLand(claim, groupId);
		if (group == null || land == null) {
			inventory.setItem(13, TradeItems.button(Material.BARRIER, "이 그룹에 속해 있지 않습니다", NamedTextColor.GRAY));
			return;
		}
		inventory.setItem(GROUP_SLOT, TradeItems.button(Material.CYAN_BANNER, "그룹 전체 (" + group.name + ")", NamedTextColor.AQUA,
				lore(land.all, "설정 안 함: '모든 사람' 권한을 따릅니다.")));
		int slot = 9;
		for (Groups.Role role : group.roles) {
			inventory.setItem(slot, TradeItems.button(GroupRolesMenu.icon(role), "역할: " + role.name, NamedTextColor.LIGHT_PURPLE,
					lore(land.roles.get(role.id), "설정 안 함: '그룹 전체' 설정을 따릅니다.")));
			bySlot.put(slot, role.id);
			slot++;
		}
	}

	@Override
	void click(int slot, ClickType click) {
		if (slot == BACK_SLOT) {
			clickSound();
			if (groupId != null && Groups.of(player.getUniqueId()).size() > 1) {
				new LandGroupMenu(player, at, null).open();
			} else {
				new LandMenu(player).open();
			}
			return;
		}
		Land.Claim claim = claim();
		if (claim == null) {
			return;
		}
		if (groupId == null) {
			if (groupBySlot.containsKey(slot)) {
				clickSound();
				new LandGroupMenu(player, at, groupBySlot.get(slot)).open();
			}
			return;
		}
		Groups.Group group = Groups.member(groupId, player.getUniqueId());
		Land.GroupLand land = Land.groupLand(claim, groupId);
		if (group == null || land == null) {
			return;
		}
		if (slot == GROUP_SLOT) {
			if (land.all == null) {
				// Starts from what everyone may do.
				land.all = EnumSet.copyOf(claim.everyone);
				Land.save();
			}
			clickSound();
			LandPermissionMenu.group(player, at, groupId).open();
		} else if (bySlot.containsKey(slot)) {
			Groups.Role role = group.role(bySlot.get(slot));
			if (role == null) {
				return;
			}
			if (!land.roles.containsKey(role.id)) {
				land.roles.put(role.id, EnumSet.copyOf(land.all != null ? land.all : claim.everyone));
				Land.save();
			}
			clickSound();
			LandPermissionMenu.role(player, at, groupId, role).open();
		}
	}
}
