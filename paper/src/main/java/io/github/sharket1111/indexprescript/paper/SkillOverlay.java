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
 * black wedges in the bottom-left and top-right corners for big ones (where
 * the TOTAL counter shows). They slide in from the screen edges and slide back
 * out when done, one frame per tick (tools/overlays.py draws the frames).
 * Like the carved pumpkin's blur, only the caster's own client is told they
 * wear an item whose equippable component has a camera overlay; their real
 * helmet is untouched and shown again after. Bedrock clients have no camera
 * overlays, so they see nothing.
 */
final class SkillOverlay {
	/** Slide frames per overlay: 0 is fully on screen, the last fully off it. */
	static final int FRAMES = 6;

	enum Size {
		SMALL("misc/skill_small_"),
		BIG("misc/skill_big_");

		final ItemStack[] masks = new ItemStack[FRAMES];

		Size(String texture) {
			for (int k = 0; k < FRAMES; k++) {
				masks[k] = ItemStack.of(Material.PAPER);
				masks[k].setData(DataComponentTypes.EQUIPPABLE, Equippable.equippable(EquipmentSlot.HEAD)
						.cameraOverlay(Key.key(Items.NAMESPACE, texture + k)).build());
			}
		}
	}

	private static final class Shown {
		final Size size;
		int frame = FRAMES - 1;
		boolean leaving;

		Shown(Size size) {
			this.size = size;
		}
	}

	/** Re-sent now and then, in case the client's head slot got refreshed (e.g. on a game mode change). */
	private static final int RESEND_TICKS = 10;
	private static final String FLOODGATE_PREFIX = "00000000-0000-0000-";
	private static final Map<UUID, Shown> SHOWING = new ConcurrentHashMap<>();

	private SkillOverlay() {
	}

	/** Slides the overlay in. */
	static void show(Player player, Size size) {
		if (player.getUniqueId().toString().startsWith(FLOODGATE_PREFIX)) {
			return;
		}
		Shown shown = new Shown(size);
		SHOWING.put(player.getUniqueId(), shown);
		send(player, shown);
	}

	/** Slides the overlay out; the helmet shows again once it is off screen. */
	/** Whether the skill screen's edges are up on the player's screen. */
	static boolean showing(Player player) {
		return SHOWING.containsKey(player.getUniqueId());
	}

	static void hide(Player player) {
		Shown shown = SHOWING.get(player.getUniqueId());
		if (shown != null) {
			shown.leaving = true;
		}
	}

	private static void send(Player player, Shown shown) {
		player.sendEquipmentChange(player, EquipmentSlot.HEAD, shown.size.masks[shown.frame]);
	}

	private static void restoreHelmet(Player player) {
		ItemStack helmet = player.getInventory().getHelmet();
		player.sendEquipmentChange(player, EquipmentSlot.HEAD, helmet == null ? ItemStack.empty() : helmet);
	}

	/** Called every tick: steps slides, and now and then re-sends a held overlay. */
	static void tick() {
		long now = CaduceusListener.now();
		Iterator<Map.Entry<UUID, Shown>> it = SHOWING.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Shown> entry = it.next();
			Player player = Bukkit.getPlayer(entry.getKey());
			Shown shown = entry.getValue();
			if (player == null) {
				it.remove();
				continue;
			}
			if (shown.leaving) {
				if (++shown.frame >= FRAMES) {
					restoreHelmet(player);
					it.remove();
				} else {
					send(player, shown);
				}
			} else if (shown.frame > 0) {
				shown.frame--;
				send(player, shown);
			} else if (now % RESEND_TICKS == 0) {
				send(player, shown);
			}
		}
	}

	static void hideAll() {
		for (UUID id : Set.copyOf(SHOWING.keySet())) {
			Player player = Bukkit.getPlayer(id);
			if (player != null) {
				restoreHelmet(player);
			}
		}
		SHOWING.clear();
	}
}
