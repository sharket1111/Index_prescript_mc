package io.github.sharket1111.indexprescript.paper;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;
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
	static final String LAEVATEIN_ICON = "laevatein_icon";
	static final String RENOIR_HAMMER = "renoir_hammer";
	static final String BLACK_GOD_VOICE = "black_god_voice";
	static final String MIMICRY = "mimicry";
	/** Mimicry's first custom model data string while E.G.O is manifested (tools/mimicry_assets.py). */
	static final String MIMICRY_EGO = "ego";
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
		});
		return stack;
	}

	/** Gebura's E.G.O, Mimicry: a netherite sword drawn as the great flesh blade (MimicryWeapon, MimicrySkill). */
	static ItemStack mimicry(CommandSender viewer) {
		ItemStack stack = ItemStack.of(Material.NETHERITE_SWORD);
		stack.editMeta(meta -> {
			tag(meta, MIMICRY);
			meta.itemName(Component.text(Messages.get(viewer, "item.mimicry"), NamedTextColor.RED));
			meta.lore(List.of(Component.text(Messages.get(viewer, "item.mimicry.desc"), NamedTextColor.GRAY)
					.decoration(TextDecoration.ITALIC, false)));
			meta.setRarity(ItemRarity.EPIC);
			meta.setUnbreakable(true);
		});
		return stack;
	}

	static boolean isMimicry(ItemStack stack) {
		return is(stack, MIMICRY);
	}

	/** Shows Mimicry manifested (E.G.O) or not. */
	static void setMimicryEgo(ItemStack stack, boolean ego) {
		if (MIMICRY_EGO.equals(modelString(stack)) != ego) {
			setModelStrings(stack, ego ? MIMICRY_EGO : null, mangString(stack));
		}
	}

	/** The Brand Manager's hammer: a mace drawn as a great two-block hammer (no skills yet). */
	static ItemStack renoirHammer(CommandSender viewer) {
		ItemStack stack = ItemStack.of(Material.MACE);
		stack.editMeta(meta -> {
			tag(meta, RENOIR_HAMMER);
			meta.itemName(Component.text(Messages.get(viewer, "item.renoir_hammer")));
			meta.setRarity(ItemRarity.EPIC);
			meta.setUnbreakable(true);
		});
		hammerStats(stack);
		return stack;
	}

	/** A mace's damage (6) at a sword's attack speed (1.6) instead of the mace's slow 0.6. */
	private static final double HAMMER_DAMAGE = 5.0;
	private static final double HAMMER_SPEED = -2.4;

	/** Gives the hammer a sword's attack speed; returns false if it already had it. */
	static boolean hammerStats(ItemStack stack) {
		ItemMeta current = stack.getItemMeta();
		if (current.hasAttributeModifiers() && current.getAttributeModifiers(Attribute.ATTACK_SPEED) != null
				&& current.getAttributeModifiers(Attribute.ATTACK_SPEED).stream().anyMatch(m -> m.getAmount() == HAMMER_SPEED)) {
			return false;
		}
		stack.editMeta(meta -> {
			meta.removeAttributeModifier(Attribute.ATTACK_DAMAGE);
			meta.removeAttributeModifier(Attribute.ATTACK_SPEED);
			// The vanilla keys, so the tooltip shows them like any weapon's own stats.
			meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(NamespacedKey.minecraft("base_attack_damage"),
					HAMMER_DAMAGE, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
			meta.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(NamespacedKey.minecraft("base_attack_speed"),
					HAMMER_SPEED, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
		});
		return true;
	}

	static boolean isRenoirHammer(ItemStack stack) {
		return is(stack, RENOIR_HAMMER);
	}

	/** The off-hand [Black God's Voice] icon; its stack size is the value. */
	static ItemStack voiceIcon(CommandSender viewer) {
		ItemStack stack = ItemStack.of(Material.PAPER);
		stack.editMeta(meta -> {
			tag(meta, BLACK_GOD_VOICE);
			meta.itemName(Component.text(Messages.get(viewer, "item.black_god_voice")));
			meta.setRarity(ItemRarity.RARE);
			meta.setMaxStackSize(RenoirHammer.ICON_MAX_STACK);
		});
		return stack;
	}

	static boolean isVoiceIcon(ItemStack stack) {
		return is(stack, BLACK_GOD_VOICE);
	}

	/** Off-hand [Seal Release] icon whose stack size shows Laevatein's hits toward the next release. */
	static ItemStack laevateinIcon(CommandSender viewer) {
		ItemStack stack = ItemStack.of(Material.PAPER);
		stack.editMeta(meta -> {
			tag(meta, LAEVATEIN_ICON);
			meta.itemName(Component.text(Messages.get(viewer, "item.laevatein_icon")));
			meta.setRarity(ItemRarity.RARE);
		});
		return stack;
	}

	static boolean isLaevateinIcon(ItemStack stack) {
		return is(stack, LAEVATEIN_ICON);
	}

	static boolean isLaevatein(ItemStack stack) {
		return is(stack, LAEVATEIN);
	}

	/** Release stage 0 (sealed) to 3, read from the first custom model data string. */
	static int laevateinStage(ItemStack stack) {
		String model = modelString(stack);
		// A sealed blade has no model string; List.of(...).indexOf(null) would throw.
		return model == null ? 0 : Math.max(LAEVATEIN_STAGES.indexOf(model), 0);
	}

	static void setLaevateinStage(ItemStack stack, int stage) {
		setModelStrings(stack, stage <= 0 ? null : LAEVATEIN_STAGES.get(stage), mangString(stack));
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
		return strings.isEmpty() || strings.getFirst().isEmpty() ? null : strings.getFirst();
	}

	/** The second custom model data string: "mang1".."mang6" while [Mang]'s rings are on the blade, else null. */
	static String mangString(ItemStack stack) {
		if (stack == null || !stack.hasItemMeta() || !stack.getItemMeta().hasCustomModelDataComponent()) {
			return null;
		}
		List<String> strings = stack.getItemMeta().getCustomModelDataComponent().getStrings();
		return strings.size() < 2 || strings.get(1).isEmpty() ? null : strings.get(1);
	}

	/** Winds {@code count} of [Mang]'s rings round the weapon's model (tools/mang_models.py); 0 removes them. */
	static void setMang(ItemStack stack, int count) {
		setModelStrings(stack, modelString(stack), count <= 0 ? null : "mang" + count);
	}

	static boolean isWeapon(ItemStack stack) {
		return isCaduceus(stack) || isLaevatein(stack) || isRenoirHammer(stack) || isMimicry(stack);
	}

	/** The form or stage string first, then the Mang string; an empty first string stands for none. */
	private static void setModelStrings(ItemStack stack, String model, String mang) {
		stack.editMeta(meta -> {
			CustomModelDataComponent data = meta.getCustomModelDataComponent();
			if (mang == null) {
				data.setStrings(model == null ? List.of() : List.of(model));
			} else {
				data.setStrings(List.of(model == null ? "" : model, mang));
			}
			meta.setCustomModelDataComponent(data);
		});
	}

	/**
	 * Sets the form shown by the model; null restores the bare rod. The name is
	 * left alone so no text pops up over the hotbar.
	 */
	static void setForm(ItemStack stack, CaduceusForm form) {
		setModelStrings(stack, form == null ? null : form.id(), mangString(stack));
	}
}
