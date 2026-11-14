package io.github.sharket1111.indexprescript.paper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Event nodes: the original's Abnormality events (and each Theme Pack's own,
 * 75% of the time where it has them), with their story and choices. A sin
 * check picks a member, who flips 4 coins (heads +4 on a base of 6; Sanity
 * sets the chance of heads, as in a clash) against the target, lower when
 * their weapon's sin is among the check's. Results: E.G.O Gifts, HP or Sanity
 * lost or healed (for everyone, the one chosen, or those with or without a
 * sin), Cost, or a fight with the Abnormality.
 */
final class MdEvent {
	private static final int CHECK_BASE = 6;
	private static final int CHECK_COINS = 4;
	private static final int CHECK_COIN = 4;

	private MdEvent() {
	}

	static void start(MdRun run) {
		MdData.Event ev = pick(run);
		if (ev == null) {
			run.backToMap();
			return;
		}
		run.seenEvents.add(ev.id());
		current.put(run, ev);
		for (Player p : run.players()) {
			show(run, ev, p);
		}
	}

	private static final java.util.Map<MdRun, MdData.Event> current = new java.util.WeakHashMap<>();

	static void reopen(MdRun run, Player p) {
		MdData.Event ev = current.get(run);
		if (ev != null) {
			show(run, ev, p);
		}
	}

	private static MdData.Event pick(MdRun run) {
		List<MdData.Event> own = new ArrayList<>();
		if (run.theme != null) {
			for (String id : run.theme.events()) {
				MdData.Event e = MdData.event(id);
				if (e != null && !run.seenEvents.contains(id)) {
					own.add(e);
				}
			}
		}
		if (!own.isEmpty() && run.random.nextInt(100) < 75) {
			return own.get(run.random.nextInt(own.size()));
		}
		List<MdData.Event> common = new ArrayList<>(MdData.commonEvents().stream().filter(e -> !run.seenEvents.contains(e.id())).toList());
		if (common.isEmpty()) {
			common.addAll(MdData.commonEvents());
		}
		return common.isEmpty() ? null : common.get(run.random.nextInt(common.size()));
	}

	/** The sin a player brings to a check: their weapon's. */
	static String sinOf(Player p) {
		ItemStack held = p.getInventory().getItemInMainHand();
		if (Items.isLaevatein(held)) {
			return "wrath";
		}
		if (Items.isMimicry(held)) {
			return "lust";
		}
		if (Items.isRenoirHammer(held)) {
			return "sloth";
		}
		if (Items.isCaduceus(held)) {
			return "gloom";
		}
		String n = held.getType().name();
		if (n.endsWith("_SWORD")) {
			return "wrath";
		}
		if (n.endsWith("_AXE")) {
			return "gluttony";
		}
		if (held.getType() == Material.TRIDENT || n.endsWith("_SPEAR")) {
			return "envy";
		}
		if (held.getType() == Material.MACE) {
			return "sloth";
		}
		if (held.getType() == Material.BOW || held.getType() == Material.CROSSBOW) {
			return "pride";
		}
		return "none";
	}

	private static void show(MdRun run, MdData.Event ev, Player p) {
		boolean lead = p.getUniqueId().equals(run.leader);
		MdGui gui = new MdGui(4, ev.kr() != null && !ev.kr().isBlank() ? ev.kr() : ev.name());
		List<String> story = new ArrayList<>();
		if (ev.abnormality() != null) {
			story.add(ev.abnormality());
		}
		story.addAll(MdGui.wrap(ev.story(), 44));
		gui.set(4, MdGui.item(Material.WRITTEN_BOOK, ev.name(), NamedTextColor.GOLD, story.toArray(String[]::new)), (Runnable) null);
		int[] slots = {19, 21, 23, 25, 20, 24};
		for (int i = 0; i < ev.choices().size() && i < slots.length; i++) {
			MdData.Choice c = ev.choices().get(i);
			List<String> lore = new ArrayList<>();
			if (!c.desc().isBlank()) {
				lore.addAll(MdGui.wrap(c.desc(), 44));
			}
			if (c.check() != null) {
				lore.add("판정: " + (c.check().sins().isEmpty() ? "-" : String.join("/", c.check().sins().stream().map(s -> MdData.SIN_KO.getOrDefault(s, s)).toList()))
						+ " 이점 ≥ " + c.check().adv() + " · 그 외 ≥ " + c.check().disadv());
			}
			lore.add(lead ? "\n클릭해서 선택" : "\n리더가 골라요");
			gui.set(slots[i], MdGui.item(Material.PAPER, c.label(), NamedTextColor.WHITE, lore.toArray(String[]::new)), lead ? () -> choose(run, ev, c, p) : null);
		}
		gui.fill().open(p);
	}

