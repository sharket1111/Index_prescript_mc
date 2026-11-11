package io.github.sharket1111.indexprescript.paper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

/**
 * The Mirror Dungeon's enemies, with the original's numbers (from the Limbus
 * Company Wiki): every enemy uses its own skills (name, base power, coin
 * power, coins, and each coin's effects such as "On Hit inflict 3 Bleed",
 * "Heads Hit inflict Tremor", "Trigger Tremor Burst"), as often as the
 * original lets it act in a turn; its guard and evade skills raise its
 * defence for a moment. It aims a skill at a member, who may answer within 2
 * seconds with one of the plugin's weapons and clash. Unanswered, it closes
 * in and strikes coin by coin, each hit's damage following the power so far
 * (a tenth of it in the plugin's health, raised or lowered by the level
 * difference, as the original does). Enemies answer members' skills too.
 * Passives on a turn's start or end are played. Stagger thresholds and
 * resistances are the original's. Enemies the wiki has no numbers for get
 * skills made for their Theme Pack's keyword.
 */
final class MdFoes {
	private static final Map<UUID, Ai> AI = new ConcurrentHashMap<>();
	/** The level the members count as against the enemies' levels (the original's Identity level cap in the Mirror Dungeon era). */
	private static final int MEMBER_LEVEL = 65;
	/** The original's triggers read as "when the coin hits". */
	private static final Set<String> HIT = Set.of("hit", "on hit without cracking", "hit after clash win");
	private static final Set<String> USE = Set.of("use", "combat start", "before attack");

	static final class Ai {
		final LivingEntity mob;
		final MdBattle.Foe foe;
		final MdBattle.Kind kind;
		final List<SkillCard> cards = new ArrayList<>();
		final List<MdData.EnemySkill> skills = new ArrayList<>();
		final List<MdData.EnemySkill> defences = new ArrayList<>();
		final List<Double> thresholds = new ArrayList<>();
		long nextSkill;
		boolean acting;

		Ai(LivingEntity mob, MdBattle.Foe foe, MdBattle.Kind kind) {
			this.mob = mob;
			this.foe = foe;
			this.kind = kind;
		}

		/** Health fraction at or under which the next stagger comes (Tremor raises it). */
		double nextThreshold() {
			return thresholds.isEmpty() ? -1 : thresholds.get(0);
		}

		MdData.EnemySkill skillOf(SkillCard card) {
			int i = cards.indexOf(card);
			return i >= 0 && i < skills.size() ? skills.get(i) : null;
		}
	}

	private MdFoes() {
	}

	static Ai of(LivingEntity entity) {
		return entity == null ? null : AI.get(entity.getUniqueId());
	}

	// --- setting up -------------------------------------------------------------------------------

	private static final Map<String, List<String>> NAMES = Map.of(
			"burn", List.of("불꽃 베기", "잿더미", "작열"),
			"bleed", List.of("찢어발기기", "피의 연격", "난도질"),
			"tremor", List.of("진동 강타", "울림", "땅 울리기"),
			"rupture", List.of("파쇄", "꿰뚫기", "균열"),
			"sinking", List.of("가라앉히기", "침전", "검은 물결"));

