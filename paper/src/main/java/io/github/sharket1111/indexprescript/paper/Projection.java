package io.github.sharket1111.indexprescript.paper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Mirror Projection of the current Mirror Dungeon (Mirror of Sisyphe), as in the original
 * (season/projection.json): Achievements give Projection Rate; so do runs (5 a Normal floor, 10 a Hard one,
 * the INFINITY/EXTREME floors more, 15 an Adversity point), at most 1000 a week from runs. Every 100 is a
 * Reward Level (up to 100), claimed from the menu; clearing every Achievement gives the completionist banner.
 * Achievements that need what this server does not have (Identities, Speed, Shields...) are listed but
 * cannot be completed here.
 */
final class Projection {
	private static JsonObject data = new JsonObject();
	private static List<JsonObject> achievements = new ArrayList<>();
	private static final String[] CATEGORIES = {"수집", "클리어", "편성", "상점", "전투", "역경 - EXTREME"};

	private Projection() {
	}

	static void load() {
		data = Season.json("projection");
		achievements = new ArrayList<>();
		if (data.has("achievements")) {
			for (JsonElement e : data.getAsJsonArray("achievements")) {
				achievements.add(e.getAsJsonObject());
			}
		}
	}

	private static String key(String k) {
		return "proj_S8_" + k;
	}

	static int rate(UUID id) {
		return Season.getInt(id, key("rate"));
	}

	static int level(UUID id) {
		JsonObject runs = data.getAsJsonObject("runs");
		return Math.min(runs.get("levels").getAsInt(), rate(id) / runs.get("per_level").getAsInt());
	}

	private static boolean done(UUID id, JsonObject a) {
		return Season.getList(id, key("done")).contains(a.get("id").getAsString());
	}

