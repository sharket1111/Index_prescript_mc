package io.github.sharket1111.indexprescript;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Server-side lookup of this mod's own translations. A dedicated server does
 * not load mod language files, but the prescript reveal needs the localized
 * text to scramble it letter by letter.
 */
final class ModLang {
	private static final String FALLBACK = "en_us";
	private static final Map<String, Map<String, String>> CACHE = new ConcurrentHashMap<>();

	private ModLang() {
	}

	static String format(String language, String key, Object... args) {
		String pattern = load(language).getOrDefault(key, load(FALLBACK).getOrDefault(key, key));
		try {
			return String.format(Locale.ROOT, pattern, args);
		} catch (RuntimeException e) {
			return pattern;
		}
	}

	private static Map<String, String> load(String language) {
		return CACHE.computeIfAbsent(language.toLowerCase(Locale.ROOT), lang -> {
			Map<String, String> entries = new HashMap<>();
			String path = "/assets/" + IndexPrescript.MOD_ID + "/lang/" + lang + ".json";
			try (InputStream in = ModLang.class.getResourceAsStream(path)) {
				if (in != null) {
					JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
					for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
						entries.put(entry.getKey(), entry.getValue().getAsString());
					}
				}
			} catch (Exception e) {
				IndexPrescript.LOGGER.warn("Could not read {}", path, e);
			}
			return entries;
		});
	}
}
