package io.github.sharket1111.indexprescript.paper;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Sanity (SP, -45 to 45), as in Limbus Company, kept for every player:
 * - landing a hit on a creature: +{@link #HIT_GAIN}; being hit by one: -{@link #HURT_LOSS}
 * - winning or losing a clash: up or down by how many rounds it took (see {@link Clash})
 * - slaying a creature: +{@link #KILL_GAIN}
 * - after {@link #CALM_TICKS} out of combat it drifts back to 0, a point a second
 * - a coin lands heads with a chance of 50% + SP%
 * Shown at the bottom left of the screen (see {@link Hud}). Creatures other
 * than players stay at 0.
 */
final class Sanity implements Listener {
	static final int MIN = -45;
	static final int MAX = 45;
	static final int HIT_GAIN = 2;
	static final int HURT_LOSS = 2;
	static final int KILL_GAIN = 10;
	static final int CALM_TICKS = 30 * 20;
	private static final Map<UUID, Integer> VALUES = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_FIGHT = new ConcurrentHashMap<>();

	static int of(Entity entity) {
		if (entity instanceof LivingEntity living && ClashDummy.is(living)) {
			// A clash dummy keeps the Sanity it was given.
			return ClashDummy.sanity(living);
		}
		return entity == null ? 0 : VALUES.getOrDefault(entity.getUniqueId(), 0);
	}

	static void set(LivingEntity entity, int value) {
		if (!(entity instanceof Player)) {
			return;
		}
		int clamped = Math.max(MIN, Math.min(MAX, value));
		if (clamped == 0) {
			VALUES.remove(entity.getUniqueId());
		} else {
			VALUES.put(entity.getUniqueId(), clamped);
		}
	}

	static void add(LivingEntity entity, int amount) {
		set(entity, of(entity) + amount);
	}

	static void lose(LivingEntity entity, int amount) {
		add(entity, -amount);
	}

	/** The chance (0-1) that one of this creature's coins lands heads: 50% + SP%. */
	static double headsChance(Entity entity) {
		return Math.max(0.05, Math.min(0.95, (50 + of(entity)) / 100.0));
	}

	static void fought(Entity entity) {
		if (entity instanceof Player) {
			LAST_FIGHT.put(entity.getUniqueId(), CaduceusListener.now());
		}
	}

	static boolean inCombat(Player player) {
		Long at = LAST_FIGHT.get(player.getUniqueId());
		return at != null && CaduceusListener.now() - at < CALM_TICKS;
	}

	/** Every tick: out of combat for 30 s, SP drifts to 0 by a point a second. */
	static void tick() {
		long now = CaduceusListener.now();
		if (now % 20 != 0) {
			return;
		}
		for (Player player : Bukkit.getOnlinePlayers()) {
			int value = of(player);
			if (value != 0 && !inCombat(player)) {
				set(player, value - Integer.signum(value));
			}
		}
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onHit(EntityDamageByEntityEvent event) {
		if (!(event.getEntity() instanceof LivingEntity victim) || event.getFinalDamage() <= 0) {
			return;
		}
		Entity damager = event.getDamager();
		if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
			damager = shooter;
		}
		if (damager == victim || !(damager instanceof LivingEntity attacker)) {
			return;
		}
		fought(attacker);
		fought(victim);
		add(attacker, HIT_GAIN);
		lose(victim, HURT_LOSS);
	}

	/** The original: slaying a foe gives 10. */
	@EventHandler(priority = EventPriority.MONITOR)
	public void onKill(org.bukkit.event.entity.EntityDeathEvent event) {
		Player killer = event.getEntity().getKiller();
		if (killer != null && killer != event.getEntity()) {
			add(killer, KILL_GAIN);
			fought(killer);
		}
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		LAST_FIGHT.remove(event.getPlayer().getUniqueId());
		Hud.forget(event.getPlayer());
	}
}
