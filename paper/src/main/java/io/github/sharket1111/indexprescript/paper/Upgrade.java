package io.github.sharket1111.indexprescript.paper;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Weapon levels and Uptie, after the original's Identities.
 *
 * A weapon's level (kept on the item; 40 when new, up to 65 as the current
 * Season's Identity cap) rises with the original's Identity Training Tickets
 * (I-IV: 50/200/1000/3000 EXP) or jumps with a Level Boost Ticket, and is its Offense and Defense Level: damage is scaled as in the
 * original, by 1 + (offense - defense) / (|offense - defense| + 25), with the
 * attacker's weapon level (a Mirror Dungeon enemy's level) against the
 * target's (the player holding no weapon, or anything else, counts 40).
 *
 * Uptie (1-4) costs what the original's does (Thread, and Egoshards for Tier 4: the two-star weapons as a 2★
 * Identity with their Sinner's shards, the plugin's own as a 3★ with universal shards, swords/spears/axes as a
 * 1★); a Takeoff Module raises one straight to 4. Uptie makes a weapon stronger: the two-star weapons play
 * their Identity's own skills and passive at that Uptie; the plugin's own
 * weapons gain clash power (+1 at Uptie 3, +2 at 4) and damage (+5% an Uptie
 * past 1), which carries their passives and effects too. The E.G.O's
 * Threadspin works the same way on its Awakening skill (Ego).
 */
final class Upgrade implements Listener {
	static final int START_LEVEL = 40;
	static final int MAX_LEVEL = 65;
	static final int NEUTRAL_LEVEL = 40;
	/** The training tickets, best first. */
	static final Gacha.Kind[] TICKETS = {Gacha.Kind.TRAIN4, Gacha.Kind.EXP, Gacha.Kind.TRAIN2, Gacha.Kind.TRAIN1};
	static final Gacha.Kind[] BOOSTS = {Gacha.Kind.BOOST1, Gacha.Kind.BOOST2, Gacha.Kind.BOOST3, Gacha.Kind.BOOST4, Gacha.Kind.BOOST5, Gacha.Kind.BOOST6};

	private static final NamespacedKey LEVEL = new NamespacedKey(Items.NAMESPACE, "weapon_level");
	private static final NamespacedKey EXP = new NamespacedKey(Items.NAMESPACE, "weapon_exp");
	private static final NamespacedKey UPTIE = new NamespacedKey(Items.NAMESPACE, "weapon_uptie");

	/** Whether the stack is a weapon that levels: the plugin's own or a gacha weapon. */
	static boolean levels(ItemStack stack) {
		return stack != null && !stack.isEmpty() && (Items.isWeapon(stack) || Gacha.weaponOf(stack) != null || VanillaWeapon.is(stack));
	}

	static int level(ItemStack stack) {
		return levels(stack) ? stack.getItemMeta().getPersistentDataContainer().getOrDefault(LEVEL, PersistentDataType.INTEGER, START_LEVEL) : NEUTRAL_LEVEL;
	}

	static int exp(ItemStack stack) {
		return stack.getItemMeta().getPersistentDataContainer().getOrDefault(EXP, PersistentDataType.INTEGER, 0);
	}

	static int uptie(ItemStack stack) {
		return levels(stack) ? stack.getItemMeta().getPersistentDataContainer().getOrDefault(UPTIE, PersistentDataType.INTEGER, 1) : 1;
	}

	/**
	 * EXP from one level to the next: past 45 the original's curve rises gently, and from 60 to 65 it takes
	 * 48,960 EXP in all; this line follows it (49,800 from 60 to 65).
	 */
	static int need(int level) {
		return 6000 + 180 * Math.max(0, level - 40);
	}

