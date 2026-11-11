package io.github.sharket1111.indexprescript.paper;

import java.util.List;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.LivingEntity;
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
	public void onLoad() {
		saveDefaultConfig();
		if (getConfig().getBoolean("bedrock-pack.install", true)) {
			BedrockPack.install(this);
		}
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
		getServer().getPluginManager().registerEvents(new Cinematic.Events(), this);
		getServer().getPluginManager().registerEvents(new MangRing.Events(), this);
		getServer().getPluginManager().registerEvents(new Stagger(), this);
		getServer().getPluginManager().registerEvents(new RenoirHammer(), this);
		getServer().getPluginManager().registerEvents(new BlackGod(), this);
		getServer().getPluginManager().registerEvents(new BlackGodRevive.Events(), this);
		getServer().getPluginManager().registerEvents(new BlackGodState.Events(), this);
		getServer().getPluginManager().registerEvents(new Sanity(), this);
		getServer().getPluginManager().registerEvents(new ClashDummy(), this);
		getServer().getPluginManager().registerEvents(new MimicryWeapon(), this);
		getServer().getPluginManager().registerEvents(new Bleed(), this);
		getServer().getPluginManager().registerEvents(new Rupture(), this);
		getServer().getPluginManager().registerEvents(new MimicrySkill.Events(), this);
		// Made in the mod studio: keyframe animations, particle effects and skill numbers, reloadable in game.
		getLogger().info(SkillTuning.load(this));
		StudioEffects.load(this);
		Animations.load(this);
		Bukkit.getScheduler().runTaskTimer(this, Animations::tick, 1L, 1L);
		Bukkit.getScheduler().runTask(this, ClashDummy::scan);
		Bukkit.getScheduler().runTaskTimer(this, PrescriptTracker::tick, 1L, 1L);
		Bukkit.getScheduler().runTaskTimer(this, Laevatein::tick, 1L, 1L);
		Bukkit.getScheduler().runTaskTimer(this, Matthias::tick, 1L, 1L);
		Bukkit.getScheduler().runTaskTimer(this, IndexMark::tick, 1L, 1L);
		Bukkit.getScheduler().runTaskTimer(this, StatusIcons::tick, 1L, 1L);
		Bukkit.getScheduler().runTaskTimer(this, Stagger::tick, 1L, 1L);
		Bukkit.getScheduler().runTaskTimer(this, TotalDamage::tick, 1L, 1L);
		Bukkit.getScheduler().runTaskTimer(this, SkillOverlay::tick, 1L, 1L);
		Bukkit.getScheduler().runTaskTimer(this, MangRing::tick, 1L, 1L);
		Bukkit.getScheduler().runTaskTimer(this, RenoirHammer::tick, 1L, 1L);
		Bukkit.getScheduler().runTaskTimer(this, BlackGod::tick, 1L, 1L);
		Bukkit.getScheduler().runTaskTimer(this, BlackGodRevive::tickAll, 1L, 1L);
		Bukkit.getScheduler().runTaskTimer(this, BlackGodSkill::tickAll, 1L, 1L);
		Bukkit.getScheduler().runTaskTimer(this, BlackGodState::tick, 1L, 1L);
		Bukkit.getScheduler().runTaskTimer(this, Clash::tick, 1L, 1L);
		Bukkit.getScheduler().runTaskTimer(this, RienSkill::tickAll, 1L, 1L);
		Bukkit.getScheduler().runTaskTimer(this, MimicrySkill::tickAll, 1L, 1L);
		Bukkit.getScheduler().runTaskTimer(this, MimicryWeapon::tick, 1L, 1L);
		Bukkit.getScheduler().runTaskTimer(this, Sanity::tick, 1L, 1L);
		Bukkit.getScheduler().runTaskTimer(this, Hud::tick, 1L, 1L);
		Bukkit.getScheduler().runTaskTimer(this, UnitHud::tick, 1L, 1L);
		Bukkit.getScheduler().runTaskTimer(this, () -> CombatState.prune(CaduceusListener.now()), 1200L, 1200L);

		var command = getCommand("indexprescript");
		if (command != null) {
			command.setExecutor(this);
			command.setTabCompleter(this);
		}
	}

	@Override
	public void onDisable() {
		Animations.stop(null);
		RenoirSkill.endAll();
		BlackGodRevive.endAll();
		BlackGodSkill.endAll();
		BlackGodState.clearAll();
		Clash.endAll();
		RienSkill.endAll();
		MimicrySkill.endAll();
		MimicryWeapon.endAll();
		UnitHud.clearAll();
		BlackGod.stopAll();
		PromisedNails.clearAll();
		Cinematic.endAll();
		SkillOverlay.hideAll();
		MangRing.hideAll();
		IndexMark.clearAll();
		Stagger.clearAll();
		if (resourcePack != null) {
			resourcePack.stop();
		}
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (args.length >= 1 && args[0].equalsIgnoreCase("laevatein")) {
			return setLaevateinStage(sender, args);
		}
		if (args.length >= 1 && (args[0].equalsIgnoreCase("anim") || args[0].equalsIgnoreCase("fx") || args[0].equalsIgnoreCase("skills"))) {
			return studio(sender, args);
		}
		if (args.length >= 1 && args[0].equalsIgnoreCase("mang")) {
			return mang(sender, args);
		}
		if (args.length >= 1 && args[0].equalsIgnoreCase("sanity")) {
			return sanity(sender, args);
		}
		if (args.length >= 1 && args[0].equalsIgnoreCase("renoir")) {
			return renoir(sender, args);
		}
		if (args.length >= 1 && args[0].equalsIgnoreCase("dummy")) {
			return dummy(sender, args);
		}
		if (args.length >= 1 && args[0].equalsIgnoreCase("blackgod")) {
			if (!(sender instanceof Player player)) {
				sender.sendMessage(Messages.get(sender, "command.players_only"));
				return true;
			}
			BlackGod.toggle(player);
			return true;
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
			case "hammer", "renoir_hammer" -> Items.renoirHammer(target);
			case "mimicry", "미미크리" -> Items.mimicry(target);
			case "blackgod", "black_god" -> BlackGod.toggleItem(target);
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

	/**
	 * /indexprescript mang [1-6]: shows that many of [Mang]'s rings (one by
	 * default) on the held weapon for 15 seconds; without a number it hides
	 * rings already shown.
	 */
	private boolean mang(CommandSender sender, String[] args) {
		if (!(sender instanceof Player player)) {
			sender.sendMessage(Messages.get(sender, "command.players_only"));
			return true;
		}
		int count = 1;
		if (args.length >= 2) {
			try {
				count = Integer.parseInt(args[1]);
			} catch (NumberFormatException e) {
				count = -1;
			}
			if (count < 1 || count > MangRing.MAX_RINGS) {
				sender.sendMessage(Messages.get(sender, "command.mang_usage"));
				return true;
			}
		} else if (MangRing.showing(player)) {
			MangRing.hide(player);
			sender.sendMessage(Messages.get(sender, "command.mang_off"));
			return true;
		}
		MangRing.show(player, 300, count);
		sender.sendMessage(Messages.get(sender, "command.mang_on", count));
		return true;
	}

	/** /indexprescript sanity [value]: shows the sender's Sanity, or sets health to match a value for testing. */
	private boolean sanity(CommandSender sender, String[] args) {
		if (!(sender instanceof Player player)) {
			sender.sendMessage(Messages.get(sender, "command.players_only"));
			return true;
		}
		if (args.length >= 2) {
			try {
				Sanity.set(player, Integer.parseInt(args[1]));
			} catch (NumberFormatException e) {
				sender.sendMessage(Messages.get(sender, "command.usage"));
				return true;
			}
		}
		sender.sendMessage(Messages.get(sender, "command.sanity", Sanity.of(player)));
		return true;
	}

	/** Test helper: /indexprescript laevatein <0-3> sets the held Laevatein's stage and clears its hit count. */
	private boolean setLaevateinStage(CommandSender sender, String[] args) {
		if (!(sender instanceof Player player)) {
			sender.sendMessage(Messages.get(sender, "command.players_only"));
			return true;
		}
		ItemStack held = player.getInventory().getItemInMainHand();
		if (args.length < 2 && Items.isLaevatein(held)) {
			// Status for testing: stage, hits toward the next release, and the displayed stack size.
			sender.sendMessage(Messages.get(sender, "command.laevatein_status", Items.laevateinStage(held), Laevatein.hits(held),
					Laevatein.iconCount(held), Laevatein.ready(held)));
			return true;
		}
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

	/** /indexprescript renoir [voice] [preservation]: shows or sets the hammer's Black God's Voice and Preservation, for testing. */
	private boolean renoir(CommandSender sender, String[] args) {
		if (!(sender instanceof Player player)) {
			sender.sendMessage(Messages.get(sender, "command.players_only"));
			return true;
		}
		try {
			if (args.length >= 2) {
				int voice = Integer.parseInt(args[1]);
				int preservation = args.length >= 3 ? Integer.parseInt(args[2]) : RenoirHammer.preservation(player);
				RenoirHammer.set(player, voice, preservation);
			}
		} catch (NumberFormatException e) {
			sender.sendMessage(Component.text("/ip renoir [voice 0-100] [preservation 0-20]", NamedTextColor.RED));
			return true;
		}
		sender.sendMessage(Component.text("Voice " + RenoirHammer.voice(player) + "/" + RenoirHammer.MAX_VOICE
				+ ", Preservation " + RenoirHammer.preservation(player) + " (potency " + RenoirHammer.potency(player) + ")", NamedTextColor.GOLD));
		return true;
	}

	/**
	 * /ip dummy [base] [coin power] [coins] [types] [sanity] spawns a clash dummy;
	 * /ip dummy set <base|power|coins|types|sanity|damage> <value>, /ip dummy info,
	 * /ip dummy remove act on the dummy looked at (or the nearest within 16 blocks);
	 * /ip dummy removeall removes every loaded one.
	 */
	private boolean dummy(CommandSender sender, String[] args) {
		if (!(sender instanceof Player player)) {
			sender.sendMessage(Messages.get(sender, "command.players_only"));
			return true;
		}
		String sub = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "";
		try {
			switch (sub) {
				case "set" -> {
					LivingEntity dummy = nearDummy(player);
					if (dummy == null || args.length < 4 || !ClashDummy.set(dummy, args[2], args[3])) {
						player.sendMessage(Component.text("/ip dummy set <base|power|coins|types|sanity|damage> <값> (더미를 바라보고)", NamedTextColor.RED));
						return true;
					}
					player.sendMessage(Component.text("더미: " + ClashDummy.describe(dummy), NamedTextColor.GOLD));
				}
				case "info" -> {
					LivingEntity dummy = nearDummy(player);
					player.sendMessage(dummy == null ? Component.text("근처에 더미가 없습니다.", NamedTextColor.RED)
							: Component.text("더미: " + ClashDummy.describe(dummy), NamedTextColor.GOLD));
				}
				case "remove" -> {
					LivingEntity dummy = nearDummy(player);
					if (dummy != null) {
						dummy.remove();
					}
					player.sendMessage(Component.text(dummy == null ? "근처에 더미가 없습니다." : "더미를 없앴습니다.", NamedTextColor.GOLD));
				}
				case "removeall" -> {
					List<LivingEntity> all = ClashDummy.all();
					all.forEach(org.bukkit.entity.Entity::remove);
					player.sendMessage(Component.text("더미 " + all.size() + "개를 없앴습니다.", NamedTextColor.GOLD));
				}
				default -> {
					int base = args.length >= 2 ? Integer.parseInt(args[1]) : 4;
					int power = args.length >= 3 ? Integer.parseInt(args[2]) : 3;
					int coins = args.length >= 4 ? Integer.parseInt(args[3]) : 3;
					String types = args.length >= 5 ? args[4] : "";
					int sanity = args.length >= 6 ? Integer.parseInt(args[5]) : 0;
					LivingEntity dummy = ClashDummy.spawn(player, base, power, coins, types, sanity);
					player.sendMessage(Component.text("합 테스트 더미를 세웠습니다. " + ClashDummy.describe(dummy), NamedTextColor.GOLD));
				}
			}
		} catch (NumberFormatException e) {
			player.sendMessage(Component.text("/ip dummy [기본위력] [코인위력] [코인수] [코인유형 N/R/G] [정신력]", NamedTextColor.RED));
		}
		return true;
	}

	/**
	 * The mod studio's files: /ip anim [이름|list|reload|stop], /ip fx [이름|list|reload],
	 * /ip skills [reload]. Files go in plugins/IndexPrescript/animations, effects and skills.yml.
	 */
	private boolean studio(CommandSender sender, String[] args) {
		String what = args[0].toLowerCase(Locale.ROOT);
		String sub = args.length >= 2 ? args[1] : "list";
		NamedTextColor ok = NamedTextColor.GREEN;
		switch (what) {
			case "skills" -> {
				if (sub.equalsIgnoreCase("reload")) {
					sender.sendMessage(Component.text(SkillTuning.load(this), ok));
				} else {
					sender.sendMessage(Component.text("스킬 수치 파일: " + SkillTuning.file().getPath() + " — 고친 뒤 /ip skills reload", NamedTextColor.YELLOW));
				}
			}
			case "fx" -> {
				if (sub.equalsIgnoreCase("reload")) {
					sender.sendMessage(Component.text("이펙트 " + StudioEffects.load(this) + "개를 읽었어요.", ok));
				} else if (sub.equalsIgnoreCase("list")) {
					sender.sendMessage(Component.text("이펙트: " + String.join(", ", StudioEffects.names()) + " (" + StudioEffects.folder().getPath() + ")", NamedTextColor.YELLOW));
				} else if (!(sender instanceof Player player)) {
					sender.sendMessage(Messages.get(sender, "command.players_only"));
				} else if (!StudioEffects.play(sub, player.getLocation())) {
					sender.sendMessage(Component.text("'" + sub + "' 이펙트가 없어요. /ip fx list", NamedTextColor.RED));
				}
			}
			default -> {
				if (sub.equalsIgnoreCase("reload")) {
					int fx = StudioEffects.load(this);
					sender.sendMessage(Component.text("애니메이션 " + Animations.load(this) + "개, 이펙트 " + fx + "개를 읽었어요.", ok));
				} else if (sub.equalsIgnoreCase("list")) {
					sender.sendMessage(Component.text("애니메이션: " + String.join(", ", Animations.names()) + " (" + Animations.folder().getPath() + ")",
							NamedTextColor.YELLOW));
				} else if (sub.equalsIgnoreCase("stop")) {
					sender.sendMessage(Component.text(Animations.stop(sender instanceof Player p ? p : null) + "개를 멈췄어요.", ok));
				} else if (sub.equalsIgnoreCase("rig") && sender instanceof Player player) {
					String outfit = args.length >= 3 ? args[2] : "player";
					Animations.testRig(player, outfit);
					sender.sendMessage(Component.text("앞에 인형(" + outfit + ")을 10초 동안 세웠어요. 몸이 안 보이면 다른 옷으로도 해 보세요: /ip anim rig suit, /ip anim rig steve", ok));
				} else if (!(sender instanceof Player player)) {
					sender.sendMessage(Messages.get(sender, "command.players_only"));
				} else if (!Animations.play(player, sub)) {
					sender.sendMessage(Component.text("'" + sub + "' 애니메이션이 없어요. /ip anim list", NamedTextColor.RED));
				}
			}
		}
		return true;
	}

	/** The clash dummy the player looks at, or the nearest within 16 blocks. */
	private static LivingEntity nearDummy(Player player) {
		if (player.getTargetEntity(16) instanceof LivingEntity living && ClashDummy.is(living)) {
			return living;
		}
		LivingEntity best = null;
		double bestDistance = 16 * 16;
		for (LivingEntity dummy : ClashDummy.all()) {
			if (dummy.getWorld() == player.getWorld() && dummy.getLocation().distanceSquared(player.getLocation()) < bestDistance) {
				best = dummy;
				bestDistance = dummy.getLocation().distanceSquared(player.getLocation());
			}
		}
		return best;
	}

	private static List<String> join(List<String> a, List<String> b) {
		List<String> out = new java.util.ArrayList<>(a);
		out.addAll(b);
		return out;
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
		return switch (args.length) {
			case 1 -> List.of("give", "pack", "laevatein", "sanity", "mang", "renoir", "blackgod", "dummy", "anim", "fx", "skills");
			case 2 -> switch (args[0].toLowerCase(Locale.ROOT)) {
				case "give" -> List.of("caduceus", "terminal", "laevatein", "hammer", "mimicry", "blackgod");
				case "laevatein" -> List.of("0", "1", "2", "3");
				case "mang" -> List.of("1", "2", "3", "4", "5", "6");
				case "renoir" -> List.of("0", "50", "100");
				case "dummy" -> List.of("4", "set", "info", "remove", "removeall");
				case "anim" -> join(List.of("list", "reload", "stop", "rig"), Animations.names());
				case "fx" -> join(List.of("list", "reload"), StudioEffects.names());
				case "skills" -> List.of("reload");
				default -> List.of();
			};
			case 3 -> args[0].equalsIgnoreCase("dummy") && args[1].equalsIgnoreCase("set")
					? List.of("base", "power", "coins", "types", "sanity", "damage")
					: args[0].equalsIgnoreCase("anim") && args[1].equalsIgnoreCase("rig")
							? List.of("player", "suit", "suit_slim", "steve", "steve_slim", "alex", "alex_slim") : List.of();
			default -> List.of();
		};
	}
}
