package io.github.sharket1111.indexprescript.paper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.PiglinAbstract;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

/**
 * One battle node: waves of the Theme Pack's enemies (named after the
 * original's, from its pools), stronger with the floor, Hard mode, the party's
 * size and the Mounting Trials taken. A turn is 7 seconds: E.G.O Gifts fire at
 * turn start and end, Burn burns at turn end, and the boss strikes the ground
 * every other turn. The battle is won when the last wave falls, lost when every
 * member is down.
 */
final class MdBattle {
	private static final Map<UUID, Foe> FOES = new HashMap<>();

	/** An enemy's numbers: damage dealt and taken multipliers, its keyword, and debuffs until a tick. */
	static final class Foe {
		final MdBattle battle;
		final String name;
		final double damage;
		final double taken;
		final String keyword;
		final int potency;
		final boolean boss;
		/** The original's numbers for this enemy (null for the few the wiki has none of), and its level here. */
		MdData.EnemyData data;
		int level;
		/** Buffs from its own skills and passives, until the next turn: damage up and protection (stacks). */
		int damageUp;
		int protection;
		long buffUntil;
		/** Guarding or evading (a Guard/Evade skill) until this tick. */
		long guardUntil;
		boolean evading;
		int offenseDown;
		int defenseDown;
		long debuffUntil;
		/** For a part of an Abnormality: which part (its own HP, stagger and resistances). */
		MdData.Part part;
		/** The original's own statuses on it (Ember, Bloodfeast...), by name. */
		final Map<String, Integer> stacks = new java.util.LinkedHashMap<>();
		/** Resistance changes from new patterns (+0.5 is weaker). */
		final Map<String, Double> resistBonus = new HashMap<>();

		/** Its resistance multiplier to an attack kind or sin (1 = normal). */
		double resist(String key) {
			Map<String, Double> base = part != null ? part.resist() : data != null ? data.resist() : Map.of();
			return base.getOrDefault(key, 1.0) + resistBonus.getOrDefault(key, 0.0);
		}

		Foe(MdBattle battle, String name, double damage, double taken, String keyword, int potency, boolean boss) {
			this.battle = battle;
			this.name = name;
			this.damage = damage;
			this.taken = taken;
			this.keyword = keyword;
			this.potency = potency;
			this.boss = boss;
		}
	}

	final MdRun run;
	final MdRun.NodeType type;
	private final String eventFoe;
	/** For a hidden boss: who comes with it (or null). */
	String ally;
	private final List<String> eventGifts;
	final List<LivingEntity> mobs = new ArrayList<>();
	private int wave;
	private int waves;
	private long nextTurn;
	private int turn;
	private int tick;
	private BossBar bar;
	private LivingEntity boss;
	private boolean over;

	MdBattle(MdRun run, MdRun.NodeType type, String eventFoe, List<String> eventGifts) {
		this.run = run;
		this.type = type;
		this.eventFoe = eventFoe;
		this.eventGifts = eventGifts;
	}

	/** The enemy this battle was started for (an event's Abnormality, a hidden boss), or null. */
	String eventFoe() {
		return eventFoe;
	}

	static Foe foe(LivingEntity entity) {
		return FOES.get(entity.getUniqueId());
	}

	MdWorld.Room room() {
		return type == MdRun.NodeType.BOSS || type == MdRun.NodeType.HIDDEN ? MdWorld.Room.BOSS : MdWorld.Room.BATTLE;
	}

	void start() {
		waves = switch (type) {
			case NORMAL, RISKY -> 2;
			default -> 1;
		};
		bar = org.bukkit.Bukkit.createBossBar(type.ko, type == MdRun.NodeType.BOSS ? BarColor.RED : type == MdRun.NodeType.HIDDEN ? BarColor.PURPLE : BarColor.YELLOW,
				BarStyle.SEGMENTED_10);
		for (Player p : run.players()) {
			bar.addPlayer(p);
		}
		nextTurn = CaduceusListener.now() + 40;
		spawnWave();
	}

	// --- enemies --------------------------------------------------------------------------------

