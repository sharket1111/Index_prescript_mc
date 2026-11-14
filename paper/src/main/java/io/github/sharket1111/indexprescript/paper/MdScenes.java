package io.github.sharket1111.indexprescript.paper;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.WeatherType;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;
import org.bukkit.entity.Player;

/**
 * The Mirror Dungeon's stages: each Theme Pack fights in a place like its
 * background in the original (the Backstreets at night, a K Corp. lab, N
 * Corp.'s chapel, a whaling ship on the Lake, the Wuthering manor, La
 * Manchaland's carnival, the Warp train, a kitchen, a boutique, the Ring's
 * studio, the East's pavilions...). A scene lays the floor, a ring of props
 * around the fighting ground and a backdrop beyond it (buildings, masts,
 * shelves, trees...), and sets the time of day, the weather and the particles
 * in the air for the players there. The fighting ground itself stays open.
 */
final class MdScenes {
	enum Scene {
		ALLEY(18000, false, Particle.ASH), LAB(6000, false, Particle.WHITE_ASH), CHAPEL(18000, false, Particle.END_ROD), SHIP(14000, true, null),
		DEEP(18000, false, Particle.UNDERWATER), MANOR(18000, true, null), MOOR(17000, true, Particle.WHITE_ASH), CARNIVAL(16000, false,
				Particle.FIREWORK), TRAIN(18000, false, null), KITCHEN(12000, false, Particle.CAMPFIRE_COSY_SMOKE), BOUTIQUE(13000, false,
						Particle.CHERRY_LEAVES), STUDIO(12000, false, Particle.DRIPPING_DRIPSTONE_LAVA), EAST(13500, false, Particle.CHERRY_LEAVES),
		FOREST(1000, false, Particle.SPORE_BLOSSOM_AIR), LIBRARY(18000, false, Particle.ENCHANT), SNOW(16000, true, Particle.SNOWFLAKE),
		CASINO(18000, false, Particle.WAX_ON), FIRE(13000, false, Particle.LAVA), STORM(18000, true, Particle.ELECTRIC_SPARK), QUAKE(12500, false,
				Particle.FALLING_DUST), BLOOD(13000, false, Particle.DRIPPING_LAVA), FACTORY(14000, false, Particle.LARGE_SMOKE), MIRROR(18000, false,
						Particle.REVERSE_PORTAL), NEON(18000, false, Particle.END_ROD), WHALE(18000, false, Particle.FALLING_WATER),
		THEATER(18000, false, Particle.WHITE_ASH), CLOCK(12000, false, Particle.WHITE_ASH), ARENA(18000, false, Particle.SMOKE),
		TILES(13000, false, Particle.FALLING_SPORE_BLOSSOM);

		final long time;
		final boolean rain;
		final Particle air;

		Scene(long time, boolean rain, Particle air) {
			this.time = time;
			this.rain = rain;
			this.air = air;
		}
	}

	/** Theme Pack id -> scene, after where the original fights each one. */
	private static final Map<String, Scene> THEMES = new HashMap<>();
	/** Theme Pack id -> its picture's two main colours (the scene's accents). */
	private static final Map<String, int[]> PALETTE = new HashMap<>();

