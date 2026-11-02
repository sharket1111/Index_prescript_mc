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
		getServer().getPluginManager().registerEvents(new TradeMenu.Events(), this);
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!(sender instanceof Player player)) {
			sender.sendMessage(Component.text("플레이어만 쓸 수 있습니다.", NamedTextColor.RED));
			return true;
		}
		TradeMenu.open(player);
		return true;
	}
}
