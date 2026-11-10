package io.github.sharket1111.trade;

import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.SmithingTransformRecipe;
import org.bukkit.inventory.StonecuttingRecipe;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

/**
 * What the server pays for each item when a player sells it (in the
 * currency of {@link Money}), from values.yml.
 *
 * The first time (no values.yml), the table is built: a hand-set value for
 * every raw material (ores, crops, mob drops, wood, stone...: a diamond is
 * 100, iron 10, cobblestone 0.1), and every item crafted, smelted, cut or
 * smithed from them is worth what goes into it, through the server's own
 * recipes (the cheapest way to make it). Crafting therefore never makes
 * money. The result is written to values.yml, sorted by name, where an admin
 * can change any value or remove an item to stop it being sold; deleting the
 * file builds it again on the next start.
 *
 * Tools, weapons and armour sell for half what they are made of, less the
 * share of durability used; an enchanted book adds 20 per enchantment level.
 * Emeralds are cheap (villagers give them for crops). Items marked by plugins,
 * with a custom model, or holding other items (a filled shulker box or
 * bundle) cannot be sold.
 */
final class ItemValues {
	/** What each level of an enchanted book's enchantments adds. */
	private static final double ENCHANTED_BOOK_PER_LEVEL = 20;
	/** Tools, weapons and armour sell for this share of what they are made of (villagers sell them cheaply). */
	private static final double GEAR = 0.5;
	/** Smelting costs a little fuel. */
	private static final double FUEL = 0.1;
	private static final int PASSES = 16;

	record Appraisal(double unit, String reason) {
		boolean sellable() {
			return reason == null;
		}
	}

	private static Plugin plugin;
	private static File file;
	private static final Map<Material, Double> VALUES = new EnumMap<>(Material.class);

	private ItemValues() {
	}

	/** Bumped when the default table changes in a way existing files should take up. */
	private static final int VERSION = 3;

	/** Each ore (and its deepslate form) is worth exactly what it gives: the mineral, or the ingot it smelts into. */
	private static final Map<String, String> ORES = new TreeMap<>(Map.ofEntries(
			Map.entry("DIAMOND_ORE", "DIAMOND"), Map.entry("DEEPSLATE_DIAMOND_ORE", "DIAMOND"),
			Map.entry("EMERALD_ORE", "EMERALD"), Map.entry("DEEPSLATE_EMERALD_ORE", "EMERALD"),
			Map.entry("IRON_ORE", "IRON_INGOT"), Map.entry("DEEPSLATE_IRON_ORE", "IRON_INGOT"),
			Map.entry("GOLD_ORE", "GOLD_INGOT"), Map.entry("DEEPSLATE_GOLD_ORE", "GOLD_INGOT"),
			Map.entry("NETHER_GOLD_ORE", "GOLD_INGOT"),
			Map.entry("COPPER_ORE", "COPPER_INGOT"), Map.entry("DEEPSLATE_COPPER_ORE", "COPPER_INGOT"),
			Map.entry("COAL_ORE", "COAL"), Map.entry("DEEPSLATE_COAL_ORE", "COAL"),
			Map.entry("REDSTONE_ORE", "REDSTONE"), Map.entry("DEEPSLATE_REDSTONE_ORE", "REDSTONE"),
			Map.entry("LAPIS_ORE", "LAPIS_LAZULI"), Map.entry("DEEPSLATE_LAPIS_ORE", "LAPIS_LAZULI"),
			Map.entry("NETHER_QUARTZ_ORE", "QUARTZ"),
			Map.entry("ANCIENT_DEBRIS", "NETHERITE_SCRAP")));

	static void load(Plugin owner) {
		plugin = owner;
		file = new File(owner.getDataFolder(), "values.yml");
		VALUES.clear();
		if (!file.exists()) {
			build();
			save();
			return;
		}
		YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
		ConfigurationSection root = yaml.getConfigurationSection("values");
		if (root == null) {
			return;
		}
		for (String key : root.getKeys(false)) {
			Material material = Material.matchMaterial(key);
			double value = root.getDouble(key, -1);
			if (material != null && material.isItem() && value > 0) {
				VALUES.put(material, value);
			}
		}
		int version = yaml.getInt("version", 1);
		if (version < 2) {
			// Older tables valued ores above their minerals: they now match, the rest stays as the admin left it.
			oresAsMinerals();
			owner.getLogger().info("values.yml: ores now match their minerals.");
		}
		if (version < 3) {
			// Emeralds and quartz are easy to farm: much cheaper now.
			int changed = repriced(Map.of("EMERALD", 10.0, "QUARTZ", 4.0));
			owner.getLogger().info("values.yml: emeralds and quartz are cheaper (" + changed + " items repriced).");
		}
		if (version < VERSION) {
			save();
		}
	}

