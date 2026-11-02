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
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * [Mang]'s glowing golden ring as a 3D model (tools/mang_ring.py), wound round
 * the held weapon and slowly turning about it. Up to six can be wound at once,
 * spaced along the blade and turning in alternate directions. Golden motes go with it, which
 * Bedrock players (who cannot see the model) see as well.
 */
final class MangRing {
	private static final float SCALE = 0.35F;
	private static final double SPIN_PER_TICK = 0.12;
	static final int MAX_RINGS = 6;
	private static final int SWING_TICKS = 6;
	/** Distance between neighbouring rings along the weapon, in blocks. */
	private static final double SPACING = 0.15;
	private static final Map<UUID, Ring> RINGS = new ConcurrentHashMap<>();

	private static final class Ring {
		final LivingEntity holder;
		final List<ItemDisplay> displays;
		final long until;
		int age;
		int swingAge = -100;

		Ring(LivingEntity holder, List<ItemDisplay> displays, long until) {
			this.holder = holder;
			this.displays = displays;
			this.until = until;
		}

		void remove() {
			displays.forEach(ItemDisplay::remove);
		}
	}

	private MangRing() {
	}

	/** Shows one ring on {@code holder}'s weapon for {@code ticks} ticks, or until hidden if 0. */
	static void show(LivingEntity holder, int ticks) {
		show(holder, ticks, 1);
	}

	/** Shows {@code count} rings (1-6) on {@code holder}'s weapon for {@code ticks} ticks, or until hidden if 0. */
	static void show(LivingEntity holder, int ticks, int count) {
		hide(holder);
		ItemStack model = ItemStack.of(Material.PAPER);
		model.editMeta(meta -> meta.setItemModel(new NamespacedKey(Items.NAMESPACE, "mang_ring")));
		Vector center = Effects.weaponFrame(holder)[0];
		List<ItemDisplay> displays = new ArrayList<>();
		for (int i = 0; i < Math.max(1, Math.min(MAX_RINGS, count)); i++) {
			displays.add(holder.getWorld().spawn(center.toLocation(holder.getWorld()), ItemDisplay.class, d -> {
				d.setPersistent(false);
				d.setItemStack(model);
				d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
				d.setBrightness(new Display.Brightness(15, 15));
				d.setTeleportDuration(1);
				d.setInterpolationDuration(1);
			}));
		}
		long until = ticks > 0 ? CaduceusListener.now() + ticks : Long.MAX_VALUE;
		RINGS.put(holder.getUniqueId(), new Ring(holder, displays, until));
		Effects.mangForm(holder);
	}

	static void hide(LivingEntity holder) {
		Ring ring = RINGS.remove(holder.getUniqueId());
		if (ring != null) {
			ring.remove();
		}
	}

	/** The holder swung: the rings follow the blade through the swing. */
	static void swing(LivingEntity holder) {
		Ring ring = RINGS.get(holder.getUniqueId());
		if (ring != null) {
			ring.swingAge = ring.age;
		}
	}

	/**
	 * The blade through a swing (vanilla's lasts 6 ticks): raised up and to the
	 * right, chopped down across to the left, then back to rest.
	 */
	private static Vector[] swingFrame(Ring ring) {
		double t = (ring.age - ring.swingAge) / (double) SWING_TICKS;
		if (t < 0 || t >= 1) {
			return Effects.weaponFrame(ring.holder);
		}
		double tilt;
		double yaw;
		if (t < 0.3) {
			double u = t / 0.3;
			tilt = Effects.REST_TILT + (100 - Effects.REST_TILT) * u;
			yaw = -30 * u;
		} else if (t < 0.7) {
			double u = (t - 0.3) / 0.4;
			tilt = 100 - 140 * u;
			yaw = -30 + 70 * u;
		} else {
			double u = (t - 0.7) / 0.3;
			tilt = -40 + (Effects.REST_TILT + 40) * u;
			yaw = 40 * (1 - u);
		}
		return Effects.weaponFrame(ring.holder, tilt, yaw);
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
			if (now >= ring.until || !ring.holder.isValid() || ring.holder.isDead()
					|| ring.displays.stream().anyMatch(d -> !d.isValid())) {
				ring.remove();
				it.remove();
				continue;
			}
			Vector[] frame = swingFrame(ring);
			Vector axis = frame[1];
			Vector3f axisF = new Vector3f((float) axis.getX(), (float) axis.getY(), (float) axis.getZ());
			int count = ring.displays.size();
			for (int i = 0; i < count; i++) {
				ItemDisplay display = ring.displays.get(i);
				// Spread along the blade around its middle, neighbours turning opposite ways.
				double offset = (i - (count - 1) / 2.0) * SPACING;
				Location at = frame[0].clone().add(axis.clone().multiply(offset)).toLocation(ring.holder.getWorld());
				display.teleport(at);
				double spin = ring.age * SPIN_PER_TICK * (i % 2 == 0 ? 1 : -1) + i * 0.7;
				Quaternionf turn = new Quaternionf().rotationTo(new Vector3f(0, 0, 1), axisF).rotateZ((float) spin);
				display.setInterpolationDelay(0);
				display.setTransformation(new Transformation(new Vector3f(), turn, new Vector3f(SCALE, SCALE, SCALE), new Quaternionf()));
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
