package io.github.sharket1111.indexprescript.paper;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Extraction, after the original: 130 Lunacy or an Extraction Ticket a pull,
 * 1300 or 10 tickets for ten (the tenth at least two-star). Rates: E.G.O 3.5% (above the original's 1.3%)
 * (only ones not owned yet; when none are left the rest share it), three-star
 * 2.9% (the plugin's own weapons: Caduceus, Laevatein, Renoir's hammer,
 * Mimicry), two-star 12.8% (the original two-star Identities'
 * weapons, gacha/weapons.json), one-star 80.8% (EXP tickets, as the original's
 * base Identities are owned already). Every pull adds a point of Ideality; 200
 * buy any three-star. A duplicate weapon becomes Threads.
 *
 * Lunacy comes from the Mirror Dungeon, as do Extraction Tickets, EXP tickets
 * (weapon levels) and Threads (Uptie and E.G.O Threadspin).
 */
final class Gacha {
	static final int SINGLE_LUNACY = 130;
	static final int TEN_LUNACY = 1300;
	static final int IDEALITY_EXCHANGE = 200;
	static final double EGO_RATE = 3.5;
	static final double THREE_RATE = 2.9;
	static final double TWO_RATE = 12.8;
	/** A duplicate weapon becomes Egoshards, as a duplicate Identity in the original: 15 for two-star (that Sinner's), 50 for three-star (universal; the plugin's own weapons have no Sinner). */
	static final int DUP_TWO_SHARDS = 15;
	static final int DUP_THREE_SHARDS = 50;

	static final TextColor ONE_COLOUR = TextColor.color(0xB98A5A);
	static final TextColor TWO_COLOUR = TextColor.color(0xF2B33D);
	static final TextColor THREE_COLOUR = TextColor.color(0xFF8A1E);
	static final TextColor EGO_COLOUR = TextColor.color(0xE8402A);

	private static final NamespacedKey LUNACY = new NamespacedKey(Items.NAMESPACE, "lunacy");
	private static final NamespacedKey IDEALITY = new NamespacedKey(Items.NAMESPACE, "ideality");
	private static final NamespacedKey OWNED = new NamespacedKey(Items.NAMESPACE, "weapons_owned");
	/** Item kinds: ticket, exp, thread; and the two-star weapon's id on its item. */
	static final NamespacedKey KIND = new NamespacedKey(Items.NAMESPACE, "gacha_item");
	static final NamespacedKey WEAPON = new NamespacedKey(Items.NAMESPACE, "gacha_weapon");

	/** A two-star weapon: an original two-star Identity's three skills at each Uptie, and its passive. */
	record Weapon(String id, String name, String identity, String sinner, String model, List<List<Ego.Skill>> upties, String passiveName,
			List<JsonArray> passiveEffects, List<List<String>> passiveKo, String passiveText, List<Defense.Spec> defense) {
		List<Ego.Skill> skills(int uptie) {
			return upties.get(Math.max(0, Math.min(upties.size() - 1, uptie - 1)));
		}

		/** The original Identity's Defense skill at this Uptie (null if it has none). */
		Defense.Spec defense(int uptie) {
			return defense.isEmpty() ? null : defense.get(Math.max(0, Math.min(defense.size() - 1, uptie - 1)));
		}
	}

	/** The plugin's own weapons, as three-star. */
	enum Three {
		CADUCEUS("카두케우스 (뤼엔)"), LAEVATEIN("레바테인 (마티아스)"), RENOIR("르누아르의 망치"), MIMICRY("미미크리 (게부라)");

		final String name;

		Three(String name) {
			this.name = name;
		}

		String id() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}

		List<ItemStack> items(Player player) {
			return switch (this) {
				case CADUCEUS -> List.of(Items.caduceus(player), Items.terminal(player));
				case LAEVATEIN -> List.of(Items.laevatein(player));
				case RENOIR -> List.of(Items.renoirHammer(player));
				case MIMICRY -> List.of(Items.mimicry(player));
			};
		}
	}

	/** One result of a pull. */
	record Pull(int rarity, String id, String name, boolean ego, boolean duplicate) {
	}

	private static final Map<String, Weapon> WEAPONS = new LinkedHashMap<>();

	private Gacha() {
	}

	static void load(IndexPrescriptPlugin plugin) {
		WEAPONS.clear();
		try (InputStream in = plugin.getResource("gacha/weapons.json")) {
			if (in == null) {
				return;
			}
			JsonObject root = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
			for (JsonElement e : root.getAsJsonArray("weapons")) {
				JsonObject o = e.getAsJsonObject();
				List<List<Ego.Skill>> upties = new ArrayList<>();
				for (JsonElement u : o.getAsJsonArray("upties")) {
					List<Ego.Skill> skills = new ArrayList<>();
					u.getAsJsonArray().forEach(sk -> skills.add(Ego.skillOf(sk.getAsJsonObject())));
					upties.add(skills);
				}
				List<JsonArray> effects = new ArrayList<>();
				o.getAsJsonArray("passive_effects").forEach(x -> effects.add(x.getAsJsonArray()));
				List<List<String>> ko = new ArrayList<>();
				o.getAsJsonArray("passive_ko").forEach(x -> {
					List<String> lines = new ArrayList<>();
					x.getAsJsonArray().forEach(l -> lines.add(l.getAsString()));
					ko.add(lines);
				});
				List<Defense.Spec> defense = new ArrayList<>();
				if (o.has("defense")) {
					for (JsonElement d : o.getAsJsonArray("defense")) {
						JsonObject dj = d.getAsJsonObject();
						defense.add(Defense.Spec.of(Ego.skillOf(dj), Defense.Type.of(dj.get("kind").getAsString())));
					}
				}
				Weapon w = new Weapon(o.get("id").getAsString(), o.get("name").getAsString(), o.get("identity").getAsString(), o.get("sinner").getAsString(),
						o.get("model").getAsString(), upties, o.get("passive_name").getAsString(), effects, ko,
						o.getAsJsonObject("passive").get("text").getAsString(), defense);
				WEAPONS.put(w.id(), w);
			}
			plugin.getLogger().info("Extraction: " + WEAPONS.size() + " two-star weapons");
		} catch (java.io.IOException | RuntimeException e) {
			plugin.getLogger().log(Level.WARNING, "Could not read gacha/weapons.json", e);
		}
	}

	static Weapon weapon(String id) {
		return id == null ? null : WEAPONS.get(id);
	}

	static List<Weapon> weapons() {
		return List.copyOf(WEAPONS.values());
	}

	// --- Lunacy, Ideality, owned weapons -----------------------------------------------------------

	static int lunacy(Player p) {
		return p.getPersistentDataContainer().getOrDefault(LUNACY, PersistentDataType.INTEGER, 0);
	}

	static void addLunacy(Player p, int amount) {
		p.getPersistentDataContainer().set(LUNACY, PersistentDataType.INTEGER, Math.max(0, lunacy(p) + amount));
	}

	private static final NamespacedKey PAID = new NamespacedKey(Items.NAMESPACE, "lunacy_paid");

	/** Paid Lunacy (bought in the Shop): the Limbus Pass and the packs priced in Lunacy take only this. */
	static int paidLunacy(Player p) {
		return p.getPersistentDataContainer().getOrDefault(PAID, PersistentDataType.INTEGER, 0);
	}

	static void addPaidLunacy(Player p, int amount) {
		p.getPersistentDataContainer().set(PAID, PersistentDataType.INTEGER, Math.max(0, paidLunacy(p) + amount));
	}

	/** All Lunacy, free and paid. */
	static int allLunacy(Player p) {
		return lunacy(p) + paidLunacy(p);
	}

	/** Spends Lunacy for Extraction: free first, then paid; false (nothing spent) if there is not enough. */
	static boolean spendLunacy(Player p, int amount) {
		if (allLunacy(p) < amount) {
			return false;
		}
		int free = Math.min(lunacy(p), amount);
		addLunacy(p, -free);
		addPaidLunacy(p, -(amount - free));
		return true;
	}

	static boolean spendPaidLunacy(Player p, int amount) {
		if (paidLunacy(p) < amount) {
			return false;
		}
		addPaidLunacy(p, -amount);
		return true;
	}

	static int ideality(Player p) {
		return p.getPersistentDataContainer().getOrDefault(IDEALITY, PersistentDataType.INTEGER, 0);
	}

	static void setIdeality(Player p, int value) {
		p.getPersistentDataContainer().set(IDEALITY, PersistentDataType.INTEGER, Math.max(0, value));
	}

	static boolean owns(Player p, String id) {
		String owned = p.getPersistentDataContainer().getOrDefault(OWNED, PersistentDataType.STRING, "");
		return ("," + owned + ",").contains("," + id + ",");
	}

	static void own(Player p, String id) {
		if (!owns(p, id)) {
			String owned = p.getPersistentDataContainer().getOrDefault(OWNED, PersistentDataType.STRING, "");
			p.getPersistentDataContainer().set(OWNED, PersistentDataType.STRING, owned.isEmpty() ? id : owned + "," + id);
		}
	}

	// --- items: tickets, EXP tickets, Threads -------------------------------------------------------

	/** The twelve Sinners (whose Egoshards are kept apart), in the original's order, and their Korean names. */
	static final String[] SINNERS = {"Yi Sang", "Faust", "Don Quixote", "Ryōshū", "Meursault", "Hong Lu", "Heathcliff", "Ishmael", "Rodion",
		"Sinclair", "Outis", "Gregor"};
	static final String[] SINNERS_KO = {"이상", "파우스트", "돈키호테", "료슈", "뫼르소", "홍루", "히스클리프", "이스마엘", "로쟈", "싱클레어", "오티스", "그레고르"};

	/** The index of a Sinner by English or Korean name (-1: none). */
	static int sinner(String name) {
		for (int i = 0; i < SINNERS.length; i++) {
			if (SINNERS[i].equalsIgnoreCase(name) || SINNERS_KO[i].equals(name)) {
				return i;
			}
		}
		return -1;
	}

	enum Kind {
		TICKET("추출권", "minecraft:paper", ONE_COLOUR, "추출 1회 (10장이면 10회 추출)"),
		DECA("10회 추출권", "minecraft:map", TWO_COLOUR, "10회 추출 1번 (추출권 10장과 같아요)"),
		DECA3("3성 확정 10회 추출권", "minecraft:filled_map", THREE_COLOUR, "ØØØ 무기가 반드시 1개 이상 나오는 10회 추출 (E.G.O 제외)"),
		TRAIN1("인격 경험치 티켓 I", "minecraft:experience_bottle", TextColor.color(0xA9D8E8), "무기 경험치 +50 (/메뉴 → 무기 강화)"),
		TRAIN2("인격 경험치 티켓 II", "minecraft:experience_bottle", TextColor.color(0x8CCBE6), "무기 경험치 +200 (/메뉴 → 무기 강화)"),
		EXP("인격 경험치 티켓 III", "minecraft:experience_bottle", TextColor.color(0x7FD8FF), "무기 경험치 +1000 (/메뉴 → 무기 강화)"),
		TRAIN4("인격 경험치 티켓 IV", "minecraft:experience_bottle", TextColor.color(0x4FB8FF), "무기 경험치 +3000 (/메뉴 → 무기 강화)"),
		THREAD("끈", "minecraft:string", TextColor.color(0xE8D7A8), "무기 동기화·E.G.O 동기화 재료 (/메뉴)"),
		SHARD("동기화 전용 조각 (범용)", "minecraft:prismarine_shard", TextColor.color(0xD9F2FF), "모든 무기·E.G.O의 동기화에 에고 조각 대신 쓸 수 있어요"),
		SHARD_0("이상의 에고 조각", "minecraft:amethyst_shard", TextColor.color(0x9DC9E8), "이상의 무기·E.G.O 동기화 재료"),
		SHARD_1("파우스트의 에고 조각", "minecraft:amethyst_shard", TextColor.color(0xF2B5C8), "파우스트의 무기·E.G.O 동기화 재료"),
		SHARD_2("돈키호테의 에고 조각", "minecraft:amethyst_shard", TextColor.color(0xF5E07A), "돈키호테의 무기·E.G.O 동기화 재료"),
		SHARD_3("료슈의 에고 조각", "minecraft:amethyst_shard", TextColor.color(0xE05050), "료슈의 무기·E.G.O 동기화 재료"),
		SHARD_4("뫼르소의 에고 조각", "minecraft:amethyst_shard", TextColor.color(0x5A6FB0), "뫼르소의 무기·E.G.O 동기화 재료"),
		SHARD_5("홍루의 에고 조각", "minecraft:amethyst_shard", TextColor.color(0x6FC2A8), "홍루의 무기·E.G.O 동기화 재료"),
		SHARD_6("히스클리프의 에고 조각", "minecraft:amethyst_shard", TextColor.color(0x7A5A9A), "히스클리프의 무기·E.G.O 동기화 재료"),
		SHARD_7("이스마엘의 에고 조각", "minecraft:amethyst_shard", TextColor.color(0xF09A40), "이스마엘의 무기·E.G.O 동기화 재료"),
		SHARD_8("로쟈의 에고 조각", "minecraft:amethyst_shard", TextColor.color(0xD06070), "로쟈의 무기·E.G.O 동기화 재료"),
		SHARD_9("싱클레어의 에고 조각", "minecraft:amethyst_shard", TextColor.color(0xE8C860), "싱클레어의 무기·E.G.O 동기화 재료"),
		SHARD_10("오티스의 에고 조각", "minecraft:amethyst_shard", TextColor.color(0x8A9A6A), "오티스의 무기·E.G.O 동기화 재료"),
		SHARD_11("그레고르의 에고 조각", "minecraft:amethyst_shard", TextColor.color(0x9A7A50), "그레고르의 무기·E.G.O 동기화 재료"),
		CRATE_RANDOM("무작위 에고 조각 상자", "minecraft:chest", TextColor.color(0xC9A8E8), "우클릭: 무작위 수감자의 에고 조각 1~3개"),
		CRATE_NOMINABLE("지정 에고 조각 상자", "minecraft:ender_chest", TextColor.color(0xB98AF0), "우클릭: 고른 수감자의 에고 조각 1~3개"),
		ENK_MODULE("엔케팔린 모듈", "minecraft:heart_of_the_sea", TextColor.color(0x5AE0C8), "루사이크 입장·거울 던전 보상 수령에 써요"),
		ENK_BOX("엔케팔린 상자", "minecraft:shulker_shell", TextColor.color(0x4AC0A8), "우클릭: 엔케팔린 +60"),
		BOOST1("레벨 부스트 티켓 I", "minecraft:name_tag", TextColor.color(0xE0E0E0), "무기 레벨을 10까지 올려요 (/메뉴 → 무기 강화)"),
		BOOST2("레벨 부스트 티켓 II", "minecraft:name_tag", TextColor.color(0xE0E0E0), "무기 레벨을 20까지 올려요 (/메뉴 → 무기 강화)"),
		BOOST3("레벨 부스트 티켓 III", "minecraft:name_tag", TextColor.color(0xE0E0E0), "무기 레벨을 30까지 올려요 (/메뉴 → 무기 강화)"),
		BOOST4("레벨 부스트 티켓 IV", "minecraft:name_tag", TextColor.color(0xF0E8A0), "무기 레벨을 40까지 올려요 (/메뉴 → 무기 강화)"),
		BOOST5("레벨 부스트 티켓 V", "minecraft:name_tag", TextColor.color(0xF0D870), "무기 레벨을 50까지 올려요 (/메뉴 → 무기 강화)"),
		BOOST6("레벨 부스트 티켓 VI", "minecraft:name_tag", TextColor.color(0xF0C040), "무기 레벨을 60까지 올려요 (/메뉴 → 무기 강화)"),
		TAKEOFF_UPTIE("테이크오프 모듈 - 인격 동기화", "minecraft:nether_star", TextColor.color(0xFF9A40), "무기 하나를 바로 동기화 4로 올려요 (/메뉴 → 무기 강화)");

		final String name;
		final String model;
		final TextColor colour;
		final String lore;

		Kind(String name, String model, TextColor colour, String lore) {
			this.name = name;
			this.model = model;
			this.colour = colour;
			this.lore = lore;
		}

		/** The Egoshard of Sinner {@code i}. */
		static Kind shard(int i) {
			return valueOf("SHARD_" + i);
		}

		/** Weapon EXP a training ticket gives (0 for anything else). */
		int exp() {
			return switch (this) {
				case TRAIN1 -> 50;
				case TRAIN2 -> 200;
				case EXP -> 1000;
				case TRAIN4 -> 3000;
				default -> 0;
			};
		}
	}

	static ItemStack item(Kind kind, int amount) {
		ItemStack stack = ItemStack.of(Material.PAPER);
		stack.editMeta(meta -> {
			meta.setMaxStackSize(99);
			meta.itemName(Component.text(kind.name, kind.colour).decoration(TextDecoration.ITALIC, false));
			meta.lore(List.of(Component.text(kind.lore, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
			meta.setItemModel(NamespacedKey.fromString(kind.model));
			meta.setRarity(ItemRarity.UNCOMMON);
			meta.getPersistentDataContainer().set(KIND, PersistentDataType.STRING, kind.name());
		});
		stack.setAmount(Math.max(1, Math.min(99, amount)));
		return stack;
	}

	static Kind kindOf(ItemStack stack) {
		if (stack == null || stack.isEmpty() || !stack.hasItemMeta()) {
			return null;
		}
		String k = stack.getItemMeta().getPersistentDataContainer().get(KIND, PersistentDataType.STRING);
		try {
			return k == null ? null : Kind.valueOf(k);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	static int count(Player p, Kind kind) {
		int n = 0;
		for (ItemStack s : p.getInventory().getStorageContents()) {
			if (kindOf(s) == kind) {
				n += s.getAmount();
			}
		}
		return n;
	}

	/** Takes {@code amount} of {@code kind} from the inventory; false (nothing taken) if there are not that many. */
	static boolean take(Player p, Kind kind, int amount) {
		if (count(p, kind) < amount) {
			return false;
		}
		int left = amount;
		ItemStack[] contents = p.getInventory().getStorageContents();
		for (int i = 0; i < contents.length && left > 0; i++) {
			if (kindOf(contents[i]) == kind) {
				int use = Math.min(left, contents[i].getAmount());
				contents[i].setAmount(contents[i].getAmount() - use);
				left -= use;
			}
		}
		p.getInventory().setStorageContents(contents);
		return true;
	}

	static void give(Player p, ItemStack stack) {
		p.getInventory().addItem(stack).values().forEach(rest -> p.getWorld().dropItem(p.getLocation(), rest));
	}

	static void give(Player p, Kind kind, int amount) {
		while (amount > 0) {
			int n = Math.min(99, amount);
			give(p, item(kind, n));
			amount -= n;
		}
	}

	// --- the two-star weapon item ------------------------------------------------------------------

	static ItemStack weaponItem(Weapon w) {
		ItemStack stack = ItemStack.of(Material.PAPER);
		stack.editMeta(meta -> {
			meta.setMaxStackSize(1);
			meta.itemName(Component.text(w.name(), TWO_COLOUR).decoration(TextDecoration.ITALIC, false));
			meta.setItemModel(NamespacedKey.fromString(w.model()));
			meta.setRarity(ItemRarity.RARE);
			meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
			meta.getPersistentDataContainer().set(WEAPON, PersistentDataType.STRING, w.id());
		});
		Upgrade.refreshLore(stack);
		return stack;
	}

	static Weapon weaponOf(ItemStack stack) {
		if (stack == null || stack.isEmpty() || !stack.hasItemMeta()) {
			return null;
		}
		return weapon(stack.getItemMeta().getPersistentDataContainer().get(WEAPON, PersistentDataType.STRING));
	}

	// --- pulling -----------------------------------------------------------------------------------

	/** Rolls one pull ({@code tenth}: the last of ten, at least two-star) without giving it. */
	static Pull roll(Player p, boolean tenth) {
		ThreadLocalRandom r = ThreadLocalRandom.current();
		List<Ego.Def> egos = Ego.all().stream().filter(d -> !Ego.owns(p, d)).toList();
		double ego = egos.isEmpty() ? 0 : EGO_RATE;
		double three = egos.isEmpty() ? 3.0 : THREE_RATE;
		double roll = r.nextDouble(100);
		if (roll < ego) {
			Ego.Def d = egos.get(r.nextInt(egos.size()));
			return new Pull(3, d.id(), "E.G.O::" + d.nameKo() + " (" + d.risk() + ")", true, false);
		}
		roll -= ego;
		if (roll < three) {
			Three t = Three.values()[r.nextInt(Three.values().length)];
			return new Pull(3, t.id(), t.name, false, owns(p, t.id()));
		}
		roll -= three;
		double two = tenth ? 100 : egos.isEmpty() ? 13.0 : TWO_RATE;
		if (roll < two && !WEAPONS.isEmpty()) {
			Weapon w = weapons().get(r.nextInt(WEAPONS.size()));
			return new Pull(2, w.id(), w.name(), false, owns(p, w.id()));
		}
		return new Pull(1, "exp", "인격 경험치 티켓 III", false, false);
	}

	/** One pull without E.G.O (a 3★ Guarantee Decaextraction Ticket's pool). */
	static Pull rollNoEgo(Player p, boolean tenth) {
		ThreadLocalRandom r = ThreadLocalRandom.current();
		double roll = r.nextDouble(100);
		if (roll < 3.0) {
			return rollThree(p);
		}
		roll -= 3.0;
		if ((tenth || roll < 13.0) && !WEAPONS.isEmpty()) {
			Weapon w = weapons().get(r.nextInt(WEAPONS.size()));
			return new Pull(2, w.id(), w.name(), false, owns(p, w.id()));
		}
		return new Pull(1, "exp", "인격 경험치 티켓 III", false, false);
	}

	static Pull rollThree(Player p) {
		Three t = Three.values()[ThreadLocalRandom.current().nextInt(Three.values().length)];
		return new Pull(3, t.id(), t.name, false, owns(p, t.id()));
	}

	/** Gives what a pull brought: the weapon (or Threads for a duplicate), the E.G.O, or an EXP ticket. */
	static void grant(Player p, Pull pull) {
		if (pull.ego()) {
			Ego.Def d = Ego.get(pull.id());
			if (d != null) {
				Ego.give(p, d);
			}
			return;
		}
		if (pull.rarity() == 1) {
			give(p, Kind.EXP, 1);
			return;
		}
		if (pull.duplicate()) {
			Weapon w = weapon(pull.id());
			int s = w == null ? -1 : sinner(w.sinner());
			if (pull.rarity() == 2 && s >= 0) {
				give(p, Kind.shard(s), DUP_TWO_SHARDS);
			} else {
				give(p, Kind.SHARD, pull.rarity() == 3 ? DUP_THREE_SHARDS : DUP_TWO_SHARDS);
			}
			return;
		}
		own(p, pull.id());
		// Weapons are carried from the menu (one at a time); the first one is carried at once.
		if (Armory.equipped(p) == null) {
			Armory.equip(p, pull.id());
		}
	}

	/** Ideality exchange: 200 points buy a three-star of the player's choice. */
	static boolean exchange(Player p, Three t) {
		if (ideality(p) < IDEALITY_EXCHANGE) {
			return false;
		}
		setIdeality(p, ideality(p) - IDEALITY_EXCHANGE);
		grant(p, new Pull(3, t.id(), t.name, false, owns(p, t.id())));
		return true;
	}

	static String stars(int rarity) {
		return "Ø".repeat(Math.max(1, rarity));
	}

	static TextColor colour(Pull pull) {
		return pull.ego() ? EGO_COLOUR : pull.rarity() == 3 ? THREE_COLOUR : pull.rarity() == 2 ? TWO_COLOUR : ONE_COLOUR;
	}
}
