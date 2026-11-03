package io.github.sharket1111.indexprescript.paper;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * [Mang]'s glowing golden ring as a 3D model (tools/mang_ring.py), wound round
 * the held weapon and slowly turning about it. Up to six can be wound at once,
 * spaced along the blade and turning in alternate directions. Golden motes go
 * with it, which Bedrock players (who cannot see the model) see as well.
 *
 * The server cannot tell first person from third, so a player holding Mang
 * sees their own rings as in first person (following where they look, by the
 * held item in the corner of the view) while everyone else sees them on the
 * arm; /ip mang view switches the holder's own view to the third-person rings
 * (for looking with F5).
 */
final class MangRing {
	private static final float SCALE = 0.35F;
	private static final double SPIN_PER_TICK = 0.12;
	static final int MAX_RINGS = 6;
	private static final int SWING_TICKS = 6;
	/** Distance between neighbouring rings along the weapon, in blocks. */
	private static final double SPACING = 0.15;
	/**
	 * First person, matched to where the held sword is drawn: near the right
	 * edge of the view, standing almost upright and leaning left, so the rings
	 * there are smaller and closer together.
	 */
	private static final double FIRST_PERSON_TILT = 65.0;
	private static final double FIRST_PERSON_LEAN = 30.0;
	private static final float FIRST_PERSON_SCALE = 0.18F;
	private static final double FIRST_PERSON_SPACING = 0.08;
	private static final Map<UUID, Ring> RINGS = new ConcurrentHashMap<>();
	/** Holders who see their own rings as in third person. */
	private static final Set<UUID> THIRD_PERSON_VIEW = ConcurrentHashMap.newKeySet();

	private static final class Ring {
		final LivingEntity holder;
		/** Seen by everyone else (and by the holder in third-person view). */
		final List<ItemDisplay> third;
		/** Seen only by a player holder, as in first person. */
		final List<ItemDisplay> first;
		final long until;
		final int count;
		int age;
		int swingAge = -100;

		Ring(LivingEntity holder, List<ItemDisplay> third, List<ItemDisplay> first, long until, int count) {
			this.holder = holder;
			this.third = third;
			this.first = first;
			this.until = until;
			this.count = count;
		}

		void remove() {
			third.forEach(ItemDisplay::remove);
			first.forEach(ItemDisplay::remove);
		}

		boolean broken() {
			return third.stream().anyMatch(d -> !d.isValid()) || first.stream().anyMatch(d -> !d.isValid());
		}
	}

	private MangRing() {
	}

	/** Shows one ring on {@code holder}'s weapon for {@code ticks} ticks, or until hidden if 0. */
	static void show(LivingEntity holder, int ticks) {
		show(holder, ticks, 1);
	}

	private static ItemDisplay spawnRing(LivingEntity holder, Vector at, ItemStack model, boolean visible) {
		return holder.getWorld().spawn(at.toLocation(holder.getWorld()), ItemDisplay.class, d -> {
			d.setPersistent(false);
			d.setVisibleByDefault(visible);
			d.setItemStack(model);
			d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
			d.setBrightness(new Display.Brightness(15, 15));
			d.setTeleportDuration(1);
			d.setInterpolationDuration(1);
		});
	}

