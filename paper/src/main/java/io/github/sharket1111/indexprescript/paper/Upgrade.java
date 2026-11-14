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
 * A weapon's level (kept on the item; 40 when new, up to 60) rises with EXP
 * tickets, and is its Offense and Defense Level: damage is scaled as in the
 * original, by 1 + (offense - defense) / (|offense - defense| + 25), with the
 * attacker's weapon level (a Mirror Dungeon enemy's level) against the
 * target's (the player holding no weapon, or anything else, counts 40).
 *
 * Uptie (1-4, Threads) makes a weapon stronger: the two-star weapons play
 * their Identity's own skills and passive at that Uptie; the plugin's own
 * weapons gain clash power (+1 at Uptie 3, +2 at 4) and damage (+5% an Uptie
 * past 1), which carries their passives and effects too. The E.G.O's
 * Threadspin works the same way on its Awakening skill (Ego).
 */
final class Upgrade implements Listener {
	static final int START_LEVEL = 40;
	static final int MAX_LEVEL = 60;
	static final int NEUTRAL_LEVEL = 40;
	static final int EXP_PER_TICKET = 100;
	/** Threads to reach Uptie 2, 3, 4 (two-star; three-star and E.G.O cost double). */
	static final int[] THREADS = {0, 0, 20, 50, 100};

	private static final NamespacedKey LEVEL = new NamespacedKey(Items.NAMESPACE, "weapon_level");
	private static final NamespacedKey EXP = new NamespacedKey(Items.NAMESPACE, "weapon_exp");
	private static final NamespacedKey UPTIE = new NamespacedKey(Items.NAMESPACE, "weapon_uptie");