	/** The original's Uptie cost to reach {@code toUptie}: {Thread, Egoshards}. */
	static int[] costFor(ItemStack stack, int toUptie) {
		int t = Math.max(2, Math.min(4, toUptie));
		if (Gacha.weaponOf(stack) != null) {
			return new int[] {new int[] {10, 40, 100}[t - 2], t == 4 ? 30 : 0};
		}
		if (VanillaWeapon.is(stack)) {
			return new int[] {t == 4 ? 50 : 0, t == 4 ? 20 : 0};
		}
		return new int[] {new int[] {20, 80, 150}[t - 2], t == 4 ? 50 : 0};
	}

	/** Whose Egoshards a weapon takes (-1: universal only). */
	static int sinnerOf(ItemStack stack) {
		Gacha.Weapon w = Gacha.weaponOf(stack);
		return w == null ? -1 : Gacha.sinner(w.sinner());
	}

	/** Adds EXP; returns how many levels it gained. */
	static int addExp(ItemStack stack, int amount) {
		int level = level(stack);
		int exp = exp(stack) + amount;
		int gained = 0;
		while (level < MAX_LEVEL && exp >= need(level)) {
			exp -= need(level);
			level++;
			gained++;
		}
		if (level >= MAX_LEVEL) {
			exp = 0;
		}
		int l = level;
		int e = exp;
		stack.editMeta(meta -> {
			meta.getPersistentDataContainer().set(LEVEL, PersistentDataType.INTEGER, l);
			meta.getPersistentDataContainer().set(EXP, PersistentDataType.INTEGER, e);
		});
		refreshLore(stack);
		return gained;
	}

	static void setUptie(ItemStack stack, int uptie) {
		stack.editMeta(meta -> meta.getPersistentDataContainer().set(UPTIE, PersistentDataType.INTEGER, Math.max(1, Math.min(4, uptie))));
		refreshLore(stack);
	}

	/** Clash power the plugin's own weapons gain from Uptie (the gacha weapons' is in their skills). */
	static int clashBonus(Player p) {
		ItemStack held = p.getInventory().getItemInMainHand();
		if (!Items.isWeapon(held)) {
			return 0;
		}
		int u = uptie(held);
		return (u >= 3 ? 1 : 0) + (u >= 4 ? 1 : 0);
	}

	private static double uptieDamage(Player p) {
		ItemStack held = p.getInventory().getItemInMainHand();
		return Items.isWeapon(held) ? 1 + 0.05 * (uptie(held) - 1) : 1;
	}

	/** The original's level difference: 1 + d / (|d| + 25). */
	static double levelScale(int offense, int defense) {
		int d = offense - defense;
		return Math.max(0.1, 1 + d / (double) (Math.abs(d) + 25));
	}

	private static int baseLevel(LivingEntity unit) {
		if (unit instanceof Player p) {
			return level(p.getInventory().getItemInMainHand());
		}
		MdBattle.Foe foe = MdBattle.foe(unit);
		return foe != null && foe.level > 0 ? foe.level : NEUTRAL_LEVEL;
	}

	/**
	 * The unit's Offense Level now: its level, raised by Offense Level Up and lowered by Offense Level Down
	 * (each a level a stack, for the turn), and for a dungeon enemy its Mounting Trials.
	 */
	static int offenseLevel(LivingEntity unit) {
		int level = baseLevel(unit);
		if (unit instanceof Player p) {
			return level + MdGifts.buffOf(p, "offense_level") - MdGifts.buffOf(p, "offense_down");
		}
		MdBattle.Foe foe = MdBattle.foe(unit);
		if (foe != null) {
			level += foe.offenseBonus - (foe.debuffUntil > CaduceusListener.now() ? foe.offenseDown : 0);
		}
		return level;
	}

	/** The unit's Defense Level now, as {@link #offenseLevel} with Defense Level Up and Down. */
	static int defenseLevel(LivingEntity unit) {
		int level = baseLevel(unit);
		if (unit instanceof Player p) {
			return level + MdGifts.buffOf(p, "defense_level") - MdGifts.buffOf(p, "defense_down");
		}
		MdBattle.Foe foe = MdBattle.foe(unit);
		if (foe != null) {
			level += foe.defenseBonus - (foe.debuffUntil > CaduceusListener.now() ? foe.defenseDown : 0);
		}
		return level;
	}

