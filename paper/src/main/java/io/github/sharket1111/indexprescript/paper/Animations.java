package io.github.sharket1111.indexprescript.paper;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.SoundCategory;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.Plugin;
import org.joml.Matrix4f;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;

import io.papermc.paper.entity.TeleportFlag;

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
 * turns with them. A "rig" part is a whole player body (head, body, arms,
 * legs) whose bones have keyframes of their own; other parts can hang from a
 * bone ("parent": "actor.right_arm") to hold a weapon. A rig can stand in for
 * the player (hide_player) and carry them along (drive_player). Between
 * keyframes the client smooths the motion; easing is followed by sending a new
 * pose every two ticks. Events play sounds and particle effects
 * ({@link StudioEffects}) on their tick.
 */
final class Animations {
	private static final Gson GSON = new Gson();
	private static final int STEP = 2;
	/** A player model is 32 pixels tall but drawn 1.875 blocks tall. */
	private static final float RIG_SCALE = 0.9375F;
	static final List<String> BONES = List.of("head", "body", "right_arm", "left_arm", "right_leg", "left_leg");
	/** Joints in pixels, in the player's frame (x right, y up, z forward). */
	private static final Map<String, float[]> PIVOTS = Map.of("head", new float[] {0, 24, 0}, "body", new float[] {0, 24, 0},
			"right_arm", new float[] {5, 22, 0}, "left_arm", new float[] {-5, 22, 0}, "right_leg", new float[] {2, 12, 0}, "left_leg",
			new float[] {-2, 12, 0});

	record Key(int t, float[] pos, float[] rot, float[] scale, String ease) {
	}

	record Part(String id, String kind, String item, String model, List<String> data, String block, String transform, boolean glow,
			int brightness, List<Key> keys, String parent, String skin, String outfit, boolean hidePlayer, boolean drivePlayer,
			Map<String, List<Key>> bones) {
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
		}
		writeExample(dir, "example_spin.json", EXAMPLE);
		writeExample(dir, "example_rig_slash.json", EXAMPLE_RIG);
		syncBundled(dir);
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

	private static void writeExample(File dir, String name, String text) {
		File file = new File(dir, name);
		if (file.exists()) {
			return;
		}
		try {
			Files.writeString(file.toPath(), text, StandardCharsets.UTF_8);
		} catch (IOException e) {
			plugin.getLogger().log(Level.WARNING, "Could not write the example animation " + name, e);
		}
	}

