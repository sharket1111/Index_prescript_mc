package io.github.sharket1111.indexprescript.paper;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * The Mirror Dungeon's data, made from the original's by tools/md_data.py and
 * bundled in the jar (md/*.json): E.G.O Gifts with their effects, Theme Packs
 * with their enemies and bosses, Abnormality events, and Mounting Trials.
 */
final class MdData {
	static final List<String> KEYWORDS = List.of("burn", "bleed", "tremor", "rupture", "sinking", "poise", "charge", "slash", "pierce", "blunt");
	static final Map<String, String> KEYWORD_KO = Map.of("burn", "화상", "bleed", "출혈", "tremor", "진동", "rupture", "파열", "sinking", "침잠",
			"poise", "호흡", "charge", "충전", "slash", "참격", "pierce", "관통", "blunt", "타격");
	static final Map<String, String> SIN_KO = Map.of("wrath", "분노", "lust", "색욕", "sloth", "나태", "gluttony", "탐식", "gloom", "우울",
			"pride", "오만", "envy", "질투");
	/** Gift tiers as numbers (V and EX count as 5). */
	static final Map<String, Integer> TIER = Map.of("I", 1, "II", 2, "III", 3, "IV", 4, "V", 5, "EX", 5);

	record Level(String text, String textKo, JsonArray effects, List<String> ko, boolean generic) {
	}

	record Gift(String id, String name, String tier, int tierNumber, String sin, String keyword, int cost, int enhance, boolean themeOnly,
			boolean fusionOnly, List<Level> levels) {
		/** Can turn up in shops, rewards and random fusions (not theme-only, not a fixed fusion's result, not priceless). */
		boolean common() {
			return !themeOnly && !fusionOnly && cost < 999 && tierNumber <= 4;
		}

		Level level(int n) {
			return levels.get(Math.max(0, Math.min(levels.size() - 1, n)));
		}

		String keywordKo() {
			return KEYWORD_KO.getOrDefault(keyword, "범용");
		}
	}

	record Enemy(String name, String risk) {
	}

	/** An original skill: power, coins, and effects as [on, act, status, potency, count] objects (skill-wide and per coin). */
	record EnemySkill(String name, String sin, String type, int base, int coinPower, int coins, int weight, String text, List<String> coinText,
			JsonArray effects, List<JsonArray> coinEffects, String part) {
		boolean attack() {
			return type.equals("slash") || type.equals("pierce") || type.equals("blunt");
		}
	}

	/** A passive: its text, the effects the plugin plays, and the new pattern it brings (phase: "at" HP fractions or a "turn"), if any. */
	record Passive(String name, String text, JsonArray effects, JsonObject phase) {
	}

	/** One part of an Abnormality (Body, Wings, Arm...): its HP, stagger, resistances, and how it breaks (destroy / sever / none, regrowing after regen turns). */
	record Part(String name, String icon, String breaks, int regen, double hp, double hpGrowth, List<Double> stagger, Map<String, Double> resist) {
		double hpAt(int lv, int baseLevel) {
			return Math.max(1, hp + hpGrowth * lv);
		}
	}

	/** An original enemy's numbers (Limbus Company Wiki): HP at a level, stagger thresholds (%), resistances, skills, passives. */
	record EnemyData(String name, double hp, double hpGrowth, int level, int[] speed, int defense, List<Double> stagger, Map<String, Double> resist,
			int maxActions, String risk, List<EnemySkill> skills, List<Passive> passives, String faction, List<Part> parts) {
		/** HP at level {@code lv}: the wiki's base HP plus its growth per level (the listed level is only where it was met). */
		double hpAt(int lv) {
			return Math.max(1, hp + hpGrowth * lv);
		}
	}

	/** How an enemy looks (tools/md_looks.py): outfit md_<look> on the rig, its size, bone proportions, and weapon item ("" for none). */
	record Look(String look, float scale, Map<String, float[]> bones, String weapon) {
	}

	record Theme(String id, String name, String category, int[] normal, int[] hard, int length, String layout, String boss,
			List<Enemy> bosses, Map<String, List<Enemy>> enemies, int color, String keyword, String sin, List<String> unique, List<String> events) {
		boolean on(int floor, boolean hardMode) {
			int[] range = hardMode ? hard : normal;
			return range != null && floor >= range[0] && floor <= range[1];
		}
	}

	record Result(String what, int amount, String who, String gift, List<String> gifts) {
	}

	record Check(String prompt, List<String> sins, int adv, int disadv, String passText, List<Result> pass, String passResult, String failText,
			List<Result> fail, String failResult) {
	}

	record Choice(String label, String desc, String story, List<Result> result, String resultText, Check check) {
	}

	record Event(String id, String name, String abnormality, String kr, String theme, String story, List<Choice> choices) {
	}

	record Trial(int floor, String name, String text, Map<String, Double> stats) {
	}

	private static final Map<String, Gift> GIFTS = new LinkedHashMap<>();
	private static final Map<String, Theme> THEMES = new LinkedHashMap<>();
	private static final Map<String, Event> EVENTS = new LinkedHashMap<>();
	private static final List<Trial> TRIALS = new ArrayList<>();
	private static final Map<String, EnemyData> ENEMIES = new LinkedHashMap<>();
	private static final Map<String, Look> LOOKS = new LinkedHashMap<>();
	/** Fixed fusions: the ingredients (as a set) and the result. */
	private static final Map<java.util.Set<String>, String> RECIPES = new LinkedHashMap<>();

	private MdData() {
	}

	static void load(IndexPrescriptPlugin plugin) {
		Gson gson = new Gson();
		try {
			for (JsonElement e : read(plugin, gson, "gifts").getAsJsonArray("gifts")) {
				JsonObject o = e.getAsJsonObject();
				List<Level> levels = new ArrayList<>();
				for (JsonElement l : o.getAsJsonArray("levels")) {
					JsonObject lo = l.getAsJsonObject();
					List<String> ko = new ArrayList<>();
					lo.getAsJsonArray("ko").forEach(k -> ko.add(k.getAsString()));
					levels.add(new Level(lo.get("text").getAsString(), lo.has("text_ko") ? lo.get("text_ko").getAsString() : null, lo.getAsJsonArray("effects"), ko,
							lo.has("generic")));
				}
				String tier = o.get("tier").getAsString();
				// The Korean name where there is one (lookups by the original's name go by id).
				String name = o.has("name_ko") && !o.get("name_ko").isJsonNull() ? o.get("name_ko").getAsString() : o.get("name").getAsString();
				Gift gift = new Gift(o.get("id").getAsString(), name, tier, TIER.getOrDefault(tier, 1), str(o, "sin", "wrath"),
						str(o, "keyword", "none"), o.get("cost").getAsInt(), o.get("enhance").getAsInt(), o.get("theme_only").getAsBoolean(),
						o.has("fusion_only") && o.get("fusion_only").getAsBoolean(), levels);
				GIFTS.put(gift.id(), gift);
			}
			for (JsonElement e : read(plugin, gson, "events").getAsJsonArray("events")) {
				Event ev = event(e.getAsJsonObject());
				EVENTS.put(ev.id(), ev);
			}
			for (JsonElement e : read(plugin, gson, "themes").getAsJsonArray("themes")) {
				JsonObject o = e.getAsJsonObject();
				Map<String, List<Enemy>> enemies = new LinkedHashMap<>();
				for (Map.Entry<String, JsonElement> pool : o.getAsJsonObject("enemies").entrySet()) {
					enemies.put(pool.getKey(), enemies(pool.getValue().getAsJsonArray()));
				}
				List<String> unique = strings(o.getAsJsonArray("unique"));
				Theme theme = new Theme(o.get("id").getAsString(), o.get("name").getAsString(), o.get("category").getAsString(), range(o.get("normal")),
						range(o.get("hard")), o.get("length").getAsInt(), o.get("layout").getAsString(), o.get("boss").getAsString(),
						enemies(o.getAsJsonArray("bosses")), enemies, Integer.parseInt(o.get("color").getAsString().substring(1), 16), str(o, "keyword", null),
						str(o, "sin", null), unique, strings(o.getAsJsonArray("events")));
				THEMES.put(theme.id(), theme);
			}
			for (JsonElement e : read(plugin, gson, "trials").getAsJsonArray("trials")) {
				JsonObject o = e.getAsJsonObject();
				Map<String, Double> stats = new LinkedHashMap<>();
				o.getAsJsonObject("stats").entrySet().forEach(s -> stats.put(s.getKey(), s.getValue().getAsDouble()));
				TRIALS.add(new Trial(o.get("floor").getAsInt(), o.get("name").getAsString(), o.get("text").getAsString(), stats));
			}
			for (Map.Entry<String, JsonElement> e : read(plugin, gson, "enemies").getAsJsonObject("enemies").entrySet()) {
				JsonObject o = e.getValue().getAsJsonObject();
				List<EnemySkill> skills = new ArrayList<>();
				for (JsonElement sk : o.getAsJsonArray("skills")) {
					JsonObject k = sk.getAsJsonObject();
					List<JsonArray> coinEffects = new ArrayList<>();
					k.getAsJsonArray("coin_effects").forEach(c -> coinEffects.add(c.getAsJsonArray()));
					skills.add(new EnemySkill(k.get("name").getAsString(), k.get("sin").getAsString(), k.get("type").getAsString(), k.get("base").getAsInt(),
							k.get("coin_power").getAsInt(), k.get("coins").getAsInt(), k.get("weight").getAsInt(), k.get("text").getAsString(),
							strings(k.getAsJsonArray("coin_text")), k.getAsJsonArray("effects"), coinEffects, str(k, "part", "")));
				}
				List<Passive> passives = new ArrayList<>();
				for (JsonElement ps : o.getAsJsonArray("passives")) {
					JsonObject k = ps.getAsJsonObject();
					passives.add(new Passive(k.get("name").getAsString(), k.get("text").getAsString(), k.getAsJsonArray("effects"),
							k.has("phase") ? k.getAsJsonObject("phase") : null));
				}
				List<Part> parts = new ArrayList<>();
				if (o.has("parts")) {
					for (JsonElement pe : o.getAsJsonArray("parts")) {
						JsonObject k = pe.getAsJsonObject();
						Map<String, Double> pr = new LinkedHashMap<>();
						k.getAsJsonObject("resist").entrySet().forEach(r -> pr.put(r.getKey(), r.getValue().getAsDouble()));
						List<Double> ps = new ArrayList<>();
						k.getAsJsonArray("stagger").forEach(x -> ps.add(x.getAsDouble()));
						parts.add(new Part(k.get("name").getAsString(), k.get("icon").getAsString(), k.get("break").getAsString(), k.get("regen").getAsInt(),
								k.get("hp").getAsDouble(), k.get("hp_growth").getAsDouble(), ps, pr));
					}
				}
				Map<String, Double> resist = new LinkedHashMap<>();
				o.getAsJsonObject("resist").entrySet().forEach(r -> resist.put(r.getKey(), r.getValue().getAsDouble()));
				List<Double> stagger = new ArrayList<>();
				o.getAsJsonArray("stagger").forEach(x -> stagger.add(x.getAsDouble()));
				JsonArray sp = o.getAsJsonArray("speed");
				ENEMIES.put(e.getKey(), new EnemyData(e.getKey(), o.get("hp").getAsDouble(), o.get("hp_growth").getAsDouble(), o.get("level").getAsInt(),
						new int[] {sp.get(0).getAsInt(), sp.size() > 1 ? sp.get(1).getAsInt() : sp.get(0).getAsInt()}, o.get("defense").getAsInt(), stagger,
						resist, o.get("max_actions").getAsInt(), o.get("risk").getAsString(), skills, passives, str(o, "faction", ""), parts));
			}
			JsonObject looks = read(plugin, gson, "looks");
			Map<String, Look> forms = new LinkedHashMap<>();
			for (Map.Entry<String, JsonElement> f : looks.getAsJsonObject("forms").entrySet()) {
				JsonObject o = f.getValue().getAsJsonObject();
				Map<String, float[]> bones = new LinkedHashMap<>();
				for (Map.Entry<String, JsonElement> b : o.getAsJsonObject("bones").entrySet()) {
					JsonArray a = b.getValue().getAsJsonArray();
					bones.put(b.getKey(), new float[] {a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()});
				}
				forms.put(f.getKey(), new Look("", o.get("scale").getAsFloat(), bones, ""));
			}
			for (Map.Entry<String, JsonElement> e : looks.getAsJsonObject("enemies").entrySet()) {
				JsonObject o = e.getValue().getAsJsonObject();
				Look form = forms.getOrDefault(o.get("form").getAsString(), new Look("", 1, Map.of(), ""));
				LOOKS.put(e.getKey(), new Look(o.get("look").getAsString(), form.scale(), form.bones(), o.get("weapon").getAsString()));
			}
			for (JsonElement e : read(plugin, gson, "recipes").getAsJsonArray("recipes")) {
				JsonObject o = e.getAsJsonObject();
				RECIPES.put(java.util.Set.copyOf(strings(o.getAsJsonArray("ingredients"))), o.get("result").getAsString());
			}
			plugin.getLogger().info("Mirror Dungeon: " + ENEMIES.size() + " enemies, " + RECIPES.size() + " fusion recipes, " + GIFTS.size() + " gifts, " + THEMES.size() + " theme packs, " + EVENTS.size() + " events, "
					+ TRIALS.size() + " trials");
		} catch (RuntimeException | java.io.IOException e) {
			plugin.getLogger().log(java.util.logging.Level.SEVERE, "Could not read the Mirror Dungeon data", e);
		}
	}

	private static JsonObject read(IndexPrescriptPlugin plugin, Gson gson, String name) throws java.io.IOException {
		try (InputStream in = plugin.getResource("md/" + name + ".json")) {
			if (in == null) {
				throw new java.io.IOException("md/" + name + ".json missing from the jar");
			}
			return gson.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
		}
	}

	private static String str(JsonObject o, String key, String fallback) {
		return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : fallback;
	}

	private static List<String> strings(JsonArray array) {
		List<String> out = new ArrayList<>();
		if (array != null) {
			array.forEach(x -> out.add(x.getAsString()));
		}
		return out;
	}

	private static int[] range(JsonElement e) {
		if (e == null || e.isJsonNull()) {
			return null;
		}
		JsonArray a = e.getAsJsonArray();
		return new int[] {a.get(0).getAsInt(), a.get(1).getAsInt()};
	}

	private static List<Enemy> enemies(JsonArray array) {
		List<Enemy> out = new ArrayList<>();
		if (array != null) {
			for (JsonElement x : array) {
				JsonObject o = x.getAsJsonObject();
				out.add(new Enemy(o.get("name").getAsString(), str(o, "risk", "human")));
			}
		}
		return out;
	}

	private static List<Result> results(JsonArray array) {
		List<Result> out = new ArrayList<>();
		if (array == null) {
			return out;
		}
		for (JsonElement x : array) {
			JsonObject o = x.getAsJsonObject();
			out.add(new Result(o.get("do").getAsString(), o.has("amount") ? o.get("amount").getAsInt() : 0, str(o, "who", "all"), str(o, "name", null),
					o.has("gifts") ? strings(o.getAsJsonArray("gifts")) : List.of()));
		}
		return out;
	}

	private static Event event(JsonObject o) {
		List<Choice> choices = new ArrayList<>();
		for (JsonElement c : o.getAsJsonArray("choices")) {
			JsonObject co = c.getAsJsonObject();
			Check check = null;
			if (co.has("check")) {
				JsonObject k = co.getAsJsonObject("check");
				JsonObject pass = k.getAsJsonObject("pass");
				JsonObject fail = k.getAsJsonObject("fail");
				check = new Check(str(k, "prompt", ""), strings(k.getAsJsonArray("sins")), k.get("adv").getAsInt(),
						k.has("disadv") && !k.get("disadv").isJsonNull() ? k.get("disadv").getAsInt() : k.get("adv").getAsInt() + 8,
						str(pass, "text", ""), results(pass.getAsJsonArray("result")), str(pass, "result_text", ""), str(fail, "text", ""),
						results(fail.getAsJsonArray("result")), str(fail, "result_text", ""));
			}
			choices.add(new Choice(str(co, "label", ""), str(co, "desc", ""), str(co, "story", ""), results(co.getAsJsonArray("result")),
					str(co, "result_text", ""), check));
		}
		return new Event(o.get("id").getAsString(), o.get("name").getAsString(), str(o, "abnormality", null), str(o, "kr", ""), str(o, "theme", null),
				str(o, "story", ""), choices);
	}

	// --- lookups -----------------------------------------------------------------------------

	static Gift gift(String id) {
		return GIFTS.get(id);
	}

	/** A gift by its name as events write it (quotes and "(MD)" vary). */
	static Gift giftByName(String name) {
		String id = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
		Gift gift = GIFTS.get(id);
		return gift != null ? gift : GIFTS.get(id + "_md");
	}

	static List<Gift> gifts() {
		return Collections.unmodifiableList(new ArrayList<>(GIFTS.values()));
	}

	static Theme theme(String id) {
		return THEMES.get(id);
	}

	static List<Theme> themes() {
		return List.copyOf(THEMES.values());
	}

	static Event event(String id) {
		return EVENTS.get(id);
	}

	/** Events that belong to no Theme Pack (the Abnormality events). */
	static List<Event> commonEvents() {
		return EVENTS.values().stream().filter(e -> e.theme() == null).toList();
	}

	static Look look(String name) {
		return LOOKS.get(name);
	}

	static EnemyData enemy(String name) {
		return ENEMIES.get(name);
	}

	/** The fixed fusion result for these ingredients, or null. */
	static Gift recipe(java.util.Collection<String> ingredients) {
		String result = RECIPES.get(java.util.Set.copyOf(ingredients));
		return result == null ? null : GIFTS.get(result);
	}

	static List<Trial> trials(int floor) {
		return TRIALS.stream().filter(t -> t.floor() == floor).toList();
	}

	static boolean loaded() {
		return !GIFTS.isEmpty() && !THEMES.isEmpty();
	}
}