	/** The original's clash rule: +1 Clash Power for every 3 Offense Levels above the opponent's. */
	static int clashLevelBonus(LivingEntity unit, LivingEntity opponent) {
		if (opponent == null) {
			return 0;
		}
		return Math.max(0, (offenseLevel(unit) - offenseLevel(opponent)) / 3);
	}

	/**
	 * A Guard's share of the damage it lets through: half, 5% less for every 3 Defense Levels the guard has
	 * above the attacker's Offense Level (5% more for every 3 below), between a quarter and three quarters.
	 */
	static double guardShare(LivingEntity guard, LivingEntity attacker) {
		int d = (defenseLevel(guard) - offenseLevel(attacker)) / 3;
		return Math.max(0.25, Math.min(0.75, 0.5 - 0.05 * d));
	}

	/** The info lines of a gacha weapon's item (the plugin's own keep their own lore; the menu shows theirs). */
	static void refreshLore(ItemStack stack) {
		if (VanillaWeapon.is(stack)) {
			List<Component> vanilla = VanillaWeapon.lore(stack);
			stack.editMeta(meta -> meta.lore(vanilla));
			return;
		}
		Gacha.Weapon w = Gacha.weaponOf(stack);
		if (w == null) {
			return;
		}
		int level = level(stack);
		int uptie = uptie(stack);
		List<Component> lore = new ArrayList<>();
		lore.add(line(Gacha.stars(2) + " · " + w.sinner() + " · " + w.identity(), Gacha.TWO_COLOUR));
		lore.add(line("Lv." + level + (level < MAX_LEVEL ? " (EXP " + exp(stack) + "/" + need(level) + ")" : " (최대)") + " · 동기화 " + uptie,
				NamedTextColor.GOLD));
		lore.add(line("공격 레벨 " + level + " · 방어 레벨 " + level, NamedTextColor.GRAY));
		int n = 1;
		for (Ego.Skill s : w.skills(uptie)) {
			lore.add(line(n++ + ". " + s.name() + "  " + Ego.typeKo(s.type()) + " · " + Ego.SINS_KO[Math.max(0, Ego.index(s.sin()))] + " · 위력 "
					+ s.power() + " · 코인 " + (s.coinPower() >= 0 ? "+" : "") + s.coinPower() + " ×" + s.coins(), TextColorOf(s)));
			for (String t : s.text()) {
				for (String l : MdGui.wrap(t, 38)) {
					lore.add(line("   " + l, NamedTextColor.DARK_GRAY));
				}
			}
		}
		lore.add(line("패시브 [" + w.passiveName() + "]", NamedTextColor.DARK_AQUA));
		for (String l : w.passiveKo().get(Math.max(0, Math.min(3, uptie - 1)))) {
			lore.add(line("   " + l, NamedTextColor.AQUA));
		}
		lore.add(line("우클릭: 스킬 (핫바에서 숫자키)", NamedTextColor.YELLOW));
		stack.editMeta(meta -> meta.lore(lore));
	}

	private static net.kyori.adventure.text.format.TextColor TextColorOf(Ego.Skill s) {
		return net.kyori.adventure.text.format.TextColor.color(Ego.SIN_RGB[Math.max(0, Ego.index(s.sin()))]);
	}

	private static Component line(String text, net.kyori.adventure.text.format.TextColor colour) {
		return Component.text(text, colour).decoration(TextDecoration.ITALIC, false);
	}

	// --- the damage -----------------------------------------------------------------------------------

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onDamage(EntityDamageByEntityEvent event) {
		if (!(event.getEntity() instanceof LivingEntity victim)) {
			return;
		}
		LivingEntity attacker = event.getDamager() instanceof LivingEntity l ? l
				: event.getDamager() instanceof Projectile pr && pr.getShooter() instanceof LivingEntity s ? s : null;
		if (attacker == null || attacker == victim) {
			return;
		}
		boolean armed = attacker instanceof Player p && levels(p.getInventory().getItemInMainHand());
		boolean defended = victim instanceof Player v && levels(v.getInventory().getItemInMainHand());
		boolean dungeon = MdBattle.foe(attacker) != null || MdBattle.foe(victim) != null;
		// Only where a level is in play: a weapon on either side, or a dungeon enemy.
		if (!armed && !defended && !dungeon) {
			return;
		}
		double scale = levelScale(offenseLevel(attacker), defenseLevel(victim));
		if (attacker instanceof Player p) {
			scale *= uptieDamage(p);
		}
		event.setDamage(event.getDamage() * scale);
	}

