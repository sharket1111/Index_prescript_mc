package io.github.sharket1111.trade;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * The server's own currency: a balance per player (not an item), saved to
 * economy.yml. Its name and symbol are set there too ("골드", "G" by default).
 *
 * It replaced the trade diamond: on the first start with this version every
 * price and amount saved in diamonds is converted at {@link #DIAMOND_RATE}
 * per diamond (see {@link #needsMigration}), and trade diamonds a player
 * still carries become money on join or when the trade menu opens.
 *
 * Money given to someone who is away goes straight onto their balance; they
 * are told what came in on their next join.
 */
final class Money {
	/** What one trade diamond was worth when the currency replaced it. */
	static final long DIAMOND_RATE = 100;
	static final long MAX_BALANCE = 1_000_000_000_000L;
	private static final int MAX_NOTES = 5;

	private static Plugin plugin;
	private static File file;
	private static boolean migrate;
	private static String name = "골드";
	private static String symbol = "G";
	private static final Map<UUID, Long> BALANCES = new LinkedHashMap<>();
	/** What came in while each player was away: the total and the latest notes. */
	private static final Map<UUID, Long> AWAY_TOTAL = new LinkedHashMap<>();
	private static final Map<UUID, List<String>> AWAY_NOTES = new LinkedHashMap<>();

	private Money() {
	}

	/** Loads economy.yml; a server that never had it still has diamond prices to convert. */
	static void load(Plugin owner) {
		plugin = owner;
		file = new File(owner.getDataFolder(), "economy.yml");
		BALANCES.clear();
		AWAY_TOTAL.clear();
		AWAY_NOTES.clear();
		migrate = !file.exists();
		if (migrate) {
			return;
		}
		YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
		name = yaml.getString("currency-name", name);
		symbol = yaml.getString("currency-symbol", symbol);
		ConfigurationSection balances = yaml.getConfigurationSection("balances");
		if (balances != null) {
			for (String key : balances.getKeys(false)) {
				BALANCES.put(UUID.fromString(key), balances.getLong(key));
			}
		}
		ConfigurationSection away = yaml.getConfigurationSection("away");
		if (away != null) {
			for (String key : away.getKeys(false)) {
				UUID id = UUID.fromString(key);
				AWAY_TOTAL.put(id, away.getLong(key + ".total"));
				AWAY_NOTES.put(id, new ArrayList<>(away.getStringList(key + ".notes")));
			}
		}
	}

	/** True on the first start after the switch: prices saved in diamonds must be multiplied by {@link #DIAMOND_RATE}. */
	static boolean needsMigration() {
		return migrate;
	}

	static void save() {
		YamlConfiguration yaml = new YamlConfiguration();
		yaml.options().setHeader(List.of(
				"PlayerTrade economy. currency-name / currency-symbol: how money is shown.",
				"balances: each player's money. Edit only while the server is stopped."));
		yaml.set("currency-name", name);
		yaml.set("currency-symbol", symbol);
		BALANCES.forEach((id, amount) -> yaml.set("balances." + id, amount));
		AWAY_TOTAL.forEach((id, amount) -> {
			yaml.set("away." + id + ".total", amount);
			yaml.set("away." + id + ".notes", AWAY_NOTES.getOrDefault(id, List.of()));
		});
		try {
			file.getParentFile().mkdirs();
			yaml.save(file);
		} catch (IOException e) {
			plugin.getLogger().log(Level.SEVERE, "Could not save economy.yml", e);
		}
	}

	/** Called once everything saved in diamonds has been converted: from now on economy.yml exists. */
	static void finishMigration() {
		migrate = false;
		save();
	}

	// --- showing money ------------------------------------------------------------------

	static String name() {
		return name;
	}

	static String symbol() {
		return symbol;
	}

	/** The name with 이/가. */
	static String subject() {
		return name + (batchim(name) ? "이" : "가");
	}

	/** The name with 을/를. */
	static String object() {
		return name + (batchim(name) ? "을" : "를");
	}

	/** The name with (으)로. */
	static String means() {
		char last = name.isEmpty() ? 'a' : name.charAt(name.length() - 1);
		boolean rieul = last >= 0xAC00 && last <= 0xD7A3 && (last - 0xAC00) % 28 == 8;
		return name + (batchim(name) && !rieul ? "으로" : "로");
	}

	/** Whether a word ends in a final consonant (Hangul; anything else reads as not). */
	private static boolean batchim(String word) {
		if (word.isEmpty()) {
			return false;
		}
		char last = word.charAt(word.length() - 1);
		return last >= 0xAC00 && last <= 0xD7A3 && (last - 0xAC00) % 28 != 0;
	}

	/** Why a currency name or symbol cannot be used, or null. */
	static String badName(String text, int max) {
		if (text.isBlank() || text.length() > max) {
			return "1~" + max + "글자로 정하세요.";
		}
		for (char c : text.toCharArray()) {
			if (Character.isISOControl(c) || c == '\u00a7' || c == '&') {
				return "쓸 수 없는 문자가 있습니다.";
			}
		}
		return null;
	}

	/** Renames the currency (and its symbol when given); saved at once. */
	static void rename(String newName, String newSymbol) {
		if (newName != null) {
			name = newName.strip();
		}
		if (newSymbol != null) {
			symbol = newSymbol.strip();
		}
		save();
	}

	/** "1,250 G". */
	static String format(long amount) {
		return String.format("%,d %s", amount, symbol);
	}

	/** The wallet button the menus show. */
	static ItemStack wallet(Player player) {
		return TradeItems.glint(TradeItems.button(Material.GOLD_INGOT, "내 " + name + ": " + format(balance(player.getUniqueId())),
				NamedTextColor.GOLD, "아이템 판매 탭에서 아이템을 팔아 " + object() + " 얻습니다."));
	}

	// --- balances -----------------------------------------------------------------------

	static long balance(UUID id) {
		return BALANCES.getOrDefault(id, 0L);
	}

	static boolean has(UUID id, long amount) {
		return amount <= 0 || balance(id) >= amount;
	}

	/** Takes {@code amount} if the player has it; returns false (taking nothing) if not. */
	static boolean take(UUID id, long amount) {
		if (amount <= 0) {
			return true;
		}
		long balance = balance(id);
		if (balance < amount) {
			return false;
		}
		set(id, balance - amount);
		save();
		return true;
	}

	/** Adds {@code amount}, quietly. */
	static void add(UUID id, long amount) {
		if (amount <= 0) {
			return;
		}
		set(id, Math.min(MAX_BALANCE, balance(id) + amount));
		save();
	}

	/**
	 * Pays a player and tells them: at once if online ({@code message}), or on
	 * their next join with {@code note}.
	 */
	static void give(UUID id, long amount, Component message, String note) {
		if (amount <= 0) {
			return;
		}
		set(id, Math.min(MAX_BALANCE, balance(id) + amount));
		Player online = Bukkit.getPlayer(id);
		if (online != null) {
			if (message != null) {
				online.sendMessage(message);
			}
		} else {
			AWAY_TOTAL.merge(id, amount, Long::sum);
			List<String> notes = AWAY_NOTES.computeIfAbsent(id, key -> new ArrayList<>());
			notes.add(note + ": " + format(amount));
			while (notes.size() > MAX_NOTES) {
				notes.remove(0);
			}
		}
		save();
	}

	/**
	 * Pays every one of {@code ids} the same amount (saved once): those online
	 * are told at once, the others on their next join.
	 */
	static void giveAll(java.util.Collection<UUID> ids, long amount, String note) {
		if (amount <= 0) {
			return;
		}
		for (UUID id : ids) {
			set(id, Math.min(MAX_BALANCE, balance(id) + amount));
			Player online = Bukkit.getPlayer(id);
			if (online != null) {
				online.sendMessage(Component.text(note + ": " + format(amount) + "을(를) 받았습니다.", NamedTextColor.GOLD));
			} else {
				AWAY_TOTAL.merge(id, amount, Long::sum);
				List<String> notes = AWAY_NOTES.computeIfAbsent(id, key -> new ArrayList<>());
				notes.add(note + ": " + format(amount));
				while (notes.size() > MAX_NOTES) {
					notes.remove(0);
				}
			}
		}
		save();
	}

	/** Pays a player with a plain note, shown now or on their next join. */
	static void give(UUID id, long amount, String note) {
		give(id, amount, Component.text(note + ": " + format(amount) + "을(를) 받았습니다.", NamedTextColor.GREEN), note);
	}

	private static void set(UUID id, long amount) {
		if (amount <= 0) {
			BALANCES.remove(id);
		} else {
			BALANCES.put(id, amount);
		}
	}

	// --- on join ------------------------------------------------------------------------

	/** Tells a player what came in while they were away, and turns their old trade diamonds into money. */
	static void onJoin(Player player) {
		Long total = AWAY_TOTAL.remove(player.getUniqueId());
		List<String> notes = AWAY_NOTES.remove(player.getUniqueId());
		if (total != null && total > 0) {
			player.sendMessage(Component.text("접속하지 않은 동안 " + format(total) + "을(를) 받았습니다.", NamedTextColor.GREEN));
			if (notes != null) {
				for (String line : notes) {
					player.sendMessage(Component.text(" - " + line, NamedTextColor.GRAY));
				}
			}
			save();
		}
		convertDiamonds(player);
	}

	/** Old trade diamonds in the inventory or ender chest become money at {@link #DIAMOND_RATE} each. */
	static void convertDiamonds(Player player) {
		int count = takeDiamonds(player.getInventory()) + takeDiamonds(player.getEnderChest());
		if (count > 0) {
			long amount = count * DIAMOND_RATE;
			add(player.getUniqueId(), amount);
			player.sendMessage(Component.text("거래 전용 다이아 " + count + "개가 " + format(amount) + "(으)로 바뀌었습니다.", NamedTextColor.GOLD));
		}
	}

	private static int takeDiamonds(Inventory inventory) {
		int count = 0;
		for (int slot = 0; slot < inventory.getSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (TradeItems.isTradeDiamond(stack)) {
				count += stack.getAmount();
				inventory.setItem(slot, null);
			}
		}
		return count;
	}
}
