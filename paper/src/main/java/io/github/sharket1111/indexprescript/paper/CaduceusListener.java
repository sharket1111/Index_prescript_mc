package io.github.sharket1111.indexprescript.paper;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;

/**
 * Caduceus combat: a charged swing forms a random weapon before the hit, the
 * hit's damage is rescaled to that weapon, and its on-hit effect follows.
 * Only charged swings form a new weapon and apply effects, so spam clicking
 * cannot stack statuses.
 */
final class CaduceusListener implements Listener {
	private static final int FORM_TICKS = 40;
	private static final float CHARGED_THRESHOLD = 0.9F;

	// Per-player bookkeeping, keyed by player UUID.
	private static final Map<UUID, Long> REVERT_AT = new ConcurrentHashMap<>();
	private static final Map<UUID, Boolean> CHARGED = new ConcurrentHashMap<>();
	private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

	private record Pending(CaduceusForm form, boolean crit) {
	}

	static long now() {
		return Bukkit.getCurrentTick();
	}

	/** Keeps the current form from melting back for at least {@code ticks}. */
	static void keepFormed(Player player, int ticks) {
		REVERT_AT.merge(player.getUniqueId(), now() + ticks, Math::max);
	}

	/** Melts formed Caduceus items back into the rod once their time is up; called periodically. */
	static void revertExpired(Player player) {
		Long revertAt = REVERT_AT.get(player.getUniqueId());
		if (revertAt != null && now() < revertAt) {
			return;
		}
		ItemStack[] contents = player.getInventory().getContents();
		for (ItemStack stack : contents) {
			if (Items.isCaduceus(stack) && CaduceusForm.of(stack) != null) {
				Items.setForm(stack, null);
				Effects.melt(player);
			}
		}
		REVERT_AT.remove(player.getUniqueId());
	}

	@EventHandler(ignoreCancelled = true)
	public void onPreAttack(PrePlayerAttackEntityEvent event) {
		Player player = event.getPlayer();
		ItemStack stack = player.getInventory().getItemInMainHand();
		if (!event.willAttack() || !Items.isCaduceus(stack)) {
			return;
		}
		Entity target = event.getAttacked();
		if (Furioso.active(player)) {
			event.setCancelled(true);
			return;
		}
		if (target instanceof LivingEntity living && !living.isDead() && PrescriptTracker.ready(player)) {
			event.setCancelled(true);
			Furioso.start(player, living);
			return;
		}

		boolean charged = player.getAttackCooldown() >= CHARGED_THRESHOLD;
		CHARGED.put(player.getUniqueId(), charged);
		CaduceusForm current = CaduceusForm.of(stack);
		if (charged || current == null) {
			current = CaduceusForm.random(current);
			Items.setForm(stack, current);
			Effects.form(player);
		}
		REVERT_AT.put(player.getUniqueId(), now() + FORM_TICKS);
		Effects.impact(target);
	}

	/**
	 * Rescales the hit (which already includes attack cooldown, vanilla crits
	 * and enchantments) by the form's damage relative to the bare rod.
	 */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onDamage(EntityDamageByEntityEvent event) {
		if (Damage.dealing() || event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK
				|| !(event.getDamager() instanceof Player player)) {
			return;
		}
		CaduceusForm form = CaduceusForm.of(player.getInventory().getItemInMainHand());
		if (form == null || !Items.isCaduceus(player.getInventory().getItemInMainHand())) {
			return;
		}
		CaduceusForm.Hit hit = form.resolve(player, event.getEntity(), now());
		event.setDamage(event.getDamage() * hit.damage() / CaduceusForm.ROD_DAMAGE);
		PENDING.put(player.getUniqueId(), new Pending(form, hit.crit()));
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void afterDamage(EntityDamageByEntityEvent event) {
		if (Damage.dealing() || !(event.getDamager() instanceof Player player) || !(event.getEntity() instanceof LivingEntity target)) {
			return;
		}
		Pending pending = PENDING.remove(player.getUniqueId());
		if (pending == null) {
			return;
		}
		long now = now();
		if (pending.crit()) {
			Effects.crit(target);
		}
		boolean charged = !Boolean.FALSE.equals(CHARGED.get(player.getUniqueId()));
		boolean staggered = charged && pending.form().onHit(player, target, now);
		if (charged) {
			PrescriptTracker.onHit(player, pending.form(), pending.crit(), staggered);
		}
	}
}
