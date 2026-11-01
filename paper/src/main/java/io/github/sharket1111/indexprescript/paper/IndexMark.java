package io.github.sharket1111.indexprescript.paper;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * [Index Prescript Target]: Caduceus hits mark the foe struck and one random
 * foe within 6 blocks of it. A marked foe shows a translucent blue prescript
 * sign over its head (plus faint blue motes, which Bedrock players see too) and
 * takes more damage from Rien:
 * - marked: damage taken +20%, and +2.5% per [Hermes] (max +20%)
 * - at 9 [Hermes]: +30% against every target instead of the per-[Hermes] bonus
 * A mark lasts two turns and is refreshed by further hits.
 */
final class IndexMark {
	private static final int DURATION_TICKS = CombatState.TURN_TICKS * 2;
	private static final double SPREAD_RADIUS = 6.0;
	private static final double MARKED_TAKEN = 0.20;
	private static final double PER_HERMES = 0.025;
	private static final double MAX_HERMES_BONUS = 0.20;
	private static final double FULL_HERMES_BONUS = 0.30;
	private static final Particle.DustOptions MOTE = new Particle.DustOptions(Color.fromRGB(0x4FA8FF), 0.8F);
	private static final Map<UUID, Mark> MARKS = new ConcurrentHashMap<>();

	private static final class Mark {
		final LivingEntity entity;
		ItemDisplay sign;
		long expiresAt;

		Mark(LivingEntity entity) {
			this.entity = entity;
		}
	}

	private IndexMark() {
	}

	static boolean marked(Entity entity) {
		Mark mark = MARKS.get(entity.getUniqueId());
		return mark != null && mark.expiresAt > CaduceusListener.now();
	}

	/** Damage multiplier for Rien's hit on {@code victim}, read before the hit marks it. */
	static double multiplier(Player rien, Entity victim) {
		int hermes = PrescriptTracker.hermes(rien);
		boolean marked = marked(victim);
		double bonus = 1.0 + (marked ? MARKED_TAKEN : 0.0);
		if (hermes >= Items.MAX_HERMES) {
			bonus += FULL_HERMES_BONUS;
		} else if (marked) {
			bonus += Math.min(MAX_HERMES_BONUS, hermes * PER_HERMES);
		}
		return bonus;
	}

	/** Marks the foe Rien struck and a random foe within 6 blocks of it. */
	static void strike(Player rien, LivingEntity victim) {
		if (!victim.isValid() || victim.isDead()) {
			return;
		}
		mark(victim);
		List<LivingEntity> others = foesNear(rien, victim);
		if (!others.isEmpty()) {
			mark(others.get(ThreadLocalRandom.current().nextInt(others.size())));
		}
	}

	private static List<LivingEntity> foesNear(Player rien, LivingEntity center) {
		List<LivingEntity> foes = new ArrayList<>();
		Entity actor = Cinematic.body(rien);
		for (Entity entity : center.getNearbyEntities(SPREAD_RADIUS, SPREAD_RADIUS, SPREAD_RADIUS)) {
			if (!(entity instanceof LivingEntity living) || entity == rien || entity == actor
					|| entity instanceof ArmorStand || entity instanceof Mannequin
					|| !living.isValid() || living.isDead() || living.isInvulnerable()
					|| living.getLocation().distanceSquared(center.getLocation()) > SPREAD_RADIUS * SPREAD_RADIUS) {
				continue;
			}
			if (living instanceof Player player && (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR)) {
				continue;
			}
			foes.add(living);
		}
		return foes;
	}

	private static void mark(LivingEntity entity) {
		Mark mark = MARKS.computeIfAbsent(entity.getUniqueId(), id -> new Mark(entity));
		mark.expiresAt = CaduceusListener.now() + DURATION_TICKS;
		if (mark.sign == null || !mark.sign.isValid()) {
			mark.sign = spawnSign(entity);
		}
	}

	private static ItemDisplay spawnSign(LivingEntity entity) {
		ItemStack icon = ItemStack.of(Material.PAPER);
		icon.editMeta(meta -> meta.setItemModel(new NamespacedKey(Items.NAMESPACE, "prescript_mark")));
		ItemDisplay sign = entity.getWorld().spawn(entity.getLocation().add(0, entity.getHeight() + 0.6, 0), ItemDisplay.class, display -> {
			display.setPersistent(false);
			display.setItemStack(icon);
			display.setBillboard(Display.Billboard.CENTER);
			display.setBrightness(new Display.Brightness(15, 15));
			display.setTransformation(new Transformation(new Vector3f(0, 0.55F, 0), new AxisAngle4f(),
					new Vector3f(0.7F, 0.7F, 0.7F), new AxisAngle4f()));
		});
		// Riding the marked foe keeps the sign over its head as it moves.
		if (!entity.addPassenger(sign)) {
			sign.setTeleportDuration(2);
		}
		return sign;
	}

	/** Called every tick: expires marks, keeps unmounted signs in place and shows motes. */
	static void tick() {
		long now = CaduceusListener.now();
		Iterator<Mark> it = MARKS.values().iterator();
		while (it.hasNext()) {
			Mark mark = it.next();
			LivingEntity entity = mark.entity;
			if (now >= mark.expiresAt || !entity.isValid() || entity.isDead()) {
				removeSign(mark);
				it.remove();
				continue;
			}
			if (mark.sign != null && mark.sign.isValid() && mark.sign.getVehicle() == null) {
				mark.sign.teleport(entity.getLocation().add(0, entity.getHeight() + 0.6, 0));
			}
			if (now % 10 == 0) {
				Location top = entity.getLocation().add(0, entity.getHeight() + 0.7, 0);
				entity.getWorld().spawnParticle(Particle.DUST, top, 3, 0.2, 0.1, 0.2, 0.0, MOTE);
			}
		}
	}

	private static void removeSign(Mark mark) {
		if (mark.sign != null) {
			mark.sign.remove();
			mark.sign = null;
		}
	}

	static void clearAll() {
		MARKS.values().forEach(IndexMark::removeSign);
		MARKS.clear();
	}
}
