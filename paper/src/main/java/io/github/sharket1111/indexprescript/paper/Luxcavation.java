package io.github.sharket1111.indexprescript.paper;

import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Luxcavation, with the original's stages and rewards: EXP Luxcavation (Canto I-X, Identity Training
 * Tickets) and Thread Luxcavation (Lv 20-60, Thread; the first three clears a day give double). There is no
 * battle here: "클리어" takes the stage's modules for its rewards, "스킵" twice the modules for the skip
 * rewards (Thread: the original's skip bonus; EXP: half as many tickets again).
 */
final class Luxcavation {
	private static JsonArray exp = new JsonArray();
	private static JsonArray thread = new JsonArray();
	private static int dailyBonus = 3;
	private static final Gacha.Kind[] TICKETS = {Gacha.Kind.TRAIN1, Gacha.Kind.TRAIN2, Gacha.Kind.EXP, Gacha.Kind.TRAIN4};
	private static final String[] TICKET_KO = {"I", "II", "III", "IV"};

	private Luxcavation() {
	}

	static void load() {
		JsonObject o = Season.json("luxcavation");
		if (o.has("exp")) {
			exp = o.getAsJsonArray("exp");
			thread = o.getAsJsonArray("thread");
			dailyBonus = o.get("thread_daily_bonus").getAsInt();
		}
	}

	static void open(Player p) {
		UUID id = p.getUniqueId();
		MdGui gui = new MdGui(5, "루사이크");
		int modules = Gacha.count(p, Gacha.Kind.ENK_MODULE);
		gui.set(4, MdGui.item(Material.HEART_OF_THE_SEA, "엔케팔린 모듈 " + modules + "개", NamedTextColor.AQUA, "좌클릭: 클리어 · 우클릭: 스킵(모듈 2배, 보상 더)",
				"모듈은 관리자 메뉴에서 엔케팔린으로 조립해요"), () -> Manager.open(p));
		for (int i = 0; i < exp.size(); i++) {
			JsonObject st = exp.get(i).getAsJsonObject();
			int stage = i;
			StringBuilder rewards = new StringBuilder();
			JsonArray t = st.getAsJsonArray("tickets");
			for (int k = 0; k < 4; k++) {
				if (t.get(k).getAsInt() > 0) {
					rewards.append(rewards.isEmpty() ? "" : ", ").append(TICKET_KO[k]).append(" ×").append(t.get(k).getAsInt());
				}
			}
			int m = st.get("modules").getAsInt();
			gui.set(9 + i, MdGui.item(Material.EXPERIENCE_BOTTLE, "경험치 루사이크 · 칸토 " + st.get("canto").getAsString(), NamedTextColor.AQUA,
					"인격 경험치 티켓 " + rewards, "클리어: 모듈 " + m + " · 스킵: 모듈 " + m * 2 + " (티켓 1.5배)"), click -> {
						runExp(p, stage, click.isRightClick());
						open(p);
					});
		}
		int bonusLeft = Math.max(0, dailyBonus - Season.periodic(id, "lux_thread_bonus", Season.day()));
		for (int i = 0; i < thread.size(); i++) {
			JsonObject st = thread.get(i).getAsJsonObject();
			int stage = i;
			int m = st.get("modules").getAsInt();
			int base = st.get("thread").getAsInt();
			int skip = st.get("skip_bonus").getAsInt();
			gui.set(28 + i, MdGui.item(Material.STRING, "끈 루사이크 · Lv " + st.get("level").getAsInt(), NamedTextColor.GOLD,
					"클리어: 모듈 " + m + " → 끈 " + base + " (보너스 " + base * 2 + ")", "스킵: 모듈 " + m * 2 + " → 끈 " + (base + skip) + " (보너스 " + (base + skip) * 2 + ")",
					"오늘 남은 2배 보너스 " + bonusLeft + "/" + dailyBonus), click -> {
						runThread(p, stage, click.isRightClick());
						open(p);
					});
		}
		gui.set(40, MdGui.item(Material.ARROW, "뒤로", NamedTextColor.WHITE), () -> Manager.open(p));
		gui.fill().open(p);
	}

	private static boolean pay(Player p, int modules) {
		if (!Gacha.take(p, Gacha.Kind.ENK_MODULE, modules)) {
			Hud.message(p, Component.text("엔케팔린 모듈이 모자라요 (" + modules + "개)", NamedTextColor.GRAY));
			return false;
		}
		return true;
	}

	private static void cleared(Player p, String track) {
		Missions.progress(p, track, 1);
		Missions.progress(p, "stage", 1);
		p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6F, 1.6F);
	}

	private static void runExp(Player p, int stage, boolean skip) {
		JsonObject st = exp.get(stage).getAsJsonObject();
		int m = st.get("modules").getAsInt() * (skip ? 2 : 1);
		if (!pay(p, m)) {
			return;
		}
		JsonArray t = st.getAsJsonArray("tickets");
		StringBuilder got = new StringBuilder();
		for (int k = 0; k < 4; k++) {
			int n = t.get(k).getAsInt();
			if (skip) {
				n = (int) Math.ceil(n * 1.5);
			}
			if (n > 0) {
				Gacha.give(p, TICKETS[k], n);
				got.append(got.isEmpty() ? "" : ", ").append(TICKET_KO[k]).append(" ×").append(n);
			}
		}
		Hud.message(p, Component.text("경험치 루사이크 " + (skip ? "스킵" : "클리어") + " · 경험치 티켓 " + got, NamedTextColor.AQUA));
		cleared(p, "lux_exp");
	}

	private static void runThread(Player p, int stage, boolean skip) {
		JsonObject st = thread.get(stage).getAsJsonObject();
		int m = st.get("modules").getAsInt() * (skip ? 2 : 1);
		if (!pay(p, m)) {
			return;
		}
		int n = st.get("thread").getAsInt() + (skip ? st.get("skip_bonus").getAsInt() : 0);
		UUID id = p.getUniqueId();
		boolean bonus = Season.periodic(id, "lux_thread_bonus", Season.day()) < dailyBonus;
		if (bonus) {
			Season.addPeriodic(id, "lux_thread_bonus", Season.day(), 1);
			n *= 2;
		}
		Gacha.give(p, Gacha.Kind.THREAD, n);
		Hud.message(p, Component.text("끈 루사이크 " + (skip ? "스킵" : "클리어") + " · 끈 +" + n + (bonus ? " (2배 보너스)" : ""), NamedTextColor.GOLD));
		cleared(p, "lux_thread");
	}
}
