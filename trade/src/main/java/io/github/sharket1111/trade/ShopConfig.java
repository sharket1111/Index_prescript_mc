package io.github.sharket1111.trade;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.format.NamedTextColor;

/**
 * The server shop's goods and prices, from shop.yml (written with the default
 * goods on first start). Each product has a type:
 * - spawn_ticket / custom_ticket: the return tickets
 * - xp: {@code levels} experience levels
 * - land: the chunk the buyer stands in
 * - ender_chest: opens the buyer's ender chest on the spot, once
 * - item: {@code material} and {@code amount} (easy to write by hand), or a
 *   full {@code item} (added by an admin from the trade menu)
 * Admins can add, reprice and remove goods in the trade menu, which saves
 * the file; it can also be edited by hand and is read on start.
 */
final class ShopConfig {
	static final int MAX_PRODUCTS = 18;
	static final int MAX_PRICE = 9999;

	static final class Product {
		final String id;
		final String type;
		int price;
		final int levels;
		final ItemStack item;

		Product(String id, String type, int price, int levels, ItemStack item) {
			this.id = id;
			this.type = type;
			this.price = price;
			this.levels = levels;
			this.item = item;
		}

		String name() {
			return switch (type) {
				case "spawn_ticket" -> "스폰 귀환권";
				case "custom_ticket" -> "커스텀 귀환권";
				case "xp" -> "경험치 " + levels + "레벨";
				case "land" -> "땅 소유권";
				case "ender_chest" -> "원격 엔더 상자";
				default -> item == null ? id : net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
						.serialize(item.effectiveName()) + " x" + item.getAmount();
			};
		}

		ItemStack icon() {
			return switch (type) {
				case "spawn_ticket" -> Tickets.spawnTicket(1);
				case "custom_ticket" -> Tickets.customTicket();
				case "xp" -> TradeItems.button(Material.EXPERIENCE_BOTTLE, Math.min(64, Math.max(1, levels)), "경험치 " + levels + "레벨",
						NamedTextColor.GREEN, "바로 경험치 " + levels + "레벨을 얻습니다.");
				case "land" -> TradeItems.button(Material.GRASS_BLOCK, "땅 소유권 (청크)", NamedTextColor.GREEN,
						"지금 서 있는 청크(16x16)를 내 땅으로 삽니다.", "거래 메뉴의 '내 땅'에서 권한을 정할 수 있습니다.",
						"스폰(0,0) 주변 " + Land.SPAWN_RADIUS + "청크, 보호구역, 남의 땅은 살 수 없습니다.");
				case "ender_chest" -> TradeItems.button(Material.ENDER_CHEST, "원격 엔더 상자", NamedTextColor.LIGHT_PURPLE,
						"어디서든 내 엔더 상자를 바로 엽니다. (1회)", "닫으면 다시 사야 합니다.");
				default -> item == null ? TradeItems.button(Material.BARRIER, id, NamedTextColor.RED) : item.clone();
			};
		}

		/** Why the player cannot buy this right now, or null. */
		String whyNot(Player player) {
			return type.equals("land") ? Land.whyNotBuyable(player.getLocation()) : null;
		}

		void deliver(Player player) {
			switch (type) {
				case "land" -> {
					Land.claim(player, player.getLocation());
					Land.showBorder(player, player.getLocation(), org.bukkit.Color.LIME);
				}
				case "spawn_ticket" -> give(player, Tickets.spawnTicket(1));
				case "custom_ticket" -> give(player, Tickets.customTicket());
				case "xp" -> player.giveExpLevels(levels);
				case "ender_chest" -> {
					player.openInventory(player.getEnderChest());
					player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_ENDER_CHEST_OPEN, 0.8F, 1.0F);
				}
				default -> {
					if (item != null) {
						give(player, item.clone());
					}
				}
			}
		}

