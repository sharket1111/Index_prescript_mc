package io.github.sharket1111.trade;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** Trading between players. /trade opens the trade menu. */
public final class TradePlugin extends JavaPlugin {
	@Override
	public void onEnable() {
		TradeItems.init(this);
		UiMode.init(this);
		Tickets.init(this);
		// The currency first: a server coming from trade diamonds converts its saved prices as they load.
		Money.load(this);
		boolean migrate = Money.needsMigration();
		Market.load(this);
		History.load(this);
		Mailbox.load(this);
		BuyOrders.load(this);
		ShopConfig.load(this);
		if (migrate) {
			Market.migrate();
			Mailbox.migrate();
			BuyOrders.save();
			History.save();
			Money.finishMigration();
			getLogger().info("Converted saved trade diamond prices to " + Money.name() + " (1 diamond = " + Money.DIAMOND_RATE + ").");
		}
		ItemValues.load(this);
		ServerPrices.load(this);
		getLogger().info(ItemValues.count() + " items can be sold to the server.");
		Land.load(this);
		ChatPrompt.init(this);
		AnvilPrompt.init(this);
		TradeTools.init(this);
		Groups.load(this);
		SharedStorage.load(this);
		Discord.load(this);
		Auctions.load(this);
		InventorySnapshots.start(this);
		getServer().getPluginManager().registerEvents(new Menu.Events(), this);
		getServer().getPluginManager().registerEvents(new Tickets(), this);
		getServer().getPluginManager().registerEvents(new LandListener(), this);
		getServer().getPluginManager().registerEvents(new ChatPrompt.Events(), this);
		getServer().getPluginManager().registerEvents(new AnvilPrompt(), this);
		getServer().getPluginManager().registerEvents(new Groups.Events(), this);
		getServer().getPluginManager().registerEvents(new TradeTools.Events(), this);
		BeaconRange beacons = new BeaconRange(this);
		getServer().getPluginManager().registerEvents(beacons, this);
		var beaconCommand = getCommand("beaconrange");
		if (beaconCommand != null) {
			beaconCommand.setExecutor(beacons);
			beaconCommand.setTabCompleter(beacons);
		}
		getServer().getScheduler().runTask(this, beacons::restoreLoaded);
		// Last: the web page reads the market, land, groups and the rest, so everything must be loaded first.
		try {
			WebSite.start(this);
		} catch (RuntimeException e) {
			getLogger().log(java.util.logging.Level.SEVERE, "The web page could not start; the rest of the plugin works as usual", e);
		}
		Discord.serverStarted();
	}

