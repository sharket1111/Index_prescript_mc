package io.github.sharket1111.indexprescript.paper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * The skill weapons a player owns (from Extraction) and the one they carry. Only one skill weapon is ever in
 * the inventory: it is picked in the menu, and picking another puts the first away. A weapon's level, EXP and
 * Uptie live on its item while carried and on the player while put away.
 */
final class Armory implements Listener {
	private static final NamespacedKey EQUIPPED = new NamespacedKey(Items.NAMESPACE, "weapon_equipped");
	private static final NamespacedKey STATS = new NamespacedKey(Items.NAMESPACE, "weapon_stats");
	private static final NamespacedKey LEVEL = new NamespacedKey(Items.NAMESPACE, "weapon_level");
	private static final NamespacedKey EXP = new NamespacedKey(Items.NAMESPACE, "weapon_exp");
	private static final NamespacedKey UPTIE = new NamespacedKey(Items.NAMESPACE, "weapon_uptie");

	/** The id of the skill weapon a stack belongs to (the terminal belongs to the Caduceus), or null. */
	static String idOf(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return null;
		}
		Gacha.Weapon w = Gacha.weaponOf(stack);
		if (w != null) {
			return w.id();
		}
		if (Items.isCaduceus(stack) || Items.isTerminal(stack)) {
			return Gacha.Three.CADUCEUS.id();
		}
		if (Items.isLaevatein(stack)) {
			return Gacha.Three.LAEVATEIN.id();
		}
		if (Items.isRenoirHammer(stack)) {
			return Gacha.Three.RENOIR.id();
		}
		if (Items.isMimicry(stack)) {
			return Gacha.Three.MIMICRY.id();
		}
		if ("toggle".equals(BlackGod.idOf(stack))) {
			return Gacha.Three.BLACK_GOD.id();
		}
		return null;
	}

	static String equipped(Player p) {
		return p.getPersistentDataContainer().get(EQUIPPED, PersistentDataType.STRING);
	}

	private static Gacha.Three three(String id) {
		for (Gacha.Three t : Gacha.Three.values()) {
			if (t.id().equals(id)) {
				return t;
			}
		}
		return null;
	}

	static String name(String id) {
		Gacha.Three t = three(id);
		if (t != null) {
			return t.name;
		}
		Gacha.Weapon w = Gacha.weapon(id);
		return w == null ? id : w.name();
	}

	private static List<ItemStack> items(Player p, String id) {
		Gacha.Three t = three(id);
		if (t != null) {
			return t.items(p);
		}
		Gacha.Weapon w = Gacha.weapon(id);
		return w == null ? List.of() : List.of(Gacha.weaponItem(w));
	}

	// --- stats kept on the player while a weapon is put away -----------------------------------------

	private static Map<String, int[]> stats(Player p) {
		Map<String, int[]> map = new LinkedHashMap<>();
		String raw = p.getPersistentDataContainer().getOrDefault(STATS, PersistentDataType.STRING, "");
		for (String part : raw.split(";")) {
			String[] kv = part.split("=");
			if (kv.length != 2) {
				continue;
			}
			String[] v = kv[1].split("/");
			try {
				map.put(kv[0], new int[] {Integer.parseInt(v[0]), Integer.parseInt(v[1]), Integer.parseInt(v[2])});
			} catch (RuntimeException e) {
				// a broken entry starts over
			}
		}
		return map;
	}

	private static void save(Player p, String id, ItemStack stack) {
		if (!Upgrade.levels(stack)) {
			return;
		}
		Map<String, int[]> map = stats(p);
		map.put(id, new int[] {Upgrade.level(stack), Upgrade.exp(stack), Upgrade.uptie(stack)});
		StringBuilder out = new StringBuilder();
		map.forEach((k, v) -> out.append(out.isEmpty() ? "" : ";").append(k).append('=').append(v[0]).append('/').append(v[1]).append('/').append(v[2]));
		p.getPersistentDataContainer().set(STATS, PersistentDataType.STRING, out.toString());
	}

	private static void apply(Player p, String id, ItemStack stack) {
		int[] v = stats(p).get(id);
		if (v == null || !Upgrade.levels(stack)) {
			Upgrade.refreshLore(stack);
			return;
		}
		stack.editMeta(meta -> {
			meta.getPersistentDataContainer().set(LEVEL, PersistentDataType.INTEGER, v[0]);
			meta.getPersistentDataContainer().set(EXP, PersistentDataType.INTEGER, v[1]);
			meta.getPersistentDataContainer().set(UPTIE, PersistentDataType.INTEGER, v[2]);
		});
		Upgrade.refreshLore(stack);
	}

	/** Level, EXP and Uptie of an owned weapon, carried or not. */
	static int[] statsOf(Player p, String id) {
		int slot = mainSlot(p);
		if (slot >= 0 && id.equals(equipped(p))) {
			ItemStack s = p.getInventory().getItem(slot);
			return new int[] {Upgrade.level(s), Upgrade.exp(s), Upgrade.uptie(s)};
		}
		return stats(p).getOrDefault(id, new int[] {Upgrade.START_LEVEL, 0, 1});
	}

	// --- carrying -----------------------------------------------------------------------------------

	/** The inventory slot of the carried weapon's levelling item (the Caduceus, not its terminal), or -1. */
	static int mainSlot(Player p) {
		String id = equipped(p);
		ItemStack[] contents = p.getInventory().getContents();
		for (int i = 0; i < contents.length; i++) {
			if (id != null && id.equals(idOf(contents[i])) && Upgrade.levels(contents[i])) {
				return i;
			}
		}
		return -1;
	}

	/** Takes every skill weapon out of the inventory, keeping their stats on the player. */
	private static void putAway(Player p) {
		PlayerInventory inv = p.getInventory();
		ItemStack[] contents = inv.getContents();
		for (int i = 0; i < contents.length; i++) {
			String id = idOf(contents[i]);
			if (id != null) {
				save(p, id, contents[i]);
				contents[i] = null;
			}
		}
		inv.setContents(contents);
		ItemStack cursor = p.getItemOnCursor();
		String cid = idOf(cursor);
		if (cid != null) {
			save(p, cid, cursor);
			p.setItemOnCursor(null);
		}
	}

	/** Carries {@code id} (null: none): the weapon goes in hand, any other skill weapon is put away. */
	static void equip(Player p, String id) {
		SkillMenu.close(p);
		if (BlackGod.active(p)) {
			BlackGod.leave(p);
		}
		putAway(p);
		if (id == null) {
			p.getPersistentDataContainer().remove(EQUIPPED);
			return;
		}
		p.getPersistentDataContainer().set(EQUIPPED, PersistentDataType.STRING, id);
		give(p, id);
	}

	private static void give(Player p, String id) {
		PlayerInventory inv = p.getInventory();
		boolean first = true;
		for (ItemStack stack : items(p, id)) {
			apply(p, id, stack);
			if (first) {
				ItemStack displaced = inv.getItemInMainHand();
				inv.setItemInMainHand(stack);
				if (!displaced.isEmpty()) {
					Gacha.give(p, displaced);
				}
				first = false;
			} else {
				Gacha.give(p, stack);
			}
		}
	}

	/** Gives the carried weapon back if it is gone (after death). */
	private static void ensure(Player p) {
		String id = equipped(p);
		if (id == null || BlackGod.active(p) || !Gacha.owns(p, id)) {
			return;
		}
		for (ItemStack s : p.getInventory().getContents()) {
			if (id.equals(idOf(s))) {
				return;
			}
		}
		give(p, id);
	}

	/**
	 * Every second: a skill weapon the player does not own is removed; an owned one other than the carried one
	 * (or a second copy) is put away.
	 */
	static void tick() {
		for (Player p : Bukkit.getOnlinePlayers()) {
			if (BlackGod.active(p) || SkillMenu.choosing(p)) {
				continue;
			}
			PlayerInventory inv = p.getInventory();
			ItemStack[] contents = inv.getContents();
			String id = equipped(p);
			if (id != null && !Gacha.owns(p, id)) {
				p.getPersistentDataContainer().remove(EQUIPPED);
				id = null;
			}
			if (id == null) {
				// An owned weapon in the inventory with none carried becomes the carried one.
				for (ItemStack s : contents) {
					String found = idOf(s);
					if (found != null && Gacha.owns(p, found)) {
						id = found;
						p.getPersistentDataContainer().set(EQUIPPED, PersistentDataType.STRING, id);
						break;
					}
				}
			}
			Set<String> seen = new HashSet<>();
			boolean removed = false;
			boolean putAway = false;
			for (int i = 0; i < contents.length; i++) {
				String found = idOf(contents[i]);
				if (found == null) {
					continue;
				}
				if (!Gacha.owns(p, found)) {
					contents[i] = null;
					removed = true;
					continue;
				}
				String kind = found + ":" + contents[i].getType() + ":" + (Items.isTerminal(contents[i]) ? "t" : "w");
				if (!found.equals(id) || !seen.add(kind)) {
					if (!found.equals(id)) {
						save(p, found, contents[i]);
					}
					contents[i] = null;
					putAway = true;
				}
			}
			ItemStack cursor = p.getItemOnCursor();
			String cid = idOf(cursor);
			if (cid != null && !Gacha.owns(p, cid)) {
				p.setItemOnCursor(null);
				removed = true;
			}
			if (removed || putAway) {
				inv.setContents(contents);
				Hud.message(p, Component.text(removed ? "가지지 않은 스킬 무기는 사라져요 (추출로 얻어요)"
						: "스킬 무기는 하나만 들 수 있어요 (/메뉴 → 무기 선택)", NamedTextColor.GRAY));
			}
		}
	}

	/** A skill weapon the player does not own cannot be picked up; it vanishes. */
	@EventHandler(ignoreCancelled = true)
	public void onPickup(org.bukkit.event.entity.EntityPickupItemEvent event) {
		if (!(event.getEntity() instanceof Player p)) {
			return;
		}
		String id = idOf(event.getItem().getItemStack());
		if (id != null && !Gacha.owns(p, id)) {
			event.setCancelled(true);
			event.getItem().remove();
			Hud.message(p, Component.text("가지지 않은 스킬 무기는 사라져요 (추출로 얻어요)", NamedTextColor.GRAY));
		}
	}

	// --- the menu -------------------------------------------------------------------------------------

	static void open(Player p) {
		MdGui gui = new MdGui(4, "무기 선택");
		String current = equipped(p);
		int slot = 1;
		for (Gacha.Three t : Gacha.Three.values()) {
			gui.set(slot, icon(p, t.id(), 3, current), click(p, t.id()));
			slot++;
		}
		slot = 9;
		for (Gacha.Weapon w : Gacha.weapons()) {
			gui.set(slot == 17 ? 18 : slot, icon(p, w.id(), 2, current), click(p, w.id()));
			slot = slot == 17 ? 19 : slot + 1;
		}
		gui.set(30, MdGui.item(Material.BARRIER, "무기 내려놓기", NamedTextColor.RED, "스킬 무기를 들지 않아요"), () -> {
			equip(p, null);
			open(p);
		});
		gui.set(31, MdGui.item(Material.ARROW, "뒤로", NamedTextColor.WHITE), () -> Ego.Menu.open(p));
		gui.set(32, MdGui.item(Material.ANVIL, "무기 강화", NamedTextColor.AQUA, "들고 있는 스킬 무기의 레벨과 동기화"), () -> Upgrade.open(p));
		gui.fill().open(p);
	}

	private static Runnable click(Player p, String id) {
		return () -> {
			if (!Gacha.owns(p, id)) {
				return;
			}
			if (id.equals(equipped(p))) {
				p.closeInventory();
				return;
			}
			equip(p, id);
			p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.0F, 1.0F);
			Hud.message(p, Component.text(name(id) + " 장착", Gacha.THREE_COLOUR));
			open(p);
		};
	}

	private static ItemStack icon(Player p, String id, int rarity, String current) {
		boolean owned = Gacha.owns(p, id);
		List<String> lore = new ArrayList<>();
		if (owned) {
			int[] v = statsOf(p, id);
			lore.add("Lv." + v[0] + " · 동기화 " + v[2]);
			lore.add(id.equals(current) ? "장착 중" : "클릭해서 장착 (다른 스킬 무기는 내려놓아요)");
		} else {
			lore.add("추출로 얻어요");
		}
		List<ItemStack> stacks = owned ? items(p, id) : List.of();
		ItemStack stack;
		if (stacks.isEmpty()) {
			stack = MdGui.item(Material.GRAY_DYE, Gacha.stars(rarity) + " " + name(id), NamedTextColor.DARK_GRAY, lore.toArray(String[]::new));
		} else {
			stack = stacks.get(0).clone();
			List<Component> lines = new ArrayList<>();
			lore.forEach(l -> lines.add(Component.text(l, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
			stack.editMeta(meta -> {
				meta.displayName(Component.text(Gacha.stars(rarity) + " " + name(id), rarity == 3 ? Gacha.THREE_COLOUR : Gacha.TWO_COLOUR)
						.decoration(TextDecoration.ITALIC, false));
				meta.lore(lines);
			});
			if (id.equals(current)) {
				MdGui.glow(stack);
			}
		}
		return stack;
	}

	// --- keeping it single ---------------------------------------------------------------------------

	@EventHandler(ignoreCancelled = true)
	public void onDrop(PlayerDropItemEvent event) {
		if (idOf(event.getItemDrop().getItemStack()) != null) {
			event.setCancelled(true);
		}
	}

	/** Skill weapons stay in the player's own inventory (not into chests). */
	@EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
	public void onClick(InventoryClickEvent event) {
		if (event.getView().getTopInventory().getType() == InventoryType.CRAFTING
				|| event.getView().getTopInventory().getHolder() instanceof MdGui) {
			return;
		}
		boolean intoTop = event.getClickedInventory() == event.getView().getTopInventory();
		boolean weapon = idOf(event.getCurrentItem()) != null || idOf(event.getCursor()) != null;
		boolean hotbar = event.getHotbarButton() >= 0 && idOf(event.getWhoClicked().getInventory().getItem(event.getHotbarButton())) != null;
		if ((weapon && (intoTop || event.isShiftClick())) || (hotbar && intoTop)) {
			event.setCancelled(true);
		}
	}

	@EventHandler(ignoreCancelled = true)
	public void onDrag(InventoryDragEvent event) {
		int top = event.getView().getTopInventory().getSize();
		if (idOf(event.getOldCursor()) != null && event.getRawSlots().stream().anyMatch(s -> s < top)
				&& event.getView().getTopInventory().getType() != InventoryType.CRAFTING) {
			event.setCancelled(true);
		}
	}

	@EventHandler
	public void onDeath(PlayerDeathEvent event) {
		Player p = event.getPlayer();
		for (ItemStack s : event.getDrops()) {
			String id = idOf(s);
			if (id != null) {
				save(p, id, s);
			}
		}
		event.getDrops().removeIf(s -> idOf(s) != null);
	}

	@EventHandler
	public void onRespawn(PlayerRespawnEvent event) {
		Player p = event.getPlayer();
		Bukkit.getScheduler().runTaskLater(IndexPrescriptPlugin.instance(), () -> {
			if (p.isOnline()) {
				ensure(p);
			}
		}, 2L);
	}

	@EventHandler
	public void onJoin(PlayerJoinEvent event) {
		Player p = event.getPlayer();
		Bukkit.getScheduler().runTaskLater(IndexPrescriptPlugin.instance(), () -> {
			if (p.isOnline()) {
				ensure(p);
			}
		}, 20L);
	}
}