	/** Whether the stack is a weapon that levels: the plugin's own or a gacha weapon. */
	static boolean levels(ItemStack stack) {
		return stack != null && !stack.isEmpty() && (Items.isWeapon(stack) || Gacha.weaponOf(stack) != null);
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

	/** EXP from one level to the next. */
	static int need(int level) {
		return 50 + level * 5;
	}

	static int threadsFor(ItemStack stack, int toUptie) {
		int base = THREADS[Math.max(0, Math.min(4, toUptie))];
		return Gacha.weaponOf(stack) != null ? base : base * 2;
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

	private static int levelOf(LivingEntity unit) {
		if (unit instanceof Player p) {
			return level(p.getInventory().getItemInMainHand());
		}
		MdBattle.Foe foe = MdBattle.foe(unit);
		return foe != null && foe.level > 0 ? foe.level : NEUTRAL_LEVEL;
	}

	/** The info lines of a gacha weapon's item (the plugin's own keep their own lore; the menu shows theirs). */
	static void refreshLore(ItemStack stack) {
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
		double scale = levelScale(levelOf(attacker), levelOf(victim));
		if (attacker instanceof Player p) {
			scale *= uptieDamage(p);
		}
		event.setDamage(event.getDamage() * scale);
	}

	// --- the menu --------------------------------------------------------------------------------------

	/** The upgrade menu for the weapon in hand: levels with EXP tickets, Uptie with Threads. */
	static void open(Player p) {
		ItemStack held = p.getInventory().getItemInMainHand();
		MdGui gui = new MdGui(3, "무기 강화");
		if (!levels(held)) {
			gui.set(13, MdGui.item(Material.BARRIER, "강화할 무기를 손에 들고 여세요", NamedTextColor.RED, "이 플러그인의 무기나 추출로 얻은 무기"),
					(Runnable) null);
			gui.fill().open(p);
			return;
		}
		int level = level(held);
		int uptie = uptie(held);
		int tickets = Gacha.count(p, Gacha.Kind.EXP);
		int threads = Gacha.count(p, Gacha.Kind.THREAD);
		ItemStack info = held.clone();
		List<Component> lore = new ArrayList<>();
		lore.add(line("Lv." + level + (level < MAX_LEVEL ? " (EXP " + exp(held) + "/" + need(level) + ")" : " (최대)"), NamedTextColor.GOLD));
		lore.add(line("공격 레벨 " + level + " · 방어 레벨 " + level, NamedTextColor.GRAY));
		lore.add(line("동기화 " + uptie + "/4" + (Gacha.weaponOf(held) == null && uptie > 1 ? " (합 위력 +" + ((uptie >= 3 ? 1 : 0) + (uptie >= 4 ? 1 : 0))
				+ ", 피해 +" + 5 * (uptie - 1) + "%)" : ""), NamedTextColor.AQUA));
		info.editMeta(meta -> meta.lore(lore));
		gui.set(4, info, (Runnable) null);
		gui.set(11, MdGui.item(Material.EXPERIENCE_BOTTLE, "경험치 티켓 1장", NamedTextColor.AQUA, "EXP +" + EXP_PER_TICKET, "가진 티켓 " + tickets + "장"),
				() -> level(p, 1));
		gui.set(12, MdGui.item(Material.EXPERIENCE_BOTTLE, "경험치 티켓 10장", NamedTextColor.AQUA, "EXP +" + EXP_PER_TICKET * 10, "가진 티켓 " + tickets + "장"),
				() -> level(p, 10));
		gui.set(13, MdGui.item(Material.EXPERIENCE_BOTTLE, "최대 레벨까지", NamedTextColor.AQUA, "가진 티켓으로 올릴 수 있는 만큼"), () -> level(p, 999));
		if (uptie < 4) {
			int cost = threadsFor(held, uptie + 1);
			gui.set(15, MdGui.item(Material.STRING, "동기화 " + (uptie + 1) + " (끈 " + cost + ")", NamedTextColor.GOLD, "가진 끈 " + threads + "개",
					Gacha.weaponOf(held) != null ? "원작 인격의 동기화 " + (uptie + 1) + " 스킬·패시브가 돼요" : "합 위력과 피해가 올라요"), () -> spin(p));
		} else {
			gui.set(15, MdGui.item(Material.NETHER_STAR, "동기화 4 (최대)", NamedTextColor.GOLD), (Runnable) null);
		}
		gui.set(22, MdGui.item(Material.ARROW, "뒤로", NamedTextColor.WHITE), () -> Ego.Menu.open(p));
		gui.fill().open(p);
	}

	private static void level(Player p, int tickets) {
		ItemStack held = p.getInventory().getItemInMainHand();
		if (!levels(held) || level(held) >= MAX_LEVEL) {
			open(p);
			return;
		}
		int used = 0;
		int gained = 0;
		while (used < tickets && level(held) < MAX_LEVEL && Gacha.take(p, Gacha.Kind.EXP, 1)) {
			gained += addExp(held, EXP_PER_TICKET);
			used++;
		}
		p.getInventory().setItemInMainHand(held);
		if (used == 0) {
			Hud.message(p, Component.text("경험치 티켓이 없어요", NamedTextColor.GRAY));
		} else {
			Hud.message(p, Component.text("경험치 티켓 " + used + "장 사용" + (gained > 0 ? " · 레벨 +" + gained + " (Lv." + level(held) + ")" : ""), NamedTextColor.AQUA));
			p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.7F, 1.4F);
		}
		open(p);
	}

	private static void spin(Player p) {
		ItemStack held = p.getInventory().getItemInMainHand();
		int uptie = uptie(held);
		if (!levels(held) || uptie >= 4) {
			open(p);
			return;
		}
		if (!Gacha.take(p, Gacha.Kind.THREAD, threadsFor(held, uptie + 1))) {
			Hud.message(p, Component.text("끈이 모자라요", NamedTextColor.GRAY));
			open(p);
			return;
		}
		setUptie(held, uptie + 1);
		p.getInventory().setItemInMainHand(held);
		Hud.message(p, Component.text("동기화 " + (uptie + 1) + "!", NamedTextColor.GOLD));
		p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0F, 1.0F);
		open(p);
	}
}
