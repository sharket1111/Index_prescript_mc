package io.github.sharket1111.indexprescript.paper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Plays the E.G.O Gifts a run owns. Each gift's effects (made from its
 * original text by tools/md_data.py) fire on their trigger: Combat Start (a
 * wave), Turn Start and Turn End (every 7 s in a fight), on hit, on a critical
 * hit, on a kill, when hurt; passive ones change the damage dealt and taken.
 * Statuses go through the plugin's own: Burn, Bleed, Tremor, Rupture,
 * Sinking, Poise, Charge. Buffs last until the next turn: Power Up and Damage
 * Up +5% damage a stack, Offense Level +2%, Protection -10% damage taken,
 * Defense Level -2%, Haste as Speed. Each Charge Count gives +1% damage (max 20%).
 */
final class MdGifts implements Listener {
	/** Hit triggers fire at most once per player in this many ticks (swings and arrows come fast). */
	private static final int HIT_GAP = 10;
	private static final Map<UUID, Long> LAST_HIT = new ConcurrentHashMap<>();
	private static final Map<UUID, Map<String, int[]>> BUFFS = new ConcurrentHashMap<>();

	// --- triggers ------------------------------------------------------------------------------

	/** Fires every owned gift's effects with trigger {@code on}; {@code actor} is the player concerned (null: everyone), {@code target} the other unit. */
	static void trigger(MdRun run, String on, Player actor, LivingEntity target) {
		if (on.equals("combat_start")) {
			run.giftUses.clear();
		}
		if (on.equals("turn_start")) {
			run.giftUses.keySet().removeIf(k -> k.startsWith("turn|"));
		}
		for (Map.Entry<String, Integer> owned : run.gifts.entrySet()) {
			if (run.fuseOnly.contains(owned.getKey())) {
				continue;
			}
			MdData.Gift gift = MdData.gift(owned.getKey());
			if (gift == null) {
				continue;
			}
			JsonArray effects = gift.level(owned.getValue()).effects();
			for (int i = 0; i < effects.size(); i++) {
				JsonObject effect = effects.get(i).getAsJsonObject();
				if (!effect.get("on").getAsString().equals(on)) {
					continue;
				}
				// A moment of the whole team (turn start...) with conditions on the member: each member that meets them.
				List<Player> actors = new ArrayList<>();
				if (actor == null && selfConditions(effect)) {
					actors.addAll(run.fighters());
				} else {
					actors.add(actor);
				}
				for (Player a : actors) {
					// Conditions on the unit concerned (a hit's target, the member hit...).
					if (!passes(effect, target, a)) {
						continue;
					}
					// No enemy concerned yet: effects on the team need one enemy that meets them.
					if (target == null && enemyConditions(effect) && !ENEMY_ACTS.contains(effect.get("act").getAsString())
							&& (run.battle == null || run.battle.mobs.stream().noneMatch(m -> passes(effect, m, a)))) {
						continue;
					}
					if (effect.has("limit")) {
						String key = effect.get("limit").getAsString() + "|" + gift.id() + "|" + i + "|" + (a == null ? "" : a.getUniqueId());
						int used = run.giftUses.getOrDefault(key, 0);
						if (used >= effect.get("times").getAsInt()) {
							continue;
						}
						run.giftUses.put(key, used + 1);
					}
					run(run, effect, actors.size() > 1 ? a : actor, target);
				}
			}
		}
	}