	@Override
	public void onDisable() {
		// The sell box gives its items back when it closes.
		for (Player player : getServer().getOnlinePlayers()) {
			if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof SellMenu) {
				player.closeInventory();
			}
		}
		WebSite.stop();
		Auctions.save();
		InventorySnapshots.saveAll();
		ServerPrices.save();
		Discord.serverStopping();
		AnvilPrompt.closeAll();
		DirectTrade.cancelAll();
		Market.save();
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!(sender instanceof Player player)) {
			sender.sendMessage(Component.text("플레이어만 쓸 수 있습니다.", NamedTextColor.RED));
			return true;
		}
		if (args.length >= 2 && args[0].equals("공지") && player.hasPermission(AdminMenu.PERMISSION)) {
			WebNotices.post("공지", String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)), player.getName(), false);
			player.sendMessage(Component.text("웹 사이트에 공지를 올렸습니다.", NamedTextColor.GREEN));
		} else if (args.length >= 1 && args[0].equals("시세고정") && player.hasPermission(AdminMenu.PERMISSION)) {
			fixPrice(player, args.length >= 2 ? args[1] : "");
		} else if (args.length >= 1 && args[0].equals("디스코드") && player.hasPermission(AdminMenu.PERMISSION)) {
			player.sendMessage(Component.text(Discord.test(player.getName()), NamedTextColor.YELLOW));
		} else if (args.length >= 1 && (args[0].equals("경매") || args[0].equalsIgnoreCase("auction"))) {
			new AuctionMenu(player, 0, false).open();
		} else if (args.length >= 1 && (args[0].equals("웹") || args[0].equalsIgnoreCase("web"))) {
			WebSite.command(player, args.length >= 2 ? args[1] : "");
		} else if (args.length >= 1 && (args[0].equals("화폐") || args[0].equalsIgnoreCase("currency"))) {
			currency(player, args);
		} else if (args.length >= 1 && (args[0].equals("판매") || args[0].equalsIgnoreCase("sell"))) {
			new SellMenu(player).open();
		} else if (args.length >= 1 && (args[0].equals("잔액") || args[0].equalsIgnoreCase("money") || args[0].equalsIgnoreCase("balance"))) {
			player.sendMessage(Component.text("내 " + Money.name() + ": " + Money.format(Money.balance(player.getUniqueId())), NamedTextColor.GOLD));
		} else {
			new MainMenu(player).open();
		}
		return true;
	}

	/** /trade 화폐 <이름> [단위]: renames the currency (admins). */
	private static void currency(Player player, String[] args) {
		if (!player.hasPermission(AdminMenu.PERMISSION)) {
			player.sendMessage(Component.text("관리자만 쓸 수 있습니다.", NamedTextColor.RED));
			return;
		}
		if (args.length < 2) {
			player.sendMessage(Component.text("지금 화폐: " + Money.name() + " (단위 " + Money.symbol() + ")", NamedTextColor.GOLD));
			player.sendMessage(Component.text("사용법: /trade 화폐 <이름> [단위]  예: /trade 화폐 원 원", NamedTextColor.GRAY));
			return;
		}
		String problem = Money.badName(args[1], AdminMenu.NAME_MAX);
		if (problem == null && args.length >= 3) {
			problem = Money.badName(args[2], AdminMenu.SYMBOL_MAX);
		}
		if (problem != null) {
			player.sendMessage(Component.text(problem, NamedTextColor.RED));
			return;
		}
		Money.rename(args[1], args.length >= 3 ? args[2] : null);
		player.sendMessage(Component.text("화폐를 '" + Money.name() + "'(단위 " + Money.symbol() + ")(으)로 바꿨습니다. 예: "
				+ Money.format(1250), NamedTextColor.GREEN));
	}

	/** /trade 시세고정 [목록|아이템 이름]: keeps the item in hand (or the named one) at its full server price, or lets it move again. */
	private void fixPrice(Player player, String arg) {
		if (arg.equals("목록") || arg.equalsIgnoreCase("list")) {
			java.util.List<org.bukkit.Material> fixed = ServerPrices.fixedItems();
			player.sendMessage(Component.text(fixed.isEmpty() ? "시세가 고정된 아이템이 없습니다."
					: "시세 고정: " + String.join(", ", fixed.stream().map(m -> WebSite.itemText(new org.bukkit.inventory.ItemStack(m))).toList()),
					NamedTextColor.YELLOW));
			return;
		}
		org.bukkit.Material material = arg.isEmpty() ? player.getInventory().getItemInMainHand().getType() : org.bukkit.Material.matchMaterial(arg);
		if (material == null || material.isAir() || ItemValues.value(material) == null) {
			player.sendMessage(Component.text("서버에 팔 수 있는 아이템을 손에 들고 /trade 시세고정 을 치세요. (목록: /trade 시세고정 목록)", NamedTextColor.RED));
			return;
		}
		boolean fixed = !ServerPrices.fixed(material);
		ServerPrices.setFixed(material, fixed);
		String name = WebSite.itemText(new org.bukkit.inventory.ItemStack(material));
		player.sendMessage(Component.text(fixed ? name + ": 이제 시세가 떨어지지 않고 항상 제값에 팔려요." : name + ": 다시 팔수록 시세가 떨어져요.",
				fixed ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
	}
}
