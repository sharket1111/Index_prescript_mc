package io.github.sharket1111.trade;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MenuType;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Typing a line of text in the touch mode ({@link UiMode}): an anvil opens,
 * the player writes in its name field with the on-screen keyboard (Bedrock
 * has a native anvil), then taps the green check (or the result) to send it;
 * the paper on the left, or closing the anvil, gives up. Nothing in it is a
 * real item: the anvil is emptied before it closes, so nothing comes back
 * into the inventory.
 */
final class AnvilPrompt implements Listener {
	private static final int INPUT = 0;
	private static final int CHECK = 1;
	private static final int RESULT = 2;
	private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();
	private static Plugin plugin;

	private static final class Pending {
		final AnvilView view;
		final Consumer<String> answer;
		final Runnable cancelled;
		boolean done;

		Pending(AnvilView view, Consumer<String> answer, Runnable cancelled) {
			this.view = view;
			this.answer = answer;
			this.cancelled = cancelled;
		}
	}

	static void init(Plugin owner) {
		plugin = owner;
	}

	/** Asks {@code question} with an anvil; {@code answer} gets the text, {@code cancelled} runs on giving up. */
	static void ask(Player player, String question, Consumer<String> answer, Runnable cancelled) {
		AnvilView view = MenuType.ANVIL.create(player, Component.text(question.length() > 30 ? question.substring(0, 30) + "…" : question));
		Pending pending = new Pending(view, answer, cancelled);
		ItemStack input = TradeItems.button(Material.PAPER, " ", NamedTextColor.WHITE, question, "위 칸에 입력하고 초록 체크를 누르세요.",
				"이 종이를 누르면 취소합니다.");
		input.editMeta(meta -> meta.customName(Component.text(" ")));
		view.getTopInventory().setItem(INPUT, input);
		view.getTopInventory().setItem(CHECK, TradeItems.glint(TradeItems.button(Material.LIME_DYE, "✔ 확인", NamedTextColor.GREEN,
				"입력한 내용을 보냅니다.")));
		player.openInventory(view);
		PENDING.put(player.getUniqueId(), pending);
		player.sendMessage(Component.text(question, NamedTextColor.GOLD));
		player.sendMessage(Component.text("모루 위 칸에 입력하고 초록 체크를 누르세요. (종이를 누르면 취소)", NamedTextColor.GRAY));
	}

	private static Pending of(Player player, Object view) {
		Pending pending = PENDING.get(player.getUniqueId());
		return pending != null && pending.view == view ? pending : null;
	}

	/** Empties the anvil so closing it gives nothing back. */
	private static void empty(AnvilView view) {
		view.getTopInventory().setItem(INPUT, null);
		view.getTopInventory().setItem(CHECK, null);
		view.getTopInventory().setItem(RESULT, null);
	}

	private static void finish(Player player, Pending pending, boolean send) {
		pending.done = true;
		PENDING.remove(player.getUniqueId(), pending);
		String text = pending.view.getRenameText() == null ? "" : pending.view.getRenameText().strip();
		empty(pending.view);
		player.closeInventory();
		Bukkit.getScheduler().runTask(plugin, () -> {
			if (!player.isOnline()) {
				return;
			}
			if (send && !text.isEmpty()) {
				pending.answer.accept(text);
			} else {
				if (send) {
					player.sendMessage(Component.text("아무것도 입력하지 않았습니다.", NamedTextColor.GRAY));
				}
				pending.cancelled.run();
			}
		});
	}

	/** The result shows what was typed, and costs nothing. */
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPrepare(PrepareAnvilEvent event) {
		if (!(event.getView().getPlayer() instanceof Player player) || of(player, event.getView()) == null) {
			return;
		}
		String text = event.getView().getRenameText();
		ItemStack shown = TradeItems.button(Material.NAME_TAG, text == null || text.isBlank() ? " " : text.strip(), NamedTextColor.YELLOW,
				"누르면 보냅니다.");
		event.setResult(shown);
		event.getView().setRepairCost(0);
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onClick(InventoryClickEvent event) {
		if (!(event.getWhoClicked() instanceof Player player)) {
			return;
		}
		Pending pending = of(player, event.getView());
		if (pending == null) {
			return;
		}
		event.setCancelled(true);
		if (pending.done || event.getClickedInventory() != event.getView().getTopInventory()) {
			return;
		}
		switch (event.getRawSlot()) {
			case CHECK, RESULT -> finish(player, pending, true);
			case INPUT -> finish(player, pending, false);
			default -> {
			}
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onDrag(InventoryDragEvent event) {
		if (event.getWhoClicked() instanceof Player player && of(player, event.getView()) != null) {
			event.setCancelled(true);
		}
	}

	/** Closing the anvil gives up (and it is emptied first). */
	@EventHandler
	public void onClose(InventoryCloseEvent event) {
		if (!(event.getPlayer() instanceof Player player)) {
			return;
		}
		Pending pending = of(player, event.getView());
		if (pending == null) {
			return;
		}
		empty(pending.view);
		if (!pending.done) {
			pending.done = true;
			PENDING.remove(player.getUniqueId(), pending);
			Bukkit.getScheduler().runTask(plugin, () -> {
				if (player.isOnline()) {
					player.sendMessage(Component.text("입력을 취소했습니다.", NamedTextColor.GRAY));
					pending.cancelled.run();
				}
			});
		}
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		Pending pending = PENDING.remove(event.getPlayer().getUniqueId());
		if (pending != null) {
			empty(pending.view);
		}
	}

	/** On disable: open anvils are emptied and closed. */
	static void closeAll() {
		List<Map.Entry<UUID, Pending>> open = List.copyOf(PENDING.entrySet());
		PENDING.clear();
		for (Map.Entry<UUID, Pending> entry : open) {
			empty(entry.getValue().view);
			Player player = Bukkit.getPlayer(entry.getKey());
			if (player != null) {
				player.closeInventory();
			}
		}
	}
}
