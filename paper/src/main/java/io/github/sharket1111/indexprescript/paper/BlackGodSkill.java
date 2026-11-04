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
		SISYPHUS(6, 140, 30), FORCED_EXIT(7, 112, 60), SHIFT_DELETE(8, 162, 60);

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
		Kind kind = Kind.ofSlot(slot);
		if (kind == null) {
			return false;
		}
		if (ACTIVE.containsKey(god.getUniqueId()) || BlackGodRevive.reviving(god)) {
			return true;
		}
		String key = god.getUniqueId() + ":" + kind;
		long now = CaduceusListener.now();
		long ready = READY_AT.getOrDefault(key, 0L);
		if (now < ready) {
			god.sendActionBar(Component.text(Messages.get(god, "blackgod.cooldown", (ready - now + 19) / 20), NamedTextColor.GRAY));
			return true;
		}
		LivingEntity target = lookedAt(god);
		if (target == null) {
			god.sendActionBar(Component.text(Messages.get(god, "renoir.no_target"), NamedTextColor.GRAY));
			return true;
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
		READY_AT.put(key, now + kind.cooldownSeconds * 20L);
		BlackGodSkill skill = new BlackGodSkill(god, kind, target, foes);
		ACTIVE.put(god.getUniqueId(), skill);
		skill.open();
		return true;
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

	private void open() {
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
		TotalDamage.start(god);
		SkillOverlay.show(god, SkillOverlay.Size.BIG);
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

	// --- 시지프 시스템을 진짜 망가트리려고? ------------------------------------------

	private static final int[] SISYPHUS_COINS = {22, 44, 66, 88, 110};

	private void sisyphus() {
		boolean impact = false;
		for (int at : SISYPHUS_COINS) {
			impact |= tick >= at + 3 && tick < at + 7;
		}
		double shake = impact ? 0.35 : 0.0;
		if (tick < 18) {
			onGod(4.2, 1.7, 1.6, 0.0);
			flareHalo(1.9F + tick * 0.05F);
			rays(spot.clone().add(0, 1.6, 0), 2);
		} else if (tick < 44) {
			wide(5.0, 3.5, shake);
		} else if (tick < 62) {
			onGod(2.0, 1.1, 1.1, shake);
		} else if (tick < 86) {
			wide(6.0, 4.5, shake);
		} else if (tick < 106) {
			onGod(3.4, 1.8, 1.8, shake);
		} else if (tick < 124) {
			onTarget(4.5, 2.2, shake);
		} else {
			wide(9.0, 6.0, 0.0);
		}
		for (int i = 0; i < SISYPHUS_COINS.length; i++) {
			int at = SISYPHUS_COINS[i];
			if (tick == at) {
				Location center = centroid();
				crescent(center, 3.5 + held.size() * 0.4, i % 2 == 0 ? -1 : 1, PALE);
				spot.getWorld().playSound(center, Sound.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.5F, 0.5F);
				spot.getWorld().playSound(center, Sound.ITEM_TRIDENT_RIPTIDE_3, SoundCategory.PLAYERS, 1.0F, 1.4F);
			}
			if (tick == at + 3) {
				boolean last = i == SISYPHUS_COINS.length - 1;
				if (last) {
					pillar(target.getLocation(), 9, true);
					whiteBurst(target.getLocation(), 1.5);
					hit(target, 9.0);
				} else {
					for (LivingEntity foe : List.copyOf(held.keySet())) {
						starburst(foe.getLocation().add(0, foe.getHeight() * 0.6, 0));
						if (i % 2 == 1) {
							pillar(foe.getLocation(), 7, false);
						}
						hit(foe, 4.0);
					}
				}
			}
		}
		if (tick > 112 && tick % 3 == 0) {
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

	// --- 강제 퇴장 -------------------------------------------------------------------

	private void forcedExit() {
		if (tick < 30) {
			onGod(3.0 - tick * 0.02, 1.7, 1.6, 0.0);
			flareHalo(1.9F + tick * 0.04F);
			if (tick % 4 == 0) {
				panels(tick / 4);
			}
			if (tick == 0) {
				spot.getWorld().playSound(spot, Sound.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 1.5F, 0.6F);
			}
		} else if (tick < 74) {
			onTarget(4.0, 1.6, tick >= 70 ? 0.5 : 0.0);
		} else {
			onTarget(6.5 + (tick - 74) * 0.05, 3.0, tick < 82 ? 0.3 : 0.0);
		}
		if (tick == 30) {
			Effects.staggered(target);
			spot.getWorld().playSound(target.getLocation(), Sound.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 1.2F, 0.5F);
		}
		if (tick >= 34 && tick < 62 && tick % 2 == 0) {
			diagonalBeam(target.getLocation().add(0, 1.0, 0));
		}
		if (tick == 56) {
			disc(target.getLocation().add(0, 6, 0), 3.5F);
		}
		if (tick == 70) {
			pillar(target.getLocation(), 6, true);
			whiteBurst(target.getLocation(), 1.2);
			fixed(target, maxHealth(target) * 2.0);
		}
		if (tick > 70 && tick < 100 && tick % 3 == 0) {
			sparkle(target.getLocation(), 2.0);
		}
	}

	/** Golden panels of light (like an error window) closing round the Black God. */
	private void panels(int step) {
		World world = spot.getWorld();
		double radius = 2.2 - step * 0.12;
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

	// --- Shift+Delete ----------------------------------------------------------------

	private static final int[] DELETE_COINS = {62, 88};
	private static final int DELETE_LAST = 118;

	private void shiftDelete() {
		if (tick < 28) {
			onGod(5.0, 2.2, 1.8, 0.0);
			rays(spot.clone().add(0, 1.6, 0), 3);
		} else if (tick < 50) {
			onGod(2.6 - (tick - 28) * 0.03, 1.7, 1.7, 0.0);
			flareHalo(1.9F + (tick - 28) * 0.08F);
			if (tick == 28) {
				spot.getWorld().playSound(spot, Sound.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 1.5F, 0.5F);
			}
		} else if (tick < DELETE_LAST + 12) {
			boolean impact = tick >= DELETE_COINS[0] && tick < DELETE_COINS[0] + 4 || tick >= DELETE_COINS[1] && tick < DELETE_COINS[1] + 4
					|| tick >= DELETE_LAST && tick < DELETE_LAST + 10;
			onTarget(4.2, 1.5, impact ? 0.5 : 0.0);
		} else {
			wide(8.0, 4.0, 0.0);
		}
		if (tick >= 50 && tick < 60 && tick % 2 == 0) {
			horizontalStreak(target.getLocation().add(0, 1.0 + (ThreadLocalRandom.current().nextDouble() - 0.5), 0));
		}
		for (int at : DELETE_COINS) {
			if (tick == at - 8) {
				disc(target.getLocation().add(0, 6, 0), 3.0F);
			}
			if (tick == at) {
				pillar(target.getLocation(), 6, true);
				whiteBurst(target.getLocation(), 1.0);
				fixed(target, maxHealth(target) * 0.5);
			}
		}
		if (tick == DELETE_LAST - 10) {
			disc(target.getLocation().add(0, 7, 0), 5.0F);
		}
		if (tick == DELETE_LAST) {
			// The whole screen burns white.
			overlay(FLASH);
			pillar(target.getLocation(), 9, true);
			whiteBurst(target.getLocation(), 2.5);
			spot.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, target.getLocation().add(2, 1, 0), 1);
			spot.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, target.getLocation().add(-2, 1, 0), 1);
			fixed(target, maxHealth(target) * 2.0);
		}
		if (tick == DELETE_LAST + 16) {
			clearOverlay();
		}
		if (tick > DELETE_LAST && tick < kind.length - 6 && tick % 3 == 0) {
			sparkle(target.getLocation(), 3.0);
		}
	}

	// --- damage ----------------------------------------------------------------------

	private static double maxHealth(LivingEntity entity) {
		AttributeInstance max = entity.getAttribute(Attribute.MAX_HEALTH);
		return max == null ? 20.0 : max.getValue();
	}

	private void hit(LivingEntity foe, double damage) {
		if (foe.isValid() && !foe.isDead()) {
			Damage.deal(foe, damage, god, true);
		}
	}

	/** Fixed damage: past armour and resistance; enough to kill, kills (credited to the Black God). */
	private void fixed(LivingEntity foe, double amount) {
		if (!foe.isValid() || foe.isDead()) {
			return;
		}
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
		TotalDamage.finish(god);
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
