package io.github.sharket1111.indexprescript.paper;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Per-player prescript progress and [Hermes] stacks. Prescripts are shown and
 * tracked while the terminal is held in the off hand; completing one in combat
 * grants a [Hermes] stack, and at {@link Items#MAX_HERMES} the next Caduceus
 * attack unleashes Furioso-Replica. The off-hand terminal's stack size mirrors
 * [Hermes], which also restores it after a restart.
 *
 * A new prescript is revealed like the original: random letters and symbols
 * appear one at a time, then resolve into the text from left to right. The
 * prescript is the only text this plugin puts on screen. Sneak + right-click
 * with the terminal rerolls it at the cost of Weakness III for 10 seconds.
 */
final class PrescriptTracker implements Listener {
	private static final int COMBAT_TICKS = 200;
	private static final int OVERLAY_INTERVAL = 10;
	private static final int NEXT_PRESCRIPT_DELAY = 10;
	private static final int AURA_INTERVAL = 2;
	private static final int REVEAL_HOLD_TICKS = 8;
	private static final int REROLL_COOLDOWN_TICKS = 20;
	private static final int REROLL_WEAKNESS_TICKS = 200;
	private static final int REROLL_WEAKNESS_AMPLIFIER = 2;
	private static final String GLYPHS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ#$%&*@?!/<>+=";

	private static final Map<UUID, PrescriptTracker> TRACKERS = new ConcurrentHashMap<>();

	private Prescript prescript;
	private int progress;
	private int hermes;
	private long lastCombat = Long.MIN_VALUE / 2;
	private long hermesBlockedUntil;
	private long rerollableAt;
	private boolean restored;
	private String revealText;
	private long revealStart;

	static PrescriptTracker of(Player player) {
		return TRACKERS.computeIfAbsent(player.getUniqueId(), id -> new PrescriptTracker());
	}

	static boolean holdsTerminal(Player player) {
		return Items.isTerminal(player.getInventory().getItemInOffHand());
	}

	/** Full [Hermes] with the terminal in the off hand and Caduceus in the main hand. */
	static boolean ready(Player player) {
		return holdsTerminal(player) && Items.isCaduceus(player.getInventory().getItemInMainHand())
				&& of(player).hermes >= Items.MAX_HERMES;
	}

	static int hermes(Player player) {
		PrescriptTracker tracker = TRACKERS.get(player.getUniqueId());
		return tracker == null ? 0 : tracker.hermes;
	}

	static void markCombat(Player player) {
		of(player).lastCombat = CaduceusListener.now();
	}

	/** Furioso-Replica consumes every [Hermes] and blocks new ones until the given time. */
	static void consumeHermes(Player player, long blockedUntil) {
		PrescriptTracker tracker = of(player);
		tracker.hermes = 0;
		tracker.hermesBlockedUntil = Math.max(tracker.hermesBlockedUntil, blockedUntil);
	}

	static void blockHermes(Player player, long until) {
		PrescriptTracker tracker = of(player);
		tracker.hermesBlockedUntil = Math.max(tracker.hermesBlockedUntil, until);
	}

	/** Called every tick. */
	static void tick() {
		long now = CaduceusListener.now();
		Furioso.tickAll();
		for (Player player : Bukkit.getOnlinePlayers()) {
			if (now % 5 == 0) {
				CaduceusListener.revertExpired(player);
				keepLooseTerminalsSingle(player.getInventory());
			}
			if (!holdsTerminal(player)) {
				continue;
			}
			PrescriptTracker tracker = of(player);
			ItemStack terminal = player.getInventory().getItemInOffHand();
			if (!tracker.restored) {
				// A terminal stack of n means n [Hermes]; a single terminal reads as 0.
				int count = terminal.getAmount();
				tracker.hermes = Math.max(tracker.hermes, count > 1 ? Math.min(count, Items.MAX_HERMES) : 0);
				tracker.restored = true;
			}
			tracker.showHermes(terminal);
			if (tracker.prescript == null) {
				tracker.issue(player, now);
			}
			if (tracker.revealing(now)) {
				player.sendActionBar(tracker.revealFrame(now));
			} else if (now % OVERLAY_INTERVAL == 0) {
				player.sendActionBar(tracker.line(player));
			}
			if (now % AURA_INTERVAL == 0 && ready(player) && !Furioso.active(player)) {
				Effects.aura(Cinematic.body(player));
			}
		}
	}

	/** The off-hand terminal's stack size is the [Hermes] count; it glints when full. */
	private void showHermes(ItemStack terminal) {
		int count = Math.max(1, hermes);
		if (terminal.getAmount() != count) {
			terminal.setAmount(count);
		}
		boolean full = hermes >= Items.MAX_HERMES;
		if (terminal.getItemMeta().hasEnchantmentGlintOverride() != full) {
			terminal.editMeta(meta -> meta.setEnchantmentGlintOverride(full ? Boolean.TRUE : null));
		}
	}

	private static void keepLooseTerminalsSingle(PlayerInventory inventory) {
		ItemStack[] storage = inventory.getStorageContents();
		for (ItemStack stack : storage) {
			if (Items.isTerminal(stack) && stack.getAmount() > 1) {
				stack.setAmount(1);
			}
		}
	}

	Component line(Player player) {
		return Component.text(Messages.get(player, "message.prescript_line", Messages.get(player, prescript.key(), prescript.amount),
				progress, prescript.amount), NamedTextColor.AQUA);
	}

	private void issue(Player player, long startAt) {
		prescript = Prescript.random(prescript);
		progress = 0;
		revealText = Messages.get(player, prescript.key(), prescript.amount);
		revealStart = startAt;
	}

	private boolean revealing(long now) {
		return revealText != null && now >= revealStart && now < revealStart + revealText.length() * 2L + REVEAL_HOLD_TICKS;
	}

	/**
	 * Letters appear one per tick as random glyphs, hold briefly while still
	 * scrambling, then resolve into the real text one letter per tick.
	 */
	private Component revealFrame(long now) {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		int length = revealText.length();
		int elapsed = (int) (now - revealStart);
		int shown = Math.min(length, elapsed + 1);
		int resolved = Math.max(0, elapsed - length - REVEAL_HOLD_TICKS + 1);
		StringBuilder frame = new StringBuilder(length);
		for (int i = 0; i < shown; i++) {
			char c = revealText.charAt(i);
			frame.append(i < resolved || Character.isWhitespace(c) ? c : GLYPHS.charAt(random.nextInt(GLYPHS.length())));
		}
		return Component.text(frame.toString(), resolved > 0 ? NamedTextColor.AQUA : NamedTextColor.DARK_AQUA);
	}

	/** A charged Caduceus hit landed. */
	static void onHit(Player player, CaduceusForm form, boolean crit, boolean staggered) {
		markCombat(player);
		PrescriptTracker tracker = of(player);
		if (!holdsTerminal(player) || tracker.prescript == null) {
			return;
		}
		Prescript p = tracker.prescript;
		boolean advanced = switch (p.goal) {
			case HITS, CLEAN_HITS -> true;
			case STAGGER -> staggered;
			case CRITS -> crit;
			case SNEAK_HIT -> player.isSneaking();
			case AIR_HIT -> !player.isOnGround();
			case FORM_HIT -> form == p.form;
			case KILLS -> false;
		};
		if (advanced) {
			tracker.advance(player);
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onDeath(EntityDeathEvent event) {
		Player killer = event.getEntity().getKiller();
		if (killer == null || killer == event.getEntity() || !holdsTerminal(killer)) {
			return;
		}
		PrescriptTracker tracker = of(killer);
		if (tracker.prescript == null) {
			return;
		}
		if (tracker.prescript.goal == Prescript.Goal.KILLS) {
			tracker.advance(killer);
		}
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onPlayerDamaged(EntityDamageEvent event) {
		if (!(event.getEntity() instanceof Player player) || event.getFinalDamage() <= 0) {
			return;
		}
		if (event instanceof EntityDamageByEntityEvent byEntity && attacker(byEntity.getDamager()) != null) {
			markCombat(player);
		}
		PrescriptTracker tracker = of(player);
		if (tracker.prescript != null && tracker.prescript.goal == Prescript.Goal.CLEAN_HITS) {
			tracker.progress = 0;
		}
	}

	private static LivingEntity attacker(Entity damager) {
		if (damager instanceof Projectile projectile && projectile.getShooter() instanceof LivingEntity shooter) {
			return shooter;
		}
		return damager instanceof LivingEntity living ? living : null;
	}

	/** Sneak + right-click with the terminal rerolls the prescript, inflicting Weakness III for 10 seconds. */
	@EventHandler
	public void onUse(PlayerInteractEvent event) {
		if ((event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)
				|| !Items.isTerminal(event.getItem())) {
			return;
		}
		event.setCancelled(true);
		Player player = event.getPlayer();
		PrescriptTracker tracker = of(player);
		long now = CaduceusListener.now();
		if (!player.isSneaking() || tracker.prescript == null || now < tracker.rerollableAt) {
			return;
		}
		tracker.rerollableAt = now + REROLL_COOLDOWN_TICKS;
		tracker.issue(player, now);
		player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, REROLL_WEAKNESS_TICKS, REROLL_WEAKNESS_AMPLIFIER));
		player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.0F, 0.6F);
		player.playSound(player.getLocation(), Sound.ITEM_BUCKET_FILL_LAVA, SoundCategory.PLAYERS, 0.5F, 0.8F);
	}

	private void advance(Player player) {
		if (++progress < prescript.amount) {
			return;
		}
		long now = CaduceusListener.now();
		boolean inCombat = now - lastCombat <= COMBAT_TICKS;
		boolean granted = inCombat && now >= hermesBlockedUntil && hermes < Items.MAX_HERMES;
		if (granted) {
			hermes++;
		}
		player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.0F, granted ? 1.2F : 0.8F);
		Voice.prescriptComplete(player, granted ? hermes : 0);
		if (granted && hermes == Items.MAX_HERMES) {
			player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 0.5F, 0.6F);
		}
		issue(player, now + NEXT_PRESCRIPT_DELAY);
	}
}
