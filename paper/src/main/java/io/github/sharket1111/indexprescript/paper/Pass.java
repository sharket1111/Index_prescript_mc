package io.github.sharket1111.indexprescript.paper;

import java.util.List;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * The Limbus Pass of the current Season (Season 8: PUNCTUM), as in the original: 10 Pass XP a level, a free
 * track and the Limbus Pass track (1300 Paid Lunacy, or the package: 2700 / 1700 if the Pass is owned, with 10
 * levels, a Level Boost Ticket VI and a banner). Past the last level, each EX level gives 1 Nominable Egoshard
 * Crate (2 more with the Pass). Pass XP comes from missions and Mirror Dungeon rewards.
 */
final class Pass {
	private static JsonObject data = new JsonObject();
	private static JsonArray levels = new JsonArray();
	private static int xpPerLevel = 10;
	private static final int PER_PAGE = 9;

	private Pass() {
	}

	static void load() {
		data = Season.json("pass");
		if (data.has("levels")) {
			levels = data.getAsJsonArray("levels");
			xpPerLevel = data.get("xp_per_level").getAsInt();
		}
	}

	static int xpPerLevel() {
		return xpPerLevel;
	}

	private static String key(String k) {
		return "pass_" + Season.season() + "_" + k;
	}

	static int xp(UUID id) {
		return Season.getInt(id, key("xp"));
	}

	static int level(UUID id) {
		return xp(id) / xpPerLevel;
	}

	static int maxLevel() {
		return levels.size();
	}

	static boolean premium(UUID id) {
		return Season.getBool(id, key("premium"));
	}

	static void unlock(Player p) {
		Season.set(p.getUniqueId(), key("premium"), true);
	}

