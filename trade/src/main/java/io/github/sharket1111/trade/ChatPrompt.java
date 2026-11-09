package io.github.sharket1111.trade;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * Asks a player to type a line in chat (a group name, a tag...): the menu
 * closes, the next chat line goes to the answer instead of to everyone, and
 * "취소" (or a minute passing) gives up.
 */
final class ChatPrompt {
	private static final long TIMEOUT_TICKS = 20L * 60;
	private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();
	private static Plugin plugin;

	private record Pending(Consumer<String> answer, Runnable cancelled, Runnable timedOut, long token) {
	}

	private ChatPrompt() {
	}

	static void init(Plugin owner) {
		plugin = owner;
	}

	/** Asks {@code question}; {@code answer} gets the line typed, {@code cancelled} runs on "취소" or after a minute. */
	static void ask(Player player, String question, Consumer<String> answer, Runnable cancelled) {
		ask(player, question, answer, cancelled, null);
	}

	/** As {@link #ask(Player, String, Consumer, Runnable)}, and {@code timedOut} runs if a minute passes with no answer. */
	static void ask(Player player, String question, Consumer<String> answer, Runnable cancelled, Runnable timedOut) {
		long token = System.nanoTime();
		PENDING.put(player.getUniqueId(), new Pending(answer, cancelled, timedOut, token));
		player.closeInventory();
		player.sendMessage(Component.text(question, NamedTextColor.GOLD));
		player.sendMessage(Component.text("채팅으로 입력하세요. ('취소'를 입력하면 그만둡니다)", NamedTextColor.GRAY));
		Bukkit.getScheduler().runTaskLater(plugin, () -> {
			Pending pending = PENDING.get(player.getUniqueId());
			if (pending != null && pending.token == token) {
				PENDING.remove(player.getUniqueId());
				if (player.isOnline()) {
					player.sendMessage(Component.text("입력 시간이 지나 취소되었습니다.", NamedTextColor.GRAY));
					if (pending.timedOut != null) {
						pending.timedOut.run();
					}
				}
			}
		}, TIMEOUT_TICKS);
	}

	static final class Events implements Listener {
		@EventHandler(priority = EventPriority.LOWEST)
		public void onChat(AsyncChatEvent event) {
			Player player = event.getPlayer();
			Pending pending = PENDING.remove(player.getUniqueId());
			if (pending == null) {
				return;
			}
			event.setCancelled(true);
			String text = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
			Bukkit.getScheduler().runTask(plugin, () -> {
				if (!player.isOnline()) {
					return;
				}
				if (text.equals("취소") || text.equalsIgnoreCase("cancel")) {
					player.sendMessage(Component.text("취소했습니다.", NamedTextColor.GRAY));
					pending.cancelled.run();
				} else {
					pending.answer.accept(text);
				}
			});
		}

		@EventHandler
		public void onQuit(PlayerQuitEvent event) {
			PENDING.remove(event.getPlayer().getUniqueId());
		}
	}
}
