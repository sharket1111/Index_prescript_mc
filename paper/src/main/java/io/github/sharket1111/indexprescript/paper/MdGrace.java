package io.github.sharket1111.indexprescript.paper;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * The Starter Buffs ("Grace of the Sisyphean Star"), bought with Starlight
 * before each run as in the original: ten buffs, each up to two more times at
 * its base price (930 Starlight for all at most). Every member may buy; the
 * party gets the highest level anyone bought. Each member has a 60 Starlight
 * Bonus for the run, spent first; what is left becomes Cost (x5) or is kept.
 * Also here: the starting E.G.O Gift (one of three Tier II gifts of a chosen
 * keyword) and E.G.O Gift Search (up to 3 gifts of Tier III or lower for
 * 70/160/270 Starlight).
 */
final class MdGrace {
	static final int RUN_BONUS = 60;
	private static final int[] SEARCH_COST = {0, 70, 160, 270};

	enum Buff {
		BEGINNING("시작의 별", Material.NETHER_STAR, 10, "시작 코스트 +150/+250/+250", "테마팩 선택지 +1, 상점 진열 +1", "상점 무료 새로고침 1/1/2회"),
		STARCLOUD("쌓이는 별구름", Material.WHITE_WOOL, 10, "다음 층으로 갈 때 보유 코스트 +10/20/30% (최대 100/125/150)", "기프트 판매가 +50/75/100% (상점당 1회)"),
		TRAVEL("성간 여행", Material.ENDER_PEARL, 20, "테마팩 선택지 +1, 테마팩 새로고침 +2/3/4", "처음 고르는 테마팩마다 레벨 +3 (최대 9)", "(+, ++) 합 위력 증가 1/2: 피해 +5%씩"),
		SHOWER("별의 소나기", Material.PRISMARINE_CRYSTALS, 20, "시작 코스트 +400/500/700", "시작 기프트 선택 +1"),
		BINARY("쌍성 상점", Material.EMERALD, 30, "상점 진열 +1, 전투 보상 코스트 +20/30/40%", "상점에 높은 등급이 더 잘 나와요", "(+, ++) 상점 입장 시 보유 기프트 등급 합만큼 코스트 (최대 75/150)"),
		MOON("달별 상점", Material.SEA_LANTERN, 30, "상점 무료 키워드 새로고침 1/1/2회", "1층 입장 시 1등급 기프트 1/2/3개", "(+, ++) 슈퍼 상점 확률 +10/25%"),
		NEBULAE("성운의 총애", Material.AMETHYST_CLUSTER, 40, "입장 시 레벨 +3", "층 클리어마다 레벨 +1 (최대 5/9/9)"),
		GUIDANCE("별빛 인도", Material.BEACON, 40, "이동 속도 증가", "합 위력 증가 1/2/2 · 피해량 증가 1/1/2: 피해 +5%씩", "보호 1/1/2: 받는 피해 -10%씩"),
		COMET("우연의 혜성", Material.FIRE_CHARGE, 50, "상점 입장마다 합성·판매 전용 기프트 1개 (1~3등급, ++는 2~3등급)", "3층(+는 5층도, ++는 4등급) 입장 시 기프트 1개",
				"합성 시 결과보다 2등급(++는 1등급) 낮은 기프트 1개 더 (3등급 이상, 상점당 1회)"),
		PERFECTED("완성된 가능성", Material.TOTEM_OF_UNDYING, 60, "테마팩 선택지 +1, 시작 기프트 선택 +1", "시작 키워드의 3등급 기프트 1/2/3개");

		final String ko;
		final Material icon;
		final int cost;
		final String[] lines;

		Buff(String ko, Material icon, int cost, String... lines) {
			this.ko = ko;
			this.icon = icon;
			this.cost = cost;
			this.lines = lines;
		}
	}