	/**
	 * After raw values changed in the code ({@code before}: what they were),
	 * updates the table: every item whose value is still what the old raw
	 * values gave (the admin has not changed it) gets the new one, worked out
	 * through the recipes as when the table is built. Returns how many changed.
	 */
	private static int repriced(Map<String, Double> before) {
		Map<Material, Double> kept = new EnumMap<>(Material.class);
		kept.putAll(VALUES);
		Map<String, Double> now = new java.util.HashMap<>();
		before.keySet().forEach(name -> now.put(name, BASE.get(name)));
		BASE.putAll(before);
		VALUES.clear();
		build();
		Map<Material, Double> old = new EnumMap<>(Material.class);
		old.putAll(VALUES);
		BASE.putAll(now);
		VALUES.clear();
		build();
		Map<Material, Double> fresh = new EnumMap<>(Material.class);
		fresh.putAll(VALUES);
		VALUES.clear();
		VALUES.putAll(kept);
		int changed = 0;
		for (Map.Entry<Material, Double> entry : fresh.entrySet()) {
			Double was = old.get(entry.getKey());
			Double current = kept.get(entry.getKey());
			if (was != null && current != null && Math.abs(current - was) < 0.005 && Math.abs(entry.getValue() - was) > 1.0E-9) {
				VALUES.put(entry.getKey(), entry.getValue());
				changed++;
			}
		}
		return changed;
	}

	/** Sets every ore to its mineral's value (when the mineral has one). */
	private static void oresAsMinerals() {
		ORES.forEach((ore, mineral) -> {
			Material oreType = Material.matchMaterial(ore);
			Material mineralType = Material.matchMaterial(mineral);
			if (oreType != null && mineralType != null && VALUES.containsKey(mineralType)) {
				VALUES.put(oreType, VALUES.get(mineralType));
			}
		});
	}

	private static void save() {
		YamlConfiguration yaml = new YamlConfiguration();
		yaml.set("version", VERSION);
		yaml.options().setHeader(List.of(
				"PlayerTrade item values: what the server pays for one of each item when it is sold.",
				"Change a number to reprice an item; remove a line to stop it being sold.",
				"Delete this file to build the table again from the default values and the recipes."));
		Map<String, Double> sorted = new TreeMap<>();
		VALUES.forEach((material, value) -> sorted.put(material.name(), value));
		sorted.forEach((key, value) -> yaml.set("values." + key, value));
		try {
			file.getParentFile().mkdirs();
			yaml.save(file);
		} catch (IOException e) {
			plugin.getLogger().log(Level.SEVERE, "Could not save values.yml", e);
		}
	}

	static int count() {
		return VALUES.size();
	}

	/** The table's value of one {@code material}, or null if it cannot be sold. */
	static Double value(Material material) {
		return VALUES.get(material);
	}

	// --- appraising ---------------------------------------------------------------------

