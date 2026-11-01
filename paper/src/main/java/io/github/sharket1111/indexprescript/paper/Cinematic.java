package io.github.sharket1111.indexprescript.paper;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import com.destroystokyo.paper.event.player.PlayerStopSpectatingEntityEvent;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;

/**
 * A separate camera for skill animations. The client draws any camera in
 * first person (hiding the player and overlaying the held item), so instead
 * the player spectates an invisible camera entity while a mannequin with the
 * player's skin and equipment acts out the moves. Sequences drive the
 * {@link #actor()} in place of the player. Afterwards the player returns to
 * their game mode at the actor's spot; the game mode is also stored on the
 * player so a restart mid-animation restores it on the next join. Bedrock
 * players (Floodgate) keep their normal view.
 */
final class Cinematic {
	private static final String FLOODGATE_PREFIX = "00000000-0000-0000-";
	private static final int ATTACH_DELAY_TICKS = 2;
	private static final NamespacedKey SAVED_MODE = new NamespacedKey(Items.NAMESPACE, "cinematic_mode");
	private static final Map<UUID, Cinematic> ACTIVE = new ConcurrentHashMap<>();

	private final Player viewer;
	private final ItemDisplay camera;
	private final Mannequin actor;
	private int age;
	private boolean ended;

	private Cinematic(Player viewer, ItemDisplay camera, Mannequin actor) {
		this.viewer = viewer;
		this.camera = camera;
		this.actor = actor;
	}

	/** Starts a camera and an actor in the viewer's place; returns null for Bedrock players or if one is running. */
	static Cinematic start(Player viewer, Location cameraAt) {
		if (viewer.getUniqueId().toString().startsWith(FLOODGATE_PREFIX) || ACTIVE.containsKey(viewer.getUniqueId())) {
			return null;
		}
		World world = viewer.getWorld();
		ItemDisplay camera = world.spawn(cameraAt, ItemDisplay.class, display -> {
			display.setPersistent(false);
			display.setTeleportDuration(2);
		});
		Mannequin actor = world.spawn(viewer.getLocation(), Mannequin.class, mannequin -> {
			mannequin.setPersistent(false);
			mannequin.setProfile(ResolvableProfile.resolvableProfile(viewer.getPlayerProfile()));
			mannequin.setDescription(Component.empty());
			mannequin.setImmovable(true);
			mannequin.setInvulnerable(true);
			mannequin.setGravity(false);
			mannequin.setMainHand(viewer.getMainHand());
			copyEquipment(viewer.getEquipment(), mannequin.getEquipment());
		});
		Cinematic cinematic = new Cinematic(viewer, camera, actor);
		ACTIVE.put(viewer.getUniqueId(), cinematic);

		viewer.getPersistentDataContainer().set(SAVED_MODE, PersistentDataType.STRING, viewer.getGameMode().name());
		viewer.setGameMode(GameMode.SPECTATOR);
		return cinematic;
	}

	private static void copyEquipment(EntityEquipment from, EntityEquipment to) {
		to.setItemInMainHand(copy(from.getItemInMainHand()));
		to.setItemInOffHand(copy(from.getItemInOffHand()));
		to.setHelmet(copy(from.getHelmet()));
		to.setChestplate(copy(from.getChestplate()));
		to.setLeggings(copy(from.getLeggings()));
		to.setBoots(copy(from.getBoots()));
	}

	private static ItemStack copy(ItemStack stack) {
		return stack == null ? null : stack.clone();
	}

	/** The entity that performs the moves while the camera runs. */
	LivingEntity actor() {
		return actor;
	}

	/** Where effects around {@code player} belong: their stand-in while a camera runs, else the player. */
	static LivingEntity body(Player player) {
		Cinematic cinematic = ACTIVE.get(player.getUniqueId());
		return cinematic != null && !cinematic.ended ? cinematic.actor : player;
	}

	/** Shows the viewer's current main-hand item (e.g. a new form) in the actor's hand. */
	void syncHeld() {
		actor.getEquipment().setItemInMainHand(copy(viewer.getInventory().getItemInMainHand()));
	}

	/** Moves the camera to {@code position} looking at {@code focus}, optionally shaken; call every tick. */
	void frame(Vector position, Vector focus, double shake) {
		if (ended) {
			return;
		}
		Vector pos = position.clone();
		if (shake > 0) {
			ThreadLocalRandom random = ThreadLocalRandom.current();
			pos.add(new Vector((random.nextDouble() - 0.5) * shake, (random.nextDouble() - 0.5) * shake, (random.nextDouble() - 0.5) * shake));
		}
		Location location = pos.toLocation(camera.getWorld());
		location.setDirection(focus.clone().subtract(pos));
		camera.teleport(location);
		// The client must know the camera entity before it can spectate it.
		if (++age == ATTACH_DELAY_TICKS && viewer.getGameMode() == GameMode.SPECTATOR) {
			viewer.setSpectatorTarget(camera);
		}
	}

	/** Returns the viewer to their game mode, standing where the actor ended up. */
	void end() {
		if (ended) {
			return;
		}
		ended = true;
		ACTIVE.remove(viewer.getUniqueId());
		Location landing = actor.getLocation();
		if (viewer.isOnline()) {
			viewer.setSpectatorTarget(null);
			restoreMode(viewer);
			viewer.teleport(landing);
		}
		actor.remove();
		camera.remove();
	}

	static void endAll() {
		for (Cinematic cinematic : Set.copyOf(ACTIVE.values())) {
			cinematic.end();
		}
	}

	private static void restoreMode(Player player) {
		String saved = player.getPersistentDataContainer().get(SAVED_MODE, PersistentDataType.STRING);
		if (saved == null) {
			return;
		}
		player.getPersistentDataContainer().remove(SAVED_MODE);
		try {
			player.setGameMode(GameMode.valueOf(saved));
		} catch (IllegalArgumentException e) {
			player.setGameMode(GameMode.SURVIVAL);
		}
	}

	/** Point on a horizontal circle around {@code center}. */
	static Vector orbit(Vector center, double angle, double radius, double height) {
		return center.clone().add(new Vector(Math.cos(angle) * radius, height, Math.sin(angle) * radius));
	}

	/** Keeps the viewer on the camera and cleans up after quits and restarts. */
	static final class Events implements Listener {
		/** The viewer cannot leave the camera (sneaking would) while it runs. */
		@EventHandler(ignoreCancelled = true)
		public void onStopSpectating(PlayerStopSpectatingEntityEvent event) {
			Cinematic cinematic = ACTIVE.get(event.getPlayer().getUniqueId());
			if (cinematic != null && !cinematic.ended) {
				event.setCancelled(true);
			}
		}

		@EventHandler
		public void onQuit(PlayerQuitEvent event) {
			Cinematic cinematic = ACTIVE.get(event.getPlayer().getUniqueId());
			if (cinematic != null) {
				cinematic.end();
			}
		}

		/** Restores a game mode left behind by a restart or crash mid-animation. */
		@EventHandler
		public void onJoin(PlayerJoinEvent event) {
			restoreMode(event.getPlayer());
		}
	}
}
