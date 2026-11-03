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
		Market.load(this);
		History.load(this);
		Mailbox.load(this);
		BuyOrders.load(this);
		ShopConfig.load(this);
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
	}

	@Override
	public void onDisable() {
		DirectTrade.cancelAll();
		Market.save();
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!(sender instanceof Player player)) {
			sender.sendMessage(Component.text("플레이어만 쓸 수 있습니다.", NamedTextColor.RED));
			return true;
		}
		new MainMenu(player).open();
		return true;
	}
}
