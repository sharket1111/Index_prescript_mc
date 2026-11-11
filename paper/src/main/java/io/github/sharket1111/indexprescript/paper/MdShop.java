package io.github.sharket1111.indexprescript.paper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * The shop before each floor's boss, after the original: E.G.O Gifts for Cost
 * (4 on show, more with Starter Buffs; 7 in a Super Shop), refreshes (15 Cost,
 * +15 each; a keyword refresh 90), selling at half price, enhancing (+/++ by
 * tier), fusing 2-3 gifts (5 in a Super Shop) into a new one, and healing (100:
 * one member 50% HP and 45 SP, everyone 15% and 15 SP, or reviving one). Any
 * member can shop; the Cost is shared. The original's skill replacement and
 * team change have no counterpart here.
 */
final class MdShop {
	static final int HEAL_COST = 100;
	private static final int[][] ENHANCE = {{50, 100}, {60, 120}, {75, 150}, {100, 200}, {200, 400}};

	private final MdRun run;
	private final boolean superShop;
	private final List<MdData.Gift> offers = new ArrayList<>();
	private int refreshes;
	private int freeRefreshes;
	private int freeKeywordRefreshes;
	private boolean sold;
	private boolean fusedBonus;
	private int spent;

	private MdShop(MdRun run, boolean superShop) {
		this.run = run;
		this.superShop = superShop;
	}

	static MdShop open(MdRun run) {
		boolean superShop = run.floorHard && MdRun.rng().nextInt(100) < run.superShopChance + MdGrace.superShopBonus(run);
		MdShop shop = new MdShop(run, superShop);
		if (superShop) {
			run.superShopChance = 3;
			run.broadcast(Component.text("슈퍼 상점이 나타났어요!", NamedTextColor.LIGHT_PURPLE));
		}
		shop.freeRefreshes = MdGrace.freeRefreshes(run);
		shop.freeKeywordRefreshes = MdGrace.freeKeywordRefreshes(run);
		MdGrace.shopEnter(run);
		MdGifts.trigger(run, "shop", null, null);
		shop.stock(null);
		for (Player p : run.players()) {
			shop.open(p);
		}
		return shop;
	}

	int slots() {
		return 4 + MdGrace.shopSlots(run) + (superShop ? 3 : 0);
	}

	private void stock(String keyword) {
		offers.clear();
		offers.addAll(roll(run, slots(), keyword, MdGrace.highTierShop(run)));
	}

	/** Gifts the run does not own (not theme-only, unless the current Theme Pack's), weighted toward higher tiers on later floors. */
	static List<MdData.Gift> roll(MdRun run, int count, String keyword, boolean higher) {
		int[] weights = switch (Math.min(5, Math.max(1, run.floor))) {
			case 1 -> new int[] {40, 45, 15, 0};
			case 2 -> new int[] {25, 45, 25, 5};
			case 3 -> new int[] {15, 35, 35, 15};
			case 4 -> new int[] {0, 30, 40, 30};
			default -> new int[] {0, 20, 40, 40};
		};
		if (higher) {
			weights = new int[] {weights[0] / 2, weights[1], weights[2] + 5, weights[3] + 10};
		}
		Set<String> unique = new HashSet<>();
		if (run.theme != null) {
			for (String u : run.theme.unique()) {
				MdData.Gift g = MdData.giftByName(u);
				if (g != null) {
					unique.add(g.id());
				}
			}
		}
		List<MdData.Gift> pool = new ArrayList<>();
		for (MdData.Gift g : MdData.gifts()) {
			if (run.gifts.containsKey(g.id()) || !g.common() && !(unique.contains(g.id()) && !g.fusionOnly())) {
				continue;
			}
			pool.add(g);
		}
		Set<MdData.Gift> out = new LinkedHashSet<>();
		int guard = 0;
		while (out.size() < count && guard++ < 500 && !pool.isEmpty()) {
			int roll = MdRun.rng().nextInt(weights[0] + weights[1] + weights[2] + weights[3]);
			int tier = roll < weights[0] ? 1 : roll < weights[0] + weights[1] ? 2 : roll < weights[0] + weights[1] + weights[2] ? 3 : 4;
			boolean wantKeyword = keyword != null && MdRun.rng().nextInt(100) < 70;
			List<MdData.Gift> fit = pool.stream().filter(g -> g.tierNumber() == tier && (!wantKeyword || g.keyword().equals(keyword))).toList();
			if (fit.isEmpty()) {
				fit = pool.stream().filter(g -> g.tierNumber() == tier).toList();
			}
			if (!fit.isEmpty()) {
				out.add(fit.get(MdRun.rng().nextInt(fit.size())));
			}
		}
		return new ArrayList<>(out);
	}