		private static void give(Player player, ItemStack stack) {
			Mailbox.deliver(player.getUniqueId(), stack, "서버 상점에서 산 물품");
		}
	}

	private static Plugin plugin;
	private static File file;
	private static final List<Product> PRODUCTS = new ArrayList<>();

	private ShopConfig() {
	}

	static void load(Plugin owner) {
		plugin = owner;
		file = new File(owner.getDataFolder(), "shop.yml");
		PRODUCTS.clear();
		if (!file.exists()) {
			PRODUCTS.add(new Product("spawn_ticket", "spawn_ticket", 2, 0, null));
			PRODUCTS.add(new Product("custom_ticket", "custom_ticket", 4, 0, null));
			PRODUCTS.add(new Product("xp_3", "xp", 2, 3, null));
			PRODUCTS.add(landProduct());
			PRODUCTS.add(enderChestProduct());
			save();
			return;
		}
		YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
		ConfigurationSection root = yaml.getConfigurationSection("products");
		if (root == null) {
			root = yaml.createSection("products");
		}
		for (String id : root.getKeys(false)) {
			ConfigurationSection s = root.getConfigurationSection(id);
			try {
				String type = s.getString("type", "item").toLowerCase(Locale.ROOT);
				ItemStack item = null;
				if (type.equals("item")) {
					if (s.isString("item")) {
						item = ItemStack.deserializeBytes(Base64.getDecoder().decode(s.getString("item")));
					} else {
						Material material = Material.matchMaterial(s.getString("material", ""));
						if (material == null || !material.isItem()) {
							throw new IllegalArgumentException("unknown material " + s.getString("material"));
						}
						item = ItemStack.of(material, Math.max(1, Math.min(material.getMaxStackSize(), s.getInt("amount", 1))));
					}
				}
				PRODUCTS.add(new Product(id, type, Math.max(1, s.getInt("price", 1)), s.getInt("levels", 1), item));
			} catch (RuntimeException e) {
				owner.getLogger().log(Level.WARNING, "Skipping the broken shop product " + id + " in shop.yml", e);
			}
		}
		// Shops written before land existed get the land deed once.
		boolean added = false;
		if (!yaml.getBoolean("land-added", false)) {
			PRODUCTS.add(landProduct());
			added = true;
		}
		// ...and the remote ender chest, once.
		if (!yaml.getBoolean("ender-chest-added", false)) {
			PRODUCTS.add(enderChestProduct());
			added = true;
		}
		if (added) {
			save();
		}
	}

	private static Product enderChestProduct() {
		return new Product("ender_chest", "ender_chest", 1, 0, null);
	}

	private static Product landProduct() {
		return new Product("land", "land", 5, 0, null);
	}

	static void save() {
		YamlConfiguration yaml = new YamlConfiguration();
		yaml.options().setHeader(List.of(
				"PlayerTrade server shop. Prices are in trade diamonds. Types:",
				"  spawn_ticket, custom_ticket: return tickets",
				"  xp: give 'levels' experience levels",
				"  land: the chunk the buyer stands in becomes theirs",
				"  ender_chest: opens the buyer's ender chest on the spot (once per purchase)",
				"  item: give 'material' x 'amount' (or a full 'item', as saved by the in-game editor)",
				"Up to 18 products. Restart the server after editing by hand."));
		yaml.set("land-added", true);
		yaml.set("ender-chest-added", true);
		for (Product product : PRODUCTS) {
			String path = "products." + product.id;
			yaml.set(path + ".type", product.type);
			yaml.set(path + ".price", product.price);
			if (product.type.equals("xp")) {
				yaml.set(path + ".levels", product.levels);
			}
			if (product.item != null) {
				yaml.set(path + ".item", Base64.getEncoder().encodeToString(product.item.serializeAsBytes()));
			}
		}
		try {
			file.getParentFile().mkdirs();
			yaml.save(file);
		} catch (IOException e) {
			plugin.getLogger().log(Level.SEVERE, "Could not save shop.yml", e);
		}
	}

	static List<Product> products() {
		return List.copyOf(PRODUCTS);
	}

	static void add(ItemStack item, int price) {
		String id = "item_" + System.currentTimeMillis();
		PRODUCTS.add(new Product(id, "item", price, 0, item.clone()));
		save();
	}

	static void remove(Product product) {
		PRODUCTS.remove(product);
		save();
	}
}
