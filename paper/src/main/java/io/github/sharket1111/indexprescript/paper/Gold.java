package io.github.sharket1111.indexprescript.paper;

import java.lang.reflect.Method;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * The trade plugin's currency (PlayerTrade's TradeEconomy), reached by reflection: the Shop's prices are paid
 * in it. Without the trade plugin nothing can be bought.
 */
final class Gold {
	private static Method balance;
	private static Method take;
	private static Method format;
	private static boolean tried;

	private Gold() {
	}

	private static boolean ready() {
		if (!tried) {
			tried = true;
			Plugin trade = Bukkit.getPluginManager().getPlugin("PlayerTrade");
			if (trade != null) {
				try {
					Class<?> c = Class.forName("io.github.sharket1111.trade.TradeEconomy", true, trade.getClass().getClassLoader());
					balance = c.getMethod("balance", UUID.class);
					take = c.getMethod("take", UUID.class, long.class);
					format = c.getMethod("format", long.class);
				} catch (ReflectiveOperationException e) {
					IndexPrescriptPlugin.instance().getLogger().warning("PlayerTrade has no TradeEconomy; the Shop cannot take payment");
				}
			}
		}
		return balance != null;
	}

	static boolean available() {
		return ready();
	}

	static long balance(UUID id) {
		try {
			return ready() ? (long) balance.invoke(null, id) : 0;
		} catch (ReflectiveOperationException e) {
			return 0;
		}
	}

	static boolean take(UUID id, long amount) {
		try {
			return ready() && (boolean) take.invoke(null, id, amount);
		} catch (ReflectiveOperationException e) {
			return false;
		}
	}

	static String format(long amount) {
		try {
			return ready() ? (String) format.invoke(null, amount) : amount + " G";
		} catch (ReflectiveOperationException e) {
			return amount + " G";
		}
	}
}
