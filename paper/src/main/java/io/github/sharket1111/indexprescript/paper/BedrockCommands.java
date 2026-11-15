package io.github.sharket1111.indexprescript.paper;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandSendEvent;

/**
 * Bedrock players (Geyser/Floodgate) get the server's command list turned into Bedrock's, and the Bedrock
 * client checks every command against it before sending. Command names that are not plain ASCII (the
 * Korean aliases: /메뉴, /거울던전, /신호기...) can spoil that list, and then commands like /tp are turned
 * down on the client. They are left out of the list Bedrock players get; the English names still work.
 */
final class BedrockCommands implements Listener {
	private static final String FLOODGATE_PREFIX = "00000000-0000-0000-";

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onCommands(PlayerCommandSendEvent event) {
		if (!event.getPlayer().getUniqueId().toString().startsWith(FLOODGATE_PREFIX)) {
			return;
		}
		event.getCommands().removeIf(name -> !name.chars().allMatch(c -> c >= 0x21 && c < 0x7F));
	}
}
