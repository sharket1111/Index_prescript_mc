package io.github.sharket1111.trade;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Each player's mailbox: items and trade diamonds that could not be handed
 * over straight away (sales and transfers while away, filled buy requests,
 * listings an admin took down, a full inventory). Saved to mail.yml.
 */
final class Mailbox {
	record Mail(UUID id, ItemStack item, String note, long time) {
	}

	private static Plugin plugin;
	private static File file;
	private static final Map<UUID, List<Mail>> BOXES = new LinkedHashMap<>();

	private Mailbox() {
	}

	static void load(Plugin owner) {
		plugin = owner;
		file = new File(owner.getDataFolder(), "mail.yml");
		BOXES.clear();
		if (!file.exists()) {
			return;
		}
		YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
		var root = yaml.getConfigurationSection("boxes");
		if (root == null) {
			return;
		}
		for (String key : root.getKeys(false)) {
			List<Mail> box = new ArrayList<>();
			for (Map<?, ?> map : root.getMapList(key)) {
				try {
					box.add(new Mail(UUID.fromString(String.valueOf(map.get("id"))),
							ItemStack.deserializeBytes(Base64.getDecoder().decode(String.valueOf(map.get("item")))),
							String.valueOf(map.get("note")), ((Number) map.get("time")).longValue()));
				} catch (RuntimeException e) {
					plugin.getLogger().log(Level.WARNING, "Skipping a broken mail for " + key, e);
				}
			}
			BOXES.put(UUID.fromString(key), box);
		}
	}

	private static void save() {
		YamlConfiguration yaml = new YamlConfiguration();
		BOXES.forEach((owner, box) -> {
			List<Map<String, Object>> list = new ArrayList<>();
			for (Mail mail : box) {
				list.add(Map.of("id", mail.id().toString(), "item", Base64.getEncoder().encodeToString(mail.item().serializeAsBytes()),
						"note", mail.note(), "time", mail.time()));
			}
			if (!list.isEmpty()) {
				yaml.set("boxes." + owner, list);
			}
		});
		try {
			file.getParentFile().mkdirs();
			yaml.save(file);
		} catch (IOException e) {
			plugin.getLogger().log(Level.SEVERE, "Could not save mail.yml", e);
		}
	}

	static List<Mail> of(UUID owner) {
		return List.copyOf(BOXES.getOrDefault(owner, List.of()));
	}

	/** Puts an item in a mailbox and tells the owner if they are online. */
	static void send(UUID to, ItemStack item, String note) {
		if (item == null || item.isEmpty()) {
			return;
		}
		BOXES.computeIfAbsent(to, id -> new ArrayList<>()).add(new Mail(UUID.randomUUID(), item.clone(), note, System.currentTimeMillis()));
		save();
		Player online = Bukkit.getPlayer(to);
		if (online != null) {
			online.sendMessage(Component.text("우편함에 새 우편이 왔습니다: " + note + " ", NamedTextColor.YELLOW)
					.append(Component.text("[열기]", NamedTextColor.AQUA).clickEvent(ClickEvent.runCommand("/trade"))));
		}
	}

	/**
	 * Hands items to a player: straight into their inventory when online, and
	 * whatever does not fit (or everything, when away) into their mailbox.
	 */
	static void deliver(UUID to, ItemStack item, String note) {
		Player online = Bukkit.getPlayer(to);
		if (online == null) {
			send(to, item, note);
			return;
		}
		online.getInventory().addItem(item.clone()).values().forEach(rest -> send(to, rest, note));
	}

	/** Hands trade diamonds to a player as {@link #deliver} does, in full stacks. */
	static void deliverDiamonds(UUID to, int amount, String note) {
		int max = Material.DIAMOND.getMaxStackSize();
		for (int given = 0; given < amount; given += max) {
			deliver(to, TradeItems.tradeDiamond(Math.min(max, amount - given)), note);
		}
	}

	/** Takes a mail out of the box, if it is still there. */
	static boolean take(UUID owner, Mail mail) {
		List<Mail> box = BOXES.get(owner);
		if (box == null || !box.remove(mail)) {
			return false;
		}
		if (box.isEmpty()) {
			BOXES.remove(owner);
		}
		save();
		return true;
	}

	static void notifyOnJoin(Player player) {
		int count = BOXES.getOrDefault(player.getUniqueId(), List.of()).size();
		if (count > 0) {
			player.sendMessage(Component.text("우편함에 우편 " + count + "개가 있습니다. ", NamedTextColor.YELLOW)
					.append(Component.text("[열기]", NamedTextColor.AQUA).clickEvent(ClickEvent.runCommand("/trade"))));
		}
	}
}
