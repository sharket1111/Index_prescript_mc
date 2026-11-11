package io.github.sharket1111.indexprescript.paper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.SmallFireball;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

/**
 * The Mirror Dungeon's enemies fight like the original's:
 * <ul>
 * <li><b>Skills and clashes.</b> Risky, focused, Abnormality and boss enemies
 * have skills with coins. They aim one at a member every few seconds; a member
 * holding one of the plugin's weapons has 2 seconds to answer with a skill and
 * clash (the plugin's own clash: coins, power, broken coins). Unanswered, the
 * enemy closes in and strikes coin by coin. They also answer the members'
 * skills aimed at them, like a clash dummy.</li>
 * <li><b>Boss patterns.</b> Each boss has three of seven patterns (fixed by its
 * name), each shown on the ground a second before it lands, every other turn,
 * and becomes enraged once at half health.</li>
 * <li><b>Stagger.</b> Enemies stagger as their health crosses thresholds
 * (50% for plain enemies; 75/50/25% for bosses): for a turn they cannot act
 * and take double damage. A Tremor Burst raises the next threshold by its
 * potency (%), so they stagger sooner. The name tag shows health and the next
 * threshold.</li>
 * </ul>
 */
final class MdFoes {
	private static final Map<UUID, Ai> AI = new ConcurrentHashMap<>();

	enum Pattern {
		SLAM("대지 강타"), CHARGE("돌진"), SPIN("회전 베기"), VOLLEY("탄막"), SUMMON("소환"), LEAP("도약 강타"), NOVA("키워드 폭발");

		final String ko;

		Pattern(String ko) {
			this.ko = ko;
		}
	}

	static final class Ai {
		final LivingEntity mob;
		final MdBattle.Foe foe;
		final MdBattle.Kind kind;
		final List<SkillCard> cards = new ArrayList<>();
		final List<Pattern> patterns = new ArrayList<>();
		final List<Double> thresholds = new ArrayList<>();
		long nextSkill;
		boolean acting;
		boolean enraged;
		int patternIndex;
		/** Health fraction at or under which the next stagger comes (Tremor raises it). */
		double nextThreshold() {
			return thresholds.isEmpty() ? -1 : thresholds.get(0);
		}

		Ai(LivingEntity mob, MdBattle.Foe foe, MdBattle.Kind kind) {
			this.mob = mob;
			this.foe = foe;
			this.kind = kind;
		}
	}

	private MdFoes() {
	}

	static Ai of(LivingEntity entity) {
		return entity == null ? null : AI.get(entity.getUniqueId());
	}

	// --- setting up -------------------------------------------------------------------------------

	private static final Map<String, List<String>> NAMES = Map.of(
			"burn", List.of("불꽃 베기", "잿더미", "작열", "타오르는 일격"),
			"bleed", List.of("찢어발기기", "피의 연격", "난도질", "베어 가르기"),
			"tremor", List.of("진동 강타", "울림", "땅 울리기", "떨림"),
			"rupture", List.of("파쇄", "꿰뚫기", "균열", "부수기"),
			"sinking", List.of("가라앉히기", "침전", "검은 물결", "잠식"));
	private static final List<String> GENERIC = List.of("강타", "연속 공격", "돌진 베기", "내려치기");