	private static void run(MdRun run, JsonObject e, Player actor, LivingEntity target) {
		if (e.has("chance") && MdRun.rng().nextInt(100) >= e.get("chance").getAsInt()) {
			return;
		}
		String act = e.get("act").getAsString();
		String to = e.has("to") ? e.get("to").getAsString() : "target";
		List<LivingEntity> enemies = run.battle == null ? List.of() : List.copyOf(run.battle.mobs);
		if (target == null && ENEMY_ACTS.contains(act)) {
			// A random enemy is picked among those that meet the conditions.
			enemies = enemies.stream().filter(m -> passes(e, m, actor)).toList();
		}
		List<Player> allies = run.fighters();
		switch (act) {
			case "inflict" -> {
				String kw = e.get("kw").getAsString();
				int potency = e.get("potency").getAsInt();
				int count = e.get("count").getAsInt();
				if (to.equals("random_enemies")) {
					// "Randomly inflict a total of N between all enemies": one at a time.
					List<LivingEntity> pool = enemies.stream().filter(m -> passes(e, m, actor)).toList();
					for (int i = 0; i < potency && !pool.isEmpty(); i++) {
						inflictKeyword(pool.get(MdRun.rng().nextInt(pool.size())), kw, 1, i == 0 ? count : 0);
					}
					return;
				}
				for (LivingEntity t : targets(to, target, enemies, allies, actor)) {
					if (t instanceof Player) {
						continue;
					}
					if (passes(e, t, actor)) {
						inflictKeyword(t, kw, potency, count);
					}
				}
			}
			case "gain" -> {
				String kw = e.get("kw").getAsString();
				int potency = e.get("potency").getAsInt();
				int count = e.get("count").getAsInt();
				for (Player p : players(to, actor, allies)) {
					long now = CaduceusListener.now();
					if (kw.equals("poise")) {
						CombatState.of(p).addPoise(potency, Math.max(1, count), now);
					} else if (kw.equals("charge")) {
						CombatState.of(p).addCharge(count, now);
						if (potency > 0) {
							CombatState.of(p).addChargePotency(potency, now);
						}
					}
				}
			}
			case "buff" -> {
				for (Player p : players(to, actor, allies)) {
					buff(p, e.get("buff").getAsString(), e.get("amount").getAsInt());
				}
			}
			case "debuff" -> {
				for (LivingEntity t : targets(to, target, enemies, allies, actor)) {
					if (!(t instanceof Player) && passes(e, t, actor)) {
						MdBattle.debuff(t, e.get("debuff").getAsString(), e.get("amount").getAsInt());
					}
				}
			}
			case "heal_hp" -> {
				for (Player p : players(to, actor, allies)) {
					double max = p.getAttribute(Attribute.MAX_HEALTH) == null ? 20 : p.getAttribute(Attribute.MAX_HEALTH).getValue();
					// A flat amount is the original's HP: a tenth of it here.
					double amount = e.get("pct").getAsBoolean() ? max * e.get("amount").getAsDouble() / 100 : e.get("amount").getAsDouble() / TotalDamage.SCALE;
					p.setHealth(Math.min(max, p.getHealth() + amount));
				}
			}
			case "heal_sp" -> {
				for (Player p : players(to, actor, allies)) {
					int amount = (int) Math.round(e.get("amount").getAsDouble());
					if (amount >= 0) {
						Sanity.add(p, amount);
					} else {
						Sanity.lose(p, -amount);
					}
				}
			}
			case "cost" -> {
				if (!e.get("pct").getAsBoolean()) {
					run.cost += (int) e.get("amount").getAsDouble();
					run.updateBar();
				}
			}
			case "tremor_burst" -> {
				for (LivingEntity t : targets(to, target, enemies, allies, actor)) {
					if (!(t instanceof Player) && passes(e, t, actor)) {
						Tremor.burst(t);
					}
				}
			}
			case "fixed_damage" -> {
				for (LivingEntity t : targets(to, target, enemies, allies, actor)) {
					if (!(t instanceof Player) && t.isValid() && !t.isDead() && passes(e, t, actor)) {
						var max = t.getAttribute(Attribute.MAX_HEALTH);
						Damage.dealFixed(t, (max == null ? t.getHealth() : max.getValue()) * e.get("pct").getAsDouble() / 100.0, actor);
					}
				}
			}
			default -> {
			}
		}
	}