	private static void choose(MdRun run, MdData.Event ev, MdData.Choice c, Player leader) {
		if (run.phase != MdRun.Phase.EVENT) {
			return;
		}
		if (c.check() != null) {
			pickMember(run, ev, c, leader);
			return;
		}
		finish(run, c.story(), c.resultText(), c.result(), leader);
	}

	/** Each member's chance to pass the check, shown before choosing (as the original's lines tell it). */
	private static int chance(Player p, MdData.Check check) {
		int target = check.sins().contains(sinOf(p)) ? check.adv() : check.disadv();
		double heads = Sanity.headsChance(p);
		double pass = 0;
		for (int k = 0; k <= CHECK_COINS; k++) {
			if (CHECK_BASE + k * CHECK_COIN >= target) {
				pass += binom(CHECK_COINS, k) * Math.pow(heads, k) * Math.pow(1 - heads, CHECK_COINS - k);
			}
		}
		return (int) Math.round(pass * 100);
	}

	private static double binom(int n, int k) {
		double r = 1;
		for (int i = 1; i <= k; i++) {
			r = r * (n - k + i) / i;
		}
		return r;
	}

	private static void pickMember(MdRun run, MdData.Event ev, MdData.Choice c, Player leader) {
		MdGui gui = new MdGui(3, c.check().prompt().isBlank() ? "누가 할까요?" : c.check().prompt());
		int i = 0;
		for (Player member : run.fighters()) {
			int chance = chance(member, c.check());
			String sin = sinOf(member);
			String line = chance >= 80 ? "\"이 정도는 쉽지.\"" : chance >= 55 ? "\"해 볼 만해.\"" : chance >= 30 ? "\"조금 불안한데...\"" : "\"무리야...\"";
			gui.set(10 + i++, MdGui.item(Material.PLAYER_HEAD, member.getName(), NamedTextColor.WHITE, "무기의 죄종: " + MdData.SIN_KO.getOrDefault(sin, "없음"),
					"성공 확률 약 " + chance + "%", line), () -> roll(run, c, member, leader));
		}
		gui.fill().open(leader);
	}

	private static void roll(MdRun run, MdData.Choice c, Player member, Player leader) {
		MdData.Check check = c.check();
		int target = check.sins().contains(sinOf(member)) ? check.adv() : check.disadv();
		double heads = Sanity.headsChance(member);
		int total = CHECK_BASE;
		StringBuilder coins = new StringBuilder();
		for (int i = 0; i < CHECK_COINS; i++) {
			boolean h = MdRun.rng().nextDouble() < heads;
			total += h ? CHECK_COIN : 0;
			coins.append(h ? "●" : "○");
		}
		boolean pass = total >= target;
		run.broadcast(Component.text(member.getName() + "의 판정: " + coins + " → " + total + " / 목표 " + target + " · ", NamedTextColor.GRAY)
				.append(Component.text(pass ? "성공" : "실패", pass ? NamedTextColor.GREEN : NamedTextColor.RED)));
		for (Player p : run.players()) {
			p.playSound(p.getLocation(), pass ? Sound.ENTITY_PLAYER_LEVELUP : Sound.ENTITY_ITEM_BREAK, 0.7F, pass ? 1.5F : 0.8F);
		}
		selected = member;
		finish(run, pass ? check.passText() : check.failText(), pass ? check.passResult() : check.failResult(), pass ? check.pass() : check.fail(), leader);
	}

	private static Player selected;

