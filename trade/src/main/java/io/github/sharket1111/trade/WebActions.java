package io.github.sharket1111.trade;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * What a logged-in player can do from the web page, and what an admin can.
 * Every action runs on the main thread (the web threads hand it over) and
 * goes through the same checks as in game. Money spent from the web (buying
 * and sending) is capped per action and per day (web.yml), since the page is
 * plain http. Each answer is JSON: {"ok": message} or {"error": message}.
 */
final class WebActions {
	private static final Gson GSON = new Gson();
	private static boolean tradeEnabled = true;
	private static long maxPerAction = 100_000;
	private static long maxPerDay = 300_000;
	/** Spent from the web today, per player: {day, amount}. */
	private static final Map<UUID, long[]> SPENT = new ConcurrentHashMap<>();

	private WebActions() {
	}

	static void configure(boolean enabled, long perAction, long perDay) {
		tradeEnabled = enabled;
		maxPerAction = Math.max(1, perAction);
		maxPerDay = Math.max(1, perDay);
	}

	static JsonObject limits(UUID player) {
		JsonObject out = new JsonObject();
		out.addProperty("enabled", tradeEnabled);
		out.addProperty("perAction", maxPerAction);
		out.addProperty("perDay", maxPerDay);
		out.addProperty("spentToday", spentToday(player));
		return out;
	}

	private static long spentToday(UUID player) {
		long[] spent = SPENT.get(player);
		return spent != null && spent[0] == LocalDate.now().toEpochDay() ? spent[1] : 0;
	}

	private static void spend(UUID player, long amount) {
		long today = LocalDate.now().toEpochDay();
		SPENT.compute(player, (id, spent) -> spent == null || spent[0] != today ? new long[] {today, amount} : new long[] {today, spent[1] + amount});
	}

	/** Why the player may not spend {@code amount} from the web now, or null. */
	private static String limit(UUID player, long amount) {
		if (!tradeEnabled) {
			return "웹 거래가 꺼져 있습니다. 게임에서 해 주세요.";
		}
		if (amount > maxPerAction) {
			return "웹에서는 한 번에 " + Money.format(maxPerAction) + "까지만 쓸 수 있습니다. 더 큰 거래는 게임에서 해 주세요.";
		}
		if (spentToday(player) + amount > maxPerDay) {
			return "웹에서는 하루에 " + Money.format(maxPerDay) + "까지만 쓸 수 있습니다. (오늘 " + Money.format(spentToday(player)) + " 사용)";
		}
		return null;
	}

	static String ok(String message) {
		JsonObject out = new JsonObject();
		out.addProperty("ok", message);
		return GSON.toJson(out);
	}

	static String error(String message) {
		JsonObject out = new JsonObject();
		out.addProperty("error", message);
		return GSON.toJson(out);
	}

	/** A player's name: the server's, else the one saved with their web password, else with their listings. */
	static String name(UUID id) {
		String name = Bukkit.getOfflinePlayer(id).getName();
		if (name == null) {
			name = WebAccounts.nameOf(id);
		}
		return name != null ? name : Market.name(id);
	}

	// --- a player's own actions ----------------------------------------------------------

	/** Buys a market listing: the money leaves now, the item goes to the inventory (or the mailbox when away or full). */
	static String buy(UUID buyer, String listingId) {
		Listing listing;
		try {
			listing = Market.get(UUID.fromString(listingId));
		} catch (IllegalArgumentException e) {
			listing = null;
		}
		if (listing == null) {
			return error("이미 팔렸거나 내려간 물품입니다.");
		}
		if (listing.seller.equals(buyer)) {
			return error("자신의 물품은 살 수 없습니다.");
		}
		if (Land.deedChunk(listing.item) != null) {
			return error("땅은 게임 안에서만 살 수 있습니다.");
		}
		int price = Groups.discounted(listing.price, Groups.discount(buyer, listing.seller));
		String why = limit(buyer, price);
		if (why != null) {
			return error(why);
		}
		if (!Money.take(buyer, price)) {
			return error(Money.subject() + " 부족합니다. (" + Money.format(price) + " 필요)");
		}
		spend(buyer, price);
		String buyerName = name(buyer);
		// Record first: the history takes the seller's name from the market while the listing is still there.
		History.record(listing, buyer, buyerName, price);
		Market.remove(listing);
		Mailbox.deliver(buyer, listing.item.clone(), "웹에서 산 물품");
		Component what = listing.item.effectiveName().append(Component.text(" x" + listing.item.getAmount()));
		Market.pay(listing.seller, price, what, buyerName + "(웹)");
		Bukkit.getLogger().info("[PlayerTrade] " + buyerName + " bought a listing for " + price + " on the web");
		return ok("샀습니다. " + (Bukkit.getPlayer(buyer) != null ? "인벤토리에 들어갔습니다." : "게임에 들어가면 우편함에서 받으세요.")
				+ " (-" + Money.format(price) + ")");
	}

