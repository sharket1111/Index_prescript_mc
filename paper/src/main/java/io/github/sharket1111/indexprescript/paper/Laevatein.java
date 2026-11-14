package io.github.sharket1111.indexprescript.paper;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import io.papermc.paper.entity.TeleportFlag;

/**
 * Matthias's Laevatein. The original releases it by spending stacks with
 * certain skills; here each stage unlocks after a number of hits dealt or
 * taken (5, 7, then 9), shown as the stack size of a [Seal Release] icon held
 * in the off hand while Laevatein is wielded. Right-click (or swap-hands on
 * Java) with full hits and an enemy nearby releases one stage in a strike
 * sequence modelled on the in-game animation; with fewer hits it uses the
 * stage's skill instead (see {@link LaevateinSkill}). Sneak + right-click
 * enters a counter stance.
 */
final class Laevatein implements Listener {
	private static final int[] HITS_TO_RELEASE = {5, 7, 9};
	/** Burn on every hit once released: 1, 4, then 9 seconds by stage. */
	private static final int[] BURN_TICKS = {0, 20, 80, 180};
	/** At full release hits keep counting (for Stab and Summary Execution) up to this cap. */
	private static final int MAX_HITS = 9;
	/** Hits needed for each stage's skill, and the skill itself. */
	private static final int[] SKILL_HITS = {1, 3, 5, 7};
	private static final LaevateinSkill.Kind[] SKILLS = {
		LaevateinSkill.Kind.STOMP, LaevateinSkill.Kind.HEATED, LaevateinSkill.Kind.MELT, LaevateinSkill.Kind.STAB};
	private static final int EXECUTION_HITS = 8;
	private static final int STANCE_TICKS = 40;
	private static final int STANCE_COOLDOWN_TICKS = 40;
	private static final PotionEffectType[] AILMENTS = {
		PotionEffectType.SLOWNESS, PotionEffectType.WEAKNESS, PotionEffectType.POISON, PotionEffectType.WITHER,
		PotionEffectType.BLINDNESS, PotionEffectType.DARKNESS, PotionEffectType.NAUSEA, PotionEffectType.HUNGER,
		PotionEffectType.MINING_FATIGUE, PotionEffectType.LEVITATION};
	private static final Map<UUID, Long> STANCE_UNTIL = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> STANCE_READY_AT = new ConcurrentHashMap<>();
	private static final int MAX_STAGE = Items.LAEVATEIN_STAGES.size() - 1;
	private static final int TARGET_RANGE = 6;
	private static final int READY_EFFECT_INTERVAL = 4;
	private static final int CLEANUP_INTERVAL = 5;
	private static final int ERROR_LOG_INTERVAL = 200;
	private static final NamespacedKey HITS = new NamespacedKey(Items.NAMESPACE, "laevatein_hits");

	private static final Map<UUID, Boolean> CHARGED = new ConcurrentHashMap<>();
	private static final Map<UUID, Release> RELEASING = new ConcurrentHashMap<>();
	private static long lastError = Long.MIN_VALUE / 2;

	static int hits(ItemStack stack) {
		return stack.getItemMeta().getPersistentDataContainer().getOrDefault(HITS, PersistentDataType.INTEGER, 0);
	}

	static void setHits(ItemStack stack, int hits) {
		stack.editMeta(meta -> meta.getPersistentDataContainer().set(HITS, PersistentDataType.INTEGER, hits));
	}

	/** Whether the player's current swing started at (near) full attack charge. */
	static boolean charged(Player player) {
		return Boolean.TRUE.equals(CHARGED.get(player.getUniqueId()));
	}

	/** Enough hits for the next stage. */
	static boolean ready(ItemStack stack) {
		int stage = Items.laevateinStage(stack);
		return stage < MAX_STAGE && hits(stack) >= HITS_TO_RELEASE[stage];
	}

