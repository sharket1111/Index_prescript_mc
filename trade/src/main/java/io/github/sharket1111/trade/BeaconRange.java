package io.github.sharket1111.trade;

import java.util.List;
import java.util.Locale;

import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Beacon;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * /신호기: the effect range of the beacon the player looks at.
 *
 * - /신호기 [정보]: its tier and range now.
 * - /신호기 <블록>: sets the range (1 to {@link #MAX_RANGE} blocks).
 * - /신호기 배수 <n>: the vanilla range for its tier times n.
 * - /신호기 초기화: back to the vanilla range (tier x 10 + 10).
 *
 * Uses Paper's beacon range. The range set is also kept in the beacon's own
 * data and put back whenever its chunk loads, so it lasts across restarts.
 * Vanilla beacons reach a box: that many blocks out sideways, and the whole
 * height of the world.
 */
final class BeaconRange implements TabExecutor, Listener {
	static final int MAX_RANGE = 512;
	private static final int REACH = 6;
	private final NamespacedKey key;

	BeaconRange(JavaPlugin plugin) {
		this.key = new NamespacedKey(plugin, "beacon_range");
	}

	private static void tell(CommandSender to, String text, NamedTextColor colour) {
		to.sendMessage(Component.text(text, colour));
	}

	private static int vanilla(Beacon beacon) {
		return Math.max(1, beacon.getTier()) * 10 + 10;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!(sender instanceof Player player)) {
			tell(sender, "플레이어만 쓸 수 있습니다.", NamedTextColor.RED);
			return true;
		}
		Block block = player.getTargetBlockExact(REACH);
		if (block == null || !(block.getState() instanceof Beacon beacon)) {
			tell(player, "신호기를 바라보고 입력하세요. (" + REACH + "블록 이내)", NamedTextColor.RED);
			return true;
		}
		String first = args.length == 0 ? "정보" : args[0].toLowerCase(Locale.ROOT);
		switch (first) {
			case "정보", "info" -> info(player, beacon);
			case "초기화", "reset" -> {
				beacon.resetEffectRange();
				beacon.getPersistentDataContainer().remove(key);
				beacon.update(true);
				tell(player, "신호기 범위를 기본값(" + vanilla(beacon) + "블록)으로 되돌렸습니다.", NamedTextColor.GREEN);
			}
			case "배수", "x", "multiply" -> {
				if (args.length < 2) {
					tell(player, "사용법: /" + label + " 배수 <배수> (예: 2, 1.5)", NamedTextColor.GRAY);
					return true;
				}
				double factor = number(player, args[1]);
				if (factor > 0) {
					set(player, beacon, vanilla(beacon) * factor);
				}
			}
			default -> {
				double range = number(player, args[0]);
				if (range > 0) {
					set(player, beacon, range);
				}
			}
		}
		return true;
	}

	private static double number(Player player, String text) {
		try {
			double value = Double.parseDouble(text);
			if (value > 0 && Double.isFinite(value)) {
				return value;
			}
		} catch (NumberFormatException ignored) {
			// Told below.
		}
		tell(player, "0보다 큰 숫자를 입력하세요: " + text, NamedTextColor.RED);
		return -1;
	}

	private void set(Player player, Beacon beacon, double range) {
		double clamped = Math.min(MAX_RANGE, Math.max(1, Math.round(range)));
		beacon.setEffectRange(clamped);
		beacon.getPersistentDataContainer().set(key, PersistentDataType.DOUBLE, clamped);
		beacon.update(true);
		tell(player, "신호기 범위를 " + (int) clamped + "블록으로 정했습니다. (기본 " + vanilla(beacon) + "블록, 단계 " + beacon.getTier() + ")",
				NamedTextColor.GREEN);
		if (range > MAX_RANGE) {
			tell(player, "최대 " + MAX_RANGE + "블록까지만 정할 수 있습니다.", NamedTextColor.GRAY);
		}
		Block block = beacon.getBlock();
		block.getWorld().spawnParticle(Particle.END_ROD, block.getLocation().add(0.5, 1.2, 0.5), 30, 0.3, 0.6, 0.3, 0.05);
		block.getWorld().playSound(block.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, SoundCategory.BLOCKS, 1.0F, 1.2F);
	}

	private void info(Player player, Beacon beacon) {
		Double saved = beacon.getPersistentDataContainer().get(key, PersistentDataType.DOUBLE);
		tell(player, "신호기 단계 " + beacon.getTier() + " · 범위 " + (int) Math.round(beacon.getEffectRange()) + "블록"
				+ (saved == null ? " (기본값)" : " (직접 지정, 기본 " + vanilla(beacon) + "블록)"), NamedTextColor.YELLOW);
		tell(player, "/신호기 <블록> · /신호기 배수 <n> · /신호기 초기화", NamedTextColor.GRAY);
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
		if (args.length == 1) {
			return List.of("정보", "초기화", "배수", "50", "100", "200");
		}
		if (args.length == 2 && (args[0].equals("배수") || args[0].equalsIgnoreCase("x"))) {
			return List.of("1.5", "2", "3", "5");
		}
		return List.of();
	}

	/** A range set by command comes back whenever the beacon's chunk loads. */
	@EventHandler
	public void onChunkLoad(ChunkLoadEvent event) {
		restore(event.getChunk());
	}

	/** On start: the chunks already loaded. */
	void restoreLoaded() {
		for (org.bukkit.World world : org.bukkit.Bukkit.getWorlds()) {
			for (Chunk chunk : world.getLoadedChunks()) {
				restore(chunk);
			}
		}
	}

	private void restore(Chunk chunk) {
		for (BlockState state : chunk.getTileEntities(block -> block.getType() == org.bukkit.Material.BEACON, false)) {
			if (state instanceof Beacon beacon) {
				Double saved = beacon.getPersistentDataContainer().get(key, PersistentDataType.DOUBLE);
				if (saved != null && Math.abs(beacon.getEffectRange() - saved) > 0.01) {
					beacon.setEffectRange(saved);
					beacon.update(true);
				}
			}
		}
	}
}