	static {
		// After each Theme Pack's picture in the original.
		put(Scene.ALLEY, "the_dawn_of_green", "complete_total_extermination", "yield_my_flesh_to_claim_their_bones");
		put(Scene.FACTORY, "the_forgotten", "automated_factory", "blade_and_artwork", "the_unsevering", "s_e_a", "full_stopped_by_a_bullet",
				"bridle_of_infinity", "bearers_of_weight");
		put(Scene.LAB, "nest_workshop_and_technology", "textbook", "code_purple");
		put(Scene.CHAPEL, "the_unloving", "falling_flowers", "the_noon_of_violet", "n_corp_new_league_of_nine_litt_rateurs");
		put(Scene.QUAKE, "faith_erosion");
		put(Scene.FIRE, "the_outcast", "nagel_und_hammer");
		put(Scene.BLOOD, "the_unconfronting", "readvent_of_gluttony", "a_beautiful_voice");
		put(Scene.TILES, "tearful_things");
		put(Scene.FOREST, "efflorescing_greenery", "fairy_tale", "a_midspring_night_s_dream_2");
		put(Scene.MOOR, "the_unchanging", "impenetrable_path");
		put(Scene.NEON, "lake_world", "an_anarchy_of_colors", "who_couldn_t_be_bloodfiends");
		put(Scene.WHALE, "crawling_abyss", "the_evil_defining");
		put(Scene.MANOR, "dregs_of_the_manor", "a_certain_world", "the_heartbreaking", "hatred_and_despair");
		put(Scene.CARNIVAL, "la_manchaland_re_opening", "the_infinite_procession", "the_dream_ending", "blessed_carnival", "the_master_of_la_manchaland");
		put(Scene.EAST, "flat_broke_gamblers", "four_houses_and_greed", "the_surrendered_witnessing", "mnestic_experience",
				"yield_my_flesh_to_claim_their_bones_bokgak", "chachihu");
		put(Scene.STUDIO, "charm_wander_doubt", "twining_threads");
		put(Scene.BOUTIQUE, "storefront_campout");
		put(Scene.KITCHEN, "hell_s_chicken", "the_dusk_of_amber");
		put(Scene.TRAIN, "murder_on_the_warp_express", "murder_on_the_warp_express_bokgak", "lcb_regular_check_up", "lcb_regular_check_up_bokgak",
				"line_1", "line_2", "line_3", "line_4_section_3", "line_4_section_4", "line_5", "line_3_terminus", "line_1_madness");
		put(Scene.THEATER, "nocturnal_sweeping", "nocturnal_sweeping_bokgak");
		put(Scene.CLOCK, "timekilling_time", "timekilling_time_bokgak");
		put(Scene.SNOW, "miracle_in_district_20", "miracle_in_district_20_bokgak");
		put(Scene.LIBRARY, "into_a_certain_library_s_book", "spring_cultivation");
		put(Scene.DEEP, "creed_of_preservation", "s_e_a_c_r", "bloodtinged_ichthyic_odor");
		put(Scene.CASINO, "the_b_e");
		// The Attack Type, Sin and Status themes: the pictures' warning-taped containment panels.
		put(Scene.ARENA, "slicers_dicers", "to_be_cleaved", "piercers_penetrators", "to_be_pierced", "crushers_breakers", "to_be_crushed",
				"repressed_wrath", "unbound_wrath", "emotional_repression", "addicting_lust", "tangling_lust", "emotional_seduction", "treadwheel_sloth",
				"inert_sloth", "emotional_indolence", "devoured_gluttony", "excessive_gluttony", "emotional_craving", "degraded_gloom", "sunk_gloom",
				"emotional_flood", "vain_pride", "tyrannical_pride", "emotional_subservience", "insignificant_envy", "pitiful_envy", "emotional_judgment",
				"burning_haze", "season_of_the_flame", "trickled_sanguine_blood", "mountain_of_corpses_sea_of_blood", "dizzying_waves",
				"abnormal_seismic_zone", "crushing_external_force", "unrelenting_might", "sinking_pang", "sinking_deluge", "deep_sigh", "poised_breathing",
				"rising_power_supply", "thunder_and_lightning");
		PALETTE.put("the_forgotten", new int[] {0x572e26, 0x74372e});
		PALETTE.put("the_outcast", new int[] {0x602e23, 0x8a4435});
		PALETTE.put("flat_broke_gamblers", new int[] {0xa8563e, 0xcc8561});
		PALETTE.put("automated_factory", new int[] {0x1a554a, 0x925842});
		PALETTE.put("the_unloving", new int[] {0x4291a5, 0x5ac0cb});
		PALETTE.put("nagel_und_hammer", new int[] {0x75291f, 0xaa5146});
		PALETTE.put("faith_erosion", new int[] {0xa77151, 0x5a2e1f});
		PALETTE.put("the_unconfronting", new int[] {0xc07272, 0x602420});
		PALETTE.put("falling_flowers", new int[] {0x5e4535, 0x523a2b});
		PALETTE.put("tearful_things", new int[] {0x74573c, 0x5a432b});
		PALETTE.put("lake_world", new int[] {0x2f3472, 0x665893});
		PALETTE.put("crawling_abyss", new int[] {0xd5817a, 0x7c5249});
		PALETTE.put("the_evil_defining", new int[] {0xb07a5e, 0xdda27c});
		PALETTE.put("dregs_of_the_manor", new int[] {0x613c34, 0x502b25});
		PALETTE.put("a_certain_world", new int[] {0x7a5e4a, 0x614b3b});
		PALETTE.put("the_heartbreaking", new int[] {0x6e4452, 0x8b536e});
		PALETTE.put("la_manchaland_re_opening", new int[] {0xab75c6, 0x655078});
		PALETTE.put("the_infinite_procession", new int[] {0x7b3b41, 0xa34f52});
		PALETTE.put("the_dream_ending", new int[] {0x89503e, 0x643225});
		PALETTE.put("four_houses_and_greed", new int[] {0x7d4938, 0x5f3424});
		PALETTE.put("the_surrendered_witnessing", new int[] {0xae6667, 0x61383a});
		PALETTE.put("charm_wander_doubt", new int[] {0x63282d, 0x83494a});
		PALETTE.put("textbook", new int[] {0x3c5561, 0xb4a05e});
		PALETTE.put("blade_and_artwork", new int[] {0x6678ad, 0x53638e});
		PALETTE.put("the_unsevering", new int[] {0x464167, 0x512f36});
		PALETTE.put("storefront_campout", new int[] {0x972832, 0x643435});
		PALETTE.put("an_anarchy_of_colors", new int[] {0x2a2c5d, 0xb876b5});
		PALETTE.put("creed_of_preservation", new int[] {0x463e62, 0x353355});
		PALETTE.put("readvent_of_gluttony", new int[] {0x7c4145, 0x681b21});
		PALETTE.put("hell_s_chicken", new int[] {0x9c7d26, 0x6c5b1a});
		PALETTE.put("s_e_a", new int[] {0x5c4624, 0x7f642b});
		PALETTE.put("miracle_in_district_20", new int[] {0x704a22, 0xc58c2b});
		PALETTE.put("miracle_in_district_20_bokgak", new int[] {0x634721, 0x937616});
		PALETTE.put("yield_my_flesh_to_claim_their_bones", new int[] {0x313965, 0x8d7317});
		PALETTE.put("yield_my_flesh_to_claim_their_bones_bokgak", new int[] {0x2b416b, 0x3f6cb4});
		PALETTE.put("timekilling_time", new int[] {0x644b10, 0x92730a});
		PALETTE.put("timekilling_time_bokgak", new int[] {0xa78f63, 0x655129});
		PALETTE.put("murder_on_the_warp_express", new int[] {0x5ccde4, 0x17c5f4});
		PALETTE.put("murder_on_the_warp_express_bokgak", new int[] {0x73324e, 0x9f3f67});
		PALETTE.put("lcb_regular_check_up", new int[] {0x5d3c21, 0x735741});
		PALETTE.put("lcb_regular_check_up_bokgak", new int[] {0x375071, 0x624a0d});
		PALETTE.put("nocturnal_sweeping", new int[] {0x755d0d, 0xa17f0b});
		PALETTE.put("nocturnal_sweeping_bokgak", new int[] {0x4c4a8c, 0x2f2c63});
		PALETTE.put("spring_cultivation", new int[] {0x5c3d2e, 0x8b6d07});
		PALETTE.put("twining_threads", new int[] {0x705813, 0x6273a0});
		PALETTE.put("mnestic_experience", new int[] {0x62490b, 0x907208});
		PALETTE.put("the_noon_of_violet", new int[] {0x895e49, 0x674c19});
		PALETTE.put("full_stopped_by_a_bullet", new int[] {0x7e691f, 0x58451a});
		PALETTE.put("hatred_and_despair", new int[] {0xcf9156, 0xa6704b});
		PALETTE.put("the_dusk_of_amber", new int[] {0x425d99, 0x886d23});
		PALETTE.put("line_1", new int[] {0x2850d8, 0x1a2a9a});
		PALETTE.put("line_2", new int[] {0x30c040, 0x0f6a20});
		PALETTE.put("line_3", new int[] {0xe86a10, 0x7a3008});
		PALETTE.put("line_4_section_3", new int[] {0x20a8e0, 0x0a5080});
		PALETTE.put("line_4_section_4", new int[] {0x20a8e0, 0x0a5080});
		PALETTE.put("line_5", new int[] {0x8030c0, 0x40106a});
		PALETTE.put("slicers_dicers", new int[] {0x621010, 0x975f61});
		PALETTE.put("to_be_cleaved", new int[] {0x399e73, 0x58dec6});
		PALETTE.put("piercers_penetrators", new int[] {0x5b3a20, 0x535d28});
		PALETTE.put("to_be_pierced", new int[] {0x2c9d6f, 0x237652});
		PALETTE.put("crushers_breakers", new int[] {0x5e403b, 0x326da9});
		PALETTE.put("to_be_crushed", new int[] {0xae6e39, 0x774b23});
		PALETTE.put("repressed_wrath", new int[] {0xfb9708, 0x962809});
		PALETTE.put("unbound_wrath", new int[] {0xe04612, 0xfab00d});
		PALETTE.put("emotional_repression", new int[] {0x68261b, 0xa9825c});
		PALETTE.put("addicting_lust", new int[] {0x47b28e, 0x5f1e07});
		PALETTE.put("tangling_lust", new int[] {0x711b0e, 0x965a4f});
		PALETTE.put("emotional_seduction", new int[] {0x853905, 0x996653});
		PALETTE.put("treadwheel_sloth", new int[] {0x614725, 0x16e0d4});
		PALETTE.put("inert_sloth", new int[] {0x968040, 0x07c6bc});
		PALETTE.put("emotional_indolence", new int[] {0xf8c51b, 0x65461f});
		PALETTE.put("devoured_gluttony", new int[] {0x5eb088, 0x3e956e});
		PALETTE.put("excessive_gluttony", new int[] {0x8d8158, 0x5b6115});
		PALETTE.put("emotional_craving", new int[] {0x768728, 0xace333});
		PALETTE.put("degraded_gloom", new int[] {0x194855, 0x5a64a7});
		PALETTE.put("sunk_gloom", new int[] {0x576ba8, 0x611a17});
		PALETTE.put("emotional_flood", new int[] {0x1f767e, 0x17cee9});
		PALETTE.put("vain_pride", new int[] {0xe3b562, 0x4f262a});
		PALETTE.put("tyrannical_pride", new int[] {0x0e336d, 0x136aef});
		PALETTE.put("emotional_subservience", new int[] {0x59a78c, 0x2976ed});
		PALETTE.put("insignificant_envy", new int[] {0x6c1213, 0x401553});
		PALETTE.put("pitiful_envy", new int[] {0x602955, 0x6d3890});
		PALETTE.put("emotional_judgment", new int[] {0xa34be1, 0x7458a1});
		PALETTE.put("burning_haze", new int[] {0x681e0a, 0xba3d19});
		PALETTE.put("season_of_the_flame", new int[] {0xd54515, 0x782e16});
		PALETTE.put("trickled_sanguine_blood", new int[] {0x590b0e, 0x710d12});
		PALETTE.put("mountain_of_corpses_sea_of_blood", new int[] {0x6f0a09, 0x6d3e38});
		PALETTE.put("dizzying_waves", new int[] {0x8a4a00, 0x622b03});
		PALETTE.put("abnormal_seismic_zone", new int[] {0x2c976b, 0x41b17d});
		PALETTE.put("crushing_external_force", new int[] {0xb0672d, 0x605e39});
		PALETTE.put("unrelenting_might", new int[] {0x7e5828, 0x96783a});
		PALETTE.put("sinking_pang", new int[] {0x70513a, 0x83654a});
		PALETTE.put("sinking_deluge", new int[] {0x807038, 0xd5b839});
		PALETTE.put("deep_sigh", new int[] {0x7a5e45, 0x612e1d});
		PALETTE.put("poised_breathing", new int[] {0x119d9c, 0x02efe1});
		PALETTE.put("rising_power_supply", new int[] {0x274261, 0x651b1c});
		PALETTE.put("thunder_and_lightning", new int[] {0x1b3c5e, 0x6b3a3c});
		PALETTE.put("n_corp_new_league_of_nine_litt_rateurs", new int[] {0x590200, 0x760100});
		PALETTE.put("efflorescing_greenery", new int[] {0x0f6f13, 0x62af7c});
		PALETTE.put("line_3_terminus", new int[] {0xe86a10, 0x7a3008});
		PALETTE.put("bridle_of_infinity", new int[] {0x61130a, 0x900f09});
		PALETTE.put("s_e_a_c_r", new int[] {0x3e9048, 0x68b05e});
		PALETTE.put("impenetrable_path", new int[] {0x6b0074, 0x52005b});
		PALETTE.put("who_couldn_t_be_bloodfiends", new int[] {0x7d010b, 0xcb030d});
		PALETTE.put("a_beautiful_voice", new int[] {0x750c02, 0x600f06});
		PALETTE.put("the_dawn_of_green", new int[] {0x580b00, 0xa71000});
		PALETTE.put("into_a_certain_library_s_book", new int[] {0x096314, 0x17912b});
		PALETTE.put("code_purple", new int[] {0x690360, 0x97b40e});
		PALETTE.put("bearers_of_weight", new int[] {0x830a01, 0x690e05});
		PALETTE.put("line_1_madness", new int[] {0x2850d8, 0x1a2a9a});
		PALETTE.put("blessed_carnival", new int[] {0xc5579d, 0xb41cc4});
		PALETTE.put("fairy_tale", new int[] {0x006900, 0x005600});
		PALETTE.put("bloodtinged_ichthyic_odor", new int[] {0x082972, 0x0860a3});
		PALETTE.put("complete_total_extermination", new int[] {0x470254, 0x025e4e});
		PALETTE.put("the_master_of_la_manchaland", new int[] {0x670242, 0x690210});
		PALETTE.put("chachihu", new int[] {0x5b0000, 0x510000});
		PALETTE.put("a_midspring_night_s_dream_2", new int[] {0x914374, 0x722b59});
		PALETTE.put("the_b_e", new int[] {0xd3a645, 0xbe8c76});
	}

