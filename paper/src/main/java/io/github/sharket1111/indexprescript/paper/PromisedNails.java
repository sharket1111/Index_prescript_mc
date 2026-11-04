package io.github.sharket1111.indexprescript.paper;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * [Promised Nail] (fan-made, after Limbus Company): glowing golden nails the
 * Brand Manager's hammer drives into foes. A nail falls from above and stays
 * driven into the foe's head (up to {@link #MAX} per foe); the hammer's
 * smash spends them all for extra damage. Nails fade a minute after the last
 * one, or when the foe dies.
 */
final class PromisedNails {
	static final int MAX = 10;
	private static final int FALL_TICKS = 5;
	private static final double FALL_HEIGHT = 6.0;
	private static final int LINGER_TICKS = 60 * 20;
	private static final float STUCK_SCALE = 1.0F;
	static final Particle.DustOptions GOLD = new Particle.DustOptions(Color.fromRGB(0xFFD54A), 1.3F);
	private static final Particle.DustOptions PALE = new Particle.DustOptions(Color.fromRGB(0xFFF4C0), 1.0F);
	private static final Map<UUID, Driven> DRIVEN = new ConcurrentHashMap<>();
	private static final List<Falling> FALLING = new ArrayList<>();

	/** The nails in one foe. */
	private static final class Driven {
		final LivingEntity foe;
		final List<ItemDisplay> shown = new ArrayList<>();
		int count;
		long lastAt;

		Driven(LivingEntity foe) {
			this.foe = foe;
		}
	}

	/** A nail on its way down; {@code foe} is null for one that only strikes the ground. */
	private static final class Falling {
		final ItemDisplay display;
		final LivingEntity foe;
		final Location ground;
		final int amount;
		int age;

		Falling(ItemDisplay display, LivingEntity foe, Location ground, int amount) {
			this.display = display;
			this.foe = foe;
			this.ground = ground;
			this.amount = amount;
		}
	}

	private PromisedNails() {
	}

	static ItemStack model() {
		return model(false);
	}

	/** The nail, or (ghost) its half-see-through version for Strike Again's rain. */
	static ItemStack model(boolean ghost) {
		ItemStack stack = ItemStack.of(Material.PAPER);
		stack.editMeta(meta -> meta.setItemModel(new NamespacedKey(Items.NAMESPACE, ghost ? "promised_nail_ghost" : "promised_nail")));
		return stack;
	}

	static int count(LivingEntity foe) {
		Driven driven = DRIVEN.get(foe.getUniqueId());
		return driven == null ? 0 : driven.count;
	}

	/** Spends every nail in {@code foe}; returns how many there were. */
	static int spend(LivingEntity foe) {
		Driven driven = DRIVEN.remove(foe.getUniqueId());
		if (driven == null) {
			return 0;
		}
		hide(driven);
		if (driven.count > 0 && foe.isValid()) {
			Location top = top(foe);
			foe.getWorld().spawnParticle(Particle.DUST, top, 24, 0.35, 0.25, 0.35, 0.0, GOLD);
			foe.getWorld().spawnParticle(Particle.END_ROD, top, 10, 0.2, 0.2, 0.2, 0.08);
			foe.getWorld().playSound(top, Sound.BLOCK_AMETHYST_BLOCK_BREAK, SoundCategory.PLAYERS, 1.0F, 0.6F);
		}
		return driven.count;
	}

	private static Location top(LivingEntity foe) {
		return foe.getLocation().add(0, foe.getHeight(), 0);
	}

	/** Drops {@code amount} nails on {@code foe} from above; they count once they land. */
	static void drop(LivingEntity foe, int amount) {
		if (amount <= 0 || !foe.isValid() || foe.isDead()) {
			return;
		}
		Location start = top(foe).add(0, FALL_HEIGHT, 0);
		FALLING.add(new Falling(spawnFalling(start, 2.4F, false), foe, null, amount));
		foe.getWorld().playSound(start, Sound.ITEM_TRIDENT_RIPTIDE_1, SoundCategory.PLAYERS, 0.7F, 1.6F);
	}

	/** A great nail striking the ground near {@code center}, for the skills' rain of nails. */
	static void rain(Location center, double radius, float scale, boolean ghost) {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		double angle = random.nextDouble(Math.PI * 2);
		double distance = Math.sqrt(random.nextDouble()) * radius;
		Location ground = center.clone().add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
		Location start = ground.clone().add(0, FALL_HEIGHT + 2, 0);
		FALLING.add(new Falling(spawnFalling(start, scale, ghost), null, ground, 0));
	}

	private static ItemDisplay spawnFalling(Location at, float scale, boolean ghost) {
		return at.getWorld().spawn(at, ItemDisplay.class, display -> {
			display.setPersistent(false);
			display.setItemStack(model(ghost));
			display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
			display.setBrightness(new Display.Brightness(15, 15));
			display.setTeleportDuration(1);
			// The model's point is at its bottom: lift it so the point is at the entity's position.
			display.setTransformation(new Transformation(new Vector3f(0, 0.5F * scale, 0), new Quaternionf(),
					new Vector3f(scale, scale, scale), new Quaternionf()));
		});
	}

