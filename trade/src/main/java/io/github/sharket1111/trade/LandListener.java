package io.github.sharket1111.trade;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** Enforces land permissions and protected zones. */
final class LandListener implements Listener {
	private static final long NOTICE_MS = 2000;
	private final Map<UUID, Long> lastNotice = new ConcurrentHashMap<>();

	private boolean deny(Player player, Location at, Land.Perm perm, Cancellable event) {
		if (Land.allowed(player, at, perm)) {
			return false;
		}
		event.setCancelled(true);
		long now = System.currentTimeMillis();
		Long last = lastNotice.get(player.getUniqueId());
		if (last == null || now - last > NOTICE_MS) {
			lastNotice.put(player.getUniqueId(), now);
			Land.Claim claim = Land.claimAt(at);
			String whose = claim != null ? claim.ownerName + "님의 땅" : "보호구역";
			player.sendMessage(Component.text(whose + "에서는 " + perm.label + " 권한이 없습니다.", NamedTextColor.RED));
		}
		return true;
	}

	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	public void onBreak(BlockBreakEvent event) {
		deny(event.getPlayer(), event.getBlock().getLocation(), Land.Perm.BREAK, event);
	}

	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	public void onPlace(BlockPlaceEvent event) {
		deny(event.getPlayer(), event.getBlock().getLocation(), Land.Perm.BUILD, event);
	}

	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	public void onBucketEmpty(PlayerBucketEmptyEvent event) {
		deny(event.getPlayer(), event.getBlock().getLocation(), Land.Perm.BUILD, event);
	}

	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	public void onBucketFill(PlayerBucketFillEvent event) {
		deny(event.getPlayer(), event.getBlock().getLocation(), Land.Perm.BREAK, event);
	}

	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	public void onIgnite(BlockIgniteEvent event) {
		if (event.getPlayer() != null) {
			deny(event.getPlayer(), event.getBlock().getLocation(), Land.Perm.BUILD, event);
		}
	}

	/** Using blocks (doors, chests, buttons, levers...) and stepping on plates or farmland. */
	@EventHandler(priority = EventPriority.LOW)
	public void onInteract(PlayerInteractEvent event) {
		Block block = event.getClickedBlock();
		if (block == null) {
			return;
		}
		boolean use = event.getAction() == Action.RIGHT_CLICK_BLOCK && block.getType().isInteractable();
		if (use || event.getAction() == Action.PHYSICAL) {
			deny(event.getPlayer(), block.getLocation(), Land.Perm.INTERACT, event);
		}
	}

	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	public void onInteractEntity(PlayerInteractEntityEvent event) {
		deny(event.getPlayer(), event.getRightClicked().getLocation(), Land.Perm.INTERACT, event);
	}

	/** Hurting animals, armour stands, frames and the like (not monsters or players) counts as breaking. */
	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	public void onDamage(EntityDamageByEntityEvent event) {
		Entity victim = event.getEntity();
		if (victim instanceof Monster || victim instanceof Player) {
			return;
		}
		Entity damager = event.getDamager();
		if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
			damager = shooter;
		}
		if (damager instanceof Player player) {
			deny(player, victim.getLocation(), Land.Perm.BREAK, event);
		}
	}

	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	public void onHangingBreak(HangingBreakByEntityEvent event) {
		Entity remover = event.getRemover();
		if (remover instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
			remover = shooter;
		}
		if (remover instanceof Player player) {
			deny(player, event.getEntity().getLocation(), Land.Perm.BREAK, event);
		}
	}

	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	public void onHangingPlace(HangingPlaceEvent event) {
		if (event.getPlayer() != null) {
			deny(event.getPlayer(), event.getEntity().getLocation(), Land.Perm.BUILD, event);
		}
	}

	/** Explosions leave land and protected zones intact. */
	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	public void onEntityExplode(EntityExplodeEvent event) {
		event.blockList().removeIf(block -> Land.guarded(block.getLocation()));
	}

	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	public void onBlockExplode(BlockExplodeEvent event) {
		event.blockList().removeIf(block -> Land.guarded(block.getLocation()));
	}

	/** Walking into a chunk without access is stopped at its edge. */
	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	public void onMove(PlayerMoveEvent event) {
		Location from = event.getFrom();
		Location to = event.getTo();
		if ((from.getBlockX() >> 4) == (to.getBlockX() >> 4) && (from.getBlockZ() >> 4) == (to.getBlockZ() >> 4)
				&& from.getWorld() == to.getWorld()) {
			return;
		}
		deny(event.getPlayer(), to, Land.Perm.ACCESS, event);
	}

	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	public void onTeleport(PlayerTeleportEvent event) {
		if (event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL
				|| event.getCause() == PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT) {
			deny(event.getPlayer(), event.getTo(), Land.Perm.ACCESS, event);
		}
	}
}
