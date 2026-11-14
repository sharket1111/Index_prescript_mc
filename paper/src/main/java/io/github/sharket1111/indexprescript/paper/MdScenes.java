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
		TILES(13000, false, Particle.FALLING_SPORE_BLOSSOM), ICE(18000, false, Particle.SNOWFLAKE), CANDLES(18000, false, Particle.SMALL_FLAME),
		CAVE(18000, false, Particle.GLOW), ROOFTOP(17000, true, null), SCRAPYARD(13000, false, Particle.SPORE_BLOSSOM_AIR), INK(6000, false,
				Particle.WHITE_ASH);

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
	/** Theme Pack id -> its battle background's colours: floor, floor 2, wall, wall 2, light, accent. */
	private static final Map<String, int[]> PALETTE = new HashMap<>();

	static {
		// After each Theme Pack's battle backgrounds in the original.
		put(Scene.QUAKE, "the_forgotten", "the_outcast", "line_2");
		put(Scene.CASINO, "flat_broke_gamblers", "the_b_e");
		put(Scene.FACTORY, "automated_factory", "miracle_in_district_20", "miracle_in_district_20_bokgak", "line_3", "deep_sigh", "bearers_of_weight", "vain_pride", "bridle_of_infinity");
		put(Scene.ICE, "the_unloving");
		put(Scene.LAB, "nagel_und_hammer", "nest_workshop_and_technology", "falling_flowers", "textbook", "yield_my_flesh_to_claim_their_bones", "yield_my_flesh_to_claim_their_bones_bokgak", "lcb_regular_check_up", "lcb_regular_check_up_bokgak", "the_noon_of_violet", "thunder_and_lightning", "code_purple", "slicers_dicers", "insignificant_envy", "pitiful_envy");
		put(Scene.ALLEY, "faith_erosion", "tearful_things", "mnestic_experience", "nocturnal_sweeping_bokgak", "the_dawn_of_green", "complete_total_extermination", "sinking_pang");
		put(Scene.MOOR, "the_unconfronting", "the_unchanging", "the_heartbreaking", "a_beautiful_voice", "the_dusk_of_amber", "excessive_gluttony");
		put(Scene.CAVE, "abnormal_seismic_zone", "emotional_subservience", "line_1", "to_be_pierced", "degraded_gloom", "sunk_gloom", "emotional_judgment");
		put(Scene.NEON, "lake_world", "an_anarchy_of_colors", "emotional_flood", "rising_power_supply");
		put(Scene.WHALE, "crawling_abyss", "the_evil_defining");
		put(Scene.MANOR, "dregs_of_the_manor", "nocturnal_sweeping", "a_midspring_night_s_dream_2");
		put(Scene.STORM, "a_certain_world", "hatred_and_despair", "line_4_section_3");
		put(Scene.CARNIVAL, "la_manchaland_re_opening", "the_infinite_procession", "the_dream_ending", "blessed_carnival", "the_master_of_la_manchaland");
		put(Scene.EAST, "four_houses_and_greed", "the_surrendered_witnessing", "blade_and_artwork", "spring_cultivation", "chachihu");
		put(Scene.STUDIO, "charm_wander_doubt", "twining_threads", "line_5");
		put(Scene.CHAPEL, "the_unsevering", "full_stopped_by_a_bullet", "line_4_section_4");
		put(Scene.KITCHEN, "storefront_campout", "hell_s_chicken", "emotional_seduction", "dizzying_waves");
		put(Scene.BOUTIQUE, "creed_of_preservation", "readvent_of_gluttony");
		put(Scene.SCRAPYARD, "s_e_a", "s_e_a_c_r");
		put(Scene.CLOCK, "timekilling_time", "timekilling_time_bokgak");
		put(Scene.TRAIN, "murder_on_the_warp_express", "murder_on_the_warp_express_bokgak");
		put(Scene.FOREST, "to_be_cleaved", "tangling_lust", "emotional_craving", "fairy_tale", "piercers_penetrators", "devoured_gluttony", "line_3_terminus", "emotional_repression", "tyrannical_pride", "efflorescing_greenery", "into_a_certain_library_s_book");
		put(Scene.CANDLES, "to_be_crushed", "addicting_lust", "crushing_external_force");
		put(Scene.FIRE, "crushers_breakers", "burning_haze", "season_of_the_flame", "repressed_wrath", "unbound_wrath");
		put(Scene.DEEP, "treadwheel_sloth", "sinking_deluge", "poised_breathing", "emotional_indolence", "trickled_sanguine_blood", "line_1_madness", "bloodtinged_ichthyic_odor");
		put(Scene.INK, "inert_sloth", "unrelenting_might");
		put(Scene.THEATER, "mountain_of_corpses_sea_of_blood", "who_couldn_t_be_bloodfiends");
		put(Scene.LIBRARY, "n_corp_new_league_of_nine_litt_rateurs");
		put(Scene.ROOFTOP, "impenetrable_path");
		PALETTE.put("the_forgotten", new int[] {0x49291b, 0x854d3f, 0x472210, 0xbb5242, 0xffedd6, 0x73382a});
		PALETTE.put("the_outcast", new int[] {0x462113, 0x472713, 0x502914, 0xc15946, 0xffedd6, 0x783424});
		PALETTE.put("flat_broke_gamblers", new int[] {0x381c1c, 0x470e1a, 0x431019, 0xaa3f64, 0xfffdf6, 0x6d1921});
		PALETTE.put("automated_factory", new int[] {0x383838, 0x474747, 0x3d3d1c, 0x554e30, 0xa1804a, 0x6d5e37});
		PALETTE.put("the_unloving", new int[] {0x478ca2, 0x1a3e47, 0x8cedff, 0x519bac, 0xceffff, 0x367888});
		PALETTE.put("nagel_und_hammer", new int[] {0x000038, 0xb7c4f6, 0x384453, 0xdee6ff, 0xf2feff, 0x434f63});
		PALETTE.put("faith_erosion", new int[] {0x2d2d38, 0x625274, 0x562928, 0xda5b44, 0xffd95f, 0x7b2515});
		PALETTE.put("the_unconfronting", new int[] {0x381025, 0x67192e, 0x491326, 0xa73762, 0xe966e4, 0x732241});
		PALETTE.put("nest_workshop_and_technology", new int[] {0x263826, 0x3a4741, 0x363d3b, 0x657674, 0xe6ebed, 0x2e7c6e});
		PALETTE.put("falling_flowers", new int[] {0x2a3238, 0x506165, 0xa8cac8, 0x40514d, 0xe4fafc, 0x95b3b1});
		PALETTE.put("tearful_things", new int[] {0x1c3838, 0x3f4047, 0x3d2a29, 0xd57444, 0xf8ffff, 0x7c402e});
		PALETTE.put("the_unchanging", new int[] {0x38281f, 0x47362a, 0x44392f, 0xa69589, 0xd0c1b7, 0x3d332b});
		PALETTE.put("lake_world", new int[] {0x291a38, 0x3b2a47, 0x321f3d, 0x85669e, 0xfdf0ff, 0x46296d});
		PALETTE.put("crawling_abyss", new int[] {0x381b22, 0xaf7487, 0xe79ead, 0x673c42, 0xfdd6ef, 0x6f363d});
		PALETTE.put("the_evil_defining", new int[] {0x381b14, 0x703c30, 0x5a322a, 0xf2ad8e, 0xffffff, 0x6f3c30});
		PALETTE.put("dregs_of_the_manor", new int[] {0x381c00, 0x472313, 0x3d2b24, 0x7d756b, 0xb8aa92, 0x5c2c28});
		PALETTE.put("a_certain_world", new int[] {0x382818, 0x47392a, 0x3d2c1f, 0x6c5d4e, 0xd5dfcb, 0x663e2e});
		PALETTE.put("the_heartbreaking", new int[] {0x222f38, 0x5f6d8c, 0x201f3d, 0x6858c6, 0xffffff, 0x3a327f});
		PALETTE.put("la_manchaland_re_opening", new int[] {0x383838, 0x47052d, 0x3f0a24, 0xbc2376, 0xffb2fb, 0x771545});
		PALETTE.put("the_infinite_procession", new int[] {0x0a0038, 0x3a0147, 0x4a0c4d, 0xb74bae, 0xfef5fd, 0x781974});
		PALETTE.put("the_dream_ending", new int[] {0x80332a, 0xff8166, 0x6c302d, 0xffc48d, 0xffffe8, 0x742725});
		PALETTE.put("four_houses_and_greed", new int[] {0x293836, 0xa1bbaa, 0x343d38, 0x89a098, 0xe5e8e5, 0x734935});
		PALETTE.put("the_surrendered_witnessing", new int[] {0x380f05, 0x47190b, 0x6a2913, 0xe96f39, 0xfdb261, 0x782d16});
		PALETTE.put("charm_wander_doubt", new int[] {0x383838, 0x606060, 0x888481, 0xfffcf8, 0xffffff, 0x797573});
		PALETTE.put("textbook", new int[] {0x131f38, 0x283448, 0x3f4b66, 0x97accd, 0xf4fde1, 0x354768});
		PALETTE.put("blade_and_artwork", new int[] {0x382b21, 0x473a30, 0x524740, 0xb0a5a0, 0xc9bfb8, 0x4a3f39});
		PALETTE.put("the_unsevering", new int[] {0x38160a, 0x471614, 0x3d160c, 0x9c4d35, 0xc09177, 0x73301c});
		PALETTE.put("storefront_campout", new int[] {0x382d2d, 0x5b5855, 0x3d3931, 0xe4e4d7, 0xf2f2e7, 0x2c2923});
		PALETTE.put("an_anarchy_of_colors", new int[] {0x000038, 0x0d0647, 0x26133d, 0x724382, 0xb3b6c0, 0x3c2669});
		PALETTE.put("creed_of_preservation", new int[] {0x383826, 0x47392a, 0x3d3d2a, 0x513e2c, 0x5c4f45, 0x0d0d09});
		PALETTE.put("readvent_of_gluttony", new int[] {0x38170b, 0xa77654, 0x3d2013, 0xffd8ad, 0xfdf0c9, 0x6d452f});
		PALETTE.put("hell_s_chicken", new int[] {0x381c05, 0x47260c, 0x3d2617, 0x896a4f, 0xc6b883, 0x6b472c});
		PALETTE.put("s_e_a", new int[] {0x2e381d, 0x405129, 0x2f3d1d, 0x6c8f53, 0xcdd6a5, 0x49622f});
		PALETTE.put("miracle_in_district_20", new int[] {0x381202, 0x472516, 0x3d1f12, 0x824a2b, 0xc8a852, 0x6b3e24});
		PALETTE.put("miracle_in_district_20_bokgak", new int[] {0x380901, 0x471700, 0x481c0c, 0xb7582a, 0xffff8f, 0x793116});
		PALETTE.put("yield_my_flesh_to_claim_their_bones", new int[] {0x383838, 0x474747, 0x3d3a3d, 0x5d6062, 0xd1d2d2, 0x242324});
		PALETTE.put("yield_my_flesh_to_claim_their_bones_bokgak", new int[] {0x383838, 0x474747, 0x3b393d, 0x585a60, 0xc1c0c0, 0x29282b});
		PALETTE.put("timekilling_time", new int[] {0x381100, 0x47301e, 0x3d2912, 0x584024, 0xfefaf0, 0x684c2a});
		PALETTE.put("timekilling_time_bokgak", new int[] {0x382518, 0x47372b, 0x5f4b32, 0xecc685, 0xffffdc, 0x775c3a});
		PALETTE.put("murder_on_the_warp_express", new int[] {0x182338, 0x5c8ec1, 0x1f2e4b, 0x5989cb, 0xc0edfa, 0x304a79});
		PALETTE.put("murder_on_the_warp_express_bokgak", new int[] {0x380e09, 0x7f414a, 0x3f0c17, 0x932c57, 0xda98b6, 0x6c1b34});
		PALETTE.put("lcb_regular_check_up", new int[] {0x38290b, 0x473020, 0x3d2e20, 0x9c8566, 0xfdfdc8, 0x6b482b});
		PALETTE.put("lcb_regular_check_up_bokgak", new int[] {0x38211c, 0x5d3f2b, 0x3d291a, 0x9e7658, 0xf1dfd1, 0x6b4229});
		PALETTE.put("nocturnal_sweeping", new int[] {0x38322e, 0x473c38, 0x3d332f, 0x766553, 0xfffffd, 0x71533b});
		PALETTE.put("nocturnal_sweeping_bokgak", new int[] {0x38230e, 0x472a18, 0x3d1d08, 0x693e17, 0xfce286, 0x6d411a});
		PALETTE.put("spring_cultivation", new int[] {0x353830, 0xc1bda6, 0x3f423c, 0xcbd3bb, 0xd2d0c2, 0x3b6c61});
		PALETTE.put("twining_threads", new int[] {0x383838, 0x474747, 0x716f6c, 0xf1eae3, 0xfdfcfb, 0x656360});
		PALETTE.put("mnestic_experience", new int[] {0x1c2f38, 0x344147, 0x2f373d, 0x7f8e94, 0xffffff, 0x23292d});
		PALETTE.put("the_noon_of_violet", new int[] {0x381c00, 0x472e16, 0x412210, 0xc16538, 0xffffdf, 0x7b3d23});
		PALETTE.put("full_stopped_by_a_bullet", new int[] {0x383128, 0x473d2f, 0x433a2c, 0xbfac8a, 0xffffff, 0x3c3428});
		PALETTE.put("hatred_and_despair", new int[] {0x212638, 0x24305a, 0x0a1d4c, 0x164ab6, 0x58ffff, 0x13337b});
		PALETTE.put("the_dusk_of_amber", new int[] {0x3c1b0f, 0x782a2d, 0x26173d, 0x4a2951, 0xfffff0, 0x632732});
		PALETTE.put("line_1", new int[] {0x383838, 0x002884, 0x18293d, 0x16428a, 0x5373d5, 0x082a6d});
		PALETTE.put("line_2", new int[] {0x383800, 0x47472c, 0x554b33, 0xe7d9ad, 0xffffed, 0x6b442c});
		PALETTE.put("line_3", new int[] {0x383838, 0x47473a, 0x3e3b28, 0xb7a583, 0xe7e3d9, 0x6b5030});
		PALETTE.put("line_4_section_3", new int[] {0x150038, 0x280847, 0x22083d, 0x9546ab, 0xf8dbff, 0x5d2576});
		PALETTE.put("line_4_section_4", new int[] {0x380000, 0x471707, 0x3d1c0f, 0x512e20, 0x84715b, 0x662e20});
		PALETTE.put("line_5", new int[] {0x381e18, 0x473d35, 0x3d3d38, 0x485151, 0x5f615d, 0x181816});
		PALETTE.put("slicers_dicers", new int[] {0x1f1f38, 0x37677f, 0x332c3d, 0x9bc9fb, 0xf6fafd, 0x365c77});
		PALETTE.put("to_be_cleaved", new int[] {0x003826, 0x0f473e, 0x124b42, 0x3ab6a5, 0x85fff2, 0x1b7666});
		PALETTE.put("piercers_penetrators", new int[] {0x779420, 0x344d0a, 0x43560f, 0x7d9e1e, 0xd3e138, 0x587015});
		PALETTE.put("to_be_pierced", new int[] {0x383838, 0x002884, 0x18293d, 0x16428a, 0x5373d5, 0x082a6d});
		PALETTE.put("crushers_breakers", new int[] {0x380801, 0xe75506, 0x441007, 0xf64212, 0xffff3a, 0x931405});
		PALETTE.put("to_be_crushed", new int[] {0x380a00, 0x742904, 0x5b2308, 0xd36423, 0xf3c47a, 0x78300d});
		PALETTE.put("repressed_wrath", new int[] {0x490d00, 0x470b00, 0x761c06, 0xff7b30, 0xffb23d, 0x7e230d});
		PALETTE.put("unbound_wrath", new int[] {0x490d00, 0x470b00, 0x761c06, 0xff7b30, 0xffb23d, 0x7e230d});
		PALETTE.put("emotional_repression", new int[] {0x383838, 0x474747, 0x393d1b, 0x61712d, 0xe3ff48, 0x576f23});
		PALETTE.put("addicting_lust", new int[] {0x380a00, 0x742904, 0x5b2308, 0xd36423, 0xf3c47a, 0x78300d});
		PALETTE.put("tangling_lust", new int[] {0x003826, 0x0f473e, 0x124b42, 0x3ab6a5, 0x85fff2, 0x1b7666});
		PALETTE.put("emotional_seduction", new int[] {0x381202, 0x471c0f, 0x3d2614, 0xad8e6e, 0xffffe0, 0x704a27});
		PALETTE.put("treadwheel_sloth", new int[] {0x0f3833, 0x267e77, 0x004b43, 0x06dcc9, 0x58fbf3, 0x0d8278});
		PALETTE.put("inert_sloth", new int[] {0x2a2a38, 0x61616f, 0x989898, 0x4b4b51, 0xa8a7a8, 0x878787});
		PALETTE.put("emotional_indolence", new int[] {0x383838, 0x172f47, 0x16293d, 0x569fd3, 0xabefff, 0x2b6a9a});
		PALETTE.put("devoured_gluttony", new int[] {0x779420, 0x344d0a, 0x43560f, 0x7d9e1e, 0xd3e138, 0x587015});
		PALETTE.put("excessive_gluttony", new int[] {0x381c00, 0x472226, 0x3d1a1a, 0x894c4c, 0xa99a9c, 0x681317});
		PALETTE.put("emotional_craving", new int[] {0x003826, 0x0f473e, 0x124b42, 0x3ab6a5, 0x85fff2, 0x1b7666});
		PALETTE.put("degraded_gloom", new int[] {0x383838, 0x002884, 0x18293d, 0x16428a, 0x5373d5, 0x082a6d});
		PALETTE.put("sunk_gloom", new int[] {0x383838, 0x002884, 0x18293d, 0x16428a, 0x5373d5, 0x082a6d});
		PALETTE.put("emotional_flood", new int[] {0x38002e, 0x300e47, 0x3d1c55, 0xc581ed, 0xfdc2f8, 0x432579});
		PALETTE.put("vain_pride", new int[] {0x383838, 0x1f3447, 0x53454f, 0x405ee6, 0xe9d469, 0x524058});
		PALETTE.put("tyrannical_pride", new int[] {0x383838, 0x473d2b, 0x4e5047, 0xb7d5c4, 0xe7fcf0, 0x613332});
		PALETTE.put("emotional_subservience", new int[] {0x003834, 0x05473c, 0x147b65, 0x42fcdf, 0xd3fffe, 0x1a856e});
		PALETTE.put("insignificant_envy", new int[] {0x1f1f38, 0x37677f, 0x332c3d, 0x9bc9fb, 0xf6fafd, 0x365c77});
		PALETTE.put("pitiful_envy", new int[] {0x1f1f38, 0x37677f, 0x332c3d, 0x9bc9fb, 0xf6fafd, 0x365c77});
		PALETTE.put("emotional_judgment", new int[] {0x383838, 0x002884, 0x18293d, 0x16428a, 0x5373d5, 0x082a6d});
		PALETTE.put("burning_haze", new int[] {0x380801, 0xe75506, 0x441007, 0xf64212, 0xffff3a, 0x931405});
		PALETTE.put("season_of_the_flame", new int[] {0x380801, 0xe75506, 0x441007, 0xf64212, 0xffff3a, 0x931405});
		PALETTE.put("trickled_sanguine_blood", new int[] {0x383838, 0x172f47, 0x16293d, 0x569fd3, 0xabefff, 0x2b6a9a});
		PALETTE.put("mountain_of_corpses_sea_of_blood", new int[] {0x380800, 0x873300, 0x4c1708, 0xfa9f49, 0xfff5b1, 0x783413});
		PALETTE.put("dizzying_waves", new int[] {0x381202, 0x471c0f, 0x3d2614, 0xad8e6e, 0xffffe0, 0x704a27});
		PALETTE.put("abnormal_seismic_zone", new int[] {0x003834, 0x05473c, 0x147b65, 0x42fcdf, 0xd3fffe, 0x1a856e});
		PALETTE.put("crushing_external_force", new int[] {0x380a00, 0x742904, 0x5b2308, 0xd36423, 0xf3c47a, 0x78300d});
		PALETTE.put("unrelenting_might", new int[] {0x2a2a38, 0x61616f, 0x989898, 0x4b4b51, 0xa8a7a8, 0x878787});
		PALETTE.put("sinking_pang", new int[] {0x434b43, 0x39473d, 0x363d34, 0x6b7964, 0xf8f9ed, 0x2d322c});
		PALETTE.put("sinking_deluge", new int[] {0x0f3833, 0x267e77, 0x004b43, 0x06dcc9, 0x58fbf3, 0x0d8278});
		PALETTE.put("deep_sigh", new int[] {0x383838, 0x47473a, 0x3e3b28, 0xb7a583, 0xe7e3d9, 0x6b5030});
		PALETTE.put("poised_breathing", new int[] {0x0f3833, 0x267e77, 0x004b43, 0x06dcc9, 0x58fbf3, 0x0d8278});
		PALETTE.put("rising_power_supply", new int[] {0x38002e, 0x300e47, 0x3d1c55, 0xc581ed, 0xfdc2f8, 0x432579});
		PALETTE.put("thunder_and_lightning", new int[] {0x1f2938, 0x333d47, 0x0b295f, 0x2b8aff, 0xc6ffff, 0x3398f4});
		PALETTE.put("n_corp_new_league_of_nine_litt_rateurs", new int[] {0x381e04, 0x47290f, 0x412c16, 0x795c3c, 0xe8ceaf, 0x5d4227});
		PALETTE.put("efflorescing_greenery", new int[] {0x383838, 0x473d2b, 0x4e5047, 0xb7d5c4, 0xe7fcf0, 0x613332});
		PALETTE.put("line_3_terminus", new int[] {0x779420, 0x344d0a, 0x43560f, 0x7d9e1e, 0xd3e138, 0x587015});
		PALETTE.put("bridle_of_infinity", new int[] {0x383838, 0x1f3447, 0x53454f, 0x405ee6, 0xe9d469, 0x524058});
		PALETTE.put("s_e_a_c_r", new int[] {0x2b381b, 0x3e4e28, 0x43582d, 0x9bc481, 0xc8c889, 0x4c6633});
		PALETTE.put("impenetrable_path", new int[] {0x2e1d38, 0x4a3157, 0x43285a, 0x8f5acf, 0xf0d1ff, 0x4f3072});
		PALETTE.put("who_couldn_t_be_bloodfiends", new int[] {0x383838, 0x472a1a, 0x392f3d, 0xd38d9d, 0xfbeeff, 0x5d3b48});
		PALETTE.put("a_beautiful_voice", new int[] {0x38162a, 0x612b4e, 0xe95e6b, 0x671e23, 0xffe2ef, 0x7c293a});
		PALETTE.put("the_dawn_of_green", new int[] {0x382f29, 0x534c45, 0x3d352e, 0x998c7f, 0xfffdf7, 0x302924});
		PALETTE.put("into_a_certain_library_s_book", new int[] {0x383838, 0x003c47, 0x0b3d2c, 0x5bc389, 0xc3fac8, 0x296e4f});
		PALETTE.put("code_purple", new int[] {0x2f3838, 0x484b4b, 0x3c494e, 0x8eb7c0, 0xffffff, 0x355a63});
		PALETTE.put("bearers_of_weight", new int[] {0x380e09, 0x471c16, 0x3d1710, 0x76211b, 0xb85e4e, 0x6b1712});
		PALETTE.put("line_1_madness", new int[] {0x383838, 0x172f47, 0x16293d, 0x569fd3, 0xabefff, 0x2b6a9a});
		PALETTE.put("blessed_carnival", new int[] {0x0a0038, 0x3a0147, 0x570e52, 0xd340b6, 0xffc9fe, 0x831b73});
		PALETTE.put("fairy_tale", new int[] {0x383838, 0x003c47, 0x0b3d2c, 0x5bc389, 0xc3fac8, 0x296e4f});
		PALETTE.put("bloodtinged_ichthyic_odor", new int[] {0x383838, 0x172f47, 0x16293d, 0x569fd3, 0xabefff, 0x2b6a9a});
		PALETTE.put("complete_total_extermination", new int[] {0x38382e, 0x234723, 0x3d332b, 0xaf7354, 0xe4fffa, 0xda8857});
		PALETTE.put("the_master_of_la_manchaland", new int[] {0x5b143a, 0xf55f8a, 0x471197, 0xc63fd4, 0xe7f4ff, 0x50148a});
		PALETTE.put("chachihu", new int[] {0x381b20, 0x743741, 0x3d2936, 0x958399, 0xe1e3e9, 0x65323b});
		PALETTE.put("a_midspring_night_s_dream_2", new int[] {0x381a16, 0x47261d, 0x3d261c, 0xb66842, 0xffffbd, 0x793d25});
		PALETTE.put("the_b_e", new int[] {0x382626, 0x47121c, 0x4e1824, 0xb65f88, 0xfffaf5, 0x6e1d26});
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
		return p != null ? p : new int[] {0x303034, 0x46464c, 0x2a2a30, 0x505058, 0xFFF2D0, fallback};
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
		final Material floorA;
		final Material floorB;
		final Material wallA;
		final Material wallB;
		final Material light;
		final boolean painted;
		final Material leaves;

		Ctx(World w, int cx, int y, int cz, int h, Random r, int[] p) {
			this.w = w;
			this.cx = cx;
			this.y = y;
			this.cz = cz;
			this.h = h;
			this.r = r;
			this.painted = p.length >= 6;
			int accentRgb = painted ? p[5] : p[0];
			int accent2Rgb = painted ? p[3] : p[1];
			this.accent = MdWorld.concrete(accentRgb);
			this.glass = BlockColours.glass(accentRgb);
			this.accent2 = MdWorld.concrete(accent2Rgb);
			this.wool = Material.valueOf(MdWorld.concrete(accentRgb).name().replace("_CONCRETE", "_WOOL"));
			this.floorA = painted ? BlockColours.solid(p[0]) : Material.POLISHED_DEEPSLATE;
			this.floorB = painted ? BlockColours.solid(p[1]) : Material.DEEPSLATE_TILES;
			this.wallA = painted ? BlockColours.solid(p[2]) : Material.STONE_BRICKS;
			this.wallB = painted ? BlockColours.solid(p[3]) : Material.POLISHED_ANDESITE;
			this.light = painted ? BlockColours.light(p[4]) : Material.SEA_LANTERN;
			this.leaves = painted ? BlockColours.leaves(p[5]) : Material.AZALEA_LEAVES;
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
		Ctx k = new Ctx(c.getWorld(), c.getBlockX(), c.getBlockY() - 1, c.getBlockZ(), h, new Random(seed), palette);
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
			case ICE -> ice(k);
			case CANDLES -> candles(k);
			case CAVE -> cave(k);
			case ROOFTOP -> rooftop(k);
			case SCRAPYARD -> scrapyard(k);
			case INK -> ink(k);
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
		if (k.painted) {
			Material painted = painted(k, s, x, z, inside);
			if (painted != null) {
				return painted;
			}
		}
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

	/** The floor in the battle background's colours (null: the scene's own, e.g. a ship's deck). */
	private static Material painted(Ctx k, Scene s, int x, int z, boolean inside) {
		Random r = k.r;
		boolean outdoor = switch (s) {
			case MOOR, FOREST, QUAKE, STORM, BLOOD, FIRE, ICE, CAVE, INK, SCRAPYARD, DEEP, TILES, ROOFTOP, WHALE, SNOW, ALLEY -> true;
			default -> false;
		};
		switch (s) {
			case SHIP, TRAIN, CARNIVAL, ARENA, MIRROR -> {
				return null;
			}
			case FIRE -> {
				if (!inside && r.nextInt(3) > 0) {
					return Material.LAVA;
				}
			}
			case SCRAPYARD, DEEP -> {
				if (!inside && r.nextInt(3) > 0) {
					// Murky water: the background's tint over it.
					k.w.getBlockAt(k.cx + x, k.y - 1, k.cz + z).setType(Material.WATER, false);
					return k.glass;
				}
			}
			case CHAPEL, MANOR, BOUTIQUE, CASINO, LIBRARY, THEATER -> {
				if (Math.abs(x) <= 2 && inside) {
					return k.wool;
				}
			}
			case EAST, ALLEY -> {
				if (Math.abs(x) <= 2 && inside) {
					return k.floorB;
				}
			}
			default -> {
			}
		}
		if (outdoor) {
			return r.nextInt(10) < 8 ? k.floorA : k.floorB;
		}
		// Indoors: one tone with tile lines in the second, as the originals' floors.
		return Math.floorMod(x, 4) == 0 || Math.floorMod(z, 4) == 0 ? k.floorB : k.floorA;
	}

	// --- the scenes -----------------------------------------------------------------------------------

	/** Buildings on three sides with lit windows (the Backstreets at night), lamps, bins and crates. */
	private static void alley(Ctx k) {
		int d = k.h + 3;
		for (int x = -d; x <= d; x += 5) {
			building(k, x, d + 1, 6 + k.r.nextInt(12), k.pick(k.wallA, k.wallB, k.wallA, Material.DEEPSLATE_BRICKS));
			building(k, -d - 1, x, 5 + k.r.nextInt(10), k.pick(k.wallA, k.wallB, Material.DEEPSLATE_TILES));
			building(k, d + 1, x, 5 + k.r.nextInt(10), k.pick(k.wallA, k.wallB, Material.DEEPSLATE_TILES));
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
			k.set(x + 1, 4, d, k.light);
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
			k.column(x, z, 1, 6, (x + z) % 4 == 0 ? k.light : Math.abs(x + z) % 2 == 0 ? k.wallA : k.glass);
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
			k.box(x - 2, d + 1, x + 2, d + 5, 0, 0, k.wallB);
			k.box(x - 2, d + 5, x + 2, d + 5, 1, 5, k.wallA);
			k.set(x, 1, d + 3, k.accent);
		}
	}

	/** Stone pillars, stained glass in the theme's colour, candles and pews (N Corp.'s inquisitors). */
	private static void chapel(Ctx k) {
		int d = k.h + 1;
		k.around(d, 1, (x, z) -> {
			k.column(x, z, 1, 9, (Math.abs(x) + Math.abs(z)) % 4 == 0 ? k.wallB : Math.abs(x + z) % 3 == 1 ? k.glass
					: k.wallA);
		});
		k.around(k.h - 1, 4, (x, z) -> {
			k.column(x, z, 1, 6, k.wallB);
			k.set(x, 7, z, Material.SOUL_LANTERN);
		});
		for (int x = -k.h + 2; x <= k.h - 2; x += 2) {
			if (Math.abs(x) > 3) {
				k.set(x, 1, -k.h + 2, Material.SPRUCE_STAIRS);
			}
		}
		// The altar: a great nail on the far wall.
		k.box(-2, k.h - 1, 2, k.h - 1, 1, 1, k.wallB);
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
			k.column(x, z, 1, 8, (Math.abs(x) + Math.abs(z)) % 5 == 0 ? k.wallB : (Math.abs(x + z) % 5 == 2 ? Material.GRAY_STAINED_GLASS
					: k.wallA));
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
		k.box(-3, k.h - 1, 3, k.h - 1, 1, 3, k.wallB);
		k.set(0, 4, k.h - 1, k.accent);
	}

	/** A windswept heath at night: tall grass, dead bushes, stones, a lone tree. */
	private static void moor(Ctx k) {
		// Low hills all round and a ruined wall.
		for (int a = 0; a < 360; a += 4) {
			double rad = k.h + 7 + k.r.nextInt(5);
			int x = (int) Math.round(Math.cos(Math.toRadians(a)) * rad);
			int z = (int) Math.round(Math.sin(Math.toRadians(a)) * rad);
			k.column(x, z, 1, 1 + k.r.nextInt(5), k.pick(k.floorA, k.floorB, k.wallA));
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
		k.around(d, 1, (x, z) -> k.column(x, z, 1, 6, Math.abs(x + z) % 6 == 0 ? Material.WHITE_STAINED_GLASS : k.wallA));
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
				k.set(x, 7, z, (x % 5 == 0 && z % 5 == 0) ? k.light : k.wallA);
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
		k.around(d, 1, (x, z) -> k.column(x, z, 1, 7, (Math.abs(x) + Math.abs(z)) % 4 == 0 ? k.wallB : k.wallA));
		k.around(k.h - 1, 3, (x, z) -> {
			k.column(x, z, 1, 1, Material.POLISHED_BLACKSTONE_WALL);
			k.set(x, 2, z, k.pick(Material.RED_WOOL, Material.BLACK_WOOL, Material.WHITE_WOOL, k.accent));
			k.set(x, 3, z, Material.SKELETON_SKULL);
		});
		for (int x = -k.h; x <= k.h; x += 4) {
			k.column(x, d, 2, 5, Material.TINTED_GLASS);
			k.set(x, 6, d, k.light);
		}
	}

	/** The Ring's studio: canvases on easels, paint spilt, frames on the walls. */
	private static void studio(Ctx k) {
		int d = k.h + 1;
		k.around(d, 1, (x, z) -> k.column(x, z, 1, 7, k.wallA));
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
			k.column(x, z, 1, 6, k.wallB);
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
			k.box(-half + 1, pz - half + 1, half - 1, pz + half - 1, base, base + 3, k.wallB);
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
					k.painted ? k.leaves : k.pick(Material.AZALEA_LEAVES, Material.FLOWERING_AZALEA_LEAVES, Material.OAK_LEAVES),
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
			k.set(x, 10, z, k.wallA);
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
		k.around(d, 1, (x, z) -> k.column(x, z, 1, 7, (Math.abs(x) + Math.abs(z)) % 3 == 0 ? k.wallB : k.wallA));
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
		skyline(k, 8, 20, true, false, k.wallA, k.wallB, Material.POLISHED_DEEPSLATE);
		k.around(k.h + 2, 6, (x, z) -> {
			int height = 4 + k.r.nextInt(6);
			k.column(x, z, 1, height, k.pick(Material.TUFF_BRICKS, Material.COPPER_BLOCK, Material.OXIDIZED_COPPER));
			k.set(x, height + 1, z, Material.LIGHTNING_ROD);
		});
		k.around(k.h, 4, (x, z) -> k.set(x, 1, z, Material.REDSTONE_LAMP));
	}

	/** Shaking ground: cracked rock, fallen pillars, rubble. */
	private static void quake(Ctx k) {
		skyline(k, 6, 16, false, true, k.wallA, k.wallB, Material.CRACKED_STONE_BRICKS);
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
		k.around(d, 1, (x, z) -> k.column(x, z, 1, 9, (Math.abs(x) + Math.abs(z)) % 6 == 0 ? k.wallB : k.wallA));
		for (int x = -d; x <= d; x += 4) {
			for (int z = -d; z <= d; z++) {
				k.set(x, 10, z, Material.IRON_BLOCK);
			}
		}
		k.around(k.h - 1, 4, (x, z) -> {
			k.set(x, 1, z, k.pick(Material.BLAST_FURNACE, Material.PISTON, Material.OBSERVER, Material.DISPENSER));
			k.column(x, z, 2, 9, Material.IRON_CHAIN);
		});
		k.around(k.h - 2, 8, (x, z) -> k.set(x, 9, z, k.light));
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
		k.around(d, 1, (x, z) -> k.column(x, z, 1, 8, k.wallA));
		k.around(d, 3, (x, z) -> k.column(x, z, 1, 7, (Math.abs(x) + Math.abs(z)) % 2 == 0 ? k.glass : MdWorld.glass(0xD040E0)));
		k.around(k.h - 1, 4, (x, z) -> {
			k.column(x, z, 1, 3, Material.END_ROD);
			k.set(x, 4, z, k.pick(Material.PURPLE_STAINED_GLASS, Material.MAGENTA_STAINED_GLASS, Material.CYAN_STAINED_GLASS));
		});
		for (int x = -d; x <= d; x++) {
			for (int z = -d; z <= d; z++) {
				k.set(x, 9, z, (x + z) % 7 == 0 ? k.light : k.wallA);
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
		k.around(d + 1, 1, (x, z) -> k.column(x, z, 1, 4 + k.r.nextInt(3), k.r.nextInt(5) == 0 ? k.wallB : k.wallA));
		for (int i = 0; i < 30; i++) {
			int x = k.r.nextInt(2 * k.h + 1) - k.h;
			int z = k.r.nextInt(2 * k.h + 1) - k.h;
			if (Math.max(Math.abs(x), Math.abs(z)) >= k.h - 2) {
				k.set(x, 0, z, k.r.nextBoolean() ? k.wallB : Material.NETHER_WART_BLOCK);
			}
		}
		// The heart on the far side.
		k.box(-2, d + 2, 2, d + 3, 1, 5, Material.RED_CONCRETE);
		k.set(0, 3, d + 1, Material.SHROOMLIGHT);
	}

	/** A theatre (Nocturnal Sweeping): rows of red seats facing a stage with curtains and footlights. */
	private static void theater(Ctx k) {
		int d = k.h + 1;
		k.around(d, 1, (x, z) -> k.column(x, z, 1, 10, k.wallA));
		k.box(-k.h, d - 1, k.h, d - 1, 1, 9, k.wool);
		for (int x = -k.h + 1; x <= k.h - 1; x += 2) {
			k.set(x, 1, k.h / 2 + 1, Material.GLOWSTONE);
		}
		for (int z = -d - 6; z <= -k.h + 1; z += 2) {
			for (int x = -k.h; x <= k.h; x++) {
				if (Math.abs(x) > 2) {
					k.set(x, 1 + Math.max(0, (-k.h + 1 - z) / 2), z, k.wool);
				}
			}
		}
		k.around(k.h - 1, 6, (x, z) -> k.set(x, 8, z, Material.LANTERN));
	}

	/** Inside a clock tower: great gears on the walls, a clock face and brass. */
	private static void clock(Ctx k) {
		int d = k.h + 1;
		k.around(d, 1, (x, z) -> k.column(x, z, 1, 12, (Math.abs(x) + Math.abs(z)) % 4 == 0 ? k.wallB : k.wallA));
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

	/** An ice castle: ice floor, pillars of packed ice, icicles, cold light. */
	private static void ice(Ctx k) {
		int d = k.h + 1;
		k.around(d, 1, (x, z) -> k.column(x, z, 1, 3 + k.r.nextInt(6), k.pick(Material.PACKED_ICE, Material.BLUE_ICE, Material.ICE, k.wallA)));
		k.around(k.h - 1, 5, (x, z) -> {
			k.column(x, z, 1, 6, Material.PACKED_ICE);
			k.set(x, 7, z, k.light);
		});
		for (int i = 0; i < 40; i++) {
			int x = k.r.nextInt(2 * d + 1) - d;
			int z = d + 1 + k.r.nextInt(5);
			int top = 6 + k.r.nextInt(10);
			k.column(x, z, 1, top, Material.BLUE_ICE);
			k.column(x, -z, 1, top - 3, Material.PACKED_ICE);
		}
		skyline(k, 8, 20, false, false, Material.PACKED_ICE, Material.BLUE_ICE, k.wallB);
	}

	/** A hall of candles and strings of paper lanterns (So That No One Will Cry). */
	private static void candles(Ctx k) {
		int d = k.h + 1;
		k.around(d, 1, (x, z) -> k.column(x, z, 1, 9, (Math.abs(x) + Math.abs(z)) % 5 == 0 ? k.wallB : k.wallA));
		for (int z = -k.h; z <= k.h; z += 4) {
			for (int x = -k.h; x <= k.h; x++) {
				int sag = (int) Math.round(2 * Math.sin(Math.PI * (x + k.h) / (2.0 * k.h)));
				k.set(x, 9 - sag, z, x % 3 == 0 ? Material.LANTERN : Material.IRON_CHAIN);
			}
		}
		for (int i = 0; i < 50; i++) {
			int x = (k.r.nextBoolean() ? 1 : -1) * (k.h - k.r.nextInt(3));
			int z = k.r.nextInt(2 * k.h + 1) - k.h;
						k.lit(x, 1, z, Material.WHITE_CANDLE);
		}
		for (int i = 0; i < 30; i++) {
			int x = k.r.nextInt(2 * k.h + 1) - k.h;
			int z = k.r.nextInt(2 * k.h + 1) - k.h;
			if (Math.max(Math.abs(x), Math.abs(z)) >= k.h - 2) {
				k.set(x, 1, z, k.pick(Material.COBBLESTONE_SLAB, Material.MUD_BRICK_SLAB, k.wallB));
			}
		}
	}

	/** A cave of glowing crystals over still water (Fairy Gentleman, the Blubbering Toad). */
	private static void cave(Ctx k) {
		for (int a = 0; a < 360; a += 3) {
			double rad = k.h + 3 + k.r.nextInt(4);
			int x = (int) Math.round(Math.cos(Math.toRadians(a)) * rad);
			int z = (int) Math.round(Math.sin(Math.toRadians(a)) * rad);
			int top = 6 + k.r.nextInt(12);
			k.column(x, z, 1, top, k.pick(k.wallA, k.wallB, Material.DEEPSLATE));
		}
		// Stalactites over the ground and crystals round it.
		for (int i = 0; i < 30; i++) {
			int x = k.r.nextInt(2 * k.h + 9) - k.h - 4;
			int z = k.r.nextInt(2 * k.h + 9) - k.h - 4;
			if (Math.max(Math.abs(x), Math.abs(z)) < k.h - 3) {
				continue;
			}
			int tall = 2 + k.r.nextInt(5);
			k.column(x, z, 1, tall, k.glass);
			k.set(x, tall + 1, z, k.light);
		}
		k.around(k.h + 1, 2, (x, z) -> k.set(x, 0, z, Material.WATER));
	}

	/** Rooftops in a storm: a balustrade round the roof, chimneys, the City beyond. */
	private static void rooftop(Ctx k) {
		k.around(k.h, 1, (x, z) -> {
			k.set(x, 1, z, Material.STONE_BRICK_WALL);
			k.set(x, 2, z, (x + z) % 3 == 0 ? Material.STONE_BRICK_WALL : Material.AIR);
		});
		k.around(k.h, 2, (x, z) -> k.set(x, 2, z, Material.STONE_BRICK_SLAB));
		for (int i = 0; i < 8; i++) {
			int x = k.r.nextInt(2 * k.h + 9) - k.h - 4;
			int z = k.h + 2 + k.r.nextInt(6);
			k.column(x, z, 1, 3 + k.r.nextInt(4), k.wallA);
		}
		skyline(k, 4, 18, true, false, k.wallA, k.wallB, Material.DEEPSLATE_BRICKS);
	}

	/** A scrapyard on a murky green sea: heaps of junk, wrecks and rusted metal. */
	private static void scrapyard(Ctx k) {
		for (int i = 0; i < 26; i++) {
			int a = k.r.nextInt(360);
			double rad = k.h + 2 + k.r.nextInt(8);
			int x = (int) (Math.cos(Math.toRadians(a)) * rad);
			int z = (int) (Math.sin(Math.toRadians(a)) * rad);
			int r = 1 + k.r.nextInt(3);
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					int top = r - Math.max(Math.abs(dx), Math.abs(dz)) + 1;
					k.column(x + dx, z + dz, 1, top, k.pick(Material.EXPOSED_COPPER, Material.IRON_BLOCK, Material.RAW_IRON_BLOCK, k.wallA,
							Material.WEATHERED_CUT_COPPER));
				}
			}
		}
		k.around(k.h, 7, (x, z) -> k.set(x, 1, z, Material.LANTERN));
	}

	/** An ink-wash mountain scene (Pagoda Veneration): grey peaks and stacked stone cairns. */
	private static void ink(Ctx k) {
		for (int a = 0; a < 360; a += 6) {
			double rad = k.h + 8 + k.r.nextInt(5);
			int x = (int) Math.round(Math.cos(Math.toRadians(a)) * rad);
			int z = (int) Math.round(Math.sin(Math.toRadians(a)) * rad);
			int peak = 8 + k.r.nextInt(14);
			for (int dx = -3; dx <= 3; dx++) {
				for (int dz = -3; dz <= 3; dz++) {
					int top = peak - 3 * Math.max(Math.abs(dx), Math.abs(dz));
					if (top > 0) {
						k.column(x + dx, z + dz, 1, top, top > peak - 4 ? Material.LIGHT_GRAY_CONCRETE : k.pick(Material.GRAY_CONCRETE,
								Material.ANDESITE, Material.STONE));
					}
				}
			}
		}
		for (int i = 0; i < 12; i++) {
			int a = k.r.nextInt(360);
			double rad = k.h - 1 + k.r.nextInt(5);
			int x = (int) (Math.cos(Math.toRadians(a)) * rad);
			int z = (int) (Math.sin(Math.toRadians(a)) * rad);
			int tall = 2 + k.r.nextInt(4);
			for (int dy = 1; dy <= tall; dy++) {
				k.set(x, dy, z, dy % 2 == 0 ? Material.POLISHED_BLACKSTONE : Material.COBBLED_DEEPSLATE);
			}
		}
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
