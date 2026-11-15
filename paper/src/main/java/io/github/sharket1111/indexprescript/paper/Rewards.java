package io.github.sharket1111.indexprescript.paper;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * The original's rewards by id (season/*.json): currencies, the items (Gacha.Kind), the Pass, E.G.O and
 * cosmetics (banners, announcers, business card decorations, kept as a collection).
 */
final class Rewards {
	private Rewards() {
	}

	static Gacha.Kind kind(String id) {
		return switch (id) {
			case "ticket" -> Gacha.Kind.TICKET;
			case "deca" -> Gacha.Kind.DECA;
			case "deca3" -> Gacha.Kind.DECA3;
			case "thread" -> Gacha.Kind.THREAD;
			case "train1" -> Gacha.Kind.TRAIN1;
			case "train2" -> Gacha.Kind.TRAIN2;
			case "train3" -> Gacha.Kind.EXP;
			case "train4" -> Gacha.Kind.TRAIN4;
			case "shard_universal" -> Gacha.Kind.SHARD;
			case "crate_random" -> Gacha.Kind.CRATE_RANDOM;
			case "crate_nominable" -> Gacha.Kind.CRATE_NOMINABLE;
			case "enk_module" -> Gacha.Kind.ENK_MODULE;
			case "enk_box" -> Gacha.Kind.ENK_BOX;
			case "boost1" -> Gacha.Kind.BOOST1;
			case "boost2" -> Gacha.Kind.BOOST2;
			case "boost3" -> Gacha.Kind.BOOST3;
			case "boost4" -> Gacha.Kind.BOOST4;
			case "boost5" -> Gacha.Kind.BOOST5;
			case "boost6" -> Gacha.Kind.BOOST6;
			case "takeoff_uptie" -> Gacha.Kind.TAKEOFF_UPTIE;
			default -> null;
		};
	}

	static String name(String id) {
		Gacha.Kind k = kind(id);
		if (k != null) {
			return k.name;
		}
		if (id.startsWith("ego:")) {
			Ego.Def d = Ego.get(id.substring(4));
			return "E.G.O " + (d == null ? id.substring(4) : d.nameKo() + " (" + d.sinnerKo() + ")");
		}
		if (id.startsWith("banner:")) {
			return "배너: " + id.substring(7);
		}
		if (id.startsWith("announcer:")) {
			return "아나운서: " + id.substring(10);
		}
		if (id.startsWith("deco:")) {
			return "명함 장식: " + id.substring(5);
		}
		return switch (id) {
			case "lunacy" -> "광기";
			case "lunacy_paid" -> "유료 광기";
			case "pass" -> "림버스 패스";
			case "pass_levels" -> "패스 레벨";
			default -> id;
		};
	}

	static Material icon(String id) {
		Gacha.Kind k = kind(id);
		if (k != null) {
			Material m = Material.matchMaterial(k.model);
			return m == null ? Material.PAPER : m;
		}
		if (id.startsWith("ego:")) {
			return Material.NETHER_STAR;
		}
		if (id.startsWith("banner:")) {
			return Material.WHITE_BANNER;
		}
		if (id.startsWith("announcer:")) {
			return Material.BELL;
		}
		if (id.startsWith("deco:")) {
			return Material.PAINTING;
		}
		return switch (id) {
			case "lunacy", "lunacy_paid" -> Material.REDSTONE;
			case "pass", "pass_levels" -> Material.GOLDEN_HELMET;
			default -> Material.PAPER;
		};
	}

	static String text(String id, int n) {
		return name(id) + (n > 1 || kind(id) != null || id.startsWith("lunacy") || id.equals("pass_levels") ? " ×" + n : "");
	}

	/** Gives {@code n} of {@code id}. */
	static void grant(Player p, String id, int n) {
		if (n <= 0) {
			return;
		}
		Gacha.Kind k = kind(id);
		if (k != null) {
			Gacha.give(p, k, n);
			return;
		}
		switch (id) {
			case "lunacy" -> Gacha.addLunacy(p, n);
			case "lunacy_paid" -> Gacha.addPaidLunacy(p, n);
			case "pass" -> Pass.unlock(p);
			case "pass_levels" -> Pass.addXp(p, n * Pass.xpPerLevel());
			default -> {
				if (id.startsWith("ego:")) {
					Ego.Def d = Ego.get(id.substring(4));
					if (d != null) {
						Ego.give(p, d);
						return;
					}
				}
				// Cosmetics (and an E.G.O the plugin has no data for yet) go into the collection.
				Season.addToList(p.getUniqueId(), "cosmetics", id);
			}
		}
	}

	/** Gives a list of [id, amount] pairs and tells the player what came. */
	static void grant(Player p, JsonArray items, String source) {
		List<String> parts = new ArrayList<>();
		for (JsonElement e : items) {
			JsonArray pair = e.getAsJsonArray();
			String id = pair.get(0).getAsString();
			int n = pair.get(1).getAsInt();
			grant(p, id, n);
			parts.add(text(id, n));
		}
		if (!parts.isEmpty()) {
			p.sendMessage(Component.text("[" + source + "] " + String.join(", ", parts), NamedTextColor.LIGHT_PURPLE));
		}
	}

	/** An icon showing a list of rewards. */
	static ItemStack icon(JsonArray items, String title, NamedTextColor colour, String... extra) {
		List<String> lines = new ArrayList<>();
		Material m = Material.PAPER;
		for (JsonElement e : items) {
			JsonArray pair = e.getAsJsonArray();
			if (lines.isEmpty()) {
				m = icon(pair.get(0).getAsString());
			}
			lines.add(text(pair.get(0).getAsString(), pair.get(1).getAsInt()));
		}
		for (String x : extra) {
			lines.add(x);
		}
		return MdGui.item(m, title, colour, lines.toArray(String[]::new));
	}
}
