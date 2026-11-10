package io.github.sharket1111.indexprescript.paper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import io.papermc.paper.entity.TeleportFlag;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Mimicry's combat pages (Library of Ruina), as coin skills.
 *
 * Right-click with Mimicry lays the skills along the hotbar, as the Black God
 * does: switching to a slot (number key, scroll) or right-clicking the one
 * in hand uses it, slot 9 cancels, and after 5 seconds the hotbar goes back
 * by itself. The real hotbar is kept in memory and on the player (so a crash
 * loses nothing) and put back exactly; while choosing, the icons cannot be
 * moved, dropped or swapped.
 *
 * Light costs become cooldowns (2 light: 3 s ... 6 light: 16 s). The dice
 * become coins: the first die's range is base to base + all coins heads, and
 * each coin's damage follows its die's average. In E.G.O, Red Mist adds its
 * power (see {@link MimicryWeapon#bonusPower}). "40 damage" thresholds of the
 * original read as 30 here (3 health), "next scene" as the next 7 seconds.
 * - 종베기, 찌르기: a coin dealing 30 or more draws a page: every cooldown -1 s,
 *   종베기 and 찌르기 a second more.
 * - 횡베기: Bleed 3 on hit; dealing 30 or more recovers 2 light (every cooldown -2 s).
 * - 기합: Sanity +30 on use; the first coin guards (Resistance II); the slash gives Strength 3 for
 *   the next scene on hit (+3 power to these skills, shown as attack up).
 * - 돌진: a kill or a stagger uses it again on a random foe nearby (up to 5 times).
 * - 대절단-세로: E.G.O only; on hit, destroys one of the target's combat pages: a player or
 *   a dummy cannot use skills for a scene, a creature is weakened.
 * - 대절단-가로: E.G.O only; strikes every foe within 6 blocks, Bleed 5.
 */
final class MimicrySkill {
	enum Kind {
		VERTICAL("종베기", DamageKind.SLASH, 6, 2, new double[] {8.0, 7.5}, new int[] {10, 22}, 36, 60, "minecraft:iron_sword", false,
				"피해 30 이상: 책장 뽑기 — 모든 스킬 대기 -1초, 종베기·찌르기 -1초 더."),
		STAB("찌르기", DamageKind.PIERCE, 3, 2, new double[] {5.5, 5.0, 5.0}, new int[] {8, 16, 24}, 36, 60, "minecraft:trident", false,
				"피해 30 이상: 책장 뽑기 — 모든 스킬 대기 -1초, 종베기·찌르기 -1초 더."),
		HORIZONTAL("횡베기", DamageKind.SLASH, 5, 2, new double[] {7.0, 6.5}, new int[] {12, 26}, 40, 60, "minecraft:golden_sword", false,
				"적중 시 출혈 3. 피해 30 이상: 빛 2 회복 — 모든 스킬 대기 -2초."),
		ROAR("기합", DamageKind.SLASH, 5, 3, new double[] {10.0, 6.0}, new int[] {6, 20}, 34, 60, "minecraft:goat_horn", false,
				"사용 시 정신력 +30. 첫 코인은 방어(저항 II). 적중 시 다음 장면 힘 3(위력 +3)."),
		CHARGE("돌진", DamageKind.SLASH, 14, 12, new double[] {20.0}, new int[] {14}, 30, 160, "minecraft:wind_charge", false,
				"적중 시 흐트러짐 기준의 절반만큼 흐트러짐 피해. 처치하거나 흐트러뜨리면 무작위 적에게 이 스킬을 다시 사용(최대 5번)."),
		GREAT_VERTICAL("대절단-세로", DamageKind.SLASH, 20, 19, new double[] {29.5}, new int[] {24}, 46, 240, "index_prescript:mimicry", true,
				"적중 시 대상의 전투 책장 하나를 파괴: 1장면 동안 스킬을 쓸 수 없다. E.G.O 발현 중에만."),
		GREAT_HORIZONTAL("대절단-가로", DamageKind.SLASH, 28, 14, new double[] {35.0}, new int[] {28}, 50, 320, "index_prescript:mimicry", true,
				"광역 6블록. 적중 시 출혈 5. E.G.O 발현 중에만.");

		final String name;
		final DamageKind type;
		// Tunable from skills.yml (SkillTuning): base, coin power, coin damage, cooldown, text.
		int base;
		int coinPower;
		double[] dice;
		final int[] coins;
		final int length;
		int cooldown;
		final String icon;
		final boolean egoOnly;
		String ability;

		Kind(String name, DamageKind type, int base, int coinPower, double[] dice, int[] coins, int length, int cooldown, String icon,
				boolean egoOnly, String ability) {
			this.name = name;
			this.type = type;
			this.base = base;
			this.coinPower = coinPower;
			this.dice = dice;
			this.coins = coins;
			this.length = length;
			this.cooldown = cooldown;
			this.icon = icon;
			this.egoOnly = egoOnly;
			this.ability = ability;
		}

		boolean big() {
			return this == GREAT_VERTICAL || this == GREAT_HORIZONTAL;
		}

		SkillCard card(Player player) {
			int bonus = MimicryWeapon.bonusPower(player);
			String extra = bonus > 0 ? " (위력 +" + bonus + ")" : "";
			return new SkillCard(name, big() ? GREAT_COLOUR : COLOUR, base + bonus, coinPower, coins.length, ability + extra, length + 10,
					true, "N".repeat(coins.length));
		}
	}

	private static final TextColor COLOUR = TextColor.color(0xE03030);
	private static final TextColor GREAT_COLOUR = TextColor.color(0xFF5A28);
	private static final int CHOOSE_TICKS = 100;
	private static final int OPEN_DEBOUNCE = 8;
	private static final int INFO_SLOT = 7;
	private static final int CANCEL_SLOT = 8;
	private static final int MAX_CHAIN = 5;
	private static final int ROAR_SANITY = 30;
	private static final double RANGE = 8.0;
	private static final double CHARGE_RANGE = 12.0;
	private static final double SWEEP_RADIUS = 6.0;
	private static final double DRAW_DAMAGE = 3.0;
	private static final int SCENE = CombatState.TURN_TICKS;
	private static final NamespacedKey ICON = new NamespacedKey(Items.NAMESPACE, "mimicry_icon");
	private static final NamespacedKey BACKUP = new NamespacedKey(Items.NAMESPACE, "mimicry_hotbar_backup");

	private static final Map<UUID, Menu> CHOOSING = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> OPENED = new ConcurrentHashMap<>();
	private static final Map<UUID, long[]> READY = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> SEALED = new ConcurrentHashMap<>();
	private static final Map<UUID, MimicrySkill> ACTIVE = new ConcurrentHashMap<>();

	private static final class Menu {
		final ItemStack[] saved;
		final long until;

		Menu(ItemStack[] saved, long until) {
			this.saved = saved;
			this.until = until;
		}
	}

	// --- queries ---------------------------------------------------------------------

	static boolean active(Player player) {
		return ACTIVE.containsKey(player.getUniqueId());
	}

	static boolean choosing(Player player) {
		return CHOOSING.containsKey(player.getUniqueId());
	}

	/** Whether 대절단-세로 destroyed this unit's combat page: no skills until the scene is over. */
	static boolean sealed(Entity unit) {
		Long until = SEALED.get(unit.getUniqueId());
		if (until == null) {
			return false;
		}
		if (CaduceusListener.now() >= until) {
			SEALED.remove(unit.getUniqueId());
			return false;
		}
		return true;
	}

	private static int ticksLeft(Player player, Kind kind) {
		long[] ready = READY.get(player.getUniqueId());
		return ready == null ? 0 : (int) Math.max(0, ready[kind.ordinal()] - CaduceusListener.now());
	}

	private static void startCooldown(Player player, Kind kind) {
		long[] ready = READY.computeIfAbsent(player.getUniqueId(), id -> new long[Kind.values().length]);
		ready[kind.ordinal()] = CaduceusListener.now() + kind.cooldown;
	}

	/** Takes {@code ticks} off the cooldowns of {@code kinds} (all of them when none are named). */
	private static void shorten(Player player, int ticks, Kind... kinds) {
		long[] ready = READY.get(player.getUniqueId());
		if (ready == null) {
			return;
		}
		for (Kind kind : kinds.length == 0 ? Kind.values() : kinds) {
			ready[kind.ordinal()] -= ticks;
		}
		if (choosing(player)) {
			layIcons(player);
		}
	}

	// --- the hotbar menu -------------------------------------------------------------

	private static boolean isIcon(ItemStack stack) {
		return stack != null && !stack.isEmpty() && stack.hasItemMeta()
				&& stack.getItemMeta().getPersistentDataContainer().has(ICON, PersistentDataType.INTEGER);
	}

	private static int iconIndex(ItemStack stack) {
		Integer index = stack.getItemMeta().getPersistentDataContainer().get(ICON, PersistentDataType.INTEGER);
		return index == null ? -1 : index;
	}

	private static Component line(String text, TextColor colour) {
		return Component.text(text, colour).decoration(TextDecoration.ITALIC, false);
	}

	private static ItemStack iconItem(int index, String model, boolean egoLook, int amount, Component name, List<Component> lore) {
		ItemStack stack = ItemStack.of(Material.PAPER);
		stack.editMeta(meta -> {
			meta.setMaxStackSize(99);
			meta.itemName(name);
			meta.lore(lore);
			meta.setRarity(ItemRarity.EPIC);
			meta.addItemFlags(ItemFlag.values());
			meta.setItemModel(NamespacedKey.fromString(model));
			if (egoLook) {
				CustomModelDataComponent data = meta.getCustomModelDataComponent();
				data.setStrings(List.of(Items.MIMICRY_EGO));
				meta.setCustomModelDataComponent(data);
			}
			meta.getPersistentDataContainer().set(ICON, PersistentDataType.INTEGER, index);
		});
		stack.setAmount(Math.max(1, Math.min(99, amount)));
		return stack;
	}

	private static ItemStack skillIcon(Player player, Kind kind) {
		int left = ticksLeft(player, kind);
		boolean locked = kind.egoOnly && !MimicryWeapon.ego(player);
		int bonus = MimicryWeapon.bonusPower(player);
		List<Component> lore = new ArrayList<>();
		lore.add(line("위력 " + (kind.base + bonus) + " · 코인 +" + kind.coinPower + " ×" + kind.coins.length
				+ (bonus > 0 ? "  (붉은 안개·힘 +" + bonus + ")" : ""), NamedTextColor.GOLD));
		lore.add(line(kind.ability, NamedTextColor.GRAY));
		lore.add(line("대기 " + kind.cooldown / 20 + "초", NamedTextColor.DARK_GRAY));
		String model = kind.icon;
		TextColor colour = kind.big() ? GREAT_COLOUR : COLOUR;
		int amount = 1;
		if (locked) {
			model = "minecraft:barrier";
			colour = NamedTextColor.DARK_GRAY;
			lore.add(line("E.G.O 발현 중에만 쓸 수 있습니다.", NamedTextColor.RED));
		} else if (left > 0) {
			model = "minecraft:gray_dye";
			colour = NamedTextColor.GRAY;
			amount = (left + 19) / 20;
			lore.add(line("남은 대기 " + amount + "초", NamedTextColor.RED));
		}
		return iconItem(kind.ordinal(), model, !locked && left == 0 && kind == Kind.GREAT_HORIZONTAL, amount,
				Component.text((kind.ordinal() + 1) + ". " + kind.name, colour).decoration(TextDecoration.ITALIC, false), lore);
	}

	private static ItemStack infoIcon(Player player) {
		List<Component> lore = new ArrayList<>();
		boolean ego = MimicryWeapon.ego(player);
		if (ego) {
			DamageKind normal = MimicryWeapon.normal(player);
			lore.add(line("붉은 안개: 처치 " + MimicryWeapon.stacks(player) + "/5, 위력 +" + MimicryWeapon.bonusPower(player), NamedTextColor.RED));
			lore.add(line("이번 장면 보통 내성: " + (normal == null ? "-" : switch (normal) {
				case SLASH -> "참격";
				case PIERCE -> "관통";
				case BLUNT -> "타격";
			}) + " (그 외 견딤)", NamedTextColor.GRAY));
		} else {
			lore.add(line("F: E.G.O 발현 (정신력 " + MimicryWeapon.SANITY_NEEDED + " 이상, 체력 50% 이하, 정신력 -" + MimicryWeapon.SANITY_COST + ")",
					NamedTextColor.GRAY));
		}
		lore.add(line("반격: 장면마다 한 번, 근접 공격을 받으면 반격(4~8).", NamedTextColor.DARK_GRAY));
		return iconItem(INFO_SLOT, ego ? "minecraft:redstone" : "minecraft:nether_wart", false, 1,
				Component.text(ego ? "E.G.O — 붉은 안개" : "기본 상태", ego ? MimicryWeapon.RED_TEXT : NamedTextColor.WHITE)
						.decoration(TextDecoration.ITALIC, false), lore);
	}

	private static ItemStack cancelIcon() {
		return iconItem(CANCEL_SLOT, "minecraft:barrier", false, 1, Component.text("9. 취소", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC,
				false), List.of());
	}

	/** Every hotbar slot gets an icon, so nothing picked up can land in the hotbar meanwhile. */
	private static void layIcons(Player player) {
		PlayerInventory inventory = player.getInventory();
		Kind[] kinds = Kind.values();
		for (int slot = 0; slot < 9; slot++) {
			ItemStack icon = slot < kinds.length ? skillIcon(player, kinds[slot]) : slot == INFO_SLOT ? infoIcon(player) : cancelIcon();
			inventory.setItem(slot, icon);
		}
	}

	private static void open(Player player) {
		long now = CaduceusListener.now();
		if (choosing(player) || now - OPENED.getOrDefault(player.getUniqueId(), Long.MIN_VALUE / 2) < OPEN_DEBOUNCE) {
			return;
		}
		if (active(player) || Clash.blocked(player) || player.getGameMode() == GameMode.SPECTATOR) {
			return;
		}
		OPENED.put(player.getUniqueId(), now);
		PlayerInventory inventory = player.getInventory();
		ItemStack[] saved = new ItemStack[9];
		for (int slot = 0; slot < 9; slot++) {
			ItemStack stack = inventory.getItem(slot);
			saved[slot] = stack == null || stack.isEmpty() || isIcon(stack) ? null : stack.clone();
		}
		backup(player, saved);
		CHOOSING.put(player.getUniqueId(), new Menu(saved, now + CHOOSE_TICKS));
		layIcons(player);
		player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, SoundCategory.PLAYERS, 1.0F, 0.8F);
		Hud.message(player, Component.text("전투 책장: 1~7 선택 · 9 취소", MimicryWeapon.RED_TEXT));
	}

	/** Puts the real hotbar back. */
	private static void close(Player player) {
		Menu menu = CHOOSING.remove(player.getUniqueId());
		if (menu != null) {
			restore(player, menu.saved);
		}
	}

	private static void restore(Player player, ItemStack[] saved) {
		PlayerInventory inventory = player.getInventory();
		List<ItemStack> stray = new ArrayList<>();
		for (int slot = 0; slot < 9; slot++) {
			ItemStack current = inventory.getItem(slot);
			if (current != null && !current.isEmpty() && !isIcon(current)) {
				stray.add(current);
			}
			inventory.setItem(slot, saved[slot]);
		}
		purgeIcons(player);
		for (ItemStack stack : stray) {
			inventory.addItem(stack).values().forEach(rest -> player.getWorld().dropItem(player.getLocation(), rest));
		}
		player.getPersistentDataContainer().remove(BACKUP);
	}

	private static void purgeIcons(Player player) {
		PlayerInventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getSize(); slot++) {
			if (isIcon(inventory.getItem(slot))) {
				inventory.setItem(slot, null);
			}
		}
		if (isIcon(player.getItemOnCursor())) {
			player.setItemOnCursor(null);
		}
	}

	private static void backup(Player player, ItemStack[] saved) {
		List<byte[]> bytes = new ArrayList<>();
		for (ItemStack stack : saved) {
			bytes.add(stack == null ? new byte[0] : stack.serializeAsBytes());
		}
		player.getPersistentDataContainer().set(BACKUP, PersistentDataType.LIST.byteArrays(), bytes);
	}

	/** After a crash mid-choice: the hotbar saved on the player comes back. */
	private static void recover(Player player) {
		List<byte[]> bytes = player.getPersistentDataContainer().get(BACKUP, PersistentDataType.LIST.byteArrays());
		if (bytes == null) {
			purgeIcons(player);
			return;
		}
		ItemStack[] saved = new ItemStack[9];
		for (int slot = 0; slot < Math.min(9, bytes.size()); slot++) {
			byte[] data = bytes.get(slot);
			try {
				saved[slot] = data.length == 0 ? null : ItemStack.deserializeBytes(data);
			} catch (RuntimeException e) {
				IndexPrescriptPlugin.instance().getLogger().log(Level.WARNING, "Could not restore a Mimicry hotbar slot", e);
			}
		}
		restore(player, saved);
	}

	private static void select(Player player, int index) {
		if (index == INFO_SLOT) {
			return;
		}
		close(player);
		if (index >= 0 && index < Kind.values().length) {
			use(player, Kind.values()[index]);
		}
	}

	// --- using a skill ---------------------------------------------------------------

	static void use(Player player, Kind kind) {
		if (active(player) || Clash.blocked(player) || !Items.isMimicry(player.getInventory().getItemInMainHand())) {
			return;
		}
		if (kind.egoOnly && !MimicryWeapon.ego(player)) {
			Hud.message(player, Component.text(kind.name + "은(는) E.G.O 발현 중에만 쓸 수 있습니다.", NamedTextColor.GRAY));
			return;
		}
		int left = ticksLeft(player, kind);
		if (left > 0) {
			Hud.message(player, Component.text(kind.name + " 대기 중 (" + (left + 19) / 20 + "초)", NamedTextColor.GRAY));
			return;
		}
		LivingEntity target = Clash.answering(player) != null ? Clash.answering(player)
				: lookedAt(player, kind == Kind.CHARGE ? CHARGE_RANGE : RANGE);
		if (target == null) {
			Hud.message(player, Component.text(Messages.get(player, "renoir.no_target"), NamedTextColor.GRAY));
			return;
		}
		if (Clash.request(player, target, kind.card(player), outcome -> launch(player, outcome.target(), kind, 0))) {
			startCooldown(player, kind);
			MimicryWeapon.fought(player);
			if (kind == Kind.ROAR) {
				// 기합: Sanity +30 on use (it never goes past 45).
				Sanity.add(player, ROAR_SANITY);
				Hud.message(player, Component.text("기합 — 정신력 +" + ROAR_SANITY, MimicryWeapon.RED_TEXT));
			}
		}
	}

	private static void launch(Player player, LivingEntity target, Kind kind, int chain) {
		if (active(player) || !player.isOnline() || !Items.isMimicry(player.getInventory().getItemInMainHand())) {
			return;
		}
		MimicrySkill skill = new MimicrySkill(player, target, kind, chain);
		ACTIVE.put(player.getUniqueId(), skill);
		skill.open();
	}

	private static boolean foe(Player player, LivingEntity living) {
		if (living == player || living.isDead() || !living.isValid() || Cinematic.isActor(living)
				|| living instanceof ArmorStand) {
			return false;
		}
		if (living instanceof Player other) {
			return other.getGameMode() == GameMode.SURVIVAL || other.getGameMode() == GameMode.ADVENTURE;
		}
		return living instanceof Enemy || ClashDummy.is(living);
	}

	private static LivingEntity lookedAt(Player player, double range) {
		if (player.getTargetEntity((int) range) instanceof LivingEntity living && !living.isDead() && living != player
				&& !Cinematic.isActor(living)) {
			return living;
		}
		Vector eye = player.getEyeLocation().toVector();
		Vector look = player.getEyeLocation().getDirection();
		LivingEntity best = null;
		double bestDot = 0.93;
		for (Entity entity : player.getNearbyEntities(range, range, range)) {
			if (!(entity instanceof LivingEntity living) || living.isDead() || Cinematic.isActor(living)
					|| entity instanceof ArmorStand stand && stand.isMarker()) {
				continue;
			}
			Vector to = living.getLocation().add(0, living.getHeight() * 0.5, 0).toVector().subtract(eye);
			double dot = to.clone().normalize().dot(look);
			if (to.length() <= range && dot > bestDot && player.hasLineOfSight(living)) {
				best = living;
				bestDot = dot;
			}
		}
		return best;
	}

	private static LivingEntity randomFoe(Player player, LivingEntity except) {
		List<LivingEntity> foes = new ArrayList<>();
		for (Entity entity : player.getNearbyEntities(10, 6, 10)) {
			if (entity instanceof LivingEntity living && living != except && foe(player, living) && player.hasLineOfSight(living)) {
				foes.add(living);
			}
		}
		return foes.isEmpty() ? null : foes.get(ThreadLocalRandom.current().nextInt(foes.size()));
	}

	// --- one skill as it plays -------------------------------------------------------

	private final Player player;
	private final LivingEntity target;
	private final Kind kind;
	private final int chain;
	private final int bonus;
	private final Location anchor;
	private final boolean hadAi;
	private Location start;
	private Cinematic camera;
	private double cameraSide = Double.NaN;
	private boolean drew;
	private boolean again;
	private int tick;

	private MimicrySkill(Player player, LivingEntity target, Kind kind, int chain) {
		this.player = player;
		this.target = target;
		this.kind = kind;
		this.chain = chain;
		this.bonus = MimicryWeapon.bonusPower(player);
		this.anchor = target.getLocation();
		this.hadAi = target.hasAI();
	}

	static void tickAll() {
		long now = CaduceusListener.now();
		for (Map.Entry<UUID, Menu> entry : Map.copyOf(CHOOSING).entrySet()) {
			Player player = Bukkit.getPlayer(entry.getKey());
			if (player == null) {
				CHOOSING.remove(entry.getKey());
			} else if (now >= entry.getValue().until || player.isDead()) {
				close(player);
			} else if (now % 10 == 0) {
				layIcons(player);
			}
		}
		ACTIVE.values().removeIf(skill -> {
			try {
				return !skill.tick();
			} catch (RuntimeException e) {
				IndexPrescriptPlugin.instance().getLogger().log(Level.WARNING, "Mimicry skill failed", e);
				skill.finish();
				return true;
			}
		});
	}

	static void endAll() {
		for (UUID id : Map.copyOf(CHOOSING).keySet()) {
			Player player = Bukkit.getPlayer(id);
			if (player != null) {
				close(player);
			}
		}
		CHOOSING.clear();
		List<MimicrySkill> running = List.copyOf(ACTIVE.values());
		ACTIVE.clear();
		for (MimicrySkill skill : running) {
			skill.again = false;
			skill.finish();
		}
	}

	private void open() {
		Animations.playSkill(player, "skill_mimicry_" + kind.name().toLowerCase(java.util.Locale.ROOT), this::actor);
		if (!(target instanceof Player)) {
			target.setAI(false);
		}
		start = player.getLocation();
		camera = Cinematic.start(player, player.getLocation().add(0, 2, 0));
		if (kind.big()) {
			TotalDamage.start(player);
		}
		SkillOverlay.show(player, kind.big() ? SkillOverlay.Size.BIG : SkillOverlay.Size.SMALL);
		MimicryWeapon.fought(player);
		World world = player.getWorld();
		Location at = player.getLocation().add(0, 1, 0);
		world.playSound(at, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, SoundCategory.PLAYERS, 0.8F, 0.6F);
		world.spawnParticle(Particle.DUST, at, 20, 0.4, 0.7, 0.4, 0.0, new Particle.DustOptions(MimicryWeapon.RED, 1.5F));
		if (chain > 0) {
			Hud.message(player, Component.text("돌진 재사용 ×" + chain, MimicryWeapon.RED_TEXT));
		}
	}

	private boolean tick() {
		if (!player.isOnline() || player.isDead() || !target.isValid() || target.isDead() && tick < kind.coins[0]
				|| !Items.isMimicry(player.getInventory().getItemInMainHand())) {
			finish();
			return false;
		}
		if (!target.isDead()) {
			hold();
		}
		shoot();
		player.setFallDistance(0.0F);
		move();
		for (int i = 0; i < kind.coins.length; i++) {
			if (tick == kind.coins[i]) {
				strike(i);
			}
		}
		if (tick >= kind.length) {
			finish();
			return false;
		}
		tick++;
		return true;
	}

	// --- movement and the build-up of each skill ----------------------------------------

	private Vector toTarget() {
		return MimicryWeapon.flat(target.getLocation().toVector().subtract(actor().getLocation().toVector()));
	}

	private void place(Vector spot, double lift) {
		LivingEntity actor = actor();
		Location destination = new Location(target.getWorld(), spot.getX(), target.getLocation().getY() + lift, spot.getZ());
		destination.setDirection(MimicryWeapon.flat(target.getLocation().toVector().subtract(spot)));
		actor.teleport(destination);
	}

	/** In front of the target, {@code distance} away, a little to one side. */
	private Vector front(double distance, double side) {
		Vector toward = toTarget();
		Vector across = new Vector(-toward.getZ(), 0, toward.getX()).multiply(side);
		return target.getLocation().toVector().subtract(toward.clone().multiply(distance)).add(across);
	}

	private void move() {
		World world = target.getWorld();
		switch (kind) {
			case CHARGE -> {
				if (tick < 4) {
					// Crouch: red mist gathers at the feet.
					world.spawnParticle(Particle.DUST, actor().getLocation().add(0, 0.2, 0), 6, 0.4, 0.05, 0.4, 0.0,
							new Particle.DustOptions(MimicryWeapon.DARK_RED, 1.6F));
					if (tick == 0) {
						world.playSound(actor().getLocation(), Sound.ENTITY_RAVAGER_ROAR, SoundCategory.PLAYERS, 0.6F, 1.4F);
					}
				} else if (tick <= kind.coins[0]) {
					Vector from = actor().getLocation().toVector();
					Vector goal = front(1.2, 0);
					double t = (tick - 3) / (double) (kind.coins[0] - 3);
					Vector spot = start.toVector().multiply(1 - t).add(goal.clone().multiply(t));
					place(spot, 0);
					trail(world, from, spot);
					if (tick == 4) {
						world.playSound(actor().getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, SoundCategory.PLAYERS, 1.0F, 0.7F);
					}
				} else if (tick == kind.coins[0] + 2) {
					// Through and past.
					Vector from = actor().getLocation().toVector();
					Vector past = target.getLocation().toVector().add(toTarget().multiply(2.2));
					place(past, 0);
					trail(world, from, past);
				}
			}
			case GREAT_VERTICAL -> {
				if (tick == 0) {
					place(front(2.2, 0), 0);
					world.playSound(actor().getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, SoundCategory.PLAYERS, 1.6F, 0.7F);
				}
				if (tick > 2 && tick <= 16) {
					// The blade is raised; the mist climbs it.
					place(front(2.2, 0), (tick - 2) * 0.11);
					Location head = actor().getLocation().add(0, 2.4, 0);
					world.spawnParticle(Particle.DUST, head, 8, 0.25, 0.6, 0.25, 0.0, new Particle.DustOptions(MimicryWeapon.RED, 1.6F));
					if (tick % 4 == 0) {
						world.playSound(head, Sound.BLOCK_FIRE_AMBIENT, SoundCategory.PLAYERS, 1.0F, 0.5F);
					}
				} else if (tick > 18 && tick < kind.coins[0]) {
					place(front(1.6, 0), Math.max(0, (kind.coins[0] - tick) * 0.3));
				} else if (tick == kind.coins[0]) {
					place(front(1.6, 0), 0);
				}
			}
			case GREAT_HORIZONTAL -> {
				if (tick == 0) {
					place(front(2.6, 0), 0);
				}
				if (tick < kind.coins[0]) {
					// The mist swirls in a widening ring.
					Location at = actor().getLocation();
					double radius = 0.8 + tick * 0.18;
					for (int i = 0; i < 4; i++) {
						double angle = tick * 0.5 + i * Math.PI / 2;
						world.spawnParticle(Particle.DUST, at.clone().add(Math.cos(angle) * radius, 0.3 + (tick % 5) * 0.15, Math.sin(angle) * radius),
								2, 0.05, 0.05, 0.05, 0.0, new Particle.DustOptions(i % 2 == 0 ? MimicryWeapon.RED : MimicryWeapon.DARK_RED, 1.8F));
					}
					if (tick % 10 == 0) {
						world.playSound(at, Sound.ENTITY_WARDEN_HEARTBEAT, SoundCategory.PLAYERS, 1.8F, 0.6F + tick * 0.01F);
					}
				}
			}
			default -> {
				for (int i = 0; i < kind.coins.length; i++) {
					if (tick == kind.coins[i] - 3 && !Clash.destroyed(player, i)) {
						Vector from = actor().getLocation().toVector();
						Vector spot = front(1.7, kind == Kind.STAB ? 0 : i % 2 == 0 ? 0.5 : -0.5);
						place(spot, 0);
						trail(world, from, spot);
					}
				}
			}
		}
	}

	// --- the coins -------------------------------------------------------------------

	private void strike(int coin) {
		if (!Clash.coin(player, coin)) {
			return;
		}
		boolean last = coin == kind.coins.length - 1;
		if (camera != null) {
			camera.syncHeld();
			camera.jolt(kind.big() ? 1.3 : last ? 0.7 : 0.4);
		}
		LivingEntity actor = actor();
		actor.swingMainHand();
		MimicryWeapon.fought(player);
		double damage = MimicryWeapon.dieDamage(kind.dice[coin] + bonus);
		long now = CaduceusListener.now();
		switch (kind) {
			case ROAR -> {
				if (coin == 0) {
					guard();
					return;
				}
				visuals(coin);
				if (hit(target, damage) > 0) {
					CombatState.of(player).raiseAttackLevel(3, now, SCENE + 20);
					Hud.message(player, Component.text("힘 3 — 다음 장면 위력 +3", MimicryWeapon.RED_TEXT));
				}
			}
			case GREAT_HORIZONTAL -> {
				visuals(coin);
				List<LivingEntity> hit = new ArrayList<>();
				hit.add(target);
				for (Entity entity : actor.getNearbyEntities(SWEEP_RADIUS, 3, SWEEP_RADIUS)) {
					if (entity instanceof LivingEntity living && living != target && foe(player, living)
							&& living.getLocation().distanceSquared(actor.getLocation()) <= SWEEP_RADIUS * SWEEP_RADIUS) {
						hit.add(living);
					}
				}
				for (LivingEntity living : hit) {
					if (!living.isDead() && hit(living, damage) > 0) {
						Bleed.inflict(living, 5, 0);
					}
				}
			}
			default -> {
				visuals(coin);
				if (target.isDead()) {
					return;
				}
				double dealt = hit(target, damage);
				after(dealt);
			}
		}
	}

	/** Each coin's stagger damage, as a share of the target's stagger threshold. */
	private double staggerShare() {
		return switch (kind) {
			case VERTICAL, HORIZONTAL -> 0.15;
			case STAB -> 0.1;
			case ROAR -> 0.2;
			case CHARGE -> 0.5;
			case GREAT_VERTICAL -> 0.4;
			case GREAT_HORIZONTAL -> 0.3;
		};
	}

	/**
	 * Deals a coin's damage; the Bleed already on the target triggers (extra
	 * damage by its potency), new Bleed goes on, and the stagger damage is a
	 * share of the target's threshold. Returns the health taken.
	 */
	private double hit(LivingEntity victim, double damage) {
		double before = victim.getHealth() + victim.getAbsorptionAmount();
		Damage.deal(victim, damage, player, true);
		double dealt = before - (victim.isDead() ? 0.0 : victim.getHealth() + victim.getAbsorptionAmount());
		if (dealt > 0 && !victim.isDead()) {
			dealt += Bleed.trigger(victim, player);
		}
		if (dealt > 0 && !victim.isDead()) {
			MimicryWeapon.bleedOnHit(player, victim);
			long now = CaduceusListener.now();
			float threshold = (float) Math.max(10.0, maxHealth(victim) * 0.5);
			CombatState state = CombatState.of(victim);
			if (state.addStagger((float) (threshold * staggerShare()), threshold, now)) {
				Stagger.apply(victim, now);
				Effects.staggered(victim);
				if (victim == target && kind == Kind.CHARGE) {
					again = true;
				}
			} else {
				Stagger.gauge(victim, state.staggerBuildUp(now) / threshold);
			}
		}
		if (victim.isDead() && victim == target && kind == Kind.CHARGE) {
			again = true;
		}
		return dealt;
	}

	/** What a coin that struck the target gives, by skill. */
	private void after(double dealt) {
		switch (kind) {
			case VERTICAL, STAB -> {
				if (dealt >= DRAW_DAMAGE && !drew) {
					drew = true;
					shorten(player, 20);
					shorten(player, 20, Kind.VERTICAL, Kind.STAB);
					Hud.message(player, Component.text("책장 뽑기 — 대기 시간 감소", MimicryWeapon.RED_TEXT));
				}
			}
			case HORIZONTAL -> {
				if (dealt > 0) {
					Bleed.inflict(target, 3, 0);
				}
				if (dealt >= DRAW_DAMAGE && !drew) {
					drew = true;
					shorten(player, 40);
					Hud.message(player, Component.text("빛 2 회복 — 대기 시간 -2초", MimicryWeapon.RED_TEXT));
				}
			}
			case GREAT_VERTICAL -> {
				if (dealt > 0 && !target.isDead()) {
					destroyPage(target);
				}
			}
			default -> {
			}
		}
	}

	/** 기합's guard coin: braced, the roar pushes the target back a little. */
	private void guard() {
		player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 40, 1, true, false, true));
		LivingEntity actor = actor();
		World world = actor.getWorld();
		Location at = actor.getLocation().add(0, 1.2, 0);
		world.playSound(at, Sound.ENTITY_RAVAGER_ROAR, SoundCategory.PLAYERS, 1.0F, 1.2F);
		world.playSound(at, Sound.ITEM_SHIELD_BLOCK, SoundCategory.PLAYERS, 1.0F, 0.7F);
		for (int i = 0; i < 36; i++) {
			double angle = Math.PI * 2 * i / 36;
			world.spawnParticle(Particle.DUST, at.clone().add(Math.cos(angle) * 1.6, -0.6, Math.sin(angle) * 1.6), 1, 0.0, 0.0, 0.0, 0.0,
					new Particle.DustOptions(MimicryWeapon.RED, 1.5F));
		}
		world.spawnParticle(Particle.SONIC_BOOM, at.clone().add(toTarget().multiply(1.2)), 1);
	}

	/** 대절단-세로: one of the target's combat pages destroyed. */
	private void destroyPage(LivingEntity victim) {
		if (victim instanceof Player || ClashDummy.is(victim)) {
			SEALED.put(victim.getUniqueId(), CaduceusListener.now() + SCENE);
			if (victim instanceof Player other) {
				Hud.message(other, Component.text("전투 책장이 파괴되었다 — 7초 동안 스킬 사용 불가", NamedTextColor.RED));
			}
		} else {
			victim.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, SCENE, 1));
		}
		Hud.message(player, Component.text("전투 책장 파괴", MimicryWeapon.RED_TEXT));
		World world = victim.getWorld();
		Location at = victim.getLocation().add(0, victim.getHeight() + 0.4, 0);
		world.spawnParticle(Particle.ITEM, at, 20, 0.2, 0.2, 0.2, 0.08, ItemStack.of(Material.PAPER));
		world.playSound(at, Sound.ENTITY_ITEM_BREAK, SoundCategory.PLAYERS, 1.0F, 0.6F);
	}

	// --- visuals ---------------------------------------------------------------------

	/** A crescent of red dust: {@code tilt} 0 lies flat, 90 stands upright along {@code forward}. */
	static void arc(World world, Vector centre, Vector forward, double radius, double tilt, double from, double sweep, float size) {
		Vector f = MimicryWeapon.flat(forward);
		Vector right = new Vector(-f.getZ(), 0, f.getX());
		double t = Math.toRadians(tilt);
		Vector side = right.clone().multiply(Math.cos(t)).add(new Vector(0, Math.sin(t), 0));
		int points = (int) Math.max(12, Math.abs(sweep) / 5);
		for (int i = 0; i <= points; i++) {
			double angle = Math.toRadians(from + sweep * i / points);
			Vector p = centre.clone().add(f.clone().multiply(Math.cos(angle) * radius)).add(side.clone().multiply(Math.sin(angle) * radius));
			Location at = p.toLocation(world);
			world.spawnParticle(Particle.DUST, at, 1, 0.02, 0.02, 0.02, 0.0, new Particle.DustOptions(MimicryWeapon.RED, size));
			if (i % 2 == 0) {
				Vector inner = centre.clone().add(f.clone().multiply(Math.cos(angle) * radius * 0.86))
						.add(side.clone().multiply(Math.sin(angle) * radius * 0.86));
				world.spawnParticle(Particle.DUST, inner.toLocation(world), 1, 0.02, 0.02, 0.02, 0.0,
						new Particle.DustOptions(MimicryWeapon.DARK_RED, size * 1.2F));
			}
		}
	}

	private static void trail(World world, Vector from, Vector to) {
		Vector step = to.clone().subtract(from);
		double length = step.length();
		if (length < 0.1) {
			return;
		}
		Vector dir = step.multiply(1.0 / length);
		for (double t = 0; t <= length; t += 0.4) {
			Vector p = from.clone().add(dir.clone().multiply(t)).add(new Vector(0, 1.0, 0));
			world.spawnParticle(Particle.DUST, p.toLocation(world), 1, 0.08, 0.15, 0.08, 0.0, new Particle.DustOptions(MimicryWeapon.RED, 1.3F));
		}
	}

	private void visuals(int coin) {
		LivingEntity actor = actor();
		World world = target.getWorld();
		Location mid = target.getLocation().add(0, target.getHeight() * 0.6, 0);
		Vector chest = actor.getLocation().add(0, 1.2, 0).toVector();
		Vector forward = toTarget();
		boolean last = coin == kind.coins.length - 1;
		switch (kind) {
			case VERTICAL -> {
				// An overhead cut, then a rising one.
				arc(world, chest, forward, 2.0, 90, coin == 0 ? 110 : -60, coin == 0 ? -150 : 150, 1.4F);
				world.playSound(mid, Sound.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.PLAYERS, 1.0F, 0.7F);
			}
			case STAB -> {
				Vector tip = chest.clone();
				for (int i = 0; i < 14; i++) {
					tip.add(forward.clone().multiply(0.22));
					world.spawnParticle(Particle.DUST, tip.toLocation(world), 2, 0.03, 0.03, 0.03, 0.0,
							new Particle.DustOptions(i > 9 ? MimicryWeapon.RED : MimicryWeapon.DARK_RED, 1.3F));
				}
				world.spawnParticle(Particle.CRIT, mid, 12, 0.2, 0.2, 0.2, 0.3);
				world.playSound(mid, Sound.ITEM_TRIDENT_HIT, SoundCategory.PLAYERS, 1.0F, 0.8F + coin * 0.1F);
			}
			case HORIZONTAL -> {
				arc(world, chest, forward, 2.2, coin == 0 ? 8 : -8, coin == 0 ? -80 : 80, coin == 0 ? 160 : -160, 1.4F);
				world.spawnParticle(Particle.SWEEP_ATTACK, mid, 2, 0.3, 0.1, 0.3, 0.0);
				world.playSound(mid, Sound.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.0F, 0.7F);
			}
			case ROAR -> {
				arc(world, chest, forward, 2.0, 40, -70, 140, 1.4F);
				world.playSound(mid, Sound.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.PLAYERS, 1.0F, 0.8F);
			}
			case CHARGE -> {
				arc(world, chest, forward, 2.3, 25, -90, 180, 1.6F);
				world.spawnParticle(Particle.SWEEP_ATTACK, mid, 3, 0.4, 0.2, 0.4, 0.0);
				world.spawnParticle(Particle.EXPLOSION, mid, 1);
				world.playSound(mid, Sound.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1.2F, 0.6F);
			}
			case GREAT_VERTICAL -> {
				// One huge cut from the sky into the ground, and the ground splits.
				arc(world, chest.clone().add(new Vector(0, 0.6, 0)), forward, 3.4, 90, 120, -170, 2.2F);
				Location ground = target.getLocation();
				for (double y = 6; y >= 0; y -= 0.25) {
					world.spawnParticle(Particle.DUST, ground.clone().add(0, y, 0), 2, 0.05, 0.05, 0.05, 0.0,
							new Particle.DustOptions(MimicryWeapon.RED, 2.0F));
				}
				for (double d = -3; d <= 3; d += 0.25) {
					Vector p = ground.toVector().add(forward.clone().multiply(d));
					world.spawnParticle(Particle.DUST, p.toLocation(world).add(0, 0.1, 0), 2, 0.1, 0.0, 0.1, 0.0,
							new Particle.DustOptions(MimicryWeapon.DARK_RED, 2.2F));
				}
				world.spawnParticle(Particle.EXPLOSION_EMITTER, ground, 1);
				world.spawnParticle(Particle.BLOCK, ground, 60, 1.2, 0.2, 1.2, 0.0, Material.REDSTONE_BLOCK.createBlockData());
				world.playSound(ground, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.0F, 0.7F);
				world.playSound(ground, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, SoundCategory.PLAYERS, 1.2F, 0.7F);
			}
			case GREAT_HORIZONTAL -> {
				// The full circle, twice over, as wide as the reach.
				Vector centre = actor.getLocation().add(0, 1.0, 0).toVector();
				arc(world, centre, forward, SWEEP_RADIUS, 0, 0, 360, 2.4F);
				arc(world, centre.clone().add(new Vector(0, 0.4, 0)), forward, SWEEP_RADIUS * 0.7, 6, 0, 360, 2.0F);
				world.spawnParticle(Particle.SWEEP_ATTACK, actor.getLocation().add(0, 1, 0), 24, SWEEP_RADIUS * 0.5, 0.3, SWEEP_RADIUS * 0.5, 0.0);
				world.spawnParticle(Particle.EXPLOSION, actor.getLocation().add(0, 1, 0), 4, 2.0, 0.3, 2.0, 0.0);
				world.playSound(actor.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 2.0F, 0.5F);
				world.playSound(actor.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.0F, 0.6F);
				world.playSound(actor.getLocation(), Sound.ITEM_TRIDENT_THUNDER, SoundCategory.PLAYERS, 0.8F, 0.6F);
			}
		}
		world.spawnParticle(Particle.DUST, mid, last ? 30 : 14, 0.35, 0.45, 0.35, 0.0,
				new Particle.DustOptions(Color.fromRGB(0x9A0C10), last ? 1.8F : 1.3F));
		world.spawnParticle(Particle.DAMAGE_INDICATOR, mid, last ? 6 : 3, 0.3, 0.3, 0.3, 0.1);
	}

	/** A side view square to the line between the two; wider for the great cuts. */
	private void shoot() {
		if (camera == null) {
			return;
		}
		Vector from = actor().getLocation().toVector();
		Vector to = target.getLocation().toVector();
		Vector focus = from.clone().midpoint(to).add(new Vector(0, 1.0, 0));
		if (Double.isNaN(cameraSide)) {
			cameraSide = Math.atan2(from.getZ() - to.getZ(), from.getX() - to.getX()) + Math.PI / 2;
		}
		int lastCoin = kind.coins[kind.coins.length - 1];
		boolean close = tick >= lastCoin - 4 && tick < lastCoin + 8;
		double distance = switch (kind) {
			case GREAT_HORIZONTAL -> close ? 7.0 : 8.5;
			case GREAT_VERTICAL -> close ? 4.2 : 5.6;
			case CHARGE -> 6.0;
			default -> close ? 3.6 : 4.6;
		};
		double height = kind == Kind.GREAT_VERTICAL && tick < lastCoin ? 2.0 : 1.4;
		camera.frame(Cinematic.orbit(focus, cameraSide, distance, height), focus, 0.0);
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

	private LivingEntity actor() {
		return camera != null ? camera.actor() : player;
	}

	private static double maxHealth(LivingEntity entity) {
		var max = entity.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
		return max == null ? 20.0 : max.getValue();
	}

	private void finish() {
		Animations.stopSkill(player);
		ACTIVE.remove(player.getUniqueId(), this);
		if (kind.big()) {
			TotalDamage.finish(player);
		} else {
			SkillOverlay.hide(player);
		}
		if (camera != null) {
			camera.end();
			camera = null;
		}
		if (!(target instanceof Player) && target.isValid()) {
			target.setAI(hadAi);
		}
		if (again && chain < MAX_CHAIN && player.isOnline() && !player.isDead()) {
			// 돌진: a kill or a stagger sends it at a random foe nearby.
			again = false;
			Bukkit.getScheduler().runTaskLater(IndexPrescriptPlugin.instance(), () -> {
				if (!player.isOnline() || player.isDead() || active(player) || Clash.blocked(player)
						|| !Items.isMimicry(player.getInventory().getItemInMainHand())) {
					return;
				}
				LivingEntity next = randomFoe(player, target);
				if (next != null) {
					Clash.request(player, next, Kind.CHARGE.card(player), outcome -> launch(player, outcome.target(), Kind.CHARGE, chain + 1));
				}
			}, 3L);
		}
	}

	// --- events ----------------------------------------------------------------------

	/** The menu's and the icons' event handlers. */
	static final class Events implements Listener {
		@EventHandler(priority = EventPriority.LOW)
		public void onUse(PlayerInteractEvent event) {
			if (event.getHand() != EquipmentSlot.HAND
					|| event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
				return;
			}
			Player player = event.getPlayer();
			ItemStack held = event.getItem();
			if (isIcon(held)) {
				event.setCancelled(true);
				if (choosing(player)) {
					select(player, iconIndex(held));
				} else {
					player.getInventory().setItemInMainHand(null);
				}
				return;
			}
			if (Items.isMimicry(held) && !BlackGod.active(player)) {
				event.setCancelled(true);
				open(player);
			}
		}

		/** Right-clicking a creature with Mimicry opens the pages too (that is how one aims at it). */
		@EventHandler(priority = EventPriority.LOW)
		public void onUseOn(PlayerInteractEntityEvent event) {
			if (event.getHand() != EquipmentSlot.HAND) {
				return;
			}
			Player player = event.getPlayer();
			ItemStack held = player.getInventory().getItemInMainHand();
			if (isIcon(held)) {
				event.setCancelled(true);
				if (choosing(player)) {
					select(player, iconIndex(held));
				}
			} else if (Items.isMimicry(held) && !BlackGod.active(player)) {
				event.setCancelled(true);
				open(player);
			}
		}

		/** While choosing, switching slots uses that slot's page instead of moving. */
		@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
		public void onSwitch(PlayerItemHeldEvent event) {
			Player player = event.getPlayer();
			if (!choosing(player)) {
				return;
			}
			event.setCancelled(true);
			select(player, event.getNewSlot());
		}

		@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
		public void onClick(InventoryClickEvent event) {
			if (event.getWhoClicked() instanceof Player player && (choosing(player) || isIcon(event.getCurrentItem()) || isIcon(event.getCursor()))) {
				event.setCancelled(true);
			}
		}

		@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
		public void onDrag(InventoryDragEvent event) {
			if (event.getWhoClicked() instanceof Player player && (choosing(player) || isIcon(event.getOldCursor()))) {
				event.setCancelled(true);
			}
		}

		@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
		public void onDrop(PlayerDropItemEvent event) {
			if (isIcon(event.getItemDrop().getItemStack())) {
				event.setCancelled(true);
			}
		}

		@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
		public void onSwapHands(PlayerSwapHandItemsEvent event) {
			if (choosing(event.getPlayer()) || isIcon(event.getMainHandItem()) || isIcon(event.getOffHandItem())) {
				event.setCancelled(true);
			}
		}

		/** Dying mid-choice: the real hotbar drops (or is kept), never the icons. */
		@EventHandler(priority = EventPriority.HIGHEST)
		public void onDeath(PlayerDeathEvent event) {
			Player player = event.getPlayer();
			event.getDrops().removeIf(MimicrySkill::isIcon);
			Menu menu = CHOOSING.remove(player.getUniqueId());
			if (menu == null) {
				return;
			}
			if (event.getKeepInventory()) {
				restore(player, menu.saved);
			} else {
				for (ItemStack stack : menu.saved) {
					if (stack != null) {
						event.getDrops().add(stack);
					}
				}
				purgeIcons(player);
				player.getPersistentDataContainer().remove(BACKUP);
			}
		}

		@EventHandler
		public void onQuit(PlayerQuitEvent event) {
			close(event.getPlayer());
			OPENED.remove(event.getPlayer().getUniqueId());
		}

		@EventHandler
		public void onJoin(PlayerJoinEvent event) {
			recover(event.getPlayer());
		}
	}
}
