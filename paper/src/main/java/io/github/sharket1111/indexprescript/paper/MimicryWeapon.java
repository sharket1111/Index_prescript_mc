package io.github.sharket1111.indexprescript.paper;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;

/**
 * Mimicry, Gebura's E.G.O (fan-made, after Library of Ruina): the weapon's
 * two states and Gebura's passives. Its skills are in {@link MimicrySkill}.
 *
 * Library of Ruina is turn-based; here a "scene" is one turn,
 * {@link CombatState#TURN_TICKS} (7 s), and the dice become Limbus coins.
 *
 * - E.G.O manifestation ('전설'): with Mimicry in hand, Sanity 40 or more and
 *   health at half or less, the off-hand key (F) costs 50 Sanity and
 *   manifests E.G.O. The blade takes its manifested look.
 * - '최강' / '속도 3': Speed I while Mimicry is in hand, Speed II in E.G.O.
 * - '반격': once a scene, a melee blow taken is answered with a counter slash
 *   (a 4-8 die).
 * - '붉은 안개' (E.G.O only): every skill has +2 power and normal hits deal
 *   20% more; each kill adds a stack (up to 5), one more power and 10% each;
 *   each scene one damage type (slash, pierce, blunt) is Normal and the
 *   others are Endured (half damage); a scene in combat that deals less than
 *   40 damage (4 health) costs 40% of the stagger threshold in stagger
 *   damage, and being staggered ends E.G.O.
 * - E.G.O also ends after a minute out of combat, on death and on leaving.
 */
final class MimicryWeapon implements Listener {
	static final int SANITY_NEEDED = 40;
	static final int SANITY_COST = 50;
	private static final int SCENE = CombatState.TURN_TICKS;
	private static final int CALM_END_TICKS = 60 * 20;
	private static final int MAX_STACKS = 5;
	/** 40 damage in the original's numbers: 4 health. */
	private static final double SCENE_DAMAGE_NEEDED = 4.0;
	private static final float SELF_STAGGER = 0.4F;
	private static final double ENDURED = 0.5;
	private static final double DIE_DAMAGE = 0.7;
	static final Color RED = Color.fromRGB(0xC81E1E);
	static final Color DARK_RED = Color.fromRGB(0x6E0A0E);
	static final TextColor RED_TEXT = TextColor.color(0xE02828);
	private static final Map<UUID, Ego> EGO = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_FIGHT = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> COUNTERED = new ConcurrentHashMap<>();

	private static final class Ego {
		final long since;
		long sceneStart;
		double dealt;
		int stacks;
		DamageKind normal;

		Ego(long now) {
			this.since = now;
			this.sceneStart = now;
		}
	}

	// --- queries ---------------------------------------------------------------------

	static boolean ego(Player player) {
		return EGO.containsKey(player.getUniqueId());
	}

	static int stacks(Player player) {
		Ego ego = EGO.get(player.getUniqueId());
		return ego == null ? 0 : ego.stacks;
	}

	/** Power added to Mimicry's skills: Red Mist's +2 and its kill stacks in E.G.O, and Strength (attack level). */
	static int bonusPower(Player player) {
		Ego ego = EGO.get(player.getUniqueId());
		int bonus = ego == null ? 0 : 2 + ego.stacks;
		return bonus + CombatState.of(player).attackLevel(CaduceusListener.now());
	}

	/** This scene's Normal damage type in E.G.O, or null. */
	static DamageKind normal(Player player) {
		Ego ego = EGO.get(player.getUniqueId());
		return ego == null ? null : ego.normal;
	}

	/** Health damage of a die of average {@code value} (the original's dice, here coins). */
	static double dieDamage(double value) {
		return value * DIE_DAMAGE;
	}

	/** Every Mimicry blow that lands: Bleed 2 and count 2, or Bleed 5 and count 3 in E.G.O. */
	static void bleedOnHit(Player player, LivingEntity victim) {
		if (ego(player)) {
			Bleed.inflict(victim, 5, 3);
		} else {
			Bleed.inflict(victim, 2, 2);
		}
	}

