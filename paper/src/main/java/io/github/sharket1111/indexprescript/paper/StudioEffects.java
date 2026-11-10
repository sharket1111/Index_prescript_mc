package io.github.sharket1111.indexprescript.paper;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Particle effects made in the mod studio's effect designer, loaded from
 * plugins/IndexPrescript/effects/*.json and played with /ip fx <name> or from
 * an animation. An effect is a list of emitters, each a shape (point, line,
 * circle, arc, sphere, spiral, burst) laid out around the player: right, up
 * and forward follow where they face, so a slash arc turns with them. An
 * emitter can spread its particles over several ticks (a sweep).
 */
final class StudioEffects {
	private static final Gson GSON = new Gson();
	private static final Random RANDOM = new Random();

	record Emitter(int start, String shape, Particle particle, Color color, float size, int count, double radius, double from, double to,
			double length, double height, double turns, double forward, double right, double up, double tilt, double pitch, int duration,
			double speed, double spread) {
	}

	private static final Map<String, List<Emitter>> EFFECTS = new TreeMap<>();
	private static Plugin plugin;

	private StudioEffects() {
	}

	static File folder() {
		return new File(plugin.getDataFolder(), "effects");
	}

	/** Reads every effect file; returns how many loaded. */
	static int load(Plugin owner) {
		plugin = owner;
		EFFECTS.clear();
		File dir = folder();
		if (!dir.exists()) {
			dir.mkdirs();
			try {
				Files.writeString(new File(dir, "example_red_slash.json").toPath(), EXAMPLE, StandardCharsets.UTF_8);
			} catch (IOException e) {
				owner.getLogger().log(Level.WARNING, "Could not write the example effect", e);
			}
		}
		File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
		if (files != null) {
			for (File file : files) {
				try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
					JsonObject root = GSON.fromJson(reader, JsonObject.class);
					String name = root.has("name") ? root.get("name").getAsString() : file.getName().replace(".json", "");
					EFFECTS.put(name.toLowerCase(Locale.ROOT), parse(root.getAsJsonArray("emitters")));
				} catch (IOException | RuntimeException e) {
					owner.getLogger().warning("Effect " + file.getName() + " could not be read: " + e.getMessage());
				}
			}
		}
		return EFFECTS.size();
	}

	static List<String> names() {
		return new ArrayList<>(EFFECTS.keySet());
	}

	static boolean exists(String name) {
		return EFFECTS.containsKey(name.toLowerCase(Locale.ROOT));
	}

	private static List<Emitter> parse(JsonArray array) {
		List<Emitter> list = new ArrayList<>();
		if (array == null) {
			return list;
		}
		for (JsonElement e : array) {
			JsonObject o = e.getAsJsonObject();
			Particle particle = particle(str(o, "particle", "dust"));
			if (particle == null) {
				continue;
			}
			list.add(new Emitter(num(o, "t", 0).intValue(), str(o, "shape", "point"), particle, color(str(o, "color", "#ffffff")),
					num(o, "size", 1).floatValue(), Math.max(1, Math.min(2000, num(o, "count", 20).intValue())), num(o, "radius", 1).doubleValue(),
					num(o, "from", -60).doubleValue(), num(o, "to", 60).doubleValue(), num(o, "length", 3).doubleValue(),
					num(o, "height", 2).doubleValue(), num(o, "turns", 2).doubleValue(), num(o, "forward", 0).doubleValue(),
					num(o, "right", 0).doubleValue(), num(o, "up", 1).doubleValue(), num(o, "tilt", 0).doubleValue(), num(o, "pitch", 0).doubleValue(),
					Math.max(1, Math.min(200, num(o, "duration", 1).intValue())), num(o, "speed", 0).doubleValue(), num(o, "spread", 0).doubleValue()));
		}
		return list;
	}

	private static String str(JsonObject o, String key, String fallback) {
		return o.has(key) ? o.get(key).getAsString() : fallback;
	}

	private static Number num(JsonObject o, String key, double fallback) {
		return o.has(key) ? o.get(key).getAsNumber() : fallback;
	}

	private static Color color(String hex) {
		try {
			return Color.fromRGB(Integer.parseInt(hex.replace("#", ""), 16) & 0xFFFFFF);
		} catch (NumberFormatException e) {
			return Color.WHITE;
		}
	}

	/** A particle that needs no extra data, or dust (which takes a colour). */
	private static Particle particle(String name) {
		try {
			Particle p = Particle.valueOf(name.toUpperCase(Locale.ROOT).replace("MINECRAFT:", ""));
			return p == Particle.DUST || p.getDataType() == Void.class ? p : null;
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/** Plays an effect at {@code at}, turned the way {@code at} faces (its yaw). */
	static boolean play(String name, Location at) {
		List<Emitter> emitters = EFFECTS.get(name.toLowerCase(Locale.ROOT));
		if (emitters == null) {
			return false;
		}
		Location origin = at.clone();
		for (Emitter emitter : emitters) {
			List<Vector> points = points(emitter);
			for (int tick = 0; tick < emitter.duration(); tick++) {
				int from = points.size() * tick / emitter.duration();
				int to = points.size() * (tick + 1) / emitter.duration();
				List<Vector> slice = points.subList(from, to);
				long delay = emitter.start() + tick;
				if (delay <= 0) {
					spawn(origin, emitter, slice);
				} else {
					Bukkit.getScheduler().runTaskLater(plugin, () -> spawn(origin, emitter, slice), delay);
				}
			}
		}
		return true;
	}

	/** The emitter's points in its own frame: x right, y up, z forward (blocks). */
	private static List<Vector> points(Emitter e) {
		List<Vector> out = new ArrayList<>(e.count());
		int n = e.count();
		for (int i = 0; i < n; i++) {
			double s = n == 1 ? 0.5 : (double) i / (n - 1);
			Vector v;
			switch (e.shape()) {
				case "line" -> {
					double pitch = Math.toRadians(e.pitch());
					double yaw = Math.toRadians(e.from());
					v = new Vector(Math.sin(yaw) * Math.cos(pitch), Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch)).multiply(s * e.length());
				}
				case "circle" -> {
					double a = s * Math.PI * 2;
					v = new Vector(Math.sin(a) * e.radius(), 0, Math.cos(a) * e.radius());
				}
				case "arc" -> {
					double a = Math.toRadians(e.from() + (e.to() - e.from()) * s);
					v = new Vector(Math.sin(a) * e.radius(), 0, Math.cos(a) * e.radius());
				}
				case "sphere" -> {
					double y = 1 - 2 * s;
					double r = Math.sqrt(Math.max(0, 1 - y * y));
					double a = i * Math.PI * (3 - Math.sqrt(5));
					v = new Vector(Math.cos(a) * r, y, Math.sin(a) * r).multiply(e.radius());
				}
				case "spiral" -> {
					double a = s * e.turns() * Math.PI * 2;
					v = new Vector(Math.sin(a) * e.radius(), s * e.height(), Math.cos(a) * e.radius());
				}
				case "burst" -> v = new Vector(RANDOM.nextGaussian(), RANDOM.nextGaussian(), RANDOM.nextGaussian()).normalize().multiply(e.radius() * RANDOM.nextDouble());
				default -> v = new Vector();
			}
			// Tilt the shape about the forward axis (a diagonal slash), then lift it by pitch about the right axis.
			if (e.tilt() != 0 && !e.shape().equals("line")) {
				v.rotateAroundZ(Math.toRadians(-e.tilt()));
			}
			if (e.pitch() != 0 && !e.shape().equals("line")) {
				v.rotateAroundX(Math.toRadians(-e.pitch()));
			}
			out.add(v.add(new Vector(e.right(), e.up(), e.forward())));
		}
		return out;
	}

	private static void spawn(Location origin, Emitter e, List<Vector> points) {
		World world = origin.getWorld();
		if (world == null) {
			return;
		}
		double yaw = Math.toRadians(origin.getYaw());
		// Facing yaw: forward = (-sin, 0, cos), right = (-cos, 0, -sin).
		Vector forward = new Vector(-Math.sin(yaw), 0, Math.cos(yaw));
		Vector right = new Vector(-Math.cos(yaw), 0, -Math.sin(yaw));
		Particle.DustOptions dust = e.particle() == Particle.DUST ? new Particle.DustOptions(e.color(), Math.max(0.1F, e.size())) : null;
		for (Vector local : points) {
			Location at = origin.clone().add(right.clone().multiply(local.getX())).add(0, local.getY(), 0).add(forward.clone().multiply(local.getZ()));
			if (dust != null) {
				world.spawnParticle(Particle.DUST, at, 1, e.spread(), e.spread(), e.spread(), 0, dust, true);
			} else if (e.shape().equals("burst") && e.speed() > 0) {
				Vector out = at.toVector().subtract(origin.toVector().add(new Vector(0, e.up(), 0)));
				if (out.lengthSquared() < 1e-6) {
					out = new Vector(0, 1, 0);
				}
				out.normalize();
				world.spawnParticle(e.particle(), at, 0, out.getX(), out.getY(), out.getZ(), e.speed(), null, true);
			} else {
				world.spawnParticle(e.particle(), at, 1, e.spread(), e.spread(), e.spread(), e.speed(), null, true);
			}
		}
	}

	private static final String EXAMPLE = """
			{
			  "name": "example_red_slash",
			  "emitters": [
			    {"t": 0, "shape": "arc", "particle": "dust", "color": "#e03c28", "size": 1.3, "count": 48, "radius": 2.4,
			     "from": -70, "to": 70, "up": 1.2, "forward": 0.6, "tilt": 25, "duration": 4},
			    {"t": 3, "shape": "burst", "particle": "crit", "count": 24, "radius": 0.6, "forward": 2.2, "up": 1.2, "speed": 0.4}
			  ]
			}
			""";
}
