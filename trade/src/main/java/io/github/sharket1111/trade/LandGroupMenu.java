package io.github.sharket1111.trade;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Land permissions for the owner's group, on the chunk they stand in: one
 * setting for every member, and one for each role (which comes first). A
 * member named on their own in the land tab goes by that instead.
 */
final class LandGroupMenu extends Menu {
	private static final int GROUP_SLOT = 0;
	private final Location at;
	private final Map<Integer, String> bySlot = new HashMap<>();

	LandGroupMenu(Player player, Location at) {
		super(player, "거래 - 땅 그룹 권한");
		this.at = at;
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
		inventory.setItem(BACK_SLOT, TradeItems.back());
		Land.Claim claim = claim();
		Groups.Group group = Groups.of(player.getUniqueId());
		if (claim == null || group == null) {
			inventory.setItem(13, TradeItems.button(Material.BARRIER, claim == null ? "내 땅 안에서 열어 주세요" : "속한 그룹이 없습니다",
					NamedTextColor.GRAY));
			return;
		}
		Land.bindGroup(claim);
		inventory.setItem(GROUP_SLOT, TradeItems.button(Material.CYAN_BANNER, "그룹 전체 (" + group.name + ")", NamedTextColor.AQUA,
				lore(claim.groupAll, "설정 안 함: '모든 사람' 권한을 따릅니다.")));
		int slot = 9;
		for (Groups.Role role : group.roles) {
			inventory.setItem(slot, TradeItems.button(GroupRolesMenu.icon(role), "역할: " + role.name, NamedTextColor.LIGHT_PURPLE,
					lore(claim.roles.get(role.id), "설정 안 함: '그룹 전체' 설정을 따릅니다.")));
			bySlot.put(slot, role.id);
			slot++;
		}
	}

	@Override
	void click(int slot, ClickType click) {
		if (slot == BACK_SLOT) {
			clickSound();
			new LandMenu(player).open();
			return;
		}
		Land.Claim claim = claim();
		Groups.Group group = Groups.of(player.getUniqueId());
		if (claim == null || group == null) {
			return;
		}
		Land.bindGroup(claim);
		if (slot == GROUP_SLOT) {
			if (claim.groupAll == null) {
				// Starts from what everyone may do.
				claim.groupAll = EnumSet.copyOf(claim.everyone.isEmpty() ? EnumSet.noneOf(Land.Perm.class) : claim.everyone);
				Land.save();
			}
			clickSound();
			LandPermissionMenu.group(player, at).open();
		} else if (bySlot.containsKey(slot)) {
			Groups.Role role = group.role(bySlot.get(slot));
			if (role == null) {
				return;
			}
			if (!claim.roles.containsKey(role.id)) {
				EnumSet<Land.Perm> start = claim.groupAll != null ? claim.groupAll : claim.everyone;
				claim.roles.put(role.id, start.isEmpty() ? EnumSet.noneOf(Land.Perm.class) : EnumSet.copyOf(start));
				Land.save();
			}
			clickSound();
			LandPermissionMenu.role(player, at, role).open();
		}
	}
}