	// --- the menu --------------------------------------------------------------------------------------

	/** The upgrade menu for the weapon in hand: levels with EXP tickets, Uptie with Threads. */
	/** The weapon the menu works on: a sword, spear or axe in hand, else the carried skill weapon. */
	private static int targetSlot(Player p) {
		return VanillaWeapon.is(p.getInventory().getItemInMainHand()) ? p.getInventory().getHeldItemSlot() : Armory.mainSlot(p);
	}

	/** Tickets picked on the level-up screen (I, II, III, IV), per player. */
	private static final java.util.Map<java.util.UUID, int[]> PICKED = new java.util.concurrent.ConcurrentHashMap<>();
	private static final Gacha.Kind[] TIERS = {Gacha.Kind.TRAIN1, Gacha.Kind.TRAIN2, Gacha.Kind.EXP, Gacha.Kind.TRAIN4};
	private static final String[] ROMAN = {"I", "II", "III", "IV"};

	static void open(Player p) {
		openLevel(p);
	}

	/** The screen's frame: the weapon, the two tabs and back. Null if there is nothing to upgrade. */
	private static MdGui frame(Player p, String title, boolean levelTab) {
		int slot = targetSlot(p);
		ItemStack held = slot < 0 ? null : p.getInventory().getItem(slot);
		MdGui gui = new MdGui(6, title);
		if (!levels(held)) {
			gui.set(22, MdGui.item(Material.BARRIER, "강화할 무기가 없어요", NamedTextColor.RED, "/메뉴 → 무기 선택에서 고르거나", "검·창·도끼를 손에 들고 여세요"),
					(Runnable) null);
			gui.set(49, MdGui.item(Material.ARROW, "뒤로", NamedTextColor.WHITE), () -> Armory.open(p));
			gui.fill().open(p);
			return null;
		}
		ItemStack info = held.clone();
		List<Component> lore = new ArrayList<>();
		int level = level(held);
		lore.add(line("Lv." + level + (level < MAX_LEVEL ? " · EXP " + exp(held) + "/" + need(level) : " (최대)"), NamedTextColor.GOLD));
		lore.add(line("공격 레벨 " + level + " · 방어 레벨 " + level, NamedTextColor.GRAY));
		lore.add(line("동기화 " + ROMAN[uptie(held) - 1], NamedTextColor.AQUA));
		info.editMeta(meta -> meta.lore(lore));
		gui.set(10, info, (Runnable) null);
		var levelTabIcon = MdGui.item(Material.EXPERIENCE_BOTTLE, "레벨 업", levelTab ? NamedTextColor.GREEN : NamedTextColor.GRAY);
		var uptieTabIcon = MdGui.item(Material.STRING, "동기화", levelTab ? NamedTextColor.GRAY : NamedTextColor.GREEN);
		gui.set(3, levelTab ? MdGui.glow(levelTabIcon) : levelTabIcon, () -> openLevel(p));
		gui.set(5, levelTab ? uptieTabIcon : MdGui.glow(uptieTabIcon), () -> openUptie(p));
		gui.set(45, MdGui.item(Material.ARROW, "뒤로", NamedTextColor.WHITE), () -> {
			PICKED.remove(p.getUniqueId());
			Armory.open(p);
		});
		return gui;
	}

	/** The level after adding {@code exp}: {level, exp left}. */
	private static int[] after(int level, int exp, long add) {
		long e = exp + add;
		while (level < MAX_LEVEL && e >= need(level)) {
			e -= need(level);
			level++;
		}
		return new int[] {level, level >= MAX_LEVEL ? 0 : (int) e};
	}