	/** Acts that pick their enemies themselves, each checked against the conditions. */
	private static final java.util.Set<String> ENEMY_ACTS = java.util.Set.of("inflict", "debuff", "fixed_damage", "tremor_burst");

	private static List<String> conditions(JsonObject e) {
		List<String> conds = new ArrayList<>();
		if (e.has("if")) {
			if (e.get("if").isJsonArray()) {
				e.getAsJsonArray("if").forEach(c -> conds.add(c.getAsString()));
			} else {
				conds.add(e.get("if").getAsString());
			}
		}
		return conds;
	}

	private static boolean selfCondition(String c) {
		return c.startsWith("kind:") || c.startsWith("sin:") || c.startsWith("hp<") || c.startsWith("hp>")
				|| c.startsWith("kw>=poise") || c.startsWith("kw>=charge");
	}

	private static boolean selfConditions(JsonObject e) {
		return conditions(e).stream().anyMatch(MdGifts::selfCondition);
	}

	private static boolean enemyConditions(JsonObject e) {
		return conditions(e).stream().anyMatch(c -> !selfCondition(c));
	}

	/** Whether an effect's conditions hold: on the enemy concerned ({@code t}) and the member ({@code p}). */
	static boolean passes(JsonObject e, LivingEntity t, Player p) {
		if (!e.has("if")) {
			return true;
		}
		long now = CaduceusListener.now();
		LivingEntity enemy = t instanceof Player ? null : t;
		for (String c : conditions(e)) {
			boolean ok;
			if (t == null && !selfCondition(c)) {
				// No enemy concerned here: checked on each enemy the effect reaches.
				ok = true;
			} else if (c.startsWith("kw>=poise") || c.startsWith("kw>=charge")) {
				// Poise and Charge are the member's own.
				String[] kv = c.substring(4).split(":");
				ok = p != null && potency(p, kv[0], now) >= Integer.parseInt(kv[1]);
			} else if (c.startsWith("has:") || c.startsWith("target_has:")) {
				ok = enemy != null && has(enemy, c.substring(c.indexOf(':') + 1), now);
			} else if (c.equals("staggered") || c.equals("target_staggered")) {
				ok = enemy != null && CombatState.of(enemy).staggered(now);
			} else if (c.equals("notstaggered")) {
				ok = enemy == null || !CombatState.of(enemy).staggered(now);
			} else if (c.startsWith("kind:")) {
				ok = p != null && kindOf(p.getInventory().getItemInMainHand()).name().equalsIgnoreCase(c.substring(5));
			} else if (c.startsWith("sin:")) {
				ok = p != null && MdEvent.sinOf(p).equals(c.substring(4));
			} else if (c.startsWith("hp<") || c.startsWith("hp>")) {
				ok = p != null && compare(fraction(p), c.charAt(2), Double.parseDouble(c.substring(3)));
			} else if (c.startsWith("thp<") || c.startsWith("thp>")) {
				ok = enemy != null && compare(fraction(enemy), c.charAt(3), Double.parseDouble(c.substring(4)));
			} else if (c.startsWith("kw>=")) {
				String[] kv = c.substring(4).split(":");
				ok = enemy != null && potency(enemy, kv[0], now) >= Integer.parseInt(kv[1]);
			} else {
				ok = true;
			}
			if (!ok) {
				return false;
			}
		}
		return true;
	}

	private static double fraction(LivingEntity u) {
		var max = u.getAttribute(Attribute.MAX_HEALTH);
		return 100.0 * u.getHealth() / (max == null ? Math.max(1, u.getHealth()) : max.getValue());
	}

	private static boolean compare(double value, char op, double bound) {
		return op == '<' ? value <= bound : value >= bound;
	}

	private static int potency(LivingEntity t, String kw, long now) {
		CombatState s = CombatState.of(t);
		return switch (kw) {
			case "burn" -> s.burnPotency(now);
			case "bleed" -> s.bleedPotency(now);
			case "tremor" -> s.tremorPotency(now);
			case "rupture" -> s.rupturePotency(now);
			case "sinking" -> s.sinkingPotency(now);
			case "poise" -> s.poisePotency(now);
			case "charge" -> s.chargeCount(now);
			default -> 0;
		};
	}

