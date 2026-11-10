package io.github.sharket1111.trade;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * The auction house (auctions.yml). A player puts an item up with a starting
 * price and a length; others bid. A bid's money is taken at once and held;
 * when someone bids higher, the one outbid gets theirs back. A bid in the
 * last minute pushes the end a minute out, so nobody wins by sniping. At the
 * end the item goes to the winner (inventory, or mailbox) and the money to
 * the seller; with no bids it goes back to the seller. Every change is
 * saved straight away, so a crash loses nothing.
 */
final class Auctions {
	static final int MAX_PER_PLAYER = 5;
	static final long MIN_START = 1;
	static final long MAX_PRICE = 100_000_000;
	/** Lengths a seller can pick, in hours. */
	static final int[] HOURS = {1, 3, 6, 12, 24, 48};
	private static final long EXTEND = TimeUnit.MINUTES.toMillis(1);

	static final class Auction {
		final UUID id;
		final UUID seller;
		final String sellerName;
		final ItemStack item;
		final long start;
		final long createdAt;
		long endsAt;
		long bid;
		UUID bidder;
		String bidderName;
		int bids;

		Auction(UUID id, UUID seller, String sellerName, ItemStack item, long start, long createdAt, long endsAt) {
			this.id = id;
			this.seller = seller;
			this.sellerName = sellerName;
			this.item = item;
			this.start = start;
			this.createdAt = createdAt;
			this.endsAt = endsAt;
		}

		/** The least the next bid may be: the start price, or 5% (at least 1) over the highest bid. */
		long minimum() {
			return bidder == null ? start : bid + Math.max(1, (long) Math.ceil(bid * 0.05));
		}

		long current() {
			return bidder == null ? start : bid;
		}
	}

	private static Plugin plugin;
	private static File file;
	private static final Map<UUID, Auction> AUCTIONS = new LinkedHashMap<>();

	private Auctions() {
	}

	static void load(Plugin owner) {
		plugin = owner;
		file = new File(owner.getDataFolder(), "auctions.yml");
		AUCTIONS.clear();
		if (file.exists()) {
			ConfigurationSection root = YamlConfiguration.loadConfiguration(file).getConfigurationSection("auctions");
			if (root != null) {
				for (String key : root.getKeys(false)) {
					ConfigurationSection s = root.getConfigurationSection(key);
					try {
						Auction auction = new Auction(UUID.fromString(key), UUID.fromString(s.getString("seller")), s.getString("seller-name", "?"),
								ItemStack.deserializeBytes(Base64.getDecoder().decode(s.getString("item", ""))), s.getLong("start"),
								s.getLong("created"), s.getLong("ends"));
						if (s.isString("bidder")) {
							auction.bidder = UUID.fromString(s.getString("bidder"));
							auction.bidderName = s.getString("bidder-name", "?");
							auction.bid = s.getLong("bid");
						}
						auction.bids = s.getInt("bids");
						AUCTIONS.put(auction.id, auction);
					} catch (RuntimeException e) {
						owner.getLogger().log(Level.WARNING, "Skipping a broken auction " + key, e);
					}
				}
			}
		}
		// Ends what ran out while the server was off, then checks every second.
		Bukkit.getScheduler().runTaskTimer(owner, Auctions::tick, 20, 20);
	}

	static void save() {
		if (file == null) {
			return;
		}
		YamlConfiguration yaml = new YamlConfiguration();
		for (Auction a : AUCTIONS.values()) {
			String path = "auctions." + a.id;
			yaml.set(path + ".seller", a.seller.toString());
			yaml.set(path + ".seller-name", a.sellerName);
			yaml.set(path + ".item", Base64.getEncoder().encodeToString(a.item.serializeAsBytes()));
			yaml.set(path + ".start", a.start);
			yaml.set(path + ".created", a.createdAt);
			yaml.set(path + ".ends", a.endsAt);
			yaml.set(path + ".bids", a.bids);
			if (a.bidder != null) {
				yaml.set(path + ".bidder", a.bidder.toString());
				yaml.set(path + ".bidder-name", a.bidderName);
				yaml.set(path + ".bid", a.bid);
			}
		}
		try {
			file.getParentFile().mkdirs();
			yaml.save(file);
		} catch (IOException e) {
			plugin.getLogger().log(Level.SEVERE, "Could not save auctions.yml", e);
		}
	}

