package io.github.sharket1111.indexprescript.paper;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Display;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * The original's unit UI, under each player's feet (seen through the ground):
 * a ring gauge of health with the health in the original's scale (a heart
 * point is {@link TotalDamage#SCALE} HP), the Sanity circle, and below them
 * the statuses with their potency: Burn, Bleed, Poise, Sinking, Bind,
 * Paralysis, Fragile, Mang, Stagger, attack up, defense down. Shown while the
 * player is fighting, holds one of the plugin's weapons, has Sanity off 0,
 * or is the Black God. Glyphs from the pack's "clash" font (tools/clash_font.py).
 */
final class UnitHud {
	private static final Key FONT = Key.key(Items.NAMESPACE, "clash");
	private static final char HP_FIRST = '';
	private static final char SP_FIRST = '';
	private static final char STATUS_FIRST = '';
	private static final int BURN = 0;
	private static final int BLEED = 1;
	private static final int POISE = 2;
	private static final int SINKING = 3;
	private static final int BIND = 4;
	private static final int PARALYSIS = 5;
	private static final int FRAGILE = 6;
	private static final int MANG = 7;
	private static final int STAGGER = 8;
	private static final int ATTACK_UP = 9;
	private static final int DEFENSE_DOWN = 10;
	private static final double BELOW = 0.6;
	private static final Map<UUID, TextDisplay> SHOWN = new ConcurrentHashMap<>();

	private UnitHud() {
	}

	static void tick() {
		long now = CaduceusListener.now();
		for (Player player : Bukkit.getOnlinePlayers()) {
			boolean wanted = Hud.showsSanity(player) && player.getGameMode() != GameMode.SPECTATOR || Cinematic.body(player) != player;
			TextDisplay display = SHOWN.get(player.getUniqueId());
			if (!wanted || player.isDead()) {
				if (display != null) {
					display.remove();
					SHOWN.remove(player.getUniqueId());
				}
				continue;
			}
			LivingEntity body = Cinematic.body(player);
			Location at = body.getLocation().add(0, -BELOW, 0);
			at.setYaw(0);
			at.setPitch(0);
			if (display == null || !display.isValid() || display.getWorld() != at.getWorld()) {
				if (display != null) {
					display.remove();
				}
				display = spawn(at);
				SHOWN.put(player.getUniqueId(), display);
				display.text(text(player, body, now));
			} else {
				if (display.getLocation().distanceSquared(at) > 0.0004) {
					display.teleport(at);
				}
				if (now % 5 == 0) {
					display.text(text(player, body, now));
				}
			}
		}
		Iterator<Map.Entry<UUID, TextDisplay>> it = SHOWN.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, TextDisplay> entry = it.next();
			if (Bukkit.getPlayer(entry.getKey()) == null) {
				entry.getValue().remove();
				it.remove();
			}
		}
	}

	static void clearAll() {
		SHOWN.values().forEach(TextDisplay::remove);
		SHOWN.clear();
	}

	private static TextDisplay spawn(Location at) {
		return at.getWorld().spawn(at, TextDisplay.class, d -> {
			d.setPersistent(false);
			d.setBillboard(Display.Billboard.CENTER);
			d.setSeeThrough(true);
			d.setShadowed(true);
			d.setTeleportDuration(1);
			d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
			d.setBrightness(new Display.Brightness(15, 15));
			d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.5F, 0.5F, 0.5F), new Quaternionf()));
		});
	}

	private static Component glyph(char c) {
		return Component.text(String.valueOf(c), NamedTextColor.WHITE).font(FONT);
	}

	private static Component text(Player player, LivingEntity body, long now) {
		AttributeInstance maxAttribute = player.getAttribute(Attribute.MAX_HEALTH);
		double max = maxAttribute == null ? 20.0 : maxAttribute.getValue();
		double health = player.getHealth() + player.getAbsorptionAmount();
		int ring = (int) Math.round(Math.max(0.0, Math.min(1.0, player.getHealth() / max)) * 20);
		int sanity = Sanity.of(player);
		Component top = Component.empty()
				.append(glyph((char) (HP_FIRST + ring)))
				.append(Component.text(" " + Math.round(health * TotalDamage.SCALE), NamedTextColor.WHITE).decorate(TextDecoration.BOLD))
				.append(Component.text("/" + Math.round(max * TotalDamage.SCALE) + " ", NamedTextColor.GRAY))
				.append(glyph((char) (SP_FIRST + sanity - Sanity.MIN)));
		Component statuses = statuses(player, now);
		return statuses == null ? top : top.appendNewline().append(statuses);
	}

	private static Component statuses(Player player, long now) {
		CombatState state = CombatState.of(player);
		boolean staggered = state.staggered(now);
		Component line = Component.empty();
		int shown = 0;
		int[] values = new int[11];
		values[BURN] = (player.getFireTicks() + 19) / 20;
		values[BLEED] = level(player, PotionEffectType.WITHER);
		values[POISE] = state.poisePotency(now);
		values[SINKING] = state.sinkingPotency(now);
		values[BIND] = staggered ? 0 : level(player, PotionEffectType.SLOWNESS);
		values[PARALYSIS] = staggered ? 0 : level(player, PotionEffectType.WEAKNESS);
		values[FRAGILE] = state.fragile(DamageKind.SLASH, now) + state.fragile(DamageKind.PIERCE, now) + state.fragile(DamageKind.BLUNT, now);
		values[MANG] = level(player, PotionEffectType.STRENGTH);
		values[STAGGER] = staggered ? 1 : 0;
		values[ATTACK_UP] = state.attackLevel(now);
		values[DEFENSE_DOWN] = state.defenseDown(now);
		for (int i = 0; i < values.length; i++) {
			if (values[i] <= 0) {
				continue;
			}
			if (shown++ > 0) {
				line = line.append(Component.text(" "));
			}
			line = line.append(glyph((char) (STATUS_FIRST + i)));
			if (i != STAGGER) {
				line = line.append(Component.text(String.valueOf(values[i]), TextColor.color(0xF0E0C0)));
			}
		}
		return shown == 0 ? null : line;
	}

	private static int level(Player player, PotionEffectType type) {
		PotionEffect effect = player.getPotionEffect(type);
		return effect == null ? 0 : effect.getAmplifier() + 1;
	}
}