	/** Level up, as the original's screen: the four ticket tiers picked one by one, the result shown, then confirmed. */
	static void openLevel(Player p) {
		MdGui gui = frame(p, "무기 강화 · 레벨 업", true);
		if (gui == null) {
			return;
		}
		ItemStack held = p.getInventory().getItem(targetSlot(p));
		int[] picked = PICKED.computeIfAbsent(p.getUniqueId(), k -> new int[4]);
		long add = 0;
		for (int i = 0; i < 4; i++) {
			picked[i] = Math.min(picked[i], Gacha.count(p, TIERS[i]));
			add += (long) picked[i] * TIERS[i].exp();
		}
		int level = level(held);
		int[] to = after(level, exp(held), add);
		// The EXP bar: nine cells for the way to the next level, then the result.
		int filled = level >= MAX_LEVEL ? 9 : (int) Math.round(9.0 * exp(held) / need(level));
		for (int c = 0; c < 9; c++) {
			gui.set(18 + c, MdGui.item(c < filled ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE,
					"Lv." + level + (level < MAX_LEVEL ? " · EXP " + exp(held) + "/" + need(level) : " (최대)"), NamedTextColor.GREEN), (Runnable) null);
		}
		gui.set(13, MdGui.item(Material.NETHER_STAR, add > 0 ? "Lv." + level + " → Lv." + to[0] : "Lv." + level, add > 0 ? NamedTextColor.YELLOW : NamedTextColor.WHITE,
				"고른 경험치 +" + add, to[0] < MAX_LEVEL ? "다음 레벨까지 " + (need(to[0]) - to[1]) : "최대 레벨", "공격·방어 레벨 " + level + " → " + to[0]), (Runnable) null);
		for (int i = 0; i < 4; i++) {
			Gacha.Kind k = TIERS[i];
			int have = Gacha.count(p, k);
			int t = i;
			var icon = MdGui.item(Material.EXPERIENCE_BOTTLE, "인격 경험치 티켓 " + ROMAN[i] + "  " + picked[i] + "/" + have, k.colour, "EXP +" + k.exp() + " (1장)",
					"좌클릭 +1 · 우클릭 -1 · 쉬프트 좌클릭 +10 · 쉬프트 우클릭 0");
			icon.setAmount(Math.max(1, Math.min(99, picked[i])));
			Rewards.dress(icon, k.iconId().equals("train3") ? "train3" : "train" + (i + 1));
			gui.set(29 + i * 2 - (i > 1 ? 0 : 0), picked[i] > 0 ? MdGui.glow(icon) : icon, click -> {
				int[] pk = PICKED.computeIfAbsent(p.getUniqueId(), x -> new int[4]);
				if (click.isShiftClick() && click.isRightClick()) {
					pk[t] = 0;
				} else if (click.isRightClick()) {
					pk[t] = Math.max(0, pk[t] - 1);
				} else {
					pk[t] = Math.min(Gacha.count(p, TIERS[t]), pk[t] + (click.isShiftClick() ? 10 : 1));
				}
				openLevel(p);
			});
		}
		gui.set(47, MdGui.item(Material.HOPPER, "자동 선택", NamedTextColor.AQUA, "최대 레벨까지 필요한 만큼, 좋은 티켓부터"), () -> {
			autoPick(p);
			openLevel(p);
		});
		gui.set(49, MdGui.item(add > 0 ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE, "레벨 업", add > 0 ? NamedTextColor.GREEN : NamedTextColor.GRAY,
				add > 0 ? "고른 티켓을 써서 Lv." + to[0] + "로" : "티켓을 고르세요"), () -> {
					confirmLevel(p);
					openLevel(p);
				});
		gui.set(51, MdGui.item(Material.BARRIER, "선택 초기화", NamedTextColor.RED), () -> {
			PICKED.remove(p.getUniqueId());
			openLevel(p);
		});
		// Level Boost Tickets that would raise it.
		int b = 37;
		for (int i = 0; i < BOOSTS.length && b <= 43; i++) {
			int target = (i + 1) * 10;
			int have = Gacha.count(p, BOOSTS[i]);
			if (have > 0 && target > level) {
				Gacha.Kind k = BOOSTS[i];
				gui.set(b++, MdGui.item(Material.NAME_TAG, k.name + " (" + have + "장)", k.colour, "레벨을 " + target + "로 올려요"), () -> boost(p, k, target));
			}
		}
		gui.fill().open(p);
	}

