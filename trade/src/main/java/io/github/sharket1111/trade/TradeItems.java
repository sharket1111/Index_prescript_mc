package io.github.sharket1111.trade;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/** Items of the trade menu, and the trade diamond players get from it. */
final class TradeItems {
	private static NamespacedKey tradeDiamondKey;

	private TradeItems() {
	}

	static void init(Plugin plugin) {
		tradeDiamondKey = new NamespacedKey(plugin, "trade_diamond");
	}

	private static Component plain(String text, NamedTextColor color) {
		return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
	}

	/** A diamond for trading between players: tagged, and enchanted with Mending. */
	static ItemStack tradeDiamond() {
		ItemStack stack = ItemStack.of(Material.DIAMOND);
		stack.editMeta(meta -> {
			meta.itemName(Component.text("거래 전용 다이아", NamedTextColor.AQUA));
			meta.lore(List.of(plain("플레이어끼리 거래할 때 쓰는 다이아몬드", NamedTextColor.GRAY)));
			meta.addEnchant(Enchantment.MENDING, 1, true);
			meta.getPersistentDataContainer().set(tradeDiamondKey, PersistentDataType.BYTE, (byte) 1);
		});
		return stack;
	}

	static boolean isTradeDiamond(ItemStack stack) {
		return stack != null && stack.getType() == Material.DIAMOND && stack.hasItemMeta()
				&& stack.getItemMeta().getPersistentDataContainer().has(tradeDiamondKey, PersistentDataType.BYTE);
	}

	/** A plain vanilla diamond: no name, enchantment or tag. */
	static boolean isPlainDiamond(ItemStack stack) {
		return stack != null && stack.isSimilar(ItemStack.of(Material.DIAMOND));
	}

	/** The diamond button in the menu; its stack size shows the chosen amount. */
	static ItemStack exchangeButton(int amount) {
		ItemStack stack = ItemStack.of(Material.DIAMOND, amount);
		stack.editMeta(meta -> {
			meta.itemName(Component.text("거래 전용 다이아로 바꾸기 (" + amount + "개)", NamedTextColor.AQUA));
			meta.lore(List.of(
					plain("우클릭: 인벤토리의 다이아몬드 " + amount + "개를", NamedTextColor.GRAY),
					plain("거래 전용 다이아로 바꿉니다.", NamedTextColor.GRAY),
					plain("양옆의 버튼으로 개수를 정합니다.", NamedTextColor.DARK_GRAY)));
		});
		return stack;
	}

	/** A button that changes the amount by {@code delta}. */
	static ItemStack amountButton(int delta, int amount) {
		ItemStack stack = ItemStack.of(delta < 0 ? Material.RED_STAINED_GLASS_PANE : Material.LIME_STAINED_GLASS_PANE);
		stack.editMeta(meta -> {
			meta.itemName(Component.text((delta > 0 ? "+" : "") + delta, delta < 0 ? NamedTextColor.RED : NamedTextColor.GREEN));
			meta.lore(List.of(plain("현재 개수: " + amount + "개", NamedTextColor.GRAY)));
		});
		return stack;
	}

	/** The button that exchanges every plain diamond at once. */
	static ItemStack exchangeAllButton() {
		ItemStack stack = ItemStack.of(Material.DIAMOND_BLOCK);
		stack.editMeta(meta -> {
			meta.itemName(Component.text("전부 바꾸기", NamedTextColor.AQUA));
			meta.lore(List.of(
					plain("우클릭: 인벤토리의 다이아몬드를 모두", NamedTextColor.GRAY),
					plain("거래 전용 다이아로 바꿉니다.", NamedTextColor.GRAY)));
		});
		return stack;
	}
}
