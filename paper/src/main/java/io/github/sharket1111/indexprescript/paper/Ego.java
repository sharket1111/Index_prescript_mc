package io.github.sharket1111.indexprescript.paper;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;

/**
 * The Sinners' E.G.O (ego/egos.json, from tools/ego_data.py): any of them, by
 * grade, one per grade slot (ZAYIN, TETH, HE, WAW), whoever's it was in the
 * original.
 *
 * E.G.O resources, one count per sin, come from using skills (the skill's
 * sin, one more for a clash won) and show on the right of the action bar.
 * F (swap hands) with a weapon puts the equipped E.G.O on the hotbar; a number
 * key uses one if the resources and the Sanity it costs are there. The E.G.O's
 * Awakening skill then goes through a clash like every skill: its power, coin
 * power and coins are the original's; each coin strikes for its power so far,
 * plays the coin effects the plugin knows (keywords, Tremor Burst, healing,
 * more damage) and the rest is in its text; its passive works while equipped
 * (EgoPassive). Using an E.G.O shows its name and
 * quote, the screen's skill edges, the sin's colours and a close camera.
 */
final class Ego {
	static final String[] SINS = {"wrath", "lust", "sloth", "gluttony", "gloom", "pride", "envy"};
	static final String[] SINS_KO = {"분노", "색욕", "나태", "탐식", "우울", "오만", "질투"};
	static final int[] SIN_RGB = {0xE03C28, 0xF07828, 0xF0C83C, 0x9ACD32, 0x3C8CDC, 0x2850B4, 0x9650C8};
	static final String[] GRADES = {"ZAYIN", "TETH", "HE", "WAW"};
	private static final int MAX_RESOURCE = 10;
	private static final NamespacedKey RESOURCES = new NamespacedKey(Items.NAMESPACE, "ego_resources");

	record Skill(String type, String sin, int power, int coinPower, int coins, int weight, List<String> text, JsonArray effects, String name) {
	}

	record Def(String id, String name, String nameKo, String sinner, String sinnerKo, String risk, String sin, String abnormality, String quote,
			int sanity, Map<String, Integer> costs, Skill skill, List<String> textKo, String passiveName, String passiveText, JsonArray passiveEffects,
			List<String> passiveKo, List<Skill> upties, boolean base) {
		int sinIndex() {
			return index(sin);
		}

		TextColor colour() {
			return TextColor.color(SIN_RGB[Math.max(0, sinIndex())]);
		}

		SkillCard card(Skill at) {
			String desc = String.join(" ", textKo.isEmpty() ? at.text() : textKo);
			return new SkillCard("E.G.O::" + nameKo, colour(), at.power(), at.coinPower(), Math.max(1, at.coins()), desc, EgoSkill.length(at) + 10, true,
					"N".repeat(Math.max(1, at.coins())));
		}

		/** The Awakening skill at Threadspin (Uptie) {@code uptie} (1-4). */
		Skill skill(int uptie) {
			return upties.isEmpty() ? skill : upties.get(Math.max(0, Math.min(upties.size() - 1, uptie - 1)));
		}
	}

	private static final Map<String, Def> DEFS = new LinkedHashMap<>();
	private static final Map<UUID, int[]> HELD = new ConcurrentHashMap<>();

	private Ego() {
	}

	static int index(String sin) {
		for (int i = 0; i < SINS.length; i++) {
			if (SINS[i].equalsIgnoreCase(sin)) {
				return i;
			}
		}
		return -1;
	}

	// --- data --------------------------------------------------------------------------------

	static void load(IndexPrescriptPlugin plugin) {
		DEFS.clear();
		try (InputStream in = plugin.getResource("ego/egos.json")) {
			if (in == null) {
				plugin.getLogger().warning("ego/egos.json missing from the jar");
				return;
			}
			JsonObject root = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
			for (JsonElement e : root.getAsJsonArray("egos")) {
				JsonObject o = e.getAsJsonObject();
				JsonObject s = o.getAsJsonObject("skill");
				List<String> text = new ArrayList<>();
				s.getAsJsonArray("text").forEach(t -> text.add(t.getAsString()));
				Skill skill = skillOf(s);
				List<Skill> upties = new ArrayList<>();
				if (o.has("upties")) {
					o.getAsJsonArray("upties").forEach(u -> upties.add(skillOf(u.getAsJsonObject())));
				}
				Map<String, Integer> costs = new LinkedHashMap<>();
				o.getAsJsonObject("costs").entrySet().forEach(c -> costs.put(c.getKey(), c.getValue().getAsInt()));
				List<String> ko = new ArrayList<>();
				o.getAsJsonArray("text_ko").forEach(t -> ko.add(t.getAsString()));
				JsonObject passive = o.getAsJsonObject("passive");
				List<String> passiveKo = new ArrayList<>();
				if (o.has("passive_ko")) {
					o.getAsJsonArray("passive_ko").forEach(t -> passiveKo.add(t.getAsString()));
				}
				JsonArray passiveEffects = o.has("passive_effects") ? o.getAsJsonArray("passive_effects") : new JsonArray();
				Def def = new Def(o.get("id").getAsString(), o.get("name").getAsString(), o.get("name_ko").getAsString(), o.get("sinner").getAsString(),
						o.get("sinner_ko").getAsString(), o.get("risk").getAsString(), o.get("sin").getAsString(), str(o, "abnormality"), str(o, "quote"),
						o.get("sanity").getAsInt(), costs, skill, ko, str(passive, "name"), str(passive, "text"), passiveEffects, passiveKo, upties,
						o.has("base") && o.get("base").getAsBoolean());
				DEFS.put(def.id(), def);
			}
			plugin.getLogger().info("E.G.O: " + DEFS.size());
		} catch (java.io.IOException | RuntimeException e) {
			plugin.getLogger().log(Level.WARNING, "Could not read ego/egos.json", e);
		}
	}

