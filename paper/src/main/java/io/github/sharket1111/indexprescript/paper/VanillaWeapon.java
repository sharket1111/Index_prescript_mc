package io.github.sharket1111.indexprescript.paper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

/**
 * The game's own swords, spears and axes: right-click lays out three skills on the hotbar. The material sets
 * how strong they are (wood up to netherite); the weapon's level and Uptie (Upgrade) work as on any weapon,
 * and each Uptie step strengthens the set.
 */
final class VanillaWeapon implements Listener {
	private static final int[] WAIT = {3 * 20, 5 * 20, 8 * 20};
	private static final Map<UUID, long[]> READY = new ConcurrentHashMap<>();

	enum Kind {
		SWORD("검"), SPEAR("창"), AXE("도끼");

		final String ko;

		Kind(String ko) {
			this.ko = ko;
		}
	}

	private static final String[] TIER_PREFIX = {"WOODEN", "STONE", "COPPER", "GOLDEN", "IRON", "DIAMOND", "NETHERITE"};
	private static final String[] TIER_KO = {"나무", "돌", "구리", "금", "철", "다이아몬드", "네더라이트"};
	private static final int[] TIER_RANK = {0, 1, 2, 2, 3, 4, 5};

	static Kind kindOf(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return null;
		}
		String n = stack.getType().name();
		if (n.endsWith("_SWORD")) {
			return Kind.SWORD;
		}
		if (n.endsWith("_SPEAR")) {
			return Kind.SPEAR;
		}
		if (n.endsWith("_AXE") && !n.endsWith("_PICKAXE")) {
			return Kind.AXE;
		}
		return null;
	}

	static boolean is(ItemStack stack) {
		return kindOf(stack) != null && tierIndex(stack.getType()) >= 0;
	}

	private static int tierIndex(Material m) {
		String n = m.name();
		for (int i = 0; i < TIER_PREFIX.length; i++) {
			if (n.startsWith(TIER_PREFIX[i] + "_")) {
				return i;
			}
		}
		return -1;
	}

	/** 0 (wood) to 5 (netherite). */
	static int rank(ItemStack stack) {
		int i = tierIndex(stack.getType());
		return i < 0 ? 0 : TIER_RANK[i];
	}

	static String tierKo(ItemStack stack) {
		int i = tierIndex(stack.getType());
		return i < 0 ? "" : TIER_KO[i];
	}

	// --- the skill sets ---------------------------------------------------------------------------------

	/** One coin effect: when, what, keyword or buff, potency or amount, count, who, coin (-1: the whole skill). */
	private record Fx(String when, String act, String kw, int potency, int count, String to, int coin) {
	}

	private record Base(String name, String type, String sin, int power, int coinPower, int coins, int weight, List<Fx> fx) {
	}

	private static List<Base> base(Kind kind) {
		return switch (kind) {
			case SWORD -> List.of(
					new Base("베기", "slash", "wrath", 3, 3, 2, 1, List.of(new Fx("hit", "inflict", "bleed", 1, 0, "target", 0))),
					new Base("연속 베기", "slash", "lust", 3, 2, 3, 1, List.of(new Fx("heads", "inflict", "bleed", 1, 0, "target", 1),
							new Fx("heads", "gain", "poise", 1, 1, "self", 2))),
					new Base("일섬", "slash", "wrath", 5, 4, 2, 2, List.of(new Fx("hit", "inflict", "bleed", 2, 0, "target", 0),
							new Fx("heads", "damage", "", 10, 0, "target", 1))));
			case SPEAR -> List.of(
					new Base("찌르기", "pierce", "pride", 4, 2, 2, 1, List.of(new Fx("heads", "inflict", "rupture", 1, 0, "target", 0))),
					new Base("돌진 찌르기", "pierce", "gloom", 3, 4, 2, 1, List.of(new Fx("use", "buff", "haste", 1, 0, "self", -1),
							new Fx("hit", "inflict", "rupture", 1, 0, "target", 1))),
					new Base("꿰뚫기", "pierce", "pride", 5, 5, 2, 3, List.of(new Fx("hit", "inflict", "rupture", 2, 0, "target", 0),
							new Fx("hit", "inflict", "rupture", 0, 1, "target", 1))));
			case AXE -> List.of(
					new Base("내려찍기", "blunt", "gluttony", 4, 3, 1, 1, List.of(new Fx("hit", "inflict", "tremor", 2, 0, "target", 0))),
					new Base("휘두르기", "slash", "sloth", 3, 3, 2, 2, List.of(new Fx("hit", "inflict", "bleed", 1, 0, "target", 0),
							new Fx("hit", "inflict", "tremor", 1, 0, "target", 1))),
					new Base("쪼개기", "blunt", "gluttony", 6, 5, 2, 1, List.of(new Fx("hit", "inflict", "tremor", 2, 0, "target", 0),
							new Fx("hit", "buff", "defense_level", 1, 0, "self", 1))));
		};
	}

	/** Uptie 3 adds one effect to Skill 3. */
	private static Fx uptie3(Kind kind) {
		return switch (kind) {
			case SWORD -> new Fx("heads", "gain", "poise", 2, 1, "self", 0);
			case SPEAR -> new Fx("heads", "inflict", "rupture", 0, 1, "target", 0);
			case AXE -> new Fx("hit", "burst", "", 0, 0, "target", 1);
		};
	}

	/**
	 * The three skills at this material rank (0-5) and Uptie (1-4). Rank adds power, coin power and potency
	 * (diamond and netherite give Skill 3 a coin more); Uptie 2 adds potency, 3 coin power and an effect on
	 * Skill 3, 4 base power and a coin on Skill 2.
	 */
	static List<Ego.Skill> skills(Kind kind, int rank, int uptie) {
		List<Ego.Skill> out = new ArrayList<>();
		List<Base> set = base(kind);
		for (int i = 0; i < set.size(); i++) {
			Base b = set.get(i);
			int power = b.power() + rank / 2 + (uptie >= 4 ? 1 : 0);
			int coinPower = b.coinPower() + (rank + 1) / 2 + (uptie >= 3 ? 1 : 0);
			int coins = b.coins() + (i == 2 && rank >= 4 ? 1 : 0) + (i == 1 && uptie >= 4 ? 1 : 0);
			List<Fx> fx = new ArrayList<>(b.fx());
			if (i == 2 && uptie >= 3) {
				fx.add(uptie3(kind));
			}
			int potencyBonus = rank / 2 + (uptie >= 2 ? 1 : 0);
			JsonArray effects = new JsonArray();
			List<String> text = new ArrayList<>();
			for (Fx f : fx) {
				int coin = Math.min(f.coin(), coins - 1);
				int potency = f.potency() > 0 ? f.potency() + (f.act().equals("damage") ? potencyBonus * 5 : f.act().equals("buff") ? 0 : potencyBonus) : 0;
				int count = f.count() > 0 && f.potency() == 0 ? f.count() + (uptie >= 2 ? 1 : 0) : f.count();
				effects.add(json(f, coin, potency, count));
				text.add(textOf(f, coin, potency, count));
			}
			out.add(new Ego.Skill(b.type(), b.sin(), power, coinPower, coins, b.weight(), text, effects, b.name()));
		}
		return out;
	}

	private static JsonObject json(Fx f, int coin, int potency, int count) {
		JsonObject o = new JsonObject();
		o.addProperty("when", f.when());
		o.addProperty("do", f.act());
		o.addProperty("coin", coin);
		o.addProperty("to", f.to());
		switch (f.act()) {
			case "inflict", "gain" -> {
				o.addProperty("kw", f.kw());
				o.addProperty("potency", potency);
				o.addProperty("count", count);
			}
			case "buff" -> {
				o.addProperty("what", f.kw());
				o.addProperty("amount", potency);
			}
			case "damage" -> o.addProperty("pct", potency);
			default -> {
			}
		}
		return o;
	}

	private static String textOf(Fx f, int coin, int potency, int count) {
		String when = switch (f.when()) {
			case "heads" -> "[앞면 적중시] ";
			case "use" -> "[사용시] ";
			case "clash_win" -> "[합 승리시] ";
			default -> "[적중시] ";
		};
		String kw = switch (f.kw()) {
			case "bleed" -> "출혈";
			case "rupture" -> "파열";
			case "tremor" -> "진동";
			case "poise" -> "호흡";
			case "haste" -> "신속";
			case "defense_level" -> "방어 레벨 증가";
			default -> f.kw();
		};
		String what = switch (f.act()) {
			case "inflict" -> potency > 0 ? kw + " " + potency + " 부여" : kw + " 횟수 " + count + " 부여";
			case "gain" -> kw + " " + potency + (count > 1 ? " (횟수 " + count + ")" : "") + " 얻음";
			case "buff" -> kw + " " + potency + " 얻음";
			case "damage" -> "피해량 +" + potency + "%";
			case "burst" -> "진동 폭발";
			default -> f.act();
		};
		return (coin < 0 ? "" : "코인 " + (coin + 1) + ": ") + when + what;
	}

	/**
	 * The Defense skill: a sword counters (Bleed on the counter), a spear evades (Poise on a dodge), an axe
	 * guards (Tremor on whoever strikes the guard, three times a turn). Material and Uptie raise it like the
	 * other skills.
	 */
	static Defense.Spec defense(Kind kind, int rank, int uptie) {
		int up2 = uptie >= 2 ? 1 : 0;
		int power = rank / 2 + (uptie >= 4 ? 1 : 0);
		int coinPower = (rank + 1) / 2 + (uptie >= 3 ? 1 : 0);
		return switch (kind) {
			case SWORD -> new Defense.Spec("받아치기", Defense.Type.COUNTER, "wrath", 4 + power, 3 + coinPower, 1,
					List.of("[적중시] 출혈 " + (1 + rank / 2 + up2) + " 부여"),
					Defense.fxs(Defense.fx("hit", "inflict", "bleed", 1 + rank / 2 + up2, 0)));
			case SPEAR -> new Defense.Spec("물러서기", Defense.Type.EVADE, "pride", 4 + power, 4 + coinPower, 1,
					List.of("[회피시] 호흡 " + (1 + up2) + " 얻음"),
					Defense.fxs(Defense.fx("evade", "gain", "poise", 1 + up2, 0)));
			case AXE -> {
				JsonObject tremor = Defense.fx("struck", "inflict", "tremor", 1 + rank / 2 + up2, 0);
				tremor.addProperty("limit", 3);
				yield new Defense.Spec("자루 막기", Defense.Type.GUARD, "gluttony", 6 + power, 3 + coinPower, 1,
						List.of("[방어 중 맞으면] 공격한 대상에게 진동 " + (1 + rank / 2 + up2) + " 부여 (턴당 3회)"), Defense.fxs(tremor));
			}
		};
	}

	/** The info lines of a sword, spear or axe: material, level, Uptie and the skill set. */
	static List<Component> lore(ItemStack stack) {
		Kind kind = kindOf(stack);
		List<Component> lore = new ArrayList<>();
		int level = Upgrade.level(stack);
		int uptie = Upgrade.uptie(stack);
		lore.add(SkillMenu.line(tierKo(stack) + " " + kind.ko + " · Lv." + level
				+ (level < Upgrade.MAX_LEVEL ? " (EXP " + Upgrade.exp(stack) + "/" + Upgrade.need(level) + ")" : " (최대)") + " · 동기화 " + uptie,
				NamedTextColor.GOLD));
		int n = 1;
		for (Ego.Skill s : skills(kind, rank(stack), uptie)) {
			lore.add(SkillMenu.line(n++ + ". " + s.name() + "  위력 " + s.power() + " · 코인 +" + s.coinPower() + " ×" + s.coins(), colour(s)));
		}
		Defense.Spec def = defense(kind, rank(stack), uptie);
		lore.add(SkillMenu.line("4. " + def.name() + " (" + def.type().ko + ")  위력 " + def.power() + " · 코인 +" + def.coinPower() + " ×" + def.coins(),
				NamedTextColor.AQUA));
		lore.add(SkillMenu.line("우클릭(허공·적): 스킬" + (kind == Kind.SPEAR ? " · 웅크리고 우클릭: 원래 돌진" : "") + " · 방패를 들면 웅크리고 우클릭", NamedTextColor.YELLOW));
		return lore;
	}

	private static TextColor colour(Ego.Skill s) {
		return TextColor.color(Ego.SIN_RGB[Math.max(0, Ego.index(s.sin()))]);
	}

	// --- the hotbar ---------------------------------------------------------------------------------------

	private static List<SkillMenu.Entry> entries(Player p) {
		List<SkillMenu.Entry> out = new ArrayList<>();
		ItemStack held = p.getInventory().getItemInMainHand();
		Kind kind = kindOf(held);
		if (kind == null) {
			return out;
		}
		long now = CaduceusListener.now();
		long[] ready = READY.computeIfAbsent(p.getUniqueId(), k -> new long[3]);
		List<Ego.Skill> skills = skills(kind, rank(held), Upgrade.uptie(held));
		String model = "minecraft:" + held.getType().name().toLowerCase(Locale.ROOT);
		for (int i = 0; i < skills.size(); i++) {
			Ego.Skill s = skills.get(i);
			int slot = i;
			TextColor colour = colour(s);
			List<Component> lore = new ArrayList<>();
			lore.add(SkillMenu.line(Ego.typeKo(s.type()) + " · " + Ego.SINS_KO[Math.max(0, Ego.index(s.sin()))] + " · 위력 " + s.power() + " · 코인 +"
					+ s.coinPower() + " ×" + s.coins(), NamedTextColor.GOLD));
			s.text().forEach(t -> lore.add(SkillMenu.line(t, NamedTextColor.GRAY)));
			lore.add(SkillMenu.line("대기 " + WAIT[i] / 20 + "초", NamedTextColor.DARK_GRAY));
			String name = (i + 1) + ". " + s.name();
			if (now < ready[i]) {
				out.add(new SkillMenu.Entry(SkillMenu.icon("minecraft:gray_dye", (int) ((ready[i] - now + 19) / 20), SkillMenu.line(name, NamedTextColor.GRAY),
						lore), null));
				continue;
			}
			out.add(new SkillMenu.Entry(SkillMenu.icon(model, 1, SkillMenu.line(name, colour), lore), () -> {
				if (Ego.EgoSkill.cast(p, s, s.name(), colour, 0)) {
					READY.computeIfAbsent(p.getUniqueId(), k -> new long[3])[slot] = CaduceusListener.now() + WAIT[slot];
				}
			}));
		}
		Defense.Spec def = defense(kind, rank(held), Upgrade.uptie(held));
		TextColor defColour = TextColor.color(Ego.SIN_RGB[Math.max(0, Ego.index(def.sin()))]);
		out.add(Defense.entry(p, 4, def, () -> Defense.use(p, def, defColour, null)));
		return out;
	}

	static void openMenu(Player p) {
		ItemStack held = p.getInventory().getItemInMainHand();
		Kind kind = kindOf(held);
		if (kind == null || Ego.EgoSkill.active(p) || SkillMenu.choosing(p)) {
			return;
		}
		if (Clash.blocked(p)) {
			Hud.message(p, Component.text("합이 진행 중입니다.", NamedTextColor.GRAY));
			return;
		}
		Upgrade.refreshLore(held);
		SkillMenu.open(p, "vanilla", () -> entries(p), Component.text(tierKo(held) + " " + kind.ko + ": 1~3 스킬 · 4 수비 · 9 취소", NamedTextColor.WHITE));
	}

	/** A spear charges while sneaking; with a shield in the off hand, right-click blocks and sneak + right-click is the skill. */
	private static boolean keepsVanilla(Player p, ItemStack held) {
		boolean shield = p.getInventory().getItemInOffHand().getType() == Material.SHIELD;
		return shield ? !p.isSneaking() : kindOf(held) == Kind.SPEAR && p.isSneaking();
	}

	/** Right-click in the air (blocks keep their use: stripping logs); a spear keeps its charge while sneaking. */
	@EventHandler(priority = EventPriority.LOW)
	public void onUse(PlayerInteractEvent event) {
		if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_AIR || !is(event.getItem())) {
			return;
		}
		if (keepsVanilla(event.getPlayer(), event.getItem())) {
			return;
		}
		event.setCancelled(true);
		openMenu(event.getPlayer());
	}

	@EventHandler(priority = EventPriority.LOW)
	public void onUseOn(PlayerInteractEntityEvent event) {
		ItemStack held = event.getPlayer().getInventory().getItemInMainHand();
		if (event.getHand() == EquipmentSlot.HAND && is(held) && !keepsVanilla(event.getPlayer(), held)) {
			event.setCancelled(true);
			openMenu(event.getPlayer());
		}
	}
}