	/** What each member bought this run. */
	private static final Map<MdRun, Map<UUID, Map<Buff, Integer>>> BOUGHT = new WeakHashMap<>();
	private static final Map<MdRun, Integer> SEARCH = new WeakHashMap<>();

	private MdGrace() {
	}

	static int level(MdRun run, Buff buff) {
		return run.grace.getOrDefault(buff, 0);
	}

	private static Map<Buff, Integer> bought(MdRun run, UUID id) {
		return BOUGHT.computeIfAbsent(run, r -> new HashMap<>()).computeIfAbsent(id, k -> new EnumMap<>(Buff.class));
	}

	// --- the screen ---------------------------------------------------------------------------------

	static void open(MdRun run, Player p) {
		if (run.phase != MdRun.Phase.GRACE) {
			return;
		}
		UUID id = p.getUniqueId();
		Map<Buff, Integer> mine = bought(run, id);
		MdGui gui = new MdGui(6, "별의 가호 · 별빛 " + MdMeta.starlight(id) + " (+보너스 " + run.bonus.getOrDefault(id, 0) + ")");
		int i = 0;
		for (Buff b : Buff.values()) {
			int lv = mine.getOrDefault(b, 0);
			int party = partyLevel(run, b);
			List<String> lore = new ArrayList<>(List.of(b.lines));
			lore.add("");
			lore.add("단계당 별빛 " + b.cost + " · 내 단계 " + lv + "/3" + (party > lv ? " (파티 " + party + ")" : ""));
			lore.add("왼클릭: 단계 올리기 · 오른클릭: 내리기 (환불)");
			ItemStack icon = MdGui.item(b.icon, b.ko + (lv > 0 ? " " + "+".repeat(lv - 1) : ""), lv > 0 ? NamedTextColor.AQUA : NamedTextColor.GRAY,
					lore.toArray(String[]::new));
			if (lv > 0) {
				MdGui.glow(icon);
			}
			gui.set(10 + i + (i >= 5 ? 4 : 0), icon, e -> {
				if (e.isRightClick()) {
					if (mine.getOrDefault(b, 0) > 0) {
						mine.put(b, mine.get(b) - 1);
						refund(run, id, b.cost);
					}
				} else if (mine.getOrDefault(b, 0) < 3) {
					if (!pay(run, id, b.cost)) {
						p.sendMessage(Component.text("별빛이 모자라요.", NamedTextColor.RED));
						return;
					}
					mine.put(b, mine.getOrDefault(b, 0) + 1);
				}
				for (Player q : run.players()) {
					open(run, q);
				}
			});
			i++;
		}
		boolean conv = run.convert.getOrDefault(id, true);
		gui.set(37, MdGui.item(conv ? Material.SUNFLOWER : Material.GRAY_DYE, "남은 보너스 별빛: " + (conv ? "코스트로 바꾸기" : "별빛으로 받기"), NamedTextColor.GOLD,
				"보너스 1 = 코스트 5", "클릭해서 바꾸기"), () -> {
					run.convert.put(id, !conv);
					open(run, p);
				});
		boolean lead = id.equals(run.leader);
		boolean isReady = run.ready.contains(id);
		if (lead) {
			gui.set(43, MdGui.item(Material.LIME_CONCRETE, "출발", NamedTextColor.GREEN, "준비: " + run.ready.size() + "/" + (run.members.size() - 1) + "명 (리더 제외)",
					"누르면 시작 기프트를 골라요"), run::depart);
		} else {
			gui.set(43, MdGui.item(isReady ? Material.LIME_DYE : Material.GRAY_DYE, isReady ? "준비 완료" : "준비", NamedTextColor.GREEN, "리더가 출발을 눌러요"), () -> {
				if (!run.ready.remove(id)) {
					run.ready.add(id);
				}
				for (Player q : run.players()) {
					open(run, q);
				}
			});
		}
		gui.fill().open(p);
	}