	/** A skill in the data's shape (E.G.O and the gacha's weapons alike). */
	static Skill skillOf(JsonObject s) {
		List<String> text = new ArrayList<>();
		s.getAsJsonArray("text").forEach(t -> text.add(t.getAsString()));
		return new Skill(s.get("type").getAsString(), s.get("sin").getAsString(), s.get("power").getAsInt(), s.get("coin_power").getAsInt(),
				s.get("coins").getAsInt(), s.get("weight").getAsInt(), text, s.getAsJsonArray("effects"), s.has("name") ? s.get("name").getAsString() : "");
	}

	static List<Def> all() {
		return List.copyOf(DEFS.values());
	}

	// --- owning and threadspinning -----------------------------------------------------------

	private static final NamespacedKey OWNED = new NamespacedKey(Items.NAMESPACE, "ego_owned");
	private static final NamespacedKey UPTIES = new NamespacedKey(Items.NAMESPACE, "ego_upties");

	/** Whether {@code player} has this E.G.O: the Sinners' base E.G.O from the start, the rest from Extraction. */
	static boolean owns(Player player, Def def) {
		if (def.base()) {
			return true;
		}
		String owned = player.getPersistentDataContainer().getOrDefault(OWNED, PersistentDataType.STRING, "");
		return ("," + owned + ",").contains("," + def.id() + ",");
	}

	static void give(Player player, Def def) {
		if (owns(player, def)) {
			return;
		}
		String owned = player.getPersistentDataContainer().getOrDefault(OWNED, PersistentDataType.STRING, "");
		player.getPersistentDataContainer().set(OWNED, PersistentDataType.STRING, owned.isEmpty() ? def.id() : owned + "," + def.id());
	}

	/** The E.G.O's Threadspin (1-4). */
	static int uptie(Player player, Def def) {
		String all = player.getPersistentDataContainer().getOrDefault(UPTIES, PersistentDataType.STRING, "");
		for (String part : all.split(",")) {
			if (part.startsWith(def.id() + ":")) {
				try {
					return Math.max(1, Math.min(4, Integer.parseInt(part.substring(def.id().length() + 1))));
				} catch (NumberFormatException e) {
					return 1;
				}
			}
		}
		return 1;
	}

	static void setUptie(Player player, Def def, int uptie) {
		String all = player.getPersistentDataContainer().getOrDefault(UPTIES, PersistentDataType.STRING, "");
		StringBuilder out = new StringBuilder();
		for (String part : all.split(",")) {
			if (!part.isEmpty() && !part.startsWith(def.id() + ":")) {
				out.append(out.isEmpty() ? "" : ",").append(part);
			}
		}
		out.append(out.isEmpty() ? "" : ",").append(def.id()).append(':').append(uptie);
		player.getPersistentDataContainer().set(UPTIES, PersistentDataType.STRING, out.toString());
	}

	private static String str(JsonObject o, String key) {
		return o != null && o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
	}

	static Def get(String id) {
		return id == null ? null : DEFS.get(id);
	}

	static List<Def> ofGrade(String grade) {
		return DEFS.values().stream().filter(d -> d.risk().equals(grade))
				.sorted(Comparator.comparing(Def::sinnerKo).thenComparing(Def::nameKo)).toList();
	}

	// --- the loadout -------------------------------------------------------------------------

	private static NamespacedKey slotKey(String grade) {
		return new NamespacedKey(Items.NAMESPACE, "ego_" + grade.toLowerCase(Locale.ROOT));
	}

	static Def equipped(Player player, String grade) {
		return get(player.getPersistentDataContainer().get(slotKey(grade), PersistentDataType.STRING));
	}

	static void equip(Player player, String grade, Def def) {
		if (def == null) {
			player.getPersistentDataContainer().remove(slotKey(grade));
		} else {
			player.getPersistentDataContainer().set(slotKey(grade), PersistentDataType.STRING, def.id());
		}
	}

	// --- resources ---------------------------------------------------------------------------

	static int[] resources(Player player) {
		return HELD.computeIfAbsent(player.getUniqueId(), id -> {
			int[] stored = player.getPersistentDataContainer().get(RESOURCES, PersistentDataType.INTEGER_ARRAY);
			return stored != null && stored.length == SINS.length ? stored.clone() : new int[SINS.length];
		});
	}

	private static void save(Player player) {
		player.getPersistentDataContainer().set(RESOURCES, PersistentDataType.INTEGER_ARRAY, resources(player).clone());
	}

	static void gain(Player player, int sin, int amount) {
		if (sin < 0 || amount <= 0) {
			return;
		}
		int[] held = resources(player);
		held[sin] = Math.min(MAX_RESOURCE, held[sin] + amount);
		save(player);
	}

	/** A skill used: one of its sin (the card's colour tells it). E.G.O skills give none. */
	static void used(LivingEntity user, SkillCard card) {
		if (user instanceof Player player && !card.name().startsWith("E.G.O::")) {
			gain(player, sinOf(card.colour()), 1);
		}
	}

	/** A clash won: one more of the skill's sin. */
	static void clashWon(LivingEntity user, SkillCard card) {
		if (user instanceof Player player && card != null && !card.name().startsWith("E.G.O::")) {
			gain(player, sinOf(card.colour()), 1);
		}
	}