	/** Sends money to a player by name. */
	static String pay(UUID from, String toName, long amount) {
		if (amount <= 0) {
			return error("보낼 금액을 입력하세요.");
		}
		OfflinePlayer to = Bukkit.getOfflinePlayerIfCached(toName.trim());
		if (to == null || !to.hasPlayedBefore() && !to.isOnline()) {
			return error("'" + toName + "' 플레이어를 찾을 수 없습니다. 서버에 들어온 적 있는 닉네임을 입력하세요.");
		}
		if (to.getUniqueId().equals(from)) {
			return error("자신에게는 보낼 수 없습니다.");
		}
		String why = limit(from, amount);
		if (why != null) {
			return error(why);
		}
		if (!Money.take(from, amount)) {
			return error(Money.subject() + " 부족합니다.");
		}
		spend(from, amount);
		String fromName = name(from);
		Money.give(to.getUniqueId(), amount, Component.text(fromName + "님이 웹에서 " + Money.format(amount) + "을(를) 보냈습니다.",
				NamedTextColor.GREEN), fromName + "님의 웹 송금");
		Bukkit.getLogger().info("[PlayerTrade] " + fromName + " sent " + amount + " to " + to.getName() + " on the web");
		return ok(to.getName() + "님에게 " + Money.format(amount) + "을(를) 보냈습니다.");
	}

	/** Withdraws one of the player's own buy requests; the money held for it comes back. */
	static String cancelOrder(UUID player, String orderId) {
		for (BuyOrders.Order order : BuyOrders.all()) {
			if (order.id.toString().equals(orderId)) {
				if (!order.buyer.equals(player)) {
					return error("내 주문만 취소할 수 있습니다.");
				}
				BuyOrders.remove(order);
				Money.add(player, order.price);
				return ok("주문을 취소하고 " + Money.format(order.price) + "을(를) 돌려받았습니다.");
			}
		}
		return error("이미 끝난 주문입니다.");
	}

	// --- auctions ------------------------------------------------------------------------

	static String auctionBid(UUID player, String auctionId, long amount) {
		Auctions.Auction auction = Auctions.get(auctionId);
		if (auction == null) {
			return error("이미 끝난 경매입니다.");
		}
		String why = limit(player, amount);
		if (why != null) {
			return error(why);
		}
		why = Auctions.bid(player, name(player), auction, amount);
		if (why != null) {
			return error(why);
		}
		spend(player, amount);
		return ok(Money.format(amount) + " 입찰했습니다. 더 높은 입찰이 오면 돈을 돌려받아요.");
	}

	static String auctionCancel(UUID player, String auctionId) {
		Auctions.Auction auction = Auctions.get(auctionId);
		if (auction == null) {
			return error("이미 끝난 경매입니다.");
		}
		String why = Auctions.cancel(player, auction);
		return why != null ? error(why) : ok("경매를 취소했습니다. 물품은 " + (Bukkit.getPlayer(player) != null ? "인벤토리" : "우편함") + "로 돌아갑니다.");
	}

	// --- selling from the page (the player must be in game: the items are in their inventory) ----

	/**
	 * The item in slot {@code slot} of an online player's inventory, if it is
	 * still the one the page showed ({@code itemId} x {@code amount}); else null.
	 */
	private static org.bukkit.inventory.ItemStack slot(Player online, int slot, String itemId, long amount) {
		if (slot < 0 || slot >= online.getInventory().getStorageContents().length) {
			return null;
		}
		org.bukkit.inventory.ItemStack stack = online.getInventory().getItem(slot);
		if (stack == null || stack.isEmpty() || !stack.getType().getKey().getKey().equals(itemId) || stack.getAmount() != amount) {
			return null;
		}
		return stack;
	}

	private static final String GONE = "인벤토리가 바뀌었습니다. 새로 고친 뒤 다시 해 주세요.";
	private static final String OFFLINE = "게임에 접속해 있을 때만 인벤토리의 물건을 팔 수 있습니다.";

	static String sellToServer(UUID player, int slotIndex, String itemId, long amount) {
		Player online = Bukkit.getPlayer(player);
		if (online == null) {
			return error(OFFLINE);
		}
		if (!tradeEnabled) {
			return error("웹 거래가 꺼져 있습니다. 게임에서 해 주세요.");
		}
		org.bukkit.inventory.ItemStack stack = slot(online, slotIndex, itemId, amount);
		if (stack == null) {
			return error(GONE);
		}
		ItemValues.Appraisal appraisal = ItemValues.appraise(stack);
		if (!appraisal.sellable()) {
			return error("서버에 팔 수 없습니다: " + appraisal.reason());
		}
		long total = (long) Math.floor(ServerPrices.worth(stack, appraisal.unit()) + 1.0E-6);
		if (total <= 0) {
			return error("값이 0이라 팔 수 없습니다.");
		}
		String what = WebSite.itemText(stack);
		online.getInventory().setItem(slotIndex, null);
		ServerPrices.sold(stack);
		Money.add(player, total);
		online.sendMessage(Component.text("웹에서 " + what + "을(를) 서버에 팔아 " + Money.format(total) + "을(를) 받았습니다.", NamedTextColor.GOLD));
		return ok(what + "을(를) " + Money.format(total) + "에 팔았습니다.");
	}

