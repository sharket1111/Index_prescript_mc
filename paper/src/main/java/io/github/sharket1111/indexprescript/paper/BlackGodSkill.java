package io.github.sharket1111.indexprescript.paper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Equippable;
import io.papermc.paper.entity.TeleportFlag;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

/**
 * The Black God's great skills (fan-made, after Limbus Company; see
 * docs/black_god.md), each a filmed sequence after the in-game animation.
 * The Black God stays where they are, robe and halo, and strikes with light:
 *
 * - 시지프 시스템을 진짜 망가트리려고? — five coins over the target and up to six
 *   foes near it: pale crescents sweep across them, golden bursts and pillars
 *   of white light; the fifth falls on the main target alone.
 * - 강제 퇴장 — golden panels close round the Black God, diagonal beams of
 *   gold rain on the target, a disc of light opens over it and a pillar comes
 *   down: fixed damage of 200% of its max health.
 * - Shift+Delete — rays fan out from the halo, light streaks past the
 *   target, two discs and pillars (50% of max health each) and then the
 *   whole screen burns white (200%).
 *
 * The Black God watches from a camera (Bedrock players from where they stand);
 * the targets are held in place. Each has a cooldown, since the original's
 * costs (Authority) are not in yet.
 */
final class BlackGodSkill {
	enum Kind {
		SIGH(0, 96, 5), CLOCK_OUT(1, 66, 6), JUDGMENT(2, 216, 8), ATONE(4, 245, 8), PUNISH(5, 225, 8),
		SISYPHUS(6, 233, 30), FORCED_EXIT(7, 260, 60), SHIFT_DELETE(8, 340, 60);

		/** The great skills: the big skill screen with the TOTAL count. */
		boolean great() {
			return this == SISYPHUS || this == FORCED_EXIT || this == SHIFT_DELETE;
		}

		final int slot;
		final int length;
		final int cooldownSeconds;

		Kind(int slot, int length, int cooldownSeconds) {
			this.slot = slot;
			this.length = length;
			this.cooldownSeconds = cooldownSeconds;
		}

		static Kind ofSlot(int slot) {
			for (Kind kind : values()) {
				if (kind.slot == slot) {
					return kind;
				}
			}
			return null;
		}
	}

	private static final double RANGE = 24.0;
	private static final double SPREAD = 10.0;
	private static final int MAX_TARGETS = 7;
	private static final String FLOODGATE_PREFIX = "00000000-0000-0000-";
	private static final Map<UUID, BlackGodSkill> ACTIVE = new ConcurrentHashMap<>();
	private static final Map<String, Long> READY_AT = new ConcurrentHashMap<>();
	private static final Particle.DustOptions GOLD = new Particle.DustOptions(Color.fromRGB(0xF0C860), 1.5F);
	private static final Particle.DustOptions PALE = new Particle.DustOptions(Color.fromRGB(0xD8F6FF), 1.6F);
	private static final Particle.DustOptions WHITE = new Particle.DustOptions(Color.fromRGB(0xFFFFFF), 2.4F);
	private static final ItemStack FLASH = overlay("misc/white_flash");

	private final Player god;
	private final Kind kind;
	private LivingEntity target;
	private final Location spot;
	private final Map<LivingEntity, Location> held = new LinkedHashMap<>();
	private final Map<LivingEntity, Boolean> hadAi = new LinkedHashMap<>();
	private final List<Entity> props = new ArrayList<>();
	private ItemDisplay camera;
	private ItemDisplay halo;
	private GameMode mode;
	private int tick;
	/** This use's own damage bonus (Authority or Preservation spent on use). */
	private double bonus = 1.0;

	private BlackGodSkill(Player god, Kind kind, LivingEntity target, List<LivingEntity> foes) {
		this.god = god;
		this.kind = kind;
		this.target = target;
		this.spot = god.getLocation();
		this.spot.setDirection(target.getLocation().toVector().subtract(spot.toVector()).setY(0));
		this.spot.setPitch(0);
		for (LivingEntity foe : foes) {
			held.put(foe, foe.getLocation());
		}
	}

	private static ItemStack overlay(String texture) {
		ItemStack stack = ItemStack.of(Material.PAPER);
		stack.setData(DataComponentTypes.EQUIPPABLE, Equippable.equippable(EquipmentSlot.HEAD)
				.cameraOverlay(Key.key(Items.NAMESPACE, texture)).build());
		return stack;
	}

	static boolean active(Player player) {
		return ACTIVE.containsKey(player.getUniqueId());
	}

	/** Uses the great skill in hotbar {@code slot} (0-8) if it is one; returns false for the others. */
	static boolean use(Player god, int slot) {
		if (slot == GUARD_SLOT) {
			guard(god);
			return true;
		}
		Kind kind = Kind.ofSlot(slot);
		if (kind == null) {
			return false;
		}
		if (ACTIVE.containsKey(god.getUniqueId()) || BlackGodRevive.reviving(god) || Clash.blocked(god)) {
			return true;
		}
		String key = god.getUniqueId() + ":" + kind;
		long now = CaduceusListener.now();
		long ready = READY_AT.getOrDefault(key, 0L);
		if (now < ready) {
			Hud.message(god, Component.text(Messages.get(god, "blackgod.cooldown", (ready - now + 19) / 20), NamedTextColor.GRAY));
			return true;
		}
		if (kind.great() && !BlackGodState.authorityForm(god)) {
			// '심판의 날': the great skills come only with the Black God's Authority.
			Hud.message(god, Component.text(Messages.get(god, "blackgod.need_authority"), TextColor.color(0xF0C860)));
			return true;
		}
		// Answering a skill aimed at the Black God: the clash is with its user.
		LivingEntity aimed = Clash.answering(god) != null ? Clash.answering(god) : lookedAt(god);
		if (aimed == null) {
			Hud.message(god, Component.text(Messages.get(god, "renoir.no_target"), NamedTextColor.GRAY));
			return true;
		}
		READY_AT.put(key, now + kind.cooldownSeconds * 20L);
		Clash.request(god, aimed, cardOf(god, kind), outcome -> launch(god, kind, outcome.target()));
		return true;
	}

	/** The skill's card; with the Authority every coin but an execution coin is unbreakable. */
	private static SkillCard cardOf(Player god, Kind kind) {
		SkillCard card = SkillCard.BLACK_GOD[kind.slot];
		return BlackGodState.authorityForm(god) ? card.unbreakable() : card;
	}