	static void setup(MdBattle battle, LivingEntity mob, MdBattle.Foe foe, MdBattle.Kind kind) {
		Ai ai = new Ai(mob, foe, kind);
		TextColor colour = TextColor.color(battle.run.theme.color());
		MdData.EnemyData data = foe.data;
		if (data != null) {
			for (MdData.EnemySkill s : data.skills()) {
				if (!s.attack()) {
					ai.defences.add(s);
					continue;
				}
				StringBuilder text = new StringBuilder(s.text().replace('\n', ' ').trim());
				for (int i = 0; i < s.coinText().size(); i++) {
					String c = s.coinText().get(i).replaceAll("\\s+", " ").trim();
					if (!c.isEmpty()) {
						text.append(text.length() > 0 ? " / " : "").append("코인").append(i + 1).append(": ").append(c);
					}
				}
				int coins = Math.max(1, s.coins());
				ai.skills.add(s);
				ai.cards.add(new SkillCard(s.name(), colour, s.base(), s.coinPower(), coins, text.toString(), coins * 8 + 10, true, "N".repeat(coins)));
			}
			for (double t : data.stagger()) {
				if (t > 0 && t < 100) {
					ai.thresholds.add(t / 100.0);
				}
			}
			ai.thresholds.sort((a, b) -> Double.compare(b, a));
		}
		if (ai.cards.isEmpty()) {
			// No numbers from the original: skills made for the Theme Pack's keyword.
			ai.skills.clear();
			int floor = Math.max(1, battle.run.floor);
			List<String> names = NAMES.getOrDefault(foe.keyword, List.of("강타", "연속 공격", "내려치기"));
			int count = kind == MdBattle.Kind.BOSS ? 3 : kind == MdBattle.Kind.NORMAL ? 1 : 2;
			for (int i = 0; i < count; i++) {
				int coins = kind == MdBattle.Kind.BOSS ? 3 : 2;
				ai.cards.add(new SkillCard(names.get(i % names.size()), colour, 3 + floor / 2 + i, 2 + floor / 3, coins,
						"적중 시 " + MdData.KEYWORD_KO.getOrDefault(foe.keyword, "") + " 위력 " + foe.potency, coins * 8 + 10, true, "N".repeat(coins)));
			}
		}
		if (ai.thresholds.isEmpty() && data == null) {
			switch (kind) {
				case NORMAL -> ai.thresholds.add(0.5);
				case RISKY -> ai.thresholds.add(0.6);
				case FOCUSED -> ai.thresholds.addAll(List.of(0.65, 0.3));
				case ABNO -> ai.thresholds.addAll(List.of(0.7, 0.4));
				case BOSS -> ai.thresholds.addAll(List.of(0.75, 0.5, 0.25));
			}
		}
		ai.nextSkill = CaduceusListener.now() + 40 + MdRun.rng().nextInt(80);
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
			if (turnStart && turn > 1) {
				passives(battle, ai, "turn_end");
			}
			if (turnStart) {
				passives(battle, ai, "turn_start");
			}
			if (staggered || ai.acting || now < ai.nextSkill || Clash.busy(mob)) {
				continue;
			}
			// Now and then a guard or evade skill instead of an attack.
			if (!ai.defences.isEmpty() && MdRun.rng().nextInt(100) < 18) {
				defend(ai, ai.defences.get(MdRun.rng().nextInt(ai.defences.size())));
				continue;
			}
			Player target = nearest(mob, fighters, 14);
			if (target != null) {
				useSkill(battle, ai, target);
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

	/** Ticks between actions: a turn and a half shared out over as many actions as the original allows it (at least 2.5 s). */
	private static int cooldown(Ai ai) {
		int actions = ai.foe.data == null ? (ai.kind == MdBattle.Kind.BOSS ? 2 : 1) : Math.max(1, ai.foe.data.maxActions());
		return Math.max(50, CombatState.TURN_TICKS * 3 / 2 / actions);
	}

	private static void defend(Ai ai, MdData.EnemySkill skill) {
		long now = CaduceusListener.now();
		ai.nextSkill = now + cooldown(ai);
		ai.foe.guardUntil = now + 60;
		ai.foe.evading = skill.type().equals("evade");
		LivingEntity mob = ai.mob;
		mob.getWorld().spawnParticle(ai.foe.evading ? Particle.CLOUD : Particle.ENCHANTED_HIT, mob.getLocation().add(0, 1, 0), 16, 0.4, 0.6, 0.4, 0.05);
		mob.getWorld().playSound(mob.getLocation(), ai.foe.evading ? Sound.ENTITY_BREEZE_JUMP : Sound.ITEM_SHIELD_BLOCK, SoundCategory.HOSTILE, 0.8F, 1.0F);
		apply(ai, skill.effects(), USE, null, true);
		label(ai);
	}

	// --- skills ------------------------------------------------------------------------------------------

	/** A skill by the original's weights (how many copies of it the enemy has). */
	private static SkillCard nextCard(Ai ai) {
		if (ai.skills.size() == ai.cards.size() && !ai.skills.isEmpty()) {
			int total = 0;
			for (MdData.EnemySkill s : ai.skills) {
				total += Math.max(1, s.weight());
			}
			int roll = MdRun.rng().nextInt(total);
			for (int i = 0; i < ai.skills.size(); i++) {
				roll -= Math.max(1, ai.skills.get(i).weight());
				if (roll < 0) {
					return ai.cards.get(i);
				}
			}
		}
		return ai.cards.get(MdRun.rng().nextInt(ai.cards.size()));
	}

	private static void useSkill(MdBattle battle, Ai ai, Player target) {
		SkillCard card = nextCard(ai);
		ai.nextSkill = CaduceusListener.now() + cooldown(ai) + MdRun.rng().nextInt(30);
		ai.acting = true;
		boolean started = Clash.requestFrom(ai.mob, target, card, outcome -> attack(battle, ai, card, outcome.target()));
		if (!started) {
			ai.acting = false;
		} else {
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

	/** The enemy's skill (an answer that won its clash) goes off at {@code target}. */
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
		MdData.EnemySkill skill = ai.skillOf(card);
		if (skill != null) {
			apply(ai, skill.effects(), USE, victim, true);
		}
		// Close in: appear beside the target in a streak of the theme's colour.
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
					if (skill != null && coin >= card.coins()) {
						apply(ai, skill.effects(), Set.of("attack end"), victim, true);
					}
					ai.acting = false;
					cancel();
					return;
				}
				if (tick % 8 == 4) {
					if (Clash.coin(mob, coin)) {
						strike(battle, ai, victim, skill, coin, Clash.heads(mob, coin));
					}
					coin++;
				}
				tick++;
			}
		}.runTaskTimer(IndexPrescriptPlugin.instance(), 4L, 1L);
	}

