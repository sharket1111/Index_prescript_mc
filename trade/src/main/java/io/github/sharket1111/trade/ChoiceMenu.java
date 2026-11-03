package io.github.sharket1111.trade;

import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

/** Picks one of up to 18 options shown as icons; {@code choice} gets its index. */
final class ChoiceMenu extends Menu {
	private final List<ItemStack> icons;
	private final Supplier<Menu> back;
	private final IntConsumer choice;

	ChoiceMenu(Player player, String title, List<ItemStack> icons, Supplier<Menu> back, IntConsumer choice) {
		super(player, title);
		this.icons = icons;
		this.back = back;
		this.choice = choice;
	}

	@Override
	void draw() {
		for (int i = 0; i < icons.size() && i < BACK_SLOT; i++) {
			inventory.setItem(i, icons.get(i));
		}
		inventory.setItem(BACK_SLOT, TradeItems.back());
	}

	@Override
	void click(int slot, ClickType click) {
		if (slot == BACK_SLOT) {
			clickSound();
			back.get().open();
		} else if (slot >= 0 && slot < icons.size() && slot < BACK_SLOT) {
			clickSound();
			choice.accept(slot);
		}
	}
}