	private static void finish(MdRun run, String story, String resultText, List<MdData.Result> results, Player leader) {
		boolean battle = false;
		List<String> battleGifts = new ArrayList<>();
		for (MdData.Result r : results) {
			switch (r.what()) {
				case "gift" -> {
					MdData.Gift g = MdData.giftByName(r.gift());
					if (g != null) {
						run.addGift(g, false);
					} else {
						run.cost += 60;
					}
				}
				case "battle" -> {
					battle = true;
					battleGifts.addAll(r.gifts());
				}
				case "cost" -> run.cost += r.amount();
				default -> apply(run, r);
			}
		}
		selected = null;
		run.updateBar();
		MdData.Event ev = current.remove(run);
		List<String> lines = new ArrayList<>(MdGui.wrap(story, 44));
		lines.add("");
		lines.addAll(MdGui.wrap(resultText.replace("}}", ""), 44));
		boolean fight = battle;
		String foe = ev == null || ev.abnormality() == null ? "환상체" : ev.abnormality();
		for (Player p : run.players()) {
			MdGui gui = new MdGui(3, "결과");
			gui.set(13, MdGui.item(Material.WRITTEN_BOOK, fight ? "전투!" : "결과", fight ? NamedTextColor.RED : NamedTextColor.GOLD, lines.toArray(String[]::new)),
					(Runnable) null);
			gui.set(22, MdGui.item(fight ? Material.IRON_SWORD : Material.LIME_CONCRETE, fight ? "싸운다" : "계속", NamedTextColor.GREEN,
					p.getUniqueId().equals(run.leader) ? "클릭" : "리더가 눌러요"), () -> {
						if (!p.getUniqueId().equals(run.leader) || run.phase != MdRun.Phase.EVENT) {
							return;
						}
						for (Player q : run.players()) {
							q.closeInventory();
						}
						if (fight) {
							run.phase = MdRun.Phase.BATTLE;
							MdWorld.build(run.slot, MdWorld.Room.BATTLE, run.theme.color(), foe, MdScenes.of(run.theme));
							run.gather(MdWorld.Room.BATTLE);
							run.battle = new MdBattle(run, MdRun.NodeType.ABNO, foe, battleGifts);
							run.battle.start();
						} else {
							run.backToMap();
						}
					});
			gui.fill().open(p);
		}
	}

	/** HP and Sanity changes: an original amount N is N% of max health; Sanity uses the same -45..45 scale. */
	private static void apply(MdRun run, MdData.Result r) {
		for (Player p : run.fighters()) {
			if (!applies(r.who(), p)) {
				continue;
			}
			double max = p.getAttribute(Attribute.MAX_HEALTH) == null ? 20 : p.getAttribute(Attribute.MAX_HEALTH).getValue();
			double hp = max * r.amount() / 100.0;
			switch (r.what()) {
				case "hp_lose" -> p.setHealth(Math.max(1, p.getHealth() - hp));
				case "hp_heal" -> p.setHealth(Math.min(max, p.getHealth() + hp));
				case "sp_lose" -> Sanity.lose(p, r.amount());
				case "sp_heal" -> Sanity.add(p, r.amount());
				case "all_lose" -> {
					p.setHealth(Math.max(1, p.getHealth() - hp));
					Sanity.lose(p, r.amount());
				}
				case "all_heal" -> {
					p.setHealth(Math.min(max, p.getHealth() + hp));
					Sanity.add(p, r.amount());
				}
				default -> {
				}
			}
		}
	}

	private static boolean applies(String who, Player p) {
		if (who == null || who.equals("all")) {
			return true;
		}
		if (who.equals("selected")) {
			return selected == null || selected.equals(p);
		}
		String sin = sinOf(p).toLowerCase(Locale.ROOT);
		if (who.startsWith("without:")) {
			return !sin.equals(who.substring(8));
		}
		if (who.startsWith("with:")) {
			return sin.equals(who.substring(5));
		}
		return true;
	}

	static Component title(String text) {
		return Component.text(text, NamedTextColor.GOLD).decorate(TextDecoration.BOLD);
	}
}