	private static void autoPick(Player p) {
		ItemStack held = p.getInventory().getItem(targetSlot(p));
		int[] pk = new int[4];
		long needed = 0;
		for (int l = level(held); l < MAX_LEVEL; l++) {
			needed += need(l);
		}
		needed -= exp(held);
		for (int i = 3; i >= 0 && needed > 0; i--) {
			int per = TIERS[i].exp();
			int use = (int) Math.min(Gacha.count(p, TIERS[i]), needed / per);
			pk[i] = use;
			needed -= (long) use * per;
		}
		// What is left goes to the smallest ticket that covers it.
		for (int i = 0; i < 4 && needed > 0; i++) {
			if (Gacha.count(p, TIERS[i]) > pk[i]) {
				int use = (int) Math.min(Gacha.count(p, TIERS[i]) - pk[i], (needed + TIERS[i].exp() - 1) / TIERS[i].exp());
				pk[i] += use;
				needed -= (long) use * TIERS[i].exp();
			}
		}
		PICKED.put(p.getUniqueId(), pk);
	}

	private static void confirmLevel(Player p) {
		int[] pk = PICKED.remove(p.getUniqueId());
		if (pk == null) {
			return;
		}
		int slot = targetSlot(p);
		ItemStack held = slot < 0 ? null : p.getInventory().getItem(slot);
		if (!levels(held)) {
			return;
		}
		int used = 0;
		int gained = 0;
		for (int i = 3; i >= 0; i--) {
			for (int n = 0; n < pk[i] && level(held) < MAX_LEVEL && Gacha.take(p, TIERS[i], 1); n++) {
				gained += addExp(held, TIERS[i].exp());
				used++;
			}
		}
		p.getInventory().setItem(slot, held);
		Missions.progress(p, "train", used);
		report(p, held, "경험치 티켓 " + used + "장", gained);
	}

