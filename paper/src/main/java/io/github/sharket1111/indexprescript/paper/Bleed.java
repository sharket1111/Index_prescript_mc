package io.github.sharket1111.indexprescript.paper;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Bleed (출혈), as in Limbus Company: a potency and a count, kept in
 * {@link CombatState}. Each time it triggers, the bleeding unit takes fixed
 * damage by its potency ({@link #DAMAGE_PER_POTENCY} health a point, 3 in the
 * original's numbers) and one count is used up. It triggers as in the
 * original when the bleeding unit attacks (at most once every
 * {@link #ATTACK_GAP} ticks, so an area attack does not drain it at once),
 * and whenever a Mimicry blow lands on it. It wears off 30 seconds after it
 * was last given or triggered. Shown under units with the Bleed icon.
 */
final class Bleed implements Listener {
	static final double DAMAGE_PER_POTENCY = 0.3;
	private static final int ATTACK_GAP = 10;
	private static final Color BLOOD = Color.fromRGB(0xB0101A);
	private static final Color DARK = Color.fromRGB(0x5A060C);
	private static final Map<UUID, Long> LAST_ATTACK = new ConcurrentHashMap<>();

	/** Bleed {@code potency} and count +{@code count} on {@code target}. */
	static void inflict(LivingEntity target, int potency, int count) {
		if (!target.isValid() || target.isDead() || potency <= 0 && count <= 0) {
			return;
		}
		CombatState.of(target).addBleed(potency, count, CaduceusListener.now());
		Location at = target.getLocation().add(0, target.getHeight() * 0.6, 0);
		target.getWorld().spawnParticle(Particle.DUST, at, 6 + potency, 0.25, 0.35, 0.25, 0.0, new Particle.DustOptions(BLOOD, 1.1F));
	}

	/**
	 * Triggers the target's Bleed: fixed damage by potency, one count used up.
	 * The damage is credited to {@code source} (null for none). Returns the health taken.
	 */
	static double trigger(LivingEntity target, LivingEntity source) {
		if (!target.isValid() || target.isDead()) {
			return 0.0;
		}
		int potency = CombatState.of(target).consumeBleed(CaduceusListener.now());
		if (potency <= 0) {
			return 0.0;
		}
		double before = target.getHealth() + target.getAbsorptionAmount();
		Damage.deal(target, potency * DAMAGE_PER_POTENCY, source, true);
		double taken = before - (target.isDead() ? 0.0 : target.getHealth() + target.getAbsorptionAmount());
		World world = target.getWorld();
		Location at = target.getLocation().add(0, target.getHeight() * 0.6, 0);
		world.spawnParticle(Particle.DUST, at, 14 + Math.min(30, potency * 2), 0.3, 0.45, 0.3, 0.0, new Particle.DustOptions(BLOOD, 1.5F));
		world.spawnParticle(Particle.DUST, target.getLocation().add(0, 0.1, 0), 8, 0.4, 0.02, 0.4, 0.0, new Particle.DustOptions(DARK, 1.8F));
		world.spawnParticle(Particle.DAMAGE_INDICATOR, at, 2, 0.2, 0.2, 0.2, 0.05);
		world.playSound(at, Sound.ENTITY_PLAYER_HURT_SWEET_BERRY_BUSH, SoundCategory.PLAYERS, 0.8F, 0.7F);
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

	/** A bleeding unit that attacks bleeds. */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onAttack(EntityDamageByEntityEvent event) {
		LivingEntity attacker = attackerOf(event.getDamager());
		if (attacker == null || attacker == event.getEntity() || CombatState.of(attacker).bleedPotency(CaduceusListener.now()) <= 0) {
			return;
		}
		long now = CaduceusListener.now();
		Long last = LAST_ATTACK.get(attacker.getUniqueId());
		if (last != null && now - last < ATTACK_GAP) {
			return;
		}
		LAST_ATTACK.put(attacker.getUniqueId(), now);
		Bukkit.getScheduler().runTask(IndexPrescriptPlugin.instance(), () -> trigger(attacker, null));
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		LAST_ATTACK.remove(event.getPlayer().getUniqueId());
	}
}
