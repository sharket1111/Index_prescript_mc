package io.github.sharket1111.indexprescript.paper;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Where [Mang]'s rings sit, adjustable in game with /ip mang tune so the right
 * numbers can be found by eye (they last until the server restarts; the
 * values found are then made the defaults here).
 *
 * third (on the arm, in blocks for a player-sized holder): up, right and
 * forward of the hand from the feet; along, how far up the blade the rings'
 * middle is; tilt, the blade's angle above the horizontal at rest; scale and
 * spacing of the rings.
 * first (the holder's own view, in blocks from the eye): dist ahead, right,
 * down; tilt above the line of sight; lean, sideways (minus leans right);
 * scale and spacing.
 */
final class MangTuning {
	static final Map<String, Double> THIRD = new LinkedHashMap<>();
	static final Map<String, Double> FIRST = new LinkedHashMap<>();

	static {
		reset();
	}

	private MangTuning() {
	}

	static void reset() {
		THIRD.clear();
		THIRD.put("up", 0.77);
		THIRD.put("right", 0.36);
		THIRD.put("forward", 0.05);
		THIRD.put("along", 0.42);
		THIRD.put("tilt", 10.0);
		THIRD.put("scale", 0.37);
		THIRD.put("spacing", 0.15);
		FIRST.clear();
		FIRST.put("dist", 0.28);
		FIRST.put("right", 0.33);
		FIRST.put("down", 0.09);
		FIRST.put("tilt", 120.0);
		FIRST.put("lean", -30.0);
		FIRST.put("scale", 0.12);
		FIRST.put("spacing", 0.07);
	}

	static double third(String key) {
		return THIRD.get(key);
	}

	static double first(String key) {
		return FIRST.get(key);
	}

	static Map<String, Double> side(String name) {
		return switch (name.toLowerCase(Locale.ROOT)) {
			case "third", "3" -> THIRD;
			case "first", "1" -> FIRST;
			default -> null;
		};
	}

	/** Sets one value; returns false if the side or key is unknown. */
	static boolean set(String side, String key, double value) {
		Map<String, Double> values = side(side);
		if (values == null || !values.containsKey(key.toLowerCase(Locale.ROOT))) {
			return false;
		}
		values.put(key.toLowerCase(Locale.ROOT), value);
		return true;
	}

	/** All values of one side on a line, to copy. */
	static String line(Map<String, Double> values) {
		StringJoiner joiner = new StringJoiner(" ");
		values.forEach((key, value) -> joiner.add(key + "=" + String.format(Locale.ROOT, "%.3f", value)));
		return joiner.toString();
	}
}
