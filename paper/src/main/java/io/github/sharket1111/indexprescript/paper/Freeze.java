package io.github.sharket1111.indexprescript.paper;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.util.Vector;

/**
 * Time stops for the others while a clash or a skill plays out, as on the original's stage: while a clash
 * is being decided (and the winner strikes), every creature near it except the two in the clash stands
 * still; while a player's skill plays, every creature near it stands still, its target too. A frozen
 * creature does not move, aim, attack or use skills (its blows and shots are cancelled), so nothing hits
 * the player while the camera has moved them away.
 */
final class Freeze implements Listener {
	private static final double RADIUS = 48;
	private static final Set<UUID> FROZEN = new HashSet<>();
	/** The creatures this made unaware (to wake again); dungeon enemies are kept by their own AI. */
	private static final Set<UUID> MADE_UNAWARE = new HashSet<>();

	static boolean frozen(Entity e) {
		return e != null && FROZEN.contains(e.getUniqueId());
	}

	/** A player's own skill animation is running (the clash's strike is counted separately). */
	private static boolean usingSkill(Player p) {
		return Ego.EgoSkill.active(p) || LaevateinSkill.active(p) || RenoirSkill.active(p) || Furioso.active(p) || BlackGodSkill.active(p)
				|| RienSkill.active(p) || MimicrySkill.active(p) || Laevatein.busy(p);
	}

	private static boolean freezable(Entity e, Set<UUID> exempt) {
		return e instanceof LivingEntity l && !(e instanceof Player) && !(e instanceof ArmorStand) && l.isValid() && !l.isDead()
				&& !Cinematic.isActor(l) && !exempt.contains(e.getUniqueId());
	}

	private static void around(Location at, Set<UUID> exempt, Set<UUID> into) {
		if (at == null || at.getWorld() == null) {
			return;
		}
		for (Entity e : at.getWorld().getNearbyEntities(at, RADIUS, RADIUS / 2, RADIUS)) {
			if (freezable(e, exempt)) {
				into.add(e.getUniqueId());
			}
		}
	}

	static void tick() {
		Set<UUID> now = new HashSet<>();
		// A clash: the two in it go on, everyone else waits.
		Set<UUID> dueling = Clash.dueling();
		for (UUID id : dueling) {
			Entity e = Bukkit.getEntity(id);
			if (e instanceof Player p) {
				around(p.getLocation(), dueling, now);
			}
		}
		for (Map.Entry<LivingEntity, LivingEntity> s : Clash.striking().entrySet()) {
			LivingEntity user = s.getKey();
			if (user instanceof Player p) {
				// A player's skill: every creature stands still, the target too.
				around(p.getLocation(), Set.of(), now);
			} else if (s.getValue() instanceof Player victim) {
				// An enemy's skill at a player (after winning a clash, or unopposed): only it moves.
				around(victim.getLocation(), Set.of(user.getUniqueId()), now);
			}
		}
		for (Player p : Bukkit.getOnlinePlayers()) {
			if (usingSkill(p)) {
				around(p.getLocation(), Set.of(), now);
			}
		}
		for (UUID id : FROZEN) {
			if (!now.contains(id) && MADE_UNAWARE.remove(id) && Bukkit.getEntity(id) instanceof Mob m) {
				m.setAware(true);
			}
		}
		FROZEN.clear();
		FROZEN.addAll(now);
		for (UUID id : now) {
			if (!(Bukkit.getEntity(id) instanceof LivingEntity l)) {
				continue;
			}
			if (l instanceof Mob m) {
				m.setTarget(null);
				if (MdBattle.foe(m) == null && m.isAware()) {
					m.setAware(false);
					MADE_UNAWARE.add(id);
				}
			}
			// Held in place, unless a blow just knocked it (the skill's own knockback plays out).
			if (l.getNoDamageTicks() <= 0) {
				Vector v = l.getVelocity();
				l.setVelocity(new Vector(0, Math.min(0, v.getY()), 0));
			}
		}
		MADE_UNAWARE.retainAll(FROZEN);
	}

	/** Wakes everything (on shutdown). */
	static void releaseAll() {
		for (UUID id : MADE_UNAWARE) {
			if (Bukkit.getEntity(id) instanceof Mob m) {
				m.setAware(true);
			}
		}
		MADE_UNAWARE.clear();
		FROZEN.clear();
	}

	private static Entity source(Entity damager) {
		return damager instanceof Projectile pr && pr.getShooter() instanceof Entity shooter ? shooter : damager;
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onHit(EntityDamageByEntityEvent event) {
		if (frozen(source(event.getDamager()))) {
			event.setCancelled(true);
		}
	}

	@EventHandler(ignoreCancelled = true)
	public void onTarget(EntityTargetEvent event) {
		if (frozen(event.getEntity()) && event.getTarget() != null) {
			event.setCancelled(true);
		}
	}

	@EventHandler(ignoreCancelled = true)
	public void onShoot(ProjectileLaunchEvent event) {
		if (event.getEntity().getShooter() instanceof Entity shooter && frozen(shooter)) {
			event.setCancelled(true);
		}
	}
}