	/** Called every tick: the off-hand icon and the ready embers. */
	static void tick() {
		LaevateinSkill.tickAll();
		RELEASING.values().removeIf(release -> {
			try {
				return !release.tick();
			} catch (RuntimeException e) {
				IndexPrescriptPlugin.instance().getLogger().log(Level.WARNING, "Laevatein release failed", e);
				return true;
			}
		});
		long now = CaduceusListener.now();
		for (Player player : Bukkit.getOnlinePlayers()) {
			try {
				tick(player, now);
			} catch (RuntimeException e) {
				if (now - lastError > ERROR_LOG_INTERVAL) {
					lastError = now;
					IndexPrescriptPlugin.instance().getLogger().log(Level.WARNING, "Laevatein tick failed for " + player.getName(), e);
				}
			}
		}
	}

	/** Out of combat this long (neither hitting nor hit) the blade seals itself again. */
	private static final int RESEAL_TICKS = 60 * 20;
	private static final Map<UUID, Long> LAST_COMBAT = new ConcurrentHashMap<>();

	static void markCombat(Player player) {
		LAST_COMBAT.put(player.getUniqueId(), CaduceusListener.now());
	}

	/**
	 * After a minute out of combat every released Laevatein the player carries
	 * goes back to stage 0 (sealed in its chains) and its hits start over.
	 */
	private static void resealIfIdle(Player player, PlayerInventory inventory, long now) {
		if (RELEASING.containsKey(player.getUniqueId()) || LaevateinSkill.active(player)) {
			markCombat(player);
			return;
		}
		long last = LAST_COMBAT.computeIfAbsent(player.getUniqueId(), id -> now);
		if (now - last < RESEAL_TICKS) {
			return;
		}
		boolean sealed = false;
		for (ItemStack stack : inventory.getContents()) {
			if (Items.isLaevatein(stack) && (Items.laevateinStage(stack) > 0 || hits(stack) > 0)) {
				Items.setLaevateinStage(stack, 0);
				setHits(stack, 0);
				sealed = true;
			}
		}
		if (sealed) {
			LaevateinEffects.reseal(Cinematic.body(player));
		}
		LAST_COMBAT.put(player.getUniqueId(), now);
	}

	private static void tick(Player player, long now) {
		PlayerInventory inventory = player.getInventory();
		if (now % 20 == 0) {
			resealIfIdle(player, inventory, now);
		}
		ItemStack held = inventory.getItemInMainHand();
		if (Items.isLaevatein(held)) {
			if (held.getAmount() > 1) {
				held.setAmount(1); // left over from the old stack-size display
			}
			showIcon(player, inventory, held);
			if (!RELEASING.containsKey(player.getUniqueId()) && ready(held) && now % READY_EFFECT_INTERVAL == 0) {
				LaevateinEffects.ready(Cinematic.body(player));
			}
		} else if (Items.isLaevateinIcon(inventory.getItemInOffHand())) {
			inventory.setItemInOffHand(null);
		}
		if (now % CLEANUP_INTERVAL == 0) {
			removeStrayIcons(inventory);
		}
	}

	/**
	 * Keeps the [Seal Release] icon in the off hand while Laevatein is held; its
	 * stack size is the hit count and it glints once a release is ready. There is
	 * no icon before the first hit. Whatever was in the off
	 * hand moves to a free inventory slot (no icon if there is none).
	 */
	private static void showIcon(Player player, PlayerInventory inventory, ItemStack held) {
		ItemStack off = inventory.getItemInOffHand();
		if (hits(held) <= 0) {
			if (Items.isLaevateinIcon(off)) {
				inventory.setItemInOffHand(null);
			}
			return;
		}
		if (!Items.isLaevateinIcon(off)) {
			if (!off.isEmpty()) {
				int free = inventory.firstEmpty();
				if (free < 0) {
					return;
				}
				inventory.setItem(free, off);
			}
			off = Items.laevateinIcon(player);
		}
		int count = iconCount(held);
		boolean glint = ready(held);
		off.setAmount(count);
		ItemStack shown = off;
		if (shown.getItemMeta().hasEnchantmentGlintOverride() != glint) {
			shown.editMeta(meta -> meta.setEnchantmentGlintOverride(glint ? Boolean.TRUE : null));
		}
		if (!shown.equals(inventory.getItemInOffHand())) {
			inventory.setItemInOffHand(shown);
		}
	}

	static int iconCount(ItemStack held) {
		return Math.max(1, hits(held));
	}

