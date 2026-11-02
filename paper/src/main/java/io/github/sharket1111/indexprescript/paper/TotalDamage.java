package io.github.sharket1111.indexprescript.paper;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * The TOTAL damage counter shown during big skills (Furioso-Replica,
 * Laevatein's releases, Stab and Summary Execution), as in the original: it
 * counts the damage the skill actually deals and stays a moment after it
 * ends. It sits in the bottom-left corner: the resource pack's "total" font
 * draws it larger, drops it below the action bar line and pushes it left
 * with a negative space (the action bar cannot be anchored to the corner
 * itself); Bedrock players get it centred. The prescript line waits
 * while it shows.
 */
final class TotalDamage {
	private static final int LINGER_TICKS = 50;
	private static final int REFRESH_TICKS = 10;
	private static final String FLOODGATE_PREFIX = "00000000-0000-0000-";
	private static final Key FONT = Key.key(Items.NAMESPACE, "total");
	private static final Component SHIFT_LEFT = Component.text("\ue100").font(FONT);
	private static final Map<UUID, Tally> TALLIES = new ConcurrentHashMap<>();

	private static final class Tally {
		double total;
		boolean running = true;
		long hideAt;
		long shownTotal = -1;
		long shownAt;
	}

	private TotalDamage() {
	}

	static void start(Player player) {
		TALLIES.put(player.getUniqueId(), new Tally());
	}

	static void finish(Player player) {
		Tally tally = TALLIES.get(player.getUniqueId());
		if (tally != null && tally.running) {
			tally.running = false;
			tally.hideAt = CaduceusListener.now() + LINGER_TICKS;
		}
	}

	/** Damage a player's skill dealt, counted while one of their big skills runs. */
	static void add(Player player, double amount) {
		Tally tally = TALLIES.get(player.getUniqueId());
		if (tally != null && tally.running && amount > 0) {
			tally.total += amount;
		}
	}

	static boolean showing(Player player) {
		return TALLIES.containsKey(player.getUniqueId());
	}

	/** Called every tick. */
	static void tick() {
		long now = CaduceusListener.now();
		Iterator<Map.Entry<UUID, Tally>> it = TALLIES.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Tally> entry = it.next();
			Player player = Bukkit.getPlayer(entry.getKey());
			Tally tally = entry.getValue();
			if (player == null || (!tally.running && now >= tally.hideAt)) {
				if (player != null) {
					player.sendActionBar(Component.empty());
				}
				it.remove();
				continue;
			}
			long shown = Math.round(tally.total);
			if (shown != tally.shownTotal || now - tally.shownAt >= REFRESH_TICKS) {
				player.sendActionBar(line(player, shown));
				tally.shownTotal = shown;
				tally.shownAt = now;
			}
		}
	}

	private static Component line(Player player, long total) {
		Component text = Component.text("TOTAL ", NamedTextColor.GRAY).decorate(TextDecoration.ITALIC)
				.append(Component.text(total, NamedTextColor.GOLD).decorate(TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
		boolean bedrock = player.getUniqueId().toString().startsWith(FLOODGATE_PREFIX);
		// Java: the pack's "total" font draws it larger and lower, and pushes it left.
		return bedrock ? text : Component.empty().append(SHIFT_LEFT).append(text.font(FONT));
	}
}
