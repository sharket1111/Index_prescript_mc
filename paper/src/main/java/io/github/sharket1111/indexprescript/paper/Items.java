package io.github.sharket1111.indexprescript.paper;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataType;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Caduceus and the terminal are vanilla items (so unmodded Java and Bedrock
 * clients can join) tagged in their persistent data and drawn by the
 * resource pack through the item_model component. The Caduceus form is the
 * first custom model data string, which the pack's model definition selects on.
 */
final class Items {
	static final String NAMESPACE = "index_prescript";
	static final String CADUCEUS = "caduceus";
	static final String TERMINAL = "rien_terminal";
	static final String LAEVATEIN = "laevatein";
	/** Laevatein release stages after the sealed state; the index is the stage number. */
	static final List<String> LAEVATEIN_STAGES = List.of("sealed", "release_1", "release_2", "release_3");
	static final int MAX_HERMES = 9;

	private static final NamespacedKey ID = new NamespacedKey(NAMESPACE, "item");

	private Items() {
	}

	static ItemStack caduceus(CommandSender viewer) {
		ItemStack stack = ItemStack.of(Material.IRON_SWORD);
		stack.editMeta(meta -> {
			tag(meta, CADUCEUS);
			meta.itemName(Component.text(Messages.get(viewer, "item.caduceus")));
			meta.lore(List.of(Component.text(Messages.get(viewer, "item.caduceus.desc"), NamedTextColor.GRAY)
					.decoration(TextDecoration.ITALIC, false)));
			meta.setRarity(ItemRarity.EPIC);
			meta.setUnbreakable(true);
		});
		return stack;
	}

	static ItemStack terminal(CommandSender viewer) {
		ItemStack stack = ItemStack.of(Material.PAPER);
		stack.editMeta(meta -> {
			tag(meta, TERMINAL);
			meta.itemName(Component.text(Messages.get(viewer, "item.rien_terminal")));
			meta.setRarity(ItemRarity.RARE);
			meta.setMaxStackSize(MAX_HERMES);
		});
		return stack;
	}

	static ItemStack laevatein(CommandSender viewer) {
		ItemStack stack = ItemStack.of(Material.NETHERITE_SWORD);
		stack.editMeta(meta -> {
			tag(meta, LAEVATEIN);
			meta.itemName(Component.text(Messages.get(viewer, "item.laevatein")));
			meta.setRarity(ItemRarity.EPIC);
			meta.setUnbreakable(true);
		});
		return stack;
	}

	static boolean isLaevatein(ItemStack stack) {
		return is(stack, LAEVATEIN);
	}

	/** Release stage 0 (sealed) to 3, read from the first custom model data string. */
	static int laevateinStage(ItemStack stack) {
		int stage = LAEVATEIN_STAGES.indexOf(modelString(stack));
		return Math.max(stage, 0);
	}

	static void setLaevateinStage(ItemStack stack, int stage) {
		stack.editMeta(meta -> {
			CustomModelDataComponent data = meta.getCustomModelDataComponent();
			data.setStrings(stage <= 0 ? List.of() : List.of(LAEVATEIN_STAGES.get(stage)));
			meta.setCustomModelDataComponent(data);
		});
	}

	private static void tag(ItemMeta meta, String id) {
		meta.getPersistentDataContainer().set(ID, PersistentDataType.STRING, id);
		meta.setItemModel(new NamespacedKey(NAMESPACE, id));
	}

	static boolean is(ItemStack stack, String id) {
		if (stack == null || stack.isEmpty() || !stack.hasItemMeta()) {
			return false;
		}
		return id.equals(stack.getItemMeta().getPersistentDataContainer().get(ID, PersistentDataType.STRING));
	}

	static boolean isCaduceus(ItemStack stack) {
		return is(stack, CADUCEUS);
	}

	static boolean isTerminal(ItemStack stack) {
		return is(stack, TERMINAL);
	}

	/** First custom model data string, or null. */
	static String modelString(ItemStack stack) {
		if (stack == null || !stack.hasItemMeta()) {
			return null;
		}
		ItemMeta meta = stack.getItemMeta();
		if (!meta.hasCustomModelDataComponent()) {
			return null;
		}
		List<String> strings = meta.getCustomModelDataComponent().getStrings();
		return strings.isEmpty() ? null : strings.getFirst();
	}

	/**
	 * Sets the form shown by the model; null restores the bare rod. The name is
	 * left alone so no text pops up over the hotbar.
	 */
	static void setForm(ItemStack stack, CaduceusForm form) {
		stack.editMeta(meta -> {
			CustomModelDataComponent data = meta.getCustomModelDataComponent();
			data.setStrings(form == null ? List.of() : List.of(form.id()));
			meta.setCustomModelDataComponent(data);
		});
	}
}
