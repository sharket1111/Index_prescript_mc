package io.github.sharket1111.indexprescript.paper;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * The Mirror Dungeon enemies' 3D models (tools/md_models.py): a tree of bones,
 * each an item display showing item model index_prescript:md/&lt;model&gt; with
 * custom model data [bone], posed from keyframe clips (idle, walk,
 * attack_slash / attack_pierce / attack_blunt, guard, stagger, roar, death).
 * The pose eases toward the clip's every other tick, so clips blend into each
 * other; the client smooths between. Frame: x to the model's right, y up, z
 * forward, in pixels (16 to a block).
 */
final class MdModels {
	record Bone(String name, int parent, float[] at, float[] rest, boolean draw) {
	}

	record Clip(int length, boolean loop, Map<String, float[][]> tracks) {
	}

	record Def(String id, float scale, float height, List<Bone> bones, Map<String, Clip> clips, Map<String, List<String>> parts, boolean bright) {
		int index(String bone) {
			for (int i = 0; i < bones.size(); i++) {
				if (bones.get(i).name().equals(bone)) {
					return i;
				}
			}
			return -1;
		}
	}

	private static final Map<String, Def> MODELS = new HashMap<>();
	private static final Map<String, String> ENEMIES = new HashMap<>();
	/** Pixels to blocks, a little under 1:1 like a player (32 pixels drawn 1.875 blocks tall). */
	private static final float UNIT = 0.9375F / 16F;
	private static final int STEP = 2;

	private MdModels() {
	}

