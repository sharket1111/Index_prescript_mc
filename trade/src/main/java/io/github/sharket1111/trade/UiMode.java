package io.github.sharket1111.trade;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Each player's screen mode for the trade menus, chosen in /trade and kept
 * on the player:
 * - Java: numbers are typed in chat; right-click (or shift) for actions that need care.
 * - Bedrock (touch): numbers are entered on a keypad in the menu itself
 *   ({@link KeypadMenu}); every action is a tap, and the ones that need care
 *   take a second tap on the same button.
 * Until a player chooses, Bedrock players joining through Floodgate get the
 * Bedrock mode and everyone else the Java one.
 */
final class UiMode {
	private static final String FLOODGATE_PREFIX = "00000000-0000-0000-";
	private static final String JAVA = "java";
	private static final String TOUCH = "bedrock";
	private static NamespacedKey key;

	private UiMode() {
	}

	static void init(Plugin plugin) {
		key = new NamespacedKey(plugin, "ui_mode");
	}

	/** Whether the player uses the Bedrock (touch) mode. */
	static boolean touch(Player player) {
		String mode = player.getPersistentDataContainer().get(key, PersistentDataType.STRING);
		if (mode == null) {
			return player.getUniqueId().toString().startsWith(FLOODGATE_PREFIX);
		}
		return mode.equals(TOUCH);
	}

	static void set(Player player, boolean touch) {
		player.getPersistentDataContainer().set(key, PersistentDataType.STRING, touch ? TOUCH : JAVA);
	}

	/** Whether the player has chosen, or still has the mode picked from their device. */
	static boolean chosen(Player player) {
		return player.getPersistentDataContainer().has(key, PersistentDataType.STRING);
	}
}
