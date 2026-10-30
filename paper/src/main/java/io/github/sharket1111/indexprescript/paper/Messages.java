package io.github.sharket1111.indexprescript.paper;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Server-side text in each player's language. Plain strings are sent instead
 * of translatable components so Bedrock players (via Geyser) see real text.
 */
final class Messages {
	private static final String FALLBACK = "en_us";
	private static final Map<String, Map<String, String>> CACHE = new ConcurrentHashMap<>();

	private Messages() {
	}

	static String get(CommandSender sender, String key, Object... args) {
		return format(language(sender), key, args);
	}

	static String language(CommandSender sender) {
		if (sender instanceof Player player) {
			Locale locale = player.locale();
			String tag = (locale.getLanguage() + "_" + locale.getCountry()).toLowerCase(Locale.ROOT);
			if (!load(tag).isEmpty()) {
				return tag;
			}
			if (locale.getLanguage().equals("ko")) {
				return "ko_kr";
			}
		}
		return FALLBACK;
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
		return CACHE.computeIfAbsent(language, lang -> {
			Map<String, String> entries = new HashMap<>();
			try (InputStream in = Messages.class.getResourceAsStream("/messages/" + lang + ".json")) {
				if (in != null) {
					JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
					for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
						entries.put(entry.getKey(), entry.getValue().getAsString());
					}
				}
			} catch (Exception ignored) {
				// Unknown or unreadable language: fall back to English.
			}
			return entries;
		});
	}
}
