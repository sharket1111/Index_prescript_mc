package io.github.sharket1111.trade;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Return tickets sold by the server shop, used with sneak + right-click:
 * - spawn ticket (paper, Unbreaking III): teleports to the player's bed; single use
 * - custom ticket (paper, Mending, does not stack): the first use saves where the
 *   player stands, the next teleports there and uses it up
 */
final class Tickets implements Listener {
	private static final String SPAWN = "spawn";
	private static final String CUSTOM = "custom";
	/**
	 * One right-click can arrive as two interactions (a cancelled block use is
	 * followed by an item use, and Bedrock clients send both), so uses this
	 * close together count once.
	 */
	private static final long USE_COOLDOWN_MS = 500;
	private static final Map<UUID, Long> LAST_USE = new ConcurrentHashMap<>();
	private static NamespacedKey kindKey;
	private static NamespacedKey worldKey;
	private static NamespacedKey positionKey;

	static void init(Plugin plugin) {
		kindKey = new NamespacedKey(plugin, "ticket");
		worldKey = new NamespacedKey(plugin, "ticket_world");
		positionKey = new NamespacedKey(plugin, "ticket_position");
	}

	static ItemStack spawnTicket(int amount) {
		ItemStack stack = ItemStack.of(Material.PAPER, amount);
		stack.editMeta(meta -> {
			meta.itemName(Component.text("스폰 귀환권", NamedTextColor.GOLD));
			meta.lore(List.of(
					TradeItems.plain("쉬프트 + 우클릭: 내 침대로 돌아갑니다.", NamedTextColor.GRAY),
					TradeItems.plain("1회용", NamedTextColor.DARK_GRAY)));
			meta.addEnchant(Enchantment.UNBREAKING, 3, true);
			meta.getPersistentDataContainer().set(kindKey, PersistentDataType.STRING, SPAWN);
		});
		return stack;
	}

	static ItemStack customTicket() {
		ItemStack stack = ItemStack.of(Material.PAPER);
		stack.editMeta(meta -> {
			meta.itemName(Component.text("커스텀 귀환권", NamedTextColor.LIGHT_PURPLE));
			meta.setMaxStackSize(1);
			meta.addEnchant(Enchantment.MENDING, 1, true);
			meta.getPersistentDataContainer().set(kindKey, PersistentDataType.STRING, CUSTOM);
			customLore(meta, null);
		});
		return stack;
	}

	private static void customLore(ItemMeta meta, Location target) {
		List<Component> lore = new ArrayList<>();
		if (target == null) {
			lore.add(TradeItems.plain("좌표: 설정 안 됨", NamedTextColor.YELLOW));
			lore.add(TradeItems.plain("쉬프트 + 우클릭: 지금 서 있는 곳을 좌표로 정합니다.", NamedTextColor.GRAY));
		} else {
			lore.add(TradeItems.plain("좌표: " + target.getWorld().getName() + " " + target.getBlockX() + ", " + target.getBlockY()
					+ ", " + target.getBlockZ(), NamedTextColor.YELLOW));
			lore.add(TradeItems.plain("쉬프트 + 우클릭: 이 좌표로 이동합니다.", NamedTextColor.GRAY));
		}
		lore.add(TradeItems.plain("1회용", NamedTextColor.DARK_GRAY));
		meta.lore(lore);
	}

	private static String kind(ItemStack stack) {
		if (stack == null || stack.getType() != Material.PAPER || !stack.hasItemMeta()) {
			return null;
		}
		return stack.getItemMeta().getPersistentDataContainer().get(kindKey, PersistentDataType.STRING);
	}

	private static Location target(ItemStack stack) {
		PersistentDataContainer data = stack.getItemMeta().getPersistentDataContainer();
		String worldName = data.get(worldKey, PersistentDataType.STRING);
		double[] p = data.get(positionKey, PersistentDataType.LIST.doubles()) == null ? null
				: data.get(positionKey, PersistentDataType.LIST.doubles()).stream().mapToDouble(Double::doubleValue).toArray();
		World world = worldName == null ? null : Bukkit.getWorld(worldName);
		if (world == null || p == null || p.length < 5) {
			return null;
		}
		return new Location(world, p[0], p[1], p[2], (float) p[3], (float) p[4]);
	}

	@EventHandler(priority = EventPriority.HIGH)
	public void onUse(PlayerInteractEvent event) {
		if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
			return;
		}
		ItemStack stack = event.getItem();
		String kind = kind(stack);
		if (kind == null) {
			return;
		}
		event.setCancelled(true);
		Player player = event.getPlayer();
		if (!player.isSneaking()) {
			return;
		}
		long now = System.currentTimeMillis();
		Long last = LAST_USE.get(player.getUniqueId());
		if (last != null && now - last < USE_COOLDOWN_MS) {
			return;
		}
		LAST_USE.put(player.getUniqueId(), now);
		EquipmentSlot hand = event.getHand() == null ? EquipmentSlot.HAND : event.getHand();
		if (SPAWN.equals(kind)) {
			useSpawn(player, stack, hand);
		} else if (CUSTOM.equals(kind)) {
			useCustom(player, stack, hand);
		}
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		LAST_USE.remove(event.getPlayer().getUniqueId());
	}

	private static void useSpawn(Player player, ItemStack stack, EquipmentSlot hand) {
		Location bed = player.getRespawnLocation();
		if (bed == null) {
			player.sendMessage(Component.text("돌아갈 침대가 없습니다. 침대에서 먼저 자 두세요.", NamedTextColor.RED));
			fail(player);
			return;
		}
		consume(player, stack, hand);
		teleport(player, bed);
	}

	private static void useCustom(Player player, ItemStack stack, EquipmentSlot hand) {
		Location target = target(stack);
		if (target == null) {
			Location here = player.getLocation();
			stack.editMeta(meta -> {
				meta.getPersistentDataContainer().set(worldKey, PersistentDataType.STRING, here.getWorld().getName());
				meta.getPersistentDataContainer().set(positionKey, PersistentDataType.LIST.doubles(),
						List.of(here.getX(), here.getY(), here.getZ(), (double) here.getYaw(), (double) here.getPitch()));
				customLore(meta, here);
			});
			player.getInventory().setItem(hand, stack);
			player.sendMessage(Component.text("이곳을 커스텀 귀환권의 좌표로 정했습니다.", NamedTextColor.GREEN));
			player.playSound(here, Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.0F, 1.2F);
			return;
		}
		consume(player, stack, hand);
		teleport(player, target);
	}

	private static void consume(Player player, ItemStack stack, EquipmentSlot hand) {
		stack.setAmount(stack.getAmount() - 1);
		player.getInventory().setItem(hand, stack.getAmount() > 0 ? stack : null);
	}

	private static void teleport(Player player, Location to) {
		Location from = player.getLocation();
		from.getWorld().spawnParticle(Particle.PORTAL, from.clone().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.3);
		from.getWorld().playSound(from, Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0F, 1.0F);
		player.teleportAsync(to).thenAccept(done -> {
			if (done) {
				to.getWorld().spawnParticle(Particle.PORTAL, to.clone().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.3);
				to.getWorld().playSound(to, Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0F, 1.2F);
			}
		});
	}

	private static void fail(Player player) {
		player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.PLAYERS, 0.8F, 1.0F);
	}
}
