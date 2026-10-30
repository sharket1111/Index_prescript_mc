package io.github.sharket1111.indexprescript;

import java.util.Locale;

import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;

/** Prescripts the terminal can issue. Each one is a trackable combat goal. */
public enum Prescript {
	STRIKE_THRICE(Goal.HITS, 3, null),
	BREAK_WITH_HAMMER(Goal.STAGGER, 1, null),
	TWO_CRITS(Goal.CRITS, 2, null),
	SLAY_TWO(Goal.KILLS, 2, null),
	CROUCHING_STRIKE(Goal.SNEAK_HIT, 1, null),
	AERIAL_STRIKE(Goal.AIR_HIT, 1, null),
	REAP(Goal.FORM_HIT, 1, CaduceusForm.SCYTHE),
	LASH(Goal.FORM_HIT, 1, CaduceusForm.WHIP),
	PIERCE(Goal.FORM_HIT, 1, CaduceusForm.LANCE),
	SINK_TWICE(Goal.FORM_HIT, 2, CaduceusForm.STILETTO),
	DESPERATE_KILL(Goal.LOW_HEALTH_KILL, 1, null),
	UNTOUCHED(Goal.CLEAN_HITS, 3, null);

	enum Goal {
		HITS,
		STAGGER,
		CRITS,
		KILLS,
		SNEAK_HIT,
		AIR_HIT,
		FORM_HIT,
		LOW_HEALTH_KILL,
		CLEAN_HITS
	}

	private static final Prescript[] VALUES = values();

	final Goal goal;
	final int amount;
	final CaduceusForm form;

	Prescript(Goal goal, int amount, CaduceusForm form) {
		this.goal = goal;
		this.amount = amount;
		this.form = form;
	}

	String key() {
		return "prescript.index_prescript." + name().toLowerCase(Locale.ROOT);
	}

	Component text() {
		return Component.translatable(key(), amount);
	}

	/** Picks a random prescript, never repeating the previous one. */
	static Prescript random(RandomSource random, Prescript previous) {
		if (previous == null) {
			return VALUES[random.nextInt(VALUES.length)];
		}
		int index = random.nextInt(VALUES.length - 1);
		return VALUES[index >= previous.ordinal() ? index + 1 : index];
	}
}
