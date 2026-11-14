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
 * 1300 or 10 tickets for ten (the tenth at least two-star). Rates: E.G.O 1.3%
 * (only ones not owned yet; when none are left the rest share it), three-star
 * 2.9% (the plugin's own weapons: Caduceus, Laevatein, Renoir's hammer,
 * Mimicry, the Black God), two-star 12.8% (the original two-star Identities'
 * weapons, gacha/weapons.json), one-star 83% (EXP tickets, as the original's
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
	static final double EGO_RATE = 1.3;
	static final double THREE_RATE = 2.9;
	static final double TWO_RATE = 12.8;
	static final int DUP_TWO_THREADS = 20;
	static final int DUP_THREE_THREADS = 50;

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
			List<JsonArray> passiveEffects, List<List<String>> passiveKo, String passiveText) {
		List<Ego.Skill> skills(int uptie) {
			return upties.get(Math.max(0, Math.min(upties.size() - 1, uptie - 1)));
		}
	}

	/** The plugin's own weapons, as three-star. */
	enum Three {
		CADUCEUS("카두케우스 (뤼엔)"), LAEVATEIN("레바테인 (마티아스)"), RENOIR("르누아르의 망치"), MIMICRY("미미크리 (게부라)"), BLACK_GOD("검은 신 모드");

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
				case BLACK_GOD -> List.of(BlackGod.toggleItem(player));
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
				Weapon w = new Weapon(o.get("id").getAsString(), o.get("name").getAsString(), o.get("identity").getAsString(), o.get("sinner").getAsString(),
						o.get("model").getAsString(), upties, o.get("passive_name").getAsString(), effects, ko,
						o.getAsJsonObject("passive").get("text").getAsString());
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

	private static void own(Player p, String id) {
		if (!owns(p, id)) {
			String owned = p.getPersistentDataContainer().getOrDefault(OWNED, PersistentDataType.STRING, "");
			p.getPersistentDataContainer().set(OWNED, PersistentDataType.STRING, owned.isEmpty() ? id : owned + "," + id);
		}
	}

	// --- items: tickets, EXP tickets, Threads -------------------------------------------------------

	enum Kind {
		TICKET("추출권", "minecraft:paper", ONE_COLOUR, "추출 1회 (10장이면 10회 추출)"),
		EXP("경험치 티켓", "minecraft:experience_bottle", TextColor.color(0x7FD8FF), "무기 경험치 +" + Upgrade.EXP_PER_TICKET + " (/메뉴 → 무기 강화)"),
		THREAD("끈", "minecraft:string", TextColor.color(0xE8D7A8), "무기 동기화·E.G.O 동기화 재료 (/메뉴)");

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
		return new Pull(1, "exp", "경험치 티켓", false, false);
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
			give(p, Kind.THREAD, pull.rarity() == 3 ? DUP_THREE_THREADS : DUP_TWO_THREADS);
			return;
		}
		own(p, pull.id());
		if (pull.rarity() == 3) {
			for (Three t : Three.values()) {
				if (t.id().equals(pull.id())) {
					t.items(p).forEach(stack -> {
						Upgrade.refreshLore(stack);
						give(p, stack);
					});
				}
			}
		} else {
			Weapon w = weapon(pull.id());
			if (w != null) {
				give(p, weaponItem(w));
			}
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
