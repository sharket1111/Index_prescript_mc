package io.github.sharket1111.indexprescript.paper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * /md (/거울던전): the Mirror Dungeon's menu, parties (invite, accept, leave,
 * kick), starting a run alone or together, and what happens when a member
 * dies, quits or leaves the dungeon's world.
 */
final class MdCommand implements TabExecutor, Listener {
	/** Party leader -> members (leader first). */
	private static final Map<UUID, Set<UUID>> PARTIES = new HashMap<>();
	/** Invited player -> the leader who invited them. */
	private static final Map<UUID, UUID> INVITES = new HashMap<>();

	private static int maxParty() {
		return Math.max(1, IndexPrescriptPlugin.instance().getConfig().getInt("mirror-dungeon.party-size", 4));
	}

	private static UUID partyOf(UUID id) {
		for (Map.Entry<UUID, Set<UUID>> e : PARTIES.entrySet()) {
			if (e.getValue().contains(id)) {
				return e.getKey();
			}
		}
		return null;
	}

	private static Component msg(String text, NamedTextColor color) {
		return Component.text("[거울 던전] ", NamedTextColor.DARK_RED).append(Component.text(text, color));
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (args.length >= 1 && (args[0].equals("별빛") || args[0].equalsIgnoreCase("starlight")) && sender.hasPermission("indexprescript.command")) {
			if (args.length >= 3) {
				Player target = Bukkit.getPlayerExact(args[1]);
				if (target != null) {
					MdMeta.addStarlight(target.getUniqueId(), Integer.parseInt(args[2]));
					sender.sendMessage(msg(target.getName() + " 별빛: " + MdMeta.starlight(target.getUniqueId()), NamedTextColor.AQUA));
					return true;
				}
			}
			sender.sendMessage(msg("/md 별빛 <플레이어> <+/-양>", NamedTextColor.RED));
			return true;
		}
		if (args.length >= 1 && (args[0].equals("종료") || args[0].equalsIgnoreCase("stopall")) && sender.hasPermission("indexprescript.command")) {
			MdRun.stopAll();
			sender.sendMessage(msg("진행 중인 던전을 모두 끝냈어요.", NamedTextColor.YELLOW));
			return true;
		}
		if (args.length >= 1 && (args[0].equals("적스킨") || args[0].equalsIgnoreCase("enemyskins")) && sender.hasPermission("indexprescript.command")) {
			int n = SkinPack.reloadEnemySkins();
			sender.sendMessage(msg("적 스킨 " + n + "개를 다시 읽었어요 (plugins/IndexPrescript/enemy_skins/). 다시 접속하면 보여요.", NamedTextColor.GREEN));
			return true;
		}
		if (args.length >= 2 && args[0].equals("미리보기") && sender.hasPermission("indexprescript.command")) {
			MdRun.NodeType type = args.length >= 3 && args[2].equalsIgnoreCase("boss") ? MdRun.NodeType.BOSS
					: args.length >= 3 && args[2].equalsIgnoreCase("hidden") ? MdRun.NodeType.HIDDEN : MdRun.NodeType.NORMAL;
			org.bukkit.Location at = MdRun.preview(args[1], type);
			if (at == null) {
				sender.sendMessage(msg("그런 테마팩이 없어요. 예: hell_s_chicken", NamedTextColor.RED));
			} else {
				sender.sendMessage(msg("미리보기를 만들었어요 (1분 뒤 정리).", NamedTextColor.GREEN));
				if (sender instanceof Player viewer) {
					viewer.teleport(at);
				}
			}
			return true;
		}
		if (!(sender instanceof Player p)) {
			sender.sendMessage("플레이어만 쓸 수 있어요.");
			return true;
		}
		String sub = args.length == 0 ? "" : args[0].toLowerCase(java.util.Locale.ROOT);
		MdRun run = MdRun.of(p);
		switch (sub) {
			case "" -> {
				if (run != null) {
					run.reopen(p);
				} else {
					menu(p);
				}
			}
			case "초대", "invite" -> invite(p, args.length >= 2 ? args[1] : null);
			case "수락", "accept" -> accept(p);
			case "나가기", "leave" -> {
				if (run != null) {
					run.leave(p);
				} else {
					leaveParty(p);
				}
			}
			case "추방", "kick" -> kick(p, args.length >= 2 ? args[1] : null);
			case "시작", "start" -> start(p, args.length >= 2 && (args[1].equalsIgnoreCase("hard") || args[1].equals("하드")));
			case "기프트", "gifts" -> {
				if (run != null) {
					MdShop.showGifts(run, p, null);
				}
			}
			default -> p.sendMessage(msg("/md · /md 초대 <이름> · /md 수락 · /md 시작 [하드] · /md 나가기 · /md 기프트", NamedTextColor.GRAY));
		}
		return true;
	}

