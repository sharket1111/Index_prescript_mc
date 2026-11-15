package io.github.sharket1111.indexprescript.paper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * The original's Shop (season/shop.json), with its won prices paid in the trade currency at 2 G a won, and
 * the packs priced in Paid Lunacy paid in it. Weekly packs come back on Wednesday 06:00 KST, monthly ones on
 * the 1st, Season packs once a Season, the onboarding pack once ever. Lunacy: the first purchase of each
 * size doubles it in Paid Lunacy, later ones in free Lunacy. Monthly Lunacy: the Paid Lunacy now, then the
 * free Lunacy every day for 30 days (each purchase adds 30 days; days missed are kept and given on the
 * next log-in).
 */
final class PaidShop {
	private static JsonObject data = new JsonObject();
	private static int wonToG = 2;
	private static final String[] CATEGORIES = {"팩", "성장", "광기", "패스"};

	private PaidShop() {
	}

	static void load() {
		data = Season.json("shop");
		if (data.has("won_to_g")) {
			wonToG = data.get("won_to_g").getAsInt();
		}
	}

	private static List<JsonObject> products(String category) {
		List<JsonObject> out = new ArrayList<>();
		if (data.has("products")) {
			for (JsonElement e : data.getAsJsonArray("products")) {
				if (e.getAsJsonObject().get("category").getAsString().equals(category)) {
					out.add(e.getAsJsonObject());
				}
			}
		}
		return out;
	}

	private static String period(String reset) {
		return switch (reset) {
			case "weekly" -> Season.week();
			case "monthly" -> Season.month();
			case "season" -> Season.season();
			case "account" -> "ever";
			default -> null;
		};
	}

	private static boolean soldOut(UUID id, JsonObject pr) {
		String period = period(pr.get("reset").getAsString());
		return period != null && Season.periodic(id, "shop_" + pr.get("id").getAsString(), period) > 0;
	}

	private static long priceG(JsonObject pr) {
		return pr.get("won").isJsonNull() ? 0 : pr.get("won").getAsLong() * wonToG;
	}

	private static String note(JsonObject pr) {
		return pr.has("note") ? pr.get("note").getAsString() : "";
	}

	static void open(Player p) {
		open(p, 0);
	}

	static void open(Player p, int cat) {
		UUID id = p.getUniqueId();
		dailySupply(p);
		MdGui gui = new MdGui(6, "상점 · " + CATEGORIES[cat]);
		for (int i = 0; i < CATEGORIES.length; i++) {
			int c = i;
			var tab = MdGui.item(i == cat ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE, CATEGORIES[i],
					i == cat ? NamedTextColor.GREEN : NamedTextColor.GRAY);
			gui.set(1 + i * 2, tab, () -> open(p, c));
		}
		gui.set(0, MdGui.item(Material.GOLD_NUGGET, "가진 돈", NamedTextColor.GOLD, Gold.available() ? Gold.format(Gold.balance(id)) : "거래 플러그인이 없어요",
				"유료 광기 " + Gacha.paidLunacy(p) + " · 광기 " + Gacha.lunacy(p), "가격: 원작 원화 1원 = " + wonToG + "G"), (Runnable) null);
		int slot = 18;
		for (JsonObject pr : products(CATEGORIES[cat])) {
			List<String> lore = new ArrayList<>();
			for (JsonElement e : pr.getAsJsonArray("items")) {
				JsonArray pair = e.getAsJsonArray();
				lore.add(Rewards.text(pair.get(0).getAsString(), pair.get(1).getAsInt()));
			}
			String note = note(pr);
			String pid = pr.get("id").getAsString();
			if (note.startsWith("first:")) {
				boolean first = Season.getInt(id, "shop_first_" + pid) == 0;
				lore.add((first ? "첫 구매 보너스: 유료 광기 +" : "보너스: 광기 +") + note.substring(6));
			} else if (note.startsWith("daily:")) {
				lore.add("30일 동안 매일 광기 +" + note.substring(6) + " (사면 30일씩 늘어나요)");
				int left = supplyDaysLeft(id, pid);
				if (left > 0) {
					lore.add("남은 기간 " + left + "일");
				}
			} else if (note.startsWith("owned:") && Pass.premium(id)) {
				lore.add("림버스 패스가 있으면 유료 광기 " + note.substring(6));
			}
			String reset = pr.get("reset").getAsString();
			lore.add(switch (reset) {
				case "weekly" -> "주 1회 (수요일 06:00 초기화)";
				case "monthly" -> "월 1회 (매월 1일 06:00 초기화)";
				case "season" -> "시즌 1회";
				case "account" -> "계정당 1회";
				default -> "제한 없음";
			});
			boolean sold = soldOut(id, pr);
			String price = pr.get("won").isJsonNull() ? "유료 광기 " + pr.get("paid_lunacy").getAsInt()
					: Gold.format(priceG(pr)) + " (₩" + String.format("%,d", pr.get("won").getAsLong()) + ")";
			lore.add(sold ? "구매 완료" : "가격: " + price + " · 클릭해서 구매");
			Material icon = pr.getAsJsonArray("items").isEmpty() ? Material.CHEST
					: Rewards.icon(pr.getAsJsonArray("items").get(0).getAsJsonArray().get(0).getAsString());
			gui.set(slot++, MdGui.item(sold ? Material.BARRIER : icon, pr.get("name").getAsString(), sold ? NamedTextColor.DARK_GRAY : NamedTextColor.GOLD,
					lore.toArray(String[]::new)), () -> {
						buy(p, pr);
						open(p, cat);
					});
		}
		gui.set(49, MdGui.item(Material.ARROW, "뒤로", NamedTextColor.WHITE), () -> Ego.Menu.open(p));
		gui.fill().open(p);
	}