	private static void launch(Player god, Kind kind, LivingEntity target) {
		if (ACTIVE.containsKey(god.getUniqueId()) || !god.isOnline() || !BlackGod.active(god) || BlackGodRevive.reviving(god)) {
			return;
		}
		List<LivingEntity> foes = new ArrayList<>();
		foes.add(target);
		if (kind == Kind.SISYPHUS) {
			List<LivingEntity> near = new ArrayList<>();
			for (Entity entity : target.getNearbyEntities(SPREAD, SPREAD, SPREAD)) {
				if (entity != target && RenoirHammer.foe(god, entity)) {
					near.add((LivingEntity) entity);
				}
			}
			near.sort(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(target.getLocation())));
			foes.addAll(near.subList(0, Math.min(MAX_TARGETS - 1, near.size())));
		}
		BlackGodSkill skill = new BlackGodSkill(god, kind, target, foes);
		ACTIVE.put(god.getUniqueId(), skill);
		skill.open();
	}


	private static LivingEntity lookedAt(Player god) {
		if (god.getTargetEntity((int) RANGE) instanceof LivingEntity living && RenoirHammer.foe(god, living)) {
			return living;
		}
		Vector eye = god.getEyeLocation().toVector();
		Vector look = god.getEyeLocation().getDirection();
		LivingEntity best = null;
		double bestDot = 0.95;
		for (Entity entity : god.getNearbyEntities(RANGE, RANGE, RANGE)) {
			if (!RenoirHammer.foe(god, entity)) {
				continue;
			}
			LivingEntity living = (LivingEntity) entity;
			Vector to = living.getLocation().add(0, living.getHeight() * 0.5, 0).toVector().subtract(eye);
			double dot = to.clone().normalize().dot(look);
			if (to.length() <= RANGE && dot > bestDot && god.hasLineOfSight(living)) {
				best = living;
				bestDot = dot;
			}
		}
		return best;
	}

	static void tickAll() {
		ACTIVE.values().removeIf(skill -> {
			try {
				return !skill.tick();
			} catch (RuntimeException e) {
				IndexPrescriptPlugin.instance().getLogger().log(Level.WARNING, "Black God skill failed", e);
				skill.finish();
				return true;
			}
		});
	}

	static void endAll() {
		ACTIVE.values().forEach(BlackGodSkill::finish);
		ACTIVE.clear();
	}

	// --- staging ---------------------------------------------------------------------

	private boolean bedrock() {
		return god.getUniqueId().toString().startsWith(FLOODGATE_PREFIX);
	}

	private Vector facing() {
		return spot.getDirection().setY(0).normalize();
	}

	private static ItemStack model(String name) {
		ItemStack stack = ItemStack.of(Material.PAPER);
		stack.editMeta(meta -> meta.setItemModel(new NamespacedKey(Items.NAMESPACE, name)));
		return stack;
	}

	private ItemDisplay prop(Location at, String model, float scale, boolean glow) {
		ItemDisplay display = at.getWorld().spawn(at, ItemDisplay.class, d -> {
			d.setPersistent(false);
			d.setItemStack(model(model));
			d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
			d.setInterpolationDuration(3);
			if (glow) {
				d.setBrightness(new Display.Brightness(15, 15));
			}
			d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(scale, scale, scale), new Quaternionf()));
		});
		props.add(display);
		return display;
	}

	/** What each skill does on use (docs/black_god.md): Preservation, Poise, Authority. */
	private void onUse() {
		long now = CaduceusListener.now();
		switch (kind) {
			case SIGH -> {
				RenoirHammer.addPreservation(god, 3);
				CombatState.of(god).addPoise(2, 1, now);
			}
			case CLOCK_OUT -> {
				RenoirHammer.addPreservation(god, 5);
				CombatState.of(god).addPoise(2, 2, now);
			}
			case JUDGMENT -> {
				if (RenoirHammer.preservation(god) > 0) {
					RenoirHammer.spendPreservation(god, RenoirHammer.preservation(god));
					bonus += 0.15;
				}
			}
			case ATONE -> {
				if (BlackGodState.spendAuthority(god, 15) > 0) {
					bonus += 0.15;
				}
			}
			case PUNISH -> {
				if (BlackGodState.spendAuthority(god, 15) > 0) {
					bonus += 0.30;
				}
				RenoirHammer.addPreservation(god, 6);
			}
			case SISYPHUS -> {
				if (BlackGodState.spendAuthority(god, 40) > 0) {
					bonus += 0.50;
				}
				bonus += BlackGodState.authority(god) / 100.0;
				BlackGodState.spendAuthority(god, BlackGodState.MAX);
				if (RenoirHammer.preservation(god) > 0) {
					RenoirHammer.spendPreservation(god, RenoirHammer.preservation(god));
					bonus += 0.30;
				}
			}
			case FORCED_EXIT, SHIFT_DELETE -> BlackGodState.spendAuthority(god, BlackGodState.MAX);
		}
	}

	private void open() {
		onUse();
		for (LivingEntity foe : held.keySet()) {
			if (!(foe instanceof Player)) {
				hadAi.put(foe, foe.hasAI());
				foe.setAI(false);
			}
		}
		if (!bedrock()) {
			// The Black God watches from a camera; their robe and halo stay behind as a stand-in.
			mode = god.getGameMode();
			Cinematic.saveMode(god);
			god.setGameMode(GameMode.SPECTATOR);
			camera = spot.getWorld().spawn(spot.clone().add(0, 2, 0), ItemDisplay.class, d -> {
				d.setPersistent(false);
				d.setTeleportDuration(2);
			});
			prop(spot.clone().add(0, 0.5 * 1.05, 0), "black_god_robe", 1.05F, false);
			Location head = spot.clone().add(0, 1.5, 0).subtract(facing().multiply(0.42));
			head.setDirection(facing());
			halo = prop(head, "black_god_halo", 1.9F, true);
		}
		// The screen-edge skill UI: big with the TOTAL count for the great skills, small for the rest.
		if (kind.great()) {
			TotalDamage.start(god);
		}
		SkillOverlay.show(god, kind.great() ? SkillOverlay.Size.BIG : SkillOverlay.Size.SMALL);
		announce(god, kind.slot);
		World world = spot.getWorld();
		world.playSound(spot, Sound.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1.5F, 0.5F);
		world.playSound(spot, Sound.BLOCK_BELL_RESONATE, SoundCategory.PLAYERS, 1.5F, 0.6F);
	}

	private boolean tick() {
		if (!god.isOnline()) {
			finish();
			return false;
		}
		held.keySet().removeIf(foe -> !foe.isValid() || foe.isDead());
		if (!target.isValid() || target.isDead()) {
			if (held.isEmpty() || kind != Kind.SISYPHUS) {
				// The target is gone: let the scene play out a moment, then end.
				if (tick < kind.length - 20) {
					tick = kind.length - 20;
				}
			} else {
				target = held.keySet().iterator().next();
			}
		}
		hold();
		if (camera != null && tick >= 2 && god.getSpectatorTarget() != camera) {
			god.setSpectatorTarget(camera);
		}
		switch (kind) {
			case SISYPHUS -> sisyphus();
			case FORCED_EXIT -> forcedExit();
			case SHIFT_DELETE -> shiftDelete();
			case SIGH -> sigh();
			case CLOCK_OUT -> clockOut();
			case JUDGMENT -> judgment();
			case ATONE -> atone();
			case PUNISH -> punish();
		}
		if (tick >= kind.length) {
			finish();
			return false;
		}
		tick++;
		return true;
	}

	private void hold() {
		for (Map.Entry<LivingEntity, Location> entry : held.entrySet()) {
			LivingEntity foe = entry.getKey();
			Location here = foe.getLocation();
			if (here.distanceSquared(entry.getValue()) > 0.01) {
				Location back = entry.getValue().clone();
				back.setYaw(here.getYaw());
				back.setPitch(here.getPitch());
				foe.teleport(back, TeleportFlag.EntityState.RETAIN_PASSENGERS);
			}
			foe.setVelocity(new Vector());
		}
	}

	/** Points the camera from {@code from} at {@code look}, shaken by {@code shake}. */
	private void frame(Vector from, Vector look, double shake) {
		if (camera == null) {
			return;
		}
		Vector at = from.clone();
		if (shake > 0) {
			ThreadLocalRandom r = ThreadLocalRandom.current();
			at.add(new Vector((r.nextDouble() - 0.5) * shake, (r.nextDouble() - 0.5) * shake, (r.nextDouble() - 0.5) * shake));
		}
		Location location = at.toLocation(spot.getWorld());
		location.setDirection(look.clone().subtract(at));
		camera.teleport(location);
	}

	private Vector godAt(double up) {
		return spot.toVector().add(new Vector(0, up, 0));
	}

	private Vector targetAt(double up) {
		return target.getLocation().toVector().add(new Vector(0, up, 0));
	}

	private Vector side() {
		Vector f = facing();
		return new Vector(-f.getZ(), 0, f.getX());
	}

	/** In front of the Black God, {@code distance} out, at {@code height}, looking at them. */
	private void onGod(double distance, double height, double look, double shake) {
		frame(godAt(height).add(facing().multiply(distance)), godAt(look), shake);
	}

	/** Beside the target, {@code distance} out, looking at it. */
	private void onTarget(double distance, double height, double shake) {
		Vector toGod = spot.toVector().subtract(target.getLocation().toVector()).setY(0);
		Vector away = toGod.lengthSquared() < 1.0E-4 ? new Vector(1, 0, 0) : toGod.normalize();
		Vector across = new Vector(-away.getZ(), 0, away.getX());
		Vector from = targetAt(height).add(across.multiply(distance * 0.8)).add(away.multiply(distance * 0.6));
		frame(from, targetAt(1.0), shake);
	}

	/** Wide, over the Black God's shoulder towards the target. */
	private void wide(double back, double height, double shake) {
		Vector mid = spot.toVector().midpoint(target.getLocation().toVector());
		Vector from = godAt(height).subtract(facing().multiply(back)).add(side().multiply(back * 0.5));
		frame(from, mid.add(new Vector(0, 1.0, 0)), shake);
	}

	private void flareHalo(float size) {
		if (halo != null && halo.isValid()) {
			halo.setInterpolationDelay(0);
			halo.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotateZ(tick * 0.01F),
					new Vector3f(size, size, size), new Quaternionf()));
		}
	}

	// The timings below (in ticks, 20 a second) are measured from the in-game scenes: the
	// bright frames of each video mark where its blows land.

	/** Whether {@code tick} is within {@code length} ticks after any of {@code at}. */
	private boolean near(int[] at, int length) {
		for (int t : at) {
			if (tick >= t && tick < t + length) {
				return true;
			}
		}
		return false;
	}

	private Location targetMid() {
		return target.getLocation().add(0, target.getHeight() * 0.6, 0);
	}

	// --- 시지프 시스템을 진짜 망가트리려고? (11.7 s) -------------------------------------

	private static final int[] SISYPHUS_COINS = {44, 71, 100, 131};
	private static final int SISYPHUS_FLOURISH = 156;
	private static final int SISYPHUS_LAST = 182;

	private void sisyphus() {
		double shake = near(SISYPHUS_COINS, 6) || near(new int[] {SISYPHUS_FLOURISH, SISYPHUS_LAST}, 8) ? 0.35 : 0.0;
		if (tick < 27) {
			onGod(4.2, 1.7, 1.6, 0.0);
			flareHalo(1.9F + tick * 0.04F);
			rays(spot.clone().add(0, 1.6, 0), 2);
		} else if (tick < 60) {
			wide(5.0, 3.5, shake);
		} else if (tick < 73) {
			onGod(2.0, 1.1, 1.1, shake);
		} else if (tick < 120) {
			wide(6.0, 4.5, shake);
		} else if (tick < 140) {
			onGod(3.4, 1.8, 1.8, shake);
		} else if (tick < 150) {
			wide(6.5, 4.0, shake);
		} else if (tick < 196) {
			onTarget(4.5, 2.2, shake);
		} else {
			wide(9.0, 6.0, 0.0);
		}
		for (int i = 0; i < SISYPHUS_COINS.length; i++) {
			int at = SISYPHUS_COINS[i];
			if (tick == at - 4 && !Clash.destroyed(god, i)) {
				Location center = centroid();
				crescent(center, 3.5 + held.size() * 0.4, i % 2 == 0 ? -1 : 1, PALE);
				spot.getWorld().playSound(center, Sound.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.5F, 0.5F);
				spot.getWorld().playSound(center, Sound.ITEM_TRIDENT_RIPTIDE_3, SoundCategory.PLAYERS, 1.0F, 1.4F);
			}
			if (tick == at && coin(i)) {
				for (LivingEntity foe : List.copyOf(held.keySet())) {
					starburst(foe.getLocation().add(0, foe.getHeight() * 0.6, 0));
					if (i % 2 == 1) {
						pillar(foe.getLocation(), 7, false);
					}
					hit(foe, 4.0);
					if (i == 3) {
						// Coin IV: sets off every ~ in the foe.
						BlackGodState.trigger(god, foe);
					}
				}
			}
		}
		if (tick == SISYPHUS_FLOURISH - 4) {
			crescent(centroid(), 4.0 + held.size() * 0.4, 1, GOLD_WHITE);
		}
		if (tick == SISYPHUS_FLOURISH) {
			for (LivingEntity foe : held.keySet()) {
				starburst(foe.getLocation().add(0, foe.getHeight() * 0.6, 0));
			}
			whiteBurst(target.getLocation(), 0.8);
		}
		if (tick == SISYPHUS_LAST && coin(4)) {
			// Coin V, on the main target alone: a great column of white light.
			pillar(target.getLocation(), 10, true);
			whiteBurst(target.getLocation(), 1.6);
			hit(target, 9.0);
			BlackGodState.inflict(god, target, BlackGodState.Nail.EXCEPTION, 9);
		}
		if (tick > SISYPHUS_LAST && tick < 216 && tick % 3 == 0) {
			sparkle(target.getLocation(), 2.5);
		}
	}

	private Location centroid() {
		Vector sum = new Vector();
		for (LivingEntity foe : held.keySet()) {
			sum.add(foe.getLocation().toVector());
		}
		return sum.multiply(1.0 / Math.max(1, held.size())).toLocation(spot.getWorld()).add(0, 1.0, 0);
	}

	// --- 강제 퇴장 (13 s) ------------------------------------------------------------------

	private static final int EXIT_CUT = 112;
	private static final int EXIT_DISC = 200;
	private static final int EXIT_HIT = 218;

	private void forcedExit() {
		if (tick < EXIT_CUT) {
			onGod(3.2 - tick * 0.007, 1.7, 1.6, 0.0);
			flareHalo(1.9F + tick * 0.012F);
			if (tick % 6 == 0) {
				panels(tick / 6);
			}
			if (tick == 0) {
				spot.getWorld().playSound(spot, Sound.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 1.5F, 0.6F);
			}
		} else if (tick < EXIT_HIT + 4) {
			onTarget(4.0, 1.6, tick >= EXIT_HIT ? 0.5 : 0.0);
		} else {
			onTarget(6.5 + (tick - EXIT_HIT) * 0.05, 3.0, tick < EXIT_HIT + 10 ? 0.3 : 0.0);
		}
		if (tick == EXIT_CUT + 4) {
			Effects.staggered(target);
			spot.getWorld().playSound(target.getLocation(), Sound.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 1.2F, 0.5F);
		}
		if (tick >= 140 && tick < EXIT_DISC && tick % 2 == 0) {
			diagonalBeam(target.getLocation().add(0, 1.0, 0));
		}
		if (tick == EXIT_DISC) {
			disc(target.getLocation().add(0, 6, 0), 3.5F);
		}
		if (tick == EXIT_HIT && coin(0)) {
			pillar(target.getLocation(), 6, true);
			whiteBurst(target.getLocation(), 1.2);
			fixed(target, maxHealth(target) * 2.0);
		}
		if (tick > EXIT_HIT && tick < 255 && tick % 3 == 0) {
			sparkle(target.getLocation(), 2.0);
		}
	}

	/** Golden panels of light (like an error window) closing round the Black God. */
	private void panels(int step) {
		World world = spot.getWorld();
		double radius = Math.max(1.0, 2.2 - step * 0.07);
		for (int k = 0; k < 4; k++) {
			double a = step * 0.35 + k * Math.PI / 2;
			Vector center = godAt(1.3).add(new Vector(Math.cos(a) * radius, 0, Math.sin(a) * radius));
			Vector tangent = new Vector(-Math.sin(a), 0, Math.cos(a));
			for (double u = -0.7; u <= 0.7; u += 0.14) {
				world.spawnParticle(Particle.DUST, center.clone().add(tangent.clone().multiply(u)).add(new Vector(0, 0.9, 0)).toLocation(world),
						1, 0, 0, 0, 0.0, GOLD);
				world.spawnParticle(Particle.DUST, center.clone().add(tangent.clone().multiply(u)).add(new Vector(0, -0.9, 0)).toLocation(world),
						1, 0, 0, 0, 0.0, GOLD);
			}
			for (double v = -0.9; v <= 0.9; v += 0.15) {
				for (double u : new double[] {-0.7, 0.7}) {
					world.spawnParticle(Particle.DUST, center.clone().add(tangent.clone().multiply(u)).add(new Vector(0, v, 0)).toLocation(world),
							1, 0, 0, 0, 0.0, GOLD);
				}
			}
		}
		world.playSound(spot, Sound.BLOCK_AMETHYST_BLOCK_HIT, SoundCategory.PLAYERS, 1.0F, 0.5F + step * 0.1F);
	}

	// --- Shift+Delete (17 s) ----------------------------------------------------------------

	private static final int[] DELETE_COINS = {190, 245};
	private static final int DELETE_LAST = 272;

	private void shiftDelete() {
		if (tick < 32) {
			onGod(5.0, 2.2, 1.8, 0.0);
			rays(spot.clone().add(0, 1.6, 0), 3);
		} else if (tick < 112) {
			onGod(2.6 - (tick - 32) * 0.01, 1.7, 1.7, 0.0);
			flareHalo(1.9F + (tick - 32) * 0.019F);
			if (tick == 32) {
				spot.getWorld().playSound(spot, Sound.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 1.5F, 0.5F);
			}
			if (tick % 5 == 0) {
				rays(spot.clone().add(0, 1.6, 0), 2);
			}
		} else if (tick < 318) {
			boolean impact = near(DELETE_COINS, 4) || tick >= DELETE_LAST && tick < DELETE_LAST + 12;
			onTarget(4.2, 1.5, impact ? 0.5 : 0.0);
		} else {
			wide(8.0, 4.0, 0.0);
		}
		if (tick >= 120 && tick < 176 && tick % 4 == 0) {
			horizontalStreak(target.getLocation().add(0, 1.0 + (ThreadLocalRandom.current().nextDouble() - 0.5), 0));
		}
		for (int k = 0; k < DELETE_COINS.length; k++) {
			int at = DELETE_COINS[k];
			if (tick == at - 8 && !Clash.destroyed(god, k)) {
				disc(target.getLocation().add(0, 6, 0), 3.0F);
			}
			if (tick == at && coin(k)) {
				pillar(target.getLocation(), 6, true);
				whiteBurst(target.getLocation(), 1.0);
				fixed(target, maxHealth(target) * 0.5);
			}
		}
		if (tick == DELETE_LAST - 10) {
			disc(target.getLocation().add(0, 7, 0), 5.0F);
		}
		if (tick == DELETE_LAST && coin(2)) {
			// The whole screen burns white, for over two seconds.
			overlay(FLASH);
			pillar(target.getLocation(), 9, true);
			whiteBurst(target.getLocation(), 2.5);
			spot.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, target.getLocation().add(2, 1, 0), 1);
			spot.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, target.getLocation().add(-2, 1, 0), 1);
			fixed(target, maxHealth(target) * 2.0);
		}
		if (tick > DELETE_LAST && tick < 318 && tick % 10 == 0) {
			// Keep the white overlay up (the skill screen re-sends its own now and then).
			overlay(FLASH);
		}
		if (tick == 318) {
			clearOverlay();
		}
		if (tick > DELETE_LAST && tick < kind.length - 6 && tick % 3 == 0) {
			sparkle(target.getLocation(), 3.0);
		}
	}

	// --- 하아…이제 좀 돌아가 주지? (4.8 s) ----------------------------------------------

	private static final int[] SIGH_COINS = {17, 66};

	/** A white-gold crescent, a close look at the robe, then a pale crescent; each blow bursts on the target. */
	private void sigh() {
		double shake = near(SIGH_COINS, 4) ? 0.35 : 0.0;
		if (tick < 12) {
			onGod(3.0, 1.7, 1.6, 0.0);
			flareHalo(1.9F + tick * 0.04F);
		} else if (tick < 40) {
			wide(5.5, 3.2, shake);
		} else if (tick < 52) {
			onGod(2.0, 1.1, 1.1, 0.0);
		} else if (tick < 76) {
			onTarget(4.0, 1.6, shake);
		} else {
			wide(7.5, 4.5, 0.0);
		}
		if (tick == SIGH_COINS[0] - 3) {
			crescent(targetMid(), 2.4, 1, GOLD_WHITE);
			swoosh();
		}
		if (tick == 45) {
			rays(spot.clone().add(0, 1.6, 0), 4);
		}
		if (tick == SIGH_COINS[1] - 4) {
			crescent(targetMid(), 2.6, -1, PALE);
			swoosh();
		}
		for (int k = 0; k < SIGH_COINS.length; k++) {
			if (tick == SIGH_COINS[k] && coin(k)) {
				starburst(targetMid());
				hit(target, 4.0);
			}
		}
	}

	// --- 퇴근 좀 하자고. (3.3 s) ------------------------------------------------------------

	private static final int[] CLOCK_COINS = {30, 50};

	/** A pale crescent and a great diagonal beam, then a clock face whose hand sweeps round. */
	private void clockOut() {
		double shake = near(CLOCK_COINS, 4) ? 0.35 : 0.0;
		if (tick < 14) {
			onGod(3.0, 1.7, 1.6, 0.0);
			flareHalo(1.9F + tick * 0.04F);
		} else if (tick < 36) {
			wide(5.5, 3.2, shake);
		} else if (tick < 58) {
			onTarget(4.0, 1.6, shake);
		} else {
			wide(7.5, 4.5, 0.0);
		}
		if (tick == 24) {
			crescent(targetMid(), 2.8, 1, PALE);
			swoosh();
		}
		if (tick == CLOCK_COINS[0] && coin(0)) {
			beamSlash(targetMid());
			starburst(targetMid());
			hit(target, 5.0);
			BlackGodState.inflict(god, target, BlackGodState.Nail.CONSOLE, 1);
		}
		if (tick >= 36 && tick <= 54) {
			clockFace(targetMid(), 2.2, (tick - 36) / 16.0);
		}
		if (tick == 36) {
			spot.getWorld().playSound(target.getLocation(), Sound.BLOCK_BELL_RESONATE, SoundCategory.PLAYERS, 1.2F, 1.4F);
		}
		if (tick == CLOCK_COINS[1] && coin(1)) {
			starburst(targetMid());
			spot.getWorld().playSound(target.getLocation(), Sound.BLOCK_BELL_USE, SoundCategory.PLAYERS, 1.5F, 0.6F);
			hit(target, 6.0);
			BlackGodState.inflict(god, target, BlackGodState.Nail.CONSOLE, 2);
		}
	}

	// --- '심판' (10.8 s) -------------------------------------------------------------------

	private static final int[] JUDGMENT_COINS = {85, 100, 176};

	/**
	 * A crescent as it opens, the Black God spreading its arms (close, from
	 * below), a crescent and a burst, a white flash, a close look at the robe,
	 * then a red glitch pillar and a great golden nail, dealing more the more
	 * health the Black God has lost (up to double).
	 */
	private void judgment() {
		double shake = near(JUDGMENT_COINS, 5) ? 0.35 : 0.0;
		if (tick < 40) {
			onGod(3.2, 1.7, 1.6, 0.0);
		} else if (tick < 64) {
			onGod(2.4, 0.6, 1.9, 0.0);
			flareHalo(1.9F + (tick - 40) * 0.05F);
			if (tick % 3 == 0) {
				rays(spot.clone().add(0, 1.6, 0), 3);
			}
		} else if (tick < 116) {
			wide(5.5, 3.2, shake);
		} else if (tick < 150) {
			onGod(2.0, 1.1, 1.1, 0.0);
		} else if (tick < 186) {
			onTarget(4.0, 1.6, shake);
		} else {
			wide(7.5, 4.5, 0.0);
		}
		if (tick == 16) {
			crescent(targetMid(), 2.6, -1, PALE);
			swoosh();
		}
		if (tick == JUDGMENT_COINS[0] - 5) {
			crescent(targetMid(), 2.8, 1, PALE);
			swoosh();
		}
		if (tick == JUDGMENT_COINS[0] && coin(0)) {
			starburst(targetMid());
			hit(target, 4.0);
		}
		if (tick == JUDGMENT_COINS[1] && coin(1)) {
			whiteBurst(target.getLocation(), 0.8);
			hit(target, 4.0);
		}
		if (tick == 104) {
			goldenNail(target.getLocation());
		}
		if (tick >= 158 && tick < 172) {
			glitchPillar(target.getLocation());
		}
		if (tick == JUDGMENT_COINS[2] && coin(2)) {
			goldenNail(target.getLocation());
			double lost = 1.0 - god.getHealth() / maxHealth(god);
			hit(target, 7.0 * (1.0 + Math.max(0.0, Math.min(1.0, lost))));
			BlackGodState.inflict(god, target, BlackGodState.Nail.BREAKPOINT, 4);
		}
	}

	// --- '속죄하라' (12.3 s) ---------------------------------------------------------------

	private static final int[] ATONE_COINS = {47, 109, 218};

	/** The halo blazing, a pillar of golden light, a great beam, the halo again, then a white tremor burst. */
	private void atone() {
		double shake = near(ATONE_COINS, 5) ? 0.35 : 0.0;
		if (tick < 16) {
			onGod(2.6, 1.7, 1.7, 0.0);
		} else if (tick < 40) {
			onGod(2.2, 1.8, 1.8, 0.0);
			flareHalo(1.9F + (tick - 16) * 0.045F);
		} else if (tick < 150) {
			wide(5.5, 3.2, shake);
		} else if (tick < 176) {
			onGod(2.4, 1.8, 1.8, 0.0);
			flareHalo(1.9F + (tick - 150) * 0.04F);
		} else if (tick < 228) {
			onTarget(4.0, 1.6, shake);
		} else {
			wide(7.5, 4.5, 0.0);
		}
		if (tick == ATONE_COINS[0] && coin(0)) {
			goldPillar(target.getLocation());
			starburst(targetMid());
			hit(target, 4.0);
			BlackGodState.inflict(god, target, BlackGodState.Nail.CONSOLE, 1);
		}
		if (tick == 80 || tick == 210) {
			crescent(targetMid(), 2.6, tick == 80 ? -1 : 1, PALE);
			swoosh();
		}
		if (tick == 98) {
			whiteBurst(target.getLocation(), 0.6);
		}
		if (tick == ATONE_COINS[1] && coin(1)) {
			beamSlash(targetMid());
			starburst(targetMid());
			hit(target, 4.0);
			BlackGodState.inflict(god, target, BlackGodState.Nail.CONSOLE, 1);
		}
		if (tick == ATONE_COINS[2] && coin(2)) {
			overlay(FLASH);
			whiteBurst(target.getLocation(), 1.4);
			spot.getWorld().playSound(target.getLocation(), Sound.ITEM_MACE_SMASH_GROUND_HEAVY, SoundCategory.PLAYERS, 1.4F, 0.6F);
			hit(target, 6.0);
			BlackGodState.inflict(god, target, BlackGodState.Nail.CONSOLE, 1);
		}
		if (tick == ATONE_COINS[2] + 8) {
			clearOverlay();
		}
	}

	// --- '형벌받으라' (11.3 s) -------------------------------------------------------------

	private static final int[] PUNISH_COINS = {66, 110, 154};
	private static final int PUNISH_FLOURISH = 190;
	private ItemDisplay greatHalo;

	/** A great halo blazes up over the field, a white flash, a great beam, the Black God close, then another beam and a burst. */
	private void punish() {
		double shake = near(PUNISH_COINS, 5) || tick >= PUNISH_FLOURISH && tick < PUNISH_FLOURISH + 8 ? 0.35 : 0.0;
		if (tick < 20) {
			onGod(2.6, 1.7, 1.7, 0.0);
		} else if (tick < 106) {
			wide(8.0, 4.0, shake);
		} else if (tick < 122) {
			onTarget(4.0, 1.6, shake);
		} else if (tick < 140) {
			onGod(2.4, 1.7, 1.7, 0.0);
		} else if (tick < 205) {
			onTarget(4.2, 1.8, shake);
		} else {
			wide(8.0, 4.5, 0.0);
		}
		if (tick == 20) {
			greatHalo = prop(spot.clone().add(0, 7, 0), "black_god_halo", 0.5F, true);
			greatHalo.setBillboard(Display.Billboard.CENTER);
			greatHalo.setInterpolationDuration(40);
			greatHalo.setInterpolationDelay(1);
			greatHalo.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(9, 9, 9), new Quaternionf()));
			spot.getWorld().playSound(spot, Sound.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 1.5F, 0.5F);
		}
		if (greatHalo != null && tick % 4 == 0) {
			rays(greatHalo.getLocation(), 3);
		}
		if (tick == PUNISH_COINS[0] && coin(0)) {
			starburst(targetMid());
			pillar(target.getLocation(), 7, false);
			whiteBurst(target.getLocation(), 0.7);
			hit(target, 4.0);
			BlackGodState.inflict(god, target, BlackGodState.Nail.CONSOLE, 1);
		}
		if (tick == PUNISH_COINS[1] && coin(1)) {
			beamSlash(targetMid());
			starburst(targetMid());
			hit(target, 4.0);
			BlackGodState.inflict(god, target, BlackGodState.Nail.CONSOLE, 1);
		}
		if (tick == PUNISH_COINS[2] - 2) {
			beamSlash(targetMid());
		}
		if (tick == PUNISH_COINS[2] && coin(2)) {
			beamSlash(targetMid());
			whiteBurst(target.getLocation(), 1.2);
			hit(target, 6.0);
			BlackGodState.inflict(god, target, BlackGodState.Nail.CONSOLE, 1);
		}
		if (tick == PUNISH_FLOURISH) {
			whiteBurst(target.getLocation(), 1.6);
		}
		if (tick > PUNISH_FLOURISH && tick < 215 && tick % 3 == 0) {
			sparkle(target.getLocation(), 2.5);
		}
	}

	// --- '신 앞에 뉘우쳐라'던가? (guard) -------------------------------------------------

	private static final int GUARD_SLOT = 3;
	private static final int GUARD_TICKS = 60;
	private static final int GUARD_COOLDOWN = 8 * 20;
	private static final Map<UUID, Long> GUARD_UNTIL = new ConcurrentHashMap<>();

	/** Takes up the guard for 3 seconds: the next blow is turned away and answered with a pillar of light. */
	private static void guard(Player god) {
		String key = god.getUniqueId() + ":guard";
		long now = CaduceusListener.now();
		long ready = READY_AT.getOrDefault(key, 0L);
		if (now < ready) {
			Hud.message(god, Component.text(Messages.get(god, "blackgod.cooldown", (ready - now + 19) / 20), NamedTextColor.GRAY));
			return;
		}
		READY_AT.put(key, now + GUARD_COOLDOWN);
		GUARD_UNTIL.put(god.getUniqueId(), now + GUARD_TICKS);
		announce(god, GUARD_SLOT);
		BlackGodState.guardFill(god);
		Location at = god.getLocation();
		for (int i = 0; i < 24; i++) {
			double a = i * Math.PI / 12;
			at.getWorld().spawnParticle(Particle.DUST, at.clone().add(Math.cos(a) * 1.2, 1.0, Math.sin(a) * 1.2), 2, 0.02, 0.6, 0.02, 0.0, GOLD);
		}
		at.getWorld().playSound(at, Sound.ITEM_SHIELD_BLOCK, SoundCategory.PLAYERS, 1.2F, 0.6F);
		at.getWorld().playSound(at, Sound.BLOCK_BELL_RESONATE, SoundCategory.PLAYERS, 1.0F, 1.2F);
	}

	/** Struck while on guard: the blow is turned away and the striker repents before a pillar of light. Returns true if so. */
	static boolean guarded(Player god, Entity damager) {
		Long until = GUARD_UNTIL.get(god.getUniqueId());
		if (until == null || CaduceusListener.now() > until) {
			return false;
		}
		GUARD_UNTIL.remove(god.getUniqueId());
		LivingEntity attacker = damager instanceof org.bukkit.entity.Projectile projectile
				&& projectile.getShooter() instanceof LivingEntity shooter ? shooter
				: damager instanceof LivingEntity living ? living : null;
		god.getWorld().playSound(god.getLocation(), Sound.ITEM_SHIELD_BLOCK, SoundCategory.PLAYERS, 1.5F, 0.5F);
		if (attacker != null && attacker != god && attacker.isValid()) {
			pillar(attacker.getLocation(), 7, true);
			starburst(attacker.getLocation().add(0, attacker.getHeight() * 0.6, 0));
			Damage.deal(attacker, 8.0, god, true);
		}
		return true;
	}

	/** The skill's name under the crosshair, in its sin's colour. */
	private static void announce(Player god, int slot) {
		BlackGod.Skill skill = BlackGod.SKILLS.get(slot);
		god.showTitle(net.kyori.adventure.title.Title.title(Component.empty(), Component.text(skill.name(), skill.colour()),
				net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(100), java.time.Duration.ofMillis(1500),
						java.time.Duration.ofMillis(300))));
	}

	// --- more effects ---------------------------------------------------------------------

	private static final Particle.DustOptions GOLD_WHITE = new Particle.DustOptions(Color.fromRGB(0xFFF0C0), 1.6F);
	private static final Particle.DustOptions BLUE = new Particle.DustOptions(Color.fromRGB(0x7FB4FF), 1.4F);
	private static final Particle.DustOptions RED = new Particle.DustOptions(Color.fromRGB(0xD01818), 1.8F);

	private void swoosh() {
		spot.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.3F, 0.6F);
		spot.getWorld().playSound(target.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_2, SoundCategory.PLAYERS, 0.8F, 1.5F);
	}

	/** A great white beam slashing diagonally through {@code through}. */
	private void beamSlash(Location through) {
		World world = through.getWorld();
		ThreadLocalRandom r = ThreadLocalRandom.current();
		Vector across = side().multiply(r.nextBoolean() ? 1 : -1).add(new Vector(0, -0.9, 0)).normalize();
		for (double d = -5; d <= 5; d += 0.2) {
			Location p = through.clone().add(across.clone().multiply(d));
			world.spawnParticle(Particle.DUST, p, 3, 0.12, 0.12, 0.12, 0.0, WHITE);
			if (Math.abs(d) < 3) {
				world.spawnParticle(Particle.END_ROD, p, 1, 0.05, 0.05, 0.05, 0.02);
			}
		}
		world.playSound(through, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.PLAYERS, 1.2F, 1.2F);
		world.playSound(through, Sound.ITEM_TRIDENT_THROW, SoundCategory.PLAYERS, 1.0F, 0.6F);
	}

	/** A clock face round {@code center} facing the Black God; its hand at {@code turn} (0-1) of the way round. */
	private void clockFace(Location center, double radius, double turn) {
		World world = center.getWorld();
		Vector toGod = spot.toVector().subtract(center.toVector()).setY(0);
		Vector normal = toGod.lengthSquared() < 1.0E-4 ? facing() : toGod.normalize();
		Vector u = new Vector(-normal.getZ(), 0, normal.getX());
		Vector v = new Vector(0, 1, 0);
		for (int i = 0; i < 48; i++) {
			double a = i * Math.PI * 2 / 48;
			world.spawnParticle(Particle.DUST, center.clone().add(u.clone().multiply(Math.cos(a) * radius)).add(v.clone().multiply(Math.sin(a) * radius)),
					1, 0, 0, 0, 0.0, GOLD_WHITE);
		}
		for (int h = 0; h < 12; h++) {
			double a = h * Math.PI / 6;
			for (double d = radius * 0.8; d <= radius; d += 0.1) {
				world.spawnParticle(Particle.DUST, center.clone().add(u.clone().multiply(Math.cos(a) * d)).add(v.clone().multiply(Math.sin(a) * d)),
						1, 0, 0, 0, 0.0, GOLD);
			}
		}
		double hand = Math.PI / 2 - turn * Math.PI * 2;
		for (double d = 0; d <= radius * 0.9; d += 0.12) {
			world.spawnParticle(Particle.DUST, center.clone().add(u.clone().multiply(Math.cos(hand) * d)).add(v.clone().multiply(Math.sin(hand) * d)),
					1, 0, 0, 0, 0.0, BLUE);
		}
		if (((int) (turn * 16)) % 2 == 0) {
			world.playSound(center, Sound.BLOCK_NOTE_BLOCK_HAT, SoundCategory.PLAYERS, 0.8F, 1.6F);
		}
	}

	/** Red glitch blocks rising round {@code at}. */
	private static void glitchPillar(Location at) {
		World world = at.getWorld();
		ThreadLocalRandom r = ThreadLocalRandom.current();
		for (int i = 0; i < 10; i++) {
			Location p = at.clone().add(r.nextDouble(-0.7, 0.7), r.nextDouble(0, 4), r.nextDouble(-0.7, 0.7));
			world.spawnParticle(Particle.DUST, p, 4, 0.12, 0.05, 0.12, 0.0, RED);
		}
		if (r.nextInt(3) == 0) {
			world.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_BREAK, SoundCategory.PLAYERS, 0.8F, 0.5F);
		}
	}

	/** A great golden nail (a cross of light) standing over {@code at}. */
	private static void goldenNail(Location at) {
		World world = at.getWorld();
		for (double y = 0; y < 4.5; y += 0.15) {
			world.spawnParticle(Particle.DUST, at.clone().add(0, y, 0), 2, 0.06, 0.02, 0.06, 0.0, GOLD);
		}
		for (double x = -0.9; x <= 0.9; x += 0.15) {
			world.spawnParticle(Particle.DUST, at.clone().add(x, 3.4, 0), 2, 0.02, 0.06, 0.02, 0.0, GOLD);
			world.spawnParticle(Particle.DUST, at.clone().add(0, 3.4, x), 2, 0.02, 0.06, 0.02, 0.0, GOLD);
		}
		world.spawnParticle(Particle.END_ROD, at.clone().add(0, 1, 0), 30, 0.3, 0.6, 0.3, 0.2);
		world.playSound(at, Sound.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 1.0F, 0.6F);
		world.playSound(at, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, 0.8F, 1.5F);
	}

	/** A pillar of golden light rising from the ground at {@code at}. */
	private static void goldPillar(Location at) {
		World world = at.getWorld();
		for (double y = 0; y < 8; y += 0.25) {
			world.spawnParticle(Particle.DUST, at.clone().add(0, y, 0), 3, 0.3, 0.05, 0.3, 0.0, GOLD);
		}
		world.spawnParticle(Particle.END_ROD, at.clone().add(0, 0.5, 0), 20, 0.4, 2.0, 0.4, 0.05);
		world.playSound(at, Sound.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 1.2F, 1.4F);
	}

	// --- damage ----------------------------------------------------------------------

	private static double maxHealth(LivingEntity entity) {
		AttributeInstance max = entity.getAttribute(Attribute.MAX_HEALTH);
		return max == null ? 20.0 : max.getValue();
	}

	/** This skill reaches coin {@code index}: flipped now (Clash); false if it was destroyed in a clash. */
	private boolean coin(int index) {
		return Clash.coin(god, index);
	}

	private void hit(LivingEntity foe, double damage) {
		if (foe.isValid() && !foe.isDead()) {
			// Attack levels, System Preservation and the ~ in the foe; Pulse halves what it takes.
			double multiplier = bonus * BlackGodState.attackMultiplier(god, foe) * (BlackGodState.pulse(foe) ? 0.5 : 1.0);
			Damage.deal(foe, damage * multiplier, god, true);
		}
	}

	/** Fixed damage: past armour and resistance; enough to kill, kills (credited to the Black God). */
	private void fixed(LivingEntity foe, double amount) {
		if (!foe.isValid() || foe.isDead()) {
			return;
		}
		if (BlackGodState.pulse(foe)) {
			amount *= 0.5;
		}
		// The coin's flip scales even fixed damage.
		amount *= Clash.scale(god);
		double before = foe.getHealth() + foe.getAbsorptionAmount();
		if (amount >= before) {
			foe.setNoDamageTicks(0);
			foe.damage(Math.max(amount, before + 1), DamageSource.builder(DamageType.GENERIC_KILL)
					.withCausingEntity(god).withDirectEntity(god).build());
		} else {
			double absorbed = Math.min(foe.getAbsorptionAmount(), amount);
			foe.setAbsorptionAmount(foe.getAbsorptionAmount() - absorbed);
			foe.setHealth(Math.max(0.5, foe.getHealth() - (amount - absorbed)));
			foe.playHurtAnimation(0);
			foe.getWorld().playSound(foe.getLocation(), Sound.ENTITY_PLAYER_HURT, SoundCategory.PLAYERS, 1.0F, 0.8F);
		}
		double after = foe.isDead() ? 0.0 : foe.getHealth() + foe.getAbsorptionAmount();
		TotalDamage.add(god, before - after);
	}

	// --- effects ---------------------------------------------------------------------

	/** Light fanning out from the halo. */
	private void rays(Location from, int count) {
		World world = spot.getWorld();
		ThreadLocalRandom r = ThreadLocalRandom.current();
		for (int k = 0; k < count; k++) {
			Vector dir = side().multiply(r.nextDouble(-1, 1)).add(new Vector(0, r.nextDouble(0.1, 1), 0))
					.add(facing().multiply(-0.3)).normalize();
			for (double d = 0.6; d < 6; d += 0.3) {
				world.spawnParticle(Particle.DUST, from.clone().add(dir.clone().multiply(d)), 1, 0, 0, 0, 0.0, d > 4 ? PALE : GOLD);
			}
		}
	}

	/** A pale crescent sweeping across, round {@code center}. */
	private void crescent(Location center, double radius, int way, Particle.DustOptions dust) {
		World world = center.getWorld();
		Vector f = facing();
		Vector s = side();
		for (int i = 0; i <= 36; i++) {
			double a = Math.toRadians(-100 + i * 200.0 / 36) * way;
			for (double r = radius - 0.4; r <= radius + 0.4; r += 0.4) {
				Vector p = center.toVector().add(s.clone().multiply(Math.sin(a) * r)).add(f.clone().multiply(Math.cos(a) * r * 0.5))
						.add(new Vector(0, Math.sin(a * 0.5) * 0.4, 0));
				world.spawnParticle(Particle.DUST, p.toLocation(world), 1, 0.02, 0.02, 0.02, 0.0, dust);
			}
		}
		world.spawnParticle(Particle.SWEEP_ATTACK, center, 6, radius * 0.4, 0.3, radius * 0.4, 0.0);
	}

	private static void starburst(Location at) {
		World world = at.getWorld();
		world.spawnParticle(Particle.END_ROD, at, 16, 0.2, 0.2, 0.2, 0.18);
		world.spawnParticle(Particle.DUST, at, 14, 0.4, 0.4, 0.4, 0.0, GOLD);
		world.spawnParticle(Particle.ELECTRIC_SPARK, at, 10, 0.3, 0.3, 0.3, 0.2);
		world.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_BREAK, SoundCategory.PLAYERS, 1.0F, 0.7F);
	}

	/** A pillar of white light coming down on {@code at}. */
	private static void pillar(Location at, double height, boolean heavy) {
		World world = at.getWorld();
		for (double y = 0; y < height; y += 0.25) {
			world.spawnParticle(Particle.DUST, at.clone().add(0, y, 0), heavy ? 3 : 1, heavy ? 0.35 : 0.15, 0.05, heavy ? 0.35 : 0.15, 0.0, WHITE);
			if (heavy && y % 1.0 < 0.25) {
				world.spawnParticle(Particle.END_ROD, at.clone().add(0, y, 0), 2, 0.2, 0.1, 0.2, 0.02);
			}
		}
		world.playSound(at, Sound.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 1.2F, heavy ? 0.6F : 1.2F);
		world.playSound(at, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.PLAYERS, heavy ? 1.2F : 0.6F, 1.3F);
	}

	private static void whiteBurst(Location at, double size) {
		World world = at.getWorld();
		world.spawnParticle(Particle.EXPLOSION, at.clone().add(0, 0.8, 0), (int) (3 * size), size, 0.4, size, 0.0);
		world.spawnParticle(Particle.END_ROD, at.clone().add(0, 0.8, 0), (int) (50 * size), 0.4, 0.4, 0.4, 0.3 * size);
		ThreadLocalRandom r = ThreadLocalRandom.current();
		for (int i = 0; i < 60 * size; i++) {
			Vector dir = new Vector(r.nextGaussian(), Math.abs(r.nextGaussian()), r.nextGaussian()).normalize().multiply(r.nextDouble(0.3, 2.5 * size));
			world.spawnParticle(Particle.DUST, at.clone().add(0, 0.5, 0).add(dir), 1, 0, 0, 0, 0.0, i % 3 == 0 ? GOLD : WHITE);
		}
		world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.2F, 0.8F);
		world.playSound(at, Sound.ITEM_TOTEM_USE, SoundCategory.PLAYERS, 0.6F, 1.6F);
	}

	private static void sparkle(Location at, double radius) {
		at.getWorld().spawnParticle(Particle.DUST, at.clone().add(0, 1.0, 0), 8, radius * 0.5, 0.8, radius * 0.5, 0.0, GOLD);
		at.getWorld().spawnParticle(Particle.END_ROD, at.clone().add(0, 1.5, 0), 3, radius * 0.4, 0.6, radius * 0.4, 0.02);
	}

	/** A great diagonal beam of gold, from the sky across {@code through}. */
	private void diagonalBeam(Location through) {
		World world = through.getWorld();
		ThreadLocalRandom r = ThreadLocalRandom.current();
		Vector dir = new Vector(r.nextDouble(-1, 1), -r.nextDouble(0.6, 1.4), r.nextDouble(-1, 1)).normalize();
		Vector start = through.toVector().subtract(dir.clone().multiply(10));
		for (double d = 0; d < 14; d += 0.3) {
			world.spawnParticle(Particle.DUST, start.clone().add(dir.clone().multiply(d)).toLocation(world), 2, 0.08, 0.08, 0.08, 0.0, GOLD);
		}
		world.playSound(through, Sound.BLOCK_BEACON_AMBIENT, SoundCategory.PLAYERS, 1.0F, 1.6F);
	}

	private void horizontalStreak(Location through) {
		World world = through.getWorld();
		Vector s = side();
		for (double d = -6; d <= 6; d += 0.25) {
			world.spawnParticle(Particle.END_ROD, through.clone().add(s.clone().multiply(d)), 1, 0, 0.02, 0, 0.0);
		}
		world.playSound(through, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.PLAYERS, 0.5F, 1.8F);
	}

	/** A disc of light (the halo, lying flat) opening over the target. */
	private void disc(Location at, float size) {
		ItemDisplay disc = prop(at, "black_god_halo", 0.2F, true);
		disc.setInterpolationDuration(6);
		disc.setInterpolationDelay(1);
		disc.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotateX((float) (Math.PI / 2)),
				new Vector3f(size, size, size), new Quaternionf()));
		at.getWorld().playSound(at, Sound.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1.2F, 1.4F);
	}

	private void overlay(ItemStack mask) {
		if (!bedrock() && god.isOnline()) {
			god.sendEquipmentChange(god, EquipmentSlot.HEAD, mask);
		}
	}

	private void clearOverlay() {
		if (!bedrock() && god.isOnline()) {
			ItemStack helmet = god.getInventory().getHelmet();
			god.sendEquipmentChange(god, EquipmentSlot.HEAD, helmet == null ? ItemStack.empty() : helmet);
		}
	}

	private void finish() {
		clearOverlay();
		if (kind.great()) {
			TotalDamage.finish(god);
		} else {
			SkillOverlay.hide(god);
		}
		props.forEach(Entity::remove);
		props.clear();
		if (camera != null) {
			camera.remove();
			camera = null;
			if (god.isOnline()) {
				god.setSpectatorTarget(null);
				Cinematic.restoreMode(god);
				if (god.getGameMode() == GameMode.SPECTATOR && mode != null) {
					god.setGameMode(mode);
				}
				god.teleport(spot);
			}
		}
		hadAi.forEach((foe, ai) -> {
			if (foe.isValid()) {
				foe.setAI(ai);
			}
		});
		hadAi.clear();
	}
}
