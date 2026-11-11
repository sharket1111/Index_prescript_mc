package io.github.sharket1111.indexprescript.paper;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Rupture, as in the original: a unit with Rupture takes extra fixed damage
 * equal to its potency each time it is hit, using up one count. Linked to
 * Gluttony. Kept in {@link CombatState}; it wears off 30 seconds after it was
 * last given or triggered. Shown under units with the Rupture icon.
 */
final class Rupture implements Listener {
	static final double DAMAGE_PER_POTENCY = 0.3;
	private static final Color TEAL = Color.fromRGB(0x2CC8B4);
	private static final Color PALE = Color.fromRGB(0xC8FFF0);

	/** Rupture {@code potency} and count +{@code count} on {@code target}. */
	static void inflict(LivingEntity target, int potency, int count) {
		if (!target.isValid() || target.isDead() || potency <= 0 && count <= 0) {
			return;
		}
		CombatState.of(target).addRupture(potency, count, CaduceusListener.now());
		Location at = target.getLocation().add(0, target.getHeight() * 0.6, 0);
		target.getWorld().spawnParticle(Particle.DUST, at, 6 + potency, 0.3, 0.35, 0.3, 0.0, new Particle.DustOptions(TEAL, 1.1F));
	}

	/** Triggers the target's Rupture: fixed damage by potency, one count used up. Returns the health taken. */
	static double trigger(LivingEntity target, LivingEntity source) {
		if (!target.isValid() || target.isDead()) {
			return 0.0;
		}
		int potency = CombatState.of(target).consumeRupture(CaduceusListener.now());
		if (potency <= 0) {
			return 0.0;
		}
		double before = target.getHealth() + target.getAbsorptionAmount();
		Damage.dealFixed(target, potency * DAMAGE_PER_POTENCY, source);
		double taken = before - (target.isDead() ? 0.0 : target.getHealth() + target.getAbsorptionAmount());
		World world = target.getWorld();
		Location at = target.getLocation().add(0, target.getHeight() * 0.6, 0);
		world.spawnParticle(Particle.DUST, at, 10 + Math.min(30, potency * 2), 0.35, 0.45, 0.35, 0.0, new Particle.DustOptions(TEAL, 1.4F));
		world.spawnParticle(Particle.DUST, at, 6, 0.2, 0.3, 0.2, 0.0, new Particle.DustOptions(PALE, 1.0F));
		world.spawnParticle(Particle.CRIT, at, 6, 0.2, 0.3, 0.2, 0.2);
		world.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_BREAK, SoundCategory.PLAYERS, 0.8F, 1.3F);
		return taken;
	}

	private static LivingEntity attackerOf(Entity damager) {
		if (damager instanceof LivingEntity living) {
			return living;
		}
		if (damager instanceof Projectile projectile && projectile.getShooter() instanceof LivingEntity shooter) {
			return shooter;
		}
		return null;
	}

	/** A hit on a unit with Rupture sets it off (the plugin's own extra damage does not, so it cannot chain). */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onHit(EntityDamageByEntityEvent event) {
		if (Damage.dealing() && !Damage.countsAsHit() || !(event.getEntity() instanceof LivingEntity target)
				|| CombatState.of(target).rupturePotency(CaduceusListener.now()) <= 0) {
			return;
		}
		LivingEntity attacker = attackerOf(event.getDamager());
		Bukkit.getScheduler().runTask(IndexPrescriptPlugin.instance(), () -> trigger(target, attacker));
	}
}
