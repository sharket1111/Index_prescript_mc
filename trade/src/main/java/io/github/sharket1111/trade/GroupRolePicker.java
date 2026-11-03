package io.github.sharket1111.trade;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import net.kyori.adventure.text.format.NamedTextColor;

/** Picks one of the player's group's roles (those {@code allowed} lets through). */
final class GroupRolePicker extends Menu {
	private final java.util.UUID groupId;
	private final Supplier<Menu> back;
	private final Predicate<Groups.Role> allowed;
	private final Consumer<Groups.Role> choice;
	private final Map<Integer, String> bySlot = new HashMap<>();

	GroupRolePicker(Player player, java.util.UUID groupId, String title, Supplier<Menu> back, Predicate<Groups.Role> allowed,
			Consumer<Groups.Role> choice) {
		super(player, title);
		this.groupId = groupId;
		this.back = back;
		this.allowed = allowed;
		this.choice = choice;
	}

	@Override
	void draw() {
		bySlot.clear();
		inventory.setItem(BACK_SLOT, TradeItems.back());
		Groups.Group group = Groups.member(groupId, player.getUniqueId());
		if (group == null) {
			return;
		}
		int slot = 0;
		for (Groups.Role role : group.roles) {
			boolean ok = allowed.test(role);
			long members = group.memberRoles.values().stream().filter(role.id::equals).count();
			inventory.setItem(slot, TradeItems.button(ok ? GroupRolesMenu.icon(role) : Material.GRAY_DYE, role.name,
					ok ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.GRAY, "그룹원 " + members + "명", ok ? "클릭: 고르기" : "고를 수 없습니다"));
			if (ok) {
				bySlot.put(slot, role.id);
			}
			slot++;
		}
	}

	@Override
	void click(int slot, ClickType click) {
		if (slot == BACK_SLOT) {
			clickSound();
			back.get().open();
			return;
		}
		Groups.Group group = Groups.member(groupId, player.getUniqueId());
		String id = bySlot.get(slot);
		Groups.Role role = group == null || id == null ? null : group.role(id);
		if (role != null) {
			clickSound();
			choice.accept(role);
		}
	}
}