	static String listItem(UUID player, int slotIndex, String itemId, long amount, long price) {
		Player online = Bukkit.getPlayer(player);
		if (online == null) {
			return error(OFFLINE);
		}
		if (price < Market.MIN_PRICE || price > Market.MAX_PRICE) {
			return error("가격은 " + Money.format(Market.MIN_PRICE) + " ~ " + Money.format(Market.MAX_PRICE) + " 사이로 정하세요.");
		}
		if (Market.of(player).size() >= Market.MAX_LISTINGS_PER_PLAYER) {
			return error("물품은 " + Market.MAX_LISTINGS_PER_PLAYER + "개까지 등록할 수 있습니다.");
		}
		org.bukkit.inventory.ItemStack stack = slot(online, slotIndex, itemId, amount);
		if (stack == null) {
			return error(GONE);
		}
		if (TradeTools.toolOf(stack) != null) {
			return error("무료 아이템(거래 메뉴, 웨이포인트 책)은 팔 수 없습니다.");
		}
		online.getInventory().setItem(slotIndex, null);
		Market.add(new Listing(UUID.randomUUID(), player, online.getName(), stack.clone(), (int) price, System.currentTimeMillis()));
		online.sendMessage(Component.text("웹에서 " + WebSite.itemText(stack) + "을(를) " + Money.format(price) + "에 시장에 올렸습니다.", NamedTextColor.GREEN));
		return ok("시장에 " + Money.format(price) + "에 올렸습니다.");
	}

	static String auctionCreate(UUID player, int slotIndex, String itemId, long amount, long start, long hours) {
		Player online = Bukkit.getPlayer(player);
		if (online == null) {
			return error(OFFLINE);
		}
		boolean allowed = false;
		for (int h : Auctions.HOURS) {
			allowed |= h == hours;
		}
		if (!allowed || start < Auctions.MIN_START || start > Auctions.MAX_PRICE) {
			return error("시작가와 기간을 확인하세요.");
		}
		org.bukkit.inventory.ItemStack stack = slot(online, slotIndex, itemId, amount);
		if (stack == null) {
			return error(GONE);
		}
		String why = Auctions.refuse(player, stack);
		if (why != null) {
			return error(why);
		}
		online.getInventory().setItem(slotIndex, null);
		Auctions.start(player, online.getName(), stack, start, (int) hours);
		online.sendMessage(Component.text("웹에서 " + WebSite.itemText(stack) + " 경매를 시작했습니다.", NamedTextColor.GREEN));
		return ok("경매를 시작했습니다. 시작가 " + Money.format(start) + ", " + hours + "시간.");
	}

	/** Takes one of the player's own listings off the market; the item goes to the inventory or mailbox (land stays theirs). */
	static String withdrawListing(UUID player, String listingId) {
		Listing listing = listing(listingId);
		if (listing == null || !listing.seller.equals(player)) {
			return error("이미 없어진 물품입니다.");
		}
		Market.remove(listing);
		if (Land.deedChunk(listing.item) != null) {
			return ok("땅 판매를 취소했습니다. 땅은 계속 내 것입니다.");
		}
		Mailbox.deliver(player, listing.item.clone(), "시장에서 내린 물품");
		return ok("물품을 내렸습니다. " + (Bukkit.getPlayer(player) != null ? "인벤토리" : "우편함") + "로 돌아갑니다.");
	}

	static String listingPrice(UUID player, String listingId, long price) {
		Listing listing = listing(listingId);
		if (listing == null || !listing.seller.equals(player)) {
			return error("이미 없어진 물품입니다.");
		}
		if (price < Market.MIN_PRICE || price > Market.MAX_PRICE) {
			return error("가격은 " + Money.format(Market.MIN_PRICE) + " ~ " + Money.format(Market.MAX_PRICE) + " 사이로 정하세요.");
		}
		listing.price = (int) price;
		Market.save();
		return ok("가격을 " + Money.format(price) + "(으)로 바꿨습니다.");
	}

