package io.github.sharket1111.indexprescript.paper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import com.destroystokyo.paper.ParticleBuilder;

/**
 * Low-spec mode: the plugin's particles come to the player at a quarter (single puffs one time in three).
 * On by default for Bedrock players (Geyser), off for Java; each player can switch it (/menu 저사양).
 * Every world particle of the plugin goes through {@link #particle}, which mirrors World#spawnParticle.
 */
final class LowSpec {
	private static final NamespacedKey KEY = new NamespacedKey(Items.NAMESPACE, "low_spec");
	private static final String FLOODGATE_PREFIX = "00000000-0000-0000-";

	private LowSpec() {
	}

	static boolean bedrock(Player p) {
		return p.getUniqueId().toString().startsWith(FLOODGATE_PREFIX);
	}

	static boolean on(Player p) {
		Byte b = p.getPersistentDataContainer().get(KEY, PersistentDataType.BYTE);
		return b == null ? bedrock(p) : b != 0;
	}

	static void set(Player p, boolean on) {
		p.getPersistentDataContainer().set(KEY, PersistentDataType.BYTE, (byte) (on ? 1 : 0));
	}

	// --- World#spawnParticle, played to each player in reach ----------------------------------------------

	static void particle(World w, Particle p, Location l, int count) {
		send(w, p, l, count, 0, 0, 0, 1, null, false);
	}

	static void particle(World w, Particle p, double x, double y, double z, int count) {
		send(w, p, new Location(w, x, y, z), count, 0, 0, 0, 1, null, false);
	}

	static <T> void particle(World w, Particle p, Location l, int count, T data) {
		send(w, p, l, count, 0, 0, 0, 1, data, false);
	}

	static <T> void particle(World w, Particle p, double x, double y, double z, int count, T data) {
		send(w, p, new Location(w, x, y, z), count, 0, 0, 0, 1, data, false);
	}

	static void particle(World w, Particle p, Location l, int count, double ox, double oy, double oz) {
		send(w, p, l, count, ox, oy, oz, 1, null, false);
	}

	static void particle(World w, Particle p, double x, double y, double z, int count, double ox, double oy, double oz) {
		send(w, p, new Location(w, x, y, z), count, ox, oy, oz, 1, null, false);
	}

	static <T> void particle(World w, Particle p, Location l, int count, double ox, double oy, double oz, T data) {
		send(w, p, l, count, ox, oy, oz, 1, data, false);
	}

	static <T> void particle(World w, Particle p, double x, double y, double z, int count, double ox, double oy, double oz, T data) {
		send(w, p, new Location(w, x, y, z), count, ox, oy, oz, 1, data, false);
	}

	static void particle(World w, Particle p, Location l, int count, double ox, double oy, double oz, double extra) {
		send(w, p, l, count, ox, oy, oz, extra, null, false);
	}

	static void particle(World w, Particle p, double x, double y, double z, int count, double ox, double oy, double oz, double extra) {
		send(w, p, new Location(w, x, y, z), count, ox, oy, oz, extra, null, false);
	}

	static <T> void particle(World w, Particle p, Location l, int count, double ox, double oy, double oz, double extra, T data) {
		send(w, p, l, count, ox, oy, oz, extra, data, false);
	}

	static <T> void particle(World w, Particle p, double x, double y, double z, int count, double ox, double oy, double oz, double extra,
			T data) {
		send(w, p, new Location(w, x, y, z), count, ox, oy, oz, extra, data, false);
	}

	static <T> void particle(World w, Particle p, Location l, int count, double ox, double oy, double oz, double extra, T data, boolean force) {
		send(w, p, l, count, ox, oy, oz, extra, data, force);
	}

	static <T> void particle(World w, Particle p, double x, double y, double z, int count, double ox, double oy, double oz, double extra,
			T data, boolean force) {
		send(w, p, new Location(w, x, y, z), count, ox, oy, oz, extra, data, force);
	}

	private static void send(World w, Particle p, Location l, int count, double ox, double oy, double oz, double extra, Object data,
			boolean force) {
		if (w == null || l == null) {
			return;
		}
		double reach = force ? 512 : 32;
		List<Player> full = new ArrayList<>();
		List<Player> low = new ArrayList<>();
		for (Player viewer : w.getPlayers()) {
			if (viewer.getLocation().distanceSquared(l) <= reach * reach) {
				(on(viewer) ? low : full).add(viewer);
			}
		}
		if (!full.isEmpty()) {
			new ParticleBuilder(p).location(l).count(count).offset(ox, oy, oz).extra(extra).data(data).receivers(full).spawn();
		}
		if (!low.isEmpty()) {
			int fewer = count <= 1 ? (ThreadLocalRandom.current().nextInt(3) == 0 ? count : -1) : Math.max(1, count / 4);
			if (fewer >= 0) {
				new ParticleBuilder(p).location(l).count(fewer).offset(ox, oy, oz).extra(extra).data(data).receivers(low).spawn();
			}
		}
	}
}