	private static void menu(Player p) {
		MdMeta.welcome(p);
		UUID id = p.getUniqueId();
		UUID leader = partyOf(id);
		List<String> members = new ArrayList<>();
		if (leader != null) {
			for (UUID m : PARTIES.get(leader)) {
				Player mp = Bukkit.getPlayer(m);
				members.add((m.equals(leader) ? "★ " : "· ") + (mp == null ? "(접속 안 함)" : mp.getName()));
			}
		}
		MdGui gui = new MdGui(3, "거울 던전");
		gui.set(10, MdGui.item(Material.IRON_SWORD, "시작 (NORMAL)", NamedTextColor.WHITE, "5층 · 혼자 또는 파티로", leader == null ? "지금은 혼자 들어가요" : "파티 " + members.size() + "명"),
				() -> start(p, false));
		gui.set(11, MdGui.item(Material.NETHERITE_SWORD, "시작 (HARD)", NamedTextColor.RED, "적이 강하고, 코스트·보상이 늘어요", "층마다 바꿀 수도 있어요"), () -> start(p, true));
		List<String> lore = new ArrayList<>(members.isEmpty() ? List.of("파티가 없어요") : members);
		lore.add("");
		lore.add("초대: /md 초대 <이름> · 나가기: /md 나가기");
		lore.add("최대 " + maxParty() + "명");
		gui.set(13, MdGui.item(Material.PLAYER_HEAD, "파티", NamedTextColor.YELLOW, lore.toArray(String[]::new)), (Runnable) null);
		gui.set(15, MdGui.item(Material.NETHER_STAR, "별빛 " + MdMeta.starlight(id), NamedTextColor.AQUA, "별의 가호(시작 버프)와 기프트 탐색에 써요",
				"층을 깰 때마다 쌓여요"), (Runnable) null);
		gui.set(16, MdGui.item(Material.CLOCK, "주간 보상 " + MdMeta.claims(id) + "/" + MdMeta.WEEKLY_CLAIMS, NamedTextColor.GOLD, "매주 월요일에 초기화돼요",
				"기록: " + MdMeta.runs(id) + "번 · 최고 " + MdMeta.best(id) + "층"), (Runnable) null);
		gui.fill().open(p);
	}

	private static void invite(Player p, String name) {
		if (name == null) {
			p.sendMessage(msg("/md 초대 <이름>", NamedTextColor.RED));
			return;
		}
		Player target = Bukkit.getPlayerExact(name);
		if (target == null || target.equals(p)) {
			p.sendMessage(msg("그 플레이어를 찾을 수 없어요.", NamedTextColor.RED));
			return;
		}
		UUID leader = partyOf(p.getUniqueId());
		if (leader != null && !leader.equals(p.getUniqueId())) {
			p.sendMessage(msg("파티 리더만 초대할 수 있어요.", NamedTextColor.RED));
			return;
		}
		Set<UUID> party = PARTIES.computeIfAbsent(p.getUniqueId(), k -> new LinkedHashSet<>(List.of(k)));
		if (party.size() >= maxParty()) {
			p.sendMessage(msg("파티가 가득 찼어요 (최대 " + maxParty() + "명).", NamedTextColor.RED));
			return;
		}
		INVITES.put(target.getUniqueId(), p.getUniqueId());
		p.sendMessage(msg(target.getName() + "님을 초대했어요.", NamedTextColor.GREEN));
		target.sendMessage(msg(p.getName() + "님이 거울 던전 파티에 초대했어요. ", NamedTextColor.YELLOW)
				.append(Component.text("[수락]", NamedTextColor.GREEN).clickEvent(ClickEvent.runCommand("/md 수락"))));
	}

	private static void accept(Player p) {
		UUID leader = INVITES.remove(p.getUniqueId());
		if (leader == null || !PARTIES.containsKey(leader)) {
			p.sendMessage(msg("받은 초대가 없어요.", NamedTextColor.RED));
			return;
		}
		leaveParty(p);
		Set<UUID> party = PARTIES.get(leader);
		if (party.size() >= maxParty()) {
			p.sendMessage(msg("파티가 가득 찼어요.", NamedTextColor.RED));
			return;
		}
		party.add(p.getUniqueId());
		for (UUID m : party) {
			Player mp = Bukkit.getPlayer(m);
			if (mp != null) {
				mp.sendMessage(msg(p.getName() + "님이 파티에 들어왔어요 (" + party.size() + "/" + maxParty() + ").", NamedTextColor.GREEN));
			}
		}
	}

