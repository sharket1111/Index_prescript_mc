package io.github.sharket1111.indexprescript.paper;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * The action bar, shared: every message the plugin puts there goes through
 * {@link #message}, and Sanity rides along at the bottom left of the screen.
 *
 * The action bar is centred as a whole, so the Sanity readout (one glyph of
 * the pack's "sanity" font per value: the original's blue or red circle with
 * the number, fixed width)
 * is wrapped in negative and positive spaces that add up to nothing, and
 * the left shift is reckoned from the message's width so it always lands
 * just left of the off-hand slot, at the hotbar's height. Message widths
 * are estimated from the default font's glyph widths. Bedrock players, who
 * have no custom fonts, see "SP n" in front of the message instead.
 */
final class Hud {
	private static final Key FONT = Key.key(Items.NAMESPACE, "sanity");
	private static final Key TOTAL_FONT = Key.key(Items.NAMESPACE, "total");
	private static final String FLOODGATE_PREFIX = "00000000-0000-0000-";
	/** From the screen centre to the readout's left edge, in GUI pixels. */
	private static final int LEFT = 146;
	/** The readout glyph's advance: the 22-pixel circle and the gap after it. */
	private static final int GLYPH_ADVANCE = 23;
	private static final char FIRST_GLYPH = '\ue300';
	private static final char PLUS = '\ue3a0';
	private static final char MINUS = '\ue3b0';
	private static final int MESSAGE_TICKS = 60;
	private static final int REFRESH_TICKS = 10;
	private static final Map<UUID, Shown> SHOWN = new ConcurrentHashMap<>();

	private static final class Shown {
		Component message = Component.empty();
		long messageUntil;
		long sentAt = Long.MIN_VALUE;
		boolean blank = true;
	}

	private Hud() {
	}

	/** Puts {@code message} on the player's action bar for 3 seconds (with Sanity beside it). */
	static void message(Player player, Component message) {
		Shown shown = SHOWN.computeIfAbsent(player.getUniqueId(), id -> new Shown());
		shown.message = message;
		shown.messageUntil = CaduceusListener.now() + MESSAGE_TICKS;
		send(player, shown);
	}

	static void forget(Player player) {
		SHOWN.remove(player.getUniqueId());
	}

	/** Every tick: keeps the bar up while there is something to show. */
	static void tick() {
		long now = CaduceusListener.now();
		for (Player player : Bukkit.getOnlinePlayers()) {
			Shown shown = SHOWN.computeIfAbsent(player.getUniqueId(), id -> new Shown());
			boolean sanity = readout(player);
			boolean message = now < shown.messageUntil;
			if (!sanity && !message) {
				if (!shown.blank) {
					player.sendActionBar(Component.empty());
					shown.blank = true;
				}
				continue;
			}
			if (now - shown.sentAt >= REFRESH_TICKS) {
				send(player, shown);
			}
		}
	}

	/** The bottom-left Sanity readout: hidden while the skill screen's edges are up. */
	private static boolean readout(Player player) {
		return showsSanity(player) && !SkillOverlay.showing(player);
	}

	static boolean showsSanity(Player player) {
		return Sanity.of(player) != 0 || Sanity.inCombat(player) || Items.isWeapon(player.getInventory().getItemInMainHand())
				|| BlackGod.active(player);
	}

	private static void send(Player player, Shown shown) {
		long now = CaduceusListener.now();
		Component message = now < shown.messageUntil ? shown.message : Component.empty();
		Component line = readout(player) ? withSanity(player, message) : message;
		player.sendActionBar(line);
		shown.sentAt = now;
		shown.blank = false;
	}

	private static Component withSanity(Player player, Component message) {
		int sanity = Sanity.of(player);
		if (player.getUniqueId().toString().startsWith(FLOODGATE_PREFIX)) {
			NamedTextColor colour = sanity > 0 ? NamedTextColor.GOLD : sanity < 0 ? NamedTextColor.RED : NamedTextColor.WHITE;
			return Component.text("SP " + sanity + "  ", colour).append(message);
		}
		int width = width(message);
		// Net width zero: shift left, the glyph, shift back; the message stays centred.
		int left = LEFT - width / 2;
		String glyph = String.valueOf((char) (FIRST_GLYPH + sanity - Sanity.MIN));
		Component readout = Component.text(shift(-left, PLUS, MINUS) + glyph + shift(left - GLYPH_ADVANCE, PLUS, MINUS)).font(FONT);
		return Component.empty().append(readout).append(message);
	}

	/** Spaces of the "sanity" font adding up to {@code pixels} (powers of two, either way). */
	static String shift(int pixels, char plus, char minus) {
		StringBuilder out = new StringBuilder();
		int left = Math.abs(pixels);
		char base = pixels < 0 ? minus : plus;
		for (int bit = 10; bit >= 0; bit--) {
			if ((left & (1 << bit)) != 0) {
				out.append((char) (base + bit));
			}
		}
		return out.toString();
	}

	// --- estimating widths -------------------------------------------------------------

	static int width(Component component) {
		int total = 0;
		if (component instanceof TextComponent text) {
			Key font = component.style().font();
			boolean bold = component.style().decoration(TextDecoration.BOLD) == TextDecoration.State.TRUE;
			for (int i = 0; i < text.content().length(); ) {
				int cp = text.content().codePointAt(i);
				i += Character.charCount(cp);
				total += advance(cp, font, bold);
			}
		}
		for (Component child : component.children()) {
			total += width(child.style(child.style().merge(component.style(), net.kyori.adventure.text.format.Style.Merge.Strategy.IF_ABSENT_ON_TARGET)));
		}
		return total;
	}

	private static int advance(int cp, Key font, boolean bold) {
		if (TOTAL_FONT.equals(font)) {
			if (cp >= 0xE100 && cp <= 0xE105) {
				return -440 - 170 * (cp - 0xE100);
			}
			return cp == ' ' ? 5 : (int) Math.round((ascii(cp) - 1) * 1.5) + 1 + (bold ? 1 : 0);
		}
		if (FONT.equals(font)) {
			return 0;
		}
		return (cp < 128 ? ascii(cp) : 9) + (bold ? 1 : 0);
	}

	/** Default-font advances of the ASCII glyphs (pixels, with the 1-pixel gap). */
	private static int ascii(int cp) {
		return switch (cp) {
			case ' ' -> 4;
			case '!', '\'', ',', '.', ':', ';', 'i', '|' -> 2;
			case '`', 'l' -> 3;
			case '"', '(', ')', '*', 'I', '[', ']', 't', '{', '}' -> 4;
			case '<', '>', 'f', 'k' -> 5;
			case '@', '~' -> 7;
			default -> 6;
		};
	}
}
