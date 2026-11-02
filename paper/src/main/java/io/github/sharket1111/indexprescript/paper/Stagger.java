package io.github.sharket1111.indexprescript.paper;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Display;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;

/**
 * Stagger, in three stages: staggered again while still staggered, a foe goes
 * from Stagger to Stagger+ to Stagger++. Each stage lasts a turn and binds it
 * (Slowness, which the resource pack shows as "Stagger" in the effect list) at
 * level I, II and IV, with Weakness II. The stage's picture floats over the
 * foe's body, seen through it; Bedrock players, who cannot show it, see
 * coloured sparks instead.
 */
final class Stagger implements Listener {
	private static final int MAX_STAGE = 3;
	private static final int[] SLOWNESS_AMPLIFIER = {0, 1, 3};
	private static final Key FONT = Key.key(Items.NAMESPACE, "stagger");
	private static final String FLOODGATE_PREFIX = "00000000-0000-0000-";
	private static final Particle.DustOptions[] SPARK = {
		new Particle.DustOptions(Color.fromRGB(0xFF9A1E), 1.2F),
		new Particle.DustOptions(Color.fromRGB(0xE8202A), 1.2F),
		new Particle.DustOptions(Color.fromRGB(0x3FE0A8), 1.2F),
	};
	private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

	private static final class State {
		final LivingEntity entity;
		int stage;
		long until;
		TextDisplay sign;

		State(LivingEntity entity) {
			this.entity = entity;
		}
	}

	/** The foe was staggered: raises its stage if it still was, binds it, and shows the stage. */
	static void apply(LivingEntity target, long now) {
		State state = STATES.computeIfAbsent(target.getUniqueId(), id -> new State(target));
		state.stage = now < state.until ? Math.min(MAX_STAGE, state.stage + 1) : 1;
		state.until = now + CombatState.TURN_TICKS;
		int index = state.stage - 1;
		target.removePotionEffect(PotionEffectType.SLOWNESS);
		target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, CombatState.TURN_TICKS, SLOWNESS_AMPLIFIER[index]));
		target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, CombatState.TURN_TICKS, 1));
		if (state.sign == null || !state.sign.isValid()) {
			state.sign = spawnSign(target);
		}
		state.sign.text(glyph(state.stage));
	}

	/** The current stage (1-3), or 0 if not staggered. */
	static int stage(LivingEntity entity, long now) {
		State state = STATES.get(entity.getUniqueId());
		return state == null || now >= state.until ? 0 : state.stage;
	}

	private static Component glyph(int stage) {
		return Component.text(String.valueOf((char) (0xE000 + stage))).font(FONT);
	}

	private static Location body(LivingEntity entity) {
		return entity.getLocation().add(0, entity.getHeight() * 0.45, 0);
	}

	private static TextDisplay spawnSign(LivingEntity target) {
		TextDisplay sign = target.getWorld().spawn(body(target), TextDisplay.class, display -> {
			display.setPersistent(false);
			display.setBillboard(Display.Billboard.CENTER);
			display.setSeeThrough(true);
			display.setShadowed(false);
			display.setDefaultBackground(false);
			display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
			display.setBrightness(new Display.Brightness(15, 15));
			display.setTeleportDuration(2);
		});
		for (Player player : Bukkit.getOnlinePlayers()) {
			if (bedrock(player)) {
				player.hideEntity(IndexPrescriptPlugin.instance(), sign);
			}
		}
		return sign;
	}

	private static boolean bedrock(Player player) {
		return player.getUniqueId().toString().startsWith(FLOODGATE_PREFIX);
	}

	/** Called every tick: keeps pictures on the body, ends stagger, and shows sparks. */
	static void tick() {
		long now = CaduceusListener.now();
		Iterator<State> it = STATES.values().iterator();
		while (it.hasNext()) {
			State state = it.next();
			LivingEntity entity = state.entity;
			if (now >= state.until || !entity.isValid() || entity.isDead()) {
				removeSign(state);
				it.remove();
				continue;
			}
			if (state.sign != null && state.sign.isValid()) {
				state.sign.teleport(body(entity));
			}
			if (now % 8 == 0) {
				entity.getWorld().spawnParticle(Particle.DUST, body(entity), 6, 0.3, 0.35, 0.3, 0.0, SPARK[state.stage - 1]);
			}
		}
	}

	private static void removeSign(State state) {
		if (state.sign != null) {
			state.sign.remove();
			state.sign = null;
		}
	}

	static void clearAll() {
		STATES.values().forEach(Stagger::removeSign);
		STATES.clear();
	}

	/** Bedrock players joining later do not get the pictures either. */
	@EventHandler
	public void onJoin(PlayerJoinEvent event) {
		Player player = event.getPlayer();
		if (!bedrock(player)) {
			return;
		}
		for (State state : STATES.values()) {
			if (state.sign != null && state.sign.isValid()) {
				player.hideEntity(IndexPrescriptPlugin.instance(), state.sign);
			}
		}
	}
}
