package io.github.sharket1111.indexprescript.paper;

import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * The Manager, as in the original: a level from Manager EXP (the original's table of 300 levels) that sets
 * the Enkephalin cap; Enkephalin comes back 1 every 6 minutes up to the cap, a box gives 60 (past the cap),
 * a level up fills it to the new cap, and Lunacy fills it (26 x the refills done today, 10 a day). 20
 * Enkephalin assemble an Enkephalin Module (an item), which Luxcavation and Mirror Dungeon rewards take.
 */
final class Manager implements Listener {
	private static int[] need = new int[0];
	private static int[] cap = new int[0];
	private static long regenMs = 6 * 60_000L;
	private static int moduleCost = 20;
	private static int box = 60;
	private static int refreshLunacy = 26;
	private static int refreshes = 10;

	static void load() {
		JsonObject o = Season.json("manager");
		if (!o.has("levels")) {
			return;
		}
		JsonArray levels = o.getAsJsonArray("levels");
		need = new int[levels.size() + 1];
		cap = new int[levels.size() + 1];
		for (int i = 0; i < levels.size(); i++) {
			JsonArray row = levels.get(i).getAsJsonArray();
			need[row.get(0).getAsInt()] = row.get(1).getAsInt();
			cap[row.get(0).getAsInt()] = row.get(2).getAsInt();
		}
		regenMs = o.get("regen_minutes").getAsInt() * 60_000L;
		moduleCost = o.get("module_cost").getAsInt();
		box = o.get("box").getAsInt();
		refreshLunacy = o.get("refresh_lunacy").getAsInt();
		refreshes = o.get("refreshes").getAsInt();
	}

	static int maxLevel() {
		return need.length - 1;
	}

	static int level(UUID id) {
		return Math.max(1, Season.getInt(id, "mgr_level"));
	}

	static int exp(UUID id) {
		return Season.getInt(id, "mgr_exp");
	}

	static int cap(UUID id) {
		int lv = Math.min(maxLevel(), level(id));
		return lv < cap.length && lv > 0 ? cap[lv] : 60;
	}

	static int toNext(UUID id) {
		int lv = level(id);
		return lv < need.length ? need[lv] : 0;
	}

	/** Manager EXP; each level up fills Enkephalin to the new cap. */
	static void addExp(Player p, int amount) {
		UUID id = p.getUniqueId();
		int lv = level(id);
		int xp = exp(id) + amount;
		int gained = 0;
		while (lv < maxLevel() && need[lv] > 0 && xp >= need[lv]) {
			xp -= need[lv];
			lv++;
			gained++;
		}
		Season.set(id, "mgr_level", lv);
		Season.set(id, "mgr_exp", lv >= maxLevel() ? 0 : xp);
		if (gained > 0) {
			setEnkephalin(id, Math.max(enkephalin(id), cap(id)));
			p.sendMessage(Component.text("관리자 레벨 " + lv + "! 엔케팔린이 가득 찼어요 (최대 " + cap(id) + ")", NamedTextColor.GOLD));
			p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7F, 1.3F);
		}
	}

	// --- Enkephalin ------------------------------------------------------------------------------------

	/** Enkephalin now, with what has come back since last asked. */
	static int enkephalin(UUID id) {
		long now = System.currentTimeMillis();
		long last = Season.getLong(id, "enk_t");
		int have = Season.getString(id, "enk_t").isEmpty() ? cap(id) : Season.getInt(id, "enk");
		if (last == 0) {
			Season.set(id, "enk", have);
			Season.set(id, "enk_t", now);
			return have;
		}
		if (have >= cap(id)) {
			Season.set(id, "enk_t", now);
			return have;
		}
		long ticks = (now - last) / regenMs;
		if (ticks > 0) {
			have = (int) Math.min(cap(id), have + ticks);
			Season.set(id, "enk", have);
			Season.set(id, "enk_t", have >= cap(id) ? now : last + ticks * regenMs);
		}
		return have;
	}

	static void setEnkephalin(UUID id, int value) {
		enkephalin(id);
		Season.set(id, "enk", Math.max(0, value));
	}

	/** Seconds until the next Enkephalin (0 at the cap). */
	static long nextIn(UUID id) {
		if (enkephalin(id) >= cap(id)) {
			return 0;
		}
		return Math.max(0, (Season.getLong(id, "enk_t") + regenMs - System.currentTimeMillis()) / 1000);
	}

	/** Assembles up to {@code n} modules; returns how many. */
	static int assemble(Player p, int n) {
		UUID id = p.getUniqueId();
		int made = Math.min(n, enkephalin(id) / moduleCost);
		if (made <= 0) {
			return 0;
		}
		setEnkephalin(id, enkephalin(id) - made * moduleCost);
		Gacha.give(p, Gacha.Kind.ENK_MODULE, made);
		Missions.progress(p, "enk_module", made);
		return made;
	}

	static void useBox(Player p) {
		if (!Gacha.take(p, Gacha.Kind.ENK_BOX, 1)) {
			return;
		}
		setEnkephalin(p.getUniqueId(), enkephalin(p.getUniqueId()) + box);
		Hud.message(p, Component.text("엔케팔린 +" + box + " (" + enkephalin(p.getUniqueId()) + "/" + cap(p.getUniqueId()) + ")", NamedTextColor.AQUA));
	}

	private static void refresh(Player p) {
		UUID id = p.getUniqueId();
		int done = Season.periodic(id, "enk_refresh", Season.day());
		if (done >= refreshes) {
			Hud.message(p, Component.text("오늘은 더 충전할 수 없어요 (" + refreshes + "/" + refreshes + ")", NamedTextColor.GRAY));
			return;
		}
		int price = refreshLunacy * (done + 1);
		if (!Gacha.spendLunacy(p, price)) {
			Hud.message(p, Component.text("광기가 모자라요 (" + price + ")", NamedTextColor.GRAY));
			return;
		}
		Season.addPeriodic(id, "enk_refresh", Season.day(), 1);
		setEnkephalin(id, enkephalin(id) + cap(id));
		Hud.message(p, Component.text("광기 " + price + "로 엔케팔린 +" + cap(id), NamedTextColor.AQUA));
	}

	// --- the menu ----------------------------------------------------------------------------------------

	static void open(Player p) {
		UUID id = p.getUniqueId();
		MdGui gui = new MdGui(3, "관리자 · 엔케팔린");
		int lv = level(id);
		gui.set(4, MdGui.item(Material.PLAYER_HEAD, "관리자 레벨 " + lv, NamedTextColor.GOLD,
				lv >= maxLevel() ? "최대 레벨" : "EXP " + exp(id) + "/" + toNext(id), "엔케팔린 최대 " + cap(id),
				"관리자 EXP는 거울 던전 보상으로 얻어요"), (Runnable) null);
		int enk = enkephalin(id);
		long next = nextIn(id);
		gui.set(10, MdGui.item(Material.GLOW_INK_SAC, "엔케팔린 " + enk + "/" + cap(id), NamedTextColor.AQUA,
				next > 0 ? "다음 회복까지 " + Season.duration(next) : "가득 찼어요", "6분마다 1씩 회복해요"), (Runnable) null);
		gui.set(11, MdGui.item(Material.HEART_OF_THE_SEA, "모듈 1개 조립", NamedTextColor.AQUA, "엔케팔린 " + moduleCost + " → 엔케팔린 모듈 1개",
				"가진 모듈 " + Gacha.count(p, Gacha.Kind.ENK_MODULE) + "개"), () -> {
					Hud.message(p, Component.text(assemble(p, 1) > 0 ? "엔케팔린 모듈을 조립했어요" : "엔케팔린이 모자라요", NamedTextColor.AQUA));
					open(p);
				});
		gui.set(12, MdGui.item(Material.HEART_OF_THE_SEA, "모듈 최대 조립", NamedTextColor.AQUA, "엔케팔린으로 만들 수 있는 만큼"), () -> {
			int made = assemble(p, 999);
			Hud.message(p, Component.text(made > 0 ? "엔케팔린 모듈 " + made + "개를 조립했어요" : "엔케팔린이 모자라요", NamedTextColor.AQUA));
			open(p);
		});
		int done = Season.periodic(id, "enk_refresh", Season.day());
		gui.set(14, MdGui.item(Material.REDSTONE, "광기로 충전 (" + done + "/" + refreshes + ")", NamedTextColor.RED,
				"광기 " + refreshLunacy * (done + 1) + " → 엔케팔린 +" + cap(id), "하루 " + refreshes + "회, 회차마다 " + refreshLunacy + "씩 비싸져요"), () -> {
					refresh(p);
					open(p);
				});
		gui.set(15, MdGui.item(Material.SHULKER_SHELL, "엔케팔린 상자 쓰기", NamedTextColor.AQUA, "엔케팔린 +" + box,
				"가진 상자 " + Gacha.count(p, Gacha.Kind.ENK_BOX) + "개"), () -> {
					useBox(p);
					open(p);
				});
		gui.set(16, MdGui.item(Material.DIAMOND_PICKAXE, "루사이크", NamedTextColor.GOLD, "엔케팔린 모듈로 경험치 티켓·끈 채굴"), () -> Luxcavation.open(p));
		gui.set(22, MdGui.item(Material.ARROW, "뒤로", NamedTextColor.WHITE), () -> Ego.Menu.open(p));
		gui.fill().open(p);
	}

	/** Right-clicking a box or a crate uses it. */
	@EventHandler
	public void onUse(PlayerInteractEvent event) {
		if (event.getHand() != EquipmentSlot.HAND || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
			return;
		}
		Gacha.Kind k = Gacha.kindOf(event.getItem());
		if (k == null) {
			return;
		}
		Player p = event.getPlayer();
		switch (k) {
			case ENK_BOX -> {
				event.setCancelled(true);
				useBox(p);
			}
			case CRATE_RANDOM -> {
				event.setCancelled(true);
				Shards.openRandom(p);
			}
			case CRATE_NOMINABLE -> {
				event.setCancelled(true);
				Shards.openNominable(p);
			}
			default -> {
			}
		}
	}
}
