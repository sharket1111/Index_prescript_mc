package io.github.sharket1111.indexprescript.paper;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.LivingEntity;

/**
 * Limbus-style statuses tracked per entity on the server. A "turn" is
 * {@link #TURN_TICKS}; "until next turn" statuses last one turn from their
 * latest application, and "twice per turn" statuses cap at 2 stacks.
 * State lives in memory only and is dropped on server restart.
 */
final class CombatState {
	static final int TURN_TICKS = 60;
	private static final int MAX_POISE_POTENCY = 20;
	private static final int MAX_COUNT = 99;
	private static final int MAX_TURN_STACKS = 2;
	private static final int SINKING_TICKS = 200;
	private static final int STAGGER_RESET_TICKS = 200;

	private static final Map<UUID, CombatState> STATES = new ConcurrentHashMap<>();

	private int poisePotency;
	private int poiseCount;
	private long poiseDecayAt;

	private int sinkingPotency;
	private int sinkingCount;
	private long sinkingUntil;

	private float stagger;
	private long staggerResetAt;
	private long staggeredUntil;

	private final TurnStacks attackLevel = new TurnStacks();
	private final TurnStacks defenseDown = new TurnStacks();
	private final TurnStacks[] fragile = {new TurnStacks(), new TurnStacks(), new TurnStacks()};

	static CombatState of(LivingEntity entity) {
		return STATES.computeIfAbsent(entity.getUniqueId(), id -> new CombatState());
	}

	/** Drops entries with nothing active, so dead or idle mobs don't accumulate. */
	static void prune(long now) {
		STATES.values().removeIf(state -> state.idle(now));
	}

	// --- Poise: potency x 5% critical chance; count drops each turn and on every critical hit.

	void addPoise(int potency, int count, long now) {
		decayPoise(now);
		if (poiseCount == 0) {
			poiseDecayAt = now + TURN_TICKS;
		}
		poisePotency = Math.min(MAX_POISE_POTENCY, poisePotency + potency);
		poiseCount = Math.min(MAX_COUNT, poiseCount + count);
	}

	int poisePotency(long now) {
		decayPoise(now);
		return poisePotency;
	}

	int poiseCount(long now) {
		decayPoise(now);
		return poiseCount;
	}

	void consumePoise(long now) {
		decayPoise(now);
		if (poiseCount > 0 && --poiseCount == 0) {
			poisePotency = 0;
		}
	}

	private void decayPoise(long now) {
		while (poiseCount > 0 && now >= poiseDecayAt) {
			poiseCount--;
			poiseDecayAt += TURN_TICKS;
		}
		if (poiseCount == 0) {
			poisePotency = 0;
		}
	}

	// --- Sinking: each hit taken deals extra damage based on potency and uses up one count.

	void addSinking(int potency, int count, long now) {
		if (now >= sinkingUntil) {
			sinkingPotency = 0;
			sinkingCount = 0;
		}
		sinkingPotency = Math.min(MAX_COUNT, sinkingPotency + potency);
		sinkingCount = Math.min(MAX_COUNT, sinkingCount + count);
		sinkingUntil = now + SINKING_TICKS;
	}

	/** Returns the sinking potency triggered by this hit, or 0. */
	int consumeSinking(long now) {
		if (sinkingCount == 0 || now >= sinkingUntil) {
			return 0;
		}
		int potency = sinkingPotency;
		if (--sinkingCount == 0) {
			sinkingPotency = 0;
		}
		return potency;
	}

	int sinkingPotency(long now) {
		return sinkingCount > 0 && now < sinkingUntil ? sinkingPotency : 0;
	}

	// --- Stagger: accumulated stagger damage; reaching the threshold staggers for one turn.

	/** Adds stagger damage; returns true if this pushed the entity into the staggered state. */
	boolean addStagger(float amount, float threshold, long now) {
		if (now >= staggerResetAt) {
			stagger = 0;
		}
		stagger += amount;
		staggerResetAt = now + STAGGER_RESET_TICKS;
		if (stagger >= threshold) {
			stagger = 0;
			staggeredUntil = now + TURN_TICKS;
			return true;
		}
		return false;
	}

	boolean staggered(long now) {
		return now < staggeredUntil;
	}

	// --- Turn-limited stacks

	void addAttackLevel(long now) {
		attackLevel.add(now);
	}

	int attackLevel(long now) {
		return attackLevel.get(now);
	}

	void addDefenseDown(long now) {
		defenseDown.add(now);
	}

	int defenseDown(long now) {
		return defenseDown.get(now);
	}

	void addFragile(DamageKind kind, long now) {
		fragile[kind.ordinal()].add(now);
	}

	/** Fragile of every type, raised to at least {@code stacks} (beyond the per-turn cap) for {@code ticks}. */
	void setFragileAll(int stacks, long now, int ticks) {
		for (TurnStacks f : fragile) {
			f.raise(stacks, now, ticks);
		}
	}

	int fragile(DamageKind kind, long now) {
		return fragile[kind.ordinal()].get(now);
	}

	private boolean idle(long now) {
		if (poiseCount(now) > 0 || (sinkingCount > 0 && now < sinkingUntil) || now < staggerResetAt || staggered(now)
				|| attackLevel(now) > 0 || defenseDown(now) > 0) {
			return false;
		}
		for (DamageKind kind : DamageKind.values()) {
			if (fragile(kind, now) > 0) {
				return false;
			}
		}
		return true;
	}

	private static final class TurnStacks {
		private int stacks;
		private long until;

		void add(long now) {
			stacks = Math.min(MAX_TURN_STACKS, get(now) + 1);
			until = now + TURN_TICKS;
		}

		void raise(int value, long now, int ticks) {
			stacks = Math.max(get(now), value);
			until = Math.max(until, now + ticks);
		}

		int get(long now) {
			return now < until ? stacks : 0;
		}
	}
}
