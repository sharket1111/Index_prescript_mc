package io.github.sharket1111.indexprescript.paper;

import java.util.List;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Index Prescript for Paper: Rien's Caduceus and prescript terminal, built on
 * vanilla items so unmodded Java and Bedrock (Geyser) clients can play.
 */
public final class IndexPrescriptPlugin extends JavaPlugin implements TabExecutor {
	private static IndexPrescriptPlugin instance;
	private ResourcePack resourcePack;

	static IndexPrescriptPlugin instance() {
		return instance;
	}

	@Override
	public void onEnable() {
		instance = this;
		saveDefaultConfig();
		resourcePack = new ResourcePack(this);
		resourcePack.start();

		getServer().getPluginManager().registerEvents(new CaduceusListener(), this);
		getServer().getPluginManager().registerEvents(new PrescriptTracker(), this);
		getServer().getPluginManager().registerEvents(resourcePack, this);
		getServer().getPluginManager().registerEvents(new Laevatein(), this);
		getServer().getPluginManager().registerEvents(new Matthias(), this);
		Bukkit.getScheduler().runTaskTimer(this, PrescriptTracker::tick, 1L, 1L);
		Bukkit.getScheduler().runTaskTimer(this, Laevatein::tick, 1L, 1L);
		Bukkit.getScheduler().runTaskTimer(this, Matthias::tick, 1L, 1L);
		Bukkit.getScheduler().runTaskTimer(this, () -> CombatState.prune(CaduceusListener.now()), 1200L, 1200L);

		var command = getCommand("indexprescript");
		if (command != null) {
			command.setExecutor(this);
			command.setTabCompleter(this);
		}
	}

	@Override
	public void onDisable() {
		if (resourcePack != null) {
			resourcePack.stop();
		}
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (args.length >= 1 && args[0].equalsIgnoreCase("laevatein")) {
			return setLaevateinStage(sender, args);
		}
		if (args.length >= 1 && args[0].equalsIgnoreCase("pack")) {
			if (!(sender instanceof Player player)) {
				sender.sendMessage(Messages.get(sender, "command.players_only"));
				return true;
			}
			resourcePack.send(player);
			sender.sendMessage(Component.text(Messages.get(sender, "command.pack_sent"), NamedTextColor.GREEN));
			return true;
		}
		if (args.length < 2 || !args[0].equalsIgnoreCase("give")) {
			sender.sendMessage(Messages.get(sender, "command.usage"));
			return true;
		}
		Player target = args.length >= 3 ? Bukkit.getPlayerExact(args[2]) : sender instanceof Player self ? self : null;
		if (target == null) {
			sender.sendMessage(Messages.get(sender, "command.no_player", args.length >= 3 ? args[2] : "-"));
			return true;
		}
		ItemStack item = switch (args[1].toLowerCase(Locale.ROOT)) {
			case "caduceus" -> Items.caduceus(target);
			case "terminal", "rien_terminal" -> Items.terminal(target);
			case "laevatein" -> Items.laevatein(target);
			default -> null;
		};
		if (item == null) {
			sender.sendMessage(Messages.get(sender, "command.usage"));
			return true;
		}
		target.getInventory().addItem(item).values().forEach(left -> target.getWorld().dropItem(target.getLocation(), left));
		sender.sendMessage(Component.text(Messages.get(sender, "command.given", target.getName(), args[1]), NamedTextColor.GREEN));
		return true;
	}

	/** Test helper: /indexprescript laevatein <0-3> sets the held Laevatein's stage and clears its hit count. */
	private boolean setLaevateinStage(CommandSender sender, String[] args) {
		if (!(sender instanceof Player player)) {
			sender.sendMessage(Messages.get(sender, "command.players_only"));
			return true;
		}
		ItemStack held = player.getInventory().getItemInMainHand();
		int stage;
		try {
			stage = Integer.parseInt(args.length >= 2 ? args[1] : "");
		} catch (NumberFormatException e) {
			stage = -1;
		}
		if (!Items.isLaevatein(held) || stage < 0 || stage >= Items.LAEVATEIN_STAGES.size()) {
			sender.sendMessage(Messages.get(sender, "command.laevatein_usage"));
			return true;
		}
		Items.setLaevateinStage(held, stage);
		Laevatein.setHits(held, 0);
		return true;
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
		return switch (args.length) {
			case 1 -> List.of("give", "pack", "laevatein");
			case 2 -> switch (args[0].toLowerCase(Locale.ROOT)) {
				case "give" -> List.of("caduceus", "terminal", "laevatein");
				case "laevatein" -> List.of("0", "1", "2", "3");
				default -> List.of();
			};
			case 3 -> null; // online player names
			default -> List.of();
		};
	}
}
