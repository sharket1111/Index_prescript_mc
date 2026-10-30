package io.github.sharket1111.indexprescript;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Per-player prescript progress and [Hermes] stacks. Prescripts are only
 * shown and tracked while the terminal is held in the off hand. Completing one
 * while in combat grants a [Hermes] stack; at {@link #MAX_HERMES} the next
 * Caduceus attack unleashes Furioso-Replica. The off-hand terminal's stack
 * size mirrors the [Hermes] count, which also restores it after a restart;
 * prescript progress lives in memory only.
 *
 * A new prescript is revealed like the original: random letters and symbols
 * appear one at a time, then resolve into the text from left to right.
 */
final class PrescriptTracker {
	static final int MAX_HERMES = 9;
	private static final int COMBAT_TICKS = 200;
	private static final int OVERLAY_INTERVAL = 10;
	private static final int OVERLAY_HOLD_TICKS = 30;
	private static final int AURA_INTERVAL = 2;
	private static final int REVEAL_HOLD_TICKS = 8;
	private static final String GLYPHS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ#$%&*@?!/<>+=";

	private static final Map<UUID, PrescriptTracker> TRACKERS = new ConcurrentHashMap<>();

	private Prescript prescript;
	private int progress;
	private int hermes;
	private long lastCombat = Long.MIN_VALUE / 2;
	private long hermesBlockedUntil;
	private long overlayBlockedUntil;
	private boolean restored;
	private String revealText;
	private long revealStart;

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(PrescriptTracker::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (source.getEntity() instanceof ServerPlayer player && player != entity) {
				onKill(player);
			}
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (entity instanceof ServerPlayer player && damageTaken > 0.0F) {
				onDamaged(player, source);
			}
		});
	}

	static PrescriptTracker of(Player player) {
		return TRACKERS.computeIfAbsent(player.getUUID(), id -> new PrescriptTracker());
	}

	static boolean holdsTerminal(Player player) {
		return player.getOffhandItem().getItem() instanceof TerminalItem;
	}

	/** Full [Hermes] with the terminal in the off hand and Caduceus in the main hand. */
	static boolean ready(Player player) {
		return holdsTerminal(player) && player.getMainHandItem().getItem() instanceof CaduceusItem && of(player).hermes >= MAX_HERMES;
	}

	int hermes() {
		return hermes;
	}

	Prescript prescript() {
		return prescript;
	}

	int progress() {
		return progress;
	}

	/** Keeps the prescript line from overwriting another action bar message for a moment. */
	static void holdOverlay(Player player, long now) {
		of(player).overlayBlockedUntil = now + OVERLAY_HOLD_TICKS;
	}

	static void markCombat(Player player, long now) {
		of(player).lastCombat = now;
	}

	/** Furioso-Replica consumes every [Hermes] and blocks new ones until the given time. */
	static void consumeHermes(Player player, long blockedUntil) {
		PrescriptTracker tracker = of(player);
		tracker.hermes = 0;
		tracker.hermesBlockedUntil = Math.max(tracker.hermesBlockedUntil, blockedUntil);
	}

	static void blockHermes(Player player, long until) {
		PrescriptTracker tracker = of(player);
		tracker.hermesBlockedUntil = Math.max(tracker.hermesBlockedUntil, until);
	}

	private static void tick(MinecraftServer server) {
		Furioso.tickAll();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!holdsTerminal(player) || !(player.level() instanceof ServerLevel level)) {
				continue;
			}
			long now = level.getGameTime();
			PrescriptTracker tracker = of(player);
			if (!tracker.restored) {
				// A terminal stack of n means n [Hermes]; a single terminal reads as 0.
				int count = player.getOffhandItem().getCount();
				tracker.hermes = Math.max(tracker.hermes, count > 1 ? Math.min(count, MAX_HERMES) : 0);
				tracker.restored = true;
			}
			if (tracker.prescript == null) {
				tracker.issue(player, level, now);
			}
			if (tracker.revealing(now) && Furioso.active(player)) {
				tracker.revealStart++; // pause the reveal so it doesn't cover the Furioso TOTAL
			} else if (tracker.revealing(now)) {
				player.sendOverlayMessage(tracker.revealFrame(now, level.getRandom()));
			} else if (now % OVERLAY_INTERVAL == 0 && now >= tracker.overlayBlockedUntil) {
				player.sendOverlayMessage(tracker.line());
			}
			if (now % AURA_INTERVAL == 0 && ready(player) && !Furioso.active(player)) {
				CaduceusEffects.aura(level, player);
			}
		}
	}

	MutableComponent line() {
		return Component.translatable("message.index_prescript.prescript_line", prescript.text(), progress, prescript.amount)
				.withStyle(ChatFormatting.AQUA);
	}

	/** Issues a new prescript whose reveal starts at {@code startAt}. */
	private void issue(ServerPlayer player, ServerLevel level, long startAt) {
		prescript = Prescript.random(level.getRandom(), prescript);
		progress = 0;
		revealText = ModLang.format(player.clientInformation().language(), prescript.key(), prescript.amount);
		revealStart = startAt;
	}

	private int revealLength() {
		return revealText == null ? 0 : revealText.length() * 2 + REVEAL_HOLD_TICKS;
	}

	private boolean revealing(long now) {
		return revealText != null && now >= revealStart && now < revealStart + revealLength();
	}

	/**
	 * Letters appear one per tick as random glyphs, hold briefly while still
	 * scrambling, then resolve into the real text one letter per tick.
	 */
	private Component revealFrame(long now, RandomSource random) {
		int length = revealText.length();
		int elapsed = (int) (now - revealStart);
		int shown = Math.min(length, elapsed + 1);
		int resolved = Math.max(0, elapsed - length - REVEAL_HOLD_TICKS + 1);
		StringBuilder frame = new StringBuilder(length);
		for (int i = 0; i < shown; i++) {
			char c = revealText.charAt(i);
			frame.append(i < resolved || Character.isWhitespace(c) ? c : GLYPHS.charAt(random.nextInt(GLYPHS.length())));
		}
		return Component.translatable("message.index_prescript.prescript_reveal", Component.literal(frame.toString()))
				.withStyle(resolved > 0 ? ChatFormatting.AQUA : ChatFormatting.DARK_AQUA);
	}

	Component hermesText() {
		return Component.translatable("message.index_prescript.hermes", hermes, MAX_HERMES)
				.withStyle(hermes >= MAX_HERMES ? ChatFormatting.DARK_RED : ChatFormatting.GRAY);
	}

	/** A charged Caduceus hit landed. */
	static void onHit(ServerPlayer player, ServerLevel level, CaduceusForm form, boolean crit, boolean staggered) {
		long now = level.getGameTime();
		markCombat(player, now);
		if (!holdsTerminal(player)) {
			return;
		}
		PrescriptTracker tracker = of(player);
		if (tracker.prescript == null) {
			return;
		}
		Prescript p = tracker.prescript;
		boolean advanced = switch (p.goal) {
			case HITS, CLEAN_HITS -> true;
			case STAGGER -> staggered;
			case CRITS -> crit;
			case SNEAK_HIT -> player.isShiftKeyDown();
			case AIR_HIT -> !player.onGround();
			case FORM_HIT -> form == p.form;
			case KILLS, LOW_HEALTH_KILL -> false;
		};
		if (advanced) {
			tracker.advance(player, level);
		}
	}

	private static void onKill(ServerPlayer player) {
		if (!holdsTerminal(player) || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		PrescriptTracker tracker = of(player);
		if (tracker.prescript == null) {
			return;
		}
		boolean advanced = switch (tracker.prescript.goal) {
			case KILLS -> true;
			case LOW_HEALTH_KILL -> player.getHealth() <= player.getMaxHealth() * 0.5F;
			default -> false;
		};
		if (advanced) {
			tracker.advance(player, level);
		}
	}

	private static void onDamaged(ServerPlayer player, DamageSource source) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		if (source.getEntity() instanceof LivingEntity attacker && attacker != player) {
			markCombat(player, level.getGameTime());
		}
		PrescriptTracker tracker = of(player);
		if (tracker.prescript != null && tracker.prescript.goal == Prescript.Goal.CLEAN_HITS) {
			tracker.progress = 0;
		}
	}

	private void advance(ServerPlayer player, ServerLevel level) {
		if (++progress < prescript.amount) {
			return;
		}
		long now = level.getGameTime();
		boolean inCombat = now - lastCombat <= COMBAT_TICKS;
		boolean granted = inCombat && now >= hermesBlockedUntil && hermes < MAX_HERMES;
		if (granted) {
			hermes++;
		}

		MutableComponent message = Component.translatable(granted
				? "message.index_prescript.prescript_complete"
				: "message.index_prescript.prescript_complete_no_hermes").withStyle(ChatFormatting.GOLD);
		player.sendOverlayMessage(message.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY)).append(hermesText()));
		overlayBlockedUntil = now + OVERLAY_HOLD_TICKS;
		level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, granted ? 1.2F : 0.8F);
		if (granted && hermes == MAX_HERMES) {
			level.playSound(null, player.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.5F, 0.6F);
		}

		issue(player, level, now + OVERLAY_HOLD_TICKS);
	}
}