	static void addXp(Player p, int amount) {
		if (amount <= 0) {
			return;
		}
		UUID id = p.getUniqueId();
		int before = level(id);
		Season.set(id, key("xp"), xp(id) + amount);
		int after = level(id);
		if (after > before) {
			p.sendMessage(Component.text("림버스 패스 레벨 " + after + "! (/메뉴 → 림버스 패스에서 보상 받기)", NamedTextColor.GOLD));
			p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8F, 1.2F);
			Missions.reach(p, "pass_level", after);
		}
	}

	private static boolean claimed(UUID id, String track, int level) {
		return Season.getList(id, key(track)).contains(String.valueOf(level));
	}

	private static JsonArray rewards(int level, String track) {
		if (level > maxLevel()) {
			return data.getAsJsonArray(track.equals("free") ? "ex_free" : "ex_paid");
		}
		return levels.get(level - 1).getAsJsonObject().getAsJsonArray(track);
	}

	/** Claims one level's reward on one track if it can; true if it did. */
	private static boolean claim(Player p, int level, String track) {
		UUID id = p.getUniqueId();
		if (level > level(id) || claimed(id, track, level) || track.equals("paid") && !premium(id)) {
			return false;
		}
		Season.addToList(id, key(track), String.valueOf(level));
		Rewards.grant(p, rewards(level, track), "림버스 패스 " + (level > maxLevel() ? "EX " : "") + level + (track.equals("paid") ? " · 림버스 패스" : " · 무료"));
		return true;
	}

	private static int claimAll(Player p) {
		int n = 0;
		for (int lv = 1; lv <= level(p.getUniqueId()); lv++) {
			n += claim(p, lv, "free") ? 1 : 0;
			n += claim(p, lv, "paid") ? 1 : 0;
		}
		return n;
	}

	static void buy(Player p, boolean pack) {
		UUID id = p.getUniqueId();
		if (!pack && premium(id)) {
			Hud.message(p, Component.text("이미 림버스 패스가 있어요", NamedTextColor.GRAY));
			return;
		}
		if (pack && Season.getBool(id, key("package"))) {
			Hud.message(p, Component.text("이번 시즌 패키지는 이미 샀어요", NamedTextColor.GRAY));
			return;
		}
		int price = pack ? (premium(id) ? data.get("package_price_owned").getAsInt() : data.get("package_price").getAsInt())
				: data.get("pass_price").getAsInt();
		if (!Gacha.spendPaidLunacy(p, price)) {
			Hud.message(p, Component.text("유료 광기가 모자라요 (" + price + ", /메뉴 → 상점)", NamedTextColor.RED));
			return;
		}
		unlock(p);
		if (pack) {
			Season.set(id, key("package"), true);
			addXp(p, data.get("package_levels").getAsInt() * xpPerLevel);
			Rewards.grant(p, data.getAsJsonArray("package"), "림버스 패스 패키지");
		}
		int ready = 0;
		for (int lv = 1; lv <= level(id); lv++) {
			ready += claimed(id, "paid", lv) ? 0 : 1;
		}
		p.sendMessage(Component.text((pack ? "림버스 패스 패키지를 샀어요" : "림버스 패스로 업그레이드했어요") + " · 지금 받을 수 있는 림버스 패스 보상 " + ready + "레벨치",
				NamedTextColor.GOLD));
		p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8F, 1.0F);
	}

	/** The upgrade's confirmation: the price, what opens now and later, and where Paid Lunacy comes from. */
	static void confirmUpgrade(Player p) {
		UUID id = p.getUniqueId();
		if (premium(id)) {
			open(p);
			return;
		}
		int price = data.get("pass_price").getAsInt();
		int now = 0;
		java.util.List<String> highlights = new java.util.ArrayList<>();
		for (int lv = 1; lv <= maxLevel(); lv++) {
			JsonArray r = rewards(lv, "paid");
			if (lv <= level(id)) {
				now += r.size();
			}
			for (var e : r) {
				String rid = e.getAsJsonArray().get(0).getAsString();
				if (rid.startsWith("ego:") || rid.startsWith("announcer:") || rid.startsWith("boost") || rid.startsWith("banner:")) {
					highlights.add("레벨 " + lv + ": " + Rewards.name(rid));
				}
			}
		}
		MdGui gui = new MdGui(3, "림버스 패스 업그레이드");
		java.util.List<String> lore = new java.util.ArrayList<>();
		lore.add("유료 광기 " + price + " (가진 유료 광기 " + Gacha.paidLunacy(p) + ")");
		lore.add("지금 레벨 " + level(id) + "까지의 림버스 패스 보상 " + now + "개를 바로 받을 수 있어요");
		lore.add("EX 레벨마다 지정 에고 조각 상자 2개 더");
		lore.addAll(highlights);
		gui.set(4, MdGui.item(Material.GOLDEN_HELMET, "림버스 패스 · " + data.get("season").getAsString(), NamedTextColor.GOLD, lore.toArray(String[]::new)),
				(Runnable) null);
		boolean enough = Gacha.paidLunacy(p) >= price;
		gui.set(11, MdGui.item(enough ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE, enough ? "업그레이드 (유료 광기 " + price + ")" : "유료 광기가 모자라요",
				enough ? NamedTextColor.GREEN : NamedTextColor.GRAY, enough ? "클릭하면 바로 업그레이드돼요" : "상점 → 광기 또는 시즌 시작 광기 팩(유료 광기 1300)"), () -> {
					if (Gacha.paidLunacy(p) >= price) {
						buy(p, false);
					}
					open(p);
				});
		gui.set(13, MdGui.item(Material.EMERALD, "상점 · 광기", NamedTextColor.GREEN, "유료 광기를 사요"), () -> PaidShop.open(p, 2));
		gui.set(15, MdGui.item(Material.RED_CONCRETE, "취소", NamedTextColor.RED), () -> open(p));
		gui.fill().open(p);
	}

	static void open(Player p) {
		open(p, Math.max(0, (Math.min(level(p.getUniqueId()), maxLevel()) - 1) / PER_PAGE));
	}

	static void open(Player p, int page) {
		UUID id = p.getUniqueId();
		int pages = (maxLevel() + PER_PAGE - 1) / PER_PAGE + 1;
		page = Math.max(0, Math.min(pages - 1, page));
		int lv = level(id);
		MdGui gui = new MdGui(6, "림버스 패스 · " + data.get("season").getAsString());
		gui.set(4, MdGui.item(Material.GOLDEN_HELMET, "패스 레벨 " + lv + (lv > maxLevel() ? " (EX " + (lv - maxLevel()) + ")" : ""), NamedTextColor.GOLD,
				"XP " + xp(id) % xpPerLevel + "/" + xpPerLevel + " (총 " + xp(id) + ")", premium(id) ? "림버스 패스 보유" : "무료 패스",
				"패스 XP: 미션과 거울 던전 보상"), (Runnable) null);
		if (!premium(id)) {
			var up = MdGui.item(Material.GOLD_INGOT, "림버스 패스 업그레이드", NamedTextColor.GOLD, "유료 광기 " + data.get("pass_price").getAsInt()
					+ " (가진 유료 광기 " + Gacha.paidLunacy(p) + ")", "림버스 패스 줄의 보상 " + maxLevel() + "레벨치가 열려요",
					"이미 오른 레벨의 보상도 바로 받을 수 있어요", "클릭해서 확인");
			gui.set(6, MdGui.glow(up), () -> confirmUpgrade(p));
		}
		if (!Season.getBool(id, key("package"))) {
			gui.set(7, MdGui.item(Material.GOLD_BLOCK, "림버스 패스 패키지 구매", NamedTextColor.GOLD,
					"유료 광기 " + (premium(id) ? data.get("package_price_owned").getAsInt() : data.get("package_price").getAsInt()),
					"림버스 패스 + 패스 레벨 10 + 레벨 부스트 티켓 VI + 특별 패키지 배너"), () -> {
						buy(p, true);
						open(p);
					});
		}
		gui.set(0, MdGui.item(Material.NAME_TAG, "무료", NamedTextColor.WHITE), (Runnable) null);
		boolean ex = page == pages - 1;
		for (int i = 0; i < PER_PAGE; i++) {
			int level = ex ? maxLevel() + 1 + i : page * PER_PAGE + 1 + i;
			if (!ex && level > maxLevel()) {
				break;
			}
			boolean reached = level <= lv;
			gui.set(9 + i, MdGui.item(reached ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE,
					(level > maxLevel() ? "EX " + (level - maxLevel()) : "레벨 " + level), reached ? NamedTextColor.GREEN : NamedTextColor.GRAY), (Runnable) null);
			for (String track : List.of("free", "paid")) {
				boolean got = claimed(id, track, level);
				boolean ready = reached && !got && (track.equals("free") || premium(id));
				String state = got ? "받았어요" : ready ? "클릭해서 받기" : !reached ? "레벨 " + level + "에서 받을 수 있어요" : "림버스 패스가 필요해요";
				var icon = Rewards.icon(rewards(level, track), (track.equals("free") ? "무료 · " : "림버스 패스 · ") + (level > maxLevel() ? "EX " + (level - maxLevel()) : "레벨 " + level),
						got ? NamedTextColor.DARK_GRAY : ready ? NamedTextColor.YELLOW : NamedTextColor.GRAY, state);
				if (ready) {
					MdGui.glow(icon);
				}
				int pg = page;
				gui.set((track.equals("free") ? 18 : 27) + i, icon, () -> {
					claim(p, level, track);
					open(p, pg);
				});
			}
		}
		int pg = page;
		if (page > 0) {
			gui.set(45, MdGui.item(Material.ARROW, "이전", NamedTextColor.WHITE), () -> open(p, pg - 1));
		}
		if (page < pages - 1) {
			gui.set(53, MdGui.item(Material.ARROW, "다음", NamedTextColor.WHITE), () -> open(p, pg + 1));
		}
		gui.set(48, MdGui.item(Material.BARRIER, "뒤로", NamedTextColor.WHITE), () -> Ego.Menu.open(p));
		gui.set(49, MdGui.item(Material.CHEST_MINECART, "받을 수 있는 보상 모두 받기", NamedTextColor.YELLOW), () -> {
			int n = claimAll(p);
			Hud.message(p, Component.text(n > 0 ? "보상 " + n + "개를 받았어요" : "받을 보상이 없어요", NamedTextColor.YELLOW));
			open(p, pg);
		});
		gui.set(50, MdGui.item(Material.WRITABLE_BOOK, "미션", NamedTextColor.AQUA, "일일·주간·시즌 미션으로 패스 XP"), () -> Missions.open(p));
		gui.fill().open(p);
	}
}
