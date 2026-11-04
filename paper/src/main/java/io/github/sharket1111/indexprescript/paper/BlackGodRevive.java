package io.github.sharket1111.indexprescript.paper;

import java.util.ArrayList;
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
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.destroystokyo.paper.event.player.PlayerStopSpectatingEntityEvent;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Equippable;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * The Black God refuses death, twice (per time the mode is taken up): a blow
 * that would kill is cancelled, every player nearby (and the Black God) is
 * held watching one camera, and the revival plays after the in-game scene:
 * the screen breaks into dark and golden blocks; a close-up on the robe with
 * rings spreading; the halo flares, light streaking across it; a white
 * flash; then, from afar, a great halo blazing over the field as golden
 * light rises and the Black God's health comes back in full.
 *
 * Watchers are spectators of the camera for the scene and go back to their
 * game mode and spot after (also on their next join, if they left or the
 * server stopped). Bedrock players, who cannot be given a camera, are held
 * in place instead.
 */
final class BlackGodRevive implements Listener {
	static final int REVIVES = 2;
	private static final double WATCH_RADIUS = 32.0;
	private static final int END = 170;
	private static final int FLASH_AT = 96;
	private static final int HEAL_AT = 104;
	private static final String FLOODGATE_PREFIX = "00000000-0000-0000-";
	private static final NamespacedKey USED = new NamespacedKey(Items.NAMESPACE, "black_god_revives_used");
	private static final Map<UUID, BlackGodRevive> ACTIVE = new ConcurrentHashMap<>();
	private static final Map<UUID, BlackGodRevive> WATCHING = new ConcurrentHashMap<>();
	private static final Particle.DustOptions GOLD = new Particle.DustOptions(Color.fromRGB(0xF0D070), 1.4F);
	private static final Particle.DustOptions PALE = new Particle.DustOptions(Color.fromRGB(0xE8F4FF), 1.0F);
	private static final ItemStack[] GLITCH = new ItemStack[4];
	private static final ItemStack FLASH;

	static {
		for (int k = 0; k < GLITCH.length; k++) {
			GLITCH[k] = overlay("misc/glitch_" + k);
		}
		FLASH = overlay("misc/white_flash");
	}

	private final Player god;
	private final Location spot;
	private final Vector facing;
	private final Map<Player, Location> watchers = new LinkedHashMap<>();
	private final List<Player> held = new ArrayList<>();
	private ItemDisplay camera;
	private ItemDisplay robe;
	private ItemDisplay halo;
	private ItemDisplay skyHalo;
	private int tick;

	private BlackGodRevive(Player god) {
		this.god = god;
		this.spot = god.getLocation();
		double yaw = Math.toRadians(god.getBodyYaw());
		this.facing = new Vector(-Math.sin(yaw), 0, Math.cos(yaw));
	}

	private static ItemStack overlay(String texture) {
		ItemStack stack = ItemStack.of(Material.PAPER);
		stack.setData(DataComponentTypes.EQUIPPABLE, Equippable.equippable(EquipmentSlot.HEAD)
				.cameraOverlay(Key.key(Items.NAMESPACE, texture)).build());
		return stack;
	}

	static boolean reviving(Player player) {
		return ACTIVE.containsKey(player.getUniqueId());
	}

	/** Called when the player becomes the Black God: both revivals are theirs again. */
	static void reset(Player player) {
		player.getPersistentDataContainer().remove(USED);
	}

	static void tickAll() {
		ACTIVE.values().removeIf(revive -> {
			try {
				return !revive.tick();
			} catch (RuntimeException e) {
				IndexPrescriptPlugin.instance().getLogger().log(Level.WARNING, "Black God revival failed", e);
				revive.finish();
				return true;
			}
		});
	}

	static void endAll() {
		ACTIVE.values().forEach(BlackGodRevive::finish);
		ACTIVE.clear();
	}

	// --- the scene ----------------------------------------------------------------------

	private static boolean bedrock(Player player) {
		return player.getUniqueId().toString().startsWith(FLOODGATE_PREFIX);
	}

