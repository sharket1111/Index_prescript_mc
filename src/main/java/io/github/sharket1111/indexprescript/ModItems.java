package io.github.sharket1111.indexprescript;

import java.util.function.Function;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
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
	// Rien's Caduceus. Base stats are the bare brass rod; each formed weapon adds its own bonus.
	public static final Item CADUCEUS = register("caduceus", CaduceusItem::new,
			new Item.Properties().sword(ToolMaterial.NETHERITE, 1.0F, -2.4F).rarity(Rarity.EPIC));

	// Terminal that receives prescripts from the Index.
	public static final Item RIEN_TERMINAL = register("rien_terminal", TerminalItem::new,
			new Item.Properties().stacksTo(1).rarity(Rarity.RARE));

	private ModItems() {
	}

	private static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(IndexPrescript.MOD_ID, name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	static void init() {
		AttackEntityCallback.EVENT.register(CaduceusItem::onAttack);
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> {
			output.accept(CADUCEUS);
			output.accept(RIEN_TERMINAL);
		});
	}
}
