package io.github.sharket1111.indexprescript.paper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

/**
 * What the Season keeps per player (plugins/IndexPrescript/season.yml): Pass, missions, the Manager,
 * Mirror Projection, the Shop's purchase counts, cosmetics. Days start at 06:00 KST as in the original;
 * weeks on Wednesday 06:00 KST (the original's weekly reset), months on the 1st at 06:00 KST.
 */
final class Season {
	static final ZoneId KST = ZoneId.of("Asia/Seoul");
	private static YamlConfiguration data;
	private static File file;
	private static boolean dirty;

	private Season() {
	}

	static void load(IndexPrescriptPlugin plugin) {
		file = new File(plugin.getDataFolder(), "season.yml");
		data = YamlConfiguration.loadConfiguration(file);
	}

	/** Saves if anything changed (every few seconds and on shutdown). */
	static void save() {
		if (!dirty || data == null) {
			return;
		}
		dirty = false;
		try {
			data.save(file);
		} catch (IOException e) {
			IndexPrescriptPlugin.instance().getLogger().log(Level.WARNING, "Could not save season.yml", e);
		}
	}

	static JsonObject json(String name) {
		try (InputStream in = IndexPrescriptPlugin.instance().getResource("season/" + name + ".json")) {
			return new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
		} catch (IOException | RuntimeException e) {
			IndexPrescriptPlugin.instance().getLogger().log(Level.WARNING, "Could not read season/" + name + ".json", e);
			return new JsonObject();
		}
	}

	// --- per-player values ------------------------------------------------------------------------------

	private static String path(UUID id, String key) {
		return "players." + id + "." + key;
	}

	static int getInt(UUID id, String key) {
		return data.getInt(path(id, key), 0);
	}

	static long getLong(UUID id, String key) {
		return data.getLong(path(id, key), 0L);
	}

	static String getString(UUID id, String key) {
		return data.getString(path(id, key), "");
	}

	static boolean getBool(UUID id, String key) {
		return data.getBoolean(path(id, key), false);
	}

	static List<String> getList(UUID id, String key) {
		return new ArrayList<>(data.getStringList(path(id, key)));
	}

	static void set(UUID id, String key, Object value) {
		data.set(path(id, key), value);
		dirty = true;
	}

	static int add(UUID id, String key, int amount) {
		int v = getInt(id, key) + amount;
		set(id, key, v);
		return v;
	}

	static boolean addToList(UUID id, String key, String value) {
		List<String> list = getList(id, key);
		if (list.contains(value)) {
			return false;
		}
		list.add(value);
		set(id, key, list);
		return true;
	}

	static ConfigurationSection section(UUID id, String key) {
		ConfigurationSection s = data.getConfigurationSection(path(id, key));
		return s == null ? data.createSection(path(id, key)) : s;
	}

	/**
	 * A counter that starts over each period: {@code key} counts within the period named by {@code period}
	 * (day(), week() or month()); reading or adding in a new period starts from 0.
	 */
	static int periodic(UUID id, String key, String period) {
		return period.equals(getString(id, key + "_p")) ? getInt(id, key) : 0;
	}

	static int addPeriodic(UUID id, String key, String period, int amount) {
		int v = periodic(id, key, period) + amount;
		set(id, key + "_p", period);
		set(id, key, v);
		return v;
	}

	// --- the original's reset times (KST) ------------------------------------------------------------------

	private static ZonedDateTime shifted() {
		return ZonedDateTime.now(KST).minusHours(6);
	}

	/** Today (a day runs 06:00 to 06:00 KST). */
	static String day() {
		return shifted().toLocalDate().toString();
	}

	/** This week, named by the Wednesday it began on. */
	static String week() {
		return "W" + shifted().toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.WEDNESDAY));
	}

	static String month() {
		ZonedDateTime t = shifted();
		return "M" + t.getYear() + "-" + t.getMonthValue();
	}

	/** The current Season (the Pass's id). */
	static String season() {
		return "S8";
	}

	/** Seconds until the next daily, weekly reset. */
	static long untilNextDay() {
		ZonedDateTime now = ZonedDateTime.now(KST);
		ZonedDateTime next = now.withHour(6).withMinute(0).withSecond(0).withNano(0);
		if (!next.isAfter(now)) {
			next = next.plusDays(1);
		}
		return java.time.Duration.between(now, next).getSeconds();
	}

	static long untilNextWeek() {
		ZonedDateTime now = ZonedDateTime.now(KST);
		ZonedDateTime next = now.with(TemporalAdjusters.nextOrSame(DayOfWeek.WEDNESDAY)).withHour(6).withMinute(0).withSecond(0).withNano(0);
		if (!next.isAfter(now)) {
			next = next.plusWeeks(1);
		}
		return java.time.Duration.between(now, next).getSeconds();
	}

	static String duration(long seconds) {
		long d = seconds / 86400;
		long h = seconds % 86400 / 3600;
		long m = seconds % 3600 / 60;
		return d > 0 ? d + "일 " + h + "시간" : h > 0 ? h + "시간 " + m + "분" : m + "분";
	}
}
