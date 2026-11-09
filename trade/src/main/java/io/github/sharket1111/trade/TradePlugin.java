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
			getLogger().info("Converted trade diamonds to " + Money.name() + " (1 diamond = " + Money.DIAMOND_RATE + ").");
		}
		ItemValues.load(this);
		getLogger().info(ItemValues.count() + " items can be sold to the server.");
		Land.load(this);
		ChatPrompt.init(this);
		TradeTools.init(this);
		Groups.load(this);
		SharedStorage.load(this);
		getServer().getPluginManager().registerEvents(new Menu.Events(), this);
		getServer().getPluginManager().registerEvents(new Tickets(), this);
		getServer().getPluginManager().registerEvents(new LandListener(), this);
		getServer().getPluginManager().registerEvents(new ChatPrompt.Events(), this);
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
	}

	@Override
	public void onDisable() {
		// The sell box gives its items back when it closes.
		for (Player player : getServer().getOnlinePlayers()) {
			if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof SellMenu) {
				player.closeInventory();
			}
		}
		DirectTrade.cancelAll();
		Market.save();
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!(sender instanceof Player player)) {
			sender.sendMessage(Component.text("플레이어만 쓸 수 있습니다.", NamedTextColor.RED));
			return true;
		}
		if (args.length >= 1 && (args[0].equals("판매") || args[0].equalsIgnoreCase("sell"))) {
			new SellMenu(player).open();
		} else if (args.length >= 1 && (args[0].equals("잔액") || args[0].equalsIgnoreCase("money") || args[0].equalsIgnoreCase("balance"))) {
			player.sendMessage(Component.text("내 " + Money.name() + ": " + Money.format(Money.balance(player.getUniqueId())), NamedTextColor.GOLD));
		} else {
			new MainMenu(player).open();
		}
		return true;
	}
}