	private static Listing listing(String id) {
		try {
			return Market.get(UUID.fromString(id));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	// --- admins ------------------------------------------------------------------------

	static boolean isAdmin(UUID id) {
		Player online = Bukkit.getPlayer(id);
		if (online != null) {
			return online.hasPermission(AdminMenu.PERMISSION);
		}
		return Bukkit.getOfflinePlayer(id).isOp();
	}

	/** Players (with money, a web password or online), the shop's goods. */
	static String adminData() {
		Map<UUID, Long> balances = Money.balances();
		Map<UUID, JsonObject> rows = new LinkedHashMap<>();
		balances.keySet().forEach(id -> rows.put(id, null));
		Bukkit.getOnlinePlayers().forEach(player -> rows.put(player.getUniqueId(), null));
		JsonArray players = new JsonArray();
		for (UUID id : rows.keySet()) {
			JsonObject row = new JsonObject();
			row.addProperty("name", name(id));
			row.addProperty("balance", balances.getOrDefault(id, 0L));
			row.addProperty("online", Bukkit.getPlayer(id) != null);
			players.add(row);
		}
		JsonArray products = new JsonArray();
		for (ShopConfig.Product product : ShopConfig.products()) {
			JsonObject row = new JsonObject();
			row.addProperty("id", product.id);
			row.addProperty("name", product.name());
			row.addProperty("price", product.price);
			products.add(row);
		}
		JsonObject root = new JsonObject();
		root.add("players", players);
		root.add("products", products);
		root.addProperty("online", Bukkit.getOnlinePlayers().size());
		root.addProperty("everyone", GiveAllMenu.recipients(true).size());
		root.addProperty("discord", Discord.enabled());
		root.add("fixedPrices", ServerPrices.fixedJson());
		return GSON.toJson(root);
	}

	/** Sets or adds to a player's balance. */
	static String balance(UUID admin, String target, String mode, long amount) {
		OfflinePlayer player = Bukkit.getOfflinePlayerIfCached(target.trim());
		if (player == null) {
			return error("'" + target + "' 플레이어를 찾을 수 없습니다.");
		}
		long before = Money.balance(player.getUniqueId());
		long after = switch (mode) {
			case "add" -> before + amount;
			case "set" -> amount;
			default -> -1;
		};
		if (after < 0) {
			return error("금액이 0보다 작아질 수 없습니다.");
		}
		Money.setBalance(player.getUniqueId(), after);
		Bukkit.getLogger().info("[PlayerTrade] " + name(admin) + " (web) changed " + player.getName() + "'s balance " + before + " -> " + after);
		return ok(player.getName() + "님의 잔액: " + Money.format(before) + " → " + Money.format(Money.balance(player.getUniqueId())));
	}

	static String giveAll(UUID admin, long amount, boolean everyone, String message) {
		if (amount <= 0 || amount > 100_000_000) {
			return error("1 ~ 100,000,000 사이로 입력하세요.");
		}
		Set<UUID> ids = GiveAllMenu.recipients(everyone);
		if (ids.isEmpty()) {
			return error("받을 사람이 없습니다.");
		}
		String text = message == null || message.isBlank() ? GiveAllMenu.DEFAULT_MESSAGE : message.strip().replace("§", "");
		if (text.length() > 60) {
			text = text.substring(0, 60);
		}
		GiveAllMenu.give(ids, amount, text, name(admin) + "(웹)", everyone);
		return ok(ids.size() + "명에게 " + Money.format(amount) + "씩 지급했습니다.");
	}

	/** Keeps an item at its full server price, or lets it move with sales again. */
	static String fixPrice(UUID admin, String itemId, boolean fixed) {
		Material material = itemId.isBlank() ? null : Material.matchMaterial(itemId);
		if (material == null || ItemValues.value(material) == null) {
			return error("서버에 팔 수 있는 아이템을 고르세요.");
		}
		ServerPrices.setFixed(material, fixed);
		Bukkit.getLogger().info("[PlayerTrade] " + name(admin) + " (web) " + (fixed ? "fixed" : "unfixed") + " the price of " + material);
		return ok(WebSite.itemText(new org.bukkit.inventory.ItemStack(material)) + (fixed ? ": 이제 시세가 떨어지지 않아요." : ": 다시 팔수록 시세가 떨어져요."));
	}

	static String shopPrice(UUID admin, String productId, long price) {
		if (price < 0 || price > ShopConfig.MAX_PRICE) {
			return error("0 ~ " + Money.format(ShopConfig.MAX_PRICE) + " 사이로 입력하세요.");
		}
		for (ShopConfig.Product product : ShopConfig.products()) {
			if (product.id.equals(productId)) {
				product.price = (int) price;
				ShopConfig.save();
				Bukkit.getLogger().info("[PlayerTrade] " + name(admin) + " (web) set " + product.id + " to " + price);
				return ok(product.name() + " 가격을 " + Money.format(price) + "(으)로 바꿨습니다.");
			}
		}
		return error("없는 상품입니다.");
	}
}
