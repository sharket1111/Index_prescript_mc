package io.github.sharket1111.trade;

import java.util.EnumSet;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import net.kyori.adventure.text.format.NamedTextColor;

/** Turning one player's (or everyone's) permissions on the owner's chunk on and off. */
final class LandPermissionMenu extends Menu {
	private static final int[] PERM_SLOTS = {10, 12, 14, 16};
	private static final int REMOVE_SLOT = 26;
	private final Location at;
	/** The player, or null for everyone not named. */
	private final UUID target;
	private final String name;

	LandPermissionMenu(Player player, Location at, UUID target, String name) {
		super(player, "거래 - 땅 권한: " + name);
		this.at = at;
		this.target = target;
		this.name = name;
	}

	private Land.Claim claim() {
		Land.Claim claim = Land.claimAt(at);
		return claim != null && claim.owner.equals(player.getUniqueId()) ? claim : null;
	}

	private EnumSet<Land.Perm> perms(Land.Claim claim) {
		return target == null ? claim.everyone : claim.members.get(target);
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
		inventory.setItem(4, TradeItems.button(target == null ? Material.OAK_SIGN : Material.PLAYER_HEAD, name, NamedTextColor.YELLOW,
				target == null ? "목록에 없는 모든 플레이어에게 적용됩니다." : "이 플레이어에게만 적용됩니다."));
		Land.Perm[] all = Land.Perm.values();
		String[] help = {"블록 설치, 물/용암 붓기, 불 붙이기", "블록 부수기, 양동이로 뜨기, 액자/동물 공격", "이 청크에 들어오기",
				"문, 상자, 버튼, 레버, 주민 등 사용"};
		for (int i = 0; i < all.length; i++) {
			boolean on = perms.contains(all[i]);
			inventory.setItem(PERM_SLOTS[i], TradeItems.button(on ? Material.LIME_WOOL : Material.RED_WOOL,
					all[i].label + ": " + (on ? "허용" : "금지"), on ? NamedTextColor.GREEN : NamedTextColor.RED, help[i], "클릭: 바꾸기"));
		}
		if (target != null) {
			inventory.setItem(REMOVE_SLOT, TradeItems.button(Material.BARRIER, "목록에서 빼기", NamedTextColor.RED,
					"이 플레이어는 '모든 사람' 권한을 따릅니다."));
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
		if (claim == null || perms(claim) == null) {
			return;
		}
		if (slot == REMOVE_SLOT && target != null) {
			claim.members.remove(target);
			claim.memberNames.remove(target);
			Land.save();
			successSound();
			new LandMenu(player).open();
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
