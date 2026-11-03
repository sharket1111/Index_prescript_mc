package io.github.sharket1111.trade;

import java.util.EnumSet;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Turning permissions on the owner's chunk on and off for one of: everyone
 * not otherwise listed, one player, every member of the owner's group, or
 * everyone with one role in it.
 */
final class LandPermissionMenu extends Menu {
	enum Kind {
		EVERYONE, PLAYER, GROUP, ROLE
	}

	private static final int[] PERM_SLOTS = {11, 13, 15};
	private static final int REMOVE_SLOT = 26;
	private final Location at;
	private final Kind kind;
	private final UUID target;
	private final UUID groupId;
	private final String roleId;
	private final String name;

	private LandPermissionMenu(Player player, Location at, Kind kind, UUID target, UUID groupId, String roleId, String name) {
		super(player, "거래 - 땅 권한: " + name);
		this.at = at;
		this.kind = kind;
		this.target = target;
		this.groupId = groupId;
		this.roleId = roleId;
		this.name = name;
	}

	/** One player's permissions, or everyone's if {@code target} is null. */
	LandPermissionMenu(Player player, Location at, UUID target, String name) {
		this(player, at, target == null ? Kind.EVERYONE : Kind.PLAYER, target, null, null, name);
	}

	static LandPermissionMenu group(Player player, Location at, UUID groupId) {
		Groups.Group group = Groups.byId(groupId);
		return new LandPermissionMenu(player, at, Kind.GROUP, null, groupId, null, (group == null ? "?" : group.name) + " 전체");
	}

	static LandPermissionMenu role(Player player, Location at, UUID groupId, Groups.Role role) {
		return new LandPermissionMenu(player, at, Kind.ROLE, null, groupId, role.id, "역할 " + role.name);
	}

	private Land.GroupLand land(Land.Claim claim) {
		return Groups.member(groupId, claim.owner) == null ? null : claim.groups.get(groupId);
	}

	private Land.Claim claim() {
		Land.Claim claim = Land.claimAt(at);
		return claim != null && claim.owner.equals(player.getUniqueId()) ? claim : null;
	}

	private EnumSet<Land.Perm> perms(Land.Claim claim) {
		return switch (kind) {
			case EVERYONE -> claim.everyone;
			case PLAYER -> claim.members.get(target);
			case GROUP -> land(claim) != null ? land(claim).all : null;
			case ROLE -> land(claim) != null ? land(claim).roles.get(roleId) : null;
		};
	}

	private Menu back() {
		return kind == Kind.GROUP || kind == Kind.ROLE ? new LandGroupMenu(player, at, groupId) : new LandMenu(player);
	}

	@Override
	void draw() {
		Land.Claim claim = claim();
		inventory.setItem(BACK_SLOT, TradeItems.back());
		if (claim == null || perms(claim) == null) {
			inventory.setItem(13, TradeItems.button(Material.BARRIER, "더 이상 관리할 수 없습니다", NamedTextColor.GRAY));
			return;
		}
		EnumSet<Land.Perm> perms = perms(claim);
		String about = switch (kind) {
			case EVERYONE -> "목록에 없는 모든 플레이어에게 적용됩니다.";
			case PLAYER -> "이 플레이어에게만 적용됩니다. (그룹 설정보다 먼저)";
			case GROUP -> "내 그룹의 모든 그룹원에게 적용됩니다. (역할별 설정이 먼저)";
			case ROLE -> "내 그룹에서 이 역할인 그룹원 모두에게 적용됩니다.";
		};
		Material icon = switch (kind) {
			case EVERYONE -> Material.OAK_SIGN;
			case PLAYER -> Material.PLAYER_HEAD;
			case GROUP -> Material.CYAN_BANNER;
			case ROLE -> Material.NAME_TAG;
		};
		inventory.setItem(4, TradeItems.button(icon, name, NamedTextColor.YELLOW, about));
		Land.Perm[] all = Land.Perm.values();
		String[] help = {"블록 설치, 물/용암 붓기, 불 붙이기", "블록 부수기, 양동이로 뜨기, 액자/동물 공격",
				"문, 상자, 버튼, 레버, 주민 등 사용"};
		for (int i = 0; i < all.length; i++) {
			boolean on = perms.contains(all[i]);
			inventory.setItem(PERM_SLOTS[i], TradeItems.button(on ? Material.LIME_WOOL : Material.RED_WOOL,
					all[i].label + ": " + (on ? "허용" : "금지"), on ? NamedTextColor.GREEN : NamedTextColor.RED, help[i], "클릭: 바꾸기"));
		}
		if (kind == Kind.PLAYER) {
			inventory.setItem(REMOVE_SLOT, TradeItems.button(Material.BARRIER, "목록에서 빼기", NamedTextColor.RED,
					"이 플레이어는 그룹 또는 '모든 사람' 권한을 따릅니다."));
		} else if (kind == Kind.GROUP) {
			inventory.setItem(REMOVE_SLOT, TradeItems.button(Material.BARRIER, "그룹 설정 지우기", NamedTextColor.RED,
					"그룹원은 '모든 사람' 권한을 따릅니다. (역할별 설정은 남음)"));
		} else if (kind == Kind.ROLE) {
			inventory.setItem(REMOVE_SLOT, TradeItems.button(Material.BARRIER, "역할 설정 지우기", NamedTextColor.RED,
					"이 역할은 '그룹 전체' 설정을 따릅니다."));
		}
	}

	@Override
	void click(int slot, ClickType click) {
		if (slot == BACK_SLOT) {
			clickSound();
			back().open();
			return;
		}
		Land.Claim claim = claim();
		if (claim == null || perms(claim) == null) {
			return;
		}
		if (slot == REMOVE_SLOT && kind != Kind.EVERYONE) {
			switch (kind) {
				case PLAYER -> {
					claim.members.remove(target);
					claim.memberNames.remove(target);
				}
				case GROUP -> land(claim).all = null;
				case ROLE -> land(claim).roles.remove(roleId);
				default -> {
				}
			}
			Land.save();
			successSound();
			back().open();
			return;
		}
		Land.Perm[] all = Land.Perm.values();
		for (int i = 0; i < PERM_SLOTS.length; i++) {
			if (slot == PERM_SLOTS[i]) {
				EnumSet<Land.Perm> perms = perms(claim);
				if (!perms.remove(all[i])) {
					perms.add(all[i]);
				}
				Land.save();
				clickSound();
				redraw();
				return;
			}
		}
	}
}
