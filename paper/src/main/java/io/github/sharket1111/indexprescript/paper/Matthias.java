package io.github.sharket1111.indexprescript.paper;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * Matthias's passives for whoever wields Laevatein in the main hand, mapped
 * from Limbus Company (a "turn" is {@link CombatState#TURN_TICKS}):
 *
 * - [Watch Closely, Daughter!] (max 10, starts full): per stack Laevatein damage +4%,
 *   per 2 stacks +0.5; loses 1 for every 2 hits taken.
 * - [Middle - Grudge] (max 10): +1 for every 2 hits taken; per stack damage +0.4 and
 *   damage taken +2%; at max, damage taken +20% more.
 * - Grudge: dropping below 80%, 60% and 40% health (once each) fills Grudge; with full
 *   Grudge the next charged Laevatein hit is a powerful attack (x2, fire burst) that
 *   spends all Grudge.
 * - [Super Grit]: gained on releasing Laevatein (6/8/10 s by stage); damage taken is 1.
 * - [Middle - Grudge Tattoo] (max 3): +1 per release strike, gone a turn after the last;
 *   per stack damage +10%.
 * - [Vengeance Ledger]: whoever hurts the wielder is entered for 30 s; Laevatein hits on
 *   them heal 2 health, once per second.
 *
 * Spirit (SP) does not exist in Minecraft, so Super Grit's SP clauses are dropped and the
 * Ledger heals health instead. Stacks reset after a minute out of combat. State lives in
 * memory only.
 */
final class Matthias implements Listener {
	private static final int MAX_DAUGHTER = 10;
	private static final int MAX_GRUDGE = 10;
	private static final int MAX_TATTOO = 3;
	private static final double DAUGHTER_DAMAGE_PER_STACK = 0.04;
	private static final double DAUGHTER_FLAT_PER_TWO = 0.5;
	private static final double GRUDGE_DAMAGE_PER_STACK = 0.4;
	private static final double GRUDGE_TAKEN_PER_STACK = 0.02;
	private static final double GRUDGE_MAX_TAKEN = 0.20;
	private static final double TATTOO_DAMAGE_PER_STACK = 0.10;
	private static final double POWERFUL_MULTIPLIER = 2.0;
	private static final double[] HEALTH_THRESHOLDS = {0.8, 0.6, 0.4};
	private static final double THRESHOLD_RESET = 0.9;
	private static final int[] GRIT_TICKS = {0, 120, 160, 200};
	private static final int LEDGER_TICKS = 600;
	private static final int LEDGER_HEAL_COOLDOWN = 20;
	private static final double LEDGER_HEAL = 2.0;
	private static final int OUT_OF_COMBAT_TICKS = 1200;
	private static final int INDICATOR_INTERVAL = 4;

	private static final Map<UUID, Matthias.State> STATES = new ConcurrentHashMap<>();
	// Ledger entries: entity UUID -> game tick the entry expires.
	private static final Map<UUID, Long> LEDGER = new ConcurrentHashMap<>();

	private static final class State {
		int daughter = MAX_DAUGHTER;
		int grudge;
		int hitsTaken;
		boolean[] thresholdsUsed = new boolean[HEALTH_THRESHOLDS.length];
		long gritUntil;
		int tattoo;
		long tattooUntil;
		long lastCombat;
		long ledgerHealAt;

		int tattoo(long now) {
			return now < tattooUntil ? tattoo : 0;
		}
	}

	private static State state(Player player) {
		return STATES.computeIfAbsent(player.getUniqueId(), id -> new State());
	}

	private static boolean wielding(Player player) {
		return Items.isLaevatein(player.getInventory().getItemInMainHand());
	}

	/** Damage multiplier and flat bonus applied to Laevatein's attacks: base * mult + flat. */
	static double scale(Player player, double base) {
		State s = state(player);
		long now = CaduceusListener.now();
		double multiplier = 1.0 + s.daughter * DAUGHTER_DAMAGE_PER_STACK + s.tattoo(now) * TATTOO_DAMAGE_PER_STACK;
		double flat = (s.daughter / 2) * DAUGHTER_FLAT_PER_TWO + s.grudge * GRUDGE_DAMAGE_PER_STACK;
		return base * multiplier + flat;
	}

	static void grantGrit(Player player, int stage) {
		State s = state(player);
		s.gritUntil = CaduceusListener.now() + GRIT_TICKS[Math.min(stage, GRIT_TICKS.length - 1)];
	}

	static void addTattoo(Player player) {
		State s = state(player);
		long now = CaduceusListener.now();
		s.tattoo = Math.min(MAX_TATTOO, s.tattoo(now) + 1);
		s.tattooUntil = now + CombatState.TURN_TICKS;
		s.lastCombat = now;
	}

	/** Called every tick: out-of-combat reset and the particle indicators. */
	static void tick() {
		long now = CaduceusListener.now();
		if (now % INDICATOR_INTERVAL != 0) {
			return;
		}
		for (Player player : Bukkit.getOnlinePlayers()) {
			State s = STATES.get(player.getUniqueId());
			if (s == null) {
				continue;
			}
			if (now - s.lastCombat > OUT_OF_COMBAT_TICKS) {
				s.daughter = MAX_DAUGHTER;
				s.grudge = 0;
				s.hitsTaken = 0;
			}
			if (!wielding(player)) {
				continue;
			}
			if (s.grudge >= MAX_GRUDGE) {
				LaevateinEffects.grudgeAura(player);
			}
			if (now < s.gritUntil) {
				LaevateinEffects.grit(player);
			}
		}
		LEDGER.values().removeIf(until -> now >= until);
	}

	/** Laevatein's own hits: Daughter, Grudge and Tattoo bonuses, and the powerful attack at full Grudge. */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onAttack(EntityDamageByEntityEvent event) {
		if (Damage.dealing() || event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK
				|| !(event.getDamager() instanceof Player player) || !wielding(player)
				|| !(event.getEntity() instanceof LivingEntity target)) {
			return;
		}
		State s = state(player);
		long now = CaduceusListener.now();
		s.lastCombat = now;
		double damage = scale(player, event.getDamage());
		if (s.grudge >= MAX_GRUDGE && Laevatein.charged(player)) {
			damage *= POWERFUL_MULTIPLIER;
			s.grudge = 0;
			LaevateinEffects.powerful(player, target);
		}
		event.setDamage(damage);

		Long entry = LEDGER.get(target.getUniqueId());
		if (entry != null && now < entry && now >= s.ledgerHealAt) {
			s.ledgerHealAt = now + LEDGER_HEAL_COOLDOWN;
			player.setHealth(Math.min(maxHealth(player), player.getHealth() + LEDGER_HEAL));
		}
	}

	/** Damage taken by the wielder: Super Grit fixes it to 1, otherwise Grudge raises it. */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onHurt(EntityDamageEvent event) {
		if (!(event.getEntity() instanceof Player player) || !wielding(player) || event.getDamage() <= 0) {
			return;
		}
		State s = state(player);
		long now = CaduceusListener.now();
		if (now < s.gritUntil) {
			event.setDamage(Math.min(event.getDamage(), 1.0));
			return;
		}
		double taken = 1.0 + s.grudge * GRUDGE_TAKEN_PER_STACK + (s.grudge >= MAX_GRUDGE ? GRUDGE_MAX_TAKEN : 0.0);
		event.setDamage(event.getDamage() * taken);
	}

	/** After a hit lands on the wielder: stacks, health thresholds and the Ledger. */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void afterHurt(EntityDamageEvent event) {
		if (!(event.getEntity() instanceof Player player) || !wielding(player) || event.getFinalDamage() <= 0) {
			return;
		}
		State s = state(player);
		long now = CaduceusListener.now();
		LivingEntity attacker = event instanceof EntityDamageByEntityEvent byEntity ? attacker(byEntity.getDamager()) : null;
		if (attacker != null && attacker != player) {
			s.lastCombat = now;
			LEDGER.put(attacker.getUniqueId(), now + LEDGER_TICKS);
			if (++s.hitsTaken % 2 == 0) {
				s.daughter = Math.max(0, s.daughter - 1);
				gainGrudge(player, s, s.grudge + 1);
			}
		}

		double ratio = Math.max(0.0, player.getHealth() - event.getFinalDamage()) / maxHealth(player);
		for (int i = 0; i < HEALTH_THRESHOLDS.length; i++) {
			if (!s.thresholdsUsed[i] && ratio < HEALTH_THRESHOLDS[i]) {
				s.thresholdsUsed[i] = true;
				gainGrudge(player, s, MAX_GRUDGE);
			}
		}
	}

	/** Health thresholds re-arm once the wielder is healed back up. */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onHeal(org.bukkit.event.entity.EntityRegainHealthEvent event) {
		if (event.getEntity() instanceof Player player && STATES.containsKey(player.getUniqueId())
				&& (player.getHealth() + event.getAmount()) / maxHealth(player) >= THRESHOLD_RESET) {
			state(player).thresholdsUsed = new boolean[HEALTH_THRESHOLDS.length];
		}
	}

	private static void gainGrudge(Player player, State s, int grudge) {
		boolean wasFull = s.grudge >= MAX_GRUDGE;
		s.grudge = Math.min(MAX_GRUDGE, grudge);
		if (!wasFull && s.grudge >= MAX_GRUDGE) {
			LaevateinEffects.grudgeFull(player);
		}
	}

	private static LivingEntity attacker(Entity damager) {
		if (damager instanceof Projectile projectile && projectile.getShooter() instanceof LivingEntity shooter) {
			return shooter;
		}
		return damager instanceof LivingEntity living ? living : null;
	}

	private static double maxHealth(LivingEntity entity) {
		AttributeInstance attribute = entity.getAttribute(Attribute.MAX_HEALTH);
		return attribute == null ? 20.0 : attribute.getValue();
	}
}
