package io.github.sharket1111.trade;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Handy items, mainly for Bedrock players who cannot easily type commands:
 * - the waypoint book (a book with Mending): sneak and right-click to see
 *   every waypoint of every group at once and teleport. Anyone in a group
 *   with a waypoint gets one automatically.
 * - the trade menu star: right-click to open /trade. Free in the server shop.
 * Both come back on respawn (instead of dropping on death); the book also
 * when joining, and is free in the server shop in case it is thrown away.
 *
 * Being free, they must be worth nothing else: they cannot be crafted with
 * (a beacon from the star, bookshelves from the book), used in an anvil,
 * grindstone (experience from the Mending) or smithing table, an
 * auto-crafter, or traded to villagers; the trade menus refuse them too.
 */
final class TradeTools {
	static final String WAYPOINT = "waypoint_book";
	static final String MENU = "trade_menu";
	private static final String FLOODGATE_PREFIX = "00000000-0000-0000-";
	private static Plugin plugin;
	private static NamespacedKey toolKey;
	/** Set on players who got the menu star, so it comes back on respawn. */
	private static NamespacedKey hasMenuKey;

	private TradeTools() {
	}

	static void init(Plugin owner) {
		plugin = owner;
		toolKey = new NamespacedKey(owner, "trade_tool");
		hasMenuKey = new NamespacedKey(owner, "has_trade_menu");
	}

	static ItemStack waypointBook() {
		ItemStack book = ItemStack.of(Material.BOOK);
		book.editMeta(meta -> {
			meta.itemName(Component.text("그룹 웨이포인트", NamedTextColor.AQUA));
			meta.lore(List.of(TradeItems.plain("들고 웅크린 채 우클릭: 웨이포인트 목록", NamedTextColor.GRAY),
					TradeItems.plain("텔레포트: " + Money.format(Groups.TELEPORT_PRICE), NamedTextColor.GRAY)));
			meta.addEnchant(Enchantment.MENDING, 1, true);
			meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
			meta.setEnchantmentGlintOverride(true);
			meta.getPersistentDataContainer().set(toolKey, PersistentDataType.STRING, WAYPOINT);
		});
		return book;
	}

	static ItemStack menuStar() {
		ItemStack star = ItemStack.of(Material.NETHER_STAR);
		star.editMeta(meta -> {
			meta.itemName(Component.text("거래 메뉴", NamedTextColor.GOLD));
			meta.lore(List.of(TradeItems.plain("우클릭: 거래 메뉴(/trade)를 엽니다.", NamedTextColor.GRAY),
					TradeItems.plain("서버 상점, 시장, 그룹, 땅...", NamedTextColor.GRAY)));
			meta.getPersistentDataContainer().set(toolKey, PersistentDataType.STRING, MENU);
		});
		return star;
	}

	static String toolOf(ItemStack stack) {
		if (stack == null || stack.isEmpty() || !stack.hasItemMeta()) {
			return null;
		}
		return stack.getItemMeta().getPersistentDataContainer().get(toolKey, PersistentDataType.STRING);
	}

	private static boolean carries(Player player, String tool) {
		for (ItemStack stack : player.getInventory().getContents()) {
			if (tool.equals(toolOf(stack))) {
				return true;
			}
		}
		return tool.equals(toolOf(player.getItemOnCursor()));
	}

	private static void give(Player player, ItemStack stack) {
		player.getInventory().addItem(stack).values().forEach(rest -> player.getWorld().dropItem(player.getLocation(), rest));
	}

	/** Whether any of the player's groups has a waypoint. */
	static boolean hasWaypoints(Player player) {
		return Groups.of(player.getUniqueId()).stream().anyMatch(g -> !g.waypoints.isEmpty());
	}

	/** Gives the waypoint book if the player's groups have a waypoint and they carry none. */
	static void offerWaypointBook(Player player) {
		if (hasWaypoints(player) && !carries(player, WAYPOINT)) {
			give(player, waypointBook());
			player.sendMessage(Component.text("그룹 웨이포인트 책을 받았습니다. 들고 웅크린 채 우클릭하면 목록이 열립니다.", NamedTextColor.AQUA));
		}
	}

	/** Gives the book to every online member of {@code group} who needs one (a waypoint was just made). */
	static void offerWaypointBook(Groups.Group group) {
		for (java.util.UUID member : group.memberRoles.keySet()) {
			Player online = Bukkit.getPlayer(member);
			if (online != null) {
				offerWaypointBook(online);
			}
		}
	}

	/** Hands over a free tool from the server shop, unless the player already carries one. */
	static void deliver(Player player, String tool) {
		if (MENU.equals(tool)) {
			player.getPersistentDataContainer().set(hasMenuKey, PersistentDataType.BYTE, (byte) 1);
		}
		if (carries(player, tool)) {
			player.sendMessage(Component.text("이미 가지고 있습니다.", NamedTextColor.GRAY));
			return;
		}
		give(player, MENU.equals(tool) ? menuStar() : waypointBook());
	}