	/** The icon only ever lives in the off hand. */
	private static void removeStrayIcons(PlayerInventory inventory) {
		ItemStack[] storage = inventory.getStorageContents();
		for (int slot = 0; slot < storage.length; slot++) {
			if (Items.isLaevateinIcon(storage[slot])) {
				inventory.setItem(slot, null);
			}
		}
	}

	private static void startRelease(Player player, LivingEntity target) {
		int stage = Items.laevateinStage(player.getInventory().getItemInMainHand()) + 1;
		SkillCard card = stage == 1 ? SkillCard.RELEASE_1 : stage == 2 ? SkillCard.RELEASE_2 : SkillCard.RELEASE_3;
		Clash.request(player, target, card, outcome -> {
			if (busy(player) || !Items.isLaevatein(player.getInventory().getItemInMainHand())) {
				return;
			}
			Release release = new Release(player, outcome.target(), stage);
			RELEASING.put(player.getUniqueId(), release);
			release.start();
		});
	}

	@EventHandler(ignoreCancelled = true)
	public void onPreAttack(PrePlayerAttackEntityEvent event) {
		Player player = event.getPlayer();
		if (!Items.isLaevatein(player.getInventory().getItemInMainHand())) {
			return;
		}
		if (busy(player)) {
			event.setCancelled(true);
			return;
		}
		CHARGED.put(player.getUniqueId(), Swings.accept(player));
	}

	/** Full swings that land count toward the next release. */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onHit(EntityDamageByEntityEvent event) {
		if (event.getDamager() instanceof Player attacker && Items.isLaevatein(attacker.getInventory().getItemInMainHand())) {
			markCombat(attacker);
		}
		if (Damage.dealing() || event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK
				|| !(event.getDamager() instanceof Player player) || !(event.getEntity() instanceof LivingEntity)) {
			return;
		}
		ItemStack held = player.getInventory().getItemInMainHand();
		int stage = Items.isLaevatein(held) ? Items.laevateinStage(held) : 0;
		if (stage > 0) {
			LivingEntity target = (LivingEntity) event.getEntity();
			target.setFireTicks(Math.max(target.getFireTicks(), BURN_TICKS[stage]));
		}
		if (Boolean.TRUE.equals(CHARGED.remove(player.getUniqueId()))) {
			addHit(player);
		}
	}

	/** Being hit by any mob or player while wielding Laevatein counts too. */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onHurt(EntityDamageByEntityEvent event) {
		if (!(event.getEntity() instanceof Player player) || event.getFinalDamage() <= 0) {
			return;
		}
		LivingEntity attacker = attacker(event.getDamager());
		if (attacker == null || attacker == player) {
			return;
		}
		markCombat(player);
		if (!counter(player, attacker)) {
			addHit(player);
		}
	}

	/**
	 * Struck while in the counter stance: every ailment is cleared and Rule
	 * Violation answers, or Summary Execution at full release with 8+ hits
	 * (spending them). Returns true if a counter fired.
	 */
	private static boolean counter(Player player, LivingEntity attacker) {
		Long until = STANCE_UNTIL.get(player.getUniqueId());
		ItemStack held = player.getInventory().getItemInMainHand();
		if (until == null || CaduceusListener.now() >= until || !Items.isLaevatein(held) || busy(player) || attacker.isDead()) {
			return false;
		}
		STANCE_UNTIL.remove(player.getUniqueId());
		for (PotionEffectType ailment : AILMENTS) {
			player.removePotionEffect(ailment);
		}
		player.setFireTicks(0);
		int stage = Items.laevateinStage(held);
		LaevateinSkill.Kind kind = LaevateinSkill.Kind.RULE_VIOLATION;
		if (stage >= MAX_STAGE && hits(held) >= EXECUTION_HITS) {
			setHits(held, 0);
			kind = LaevateinSkill.Kind.EXECUTION;
		}
		LaevateinSkill.start(player, attacker, kind, stage);
		return true;
	}

