package io.github.sharket1111.indexprescript.paper;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

/**
 * A clash test dummy: a mannequin with {@link #HEALTH} health (Minecraft's
 * scale) and one fixed skill whose base power, coin power, coins, coin types
 * and Sanity are set by command. A skill aimed at it is answered with that
 * skill a moment later, inside the clash window (see {@link Clash}); if it
 * wins, its coins strike back for a set damage each. It never dies: a blow
 * that would kill it restores its health instead. It shows the same unit UI
 * as players (see {@link UnitHud}). Its settings live on the entity, so it
 * keeps them across restarts.
 */
final class ClashDummy implements Listener {
	static final double HEALTH = 1000.0;
	private static final NamespacedKey TAG = new NamespacedKey(Items.NAMESPACE, "clash_dummy");
	private static final NamespacedKey BASE = new NamespacedKey(Items.NAMESPACE, "clash_dummy_base");
	private static final NamespacedKey POWER = new NamespacedKey(Items.NAMESPACE, "clash_dummy_power");
	private static final NamespacedKey COINS = new NamespacedKey(Items.NAMESPACE, "clash_dummy_coins");
	private static final NamespacedKey TYPES = new NamespacedKey(Items.NAMESPACE, "clash_dummy_types");
	private static final NamespacedKey SANITY = new NamespacedKey(Items.NAMESPACE, "clash_dummy_sanity");
	private static final NamespacedKey DAMAGE = new NamespacedKey(Items.NAMESPACE, "clash_dummy_damage");
	private static final TextColor COLOUR = TextColor.color(0xB0B0B0);
	private static final int COIN_GAP = 12;
	private static final Set<UUID> KNOWN = ConcurrentHashMap.newKeySet();

	static boolean is(Entity entity) {
		return entity instanceof Mannequin && entity.getPersistentDataContainer().has(TAG, PersistentDataType.BYTE);
	}

	/** Every clash dummy loaded now. */
	static java.util.List<LivingEntity> all() {
		java.util.List<LivingEntity> out = new java.util.ArrayList<>();
		for (UUID id : Set.copyOf(KNOWN)) {
			Entity entity = Bukkit.getEntity(id);
			if (entity instanceof LivingEntity living && living.isValid() && is(living)) {
				out.add(living);
			} else if (entity == null || !entity.isValid()) {
				KNOWN.remove(id);
			}
		}
		return out;
	}

	/** Finds the dummies already in loaded chunks (on start). */
	static void scan() {
		for (World world : Bukkit.getWorlds()) {
			for (Mannequin mannequin : world.getEntitiesByClass(Mannequin.class)) {
				if (is(mannequin)) {
					KNOWN.add(mannequin.getUniqueId());
				}
			}
		}
	}

	@EventHandler
	public void onLoad(EntitiesLoadEvent event) {
		for (Entity entity : event.getEntities()) {
			if (is(entity)) {
				KNOWN.add(entity.getUniqueId());
			}
		}
	}

	/** It never dies: a killing blow restores its health instead. */
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onHurt(EntityDamageEvent event) {
		if (!(event.getEntity() instanceof LivingEntity dummy) || !is(dummy)) {
			return;
		}
		if (event.getFinalDamage() >= dummy.getHealth()) {
			event.setCancelled(true);
			dummy.setHealth(maxHealth(dummy));
			dummy.getWorld().playSound(dummy.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.8F, 1.6F);
		}
	}

	// --- settings ------------------------------------------------------------------------

	static Mannequin spawn(Player by, int base, int power, int coins, String types, int sanity) {
		double yaw = Math.toRadians(by.getLocation().getYaw());
		Location at = by.getLocation().add(new Vector(-Math.sin(yaw), 0, Math.cos(yaw)).multiply(3));
		at.setDirection(by.getLocation().toVector().subtract(at.toVector()).setY(0));
		Mannequin dummy = by.getWorld().spawn(at, Mannequin.class, m -> {
			m.setPersistent(true);
			m.setImmovable(true);
			m.customName(Component.text("합 테스트 더미", NamedTextColor.YELLOW));
			m.setCustomNameVisible(true);
			AttributeInstance max = m.getAttribute(Attribute.MAX_HEALTH);
			if (max != null) {
				max.setBaseValue(HEALTH);
			}
			m.setHealth(HEALTH);
			PersistentDataContainer data = m.getPersistentDataContainer();
			data.set(TAG, PersistentDataType.BYTE, (byte) 1);
		});
		set(dummy, base, power, coins, types, sanity, 3.0);
		KNOWN.add(dummy.getUniqueId());
		return dummy;
	}

	static void set(LivingEntity dummy, int base, int power, int coins, String types, int sanity, double damage) {
		PersistentDataContainer data = dummy.getPersistentDataContainer();
		data.set(BASE, PersistentDataType.INTEGER, base);
		data.set(POWER, PersistentDataType.INTEGER, power);
		data.set(COINS, PersistentDataType.INTEGER, Math.max(1, Math.min(9, coins)));
		data.set(TYPES, PersistentDataType.STRING, cleanTypes(types));
		data.set(SANITY, PersistentDataType.INTEGER, Math.max(Sanity.MIN, Math.min(Sanity.MAX, sanity)));
		data.set(DAMAGE, PersistentDataType.DOUBLE, Math.max(0.0, damage));
	}

