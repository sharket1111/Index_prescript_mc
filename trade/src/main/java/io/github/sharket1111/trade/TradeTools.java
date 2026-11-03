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
import org.bukkit.event.entity.PlayerDeathEvent;
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
					TradeItems.plain("텔레포트: 거래 전용 다이아 " + Groups.TELEPORT_PRICE + "개", NamedTextColor.GRAY)));
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