	private static void buy(Player p, JsonObject pr) {
		UUID id = p.getUniqueId();
		String pid = pr.get("id").getAsString();
		if (soldOut(id, pr)) {
			Hud.message(p, Component.text("이미 샀어요", NamedTextColor.GRAY));
			return;
		}
		if (pid.equals("pass") || pid.equals("pass_package")) {
			Pass.buy(p, pid.equals("pass_package"));
			return;
		}
		if (pr.get("won").isJsonNull()) {
			if (!Gacha.spendPaidLunacy(p, pr.get("paid_lunacy").getAsInt())) {
				Hud.message(p, Component.text("유료 광기가 모자라요", NamedTextColor.RED));
				return;
			}
		} else {
			if (!Gold.available()) {
				Hud.message(p, Component.text("거래 플러그인이 없어서 살 수 없어요", NamedTextColor.RED));
				return;
			}
			if (!Gold.take(id, priceG(pr))) {
				Hud.message(p, Component.text("돈이 모자라요 (" + Gold.format(priceG(pr)) + ")", NamedTextColor.RED));
				return;
			}
		}
		String period = period(pr.get("reset").getAsString());
		if (period != null) {
			Season.addPeriodic(id, "shop_" + pid, period, 1);
		}
		Rewards.grant(p, pr.getAsJsonArray("items"), pr.get("name").getAsString());
		String note = note(pr);
		if (note.startsWith("first:")) {
			int bonus = Integer.parseInt(note.substring(6));
			if (Season.getInt(id, "shop_first_" + pid) == 0) {
				Season.set(id, "shop_first_" + pid, 1);
				Gacha.addPaidLunacy(p, bonus);
				p.sendMessage(Component.text("첫 구매 보너스: 유료 광기 +" + bonus, NamedTextColor.GOLD));
			} else {
				Gacha.addLunacy(p, bonus);
				p.sendMessage(Component.text("보너스: 광기 +" + bonus, NamedTextColor.GOLD));
			}
		} else if (note.startsWith("daily:")) {
			LocalDate today = LocalDate.parse(Season.day());
			LocalDate until = supplyUntil(id, pid);
			LocalDate from = until == null || until.isBefore(today) ? today.minusDays(1) : until;
			if (Season.getString(id, "sup_" + pid + "_last").isEmpty() || until == null || until.isBefore(today)) {
				Season.set(id, "sup_" + pid + "_last", today.minusDays(1).toString());
			}
			Season.set(id, "sup_" + pid + "_until", from.plusDays(30).toString());
			dailySupply(p);
		}
		p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7F, 1.5F);
	}

	private static LocalDate supplyUntil(UUID id, String pid) {
		String s = Season.getString(id, "sup_" + pid + "_until");
		return s.isEmpty() ? null : LocalDate.parse(s);
	}

	private static int supplyDaysLeft(UUID id, String pid) {
		LocalDate until = supplyUntil(id, pid);
		LocalDate today = LocalDate.parse(Season.day());
		return until == null || until.isBefore(today) ? 0 : (int) (until.toEpochDay() - today.toEpochDay() + 1);
	}

	/** The monthly Lunacy for every day since it was last given (up to its end). */
	static void dailySupply(Player p) {
		UUID id = p.getUniqueId();
		LocalDate today = LocalDate.parse(Season.day());
		for (JsonObject pr : products("광기")) {
			String note = note(pr);
			if (!note.startsWith("daily:")) {
				continue;
			}
			String pid = pr.get("id").getAsString();
			LocalDate until = supplyUntil(id, pid);
			String lastS = Season.getString(id, "sup_" + pid + "_last");
			if (until == null || lastS.isEmpty()) {
				continue;
			}
			LocalDate last = LocalDate.parse(lastS);
			LocalDate end = until.isBefore(today) ? until : today;
			int days = (int) Math.max(0, end.toEpochDay() - last.toEpochDay());
			if (days > 0) {
				int each = Integer.parseInt(note.substring(6));
				Gacha.addLunacy(p, each * days);
				Season.set(id, "sup_" + pid + "_last", end.toString());
				p.sendMessage(Component.text("[" + pr.get("name").getAsString() + "] 광기 +" + each * days + " (" + days + "일)", NamedTextColor.LIGHT_PURPLE));
			}
		}
	}
}