	/** Changes one setting: base, power, coins, types, sanity or damage. Returns false for an unknown one. */
	static boolean set(LivingEntity dummy, String what, String value) {
		PersistentDataContainer data = dummy.getPersistentDataContainer();
		switch (what.toLowerCase(Locale.ROOT)) {
			case "base", "기본" -> data.set(BASE, PersistentDataType.INTEGER, Integer.parseInt(value));
			case "power", "coinpower", "코인위력" -> data.set(POWER, PersistentDataType.INTEGER, Integer.parseInt(value));
			case "coins", "코인수" -> data.set(COINS, PersistentDataType.INTEGER, Math.max(1, Math.min(9, Integer.parseInt(value))));
			case "types", "유형" -> data.set(TYPES, PersistentDataType.STRING, cleanTypes(value));
			case "sanity", "정신력" -> data.set(SANITY, PersistentDataType.INTEGER,
					Math.max(Sanity.MIN, Math.min(Sanity.MAX, Integer.parseInt(value))));
			case "damage", "피해" -> data.set(DAMAGE, PersistentDataType.DOUBLE, Math.max(0.0, Double.parseDouble(value)));
			default -> {
				return false;
			}
		}
		return true;
	}

	private static String cleanTypes(String types) {
		StringBuilder out = new StringBuilder();
		for (char c : types.toUpperCase(Locale.ROOT).toCharArray()) {
			if (c == SkillCard.COIN_PLAIN || c == SkillCard.COIN_UNBREAKABLE || c == SkillCard.COIN_EXECUTION) {
				out.append(c);
			}
		}
		return out.toString();
	}

	private static int read(LivingEntity dummy, NamespacedKey key, int fallback) {
		Integer value = dummy.getPersistentDataContainer().get(key, PersistentDataType.INTEGER);
		return value == null ? fallback : value;
	}

	static int sanity(LivingEntity dummy) {
		return read(dummy, SANITY, 0);
	}

	static double damage(LivingEntity dummy) {
		Double value = dummy.getPersistentDataContainer().get(DAMAGE, PersistentDataType.DOUBLE);
		return value == null ? 3.0 : value;
	}

	/** Its fixed skill as a clash card. */
	static SkillCard card(LivingEntity dummy) {
		int coins = read(dummy, COINS, 3);
		String types = dummy.getPersistentDataContainer().getOrDefault(TYPES, PersistentDataType.STRING, "");
		double damage = damage(dummy);
		return new SkillCard("더미의 고정 스킬", COLOUR, read(dummy, BASE, 4), read(dummy, POWER, 3), coins,
				"합 테스트용. 코인당 피해 " + Math.round(damage * TotalDamage.SCALE) + ".", 20 + coins * COIN_GAP, true, types);
	}

	static String describe(LivingEntity dummy) {
		SkillCard card = card(dummy);
		return "기본 " + card.base() + ", 코인 +" + card.coinPower() + " ×" + card.coins() + ", 유형 "
				+ (card.types().isEmpty() ? "N".repeat(card.coins()) : card.types()) + ", 정신력 " + sanity(dummy)
				+ ", 코인당 피해 " + Math.round(damage(dummy) * TotalDamage.SCALE);
	}

	private static double maxHealth(LivingEntity dummy) {
		AttributeInstance max = dummy.getAttribute(Attribute.MAX_HEALTH);
		return max == null ? HEALTH : max.getValue();
	}

	// --- striking back ---------------------------------------------------------------------

	/** Won the clash (or kept broken unbreakable coins): its coins strike, one every {@link #COIN_GAP} ticks. */
	static void attack(LivingEntity dummy, LivingEntity target) {
		int coins = card(dummy).coins();
		double damage = damage(dummy);
		new BukkitRunnable() {
			int tick;

			@Override
			public void run() {
				if (!dummy.isValid() || dummy.isDead() || !target.isValid() || target.isDead()) {
					cancel();
					return;
				}
				int coin = (tick - 10) / COIN_GAP;
				if (tick >= 10 && (tick - 10) % COIN_GAP == 0 && coin < coins) {
					if (Clash.coin(dummy, coin)) {
						strike(dummy, target, damage);
					}
				}
				if (++tick > 10 + coins * COIN_GAP) {
					cancel();
				}
			}
		}.runTaskTimer(IndexPrescriptPlugin.instance(), 1L, 1L);
	}

	private static void strike(LivingEntity dummy, LivingEntity target, double damage) {
		Location at = dummy.getLocation();
		at.setDirection(target.getLocation().toVector().subtract(at.toVector()).setY(0));
		dummy.teleport(at);
		dummy.swingMainHand();
		Location mid = target.getLocation().add(0, target.getHeight() * 0.6, 0);
		Vector toward = target.getLocation().toVector().subtract(dummy.getLocation().toVector());
		World world = target.getWorld();
		world.spawnParticle(Particle.SWEEP_ATTACK, mid.clone().subtract(toward.clone().normalize().multiply(0.3)), 1);
		world.spawnParticle(Particle.DUST, mid, 12, 0.3, 0.3, 0.3, 0.0, new Particle.DustOptions(Color.fromRGB(0xD0D0D0), 1.2F));
		world.playSound(mid, Sound.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.PLAYERS, 1.0F, 0.9F);
		Damage.deal(target, damage, dummy, true);
	}
}