	// --- the screen ---------------------------------------------------------------------------------

	void open(Player p) {
		if (run.phase != MdRun.Phase.SHOP || run.shop != this) {
			return;
		}
		MdGui gui = new MdGui(6, (superShop ? "슈퍼 상점" : "상점") + " · 코스트 " + run.cost);
		for (int i = 0; i < offers.size() && i < 14; i++) {
			MdData.Gift g = offers.get(i);
			gui.set((i < 7 ? 10 : 19) + i % 7, icon(run, g, 0, "가격 " + g.cost() + " 코스트 · 클릭해서 구매"), () -> buy(p, g));
		}
		int refreshCost = freeRefreshes > 0 ? 0 : 15 + 15 * refreshes;
		gui.set(37, MdGui.item(Material.CLOCK, "새로고침 (" + (refreshCost == 0 ? "무료" : refreshCost + " 코스트") + ")", NamedTextColor.YELLOW,
				"진열된 기프트를 다시 뽑아요"), () -> refresh(p, null));
		gui.set(38, MdGui.item(Material.COMPASS, "키워드 새로고침 (" + (freeKeywordRefreshes > 0 ? "무료" : (90 + 15 * refreshes) + " 코스트") + ")",
				NamedTextColor.YELLOW, "고른 키워드가 더 잘 나오게 다시 뽑아요"), () -> keywordMenu(p));
		gui.set(40, MdGui.item(Material.ANVIL, "기프트 강화", NamedTextColor.AQUA, "+ / ++ (등급에 따라 50~200 코스트)"), () -> enhanceMenu(p));
		gui.set(41, MdGui.item(Material.BREWING_STAND, "기프트 합성", NamedTextColor.LIGHT_PURPLE, "2~" + (superShop ? 5 : 3) + "개를 넣어 새 기프트 1개"),
				() -> new Fusion(new ArrayList<>(), null, false).open(p));
		gui.set(42, MdGui.item(Material.GOLD_INGOT, "기프트 판매", NamedTextColor.GOLD, "구매가의 50%" + (MdGrace.sellBonus(run) > 0 && !sold
				? " (+" + MdGrace.sellBonus(run) + "%, 이번 상점 1회)" : "")), () -> sellMenu(p));
		gui.set(43, MdGui.item(Material.GOLDEN_APPLE, "회복 (" + HEAL_COST + " 코스트)", NamedTextColor.GREEN, "1명: 체력 50% · 정신력 45",
				"전원: 체력 15% · 정신력 15", "쓰러진 동료 부활"), () -> healMenu(p));
		gui.set(45, MdGui.item(Material.CHEST, "보유 기프트 (" + run.gifts.size() + ")", NamedTextColor.GOLD), () -> showGifts(run, p, () -> open(p)));
		gui.set(53, MdGui.item(Material.LIME_CONCRETE, "보스에게 가기", NamedTextColor.GREEN, p.getUniqueId().equals(run.leader) ? "리더가 눌러요"
				: "리더만 누를 수 있어요"), () -> {
					if (p.getUniqueId().equals(run.leader)) {
						leave();
					}
				});
		gui.fill().open(p);
	}