	private void spawnWave() {
		wave++;
		int players = Math.max(1, run.fighters().size());
		switch (type) {
			case NORMAL -> spawnMany("normal", 2 + players, false);
			case RISKY -> spawnMany("risky", 2 + players, false);
			case FOCUSED -> spawn(pick("focused", "normal"), Kind.FOCUSED);
			case ABNO -> spawn(eventFoe != null ? new MdData.Enemy(eventFoe, "waw") : pick("focused", "miniboss"), Kind.ABNO);
			case BOSS -> {
				MdData.Enemy b = run.theme.bosses().isEmpty() ? new MdData.Enemy(run.theme.boss().isBlank() ? "층의 주인" : run.theme.boss(), "human")
						: run.theme.bosses().get(run.random.nextInt(run.theme.bosses().size()));
				boss = spawn(b, Kind.BOSS);
				spawnMany("normal", Math.max(1, players - 1), false);
			}
			case HIDDEN -> {
				boss = spawn(new MdData.Enemy(eventFoe, "human"), Kind.BOSS);
				if (ally != null) {
					spawn(new MdData.Enemy(ally, "human"), Kind.FOCUSED);
				}
			}
			default -> {
			}
		}
		for (Player p : run.fighters()) {
			p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, SoundCategory.HOSTILE, 0.6F, 1.4F);
		}
		announce();
		MdGifts.trigger(run, "combat_start", null, null);
	}

	private void spawnMany(String pool, int count, boolean ignored) {
		for (int i = 0; i < count; i++) {
			spawn(pick(pool, "normal"), type == MdRun.NodeType.RISKY ? Kind.RISKY : Kind.NORMAL);
		}
	}

	private MdData.Enemy pick(String pool, String fallback) {
		List<MdData.Enemy> list = run.theme.enemies().getOrDefault(pool, List.of());
		if (list.isEmpty()) {
			list = run.theme.enemies().getOrDefault(fallback, List.of());
		}
		if (list.isEmpty()) {
			for (List<MdData.Enemy> any : run.theme.enemies().values()) {
				if (!any.isEmpty()) {
					list = any;
					break;
				}
			}
		}
		return list.isEmpty() ? new MdData.Enemy(run.theme.name() + "의 적", "human") : list.get(run.random.nextInt(list.size()));
	}

	enum Kind {
		NORMAL(24, 1.0, 1.0F), RISKY(34, 1.15, 1.0F), FOCUSED(110, 1.25, 1.35F), ABNO(140, 1.3, 1.4F), BOSS(260, 1.4, 1.6F);

		final double health;
		final double damage;
		final float scale;

		Kind(double health, double damage, float scale) {
			this.health = health;
			this.damage = damage;
			this.scale = scale;
		}
	}

	private LivingEntity spawn(MdData.Enemy enemy, Kind kind) {
		MdData.EnemyData data = MdData.enemy(enemy.name());
		Location at = MdWorld.spawnSpot(run.slot, room(), run.random);
		if (kind == Kind.BOSS || kind == Kind.FOCUSED || kind == Kind.ABNO) {
			at = MdWorld.centre(run.slot).add(0, 0, room().half / 2.0);
		}
		// The mob is only a hitbox under the enemy's drawn body (MdLooks); a husk does not burn in daylight.
		LivingEntity mob = (LivingEntity) at.getWorld().spawnEntity(at, EntityType.HUSK);
		if (mob instanceof org.bukkit.entity.Ageable age) {
			age.setAdult();
		}
		int players = Math.max(1, run.fighters().size());
		// Mounting Trials (bosses have more of their own) and the Vitality adversity.
		double trialHp = (1 + (MdGifts.trialStat(run, "max_hp") + (kind == Kind.BOSS ? MdGifts.trialStat(run, "boss_max_hp") : 0)) / 100.0)
				* (1 + run.adv("vitality") / 100.0) - 1;
		int level = level();
		// The original's HP at this level, in the plugin's scale (a heart point is 10 of the original's HP).
		double health = data != null ? data.hpAt(level) / TotalDamage.SCALE * (1 + 0.6 * (players - 1)) * (1 + trialHp)
				: kind.health * (1 + 0.45 * (run.floor - 1)) * (run.floorHard ? 1.25 : 1.0) * (1 + 0.6 * (players - 1)) * (1 + trialHp);
		AttributeInstance max = mob.getAttribute(Attribute.MAX_HEALTH);
		if (max != null) {
			max.setBaseValue(Math.min(2048, health));
			mob.setHealth(max.getValue());
		}
		AttributeInstance follow = mob.getAttribute(Attribute.FOLLOW_RANGE);
		if (follow != null) {
			follow.setBaseValue(48);
		}
		if (kind == Kind.BOSS) {
			AttributeInstance knock = mob.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
			if (knock != null) {
				knock.setBaseValue(0.8);
			}
		}
		mob.customName(Component.text(enemy.name(), kind == Kind.BOSS ? TextColor.color(0xE5574B) : kind == Kind.NORMAL ? NamedTextColor.WHITE
				: NamedTextColor.GOLD));
		mob.setCustomNameVisible(true);
		mob.setRemoveWhenFarAway(false);
		mob.setPersistent(true);
		mob.setCanPickupItems(false);
		if (mob instanceof PiglinAbstract piglin) {
			piglin.setImmuneToZombification(true);
		}
		double trialDamage = MdGifts.trialStat(run, "damage_dealt") + 2 * MdGifts.trialStat(run, "offense_level") + 5 * (MdGifts.trialStat(run, "clash_power")
				+ MdGifts.trialStat(run, "final_power") + MdGifts.trialStat(run, "base_power") + MdGifts.trialStat(run, "plus_coin_power"));
		double damage = kind.damage * (1 + 0.2 * (run.floor - 1)) * (run.floorHard ? 1.2 : 1.0) * (1 + trialDamage / 100.0) * (1 + 0.02 * run.trials.size());
		double taken = (1 + MdGifts.trialStat(run, "damage_taken") / 100.0) / (1 + 0.03 * MdGifts.trialStat(run, "defense_level"));
		String keyword = run.theme.keyword();
		if (keyword == null || keyword.equals("poise") || keyword.equals("charge")) {
			keyword = List.of("burn", "bleed", "tremor", "rupture", "sinking").get(Math.floorMod(run.theme.id().hashCode() + mobs.size(), 5));
		}
		Foe foe = new Foe(this, enemy.name(), data != null ? 1 + trialDamage / 100.0 : damage, taken, keyword, 1 + run.floor / 2 + (kind == Kind.BOSS ? 2 : 0),
				kind == Kind.BOSS);
		foe.data = data;
		foe.level = level;
		FOES.put(mob.getUniqueId(), foe);
		MdLooks.dress(mob, foe, kind);
		MdFoes.setup(this, mob, foe, kind, null);
		if (run.adv("shield") > 0) {
			// Shield Generation: 3000 of the original's HP, no more than half its own.
			double shield = Math.min(3000.0 / TotalDamage.SCALE, health * 0.5);
			AttributeInstance cap = mob.getAttribute(Attribute.MAX_ABSORPTION);
			if (cap != null) {
				cap.setBaseValue(shield);
				mob.setAbsorptionAmount(shield);
			}
		}
		// An Abnormality's other parts (wings, arms, head...): hitboxes of their own that follow the core.
		if (data != null && data.parts().size() > 1) {
			double share = health / Math.max(1, data.hpAt(level) / TotalDamage.SCALE);
			for (MdData.Part partData : data.parts().subList(1, data.parts().size())) {
				LivingEntity part = (LivingEntity) at.getWorld().spawnEntity(mob.getLocation(), EntityType.HUSK);
				if (part instanceof org.bukkit.entity.Mob pm) {
					pm.setAI(false);
				}
				part.setGravity(false);
				part.setCollidable(false);
				part.setRemoveWhenFarAway(false);
				part.setPersistent(true);
				AttributeInstance pmax = part.getAttribute(Attribute.MAX_HEALTH);
				if (pmax != null) {
					pmax.setBaseValue(Math.min(2048, Math.max(1, partData.hpAt(level, data.level()) / TotalDamage.SCALE * share)));
					part.setHealth(pmax.getValue());
				}
				Foe pf = new Foe(this, enemy.name() + " · " + partData.name(), foe.damage, taken, keyword, foe.potency, foe.boss);
				pf.data = data;
				pf.level = level;
				pf.part = partData;
				FOES.put(part.getUniqueId(), pf);
				MdLooks.dressPart(part, mob, partData.name(), partData.icon(), run.theme.color());
				MdFoes.setup(this, part, pf, kind, mob);
			}
		}
		mob.getWorld().spawnParticle(Particle.REVERSE_PORTAL, mob.getLocation().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.05);
		mobs.add(mob);
		return mob;
	}

	/** Enemy level on this floor: 41 on floor 1 up to 65 on floor 5, +5 on Hard, +1.5 for each Mounting Trial (their Level Boost). */
	int level() {
		int f = Math.max(1, run.floor);
		// 41 on floor 1 to 65 on floor 5; then 3 a floor to 80 on floor 10, and 2 a floor to 90 on floor 15.
		int base = f <= 5 ? 35 + 6 * f : f <= 10 ? 65 + 3 * (f - 5) : 80 + 2 * (f - 10);
		return base + (run.floorHard ? 5 : 0) + (int) Math.round(1.5 * Math.min(10, run.trials.size())) + run.adv("level");
	}

	/** Tells the party who they face: each enemy's weaknesses and resistances (the original's multipliers). */
	private void announce() {
		java.util.Set<String> seen = new java.util.HashSet<>();
		for (LivingEntity m : mobs) {
			Foe f = foe(m);
			if (f == null || f.data == null || !seen.add(f.name)) {
				continue;
			}
			StringBuilder weak = new StringBuilder();
			StringBuilder strong = new StringBuilder();
			for (Map.Entry<String, Double> r : f.data.resist().entrySet()) {
				String ko = MdData.KEYWORD_KO.getOrDefault(r.getKey(), MdData.SIN_KO.getOrDefault(r.getKey(), r.getKey()));
				if (r.getValue() > 1.0) {
					weak.append(weak.length() == 0 ? "" : ", ").append(ko).append(" ×").append(r.getValue());
				} else if (r.getValue() < 1.0) {
					strong.append(strong.length() == 0 ? "" : ", ").append(ko).append(" ×").append(r.getValue());
				}
			}
			run.broadcast(Component.text(f.name + " (Lv." + f.level + ")", NamedTextColor.WHITE)
					.append(Component.text(weak.length() > 0 ? "  약점: " + weak : "", NamedTextColor.GREEN))
					.append(Component.text(strong.length() > 0 ? "  내성: " + strong : "", NamedTextColor.RED)));
		}
	}

	// --- running ----------------------------------------------------------------------------------

	void tick() {
		if (over) {
			return;
		}
		tick++;
		long now = CaduceusListener.now();
		mobs.removeIf(m -> !m.isValid() || m.isDead());
		List<Player> fighters = run.fighters();
		if (fighters.isEmpty()) {
			return;
		}
		if (tick % 20 == 0) {
			for (LivingEntity m : mobs) {
				if (m instanceof Mob mob && (mob.getTarget() == null || !(mob.getTarget() instanceof Player t) || run.downed.contains(t.getUniqueId()))) {
					mob.setTarget(nearest(m, fighters));
				}
				if (MdWorld.fell(m.getLocation())) {
					m.teleport(MdWorld.centre(run.slot));
				}
			}
		}
		// Burn on everyone in the fight, as turns pass for them.
		for (LivingEntity m : List.copyOf(mobs)) {
			burn(m, now);
		}
		for (Player p : fighters) {
			burn(p, now);
		}
		boolean turnStart = false;
		if (now >= nextTurn) {
			nextTurn = now + CombatState.TURN_TICKS;
			if (turn > 0) {
				MdGifts.trigger(run, "turn_end", null, null);
			}
			if (turn > 0) {
				adversities(fighters);
			}
			turn++;
			turnStart = true;
			MdGifts.trigger(run, "turn_start", null, null);
		}
		MdFoes.tick(this, fighters, now, turn, turnStart);
		updateBar();
		if (mobs.isEmpty()) {
			if (wave < waves) {
				spawnWave();
			} else {
				win();
			}
		}
	}

	/** A turn ends: what the adversities taken do to the members. */
	private void adversities(List<Player> fighters) {
		int fire = run.adv("fire");
		int mental = run.adv("mental");
		int cap = run.advMin("fatigue", 99);
		boolean status = run.adv("status") > 0;
		for (Player p : fighters) {
			if (fire > 0) {
				// Mark of Fire I: 5, +3 a turn, to 20; II: 10, +5 a turn, to 30 (both when both are taken).
				double damage = 0;
				for (MdAdversity a : run.adversities) {
					if (a.kind.equals("fire")) {
						damage += a.value == 1 ? Math.min(20, 5 + 3 * (turn - 1)) : Math.min(30, 10 + 5 * (turn - 1));
					}
				}
				Damage.dealFixed(p, damage / TotalDamage.SCALE, null);
				p.getWorld().spawnParticle(Particle.FLAME, p.getLocation().add(0, 1, 0), 8, 0.3, 0.5, 0.3, 0.01);
			}
			if (mental > 0) {
				Sanity.lose(p, mental);
			}
			if (Sanity.of(p) > cap) {
				Sanity.set(p, cap);
			}
			if (status) {
				String keyword = List.of("burn", "bleed", "tremor", "rupture", "sinking").get(run.random.nextInt(5));
				MdGifts.inflictKeyword(p, keyword, 5, 1);
			}
		}
	}

	private void burn(LivingEntity e, long now) {
		int damage = CombatState.of(e).tickBurn(now);
		if (damage > 0 && e.isValid() && !e.isDead()) {
			Damage.dealFixed(e, damage * Rupture.DAMAGE_PER_POTENCY, null);
			e.getWorld().spawnParticle(Particle.FLAME, e.getLocation().add(0, e.getHeight() / 2, 0), 12 + damage, 0.3, 0.5, 0.3, 0.02);
		}
	}

	private static Player nearest(LivingEntity from, List<Player> players) {
		Player best = null;
		double bestD = Double.MAX_VALUE;
		for (Player p : players) {
			double d = p.getLocation().distanceSquared(from.getLocation());
			if (d < bestD) {
				bestD = d;
				best = p;
			}
		}
		return best;
	}

	private void updateBar() {
		if (bar == null) {
			return;
		}
		if (boss != null && boss.isValid()) {
			AttributeInstance max = boss.getAttribute(Attribute.MAX_HEALTH);
			bar.setTitle(foe(boss) == null ? "보스" : foe(boss).name);
			bar.setProgress(Math.max(0, Math.min(1, boss.getHealth() / (max == null ? 1 : max.getValue()))));
		} else {
			bar.setTitle(type.ko + " · 웨이브 " + wave + "/" + waves + " · 남은 적 " + mobs.size() + " · " + turn + "턴");
			bar.setProgress(Math.max(0, Math.min(1, mobs.size() / 8.0)));
		}
	}

	private void win() {
		over = true;
		stop();
		for (Player p : run.players()) {
			p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7F, 1.2F);
			p.removePotionEffect(PotionEffectType.SLOWNESS);
			p.removePotionEffect(PotionEffectType.WEAKNESS);
		}
		run.battleWon(type, eventGifts);
	}

	/** A boss calls {@code count} of the Theme Pack's plain enemies (no more than 8 enemies at once). */
	void reinforce(int count) {
		for (int i = 0; i < count && mobs.size() < 8; i++) {
			spawn(pick("normal", "risky"), Kind.NORMAL);
		}
	}

	void stop() {
		over = true;
		for (LivingEntity m : mobs) {
			FOES.remove(m.getUniqueId());
			MdFoes.forget(m);
			m.remove();
		}
		mobs.clear();
		if (bar != null) {
			bar.removeAll();
		}
	}

	/** A debuff from a gift on an enemy: weaker attacks or softer defence until next turn, slowed, or weakened. */
	static void debuff(LivingEntity target, String what, int amount) {
		Foe foe = foe(target);
		long now = CaduceusListener.now();
		switch (what) {
			case "offense_level_down" -> {
				if (foe != null) {
					foe.offenseDown += amount;
					foe.debuffUntil = now + CombatState.TURN_TICKS;
				}
			}
			case "defense_level_down", "fragile" -> {
				if (foe != null) {
					foe.defenseDown += amount;
					foe.debuffUntil = now + CombatState.TURN_TICKS;
				}
			}
			case "bind" -> target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, CombatState.TURN_TICKS, Math.min(3, amount - 1)));
			case "paralysis" -> target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, CombatState.TURN_TICKS, Math.min(3, amount - 1)));
			default -> {
			}
		}
	}

	static void forget(LivingEntity entity) {
		FOES.remove(entity.getUniqueId());
		MdFoes.forget(entity);
	}
}
