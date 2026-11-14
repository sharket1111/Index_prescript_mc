package io.github.sharket1111.indexprescript.paper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

/**
 * The gacha's two-star weapons in hand: right-click puts their three skills
 * (the Identity's Skill 1-3 at the weapon's Uptie) on the hotbar; each plays
 * through a clash like every skill (Ego.EgoSkill.cast), Skill 1 every 3
 * seconds, 2 every 5, 3 every 8.
 */
final class GachaWeapon implements Listener {
	private static final int[] WAIT = {3 * 20, 5 * 20, 8 * 20};
	private static final Map<UUID, long[]> READY = new ConcurrentHashMap<>();

	private static List<SkillMenu.Entry> entries(Player p) {
		List<SkillMenu.Entry> out = new ArrayList<>();
		ItemStack held = p.getInventory().getItemInMainHand();
		Gacha.Weapon w = Gacha.weaponOf(held);
		if (w == null) {
			return out;
		}
		long now = CaduceusListener.now();
		long[] ready = READY.computeIfAbsent(p.getUniqueId(), k -> new long[3]);
		List<Ego.Skill> skills = w.skills(Upgrade.uptie(held));
		for (int i = 0; i < skills.size() && i < 3; i++) {
			Ego.Skill s = skills.get(i);
			int slot = i;
			TextColor colour = TextColor.color(Ego.SIN_RGB[Math.max(0, Ego.index(s.sin()))]);
			List<Component> lore = new ArrayList<>();
			lore.add(SkillMenu.line(Ego.typeKo(s.type()) + " · " + Ego.SINS_KO[Math.max(0, Ego.index(s.sin()))] + " · 위력 " + s.power() + " · 코인 "
					+ (s.coinPower() >= 0 ? "+" : "") + s.coinPower() + " ×" + s.coins(), NamedTextColor.GOLD));
			for (String t : s.text()) {
				for (String l : MdGui.wrap(t, 36)) {
					lore.add(SkillMenu.line(l, NamedTextColor.GRAY));
				}
			}
			lore.add(SkillMenu.line("대기 " + WAIT[i] / 20 + "초", NamedTextColor.DARK_GRAY));
			String name = (i + 1) + ". " + s.name();
			if (now < ready[i]) {
				out.add(new SkillMenu.Entry(SkillMenu.icon("minecraft:gray_dye", (int) ((ready[i] - now + 19) / 20), SkillMenu.line(name, NamedTextColor.GRAY),
						lore), null));
				continue;
			}
			out.add(new SkillMenu.Entry(SkillMenu.icon(w.model(), 1, SkillMenu.line(name, colour), lore), () -> {
				if (Ego.EgoSkill.cast(p, s, s.name(), colour, 0)) {
					READY.computeIfAbsent(p.getUniqueId(), k -> new long[3])[slot] = CaduceusListener.now() + WAIT[slot];
				}
			}));
		}
		return out;
	}

	static void openMenu(Player p) {
		Gacha.Weapon w = Gacha.weaponOf(p.getInventory().getItemInMainHand());
		if (w == null || Ego.EgoSkill.active(p)) {
			return;
		}
		if (Clash.blocked(p)) {
			Hud.message(p, Component.text("합이 진행 중입니다.", NamedTextColor.GRAY));
			return;
		}
		SkillMenu.open(p, "weapon", () -> entries(p), Component.text(w.name() + ": 1~3 스킬 · 9 취소", Gacha.TWO_COLOUR));
	}

	@EventHandler(priority = EventPriority.LOW)
	public void onUse(PlayerInteractEvent event) {
		if (event.getHand() == EquipmentSlot.HAND && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)
				&& Gacha.weaponOf(event.getItem()) != null) {
			event.setCancelled(true);
			openMenu(event.getPlayer());
		}
	}

	@EventHandler(priority = EventPriority.LOW)
	public void onUseOn(PlayerInteractEntityEvent event) {
		if (event.getHand() == EquipmentSlot.HAND && Gacha.weaponOf(event.getPlayer().getInventory().getItemInMainHand()) != null) {
			event.setCancelled(true);
			openMenu(event.getPlayer());
		}
	}
}
