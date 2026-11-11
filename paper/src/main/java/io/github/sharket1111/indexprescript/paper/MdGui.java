package io.github.sharket1111.indexprescript.paper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/** A chest menu: items in slots, each with what a click on it does. Nothing can be taken out. */
final class MdGui implements InventoryHolder {
	private final Inventory inventory;
	private final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();
	private Runnable onClose;

	MdGui(int rows, String title) {
		this.inventory = Bukkit.createInventory(this, rows * 9, Component.text(title));
	}

	@Override
	public Inventory getInventory() {
		return inventory;
	}

	MdGui set(int slot, ItemStack item, Consumer<InventoryClickEvent> action) {
		inventory.setItem(slot, item);
		if (action != null) {
			actions.put(slot, action);
		} else {
			actions.remove(slot);
		}
		return this;
	}

	MdGui set(int slot, ItemStack item, Runnable action) {
		return set(slot, item, action == null ? null : e -> action.run());
	}

	MdGui onClose(Runnable action) {
		this.onClose = action;
		return this;
	}

	void open(Player player) {
		player.openInventory(inventory);
	}

	/** Fills empty slots with dark glass. */
	MdGui fill() {
		ItemStack pane = item(Material.BLACK_STAINED_GLASS_PANE, " ", NamedTextColor.DARK_GRAY);
		for (int i = 0; i < inventory.getSize(); i++) {
			if (inventory.getItem(i) == null) {
				inventory.setItem(i, pane);
			}
		}
		return this;
	}

	// --- items --------------------------------------------------------------------------------

	static ItemStack item(Material material, String name, TextColor color, String... lore) {
		List<Component> lines = new ArrayList<>();
		for (String l : lore) {
			for (String part : l.split("\n")) {
				lines.add(Component.text(part, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
			}
		}
		return item(material, Component.text(name, color), lines);
	}

	static ItemStack item(Material material, Component name, List<Component> lore) {
		ItemStack stack = new ItemStack(material);
		stack.editMeta(meta -> {
			meta.displayName(name.decoration(TextDecoration.ITALIC, false));
			meta.lore(lore);
			meta.addItemFlags(ItemFlag.values());
		});
		return stack;
	}

	static ItemStack glow(ItemStack stack) {
		stack.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
		return stack;
	}

	/** Wraps text to lines of about {@code width} characters. */
	static List<String> wrap(String text, int width) {
		List<String> out = new ArrayList<>();
		for (String paragraph : text.split("\n")) {
			StringBuilder line = new StringBuilder();
			for (String word : paragraph.split(" ")) {
				if (line.length() + word.length() + 1 > width && line.length() > 0) {
					out.add(line.toString());
					line.setLength(0);
				}
				if (line.length() > 0) {
					line.append(' ');
				}
				line.append(word);
			}
			if (line.length() > 0) {
				out.add(line.toString());
			}
		}
		return out;
	}

	static final class Events implements Listener {
		@EventHandler
		public void onClick(InventoryClickEvent event) {
			if (!(event.getInventory().getHolder() instanceof MdGui gui)) {
				return;
			}
			event.setCancelled(true);
			if (event.getClickedInventory() != gui.inventory) {
				return;
			}
			Consumer<InventoryClickEvent> action = gui.actions.get(event.getSlot());
			if (action != null) {
				action.accept(event);
			}
		}

		@EventHandler
		public void onDrag(InventoryDragEvent event) {
			if (event.getInventory().getHolder() instanceof MdGui) {
				event.setCancelled(true);
			}
		}

		@EventHandler
		public void onClose(InventoryCloseEvent event) {
			if (event.getInventory().getHolder() instanceof MdGui gui && gui.onClose != null) {
				Runnable action = gui.onClose;
				gui.onClose = null;
				action.run();
			}
		}
	}
}