	/** The sin whose colour is nearest {@code colour}. */
	static int sinOf(TextColor colour) {
		int best = 0;
		long bestDistance = Long.MAX_VALUE;
		for (int i = 0; i < SIN_RGB.length; i++) {
			int r = (SIN_RGB[i] >> 16 & 0xFF) - colour.red();
			int g = (SIN_RGB[i] >> 8 & 0xFF) - colour.green();
			int b = (SIN_RGB[i] & 0xFF) - colour.blue();
			long d = (long) r * r + (long) g * g + (long) b * b;
			if (d < bestDistance) {
				best = i;
				bestDistance = d;
			}
		}
		return best;
	}

	static boolean affordable(Player player, Def def) {
		int[] held = resources(player);
		for (Map.Entry<String, Integer> cost : def.costs().entrySet()) {
			int i = index(cost.getKey());
			if (i >= 0 && held[i] < cost.getValue()) {
				return false;
			}
		}
		return true;
	}

	private static void pay(Player player, Def def) {
		int[] held = resources(player);
		for (Map.Entry<String, Integer> cost : def.costs().entrySet()) {
			int i = index(cost.getKey());
			if (i >= 0) {
				held[i] = Math.max(0, held[i] - cost.getValue());
			}
		}
		save(player);
	}

	static String costText(Def def) {
		StringBuilder out = new StringBuilder();
		for (Map.Entry<String, Integer> cost : def.costs().entrySet()) {
			int i = index(cost.getKey());
			if (i >= 0) {
				out.append(out.isEmpty() ? "" : " ").append(SINS_KO[i]).append(' ').append(cost.getValue());
			}
		}
		return out.toString();
	}

	/** The right-hand readout of the action bar: each sin's count in its colour. */
	static Component readout(Player player) {
		int[] held = resources(player);
		Component out = Component.empty();
		for (int i = 0; i < SINS.length; i++) {
			out = out.append(Component.text("■", TextColor.color(SIN_RGB[i]))).append(Component.text(held[i] + " ", held[i] > 0 ? NamedTextColor.WHITE
					: NamedTextColor.DARK_GRAY));
		}
		return out;
	}

	static boolean showsReadout(Player player) {
		int[] held = resources(player);
		for (int v : held) {
			if (v > 0) {
				return true;
			}
		}
		return holdsWeapon(player);
	}

	/** Our weapons and the game's own: what F turns into the E.G.O key with. */
	static boolean holdsWeapon(Player player) {
		ItemStack held = player.getInventory().getItemInMainHand();
		if (Items.isAnyWeapon(held)) {
			return true;
		}
		String type = held.getType().name();
		return type.endsWith("_SWORD") || type.endsWith("_AXE") || type.endsWith("_SPEAR") || type.equals("TRIDENT") || type.equals("MACE")
				|| type.equals("BOW") || type.equals("CROSSBOW");
	}

	// --- the hotbar of E.G.O ------------------------------------------------------------------

	static List<Component> lore(Def base, Player viewer) {
		List<Component> lore = new ArrayList<>();
		Def def = base;
		Skill at = viewer == null ? base.skill() : base.skill(uptie(viewer, base));
		lore.add(SkillMenu.line(def.risk() + " · " + def.sinnerKo() + " · " + SINS_KO[Math.max(0, def.sinIndex())] + " · "
				+ typeKo(def.skill().type()), def.colour()));
		lore.add(SkillMenu.line("위력 " + at.power() + " · 코인 " + (at.coinPower() >= 0 ? "+" : "") + at.coinPower() + " ×" + at.coins() + " · 공격 가중치 "
				+ at.weight() + (viewer != null ? " · 동기화 " + uptie(viewer, base) : ""), NamedTextColor.GOLD));
		lore.add(SkillMenu.line("요구 자원: " + costText(def) + " · 정신력 -" + def.sanity(), NamedTextColor.AQUA));
		for (String line : def.textKo().isEmpty() ? def.skill().text() : def.textKo()) {
			for (String l : MdGui.wrap(line, 36)) {
				lore.add(SkillMenu.line(l, NamedTextColor.GRAY));
			}
		}
		if (!def.passiveName().isBlank()) {
			lore.add(SkillMenu.line("패시브 [" + def.passiveName() + "] (장착하면 항상)", NamedTextColor.DARK_AQUA));
			for (String l : def.passiveKo()) {
				for (String w : MdGui.wrap("• " + l, 36)) {
					lore.add(SkillMenu.line(w, NamedTextColor.AQUA));
				}
			}
			for (String l : MdGui.wrap("원작: " + def.passiveText(), 36)) {
				lore.add(SkillMenu.line(l, NamedTextColor.DARK_GRAY));
			}
		}
		if (!def.quote().isBlank()) {
			lore.add(SkillMenu.line("\"" + def.quote() + "\"", NamedTextColor.WHITE));
		}
		if (viewer != null) {
			int[] held = resources(viewer);
			StringBuilder have = new StringBuilder("가진 자원:");
			for (int i = 0; i < SINS.length; i++) {
				have.append(' ').append(SINS_KO[i]).append(held[i]);
			}
			lore.add(SkillMenu.line(have.toString(), NamedTextColor.DARK_GRAY));
		}
		return lore;
	}

	static String typeKo(String type) {
		return switch (type) {
			case "pierce" -> "관통";
			case "blunt" -> "타격";
			default -> "참격";
		};
	}

