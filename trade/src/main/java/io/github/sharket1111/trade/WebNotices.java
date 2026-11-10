package io.github.sharket1111.trade;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

/**
 * Notices on the web page's front tab (notices.yml): what admins post (in
 * game with /trade 공지, or on the web admin page) and every give-all, newest
 * first, up to {@link #MAX}. Synchronized: posted from the main thread, read
 * by the web threads.
 */
final class WebNotices {
	static final int MAX = 50;
	static final int TITLE_MAX = 60;
	static final int BODY_MAX = 600;

	record Notice(String id, String title, String body, String author, long time, boolean giveAll) {
	}

	private static Plugin plugin;
	private static File file;
	private static final List<Notice> NOTICES = new ArrayList<>();

	private WebNotices() {
	}

	static synchronized void load(Plugin owner) {
		plugin = owner;
		file = new File(owner.getDataFolder(), "notices.yml");
		NOTICES.clear();
		if (!file.exists()) {
			return;
		}
		for (Map<?, ?> map : YamlConfiguration.loadConfiguration(file).getMapList("notices")) {
			try {
				NOTICES.add(new Notice(String.valueOf(map.get("id")), String.valueOf(map.get("title")), String.valueOf(map.get("body")),
						String.valueOf(map.get("author")), ((Number) map.get("time")).longValue(), Boolean.TRUE.equals(map.get("give-all"))));
			} catch (RuntimeException e) {
				owner.getLogger().log(Level.WARNING, "Skipping a broken notice", e);
			}
		}
	}

	private static void save() {
		YamlConfiguration yaml = new YamlConfiguration();
		List<Map<String, Object>> list = new ArrayList<>();
		for (Notice notice : NOTICES) {
			list.add(Map.of("id", notice.id(), "title", notice.title(), "body", notice.body(), "author", notice.author(),
					"time", notice.time(), "give-all", notice.giveAll()));
		}
		yaml.set("notices", list);
		try {
			file.getParentFile().mkdirs();
			yaml.save(file);
		} catch (IOException e) {
			plugin.getLogger().log(Level.SEVERE, "Could not save notices.yml", e);
		}
	}

	static synchronized void post(String title, String body, String author, boolean giveAll) {
		String cleanTitle = clean(title, TITLE_MAX);
		String cleanBody = clean(body, BODY_MAX);
		NOTICES.add(0, new Notice(UUID.randomUUID().toString(), cleanTitle.isEmpty() ? "공지" : cleanTitle, cleanBody, author,
				System.currentTimeMillis(), giveAll));
		Discord.notice(cleanTitle.isEmpty() ? "공지" : cleanTitle, cleanBody, author, giveAll);
		while (NOTICES.size() > MAX) {
			NOTICES.remove(NOTICES.size() - 1);
		}
		save();
	}

	static synchronized boolean delete(String id) {
		boolean removed = NOTICES.removeIf(notice -> notice.id().equals(id));
		if (removed) {
			save();
		}
		return removed;
	}

	static synchronized List<Notice> all() {
		return List.copyOf(NOTICES);
	}

	private static String clean(String text, int max) {
		String out = text == null ? "" : text.replace("§", "").strip();
		return out.length() > max ? out.substring(0, max) : out;
	}
}
