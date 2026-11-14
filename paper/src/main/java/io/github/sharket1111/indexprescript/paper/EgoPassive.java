package io.github.sharket1111.indexprescript.paper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * The passives of the equipped E.G.O (every grade slot), anywhere: the same
 * effects as the E.G.O Gifts (md_gift_dsl.py, tools/ego_passive_specs.py),
 * played for the one player.
 *
 * Moments: a hit, a crit (a hit with Poise's chance), a kill, being hurt, a
 * foe staggered by one's hits, a clash won or lost, an ally down (Mirror
 * Dungeon), and the turns: the Mirror Dungeon's own, elsewhere a turn every 7
 * seconds while in combat (combat starts on the first exchange). "Always"
 * effects add damage dealt or taken, clash power and lifesteal. Enemies are
 * the battle's in the Mirror Dungeon, elsewhere the creatures within 12
 * blocks; allies are the party there, oneself elsewhere.
 */
final class EgoPassive implements Listener {
	private static final int HIT_GAP = 10;
	private static final double RANGE = 12.0;

	/** Per player: uses of limited effects ("turn|id|i", "combat|id|i"). */
	private static final Map<UUID, Map<String, Integer>> USES = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_HIT = new ConcurrentHashMap<>();
	/** Who last hit each creature (for staggers). */
	private static final Map<UUID, UUID> LAST_ATTACKER = new ConcurrentHashMap<>();
	/** Outside the dungeon: when the player's current combat started (for its turns). */
	private static final Map<UUID, Long> COMBAT = new ConcurrentHashMap<>();

	private record Owned(String id, JsonObject effect, int index) {
	}

	private static List<Owned> effects(Player p) {
		List<Owned> out = new ArrayList<>();
		for (String grade : Ego.GRADES) {
			Ego.Def def = Ego.equipped(p, grade);
			if (def == null) {
				continue;
			}
			JsonArray list = def.passiveEffects();
			for (int i = 0; i < list.size(); i++) {
				out.add(new Owned(def.id(), list.get(i).getAsJsonObject(), i));
			}
		}
		return out;
	}

	// --- moments ---------------------------------------------------------------------------------

