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

	/**
	 * Finds the trade plugin's money: its public TradeEconomy, or, on a PlayerTrade built before that existed,
	 * its Money class directly. A failed lookup is tried again on the next purchase.
	 */
	private static boolean ready() {
		if (balance != null) {
			return true;
		}
		Plugin trade = Bukkit.getPluginManager().getPlugin("PlayerTrade");
		if (trade == null || !trade.isEnabled()) {
			if (!tried) {
				tried = true;
				IndexPrescriptPlugin.instance().getLogger().warning("PlayerTrade is not running; the Shop cannot take payment");
			}
			return false;
		}
		ClassLoader loader = trade.getClass().getClassLoader();
		for (String name : new String[] {"io.github.sharket1111.trade.TradeEconomy", "io.github.sharket1111.trade.Money"}) {
			try {
				Class<?> c = Class.forName(name, true, loader);
				Method b = c.getDeclaredMethod("balance", UUID.class);
				Method t = c.getDeclaredMethod("take", UUID.class, long.class);
				Method f = c.getDeclaredMethod("format", long.class);
				b.setAccessible(true);
				t.setAccessible(true);
				f.setAccessible(true);
				take = t;
				format = f;
				balance = b;
				IndexPrescriptPlugin.instance().getLogger().info("Shop payment through PlayerTrade (" + c.getSimpleName() + ")");
				return true;
			} catch (ReflectiveOperationException | RuntimeException e) {
				// try the next
			}
		}
		if (!tried) {
			tried = true;
			IndexPrescriptPlugin.instance().getLogger().warning("PlayerTrade has no money the Shop can use; update PlayerTrade.jar");
		}
		return false;
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