	/** Shows {@code count} rings (1-6) on {@code holder}'s weapon for {@code ticks} ticks, or until hidden if 0. */
	static void show(LivingEntity holder, int ticks, int count) {
		hide(holder);
		count = Math.max(1, Math.min(MAX_RINGS, count));
		ItemStack model = ItemStack.of(Material.PAPER);
		model.editMeta(meta -> meta.setItemModel(new NamespacedKey(Items.NAMESPACE, "mang_ring")));
		Vector center = Effects.weaponFrame(holder)[0];
		boolean firstPerson = holder instanceof Player player && !THIRD_PERSON_VIEW.contains(player.getUniqueId());
		List<ItemDisplay> third = new ArrayList<>();
		List<ItemDisplay> first = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			ItemDisplay outer = spawnRing(holder, center, model, true);
			third.add(outer);
			if (firstPerson) {
				Player player = (Player) holder;
				player.hideEntity(IndexPrescriptPlugin.instance(), outer);
				ItemDisplay own = spawnRing(holder, center, model, false);
				player.showEntity(IndexPrescriptPlugin.instance(), own);
				first.add(own);
			}
		}
		long until = ticks > 0 ? CaduceusListener.now() + ticks : Long.MAX_VALUE;
		RINGS.put(holder.getUniqueId(), new Ring(holder, third, first, until, count));
		Effects.mangForm(holder);
	}

	static void hide(LivingEntity holder) {
		Ring ring = RINGS.remove(holder.getUniqueId());
		if (ring != null) {
			ring.remove();
		}
	}

	/** Switches the player's own view of their rings between first and third person; returns true for third. */
	static boolean toggleView(Player player) {
		boolean third = THIRD_PERSON_VIEW.add(player.getUniqueId()) || !THIRD_PERSON_VIEW.remove(player.getUniqueId());
		Ring ring = RINGS.get(player.getUniqueId());
		if (ring != null) {
			long left = ring.until == Long.MAX_VALUE ? 0 : Math.max(1, ring.until - CaduceusListener.now());
			show(player, (int) left, ring.count);
		}
		return third;
	}

	/** The holder swung: the rings follow the blade through the swing. */
	static void swing(LivingEntity holder) {
		Ring ring = RINGS.get(holder.getUniqueId());
		if (ring != null) {
			ring.swingAge = ring.age;
		}
	}

	/**
	 * The blade's tilt (degrees above rest) and sideways turn through a swing
	 * (vanilla's lasts 6 ticks): raised up and to the right, chopped down
	 * across to the left, then back to rest.
	 */
	private static double[] swingPose(Ring ring) {
		double t = (ring.age - ring.swingAge) / (double) SWING_TICKS;
		if (t < 0 || t >= 1) {
			return new double[] {0, 0};
		}
		double rise = 100 - Effects.REST_TILT;
		double fall = -40 - Effects.REST_TILT;
		if (t < 0.3) {
			double u = t / 0.3;
			return new double[] {rise * u, -30 * u};
		} else if (t < 0.7) {
			double u = (t - 0.3) / 0.4;
			return new double[] {rise + (fall - rise) * u, -30 + 70 * u};
		}
		double u = (t - 0.7) / 0.3;
		return new double[] {fall * (1 - u), 40 * (1 - u)};
	}

	/**
	 * The blade as a held item sits in first person: in the lower right of the
	 * view, following where the player looks, pointing ahead and up.
	 */
	private static Vector[] firstPersonFrame(Player player, double tiltDeg, double leanDeg) {
		Location eye = player.getEyeLocation();
		Vector look = eye.getDirection().normalize();
		Vector right = look.clone().crossProduct(new Vector(0, 1, 0));
		if (right.lengthSquared() < 1.0E-4) {
			double yaw = Math.toRadians(eye.getYaw());
			right = new Vector(-Math.cos(yaw), 0, -Math.sin(yaw));
		}
		right.normalize();
		Vector up = right.clone().crossProduct(look).normalize();
		// The middle of the blade, 0.6 ahead: near the right edge and a little below the centre of the view.
		Vector middle = eye.toVector().add(look.clone().multiply(0.6)).add(right.clone().multiply(0.6)).subtract(up.clone().multiply(0.08));
		double lean = Math.toRadians(leanDeg);
		Vector heading = look.clone().multiply(Math.cos(lean)).subtract(right.clone().multiply(Math.sin(lean)));
		double tilt = Math.toRadians(tiltDeg);
		Vector axis = heading.multiply(Math.cos(tilt)).add(up.multiply(Math.sin(tilt))).normalize();
		return new Vector[] {middle, axis};
	}

	private static void place(List<ItemDisplay> displays, Vector[] frame, LivingEntity holder, int age, float scale, double spacing) {
		Vector axis = frame[1];
		Vector3f axisF = new Vector3f((float) axis.getX(), (float) axis.getY(), (float) axis.getZ());
		int count = displays.size();
		for (int i = 0; i < count; i++) {
			ItemDisplay display = displays.get(i);
			// Spread along the blade around its middle, neighbours turning opposite ways.
			double offset = (i - (count - 1) / 2.0) * spacing;
			display.teleport(frame[0].clone().add(axis.clone().multiply(offset)).toLocation(holder.getWorld()));
			double spin = age * SPIN_PER_TICK * (i % 2 == 0 ? 1 : -1) + i * 0.7;
			Quaternionf turn = new Quaternionf().rotationTo(new Vector3f(0, 0, 1), axisF).rotateZ((float) spin);
			display.setInterpolationDelay(0);
			display.setTransformation(new Transformation(new Vector3f(), turn, new Vector3f(scale, scale, scale), new Quaternionf()));
		}
	}

	static boolean showing(LivingEntity holder) {
		return RINGS.containsKey(holder.getUniqueId());
	}

	/** Called every tick: keeps each ring on its weapon, turning. */
	static void tick() {
		long now = CaduceusListener.now();
		Iterator<Ring> it = RINGS.values().iterator();
		while (it.hasNext()) {
			Ring ring = it.next();
			if (now >= ring.until || !ring.holder.isValid() || ring.holder.isDead() || ring.broken()) {
				ring.remove();
				it.remove();
				continue;
			}
			double[] pose = swingPose(ring);
			Vector[] frame = Effects.weaponFrame(ring.holder, Effects.REST_TILT + pose[0], pose[1]);
			place(ring.third, frame, ring.holder, ring.age, SCALE, SPACING);
			if (!ring.first.isEmpty() && ring.holder instanceof Player player) {
				// A first-person swing tips the blade forward and across, more gently than on the arm.
				Vector[] own = firstPersonFrame(player, FIRST_PERSON_TILT + pose[0] * 0.5, FIRST_PERSON_LEAN + pose[1]);
				place(ring.first, own, ring.holder, ring.age, FIRST_PERSON_SCALE, FIRST_PERSON_SPACING);
			}
			if (ring.age % 4 == 0) {
				Effects.mang(ring.holder, ring.age, frame);
			}
			ring.age++;
		}
	}

	static void hideAll() {
		for (Ring ring : Set.copyOf(RINGS.values())) {
			ring.remove();
		}
		RINGS.clear();
	}
}
