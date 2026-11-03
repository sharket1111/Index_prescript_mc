package io.github.sharket1111.trade;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Invitations: those the player received (pick one, then accept or decline)
 * or those their group sent (click to take one back).
 */
final class GroupInvitesMenu extends Menu {
	private static final int ACCEPT_SLOT = 11;
	private static final int DECLINE_SLOT = 15;
	private final boolean sent;
	private final UUID groupId;
	private final Map<Integer, UUID> bySlot = new HashMap<>();
	/** The received invitation being answered, or null while choosing. */
	private UUID chosen;

	/** The invitations {@code groupId}'s group sent, or (null) those the player received. */
	GroupInvitesMenu(Player player, UUID groupId) {
		super(player, groupId != null ? "거래 - 보낸 초대" : "거래 - 받은 초대");
		this.groupId = groupId;
		this.sent = groupId != null;
	}

	private Groups.Group chosenGroup() {
		for (Groups.Group group : Groups.invitesFor(player.getUniqueId())) {
			if (group.id.equals(chosen)) {
				return group;
			}
		}
		return null;
	}

	@Override
	void draw() {
		bySlot.clear();
		inventory.setItem(BACK_SLOT, TradeItems.back());
		if (sent) {
			drawSent();
		} else if (chosen != null && chosenGroup() != null) {
			Groups.Group group = chosenGroup();
			inventory.setItem(4, TradeItems.button(Material.CYAN_BANNER, group.name, NamedTextColor.AQUA,
					"접두사: " + (group.tag.isEmpty() ? "없음" : group.plainTag()), "그룹장: " + group.nameOf(group.leader),
					"그룹원: " + group.memberRoles.size() + "명"));
			inventory.setItem(ACCEPT_SLOT, TradeItems.button(Material.LIME_CONCRETE, "수락", NamedTextColor.GREEN,
					"이 그룹에 들어갑니다. (다른 그룹과 함께 속할 수 있습니다)"));
			inventory.setItem(DECLINE_SLOT, TradeItems.button(Material.RED_CONCRETE, "거절", NamedTextColor.RED, "초대를 지웁니다."));
		} else {
			chosen = null;
			List<Groups.Group> groups = Groups.invitesFor(player.getUniqueId());
			int slot = 0;
			for (Groups.Group group : groups) {
				if (slot >= BACK_SLOT) {
					break;
				}
				inventory.setItem(slot, TradeItems.button(Material.CYAN_BANNER, group.name, NamedTextColor.AQUA,
						"그룹장: " + group.nameOf(group.leader), "그룹원: " + group.memberRoles.size() + "명", "클릭: 수락/거절"));
				bySlot.put(slot, group.id);
				slot++;
			}
			if (groups.isEmpty()) {
				inventory.setItem(13, TradeItems.button(Material.BARRIER, "받은 초대가 없습니다", NamedTextColor.GRAY));
			}
		}
	}

	private void drawSent() {
		Groups.Group group = Groups.member(groupId, player.getUniqueId());
		if (group == null) {
			return;
		}
		boolean can = group.has(player.getUniqueId(), Groups.Perm.INVITE);
		int slot = 0;
		for (UUID id : new ArrayList<>(group.invites)) {
			if (slot >= BACK_SLOT) {
				break;
			}
			String name = Bukkit.getOfflinePlayer(id).getName();
			ItemStack head = ItemStack.of(Material.PLAYER_HEAD);
			head.editMeta(SkullMeta.class, meta -> {
				meta.setOwningPlayer(Bukkit.getOfflinePlayer(id));
				meta.itemName(Component.text(name == null ? "?" : name, NamedTextColor.YELLOW));
				meta.lore(List.of(TradeItems.plain(can ? "클릭: 초대 취소" : "초대 중", NamedTextColor.GRAY)));
			});
			inventory.setItem(slot, head);
			bySlot.put(slot, id);
			slot++;
		}
		if (group.invites.isEmpty()) {
			inventory.setItem(13, TradeItems.button(Material.BARRIER, "보낸 초대가 없습니다", NamedTextColor.GRAY));
		}
	}

	@Override
	void click(int slot, ClickType click) {
		if (slot == BACK_SLOT) {
			clickSound();
			if (!sent && chosen != null) {
				chosen = null;
				redraw();
			} else if (sent) {
				new GroupHomeMenu(player, groupId).open();
			} else {
				new GroupMenu(player).open();
			}
			return;
		}
		if (sent) {
			Groups.Group group = Groups.member(groupId, player.getUniqueId());
			UUID id = bySlot.get(slot);
			if (group != null && id != null && group.has(player.getUniqueId(), Groups.Perm.INVITE)) {
				group.invites.remove(id);
				Groups.save();
				clickSound();
				redraw();
			}
			return;
		}
		if (chosen == null) {
			if (bySlot.containsKey(slot)) {
				chosen = bySlot.get(slot);
				clickSound();
				redraw();
			}
			return;
		}
		Groups.Group group = chosenGroup();
		if (group == null) {
			chosen = null;
			redraw();
			return;
		}
		if (slot == ACCEPT_SLOT) {
			if (group.memberRoles.containsKey(player.getUniqueId())) {
				group.invites.remove(player.getUniqueId());
				Groups.save();
				tell("이미 이 그룹원입니다.", NamedTextColor.RED);
				failSound();
				chosen = null;
				redraw();
				return;
			}
			if (Groups.of(player.getUniqueId()).size() >= Groups.MAX_GROUPS) {
				tell("그룹은 " + Groups.MAX_GROUPS + "개까지 들어갈 수 있습니다.", NamedTextColor.RED);
				failSound();
				return;
			}
			if (group.memberRoles.size() >= Groups.MAX_MEMBERS) {
				tell("그룹 인원이 가득 찼습니다.", NamedTextColor.RED);
				failSound();
				return;
			}
			Groups.addMember(group, player.getUniqueId(), player.getName(), Groups.MEMBER);
			Groups.save();
			Groups.refreshName(player.getUniqueId());
			TradeTools.offerWaypointBook(player);
			tell("그룹 '" + group.name + "'에 들어왔습니다.", NamedTextColor.GREEN);
			successSound();
			for (UUID member : group.memberRoles.keySet()) {
				Player online = Bukkit.getPlayer(member);
				if (online != null && online != player) {
					online.sendMessage(Component.text(player.getName() + "님이 그룹에 들어왔습니다.", NamedTextColor.GREEN));
				}
			}
			new GroupHomeMenu(player, group.id).open();
		} else if (slot == DECLINE_SLOT) {
			group.invites.remove(player.getUniqueId());
			Groups.save();
			tell("그룹 '" + group.name + "'의 초대를 거절했습니다.", NamedTextColor.YELLOW);
			clickSound();
			chosen = null;
			redraw();
		}
	}
}