	private static boolean has(LivingEntity t, String kw, long now) {
		CombatState s = CombatState.of(t);
		return switch (kw) {
			case "burn" -> s.burnPotency(now) > 0 || t.getFireTicks() > 0;
			case "bleed" -> s.bleedPotency(now) > 0;
			case "tremor" -> s.tremorPotency(now) > 0;
			case "rupture" -> s.rupturePotency(now) > 0;
			case "sinking" -> s.sinkingPotency(now) > 0;
			case "poise" -> s.poisePotency(now) > 0;
			case "charge" -> s.chargeCount(now) > 0;
			default -> false;
		};
	}

	private static List<LivingEntity> targets(String to, LivingEntity target, List<LivingEntity> enemies, List<Player> allies, Player actor) {
		return switch (to) {
			case "all_enemies" -> enemies;
			case "random_enemy" -> enemies.isEmpty() ? List.of() : List.of(enemies.get(MdRun.rng().nextInt(enemies.size())));
			case "target", "attacker" -> target != null ? List.of(target)
					: enemies.isEmpty() ? List.of() : List.of(enemies.get(MdRun.rng().nextInt(enemies.size())));
			case "all_allies" -> new ArrayList<>(allies);
			case "self" -> actor != null ? List.of(actor) : new ArrayList<>(allies);
			default -> List.of();
		};
	}

	private static List<Player> players(String to, Player actor, List<Player> allies) {
		return to.equals("all_allies") || actor == null ? allies : List.of(actor);
	}

	/** Inflicts one of the original's keywords through the plugin's own status. */
	static void inflictKeyword(LivingEntity t, String kw, int potency, int count) {
		if (t == null || !t.isValid() || t.isDead()) {
			return;
		}
		long now = CaduceusListener.now();
		switch (kw) {
			case "burn" -> {
				CombatState.of(t).addBurn(potency, count, now);
				t.getWorld().spawnParticle(Particle.FLAME, t.getLocation().add(0, t.getHeight() * 0.6, 0), 6 + potency, 0.3, 0.4, 0.3, 0.01);
			}
			case "bleed" -> Bleed.inflict(t, potency, count);
			case "tremor" -> {
				if (potency > 0) {
					Tremor.inflict(t, potency);
				}
				if (count > 0) {
					Tremor.addCount(t, count);
				}
			}
			case "rupture" -> Rupture.inflict(t, potency, count);
			case "sinking" -> {
				CombatState.of(t).addSinking(potency, Math.max(1, count), now);
				t.getWorld().spawnParticle(Particle.DUST, t.getLocation().add(0, t.getHeight() * 0.6, 0), 6 + potency, 0.3, 0.4, 0.3, 0,
						new Particle.DustOptions(org.bukkit.Color.fromRGB(0x3255C8), 1.1F));
			}
			case "poise" -> CombatState.of(t).addPoise(potency, Math.max(1, count), now);
			case "charge" -> CombatState.of(t).addCharge(Math.max(count, potency), now);
			default -> {
			}
		}
	}

	// --- buffs and numbers --------------------------------------------------------------------------

