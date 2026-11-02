package io.github.sharket1111.indexprescript.paper;

import java.util.Iterator;
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
 * the held weapon and slowly turning about it. Golden motes go with it, which
 * Bedrock players (who cannot see the model) see as well.
 */
final class MangRing {
	private static final float SCALE = 1.35F;
	private static final double SPIN_PER_TICK = 0.12;
	private static final Map<UUID, Ring> RINGS = new ConcurrentHashMap<>();

	private static final class Ring {
		final LivingEntity holder;
		final ItemDisplay display;
		final long until;
		int age;

		Ring(LivingEntity holder, ItemDisplay display, long until) {
			this.holder = holder;
			this.display = display;
			this.until = until;
		}
	}

	private MangRing() {
	}

	/** Shows the ring on {@code holder}'s weapon for {@code ticks} ticks, or until hidden if 0. */
	static void show(LivingEntity holder, int ticks) {
		hide(holder);
		ItemStack model = ItemStack.of(Material.PAPER);
		model.editMeta(meta -> meta.setItemModel(new NamespacedKey(Items.NAMESPACE, "mang_ring")));
		Vector center = Effects.weaponFrame(holder)[0];
		ItemDisplay display = holder.getWorld().spawn(center.toLocation(holder.getWorld()), ItemDisplay.class, d -> {
			d.setPersistent(false);
			d.setItemStack(model);
			d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
			d.setBrightness(new Display.Brightness(15, 15));
			d.setTeleportDuration(1);
			d.setInterpolationDuration(1);
		});
		long until = ticks > 0 ? CaduceusListener.now() + ticks : Long.MAX_VALUE;
		RINGS.put(holder.getUniqueId(), new Ring(holder, display, until));
		Effects.mangForm(holder);
	}

	static void hide(LivingEntity holder) {
		Ring ring = RINGS.remove(holder.getUniqueId());
		if (ring != null) {
			ring.display.remove();
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
			if (now >= ring.until || !ring.holder.isValid() || ring.holder.isDead() || !ring.display.isValid()) {
				ring.display.remove();
				it.remove();
				continue;
			}
			Vector[] frame = Effects.weaponFrame(ring.holder);
			Location at = frame[0].toLocation(ring.holder.getWorld());
			ring.display.teleport(at);
			Vector axis = frame[1];
			Quaternionf turn = new Quaternionf()
					.rotationTo(new Vector3f(0, 0, 1), new Vector3f((float) axis.getX(), (float) axis.getY(), (float) axis.getZ()))
					.rotateZ((float) (ring.age * SPIN_PER_TICK));
			ring.display.setInterpolationDelay(0);
			ring.display.setTransformation(new Transformation(new Vector3f(), turn, new Vector3f(SCALE, SCALE, SCALE), new Quaternionf()));
			if (ring.age % 2 == 0) {
				Effects.mang(ring.holder, ring.age);
			}
			ring.age++;
		}
	}

	static void hideAll() {
		for (Ring ring : Set.copyOf(RINGS.values())) {
			ring.display.remove();
		}
		RINGS.clear();
	}
}