	static String model(Def def) {
		return switch (def.skill().type()) {
			case "pierce" -> "minecraft:trident";
			case "blunt" -> "minecraft:mace";
			default -> "minecraft:netherite_sword";
		};
	}

	private static List<SkillMenu.Entry> entries(Player player) {
		List<SkillMenu.Entry> out = new ArrayList<>();
		int n = 1;
		for (String grade : GRADES) {
			Def def = equipped(player, grade);
			String label = n++ + ". " + grade;
			if (def == null) {
				out.add(new SkillMenu.Entry(SkillMenu.locked(label + " (비어 있음)", "/메뉴 → E.G.O 장착에서 끼워요", List.of()), null));
				continue;
			}
			String name = label + " " + def.nameKo();
			List<Component> lore = lore(def, player);
			if (!owns(player, def)) {
				out.add(new SkillMenu.Entry(SkillMenu.locked(name, "보유하지 않은 E.G.O예요", lore), null));
			} else if (!affordable(player, def)) {
				out.add(new SkillMenu.Entry(SkillMenu.locked(name, "자원이 모자라요", lore), null));
			} else if (Sanity.of(player) - def.sanity() < Sanity.MIN) {
				out.add(new SkillMenu.Entry(SkillMenu.locked(name, "정신력이 모자라요", lore), null));
			} else {
				ItemStack icon = SkillMenu.icon(model(def), 1, Component.text(name, def.colour()).decoration(TextDecoration.ITALIC, false)
						.decoration(TextDecoration.BOLD, true), lore);
				out.add(new SkillMenu.Entry(icon, () -> EgoSkill.use(player, def)));
			}
		}
		return out;
	}

	static void openHotbar(Player player) {
		if (Clash.blocked(player)) {
			Hud.message(player, Component.text("합이 진행 중입니다.", NamedTextColor.GRAY));
			return;
		}
		if (EgoSkill.active(player)) {
			return;
		}
		SkillMenu.open(player, "ego", () -> entries(player), Component.text("E.G.O: 1~4 사용 · 9 취소", NamedTextColor.LIGHT_PURPLE));
	}

	/** F with a weapon in hand: the E.G.O (other F uses of the weapons are now in their right-click menus). */
	static final class Events implements Listener {
		@EventHandler(priority = EventPriority.HIGHEST)
		public void onSwap(PlayerSwapHandItemsEvent event) {
			Player player = event.getPlayer();
			if (SkillMenu.choosing(player, "ego")) {
				event.setCancelled(true);
				SkillMenu.close(player);
				return;
			}
			if (!holdsWeapon(player) && !SkillMenu.choosing(player) || BlackGod.active(player)) {
				return;
			}
			event.setCancelled(true);
			if (SkillMenu.choosing(player)) {
				SkillMenu.close(player);
			}
			openHotbar(player);
		}