	private static void strike(MdBattle battle, Ai ai, Player victim, MdData.EnemySkill skill, int coin, boolean heads) {
		LivingEntity mob = ai.mob;
		mob.swingMainHand();
		Location at = victim.getLocation().add(0, 1, 0);
		at.getWorld().spawnParticle(Particle.SWEEP_ATTACK, at, 1);
		at.getWorld().spawnParticle(Particle.DUST, at, 14, 0.4, 0.5, 0.4, 0, new Particle.DustOptions(Color.fromRGB(battle.run.theme.color()), 1.4F));
		at.getWorld().playSound(at, heads ? Sound.ENTITY_PLAYER_ATTACK_CRIT : Sound.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.HOSTILE, 0.9F, 0.8F);
		if (victim.getLocation().distanceSquared(mob.getLocation()) > 16 || battle.run.downed.contains(victim.getUniqueId())) {
			return;
		}
		double amount;
		if (skill != null) {
			// The original: a coin's damage is the skill's power so far, raised or lowered by the level difference.
			int power = Math.max(1, Clash.power(mob));
			int diff = ai.foe.level - MEMBER_LEVEL;
			double levelFactor = Math.max(0.2, 1 + diff / (double) (Math.abs(diff) + 25));
			long now = CaduceusListener.now();
			double up = ai.foe.buffUntil > now ? 1 + 0.1 * ai.foe.damageUp : 1;
			amount = power / (double) TotalDamage.SCALE * levelFactor * up;
		} else {
			amount = 1.5 + battle.run.floor * 0.6;
		}
		amount *= MdGifts.taken(battle.run, victim, mob);
		// Damage.deal multiplies by the coin's scale again; the power above already counts the heads.
		Damage.deal(victim, amount / Math.max(0.1, Clash.scale(mob)), mob, true);
		if (skill != null) {
			apply(ai, skill.effects(), HIT, victim, heads);
			if (coin < skill.coinEffects().size()) {
				apply(ai, skill.coinEffects().get(coin), HIT, victim, heads);
			}
		} else {
			MdGifts.inflictKeyword(victim, ai.foe.keyword, ai.foe.potency, 1);
		}
	}

	/** Runs the effects whose trigger is one of {@code moments}; "heads"/"tails"/"crit" go with the coin's face on a hit. */
	private static void apply(Ai ai, JsonArray effects, Set<String> moments, Player victim, boolean heads) {
		boolean hit = moments == HIT;
		for (JsonElement el : effects) {
			JsonObject e = el.getAsJsonObject();
			String on = e.get("on").getAsString();
			boolean fits = moments.contains(on) || hit && (on.equals("heads") && heads || on.equals("crit") && heads || on.equals("tails") && !heads);
			if (fits) {
				effect(ai, e, victim);
			}
		}
	}

	private static int num(JsonObject e, String key) {
		return e.has(key) && !e.get(key).isJsonNull() ? e.get(key).getAsInt() : 0;
	}

