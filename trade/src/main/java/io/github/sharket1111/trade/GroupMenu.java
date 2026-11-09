package io.github.sharket1111.trade;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import net.kyori.adventure.text.format.NamedTextColor;

/**
 * The group tab: the player's groups (up to {@link Groups#MAX_GROUPS}) to open
 * one, founding a new one (for trade diamonds), invitations received, and
 * which group's tag goes before the player's name.
 */
final class GroupMenu extends Menu {
	private static final int CREATE_SLOT = 20;
	private static final int RECEIVED_SLOT = 22;
	private static final int TAG_SLOT = 24;
	private final Map<Integer, UUID> bySlot = new HashMap<>();

	GroupMenu(Player player) {
		super(player, "거래 - 내 그룹");
	}

	@Override
	void draw() {
		bySlot.clear();
		inventory.setItem(BACK_SLOT, TradeItems.back());
		UUID me = player.getUniqueId();
		List<Groups.Group> groups = Groups.of(me);
		Groups.Group shown = Groups.shownTag(me);
		int slot = 0;
		for (Groups.Group group : groups) {
			if (slot >= 9) {
				break;
			}
			inventory.setItem(slot, TradeItems.button(group == shown ? Material.CYAN_BANNER : Material.LIGHT_BLUE_BANNER, group.name,
					NamedTextColor.AQUA, "접두사: " + (group.tag.isEmpty() ? "없음" : group.plainTag()) + (group == shown ? " (표시 중)" : ""),
					"내 역할: " + group.roleOf(me).name, "그룹원: " + group.memberRoles.size() + "명", "클릭: 열기"));
			bySlot.put(slot, group.id);
			slot++;
		}
		if (groups.isEmpty()) {
			inventory.setItem(4, TradeItems.button(Material.WHITE_BANNER, "속한 그룹이 없습니다", NamedTextColor.GRAY,
					"그룹을 만들거나, 초대를 받아 들어갈 수 있습니다.", "그룹은 " + Groups.MAX_GROUPS + "개까지 들어갈 수 있습니다."));
		}
		inventory.setItem(CREATE_SLOT, TradeItems.glint(TradeItems.button(Material.EMERALD, "그룹 만들기", NamedTextColor.GREEN,
				Money.format(Groups.PRICE) + "이(가) 듭니다.", "그룹 이름을 채팅으로 정합니다.",
				"내 " + Money.name() + ": " + Money.format(Money.balance(me)),
				"속한 그룹: " + groups.size() + "/" + Groups.MAX_GROUPS)));
		int invites = Groups.invitesFor(me).size();
		inventory.setItem(RECEIVED_SLOT, TradeItems.button(invites > 0 ? Material.WRITABLE_BOOK : Material.BOOK,
				"받은 초대 (" + invites + ")", invites > 0 ? NamedTextColor.GOLD : NamedTextColor.GRAY, "그룹 초대를 수락하거나 거절합니다."));
		inventory.setItem(TAG_SLOT, TradeItems.button(Material.GOLDEN_HELMET, "내 이름 앞 접두사", NamedTextColor.GOLD,
				"지금: " + (shown == null ? "없음" : shown.plainTag() + player.getName()),
				"내 그룹 중 어느 그룹의 접두사를 달지 고릅니다."));
	}

	@Override
	void click(int slot, ClickType click) {
		if (slot == BACK_SLOT) {
			clickSound();
			new MainMenu(player).open();
		} else if (bySlot.containsKey(slot)) {
			clickSound();
			new GroupHomeMenu(player, bySlot.get(slot)).open();
		} else if (slot == CREATE_SLOT) {
			create();
		} else if (slot == RECEIVED_SLOT) {
			clickSound();
			new GroupInvitesMenu(player, null).open();
		} else if (slot == TAG_SLOT) {
			clickSound();
			new GroupTagMenu(player).open();
		}
	}

	private String whyNotCreate() {
		if (Groups.of(player.getUniqueId()).size() >= Groups.MAX_GROUPS) {
			return "그룹은 " + Groups.MAX_GROUPS + "개까지 들어갈 수 있습니다.";
		}
		if (!Money.has(player.getUniqueId(), Groups.PRICE)) {
			return "그룹을 만들려면 " + Money.format(Groups.PRICE) + "이(가) 필요합니다.";
		}
		return null;
	}

	private void create() {
		String why = whyNotCreate();
		if (why != null) {
			tell(why, NamedTextColor.RED);
			failSound();
			return;
		}
		ChatPrompt.ask(player, "새 그룹의 이름을 입력하세요. (2~16글자)", name -> {
			String problem = Groups.badName(name, null);
			if (problem == null) {
				problem = whyNotCreate();
			}
			if (problem != null) {
				tell(problem, NamedTextColor.RED);
				failSound();
				new GroupMenu(player).open();
				return;
			}
			if (!Money.take(player.getUniqueId(), Groups.PRICE)) {
				tell(Money.subject() + " 부족합니다.", NamedTextColor.RED);
				failSound();
				new GroupMenu(player).open();
				return;
			}
			Groups.Group group = Groups.create(player, name);
			tell("그룹 '" + name + "'을(를) 만들었습니다. " + Money.format(Groups.PRICE) + "을(를) 냈습니다.", NamedTextColor.GREEN);
			successSound();
			new GroupHomeMenu(player, group.id).open();
		}, () -> new GroupMenu(player).open());
	}
}
