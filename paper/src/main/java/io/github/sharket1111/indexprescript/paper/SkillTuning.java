package io.github.sharket1111.indexprescript.paper;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

/**
 * Skill numbers an admin can change without rebuilding (skills.yml, made by
 * the mod studio's skill editor or by hand): every skill card's name, base
 * power, coin power, coins, coin types, length and text, and Mimicry's base,
 * coin power, coin damage, cooldown and text. Missing entries keep the
 * built-in values, which are remembered so a reload can always go back.
 * /ip skills reload reads the file again.
 */
final class SkillTuning {
	private static Plugin plugin;
	/** The built-in cards, by key ("STOMP", "RIEN-3"), taken before any change. */
	private static final Map<String, SkillCard> DEFAULT_CARDS = new LinkedHashMap<>();
	private static final Map<MimicrySkill.Kind, Object[]> DEFAULT_MIMICRY = new LinkedHashMap<>();

	private SkillTuning() {
	}

	/** skills.json (what the mod studio saves; JSON is also YAML) when present, otherwise skills.yml. */
	static File file() {
		File json = new File(plugin.getDataFolder(), "skills.json");
		return json.exists() ? json : new File(plugin.getDataFolder(), "skills.yml");
	}

	/** Remembers the built-in values, writes skills.yml with them if it is missing, then applies the file. Returns a short report. */
	static String load(Plugin owner) {
		plugin = owner;
		if (DEFAULT_CARDS.isEmpty()) {
			cards().forEach((key, card) -> DEFAULT_CARDS.put(key, card));
			for (MimicrySkill.Kind kind : MimicrySkill.Kind.values()) {
				DEFAULT_MIMICRY.put(kind, new Object[] {kind.base, kind.coinPower, kind.dice.clone(), kind.cooldown, kind.ability});
			}
		}
		if (!file().exists()) {
			writeDefaults();
		}
		// Back to the built-in values, then the file on top.
		DEFAULT_CARDS.forEach(SkillTuning::set);
		DEFAULT_MIMICRY.forEach((kind, v) -> {
			kind.base = (int) v[0];
			kind.coinPower = (int) v[1];
			kind.dice = ((double[]) v[2]).clone();
			kind.cooldown = (int) v[3];
			kind.ability = (String) v[4];
		});
		YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file());
		int changed = 0;
		List<String> problems = new ArrayList<>();
		ConfigurationSection cards = yaml.getConfigurationSection("cards");
		if (cards != null) {
			for (String key : cards.getKeys(false)) {
				SkillCard card = DEFAULT_CARDS.get(key);
				if (card == null) {
					problems.add("cards." + key + " (없는 스킬)");
					continue;
				}
				ConfigurationSection s = cards.getConfigurationSection(key);
				int coins = Math.max(1, Math.min(12, s.getInt("coins", card.coins())));
				String types = s.getString("types", card.types()).toUpperCase().replaceAll("[^NRG]", "");
				types = (types + "N".repeat(coins)).substring(0, coins);
				SkillCard tuned = new SkillCard(s.getString("name", card.name()), card.colour(), s.getInt("base", card.base()),
						s.getInt("coin-power", card.coinPower()), coins, s.getString("description", card.description()),
						Math.max(1, s.getInt("ticks", card.ticks())), card.clashes(), types);
				if (!tuned.equals(card)) {
					changed++;
				}
				set(key, tuned);
			}
		}
		ConfigurationSection mimicry = yaml.getConfigurationSection("mimicry");
		if (mimicry != null) {
			for (String key : mimicry.getKeys(false)) {
				MimicrySkill.Kind kind;
				try {
					kind = MimicrySkill.Kind.valueOf(key);
				} catch (IllegalArgumentException e) {
					problems.add("mimicry." + key + " (없는 스킬)");
					continue;
				}
				ConfigurationSection s = mimicry.getConfigurationSection(key);
				Object[] before = {kind.base, kind.coinPower, kind.dice.clone(), kind.cooldown, kind.ability};
				kind.base = s.getInt("base", kind.base);
				kind.coinPower = s.getInt("coin-power", kind.coinPower);
				kind.cooldown = Math.max(0, s.getInt("cooldown", kind.cooldown));
				kind.ability = s.getString("description", kind.ability);
				List<Double> dice = s.getDoubleList("coin-damage");
				if (!dice.isEmpty()) {
					if (dice.size() != kind.coins.length) {
						problems.add("mimicry." + key + ".coin-damage (코인 " + kind.coins.length + "개만큼 써야 해요)");
					} else {
						kind.dice = dice.stream().mapToDouble(Double::doubleValue).toArray();
					}
				}
				if (kind.base != (int) before[0] || kind.coinPower != (int) before[1] || kind.cooldown != (int) before[3]
						|| !kind.ability.equals(before[4]) || !java.util.Arrays.equals(kind.dice, (double[]) before[2])) {
					changed++;
				}
			}
		}
		String report = file().getName() + ": 스킬 카드 " + DEFAULT_CARDS.size() + "개, 미미크리 " + DEFAULT_MIMICRY.size() + "개를 읽었어요 (바뀐 것 " + changed + "개)";
		if (!problems.isEmpty()) {
			report += ". 문제: " + String.join(", ", problems);
			owner.getLogger().warning(report);
		}
		return report;
	}

	/** Every card field of {@link SkillCard}, by key: single cards by field name, array cards as NAME-index (a dot would nest in YAML). */
	private static Map<String, SkillCard> cards() {
		Map<String, SkillCard> out = new LinkedHashMap<>();
		for (Field field : SkillCard.class.getDeclaredFields()) {
			if (!Modifier.isStatic(field.getModifiers())) {
				continue;
			}
			try {
				field.setAccessible(true);
				Object value = field.get(null);
				if (value instanceof SkillCard card) {
					out.put(field.getName(), card);
				} else if (value instanceof SkillCard[] array) {
					for (int i = 0; i < array.length; i++) {
						out.put(field.getName() + "-" + i, array[i]);
					}
				}
			} catch (IllegalAccessException e) {
				plugin.getLogger().log(Level.WARNING, "Cannot read skill " + field.getName(), e);
			}
		}
		return out;
	}

	private static void set(String key, SkillCard card) {
		try {
			int dot = key.indexOf('-');
			Field field = SkillCard.class.getDeclaredField(dot < 0 ? key : key.substring(0, dot));
			field.setAccessible(true);
			if (dot < 0) {
				field.set(null, card);
			} else {
				((SkillCard[]) field.get(null))[Integer.parseInt(key.substring(dot + 1))] = card;
			}
		} catch (ReflectiveOperationException | RuntimeException e) {
			plugin.getLogger().log(Level.WARNING, "Cannot set skill " + key, e);
		}
	}

	private static void writeDefaults() {
		YamlConfiguration yaml = new YamlConfiguration();
		yaml.options().setHeader(List.of(
				"IndexPrescript skill numbers. Change a value and run /ip skills reload (no restart needed).",
				"cards: name, base (기본 위력), coin-power (코인 위력), coins (코인 수), types (코인 종류: N 보통, R 파괴 불가, G 처형),",
				"  ticks (스킬 길이, 애니메이션과 맞춰 둔 값이라 바꾸면 연출이 어긋날 수 있어요), description.",
				"mimicry: base, coin-power, coin-damage (코인마다 피해), cooldown (틱), description.",
				"Delete an entry (or this file) to go back to the built-in values. The mod studio's skill editor writes this file."));
		DEFAULT_CARDS.forEach((key, card) -> {
			String path = "cards." + key;
			yaml.set(path + ".name", card.name());
			yaml.set(path + ".base", card.base());
			yaml.set(path + ".coin-power", card.coinPower());
			yaml.set(path + ".coins", card.coins());
			yaml.set(path + ".types", card.types());
			yaml.set(path + ".ticks", card.ticks());
			yaml.set(path + ".description", card.description());
		});
		DEFAULT_MIMICRY.forEach((kind, v) -> {
			String path = "mimicry." + kind.name();
			yaml.set(path + ".name", kind.name);
			yaml.set(path + ".base", v[0]);
			yaml.set(path + ".coin-power", v[1]);
			List<Double> dice = new ArrayList<>();
			for (double d : (double[]) v[2]) {
				dice.add(d);
			}
			yaml.set(path + ".coin-damage", dice);
			yaml.set(path + ".cooldown", v[3]);
			yaml.set(path + ".description", v[4]);
		});
		try {
			plugin.getDataFolder().mkdirs();
			yaml.save(new File(plugin.getDataFolder(), "skills.yml"));
		} catch (IOException e) {
			plugin.getLogger().log(Level.WARNING, "Could not write skills.yml", e);
		}
	}
}
