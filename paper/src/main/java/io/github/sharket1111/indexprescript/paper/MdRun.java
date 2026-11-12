package io.github.sharket1111.indexprescript.paper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;

/**
 * One Mirror Dungeon run, for one player or a party (the original's Manager is
 * the party leader, who chooses Theme Packs, nodes, events and rewards; every
 * member fights and can shop). Five floors, after the original: each floor a
 * Theme Pack, then a map of node columns, a shop and the boss; after the boss,
 * E.G.O Gifts and Mounting Trials; moving on revives and heals everyone. The
 * run's Cost and E.G.O Gifts are shared by the party.
 */
final class MdRun {
	static final int FLOORS = 5;
	/** The INFINITY MIRROR goes to floor 10, the EXTREME MIRROR to floor 15; each is offered on clearing the one before on Hard. */
	static final int MAX_FLOORS = 15;
	/** The EXTREME floors' hidden bosses: the boss, who comes with it, and the Tier IV gift it leaves. */
	static final String[][] HIDDEN_BOSSES = {
		{"The Thumb Nursefather - Valencina", "The Thumb Apprentice - Lucio", "Imperfect Eye of Precognition"},
		{"The Index Nursefather - Rien", null, "The Abandoned Oracle"},
		{"The Middle Nursefather - Matthias", "The Middle Apprentice - Kira", "The Book of Vengeance: Annex"},
		{"The Ring Nursefather - Callisto", "The Ring Apprentice - Albina", "A Maestro's Ring-Turned-Artwork"}};
	static final int HIDDEN_CHANCE = 10;
	static final int BASE_COST = 200;
	private static final Map<UUID, MdRun> BY_PLAYER = new HashMap<>();
	private static final List<MdRun> RUNS = new ArrayList<>();
	private static final Set<Integer> SLOTS = new HashSet<>();

	enum Phase {
		GRACE, START_GIFT, SEARCH, THEME, MAP, BATTLE, EVENT, SHOP, REWARD, END
	}

	enum NodeType {
		NORMAL("일반 전투", Material.IRON_SWORD, 75, 85), RISKY("위험 전투", Material.GOLD_NUGGET, 120, 150),
		FOCUSED("집중 전투", Material.ZOMBIE_HEAD, 100, 120), ABNO("환상체 전투", Material.ENDER_EYE, 150, 180),
		EVENT("이벤트", Material.WRITABLE_BOOK, 0, 0), SHOP("상점", Material.EMERALD, 0, 0), BOSS("보스", Material.WITHER_SKELETON_SKULL, 200, 250),
		/** An optional boss that takes a node's place on the EXTREME floors. */
		HIDDEN("숨은 보스", Material.NETHER_STAR, 1000, 1000);

		final String ko;
		final Material icon;
		final int cost;
		final int hardCost;

		NodeType(String ko, Material icon, int cost, int hardCost) {
			this.ko = ko;
			this.icon = icon;
			this.cost = cost;
			this.hardCost = hardCost;
		}

		boolean battle() {
			return this != EVENT && this != SHOP;
		}
	}

	static final class Node {
		final int col;
		final int row;
		final NodeType type;
		final List<Node> next = new ArrayList<>();
		boolean visited;
		/** Whether this node has had its chance of a hidden boss. */
		boolean hiddenRolled;

		Node(int col, int row, NodeType type) {
			this.col = col;
			this.row = row;
			this.type = type;
		}
	}

	final int slot;
	final List<UUID> members = new ArrayList<>();
	UUID leader;
	final Map<UUID, Location> returnTo = new HashMap<>();
	final Map<UUID, GameMode> modes = new HashMap<>();
	final Set<UUID> downed = new HashSet<>();
	final Random random = new Random();
	Phase phase = Phase.GRACE;
	boolean hard;
	boolean floorHard;
	int floor;
	int floorsCleared;
	int hardFloorsCleared;
	/** The floor this run ends on: 5, then 10 and 15 as the party chooses to go on. */
	int goal = FLOORS;
	/** Adversities taken (from floor 10 on). */
	final List<MdAdversity> adversities = new ArrayList<>();
	final Set<String> hiddenBeaten = new HashSet<>();
	MdData.Theme theme;
	final Set<String> visitedThemes = new HashSet<>();
	List<List<Node>> map = new ArrayList<>();
	Node at;
	int cost;
	/** Owned gifts (id -> enhance level), and those only good for fusing or selling. */
	final Map<String, Integer> gifts = new LinkedHashMap<>();
	final Set<String> fuseOnly = new HashSet<>();
	final List<MdData.Trial> trials = new ArrayList<>();
	/** Starter buffs bought (buff -> level 1-3), the party's highest. */
	final Map<MdGrace.Buff, Integer> grace = new LinkedHashMap<>();
	/** Identity levels gained (Starter Buffs, Long Battle packs): +1% damage and -1% damage taken each. */
	int levels;
	int themeOffers = 3;
	int themeRefreshes = 1;
	int observeCount;
	int superShopChance = 3;
	final Set<String> seenEvents = new HashSet<>();
	MdBattle battle;
	MdShop shop;
	/** Starlight Bonus left for each member (spent on Starter Buffs first). */
	final Map<UUID, Integer> bonus = new HashMap<>();
	final Map<UUID, Boolean> convert = new HashMap<>();
	final Set<UUID> ready = new HashSet<>();
	private BossBar bar;
	List<MdData.Theme> offered = new ArrayList<>();

	private MdRun(int slot) {
		this.slot = slot;
	}

	static MdRun of(Player player) {
		return BY_PLAYER.get(player.getUniqueId());
	}

	static MdRun of(UUID id) {
		return BY_PLAYER.get(id);
	}

	static List<MdRun> all() {
		return List.copyOf(RUNS);
	}

	// --- start ---------------------------------------------------------------------------------