	/** Uptie, as the original's screen: the four tiers and what each changes, the cost against what is held. */
	static void openUptie(Player p) {
		MdGui gui = frame(p, "무기 강화 · 동기화", false);
		if (gui == null) {
			return;
		}
		ItemStack held = p.getInventory().getItem(targetSlot(p));
		int uptie = uptie(held);
		for (int t = 1; t <= 4; t++) {
			boolean done = t <= uptie;
			List<String> lore = new ArrayList<>(tierText(held, t));
			lore.add(0, done ? "달성" : t == uptie + 1 ? "다음 단계" : "잠김");
			var icon = MdGui.item(done ? Material.LIGHT_BLUE_STAINED_GLASS : t == uptie + 1 ? Material.YELLOW_STAINED_GLASS : Material.GRAY_STAINED_GLASS,
					"동기화 " + ROMAN[t - 1], done ? NamedTextColor.AQUA : t == uptie + 1 ? NamedTextColor.YELLOW : NamedTextColor.DARK_GRAY, lore.toArray(String[]::new));
			gui.set(19 + (t - 1) * 2, done ? MdGui.glow(icon) : icon, (Runnable) null);
		}
		if (uptie < 4) {
			int[] cost = costFor(held, uptie + 1);
			int sinner = sinnerOf(held);
			int threads = Gacha.count(p, Gacha.Kind.THREAD);
			int shards = Shards.count(p, sinner);
			boolean enough = threads >= cost[0] && shards >= cost[1];
			gui.set(38, Rewards.dress(MdGui.item(Material.STRING, "끈 " + threads + " / " + cost[0], threads >= cost[0] ? NamedTextColor.GREEN : NamedTextColor.RED,
					"끈 루사이크·패스·상점에서 얻어요"), "thread"), (Runnable) null);
			if (cost[1] > 0) {
				gui.set(40, Rewards.dress(MdGui.item(Material.AMETHYST_SHARD, "에고 조각 " + shards + " / " + cost[1], shards >= cost[1] ? NamedTextColor.GREEN : NamedTextColor.RED,
						sinner >= 0 ? Gacha.SINNERS_KO[sinner] + "의 에고 조각 또는 범용 동기화 조각" : "범용 동기화 조각 (또는 아무 수감자의 조각 없음)"), "shard_universal"), (Runnable) null);
			}
			int takeoff = Gacha.count(p, Gacha.Kind.TAKEOFF_UPTIE);
			if (takeoff > 0) {
				gui.set(42, MdGui.item(Material.NETHER_STAR, Gacha.Kind.TAKEOFF_UPTIE.name + " (" + takeoff + "개)", Gacha.Kind.TAKEOFF_UPTIE.colour,
						"바로 동기화 IV로 올려요"), () -> {
							takeoff(p);
							openUptie(p);
						});
			}
			gui.set(49, MdGui.item(enough ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE, "동기화 " + ROMAN[uptie], enough ? NamedTextColor.GREEN : NamedTextColor.GRAY,
					enough ? "클릭해서 동기화" : "재료가 모자라요"), () -> {
						spin(p);
						openUptie(p);
					});
		} else {
			gui.set(49, MdGui.item(Material.NETHER_STAR, "동기화 IV (최대)", NamedTextColor.GOLD), (Runnable) null);
		}
		gui.fill().open(p);
	}

	/** What a tier gives this weapon: the skills at that Uptie for the gacha and vanilla weapons, the bonus for the plugin's own. */
	private static List<String> tierText(ItemStack held, int t) {
		List<String> out = new ArrayList<>();
		Gacha.Weapon w = Gacha.weaponOf(held);
		if (w != null) {
			for (Ego.Skill sk : w.skills(t)) {
				out.add(sk.name() + "  위력 " + sk.power() + " · 코인 " + (sk.coinPower() >= 0 ? "+" : "") + sk.coinPower() + " ×" + sk.coins());
			}
			Defense.Spec d = w.defense(t);
			if (d != null) {
				out.add(d.name() + " (" + d.type().ko + ")  위력 " + d.power() + " · 코인 +" + d.coinPower() + " ×" + d.coins());
			}
			List<String> pk = w.passiveKo().get(Math.max(0, Math.min(w.passiveKo().size() - 1, t - 1)));
			out.add("패시브 [" + w.passiveName() + "]");
			pk.forEach(x -> out.add("  " + x));
			return out;
		}
		VanillaWeapon.Kind vk = VanillaWeapon.kindOf(held);
		if (vk != null && VanillaWeapon.is(held)) {
			for (Ego.Skill sk : VanillaWeapon.skills(vk, VanillaWeapon.rank(held), t)) {
				out.add(sk.name() + "  위력 " + sk.power() + " · 코인 +" + sk.coinPower() + " ×" + sk.coins());
			}
			Defense.Spec d = VanillaWeapon.defense(vk, VanillaWeapon.rank(held), t);
			out.add(d.name() + " (" + d.type().ko + ")  위력 " + d.power() + " · 코인 +" + d.coinPower() + " ×" + d.coins());
			return out;
		}
		out.add("합 위력 +" + ((t >= 3 ? 1 : 0) + (t >= 4 ? 1 : 0)) + " · 피해 +" + 5 * (t - 1) + "%");
		if (Items.isMimicry(held)) {
			out.add("미미크리: 스킬 기본 위력이 동기화마다 올라요");
		}
		return out;
	}

	private static ItemStack target(Player p, int[] slotOut) {
		int slot = targetSlot(p);
		slotOut[0] = slot;
		return slot < 0 ? null : p.getInventory().getItem(slot);
	}

