package io.github.sharket1111.indexprescript.paper;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.SoundCategory;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Keyframe animations made in the mod studio's timeline, loaded from
 * plugins/IndexPrescript/animations/*.json and played with /ip anim <name>
 * (or {@link #play} from a skill). Each part is a display entity (an item,
 * such as a weapon model, or a block) moved through its keyframes in the
 * player's own frame: x to their right, y up, z forward, so the animation
 * turns with them. Between keyframes the client smooths the motion; easing
 * is followed by sending a new pose every two ticks. Events play sounds and
 * particle effects ({@link StudioEffects}) on their tick.
 */
final class Animations {
	private static final Gson GSON = new Gson();
	private static final int STEP = 2;

	record Key(int t, float[] pos, float[] rot, float[] scale, String ease) {
	}

	record Part(String id, String kind, String item, String model, List<String> data, String block, String transform, boolean glow,
			int brightness, List<Key> keys) {
	}

	record Event(int t, String type, String sound, float volume, float pitch, String effect) {
	}

	record Anim(String name, int length, boolean follow, List<Part> parts, List<Event> events) {
	}

	private static final Map<String, Anim> ANIMS = new TreeMap<>();
	private static final List<Playback> PLAYING = new ArrayList<>();
	private static Plugin plugin;

	private Animations() {
	}

	static File folder() {
		return new File(plugin.getDataFolder(), "animations");
	}