	/** Starts a run for {@code party} (the first is the leader). */
	static MdRun start(List<Player> party, boolean hard) {
		if (!MdData.loaded()) {
			party.get(0).sendMessage(Component.text("거울 던전 자료를 읽지 못했어요. 서버 로그를 확인해 주세요.", NamedTextColor.RED));
			return null;
		}
		int slot = 1;
		while (SLOTS.contains(slot)) {
			slot++;
		}
		SLOTS.add(slot);
		MdRun run = new MdRun(slot);
		run.hard = hard;
		run.floorHard = hard;
		run.leader = party.get(0).getUniqueId();
		run.bar = Bukkit.createBossBar("거울 던전", BarColor.BLUE, BarStyle.SOLID);
		RUNS.add(run);
		MdWorld.build(slot, MdWorld.Room.START, 0x8E2121, "거울 던전");
		for (Player p : party) {
			MdMeta.welcome(p);
			run.members.add(p.getUniqueId());
			BY_PLAYER.put(p.getUniqueId(), run);
			run.returnTo.put(p.getUniqueId(), p.getLocation());
			run.modes.put(p.getUniqueId(), p.getGameMode());
			run.bonus.put(p.getUniqueId(), MdGrace.RUN_BONUS);
			run.convert.put(p.getUniqueId(), true);
			p.teleport(MdWorld.entrance(slot, MdWorld.Room.START));
			p.setGameMode(GameMode.ADVENTURE);
			run.bar.addPlayer(p);
		}
		run.broadcast(Component.text("거울 던전에 들어왔어요. 먼저 별의 가호(시작 버프)를 고르세요.", NamedTextColor.AQUA));
		run.updateBar();
		for (Player p : run.players()) {
			MdGrace.open(run, p);
		}
		return run;
	}

	/** Called by the leader from the Starter Buff screen: Cost, buffs, then the starting gift. */
	void depart() {
		if (phase != Phase.GRACE) {
			return;
		}
		cost = BASE_COST;
		for (UUID id : members) {
			if (convert.getOrDefault(id, true)) {
				cost += bonus.getOrDefault(id, 0) * 5;
				bonus.put(id, 0);
			}
		}
		MdGrace.apply(this);
		phase = Phase.START_GIFT;
		updateBar();
		for (Player p : players()) {
			p.closeInventory();
		}
		MdGrace.startGift(this, leaderPlayer());
	}

	/** After the starting gifts (and any E.G.O Gift Search): the first floor. */
	void begin() {
		if (phase != Phase.START_GIFT && phase != Phase.SEARCH) {
			return;
		}
		nextFloor();
	}

	// --- floors ------------------------------------------------------------------------------

	/** The sum of the values of the adversities of a kind taken (0: none). */
	int adv(String kind) {
		int sum = 0;
		for (MdAdversity a : adversities) {
			if (a.kind.equals(kind)) {
				sum += a.value;
			}
		}
		return sum;
	}

	/** The lowest value among the adversities of a kind taken, or {@code none}. */
	int advMin(String kind, int none) {
		int min = none;
		for (MdAdversity a : adversities) {
			if (a.kind.equals(kind)) {
				min = Math.min(min, a.value);
			}
		}
		return min;
	}

	int adversityScore() {
		int sum = 0;
		for (MdAdversity a : adversities) {
			sum += a.score;
		}
		return sum;
	}

	/** INFINITY (floors 6-10) or EXTREME (11-15), or the floor's own difficulty. */
	String modeName() {
		return floor > 10 ? "EXTREME MIRROR" : floor > FLOORS ? "INFINITY MIRROR" : floorHard ? "HARD" : "NORMAL";
	}

	private static final NamespacedKey FRAIL = new NamespacedKey("index_prescript", "md_frail");

