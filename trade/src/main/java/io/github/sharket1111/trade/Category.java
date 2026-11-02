package io.github.sharket1111.trade;

import java.util.Set;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/** Rough item categories for filtering the market. */
enum Category {
	ALL("전체", Material.NETHER_STAR),
	GEAR("무기/도구", Material.IRON_SWORD),
	ARMOR("방어구", Material.IRON_CHESTPLATE),
	BLOCK("블록", Material.BRICKS),
	FOOD("음식", Material.BREAD),
	OTHER("기타", Material.CHEST);

	private static final Set<Material> GEAR_ITEMS = Set.of(Material.BOW, Material.CROSSBOW, Material.TRIDENT, Material.MACE,
			Material.FISHING_ROD, Material.SHEARS, Material.FLINT_AND_STEEL, Material.BRUSH);
	private static final Set<Material> ARMOR_ITEMS = Set.of(Material.ELYTRA, Material.SHIELD, Material.TURTLE_HELMET);

	final String label;
	final Material icon;

	Category(String label, Material icon) {
		this.label = label;
		this.icon = icon;
	}

	Category next() {
		return values()[(ordinal() + 1) % values().length];
	}

	static Category of(ItemStack stack) {
		Material type = stack.getType();
		String name = type.name();
		if (GEAR_ITEMS.contains(type) || name.endsWith("_SWORD") || name.endsWith("_AXE") || name.endsWith("_PICKAXE")
				|| name.endsWith("_SHOVEL") || name.endsWith("_HOE") || name.endsWith("_SPEAR")) {
			return GEAR;
		}
		if (ARMOR_ITEMS.contains(type) || name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") || name.endsWith("_LEGGINGS")
				|| name.endsWith("_BOOTS")) {
			return ARMOR;
		}
		if (type.isEdible()) {
			return FOOD;
		}
		if (type.isBlock()) {
			return BLOCK;
		}
		return OTHER;
	}

	boolean matches(ItemStack stack) {
		return this == ALL || of(stack) == this;
	}
}