	private void open() {
		World world = spot.getWorld();
		camera = world.spawn(cameraSpot(0), ItemDisplay.class, d -> {
			d.setPersistent(false);
			d.setTeleportDuration(2);
		});
		if (!bedrock(god)) {
			// The Black God watches too, as a spectator; their robe stays behind as a stand-in.
			robe = world.spawn(withYaw(spot.clone().add(0, 0.5 * 1.05, 0)), ItemDisplay.class, d -> {
				d.setPersistent(false);
				d.setItemStack(model("black_god_robe"));
				d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
				d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(1.05F, 1.05F, 1.05F), new Quaternionf()));
			});
		}
		halo = world.spawn(withYaw(headSpot()), ItemDisplay.class, d -> {
			d.setPersistent(false);
			d.setItemStack(model("black_god_halo"));
			d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
			d.setBrightness(new Display.Brightness(15, 15));
			d.setInterpolationDuration(3);
			d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(1.9F, 1.9F, 1.9F), new Quaternionf()));
		});
		for (Player player : world.getPlayers()) {
			if (player != god && player.getLocation().distanceSquared(spot) > WATCH_RADIUS * WATCH_RADIUS
					|| player.getGameMode() == GameMode.SPECTATOR || WATCHING.containsKey(player.getUniqueId())) {
				continue;
			}
			watchers.put(player, player.getLocation());
			if (bedrock(player)) {
				held.add(player);
				continue;
			}
			Cinematic.saveMode(player);
			player.setGameMode(GameMode.SPECTATOR);
			WATCHING.put(player.getUniqueId(), this);
		}
		world.playSound(spot, Sound.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 2.0F, 0.5F);
		world.playSound(spot, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, SoundCategory.PLAYERS, 1.0F, 1.8F);
	}

	private static ItemStack model(String name) {
		ItemStack stack = ItemStack.of(Material.PAPER);
		stack.editMeta(meta -> meta.setItemModel(new NamespacedKey(Items.NAMESPACE, name)));
		return stack;
	}

	private Location withYaw(Location at) {
		at.setDirection(facing);
		at.setPitch(0);
		return at;
	}

	private Location headSpot() {
		return spot.clone().add(0, 1.5, 0).subtract(facing.clone().multiply(0.42));
	}

	/** Where the camera stands at {@code t}: in front for the glitch, close on the chest, on the halo, then far off. */
	private Location cameraSpot(int t) {
		Vector at;
		Vector look;
		Vector base = spot.toVector();
		Vector side = new Vector(-facing.getZ(), 0, facing.getX());
		if (t < 24) {
			at = base.clone().add(facing.clone().multiply(4.0)).add(new Vector(0, 1.6, 0));
			look = base.clone().add(new Vector(0, 1.4, 0));
		} else if (t < 60) {
			double push = 1.9 - 0.4 * (t - 24) / 36.0;
			at = base.clone().add(facing.clone().multiply(push)).add(side.clone().multiply(0.3)).add(new Vector(0, 1.05, 0));
			look = base.clone().add(new Vector(0, 1.05, 0));
		} else if (t < FLASH_AT + 4) {
			double pull = 3.0 + 0.8 * (t - 60) / 40.0;
			at = base.clone().add(facing.clone().multiply(pull)).add(new Vector(0, 1.6, 0));
			look = base.clone().add(new Vector(0, 1.7, 0));
		} else {
			double drift = (t - FLASH_AT) * 0.01;
			at = base.clone().add(facing.clone().multiply(12.0)).add(side.clone().multiply(drift * 3)).add(new Vector(0, 4.5, 0));
			look = base.clone().add(new Vector(0, 4.0, 0));
		}
		Location location = at.toLocation(spot.getWorld());
		location.setDirection(look.clone().subtract(at));
		return location;
	}

	private boolean tick() {
		if (!god.isOnline()) {
			finish();
			return false;
		}
		Location shot = cameraSpot(tick);
		if (tick < 24 || tick >= FLASH_AT && tick < FLASH_AT + 8) {
			// Handheld shake for the glitch and the flash.
			double s = tick < 24 ? 0.06 : 0.25;
			ThreadLocalRandom r = ThreadLocalRandom.current();
			shot.add((r.nextDouble() - 0.5) * s, (r.nextDouble() - 0.5) * s, (r.nextDouble() - 0.5) * s);
		}
		camera.teleport(shot);
		for (Player watcher : watchers.keySet()) {
			if (!watcher.isOnline()) {
				continue;
			}
			if (held.contains(watcher)) {
				Location back = watchers.get(watcher);
				if (watcher.getLocation().distanceSquared(back) > 0.04) {
					watcher.teleport(back);
				}
			} else if (tick >= 2 && watcher.getGameMode() == GameMode.SPECTATOR && watcher.getSpectatorTarget() != camera) {
				watcher.setSpectatorTarget(camera);
			}
		}
		god.setFireTicks(0);
		scene();
		if (tick >= END) {
			finish();
			return false;
		}
		tick++;
		return true;
	}

	private void scene() {
		World world = spot.getWorld();
		if (tick < 24) {
			// The screen breaks up.
			if (tick % 3 == 0) {
				overlayAll(GLITCH[(tick / 3) % GLITCH.length]);
				world.playSound(spot, Sound.BLOCK_AMETHYST_BLOCK_BREAK, SoundCategory.PLAYERS, 1.0F,
						0.5F + ThreadLocalRandom.current().nextFloat());
			}
			world.spawnParticle(Particle.DUST, spot.clone().add(0, 1.2, 0), 6, 0.7, 0.9, 0.7, 0.0, GOLD);
		} else if (tick == 24) {
			clearOverlays();
			world.playSound(spot, Sound.BLOCK_BELL_RESONATE, SoundCategory.PLAYERS, 1.5F, 0.5F);
		}
		if (tick >= 24 && tick < 60 && tick % 8 == 0) {
			// Rings spreading over the chest.
			Location chest = spot.clone().add(0, 1.05, 0).add(facing.clone().multiply(0.6));
			ring(chest, 0.25 + (tick - 24) / 36.0 * 0.4, (tick / 8) % 2 == 0 ? PALE : GOLD);
			world.playSound(chest, Sound.BLOCK_BEACON_AMBIENT, SoundCategory.PLAYERS, 1.2F, 1.4F);
		}
		if (tick >= 24 && tick < 60) {
			Location chest = spot.clone().add(0, 1.05, 0).add(facing.clone().multiply(0.6));
			for (int i = 0; i < 2; i++) {
				double r = 0.25 + ((tick - 24) % 8) / 8.0 * 0.6;
				ring(chest, r, PALE);
			}
		}
		if (tick >= 60 && tick < FLASH_AT) {
			// The halo flares, light streaking across it.
			float size = 1.9F + 1.6F * (tick - 60) / (FLASH_AT - 60F);
			halo.setInterpolationDelay(0);
			halo.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotateZ((tick - 60) * 0.01F),
					new Vector3f(size, size, size), new Quaternionf()));
			Location head = headSpot();
			Vector side = new Vector(-facing.getZ(), 0, facing.getX());
			if (tick % 3 == 0) {
				double y = (ThreadLocalRandom.current().nextDouble() - 0.5) * 1.6;
				for (double x = -3.5; x <= 3.5; x += 0.2) {
					world.spawnParticle(Particle.END_ROD, head.clone().add(side.clone().multiply(x)).add(0, y, 0)
							.add(facing.clone().multiply(0.2)), 1, 0, 0, 0, 0.0);
				}
				world.playSound(head, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.PLAYERS, 0.4F, 1.6F);
			}
			world.spawnParticle(Particle.ELECTRIC_SPARK, head, 6, 0.7, 0.7, 0.7, 0.2);
			if (tick == 60) {
				world.playSound(head, Sound.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 1.5F, 0.5F);
			}
		}
		if (tick == FLASH_AT) {
			overlayAll(FLASH);
			world.spawnParticle(Particle.END_ROD, spot.clone().add(0, 1.5, 0), 120, 1.5, 2.0, 1.5, 0.5);
			world.spawnParticle(Particle.EXPLOSION_EMITTER, spot.clone().add(0, 1, 0), 1);
			world.playSound(spot, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, 1.5F, 0.6F);
			world.playSound(spot, Sound.ITEM_TOTEM_USE, SoundCategory.PLAYERS, 1.0F, 0.6F);
		}
		if (tick == FLASH_AT + 6) {
			clearOverlays();
			halo.remove();
			skyHalo = world.spawn(spot.clone().add(0, 9, 0), ItemDisplay.class, d -> {
				d.setPersistent(false);
				d.setItemStack(model("black_god_halo"));
				d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
				d.setBillboard(Display.Billboard.CENTER);
				d.setBrightness(new Display.Brightness(15, 15));
				d.setInterpolationDuration(10);
				d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(2, 2, 2), new Quaternionf()));
			});
		}
		if (skyHalo != null && skyHalo.isValid()) {
			int since = tick - (FLASH_AT + 6);
			float size = since < 12 ? 2 + 10 * since / 12F : tick > END - 20 ? Math.max(0.1F, 12 * (END - tick) / 20F) : 12F;
			skyHalo.setInterpolationDelay(0);
			skyHalo.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotateZ(since * 0.004F),
					new Vector3f(size, size, size), new Quaternionf()));
			if (since % 6 == 0) {
				world.spawnParticle(Particle.END_ROD, skyHalo.getLocation(), 12, 3, 1, 3, 0.15);
			}
		}
		if (tick >= HEAL_AT && tick < END - 10) {
			// Golden light rising from the ground: the body made whole.
			for (int i = 0; i < 6; i++) {
				double a = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
				double r = ThreadLocalRandom.current().nextDouble(0.3, 3.5);
				world.spawnParticle(Particle.DUST, spot.clone().add(Math.cos(a) * r, 0.2, Math.sin(a) * r), 1, 0.05, 0.4, 0.05, 0.0, GOLD);
			}
			if (tick % 5 == 0) {
				world.spawnParticle(Particle.END_ROD, spot.clone().add(0, 0.5, 0), 4, 1.2, 0.3, 1.2, 0.08);
			}
		}
		if (tick == HEAL_AT) {
			AttributeInstance max = god.getAttribute(Attribute.MAX_HEALTH);
			god.setHealth(max == null ? 20.0 : max.getValue());
			god.setFoodLevel(Math.max(god.getFoodLevel(), 20));
			world.playSound(spot, Sound.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1.5F, 0.6F);
			world.playSound(spot, Sound.BLOCK_BELL_USE, SoundCategory.PLAYERS, 1.5F, 0.5F);
		}
	}

	/** A vertical ring facing out of the robe's front. */
	private void ring(Location center, double radius, Particle.DustOptions dust) {
		Vector side = new Vector(-facing.getZ(), 0, facing.getX());
		Vector up = new Vector(0, 1, 0);
		int points = Math.max(12, (int) (radius * 40));
		for (int i = 0; i < points; i++) {
			double a = i * Math.PI * 2 / points;
			Location p = center.clone().add(side.clone().multiply(Math.cos(a) * radius)).add(up.clone().multiply(Math.sin(a) * radius));
			center.getWorld().spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0.0, dust);
		}
	}

	private void overlayAll(ItemStack mask) {
		for (Player watcher : watchers.keySet()) {
			if (watcher.isOnline() && !bedrock(watcher)) {
				watcher.sendEquipmentChange(watcher, EquipmentSlot.HEAD, mask);
			}
		}
	}

	private void clearOverlays() {
		for (Player watcher : watchers.keySet()) {
			if (watcher.isOnline() && !bedrock(watcher)) {
				ItemStack helmet = watcher.getInventory().getHelmet();
				watcher.sendEquipmentChange(watcher, EquipmentSlot.HEAD, helmet == null ? ItemStack.empty() : helmet);
			}
		}
	}

	private void finish() {
		clearOverlays();
		for (Map.Entry<Player, Location> entry : watchers.entrySet()) {
			Player watcher = entry.getKey();
			WATCHING.remove(watcher.getUniqueId());
			if (!watcher.isOnline() || held.contains(watcher)) {
				continue;
			}
			watcher.setSpectatorTarget(null);
			Cinematic.restoreMode(watcher);
			watcher.teleport(entry.getValue());
		}
		watchers.clear();
		for (Entity entity : new Entity[] {camera, robe, halo, skyHalo}) {
			if (entity != null) {
				entity.remove();
			}
		}
		if (god.isOnline()) {
			god.setNoDamageTicks(40);
			god.setFallDistance(0.0F);
		}
	}

	// --- events --------------------------------------------------------------------------

	/** A killing blow on a Black God with a revival left starts the scene instead; no harm comes during it. */
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onDamage(EntityDamageEvent event) {
		if (!(event.getEntity() instanceof Player player)) {
			return;
		}
		if (ACTIVE.containsKey(player.getUniqueId())) {
			event.setCancelled(true);
			return;
		}
		if (!BlackGod.active(player) || event.getCause() == EntityDamageEvent.DamageCause.KILL
				|| event.getCause() == EntityDamageEvent.DamageCause.VOID
				|| event.getFinalDamage() < player.getHealth() + player.getAbsorptionAmount()) {
			return;
		}
		int used = player.getPersistentDataContainer().getOrDefault(USED, PersistentDataType.INTEGER, 0);
		if (used >= REVIVES) {
			return;
		}
		event.setCancelled(true);
		player.getPersistentDataContainer().set(USED, PersistentDataType.INTEGER, used + 1);
		player.setHealth(Math.max(1.0, player.getHealth()));
		BlackGodRevive revive = new BlackGodRevive(player);
		ACTIVE.put(player.getUniqueId(), revive);
		revive.open();
		player.sendMessage(Component.text(Messages.get(player, "blackgod.revive", REVIVES - used - 1), NamedTextColor.GOLD));
	}

	/** Watchers cannot leave the camera while the scene plays. */
	@EventHandler(ignoreCancelled = true)
	public void onStopSpectating(PlayerStopSpectatingEntityEvent event) {
		if (WATCHING.containsKey(event.getPlayer().getUniqueId())) {
			event.setCancelled(true);
		}
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		BlackGodRevive revive = ACTIVE.remove(event.getPlayer().getUniqueId());
		if (revive != null) {
			revive.finish();
		}
		BlackGodRevive watched = WATCHING.remove(event.getPlayer().getUniqueId());
		if (watched != null) {
			Player player = event.getPlayer();
			player.setSpectatorTarget(null);
			Cinematic.restoreMode(player);
			Location back = watched.watchers.remove(player);
			if (back != null) {
				player.teleport(back);
			}
		}
	}
}
