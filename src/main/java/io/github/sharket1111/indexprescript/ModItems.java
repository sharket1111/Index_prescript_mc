package io.github.sharket1111.indexprescript;

import java.util.function.Function;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;

public final class ModItems {
	// Ruen's blade: netherite-tier sword, slightly faster than vanilla.
	public static final Item RUEN_WEAPON = register("ruen_weapon", Item::new,
			new Item.Properties().sword(ToolMaterial.NETHERITE, 4.0F, -2.2F).rarity(Rarity.EPIC));

	// Terminal that receives prescripts from the Index.
	public static final Item RUEN_TERMINAL = register("ruen_terminal", TerminalItem::new,
			new Item.Properties().stacksTo(1).rarity(Rarity.RARE));

	private ModItems() {
	}

	private static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(IndexPrescript.MOD_ID, name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	static void init() {
		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.COMBAT).register(entries -> {
			entries.accept(RUEN_WEAPON);
			entries.accept(RUEN_TERMINAL);
		});
	}
}
