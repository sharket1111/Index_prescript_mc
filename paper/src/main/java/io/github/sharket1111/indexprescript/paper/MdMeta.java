package io.github.sharket1111.indexprescript.paper;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.IsoFields;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * What a player keeps between Mirror Dungeon runs (plugins/IndexPrescript/mirror.yml):
 * Starlight, the weekly reward claims (3 a week, reset on Monday, server time),
 * and a few records (runs, best floor).
 */
final class MdMeta {
	/** Starlight given the first time a player enters, as the original gives on unlocking a new Mirror Dungeon. */
	static final int FIRST_STARLIGHT = 1200;
	static final int WEEKLY_CLAIMS = 3;
	private static YamlConfiguration data;
	private static File file;

	private MdMeta() {
	}

	static void load(IndexPrescriptPlugin plugin) {
		file = new File(plugin.getDataFolder(), "mirror.yml");
		data = YamlConfiguration.loadConfiguration(file);
	}

	static void save() {
		try {
			data.save(file);
		} catch (IOException e) {
			IndexPrescriptPlugin.instance().getLogger().log(Level.WARNING, "Could not save mirror.yml", e);
		}
	}

	private static String path(UUID id, String key) {
		return "players." + id + "." + key;
	}

	/** Gives the first-time Starlight once. */
	static void welcome(Player player) {
		if (!data.contains(path(player.getUniqueId(), "starlight"))) {
			data.set(path(player.getUniqueId(), "starlight"), FIRST_STARLIGHT);
			save();
			player.sendMessage(Component.text("거울 던전에 처음 오셨어요. 별빛 " + FIRST_STARLIGHT + "을 드렸어요.", NamedTextColor.AQUA));
		}
	}

	static int starlight(UUID id) {
		return data.getInt(path(id, "starlight"), 0);
	}

	static void addStarlight(UUID id, int amount) {
		data.set(path(id, "starlight"), Math.max(0, starlight(id) + amount));
		save();
	}

	/** Spends Starlight if there is enough. */
	static boolean spendStarlight(UUID id, int amount) {
		if (amount <= 0) {
			return true;
		}
		if (starlight(id) < amount) {
			return false;
		}
		addStarlight(id, -amount);
		return true;
	}

	private static String week() {
		LocalDate today = LocalDate.now(ZoneId.systemDefault());
		return today.get(IsoFields.WEEK_BASED_YEAR) + "-W" + today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
	}

	/** Weekly reward claims used this week. */
	static int claims(UUID id) {
		return week().equals(data.getString(path(id, "week"))) ? data.getInt(path(id, "claims"), 0) : 0;
	}

	/**
	 * Uses one weekly claim if any are left this week, running the configured
	 * reward commands (mirror-dungeon.rewards); returns true if a claim was used.
	 */
	static boolean claim(Player player, int floors, boolean hard) {
		UUID id = player.getUniqueId();
		int used = claims(id);
		if (used >= WEEKLY_CLAIMS) {
			player.sendMessage(Component.text("이번 주 거울 던전 보상은 모두 받았어요 (" + WEEKLY_CLAIMS + "/" + WEEKLY_CLAIMS + "). 별빛은 계속 쌓여요.",
					NamedTextColor.GRAY));
			return false;
		}
		data.set(path(id, "week"), week());
		data.set(path(id, "claims"), used + 1);
		save();
		List<String> commands = IndexPrescriptPlugin.instance().getConfig().getStringList("mirror-dungeon.rewards." + (hard ? "hard" : "normal"));
		for (String command : commands) {
			Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("%player%", player.getName()).replace("%floors%", String.valueOf(floors)));
		}
		player.sendMessage(Component.text("주간 보상 " + (used + 1) + "/" + WEEKLY_CLAIMS + "회를 사용했어요."
				+ (commands.isEmpty() ? " (보상 내용은 아직 정해지지 않았어요)" : ""), NamedTextColor.GOLD));
		return true;
	}

	static void record(UUID id, int floors) {
		data.set(path(id, "runs"), data.getInt(path(id, "runs"), 0) + 1);
		data.set(path(id, "best"), Math.max(floors, data.getInt(path(id, "best"), 0)));
		save();
	}

	static int runs(UUID id) {
		return data.getInt(path(id, "runs"), 0);
	}

	static int best(UUID id) {
		return data.getInt(path(id, "best"), 0);
	}
}