	private static boolean bedrock(Player player) {
		return player.getUniqueId().toString().startsWith(FLOODGATE_PREFIX);
	}

	static final class Events implements Listener {
		@EventHandler(priority = EventPriority.LOW)
		public void onUse(PlayerInteractEvent event) {
			if (event.getHand() != EquipmentSlot.HAND) {
				return;
			}
			Player player = event.getPlayer();
			String tool = toolOf(event.getItem());
			if (tool == null) {
				return;
			}
			Action action = event.getAction();
			boolean right = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
			// A touch screen may only send a tap (a left click) in the air.
			boolean tap = bedrock(player) && action == Action.LEFT_CLICK_AIR;
			if (!right && !tap) {
				return;
			}
			event.setCancelled(true);
			if (MENU.equals(tool)) {
				new MainMenu(player).open();
			} else if (WAYPOINT.equals(tool)) {
				if (!player.isSneaking() && !bedrock(player)) {
					player.sendActionBar(Component.text("웅크린 채 우클릭하면 웨이포인트 목록이 열립니다.", NamedTextColor.GRAY));
					return;
				}
				new WaypointListMenu(player).open();
			}
		}

		private static boolean anyTool(ItemStack... stacks) {
			for (ItemStack stack : stacks) {
				if (toolOf(stack) != null) {
					return true;
				}
			}
			return false;
		}

		/** No crafting with them: crafting tables and the inventory's own grid. */
		@EventHandler(priority = EventPriority.HIGHEST)
		public void onCraft(PrepareItemCraftEvent event) {
			if (anyTool(event.getInventory().getMatrix())) {
				event.getInventory().setResult(null);
			}
		}

		@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
		public void onCrafter(CrafterCraftEvent event) {
			if (event.getBlock().getState() instanceof org.bukkit.block.Crafter crafter
					&& anyTool(crafter.getInventory().getContents())) {
				event.setCancelled(true);
			}
		}

		@EventHandler(priority = EventPriority.HIGHEST)
		public void onAnvil(PrepareAnvilEvent event) {
			if (anyTool(event.getInventory().getContents())) {
				event.setResult(null);
			}
		}

		@EventHandler(priority = EventPriority.HIGHEST)
		public void onGrindstone(PrepareGrindstoneEvent event) {
			if (anyTool(event.getInventory().getContents())) {
				event.setResult(null);
			}
		}

		@EventHandler(priority = EventPriority.HIGHEST)
		public void onSmithing(PrepareSmithingEvent event) {
			if (anyTool(event.getInventory().getContents())) {
				event.setResult(null);
			}
		}

		/** Nor into villagers' trades or hoppers feeding a crafter. */
		@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
		public void onClick(InventoryClickEvent event) {
			if (event.getView().getTopInventory().getType() != InventoryType.MERCHANT) {
				return;
			}
			boolean intoTop = event.getClickedInventory() == event.getView().getTopInventory() && toolOf(event.getCursor()) != null
					|| event.getClickedInventory() != event.getView().getTopInventory() && event.isShiftClick()
							&& toolOf(event.getCurrentItem()) != null
					|| event.getClick() == org.bukkit.event.inventory.ClickType.NUMBER_KEY
							&& event.getClickedInventory() == event.getView().getTopInventory()
							&& toolOf(event.getWhoClicked().getInventory().getItem(event.getHotbarButton())) != null;
			if (intoTop) {
				event.setCancelled(true);
			}
		}

		@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
		public void onDrag(InventoryDragEvent event) {
			if (event.getView().getTopInventory().getType() == InventoryType.MERCHANT && toolOf(event.getOldCursor()) != null
					&& event.getRawSlots().stream().anyMatch(slot -> slot < event.getView().getTopInventory().getSize())) {
				event.setCancelled(true);
			}
		}

		@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
		public void onMove(InventoryMoveItemEvent event) {
			if (event.getDestination().getType() == InventoryType.CRAFTER && toolOf(event.getItem()) != null) {
				event.setCancelled(true);
			}
		}

		/** The tools come back on respawn, so they do not drop on death. */
		@EventHandler
		public void onDeath(PlayerDeathEvent event) {
			event.getDrops().removeIf(stack -> toolOf(stack) != null);
		}

		@EventHandler
		public void onRespawn(PlayerRespawnEvent event) {
			Player player = event.getPlayer();
			Bukkit.getScheduler().runTask(plugin, () -> {
				if (!player.isOnline()) {
					return;
				}
				if (player.getPersistentDataContainer().has(hasMenuKey, PersistentDataType.BYTE) && !carries(player, MENU)) {
					give(player, menuStar());
				}
				offerWaypointBook(player);
			});
		}

		@EventHandler
		public void onJoin(PlayerJoinEvent event) {
			Bukkit.getScheduler().runTaskLater(plugin, () -> {
				if (event.getPlayer().isOnline()) {
					offerWaypointBook(event.getPlayer());
				}
			}, 20L);
		}
	}
}