	private static void put(Scene scene, String... ids) {
		for (String id : ids) {
			THEMES.put(id, scene);
		}
	}

	static Scene of(MdData.Theme theme) {
		return theme == null ? Scene.MIRROR : THEMES.getOrDefault(theme.id(), Scene.ARENA);
	}

	/** The colours a theme's scene is built in: its picture's, else the theme's own. */
	static int[] palette(MdData.Theme theme, int fallback) {
		int[] p = theme == null ? null : PALETTE.get(theme.id());
		return p != null ? p : new int[] {fallback, fallback};
	}

	/** A hidden boss's place: the Thumb and the Middle in the Backstreets, the Index in a chapel, the Ring in its studio. */
	static Scene ofHidden(String boss) {
		if (boss.contains("Index")) {
			return Scene.CHAPEL;
		}
		if (boss.contains("Ring")) {
			return Scene.STUDIO;
		}
		return Scene.ALLEY;
	}

	/** Each run plot's scene, for the players' sky and air. */
	private static final Map<Integer, Scene> AT = new ConcurrentHashMap<>();
	private static final Map<UUID, Scene> SHOWN = new ConcurrentHashMap<>();

	// --- building -----------------------------------------------------------------------------------

	private static final class Ctx {
		final World w;
		final int cx;
		final int y;
		final int cz;
		final int h;
		final Random r;
		final Material accent;
		final Material glass;
		final Material accent2;
		final Material wool;

		Ctx(World w, int cx, int y, int cz, int h, Random r, int rgb, int rgb2) {
			this.w = w;
			this.cx = cx;
			this.y = y;
			this.cz = cz;
			this.h = h;
			this.r = r;
			this.accent = MdWorld.concrete(rgb);
			this.glass = MdWorld.glass(rgb);
			this.accent2 = MdWorld.concrete(rgb2);
			this.wool = Material.valueOf(MdWorld.concrete(rgb2).name().replace("_CONCRETE", "_WOOL"));
		}

		void set(int x, int dy, int z, Material m) {
			w.getBlockAt(cx + x, y + dy, cz + z).setType(m, false);
		}

		void lit(int x, int dy, int z, Material m) {
			Block b = w.getBlockAt(cx + x, y + dy, cz + z);
			b.setType(m, false);
			BlockData data = b.getBlockData();
			if (data instanceof Lightable l) {
				l.setLit(true);
				b.setBlockData(l, false);
			}
		}

		void column(int x, int z, int from, int to, Material m) {
			for (int dy = from; dy <= to; dy++) {
				set(x, dy, z, m);
			}
		}