	/** Reads every animation file; returns how many loaded. */
	static int load(Plugin owner) {
		plugin = owner;
		ANIMS.clear();
		File dir = folder();
		if (!dir.exists()) {
			dir.mkdirs();
			try {
				Files.writeString(new File(dir, "example_spin.json").toPath(), EXAMPLE, StandardCharsets.UTF_8);
			} catch (IOException e) {
				owner.getLogger().log(Level.WARNING, "Could not write the example animation", e);
			}
		}
		File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
		if (files != null) {
			for (File file : files) {
				try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
					Anim anim = parse(GSON.fromJson(reader, JsonObject.class), file.getName().replace(".json", ""));
					ANIMS.put(anim.name().toLowerCase(Locale.ROOT), anim);
				} catch (IOException | RuntimeException e) {
					owner.getLogger().warning("Animation " + file.getName() + " could not be read: " + e.getMessage());
				}
			}
		}
		return ANIMS.size();
	}

	static List<String> names() {
		return new ArrayList<>(ANIMS.keySet());
	}

	private static float[] vec(JsonObject o, String key, float a, float b, float c) {
		if (!o.has(key)) {
			return new float[] {a, b, c};
		}
		JsonArray arr = o.getAsJsonArray(key);
		return new float[] {arr.get(0).getAsFloat(), arr.get(1).getAsFloat(), arr.get(2).getAsFloat()};
	}

	private static String str(JsonObject o, String key, String fallback) {
		return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : fallback;
	}

	private static Anim parse(JsonObject root, String fallbackName) {
		List<Part> parts = new ArrayList<>();
		for (JsonElement e : root.getAsJsonArray("parts")) {
			JsonObject o = e.getAsJsonObject();
			List<Key> keys = new ArrayList<>();
			for (JsonElement k : o.getAsJsonArray("keys")) {
				JsonObject ko = k.getAsJsonObject();
				keys.add(new Key(ko.get("t").getAsInt(), vec(ko, "pos", 0, 0, 0), vec(ko, "rot", 0, 0, 0), vec(ko, "scale", 1, 1, 1), str(ko, "ease", "linear")));
			}
			keys.sort((a, b) -> Integer.compare(a.t(), b.t()));
			if (keys.isEmpty()) {
				continue;
			}
			List<String> data = new ArrayList<>();
			if (o.has("data")) {
				o.getAsJsonArray("data").forEach(d -> data.add(d.getAsString()));
			}
			parts.add(new Part(str(o, "id", "part"), str(o, "kind", "item"), str(o, "item", "minecraft:iron_sword"), str(o, "model", null), data,
					str(o, "block", "minecraft:glowstone"), str(o, "transform", "fixed"), o.has("glow") && o.get("glow").getAsBoolean(),
					o.has("brightness") ? o.get("brightness").getAsInt() : -1, keys));
		}
		List<Event> events = new ArrayList<>();
		if (root.has("events")) {
			for (JsonElement e : root.getAsJsonArray("events")) {
				JsonObject o = e.getAsJsonObject();
				events.add(new Event(o.get("t").getAsInt(), str(o, "type", "sound"), str(o, "sound", "entity.player.attack.sweep"),
						o.has("volume") ? o.get("volume").getAsFloat() : 1, o.has("pitch") ? o.get("pitch").getAsFloat() : 1, str(o, "effect", "")));
			}
		}
		int length = root.has("length") ? root.get("length").getAsInt() : 40;
		return new Anim(str(root, "name", fallbackName), Math.max(1, Math.min(2400, length)), root.has("follow") && root.get("follow").getAsBoolean(),
				parts, events);
	}

	// --- playing ----------------------------------------------------------------------------

	private static final class Playback {
		final Anim anim;
		final Player player;
		Location origin;
		final Display[] displays;
		int tick;

		Playback(Anim anim, Player player) {
			this.anim = anim;
			this.player = player;
			this.origin = flat(player.getLocation());
			this.displays = new Display[anim.parts().size()];
		}
	}

	private static Location flat(Location at) {
		Location out = at.clone();
		out.setPitch(0);
		return out;
	}

	static boolean play(Player player, String name) {
		Anim anim = ANIMS.get(name.toLowerCase(Locale.ROOT));
		if (anim == null) {
			return false;
		}
		PLAYING.add(new Playback(anim, player));
		return true;
	}

	/** Stops every animation of {@code player} (all, when null). */
	static int stop(Player player) {
		int n = 0;
		for (Iterator<Playback> it = PLAYING.iterator(); it.hasNext();) {
			Playback p = it.next();
			if (player == null || p.player.equals(player)) {
				remove(p);
				it.remove();
				n++;
			}
		}
		return n;
	}

	private static void remove(Playback p) {
		for (Display d : p.displays) {
			if (d != null && d.isValid()) {
				d.remove();
			}
		}
	}

	static void tick() {
		for (Iterator<Playback> it = PLAYING.iterator(); it.hasNext();) {
			Playback p = it.next();
			if (!p.player.isOnline() || p.tick > p.anim.length()) {
				remove(p);
				it.remove();
				continue;
			}
			if (p.anim.follow()) {
				p.origin = flat(p.player.getLocation());
			}
			for (int i = 0; i < p.anim.parts().size(); i++) {
				Part part = p.anim.parts().get(i);
				int first = part.keys().get(0).t();
				int last = part.keys().get(part.keys().size() - 1).t();
				if (p.tick == first && p.displays[i] == null) {
					p.displays[i] = spawn(p, part);
				}
				Display d = p.displays[i];
				if (d == null || !d.isValid()) {
					continue;
				}
				if (p.anim.follow()) {
					d.teleport(p.origin);
				}
				if (p.tick > first && (p.tick - first) % STEP == 0 && p.tick <= last + STEP) {
					d.setInterpolationDelay(0);
					d.setInterpolationDuration(STEP);
					d.setTransformation(pose(part, p.tick + STEP));
				}
			}
			for (Event e : p.anim.events()) {
				if (e.t() == p.tick) {
					fire(p, e);
				}
			}
			p.tick++;
		}
	}

	private static Display spawn(Playback p, Part part) {
		Location at = p.origin.clone();
		Display display;
		if (part.kind().equals("block")) {
			Material material = Material.matchMaterial(part.block());
			BlockDisplay block = at.getWorld().spawn(at, BlockDisplay.class, b -> b.setBlock((material != null && material.isBlock() ? material
					: Material.GLOWSTONE).createBlockData()));
			display = block;
		} else {
			Material material = Material.matchMaterial(part.item());
			ItemStack stack = new ItemStack(material != null && material.isItem() ? material : Material.IRON_SWORD);
			stack.editMeta(meta -> {
				if (part.model() != null && !part.model().isBlank()) {
					NamespacedKey key = NamespacedKey.fromString(part.model());
					if (key != null) {
						meta.setItemModel(key);
					}
				}
				if (!part.data().isEmpty()) {
					var cmd = meta.getCustomModelDataComponent();
					cmd.setStrings(part.data());
					meta.setCustomModelDataComponent(cmd);
				}
			});
			ItemDisplay item = at.getWorld().spawn(at, ItemDisplay.class, d -> {
				d.setItemStack(stack);
				d.setItemDisplayTransform(transform(part.transform()));
			});
			display = item;
		}
		display.setPersistent(false);
		display.setRotation(at.getYaw(), 0);
		display.setTeleportDuration(1);
		display.setViewRange(2.0F);
		if (part.glow()) {
			display.setGlowing(true);
		}
		if (part.brightness() >= 0) {
			display.setBrightness(new Display.Brightness(Math.min(15, part.brightness()), Math.min(15, part.brightness())));
		}
		display.setInterpolationDuration(0);
		display.setTransformation(pose(part, part.keys().get(0).t()));
		return display;
	}

	private static ItemDisplay.ItemDisplayTransform transform(String name) {
		try {
			return ItemDisplay.ItemDisplayTransform.valueOf(name.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return ItemDisplay.ItemDisplayTransform.FIXED;
		}
	}

	/** The part's pose at tick {@code t}, eased between its keyframes. */
	private static Transformation pose(Part part, int t) {
		List<Key> keys = part.keys();
		Key a = keys.get(0);
		Key b = a;
		for (Key k : keys) {
			if (k.t() <= t) {
				a = k;
			}
			if (k.t() >= t) {
				b = k;
				break;
			}
			b = k;
		}
		float f = b.t() == a.t() ? 0 : (float) (t - a.t()) / (b.t() - a.t());
		f = ease(b.ease(), Math.max(0, Math.min(1, f)));
		float[] pos = lerp(a.pos(), b.pos(), f);
		float[] rot = lerp(a.rot(), b.rot(), f);
		float[] scale = lerp(a.scale(), b.scale(), f);
		// The studio's x points to the player's right; the entity's own +x is their left.
		Quaternionf q = new Quaternionf().rotationXYZ((float) Math.toRadians(rot[0]), (float) Math.toRadians(-rot[1]), (float) Math.toRadians(-rot[2]));
		return new Transformation(new Vector3f(-pos[0], pos[1], pos[2]), q, new Vector3f(scale[0], scale[1], scale[2]), new Quaternionf());
	}

	private static float[] lerp(float[] a, float[] b, float f) {
		return new float[] {a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f};
	}

	static float ease(String kind, float f) {
		return switch (kind) {
			case "in" -> f * f * f;
			case "out" -> 1 - (1 - f) * (1 - f) * (1 - f);
			case "inout" -> f < 0.5F ? 4 * f * f * f : 1 - (float) Math.pow(-2 * f + 2, 3) / 2;
			case "back" -> {
				float c = 1.70158F;
				yield 1 + (c + 1) * (float) Math.pow(f - 1, 3) + c * (float) Math.pow(f - 1, 2);
			}
			case "step" -> f < 1 ? 0 : 1;
			default -> f;
		};
	}

	private static void fire(Playback p, Event e) {
		Location at = p.origin.clone();
		switch (e.type()) {
			case "sound" -> at.getWorld().playSound(at, e.sound().contains(":") ? e.sound() : "minecraft:" + e.sound(), SoundCategory.PLAYERS,
					e.volume(), e.pitch());
			case "effect" -> StudioEffects.play(e.effect(), at);
			default -> {
			}
		}
	}

	private static final String EXAMPLE = """
			{
			  "name": "example_spin",
			  "length": 40,
			  "follow": false,
			  "parts": [
			    {"id": "blade", "kind": "item", "item": "minecraft:diamond_sword", "transform": "fixed",
			     "keys": [
			       {"t": 0, "pos": [0.8, 1.2, 1.0], "rot": [0, 0, 0], "scale": [1, 1, 1]},
			       {"t": 20, "pos": [-0.8, 1.6, 1.4], "rot": [0, 180, 90], "scale": [1.5, 1.5, 1.5], "ease": "out"},
			       {"t": 40, "pos": [0, 1.2, 2.0], "rot": [0, 360, 0], "scale": [0.2, 0.2, 0.2], "ease": "in"}
			     ]}
			  ],
			  "events": [
			    {"t": 0, "type": "sound", "sound": "entity.player.attack.sweep"},
			    {"t": 20, "type": "effect", "effect": "example_red_slash"}
			  ]
			}
			""";
}
