package io.github.sharket1111.indexprescript.paper;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Color;
import org.bukkit.Material;

/**
 * Full, plain blocks by colour: the block whose map colour is nearest an RGB,
 * so a stage can be built in the colours of the original's battle background.
 * The colours come from the server's own block map colours (no textures needed).
 */
final class BlockColours {
	private record Entry(Material material, int r, int g, int b) {
	}

	/** Walls and floors: stone, wood, clay, concrete, wool and the like. */
	private static final Material[] SOLID = {
		Material.WHITE_CONCRETE, Material.LIGHT_GRAY_CONCRETE, Material.GRAY_CONCRETE, Material.BLACK_CONCRETE, Material.BROWN_CONCRETE,
		Material.RED_CONCRETE, Material.ORANGE_CONCRETE, Material.YELLOW_CONCRETE, Material.LIME_CONCRETE, Material.GREEN_CONCRETE,
		Material.CYAN_CONCRETE, Material.LIGHT_BLUE_CONCRETE, Material.BLUE_CONCRETE, Material.PURPLE_CONCRETE, Material.MAGENTA_CONCRETE,
		Material.PINK_CONCRETE, Material.WHITE_TERRACOTTA, Material.LIGHT_GRAY_TERRACOTTA, Material.GRAY_TERRACOTTA, Material.BLACK_TERRACOTTA,
		Material.BROWN_TERRACOTTA, Material.RED_TERRACOTTA, Material.ORANGE_TERRACOTTA, Material.YELLOW_TERRACOTTA, Material.LIME_TERRACOTTA,
		Material.GREEN_TERRACOTTA, Material.CYAN_TERRACOTTA, Material.LIGHT_BLUE_TERRACOTTA, Material.BLUE_TERRACOTTA, Material.PURPLE_TERRACOTTA,
		Material.MAGENTA_TERRACOTTA, Material.PINK_TERRACOTTA, Material.TERRACOTTA, Material.STONE_BRICKS, Material.POLISHED_ANDESITE,
		Material.POLISHED_DIORITE, Material.POLISHED_GRANITE, Material.DEEPSLATE_TILES, Material.POLISHED_DEEPSLATE, Material.POLISHED_BLACKSTONE_BRICKS,
		Material.SMOOTH_QUARTZ, Material.QUARTZ_BRICKS, Material.CALCITE, Material.SMOOTH_SANDSTONE, Material.CUT_RED_SANDSTONE, Material.MUD_BRICKS,
		Material.BRICKS, Material.NETHER_BRICKS, Material.RED_NETHER_BRICKS, Material.OAK_PLANKS, Material.SPRUCE_PLANKS, Material.DARK_OAK_PLANKS,
		Material.BIRCH_PLANKS, Material.JUNGLE_PLANKS, Material.ACACIA_PLANKS, Material.MANGROVE_PLANKS, Material.CHERRY_PLANKS, Material.CRIMSON_PLANKS,
		Material.WARPED_PLANKS, Material.BAMBOO_PLANKS, Material.PALE_OAK_PLANKS, Material.PRISMARINE_BRICKS, Material.DARK_PRISMARINE,
		Material.CUT_COPPER, Material.EXPOSED_CUT_COPPER, Material.WEATHERED_CUT_COPPER, Material.OXIDIZED_CUT_COPPER, Material.IRON_BLOCK,
		Material.GOLD_BLOCK, Material.TUFF_BRICKS, Material.PURPUR_BLOCK, Material.END_STONE_BRICKS, Material.NETHER_WART_BLOCK,
		Material.WARPED_WART_BLOCK, Material.PACKED_MUD, Material.BONE_BLOCK, Material.OBSIDIAN};
	/** Light sources. */
	private static final Material[] LIGHT = {Material.SEA_LANTERN, Material.GLOWSTONE, Material.SHROOMLIGHT, Material.OCHRE_FROGLIGHT,
		Material.VERDANT_FROGLIGHT, Material.PEARLESCENT_FROGLIGHT, Material.REDSTONE_LAMP, Material.MAGMA_BLOCK, Material.CRYING_OBSIDIAN};
	/** Glass panes of colour, for windows and neon. */
	private static final Material[] GLASS = {Material.WHITE_STAINED_GLASS, Material.LIGHT_GRAY_STAINED_GLASS, Material.GRAY_STAINED_GLASS,
		Material.BLACK_STAINED_GLASS, Material.BROWN_STAINED_GLASS, Material.RED_STAINED_GLASS, Material.ORANGE_STAINED_GLASS,
		Material.YELLOW_STAINED_GLASS, Material.LIME_STAINED_GLASS, Material.GREEN_STAINED_GLASS, Material.CYAN_STAINED_GLASS,
		Material.LIGHT_BLUE_STAINED_GLASS, Material.BLUE_STAINED_GLASS, Material.PURPLE_STAINED_GLASS, Material.MAGENTA_STAINED_GLASS,
		Material.PINK_STAINED_GLASS};

	/** Foliage of different colours, for trees. */
	private static final Material[] LEAVES = {Material.OAK_LEAVES, Material.AZALEA_LEAVES, Material.FLOWERING_AZALEA_LEAVES, Material.CHERRY_LEAVES,
		Material.PALE_OAK_LEAVES, Material.WARPED_WART_BLOCK, Material.NETHER_WART_BLOCK, Material.MANGROVE_LEAVES, Material.DARK_OAK_LEAVES,
		Material.SPRUCE_LEAVES, Material.SHROOMLIGHT, Material.MOSS_BLOCK};

	private static List<Entry> leaves;
	private static List<Entry> solid;
	private static List<Entry> light;
	private static List<Entry> glass;

	private BlockColours() {
	}

	private static List<Entry> entries(Material[] materials) {
		List<Entry> out = new ArrayList<>();
		for (Material m : materials) {
			try {
				Color c = m.createBlockData().getMapColor();
				out.add(new Entry(m, c.getRed(), c.getGreen(), c.getBlue()));
			} catch (RuntimeException e) {
				// A block this server does not know: left out.
			}
		}
		return out;
	}

	private static Material nearest(List<Entry> list, int rgb, Material fallback) {
		int r = rgb >> 16 & 255;
		int g = rgb >> 8 & 255;
		int b = rgb & 255;
		Material best = fallback;
		double bestD = Double.MAX_VALUE;
		for (Entry e : list) {
			// Weighted by how the eye sees each channel.
			double d = 0.3 * (e.r - r) * (e.r - r) + 0.59 * (e.g - g) * (e.g - g) + 0.11 * (e.b - b) * (e.b - b);
			if (d < bestD) {
				bestD = d;
				best = e.material;
			}
		}
		return best;
	}

	static synchronized Material solid(int rgb) {
		if (solid == null) {
			solid = entries(SOLID);
		}
		return nearest(solid, rgb, Material.GRAY_CONCRETE);
	}

	static synchronized Material light(int rgb) {
		if (light == null) {
			light = entries(LIGHT);
		}
		return nearest(light, rgb, Material.SEA_LANTERN);
	}

	static synchronized Material leaves(int rgb) {
		if (leaves == null) {
			leaves = entries(LEAVES);
		}
		return nearest(leaves, rgb, Material.OAK_LEAVES);
	}

	static synchronized Material glass(int rgb) {
		if (glass == null) {
			glass = entries(GLASS);
		}
		return nearest(glass, rgb, Material.GLASS);
	}
}