	private static void leaveParty(Player p) {
		UUID leader = partyOf(p.getUniqueId());
		if (leader == null) {
			return;
		}
		Set<UUID> party = PARTIES.get(leader);
		party.remove(p.getUniqueId());
		if (leader.equals(p.getUniqueId())) {
			PARTIES.remove(leader);
			if (!party.isEmpty()) {
				UUID next = party.iterator().next();
				PARTIES.put(next, party);
			}
		}
		if (party.size() <= 1) {
			PARTIES.values().removeIf(s -> s.size() <= 1);
		}
		p.sendMessage(msg("파티를 나왔어요.", NamedTextColor.GRAY));
	}

	private static void kick(Player p, String name) {
		Set<UUID> party = PARTIES.get(p.getUniqueId());
		Player target = name == null ? null : Bukkit.getPlayerExact(name);
		if (party == null || target == null || !party.remove(target.getUniqueId())) {
			p.sendMessage(msg("파티 리더만, 파티원만 내보낼 수 있어요.", NamedTextColor.RED));
			return;
		}
		target.sendMessage(msg("파티에서 내보내졌어요.", NamedTextColor.GRAY));
	}

	private static void start(Player p, boolean hard) {
		if (MdRun.of(p) != null) {
			return;
		}
		UUID leader = partyOf(p.getUniqueId());
		List<Player> party = new ArrayList<>();
		if (leader == null) {
			party.add(p);
		} else if (!leader.equals(p.getUniqueId())) {
			p.sendMessage(msg("파티 리더가 시작해요.", NamedTextColor.RED));
			return;
		} else {
			for (UUID m : PARTIES.get(leader)) {
				Player mp = Bukkit.getPlayer(m);
				if (mp != null && mp.isOnline() && MdRun.of(mp) == null) {
					party.add(mp);
				}
			}
		}
		p.closeInventory();
		MdRun.start(party, hard);
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
		if (args.length == 1) {
			return List.of("초대", "수락", "시작", "나가기", "추방", "기프트");
		}
		if (args.length == 2 && (args[0].equals("초대") || args[0].equals("추방") || args[0].equals("invite") || args[0].equals("kick"))) {
			return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
		}
		if (args.length == 2 && args[0].equals("시작")) {
			return List.of("하드");
		}
		return List.of();
	}

	// --- the run's people -------------------------------------------------------------------------

	@EventHandler(priority = EventPriority.HIGH)
	public void onDeath(PlayerDeathEvent event) {
		Player p = event.getEntity();
		MdRun run = MdRun.of(p);
		if (run == null || !MdWorld.in(p)) {
			return;
		}
		event.setKeepInventory(true);
		event.setKeepLevel(true);
		event.getDrops().clear();
		event.setDroppedExp(0);
		run.down(p);
	}

	@EventHandler
	public void onRespawn(PlayerRespawnEvent event) {
		Player p = event.getPlayer();
		MdRun run = MdRun.of(p);
		if (run == null) {
			return;
		}
		event.setRespawnLocation(MdWorld.entrance(run.slot, run.battle != null ? run.battle.room() : MdWorld.Room.START));
		Bukkit.getScheduler().runTask(IndexPrescriptPlugin.instance(), () -> {
			if (run.downed.contains(p.getUniqueId())) {
				p.setGameMode(GameMode.SPECTATOR);
			}
		});
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		Player p = event.getPlayer();
		MdRun run = MdRun.of(p);
		if (run != null) {
			run.leave(p);
		}
		MdGifts.forget(p);
	}

	/** Leaving the dungeon's world any other way (another plugin's teleport) leaves the run. */
	@EventHandler
	public void onWorld(PlayerChangedWorldEvent event) {
		Player p = event.getPlayer();
		MdRun run = MdRun.of(p);
		if (run != null && MdWorld.is(event.getFrom()) && !MdWorld.in(p)) {
			run.leave(p);
		}
	}

	/** Someone who was in the dungeon when the server stopped comes back to the main world. */
	@EventHandler
	public void onJoin(PlayerJoinEvent event) {
		Player p = event.getPlayer();
		if (MdWorld.in(p) && MdRun.of(p) == null) {
			Bukkit.getScheduler().runTask(IndexPrescriptPlugin.instance(), () -> {
				p.setGameMode(GameMode.SURVIVAL);
				p.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
			});
		}
	}
}
