package io.github.sharket1111.trade;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** The player's group members as heads, highest role first; picking one hands it to {@code choice}. */
final class GroupMemberPicker extends Menu {
	private static final int BACK = 45;
	private final boolean includeSelf;
	private final Supplier<Menu> back;
	private final Consumer<UUID> choice;
	private final Map<Integer, UUID> bySlot = new HashMap<>();

	GroupMemberPicker(Player player, String title, Supplier<Menu> back, Consumer<UUID> choice) {
		this(player, title, false, back, choice);
	}

	private GroupMemberPicker(Player player, String title, boolean includeSelf, Supplier<Menu> back, Consumer<UUID> choice) {
		super(player, title, 6);
		this.includeSelf = includeSelf;
		this.back = back;
		this.choice = choice;
	}

	/** The member list of the group tab: clicking a member opens what can be done to them. */
	static GroupMemberPicker members(Player player) {
		return new GroupMemberPicker(player, "거래 - 그룹원", true, () -> new GroupMenu(player),
				id -> new GroupMemberMenu(player, id).open());
	}

	@Override
	void draw() {
		bySlot.clear();
		inventory.setItem(BACK, TradeItems.back());
		Groups.Group group = Groups.of(player.getUniqueId());
		if (group == null) {
			return;
		}
		List<UUID> members = new ArrayList<>(group.memberRoles.keySet());
		if (!includeSelf) {
			members.remove(player.getUniqueId());
		}
		members.sort(Comparator.comparingInt(group::rankOf).thenComparing(id -> group.nameOf(id).toLowerCase(java.util.Locale.ROOT)));
		int slot = 0;
		for (UUID id : members) {
			if (slot >= BACK) {
				break;
			}
			Groups.Role role = group.roleOf(id);
			boolean online = Bukkit.getPlayer(id) != null;
			ItemStack head = ItemStack.of(Material.PLAYER_HEAD);
			head.editMeta(SkullMeta.class, meta -> {
				meta.setOwningPlayer(Bukkit.getOfflinePlayer(id));
				meta.itemName(Component.text(group.nameOf(id), id.equals(group.leader) ? NamedTextColor.GOLD : NamedTextColor.YELLOW));
				meta.lore(List.of(TradeItems.plain("역할: " + role.name, NamedTextColor.AQUA),
						TradeItems.plain(online ? "접속 중" : "접속 안 함", online ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY)));
			});
			inventory.setItem(slot, head);
			bySlot.put(slot, id);
			slot++;
		}
	}

	@Override
	void click(int slot, ClickType click) {
		if (slot == BACK) {
			clickSound();
			back.get().open();
		} else if (bySlot.containsKey(slot)) {
			clickSound();
			choice.accept(bySlot.get(slot));
		}
	}
}