	private static void effect(Ai ai, JsonObject e, Player victim) {
		String act = e.get("act").getAsString();
		LivingEntity mob = ai.mob;
		switch (act) {
			case "tremor_burst" -> {
				if (victim != null) {
					Tremor.burst(victim);
				}
			}
			case "heal" -> {
				var max = mob.getAttribute(Attribute.MAX_HEALTH);
				double m = max == null ? mob.getHealth() : max.getValue();
				boolean pct = e.has("pct") && e.get("pct").getAsBoolean();
				double amount = pct ? m * num(e, "amount") / 100.0 : num(e, "amount") / (double) TotalDamage.SCALE;
				if (amount > 0 && !mob.isDead()) {
					mob.setHealth(Math.min(m, mob.getHealth() + amount));
				}
			}
			case "inflict", "gain" -> {
				if (!e.has("status") || e.get("status").isJsonNull()) {
					return;
				}
				String status = e.get("status").getAsString();
				int potency = num(e, "potency");
				int count = num(e, "count");
				if (act.equals("gain")) {
					gain(ai, status, Math.max(1, Math.max(potency, count)));
				} else if (victim != null) {
					inflict(victim, status, potency, count);
				}
			}
			default -> {
			}
		}
	}

	/** The enemy's own buffs: Damage Up and offence raise its damage, Protection and defence lower what it takes. */
	private static void gain(Ai ai, String status, int amount) {
		MdBattle.Foe foe = ai.foe;
		long now = CaduceusListener.now();
		switch (status) {
			case "damage_up", "power_up", "attack_power_up", "offense_level_up", "clash_power_up" -> {
				foe.damageUp = Math.min(10, (foe.buffUntil > now ? foe.damageUp : 0) + amount);
				foe.buffUntil = now + CombatState.TURN_TICKS;
			}
			case "protection", "defense_level_up" -> {
				foe.protection = Math.min(10, (foe.buffUntil > now ? foe.protection : 0) + amount);
				foe.buffUntil = now + CombatState.TURN_TICKS;
			}
			case "haste" -> ai.mob.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, CombatState.TURN_TICKS, Math.min(2, amount - 1)));
			case "poise", "charge" -> MdGifts.inflictKeyword(ai.mob, status, amount, amount);
			default -> {
			}
		}
	}

	/** A status on a member: the keywords through the plugin's own, Bind as Slowness, Paralyze as Weakness, Fragile and defence down as damage taken. */
	private static void inflict(Player victim, String status, int potency, int count) {
		int n = Math.max(1, Math.max(potency, count));
		switch (status) {
			case "burn", "bleed", "tremor", "rupture", "sinking" -> MdGifts.inflictKeyword(victim, status, potency, count);
			case "bind" -> victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, CombatState.TURN_TICKS, Math.min(3, n - 1)));
			case "paralyze" -> victim.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, CombatState.TURN_TICKS, Math.min(3, n - 1)));
			case "fragile", "defense_level_down" -> MdGifts.debuff(victim, "fragile", status.equals("fragile") ? n : (n + 1) / 2);
			case "offense_level_down" -> MdGifts.debuff(victim, "offense_down", n);
			default -> {
			}
		}
	}

	private static void passives(MdBattle battle, Ai ai, String when) {
		if (ai.foe.data == null || ai.mob.isDead()) {
			return;
		}
		Player any = nearest(ai.mob, battle.run.players(), 64);
		for (MdData.Passive p : ai.foe.data.passives()) {
			for (JsonElement el : p.effects()) {
				JsonObject e = el.getAsJsonObject();
				if (e.get("on").getAsString().equals(when)) {
					effect(ai, e, any);
				}
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
		ai.foe.guardUntil = 0;
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

	/** The name tag: name, level, health, and where the next stagger is. */
	private static void label(Ai ai) {
		LivingEntity mob = ai.mob;
		int hp = (int) Math.round(health(mob) * 100);
		Component name = Component.text(ai.foe.name, ai.kind == MdBattle.Kind.BOSS ? TextColor.color(0xE5574B)
				: ai.kind == MdBattle.Kind.NORMAL ? NamedTextColor.WHITE : NamedTextColor.GOLD)
				.append(Component.text(" Lv." + ai.foe.level, NamedTextColor.GRAY))
				.append(Component.text("  ♥" + hp + "%", NamedTextColor.RED));
		if (!ai.thresholds.isEmpty()) {
			name = name.append(Component.text("  ⚡" + Math.round(ai.nextThreshold() * 100) + "%", NamedTextColor.AQUA));
		}
		long now = CaduceusListener.now();
		if (CombatState.of(mob).staggered(now)) {
			name = name.append(Component.text("  흐트러짐", NamedTextColor.YELLOW));
		} else if (ai.foe.guardUntil > now) {
			name = name.append(Component.text(ai.foe.evading ? "  회피" : "  수비", NamedTextColor.GRAY));
		}
		mob.customName(name);
	}
}
