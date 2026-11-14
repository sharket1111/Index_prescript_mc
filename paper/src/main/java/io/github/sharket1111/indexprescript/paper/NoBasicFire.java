package io.github.sharket1111.indexprescript.paper;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustByEntityEvent;

/**
 * A player's plain attacks (a swing with Fire Aspect, a Flame arrow) set
 * nothing alight: fire comes only from the skills (which deal their damage
 * through Damage, so it is set by the skill itself). Things burnt that way
 * used to die of the fire though the hit itself did 1 damage.
 */
final class NoBasicFire implements Listener {
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onCombust(EntityCombustByEntityEvent event) {
		Entity by = event.getCombuster();
		boolean player = by instanceof Player || by instanceof Projectile p && p.getShooter() instanceof Player;
		if (player && !Damage.dealing()) {
			event.setCancelled(true);
		}
	}
}