	/** Runs {@code p}'s passives for {@code on}, with {@code target} the enemy concerned (or none). */
	static void trigger(Player p, String on, LivingEntity target) {
		if (p == null || !p.isOnline() || p.isDead()) {
			return;
		}
		Map<String, Integer> uses = USES.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>());
		if (on.equals("combat_start")) {
			uses.clear();
		}
		if (on.equals("turn_start")) {
			uses.keySet().removeIf(k -> k.startsWith("turn|"));
		}
		for (Owned owned : effects(p)) {
			JsonObject e = owned.effect();
			if (!e.get("on").getAsString().equals(on) || !MdGifts.passes(e, target, p)) {
				continue;
			}
			if (target == null && enemyCondition(e) && !ENEMY_ACTS.contains(e.get("act").getAsString())
					&& enemies(p).stream().noneMatch(m -> MdGifts.passes(e, m, p))) {
				continue;
			}
			if (e.has("limit")) {
				String key = e.get("limit").getAsString() + "|" + owned.id() + "|" + owned.index();
				int used = uses.getOrDefault(key, 0);
				if (used >= e.get("times").getAsInt()) {
					continue;
				}
				uses.put(key, used + 1);
			}
			run(p, e, target);
		}
	}

	private static final java.util.Set<String> ENEMY_ACTS = java.util.Set.of("inflict", "debuff", "fixed_damage", "tremor_burst");

	private static boolean enemyCondition(JsonObject e) {
		if (!e.has("if")) {
			return false;
		}
		for (var c : e.getAsJsonArray("if")) {
			String s = c.getAsString();
			if (!(s.startsWith("kind:") || s.startsWith("sin:") || s.startsWith("hp<") || s.startsWith("hp>") || s.startsWith("kw>=poise")
					|| s.startsWith("kw>=charge"))) {
				return true;
			}
		}
		return false;
	}

	private static List<LivingEntity> enemies(Player p) {
		MdRun run = MdRun.of(p);
		if (run != null && run.battle != null) {
			return List.copyOf(run.battle.mobs);
		}
		List<LivingEntity> out = new ArrayList<>();
		for (Entity e : p.getNearbyEntities(RANGE, RANGE / 2, RANGE)) {
			if (e instanceof LivingEntity l && !(l instanceof Player) && !(l instanceof ArmorStand) && !l.isDead() && !Cinematic.isActor(l)) {
				out.add(l);
			}
		}
		return out;
	}

	private static List<Player> allies(Player p) {
		MdRun run = MdRun.of(p);
		return run != null ? run.fighters() : List.of(p);
	}

	private static void run(Player p, JsonObject e, LivingEntity target) {
		if (e.has("chance") && ThreadLocalRandom.current().nextInt(100) >= e.get("chance").getAsInt()) {
			return;
		}
		String act = e.get("act").getAsString();
		String to = e.has("to") ? e.get("to").getAsString() : "target";
		List<LivingEntity> enemies = enemies(p).stream().filter(m -> MdGifts.passes(e, m, p)).toList();
		List<Player> mates = to.equals("all_allies") ? allies(p) : List.of(p);
		long now = CaduceusListener.now();
		switch (act) {
			case "inflict" -> {
				String kw = e.get("kw").getAsString();
				int potency = e.get("potency").getAsInt();
				int count = e.get("count").getAsInt();
				if (to.equals("random_enemies")) {
					for (int i = 0; i < potency && !enemies.isEmpty(); i++) {
						MdGifts.inflictKeyword(enemies.get(ThreadLocalRandom.current().nextInt(enemies.size())), kw, 1, i == 0 ? count : 0);
					}
					return;
				}
				for (LivingEntity t : pick(to, target, enemies)) {
					MdGifts.inflictKeyword(t, kw, potency, count);
				}
			}
			case "gain" -> {
				String kw = e.get("kw").getAsString();
				for (Player m : mates) {
					if (kw.equals("poise")) {
						CombatState.of(m).addPoise(e.get("potency").getAsInt(), Math.max(1, e.get("count").getAsInt()), now);
					} else if (kw.equals("charge")) {
						CombatState.of(m).addCharge(e.get("count").getAsInt(), now);
						if (e.get("potency").getAsInt() > 0) {
							CombatState.of(m).addChargePotency(e.get("potency").getAsInt(), now);
						}
					}
				}
			}
			case "buff" -> mates.forEach(m -> MdGifts.buff(m, e.get("buff").getAsString(), e.get("amount").getAsInt()));
			case "debuff" -> {
				for (LivingEntity t : pick(to, target, enemies)) {
					MdBattle.debuff(t, e.get("debuff").getAsString(), e.get("amount").getAsInt());
				}
			}
			case "heal_hp" -> {
				for (Player m : mates) {
					var attr = m.getAttribute(Attribute.MAX_HEALTH);
					double max = attr == null ? 20 : attr.getValue();
					double amount = e.get("pct").getAsBoolean() ? max * e.get("amount").getAsDouble() / 100 : e.get("amount").getAsDouble() / TotalDamage.SCALE;
					m.setHealth(Math.min(max, m.getHealth() + amount));
				}
			}
			case "heal_sp" -> {
				int amount = (int) Math.round(e.get("amount").getAsDouble());
				for (Player m : mates) {
					if (amount >= 0) {
						Sanity.add(m, amount);
					} else {
						Sanity.lose(m, -amount);
					}
				}
			}
			case "tremor_burst" -> pick(to, target, enemies).forEach(Tremor::burst);
			case "fixed_damage" -> {
				for (LivingEntity t : pick(to, target, enemies)) {
					var max = t.getAttribute(Attribute.MAX_HEALTH);
					Damage.dealFixed(t, (max == null ? t.getHealth() : max.getValue()) * e.get("pct").getAsDouble() / 100.0, p);
				}
			}
			case "ego_resource" -> {
				String sin = e.get("sin").getAsString();
				int amount = e.get("amount").getAsInt();
				int index = switch (sin) {
					case "random" -> ThreadLocalRandom.current().nextInt(Ego.SINS.length);
					case "lowest" -> {
						int[] held = Ego.resources(p);
						int best = 0;
						for (int i = 1; i < held.length; i++) {
							if (held[i] < held[best]) {
								best = i;
							}
						}
						yield best;
					}
					default -> Ego.index(sin);
				};
				Ego.gain(p, index, amount);
			}
			default -> {
			}
		}
	}

	private static List<LivingEntity> pick(String to, LivingEntity target, List<LivingEntity> enemies) {
		return switch (to) {
			case "all_enemies" -> enemies;
			case "random_enemy" -> enemies.isEmpty() ? List.of() : List.of(enemies.get(ThreadLocalRandom.current().nextInt(enemies.size())));
			default -> target != null && !(target instanceof Player) ? List.of(target)
					: enemies.isEmpty() ? List.of() : List.of(enemies.get(ThreadLocalRandom.current().nextInt(enemies.size())));
		};
	}

	// --- always-on numbers ----------------------------------------------------------------------

	private static double sum(Player p, String act, String field, LivingEntity other) {
		double sum = 0;
		for (Owned owned : effects(p)) {
			JsonObject e = owned.effect();
			if (e.get("act").getAsString().equals(act) && MdGifts.passes(e, other, p)
					&& (!e.has("chance") || ThreadLocalRandom.current().nextInt(100) < e.get("chance").getAsInt())) {
				sum += e.get(field).getAsDouble();
			}
		}
		return sum;
	}

	/** Clash power from the passives against {@code target}. */
	static int clashPower(Player p, LivingEntity target) {
		return (int) sum(p, "clash_power", "amount", target);
	}

	/** Damage multiplier on {@code p}'s hit on {@code target} (outside the dungeon the passives' buffs count here too). */
	static double dealt(Player p, LivingEntity target) {
		double pct = sum(p, "damage_pct", "pct", target);
		if (MdRun.of(p) == null) {
			pct += 5 * (MdGifts.buffOf(p, "power") + MdGifts.buffOf(p, "damage")) + 2 * MdGifts.buffOf(p, "offense_level");
		}
		return Math.max(0, 1 + pct / 100.0);
	}

	static double taken(Player p, LivingEntity attacker) {
		double pct = sum(p, "taken_pct", "pct", attacker);
		double mult = Math.max(0, 1 + pct / 100.0);
		if (MdRun.of(p) == null) {
			mult *= Math.max(0.3, 1 - 0.1 * MdGifts.buffOf(p, "protection") - 0.02 * MdGifts.buffOf(p, "defense_level"));
		}
		return mult;
	}

	// --- hooks from elsewhere -------------------------------------------------------------------

	/** A creature was staggered: the passives of whoever hit it last. */
	static void staggered(LivingEntity target) {
		UUID by = LAST_ATTACKER.get(target.getUniqueId());
		Player p = by == null ? null : Bukkit.getPlayer(by);
		if (p != null) {
			trigger(p, "stagger", target);
		}
	}

	static void clash(LivingEntity winner, LivingEntity loser) {
		if (winner instanceof Player p) {
			trigger(p, "clash_win", loser);
		}
		if (loser instanceof Player p) {
			trigger(p, "clash_lose", winner);
		}
	}

	/** The Mirror Dungeon's turns and battles, for each member. */
	static void dungeon(MdRun run, String on) {
		for (Player p : run.fighters()) {
			trigger(p, on, null);
		}
	}

	/** Every tick: outside the dungeon, a turn every 7 seconds of combat. */
	static void tick() {
		long now = CaduceusListener.now();
		for (Player p : Bukkit.getOnlinePlayers()) {
			if (MdRun.of(p) != null) {
				COMBAT.remove(p.getUniqueId());
				continue;
			}
			boolean fighting = Sanity.inCombat(p);
			Long start = COMBAT.get(p.getUniqueId());
			if (!fighting) {
				COMBAT.remove(p.getUniqueId());
				continue;
			}
			if (start == null) {
				COMBAT.put(p.getUniqueId(), now);
				trigger(p, "combat_start", null);
				trigger(p, "turn_start", null);
			} else if ((now - start) % CombatState.TURN_TICKS == 0 && now > start) {
				trigger(p, "turn_end", null);
				trigger(p, "turn_start", null);
			}
		}
	}

	// --- events --------------------------------------------------------------------------------

	private static Player playerOf(Entity damager) {
		if (damager instanceof Player p) {
			return p;
		}
		return damager instanceof Projectile pr && pr.getShooter() instanceof Player p ? p : null;
	}

	private static LivingEntity livingOf(Entity damager) {
		if (damager instanceof LivingEntity l) {
			return l;
		}
		return damager instanceof Projectile pr && pr.getShooter() instanceof LivingEntity l ? l : null;
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onDamage(EntityDamageByEntityEvent event) {
		if (!(event.getEntity() instanceof LivingEntity victim)) {
			return;
		}
		Player attacker = playerOf(event.getDamager());
		LivingEntity source = livingOf(event.getDamager());
		boolean dungeonBasic = attacker != null && MdWorld.in(victim) && !Damage.dealing();
		if (attacker != null && attacker != victim && !dungeonBasic) {
			double damage = event.getDamage() * dealt(attacker, victim);
			event.setDamage(damage);
			double steal = sum(attacker, "lifesteal", "pct", victim);
			if (steal > 0) {
				var max = attacker.getAttribute(Attribute.MAX_HEALTH);
				attacker.setHealth(Math.min(max == null ? 20 : max.getValue(), attacker.getHealth() + damage * steal / 100.0));
			}
		}
		if (victim instanceof Player hurt && source != null && source != hurt) {
			event.setDamage(event.getDamage() * taken(hurt, source));
			Bukkit.getScheduler().runTask(IndexPrescriptPlugin.instance(), () -> trigger(hurt, "hurt", source instanceof Player ? null : source));
		}
		if (attacker == null || attacker == victim) {
			return;
		}
		LAST_ATTACKER.put(victim.getUniqueId(), attacker.getUniqueId());
		long now = CaduceusListener.now();
		Long last = LAST_HIT.get(attacker.getUniqueId());
		if (last != null && now - last < HIT_GAP) {
			return;
		}
		LAST_HIT.put(attacker.getUniqueId(), now);
		int poise = CombatState.of(attacker).poisePotency(now);
		boolean crit = poise > 0 && ThreadLocalRandom.current().nextInt(100) < poise * 5;
		Bukkit.getScheduler().runTask(IndexPrescriptPlugin.instance(), () -> {
			trigger(attacker, "hit", victim);
			if (crit) {
				trigger(attacker, "crit", victim);
			}
		});
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onDeath(EntityDeathEvent event) {
		LivingEntity dead = event.getEntity();
		LAST_ATTACKER.remove(dead.getUniqueId());
		if (dead instanceof Player down) {
			MdRun run = MdRun.of(down);
			if (run != null) {
				for (Player p : run.fighters()) {
					if (p != down) {
						trigger(p, "ally_down", null);
					}
				}
			}
			return;
		}
		Player killer = dead.getKiller();
		if (killer != null) {
			trigger(killer, "kill", dead);
		}
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		UUID id = event.getPlayer().getUniqueId();
		USES.remove(id);
		LAST_HIT.remove(id);
		COMBAT.remove(id);
	}
}