	static void setup(MdBattle battle, LivingEntity mob, MdBattle.Foe foe, MdBattle.Kind kind) {
		Ai ai = new Ai(mob, foe, kind);
		int floor = Math.max(1, battle.run.floor);
		switch (kind) {
			case NORMAL -> ai.thresholds.add(0.5);
			case RISKY -> ai.thresholds.add(0.6);
			case FOCUSED -> ai.thresholds.addAll(List.of(0.65, 0.3));
			case ABNO -> ai.thresholds.addAll(List.of(0.7, 0.4));
			case BOSS -> ai.thresholds.addAll(List.of(0.75, 0.5, 0.25));
		}
		if (kind != MdBattle.Kind.NORMAL) {
			List<String> names = new ArrayList<>(NAMES.getOrDefault(foe.keyword, GENERIC));
			names.addAll(GENERIC);
			int count = kind == MdBattle.Kind.RISKY ? 1 : kind == MdBattle.Kind.BOSS ? 3 : 2;
			int seed = Math.floorMod(foe.name.hashCode(), 97);
			for (int i = 0; i < count; i++) {
				int coins = kind == MdBattle.Kind.BOSS ? 3 + (i == 2 ? 1 : 0) : kind == MdBattle.Kind.RISKY ? 2 : 2 + i;
				int base = (kind == MdBattle.Kind.BOSS ? 5 : kind == MdBattle.Kind.RISKY ? 3 : 4) + floor / 2 + i;
				int power = (kind == MdBattle.Kind.BOSS ? 3 : 2) + floor / 3;
				String types = kind == MdBattle.Kind.BOSS ? (i == 2 ? "RNRN" : "NRN") : "N".repeat(coins);
				String name = names.get((seed + i * 3) % names.size());
				ai.cards.add(new SkillCard(name, TextColor.color(battle.run.theme.color()), base, power, coins,
						"적중 시 " + MdData.KEYWORD_KO.getOrDefault(foe.keyword, "") + " 위력 " + foe.potency, coins * 8 + 10, true, types));
			}
		}
		if (kind == MdBattle.Kind.BOSS) {
			List<Pattern> all = new ArrayList<>(List.of(Pattern.values()));
			int seed = Math.floorMod(foe.name.hashCode(), 1 << 20);
			for (int i = 0; i < 3 && !all.isEmpty(); i++) {
				ai.patterns.add(all.remove(seed % all.size()));
				seed = seed / 7 + 13;
			}
		}
		ai.nextSkill = CaduceusListener.now() + 60 + MdRun.rng().nextInt(60);
		AI.put(mob.getUniqueId(), ai);
		label(ai);
	}

	static void forget(LivingEntity mob) {
		AI.remove(mob.getUniqueId());
	}

	// --- every tick ----------------------------------------------------------------------------------

