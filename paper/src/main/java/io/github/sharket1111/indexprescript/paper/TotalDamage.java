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
 *
 * Numbers are in the original's scale: a Minecraft health point counts as
 * {@link #SCALE} HP (a TOTAL of 127 is 12.7 health). Damage a player takes
 * from someone's skill is counted too and shown at the top right, as the
 * name of a pink boss bar the resource pack draws see-through, pushed right
 * and down by the "total" font.
 */
final class TotalDamage {
	private static final int LINGER_TICKS = 40;
	private static final int REFRESH_TICKS = 10;
	private static final String FLOODGATE_PREFIX = "00000000-0000-0000-";
	private static final Key FONT = Key.key(Items.NAMESPACE, "total");
	private static final Map<UUID, Tally> TALLIES = new ConcurrentHashMap<>();
	private static final Map<UUID, Received> RECEIVED = new ConcurrentHashMap<>();
	/** HP per Minecraft health point, as the numbers are shown. */
	static final int SCALE = 10;
	private static final int RECEIVED_LINGER = 60;

	private static final class Received {
		final net.kyori.adventure.bossbar.BossBar bar = net.kyori.adventure.bossbar.BossBar.bossBar(Component.empty(), 0F,
				net.kyori.adventure.bossbar.BossBar.Color.PINK, net.kyori.adventure.bossbar.BossBar.Overlay.PROGRESS);
		double total;
		long lastAt;
		long shown = -1;
	}

	/** Damage a player took from someone's skill: counted at the top right of their screen. */
	static void received(Player victim, double amount) {
		if (amount <= 0) {
			return;
		}
		Received received = RECEIVED.computeIfAbsent(victim.getUniqueId(), id -> {
			Received fresh = new Received();
			victim.showBossBar(fresh.bar);
			return fresh;
		});
		received.total += amount;
		received.lastAt = CaduceusListener.now();
	}

	private static void tickReceived(long now) {
		Iterator<Map.Entry<UUID, Received>> it = RECEIVED.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Received> entry = it.next();
			Player player = Bukkit.getPlayer(entry.getKey());
			Received received = entry.getValue();
			if (player == null || now - received.lastAt > RECEIVED_LINGER) {
				if (player != null) {
					player.hideBossBar(received.bar);
				}
				it.remove();
				continue;
			}
			long shown = Math.round(received.total * SCALE);
			if (shown != received.shown) {
				received.shown = shown;
				Component text = Component.text("TOTAL ", NamedTextColor.GRAY).decorate(TextDecoration.ITALIC)
						.append(Component.text(shown, NamedTextColor.RED).decorate(TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
				boolean bedrock = player.getUniqueId().toString().startsWith(FLOODGATE_PREFIX);
				received.bar.name(bedrock ? text : Component.text("\ue110").font(FONT).append(text.font(FONT)));
			}
		}
	}

	private static final class Tally {
		double total;
		boolean running = true;
		long hideAt;
		long shownTotal = -1;
		long shownAt;
		/** Slide-out frame once lingering is over, -1 before. */
		int leaving = -1;
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
		tickReceived(now);
		Iterator<Map.Entry<UUID, Tally>> it = TALLIES.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Tally> entry = it.next();
			Player player = Bukkit.getPlayer(entry.getKey());
			Tally tally = entry.getValue();
			if (player == null) {
				it.remove();
				continue;
			}
			if (!tally.running && now >= tally.hideAt) {
				// Slide out to the left together with the skill screen, then clear.
				if (tally.leaving < 0) {
					SkillOverlay.hide(player);
				}
				if (++tally.leaving >= SkillOverlay.FRAMES - 1) {
					Hud.message(player, Component.empty());
					it.remove();
				} else {
					Hud.message(player, line(player, Math.round(tally.total * SCALE), tally.leaving));
				}
				continue;
			}
			long shown = Math.round(tally.total * SCALE);
			if (shown != tally.shownTotal || now - tally.shownAt >= REFRESH_TICKS) {
				Hud.message(player, line(player, shown, 0));
				tally.shownTotal = shown;
				tally.shownAt = now;
			}
		}
	}

	private static Component line(Player player, long total, int slide) {
		Component text = Component.text("TOTAL ", NamedTextColor.GRAY).decorate(TextDecoration.ITALIC)
				.append(Component.text(total, NamedTextColor.GOLD).decorate(TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
		boolean bedrock = player.getUniqueId().toString().startsWith(FLOODGATE_PREFIX);
		// Java: the pack's "total" font draws it larger and lower, and pushes it left.
		if (bedrock) {
			return slide > 0 ? Component.empty() : text;
		}
		// Each slide frame pushes it further left with a wider negative space.
		Component shift = Component.text(String.valueOf((char) (0xE100 + slide))).font(FONT);
		return Component.empty().append(shift).append(text.font(FONT));
	}
}
