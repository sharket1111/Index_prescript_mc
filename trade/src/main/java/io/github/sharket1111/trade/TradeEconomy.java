package io.github.sharket1111.trade;

import java.util.UUID;

/**
 * The currency for other plugins on the server (IndexPrescript's Shop reaches it by reflection, so neither
 * plugin needs the other to build): balance, take, give and the formatted amount.
 */
public final class TradeEconomy {
	private TradeEconomy() {
	}

	public static long balance(UUID id) {
		return Money.balance(id);
	}

	/** Takes {@code amount} if the player has it; false (nothing taken) if not. */
	public static boolean take(UUID id, long amount) {
		return Money.take(id, amount);
	}

	public static void give(UUID id, long amount) {
		Money.add(id, amount);
	}

	public static String format(long amount) {
		return Money.format(amount);
	}
}