	/** A nail landed in {@code foe}. */
	private static void land(LivingEntity foe, int amount) {
		if (!foe.isValid() || foe.isDead()) {
			return;
		}
		Driven driven = DRIVEN.computeIfAbsent(foe.getUniqueId(), id -> new Driven(foe));
		driven.count = Math.min(MAX, driven.count + amount);
		driven.lastAt = CaduceusListener.now();
		show(driven);
		World world = foe.getWorld();
		Location top = top(foe);
		world.spawnParticle(Particle.ELECTRIC_SPARK, top, 12, 0.25, 0.25, 0.25, 0.1);
		world.spawnParticle(Particle.DUST, top, 18, 0.3, 0.3, 0.3, 0.0, GOLD);
		world.spawnParticle(Particle.END_ROD, top, 8, 0.15, 0.3, 0.15, 0.05);
		world.playSound(top, Sound.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 0.6F, 1.8F);
		world.playSound(top, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 1.0F, 1.4F);
	}

	/** Shows one small nail per nail, in a ring on the foe's head, riding along with it. */
	private static void show(Driven driven) {
		LivingEntity foe = driven.foe;
		while (driven.shown.size() < driven.count) {
			int index = driven.shown.size();
			double angle = index * (Math.PI * 2 / MAX) + (index % 2) * 0.3;
			double radius = foe.getWidth() * 0.35 + (index % 2) * 0.08;
			float tilt = 0.25F;
			Quaternionf lean = new Quaternionf().rotateY((float) -angle).rotateZ(tilt);
			Vector3f offset = new Vector3f((float) (Math.cos(angle) * radius), (float) (STUCK_SCALE * 0.5 - 0.25),
					(float) (Math.sin(angle) * radius));
			ItemDisplay nail = foe.getWorld().spawn(top(foe), ItemDisplay.class, display -> {
				display.setPersistent(false);
				display.setItemStack(model());
				display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
				display.setBrightness(new Display.Brightness(15, 15));
				display.setTransformation(new Transformation(offset, lean,
						new Vector3f(STUCK_SCALE, STUCK_SCALE, STUCK_SCALE), new Quaternionf()));
			});
			if (!foe.addPassenger(nail)) {
				nail.setTeleportDuration(2);
			}
			driven.shown.add(nail);
		}
	}

	private static void hide(Driven driven) {
		driven.shown.forEach(ItemDisplay::remove);
		driven.shown.clear();
	}

	/** Called every tick: moves falling nails, keeps driven ones on their foes, and fades old ones. */
	static void tick() {
		long now = CaduceusListener.now();
		Iterator<Falling> falling = FALLING.iterator();
		while (falling.hasNext()) {
			Falling nail = falling.next();
			if (!nail.display.isValid()) {
				falling.remove();
				continue;
			}
			nail.age++;
			Location target = nail.foe != null ? top(nail.foe) : nail.ground;
			if (nail.foe != null && (!nail.foe.isValid() || nail.foe.isDead())) {
				nail.display.remove();
				falling.remove();
				continue;
			}
			double left = Math.max(0, FALL_TICKS - nail.age) / (double) FALL_TICKS;
			Location at = target.clone().add(0, (nail.foe != null ? FALL_HEIGHT : FALL_HEIGHT + 2) * left * left, 0);
			nail.display.teleport(at);
			nail.display.getWorld().spawnParticle(Particle.DUST, at.clone().add(0, 0.8, 0), 2, 0.05, 0.3, 0.05, 0.0, PALE);
			if (nail.age == FALL_TICKS) {
				if (nail.foe != null) {
					nail.display.remove();
					falling.remove();
					land(nail.foe, nail.amount);
				} else {
					strikeGround(nail.ground);
				}
			} else if (nail.foe == null && nail.age > FALL_TICKS + 50) {
				// Rain nails stay standing in the ground a moment, then fade.
				nail.display.remove();
				falling.remove();
			}
		}
		Iterator<Driven> it = DRIVEN.values().iterator();
		while (it.hasNext()) {
			Driven driven = it.next();
			LivingEntity foe = driven.foe;
			if (!foe.isValid() || foe.isDead() || now - driven.lastAt > LINGER_TICKS) {
				hide(driven);
				it.remove();
				continue;
			}
			driven.shown.removeIf(nail -> !nail.isValid());
			if (driven.shown.size() < driven.count) {
				show(driven);
			}
			for (ItemDisplay nail : driven.shown) {
				if (nail.getVehicle() == null) {
					nail.teleport(top(foe));
				}
			}
			if (now % 8 == 0) {
				foe.getWorld().spawnParticle(Particle.DUST, top(foe), 1 + driven.count / 3, 0.25, 0.1, 0.25, 0.0, GOLD);
			}
		}
	}

	private static void strikeGround(Location ground) {
		World world = ground.getWorld();
		world.spawnParticle(Particle.DUST, ground, 16, 0.4, 0.1, 0.4, 0.0, GOLD);
		world.spawnParticle(Particle.END_ROD, ground.clone().add(0, 0.3, 0), 6, 0.2, 0.2, 0.2, 0.06);
		world.spawnParticle(Particle.BLOCK, ground, 10, 0.4, 0.1, 0.4, 0.0, ground.clone().add(0, -0.5, 0).getBlock().getBlockData());
		world.playSound(ground, Sound.BLOCK_ANVIL_PLACE, SoundCategory.PLAYERS, 0.35F, 1.7F);
	}

	/** Golden motes rising from {@code at}, for the skills. */
	static void glow(Location at, int count, double spread) {
		at.getWorld().spawnParticle(Particle.DUST, at, count, spread, spread, spread, 0.0, GOLD);
	}

	static void clearAll() {
		DRIVEN.values().forEach(PromisedNails::hide);
		DRIVEN.clear();
		FALLING.forEach(nail -> nail.display.remove());
		FALLING.clear();
	}
}
