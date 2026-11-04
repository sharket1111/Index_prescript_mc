package io.github.sharket1111.indexprescript.paper;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * The Brand Manager's hammer (fan-made, after Limbus Company), on a mace
 * with a sword's attack speed.
 *
 * - [Promised Nail]: three full normal hits in a row on the same foe drive a
 *   glowing nail into it from above (up to 10 per foe). A smash (the mace's
 *   falling attack) deals +1 damage per nail in the foe and spends them all.
 * - [Black God's Voice]: +5 for every hit on a foe, up to 100, shown as the
 *   stack size of an icon in the off hand while the hammer is held.
 * - Right-click (or swap-hands on Java) looking at a foe, spending all Voice:
 *   at 100, "Strike Again and Again, Sever Birth" (every foe within 8 blocks
 *   of it); at 50 or more, "Sever Birth" (see {@link RenoirSkill}).
 * - [Preservation]: a count (up to 20) and a potency, shown as the Unluck
 *   effect renamed by the resource pack: its level is the potency and its
 *   time reads 10:SS with SS the count (10 minutes in front so the icon
 *   never blinks), held still while the hammer is in hand.
 *   Skills add to the count. After [Malleus Promissionis], driving a nail
 *   spends Preservation: with 12 or more it drives one more nail and spends 4,
 *   otherwise 2 (none if there is not enough; the nail still goes in). Every
 *   10 spent in all raises the potency by 1.
 */
final class RenoirHammer implements Listener {
	static final int MAX_VOICE = 100;
	/** Voice needed for Sever Birth; the strong skill needs all {@link #MAX_VOICE}. */
	static final int SEVER_VOICE = 50;
	/** A stack holds at most 99, so 100 shows as 99 (with the glint and the value in the name). */
	static final int ICON_MAX_STACK = 99;
	static final int VOICE_PER_HIT = 5;
	static final int MAX_PRESERVATION = 20;
	static final int MAX_POTENCY = 10;
	private static final int HITS_PER_NAIL = 3;
	private static final int STREAK_TICKS = 60;
	private static final int TARGET_RANGE = 8;
	private static final int ICON_INTERVAL = 2;
	private static final int STATUS_INTERVAL = 5;
	private static final int DISPLAY_OFFSET_SECONDS = 600;
	private static final NamespacedKey VOICE = new NamespacedKey(Items.NAMESPACE, "renoir_voice");
	private static final NamespacedKey PRESERVATION = new NamespacedKey(Items.NAMESPACE, "renoir_preservation");
	private static final NamespacedKey POTENCY = new NamespacedKey(Items.NAMESPACE, "renoir_preservation_potency");
	private static final NamespacedKey SPENT = new NamespacedKey(Items.NAMESPACE, "renoir_preservation_spent");

	/** Damage a smash just earned from the nails it spent, added when its damage comes. */
	private static final Map<UUID, Integer> SMASH_BONUS = new ConcurrentHashMap<>();
	private static final Map<UUID, Streak> STREAKS = new ConcurrentHashMap<>();
	private static long lastError = Long.MIN_VALUE / 2;

	private record Streak(UUID foe, int hits, long at) {
	}

	// --- state ---------------------------------------------------------------

	private static int read(Player player, NamespacedKey key, int fallback) {
		return player.getPersistentDataContainer().getOrDefault(key, PersistentDataType.INTEGER, fallback);
	}

	private static void write(Player player, NamespacedKey key, int value) {
		player.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, value);
	}

	static int voice(Player player) {
		return read(player, VOICE, 0);
	}

	static void setVoice(Player player, int value) {
		write(player, VOICE, Math.max(0, Math.min(MAX_VOICE, value)));
	}

	static int preservation(Player player) {
		return read(player, PRESERVATION, 0);
	}

	static int potency(Player player) {
		return Math.max(1, read(player, POTENCY, 1));
	}

	static void addPreservation(Player player, int amount) {
		int before = preservation(player);
		int after = Math.max(0, Math.min(BlackGodState.preservationCap(player), before + amount));
		write(player, PRESERVATION, after);
		BlackGodState.preservationChanged(player, after - before);
	}

	static void setPotency(Player player, int value) {
		write(player, POTENCY, Math.max(1, value));
	}

	/** The potency's cap: 10, or 20 for the Black God (the excess becomes System Preservation at a turn's end). */
	private static int potencyCap(Player player) {
		return BlackGod.active(player) ? MAX_POTENCY + 10 : MAX_POTENCY;
	}

	/** Spends Preservation; every 10 spent in all raises the potency by 1. */
	static void spendPreservation(Player player, int amount) {
		int spent = Math.min(amount, preservation(player));
		if (spent <= 0) {
			return;
		}
		write(player, PRESERVATION, preservation(player) - spent);
		BlackGodState.preservationChanged(player, -spent);
		int total = read(player, SPENT, 0) + spent;
		int potency = potency(player);
		while (total >= 10 && potency < potencyCap(player)) {
			total -= 10;
			potency++;
			RenoirSkill.potencyUp(Cinematic.body(player));
		}
		write(player, SPENT, total);
		write(player, POTENCY, potency);
	}

	/**
	 * Drives a nail into {@code foe}, after [Malleus Promissionis]: with 12+
	 * Preservation one more nail goes in and 4 are spent, otherwise 2 are
	 * spent (if there are 2).
	 */
	static void driveNail(Player player, LivingEntity foe) {
		int count = preservation(player);
		int nails = 1;
		if (count >= 12) {
			nails = 2;
			spendPreservation(player, 4);
		} else if (count >= 2) {
			spendPreservation(player, 2);
		}
		PromisedNails.drop(foe, nails);
	}

	static boolean holding(Player player) {
		return Items.isRenoirHammer(player.getInventory().getItemInMainHand());
	}

	// --- every tick ----------------------------------------------------------

	static void tick() {
		RenoirSkill.tickAll();
		PromisedNails.tick();
		long now = CaduceusListener.now();
		for (Player player : Bukkit.getOnlinePlayers()) {
			try {
				if (now % ICON_INTERVAL == 0) {
					showVoice(player);
				}
				if (now % STATUS_INTERVAL == 0) {
					showPreservation(player);
				}
				if (now % 20 == 0) {
					// Hammers made before they had a sword's attack speed get it in hand.
					ItemStack held = player.getInventory().getItemInMainHand();
					if (Items.isRenoirHammer(held) && Items.hammerStats(held)) {
						player.getInventory().setItemInMainHand(held);
					}
				}
			} catch (RuntimeException e) {
				if (now - lastError > 200) {
					lastError = now;
					IndexPrescriptPlugin.instance().getLogger().log(Level.WARNING, "Renoir hammer tick failed for " + player.getName(), e);
				}
			}
		}
	}

	/**
	 * The [Black God's Voice] icon in the off hand while the hammer is held,
	 * its stack size the value; it glints at 50. Whatever was in the off hand
	 * moves to a free slot (no icon if there is none). Nothing shows at 0.
	 */
	private static void showVoice(Player player) {
		PlayerInventory inventory = player.getInventory();
		ItemStack off = inventory.getItemInOffHand();
		int voice = voice(player);
		if (!holding(player) || voice <= 0) {
			if (Items.isVoiceIcon(off)) {
				inventory.setItemInOffHand(null);
			}
			removeStrayIcons(inventory);
			return;
		}
		if (!Items.isVoiceIcon(off)) {
			if (!off.isEmpty()) {
				int free = inventory.firstEmpty();
				if (free < 0) {
					return;
				}
				inventory.setItem(free, off);
			}
			off = Items.voiceIcon(player);
		}
		boolean full = voice >= MAX_VOICE;
		ItemStack shown = off.clone();
		shown.setAmount(Math.min(ICON_MAX_STACK, voice));
		Component name = Component.text(Messages.get(player, "item.black_god_voice") + " " + voice + "/" + MAX_VOICE,
				voice >= SEVER_VOICE ? NamedTextColor.GOLD : NamedTextColor.WHITE);
		shown.editMeta(meta -> {
			meta.setEnchantmentGlintOverride(full ? Boolean.TRUE : null);
			meta.itemName(name);
		});
		if (!shown.equals(inventory.getItemInOffHand())) {
			inventory.setItemInOffHand(shown);
		}
	}

	private static void removeStrayIcons(PlayerInventory inventory) {
		ItemStack[] storage = inventory.getStorageContents();
		for (int slot = 0; slot < storage.length; slot++) {
			if (Items.isVoiceIcon(storage[slot])) {
				inventory.setItem(slot, null);
			}
		}
	}

	/**
	 * [Preservation] as the renamed Unluck effect while the hammer is held:
	 * level = potency, time = count seconds, kept from running down.
	 */
	private static void showPreservation(Player player) {
		PotionEffect shown = player.getPotionEffect(PotionEffectType.UNLUCK);
		int count = preservation(player);
		if (!(holding(player) || BlackGod.active(player)) || count <= 0) {
			if (shown != null && !shown.hasParticles() && !shown.isAmbient()) {
				player.removePotionEffect(PotionEffectType.UNLUCK);
			}
			return;
		}
		// The client blinks an effect icon in its last 10 seconds, so 10 minutes go in front:
		// the time reads 10:SS, where SS is the count.
		// Plus most of a second, so the seconds shown never slip below the count between refreshes.
		int ticks = (DISPLAY_OFFSET_SECONDS + count) * 20 + 15;
		int amplifier = potency(player) - 1;
		if (shown != null && shown.getAmplifier() == amplifier && Math.abs(shown.getDuration() - ticks) <= STATUS_INTERVAL) {
			return;
		}
		player.addPotionEffect(new PotionEffect(PotionEffectType.UNLUCK, ticks, amplifier, false, false, true));
	}

	// --- hits ----------------------------------------------------------------

	/** No attacking mid-skill. */
	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	public void onPreAttack(PrePlayerAttackEntityEvent event) {
		if (holding(event.getPlayer()) && RenoirSkill.active(event.getPlayer())) {
			event.setCancelled(true);
		}
	}

	/** A smash: the mace's falling attack (falling more than 1.5 blocks, not gliding). */
	private static boolean smashing(Player player) {
		return player.getFallDistance() > 1.5F && !player.isGliding() && !player.isFlying();
	}

	/**
	 * Every attack on a foe, read when it is made rather than from the damage
	 * (training dummies are often invulnerable or cancel the damage, so it
	 * may never come): a smash spends the foe's nails (their damage is added
	 * below); a full normal hit builds toward the next nail; full swings give Voice.
	 */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onAttack(PrePlayerAttackEntityEvent event) {
		Player player = event.getPlayer();
		if (!holding(player) || !(event.getAttacked() instanceof LivingEntity foe) || !foe(player, foe)) {
			return;
		}
		boolean full = Swings.accept(player);
		if (smashing(player)) {
			int nails = PromisedNails.spend(foe);
			if (nails > 0) {
				SMASH_BONUS.put(player.getUniqueId(), nails);
				RenoirSkill.nailSmash(foe, nails);
			}
			STREAKS.remove(player.getUniqueId());
		} else if (full) {
			long now = CaduceusListener.now();
			Streak streak = STREAKS.get(player.getUniqueId());
			int hits = streak != null && streak.foe().equals(foe.getUniqueId()) && now - streak.at() <= STREAK_TICKS ? streak.hits() + 1 : 1;
			if (hits >= HITS_PER_NAIL) {
				STREAKS.remove(player.getUniqueId());
				if (PromisedNails.count(foe) < PromisedNails.MAX) {
					driveNail(player, foe);
				}
			} else {
				STREAKS.put(player.getUniqueId(), new Streak(foe.getUniqueId(), hits, now));
				foe.getWorld().spawnParticle(org.bukkit.Particle.DUST, foe.getLocation().add(0, foe.getHeight() + 0.3, 0),
						3 * hits, 0.2, 0.1, 0.2, 0.0, PromisedNails.GOLD);
			}
		}
		if (full || smashing(player)) {
			int before = voice(player);
			setVoice(player, before + VOICE_PER_HIT);
			if (before < MAX_VOICE && voice(player) >= MAX_VOICE) {
				RenoirSkill.voiceFull(player);
			}
		}
	}

	/** The smash's damage: +1 per nail it spent. */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onHit(EntityDamageByEntityEvent event) {
		if (Damage.dealing() || !(event.getDamager() instanceof Player player)) {
			return;
		}
		Integer nails = SMASH_BONUS.remove(player.getUniqueId());
		if (nails != null && event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
			event.setDamage(event.getDamage() + nails);
		}
	}

	// --- skills --------------------------------------------------------------

	/** Something the skills may strike: alive, not the wielder or a stand-in, not a spectator. */
	static boolean foe(Player player, Entity entity) {
		if (!(entity instanceof LivingEntity living) || entity == player || Cinematic.isActor(entity)
				|| entity instanceof ArmorStand stand && stand.isMarker() || !living.isValid() || living.isDead()) {
			return false;
		}
		// Training dummies count (armour stands, mannequins, invulnerable mobs); spectators and creative players do not.
		return !(living instanceof Player other)
				|| other.getGameMode() != GameMode.CREATIVE && other.getGameMode() != GameMode.SPECTATOR;
	}

	/** The foe the player is looking at, within reach. */
	private static LivingEntity lookedAt(Player player) {
		if (player.getTargetEntity(TARGET_RANGE) instanceof LivingEntity living && foe(player, living)) {
			return living;
		}
		Vector eye = player.getEyeLocation().toVector();
		Vector look = player.getEyeLocation().getDirection();
		LivingEntity best = null;
		double bestDot = 0.9;
		for (Entity entity : player.getNearbyEntities(TARGET_RANGE, TARGET_RANGE, TARGET_RANGE)) {
			if (!foe(player, entity)) {
				continue;
			}
			LivingEntity living = (LivingEntity) entity;
			Vector to = living.getLocation().add(0, living.getHeight() * 0.5, 0).toVector().subtract(eye);
			if (to.length() > TARGET_RANGE) {
				continue;
			}
			double dot = to.normalize().dot(look);
			if (dot > bestDot && player.hasLineOfSight(living)) {
				best = living;
				bestDot = dot;
			}
		}
		return best;
	}

	private static void act(Player player) {
		if (!holding(player) || RenoirSkill.active(player)) {
			return;
		}
		LivingEntity target = lookedAt(player);
		if (target == null) {
			player.sendActionBar(Component.text(Messages.get(player, "renoir.no_target"), NamedTextColor.GRAY));
			return;
		}
		int voice = voice(player);
		if (voice < SEVER_VOICE) {
			player.sendActionBar(Component.text(Messages.get(player, "renoir.need_voice", voice), NamedTextColor.GRAY));
			return;
		}
		// Either skill spends all of the Voice.
		setVoice(player, 0);
		RenoirSkill.start(player, target, voice >= MAX_VOICE ? RenoirSkill.Kind.AGAIN : RenoirSkill.Kind.SEVER);
	}

	@EventHandler
	public void onUse(PlayerInteractEvent event) {
		if (event.getHand() != EquipmentSlot.HAND
				|| event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK
				|| !Items.isRenoirHammer(event.getItem())) {
			return;
		}
		act(event.getPlayer());
	}

	/** Swap-hands (F) never moves the hammer or the icon; it uses a skill instead. */
	@EventHandler(ignoreCancelled = true)
	public void onSwap(PlayerSwapHandItemsEvent event) {
		PlayerInventory inventory = event.getPlayer().getInventory();
		if (!Items.isRenoirHammer(inventory.getItemInMainHand()) && !Items.isVoiceIcon(inventory.getItemInOffHand())) {
			return;
		}
		event.setCancelled(true);
		act(event.getPlayer());
	}

	// --- the icon stays put ----------------------------------------------------

	@EventHandler(ignoreCancelled = true)
	public void onDrop(PlayerDropItemEvent event) {
		if (Items.isVoiceIcon(event.getItemDrop().getItemStack())) {
			event.setCancelled(true);
		}
	}

	@EventHandler(ignoreCancelled = true)
	public void onClick(InventoryClickEvent event) {
		boolean offhandIcon = event.getClick() == ClickType.SWAP_OFFHAND
				&& Items.isVoiceIcon(event.getWhoClicked().getInventory().getItemInOffHand());
		if (offhandIcon || Items.isVoiceIcon(event.getCurrentItem()) || Items.isVoiceIcon(event.getCursor())) {
			event.setCancelled(true);
		}
	}

	@EventHandler(ignoreCancelled = true)
	public void onDrag(InventoryDragEvent event) {
		if (Items.isVoiceIcon(event.getOldCursor())) {
			event.setCancelled(true);
		}
	}

	@EventHandler
	public void onDeath(PlayerDeathEvent event) {
		event.getDrops().removeIf(Items::isVoiceIcon);
	}

	/** Testing: sets the player's Voice and Preservation. */
	static void set(Player player, int voice, int preservation) {
		setVoice(player, voice);
		PersistentDataContainer data = player.getPersistentDataContainer();
		data.set(PRESERVATION, PersistentDataType.INTEGER, Math.max(0, Math.min(BlackGodState.preservationCap(player), preservation)));
	}
}