		@EventHandler
		public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
			HELD.remove(event.getPlayer().getUniqueId());
		}
	}

	// --- the E.G.O skill ------------------------------------------------------------------------

	/** An E.G.O's Awakening skill being used. */
	static final class EgoSkill {
		private static final Map<UUID, EgoSkill> ACTIVE = new ConcurrentHashMap<>();
		private static final int WIND_UP = 26;
		private static final int WEAPON_WIND_UP = 8;
		private static final int COIN_GAP = 12;
		private static final double RANGE = 10.0;
		/** Health per point of power a coin strikes for (the original's power, a skill's damage here). */
		private static final double DAMAGE_PER_POWER = 0.3;

		private final Player player;
		private final LivingEntity target;
		private final Skill skill;
		private final String name;
		private final TextColor colour;
		private final String quote;
		private final boolean ego;
		private final Particle.DustOptions main;
		private final Particle.DustOptions pale;
		private Cinematic camera;
		private double cameraSide = Double.NaN;
		private int tick;
		private int heads;

		private EgoSkill(Player player, LivingEntity target, Skill skill, String name, TextColor colour, String quote, boolean ego) {
			this.player = player;
			this.target = target;
			this.skill = skill;
			this.name = name;
			this.colour = colour;
			this.quote = quote;
			this.ego = ego;
			int rgb = SIN_RGB[Math.max(0, index(skill.sin()))];
			this.main = new Particle.DustOptions(Color.fromRGB(rgb), 1.6F);
			this.pale = new Particle.DustOptions(Color.fromRGB(lighten(rgb)), 1.1F);
		}

		private static int lighten(int rgb) {
			int r = Math.min(255, (rgb >> 16 & 0xFF) + 90);
			int g = Math.min(255, (rgb >> 8 & 0xFF) + 90);
			int b = Math.min(255, (rgb & 0xFF) + 90);
			return r << 16 | g << 8 | b;
		}

		static int length(Skill skill) {
			return WIND_UP + Math.max(1, skill.coins()) * COIN_GAP + 24;
		}

		private int windUp() {
			return ego ? WIND_UP : WEAPON_WIND_UP;
		}


		static boolean active(Player player) {
			return ACTIVE.containsKey(player.getUniqueId());
		}

		static void use(Player player, Def def) {
			if (active(player) || Clash.blocked(player)) {
				return;
			}
			if (!affordable(player, def)) {
				Hud.message(player, Component.text("E.G.O 자원이 모자라요: " + costText(def), NamedTextColor.GRAY));
				return;
			}
			LivingEntity target = Clash.answering(player) != null ? Clash.answering(player) : lookedAt(player);
			if (target == null) {
				Hud.message(player, Component.text(Messages.get(player, "renoir.no_target"), NamedTextColor.GRAY));
				return;
			}
			Skill skill = def.skill(uptie(player, def));
			if (Clash.request(player, target, def.card(skill), outcome -> launch(player, outcome.target(), skill, def.nameKo(), def.colour(),
					def.quote().isBlank() ? def.sinnerKo() : "\"" + def.quote() + "\"", true))) {
				pay(player, def);
				Sanity.lose(player, def.sanity());
			}
		}

		/** A weapon's skill (the gacha's weapons): the same strikes and coin effects, without the E.G.O's title. Returns false if it could not start. */
		static boolean cast(Player player, Skill skill, String name, TextColor colour, int clashBonus) {
			if (active(player) || Clash.blocked(player)) {
				return false;
			}
			LivingEntity target = Clash.answering(player) != null ? Clash.answering(player) : lookedAt(player);
			if (target == null) {
				Hud.message(player, Component.text(Messages.get(player, "renoir.no_target"), NamedTextColor.GRAY));
				return false;
			}
			SkillCard card = new SkillCard(name, colour, skill.power() + clashBonus, skill.coinPower(), Math.max(1, skill.coins()),
					String.join(" ", skill.text()), length(skill) + 10, true, "N".repeat(Math.max(1, skill.coins())));
			return Clash.request(player, target, card, outcome -> launch(player, outcome.target(), skill, name, colour, "", false));
		}

		private static void launch(Player player, LivingEntity target, Skill skill, String name, TextColor colour, String quote, boolean ego) {
			if (active(player) || !player.isOnline() || player.isDead()) {
				return;
			}
			EgoSkill run = new EgoSkill(player, target, skill, name, colour, quote, ego);
			ACTIVE.put(player.getUniqueId(), run);
			run.open();
		}

		private static LivingEntity lookedAt(Player player) {
			if (player.getTargetEntity((int) RANGE) instanceof LivingEntity living && !living.isDead() && !Cinematic.isActor(living)) {
				return living;
			}
			Vector eye = player.getEyeLocation().toVector();
			Vector look = player.getEyeLocation().getDirection();
			LivingEntity best = null;
			double bestDot = 0.9;
			for (Entity entity : player.getNearbyEntities(RANGE, RANGE, RANGE)) {
				if (!(entity instanceof LivingEntity living) || living.isDead() || Cinematic.isActor(living)
						|| entity instanceof org.bukkit.entity.ArmorStand stand && stand.isMarker()) {
					continue;
				}
				Vector to = living.getLocation().add(0, living.getHeight() * 0.5, 0).toVector().subtract(eye);
				double dot = to.clone().normalize().dot(look);
				if (to.length() <= RANGE && dot > bestDot && player.hasLineOfSight(living)) {
					best = living;
					bestDot = dot;
				}
			}
			return best;
		}

		static void tickAll() {
			ACTIVE.values().removeIf(skill -> {
				try {
					return !skill.tick();
				} catch (RuntimeException e) {
					IndexPrescriptPlugin.instance().getLogger().log(Level.WARNING, "E.G.O skill failed", e);
					skill.finish();
					return true;
				}
			});
		}

		static void endAll() {
			ACTIVE.values().forEach(EgoSkill::finish);
			ACTIVE.clear();
		}

		private void open() {
			camera = Cinematic.start(player, player.getLocation().add(0, 2, 0));
			SkillOverlay.show(player, ego ? SkillOverlay.Size.BIG : SkillOverlay.Size.SMALL);
			if (ego) {
				TotalDamage.start(player);
				player.showTitle(Title.title(Component.text(name, colour).decorate(TextDecoration.BOLD), Component.text(quote, NamedTextColor.WHITE),
						Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(1500), Duration.ofMillis(400))));
			}
			World world = player.getWorld();
			if (ego) {
				world.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 1.0F, 0.7F);
				world.playSound(player.getLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, SoundCategory.PLAYERS, 0.6F, 1.4F);
				for (Player near : world.getPlayers()) {
					if (near != player && near.getLocation().distanceSquared(player.getLocation()) < 32 * 32) {
						near.sendActionBar(Component.text(player.getName() + " — E.G.O::" + name, colour));
					}
				}
			}
			apply(-1, true);
		}

		private boolean tick() {
			if (!player.isOnline() || player.isDead() || !target.isValid() || target.isDead()) {
				finish();
				return false;
			}
			shoot();
			LivingEntity actor = actor();
			World world = actor.getWorld();
			Location at = actor.getLocation();
			if (tick < windUp()) {
				// The E.G.O wells up: a ring of the sin's colour closing in, a pillar rising.
				double r = 2.6 * (1 - tick / (double) windUp()) + 0.4;
				for (int i = 0; i < 16; i++) {
					double a = Math.PI * 2 * i / 16 + tick * 0.2;
					world.spawnParticle(Particle.DUST, at.clone().add(Math.cos(a) * r, 0.1 + tick * 0.06, Math.sin(a) * r), 1, 0, 0, 0, 0, i % 3 == 0 ? pale : main);
				}
				if (tick % 6 == 0) {
					world.spawnParticle(Particle.END_ROD, at.clone().add(0, 1.2, 0), 4, 0.3, 0.6, 0.3, 0.02);
				}
			}
			int coins = Math.max(1, skill.coins());
			for (int c = 0; c < coins; c++) {
				if (tick == windUp() + c * COIN_GAP - 3) {
					dashBeside(c);
				}
				if (tick == windUp() + c * COIN_GAP) {
					strike(c, c == coins - 1);
				}
			}
			if (tick >= windUp() + Math.max(1, skill.coins()) * COIN_GAP + 24) {
				finish();
				return false;
			}
			tick++;
			return true;
		}

		private void dashBeside(int coin) {
			LivingEntity actor = actor();
			Vector from = actor.getLocation().toVector();
			Vector toward = target.getLocation().toVector().subtract(from).setY(0);
			if (toward.lengthSquared() < 1.0E-4) {
				toward = new Vector(1, 0, 0);
			}
			toward.normalize();
			Vector side = new Vector(-toward.getZ(), 0, toward.getX()).multiply(coin % 2 == 0 ? 1.0 : -1.0);
			Vector spot = target.getLocation().toVector().subtract(toward.clone().multiply(1.7)).add(side);
			Location destination = new Location(target.getWorld(), spot.getX(), target.getLocation().getY(), spot.getZ());
			destination.setDirection(target.getLocation().toVector().subtract(spot).setY(0));
			actor.teleport(destination);
			Effects.dashTrail(target.getWorld(), from, spot);
		}

		private void strike(int coin, boolean last) {
			if (!Clash.coin(player, coin)) {
				return;
			}
			boolean isHeads = Clash.heads(player, coin);
			if (isHeads) {
				heads++;
			}
			if (camera != null) {
				camera.jolt(last ? 1.0 : 0.5);
			}
			LivingEntity actor = actor();
			actor.swingMainHand();
			double power = Math.max(1, skill.power() + skill.coinPower() * heads);
			double bonus = apply(coin, isHeads);
			double damage = power * DAMAGE_PER_POWER * (1 + bonus / 100.0) * IndexMark.multiplier(player, target);
			List<LivingEntity> hit = new ArrayList<>(List.of(target));
			// Attack weight: that many foes around the target take the coin too.
			for (Entity e : target.getNearbyEntities(5, 3, 5)) {
				if (hit.size() >= skill.weight()) {
					break;
				}
				if (e instanceof LivingEntity other && other != player && !(other instanceof Player) && !other.isDead() && !Cinematic.isActor(other)
						&& !(other instanceof org.bukkit.entity.ArmorStand)) {
					hit.add(other);
				}
			}
			for (LivingEntity foe : hit) {
				Damage.deal(foe, foe == target ? damage : damage * 0.7, player, true);
			}
			IndexMark.strike(player, target);
			visuals(coin, last, isHeads);
			if (last) {
				apply(coin, isHeads, "end");
			}
		}

		private double apply(int coin, boolean isHeads) {
			return apply(coin, isHeads, null);
		}

		/** Plays the coin's effects for this moment (the hit, or with {@code moment} "end" the attack's end); returns +damage %. */
		private double apply(int coin, boolean isHeads, String moment) {
			double bonus = 0;
			long now = CaduceusListener.now();
			for (JsonElement el : skill.effects()) {
				JsonObject e = el.getAsJsonObject();
				if (e.get("coin").getAsInt() != coin) {
					continue;
				}
				String when = e.get("when").getAsString();
				boolean fires = moment == null
						? coin < 0 ? when.equals("use") || !when.startsWith("end") : switch (when) {
							case "hit", "clash_win", "kill" -> true;
							case "heads", "crit" -> isHeads;
							case "tails" -> !isHeads;
							default -> false;
						}
						: switch (when) {
							case "end" -> true;
							case "end_heads" -> isHeads;
							default -> false;
						};
				if (!fires) {
					continue;
				}
				String to = e.has("to") ? e.get("to").getAsString() : "target";
				switch (e.get("do").getAsString()) {
					case "inflict" -> {
						for (LivingEntity foe : foes(to)) {
							MdGifts.inflictKeyword(foe, e.get("kw").getAsString(), e.get("potency").getAsInt(), e.get("count").getAsInt());
						}
					}
					case "debuff" -> {
						for (LivingEntity foe : foes(to)) {
							MdBattle.debuff(foe, e.get("what").getAsString(), e.get("amount").getAsInt());
						}
					}
					case "gain" -> {
						String kw = e.get("kw").getAsString();
						if (kw.equals("poise")) {
							CombatState.of(player).addPoise(e.get("potency").getAsInt(), Math.max(1, e.get("count").getAsInt()), now);
						} else if (kw.equals("charge")) {
							CombatState.of(player).addCharge(Math.max(e.get("count").getAsInt(), e.get("potency").getAsInt()), now);
						}
					}
					case "buff" -> MdGifts.buff(player, e.get("what").getAsString(), e.get("amount").getAsInt());
					case "burst" -> Tremor.burst(target);
					case "sp" -> Sanity.add(player, e.get("amount").getAsInt());
					case "heal" -> {
						var max = player.getAttribute(Attribute.MAX_HEALTH);
						double full = max == null ? 20 : max.getValue();
						player.setHealth(Math.min(full, player.getHealth() + full * e.get("pct").getAsInt() / 100.0));
					}
					case "damage" -> bonus += e.get("pct").getAsInt();
					default -> {
					}
				}
			}
			return bonus;
		}

		/** The foes an effect reaches: the target, three at random, or all around it. */
		private List<LivingEntity> foes(String to) {
			if (to.equals("target")) {
				return List.of(target);
			}
			List<LivingEntity> around = new ArrayList<>();
			around.add(target);
			for (Entity e : target.getNearbyEntities(8, 4, 8)) {
				if (e instanceof LivingEntity other && other != player && !(other instanceof Player) && !other.isDead() && !Cinematic.isActor(other)
						&& !(other instanceof org.bukkit.entity.ArmorStand)) {
					around.add(other);
				}
			}
			if (to.equals("random")) {
				java.util.Collections.shuffle(around, ThreadLocalRandom.current());
				return around.subList(0, Math.min(3, around.size()));
			}
			return around;
		}

		private void visuals(int coin, boolean last, boolean isHeads) {
			LivingEntity actor = actor();
			World world = target.getWorld();
			Location mid = target.getLocation().add(0, target.getHeight() * 0.6, 0);
			switch (skill.type()) {
				case "blunt" -> Effects.arc(actor, target, -150, 200, 75);
				case "pierce" -> Effects.slash(actor, target, 0, 3.2);
				default -> Effects.arc(actor, target, coin % 2 == 0 ? -100 : 100, coin % 2 == 0 ? 200 : -200, -10);
			}
			world.spawnParticle(Particle.DUST, mid, last ? 60 : 22, 0.6, 0.6, 0.6, 0.0, main);
			world.spawnParticle(Particle.DUST, mid, last ? 30 : 10, 0.9, 0.9, 0.9, 0.0, pale);
			if (isHeads) {
				Effects.star(target, last ? 1.8 : 1.1);
			}
			world.playSound(mid, Sound.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.PLAYERS, 1.0F, 0.7F + coin * 0.1F);
			if (last) {
				// The finish: a column and a ring of the sin's colour, a flash.
				Location base = target.getLocation();
				for (double y = 0; y < 5; y += 0.25) {
					world.spawnParticle(Particle.DUST, base.clone().add(0, y, 0), 3, 0.25, 0.05, 0.25, 0, y % 0.75 < 0.4 ? main : pale);
				}
				for (int i = 0; i < 40; i++) {
					double a = Math.PI * 2 * i / 40;
					world.spawnParticle(Particle.DUST, base.clone().add(Math.cos(a) * 3, 0.15, Math.sin(a) * 3), 1, 0, 0, 0, 0, main);
				}
				Effects.finalCut(actor, target);
				Effects.flash(target);
				world.spawnParticle(Particle.EXPLOSION, mid, 1);
				world.playSound(mid, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.7F, 1.2F);
				world.playSound(mid, Sound.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 0.9F, 0.6F);
			}
		}

		/** A side view square to the line between the two, pulling in for the last coin. */
		private void shoot() {
			if (camera == null) {
				return;
			}
			Vector from = actor().getLocation().toVector();
			Vector to = target.getLocation().toVector();
			Vector focus = from.clone().midpoint(to).add(new Vector(0, 1.0, 0));
			if (Double.isNaN(cameraSide)) {
				cameraSide = Math.atan2(from.getZ() - to.getZ(), from.getX() - to.getX()) + Math.PI / 2;
			}
			int lastCoin = windUp() + (Math.max(1, skill.coins()) - 1) * COIN_GAP;
			double distance = tick < windUp() ? 3.2 : tick >= lastCoin - 4 && tick < lastCoin + 10 ? 3.4 : 4.6;
			Vector at = tick < windUp() ? Cinematic.orbit(from.clone().add(new Vector(0, 1.2, 0)), cameraSide + tick * 0.02, distance, 0.6)
					: Cinematic.orbit(focus, cameraSide, distance, 1.4);
			camera.frame(at, tick < windUp() ? from.clone().add(new Vector(0, 1.3, 0)) : focus, 0.0);
		}

		private LivingEntity actor() {
			return camera != null ? camera.actor() : player;
		}

		private void finish() {
			if (ego) {
				TotalDamage.finish(player);
			}
			SkillOverlay.hide(player);
			if (camera != null) {
				camera.end();
				camera = null;
			}
		}
	}

	// --- the INDEXPRESCRIPT menu ---------------------------------------------------------------

	static final class Menu {
		private Menu() {
		}

		static void open(Player player) {
			MdGui gui = new MdGui(3, "INDEX PRESCRIPT");
			List<String> loadout = new ArrayList<>();
			for (String grade : GRADES) {
				Def def = equipped(player, grade);
				loadout.add(grade + ": " + (def == null ? "비어 있음" : def.nameKo() + " (" + def.sinnerKo() + ")"));
			}
			gui.set(10, MdGui.item(Material.NETHER_STAR, "E.G.O 장착", NamedTextColor.LIGHT_PURPLE, loadout.toArray(String[]::new)), () -> loadout(player));
			int[] held = resources(player);
			List<String> res = new ArrayList<>();
			for (int i = 0; i < SINS.length; i++) {
				res.add(SINS_KO[i] + " " + held[i] + "/" + MAX_RESOURCE);
			}
			res.add("스킬을 쓰면 그 스킬의 죄악 속성 자원 +1, 합에서 이기면 +1 더");
			gui.set(12, MdGui.item(Material.AMETHYST_SHARD, "E.G.O 자원", NamedTextColor.AQUA, res.toArray(String[]::new)), (Runnable) null);
			gui.set(11, MdGui.item(Material.GOLD_BLOCK, "추출", Gacha.THREE_COLOUR, "광기 " + Gacha.lunacy(player) + " · 추출권 "
					+ Gacha.count(player, Gacha.Kind.TICKET) + "장", "무기와 E.G.O를 뽑아요"), () -> Extraction.open(player));
			gui.set(15, MdGui.item(Material.ANVIL, "무기 강화", NamedTextColor.AQUA, "손에 든 무기의 레벨과 동기화", "경험치 티켓 "
					+ Gacha.count(player, Gacha.Kind.EXP) + "장 · 끈 " + Gacha.count(player, Gacha.Kind.THREAD) + "개"), () -> Upgrade.open(player));
			gui.set(14, MdGui.item(Material.ENDER_EYE, "거울 던전", NamedTextColor.GOLD, "거울 던전 메뉴를 열어요 (/md)"), () -> {
				player.closeInventory();
				player.performCommand("md");
			});
			gui.set(16, MdGui.item(Material.WRITABLE_BOOK, "조작법", NamedTextColor.WHITE,
					"우클릭: 들고 있는 무기의 스킬 (핫바에서 숫자키로 선택)",
					"추출: 광기 130 또는 추출권 1장 (10회는 1300 / 10장)",
					"무기 강화: 경험치 티켓으로 레벨(공격·방어 레벨), 끈으로 동기화",
					"F (왼손 들기): 무기를 든 채로 누르면 E.G.O (숫자키로 사용)",
					"9번 또는 5초 기다리면 핫바가 돌아와요",
					"E.G.O는 수감자와 상관없이 등급에 맞으면 낄 수 있어요 (가진 것만)",
					"자원과 정신력을 써서 E.G.O 각성 스킬을 써요"), (Runnable) null);
			gui.fill().open(player);
		}

		static void loadout(Player player) {
			MdGui gui = new MdGui(3, "E.G.O 장착");
			for (int i = 0; i < GRADES.length; i++) {
				String grade = GRADES[i];
				Def def = equipped(player, grade);
				ItemStack icon = def == null ? MdGui.item(Material.GRAY_STAINED_GLASS_PANE, grade + " (비어 있음)", NamedTextColor.GRAY, "클릭해서 고르기")
						: MdGui.item(Material.PAPER, Component.text(grade + " · " + def.nameKo(), def.colour()).decoration(TextDecoration.ITALIC, false),
								lore(def, null));
				if (def != null) {
					icon.editMeta(meta -> meta.setItemModel(NamespacedKey.fromString(model(def))));
				}
				gui.set(10 + i * 2, icon, () -> pick(player, grade, 0));
			}
			gui.set(22, MdGui.item(Material.ARROW, "뒤로", NamedTextColor.WHITE), () -> open(player));
			gui.fill().open(player);
		}

		static void pick(Player player, String grade, int page) {
			// Owned E.G.O first (the Sinners' base E.G.O, and those from Extraction), the rest shown locked.
			List<Def> list = new ArrayList<>(ofGrade(grade));
			list.sort(Comparator.comparing((Def d) -> !owns(player, d)));
			int per = 45;
			int pages = Math.max(1, (list.size() + per - 1) / per);
			MdGui gui = new MdGui(6, grade + " E.G.O (" + (page + 1) + "/" + pages + ")");
			Def current = equipped(player, grade);
			for (int i = page * per; i < Math.min(list.size(), (page + 1) * per); i++) {
				Def def = list.get(i);
				boolean owned = owns(player, def);
				int spin = uptie(player, def);
				List<Component> lore = new ArrayList<>(lore(def, player));
				if (!owned) {
					lore.add(SkillMenu.line("아직 없어요: 추출로 얻어요", NamedTextColor.RED));
				} else {
					lore.add(SkillMenu.line("동기화 " + spin + "/4" + (spin < 4 ? " · 우클릭: 동기화 " + (spin + 1) + " (끈 " + Upgrade.THREADS[spin + 1] * 2 + ")" : ""),
							NamedTextColor.AQUA));
					lore.add(SkillMenu.line(def == current ? "좌클릭: 장착 해제" : "좌클릭: 장착", NamedTextColor.YELLOW));
				}
				ItemStack icon = MdGui.item(Material.PAPER, Component.text(def.nameKo() + " · " + def.sinnerKo() + (owned ? "" : " (미보유)"),
						owned ? def.colour() : NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false), lore);
				icon.editMeta(meta -> meta.setItemModel(NamespacedKey.fromString(owned ? model(def) : "minecraft:gray_dye")));
				if (def == current) {
					MdGui.glow(icon);
				}
				gui.set(i - page * per, icon, (org.bukkit.event.inventory.InventoryClickEvent click) -> {
					if (!owned) {
						return;
					}
					if (click.isRightClick()) {
						// Threadspin: Threads for the next Uptie of its Awakening skill.
						if (spin < 4 && Gacha.take(player, Gacha.Kind.THREAD, Upgrade.THREADS[spin + 1] * 2)) {
							setUptie(player, def, spin + 1);
							Hud.message(player, Component.text(def.nameKo() + " 동기화 " + (spin + 1) + "!", def.colour()));
							player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0F, 1.0F);
						} else if (spin < 4) {
							Hud.message(player, Component.text("끈이 모자라요", NamedTextColor.GRAY));
						}
						pick(player, grade, page);
						return;
					}
					equip(player, grade, def == current ? null : def);
					Hud.message(player, Component.text(def == current ? grade + " E.G.O를 뺐어요" : grade + " E.G.O: " + def.nameKo(), def.colour()));
					loadout(player);
				});
			}
			if (page > 0) {
				gui.set(45, MdGui.item(Material.ARROW, "이전", NamedTextColor.WHITE), () -> pick(player, grade, page - 1));
			}
			if (page + 1 < pages) {
				gui.set(53, MdGui.item(Material.ARROW, "다음", NamedTextColor.WHITE), () -> pick(player, grade, page + 1));
			}
			gui.set(49, MdGui.item(Material.BARRIER, "뒤로", NamedTextColor.WHITE), () -> loadout(player));
			gui.fill().open(player);
		}
	}
}