	/** What one of {@code stack} sells for, or why it cannot be sold. */
	static Appraisal appraise(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return new Appraisal(0, "빈 칸");
		}
		if (TradeItems.isTradeDiamond(stack)) {
			return new Appraisal(Money.diamondValue(), null);
		}
		if (TradeTools.toolOf(stack) != null) {
			return new Appraisal(0, "무료 아이템");
		}
		Double base = VALUES.get(stack.getType());
		if (base == null) {
			return new Appraisal(0, "팔 수 없는 아이템");
		}
		double value = base;
		if (stack.getType().getMaxDurability() > 0) {
			value *= GEAR;
		}
		if (stack.hasItemMeta()) {
			ItemMeta meta = stack.getItemMeta();
			if (!meta.getPersistentDataContainer().isEmpty() || meta.hasItemModel() || meta.hasCustomModelDataComponent()) {
				return new Appraisal(0, "특수 아이템");
			}
			if (meta instanceof BlockStateMeta blockMeta && blockMeta.hasBlockState()
					&& blockMeta.getBlockState() instanceof org.bukkit.block.Container container && !container.getInventory().isEmpty()) {
				return new Appraisal(0, "안에 든 물건을 먼저 꺼내세요");
			}
			if (meta instanceof BundleMeta bundle && bundle.hasItems()) {
				return new Appraisal(0, "꾸러미를 먼저 비우세요");
			}
			if (meta instanceof EnchantmentStorageMeta book) {
				for (Map.Entry<Enchantment, Integer> entry : book.getStoredEnchants().entrySet()) {
					value += ENCHANTED_BOOK_PER_LEVEL * entry.getValue();
				}
			}
			if (meta instanceof Damageable damageable && damageable.hasDamage()) {
				int max = damageable.hasMaxDamage() ? damageable.getMaxDamage() : stack.getType().getMaxDurability();
				if (max > 0) {
					value *= Math.max(0.0, 1.0 - (double) damageable.getDamage() / max);
				}
			}
		}
		return new Appraisal(value, null);
	}

	/** Shows a value: whole numbers plain, small ones with up to two decimals. */
	static String show(double value) {
		if (value >= 100 || Math.abs(value - Math.rint(value)) < 0.005) {
			return String.format("%,d", Math.round(value));
		}
		return String.format(Locale.ROOT, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
	}

	// --- building the table -------------------------------------------------------------

	private static void build() {
		Map<Material, Double> base = new EnumMap<>(Material.class);
		BASE.forEach((key, value) -> {
			Material material = Material.matchMaterial(key);
			if (material != null && material.isItem()) {
				base.put(material, value);
			}
		});
		for (Material material : Material.values()) {
			if (material.isLegacy() || !material.isItem() || base.containsKey(material)) {
				continue;
			}
			Double value = byName(material.name());
			if (value != null) {
				base.put(material, value);
			}
		}
		VALUES.putAll(base);
		oresAsMinerals();
		for (String ore : ORES.keySet()) {
			Material type = Material.matchMaterial(ore);
			if (type != null && VALUES.containsKey(type)) {
				base.put(type, VALUES.get(type));
			}
		}
		// Everything made from them is worth what goes into it, the cheapest way.
		for (int pass = 0; pass < PASSES; pass++) {
			boolean changed = false;
			for (Iterator<Recipe> it = Bukkit.recipeIterator(); it.hasNext();) {
				Recipe recipe;
				try {
					recipe = it.next();
				} catch (RuntimeException e) {
					continue;
				}
				ItemStack result = recipe.getResult();
				if (result == null || result.isEmpty() || base.containsKey(result.getType()) || result.getAmount() <= 0) {
					continue;
				}
				double cost = cost(recipe);
				if (cost <= 0) {
					continue;
				}
				double value = round(cost / result.getAmount());
				if (value <= 0) {
					continue;
				}
				Double known = VALUES.get(result.getType());
				if (known == null || value < known - 1.0E-9) {
					VALUES.put(result.getType(), value);
					changed = true;
				}
			}
			if (!changed) {
				break;
			}
		}
		// Made without a recipe: concrete is its powder set in water.
		for (Material material : Material.values()) {
			if (!material.isLegacy() && material.name().endsWith("_CONCRETE") && !VALUES.containsKey(material)) {
				Material powder = Material.matchMaterial(material.name() + "_POWDER");
				if (powder != null && VALUES.containsKey(powder)) {
					VALUES.put(material, VALUES.get(powder));
				}
			}
		}
	}

	/** What a recipe's ingredients are worth, or -1 if any of them has no value. */
	private static double cost(Recipe recipe) {
		double total = 0;
		if (recipe instanceof ShapedRecipe shaped) {
			Map<Character, RecipeChoice> choices = shaped.getChoiceMap();
			for (String row : shaped.getShape()) {
				for (char c : row.toCharArray()) {
					RecipeChoice choice = choices.get(c);
					if (choice == null) {
						continue;
					}
					double value = value(choice);
					if (value < 0) {
						return -1;
					}
					total += value;
				}
			}
		} else if (recipe instanceof ShapelessRecipe shapeless) {
			for (RecipeChoice choice : shapeless.getChoiceList()) {
				double value = value(choice);
				if (value < 0) {
					return -1;
				}
				total += value;
			}
		} else if (recipe instanceof CookingRecipe<?> cooking) {
			double value = value(cooking.getInputChoice());
			if (value < 0) {
				return -1;
			}
			total = value + FUEL;
		} else if (recipe instanceof StonecuttingRecipe cutting) {
			total = value(cutting.getInputChoice());
		} else if (recipe instanceof SmithingTransformRecipe smithing) {
			for (RecipeChoice choice : new RecipeChoice[] {smithing.getTemplate(), smithing.getBase(), smithing.getAddition()}) {
				if (choice == null) {
					continue;
				}
				double value = value(choice);
				if (value < 0) {
					return -1;
				}
				total += value;
			}
		} else {
			return -1;
		}
		return total;
	}

	/** The cheapest known item a choice accepts, or -1. */
	private static double value(RecipeChoice choice) {
		double best = -1;
		if (choice instanceof RecipeChoice.MaterialChoice materials) {
			for (Material material : materials.getChoices()) {
				Double value = VALUES.get(material);
				if (value != null && (best < 0 || value < best)) {
					best = value;
				}
			}
		} else if (choice instanceof RecipeChoice.ExactChoice exact) {
			for (ItemStack stack : exact.getChoices()) {
				Double value = VALUES.get(stack.getType());
				if (value != null && (best < 0 || value < best)) {
					best = value;
				}
			}
		}
		return best;
	}

	private static double round(double value) {
		return Math.round(value * 100.0) / 100.0;
	}

	/** Values for whole families of raw items, by name. */
	private static Double byName(String name) {
		if (name.startsWith("STRIPPED_")) {
			return name.endsWith("_LOG") || name.endsWith("_STEM") || name.equals("STRIPPED_BAMBOO_BLOCK") ? 1.0 : null;
		}
		if (name.endsWith("_LOG") || name.endsWith("_STEM") && !name.equals("MUSHROOM_STEM") || name.equals("BAMBOO_BLOCK")) {
			return 1.0;
		}
		if (name.endsWith("_WOOD") || name.endsWith("_HYPHAE")) {
			return null;
		}
		if (name.endsWith("_SAPLING") || name.equals("MANGROVE_PROPAGULE") || name.equals("AZALEA") || name.equals("FLOWERING_AZALEA")) {
			return 0.2;
		}
		if (name.endsWith("_LEAVES")) {
			return 0.05;
		}
		if (name.startsWith("MUSIC_DISC_")) {
			return 30.0;
		}
		if (name.endsWith("_POTTERY_SHERD")) {
			return 10.0;
		}
		if (name.endsWith("_ARMOR_TRIM_SMITHING_TEMPLATE")) {
			return 40.0;
		}
		if (name.endsWith("_CORAL") || name.endsWith("_CORAL_FAN") || name.endsWith("_CORAL_BLOCK")) {
			return name.startsWith("DEAD_") ? 0.2 : 1.0;
		}
		return null;
	}

	/** Hand-set values of raw materials (a diamond is 100). */
	private static final Map<String, Double> BASE = new TreeMap<>();

	private static void put(double value, String... names) {
		for (String name : names) {
			BASE.put(name, value);
		}
	}

	static {
		// Ores and what they give.
		put(100, "DIAMOND");

		put(2, "EMERALD");

		put(10, "IRON_INGOT");
		put(8, "RAW_IRON");
		put(20, "GOLD_INGOT");
		put(16, "RAW_GOLD");

		put(3, "COPPER_INGOT");
		put(2, "RAW_COPPER");
		put(4, "COAL");

		put(3, "CHARCOAL");
		put(3, "REDSTONE");

		put(4, "LAPIS_LAZULI");

		put(1, "QUARTZ");

		put(250, "NETHERITE_SCRAP");
		put(3, "AMETHYST_SHARD");
		put(100, "ECHO_SHARD");
		put(2, "GLOWSTONE_DUST");
		put(1, "FLINT");
		put(2, "PRISMARINE_SHARD");
		put(3, "PRISMARINE_CRYSTALS");
		// Stone, earth and the like.
		put(0.1, "DIRT", "COARSE_DIRT", "COBBLESTONE", "COBBLED_DEEPSLATE", "NETHERRACK", "SNOWBALL", "ROOTED_DIRT");
		put(0.15, "GRANITE", "DIORITE", "ANDESITE", "TUFF", "BLACKSTONE", "BASALT");
		put(0.2, "STONE", "DEEPSLATE", "GRASS_BLOCK", "SAND", "GRAVEL", "MUD", "MYCELIUM", "PODZOL", "POINTED_DRIPSTONE");
		put(0.3, "RED_SAND", "CALCITE", "SOUL_SAND", "SOUL_SOIL", "SMOOTH_BASALT");
		put(0.5, "CLAY_BALL", "END_STONE", "ICE", "MOSS_BLOCK", "MOSS_CARPET", "SCULK", "PALE_MOSS_BLOCK", "SNOW_BLOCK");
		put(5, "OBSIDIAN");
		put(8, "CRYING_OBSIDIAN");
		put(1, "WHITE_WOOL");
		put(0.1, "BAMBOO", "KELP", "SHORT_GRASS", "TALL_GRASS", "FERN", "LARGE_FERN", "VINE", "SEAGRASS", "DEAD_BUSH", "LILY_PAD");
		put(0.2, "DANDELION", "POPPY", "BLUE_ORCHID", "ALLIUM", "AZURE_BLUET", "RED_TULIP", "ORANGE_TULIP", "WHITE_TULIP",
				"PINK_TULIP", "OXEYE_DAISY", "CORNFLOWER", "LILY_OF_THE_VALLEY", "SUNFLOWER", "LILAC", "ROSE_BUSH", "PEONY",
				"TORCHFLOWER", "PITCHER_PLANT", "PINK_PETALS", "SPORE_BLOSSOM", "CRIMSON_FUNGUS", "WARPED_FUNGUS", "CRIMSON_ROOTS",
				"WARPED_ROOTS", "NETHER_SPROUTS", "WEEPING_VINES", "TWISTING_VINES", "HANGING_ROOTS", "GLOW_LICHEN", "BIG_DRIPLEAF",
				"SMALL_DRIPLEAF", "CLOSED_EYEBLOSSOM", "OPEN_EYEBLOSSOM", "WILDFLOWERS", "LEAF_LITTER", "BUSH", "FIREFLY_BUSH",
				"SHORT_DRY_GRASS", "TALL_DRY_GRASS", "CACTUS_FLOWER", "WITHER_ROSE");
		put(4, "SHROOMLIGHT");
		put(1, "NETHER_WART_BLOCK", "WARPED_WART_BLOCK");
		put(0.5, "CRIMSON_NYLIUM", "WARPED_NYLIUM");
		// Crops and food.
		put(0.8, "WHEAT", "NETHER_WART");
		put(0.05, "WHEAT_SEEDS", "BEETROOT_SEEDS", "MELON_SEEDS", "PUMPKIN_SEEDS");
		put(0.5, "CARROT", "POTATO", "BEETROOT", "COCOA_BEANS", "BROWN_MUSHROOM", "RED_MUSHROOM", "CHORUS_FRUIT", "EGG",
				"BLUE_EGG", "BROWN_EGG");
		put(0.2, "MELON_SLICE");
		put(1, "PUMPKIN");
		put(0.4, "SUGAR_CANE", "CACTUS", "GLOW_BERRIES");
		put(0.3, "SWEET_BERRIES");
		put(2, "APPLE", "HONEYCOMB");
		put(3, "HONEY_BOTTLE");
		put(0.1, "POISONOUS_POTATO");
		put(1, "TORCHFLOWER_SEEDS", "PITCHER_POD");
		put(1.5, "BEEF", "PORKCHOP");
		put(1, "CHICKEN", "COD");
		put(1.2, "MUTTON", "RABBIT", "SALMON");
		put(2, "TROPICAL_FISH", "PUFFERFISH");
		// Mob drops.
		put(0.2, "ROTTEN_FLESH");
		put(0.5, "BONE", "STRING", "FEATHER", "RABBIT_HIDE");
		put(1, "SPIDER_EYE", "INK_SAC");
		put(1.5, "GUNPOWDER");
		put(2, "LEATHER", "SLIME_BALL", "GLOW_INK_SAC");
		put(4, "PHANTOM_MEMBRANE");
		put(5, "ARMADILLO_SCUTE");
		put(6, "RABBIT_FOOT");
		put(8, "ENDER_PEARL", "BLAZE_ROD");
		put(10, "TURTLE_SCUTE", "BREEZE_ROD");
		put(25, "GHAST_TEAR", "NAUTILUS_SHELL");
		put(60, "SHULKER_SHELL");
		put(20, "DRAGON_BREATH", "SADDLE", "SKELETON_SKULL", "ZOMBIE_HEAD", "TRIAL_KEY");
		put(30, "CREEPER_HEAD", "PIGLIN_HEAD");
		put(150, "WITHER_SKELETON_SKULL");
		put(15, "NAME_TAG", "GOAT_HORN");
		put(60, "OMINOUS_TRIAL_KEY");
		put(200, "HEART_OF_THE_SEA");
		put(300, "TOTEM_OF_UNDYING", "TRIDENT", "NETHERITE_UPGRADE_SMITHING_TEMPLATE");
		put(500, "ENCHANTED_GOLDEN_APPLE");
		put(800, "HEAVY_CORE");
		put(1500, "NETHER_STAR");
		put(2000, "ELYTRA");
		put(50, "SNIFFER_EGG");
		put(20, "SPONGE", "WET_SPONGE");
		put(10, "SCULK_CATALYST", "SCULK_SENSOR");
		put(15, "SCULK_SHRIEKER");
		put(2, "BOOK");
		put(1, "ENCHANTED_BOOK");
		put(3, "FLOW_BANNER_PATTERN", "GUSTER_BANNER_PATTERN", "GLOBE_BANNER_PATTERN", "PIGLIN_BANNER_PATTERN");
	}
}
