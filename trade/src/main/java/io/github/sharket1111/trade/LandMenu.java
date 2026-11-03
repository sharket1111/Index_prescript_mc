package io.github.sharket1111.trade;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * The land tab, about the chunk the player stands in. On their own land:
 * the defaults for everyone and each named player's permissions, adding
 * players, and showing the border. Elsewhere: who owns it or why it cannot
 * be bought.
 */
final class LandMenu extends Menu {
	private static final int EVERYONE_SLOT = 0;
	private static final int BORDER_SLOT = 20;
	private static final int INFO_SLOT = 22;
	private static final int ADD_SLOT = 24;
	private final Location at;
	private final Map<Integer, UUID> bySlot = new HashMap<>();

	LandMenu(Player player) {
		super(player, "거래 - 내 땅");
		this.at = player.getLocation();
	}

	private Land.Claim ownClaim() {
		Land.Claim claim = Land.claimAt(at);
		return claim != null && claim.owner.equals(player.getUniqueId()) ? claim : null;
	}

	static List<String> permLines(java.util.Set<Land.Perm> perms) {
		List<String> lines = new ArrayList<>();
		for (Land.Perm perm : Land.Perm.values()) {
			lines.add((perms.contains(perm) ? "✔ " : "✘ ") + perm.label);
		}
		return lines;
	}

	@Override
	void draw() {
		bySlot.clear();
		int cx = at.getBlockX() >> 4;
		int cz = at.getBlockZ() >> 4;
		String where = at.getWorld().getName() + " 청크 " + cx + ", " + cz;
		Land.Claim claim = ownClaim();
		inventory.setItem(BACK_SLOT, TradeItems.back());
		if (claim == null) {
			Land.Claim other = Land.claimAt(at);
			String status;
			if (Land.zoneAt(at)) {
				status = "보호구역입니다.";
			} else if (other != null) {
				status = other.ownerName + "님의 땅입니다.";
			} else if (Land.nearSpawn(at)) {
				status = "스폰 근처라 살 수 없는 구역입니다.";
			} else {
				status = "주인이 없는 땅입니다. 서버 상점에서 '땅 소유권'을 사면 가질 수 있습니다.";
			}
			inventory.setItem(13, TradeItems.button(Material.GRASS_BLOCK, where, NamedTextColor.YELLOW, status,
					"내 땅: " + Land.claimsOf(player.getUniqueId()) + "청크",
					"내 땅 안에 서서 열면 권한을 관리할 수 있습니다."));
			inventory.setItem(BORDER_SLOT, TradeItems.button(Material.SPYGLASS, "청크 경계 보기", NamedTextColor.WHITE, "10초 동안 보여 줍니다."));
			return;
		}
		List<String> everyone = new ArrayList<>(List.of("목록에 없는 모든 플레이어의 권한", ""));
		everyone.addAll(permLines(claim.everyone));
		everyone.add("클릭: 수정");
		inventory.setItem(EVERYONE_SLOT, TradeItems.button(Material.OAK_SIGN, "모든 사람 (기본)", NamedTextColor.GOLD,
				everyone.toArray(String[]::new)));
		int slot = 1;
		for (Map.Entry<UUID, java.util.EnumSet<Land.Perm>> entry : claim.members.entrySet()) {
			if (slot > Land.MAX_MEMBERS) {
				break;
			}
			UUID id = entry.getKey();
			String name = claim.memberNames.getOrDefault(id, "?");
			ItemStack head = ItemStack.of(Material.PLAYER_HEAD);
			List<Component> lore = new ArrayList<>();
			for (String line : permLines(entry.getValue())) {
				lore.add(TradeItems.plain(line, NamedTextColor.GRAY));
			}
			lore.add(TradeItems.plain("클릭: 수정", NamedTextColor.DARK_GRAY));
			head.editMeta(SkullMeta.class, meta -> {
				meta.setOwningPlayer(Bukkit.getOfflinePlayer(id));
				meta.itemName(Component.text(name, NamedTextColor.YELLOW));
				meta.lore(lore);
			});
			inventory.setItem(slot, head);
			bySlot.put(slot, id);
			slot++;
		}
		inventory.setItem(BORDER_SLOT, TradeItems.button(Material.SPYGLASS, "청크 경계 보기", NamedTextColor.WHITE, "10초 동안 보여 줍니다."));
		inventory.setItem(INFO_SLOT, TradeItems.button(Material.GRASS_BLOCK, "내 땅: " + where, NamedTextColor.GREEN,
				"권한을 받은 플레이어: " + claim.members.size() + "/" + Land.MAX_MEMBERS + "명",
				"가진 땅: " + Land.claimsOf(player.getUniqueId()) + "청크"));
		inventory.setItem(ADD_SLOT, TradeItems.button(Material.EMERALD, "플레이어 추가", NamedTextColor.GREEN,
				"플레이어를 골라 이 땅의 권한을 따로 정합니다."));
	}

	@Override
	void click(int slot, ClickType click) {
		if (slot == BACK_SLOT) {
			clickSound();
			new MainMenu(player).open();
			return;
		}
		if (slot == BORDER_SLOT) {
			player.closeInventory();
			Land.showBorder(player, at, ownClaim() != null ? Color.LIME : Color.ORANGE);
			return;
		}
		Land.Claim claim = ownClaim();
		if (claim == null) {
			return;
		}
		if (slot == EVERYONE_SLOT) {
			clickSound();
			new LandPermissionMenu(player, at, null, "모든 사람").open();
		} else if (slot == ADD_SLOT) {
			if (claim.members.size() >= Land.MAX_MEMBERS) {
				tell("권한을 따로 줄 수 있는 플레이어는 " + Land.MAX_MEMBERS + "명까지입니다.", NamedTextColor.RED);
				failSound();
				return;
			}
			clickSound();
			new PlayerPicker(player, "거래 - 권한을 줄 플레이어", false, false, 0, () -> new LandMenu(player), (id, name) -> {
				Land.Claim current = Land.claimAt(at);
				if (current != null && current.owner.equals(player.getUniqueId())) {
					current.members.putIfAbsent(id, java.util.EnumSet.of(Land.Perm.ACCESS));
					current.memberNames.put(id, name);
					Land.save();
				}
				new LandPermissionMenu(player, at, id, name).open();
			}).open();
		} else if (bySlot.containsKey(slot)) {
			clickSound();
			UUID id = bySlot.get(slot);
			new LandPermissionMenu(player, at, id, claim.memberNames.getOrDefault(id, "?")).open();
		}
	}
}