	/** Frailness: each one taken lowers the members' maximum health by 10%. */
	void applyFrail() {
		int pct = Math.min(80, adv("frail"));
		for (Player p : players()) {
			AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
			if (max == null) {
				continue;
			}
			max.removeModifier(FRAIL);
			if (pct > 0) {
				max.addModifier(new AttributeModifier(FRAIL, -pct / 100.0, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
				p.setHealth(Math.min(p.getHealth(), max.getValue()));
			}
		}
	}

	void nextFloor() {
		if (floor >= goal) {
			finish(true);
			return;
		}
		if (floor > 0) {
			MdGrace.floorClear(this);
		}
		floor++;
		// Past floor 5 the dungeon is always Hard.
		floorHard = hard || floor > FLOORS;
		applyFrail();
		theme = null;
		map.clear();
		at = null;
		// Moving to a new floor revives and heals everyone.
		for (UUID id : List.copyOf(downed)) {
			revive(id);
		}
		for (Player p : players()) {
			heal(p, 1.0);
		}
		MdGrace.floorEnter(this);
		phase = Phase.THEME;
		MdWorld.build(slot, MdWorld.Room.START, 0x8E2121, floor + "층");
		for (Player p : players()) {
			p.teleport(MdWorld.entrance(slot, MdWorld.Room.START));
			p.showTitle(Title.title(Component.text(floor + "층", NamedTextColor.WHITE).decorate(TextDecoration.BOLD),
					Component.text(modeName(), floorHard ? NamedTextColor.RED : NamedTextColor.GRAY)));
		}
		offered = rollThemes(themeOffers);
		updateBar();
		openTheme(leaderPlayer());
	}

	/** Theme Packs for this floor and difficulty, not visited this run. */
	List<MdData.Theme> themePool() {
		List<MdData.Theme> pool = new ArrayList<>();
		// Floors 6-10 draw from Hard floor 5's packs; floors 11-15 from the EXTREME packs.
		boolean extreme = floor > 10;
		int at = floor > FLOORS && !extreme ? FLOORS : floor;
		for (MdData.Theme t : MdData.themes()) {
			if (t.on(at, floorHard) && !visitedThemes.contains(t.id()) && t.category().contains("EXTREME") == extreme) {
				pool.add(t);
			}
		}
		if (pool.size() < 3) {
			// Few left unvisited this deep: packs may come again.
			for (MdData.Theme t : MdData.themes()) {
				if (t.on(at, floorHard) && t.category().contains("EXTREME") == extreme && !pool.contains(t)) {
					pool.add(t);
				}
			}
		}
		return pool;
	}

	List<MdData.Theme> rollThemes(int count) {
		List<MdData.Theme> pool = new ArrayList<>(themePool());
		java.util.Collections.shuffle(pool, random);
		return new ArrayList<>(pool.subList(0, Math.min(count, pool.size())));
	}

	void openTheme(Player viewer) {
		if (viewer == null || phase != Phase.THEME) {
			return;
		}
		boolean lead = viewer.getUniqueId().equals(leader);
		MdGui gui = new MdGui(4, floor + "층 · 테마팩 선택");
		int[] slots = {10, 12, 14, 16, 11, 15};
		for (int i = 0; i < offered.size() && i < slots.length; i++) {
			MdData.Theme t = offered.get(i);
			List<String> lore = new ArrayList<>();
			lore.add(t.category());
			lore.add("층 길이: 노드 " + t.length() + "개" + (t.layout().equals("long") ? " (장기전)" : ""));
			if (!t.boss().isBlank()) {
				lore.add("보스: " + t.boss());
			}
			if (t.keyword() != null) {
				lore.add("적 키워드: " + MdData.KEYWORD_KO.get(t.keyword()));
			}
			if (!t.unique().isEmpty()) {
				lore.add("전용 기프트: " + String.join(", ", t.unique().subList(0, Math.min(3, t.unique().size()))));
			}
			lore.add(lead ? "\n클릭해서 이 테마팩으로 가요" : "\n리더가 골라요");
			gui.set(slots[i], MdGui.item(MdWorld.concrete(t.color()), t.name(), TextColor.color(t.color()), lore.toArray(String[]::new)),
					lead ? () -> chooseTheme(t) : null);
		}
		if (lead) {
			gui.set(29, MdGui.item(Material.CLOCK, "테마팩 새로고침 (" + themeRefreshes + "번 남음)", NamedTextColor.YELLOW, "선택지를 모두 다시 뽑아요"), () -> {
				if (themeRefreshes <= 0) {
					viewer.sendMessage(Component.text("새로고침 기회가 없어요.", NamedTextColor.RED));
					return;
				}
				themeRefreshes--;
				offered = rollThemes(themeOffers);
				openTheme(viewer);
			});
			int observeCost = 20 + 10 * observeCount;
			gui.set(31, MdGui.item(Material.SPYGLASS, "테마팩 관측 (별빛 " + observeCost + ")", NamedTextColor.AQUA,
					"이번 층에 나올 수 있는 테마팩 하나를", "골라 첫 번째 선택지와 바꿔요.", "쓸 때마다 별빛 +10",
					"내 별빛: " + MdMeta.starlight(viewer.getUniqueId())), () -> observe(viewer, 0));
			gui.set(33, MdGui.item(floorHard ? Material.REDSTONE_BLOCK : Material.IRON_BLOCK, "난이도: " + (floorHard ? "HARD" : "NORMAL"),
					floorHard ? NamedTextColor.RED : NamedTextColor.GRAY, "클릭해서 이번 층부터 난이도를 바꿔요",
					"HARD: 적이 강하고, 코스트·보상 기프트가 늘고,", "보스 뒤 기프트 2개·시련 2개, 슈퍼 상점이 나올 수 있어요"), () -> {
						floorHard = !floorHard;
						hard = floorHard;
						offered = rollThemes(themeOffers);
						updateBar();
						openTheme(viewer);
					});
		}
		gui.set(35, MdGui.item(Material.CHEST, "보유 기프트 (" + gifts.size() + ")", NamedTextColor.GOLD), () -> MdShop.showGifts(this, viewer, () -> openTheme(viewer)));
		gui.fill().open(viewer);
	}

	/** Theme Pack Observation: pick any Theme Pack for this floor in place of an offer, for Starlight. */
	void observe(Player viewer, int page) {
		List<MdData.Theme> pool = themePool();
		MdGui gui = new MdGui(6, "테마팩 관측");
		int per = 45;
		for (int i = page * per; i < pool.size() && i < (page + 1) * per; i++) {
			MdData.Theme t = pool.get(i);
			int price = 20 + 10 * observeCount;
			gui.set(i - page * per, MdGui.item(MdWorld.concrete(t.color()), t.name(), TextColor.color(t.color()), t.category(), "별빛 " + price), () -> {
				if (!MdMeta.spendStarlight(viewer.getUniqueId(), price)) {
					viewer.sendMessage(Component.text("별빛이 모자라요.", NamedTextColor.RED));
					return;
				}
				observeCount++;
				offered.removeIf(o -> o.id().equals(t.id()));
				if (offered.isEmpty()) {
					offered.add(t);
				} else {
					offered.set(0, t);
				}
				openTheme(viewer);
			});
		}
		if (pool.size() > (page + 1) * per) {
			gui.set(53, MdGui.item(Material.ARROW, "다음", NamedTextColor.WHITE), () -> observe(viewer, page + 1));
		}
		gui.set(49, MdGui.item(Material.BARRIER, "돌아가기", NamedTextColor.RED), () -> openTheme(viewer));
		gui.fill().open(viewer);
	}

	void chooseTheme(MdData.Theme t) {
		if (phase != Phase.THEME) {
			return;
		}
		theme = t;
		boolean first = visitedThemes.add(t.id());
		if (first) {
			MdGrace.newTheme(this);
		}
		generateMap();
		phase = Phase.MAP;
		broadcast(Component.text(floor + "층 테마팩: ", NamedTextColor.GRAY).append(Component.text(t.name(), TextColor.color(t.color()))));
		updateBar();
		for (Player p : players()) {
			p.closeInventory();
		}
		openMap(leaderPlayer());
	}

	// --- the map ---------------------------------------------------------------------------------

	private void generateMap() {
		map = new ArrayList<>();
		int columns;
		if (theme.layout().equals("long")) {
			columns = 1;
		} else {
			columns = Math.max(2, Math.min(5, theme.length() - 2));
		}
		for (int c = 0; c < columns; c++) {
			List<Node> col = new ArrayList<>();
			int count = theme.layout().equals("long") ? 2 : 1 + random.nextInt(3);
			List<Integer> rows = new ArrayList<>(List.of(0, 1, 2));
			java.util.Collections.shuffle(rows, random);
			rows = rows.subList(0, count);
			java.util.Collections.sort(rows);
			for (int r : rows) {
				col.add(new Node(c, r, theme.layout().equals("long") ? NodeType.EVENT : rollNode(c)));
			}
			map.add(col);
		}
		// The first column always offers a plain battle.
		if (!theme.layout().equals("long") && map.get(0).stream().noneMatch(n -> n.type == NodeType.NORMAL)) {
			Node n = map.get(0).get(0);
			map.get(0).set(0, new Node(n.col, n.row, NodeType.NORMAL));
		}
		List<Node> shop = List.of(new Node(columns, 1, NodeType.SHOP));
		List<Node> boss = List.of(new Node(columns + 1, 1, NodeType.BOSS));
		map.add(shop);
		map.add(boss);
		for (int c = 0; c < map.size() - 1; c++) {
			List<Node> next = map.get(c + 1);
			for (Node n : map.get(c)) {
				for (Node m : next) {
					if (next.size() == 1 || map.get(c).size() == 1 || Math.abs(n.row - m.row) <= 1) {
						n.next.add(m);
					}
				}
				if (n.next.isEmpty()) {
					n.next.add(next.get(0));
				}
			}
			for (Node m : next) {
				if (map.get(c).stream().noneMatch(n -> n.next.contains(m))) {
					map.get(c).get(0).next.add(m);
				}
			}
		}
	}

	private NodeType rollNode(int column) {
		int roll = random.nextInt(100);
		if (roll < 40) {
			return NodeType.NORMAL;
		}
		if (roll < 65) {
			return NodeType.EVENT;
		}
		if (roll < 78) {
			return NodeType.RISKY;
		}
		if (roll < 91) {
			return NodeType.FOCUSED;
		}
		return NodeType.ABNO;
	}

	List<Node> reachable() {
		if (map.isEmpty()) {
			return List.of();
		}
		return at == null ? map.get(0) : at.next;
	}

	void openMap(Player viewer) {
		if (viewer == null || phase != Phase.MAP) {
			return;
		}
		boolean lead = viewer.getUniqueId().equals(leader);
		MdGui gui = new MdGui(6, floor + "층 · " + theme.name());
		List<Node> can = reachable();
		gui.set(18, MdGui.item(Material.LODESTONE, "시작", NamedTextColor.WHITE), (Runnable) null);
		for (List<Node> col : map) {
			for (Node n : col) {
				int slot = (n.row + 1) * 9 + n.col + 1;
				boolean open = can.contains(n);
				List<String> lore = new ArrayList<>();
				if (n.type.battle()) {
					int c = floorHard ? n.type.hardCost : n.type.cost;
					lore.add("코스트 " + c + (n.type == NodeType.BOSS ? "+" : ""));
					lore.add(switch (n.type) {
						case NORMAL -> "기프트 확률 5%";
						case RISKY -> "기프트 확률 35%";
						case FOCUSED, ABNO -> "기프트 확률 70%";
						case BOSS -> "기프트 선택 + 등반 시련";
						default -> "";
					});
				}
				if (n == at) {
					lore.add("현재 위치");
				} else if (n.visited) {
					lore.add("지나온 곳");
				} else if (open) {
					lore.add(lead ? "\n클릭해서 이동" : "\n리더가 골라요");
				}
				ItemStack icon = MdGui.item(n.visited && n != at ? Material.GRAY_DYE : n.type.icon, n.type.ko,
						n == at ? NamedTextColor.AQUA : open ? NamedTextColor.YELLOW : NamedTextColor.DARK_GRAY, lore.toArray(String[]::new));
				if (open) {
					MdGui.glow(icon);
				}
				gui.set(slot, icon, open && lead ? () -> enter(n) : null);
			}
		}
		gui.set(45, MdGui.item(Material.SUNFLOWER, "코스트 " + cost, NamedTextColor.GOLD, "판 안에서 쓰는 화폐예요"), (Runnable) null);
		gui.set(46, MdGui.item(Material.CHEST, "보유 기프트 (" + gifts.size() + ")", NamedTextColor.GOLD), () -> MdShop.showGifts(this, viewer, () -> openMap(viewer)));
		gui.set(47, MdGui.item(Material.REDSTONE_TORCH, "등반 시련 (" + trials.size() + ")", NamedTextColor.RED, trialLines().toArray(String[]::new)), (Runnable) null);
		gui.set(53, MdGui.item(Material.BARRIER, "던전 나가기", NamedTextColor.RED, "지금까지 깬 층까지만 보상을 받아요", "(Shift+클릭)"), e -> {
			if (e.isShiftClick()) {
				leave(viewer);
			}
		});
		gui.fill().open(viewer);
	}

	List<String> trialLines() {
		List<String> out = new ArrayList<>();
		for (MdData.Trial t : trials) {
			out.add(t.name() + ": " + t.text());
		}
		return out.isEmpty() ? List.of("아직 없어요") : out;
	}

	void enter(Node n) {
		if (phase != Phase.MAP || !reachable().contains(n)) {
			return;
		}
		if (floor > 10 && n.type != NodeType.BOSS && n.type != NodeType.SHOP && !n.hiddenRolled) {
			n.hiddenRolled = true;
			if (random.nextInt(100) < HIDDEN_CHANCE) {
				offerHidden(n);
				return;
			}
		}
		proceed(n);
	}

	/** A hidden boss stands in the node's place: the leader may take it on (no leaving once in) or pass by to the node itself. */
	private void offerHidden(Node n) {
		Player viewer = leaderPlayer();
		List<String[]> left = new ArrayList<>();
		for (String[] h : HIDDEN_BOSSES) {
			if (!hiddenBeaten.contains(h[0])) {
				left.add(h);
			}
		}
		if (viewer == null || left.isEmpty()) {
			proceed(n);
			return;
		}
		String[] boss = left.get(random.nextInt(left.size()));
		MdGui gui = new MdGui(3, "숨은 보스가 나타났어요");
		gui.set(11, MdGui.item(Material.NETHER_STAR, boss[0], NamedTextColor.LIGHT_PURPLE, boss[1] == null ? "혼자 나와요" : "함께: " + boss[1],
				"이기면: 4등급 기프트 [" + boss[2] + "], 코스트 1000, 모두 완전 회복", "들어가면 전투를 포기할 수 없어요", "클릭해서 도전"), () -> {
					viewer.closeInventory();
					fightHidden(n, boss);
				});
		gui.set(15, MdGui.item(Material.OAK_DOOR, "지나가기", NamedTextColor.GRAY, "원래 노드(" + n.type.ko + ")로 가요"), () -> {
			viewer.closeInventory();
			proceed(n);
		});
		gui.fill().open(viewer);
	}

	private void fightHidden(Node n, String[] boss) {
		if (phase != Phase.MAP) {
			return;
		}
		at = n;
		n.visited = true;
		phase = Phase.BATTLE;
		MdWorld.build(slot, MdWorld.Room.BOSS, 0x6A1FA8, boss[0]);
		gather(MdWorld.Room.BOSS);
		broadcast(Component.text("숨은 보스: " + boss[0], NamedTextColor.LIGHT_PURPLE));
		battle = new MdBattle(this, NodeType.HIDDEN, boss[0], List.of(boss[2]));
		battle.ally = boss[1];
		battle.start();
		updateBar();
	}

	private void proceed(Node n) {
		if (phase != Phase.MAP) {
			return;
		}
		at = n;
		n.visited = true;
		for (Player p : players()) {
			p.closeInventory();
		}
		switch (n.type) {
			case EVENT -> {
				phase = Phase.EVENT;
				MdWorld.build(slot, MdWorld.Room.EVENT, theme.color(), "이벤트");
				gather(MdWorld.Room.EVENT);
				MdEvent.start(this);
			}
			case SHOP -> {
				phase = Phase.SHOP;
				MdWorld.build(slot, MdWorld.Room.SHOP, theme.color(), "상점");
				gather(MdWorld.Room.SHOP);
				shop = MdShop.open(this);
			}
			default -> {
				phase = Phase.BATTLE;
				MdWorld.Room room = n.type == NodeType.BOSS ? MdWorld.Room.BOSS : MdWorld.Room.BATTLE;
				MdWorld.build(slot, room, theme.color(), n.type == NodeType.BOSS ? (theme.boss().isBlank() ? "보스" : theme.boss()) : theme.name());
				gather(room);
				battle = new MdBattle(this, n.type, null, List.of());
				battle.start();
			}
		}
		updateBar();
	}

	/** Brings everyone (the downed watch as spectators) into the current room. */
	void gather(MdWorld.Room room) {
		Location at = MdWorld.entrance(slot, room);
		for (Player p : players()) {
			p.teleport(at);
		}
	}

	/** Back to the map after a node (battle won, event or shop done). */
	void backToMap() {
		battle = null;
		shop = null;
		if (at != null && at.type == NodeType.BOSS) {
			return;
		}
		phase = Phase.MAP;
		updateBar();
		openMap(leaderPlayer());
	}

	// --- battle results ------------------------------------------------------------------------

	/** A won battle node: Cost, a chance of an E.G.O Gift (the boss: gifts and Mounting Trials). */
	void battleWon(NodeType type, List<String> eventGifts) {
		String battleFoe = battle == null ? null : battle.eventFoe();
		battle = null;
		int gained = (int) Math.round((floorHard ? type.hardCost : type.cost) * (type == NodeType.BOSS ? 1 + 0.25 * (floor - 1) : 1)
				* (1 + MdGrace.rewardBonus(this) + MdGifts.rewardPct(this) / 100.0));
		cost += gained;
		broadcast(Component.text("승리! 코스트 +" + gained + " (보유 " + cost + ")", NamedTextColor.GOLD));
		for (String name : eventGifts) {
			MdData.Gift g = MdData.giftByName(name);
			if (g != null) {
				addGift(g, false);
			}
		}
		if (type == NodeType.HIDDEN) {
			// A hidden boss: everyone back on their feet and whole.
			for (UUID id : List.copyOf(downed)) {
				revive(id);
			}
			for (Player p : players()) {
				heal(p, 1.0);
			}
			if (battleFoe != null) {
				hiddenBeaten.add(battleFoe);
			}
			backToMap();
			return;
		}
		if (type == NodeType.BOSS) {
			floorsCleared = floor;
			if (floorHard) {
				hardFloorsCleared++;
			}
			phase = Phase.REWARD;
			bossReward();
			return;
		}
		int chance = switch (type) {
			case NORMAL -> 5;
			case RISKY -> 35;
			case FOCUSED, ABNO -> 70;
			default -> 0;
		};
		if (eventGifts.isEmpty() && random.nextInt(100) < chance) {
			List<MdData.Gift> offer = MdShop.roll(this, 1, null, false);
			if (!offer.isEmpty()) {
				phase = Phase.REWARD;
				offerGift(offer.get(0));
				return;
			}
		}
		backToMap();
	}

	private void offerGift(MdData.Gift gift) {
		Player viewer = leaderPlayer();
		MdGui gui = new MdGui(3, "E.G.O 기프트 획득");
		gui.set(13, MdShop.icon(this, gift, 0, "클릭해서 받기"), () -> {
			addGift(gift, false);
			viewer.closeInventory();
			backToMap();
		});
		gui.set(22, MdGui.item(Material.BARRIER, "거절하기", NamedTextColor.RED), () -> {
			viewer.closeInventory();
			backToMap();
		});
		gui.fill().open(viewer);
	}

	/** After a floor boss: E.G.O Gifts (Normal 1 of 3, Hard 2 of 4) and Mounting Trials (1, Hard 2, of 3). */
	private void bossReward() {
		int offerCount = floorHard ? 4 : 3;
		int picks = floorHard ? 2 : 1;
		List<MdData.Gift> offer = new ArrayList<>();
		if (theme != null) {
			for (String u : theme.unique()) {
				MdData.Gift g = MdData.giftByName(u);
				if (g != null && !gifts.containsKey(g.id()) && offer.size() < 1) {
					offer.add(g);
				}
			}
		}
		for (MdData.Gift g : MdShop.roll(this, offerCount, theme == null ? null : theme.keyword(), true)) {
			if (offer.size() < offerCount && offer.stream().noneMatch(o -> o.id().equals(g.id()))) {
				offer.add(g);
			}
		}
		// Mounting Trials: Hard's for floors 1-4, the INFINITY MIRROR's for 5-9, the EXTREME MIRROR's for 10-14.
		List<MdData.Trial> pool = new ArrayList<>(MdData.trials(Math.min(MAX_FLOORS - 1, floor)));
		java.util.Collections.shuffle(pool, random);
		List<MdData.Trial> trialOffer = floor >= MAX_FLOORS || floor >= goal && !canExtend() ? List.of() : pool.subList(0, Math.min(3, pool.size()));
		Set<String> chosenGifts = new HashSet<>();
		Set<MdData.Trial> chosenTrials = new HashSet<>();
		// From floor 5 on a trial is a whole tier of the enemy's strength: one a floor.
		openBossReward(offer, picks, trialOffer, floorHard && floor < FLOORS ? 2 : 1, chosenGifts, chosenTrials);
	}

	/** On clearing floor 5 or 10 on Hard the party may go on: to floor 10 (INFINITY MIRROR), then 15 (EXTREME MIRROR). */
	boolean canExtend() {
		return floor == goal && goal < MAX_FLOORS && floorHard;
	}

	/** After floors 10-14: the floor's adversities, as many as the leader takes (or none), then on. */
	private void openAdversities(Set<MdAdversity> chosen) {
		Player viewer = leaderPlayer();
		List<MdAdversity> list = MdAdversity.of(floor);
		if (viewer == null || list.isEmpty()) {
			nextFloor();
			return;
		}
		int now = adversityScore();
		int add = chosen.stream().mapToInt(a -> a.score).sum();
		MdGui gui = new MdGui(4, "역경 (제약) · 점수 " + now + " + " + add);
		for (int i = 0; i < list.size(); i++) {
			MdAdversity a = list.get(i);
			ItemStack icon = MdGui.item(Material.WITHER_ROSE, a.ko + " (+" + a.score + "점)", NamedTextColor.DARK_RED, a.text, "남은 층 내내 적용돼요",
					chosen.contains(a) ? "선택됨 (다시 누르면 취소)" : "클릭해서 받기");
			if (chosen.contains(a)) {
				MdGui.glow(icon);
			}
			gui.set(10 + i + (i >= 7 ? 2 : 0), icon, () -> {
				if (!chosen.remove(a)) {
					chosen.add(a);
				}
				openAdversities(chosen);
			});
		}
		gui.set(31, MdGui.item(Material.LIME_CONCRETE, "다음 층으로", NamedTextColor.GREEN, "고르지 않아도 돼요",
				"판이 끝나면 점수 1당 별빛 " + MdAdversity.STARLIGHT_PER_SCORE + " (지금 " + (now + add) + "점)"), () -> {
					adversities.addAll(chosen);
					for (MdAdversity a : chosen) {
						broadcast(Component.text("역경: " + a.ko + " - " + a.text, NamedTextColor.DARK_RED));
					}
					viewer.closeInventory();
					nextFloor();
				});
		gui.fill().open(viewer);
	}

	private void openBossReward(List<MdData.Gift> offer, int picks, List<MdData.Trial> trialOffer, int trialPicks, Set<String> chosen,
			Set<MdData.Trial> chosenTrials) {
		Player viewer = leaderPlayer();
		MdGui gui = new MdGui(5, floor + "층 클리어 보상");
		for (int i = 0; i < offer.size(); i++) {
			MdData.Gift g = offer.get(i);
			ItemStack icon = MdShop.icon(this, g, 0, chosen.contains(g.id()) ? "선택됨 (다시 누르면 취소)" : "클릭해서 선택 (" + picks + "개까지)");
			if (chosen.contains(g.id())) {
				MdGui.glow(icon);
			}
			gui.set(10 + i * 2, icon, () -> {
				if (!chosen.remove(g.id()) && chosen.size() < picks) {
					chosen.add(g.id());
				}
				openBossReward(offer, picks, trialOffer, trialPicks, chosen, chosenTrials);
			});
		}
		for (int i = 0; i < trialOffer.size(); i++) {
			MdData.Trial t = trialOffer.get(i);
			ItemStack icon = MdGui.item(Material.REDSTONE_TORCH, "등반 시련: " + t.name(), NamedTextColor.RED, t.text(), "다음 층부터 적이 강해져요",
					chosenTrials.contains(t) ? "선택됨" : "클릭해서 선택 (" + trialPicks + "개)");
			if (chosenTrials.contains(t)) {
				MdGui.glow(icon);
			}
			gui.set(29 + i * 2, icon, () -> {
				if (!chosenTrials.remove(t) && chosenTrials.size() < trialPicks) {
					chosenTrials.add(t);
				}
				openBossReward(offer, picks, trialOffer, trialPicks, chosen, chosenTrials);
			});
		}
		boolean extend = canExtend();
		if (extend) {
			gui.set(42, MdGui.item(Material.GOLD_BLOCK, "여기서 끝내기 (던전 클리어)", NamedTextColor.GOLD, floor + "층까지의 보상을 받고 나가요"), () -> {
				for (String id : chosen) {
					addGift(MdData.gift(id), false);
				}
				viewer.closeInventory();
				finish(true);
			});
		}
		String onward = extend ? (floor == FLOORS ? "무한 거울로 (6~10층)" : "익스트림 거울로 (11~15층)") : floor >= goal ? "던전 클리어!" : "다음 층으로";
		gui.set(extend ? 38 : 40, MdGui.item(extend ? Material.CRYING_OBSIDIAN : Material.LIME_CONCRETE, onward, extend ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.GREEN,
				"기프트를 안 고르면 시련이 무작위로 정해져요", extend ? "적이 훨씬 강해져요. 6층부터는 언제나 HARD예요" : ""), () -> {
					if (canExtend()) {
						goal += FLOORS;
						broadcast(Component.text(goal == 10 ? "무한 거울: 10층까지 이어 가요." : "익스트림 거울: 15층까지 이어 가요. 층을 깰 때마다 역경(제약)을 고를 수 있어요.",
								NamedTextColor.LIGHT_PURPLE));
					}
					for (String id : chosen) {
						addGift(MdData.gift(id), false);
					}
					List<MdData.Trial> picked = new ArrayList<>(chosenTrials);
					List<MdData.Trial> rest = new ArrayList<>(trialOffer);
					rest.removeAll(picked);
					java.util.Collections.shuffle(rest, random);
					while (picked.size() < Math.min(trialPicks, trialOffer.size())) {
						picked.add(rest.remove(0));
					}
					trials.addAll(picked);
					viewer.closeInventory();
					if (floor >= 10 && floor < goal) {
						openAdversities(new HashSet<>());
					} else {
						nextFloor();
					}
				});
		gui.fill().open(viewer);
	}

	// --- gifts and people ---------------------------------------------------------------------------

	void addGift(MdData.Gift gift, boolean fuseOnlyGift) {
		if (gift == null) {
			return;
		}
		if (gifts.containsKey(gift.id())) {
			// A duplicate becomes a gift for fusing or selling only (as since the Mirror of the Dreaming).
			fuseOnly.add(gift.id());
			broadcast(Component.text("이미 가진 기프트라 합성·판매 전용이 됐어요: " + gift.name(), NamedTextColor.GRAY));
			return;
		}
		gifts.put(gift.id(), 0);
		if (fuseOnlyGift) {
			fuseOnly.add(gift.id());
		}
		for (Player p : players()) {
			p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6F, 1.6F);
		}
		broadcast(Component.text("E.G.O 기프트 획득: ", NamedTextColor.GRAY).append(MdShop.name(gift)));
		MdGifts.changed(this);
		updateBar();
	}

	void removeGift(String id) {
		gifts.remove(id);
		fuseOnly.remove(id);
		MdGifts.changed(this);
		updateBar();
	}

	List<Player> players() {
		List<Player> out = new ArrayList<>();
		for (UUID id : members) {
			Player p = Bukkit.getPlayer(id);
			if (p != null && p.isOnline()) {
				out.add(p);
			}
		}
		return out;
	}

	/** Members standing (not downed). */
	List<Player> fighters() {
		return players().stream().filter(p -> !downed.contains(p.getUniqueId())).toList();
	}

	Player leaderPlayer() {
		Player p = leader == null ? null : Bukkit.getPlayer(leader);
		if (p == null || !p.isOnline()) {
			List<Player> online = players();
			if (online.isEmpty()) {
				return null;
			}
			leader = online.get(0).getUniqueId();
			p = online.get(0);
			broadcast(Component.text(p.getName() + "님이 리더가 됐어요.", NamedTextColor.YELLOW));
		}
		return p;
	}

	void broadcast(Component message) {
		Component prefix = Component.text("[거울 던전] ", NamedTextColor.DARK_RED);
		for (Player p : players()) {
			p.sendMessage(prefix.append(message));
		}
	}

	void heal(Player p, double fraction) {
		var max = p.getAttribute(Attribute.MAX_HEALTH);
		double m = max == null ? 20 : max.getValue();
		p.setHealth(Math.min(m, p.getHealth() + m * fraction));
		p.setFireTicks(0);
	}

	void down(Player p) {
		downed.add(p.getUniqueId());
		broadcast(Component.text(p.getName() + "님이 쓰러졌어요. 층을 옮기거나 상점에서 부활할 수 있어요.", NamedTextColor.RED));
		if (fighters().isEmpty()) {
			Bukkit.getScheduler().runTask(IndexPrescriptPlugin.instance(), () -> finish(false));
		}
	}

	void revive(UUID id) {
		downed.remove(id);
		Player p = Bukkit.getPlayer(id);
		if (p != null) {
			p.setGameMode(GameMode.ADVENTURE);
			heal(p, 1.0);
			if (MdWorld.in(p)) {
				p.teleport(MdWorld.entrance(slot, MdWorld.Room.START));
			}
		}
	}

	void updateBar() {
		if (bar == null) {
			return;
		}
		String title = "거울 던전 · " + (floor == 0 ? "준비" : floor + "층") + (theme != null ? " · " + theme.name() : "") + " · 코스트 " + cost
				+ " · 기프트 " + gifts.size() + (floorHard ? " · HARD" : "");
		bar.setTitle(title);
		bar.setColor(floorHard ? BarColor.RED : BarColor.BLUE);
		bar.setProgress(Math.max(0, Math.min(1, floor / (double) goal)));
		for (Player p : players()) {
			if (!bar.getPlayers().contains(p)) {
				bar.addPlayer(p);
			}
		}
	}

	/** Reopens whatever screen this run is on, for {@code p}. */
	void reopen(Player p) {
		switch (phase) {
			case GRACE -> MdGrace.open(this, p);
			case START_GIFT, SEARCH -> {
				if (p.getUniqueId().equals(leader)) {
					MdGrace.startGift(this, p);
				}
			}
			case THEME -> openTheme(p);
			case MAP -> openMap(p);
			case SHOP -> {
				if (shop != null) {
					shop.open(p);
				}
			}
			case EVENT -> MdEvent.reopen(this, p);
			default -> MdShop.showGifts(this, p, null);
		}
	}

	// --- leaving and ending ------------------------------------------------------------------------

	/** A member leaves (or quits): they go home; the run ends when nobody is left. */
	void leave(Player p) {
		UUID id = p.getUniqueId();
		if (!members.contains(id)) {
			return;
		}
		if (battle != null && battle.type == NodeType.HIDDEN && !downed.contains(id) && p.isOnline()) {
			p.sendMessage(Component.text("숨은 보스와의 전투는 포기할 수 없어요.", NamedTextColor.RED));
			return;
		}
		members.remove(id);
		BY_PLAYER.remove(id);
		downed.remove(id);
		sendHome(p);
		settle(p, false);
		if (bar != null) {
			bar.removePlayer(p);
		}
		broadcast(Component.text(p.getName() + "님이 던전을 나갔어요.", NamedTextColor.GRAY));
		if (members.isEmpty()) {
			close();
		} else if (fighters().isEmpty() && phase == Phase.BATTLE) {
			finish(false);
		}
	}

	/** The run ends: everyone home, Starlight and weekly rewards for the floors cleared. */
	void finish(boolean cleared) {
		if (phase == Phase.END) {
			return;
		}
		phase = Phase.END;
		if (battle != null) {
			battle.stop();
			battle = null;
		}
		for (Player p : players()) {
			BY_PLAYER.remove(p.getUniqueId());
			p.closeInventory();
			p.showTitle(Title.title(Component.text(cleared ? "거울 던전 클리어" : "거울 던전 실패", cleared ? NamedTextColor.GOLD : NamedTextColor.RED),
					Component.text(floorsCleared + "층까지 클리어", NamedTextColor.GRAY)));
			sendHome(p);
			settle(p, true);
		}
		for (UUID id : members) {
			BY_PLAYER.remove(id);
		}
		members.clear();
		close();
	}

	/** Starlight for the floors cleared (and the unspent bonus), the weekly claim, records. */
	private void settle(Player p, boolean finished) {
		UUID id = p.getUniqueId();
		int starlight = bonus.getOrDefault(id, 0);
		for (int f = 1; f <= floorsCleared; f++) {
			starlight += Math.min(5, f);
		}
		for (int f = 1; f <= hardFloorsCleared; f++) {
			starlight += Math.min(20, 4 * f);
		}
		// The INFINITY and EXTREME floors, and the adversities taken.
		for (int f = FLOORS + 1; f <= floorsCleared; f++) {
			starlight += f > 10 ? 30 : 15;
		}
		if (floorsCleared >= 10) {
			starlight += adversityScore() * MdAdversity.STARLIGHT_PER_SCORE;
		}
		bonus.put(id, 0);
		if (starlight > 0) {
			MdMeta.addStarlight(id, starlight);
			p.sendMessage(Component.text("별빛 +" + starlight + " (보유 " + MdMeta.starlight(id) + ")", NamedTextColor.AQUA));
		}
		if (floorsCleared > 0) {
			MdMeta.claim(p, floorsCleared, hardFloorsCleared > 0);
		}
		MdMeta.record(id, floorsCleared);
	}

	void sendHome(Player p) {
		UUID id = p.getUniqueId();
		Location back = returnTo.getOrDefault(id, Bukkit.getWorlds().get(0).getSpawnLocation());
		p.setGameMode(modes.getOrDefault(id, GameMode.SURVIVAL));
		for (PotionEffect effect : p.getActivePotionEffects()) {
			if (effect.getType() == PotionEffectType.SPEED || effect.getType() == PotionEffectType.SLOWNESS
					|| effect.getType() == PotionEffectType.WEAKNESS) {
				p.removePotionEffect(effect.getType());
			}
		}
		CombatState.of(p).setMaxCharge(CombatState.DEFAULT_MAX_CHARGE);
		AttributeInstance maxHealth = p.getAttribute(Attribute.MAX_HEALTH);
		if (maxHealth != null) {
			maxHealth.removeModifier(FRAIL);
		}
		heal(p, 1.0);
		if (bar != null) {
			bar.removePlayer(p);
		}
		p.teleport(back);
	}

	private void close() {
		if (battle != null) {
			battle.stop();
		}
		if (bar != null) {
			bar.removeAll();
		}
		RUNS.remove(this);
		SLOTS.remove(slot);
		phase = Phase.END;
	}

	/**
	 * An admin preview: builds Theme Pack {@code id}'s battle room on plot 0 and
	 * spawns one wave of its enemies (removed after a minute). Returns where it is.
	 */
	static Location preview(String id, MdRun.NodeType type) {
		MdData.Theme t = MdData.theme(id);
		if (t == null) {
			return null;
		}
		MdRun run = new MdRun(0);
		run.theme = t;
		run.floor = t.normal() != null ? t.normal()[0] : t.hard() != null ? t.hard()[0] : 1;
		MdWorld.Room room = type == NodeType.BOSS ? MdWorld.Room.BOSS : MdWorld.Room.BATTLE;
		MdWorld.build(0, room, t.color(), type == NodeType.BOSS ? t.boss() : t.name());
		String[] hidden = type == NodeType.HIDDEN ? HIDDEN_BOSSES[run.random.nextInt(HIDDEN_BOSSES.length)] : null;
		MdBattle battle = new MdBattle(run, type, hidden == null ? null : hidden[0], List.of());
		if (hidden != null) {
			battle.ally = hidden[1];
			run.floor = 11;
		}
		battle.start();
		Bukkit.getScheduler().runTaskLater(IndexPrescriptPlugin.instance(), battle::stop, 1200L);
		return MdWorld.entrance(0, room);
	}

	static void stopAll() {
		for (MdRun run : List.copyOf(RUNS)) {
			run.finish(false);
		}
	}

	static void tickAll() {
		MdLooks.tick();
		for (MdRun run : List.copyOf(RUNS)) {
			if (run.battle != null) {
				run.battle.tick();
			}
			// Nobody falls out of the dungeon.
			for (Player p : run.players()) {
				if (MdWorld.in(p) && MdWorld.fell(p.getLocation())) {
					p.teleport(MdWorld.entrance(run.slot, run.battle != null ? run.battle.room() : MdWorld.Room.START));
				}
			}
		}
	}

	/** A random number source for effects. */
	static ThreadLocalRandom rng() {
		return ThreadLocalRandom.current();
	}
}
