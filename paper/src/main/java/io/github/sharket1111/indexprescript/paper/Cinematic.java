package io.github.sharket1111.indexprescript.paper;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * A separate camera for skill animations: an invisible display entity the
 * player's view is switched to, moved along a path while it looks at the
 * action, then switched back. Paper has no API for viewing through another
 * entity outside spectator mode, so the vanilla set-camera packet is sent
 * reflectively (Paper runs with Mojang-mapped class names). Bedrock players
 * (Floodgate) keep their normal view.
 */
final class Cinematic {
	private static final String FLOODGATE_PREFIX = "00000000-0000-0000-";
	private static final int ATTACH_DELAY_TICKS = 2;
	private static final Set<Cinematic> ACTIVE = ConcurrentHashMap.newKeySet();
	private static boolean warned;

	private final Player viewer;
	private final ItemDisplay camera;
	private int age;
	private boolean ended;

	private Cinematic(Player viewer, ItemDisplay camera) {
		this.viewer = viewer;
		this.camera = camera;
	}

	/** Starts a camera at {@code at}; returns null for Bedrock players, who keep their own view. */
	static Cinematic start(Player viewer, Location at) {
		if (viewer.getUniqueId().toString().startsWith(FLOODGATE_PREFIX)) {
			return null;
		}
		World world = at.getWorld();
		ItemDisplay camera = world.spawn(at, ItemDisplay.class, display -> {
			display.setPersistent(false);
			display.setTeleportDuration(2);
		});
		Cinematic cinematic = new Cinematic(viewer, camera);
		ACTIVE.add(cinematic);
		return cinematic;
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
		// The client must know the camera entity before it can view through it.
		if (++age == ATTACH_DELAY_TICKS) {
			setCamera(viewer, camera);
		}
	}

	void end() {
		if (ended) {
			return;
		}
		ended = true;
		ACTIVE.remove(this);
		if (viewer.isOnline()) {
			setCamera(viewer, viewer);
		}
		camera.remove();
	}

	static void endAll() {
		for (Cinematic cinematic : Set.copyOf(ACTIVE)) {
			cinematic.end();
		}
	}

	/** Point on a horizontal circle around {@code center}. */
	static Vector orbit(Vector center, double angle, double radius, double height) {
		return center.clone().add(new Vector(Math.cos(angle) * radius, height, Math.sin(angle) * radius));
	}

	private static void setCamera(Player viewer, Entity target) {
		try {
			Object player = viewer.getClass().getMethod("getHandle").invoke(viewer);
			Object entity = target.getClass().getMethod("getHandle").invoke(target);
			Class<?> nmsEntity = Class.forName("net.minecraft.world.entity.Entity");
			Class<?> packetType = Class.forName("net.minecraft.network.protocol.Packet");
			Object packet = Class.forName("net.minecraft.network.protocol.game.ClientboundSetCameraPacket")
					.getConstructor(nmsEntity).newInstance(entity);
			Object connection = player.getClass().getField("connection").get(player);
			connection.getClass().getMethod("send", packetType).invoke(connection, packet);
		} catch (ReflectiveOperationException | RuntimeException e) {
			if (!warned) {
				warned = true;
				IndexPrescriptPlugin.instance().getLogger().log(Level.WARNING, "Could not switch the camera; skill animations use the normal view", e);
			}
		}
	}
}