	static void fought(Player player) {
		LAST_FIGHT.put(player.getUniqueId(), CaduceusListener.now());
		Sanity.fought(player);
	}

	private static boolean holding(Player player) {
		return Items.isMimicry(player.getInventory().getItemInMainHand());
	}

	private static double maxHealth(LivingEntity entity) {
		AttributeInstance max = entity.getAttribute(Attribute.MAX_HEALTH);
		return max == null ? 20.0 : max.getValue();
	}

	private static String kindName(DamageKind kind) {
		return switch (kind) {
			case SLASH -> "참격";
			case PIERCE -> "관통";
			case BLUNT -> "타격";
		};
	}

	// --- manifesting -----------------------------------------------------------------

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onSwap(PlayerSwapHandItemsEvent event) {
		Player player = event.getPlayer();
		if (!holding(player) || BlackGod.active(player) || MimicrySkill.choosing(player)) {
			return;
		}
		event.setCancelled(true);
		manifest(player);
	}

	static void manifest(Player player) {
		if (ego(player)) {
			Hud.message(player, Component.text("이미 E.G.O가 발현되어 있습니다.", NamedTextColor.GRAY));
			return;
		}
		if (MimicrySkill.active(player) || Clash.busy(player)) {
			return;
		}
		long now = CaduceusListener.now();
		if (CombatState.of(player).staggered(now)) {
			Hud.message(player, Component.text("흐트러진 상태에서는 발현할 수 없습니다.", NamedTextColor.GRAY));
			return;
		}
		int sanity = Sanity.of(player);
		double health = player.getHealth() / maxHealth(player);
		if (sanity < SANITY_NEEDED || health > 0.5) {
			Hud.message(player, Component.text("E.G.O 발현: 정신력 " + SANITY_NEEDED + " 이상(현재 " + sanity + "), 체력 50% 이하(현재 "
					+ Math.round(health * 100) + "%)일 때", NamedTextColor.GRAY));
			player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.PLAYERS, 0.7F, 0.6F);
			return;
		}
		Sanity.lose(player, SANITY_COST);
		Ego ego = new Ego(now);
		EGO.put(player.getUniqueId(), ego);
		newScene(player, ego, now, false);
		fought(player);
		ItemStack held = player.getInventory().getItemInMainHand();
		Items.setMimicryEgo(held, true);
		player.showTitle(Title.title(Component.text("E.G.O", RED_TEXT).decorate(TextDecoration.BOLD),
				Component.text("미미크리 — 붉은 안개", NamedTextColor.RED),
				Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1600), Duration.ofMillis(500))));
		World world = player.getWorld();
		Location at = player.getLocation().add(0, 1, 0);
		world.playSound(at, Sound.ENTITY_WARDEN_HEARTBEAT, SoundCategory.PLAYERS, 2.0F, 0.6F);
		world.playSound(at, Sound.ENTITY_WITHER_SPAWN, SoundCategory.PLAYERS, 0.5F, 1.6F);
		world.playSound(at, Sound.ITEM_TRIDENT_THUNDER, SoundCategory.PLAYERS, 0.6F, 0.7F);
		world.spawnParticle(Particle.DUST, at, 120, 0.9, 1.1, 0.9, 0.0, new Particle.DustOptions(RED, 2.0F));
		world.spawnParticle(Particle.DUST, at, 60, 1.4, 0.6, 1.4, 0.0, new Particle.DustOptions(DARK_RED, 2.4F));
		for (int i = 0; i < 48; i++) {
			double angle = Math.PI * 2 * i / 48;
			world.spawnParticle(Particle.DUST, player.getLocation().add(Math.cos(angle) * 2.2, 0.15, Math.sin(angle) * 2.2), 2, 0.05, 0.05,
					0.05, 0.0, new Particle.DustOptions(RED, 1.6F));
		}
		world.spawnParticle(Particle.CRIMSON_SPORE, at, 80, 1.2, 1.2, 1.2, 0.02);
	}

	/** Ends E.G.O: the blade goes back to its plain look. */
	static void end(Player player, String reason) {
		if (EGO.remove(player.getUniqueId()) == null) {
			return;
		}
		for (ItemStack stack : player.getInventory().getContents()) {
			if (Items.isMimicry(stack)) {
				Items.setMimicryEgo(stack, false);
			}
		}
		if (player.isOnline()) {
			Hud.message(player, Component.text("E.G.O 해제 — " + reason, NamedTextColor.RED));
			World world = player.getWorld();
			world.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 1.0F, 0.6F);
			world.spawnParticle(Particle.SMOKE, player.getLocation().add(0, 1, 0), 40, 0.5, 0.8, 0.5, 0.02);
		}
	}

	private static void newScene(Player player, Ego ego, long now, boolean announce) {
		DamageKind[] kinds = DamageKind.values();
		DamageKind next = kinds[ThreadLocalRandom.current().nextInt(kinds.length)];
		boolean changed = next != ego.normal;
		ego.normal = next;
		ego.sceneStart = now;
		ego.dealt = 0;
		if (!announce || changed) {
			player.sendMessage(Component.text("[붉은 안개] 이번 장면: " + kindName(next) + " 보통, 그 외 견딤", NamedTextColor.RED));
		}
	}

	// --- every tick ------------------------------------------------------------------

	static void tick() {
		long now = CaduceusListener.now();
		for (Player player : Bukkit.getOnlinePlayers()) {
			Ego ego = EGO.get(player.getUniqueId());
			boolean holding = holding(player);
			if (now % 10 == 0 && player.getGameMode() != GameMode.SPECTATOR) {
				if (ego != null) {
					player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 30, 1, true, false, true));
				} else if (holding) {
					player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 30, 0, true, false, true));
				}
				if (holding) {
					Items.setMimicryEgo(player.getInventory().getItemInMainHand(), ego != null);
				}
			}
			if (ego == null) {
				continue;
			}
			if (CombatState.of(player).staggered(now)) {
				end(player, "흐트러짐");
				continue;
			}
			long last = LAST_FIGHT.getOrDefault(player.getUniqueId(), ego.since);
			if (now - Math.max(last, ego.since) >= CALM_END_TICKS && !MimicrySkill.active(player)) {
				end(player, "1분간 비전투");
				continue;
			}
			if (now - ego.sceneStart >= SCENE) {
				boolean fighting = last >= ego.sceneStart;
				if (fighting && ego.dealt < SCENE_DAMAGE_NEEDED && !MimicrySkill.active(player)) {
					selfStagger(player, now);
					if (!ego(player)) {
						continue;
					}
				}
				newScene(player, ego, now, true);
			}
			if (now % 3 == 0) {
				mist(player);
			}
		}
	}

	/** Red Mist: a scene without enough blood costs 40% of the stagger threshold. */
	private static void selfStagger(Player player, long now) {
		float threshold = (float) Math.max(10.0, maxHealth(player) * 0.5);
		CombatState state = CombatState.of(player);
		Hud.message(player, Component.text("[붉은 안개] 이번 장면의 피해가 40 미만 — 흐트러짐 피해", NamedTextColor.RED));
		player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT_SWEET_BERRY_BUSH, SoundCategory.PLAYERS, 1.0F, 0.6F);
		if (state.addStagger(threshold * SELF_STAGGER, threshold, now)) {
			Stagger.apply(player, now);
			Effects.staggered(player);
			end(player, "흐트러짐");
		} else {
			Stagger.gauge(player, state.staggerBuildUp(now) / threshold);
		}
	}

	/** The red mist hanging round a manifested Gebura. */
	private static void mist(Player player) {
		if (player.getGameMode() == GameMode.SPECTATOR) {
			return;
		}
		World world = player.getWorld();
		Location at = player.getLocation().add(0, 1.0, 0);
		world.spawnParticle(Particle.DUST, at, 4, 0.45, 0.8, 0.45, 0.0, new Particle.DustOptions(RED, 1.3F));
		world.spawnParticle(Particle.DUST, player.getLocation().add(0, 0.2, 0), 3, 0.7, 0.1, 0.7, 0.0, new Particle.DustOptions(DARK_RED, 1.8F));
	}

	static void endAll() {
		for (UUID id : Map.copyOf(EGO).keySet()) {
			Player player = Bukkit.getPlayer(id);
			if (player != null) {
				end(player, "플러그인 종료");
			}
		}
		EGO.clear();
	}

	// --- events ----------------------------------------------------------------------

	private static Player attackerOf(Entity damager) {
		if (damager instanceof Player player) {
			return player;
		}
		if (damager instanceof Projectile projectile) {
			ProjectileSource shooter = projectile.getShooter();
			if (shooter instanceof Player player) {
				return player;
			}
		}
		return null;
	}

	/** Damage types for Red Mist's resistances; null for damage of no type (fire, poison, magic...). */
	private static DamageKind kindOf(EntityDamageEvent event) {
		switch (event.getCause()) {
			case FALL, FLY_INTO_WALL, BLOCK_EXPLOSION, ENTITY_EXPLOSION, FALLING_BLOCK -> {
				return DamageKind.BLUNT;
			}
			case THORNS -> {
				return DamageKind.PIERCE;
			}
			default -> {
			}
		}
		if (!(event instanceof EntityDamageByEntityEvent by)) {
			return null;
		}
		Entity damager = by.getDamager();
		if (damager instanceof Projectile) {
			return DamageKind.PIERCE;
		}
		if (damager instanceof LivingEntity living && living.getEquipment() != null) {
			Material held = living.getEquipment().getItemInMainHand().getType();
			String name = held.name();
			if (held == Material.TRIDENT || name.endsWith("_SPEAR")) {
				return DamageKind.PIERCE;
			}
			if (name.endsWith("_SWORD") || name.endsWith("_AXE")) {
				return DamageKind.SLASH;
			}
			return DamageKind.BLUNT;
		}
		return null;
	}

	/** Red Mist's resistances: the scene's Normal type, the others Endured. */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onHurt(EntityDamageEvent event) {
		if (!(event.getEntity() instanceof Player player)) {
			return;
		}
		Ego ego = EGO.get(player.getUniqueId());
		if (ego == null) {
			return;
		}
		DamageKind kind = kindOf(event);
		if (kind != null && kind != ego.normal) {
			event.setDamage(event.getDamage() * ENDURED);
		}
	}

	/** Red Mist's power on normal hits in E.G.O. */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onStrike(EntityDamageByEntityEvent event) {
		if (!(event.getDamager() instanceof Player player) || Damage.dealing()
				|| event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK || !holding(player)) {
			return;
		}
		Ego ego = EGO.get(player.getUniqueId());
		if (ego != null) {
			event.setDamage(event.getDamage() * (1.0 + 0.1 * (2 + ego.stacks)));
		}
	}

	/** Combat time, Red Mist's damage tally and the counter ('반격'). */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onDamage(EntityDamageByEntityEvent event) {
		Player attacker = attackerOf(event.getDamager());
		if (attacker != null && event.getEntity() instanceof LivingEntity victim && attacker != event.getEntity()) {
			LAST_FIGHT.put(attacker.getUniqueId(), CaduceusListener.now());
			if (event.getDamager() == attacker && event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK && !Damage.dealing()
					&& holding(attacker)) {
				// A normal Mimicry blow: the target's Bleed triggers, then new Bleed goes on.
				Bukkit.getScheduler().runTask(IndexPrescriptPlugin.instance(), () -> {
					if (victim.isValid() && !victim.isDead() && attacker.isOnline()) {
						Bleed.trigger(victim, attacker);
						bleedOnHit(attacker, victim);
					}
				});
			}
			Ego ego = EGO.get(attacker.getUniqueId());
			if (ego != null) {
				ego.dealt += event.getFinalDamage();
			}
		}
		if (event.getEntity() instanceof Player victim && (event.getDamager() instanceof LivingEntity || attacker != null)) {
			LAST_FIGHT.put(victim.getUniqueId(), CaduceusListener.now());
			if (event.getDamager() instanceof LivingEntity foe && event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK
					&& !Damage.dealing()) {
				counter(victim, foe);
			}
		}
	}

	/** '반격': once a scene, a melee blow is answered with a slash (a 4-8 die, plus Red Mist's power). */
	private static void counter(Player player, LivingEntity foe) {
		long now = CaduceusListener.now();
		if (!holding(player) || MimicrySkill.active(player) || player.getGameMode() == GameMode.SPECTATOR || foe == player
				|| Cinematic.isActor(foe) || now < COUNTERED.getOrDefault(player.getUniqueId(), Long.MIN_VALUE)
				|| CombatState.of(player).staggered(now)) {
			return;
		}
		COUNTERED.put(player.getUniqueId(), now + SCENE);
		Bukkit.getScheduler().runTask(IndexPrescriptPlugin.instance(), () -> {
			if (!player.isOnline() || player.isDead() || !foe.isValid() || foe.isDead()
					|| foe.getLocation().distanceSquared(player.getLocation()) > 25) {
				return;
			}
			int roll = 4 + ThreadLocalRandom.current().nextInt(5) + bonusPower(player);
			Location at = player.getLocation();
			at.setDirection(foe.getLocation().toVector().subtract(at.toVector()).setY(0));
			player.setRotation(at.getYaw(), player.getLocation().getPitch());
			player.swingMainHand();
			Location mid = foe.getLocation().add(0, foe.getHeight() * 0.6, 0);
			World world = foe.getWorld();
			MimicrySkill.arc(world, player.getLocation().add(0, 1.1, 0).toVector(), foe.getLocation().toVector()
					.subtract(player.getLocation().toVector()), 2.0, 20, -70, 140, 1.2F);
			world.spawnParticle(Particle.SWEEP_ATTACK, mid, 1);
			world.playSound(mid, Sound.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.0F, 0.7F);
			world.playSound(mid, Sound.ITEM_SHIELD_BLOCK, SoundCategory.PLAYERS, 0.8F, 0.8F);
			Damage.deal(foe, dieDamage(roll), player, true);
			Hud.message(player, Component.text("반격 " + roll, RED_TEXT));
		});
	}

	@EventHandler
	public void onKill(EntityDeathEvent event) {
		Player killer = event.getEntity().getKiller();
		if (killer == null || killer == event.getEntity()) {
			return;
		}
		Ego ego = EGO.get(killer.getUniqueId());
		if (ego != null && ego.stacks < MAX_STACKS) {
			ego.stacks++;
			Hud.message(killer, Component.text("[붉은 안개] 처치 — 위력 +" + (2 + ego.stacks) + " (" + ego.stacks + "/" + MAX_STACKS + ")", RED_TEXT));
			killer.getWorld().spawnParticle(Particle.DUST, killer.getLocation().add(0, 1, 0), 30, 0.5, 0.8, 0.5, 0.0,
					new Particle.DustOptions(RED, 1.8F));
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onDeath(PlayerDeathEvent event) {
		end(event.getPlayer(), "사망");
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		end(event.getPlayer(), "퇴장");
		LAST_FIGHT.remove(event.getPlayer().getUniqueId());
		COUNTERED.remove(event.getPlayer().getUniqueId());
	}

	/** E.G.O does not outlast a restart: a blade left manifested goes back. */
	@EventHandler
	public void onJoin(PlayerJoinEvent event) {
		if (ego(event.getPlayer())) {
			return;
		}
		for (ItemStack stack : event.getPlayer().getInventory().getContents()) {
			if (Items.isMimicry(stack)) {
				Items.setMimicryEgo(stack, false);
			}
		}
	}

	/** Direction helper for the skills: horizontal, normalised, never zero. */
	static Vector flat(Vector v) {
		Vector out = v.clone().setY(0);
		return out.lengthSquared() < 1.0E-6 ? new Vector(1, 0, 0) : out.normalize();
	}
}