	private static void buff(Player p, String kind, int amount) {
		long until = CaduceusListener.now() + CombatState.TURN_TICKS;
		BUFFS.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>()).merge(kind, new int[] {amount, (int) until}, (a, b) -> new int[] {
			Math.min(10, (a[1] > CaduceusListener.now() ? a[0] : 0) + b[0]), b[1]});
		if (kind.equals("haste")) {
			p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, CombatState.TURN_TICKS, Math.min(3, buffOf(p, "haste") - 1), false, false, true));
		}
	}

	/** A debuff from an enemy's skill on a member until the next turn: "fragile" (+10% damage taken each), "offense_down" (-10% dealt each). */
	static void debuff(Player p, String kind, int amount) {
		buff(p, kind, amount);
	}

	private static int buffOf(Player p, String kind) {
		Map<String, int[]> map = BUFFS.get(p.getUniqueId());
		int[] b = map == null ? null : map.get(kind);
		return b == null || b[1] < CaduceusListener.now() ? 0 : b[0];
	}

	/** A Mounting Trial stat summed over the trials taken (e.g. "max_hp", "offense_level", "damage_dealt"). */
	static double trialStat(MdRun run, String key) {
		double sum = 0;
		for (MdData.Trial t : run.trials) {
			sum += t.stats().getOrDefault(key, 0.0);
		}
		return sum;
	}

	/** Percent of extra Cost from battles that gifts give. */
	static double rewardPct(MdRun run) {
		double pct = 0;
		for (JsonObject e : passives(run)) {
			if (e.get("act").getAsString().equals("cost") && e.get("pct").getAsBoolean()) {
				pct += e.get("amount").getAsDouble();
			}
		}
		return pct;
	}

	private static List<JsonObject> passives(MdRun run) {
		List<JsonObject> out = new ArrayList<>();
		for (Map.Entry<String, Integer> owned : run.gifts.entrySet()) {
			MdData.Gift gift = MdData.gift(owned.getKey());
			if (gift == null || run.fuseOnly.contains(owned.getKey())) {
				continue;
			}
			for (JsonElement e : gift.level(owned.getValue()).effects()) {
				out.add(e.getAsJsonObject());
			}
		}
		return out;
	}

	/** Gifts changed: refresh what depends on them all the time (max Charge). */
	static void changed(MdRun run) {
		int max = CombatState.DEFAULT_MAX_CHARGE;
		for (JsonObject e : passives(run)) {
			if (e.get("act").getAsString().equals("max_charge")) {
				max += e.get("amount").getAsInt();
			}
		}
		for (Player p : run.players()) {
			CombatState.of(p).setMaxCharge(max);
		}
	}

	private static DamageKind kindOf(ItemStack held) {
		if (held == null) {
			return DamageKind.BLUNT;
		}
		Material m = held.getType();
		String n = m.name();
		if (n.endsWith("_SWORD") || Items.isLaevatein(held) || Items.isMimicry(held)) {
			return DamageKind.SLASH;
		}
		if (m == Material.TRIDENT || m == Material.BOW || m == Material.CROSSBOW || n.endsWith("_SPEAR")) {
			return DamageKind.PIERCE;
		}
		return DamageKind.BLUNT;
	}

	/** The multiplier on a player's hit on {@code target}: gifts, buffs, levels, Charge. */
	static double dealt(MdRun run, Player p, LivingEntity target) {
		long now = CaduceusListener.now();
		double pct = 0;
		DamageKind kind = kindOf(p.getInventory().getItemInMainHand());
		for (JsonObject e : passives(run)) {
			String act = e.get("act").getAsString();
			String on = e.get("on").getAsString();
			if (act.equals("damage_pct") && (on.equals("damage") || on.equals("passive")) && passes(e, target, p)
					&& (!e.has("chance") || MdRun.rng().nextInt(100) < e.get("chance").getAsInt())) {
				pct += e.get("pct").getAsDouble();
			}
		}
		pct += 5 * (buffOf(p, "power") + buffOf(p, "damage")) + 2 * buffOf(p, "offense_level") + run.levels - 10 * buffOf(p, "offense_down");
		pct += Math.min(20, CombatState.of(p).chargeCount(now));
		pct += MdGrace.damageBonus(run);
		MdBattle.Foe foe = MdBattle.foe(target);
		double mult = 1 + pct / 100.0;
		if (foe != null) {
			mult *= foe.taken * (foe.debuffUntil > now ? 1 + 0.1 * foe.defenseDown : 1);
			// The original's resistances: by the attack's kind (Slash/Pierce/Blunt) and by its sin.
			if (foe.data != null) {
				mult *= Math.max(0, foe.resist(kind.name().toLowerCase(java.util.Locale.ROOT)));
				mult *= Math.max(0, foe.resist(MdEvent.sinOf(p)));
			}
			if (foe.buffUntil > now) {
				mult *= Math.max(0.3, 1 - 0.1 * foe.protection);
			}
			// A Guard skill halves what it takes.
			if (foe.guardUntil > now && !foe.evading) {
				mult *= 0.5;
			}
			// A staggered enemy takes double damage.
			if (CombatState.of(target).staggered(now)) {
				mult *= 2;
			}
		}
		return mult;
	}

	/** The multiplier on damage a player takes from an enemy. */
	static double taken(MdRun run, Player p, LivingEntity attacker) {
		long now = CaduceusListener.now();
		double pct = 0;
		for (JsonObject e : passives(run)) {
			if (e.get("act").getAsString().equals("taken_pct") && passes(e, attacker, p)) {
				pct += e.get("pct").getAsDouble();
			}
		}
		pct += 10 * buffOf(p, "fragile");
		double mult = (1 + pct / 100.0) * Math.max(0.3, 1 - 0.1 * buffOf(p, "protection") - 0.02 * buffOf(p, "defense_level") - 0.01 * run.levels)
				* (1 - MdGrace.protection(run));
		MdBattle.Foe foe = MdBattle.foe(attacker);
		if (foe != null) {
			mult *= foe.damage * (foe.debuffUntil > now ? Math.max(0.3, 1 - 0.1 * foe.offenseDown) : 1);
		}
		return mult;
	}

	/** Clash power from gifts for {@code p}'s skill against {@code target} (Clash Power, Coin Power, Final Power...). */
	static int clashPower(Player p, LivingEntity target) {
		MdRun run = MdRun.of(p);
		if (run == null) {
			return 0;
		}
		int sum = 0;
		for (JsonObject e : passives(run)) {
			if (e.get("act").getAsString().equals("clash_power") && passes(e, target, p)) {
				sum += e.get("amount").getAsInt();
			}
		}
		return sum;
	}

	/** Percent of the damage dealt healed back (Lifesteal-like gifts). */
	static double lifesteal(MdRun run, Player p, LivingEntity target) {
		double sum = 0;
		for (JsonObject e : passives(run)) {
			if (e.get("act").getAsString().equals("lifesteal") && passes(e, target, p)) {
				sum += e.get("pct").getAsDouble();
			}
		}
		return sum;
	}

	/** Percent off the shop's prices. */
	static double shopDiscount(MdRun run) {
		double sum = 0;
		for (JsonObject e : passives(run)) {
			if (e.get("act").getAsString().equals("shop_discount")) {
				sum += e.get("pct").getAsDouble();
			}
		}
		return Math.min(60, sum);
	}

	static double critMultiplier(MdRun run) {
		double best = 1.2;
		for (JsonObject e : passives(run)) {
			if (e.get("act").getAsString().equals("crit_mult")) {
				best = Math.max(best, e.get("value").getAsDouble());
			}
		}
		return best;
	}

	// --- the fight ---------------------------------------------------------------------------------

	private static Player playerOf(Entity damager) {
		if (damager instanceof Player p) {
			return p;
		}
		if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player p) {
			return p;
		}
		return null;
	}

	private static LivingEntity livingOf(Entity damager) {
		if (damager instanceof LivingEntity l) {
			return l;
		}
		if (damager instanceof Projectile projectile && projectile.getShooter() instanceof LivingEntity l) {
			return l;
		}
		return null;
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onDamage(EntityDamageByEntityEvent event) {
		if (!MdWorld.in(event.getEntity()) || !(event.getEntity() instanceof LivingEntity victim)) {
			return;
		}
		Player attacker = playerOf(event.getDamager());
		LivingEntity source = livingOf(event.getDamager());
		if (victim instanceof Player hurt) {
			if (attacker != null) {
				// No fighting each other in the dungeon.
				event.setCancelled(true);
				return;
			}
			MdRun run = MdRun.of(hurt);
			if (run == null || source == null || MdBattle.foe(source) == null) {
				return;
			}
			if (!Damage.dealing()) {
				// Enemies hurt the members only with their skills (coin by coin), not with the vanilla mob's own swings.
				event.setCancelled(true);
				return;
			}
			Bukkit.getScheduler().runTask(IndexPrescriptPlugin.instance(), () -> trigger(run, "hurt", hurt, source));
			return;
		}
		if (MdBattle.foe(victim) == null) {
			return;
		}
		if (attacker == null) {
			if (source != null && MdBattle.foe(source) != null) {
				// Enemies do not hurt each other.
				event.setCancelled(true);
			}
			return;
		}
		MdRun run = MdRun.of(attacker);
		if (run == null) {
			return;
		}
		long now = CaduceusListener.now();
		MdBattle.Foe hitFoe = MdBattle.foe(victim);
		// An Evade skill: half the hits miss.
		if (hitFoe.guardUntil > now && hitFoe.evading && MdRun.rng().nextBoolean()) {
			event.setCancelled(true);
			victim.getWorld().spawnParticle(Particle.CLOUD, victim.getLocation().add(0, 1, 0), 6, 0.3, 0.4, 0.3, 0.02);
			victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_BREEZE_JUMP, SoundCategory.HOSTILE, 0.6F, 1.4F);
			return;
		}
		if (basic(event)) {
			// In the Mirror Dungeon a plain attack (a swing or a shot, not a skill) always does exactly 1 damage.
			event.setDamage(1.0);
			Bukkit.getScheduler().runTask(IndexPrescriptPlugin.instance(), () -> MdFoes.checkStagger(victim, attacker));
			Long lastBasic = LAST_HIT.get(attacker.getUniqueId());
			if (lastBasic == null || now - lastBasic >= HIT_GAP) {
				LAST_HIT.put(attacker.getUniqueId(), now);
				Bukkit.getScheduler().runTask(IndexPrescriptPlugin.instance(), () -> trigger(run, "hit", attacker, victim));
			}
			return;
		}
		double damage = event.getDamage() * dealt(run, attacker, victim);
		boolean crit = false;
		CombatState state = CombatState.of(attacker);
		int poise = state.poisePotency(now);
		if (poise > 0 && MdRun.rng().nextInt(100) < poise * 5) {
			damage *= critMultiplier(run);
			state.consumePoise(now);
			crit = true;
			victim.getWorld().spawnParticle(Particle.CRIT, victim.getLocation().add(0, victim.getHeight() * 0.6, 0), 14, 0.3, 0.4, 0.3, 0.3);
			victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 0.8F, 1.2F);
		}
		event.setDamage(damage);
		double steal = lifesteal(run, attacker, victim);
		if (steal > 0) {
			var maxHp = attacker.getAttribute(Attribute.MAX_HEALTH);
			attacker.setHealth(Math.min(maxHp == null ? 20 : maxHp.getValue(), attacker.getHealth() + damage * steal / 100.0));
		}
		Bukkit.getScheduler().runTask(IndexPrescriptPlugin.instance(), () -> MdFoes.checkStagger(victim, attacker));
		boolean fresh = !Damage.dealing() || Damage.countsAsHit();
		Long last = LAST_HIT.get(attacker.getUniqueId());
		if (!fresh || last != null && now - last < HIT_GAP) {
			return;
		}
		LAST_HIT.put(attacker.getUniqueId(), now);
		boolean critHit = crit;
		Bukkit.getScheduler().runTask(IndexPrescriptPlugin.instance(), () -> {
			// Sinking on an enemy without Sanity: Gloom damage by potency, as the original's Abnormalities take it.
			if (!Items.isCaduceus(attacker.getInventory().getItemInMainHand())) {
				int sinking = CombatState.of(victim).consumeSinking(CaduceusListener.now());
				if (sinking > 0) {
					Damage.dealFixed(victim, sinking * Rupture.DAMAGE_PER_POTENCY, attacker);
				}
			}
			trigger(run, "hit", attacker, victim);
			if (critHit) {
				trigger(run, "crit", attacker, victim);
			}
		});
	}

	/** A player's plain attack on a dungeon enemy: a swing, sweep or shot that is not the plugin's own (skill) damage. */
	private static boolean basic(EntityDamageByEntityEvent event) {
		if (Damage.dealing()) {
			return false;
		}
		return switch (event.getCause()) {
			case ENTITY_ATTACK, ENTITY_SWEEP_ATTACK, PROJECTILE -> true;
			default -> false;
		};
	}

	/**
	 * Last word on damage to a dungeon enemy, after every other listener: a
	 * plain attack (weapon passives that scale the swing, armour and
	 * resistance taken out) does exactly 1; a part of an Abnormality does not
	 * die but breaks at 0 (MdFoes.partDamage).
	 */
	@SuppressWarnings("deprecation")
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onFinalDamage(EntityDamageEvent event) {
		if (!MdWorld.in(event.getEntity()) || !(event.getEntity() instanceof LivingEntity victim) || victim instanceof Player || MdBattle.foe(victim) == null) {
			return;
		}
		if (event instanceof EntityDamageByEntityEvent byEntity && playerOf(byEntity.getDamager()) != null && basic(byEntity)) {
			event.setDamage(1.0);
			for (EntityDamageEvent.DamageModifier m : EntityDamageEvent.DamageModifier.values()) {
				if (m != EntityDamageEvent.DamageModifier.BASE && event.isApplicable(m)) {
					event.setDamage(m, 0);
				}
			}
		}
		double through = MdFoes.partDamage(victim, event.getFinalDamage());
		if (through <= 0) {
			event.setCancelled(true);
			return;
		}
		if (through < event.getFinalDamage()) {
			event.setDamage(event.getDamage() - (event.getFinalDamage() - through));
		}
		MdLooks.hurt(victim);
		// Any damage (statuses too) can cross a stagger threshold or bring a new pattern.
		Player by = event instanceof EntityDamageByEntityEvent byEntity ? playerOf(byEntity.getDamager()) : null;
		Bukkit.getScheduler().runTask(IndexPrescriptPlugin.instance(), () -> MdFoes.checkStagger(victim, by));
	}

	@EventHandler(priority = EventPriority.HIGH)
	public void onDeath(EntityDeathEvent event) {
		if (!MdWorld.in(event.getEntity())) {
			return;
		}
		LivingEntity dead = event.getEntity();
		if (dead instanceof Player) {
			return;
		}
		event.getDrops().clear();
		event.setDroppedExp(0);
		MdBattle.Foe foe = MdBattle.foe(dead);
		Player killer = dead.getKiller();
		MdBattle.forget(dead);
		if (foe != null && foe.battle.run.adv("redistribute") > 0) {
			// Force Redistribution: the fallen's strength goes to the rest.
			long until = CaduceusListener.now() + 2L * CombatState.TURN_TICKS;
			for (LivingEntity other : foe.battle.mobs) {
				MdBattle.Foe f = MdBattle.foe(other);
				if (f != null && other != dead) {
					f.damageUp = Math.min(10, (f.buffUntil > CaduceusListener.now() ? f.damageUp : 0) + 3);
					f.buffUntil = until;
				}
			}
		}
		if (foe != null && killer != null) {
			MdRun run = MdRun.of(killer);
			if (run != null) {
				trigger(run, "kill", killer, dead);
			}
		}
	}

	static void forget(Player p) {
		LAST_HIT.remove(p.getUniqueId());
		BUFFS.remove(p.getUniqueId());
	}
}