	private static void complete(Player p, JsonObject a) {
		UUID id = p.getUniqueId();
		if (!Season.addToList(id, key("done"), a.get("id").getAsString())) {
			return;
		}
		Season.add(id, key("rate"), a.get("rate").getAsInt());
		p.sendMessage(Component.text("[거울 투영 업적] " + a.get("text").getAsString() + " · 투영률 +" + a.get("rate").getAsInt(), NamedTextColor.AQUA));
		p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6F, 1.4F);
		boolean all = true;
		for (JsonObject x : achievements) {
			if (!x.get("when").isJsonNull() && !done(id, x)) {
				all = false;
				break;
			}
		}
		if (all && !Season.getBool(id, key("complete"))) {
			Season.set(id, key("complete"), true);
			JsonArray c = data.getAsJsonArray("completionist");
			Rewards.grant(p, c.get(0).getAsString(), c.get(1).getAsInt());
			p.sendMessage(Component.text("모든 업적 달성! " + Rewards.name(c.get(0).getAsString()), NamedTextColor.GOLD));
		}
	}

	private static int counter(UUID id, JsonObject a) {
		return Season.getInt(id, key("n_" + a.get("id").getAsString()));
	}

	// --- what completes them -----------------------------------------------------------------------------

	/** A gift seen for the first time goes into the compendium. */
	static void compendium(Player p, MdData.Gift gift) {
		if (Season.addToList(p.getUniqueId(), key("compendium"), gift.id())) {
			checkAlways(p);
		}
	}

	static void themeSeen(Player p, MdData.Theme theme, boolean hard, boolean extreme) {
		String list = extreme || theme.category().contains("EXTREME") ? "themes_extreme" : hard ? "themes_hard" : "themes_normal";
		if (Season.addToList(p.getUniqueId(), key(list), theme.id())) {
			checkAlways(p);
		}
	}

	private static void checkAlways(Player p) {
		UUID id = p.getUniqueId();
		List<String> seen = Season.getList(id, key("compendium"));
		for (JsonObject a : achievements) {
			if (a.get("when").isJsonNull() || done(id, a)) {
				continue;
			}
			JsonObject w = a.getAsJsonObject("when");
			if (!w.get("on").getAsString().equals("always")) {
				continue;
			}
			boolean ok = false;
			if (w.has("themes")) {
				ok = Season.getList(id, key("themes_" + w.get("themes").getAsString())).size() >= w.get("n").getAsInt();
			} else if (w.has("compendium_kw")) {
				String kw = w.get("compendium_kw").getAsString();
				ok = seen.stream().map(MdData::gift).filter(g -> g != null && g.keyword().equals(kw)).count() >= w.get("n").getAsInt();
			} else if (w.has("compendium")) {
				ok = seen.size() >= w.get("compendium").getAsInt();
			}
			if (ok) {
				complete(p, a);
			}
		}
	}

	/** One big skill's damage, as shown (TOTAL). */
	static void skillDamage(Player p, long shown) {
		for (JsonObject a : achievements) {
			if (!a.get("when").isJsonNull() && a.getAsJsonObject("when").get("on").getAsString().equals("skill_damage")
					&& shown >= a.getAsJsonObject("when").get("n").getAsInt() && MdRun.of(p) != null) {
				complete(p, a);
			}
		}
	}

	/** Every second: the keyword potencies on the dungeon's enemies and members. */
	static void tick() {
		long now = CaduceusListener.now();
		for (MdRun run : MdRun.all()) {
			if (run.battle == null) {
				continue;
			}
			int[] best = new int[7];
			for (LivingEntity m : run.battle.mobs) {
				CombatState s = CombatState.of(m);
				best[0] = Math.max(best[0], s.burnPotency(now));
				best[1] = Math.max(best[1], s.bleedPotency(now));
				best[2] = Math.max(best[2], s.tremorPotency(now));
				best[3] = Math.max(best[3], s.rupturePotency(now));
				best[4] = Math.max(best[4], s.sinkingPotency(now));
			}
			for (Player p : run.players()) {
				CombatState s = CombatState.of(p);
				best[5] = Math.max(best[5], s.poisePotency(now));
				best[6] = Math.max(best[6], Math.max(s.chargePotency(now), s.chargeCount(now)));
			}
			String[] kws = {"burn", "bleed", "tremor", "rupture", "sinking", "poise", "charge"};
			for (JsonObject a : achievements) {
				if (a.get("when").isJsonNull() || !a.getAsJsonObject("when").get("on").getAsString().equals("potency")) {
					continue;
				}
				JsonObject w = a.getAsJsonObject("when");
				for (int i = 0; i < kws.length; i++) {
					if (kws[i].equals(w.get("kw").getAsString()) && best[i] >= w.get("n").getAsInt()) {
						for (Player p : run.players()) {
							complete(p, a);
						}
					}
				}
			}
		}
	}

	/** A run ends: its Projection Rate (weekly cap) and the Achievements it met. */
	static void runEnded(Player p, MdRun run, boolean cleared) {
		UUID id = p.getUniqueId();
		JsonObject r = data.getAsJsonObject("runs");
		int gained = 0;
		int normalFloors = Math.min(MdRun.FLOORS, run.floorsCleared - Math.min(run.floorsCleared, run.hardFloorsCleared));
		int hardFloors = Math.min(MdRun.FLOORS, run.hardFloorsCleared);
		gained += normalFloors * r.get("normal_floor").getAsInt() + hardFloors * r.get("hard_floor").getAsInt();
		JsonObject deep = r.getAsJsonObject("deep");
		for (int f = MdRun.FLOORS + 1; f <= run.floorsCleared; f++) {
			gained += deep.has(String.valueOf(f)) ? deep.get(String.valueOf(f)).getAsInt() : 0;
		}
		gained += run.adversityScore() * r.get("adversity").getAsInt();
		int room = Math.max(0, r.get("weekly_cap").getAsInt() - Season.periodic(id, key("week"), Season.week()));
		gained = Math.min(gained, room);
		if (gained > 0) {
			Season.addPeriodic(id, key("week"), Season.week(), gained);
			Season.add(id, key("rate"), gained);
			p.sendMessage(Component.text("[거울 투영] 투영률 +" + gained + " (이번 주 " + Season.periodic(id, key("week"), Season.week()) + "/"
					+ r.get("weekly_cap").getAsInt() + ")", NamedTextColor.AQUA));
		}
		for (JsonObject a : achievements) {
			if (a.get("when").isJsonNull() || done(id, a)) {
				continue;
			}
			JsonObject w = a.getAsJsonObject("when");
			if (!w.get("on").getAsString().equals("clear") || !meets(run, w, cleared)) {
				continue;
			}
			if (w.has("count")) {
				int n = Season.add(id, key("n_" + a.get("id").getAsString()), 1);
				if (n < w.get("count").getAsInt()) {
					continue;
				}
			}
			complete(p, a);
		}
		checkAlways(p);
	}

	private static boolean meets(MdRun run, JsonObject w, boolean cleared) {
		if (!cleared && run.floorsCleared < run.goal || run.floorsCleared < w.get("floor").getAsInt()) {
			return false;
		}
		Map<String, Integer> gifts = run.gifts;
		List<MdData.Gift> held = gifts.keySet().stream().map(MdData::gift).filter(g -> g != null).toList();
		if (w.has("gifts") && gifts.size() < w.get("gifts").getAsInt()) {
			return false;
		}
		if (w.has("kw_gifts") && held.stream().filter(g -> g.keyword().equals(w.get("kw_gifts").getAsString())).count() < w.get("n").getAsInt()) {
			return false;
		}
		if (w.has("tier4") && held.stream().filter(g -> g.tierNumber() >= 4).count() < w.get("tier4").getAsInt()) {
			return false;
		}
		if (w.has("fusion4") && held.stream().filter(g -> g.fusionOnly() && g.tierNumber() == 4).count() < w.get("fusion4").getAsInt()) {
			return false;
		}
		if (w.has("theme_fusion") && held.stream().filter(g -> g.themeOnly() && run.fused.contains(g.id())).count() < w.get("theme_fusion").getAsInt()) {
			return false;
		}
		if (w.has("all_hard") && w.get("all_hard").getAsBoolean() && run.hardFloorsCleared < run.floorsCleared) {
			return false;
		}
		if (w.has("refresh") && run.shopRefreshes < w.get("refresh").getAsInt()) {
			return false;
		}
		if (w.has("bought") && run.giftsBought < w.get("bought").getAsInt()) {
			return false;
		}
		if (w.has("enhanced") && gifts.values().stream().filter(v -> v != null && v > 0).count() < w.get("enhanced").getAsInt()) {
			return false;
		}
		if (w.has("cost") && run.cost < w.get("cost").getAsInt()) {
			return false;
		}
		if (w.has("shop_spend") && run.maxShopSpend < w.get("shop_spend").getAsInt()) {
			return false;
		}
		if (w.has("sold") && run.costSold < w.get("sold").getAsInt()) {
			return false;
		}
		if (w.has("no_refresh") && run.shopRefreshes > 0) {
			return false;
		}
		if (w.has("no_fusion") && gifts.keySet().stream().anyMatch(run.fused::contains)) {
			return false;
		}
		if (w.has("adversity") && run.adv(w.get("adversity").getAsString()) <= 0) {
			return false;
		}
		if (w.has("score") && run.adversityScore() < w.get("score").getAsInt()) {
			return false;
		}
		return !(w.has("without_gift") && run.everGifts.contains(w.get("without_gift").getAsString()));
	}

	// --- the menu -------------------------------------------------------------------------------------

	private static boolean claimed(UUID id, int level) {
		return Season.getList(id, key("claimed")).contains(String.valueOf(level));
	}

	private static JsonArray reward(int level) {
		return data.getAsJsonArray("rewards").get(level - 1).getAsJsonObject().getAsJsonArray("items");
	}

	static void open(Player p) {
		UUID id = p.getUniqueId();
		JsonObject r = data.getAsJsonObject("runs");
		MdGui gui = new MdGui(3, "거울 투영 · " + data.get("dungeon").getAsString());
		gui.set(4, MdGui.item(Material.ENDER_EYE, "투영률 " + rate(id) + " · 보상 레벨 " + level(id) + "/" + r.get("levels").getAsInt(), NamedTextColor.AQUA,
				"투영률 100마다 보상 레벨 1", "탐험으로 얻는 투영률: 이번 주 " + Season.periodic(id, key("week"), Season.week()) + "/" + r.get("weekly_cap").getAsInt(),
				"NORMAL 층 5 · HARD 층 10 · 6층부터 더 많이 · 역경 점수 1당 15", "업적으로 얻는 투영률은 주간 제한이 없어요"), (Runnable) null);
		for (int i = 0; i < CATEGORIES.length; i++) {
			String cat = CATEGORIES[i];
			long total = achievements.stream().filter(a -> a.get("category").getAsString().equals(cat)).count();
			long got = achievements.stream().filter(a -> a.get("category").getAsString().equals(cat) && done(id, a)).count();
			gui.set(10 + i, MdGui.item(Material.BOOK, "업적 · " + cat, NamedTextColor.WHITE, got + "/" + total + " 달성"), () -> achievements(p, cat, 0));
		}
		int ready = 0;
		for (int lv = 1; lv <= level(id); lv++) {
			ready += claimed(id, lv) ? 0 : 1;
		}
		gui.set(16, MdGui.item(Material.CHEST, "보상 (" + ready + "개 받을 수 있어요)", NamedTextColor.GOLD), () -> rewards(p, 0));
		gui.set(22, MdGui.item(Material.ARROW, "뒤로", NamedTextColor.WHITE), () -> Ego.Menu.open(p));
		gui.fill().open(p);
	}

	private static void achievements(Player p, String cat, int page) {
		UUID id = p.getUniqueId();
		List<JsonObject> list = achievements.stream().filter(a -> a.get("category").getAsString().equals(cat)).toList();
		MdGui gui = new MdGui(6, "업적 · " + cat);
		int per = 45;
		for (int i = page * per; i < list.size() && i < (page + 1) * per; i++) {
			JsonObject a = list.get(i);
			boolean d = done(id, a);
			boolean possible = !a.get("when").isJsonNull();
			List<String> lore = new ArrayList<>();
			lore.add("투영률 +" + a.get("rate").getAsInt());
			if (possible && a.getAsJsonObject("when").has("count")) {
				lore.add("진행 " + Math.min(counter(id, a), a.getAsJsonObject("when").get("count").getAsInt()) + "/" + a.getAsJsonObject("when").get("count").getAsInt());
			}
			if (!possible) {
				lore.add("원작 전용 (이 서버에 없는 인격·속도·보호막 등이 필요해 달성할 수 없어요)");
			}
			lore.add(d ? "달성" : "미달성");
			gui.set(i - page * per, MdGui.item(d ? Material.LIME_DYE : possible ? Material.PAPER : Material.GRAY_DYE, a.get("text").getAsString(),
					d ? NamedTextColor.GREEN : possible ? NamedTextColor.WHITE : NamedTextColor.DARK_GRAY, lore.toArray(String[]::new)), (Runnable) null);
		}
		if (page > 0) {
			gui.set(45, MdGui.item(Material.ARROW, "이전", NamedTextColor.WHITE), () -> achievements(p, cat, page - 1));
		}
		if ((page + 1) * per < list.size()) {
			gui.set(53, MdGui.item(Material.ARROW, "다음", NamedTextColor.WHITE), () -> achievements(p, cat, page + 1));
		}
		gui.set(49, MdGui.item(Material.BARRIER, "뒤로", NamedTextColor.WHITE), () -> open(p));
		gui.fill().open(p);
	}

	private static void rewards(Player p, int page) {
		UUID id = p.getUniqueId();
		MdGui gui = new MdGui(6, "거울 투영 보상");
		int per = 45;
		int max = data.getAsJsonArray("rewards").size();
		for (int lv = page * per + 1; lv <= Math.min(max, (page + 1) * per); lv++) {
			boolean got = claimed(id, lv);
			boolean ready = !got && lv <= level(id);
			int level = lv;
			var icon = Rewards.icon(reward(lv), "보상 레벨 " + lv, got ? NamedTextColor.DARK_GRAY : ready ? NamedTextColor.YELLOW : NamedTextColor.GRAY,
					got ? "받았어요" : ready ? "클릭해서 받기" : "투영률 " + lv * 100 + " 필요");
			if (ready) {
				MdGui.glow(icon);
			}
			gui.set(lv - 1 - page * per, icon, () -> {
				if (!claimed(id, level) && level <= level(id)) {
					Season.addToList(id, key("claimed"), String.valueOf(level));
					Rewards.grant(p, reward(level), "거울 투영 보상 " + level);
				}
				rewards(p, page);
			});
		}
		if (page > 0) {
			gui.set(45, MdGui.item(Material.ARROW, "이전", NamedTextColor.WHITE), () -> rewards(p, page - 1));
		}
		if ((page + 1) * per < max) {
			gui.set(53, MdGui.item(Material.ARROW, "다음", NamedTextColor.WHITE), () -> rewards(p, page + 1));
		}
		gui.set(48, MdGui.item(Material.CHEST_MINECART, "모두 받기", NamedTextColor.YELLOW), () -> {
			for (int lv = 1; lv <= level(id); lv++) {
				if (!claimed(id, lv)) {
					Season.addToList(id, key("claimed"), String.valueOf(lv));
					Rewards.grant(p, reward(lv), "거울 투영 보상 " + lv);
				}
			}
			rewards(p, page);
		});
		gui.set(49, MdGui.item(Material.BARRIER, "뒤로", NamedTextColor.WHITE), () -> open(p));
		gui.fill().open(p);
	}
}