	/** Running auctions, ending soonest first. */
	static List<Auction> all() {
		List<Auction> list = new ArrayList<>(AUCTIONS.values());
		list.sort(Comparator.comparingLong(a -> a.endsAt));
		return list;
	}

	static Auction get(UUID id) {
		return AUCTIONS.get(id);
	}

	static Auction get(String id) {
		try {
			return AUCTIONS.get(UUID.fromString(id));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	static List<Auction> of(UUID seller) {
		return all().stream().filter(a -> a.seller.equals(seller)).toList();
	}

	/** Auctions where {@code player} has the highest bid. */
	static List<Auction> leading(UUID player) {
		return all().stream().filter(a -> player.equals(a.bidder)).toList();
	}

	/** Why {@code stack} cannot be auctioned, or null. */
	static String refuse(UUID seller, ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return "빈 칸입니다.";
		}
		if (TradeTools.toolOf(stack) != null) {
			return "무료 아이템(거래 메뉴, 웨이포인트 책)은 경매에 올릴 수 없습니다.";
		}
		if (Land.deedChunk(stack) != null) {
			return "땅은 시장에서만 팔 수 있습니다.";
		}
		if (of(seller).size() >= MAX_PER_PLAYER) {
			return "경매는 한 사람당 " + MAX_PER_PLAYER + "개까지 올릴 수 있습니다.";
		}
		return null;
	}

	/** Starts an auction of {@code item} (already taken from the seller). */
	static Auction start(UUID seller, String sellerName, ItemStack item, long start, int hours) {
		long now = System.currentTimeMillis();
		Auction auction = new Auction(UUID.randomUUID(), seller, sellerName, item.clone(), Math.max(MIN_START, Math.min(MAX_PRICE, start)), now,
				now + TimeUnit.HOURS.toMillis(Math.max(1, Math.min(48, hours))));
		AUCTIONS.put(auction.id, auction);
		save();
		Discord.auctionStarted(WebSite.itemText(item), sellerName, auction.start, auction.endsAt);
		Bukkit.getLogger().info("[PlayerTrade] " + sellerName + " started an auction from " + auction.start);
		return auction;
	}

	/**
	 * A bid: null when it went through, or why not. The bidder's money is
	 * taken now; the player outbid gets theirs back.
	 */
	static String bid(UUID bidder, String bidderName, Auction auction, long amount) {
		if (AUCTIONS.get(auction.id) != auction || System.currentTimeMillis() >= auction.endsAt) {
			return "이미 끝난 경매입니다.";
		}
		if (auction.seller.equals(bidder)) {
			return "자신의 경매에는 입찰할 수 없습니다.";
		}
		if (bidder.equals(auction.bidder)) {
			return "이미 최고 입찰자입니다.";
		}
		if (amount < auction.minimum()) {
			return "최소 " + Money.format(auction.minimum()) + " 이상 입찰해야 합니다.";
		}
		if (amount > MAX_PRICE) {
			return "입찰은 " + Money.format(MAX_PRICE) + "까지입니다.";
		}
		if (!Money.take(bidder, amount)) {
			return Money.subject() + " 부족합니다. (" + Money.format(amount) + " 필요)";
		}
		String what = WebSite.itemText(auction.item);
		if (auction.bidder != null) {
			Money.give(auction.bidder, auction.bid, Component.text("경매 '" + what + "'에서 " + bidderName + "님이 더 높게 입찰해 "
					+ Money.format(auction.bid) + "을(를) 돌려받았습니다. (/trade 경매)", NamedTextColor.YELLOW), "경매 상회 입찰로 돌려받은 돈");
			ding(auction.bidder, Sound.BLOCK_NOTE_BLOCK_BASS);
		}
		auction.bid = amount;
		auction.bidder = bidder;
		auction.bidderName = bidderName;
		auction.bids++;
		long now = System.currentTimeMillis();
		if (auction.endsAt - now < EXTEND) {
			auction.endsAt = now + EXTEND;
		}
		save();
		Player seller = Bukkit.getPlayer(auction.seller);
		if (seller != null) {
			seller.sendMessage(Component.text("경매 '" + what + "'에 " + bidderName + "님이 " + Money.format(amount) + " 입찰했습니다.", NamedTextColor.AQUA));
		}
		return null;
	}

	/** The seller takes back an auction nobody has bid on; the item comes back. */
	static String cancel(UUID seller, Auction auction) {
		if (AUCTIONS.get(auction.id) != auction) {
			return "이미 끝난 경매입니다.";
		}
		if (!auction.seller.equals(seller)) {
			return "내 경매만 취소할 수 있습니다.";
		}
		if (auction.bidder != null) {
			return "입찰이 들어온 경매는 취소할 수 없습니다.";
		}
		AUCTIONS.remove(auction.id);
		save();
		Mailbox.deliver(seller, auction.item.clone(), "취소한 경매 물품");
		return null;
	}

	private static void tick() {
		long now = System.currentTimeMillis();
		List<Auction> ended = new ArrayList<>();
		for (Auction auction : AUCTIONS.values()) {
			if (now >= auction.endsAt) {
				ended.add(auction);
			}
		}
		if (ended.isEmpty()) {
			return;
		}
		ended.forEach(auction -> AUCTIONS.remove(auction.id));
		save();
		for (Auction auction : ended) {
			settle(auction);
		}
	}

	private static void settle(Auction auction) {
		String what = WebSite.itemText(auction.item);
		if (auction.bidder == null) {
			Mailbox.deliver(auction.seller, auction.item.clone(), "입찰이 없어 돌아온 경매 물품");
			tell(auction.seller, "경매 '" + what + "'에 입찰이 없어 물품이 돌아왔습니다.", NamedTextColor.YELLOW);
			Discord.auctionEnded(what, auction.sellerName, null, 0);
			return;
		}
		Mailbox.deliver(auction.bidder, auction.item.clone(), "경매 낙찰");
		tell(auction.bidder, "경매 '" + what + "'을(를) " + Money.format(auction.bid) + "에 낙찰받았습니다!", NamedTextColor.GREEN);
		ding(auction.bidder, Sound.ENTITY_PLAYER_LEVELUP);
		Money.give(auction.seller, auction.bid, Component.text("경매 '" + what + "'이(가) " + auction.bidderName + "님에게 "
				+ Money.format(auction.bid) + "에 낙찰되었습니다.", NamedTextColor.GREEN), "경매 낙찰 대금");
		History.recordRaw(auction.bidder, auction.bidderName, auction.seller, auction.sellerName, auction.item,
				(int) Math.min(Integer.MAX_VALUE, auction.bid), null);
		Discord.auctionEnded(what, auction.sellerName, auction.bidderName, auction.bid);
		Bukkit.getLogger().info("[PlayerTrade] Auction won by " + auction.bidderName + " for " + auction.bid);
	}

	private static void tell(UUID id, String message, NamedTextColor color) {
		Player player = Bukkit.getPlayer(id);
		if (player != null) {
			player.sendMessage(Component.text(message, color));
		}
	}

	private static void ding(UUID id, Sound sound) {
		Player player = Bukkit.getPlayer(id);
		if (player != null) {
			player.playSound(player.getLocation(), sound, SoundCategory.PLAYERS, 0.8F, 1.0F);
		}
	}

	/** "2시간 13분", "45초" */
	static String left(long endsAt) {
		long s = Math.max(0, (endsAt - System.currentTimeMillis()) / 1000);
		if (s >= 3600) {
			return s / 3600 + "시간 " + s % 3600 / 60 + "분";
		}
		if (s >= 60) {
			return s / 60 + "분 " + s % 60 + "초";
		}
		return s + "초";
	}
}
