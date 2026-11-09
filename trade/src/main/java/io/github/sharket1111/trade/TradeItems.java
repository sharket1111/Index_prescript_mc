package io.github.sharket1111.trade;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/** Helpers for menu buttons, and the old trade diamond (now converted to money). */
final class TradeItems {
	private static NamespacedKey tradeDiamondKey;

	private TradeItems() {
	}

	static void init(Plugin plugin) {
		tradeDiamondKey = new NamespacedKey(plugin, "trade_diamond");
	}

	static Component plain(String text, NamedTextColor color) {
		return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
	}

	/** The old trade diamond (replaced by {@link Money}): still recognised so it can be turned into money. */
	static boolean isTradeDiamond(ItemStack stack) {
		return stack != null && stack.getType() == Material.DIAMOND && stack.hasItemMeta()
				&& stack.getItemMeta().getPersistentDataContainer().has(tradeDiamondKey, PersistentDataType.BYTE);
	}

	/** A menu button with a name and grey description lines. */
	static ItemStack button(Material material, int amount, String name, NamedTextColor color, String... lore) {
		ItemStack stack = ItemStack.of(material, Math.max(1, Math.min(material.getMaxStackSize(), amount)));
		stack.editMeta(meta -> {
			meta.itemName(Component.text(name, color));
			List<Component> lines = new ArrayList<>();
			for (String line : lore) {
				lines.add(plain(line, NamedTextColor.GRAY));
			}
			meta.lore(lines);
			meta.addItemFlags(ItemFlag.values());
		});
		return stack;
	}

	static ItemStack button(Material material, String name, NamedTextColor color, String... lore) {
		return button(material, 1, name, color, lore);
	}

	/** Adds the enchantment glint to a button. */
	static ItemStack glint(ItemStack stack) {
		stack.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
		return stack;
	}

	static ItemStack back() {
		return button(Material.ARROW, "뒤로가기", NamedTextColor.WHITE);
	}

	/** A button that changes an amount by {@code delta}. */
	static ItemStack stepButton(int delta, String current) {
		return button(delta < 0 ? Material.RED_STAINED_GLASS_PANE : Material.LIME_STAINED_GLASS_PANE,
				(delta > 0 ? "+" : "") + delta, delta < 0 ? NamedTextColor.RED : NamedTextColor.GREEN, current);
	}
}
