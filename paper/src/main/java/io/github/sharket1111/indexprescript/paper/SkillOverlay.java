package io.github.sharket1111.indexprescript.paper;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Equippable;
import net.kyori.adventure.key.Key;

/**
 * The skill screens of the original, drawn over the whole screen while a
 * skill runs: slanted black bands at the top and bottom for small skills, and
 * big black wedges in the bottom-left and top-right corners for big ones (where
 * the TOTAL counter shows). It works like the carved pumpkin's blur: only the
 * caster's own client is told they wear an item whose equippable component
 * has a camera overlay; their real helmet is untouched and shown again after.
 * Bedrock clients have no camera overlays, so they see nothing.
 */
final class SkillOverlay {
	enum Size {
		SMALL("misc/skill_small"),
		BIG("misc/skill_big");

		final ItemStack mask;

		Size(String texture) {
			mask = ItemStack.of(Material.PAPER);
			mask.setData(DataComponentTypes.EQUIPPABLE, Equippable.equippable(EquipmentSlot.HEAD)
					.cameraOverlay(Key.key(Items.NAMESPACE, texture)).build());
		}
	}

	/** Re-sent now and then, in case the client's head slot got refreshed (e.g. on a game mode change). */
	private static final int RESEND_TICKS = 10;
	private static final String FLOODGATE_PREFIX = "00000000-0000-0000-";
	private static final Map<UUID, Size> SHOWING = new ConcurrentHashMap<>();

	private SkillOverlay() {
	}

	static void show(Player player, Size size) {
		if (player.getUniqueId().toString().startsWith(FLOODGATE_PREFIX)) {
			return;
		}
		SHOWING.put(player.getUniqueId(), size);
		player.sendEquipmentChange(player, EquipmentSlot.HEAD, size.mask);
	}

	static void hide(Player player) {
		if (SHOWING.remove(player.getUniqueId()) != null && player.isOnline()) {
			ItemStack helmet = player.getInventory().getHelmet();
			player.sendEquipmentChange(player, EquipmentSlot.HEAD, helmet == null ? ItemStack.empty() : helmet);
		}
	}

	/** Called every tick. */
	static void tick() {
		if (CaduceusListener.now() % RESEND_TICKS != 0) {
			return;
		}
		Iterator<Map.Entry<UUID, Size>> it = SHOWING.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Size> entry = it.next();
			Player player = Bukkit.getPlayer(entry.getKey());
			if (player == null) {
				it.remove();
			} else {
				player.sendEquipmentChange(player, EquipmentSlot.HEAD, entry.getValue().mask);
			}
		}
	}

	static void hideAll() {
		for (UUID id : Set.copyOf(SHOWING.keySet())) {
			Player player = Bukkit.getPlayer(id);
			if (player != null) {
				hide(player);
			}
		}
		SHOWING.clear();
	}
}
