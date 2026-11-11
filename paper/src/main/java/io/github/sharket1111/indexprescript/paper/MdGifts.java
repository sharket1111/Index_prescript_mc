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
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

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
		for (Map.Entry<String, Integer> owned : run.gifts.entrySet()) {
			if (run.fuseOnly.contains(owned.getKey())) {
				continue;
			}
			MdData.Gift gift = MdData.gift(owned.getKey());
			if (gift == null) {
				continue;
			}
			for (JsonElement e : gift.level(owned.getValue()).effects()) {
				JsonObject effect = e.getAsJsonObject();
				String when = effect.get("on").getAsString();
				if (when.equals(on) || on.equals("hit") && when.equals("clash_win") && MdRun.rng().nextInt(4) == 0) {
					run(run, effect, actor, target);
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
		List<Player> allies = run.fighters();
		switch (act) {
			case "inflict" -> {
				String kw = e.get("kw").getAsString();
				int potency = e.get("potency").getAsInt();
				int count = e.get("count").getAsInt();
				if (to.equals("random_enemies")) {
					// "Randomly inflict a total of N between all enemies": one at a time.
					List<LivingEntity> pool = enemies.stream().filter(m -> passes(e, m)).toList();
					for (int i = 0; i < potency && !pool.isEmpty(); i++) {
						inflictKeyword(pool.get(MdRun.rng().nextInt(pool.size())), kw, 1, i == 0 ? count : 0);
					}
					return;
				}
				for (LivingEntity t : targets(to, target, enemies, allies, actor)) {
					if (t instanceof Player) {
						continue;
					}
					if (passes(e, t)) {
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
					if (!(t instanceof Player)) {
						MdBattle.debuff(t, e.get("debuff").getAsString(), e.get("amount").getAsInt());
					}
				}
			}
			case "heal_hp" -> {
				for (Player p : players(to, actor, allies)) {
					double max = p.getAttribute(Attribute.MAX_HEALTH) == null ? 20 : p.getAttribute(Attribute.MAX_HEALTH).getValue();
					double amount = e.get("pct").getAsBoolean() ? max * e.get("amount").getAsDouble() / 100 : e.get("amount").getAsDouble() / 5.0;
					p.setHealth(Math.min(max, p.getHealth() + amount));
				}
			}
			case "heal_sp" -> {
				for (Player p : players(to, actor, allies)) {
					Sanity.add(p, (int) Math.round(e.get("amount").getAsDouble()));
				}
			}
			case "cost" -> {
				if (!e.get("pct").getAsBoolean() && !e.get("on").getAsString().equals("reward")) {
					run.cost += (int) e.get("amount").getAsDouble();
					run.updateBar();
				}
			}
			case "tremor_burst" -> {
				for (LivingEntity t : targets(to, target, enemies, allies, actor)) {
					if (!(t instanceof Player)) {
						Tremor.burst(t);
					}
				}
			}
			default -> {
			}
		}
	}

	private static boolean passes(JsonObject e, LivingEntity t) {
		if (!e.has("if")) {
			return true;
		}
		String cond = e.get("if").getAsString();
		long now = CaduceusListener.now();
		if (cond.startsWith("target_has:")) {
			return has(t, cond.substring(11), now);
		}
		if (cond.equals("target_staggered")) {
			return CombatState.of(t).staggered(now);
		}
		return true;
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
			if (act.equals("damage_pct") && (on.equals("damage") || on.equals("passive"))) {
				if (e.has("if")) {
					String cond = e.get("if").getAsString();
					if (cond.startsWith("kind:") && !cond.substring(5).equalsIgnoreCase(kind.name())) {
						continue;
					}
					if (!cond.startsWith("kind:") && !passes(e, target)) {
						continue;
					}
				}
				pct += e.get("pct").getAsDouble();
			}
		}
		pct += 5 * (buffOf(p, "power") + buffOf(p, "damage")) + 2 * buffOf(p, "offense_level") + run.levels;
		pct += Math.min(20, CombatState.of(p).chargeCount(now));
		pct += MdGrace.damageBonus(run);
		MdBattle.Foe foe = MdBattle.foe(target);
		double mult = 1 + pct / 100.0;
		if (foe != null) {
			mult *= foe.taken * (foe.debuffUntil > now ? 1 + 0.1 * foe.defenseDown : 1);
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
			if (e.get("act").getAsString().equals("taken_pct")) {
				pct += e.get("pct").getAsDouble();
			}
		}
		double mult = (1 + pct / 100.0) * Math.max(0.3, 1 - 0.1 * buffOf(p, "protection") - 0.02 * buffOf(p, "defense_level") - 0.01 * run.levels)
				* (1 - MdGrace.protection(run));
		MdBattle.Foe foe = MdBattle.foe(attacker);
		if (foe != null) {
			mult *= foe.damage * foe.rage * (foe.debuffUntil > now ? Math.max(0.3, 1 - 0.1 * foe.offenseDown) : 1);
		}
		return mult;
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
				event.setDamage(event.getDamage() * taken(run, hurt, source));
			}
			MdBattle.Foe foe = MdBattle.foe(source);
			if (MdRun.rng().nextInt(100) < 35) {
				Bukkit.getScheduler().runTask(IndexPrescriptPlugin.instance(), () -> inflictKeyword(hurt, foe.keyword, foe.potency, 1));
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