	/** Sneak + right-click: counter stance, Slowness V and Weakness V for 2 seconds. */
	private static void enterStance(Player player) {
		long now = CaduceusListener.now();
		if (busy(player) || now < STANCE_READY_AT.getOrDefault(player.getUniqueId(), 0L)) {
			return;
		}
		STANCE_UNTIL.put(player.getUniqueId(), now + STANCE_TICKS);
		STANCE_READY_AT.put(player.getUniqueId(), now + STANCE_TICKS + STANCE_COOLDOWN_TICKS);
		player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, STANCE_TICKS, 4));
		player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, STANCE_TICKS, 4));
		LaevateinEffects.stance(player);
	}

	static boolean busy(Player player) {
		return RELEASING.containsKey(player.getUniqueId()) || LaevateinSkill.active(player);
	}

	private static LivingEntity attacker(Entity damager) {
		if (damager instanceof Projectile projectile && projectile.getShooter() instanceof LivingEntity shooter) {
			return shooter;
		}
		return damager instanceof LivingEntity living ? living : null;
	}

	private static void addHit(Player player) {
		ItemStack held = player.getInventory().getItemInMainHand();
		if (!Items.isLaevatein(held) || busy(player)) {
			return;
		}
		int stage = Items.laevateinStage(held);
		int cap = stage < MAX_STAGE ? HITS_TO_RELEASE[stage] : MAX_HITS;
		if (hits(held) >= cap) {
			return;
		}
		int hits = hits(held) + 1;
		setHits(held, hits);
		if (stage < MAX_STAGE && hits == cap) {
			LaevateinEffects.readySound(Cinematic.body(player));
		}
	}

	/**
	 * Swap-hands or right-click: with full hits for the next stage this
	 * releases it; otherwise, with enough hits, it uses the stage's skill and
	 * spends every hit. Both need an enemy within reach.
	 */
	/** The stage's skill ({@code release} false) or the next release (true). */
	private static void act(Player player, boolean release) {
		ItemStack held = player.getInventory().getItemInMainHand();
		if (!Items.isLaevatein(held) || busy(player) || Clash.blocked(player)) {
			return;
		}
		int stage = Items.laevateinStage(held);
		if (release ? !ready(held) : hits(held) < SKILL_HITS[stage]) {
			return;
		}
		// Answering a skill aimed at us: the clash is with its user.
		LivingEntity target = Clash.answering(player) != null ? Clash.answering(player) : findTarget(player);
		if (target == null) {
			Hud.message(player, Component.text(Messages.get(player, "renoir.no_target"), NamedTextColor.GRAY));
			return;
		}
		if (release) {
			startRelease(player, target);
		} else {
			setHits(held, 0);
			LaevateinSkill.start(player, target, SKILLS[stage], stage);
		}
	}

	private static SkillCard card(LaevateinSkill.Kind kind) {
		return switch (kind) {
			case STOMP -> SkillCard.STOMP;
			case HEATED -> SkillCard.HEATED;
			case MELT -> SkillCard.MELT;
			default -> SkillCard.STAB;
		};
	}

	private static java.util.List<net.kyori.adventure.text.Component> cardLore(SkillCard card) {
		java.util.List<net.kyori.adventure.text.Component> lore = new java.util.ArrayList<>();
		lore.add(SkillMenu.line("위력 " + card.base() + " · 코인 +" + card.coinPower() + " ×" + card.coins(), NamedTextColor.GOLD));
		for (String l : MdGui.wrap(card.description(), 34)) {
			lore.add(SkillMenu.line(l, NamedTextColor.GRAY));
		}
		return lore;
	}

	/** The skill menu: the stage's skill, the next release, the counter stance. */
	private static java.util.List<SkillMenu.Entry> entries(Player player) {
		java.util.List<SkillMenu.Entry> out = new java.util.ArrayList<>();
		ItemStack held = player.getInventory().getItemInMainHand();
		if (!Items.isLaevatein(held)) {
			return out;
		}
		int stage = Items.laevateinStage(held);
		int hits = hits(held);
		SkillCard skill = card(SKILLS[stage]);
		if (hits >= SKILL_HITS[stage]) {
			out.add(new SkillMenu.Entry(SkillMenu.icon("minecraft:blaze_powder", 1, SkillMenu.line("1. " + skill.name(), skill.colour()),
					cardLore(skill)), () -> act(player, false)));
		} else {
			out.add(new SkillMenu.Entry(SkillMenu.locked("1. " + skill.name(), "적중 " + hits + "/" + SKILL_HITS[stage] + " (적중이 더 필요해요)",
					cardLore(skill)), null));
		}
		if (stage < MAX_STAGE) {
			SkillCard release = stage == 0 ? SkillCard.RELEASE_1 : stage == 1 ? SkillCard.RELEASE_2 : SkillCard.RELEASE_3;
			String name = "2. 해방: " + release.name();
			out.add(ready(held)
					? new SkillMenu.Entry(SkillMenu.icon("minecraft:fire_charge", 1, SkillMenu.line(name, release.colour()), cardLore(release)),
							() -> act(player, true))
					: new SkillMenu.Entry(SkillMenu.locked(name, "적중 " + hits + "/" + HITS_TO_RELEASE[stage], cardLore(release)), null));
		} else {
			out.add(new SkillMenu.Entry(SkillMenu.locked("2. 해방", "이미 완전히 해방됐어요", java.util.List.of()), null));
		}
		long now = CaduceusListener.now();
		long ready = STANCE_READY_AT.getOrDefault(player.getUniqueId(), 0L);
		java.util.List<net.kyori.adventure.text.Component> stance = cardLore(SkillCard.RULE_VIOLATION);
		stance.add(0, SkillMenu.line("2초 동안 반격 태세: 그동안 맞으면 반격해요", NamedTextColor.YELLOW));
		out.add(now >= ready
				? new SkillMenu.Entry(SkillMenu.icon("minecraft:shield", 1, SkillMenu.line("3. 반격 태세", NamedTextColor.YELLOW), stance),
						() -> enterStance(player))
				: new SkillMenu.Entry(SkillMenu.icon("minecraft:gray_dye", (int) ((ready - now + 19) / 20), SkillMenu.line("3. 반격 태세 (대기)",
						NamedTextColor.GRAY), stance), null));
		return out;
	}

	static void openMenu(Player player) {
		if (busy(player)) {
			return;
		}
		if (Clash.blocked(player)) {
			Hud.message(player, Component.text("합이 진행 중입니다.", NamedTextColor.GRAY));
			return;
		}
		SkillMenu.open(player, "weapon", () -> entries(player), Component.text("레바테인: 1 스킬 · 2 해방 · 3 반격 태세 · 9 취소", NamedTextColor.GOLD));
	}

	/** Right-click opens the skill menu (the stage's skill, the release, the counter stance). */
	@EventHandler
	public void onUse(PlayerInteractEvent event) {
		if (event.getHand() != EquipmentSlot.HAND
				|| (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)
				|| !Items.isLaevatein(event.getItem())) {
			return;
		}
		event.setCancelled(true);
		openMenu(event.getPlayer());
	}

	/** Right-clicking a creature with Laevatein opens the menu too. */
	@EventHandler
	public void onUseOn(org.bukkit.event.player.PlayerInteractEntityEvent event) {
		if (event.getHand() == EquipmentSlot.HAND && Items.isLaevatein(event.getPlayer().getInventory().getItemInMainHand())) {
			event.setCancelled(true);
			openMenu(event.getPlayer());
		}
	}

	/** Swap-hands (F) never moves the off-hand icon (F with a weapon is the E.G.O: EgoMenu). */
	@EventHandler(ignoreCancelled = true)
	public void onSwap(PlayerSwapHandItemsEvent event) {
		if (Items.isLaevateinIcon(event.getPlayer().getInventory().getItemInOffHand())) {
			event.setCancelled(true);
		}
	}

	@EventHandler(ignoreCancelled = true)
	public void onDrop(PlayerDropItemEvent event) {
		if (Items.isLaevateinIcon(event.getItemDrop().getItemStack())) {
			event.setCancelled(true);
		}
	}

	@EventHandler(ignoreCancelled = true)
	public void onClick(InventoryClickEvent event) {
		boolean offhandIcon = event.getClick() == ClickType.SWAP_OFFHAND
				&& Items.isLaevateinIcon(event.getWhoClicked().getInventory().getItemInOffHand());
		if (offhandIcon || Items.isLaevateinIcon(event.getCurrentItem()) || Items.isLaevateinIcon(event.getCursor())) {
			event.setCancelled(true);
		}
	}

	@EventHandler(ignoreCancelled = true)
	public void onDrag(InventoryDragEvent event) {
		if (Items.isLaevateinIcon(event.getOldCursor())) {
			event.setCancelled(true);
		}
	}

	@EventHandler
	public void onDeath(PlayerDeathEvent event) {
		event.getDrops().removeIf(Items::isLaevateinIcon);
	}

	private static LivingEntity findTarget(Player player) {
		if (player.getTargetEntity(TARGET_RANGE) instanceof LivingEntity living && !living.isDead()) {
			return living;
		}
		Vector look = player.getEyeLocation().getDirection();
		LivingEntity best = null;
		double bestDistance = Double.MAX_VALUE;
		for (Entity entity : player.getNearbyEntities(TARGET_RANGE, TARGET_RANGE, TARGET_RANGE)) {
			if (!(entity instanceof LivingEntity living) || living.isDead()) {
				continue;
			}
			Vector to = living.getLocation().toVector().subtract(player.getLocation().toVector());
			double distance = to.length();
			if (distance > TARGET_RANGE) {
				continue;
			}
			// Enemies in front count as half as far, so they win over ones behind.
			double score = to.normalize().dot(look) > 0.6 ? distance * 0.5 : distance;
			if (score < bestDistance) {
				best = living;
				bestDistance = score;
			}
		}
		return best;
	}

	/**
	 * One release sequence. The blade changes to the new stage at the moment
	 * the casing breaks or the fire bursts; the target is held in place.
	 */
	private static final class Release {
		private final Player player;
		private final LivingEntity target;
		private final int stage;
		private final Location anchor;
		private final boolean hadAi;
		private int tick;
		/** The coins struck so far (see Clash.coin). */
		private int coinIndex;
		private Cinematic camera;
		private double cameraSide = Double.NaN;

		Release(Player player, LivingEntity target, int stage) {
			this.player = player;
			this.target = target;
			this.stage = stage;
			this.anchor = target.getLocation();
			this.hadAi = target.hasAI();
		}

		void start() {
			Animations.playSkill(player, "skill_laevatein_release_" + stage, this::actor);
			if (!(target instanceof Player)) {
				target.setAI(false);
			}
			camera = Cinematic.start(player, player.getLocation().add(0, 2, 0));
			TotalDamage.start(player);
			SkillOverlay.show(player, SkillOverlay.Size.BIG);
			LaevateinEffects.aura(actor(), 6);
			LaevateinEffects.auraSound(actor());
		}

		private int releaseTick() {
			// From the in-game scenes: the casing breaks at 2.95 s, the fire bursts at 6.6 s and 6.75 s.
			return switch (stage) {
				case 1 -> 59;
				case 2 -> 132;
				default -> 135;
			};
		}

		/** Camera path: a fixed side view of the clash, shaking as the casing breaks or the fire bursts. */
		private void shoot() {
			if (camera == null) {
				return;
			}
			Vector from = actor().getLocation().toVector();
			Vector to = target.getLocation().toVector();
			Vector focus = from.clone().midpoint(to).add(new Vector(0, 1.0, 0));
			if (Double.isNaN(cameraSide)) {
				// Fixed once, square to the line between the two, so the view never turns.
				cameraSide = Math.atan2(from.getZ() - to.getZ(), from.getX() - to.getX()) + Math.PI / 2;
			}
			int sinceRelease = tick - releaseTick();
			double shake = sinceRelease >= 0 && sinceRelease < 5 ? 0.4 + 0.2 * stage : 0.0;
			camera.frame(Cinematic.orbit(focus, cameraSide, 3.0 + 0.3 * stage, 1.3), focus, shake);
		}

		private int endTick() {
			// The scenes run 5.5 s, 8.6 s and 8.75 s.
			return switch (stage) {
				case 1 -> 110;
				case 2 -> 172;
				default -> 175;
			};
		}

		boolean tick() {
			if (!player.isOnline() || player.isDead() || !target.isValid() || target.isDead()
					|| !Items.isLaevatein(player.getInventory().getItemInMainHand())) {
				finish();
				return false;
			}
			hold();
			shoot();
			if (tick % 6 == 0) {
				LaevateinEffects.aura(actor(), stage);
			}
			switch (stage) {
				case 1 -> stageOne();
				case 2 -> stageTwo();
				default -> stageThree();
			}
			if (tick >= endTick()) {
				finish();
				return false;
			}
			tick++;
			return true;
		}

		/** "Unwrapping one package": grey sweeps, the casing shatters, then a burning slash. */
		private void stageOne() {
			switch (tick) {
				case 20 -> strike(4.0, 0, () -> LaevateinEffects.greyArc(actor(), target, -100, 15));
				case 36 -> strike(4.0, 0, () -> LaevateinEffects.greyArc(actor(), target, 80, -20));
				case 59 -> {
					release();
					strike(6.0, 0, () -> LaevateinEffects.shatter(target));
				}
				case 90 -> strike(5.0, 60, () -> LaevateinEffects.flameArc(actor(), target, -90, 25));
				default -> {
				}
			}
		}

		/** "The second wrapping": three burning crescents, then a great explosion. */
		private void stageTwo() {
			switch (tick) {
				case 18 -> strike(5.0, 60, () -> LaevateinEffects.flameArc(actor(), target, -100, 20));
				// A burning slash between the blows (no damage of its own).
				case 60 -> {
					actor().swingMainHand();
					LaevateinEffects.flameArc(actor(), target, -80, 30);
				}
				case 45 -> strike(5.0, 60, () -> {
					LaevateinEffects.flameArc(actor(), target, 70, -25);
					LaevateinEffects.explosion(target, 1.0);
				});
				case 88 -> strike(5.0, 60, () -> LaevateinEffects.flameArc(actor(), target, -60, 40));
				case 132 -> {
					release();
					strike(9.0, 100, () -> LaevateinEffects.explosion(target, 2.5));
				}
				default -> {
				}
			}
		}

		/** "Brought out this far": lightning falls, two burning slashes, then an inferno. */
		private void stageThree() {
			switch (tick) {
				case 8, 70 -> strike(4.0, 0, () -> LaevateinEffects.lightningStrike(target));
				case 29 -> strike(6.0, 80, () -> LaevateinEffects.flameArc(actor(), target, -100, 20));
				case 80 -> strike(6.0, 80, () -> LaevateinEffects.flameArc(actor(), target, 80, -30));
				case 135 -> {
					release();
					strike(14.0, 160, () -> LaevateinEffects.inferno(target));
				}
				default -> {
				}
			}
		}

		private void strike(double damage, int fireTicks, Runnable effect) {
			// Each strike is a coin: flipped as it lands, skipped if destroyed in a clash.
			SkillCard card = stage == 1 ? SkillCard.RELEASE_1 : stage == 2 ? SkillCard.RELEASE_2 : SkillCard.RELEASE_3;
			if (!Clash.coin(player, Math.min(coinIndex++, card.coins() - 1))) {
				return;
			}
			if (camera != null) {
				camera.jolt(0.45);
			}
			actor().swingMainHand();
			effect.run();
			Matthias.addTattoo(player);
			Damage.deal(target, Matthias.scale(player, damage), player, true);
			if (fireTicks > 0 && target.isValid()) {
				target.setFireTicks(Math.max(target.getFireTicks(), fireTicks));
			}
		}

		/** The blade takes its new form and the hit count starts over. */
		private void release() {
			ItemStack held = player.getInventory().getItemInMainHand();
			if (Items.isLaevatein(held)) {
				Items.setLaevateinStage(held, stage);
				setHits(held, 0);
			}
			if (camera != null) {
				camera.syncHeld();
			}
			Matthias.grantGrit(player, stage);
		}

		private void hold() {
			Location here = target.getLocation();
			if (here.distanceSquared(anchor) > 0.01) {
				Location back = anchor.clone();
				back.setYaw(here.getYaw());
				back.setPitch(here.getPitch());
				target.teleport(back, TeleportFlag.EntityState.RETAIN_PASSENGERS);
			}
			target.setVelocity(new Vector());
		}

		/** The body that swings: the camera's stand-in while one runs, else the player. */
		private LivingEntity actor() {
			return camera != null ? camera.actor() : player;
		}

		private void finish() {
			Animations.stopSkill(player);
			TotalDamage.finish(player);
			if (camera != null) {
				camera.end();
				camera = null;
			}
			if (!(target instanceof Player)) {
				target.setAI(hadAi);
			}
		}
	}
}
