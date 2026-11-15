package io.github.sharket1111.indexprescript.paper;

import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * The Limbus Pass missions, as in the original (season/missions.json): daily ones give 2 Pass XP, weekly 4,
 * Season 5 or 8, and are claimed from the menu. What counts here: an Enkephalin Module assembled; an EXP or
 * Thread Luxcavation cleared; a "stage" is a Luxcavation or a Mirror Dungeon battle won; an enemy is a
 * hostile creature or a Mirror Dungeon enemy killed; an Abnormality suppressed is a Mirror Dungeon
 * Abnormality battle won; log-ins count once a day.
 */
final class Missions implements Listener {
	private static JsonObject data = new JsonObject();
	private static final String[] PERIODS = {"daily", "weekly", "season"};
	private static final String[] PERIODS_KO = {"일일", "주간", "시즌"};

	static void load() {
		data = Season.json("missions");
	}

	private static String period(String p) {
		return switch (p) {
			case "daily" -> Season.day();
			case "weekly" -> Season.week();
			default -> Season.season();
		};
	}

	private static int count(UUID id, String period, JsonObject m) {
		return Season.periodic(id, "mis_" + m.get("id").getAsString(), period(period));
	}

	private static boolean claimed(UUID id, String period, JsonObject m) {
		return Season.periodic(id, "mis_c_" + m.get("id").getAsString(), period(period)) > 0;
	}

	/** Counts {@code n} towards every mission that tracks {@code track}. */
	static void progress(Player p, String track, int n) {
		if (n <= 0 || !data.has("daily")) {
			return;
		}
		UUID id = p.getUniqueId();
		for (int i = 0; i < PERIODS.length; i++) {
			for (JsonElement e : data.getAsJsonArray(PERIODS[i])) {
				JsonObject m = e.getAsJsonObject();
				if (!m.get("track").getAsString().equals(track)) {
					continue;
				}
				int before = count(id, PERIODS[i], m);
				int goal = m.get("goal").getAsInt();
				if (before >= goal) {
					continue;
				}
				int after = Math.min(goal, before + n);
				Season.addPeriodic(id, "mis_" + m.get("id").getAsString(), period(PERIODS[i]), after - before);
				if (after >= goal) {
					p.sendMessage(Component.text("[" + PERIODS_KO[i] + " 미션 달성] " + m.get("text").getAsString() + " (/메뉴 → 림버스 패스 → 미션)",
							NamedTextColor.AQUA));
				}
			}
		}
	}

	/** For "reach" missions (Pass level): the value reached. */
	static void reach(Player p, String track, int value) {
		if (!data.has("season")) {
			return;
		}
		UUID id = p.getUniqueId();
		for (JsonElement e : data.getAsJsonArray("season")) {
			JsonObject m = e.getAsJsonObject();
			if (m.get("track").getAsString().equals(track)) {
				int before = count(id, "season", m);
				int target = Math.min(m.get("goal").getAsInt(), value);
				if (target > before) {
					Season.addPeriodic(id, "mis_" + m.get("id").getAsString(), Season.season(), target - before);
				}
			}
		}
	}

	private static boolean claim(Player p, String period, JsonObject m) {
		UUID id = p.getUniqueId();
		if (claimed(id, period, m) || count(id, period, m) < m.get("goal").getAsInt()) {
			return false;
		}
		Season.addPeriodic(id, "mis_c_" + m.get("id").getAsString(), period(period), 1);
		Pass.addXp(p, m.get("xp").getAsInt());
		return true;
	}