	/**
	 * Copies the skill motions bundled with the plugin (animations/index.txt) into
	 * the folder: new ones, and newer versions of files still marked "auto" (a file
	 * saved from the studio drops the mark and is kept as it is).
	 */
	private static void syncBundled(File dir) {
		InputStream index = plugin.getResource("animations/index.txt");
		if (index == null) {
			return;
		}
		try (index) {
			for (String name : new String(index.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
				name = name.trim();
				if (name.isEmpty()) {
					continue;
				}
				InputStream in = plugin.getResource("animations/" + name + ".json");
				if (in == null) {
					continue;
				}
				byte[] bundled;
				try (in) {
					bundled = in.readAllBytes();
				}
				File file = new File(dir, name + ".json");
				if (file.exists()) {
					JsonObject mine;
					try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
						mine = GSON.fromJson(reader, JsonObject.class);
					} catch (RuntimeException e) {
						continue;
					}
					JsonObject theirs = GSON.fromJson(new InputStreamReader(new java.io.ByteArrayInputStream(bundled), StandardCharsets.UTF_8), JsonObject.class);
					boolean auto = mine != null && mine.has("auto") && mine.get("auto").getAsBoolean();
					int have = mine != null && mine.has("version") ? mine.get("version").getAsInt() : 0;
					int now = theirs.has("version") ? theirs.get("version").getAsInt() : 0;
					if (!auto || have >= now) {
						continue;
					}
				}
				Files.write(file.toPath(), bundled);
			}
		} catch (IOException | RuntimeException e) {
			plugin.getLogger().log(Level.WARNING, "Could not copy the bundled skill motions", e);
		}
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

	private static boolean bool(JsonObject o, String key, boolean fallback) {
		return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsBoolean() : fallback;
	}

	private static List<Key> keys(JsonArray arr) {
		List<Key> keys = new ArrayList<>();
		if (arr != null) {
			for (JsonElement k : arr) {
				JsonObject ko = k.getAsJsonObject();
				keys.add(new Key(ko.get("t").getAsInt(), vec(ko, "pos", 0, 0, 0), vec(ko, "rot", 0, 0, 0), vec(ko, "scale", 1, 1, 1), str(ko, "ease", "linear")));
			}
		}
		keys.sort((a, b) -> Integer.compare(a.t(), b.t()));
		return keys;
	}

	private static Anim parse(JsonObject root, String fallbackName) {
		List<Part> parts = new ArrayList<>();
		for (JsonElement e : root.getAsJsonArray("parts")) {
			JsonObject o = e.getAsJsonObject();
			String kind = str(o, "kind", "item");
			List<Key> keys = keys(o.getAsJsonArray("keys"));
			if (keys.isEmpty()) {
				if (!kind.equals("rig")) {
					continue;
				}
				keys.add(new Key(0, new float[3], new float[3], new float[] {1, 1, 1}, "linear"));
			}
			List<String> data = new ArrayList<>();
			if (o.has("data")) {
				o.getAsJsonArray("data").forEach(d -> data.add(d.getAsString()));
			}
			Map<String, List<Key>> bones = new HashMap<>();
			if (o.has("bones")) {
				JsonObject b = o.getAsJsonObject("bones");
				for (String bone : BONES) {
					if (b.has(bone)) {
						bones.put(bone, keys(b.getAsJsonArray(bone)));
					}
				}
			}
			parts.add(new Part(str(o, "id", "part"), kind, str(o, "item", "minecraft:iron_sword"), str(o, "model", null), data,
					str(o, "block", "minecraft:glowstone"), str(o, "transform", "fixed"), bool(o, "glow", false),
					o.has("brightness") ? o.get("brightness").getAsInt() : -1, keys, str(o, "parent", ""), str(o, "skin", "player"),
					str(o, "outfit", ""), bool(o, "hide_player", false), bool(o, "drive_player", false), bones));
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
		boolean drives = parts.stream().anyMatch(Part::drivePlayer);
		return new Anim(str(root, "name", fallbackName), Math.max(1, Math.min(2400, length)), !drives && bool(root, "follow", false), parts, events);
	}

	// --- playing ----------------------------------------------------------------------------

	private static final class Playback {
		final Anim anim;
		final Player player;
		Location origin;
		/** Per part: one display, or for a rig one per bone (BONES order). */
		final Display[][] displays;
		final Map<String, Part> byId = new HashMap<>();
		/** For a skill's motion: the body it stands in for (the player, or the cinematic stand-in), followed each tick. */
		final Supplier<LivingEntity> actor;
		/** Entities hidden behind the rig, with whether this playback made them invisible. */
		final Map<LivingEntity, Boolean> hidden = new HashMap<>();
		/** The hand item shown by each "@mainhand" part. */
		final Map<Integer, ItemStack> hands = new HashMap<>();
		int tick;

		Playback(Anim anim, Player player, Supplier<LivingEntity> actor) {
			this.anim = anim;
			this.player = player;
			this.actor = actor;
			this.origin = flat(body(this).getLocation());
			this.displays = new Display[anim.parts().size()][];
			anim.parts().forEach(p -> byId.put(p.id(), p));
		}
	}

	/** The body the animation is about: the skill's actor when it has one, else the player. */
	private static LivingEntity body(Playback p) {
		LivingEntity a = p.actor == null ? null : p.actor.get();
		return a != null && a.isValid() ? a : p.player;
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
		PLAYING.add(new Playback(anim, player, null));
		return true;
	}

	/**
	 * A rig standing two blocks in front of the player for 10 seconds, facing
	 * them and waving, to check that an outfit shows ("player" for their own head
	 * on the default outfit).
	 */
	static void testRig(Player player, String outfit) {
		Key k = new Key(0, new float[] {0, 0, 2}, new float[] {0, 180, 0}, new float[] {1, 1, 1}, "linear");
		Map<String, List<Key>> bones = new HashMap<>();
		bones.put("right_arm", List.of(new Key(0, new float[3], new float[] {0, 0, 0}, new float[] {1, 1, 1}, "linear"),
				new Key(20, new float[3], new float[] {0, 0, 150}, new float[] {1, 1, 1}, "out"),
				new Key(40, new float[3], new float[] {0, 0, 110}, new float[] {1, 1, 1}, "inout"),
				new Key(60, new float[3], new float[] {0, 0, 150}, new float[] {1, 1, 1}, "inout"),
				new Key(80, new float[3], new float[] {0, 0, 110}, new float[] {1, 1, 1}, "inout"),
				new Key(100, new float[3], new float[] {0, 0, 0}, new float[] {1, 1, 1}, "inout")));
		bones.put("head", List.of(new Key(0, new float[3], new float[] {0, -20, 0}, new float[] {1, 1, 1}, "linear"),
				new Key(100, new float[3], new float[] {0, 20, 0}, new float[] {1, 1, 1}, "inout")));
		Part rig = new Part("test", "rig", "", null, List.of(), "", "none", false, -1, List.of(k), "",
				outfit == null || outfit.isBlank() ? "player" : outfit, "", false, false, bones);
		PLAYING.add(new Playback(new Anim("rig_test", 200, false, List.of(rig), List.of()), player, null));
	}

	/**
	 * Plays a skill's body motion (skill_*.json) on {@code actor}, if the file
	 * exists and skill motions are on (config: skill-motions). Replaces the
	 * player's previous skill motion; ends with {@link #stopSkill} or its length.
	 */
	static void playSkill(Player player, String name, Supplier<LivingEntity> actor) {
		if (plugin == null || !plugin.getConfig().getBoolean("skill-motions", true)) {
			return;
		}
		stopSkill(player);
		Anim anim = ANIMS.get(name.toLowerCase(Locale.ROOT));
		if (anim != null) {
			PLAYING.add(new Playback(anim, player, actor));
		}
	}

	static void stopSkill(Player player) {
		for (Iterator<Playback> it = PLAYING.iterator(); it.hasNext();) {
			Playback p = it.next();
			if (p.actor != null && p.player.equals(player)) {
				remove(p);
				it.remove();
			}
		}
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
		for (Display[] ds : p.displays) {
			if (ds != null) {
				for (Display d : ds) {
					if (d != null && d.isValid()) {
						d.remove();
					}
				}
			}
		}
		p.hidden.forEach((entity, madeInvisible) -> {
			if (madeInvisible) {
				if (entity instanceof Player player) {
					player.removePotionEffect(PotionEffectType.INVISIBILITY);
				} else if (entity.isValid()) {
					entity.setInvisible(false);
				}
			}
			if (!entity.isValid()) {
				return;
			}
			Map<EquipmentSlot, ItemStack> real = new HashMap<>();
			for (EquipmentSlot slot : EquipmentSlot.values()) {
				if (entity.canUseEquipmentSlot(slot) && entity.getEquipment() != null) {
					real.put(slot, entity.getEquipment().getItem(slot));
				}
			}
			for (Player viewer : entity.getWorld().getPlayers()) {
				if (!viewer.equals(entity)) {
					viewer.sendEquipmentChange(entity, real);
				}
			}
		});
		p.hidden.clear();
	}

	static void tick() {
		for (Iterator<Playback> it = PLAYING.iterator(); it.hasNext();) {
			Playback p = it.next();
			if (!p.player.isOnline() || p.tick > p.anim.length()) {
				remove(p);
				it.remove();
				continue;
			}
			boolean follow = p.anim.follow() || p.actor != null;
			if (follow) {
				p.origin = flat(body(p).getLocation());
			}
			for (int i = 0; i < p.anim.parts().size(); i++) {
				Part part = p.anim.parts().get(i);
				int first = part.keys().get(0).t();
				if (p.tick == first && p.displays[i] == null) {
					p.displays[i] = spawn(p, part);
				}
				Display[] ds = p.displays[i];
				if (ds == null) {
					continue;
				}
				if (part.kind().equals("rig")) {
					if (part.hidePlayer()) {
						hide(p);
					}
					if (part.drivePlayer() && p.actor == null) {
						drive(p, part);
					}
				} else if (part.item().equals("@mainhand") && ds[0] instanceof ItemDisplay item) {
					ItemStack hand = hand(p);
					if (!hand.equals(p.hands.get(i))) {
						p.hands.put(i, hand.clone());
						item.setItemStack(hand.clone());
					}
				}
				boolean send = p.tick > first && (p.tick - first) % STEP == 0;
				Matrix4f[] poses = send ? matrices(p, part, p.tick + STEP) : null;
				for (int b = 0; b < ds.length; b++) {
					Display d = ds[b];
					if (d == null || !d.isValid()) {
						continue;
					}
					if (follow) {
						d.teleport(p.origin);
					}
					if (send) {
						d.setInterpolationDelay(0);
						d.setInterpolationDuration(STEP);
						d.setTransformationMatrix(poses[b]);
					}
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

	/** Hides the body a rig stands in for: invisible, and its equipment shown as empty to everyone else. */
	private static void hide(Playback p) {
		LivingEntity body = body(p);
		if (!p.hidden.containsKey(body)) {
			boolean made = false;
			if (body instanceof Player player) {
				if (!player.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
					player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, p.anim.length() + 20, 0, false, false, false));
					made = true;
				}
			} else if (!body.isInvisible()) {
				body.setInvisible(true);
				made = true;
			}
			p.hidden.put(body, made);
		}
		Map<EquipmentSlot, ItemStack> empty = new HashMap<>();
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (body.canUseEquipmentSlot(slot)) {
				empty.put(slot, ItemStack.empty());
			}
		}
		for (Player viewer : body.getWorld().getPlayers()) {
			if (!viewer.equals(body) && viewer.getLocation().distanceSquared(body.getLocation()) < 128 * 128) {
				viewer.sendEquipmentChange(body, empty);
			}
		}
	}

	/** What the body holds, for "@mainhand" parts. */
	private static ItemStack hand(Playback p) {
		LivingEntity body = body(p);
		ItemStack held = body.getEquipment() == null ? null : body.getEquipment().getItemInMainHand();
		return held == null ? ItemStack.empty() : held;
	}

	/** Moves the player to where the rig's feet are, keeping where they look. */
	private static void drive(Playback p, Part rig) {
		Key k = sample(rig.keys(), p.tick);
		Location to = world(p.origin, k.pos()[0], k.pos()[1], k.pos()[2]);
		Location at = p.player.getLocation();
		to.setYaw(0);
		to.setPitch(0);
		p.player.setFallDistance(0);
		p.player.teleport(new Location(at.getWorld(), to.getX(), to.getY(), to.getZ(), 0, 0), TeleportFlag.Relative.YAW, TeleportFlag.Relative.PITCH);
		p.player.setVelocity(new org.bukkit.util.Vector());
	}

	/** A point in the player's frame (right, up, forward), as a world location. */
	private static Location world(Location origin, float right, float up, float forward) {
		double yaw = Math.toRadians(origin.getYaw());
		double sin = Math.sin(yaw);
		double cos = Math.cos(yaw);
		// Facing yaw, forward is (-sin, 0, cos) and the player's left is (cos, 0, sin).
		return origin.clone().add(-right * cos - forward * sin, up, -right * sin + forward * cos);
	}

	private static Display[] spawn(Playback p, Part part) {
		Location at = p.origin.clone();
		Display[] out;
		if (part.kind().equals("rig")) {
			String[] look = look(p, part);
			out = new Display[BONES.size()];
			for (int b = 0; b < BONES.size(); b++) {
				String bone = BONES.get(b);
				ItemStack stack;
				if (bone.equals("head") && part.skin().equals("player")) {
					stack = new ItemStack(Material.PLAYER_HEAD);
					stack.editMeta(SkullMeta.class, meta -> meta.setPlayerProfile(p.player.getPlayerProfile()));
				} else {
					stack = modelled(Material.STICK, look[0], List.of(look[1], bone));
				}
				out[b] = at.getWorld().spawn(at, ItemDisplay.class, d -> {
					d.setItemStack(stack);
					d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
				});
			}
		} else if (part.kind().equals("block")) {
			Material material = Material.matchMaterial(part.block());
			out = new Display[] {at.getWorld().spawn(at, BlockDisplay.class, b -> b.setBlock((material != null && material.isBlock() ? material
					: Material.GLOWSTONE).createBlockData()))};
		} else {
			ItemStack stack;
			if (part.item().equals("@mainhand")) {
				stack = hand(p).clone();
				p.hands.put(p.anim.parts().indexOf(part), stack.clone());
			} else {
				Material material = Material.matchMaterial(part.item());
				stack = modelled(material != null && material.isItem() ? material : Material.IRON_SWORD, part.model(), part.data());
			}
			out = new Display[] {at.getWorld().spawn(at, ItemDisplay.class, d -> {
				d.setItemStack(stack);
				d.setItemDisplayTransform(transform(part.transform()));
			})};
		}
		Matrix4f[] poses = matrices(p, part, part.keys().get(0).t());
		for (int i = 0; i < out.length; i++) {
			Display display = out[i];
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
			display.setTransformationMatrix(poses[i]);
		}
		return out;
	}

	/**
	 * The item model and outfit of a rig's bones: the outfit named; else for the
	 * player's own skin, their skin from the skin pack, or the suit with their arm width.
	 */
	private static String[] look(Playback p, Part part) {
		if (!part.skin().equals("player")) {
			return new String[] {"index_prescript:rig", part.skin()};
		}
		if (!part.outfit().isBlank()) {
			return new String[] {"index_prescript:rig", part.outfit()};
		}
		String own = SkinPack.outfit(p.player.getUniqueId());
		if (own != null) {
			return new String[] {SkinPack.MODEL, own};
		}
		boolean slim = false;
		PlayerProfile profile = p.player.getPlayerProfile();
		for (ProfileProperty property : profile.getProperties()) {
			if (property.getName().equals("textures")) {
				String json = new String(java.util.Base64.getDecoder().decode(property.getValue()), StandardCharsets.UTF_8);
				slim = json.contains("\"slim\"");
			}
		}
		return new String[] {"index_prescript:rig", slim ? "suit_slim" : "suit"};
	}

	private static ItemStack modelled(Material material, String model, List<String> data) {
		ItemStack stack = new ItemStack(material);
		stack.editMeta(meta -> {
			if (model != null && !model.isBlank()) {
				NamespacedKey key = NamespacedKey.fromString(model);
				if (key != null) {
					meta.setItemModel(key);
				}
			}
			if (!data.isEmpty()) {
				var cmd = meta.getCustomModelDataComponent();
				cmd.setStrings(data);
				meta.setCustomModelDataComponent(cmd);
			}
		});
		return stack;
	}

	private static ItemDisplay.ItemDisplayTransform transform(String name) {
		try {
			return ItemDisplay.ItemDisplayTransform.valueOf(name.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return ItemDisplay.ItemDisplayTransform.FIXED;
		}
	}

	// --- poses -------------------------------------------------------------------------------

	/** The display matrices of a part at tick {@code t}: one, or one per bone for a rig. */
	private static Matrix4f[] matrices(Playback p, Part part, int t) {
		Matrix4f[] out;
		if (part.kind().equals("rig")) {
			out = new Matrix4f[BONES.size()];
			for (int b = 0; b < BONES.size(); b++) {
				String bone = BONES.get(b);
				Matrix4f m = boneMatrix(p, part, bone, t, new HashSet<>());
				if (bone.equals("head") && part.skin().equals("player")) {
					// A player head item draws its skull below the origin, face turned back.
					m.translate(0, 0.5F, 0).rotateY((float) Math.PI);
				}
				out[b] = m;
			}
		} else {
			out = new Matrix4f[] {partMatrix(p, part, t, new HashSet<>())};
		}
		for (int i = 0; i < out.length; i++) {
			// The studio's x points to the player's right; the entity's own +x is their left.
			out[i] = new Matrix4f().scale(-1, 1, 1).mul(out[i]).scale(-1, 1, 1);
		}
		return out;
	}

	private static Matrix4f local(Key k) {
		return new Matrix4f().translate(k.pos()[0], k.pos()[1], k.pos()[2])
				.rotateXYZ((float) Math.toRadians(k.rot()[0]), (float) Math.toRadians(k.rot()[1]), (float) Math.toRadians(k.rot()[2]))
				.scale(k.scale()[0], k.scale()[1], k.scale()[2]);
	}

	/** A part's own frame in the player's frame, under its parent if it has one. */
	private static Matrix4f partMatrix(Playback p, Part part, int t, Set<String> seen) {
		Matrix4f base = parentMatrix(p, part.parent(), t, seen.add(part.id()) ? seen : null);
		Matrix4f m = base.mul(local(sample(part.keys(), t)));
		if (part.kind().equals("rig")) {
			m.scale(RIG_SCALE);
		}
		return m;
	}

	private static Matrix4f parentMatrix(Playback p, String parent, int t, Set<String> seen) {
		if (parent == null || parent.isBlank() || seen == null) {
			return new Matrix4f();
		}
		int dot = parent.indexOf('.');
		Part target = p.byId.get(dot < 0 ? parent : parent.substring(0, dot));
		if (target == null || seen.contains(target.id())) {
			return new Matrix4f();
		}
		if (dot >= 0 && target.kind().equals("rig") && BONES.contains(parent.substring(dot + 1))) {
			return boneMatrix(p, target, parent.substring(dot + 1), t, seen);
		}
		return partMatrix(p, target, t, seen);
	}

	/** A bone's frame: the rig's, moved to the joint (in pixels) and turned by the bone's keys. */
	private static Matrix4f boneMatrix(Playback p, Part rig, String bone, int t, Set<String> seen) {
		Matrix4f m = partMatrix(p, rig, t, seen).scale(1 / 16F);
		float[] pivot = PIVOTS.get(bone);
		m.translate(pivot[0], pivot[1], pivot[2]);
		List<Key> keys = rig.bones().get(bone);
		if (keys != null && !keys.isEmpty()) {
			m.mul(local(sample(keys, t)));
		}
		// Back to blocks: bone models and children measure in blocks (16 pixels).
		return m.scale(16F);
	}

	/** The pose at tick {@code t}, eased between keyframes. */
	private static Key sample(List<Key> keys, int t) {
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
		return new Key(t, lerp(a.pos(), b.pos(), f), lerp(a.rot(), b.rot(), f), lerp(a.scale(), b.scale(), f), "linear");
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

	/** A rig standing in for the player: raises the right arm and swings down, the weapon in hand. */
	private static final String EXAMPLE_RIG = """
			{
			  "name": "example_rig_slash",
			  "length": 30,
			  "parts": [
			    {"id": "actor", "kind": "rig", "skin": "player", "hide_player": true,
			     "keys": [{"t": 0, "pos": [0, 0, 0]}, {"t": 12, "pos": [0, 0, 0.4], "ease": "out"}, {"t": 30, "pos": [0, 0, 0.4]}],
			     "bones": {
			       "right_arm": [{"t": 0, "rot": [0, 0, 0]}, {"t": 10, "rot": [-160, 0, -10], "ease": "out"},
			                     {"t": 14, "rot": [-30, 0, 0], "ease": "in"}, {"t": 30, "rot": [0, 0, 0], "ease": "inout"}],
			       "body": [{"t": 0, "rot": [0, 0, 0]}, {"t": 10, "rot": [0, -20, 0], "ease": "out"}, {"t": 14, "rot": [15, 15, 0], "ease": "in"},
			                {"t": 30, "rot": [0, 0, 0], "ease": "inout"}],
			       "left_leg": [{"t": 0, "rot": [0, 0, 0]}, {"t": 12, "rot": [-25, 0, 0], "ease": "out"}, {"t": 30, "rot": [0, 0, 0]}],
			       "right_leg": [{"t": 0, "rot": [0, 0, 0]}, {"t": 12, "rot": [20, 0, 0], "ease": "out"}, {"t": 30, "rot": [0, 0, 0]}]
			     }},
			    {"id": "weapon", "kind": "item", "item": "minecraft:stick", "model": "index_prescript:laevatein", "data": ["release_3"],
			     "transform": "thirdperson_righthand", "parent": "actor.right_arm",
			     "keys": [{"t": 0, "pos": [0.0625, -0.625, 0.125], "rot": [90, 0, 0]}]}
			  ],
			  "events": [
			    {"t": 10, "type": "sound", "sound": "entity.player.attack.sweep", "pitch": 0.7},
			    {"t": 14, "type": "effect", "effect": "example_red_slash"}
			  ]
			}
			""";
}