	private static void level(Player p, Gacha.Kind kind, int tickets) {
		int[] slot = new int[1];
		ItemStack held = target(p, slot);
		if (!levels(held) || level(held) >= MAX_LEVEL) {
			open(p);
			return;
		}
		int used = 0;
		int gained = 0;
		while (used < tickets && level(held) < MAX_LEVEL && Gacha.take(p, kind, 1)) {
			gained += addExp(held, kind.exp());
			used++;
		}
		p.getInventory().setItem(slot[0], held);
		Missions.progress(p, "train", used);
		report(p, held, used == 0 ? kind.name + "이 없어요" : kind.name + " " + used + "장", gained);
		open(p);
	}

	private static void levelMax(Player p) {
		int[] slot = new int[1];
		ItemStack held = target(p, slot);
		if (!levels(held)) {
			open(p);
			return;
		}
		int used = 0;
		int gained = 0;
		for (Gacha.Kind k : TICKETS) {
			while (level(held) < MAX_LEVEL && Gacha.take(p, k, 1)) {
				gained += addExp(held, k.exp());
				used++;
			}
		}
		p.getInventory().setItem(slot[0], held);
		report(p, held, used == 0 ? "경험치 티켓이 없어요" : "경험치 티켓 " + used + "장", gained);
		open(p);
	}

	private static void report(Player p, ItemStack held, String what, int gained) {
		Hud.message(p, Component.text(what + (gained > 0 ? " · 레벨 +" + gained + " (Lv." + level(held) + ")" : ""), NamedTextColor.AQUA));
		if (gained > 0) {
			p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.7F, 1.4F);
		}
	}

	private static void boost(Player p, Gacha.Kind kind, int to) {
		int[] slot = new int[1];
		ItemStack held = target(p, slot);
		if (!levels(held) || level(held) >= to || !Gacha.take(p, kind, 1)) {
			open(p);
			return;
		}
		held.editMeta(meta -> {
			meta.getPersistentDataContainer().set(LEVEL, PersistentDataType.INTEGER, to);
			meta.getPersistentDataContainer().set(EXP, PersistentDataType.INTEGER, 0);
		});
		refreshLore(held);
		p.getInventory().setItem(slot[0], held);
		report(p, held, kind.name, 1);
		open(p);
	}

	private static void takeoff(Player p) {
		int[] slot = new int[1];
		ItemStack held = target(p, slot);
		if (!levels(held) || uptie(held) >= 4 || !Gacha.take(p, Gacha.Kind.TAKEOFF_UPTIE, 1)) {
			open(p);
			return;
		}
		setUptie(held, 4);
		p.getInventory().setItem(slot[0], held);
		Hud.message(p, Component.text("동기화 4!", NamedTextColor.GOLD));
		p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0F, 1.0F);
		open(p);
	}

	private static void spin(Player p) {
		int slot = targetSlot(p);
		if (slot < 0) {
			open(p);
			return;
		}
		ItemStack held = p.getInventory().getItem(slot);
		int uptie = uptie(held);
		if (!levels(held) || uptie >= 4) {
			open(p);
			return;
		}
		int[] cost = costFor(held, uptie + 1);
		int sinner = sinnerOf(held);
		if (Gacha.count(p, Gacha.Kind.THREAD) < cost[0] || Shards.count(p, sinner) < cost[1]) {
			Hud.message(p, Component.text(Gacha.count(p, Gacha.Kind.THREAD) < cost[0] ? "끈이 모자라요" : "에고 조각이 모자라요", NamedTextColor.GRAY));
			open(p);
			return;
		}
		Gacha.take(p, Gacha.Kind.THREAD, cost[0]);
		Shards.take(p, sinner, cost[1]);
		setUptie(held, uptie + 1);
		p.getInventory().setItem(slot, held);
		Hud.message(p, Component.text("동기화 " + (uptie + 1) + "!", NamedTextColor.GOLD));
		p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0F, 1.0F);
		open(p);
	}
}
