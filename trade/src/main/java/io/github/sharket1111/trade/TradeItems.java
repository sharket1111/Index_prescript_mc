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

	/** The diamond button in the menu. */
	static ItemStack exchangeButton() {
		ItemStack stack = ItemStack.of(Material.DIAMOND);
		stack.editMeta(meta -> {
			meta.itemName(Component.text("거래 전용 다이아로 바꾸기", NamedTextColor.AQUA));
			meta.lore(List.of(
					plain("우클릭: 인벤토리의 다이아몬드 1개를", NamedTextColor.GRAY),
					plain("거래 전용 다이아 1개로 바꿉니다.", NamedTextColor.GRAY)));
		});
		return stack;
	}
}