	static void load(IndexPrescriptPlugin plugin) {
		MODELS.clear();
		ENEMIES.clear();
		try (InputStream in = plugin.getResource("md/models.json")) {
			if (in == null) {
				return;
			}
			JsonObject root = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
			Map<String, Map<String, Clip>> sets = new HashMap<>();
			for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("animsets").entrySet()) {
				sets.put(e.getKey(), clips(e.getValue().getAsJsonObject()));
			}
			for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("models").entrySet()) {
				JsonObject o = e.getValue().getAsJsonObject();
				List<Bone> bones = new ArrayList<>();
				for (JsonElement be : o.getAsJsonArray("bones")) {
					JsonObject b = be.getAsJsonObject();
					bones.add(new Bone(b.get("name").getAsString(), b.get("parent").getAsInt(), floats(b.getAsJsonArray("at")), floats(b.getAsJsonArray("rest")),
							b.get("draw").getAsBoolean()));
				}
				Map<String, Clip> clips = new LinkedHashMap<>(sets.getOrDefault(o.get("anims").getAsString(), Map.of()));
				if (o.has("clips")) {
					clips.putAll(clips(o.getAsJsonObject("clips")));
				}
				Map<String, List<String>> parts = new LinkedHashMap<>();
				for (Map.Entry<String, JsonElement> p : o.getAsJsonObject("parts").entrySet()) {
					List<String> names = new ArrayList<>();
					p.getValue().getAsJsonArray().forEach(x -> names.add(x.getAsString()));
					parts.put(p.getKey(), names);
				}
				MODELS.put(e.getKey(), new Def(e.getKey(), o.get("scale").getAsFloat(), o.get("height").getAsFloat(), bones, clips, parts,
						o.has("bright") && o.get("bright").getAsBoolean()));
			}
			root.getAsJsonObject("enemies").entrySet().forEach(e -> ENEMIES.put(e.getKey(), e.getValue().getAsString()));
			plugin.getLogger().info("Mirror Dungeon: " + MODELS.size() + " enemy models");
		} catch (java.io.IOException | RuntimeException e) {
			plugin.getLogger().log(Level.SEVERE, "Could not read md/models.json", e);
		}
	}

	private static float[] floats(JsonArray a) {
		float[] out = new float[a.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = a.get(i).getAsFloat();
		}
		return out;
	}

	private static Map<String, Clip> clips(JsonObject o) {
		Map<String, Clip> out = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> e : o.entrySet()) {
			JsonObject c = e.getValue().getAsJsonObject();
			Map<String, float[][]> tracks = new HashMap<>();
			for (Map.Entry<String, JsonElement> t : c.getAsJsonObject("tracks").entrySet()) {
				JsonArray keys = t.getValue().getAsJsonArray();
				float[][] track = new float[keys.size()][];
				for (int i = 0; i < track.length; i++) {
					track[i] = floats(keys.get(i).getAsJsonArray());
				}
				tracks.put(t.getKey(), track);
			}
			out.put(e.getKey(), new Clip(c.get("len").getAsInt(), c.get("loop").getAsBoolean(), tracks));
		}
		return out;
	}

	/** The model of an enemy by name, or null (it then wears an outfit on the player rig). */
	static Def of(String enemy) {
		String id = ENEMIES.get(enemy);
		return id == null ? null : MODELS.get(id);
	}

	// --- an enemy's model in the world ----------------------------------------------------------------

	static final class Inst {
		final Def def;
		final float scale;
		final ItemDisplay[] displays;
		final boolean[] hidden;
		/** The eased pose: per bone rx, ry, rz, px, py, pz, scale. */
		final float[][] pose;
		final Matrix4f[] world;
		String once;
		long onceAt;
		double walk;
		double walkAmount;
		Location last;
		long dyingAt = -1;
		long hurtAt = -100;
		boolean flashing;

		Inst(Def def, float scale, Location at) {
			this.def = def;
			this.scale = scale;
			int n = def.bones().size();
			displays = new ItemDisplay[n];
			hidden = new boolean[n];
			pose = new float[n][];
			world = new Matrix4f[n];
			for (int i = 0; i < n; i++) {
				pose[i] = new float[] {0, 0, 0, 0, 0, 0, 1};
				world[i] = new Matrix4f();
				Bone bone = def.bones().get(i);
				if (!bone.draw()) {
					continue;
				}
				ItemStack stack = new ItemStack(Material.STICK);
				stack.editMeta(meta -> {
					meta.setItemModel(new NamespacedKey(Items.NAMESPACE, "md/" + def.id()));
					CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
					cmd.setStrings(List.of(bone.name()));
					meta.setCustomModelDataComponent(cmd);
				});
				displays[i] = at.getWorld().spawn(at, ItemDisplay.class, d -> {
					d.setItemStack(stack);
					d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
					d.setPersistent(false);
					d.setTeleportDuration(STEP);
					d.setViewRange(1.2F);
					if (def.bright()) {
						d.setBrightness(new Display.Brightness(15, 15));
					}
				});
			}
		}

		/** Plays a one-shot clip (an attack, a roar) from now, if the model has it. */
		boolean play(String clip) {
			if (!def.clips().containsKey(clip)) {
				return false;
			}
			once = clip;
			onceAt = CaduceusListener.now();
			return true;
		}

		/** Shows or hides a part's bones (and what hangs from them). */
		void show(String part, boolean shown) {
			for (String name : def.parts().getOrDefault(part, List.of())) {
				int i = def.index(name);
				if (i >= 0) {
					hidden[i] = !shown;
				}
			}
		}

		private boolean isHidden(int i) {
			for (int b = i; b >= 0; b = def.bones().get(b).parent()) {
				if (hidden[b]) {
					return true;
				}
			}
			return false;
		}

		/** Where a part's first bone is now, as an offset in blocks in the model's frame (right, up, forward); null if the model has no such part. */
		Vector3f partAt(String part) {
			List<String> names = def.parts().get(part);
			if (names == null || names.isEmpty()) {
				return null;
			}
			int i = def.index(names.get(0));
			if (i < 0) {
				return null;
			}
			Vector3f p = world[i].getTranslation(new Vector3f());
			return p.mul(scale * UNIT);
		}

		/**
		 * One tick: picks the clip for the state, eases the pose toward it and moves
		 * the displays (every other tick). {@code at}: the feet, facing the yaw.
		 */
		void tick(Location at, long now, boolean staggered, boolean guarding) {
			if (last != null && last.getWorld() == at.getWorld() && dyingAt < 0) {
				double moved = Math.hypot(at.getX() - last.getX(), at.getZ() - last.getZ());
				walkAmount = walkAmount * 0.8 + Math.min(1, moved * 8) * 0.2;
				walk += moved;
			}
			last = at.clone();
			if (now % STEP != 0) {
				return;
			}
			Clip clip;
			float t;
			if (dyingAt >= 0 && def.clips().containsKey("death")) {
				clip = def.clips().get("death");
				t = now - dyingAt;
			} else if (once != null && now - onceAt <= def.clips().get(once).length() && !staggered) {
				clip = def.clips().get(once);
				t = now - onceAt;
			} else if (staggered && def.clips().containsKey("stagger")) {
				clip = def.clips().get("stagger");
				t = now;
			} else if (guarding && def.clips().containsKey("guard")) {
				clip = def.clips().get("guard");
				t = now;
			} else if (walkAmount > 0.12 && def.clips().containsKey("walk")) {
				clip = def.clips().get("walk");
				// A stride (the whole clip) every 1.6 blocks at the model's size.
				t = (float) (walk / (1.6 * scale) * clip.length());
			} else {
				clip = def.clips().get("idle");
				t = now;
			}
			if (clip != null) {
				t = clip.loop() ? t % clip.length() : Math.min(t, clip.length());
			}
			boolean flash = now - hurtAt < 5;
			float k = scale * UNIT;
			for (int i = 0; i < def.bones().size(); i++) {
				Bone bone = def.bones().get(i);
				float[] target = clip == null ? null : sample(clip.tracks().get(bone.name()), t);
				float[] p = pose[i];
				for (int c = 0; c < 7; c++) {
					float goal = target == null ? (c == 6 ? 1 : 0) : target[c];
					p[c] += (goal - p[c]) * 0.7F;
				}
				Matrix4f m = world[i];
				if (bone.parent() >= 0) {
					m.set(world[bone.parent()]);
				} else {
					m.identity();
				}
				m.translate(bone.at()[0] + p[3], bone.at()[1] + p[4], bone.at()[2] + p[5])
						.rotateXYZ((float) Math.toRadians(bone.rest()[0] + p[0]), (float) Math.toRadians(bone.rest()[1] + p[1]),
								(float) Math.toRadians(bone.rest()[2] + p[2]))
						.scale(p[6]);
				ItemDisplay d = displays[i];
				if (d == null || !d.isValid()) {
					continue;
				}
				Matrix4f shown = new Matrix4f().scale(k).mul(m).scale(16F);
				if (isHidden(i)) {
					shown.scale(0);
				}
				if (d.getLocation().distanceSquared(at) > 0.0001 || Math.abs(d.getLocation().getYaw() - at.getYaw()) > 0.5) {
					d.teleport(at);
				}
				d.setInterpolationDelay(0);
				d.setInterpolationDuration(STEP);
				// The model's x points to its right; the entity's own +x is its left.
				d.setTransformationMatrix(new Matrix4f().scale(-1, 1, 1).mul(shown).scale(-1, 1, 1));
				if (flash != flashing) {
					d.setGlowing(flash);
					d.setGlowColorOverride(flash ? Color.fromRGB(0xFF4040) : null);
				}
			}
			flashing = flash;
		}

		/** Ticks the death clip runs before the model is taken away. */
		int deathTicks() {
			Clip death = def.clips().get("death");
			return death == null ? 0 : death.length() + 16;
		}

		void remove() {
			for (ItemDisplay d : displays) {
				if (d != null) {
					d.remove();
				}
			}
		}
	}

	/** A track's value at {@code t}: rx, ry, rz, px, py, pz, scale, eased between keys ([t, ...]). */
	private static float[] sample(float[][] track, float t) {
		if (track == null || track.length == 0) {
			return null;
		}
		float[] a = track[0];
		float[] b = a;
		for (float[] k : track) {
			if (k[0] <= t) {
				a = k;
			}
			b = k;
			if (k[0] >= t) {
				break;
			}
		}
		float f = b[0] == a[0] ? 0 : (t - a[0]) / (b[0] - a[0]);
		f = Math.max(0, Math.min(1, f));
		f = f * f * (3 - 2 * f);
		float[] out = new float[7];
		for (int i = 0; i < 7; i++) {
			out[i] = a[i + 1] + (b[i + 1] - a[i + 1]) * f;
		}
		return out;
	}
}