		void box(int x0, int z0, int x1, int z1, int from, int to, Material m) {
			for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
				for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
					column(x, z, from, to, m);
				}
			}
		}

		Material pick(Material... options) {
			return options[r.nextInt(options.length)];
		}

		/** Each spot around the fighting ground at distance d (on the square of half width d), every {@code step}. */
		void around(int d, int step, java.util.function.BiConsumer<Integer, Integer> at) {
			for (int i = -d; i <= d; i += step) {
				at.accept(i, -d);
				at.accept(i, d);
				if (Math.abs(i) != d) {
					at.accept(-d, i);
					at.accept(d, i);
				}
			}
		}

		/** The far side of the fighting ground (z > 0), where the backdrop is seen from the entrance. */
		boolean edge(int x, int z) {
			return Math.max(Math.abs(x), Math.abs(z)) == h;
		}
	}

	/** Builds {@code scene} on run {@code slot}'s cleared plot: the floor (half width {@code h}), props and backdrop. */
	static void build(int slot, Scene scene, int h, int[] palette, long seed) {
		Location c = MdWorld.centre(slot);
		Ctx k = new Ctx(c.getWorld(), c.getBlockX(), c.getBlockY() - 1, c.getBlockZ(), h, new Random(seed), palette[0], palette[1]);
		AT.put(slot, scene);
		floor(k, scene);
		switch (scene) {
			case ALLEY -> alley(k);
			case LAB -> lab(k);
			case CHAPEL -> chapel(k);
			case SHIP -> ship(k);
			case DEEP -> deep(k);
			case MANOR -> manor(k);
			case MOOR -> moor(k);
			case CARNIVAL -> carnival(k);
			case TRAIN -> train(k);
			case KITCHEN -> kitchen(k);
			case BOUTIQUE -> boutique(k);
			case STUDIO -> studio(k);
			case EAST -> east(k);
			case FOREST -> forest(k);
			case LIBRARY -> library(k);
			case SNOW -> snow(k);
			case CASINO -> casino(k);
			case FIRE -> fire(k);
			case STORM -> storm(k);
			case QUAKE -> quake(k);
			case BLOOD -> blood(k);
			case FACTORY -> factory(k);
			case NEON -> neon(k);
			case WHALE -> whale(k);
			case THEATER -> theater(k);
			case CLOCK -> clock(k);
			case ARENA -> arena(k);
			case TILES -> tiles(k);
			default -> mirror(k);
		}
	}

	/** The fighting ground's floor, and below and around it the scene's ground to the backdrop. */
	private static void floor(Ctx k, Scene s) {
		int far = k.h + 7;
		for (int x = -far; x <= far; x++) {
			for (int z = -far; z <= far; z++) {
				boolean inside = Math.max(Math.abs(x), Math.abs(z)) <= k.h;
				Material m = ground(k, s, x, z, inside);
				if (m != null) {
					k.set(x, 0, z, m);
					k.set(x, -1, z, Material.BLACKSTONE);
				}
			}
		}
	}

	private static Material ground(Ctx k, Scene s, int x, int z, boolean inside) {
		Random r = k.r;
		return switch (s) {
			case ALLEY -> Math.abs(x) <= 3 && inside ? (z % 6 == 0 && x == 0 ? Material.WHITE_CONCRETE : Material.BLACK_CONCRETE_POWDER)
					: k.pick(Material.STONE_BRICKS, Material.CRACKED_STONE_BRICKS, Material.ANDESITE, Material.STONE_BRICKS, Material.GRAVEL);
			case LAB -> (x + z & 1) == 0 ? Material.SMOOTH_QUARTZ : Math.abs(x) % 6 == 0 || Math.abs(z) % 6 == 0 ? k.accent : Material.WHITE_CONCRETE;
			case CHAPEL -> Math.abs(x) <= 2 ? Material.RED_WOOL
					: (x + z & 1) == 0 ? Material.POLISHED_DIORITE : Material.CHISELED_STONE_BRICKS;
			case SHIP -> inside ? (Math.abs(x) % 4 == 0 ? Material.STRIPPED_SPRUCE_LOG : Material.SPRUCE_PLANKS) : Material.WATER;
			case DEEP -> k.pick(Material.PRISMARINE, Material.PRISMARINE_BRICKS, Material.DARK_PRISMARINE, Material.SAND, Material.SAND);
			case MANOR -> inside ? (Math.abs(x) <= 3 ? Material.RED_WOOL : (x + z & 1) == 0 ? Material.DARK_OAK_PLANKS : Material.SPRUCE_PLANKS)
					: Material.DARK_OAK_PLANKS;
			case MOOR -> k.pick(Material.GRASS_BLOCK, Material.COARSE_DIRT, Material.PODZOL, Material.GRASS_BLOCK, Material.MOSS_BLOCK);
			case CARNIVAL -> (Math.abs(x) / 2 + Math.abs(z) / 2 & 1) == 0 ? Material.RED_TERRACOTTA : Material.WHITE_TERRACOTTA;
			case TRAIN -> Math.abs(x) >= k.h - 1 && inside ? Material.IRON_BLOCK : Math.abs(x) <= 4 ? Material.GRAY_WOOL : Material.POLISHED_ANDESITE;
			case KITCHEN -> (x + z & 1) == 0 ? Material.WHITE_CONCRETE : Material.BLACK_CONCRETE;
			case BOUTIQUE -> Math.abs(x) <= 2 ? Material.RED_WOOL : (x + z & 1) == 0 ? Material.POLISHED_BLACKSTONE : Material.QUARTZ_BLOCK;
			case STUDIO -> r.nextInt(9) == 0 ? k.pick(Material.RED_CONCRETE, Material.YELLOW_CONCRETE, Material.BLUE_CONCRETE, Material.PURPLE_CONCRETE)
					: Material.BIRCH_PLANKS;
			case EAST -> Math.abs(x) <= 2 ? Material.SMOOTH_STONE : (x + z & 1) == 0 ? Material.DARK_OAK_PLANKS : Material.MUD_BRICKS;
			case FOREST -> k.pick(Material.GRASS_BLOCK, Material.GRASS_BLOCK, Material.MOSS_BLOCK, Material.ROOTED_DIRT, Material.PODZOL);
			case LIBRARY -> Math.abs(x) <= 3 ? Material.GREEN_WOOL : (x + z & 1) == 0 ? Material.OAK_PLANKS : Material.BIRCH_PLANKS;
			case SNOW -> k.pick(Material.SNOW_BLOCK, Material.SNOW_BLOCK, Material.SNOW_BLOCK,
					Material.PACKED_ICE, Material.SNOW_BLOCK);
			case CASINO -> Math.abs(x) <= 2 ? Material.RED_WOOL : (x + z & 1) == 0 ? Material.GREEN_WOOL : Material.LIME_WOOL;
			case FIRE -> inside ? k.pick(Material.BLACKSTONE, Material.BASALT, Material.NETHERRACK, Material.MAGMA_BLOCK) : k.pick(Material.LAVA,
					Material.NETHERRACK, Material.LAVA, Material.MAGMA_BLOCK);
			case STORM -> k.pick(Material.STONE, Material.TUFF, Material.DEEPSLATE, Material.COBBLED_DEEPSLATE);
			case QUAKE -> k.pick(Material.CRACKED_DEEPSLATE_TILES, Material.COBBLED_DEEPSLATE, Material.DEEPSLATE, Material.CRACKED_DEEPSLATE_BRICKS,
					Material.GRAVEL);
			case BLOOD -> inside ? k.pick(Material.RED_TERRACOTTA, Material.NETHER_WART_BLOCK, Material.RED_NETHER_BRICKS, Material.CRIMSON_NYLIUM)
					: k.pick(Material.RED_CONCRETE, Material.NETHER_WART_BLOCK);
			case FACTORY -> Math.abs(x) % 5 == 0 ? Material.YELLOW_CONCRETE : k.pick(Material.IRON_BLOCK, Material.POLISHED_ANDESITE,
					Material.WAXED_EXPOSED_CUT_COPPER, Material.POLISHED_ANDESITE);
			case NEON -> (Math.abs(x) / 3 + Math.abs(z) / 3 & 1) == 0 ? Material.BLACK_CONCRETE : (r.nextInt(4) == 0 ? k.glass : Material.GRAY_CONCRETE);
			case WHALE -> k.pick(Material.BONE_BLOCK, Material.WHITE_TERRACOTTA, Material.CALCITE, Material.BONE_BLOCK, Material.PINK_TERRACOTTA);
			case THEATER -> inside ? Material.DARK_OAK_PLANKS : Material.BLACK_CONCRETE;
			case TILES -> r.nextInt(14) == 0 ? Material.BLACK_CONCRETE : (x & 1) == 0 && (z & 1) == 0 ? Material.SMOOTH_QUARTZ : Material.WHITE_CONCRETE;
			case CLOCK -> (x + z & 1) == 0 ? Material.SMOOTH_SANDSTONE : Material.CUT_SANDSTONE;
			case ARENA -> Math.abs(x) % 6 == 0 || Math.abs(z) % 6 == 0 ? Material.POLISHED_ANDESITE : (x + z & 1) == 0 ? Material.GRAY_CONCRETE
					: Material.LIGHT_GRAY_CONCRETE;
			default -> (x + z & 1) == 0 ? Material.BLACK_STAINED_GLASS : Material.OBSIDIAN;
		};
	}

	/** A ring of distant towers behind the scene (the City's skyline, ruins, cliffs...): {@code lit} windows on some. */
	private static void skyline(Ctx k, int minH, int maxH, boolean lit, boolean broken, Material... walls) {
		for (int a = 0; a < 360; a += 9) {
			double rad = k.h + 9 + k.r.nextInt(5);
			int x = (int) Math.round(Math.cos(Math.toRadians(a)) * rad);
			int z = (int) Math.round(Math.sin(Math.toRadians(a)) * rad);
			if (z < -k.h + 2 && Math.abs(x) < 6) {
				continue;
			}
			int w = 1 + k.r.nextInt(2);
			int height = minH + k.r.nextInt(Math.max(1, maxH - minH));
			Material wall = walls[k.r.nextInt(walls.length)];
			for (int dx = -w; dx <= w; dx++) {
				for (int dz = -w; dz <= w; dz++) {
					int top = broken ? height - k.r.nextInt(Math.max(1, height / 2)) : height;
					for (int dy = 1; dy <= top; dy++) {
						boolean window = lit && dy % 3 == 0 && (dx + dz & 1) == 0 && k.r.nextInt(3) == 0;
						k.set(x + dx, dy, z + dz, window ? Material.GLOWSTONE : wall);
					}
				}
			}
		}
	}

	/** Cracks across the ground near the edges: dark gaps with a glow far down. */
	private static void cracks(Ctx k, Material glow, int count) {
		for (int i = 0; i < count; i++) {
			int x = k.r.nextInt(2 * k.h + 9) - k.h - 4;
			int z = k.r.nextInt(2 * k.h + 9) - k.h - 4;
			if (Math.max(Math.abs(x), Math.abs(z)) < k.h - 4) {
				continue;
			}
			int dx = k.r.nextBoolean() ? 1 : 0;
			int dz = 1 - dx;
			for (int j = 0; j < 4 + k.r.nextInt(5); j++) {
				k.set(x, 0, z, Material.AIR);
				k.set(x, -1, z, glow);
				x += dx;
				z += dz;
				if (k.r.nextInt(3) == 0) {
					x += k.r.nextBoolean() ? 1 : -1;
				}
			}
		}
	}

	// --- the scenes -----------------------------------------------------------------------------------

	/** Buildings on three sides with lit windows (the Backstreets at night), lamps, bins and crates. */
	private static void alley(Ctx k) {
		int d = k.h + 3;
		for (int x = -d; x <= d; x += 5) {
			building(k, x, d + 1, 6 + k.r.nextInt(12), k.pick(Material.BRICKS, Material.STONE_BRICKS, Material.DEEPSLATE_BRICKS, Material.MUD_BRICKS));
			building(k, -d - 1, x, 5 + k.r.nextInt(10), k.pick(Material.BRICKS, Material.POLISHED_ANDESITE, Material.DEEPSLATE_TILES));
			building(k, d + 1, x, 5 + k.r.nextInt(10), k.pick(Material.BRICKS, Material.POLISHED_ANDESITE, Material.DEEPSLATE_TILES));
		}
		k.around(k.h, 6, (x, z) -> lamp(k, x, z, Material.LANTERN));
		for (int i = 0; i < 10; i++) {
			int x = k.h - 1 - k.r.nextInt(2);
			int z = -k.h + 2 + k.r.nextInt(2 * k.h - 3);
			k.set(k.r.nextBoolean() ? x : -x, 1, z, k.pick(Material.COMPOSTER, Material.BARREL, Material.CAULDRON, Material.SMOKER));
		}
		// Neon signs in the theme's colour.
		for (int x = -d; x <= d; x += 9) {
			k.box(x, d, x + 2, d, 5, 5, k.accent);
			k.set(x + 1, 4, d, Material.SEA_LANTERN);
		}
	}

	private static void building(Ctx k, int x, int z, int height, Material wall) {
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				for (int dy = 1; dy <= height; dy++) {
					boolean shell = Math.abs(dx) == 2 || Math.abs(dz) == 2;
					if (!shell) {
						continue;
					}
					// Windows: the middle of each face, every third storey, most of them lit.
					boolean window = dy % 3 == 2 && (Math.abs(dx) < 2 || Math.abs(dz) < 2) && k.r.nextInt(3) > 0;
					k.set(x + dx, dy, z + dz, window ? (k.r.nextInt(3) == 0 ? Material.YELLOW_STAINED_GLASS : Material.GLOWSTONE) : wall);
				}
				k.set(x + dx, height + 1, z + dz, Material.SMOOTH_STONE_SLAB);
			}
		}
	}

	private static void lamp(Ctx k, int x, int z, Material light) {
		k.column(x, z, 1, 3, Material.POLISHED_BLACKSTONE_WALL);
		k.set(x, 4, z, light);
	}

	/** White corridors of glass, iron bars and lights (K Corp., Lobotomy Corp., G Corp.'s facility). */
	private static void lab(Ctx k) {
		int d = k.h + 1;
		k.around(d, 1, (x, z) -> {
			k.column(x, z, 1, 6, (x + z) % 4 == 0 ? Material.SEA_LANTERN : Math.abs(x + z) % 2 == 0 ? Material.WHITE_CONCRETE : Material.GLASS);
			k.set(x, 7, z, Material.QUARTZ_SLAB);
		});
		k.around(k.h - 1, 6, (x, z) -> {
			k.column(x, z, 1, 2, Material.IRON_BARS);
			k.set(x, 3, z, Material.LIGHTNING_ROD);
		});
		for (int i = 0; i < 6; i++) {
			int x = (k.r.nextBoolean() ? 1 : -1) * (k.h - 2);
			int z = -k.h + 3 + k.r.nextInt(2 * k.h - 5);
			k.set(x, 1, z, k.pick(Material.BREWING_STAND, Material.CAULDRON, Material.IRON_BLOCK, Material.OBSERVER));
		}
		// Containment cells behind the glass on the far side.
		for (int x = -k.h; x <= k.h; x += 7) {
			k.box(x - 2, d + 1, x + 2, d + 5, 0, 0, Material.SMOOTH_QUARTZ);
			k.box(x - 2, d + 5, x + 2, d + 5, 1, 5, Material.WHITE_CONCRETE);
			k.set(x, 1, d + 3, k.accent);
		}
	}

	/** Stone pillars, stained glass in the theme's colour, candles and pews (N Corp.'s inquisitors). */
	private static void chapel(Ctx k) {
		int d = k.h + 1;
		k.around(d, 1, (x, z) -> {
			k.column(x, z, 1, 9, (Math.abs(x) + Math.abs(z)) % 4 == 0 ? Material.CHISELED_STONE_BRICKS : Math.abs(x + z) % 3 == 1 ? k.glass
					: Material.STONE_BRICKS);
		});
		k.around(k.h - 1, 4, (x, z) -> {
			k.column(x, z, 1, 6, Material.POLISHED_DIORITE);
			k.set(x, 7, z, Material.SOUL_LANTERN);
		});
		for (int x = -k.h + 2; x <= k.h - 2; x += 2) {
			if (Math.abs(x) > 3) {
				k.set(x, 1, -k.h + 2, Material.SPRUCE_STAIRS);
			}
		}
		// The altar: a great nail on the far wall.
		k.box(-2, k.h - 1, 2, k.h - 1, 1, 1, Material.POLISHED_DIORITE);
		k.column(0, k.h, 1, 8, Material.IRON_BLOCK);
		k.box(-2, k.h, 2, k.h, 7, 7, Material.IRON_BLOCK);
		for (int x = -2; x <= 2; x += 2) {
			k.lit(x, 2, k.h - 1, Material.CANDLE);
		}
	}

	/** A whaling ship's deck on dark water: rails, masts with sails, barrels, harpoons. */
	private static void ship(Ctx k) {
		k.around(k.h, 1, (x, z) -> k.set(x, 1, z, Material.SPRUCE_FENCE));
		for (int z : new int[] {-k.h / 2, k.h / 2}) {
			int x = z < 0 ? k.h - 3 : -k.h + 3;
			k.column(x, z, 1, 14, Material.STRIPPED_SPRUCE_LOG);
			for (int dy = 6; dy <= 12; dy++) {
				for (int dz = -3; dz <= 3; dz++) {
					if (dy == 6 || dy == 12 || Math.abs(dz) < 3) {
						k.set(x + 1, dy, z + dz, Material.WHITE_WOOL);
					}
				}
			}
		}
		for (int i = 0; i < 10; i++) {
			int x = (k.r.nextBoolean() ? 1 : -1) * (k.h - 1 - k.r.nextInt(2));
			int z = -k.h + 2 + k.r.nextInt(2 * k.h - 3);
			k.set(x, 1, z, k.pick(Material.BARREL, Material.CHEST, Material.SPRUCE_TRAPDOOR, Material.IRON_CHAIN));
		}
		k.around(k.h, 7, (x, z) -> k.set(x, 2, z, Material.LANTERN));
		// The bow rising beyond the far rail, and the Lake's fog.
		k.box(-3, k.h + 1, 3, k.h + 3, 0, 2, Material.SPRUCE_PLANKS);
		k.column(0, k.h + 4, 1, 4, Material.STRIPPED_SPRUCE_LOG);
	}

	/** The bottom of the Lake: kelp, coral, sea lanterns, bones and pale light. */
	private static void deep(Ctx k) {
		skyline(k, 5, 16, false, true, Material.PRISMARINE_BRICKS, Material.DARK_PRISMARINE, Material.PRISMARINE);
		for (int i = 0; i < 140; i++) {
			int x = k.r.nextInt(2 * k.h + 13) - k.h - 6;
			int z = k.r.nextInt(2 * k.h + 13) - k.h - 6;
			if (Math.max(Math.abs(x), Math.abs(z)) < k.h - 2) {
				continue;
			}
			int tall = 2 + k.r.nextInt(6);
			k.column(x, z, 1, tall, k.pick(Material.DRIED_KELP_BLOCK, Material.PRISMARINE_WALL, Material.BONE_BLOCK, Material.TUBE_CORAL_BLOCK,
					Material.BRAIN_CORAL_BLOCK));
			k.set(x, tall + 1, z, k.r.nextInt(3) == 0 ? Material.SEA_LANTERN : Material.TUBE_CORAL_FAN);
		}
		k.around(k.h - 1, 5, (x, z) -> k.set(x, 1, z, Material.SEA_PICKLE));
	}

	/** The Wuthering manor's hall: dark wood walls, bookshelves, candles and a chandelier, windows to the storm. */
	private static void manor(Ctx k) {
		int d = k.h + 1;
		k.around(d, 1, (x, z) -> {
			k.column(x, z, 1, 8, (Math.abs(x) + Math.abs(z)) % 5 == 0 ? Material.DARK_OAK_LOG : (Math.abs(x + z) % 5 == 2 ? Material.GRAY_STAINED_GLASS
					: Material.DARK_OAK_PLANKS));
			k.set(x, 9, z, Material.DARK_OAK_SLAB);
		});
		k.around(k.h - 1, 1, (x, z) -> {
			if ((Math.abs(x) + Math.abs(z)) % 3 != 0) {
				k.column(x, z, 1, 2, Material.BOOKSHELF);
			} else {
				k.lit(x, 1, z, Material.CANDLE);
			}
		});
		for (int[] p : new int[][] {{-6, -6}, {6, -6}, {-6, 6}, {6, 6}}) {
			k.column(p[0], p[1], 7, 8, Material.IRON_CHAIN);
			k.set(p[0], 6, p[1], Material.LANTERN);
		}
		k.box(-3, k.h - 1, 3, k.h - 1, 1, 3, Material.POLISHED_BLACKSTONE_BRICKS);
		k.set(0, 4, k.h - 1, k.accent);
	}

	/** A windswept heath at night: tall grass, dead bushes, stones, a lone tree. */
	private static void moor(Ctx k) {
		// Low hills all round and a ruined wall.
		for (int a = 0; a < 360; a += 4) {
			double rad = k.h + 7 + k.r.nextInt(5);
			int x = (int) Math.round(Math.cos(Math.toRadians(a)) * rad);
			int z = (int) Math.round(Math.sin(Math.toRadians(a)) * rad);
			k.column(x, z, 1, 1 + k.r.nextInt(5), k.pick(Material.GRASS_BLOCK, Material.MOSS_BLOCK, Material.DIRT));
		}
		for (int i = 0; i < 8; i++) {
			int a = k.r.nextInt(360);
			double rad = k.h + 3 + k.r.nextInt(4);
			tree(k, (int) (Math.cos(Math.toRadians(a)) * rad), (int) (Math.sin(Math.toRadians(a)) * rad), Material.DARK_OAK_LOG, null, 5 + k.r.nextInt(5));
		}
		for (int i = 0; i < 120; i++) {
			int x = k.r.nextInt(2 * k.h + 13) - k.h - 6;
			int z = k.r.nextInt(2 * k.h + 13) - k.h - 6;
			if (Math.max(Math.abs(x), Math.abs(z)) < k.h - 3) {
				continue;
			}
			k.set(x, 1, z, k.pick(Material.TALL_GRASS, Material.DEAD_BUSH, Material.SHORT_GRASS, Material.MOSSY_COBBLESTONE, Material.FERN));
		}
		tree(k, -k.h - 3, k.h + 2, Material.DARK_OAK_LOG, null, 9);
		tree(k, k.h + 4, -k.h + 4, Material.DARK_OAK_LOG, null, 7);
		k.box(k.h - 2, k.h + 3, k.h + 2, k.h + 5, 1, 2, Material.COBBLESTONE);
	}

	/** La Manchaland: striped tents, lights, a carousel's top and stalls. */
	private static void carnival(Ctx k) {
		int d = k.h + 3;
		for (int x = -d; x <= d; x += 7) {
			tent(k, x, d + 1);
			tent(k, -d - 1, x);
			tent(k, d + 1, x);
		}
		k.around(k.h, 3, (x, z) -> {
			k.column(x, z, 1, 3, Material.OAK_FENCE);
			k.set(x, 4, z, k.pick(Material.REDSTONE_LAMP, Material.SHROOMLIGHT, Material.GLOWSTONE));
		});
		// A carousel roof on the far side.
		for (int a = 0; a < 32; a++) {
			double t = a * Math.PI * 2 / 32;
			int x = (int) Math.round(Math.cos(t) * 4);
			int z = d + 6 + (int) Math.round(Math.sin(t) * 4);
			k.set(x, 7, z, a % 2 == 0 ? Material.RED_WOOL : Material.YELLOW_WOOL);
			if (a % 4 == 0) {
				k.column(x, z, 1, 6, Material.GOLD_BLOCK);
			}
		}
		k.column(0, d + 6, 1, 9, Material.GOLD_BLOCK);
	}

	private static void tent(Ctx k, int x, int z) {
		for (int dy = 0; dy <= 4; dy++) {
			int r = 3 - dy * 3 / 4;
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.abs(dx) == r || Math.abs(dz) == r || dy == 4) {
						k.set(x + dx, 1 + dy, z + dz, (dx + dz & 1) == 0 ? Material.RED_WOOL : Material.WHITE_WOOL);
					}
				}
			}
		}
		k.set(x, 6, z, Material.YELLOW_BANNER);
	}

	/** A train car: seats along both sides, poles, windows to the dark and lights overhead. */
	private static void train(Ctx k) {
		int d = k.h + 1;
		for (int z = -d; z <= d; z++) {
			for (int side : new int[] {-1, 1}) {
				int x = side * d;
				k.column(x, z, 1, 6, z % 4 == 0 ? Material.IRON_BLOCK : (z % 4 == 2 ? k.glass : Material.LIGHT_GRAY_CONCRETE));
				k.set(x, 6, z, k.accent);
				k.set(x - side, 1, z, z % 4 == 0 ? Material.IRON_BLOCK : k.wool);
			}
		}
		for (int x = -d; x <= d; x++) {
			for (int z = -d; z <= d; z++) {
				k.set(x, 7, z, Math.abs(x) <= 1 && z % 3 == 0 ? Material.SEA_LANTERN : Math.abs(x) == 3 ? k.accent : Material.LIGHT_GRAY_CONCRETE);
			}
			k.column(x, d, 1, 6, Math.abs(x) <= 1 ? Material.IRON_BLOCK : Material.LIGHT_GRAY_CONCRETE);
			k.column(x, -d, 1, 6, Material.LIGHT_GRAY_CONCRETE);
		}
		for (int z = -k.h + 3; z <= k.h - 3; z += 6) {
			k.column(-k.h + 3, z, 1, 6, Material.IRON_BARS);
			k.column(k.h - 3, z, 1, 6, Material.IRON_BARS);
		}
	}

	/** A kitchen and diner: counters, stoves and smokers, tables and hanging lights. */
	private static void kitchen(Ctx k) {
		int d = k.h + 1;
		k.around(d, 1, (x, z) -> k.column(x, z, 1, 6, Math.abs(x + z) % 6 == 0 ? Material.WHITE_STAINED_GLASS : Material.WHITE_TERRACOTTA));
		k.around(k.h - 1, 1, (x, z) -> {
			if (z > 0 || Math.abs(x) == k.h - 1) {
				k.set(x, 1, z, (Math.abs(x) + Math.abs(z)) % 4 == 0 ? Material.SMOKER : (Math.abs(x) + Math.abs(z)) % 4 == 2 ? Material.FURNACE
						: Material.SMOOTH_STONE);
			}
		});
		for (int x = -k.h + 3; x <= k.h - 3; x += 6) {
			k.set(x, 1, -k.h + 3, Material.OAK_FENCE);
			k.set(x, 2, -k.h + 3, Material.OAK_PRESSURE_PLATE);
			k.set(x, 5, -k.h + 3, Material.LANTERN);
		}
		k.box(-3, k.h + 2, 3, k.h + 2, 1, 5, Material.RED_TERRACOTTA);
		k.set(0, 4, k.h + 1, k.accent);
		for (int x = -k.h; x <= k.h; x++) {
			for (int z = -k.h; z <= k.h; z++) {
				k.set(x, 7, z, (x % 5 == 0 && z % 5 == 0) ? Material.SEA_LANTERN : Material.WHITE_TERRACOTTA);
			}
		}
		for (int z = -k.h + 3; z <= k.h - 3; z += 5) {
			for (int side : new int[] {-1, 1}) {
				k.set(side * (k.h - 3), 1, z, Material.OAK_FENCE);
				k.set(side * (k.h - 3), 2, z, Material.WHITE_CARPET);
				k.set(side * (k.h - 4), 1, z, Material.OAK_STAIRS);
			}
		}
	}

	/** A boutique: mirrors, mannequins (armour stands are not used: dark posts with a head), racks and a runway. */
	private static void boutique(Ctx k) {
		int d = k.h + 1;
		k.around(d, 1, (x, z) -> k.column(x, z, 1, 7, (Math.abs(x) + Math.abs(z)) % 4 == 0 ? Material.GOLD_BLOCK : Material.BLACK_CONCRETE));
		k.around(k.h - 1, 3, (x, z) -> {
			k.column(x, z, 1, 1, Material.POLISHED_BLACKSTONE_WALL);
			k.set(x, 2, z, k.pick(Material.RED_WOOL, Material.BLACK_WOOL, Material.WHITE_WOOL, k.accent));
			k.set(x, 3, z, Material.SKELETON_SKULL);
		});
		for (int x = -k.h; x <= k.h; x += 4) {
			k.column(x, d, 2, 5, Material.TINTED_GLASS);
			k.set(x, 6, d, Material.GLOWSTONE);
		}
	}

	/** The Ring's studio: canvases on easels, paint spilt, frames on the walls. */
	private static void studio(Ctx k) {
		int d = k.h + 1;
		k.around(d, 1, (x, z) -> k.column(x, z, 1, 7, Material.WHITE_CONCRETE));
		k.around(d, 4, (x, z) -> {
			k.box(x - 1, z, x + 1, z, 3, 5, k.pick(Material.RED_CONCRETE, Material.YELLOW_CONCRETE, Material.BLUE_CONCRETE, Material.PURPLE_CONCRETE,
					Material.ORANGE_CONCRETE, k.accent));
		});
		for (int i = 0; i < 12; i++) {
			int x = (k.r.nextBoolean() ? 1 : -1) * (k.h - 1 - k.r.nextInt(3));
			int z = -k.h + 2 + k.r.nextInt(2 * k.h - 3);
			k.set(x, 1, z, Material.OAK_FENCE);
			k.set(x, 2, z, k.pick(Material.WHITE_WOOL, Material.RED_WOOL, Material.BLACK_WOOL));
		}
	}

	/** The East: red pillars and roofs, paper lanterns, bamboo and a moon gate. */
	private static void east(Ctx k) {
		int d = k.h + 2;
		k.around(d, 4, (x, z) -> {
			k.column(x, z, 1, 6, Material.RED_TERRACOTTA);
			k.set(x, 7, z, Material.DARK_OAK_SLAB);
		});
		k.around(d, 1, (x, z) -> k.set(x, 7, z, Material.DEEPSLATE_TILE_SLAB));
		k.around(k.h - 1, 5, (x, z) -> {
			k.column(x, z, 1, 1, Material.STONE_BRICK_WALL);
			k.set(x, 2, z, Material.LANTERN);
		});
		for (int i = 0; i < 40; i++) {
			int x = k.r.nextInt(2 * d + 9) - d - 4;
			int z = d + 2 + k.r.nextInt(4);
			k.column(x, k.r.nextBoolean() ? z : -z, 1, 3 + k.r.nextInt(6), Material.BAMBOO_BLOCK);
		}
		// A moon gate on the far side.
		for (int a = 0; a <= 20; a++) {
			double t = Math.PI * a / 20;
			k.set((int) Math.round(Math.cos(t) * 4), 1 + (int) Math.round(Math.sin(t) * 5), d, Material.POLISHED_DEEPSLATE);
		}
		k.box(-5, d, 5, d, 1, 1, Material.POLISHED_DEEPSLATE);
		// A pagoda beyond it: three storeys of red walls under wide dark roofs, lit windows.
		int pz = d + 7;
		for (int storey = 0; storey < 3; storey++) {
			int half = 5 - storey;
			int base = 1 + storey * 5;
			k.box(-half + 1, pz - half + 1, half - 1, pz + half - 1, base, base + 3, Material.RED_TERRACOTTA);
			for (int x = -half + 1; x <= half - 1; x += 2) {
				k.set(x, base + 2, pz - half + 1, Material.GLOWSTONE);
			}
			k.box(-half - 1, pz - half - 1, half + 1, pz + half + 1, base + 4, base + 4, Material.DEEPSLATE_TILES);
			k.set(-half - 1, base + 5, pz - half - 1, Material.GOLD_BLOCK);
			k.set(half + 1, base + 5, pz - half - 1, Material.GOLD_BLOCK);
		}
		k.column(0, pz, 16, 18, Material.GOLD_BLOCK);
	}

	/** A forest or garden: trees, flowers, glow berries and mushrooms. */
	private static void forest(Ctx k) {
		for (int i = 0; i < 34; i++) {
			int a = k.r.nextInt(360);
			double rad = k.h + 2 + k.r.nextInt(9);
			tree(k, (int) (Math.cos(Math.toRadians(a)) * rad), (int) (Math.sin(Math.toRadians(a)) * rad), k.pick(Material.OAK_LOG, Material.BIRCH_LOG),
					k.pick(Material.AZALEA_LEAVES, Material.FLOWERING_AZALEA_LEAVES, Material.OAK_LEAVES),
					5 + k.r.nextInt(4));
		}
		for (int i = 0; i < 70; i++) {
			int x = k.r.nextInt(2 * k.h + 1) - k.h;
			int z = k.r.nextInt(2 * k.h + 1) - k.h;
			if (Math.max(Math.abs(x), Math.abs(z)) >= k.h - 3) {
				k.set(x, 1, z, k.pick(Material.POPPY, Material.DANDELION, Material.CORNFLOWER, Material.RED_MUSHROOM, Material.LILY_OF_THE_VALLEY,
						Material.SHORT_GRASS));
			}
		}
	}

	private static void tree(Ctx k, int x, int z, Material log, Material leaves, int height) {
		k.column(x, z, 1, height, log);
		if (leaves == null) {
			// Bare branches.
			k.set(x + 1, height - 1, z, log);
			k.set(x - 1, height - 2, z + 1, log);
			return;
		}
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				for (int dy = height - 1; dy <= height + 1; dy++) {
					if (Math.abs(dx) + Math.abs(dz) + Math.abs(dy - height) <= 3 && !(dx == 0 && dz == 0 && dy <= height)) {
						k.set(x + dx, dy, z + dz, leaves);
					}
				}
			}
		}
	}

	/** A library: walls of shelves, reading lamps and tables. */
	private static void library(Ctx k) {
		int d = k.h + 1;
		k.around(d, 1, (x, z) -> {
			k.column(x, z, 1, 9, Material.BOOKSHELF);
			k.set(x, 10, z, Material.DARK_OAK_PLANKS);
		});
		k.around(k.h - 1, 4, (x, z) -> {
			k.column(x, z, 1, 3, Material.BOOKSHELF);
			k.set(x, 4, z, Material.LANTERN);
		});
		k.set(0, 1, k.h - 2, Material.LECTERN);
	}

	/** Snow: firs, drifts and ice, a cabin on the far side. */
	private static void snow(Ctx k) {
		for (int i = 0; i < 34; i++) {
			int a = k.r.nextInt(360);
			double rad = k.h + 2 + k.r.nextInt(10);
			int x = (int) (Math.cos(Math.toRadians(a)) * rad);
			int z = (int) (Math.sin(Math.toRadians(a)) * rad);
			int height = 5 + k.r.nextInt(7);
			k.column(x, z, 1, height, Material.SPRUCE_LOG);
			for (int dy = 3; dy <= height + 1; dy++) {
				int r = Math.max(0, (height + 1 - dy) / 2);
				for (int dx = -r; dx <= r; dx++) {
					for (int dz = -r; dz <= r; dz++) {
						if (!(dx == 0 && dz == 0) && Math.abs(dx) + Math.abs(dz) <= r + 1) {
							k.set(x + dx, dy, z + dz, dy % 2 == 0 ? Material.SPRUCE_LEAVES : Material.SNOW_BLOCK);
						}
					}
				}
			}
		}
		k.around(k.h, 1, (x, z) -> {
			if (k.r.nextInt(3) == 0) {
				k.set(x, 1, z, Material.SNOW);
			}
		});
		k.box(-3, k.h + 3, 3, k.h + 7, 1, 4, Material.SPRUCE_PLANKS);
		k.box(-3, k.h + 3, 3, k.h + 7, 5, 5, Material.SNOW_BLOCK);
		k.set(0, 2, k.h + 3, Material.ORANGE_STAINED_GLASS);
	}

	/** A casino floor: tables, chandeliers, gold and slot machines along the walls. */
	private static void casino(Ctx k) {
		int d = k.h + 1;
		k.around(d, 1, (x, z) -> k.column(x, z, 1, 7, (Math.abs(x) + Math.abs(z)) % 3 == 0 ? Material.GOLD_BLOCK : Material.RED_TERRACOTTA));
		k.around(k.h - 1, 2, (x, z) -> {
			k.set(x, 1, z, Material.DISPENSER);
			k.set(x, 2, z, k.pick(Material.REDSTONE_LAMP, Material.GLOWSTONE, Material.SHROOMLIGHT));
		});
		for (int[] p : new int[][] {{-6, -6}, {6, -6}, {-6, 6}, {6, 6}, {0, 0}}) {
			k.set(p[0], 8, p[1], Material.GOLD_BLOCK);
			k.set(p[0], 7, p[1], Material.GLOWSTONE);
		}
	}

	/** Burning ground: fire, lava pools around, basalt spires, embers. */
	private static void fire(Ctx k) {
		skyline(k, 6, 15, false, true, Material.BLACKSTONE, Material.CRACKED_POLISHED_BLACKSTONE_BRICKS, Material.BASALT);
		cracks(k, Material.LAVA, 14);
		for (int i = 0; i < 18; i++) {
			int a = k.r.nextInt(360);
			double rad = k.h + 2 + k.r.nextInt(5);
			int x = (int) (Math.cos(Math.toRadians(a)) * rad);
			int z = (int) (Math.sin(Math.toRadians(a)) * rad);
			k.column(x, z, 1, 3 + k.r.nextInt(8), Material.BASALT);
		}
		k.around(k.h, 2, (x, z) -> k.set(x, 1, z, k.r.nextBoolean() ? Material.FIRE : Material.MAGMA_BLOCK));
	}

	/** A storm: lightning rods on stone towers, copper and the rain. */
	private static void storm(Ctx k) {
		skyline(k, 8, 20, true, false, Material.DEEPSLATE_TILES, Material.POLISHED_DEEPSLATE, Material.TUFF_BRICKS);
		k.around(k.h + 2, 6, (x, z) -> {
			int height = 4 + k.r.nextInt(6);
			k.column(x, z, 1, height, k.pick(Material.TUFF_BRICKS, Material.COPPER_BLOCK, Material.OXIDIZED_COPPER));
			k.set(x, height + 1, z, Material.LIGHTNING_ROD);
		});
		k.around(k.h, 4, (x, z) -> k.set(x, 1, z, Material.REDSTONE_LAMP));
	}

	/** Shaking ground: cracked rock, fallen pillars, rubble. */
	private static void quake(Ctx k) {
		skyline(k, 6, 16, false, true, Material.CRACKED_STONE_BRICKS, Material.COBBLED_DEEPSLATE, Material.CRACKED_DEEPSLATE_BRICKS);
		cracks(k, Material.MAGMA_BLOCK, 18);
		for (int i = 0; i < 12; i++) {
			int a = k.r.nextInt(360);
			double rad = k.h + 1 + k.r.nextInt(5);
			int x = (int) (Math.cos(Math.toRadians(a)) * rad);
			int z = (int) (Math.sin(Math.toRadians(a)) * rad);
			k.column(x, z, 1, 1 + k.r.nextInt(6), k.pick(Material.CRACKED_STONE_BRICKS, Material.COBBLED_DEEPSLATE, Material.TUFF));
		}
		for (int i = 0; i < 40; i++) {
			int x = k.r.nextInt(2 * k.h + 1) - k.h;
			int z = k.r.nextInt(2 * k.h + 1) - k.h;
			if (Math.max(Math.abs(x), Math.abs(z)) >= k.h - 2) {
				k.set(x, 1, z, k.pick(Material.COBBLESTONE_SLAB, Material.GRAVEL, Material.COBBLED_DEEPSLATE_SLAB));
			}
		}
	}

	/** Blood: crimson growths, red water, bones. */
	private static void blood(Ctx k) {
		skyline(k, 5, 14, false, true, Material.NETHER_WART_BLOCK, Material.RED_NETHER_BRICKS, Material.CRIMSON_HYPHAE);
		for (int i = 0; i < 30; i++) {
			int a = k.r.nextInt(360);
			double rad = k.h + 1 + k.r.nextInt(6);
			int x = (int) (Math.cos(Math.toRadians(a)) * rad);
			int z = (int) (Math.sin(Math.toRadians(a)) * rad);
			k.column(x, z, 1, 1 + k.r.nextInt(5), k.pick(Material.NETHER_WART_BLOCK, Material.CRIMSON_STEM, Material.BONE_BLOCK));
			k.set(x, 0, z, Material.CRIMSON_NYLIUM);
		}
		k.around(k.h, 3, (x, z) -> k.set(x, 1, z, Material.CRIMSON_ROOTS));
	}

	/** A factory: machines, chains from the ceiling beams, pipes, hazard stripes. */
	private static void factory(Ctx k) {
		int d = k.h + 1;
		k.around(d, 1, (x, z) -> k.column(x, z, 1, 9, (Math.abs(x) + Math.abs(z)) % 6 == 0 ? Material.IRON_BLOCK : Material.WAXED_COPPER_BLOCK));
		for (int x = -d; x <= d; x += 4) {
			for (int z = -d; z <= d; z++) {
				k.set(x, 10, z, Material.IRON_BLOCK);
			}
		}
		k.around(k.h - 1, 4, (x, z) -> {
			k.set(x, 1, z, k.pick(Material.BLAST_FURNACE, Material.PISTON, Material.OBSERVER, Material.DISPENSER));
			k.column(x, z, 2, 9, Material.IRON_CHAIN);
		});
		k.around(k.h - 2, 8, (x, z) -> k.set(x, 9, z, Material.SEA_LANTERN));
	}

	/** The Mirror: black glass, crying obsidian and the theme's colour (the Sins' and Emotions' themes). */
	private static void mirror(Ctx k) {
		skyline(k, 6, 18, false, true, Material.OBSIDIAN, Material.CRYING_OBSIDIAN, k.glass);
		k.around(k.h + 2, 3, (x, z) -> {
			int height = 3 + k.r.nextInt(8);
			k.column(x, z, 1, height, k.pick(Material.CRYING_OBSIDIAN, Material.OBSIDIAN, Material.BLACK_STAINED_GLASS));
			k.set(x, height + 1, z, k.glass);
		});
		k.around(k.h, 4, (x, z) -> k.set(x, 1, z, Material.END_ROD));
	}

	/** A night club (Lake World): neon tubes, lasers, a stage of lights, the dance floor. */
	private static void neon(Ctx k) {
		int d = k.h + 1;
		k.around(d, 1, (x, z) -> k.column(x, z, 1, 8, Material.BLACK_CONCRETE));
		k.around(d, 3, (x, z) -> k.column(x, z, 1, 7, (Math.abs(x) + Math.abs(z)) % 2 == 0 ? k.glass : MdWorld.glass(0xD040E0)));
		k.around(k.h - 1, 4, (x, z) -> {
			k.column(x, z, 1, 3, Material.END_ROD);
			k.set(x, 4, z, k.pick(Material.PURPLE_STAINED_GLASS, Material.MAGENTA_STAINED_GLASS, Material.CYAN_STAINED_GLASS));
		});
		for (int x = -d; x <= d; x++) {
			for (int z = -d; z <= d; z++) {
				k.set(x, 9, z, (x + z) % 7 == 0 ? Material.SEA_LANTERN : Material.BLACK_CONCRETE);
			}
		}
		k.box(-5, k.h - 2, 5, k.h - 1, 1, 1, Material.POLISHED_BLACKSTONE);
		k.box(-5, k.h, 5, k.h, 2, 6, k.accent);
	}

	/** Inside the Pallid Whale: ribs of bone arching over, pale walls with red veins. */
	private static void whale(Ctx k) {
		int d = k.h + 1;
		for (int z = -d; z <= d; z += 4) {
			for (int a = 0; a <= 36; a++) {
				double t = Math.PI * a / 36;
				int x = (int) Math.round(Math.cos(t) * (d + 1));
				int y = 1 + (int) Math.round(Math.sin(t) * (d - 2));
				k.set(x, y, z, Material.BONE_BLOCK);
			}
		}
		k.around(d + 1, 1, (x, z) -> k.column(x, z, 1, 4 + k.r.nextInt(3), k.r.nextInt(5) == 0 ? Material.RED_TERRACOTTA : Material.CALCITE));
		for (int i = 0; i < 30; i++) {
			int x = k.r.nextInt(2 * k.h + 1) - k.h;
			int z = k.r.nextInt(2 * k.h + 1) - k.h;
			if (Math.max(Math.abs(x), Math.abs(z)) >= k.h - 2) {
				k.set(x, 0, z, k.r.nextBoolean() ? Material.RED_TERRACOTTA : Material.NETHER_WART_BLOCK);
			}
		}
		// The heart on the far side.
		k.box(-2, d + 2, 2, d + 3, 1, 5, Material.RED_CONCRETE);
		k.set(0, 3, d + 1, Material.SHROOMLIGHT);
	}

	/** A theatre (Nocturnal Sweeping): rows of red seats facing a stage with curtains and footlights. */
	private static void theater(Ctx k) {
		int d = k.h + 1;
		k.around(d, 1, (x, z) -> k.column(x, z, 1, 10, Material.DARK_OAK_PLANKS));
		k.box(-k.h, d - 1, k.h, d - 1, 1, 9, Material.RED_WOOL);
		for (int x = -k.h + 1; x <= k.h - 1; x += 2) {
			k.set(x, 1, k.h / 2 + 1, Material.GLOWSTONE);
		}
		for (int z = -d - 6; z <= -k.h + 1; z += 2) {
			for (int x = -k.h; x <= k.h; x++) {
				if (Math.abs(x) > 2) {
					k.set(x, 1 + Math.max(0, (-k.h + 1 - z) / 2), z, Material.RED_WOOL);
				}
			}
		}
		k.around(k.h - 1, 6, (x, z) -> k.set(x, 8, z, Material.LANTERN));
	}

	/** Inside a clock tower: great gears on the walls, a clock face and brass. */
	private static void clock(Ctx k) {
		int d = k.h + 1;
		k.around(d, 1, (x, z) -> k.column(x, z, 1, 12, (Math.abs(x) + Math.abs(z)) % 4 == 0 ? Material.CUT_SANDSTONE : Material.SMOOTH_SANDSTONE));
		for (int[] g : new int[][] {{-8, d, 7, 4}, {8, d, 5, 3}, {-d, -4, 6, 3}, {d, 3, 6, 4}}) {
			for (int a = 0; a < 40; a++) {
				double t = Math.PI * 2 * a / 40;
				int rr = g[3];
				int u = (int) Math.round(Math.cos(t) * rr);
				int v = g[2] + (int) Math.round(Math.sin(t) * rr);
				if (Math.abs(g[1]) == d && Math.abs(g[0]) != d) {
					k.set(g[0] + u, v, g[1] - Integer.signum(g[1]), a % 4 == 0 ? Material.COPPER_BLOCK : Material.CUT_COPPER);
				} else {
					k.set(g[0] - Integer.signum(g[0]), v, g[1] + u, a % 4 == 0 ? Material.COPPER_BLOCK : Material.CUT_COPPER);
				}
			}
		}
		// The clock face on the far wall.
		for (int a = 0; a < 48; a++) {
			double t = Math.PI * 2 * a / 48;
			k.set((int) Math.round(Math.cos(t) * 5), 8 + (int) Math.round(Math.sin(t) * 5), d - 1, Material.GOLD_BLOCK);
		}
		k.column(0, d - 1, 8, 12, Material.BLACK_CONCRETE);
		k.box(0, d - 1, 3, d - 1, 8, 8, Material.BLACK_CONCRETE);
		k.around(k.h - 1, 6, (x, z) -> k.set(x, 1, z, Material.LANTERN));
	}

	/** The Sin and Status themes' containment arena: steel panels in the picture's colours, warning tape, alarm lights. */
	private static void arena(Ctx k) {
		int d = k.h + 1;
		k.around(d, 1, (x, z) -> {
			for (int dy = 1; dy <= 9; dy++) {
				Material m;
				if (dy == 2 || dy == 8) {
					m = (Math.abs(x + z) + dy) % 2 == 0 ? Material.YELLOW_CONCRETE : Material.BLACK_CONCRETE;
				} else if (dy >= 3 && dy <= 7) {
					m = (Math.abs(x) + Math.abs(z)) % 6 == 0 ? Material.IRON_BLOCK : dy % 2 == 0 ? k.accent : k.accent2;
				} else {
					m = Material.POLISHED_ANDESITE;
				}
				k.set(x, dy, z, m);
			}
		});
		k.around(d, 6, (x, z) -> {
			k.set(x, 10, z, Material.REDSTONE_LAMP);
			k.lit(x, 10, z, Material.REDSTONE_LAMP);
		});
		k.around(k.h - 1, 6, (x, z) -> {
			k.column(x, z, 1, 1, Material.IRON_BLOCK);
			k.set(x, 2, z, Material.LIGHTNING_ROD);
		});
		// A big X of steel on the far wall, as on the cards.
		for (int i = -4; i <= 4; i++) {
			k.set(i, 5 + i, d - 1, Material.IRON_BLOCK);
			k.set(i, 5 - i, d - 1, Material.IRON_BLOCK);
		}
	}

	/** A cracked hall of white tiles overgrown with yellow, as in Tearful Things. */
	private static void tiles(Ctx k) {
		cracks(k, Material.BLACK_CONCRETE, 22);
		for (int i = 0; i < 60; i++) {
			int a = k.r.nextInt(360);
			double rad = k.h - 1 + k.r.nextInt(9);
			int x = (int) (Math.cos(Math.toRadians(a)) * rad);
			int z = (int) (Math.sin(Math.toRadians(a)) * rad);
			int tall = 1 + k.r.nextInt(4);
			k.column(x, z, 1, tall, k.pick(Material.YELLOW_WOOL, Material.YELLOW_CONCRETE_POWDER, Material.HAY_BLOCK, Material.GOLD_BLOCK));
			k.set(x, tall + 1, z, k.pick(Material.SUNFLOWER, Material.DANDELION, Material.TORCHFLOWER));
		}
		skyline(k, 4, 12, false, true, Material.QUARTZ_BRICKS, Material.WHITE_CONCRETE, Material.CALCITE);
	}

	// --- the players' sky and air --------------------------------------------------------------

	/** Every second: players in a run plot see its scene's time and weather and the particles in its air. */
	static void tick() {
		if (CaduceusListener.now() % 20 != 0) {
			return;
		}
		World md = Bukkit.getWorld(MdWorld.NAME);
		for (Player p : Bukkit.getOnlinePlayers()) {
			Scene scene = null;
			if (md != null && p.getWorld() == md) {
				MdRun run = MdRun.of(p);
				scene = run == null ? null : AT.get(run.slot);
			}
			Scene shown = SHOWN.get(p.getUniqueId());
			if (scene == null) {
				if (shown != null) {
					p.resetPlayerTime();
					p.resetPlayerWeather();
					SHOWN.remove(p.getUniqueId());
				}
				continue;
			}
			if (shown != scene) {
				p.setPlayerTime(scene.time, false);
				p.setPlayerWeather(scene.rain ? WeatherType.DOWNFALL : WeatherType.CLEAR);
				SHOWN.put(p.getUniqueId(), scene);
			}
			if (scene.air != null) {
				Location at = p.getLocation().add(0, 2, 0);
				try {
					p.spawnParticle(scene.air, at, 18, 9, 4, 9, 0.01);
				} catch (IllegalArgumentException e) {
					// A particle that needs data: dust in the scene's tone instead.
					p.spawnParticle(Particle.DUST, at, 12, 9, 4, 9, 0, new Particle.DustOptions(Color.GRAY, 1.0F));
				}
			}
		}
	}

	static void forget(int slot) {
		AT.remove(slot);
	}
}