	static void tick(MdBattle battle, List<Player> fighters, long now, int turn, boolean turnStart) {
		for (LivingEntity mob : List.copyOf(battle.mobs)) {
			Ai ai = of(mob);
			if (ai == null || !mob.isValid()) {
				continue;
			}
			boolean staggered = CombatState.of(mob).staggered(now);
			if (mob instanceof Mob m) {
				m.setAware(!staggered && !ai.acting);
			}
			if (now % 10 == 0) {
				label(ai);
			}
			if (staggered || ai.acting) {
				continue;
			}
			if (ai.kind == MdBattle.Kind.BOSS) {
				double hp = health(mob);
				if (!ai.enraged && hp <= 0.5) {
					ai.enraged = true;
					ai.foe.rage = 1.25;
					mob.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 0, false, false));
					battle.run.broadcast(Component.text(ai.foe.name + "이(가) 광폭해졌어요! (주는 피해 +25%)", NamedTextColor.RED));
					mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 0.8F, 1.2F);
				}
				if (turnStart && turn % 2 == 0 && !ai.patterns.isEmpty()) {
					Pattern p = ai.patterns.get(ai.patternIndex++ % ai.patterns.size());
					pattern(battle, ai, p, fighters);
					continue;
				}
			}
			if (!ai.cards.isEmpty() && now >= ai.nextSkill && !Clash.busy(mob)) {
				Player target = nearest(mob, fighters, 12);
				if (target != null) {
					useSkill(battle, ai, target);
				}
			}
		}
	}

	private static double health(LivingEntity mob) {
		var max = mob.getAttribute(Attribute.MAX_HEALTH);
		return mob.getHealth() / (max == null ? mob.getHealth() : max.getValue());
	}

	private static Player nearest(LivingEntity mob, List<Player> players, double range) {
		Player best = null;
		double bestD = range * range;
		for (Player p : players) {
			double d = p.getLocation().distanceSquared(mob.getLocation());
			if (d < bestD) {
				bestD = d;
				best = p;
			}
		}
		return best;
	}

	private static int cooldown(Ai ai) {
		return switch (ai.kind) {
			case BOSS -> 120;
			case FOCUSED, ABNO -> 170;
			default -> 240;
		};
	}

	// --- skills ------------------------------------------------------------------------------------------

	private static SkillCard nextCard(Ai ai) {
		return ai.cards.get(MdRun.rng().nextInt(ai.cards.size()));
	}

	private static void useSkill(MdBattle battle, Ai ai, Player target) {
		SkillCard card = nextCard(ai);
		ai.nextSkill = CaduceusListener.now() + cooldown(ai) + MdRun.rng().nextInt(40);
		ai.acting = true;
		boolean started = Clash.requestFrom(ai.mob, target, card, outcome -> attack(battle, ai, card, outcome.target()));
		if (!started) {
			ai.acting = false;
		} else {
			// The clash (if any) or the attack ends the action; a safety release in case neither comes.
			Bukkit.getScheduler().runTaskLater(IndexPrescriptPlugin.instance(), () -> ai.acting = false, Clash.WINDOW + card.ticks() + 60L);
		}
	}

	/** Whether {@code unit} is a dungeon enemy that can answer a skill aimed at it now. */
	static boolean canAnswer(LivingEntity unit) {
		Ai ai = of(unit);
		return ai != null && !ai.cards.isEmpty() && !ai.acting && unit.isValid() && !CombatState.of(unit).staggered(CaduceusListener.now());
	}

	static SkillCard answerCard(LivingEntity unit) {
		Ai ai = of(unit);
		ai.acting = true;
		ai.nextSkill = CaduceusListener.now() + cooldown(ai);
		SkillCard card = nextCard(ai);
		// Released when its attack ends; this covers a lost clash, which never attacks.
		Bukkit.getScheduler().runTaskLater(IndexPrescriptPlugin.instance(), () -> ai.acting = false, card.ticks() + 120L);
		return card;
	}

	/** The enemy's skill goes off at {@code target}: it closes in and strikes coin by coin. */
	static void attack(LivingEntity unit, LivingEntity target) {
		Ai ai = of(unit);
		if (ai == null) {
			return;
		}
		attack(ai.foe.battle, ai, Clash.cardOf(unit), target);
	}

	private static void attack(MdBattle battle, Ai ai, SkillCard card, LivingEntity target) {
		LivingEntity mob = ai.mob;
		if (card == null || !(target instanceof Player victim) || !mob.isValid()) {
			ai.acting = false;
			return;
		}
		// Close in: appear at the target's side in a streak of the theme's colour.
		Vector dir = victim.getLocation().toVector().subtract(mob.getLocation().toVector()).setY(0);
		if (dir.lengthSquared() > 4) {
			Location to = victim.getLocation().clone().subtract(dir.normalize().multiply(1.6));
			to.setDirection(victim.getLocation().toVector().subtract(to.toVector()));
			Effects.dashTrail(mob.getWorld(), mob.getLocation().toVector(), to.toVector());
			mob.teleport(to);
		}
		new BukkitRunnable() {
			int tick;
			int coin;

			@Override
			public void run() {
				if (!mob.isValid() || mob.isDead() || !victim.isOnline() || CombatState.of(mob).staggered(CaduceusListener.now()) || coin >= card.coins()) {
					ai.acting = false;
					cancel();
					return;
				}
				if (tick % 8 == 4) {
					if (Clash.coin(mob, coin)) {
						strike(battle, ai, victim, card);
					}
					coin++;
				}
				tick++;
			}
		}.runTaskTimer(IndexPrescriptPlugin.instance(), 4L, 1L);
	}

	private static void strike(MdBattle battle, Ai ai, Player victim, SkillCard card) {
		LivingEntity mob = ai.mob;
		mob.swingMainHand();
		Location at = victim.getLocation().add(0, 1, 0);
		at.getWorld().spawnParticle(Particle.SWEEP_ATTACK, at, 1);
		at.getWorld().spawnParticle(Particle.DUST, at, 14, 0.4, 0.5, 0.4, 0, new Particle.DustOptions(Color.fromRGB(battle.run.theme.color()), 1.4F));
		at.getWorld().playSound(at, Sound.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.HOSTILE, 0.9F, 0.8F);
		if (victim.getLocation().distanceSquared(mob.getLocation()) > 16 || battle.run.downed.contains(victim.getUniqueId())) {
			return;
		}
		double amount = (2.0 + battle.run.floor * 0.6 + card.coinPower() * 0.3) * MdGifts.taken(battle.run, victim, mob);
		Damage.deal(victim, amount, mob, true);
		MdGifts.inflictKeyword(victim, ai.foe.keyword, ai.foe.potency, 1);
	}

	// --- boss patterns --------------------------------------------------------------------------------------

	private static void pattern(MdBattle battle, Ai ai, Pattern p, List<Player> fighters) {
		LivingEntity boss = ai.mob;
		ai.acting = true;
		battle.run.broadcast(Component.text(ai.foe.name + ": ", NamedTextColor.RED).append(Component.text(p.ko, NamedTextColor.GOLD)));
		boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_EVOKER_PREPARE_ATTACK, SoundCategory.HOSTILE, 1.2F, 0.7F);
		Color colour = Color.fromRGB(battle.run.theme.color());
		Player target = nearest(boss, fighters, 64);
		Location centre = boss.getLocation();
		Location aim = target == null ? centre : target.getLocation();
		// The warning: where it will land, for a second.
		new BukkitRunnable() {
			int tick;

			@Override
			public void run() {
				if (!boss.isValid() || CombatState.of(boss).staggered(CaduceusListener.now())) {
					ai.acting = false;
					cancel();
					return;
				}
				if (tick < 20) {
					warn(p, boss, centre, aim, tick);
				} else {
					land(battle, ai, p, centre, aim, colour);
					ai.acting = false;
					cancel();
				}
				tick++;
			}
		}.runTaskTimer(IndexPrescriptPlugin.instance(), 0L, 1L);
	}

	private static void warn(Pattern p, LivingEntity boss, Location centre, Location aim, int tick) {
		Particle.DustOptions red = new Particle.DustOptions(Color.fromRGB(0xE02020), 1.3F);
		switch (p) {
			case SLAM, SPIN, NOVA -> ring(boss.getLocation(), p == Pattern.SPIN ? 4.5 : p == Pattern.NOVA ? 8 : 5, red);
			case CHARGE -> {
				Vector d = aim.toVector().subtract(centre.toVector()).setY(0);
				for (double s = 0; s < d.length(); s += 0.7) {
					centre.getWorld().spawnParticle(Particle.DUST, centre.clone().add(d.clone().normalize().multiply(s)).add(0, 0.1, 0), 1, 0, 0, 0, 0, red);
				}
			}
			case LEAP -> ring(aim, 3.5, red);
			case VOLLEY, SUMMON -> {
				if (tick % 4 == 0) {
					boss.getWorld().spawnParticle(Particle.WITCH, boss.getLocation().add(0, 2, 0), 10, 0.5, 0.5, 0.5, 0);
				}
			}
		}
	}

	private static void ring(Location at, double r, Particle.DustOptions dust) {
		for (int i = 0; i < 28; i++) {
			double a = i * Math.PI * 2 / 28;
			at.getWorld().spawnParticle(Particle.DUST, at.clone().add(Math.cos(a) * r, 0.15, Math.sin(a) * r), 1, 0, 0, 0, 0, dust);
		}
	}

	private static void land(MdBattle battle, Ai ai, Pattern p, Location centre, Location aim, Color colour) {
		LivingEntity boss = ai.mob;
		MdRun run = battle.run;
		Particle.DustOptions dust = new Particle.DustOptions(colour, 2.0F);
		switch (p) {
			case SLAM -> {
				boss.getWorld().spawnParticle(Particle.EXPLOSION, boss.getLocation(), 6, 2, 0.2, 2, 0);
				hitAround(run, ai, boss.getLocation(), 5, 4.0, 1);
			}
			case SPIN -> {
				for (int i = 0; i < 36; i++) {
					double a = i * Math.PI / 18;
					boss.getWorld().spawnParticle(Particle.SWEEP_ATTACK, boss.getLocation().add(Math.cos(a) * 2.5, 1, Math.sin(a) * 2.5), 1);
				}
				boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 1.4F, 0.6F);
				hitAround(run, ai, boss.getLocation(), 4.5, 3.0, 1);
			}
			case NOVA -> {
				boss.getWorld().spawnParticle(Particle.DUST, boss.getLocation().add(0, 1, 0), 200, 4, 1, 4, 0, dust);
				boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.HOSTILE, 0.8F, 1.2F);
				for (Player pl : run.fighters()) {
					if (pl.getLocation().distanceSquared(boss.getLocation()) <= 64) {
						MdGifts.inflictKeyword(pl, ai.foe.keyword, ai.foe.potency + 3, 2);
						Damage.deal(pl, 1.5 * MdGifts.taken(run, pl, boss), boss, true);
					}
				}
			}
			case CHARGE -> {
				Vector d = aim.toVector().subtract(centre.toVector()).setY(0);
				if (d.lengthSquared() < 0.01) {
					return;
				}
				Location to = centre.clone().add(d.clone().normalize().multiply(Math.min(14, d.length() + 2)));
				Effects.dashTrail(boss.getWorld(), centre.toVector(), to.toVector());
				boss.teleport(to);
				for (Player pl : run.fighters()) {
					if (distanceToSegment(pl.getLocation().toVector(), centre.toVector(), to.toVector()) < 1.6) {
						Damage.deal(pl, 4.0 * MdGifts.taken(run, pl, boss), boss, true);
						pl.setVelocity(d.clone().normalize().multiply(0.9).setY(0.4));
					}
				}
			}
			case LEAP -> {
				boss.teleport(aim);
				boss.getWorld().spawnParticle(Particle.EXPLOSION, aim, 4, 1.5, 0.2, 1.5, 0);
				boss.getWorld().playSound(aim, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 1.0F, 0.8F);
				hitAround(run, ai, aim, 3.5, 4.5, 1);
			}
			case VOLLEY -> {
				for (Player pl : run.fighters()) {
					Vector v = pl.getEyeLocation().toVector().subtract(boss.getEyeLocation().toVector()).normalize();
					for (int i = -1; i <= 1; i++) {
						SmallFireball ball = boss.getWorld().spawn(boss.getEyeLocation().add(v), SmallFireball.class);
						ball.setShooter(boss);
						ball.setIsIncendiary(false);
						ball.setDirection(v.clone().rotateAroundY(i * 0.18).multiply(0.6));
					}
				}
				boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_BLAZE_SHOOT, SoundCategory.HOSTILE, 1.2F, 0.8F);
			}
			case SUMMON -> {
				battle.reinforce(2);
				boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, SoundCategory.HOSTILE, 1.2F, 0.8F);
			}
		}
	}

	private static double distanceToSegment(Vector p, Vector a, Vector b) {
		Vector ab = b.clone().subtract(a).setY(0);
		Vector ap = p.clone().subtract(a).setY(0);
		double t = Math.max(0, Math.min(1, ap.dot(ab) / Math.max(1e-6, ab.lengthSquared())));
		return a.clone().setY(0).add(ab.multiply(t)).distance(p.clone().setY(0));
	}

	private static void hitAround(MdRun run, Ai ai, Location at, double radius, double base, int keywordCount) {
		for (Player pl : run.fighters()) {
			if (pl.getLocation().distanceSquared(at) <= radius * radius) {
				Damage.deal(pl, base * MdGifts.taken(run, pl, ai.mob), ai.mob, true);
				MdGifts.inflictKeyword(pl, ai.foe.keyword, ai.foe.potency + 1, keywordCount);
			}
		}
	}

	// --- stagger ------------------------------------------------------------------------------------------

	/** After a hit: staggers the enemy if its health crossed the next threshold. */
	static void checkStagger(LivingEntity mob, Player by) {
		Ai ai = of(mob);
		if (ai == null || !mob.isValid() || mob.isDead() || ai.thresholds.isEmpty()) {
			return;
		}
		double hp = health(mob);
		if (hp > ai.nextThreshold()) {
			label(ai);
			return;
		}
		while (!ai.thresholds.isEmpty() && hp <= ai.thresholds.get(0)) {
			ai.thresholds.remove(0);
		}
		long now = CaduceusListener.now();
		CombatState.of(mob).addStagger(1, 0.5F, now);
		Stagger.apply(mob, now);
		Effects.staggered(mob);
		ai.acting = false;
		MdRun run = ai.foe.battle.run;
		run.broadcast(Component.text(ai.foe.name + " 흐트러짐! (1턴 동안 행동 불가 · 받는 피해 2배)", NamedTextColor.AQUA));
		MdGifts.trigger(run, "stagger", by, mob);
		label(ai);
	}

	/** A Tremor Burst on a dungeon enemy raises its next stagger threshold by the potency (in %). */
	static void tremorBurst(LivingEntity mob, int potency) {
		Ai ai = of(mob);
		if (ai == null || ai.thresholds.isEmpty()) {
			return;
		}
		ai.thresholds.set(0, Math.min(0.99, ai.thresholds.get(0) + potency / 100.0));
		checkStagger(mob, null);
	}

	/** The name tag: name, health, and where the next stagger is. */
	private static void label(Ai ai) {
		LivingEntity mob = ai.mob;
		int hp = (int) Math.round(health(mob) * 100);
		Component name = Component.text(ai.foe.name, ai.kind == MdBattle.Kind.BOSS ? TextColor.color(0xE5574B)
				: ai.kind == MdBattle.Kind.NORMAL ? NamedTextColor.WHITE : NamedTextColor.GOLD)
				.append(Component.text("  ♥" + hp + "%", NamedTextColor.RED));
		if (!ai.thresholds.isEmpty()) {
			name = name.append(Component.text("  ⚡" + Math.round(ai.nextThreshold() * 100) + "%", NamedTextColor.AQUA));
		}
		if (CombatState.of(mob).staggered(CaduceusListener.now())) {
			name = name.append(Component.text("  흐트러짐", NamedTextColor.YELLOW));
		}
		mob.customName(name);
	}
}