	static void open(Player p) {
		UUID id = p.getUniqueId();
		MdGui gui = new MdGui(6, "미션");
		gui.set(4, MdGui.item(Material.CLOCK, "초기화", NamedTextColor.WHITE, "일일: " + Season.duration(Season.untilNextDay()) + " 뒤 (매일 06:00)",
				"주간: " + Season.duration(Season.untilNextWeek()) + " 뒤 (수요일 06:00)", "시즌 미션은 시즌이 끝날 때까지"), (Runnable) null);
		for (int i = 0; i < PERIODS.length; i++) {
			if (!data.has(PERIODS[i])) {
				continue;
			}
			int row = i == 0 ? 9 : i == 1 ? 18 : 36;
			int col = 0;
			for (JsonElement e : data.getAsJsonArray(PERIODS[i])) {
				JsonObject m = e.getAsJsonObject();
				String period = PERIODS[i];
				int have = count(id, period, m);
				int goal = m.get("goal").getAsInt();
				boolean got = claimed(id, period, m);
				boolean ready = !got && have >= goal;
				var icon = MdGui.item(got ? Material.GRAY_DYE : ready ? Material.LIME_DYE : Material.PAPER,
						"[" + PERIODS_KO[i] + "] " + m.get("text").getAsString(), got ? NamedTextColor.DARK_GRAY : ready ? NamedTextColor.GREEN : NamedTextColor.WHITE,
						"진행 " + Math.min(have, goal) + "/" + goal, "보상: 패스 XP " + m.get("xp").getAsInt(), got ? "받았어요" : ready ? "클릭해서 받기" : "");
				if (ready) {
					MdGui.glow(icon);
				}
				int slot = row + col++;
				if (slot >= row + 9) {
					slot = row + 9 + (col - 10);
				}
				gui.set(slot, icon, () -> {
					claim(p, period, m);
					open(p);
				});
			}
		}
		gui.set(49, MdGui.item(Material.CHEST_MINECART, "모두 받기", NamedTextColor.YELLOW), () -> {
			int n = 0;
			for (String period : PERIODS) {
				for (JsonElement e : data.getAsJsonArray(period)) {
					n += claim(p, period, e.getAsJsonObject()) ? 1 : 0;
				}
			}
			Hud.message(p, Component.text(n > 0 ? "미션 보상 " + n + "개를 받았어요" : "받을 보상이 없어요", NamedTextColor.YELLOW));
			if (n > 0) {
				p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 1.0F);
			}
			open(p);
		});
		gui.set(48, MdGui.item(Material.GOLDEN_HELMET, "림버스 패스", NamedTextColor.GOLD), () -> Pass.open(p));
		gui.set(50, MdGui.item(Material.BARRIER, "뒤로", NamedTextColor.WHITE), () -> Ego.Menu.open(p));
		gui.fill().open(p);
	}

	// --- what counts ----------------------------------------------------------------------------------

	@EventHandler
	public void onJoin(PlayerJoinEvent event) {
		Player p = event.getPlayer();
		UUID id = p.getUniqueId();
		String today = Season.day();
		if (!today.equals(Season.getString(id, "login_day"))) {
			Season.set(id, "login_day", today);
			progress(p, "login", 1);
		}
		PaidShop.dailySupply(p);
		// Items made before the server had their pictures get them.
		for (org.bukkit.inventory.ItemStack stack : p.getInventory().getContents()) {
			Gacha.Kind k = Gacha.kindOf(stack);
			if (k != null && stack.getItemMeta().getItemModel() != null && !k.modelKey().equals(stack.getItemMeta().getItemModel())) {
				stack.editMeta(meta -> meta.setItemModel(k.modelKey()));
			}
		}
		// E.G.O kept in the collection while the plugin had no data for them become owned E.G.O once it does.
		for (String c : Season.getList(id, "cosmetics")) {
			if (c.startsWith("ego:") && Ego.get(c.substring(4)) != null) {
				Ego.give(p, Ego.get(c.substring(4)));
				java.util.List<String> left = Season.getList(id, "cosmetics");
				left.remove(c);
				Season.set(id, "cosmetics", left);
			}
		}
	}

	@EventHandler
	public void onKill(EntityDeathEvent event) {
		LivingEntity dead = event.getEntity();
		Player killer = dead.getKiller();
		if (killer == null || dead instanceof Player) {
			return;
		}
		if (dead instanceof Enemy || MdBattle.foe(dead) != null) {
			progress(killer, "kill", 1);
		}
	}
}