	private void refreshAll() {
		for (Player p : run.players()) {
			if (p.getOpenInventory().getTopInventory().getHolder() instanceof MdGui) {
				open(p);
			}
		}
		run.updateBar();
	}

	private boolean pay(Player p, int amount) {
		if (run.cost < amount) {
			p.sendMessage(Component.text("코스트가 모자라요 (" + run.cost + "/" + amount + ").", NamedTextColor.RED));
			p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6F, 1F);
			return false;
		}
		run.cost -= amount;
		spent += amount;
		run.superShopChance += amount / 20;
		return true;
	}

	private void buy(Player p, MdData.Gift g) {
		if (!offers.contains(g) || !pay(p, g.cost())) {
			return;
		}
		offers.remove(g);
		run.addGift(g, false);
		refreshAll();
	}

	private void refresh(Player p, String keyword) {
		boolean free = keyword == null ? freeRefreshes > 0 : freeKeywordRefreshes > 0;
		int price = free ? 0 : (keyword == null ? 15 : 90) + 15 * refreshes;
		if (!pay(p, price)) {
			return;
		}
		if (free && keyword == null) {
			freeRefreshes--;
		} else if (free) {
			freeKeywordRefreshes--;
		} else {
			refreshes++;
		}
		stock(keyword);
		refreshAll();
	}

	private void keywordMenu(Player p) {
		MdGui gui = new MdGui(2, "키워드 새로고침");
		int i = 0;
		for (String kw : MdData.KEYWORDS) {
			gui.set(i++, MdGui.item(keywordIcon(kw), MdData.KEYWORD_KO.get(kw), NamedTextColor.YELLOW), () -> refresh(p, kw));
		}
		gui.set(17, MdGui.item(Material.BARRIER, "돌아가기", NamedTextColor.RED), () -> open(p));
		gui.fill().open(p);
	}

	private void sellMenu(Player p) {
		MdGui gui = new MdGui(6, "기프트 판매");
		int i = 0;
		for (String id : List.copyOf(run.gifts.keySet())) {
			MdData.Gift g = MdData.gift(id);
			if (g == null || i >= 45) {
				continue;
			}
			int price = sellPrice(g);
			gui.set(i++, icon(run, g, run.gifts.get(id), "판매가 " + price + " 코스트 · 클릭해서 판매"), () -> {
				if (!run.gifts.containsKey(id)) {
					return;
				}
				run.cost += sellPrice(g);
				sold = true;
				run.removeGift(id);
				sellMenu(p);
			});
		}
		gui.set(49, MdGui.item(Material.BARRIER, "돌아가기", NamedTextColor.RED), () -> open(p));
		gui.fill().open(p);
	}

	private int sellPrice(MdData.Gift g) {
		int bonus = sold ? 0 : MdGrace.sellBonus(run);
		return g.cost() / 2 * (100 + bonus) / 100;
	}

	private void enhanceMenu(Player p) {
		MdGui gui = new MdGui(6, "기프트 강화");
		int i = 0;
		for (Map.Entry<String, Integer> owned : run.gifts.entrySet()) {
			MdData.Gift g = MdData.gift(owned.getKey());
			if (g == null || g.enhance() <= 0 || i >= 45) {
				continue;
			}
			int level = owned.getValue();
			String id = owned.getKey();
			if (level >= g.enhance()) {
				gui.set(i++, icon(run, g, level, "최대로 강화했어요"), (Runnable) null);
				continue;
			}
			int price = ENHANCE[Math.min(4, g.tierNumber() - 1)][level];
			List<String> next = g.level(level + 1).ko();
			ItemStack icon = icon(run, g, level, "강화 " + price + " 코스트 · 클릭\n강화 후: " + String.join(" / ", next));
			gui.set(i++, icon, () -> {
				if (run.gifts.getOrDefault(id, 99) != level || !pay(p, price)) {
					return;
				}
				run.gifts.put(id, level + 1);
				MdGifts.changed(run);
				p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.6F, 1.4F);
				enhanceMenu(p);
			});
		}
		gui.set(49, MdGui.item(Material.BARRIER, "돌아가기", NamedTextColor.RED), () -> open(p));
		gui.fill().open(p);
	}

	private void healMenu(Player p) {
		MdGui gui = new MdGui(3, "회복 · " + HEAL_COST + " 코스트");
		int i = 0;
		for (Player member : run.players()) {
			boolean down = run.downed.contains(member.getUniqueId());
			ItemStack head = MdGui.item(down ? Material.SKELETON_SKULL : Material.PLAYER_HEAD, member.getName(), down ? NamedTextColor.RED : NamedTextColor.WHITE,
					down ? "쓰러짐 · 클릭해서 부활" : "클릭: 체력 50% · 정신력 45 회복");
			gui.set(i++, head, () -> {
				if (!pay(p, HEAL_COST)) {
					return;
				}
				if (run.downed.contains(member.getUniqueId())) {
					run.revive(member.getUniqueId());
					member.teleport(MdWorld.entrance(run.slot, MdWorld.Room.SHOP));
					member.setGameMode(GameMode.ADVENTURE);
				} else {
					run.heal(member, 0.5);
					Sanity.add(member, 45);
				}
				refreshAll();
			});
		}
		gui.set(22, MdGui.item(Material.GLISTERING_MELON_SLICE, "전원 회복", NamedTextColor.GREEN, "체력 15% · 정신력 15"), () -> {
			if (!pay(p, HEAL_COST)) {
				return;
			}
			for (Player member : run.fighters()) {
				run.heal(member, 0.15);
				Sanity.add(member, 15);
			}
			refreshAll();
		});
		gui.set(26, MdGui.item(Material.BARRIER, "돌아가기", NamedTextColor.RED), () -> open(p));
		gui.fill().open(p);
	}

	private void leave() {
		for (Player p : run.players()) {
			p.closeInventory();
		}
		run.backToMap();
	}

	// --- fusion -----------------------------------------------------------------------------------

	/** Fusion as in the original: choose a keyword, put in 2-3 gifts (5 in a Super Shop); points decide the tier. */
	private final class Fusion {
		final List<String> chosen;
		final String keyword;
		final boolean wish;

		Fusion(List<String> chosen, String keyword, boolean wish) {
			this.chosen = chosen;
			this.keyword = keyword;
			this.wish = wish;
		}

		int points() {
			int sum = 0;
			for (String id : chosen) {
				MdData.Gift g = MdData.gift(id);
				sum += g == null ? 0 : switch (g.tierNumber()) {
					case 1 -> 3;
					case 2 -> 6;
					case 3 -> 10;
					case 4 -> 15;
					default -> 30;
				};
			}
			return sum;
		}

		int tier() {
			int p = points();
			if (superShop) {
				return p <= 9 ? 1 : p <= 14 ? 2 : p <= 21 ? 3 : 4;
			}
			return p <= 10 ? 1 : p <= 16 ? 2 : p <= 24 ? 3 : 4;
		}

		int odds() {
			int n = chosen.size();
			if (wish) {
				return n <= 2 ? 90 : n == 3 ? 99 : 100;
			}
			return n <= 2 ? 60 : n == 3 ? 90 : 99;
		}

		void open(Player p) {
			MdGui gui = new MdGui(6, "기프트 합성");
			int i = 0;
			int max = superShop ? 5 : 3;
			for (String id : run.gifts.keySet()) {
				MdData.Gift g = MdData.gift(id);
				if (g == null || i >= 36) {
					continue;
				}
				boolean in = chosen.contains(id);
				ItemStack icon = icon(run, g, run.gifts.get(id), in ? "넣음 · 클릭해서 빼기" : "클릭해서 넣기");
				if (in) {
					MdGui.glow(icon);
				}
				gui.set(i++, icon, () -> {
					List<String> next = new ArrayList<>(chosen);
					if (!next.remove(id) && next.size() < max) {
						next.add(id);
					}
					new Fusion(next, keyword, wish).open(p);
				});
			}
			int k = 36;
			for (String kw : MdData.KEYWORDS) {
				ItemStack icon = MdGui.item(keywordIcon(kw), MdData.KEYWORD_KO.get(kw), kw.equals(keyword) ? NamedTextColor.GREEN : NamedTextColor.GRAY,
						kw.equals(keyword) ? "선택한 키워드" : "이 키워드로 합성");
				if (kw.equals(keyword)) {
					MdGui.glow(icon);
				}
				gui.set(k++, icon, () -> new Fusion(chosen, kw, wish).open(p));
			}
			gui.set(46, MdGui.item(Material.NETHER_STAR, "소원 빌기: " + (wish ? "켬" : "끔") + " (별빛 5)", NamedTextColor.AQUA, "고른 키워드가 나올 확률을 올려요",
					"내 별빛: " + MdMeta.starlight(p.getUniqueId())), () -> new Fusion(chosen, keyword, !wish).open(p));
			MdData.Gift recipe = MdData.recipe(chosen);
			boolean ok = chosen.size() >= 2 && (keyword != null || recipe != null);
			gui.set(49, MdGui.item(ok ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE, "합성하기", ok ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY,
					"넣은 기프트 " + chosen.size() + "개 · 점수 " + points(), "결과 등급: " + roman(tier()),
					recipe != null ? "고정 레시피: " + recipe.name() : keyword == null ? "키워드를 고르세요" : MdData.KEYWORD_KO.get(keyword) + " 확률 " + odds() + "%"), () -> {
						if (ok) {
							fuse(p);
						}
					});
			gui.set(53, MdGui.item(Material.BARRIER, "돌아가기", NamedTextColor.RED), () -> MdShop.this.open(p));
			gui.fill().open(p);
		}

		void fuse(Player p) {
			if (!run.gifts.keySet().containsAll(chosen)) {
				return;
			}
			if (wish && !MdMeta.spendStarlight(p.getUniqueId(), 5)) {
				p.sendMessage(Component.text("별빛이 모자라요.", NamedTextColor.RED));
				return;
			}
			int tier = tier();
			boolean hit = MdRun.rng().nextInt(10000) < odds() * 100;
			MdData.Gift philosophy = MdData.giftByName("A Certain Philosophy");
			// A fixed recipe takes priority over a random fusion.
			MdData.Gift result = MdData.recipe(chosen);
			if (result != null && run.gifts.containsKey(result.id())) {
				result = null;
			}
			if (result != null) {
				tier = result.tierNumber();
			} else if (!hit && odds() >= 99 && philosophy != null && !run.gifts.containsKey(philosophy.id())) {
				result = philosophy;
			} else {
				List<MdData.Gift> pool = new ArrayList<>();
				for (MdData.Gift g : MdData.gifts()) {
					if (run.gifts.containsKey(g.id()) || !g.common() || g.tierNumber() != tier) {
						continue;
					}
					if (hit == g.keyword().equals(keyword)) {
						pool.add(g);
					}
				}
				if (!pool.isEmpty()) {
					result = pool.get(MdRun.rng().nextInt(pool.size()));
				}
			}
			if (result == null) {
				p.sendMessage(Component.text("합성할 수 있는 기프트가 없어요.", NamedTextColor.RED));
				return;
			}
			for (String id : chosen) {
				run.removeGift(id);
			}
			run.addGift(result, false);
			p.playSound(p.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 1F, 1.2F);
			if (!fusedBonus) {
				MdData.Gift extra = MdGrace.fusionBonus(run, tier);
				if (extra != null) {
					fusedBonus = true;
					run.addGift(extra, false);
				}
			}
			MdShop.this.open(p);
		}
	}

	// --- shared views ---------------------------------------------------------------------------------

	static String roman(int tier) {
		return switch (tier) {
			case 1 -> "I";
			case 2 -> "II";
			case 3 -> "III";
			case 4 -> "IV";
			default -> "V";
		};
	}

	static TextColor tierColor(int tier) {
		return switch (tier) {
			case 1 -> TextColor.color(0xF8BB00);
			case 2 -> TextColor.color(0xF8C201);
			case 3 -> TextColor.color(0xD8C816);
			case 4 -> TextColor.color(0xFDF858);
			default -> TextColor.color(0xFF6060);
		};
	}

	static Material keywordIcon(String kw) {
		return switch (kw) {
			case "burn" -> Material.BLAZE_POWDER;
			case "bleed" -> Material.REDSTONE;
			case "tremor" -> Material.AMETHYST_SHARD;
			case "rupture" -> Material.PRISMARINE_SHARD;
			case "sinking" -> Material.LAPIS_LAZULI;
			case "poise" -> Material.FEATHER;
			case "charge" -> Material.GLOWSTONE_DUST;
			case "slash" -> Material.IRON_SWORD;
			case "pierce" -> Material.ARROW;
			case "blunt" -> Material.MACE;
			default -> Material.PAPER;
		};
	}

	static Component name(MdData.Gift g) {
		return Component.text("[" + roman(g.tierNumber()) + "] ", tierColor(g.tierNumber())).append(Component.text(g.name(), NamedTextColor.WHITE));
	}

	/** A gift as an item: what it does here (Korean), the original's text, tier, sin, keyword, price. */
	static ItemStack icon(MdRun run, MdData.Gift g, int level, String action) {
		List<Component> lore = new ArrayList<>();
		lore.add(Component.text(MdData.SIN_KO.getOrDefault(g.sin(), g.sin()) + " · " + g.keywordKo() + " · " + roman(g.tierNumber()) + "등급"
				+ (level > 0 ? " · " + "+".repeat(level) : ""), NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
		MdData.Level lv = g.level(level);
		for (String line : lv.ko()) {
			lore.add(Component.text("• " + line, NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
		}
		if (lv.generic()) {
			lore.add(Component.text("  (원작 효과를 간단히 옮긴 효과예요)", NamedTextColor.DARK_AQUA).decoration(TextDecoration.ITALIC, false));
		}
		lore.add(Component.empty());
		for (String line : MdGui.wrap(lv.text(), 46)) {
			lore.add(Component.text(line, NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
		}
		if (run != null && run.fuseOnly.contains(g.id())) {
			lore.add(Component.text("합성·판매 전용", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
		}
		if (action != null) {
			lore.add(Component.empty());
			for (String a : action.split("\n")) {
				lore.add(Component.text(a, NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
			}
		}
		return MdGui.item(keywordIcon(g.keyword()), name(g), lore);
	}

	static void showGifts(MdRun run, Player p, Runnable back) {
		MdGui gui = new MdGui(6, "보유 E.G.O 기프트 (" + run.gifts.size() + ")");
		int i = 0;
		for (Map.Entry<String, Integer> owned : run.gifts.entrySet()) {
			MdData.Gift g = MdData.gift(owned.getKey());
			if (g != null && i < 45) {
				gui.set(i++, icon(run, g, owned.getValue(), null), (Runnable) null);
			}
		}
		if (back != null) {
			gui.set(49, MdGui.item(Material.ARROW, "돌아가기", NamedTextColor.WHITE), back);
		}
		gui.fill().open(p);
	}

	static Set<UUID> viewers(MdRun run) {
		Set<UUID> out = new HashSet<>();
		for (Player p : Bukkit.getOnlinePlayers()) {
			if (MdRun.of(p) == run) {
				out.add(p.getUniqueId());
			}
		}
		return out;
	}
}
