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
 * Mirror Dungeon rewards, as in the original (season/mirror.json): 3 Weekly Bonus charges a week. A run's
 * reward is kept until it is claimed (from /md or /메뉴) with Enkephalin Modules: 5 for Normal, 6 for each
 * charge on Hard. Normal: one charge gives 250 Lunacy and 45 Pass XP; Hard can spend 1-3 at once (750
 * Lunacy and 225-250 Pass XP over the week); without a charge only 30 Pass XP. Every claim also gives
 * Manager EXP. A run left before floor 5 gives part of it by the floors cleared. Nothing else comes from
 * the dungeon.
 */
final class MdClaim {
	private static JsonObject data = new JsonObject();

	private MdClaim() {
	}

	static void load() {
		data = Season.json("mirror");
	}

	static int charges() {
		return data.has("charges") ? data.get("charges").getAsInt() : 3;
	}

	static int used(UUID id) {
		return Season.periodic(id, "md_bonus", Season.week());
	}

	static int left(UUID id) {
		return Math.max(0, charges() - used(id));
	}

	/** A finished run's reward waits for its claim (one at a time; a newer run replaces an unclaimed one). */
	static void pending(Player p, int floors, boolean hard) {
		if (floors <= 0) {
			return;
		}
		UUID id = p.getUniqueId();
		Season.set(id, "md_pending_floors", Math.min(floors, MdRun.FLOORS));
		Season.set(id, "md_pending_hard", hard);
		p.sendMessage(Component.text("거울 던전 보상이 준비됐어요. /md 또는 /메뉴 → 거울 던전 보상에서 엔케팔린 모듈로 받아요 (주간 보너스 " + left(id) + "/"
				+ charges() + ")", NamedTextColor.GOLD));
	}

	static boolean has(UUID id) {
		return Season.getInt(id, "md_pending_floors") > 0;
	}

	private static int[] partial(int floors) {
		JsonArray a = data.getAsJsonObject("partial").getAsJsonArray(String.valueOf(Math.max(1, Math.min(5, floors))));
		return new int[] {a.get(0).getAsInt(), a.get(1).getAsInt()};
	}

	/** What a claim gives: {lunacy, pass XP, manager XP}. */
	private static int[] reward(UUID id, boolean hard, int use) {
		if (use <= 0) {
			JsonObject n = data.getAsJsonObject("none");
			return new int[] {0, n.get("pass_xp").getAsInt(), n.get("manager_xp").getAsInt()};
		}
		JsonObject r = hard ? data.getAsJsonObject("hard").getAsJsonArray(String.valueOf(use)).get(Math.min(used(id),
				data.getAsJsonObject("hard").getAsJsonArray(String.valueOf(use)).size() - 1)).getAsJsonObject() : data.getAsJsonObject("normal");
		return new int[] {r.get("lunacy").getAsInt(), r.get("pass_xp").getAsInt(), r.get("manager_xp").getAsInt()};
	}

	private static int modules(boolean hard, int use) {
		return hard && use > 0 ? data.get("modules_hard").getAsInt() * use : data.get("modules_normal").getAsInt();
	}

	private static void claim(Player p, int use) {
		UUID id = p.getUniqueId();
		if (!has(id)) {
			return;
		}
		boolean hard = Season.getBool(id, "md_pending_hard");
		int floors = Season.getInt(id, "md_pending_floors");
		use = Math.min(use, left(id));
		if (!hard) {
			use = Math.min(use, 1);
		}
		int cost = modules(hard, use);
		if (!Gacha.take(p, Gacha.Kind.ENK_MODULE, cost)) {
			Hud.message(p, Component.text("엔케팔린 모듈이 모자라요 (" + cost + "개, /메뉴 → 엔케팔린)", NamedTextColor.RED));
			return;
		}
		int[] r = reward(id, hard, use);
		int[] pct = partial(floors);
		int xp = (int) Math.round(r[1] * pct[0] / 100.0);
		int lunacy = (int) Math.round(r[0] * pct[1] / 100.0);
		if (use > 0) {
			Season.addPeriodic(id, "md_bonus", Season.week(), use);
		}
		Season.set(id, "md_pending_floors", 0);
		Gacha.addLunacy(p, lunacy);
		Pass.addXp(p, xp);
		Manager.addExp(p, r[2]);
		p.sendMessage(Component.text("[거울 던전 보상] " + (lunacy > 0 ? "광기 +" + lunacy + " · " : "") + "패스 XP +" + xp + " · 관리자 EXP +" + r[2]
				+ (use > 0 ? " (주간 보너스 " + use + "회 사용, 남은 " + left(id) + "/" + charges() + ")" : ""), NamedTextColor.LIGHT_PURPLE));
		p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8F, 1.3F);
	}

	static void open(Player p) {
		UUID id = p.getUniqueId();
		MdGui gui = new MdGui(3, "거울 던전 보상");
		gui.set(4, MdGui.item(Material.CLOCK, "주간 보너스 " + left(id) + "/" + charges(), NamedTextColor.GOLD, "수요일 06:00에 다시 채워져요 ("
				+ Season.duration(Season.untilNextWeek()) + " 뒤)", "엔케팔린 모듈 " + Gacha.count(p, Gacha.Kind.ENK_MODULE) + "개"), (Runnable) null);
		if (!has(id)) {
			gui.set(13, MdGui.item(Material.BARRIER, "받을 보상이 없어요", NamedTextColor.GRAY, "거울 던전을 끝내면 여기서 받아요"), (Runnable) null);
		} else {
			boolean hard = Season.getBool(id, "md_pending_hard");
			int floors = Season.getInt(id, "md_pending_floors");
			int[] pct = partial(floors);
			int max = hard ? Math.min(3, left(id)) : Math.min(1, left(id));
			for (int use = 0; use <= max; use++) {
				int[] r = reward(id, hard, use);
				int u = use;
				gui.set(10 + use * 2, MdGui.item(use == 0 ? Material.PAPER : Material.GOLD_INGOT,
						use == 0 ? "보너스 없이 받기" : "주간 보너스 " + use + "회 사용", use == 0 ? NamedTextColor.WHITE : NamedTextColor.GOLD,
						(r[0] > 0 ? "광기 " + Math.round(r[0] * pct[1] / 100.0) + " · " : "") + "패스 XP " + Math.round(r[1] * pct[0] / 100.0) + " · 관리자 EXP " + r[2],
						"엔케팔린 모듈 " + modules(hard, use) + "개", (hard ? "HARD" : "NORMAL") + " · " + floors + "층"
								+ (floors < MdRun.FLOORS ? " (중간 이탈: XP " + pct[0] + "% · 광기 " + pct[1] + "%)" : "")), () -> {
									claim(p, u);
									open(p);
								});
			}
		}
		gui.set(22, MdGui.item(Material.ARROW, "뒤로", NamedTextColor.WHITE), () -> Ego.Menu.open(p));
		gui.fill().open(p);
	}
}