	private static int partyLevel(MdRun run, Buff b) {
		int best = 0;
		for (Map<Buff, Integer> m : BOUGHT.getOrDefault(run, Map.of()).values()) {
			best = Math.max(best, m.getOrDefault(b, 0));
		}
		return best;
	}

	/** Pays from the run's Starlight Bonus first, then the player's Starlight. */
	private static boolean pay(MdRun run, UUID id, int amount) {
		int bonus = run.bonus.getOrDefault(id, 0);
		int fromBonus = Math.min(bonus, amount);
		if (!MdMeta.spendStarlight(id, amount - fromBonus)) {
			return false;
		}
		run.bonus.put(id, bonus - fromBonus);
		return true;
	}

	private static void refund(MdRun run, UUID id, int amount) {
		int room = RUN_BONUS - run.bonus.getOrDefault(id, 0);
		int toBonus = Math.min(room, amount);
		run.bonus.put(id, run.bonus.getOrDefault(id, 0) + toBonus);
		MdMeta.addStarlight(id, amount - toBonus);
	}

	/** At departure: the party's levels, and what applies at once. */
	static void apply(MdRun run) {
		for (Buff b : Buff.values()) {
			int lv = partyLevel(run, b);
			if (lv > 0) {
				run.grace.put(b, lv);
			}
		}
		int beginning = level(run, Buff.BEGINNING);
		int shower = level(run, Buff.SHOWER);
		run.cost += beginning == 0 ? 0 : beginning == 1 ? 150 : 250;
		run.cost += shower == 0 ? 0 : shower == 1 ? 400 : shower == 2 ? 500 : 700;
		run.themeOffers = 3 + (beginning > 0 ? 1 : 0) + (level(run, Buff.TRAVEL) > 0 ? 1 : 0) + (level(run, Buff.PERFECTED) > 0 ? 1 : 0);
		run.themeOffers = Math.min(5, run.themeOffers);
		int travel = level(run, Buff.TRAVEL);
		run.themeRefreshes = 1 + (travel == 0 ? 0 : travel + 1);
		if (level(run, Buff.NEBULAE) > 0) {
			run.levels += 3;
		}
		if (level(run, Buff.GUIDANCE) > 0) {
			for (Player p : run.players()) {
				p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 0, false, false, true));
			}
		}
		run.updateBar();
	}

	// --- hooks ---------------------------------------------------------------------------------

	static void floorEnter(MdRun run) {
		if (run.floor == 1) {
			int moon = level(run, Buff.MOON);
			for (int i = 0; i < moon; i++) {
				giveRandom(run, 1, null, false);
			}
		}
		int comet = level(run, Buff.COMET);
		if (comet > 0 && (run.floor == 3 || comet >= 2 && run.floor == 5)) {
			giveRandom(run, comet >= 3 ? 4 : 3, null, false);
		}
	}

	static void floorClear(MdRun run) {
		int cloud = level(run, Buff.STARCLOUD);
		if (cloud > 0) {
			int gain = (int) Math.min(new int[] {0, 100, 125, 150}[cloud], run.cost * (0.1 * cloud));
			run.cost += gain;
			run.broadcast(Component.text("쌓이는 별구름: 코스트 +" + gain, NamedTextColor.AQUA));
		}
		int nebulae = level(run, Buff.NEBULAE);
		if (nebulae > 0 && run.levels < 3 + (nebulae == 1 ? 5 : 9)) {
			run.levels++;
		}
	}

	/** The first time a Theme Pack is chosen this run (Interstellar Travel: +3 levels, up to 9). */
	static void newTheme(MdRun run) {
		if (level(run, Buff.TRAVEL) > 0 && travelLevels(run) < 9) {
			run.levels += 3;
			TRAVEL_LEVELS.merge(run, 3, Integer::sum);
		}
		if (run.theme != null && run.theme.layout().equals("long") && !LONG_DONE.containsKey(run)) {
			LONG_DONE.put(run, true);
			run.levels += 3;
		}
	}

	private static final Map<MdRun, Integer> TRAVEL_LEVELS = new WeakHashMap<>();
	private static final Map<MdRun, Boolean> LONG_DONE = new WeakHashMap<>();

	private static int travelLevels(MdRun run) {
		return TRAVEL_LEVELS.getOrDefault(run, 0);
	}

	static void shopEnter(MdRun run) {
		int binary = level(run, Buff.BINARY);
		if (binary >= 2) {
			int tiers = 0;
			for (String id : run.gifts.keySet()) {
				MdData.Gift g = MdData.gift(id);
				tiers += g == null ? 0 : g.tierNumber();
			}
			int gain = Math.min(binary == 2 ? 75 : 150, tiers);
			run.cost += gain;
			run.broadcast(Component.text("쌍성 상점: 코스트 +" + gain, NamedTextColor.AQUA));
		}
		int comet = level(run, Buff.COMET);
		if (comet > 0) {
			giveRandom(run, comet >= 3 ? 2 + MdRun.rng().nextInt(2) : 1 + MdRun.rng().nextInt(3), null, true);
		}
	}

	static int shopSlots(MdRun run) {
		return (level(run, Buff.BEGINNING) > 0 ? 1 : 0) + (level(run, Buff.BINARY) > 0 ? 1 : 0);
	}

	static boolean highTierShop(MdRun run) {
		return level(run, Buff.BINARY) > 0;
	}

	static int freeRefreshes(MdRun run) {
		int lv = level(run, Buff.BEGINNING);
		return lv == 0 ? 0 : lv == 3 ? 2 : 1;
	}

	static int freeKeywordRefreshes(MdRun run) {
		int lv = level(run, Buff.MOON);
		return lv == 0 ? 0 : lv == 3 ? 2 : 1;
	}

	static int superShopBonus(MdRun run) {
		int lv = level(run, Buff.MOON);
		return lv == 2 ? 10 : lv == 3 ? 25 : 0;
	}

	static int sellBonus(MdRun run) {
		int lv = level(run, Buff.STARCLOUD);
		return lv == 0 ? 0 : 25 + 25 * lv;
	}

	/** Extra battle Cost (fraction). */
	static double rewardBonus(MdRun run) {
		int lv = level(run, Buff.BINARY);
		return lv == 0 ? 0 : 0.1 + 0.1 * lv;
	}

	/** Extra damage (percent) from Starlight Guidance and Interstellar Travel. */
	static double damageBonus(MdRun run) {
		int g = level(run, Buff.GUIDANCE);
		int clash = g == 0 ? 0 : g == 1 ? 1 : 2;
		int up = g == 0 ? 0 : g == 3 ? 2 : 1;
		int t = level(run, Buff.TRAVEL);
		clash += t <= 1 ? 0 : t - 1;
		return 5 * (clash + up);
	}

	/** Damage taken reduction (fraction) from Starlight Guidance's Protection. */
	static double protection(MdRun run) {
		int g = level(run, Buff.GUIDANCE);
		return g == 0 ? 0 : g == 3 ? 0.2 : 0.1;
	}

	/** Chance Comet: a gift 2 tiers below a fusion's result (1 below at ++), when the result is Tier III or higher. */
	static MdData.Gift fusionBonus(MdRun run, int tier) {
		int lv = level(run, Buff.COMET);
		if (lv == 0 || tier < 3) {
			return null;
		}
		int want = tier - (lv >= 3 ? 1 : 2);
		List<MdData.Gift> pool = MdData.gifts().stream().filter(g -> g.tierNumber() == want && g.common()
				&& !run.gifts.containsKey(g.id())).toList();
		return pool.isEmpty() ? null : pool.get(MdRun.rng().nextInt(pool.size()));
	}

	private static void giveRandom(MdRun run, int tier, String keyword, boolean fuseOnly) {
		List<MdData.Gift> pool = MdData.gifts().stream().filter(g -> g.tierNumber() == tier && g.common()
				&& !run.gifts.containsKey(g.id()) && (keyword == null || g.keyword().equals(keyword))).toList();
		if (!pool.isEmpty()) {
			run.addGift(pool.get(MdRun.rng().nextInt(pool.size())), fuseOnly);
		}
	}

	// --- the starting gift and E.G.O Gift Search ---------------------------------------------------------

	private static final Map<String, List<String>> START = Map.of(
			"burn", List.of("Hellterfly’s Dream", "Fiery Down", "Pain of Stifled Rage"),
			"bleed", List.of("Wound Clerid", "Little and To-be-Naughty Plushie", "Awe"),
			"tremor", List.of("Oscillating Bracelet", "Reverberation", "Sour Liquor Aroma"),
			"rupture", List.of("Barbed Lasso", "Fluorescent Lamp", "Smoking Gunpowder"),
			"sinking", List.of("Red Order", "Melted Spring", "Grandeur"),
			"poise", List.of("Cigarette Holder", "Stone Tomb", "Old Wooden Doll"),
			"charge", List.of("Employee Card", "Portable Battery Socket", "Patrolling Flashlight"),
			"slash", List.of("Dreaming Electric Sheep", "Short Cane Sword", "Resolution"),
			"pierce", List.of("Gathering Skulls", "Plume of Proof", "Torn Bandolier"),
			"blunt", List.of("Today’s Expression", "Pocket Flashcards", "Temporal Bridle"));
	private static final Map<MdRun, String> START_KEYWORD = new WeakHashMap<>();

	static int startPicks(MdRun run) {
		return 1 + (level(run, Buff.SHOWER) > 0 ? 1 : 0) + (level(run, Buff.PERFECTED) > 0 ? 1 : 0);
	}

	static void startGift(MdRun run, Player leader) {
		if (leader == null) {
			return;
		}
		String keyword = START_KEYWORD.get(run);
		if (run.phase == MdRun.Phase.SEARCH) {
			search(run, leader, keyword == null ? "burn" : keyword, new ArrayList<>(), 0);
			return;
		}
		if (keyword == null) {
			MdGui gui = new MdGui(3, "시작 기프트 · 키워드 고르기");
			int i = 0;
			for (String kw : START.keySet().stream().sorted((a, b) -> MdData.KEYWORDS.indexOf(a) - MdData.KEYWORDS.indexOf(b)).toList()) {
				gui.set(i < 7 ? 10 + i : 12 + i, MdGui.item(MdShop.keywordIcon(kw), MdData.KEYWORD_KO.get(kw), NamedTextColor.YELLOW, "이 키워드의 2등급 기프트 중에서 골라요"),
						() -> {
							START_KEYWORD.put(run, kw);
							startGift(run, leader);
						});
				i++;
			}
			gui.fill().open(leader);
			return;
		}
		int picks = startPicks(run);
		long owned = START.get(keyword).stream().filter(n -> {
			MdData.Gift g = MdData.giftByName(n);
			return g != null && run.gifts.containsKey(g.id());
		}).count();
		MdGui gui = new MdGui(3, "시작 기프트 (" + owned + "/" + picks + ")");
		int i = 0;
		for (String name : START.get(keyword)) {
			MdData.Gift g = MdData.giftByName(name);
			if (g == null) {
				continue;
			}
			boolean has = run.gifts.containsKey(g.id());
			ItemStack icon = MdShop.icon(run, g, 0, has ? "받음" : "클릭해서 받기");
			if (has) {
				MdGui.glow(icon);
			}
			gui.set(11 + i * 2, icon, () -> {
				if (has || owned >= picks) {
					return;
				}
				run.addGift(g, false);
				if (owned + 1 >= picks) {
					afterStart(run, leader, keyword);
				} else {
					startGift(run, leader);
				}
			});
			i++;
		}
		gui.set(18, MdGui.item(Material.ARROW, "키워드 다시 고르기", NamedTextColor.WHITE), () -> {
			if (owned == 0) {
				START_KEYWORD.remove(run);
				startGift(run, leader);
			}
		});
		gui.fill().open(leader);
	}

	private static void afterStart(MdRun run, Player leader, String keyword) {
		int perfected = level(run, Buff.PERFECTED);
		for (int i = 0; i < perfected; i++) {
			giveRandom(run, 3, keyword, false);
		}
		run.phase = MdRun.Phase.SEARCH;
		search(run, leader, keyword, new ArrayList<>(), 0);
	}

	/** E.G.O Gift Search: up to 3 gifts of Tier III or lower, for 70/160/270 Starlight, before the first floor. */
	private static void search(MdRun run, Player leader, String keyword, List<String> chosen, int page) {
		MdGui gui = new MdGui(6, "기프트 탐색 · " + MdData.KEYWORD_KO.getOrDefault(keyword, "범용") + " (" + chosen.size() + "/3)");
		List<MdData.Gift> pool = MdData.gifts().stream().filter(g -> g.tierNumber() <= 3 && g.common() && !run.gifts.containsKey(g.id())
				&& (keyword.equals("none") ? g.keyword().equals("none") : g.keyword().equals(keyword))).toList();
		int per = 36;
		for (int i = page * per; i < pool.size() && i < (page + 1) * per; i++) {
			MdData.Gift g = pool.get(i);
			boolean in = chosen.contains(g.id());
			ItemStack icon = MdShop.icon(run, g, 0, in ? "고름 · 클릭해서 빼기" : "클릭해서 고르기");
			if (in) {
				MdGui.glow(icon);
			}
			gui.set(i - page * per, icon, () -> {
				List<String> next = new ArrayList<>(chosen);
				if (!next.remove(g.id()) && next.size() < 3) {
					next.add(g.id());
				}
				search(run, leader, keyword, next, page);
			});
		}
		int k = 36;
		for (String kw : MdData.KEYWORDS) {
			gui.set(k++, MdGui.item(MdShop.keywordIcon(kw), MdData.KEYWORD_KO.get(kw), kw.equals(keyword) ? NamedTextColor.GREEN : NamedTextColor.GRAY),
					() -> search(run, leader, kw, chosen, 0));
		}
		gui.set(46, MdGui.item(Material.PAPER, "범용", keyword.equals("none") ? NamedTextColor.GREEN : NamedTextColor.GRAY), () -> search(run, leader, "none", chosen, 0));
		if (page > 0) {
			gui.set(45, MdGui.item(Material.ARROW, "이전", NamedTextColor.WHITE), () -> search(run, leader, keyword, chosen, page - 1));
		}
		if (pool.size() > (page + 1) * per) {
			gui.set(53, MdGui.item(Material.ARROW, "다음", NamedTextColor.WHITE), () -> search(run, leader, keyword, chosen, page + 1));
		}
		int price = SEARCH_COST[chosen.size()];
		gui.set(49, MdGui.item(Material.LIME_CONCRETE, chosen.isEmpty() ? "탐색 없이 출발" : "별빛 " + price + "으로 받고 출발", NamedTextColor.GREEN,
				"1개 70 · 2개 160 · 3개 270 별빛", "내 별빛: " + MdMeta.starlight(leader.getUniqueId())), () -> {
					if (!chosen.isEmpty() && !MdMeta.spendStarlight(leader.getUniqueId(), price)) {
						leader.sendMessage(Component.text("별빛이 모자라요.", NamedTextColor.RED));
						return;
					}
					for (String id : chosen) {
						run.addGift(MdData.gift(id), false);
					}
					leader.closeInventory();
					run.begin();
				});
		gui.fill().open(leader);
	}
}
