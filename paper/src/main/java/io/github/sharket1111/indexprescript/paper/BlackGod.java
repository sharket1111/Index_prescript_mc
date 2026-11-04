package io.github.sharket1111.indexprescript.paper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;

/**
 * Black God mode (fan-made, after Limbus Company's Sisyphus System boss; the
 * skills and passives are written down in docs/black_god.md).
 *
 * Toggled with /ip blackgod or the Black God item. Turning it on saves the
 * player's whole inventory on the player (so a restart or a crash loses
 * nothing) and clears it; the mode's hotbar is the Black God's weapon (a
 * hammer of white light, "use skills") in slot 1 and "leave" in slot 9. Right-clicking "use skills" lays the nine skills along
 * the hotbar; switching to a slot then (number key, scroll, a tap on
 * Bedrock) uses that slot's skill instead of moving, right-click uses the
 * one in hand, and the hotbar goes back. A Black God's body is hidden in a
 * 3D black robe, with a sunburst halo behind where the head was; twice per
 * time the mode is taken up, a killing blow brings the revival instead
 * ({@link BlackGodRevive}). Turning the mode off gives the
 * saved inventory back, keeping anything picked up meanwhile.
 */
final class BlackGod implements Listener {
	/** A skill: its name, sin (icon and colour), and coin count (the icon's stack size). */
	record Skill(String name, String sin, Material icon, TextColor colour, int coins, String summary) {
	}

	private static final TextColor PRIDE = TextColor.color(0x3F7FE0);
	private static final TextColor GLOOM = TextColor.color(0x2FB8C8);
	private static final TextColor SLOTH = TextColor.color(0xE8B830);
	private static final TextColor ENVY = TextColor.color(0x9A4FD0);
	private static final TextColor GLUTTONY = TextColor.color(0x8AC83A);

	static final List<Skill> SKILLS = List.of(
			new Skill("하아…이제 좀 돌아가 주지?", "오만", Material.BLUE_DYE, PRIDE, 2, "진동 2 · 보존 +3 · 호흡"),
			new Skill("퇴근 좀 하자고.", "우울", Material.CYAN_DYE, GLOOM, 2, "못[Console] · 진동 횟수 · 보존 +5"),
			new Skill("'심판'", "나태", Material.YELLOW_DYE, SLOTH, 3, "파괴 불가 코인 · 못[Breakpoint] 4"),
			new Skill("'신 앞에 뉘우쳐라'던가?", "질투", Material.PURPLE_DYE, ENVY, 1, "방어 · 검은 신의 목소리 +100"),
			new Skill("'속죄하라'", "오만", Material.BLUE_DYE, PRIDE, 3, "권능 15 소모 · 못[Console] · 진동 폭발"),
			new Skill("'형벌받으라'", "우울", Material.CYAN_DYE, GLOOM, 3, "권능 15 소모 · 보존 +6 · 진동 폭발"),
			new Skill("시지프 시스템을 진짜 망가트리려고?", "나태", Material.YELLOW_DYE, SLOTH, 5, "권능 40 소모 · 못[Exception] 9"),
			new Skill("강제 퇴장", "탐식", Material.LIME_DYE, GLUTTONY, 1, "적출 코인 · 최대 체력 200% 고정 피해"),
			new Skill("Shift+Delete", "나태", Material.YELLOW_DYE, SLOTH, 3, "적출 코인 3개 · 50%/50%/200% 고정 피해"));

	private static final String TOGGLE = "toggle";
	private static final String USE = "use";
	private static final String LEAVE = "leave";
	private static final String SKILL = "skill_";
	private static final int USE_SLOT = 0;
	private static final int LEAVE_SLOT = 8;
	private static final int CHOOSE_TICKS = 200;
	private static final Color GOLD = Color.fromRGB(0xE8C860);
	private static final NamespacedKey ITEM = new NamespacedKey(Items.NAMESPACE, "black_god_item");
	private static final NamespacedKey ACTIVE = new NamespacedKey(Items.NAMESPACE, "black_god");
	private static final NamespacedKey SAVED = new NamespacedKey(Items.NAMESPACE, "black_god_saved_inventory");
	/** Players choosing a skill, until when. */
	private static final Map<UUID, Long> CHOOSING = new ConcurrentHashMap<>();
	/** Each Black God's halo, riding them. */
	private static final Map<UUID, ItemDisplay> HALOS = new ConcurrentHashMap<>();
	/** Each Black God's 3D robe, riding them. */
	private static final Map<UUID, ItemDisplay> ROBES = new ConcurrentHashMap<>();
	private static final float ROBE_SIZE = 1.05F;
	private static final String ROBE = "robe";
	private static final float HALO_SIZE = 1.9F;
	private static final float HALO_BEHIND = 0.42F;
	private static final float HALO_DROP = 0.3F;

	// --- items ---------------------------------------------------------------------

	private static ItemStack tagged(Material material, int amount, String id, Component name, List<Component> lore) {
		ItemStack stack = ItemStack.of(material, Math.max(1, amount));
		stack.editMeta(meta -> {
			meta.itemName(name);
			meta.lore(lore);
			meta.setRarity(ItemRarity.EPIC);
			meta.addItemFlags(ItemFlag.values());
			meta.getPersistentDataContainer().set(ITEM, PersistentDataType.STRING, id);
		});
		return stack;
	}

	private static Component line(String text, NamedTextColor colour) {
		return Component.text(text, colour).decoration(TextDecoration.ITALIC, false);
	}

	/** The item that turns Black God mode on (or off). */
	static ItemStack toggleItem(Player viewer) {
		ItemStack stack = tagged(Material.ECHO_SHARD, 1, TOGGLE, Component.text(Messages.get(viewer, "blackgod.item"), NamedTextColor.GOLD),
				List.of(line(Messages.get(viewer, "blackgod.item.desc"), NamedTextColor.GRAY)));
		stack.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
		return stack;
	}

	/** The Black God's weapon in slot 1, a hammer of white light (a 3D model): right-click lays out the skills. */
	private static ItemStack useItem(Player viewer) {
		ItemStack stack = tagged(Material.MACE, 1, USE, Component.text(Messages.get(viewer, "blackgod.use"), TextColor.color(0xFFF0C0)),
				List.of(line(Messages.get(viewer, "blackgod.use.desc"), NamedTextColor.GRAY)));
		stack.editMeta(meta -> {
			meta.setItemModel(new NamespacedKey(Items.NAMESPACE, "black_god_hammer"));
			meta.setUnbreakable(true);
		});
		return stack;
	}

	private static ItemStack leaveItem(Player viewer) {
		return tagged(Material.BARRIER, 1, LEAVE, Component.text(Messages.get(viewer, "blackgod.leave"), NamedTextColor.RED),
				List.of(line(Messages.get(viewer, "blackgod.leave.desc"), NamedTextColor.GRAY)));
	}

	private static ItemStack skillItem(int index) {
		Skill skill = SKILLS.get(index);
		return tagged(skill.icon(), skill.coins(), SKILL + index,
				Component.text((index + 1) + ". " + skill.name(), skill.colour()).decoration(TextDecoration.ITALIC, false),
				List.of(line(skill.sin() + " · 코인 " + skill.coins() + "개", NamedTextColor.GRAY),
						line(skill.summary(), NamedTextColor.DARK_GRAY)));
	}

	static String idOf(ItemStack stack) {
		if (stack == null || stack.isEmpty() || !stack.hasItemMeta()) {
			return null;
		}
		return stack.getItemMeta().getPersistentDataContainer().get(ITEM, PersistentDataType.STRING);
	}

	/** An item that only exists in Black God mode (everything tagged but the toggle item). */
	private static boolean modeItem(ItemStack stack) {
		String id = idOf(stack);
		return id != null && !id.equals(TOGGLE);
	}

	// --- the mode ------------------------------------------------------------------

	static boolean active(Player player) {
		return player.getPersistentDataContainer().has(ACTIVE, PersistentDataType.BYTE);
	}

	static void toggle(Player player) {
		if (active(player)) {
			leave(player);
		} else {
			enter(player);
		}
	}

	/** Things other features keep in the off hand for a moment; never worth saving. */
	private static boolean transient_(ItemStack stack) {
		return Items.isLaevateinIcon(stack) || Items.isVoiceIcon(stack) || modeItem(stack);
	}

	static void enter(Player player) {
		if (active(player)) {
			return;
		}
		PlayerInventory inventory = player.getInventory();
		ItemStack[] contents = inventory.getContents();
		ItemStack[] saved = new ItemStack[contents.length];
		for (int i = 0; i < contents.length; i++) {
			ItemStack stack = contents[i];
			saved[i] = stack == null || stack.isEmpty() || transient_(stack) ? ItemStack.empty() : stack.clone();
		}
		PersistentDataContainer data = player.getPersistentDataContainer();
		data.set(SAVED, PersistentDataType.BYTE_ARRAY, ItemStack.serializeItemsAsBytes(saved));
		data.set(ACTIVE, PersistentDataType.BYTE, (byte) 1);
		BlackGodRevive.reset(player);
		BlackGodState.enter(player);
		inventory.clear();
		giveHotbar(player);
		inventory.setHeldItemSlot(USE_SLOT);
		Location at = player.getLocation().add(0, 1, 0);
		player.getWorld().spawnParticle(Particle.DUST, at, 60, 0.6, 1.0, 0.6, 0.0, new Particle.DustOptions(GOLD, 1.6F));
		player.getWorld().spawnParticle(Particle.SQUID_INK, at, 30, 0.5, 0.8, 0.5, 0.02);
		player.getWorld().playSound(at, Sound.BLOCK_BELL_RESONATE, SoundCategory.PLAYERS, 1.2F, 0.5F);
		player.getWorld().playSound(at, Sound.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1.0F, 0.5F);
		player.showTitle(Title.title(Component.text(Messages.get(player, "blackgod.title"), TextColor.color(0xE8C860)),
				Component.text(Messages.get(player, "blackgod.on"), NamedTextColor.GRAY)));
	}

	static void leave(Player player) {
		if (!active(player)) {
			return;
		}
		CHOOSING.remove(player.getUniqueId());
		BlackGodState.leave(player);
		PlayerInventory inventory = player.getInventory();
		List<ItemStack> picked = new ArrayList<>();
		for (ItemStack stack : inventory.getContents()) {
			if (stack != null && !stack.isEmpty() && !transient_(stack)) {
				picked.add(stack.clone());
			}
		}
		PersistentDataContainer data = player.getPersistentDataContainer();
		byte[] bytes = data.get(SAVED, PersistentDataType.BYTE_ARRAY);
		ItemStack[] saved = bytes == null ? new ItemStack[0] : ItemStack.deserializeItemsFromBytes(bytes);
		inventory.clear();
		ItemStack[] contents = Arrays.copyOf(saved, inventory.getContents().length);
		for (int i = 0; i < contents.length; i++) {
			if (contents[i] != null && contents[i].isEmpty()) {
				contents[i] = null;
			}
		}
		inventory.setContents(contents);
		// Whatever was picked up meanwhile goes on top, or to the ground if there is no room.
		for (ItemStack stack : picked) {
			inventory.addItem(stack).values().forEach(rest -> player.getWorld().dropItem(player.getLocation(), rest));
		}
		data.remove(SAVED);
		data.remove(ACTIVE);
		keepFigure(player);
		Location at = player.getLocation().add(0, 1, 0);
		player.getWorld().spawnParticle(Particle.DUST, at, 30, 0.5, 0.8, 0.5, 0.0, new Particle.DustOptions(GOLD, 1.2F));
		player.getWorld().playSound(at, Sound.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 1.0F, 0.7F);
		player.sendActionBar(Component.text(Messages.get(player, "blackgod.off"), NamedTextColor.GRAY));
	}

	/** Takes off robe armour left over from before the robe was a 3D model. */
	private static void clearOldRobe(Player player) {
		PlayerInventory inventory = player.getInventory();
		if (ROBE.equals(idOf(inventory.getChestplate()))) {
			inventory.setChestplate(null);
		}
		if (ROBE.equals(idOf(inventory.getLeggings()))) {
			inventory.setLeggings(null);
		}
		if (ROBE.equals(idOf(inventory.getBoots()))) {
			inventory.setBoots(null);
		}
	}

	private static ItemStack model(String name) {
		ItemStack stack = ItemStack.of(Material.PAPER);
		stack.editMeta(meta -> meta.setItemModel(new NamespacedKey(Items.NAMESPACE, name)));
		return stack;
	}

	/** The display riding {@code player} from {@code map}, spawned (or remounted) if needed. */
	private static ItemDisplay ride(Map<UUID, ItemDisplay> map, Player player, String name) {
		ItemDisplay display = map.get(player.getUniqueId());
		if (display != null && display.isValid() && display.getVehicle() == player) {
			return display;
		}
		if (display != null) {
			display.remove();
		}
		// Spawned unturned and level: the transformation alone turns it (a rider keeps the rotation it was spawned with).
		Location at = player.getLocation().add(0, player.getHeight(), 0);
		at.setYaw(0);
		at.setPitch(0);
		display = player.getWorld().spawn(at, ItemDisplay.class, d -> {
			d.setPersistent(false);
			d.setItemStack(model(name));
			d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
			d.setInterpolationDuration(1);
		});
		player.addPassenger(display);
		map.put(player.getUniqueId(), display);
		return display;
	}

	private static void drop(Map<UUID, ItemDisplay> map, UUID id) {
		ItemDisplay display = map.remove(id);
		if (display != null) {
			display.remove();
		}
	}

	/**
	 * Keeps the Black God's figure on them, as in the art: the body hidden
	 * (Invisibility, no particles or icon), the 3D robe round it and the
	 * halo behind where the head was, its black disc in place of a face.
	 * Both ride the player, and their transformations turn them to the
	 * body's facing every tick (a rider's own rotation is not sent),
	 * smoothed over a tick.
	 */
	private static void keepFigure(Player player) {
		UUID id = player.getUniqueId();
		boolean wanted = active(player) && !player.isDead() && player.getGameMode() != org.bukkit.GameMode.SPECTATOR;
		if (!wanted) {
			drop(HALOS, id);
			drop(ROBES, id);
			PotionEffect hidden = player.getPotionEffect(PotionEffectType.INVISIBILITY);
			if (hidden != null && hidden.isInfinite() && !hidden.hasParticles()) {
				player.removePotionEffect(PotionEffectType.INVISIBILITY);
			}
			return;
		}
		if (!player.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
			player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, false, false, false));
		}
		ItemDisplay halo = ride(HALOS, player, "black_god_halo");
		ItemDisplay robe = ride(ROBES, player, "black_god_robe");
		halo.setBrightness(new Display.Brightness(15, 15));
		double yaw = Math.toRadians(player.getBodyYaw());
		Vector3f facing = new Vector3f((float) -Math.sin(yaw), 0, (float) Math.cos(yaw));
		Quaternionf turn = new Quaternionf().rotateY((float) -yaw);
		Vector3f haloAt = new Vector3f(facing).mul(-HALO_BEHIND).add(0, -HALO_DROP, 0);
		halo.setInterpolationDelay(0);
		halo.setTransformation(new Transformation(haloAt, turn, new Vector3f(HALO_SIZE, HALO_SIZE, HALO_SIZE), new Quaternionf()));
		// The robe model's feet are its bottom; the rider sits at the top of the player.
		// Squashed with the body when sneaking, so the eyes stay above the collar.
		float tall = (float) (ROBE_SIZE * player.getHeight() / 1.8);
		Vector3f robeAt = new Vector3f(0, (float) (-player.getHeight() + 0.5 * tall), 0);
		robe.setInterpolationDelay(0);
		robe.setTransformation(new Transformation(robeAt, new Quaternionf(turn), new Vector3f(ROBE_SIZE, tall, ROBE_SIZE),
				new Quaternionf()));
	}

	static void removeHalos() {
		HALOS.values().forEach(ItemDisplay::remove);
		HALOS.clear();
		ROBES.values().forEach(ItemDisplay::remove);
		ROBES.clear();
	}

	/** The mode's hotbar: "use skills" first, "leave" last. */
	private static void giveHotbar(Player player) {
		clearOldRobe(player);
		PlayerInventory inventory = player.getInventory();
		for (int slot = 0; slot < 9; slot++) {
			if (modeItem(inventory.getItem(slot))) {
				inventory.setItem(slot, null);
			}
		}
		List<ItemStack> displaced = new ArrayList<>();
		for (int slot : new int[] {USE_SLOT, LEAVE_SLOT}) {
			ItemStack stack = inventory.getItem(slot);
			if (stack != null && !stack.isEmpty() && !modeItem(stack)) {
				displaced.add(stack);
			}
		}
		inventory.setItem(USE_SLOT, useItem(player));
		inventory.setItem(LEAVE_SLOT, leaveItem(player));
		stash(player, displaced);
	}

	/** Puts items back in the inventory (after the hotbar is laid out), dropping what does not fit. */
	private static void stash(Player player, List<ItemStack> items) {
		for (ItemStack stack : items) {
			player.getInventory().addItem(stack).values().forEach(rest -> player.getWorld().dropItem(player.getLocation(), rest));
		}
	}

	/** Lays the nine skills along the hotbar; whatever else was there moves into the inventory. */
	private static void choose(Player player) {
		PlayerInventory inventory = player.getInventory();
		List<ItemStack> displaced = new ArrayList<>();
		for (int slot = 0; slot < 9; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack != null && !stack.isEmpty() && !modeItem(stack)) {
				displaced.add(stack);
			}
			inventory.setItem(slot, skillItem(slot));
		}
		stash(player, displaced);
		CHOOSING.put(player.getUniqueId(), CaduceusListener.now() + CHOOSE_TICKS);
		player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.0F, 0.8F);
		player.sendActionBar(Component.text(Messages.get(player, "blackgod.choose"), NamedTextColor.GOLD));
	}

	/** Puts the mode's hotbar back after choosing. */
	private static void stopChoosing(Player player) {
		CHOOSING.remove(player.getUniqueId());
		if (active(player)) {
			giveHotbar(player);
		}
	}

	/**
	 * Uses skill {@code index} (0-8): the great skills (7-9) play out in
	 * {@link BlackGodSkill}; the others are still to come (see
	 * docs/black_god.md) and only show their name for now.
	 */
	private static void useSkill(Player player, int index) {
		stopChoosing(player);
		if (BlackGodSkill.use(player, index)) {
			return;
		}
		Skill skill = SKILLS.get(index);
		player.showTitle(Title.title(Component.empty(), Component.text(skill.name(), skill.colour()),
				Title.Times.times(java.time.Duration.ofMillis(100), java.time.Duration.ofMillis(1200), java.time.Duration.ofMillis(300))));
		Location at = player.getLocation().add(0, 1, 0);
		Color colour = Color.fromRGB(skill.colour().value());
		player.getWorld().spawnParticle(Particle.DUST, at, 40, 0.6, 0.9, 0.6, 0.0, new Particle.DustOptions(colour, 1.4F));
		player.getWorld().playSound(at, Sound.BLOCK_BELL_USE, SoundCategory.PLAYERS, 1.0F, 0.6F);
	}

	/** Called every tick: a skill not chosen in 10 seconds is let go. */
	static void tick() {
		long now = CaduceusListener.now();
		for (Player player : Bukkit.getOnlinePlayers()) {
			if (active(player) || HALOS.containsKey(player.getUniqueId()) || ROBES.containsKey(player.getUniqueId())) {
				keepFigure(player);
			}
		}
		for (UUID id : List.copyOf(HALOS.keySet())) {
			if (Bukkit.getPlayer(id) == null) {
				drop(HALOS, id);
			}
		}
		for (UUID id : List.copyOf(ROBES.keySet())) {
			if (Bukkit.getPlayer(id) == null) {
				drop(ROBES, id);
			}
		}
		for (Map.Entry<UUID, Long> entry : Map.copyOf(CHOOSING).entrySet()) {
			Player player = Bukkit.getPlayer(entry.getKey());
			if (player == null) {
				CHOOSING.remove(entry.getKey());
			} else if (now >= entry.getValue()) {
				stopChoosing(player);
			}
		}
	}

	/** On disable: hotbars laid out for choosing go back (the mode itself stays saved on the player). */
	static void stopAll() {
		for (UUID id : Map.copyOf(CHOOSING).keySet()) {
			Player player = Bukkit.getPlayer(id);
			if (player != null) {
				stopChoosing(player);
			}
		}
		CHOOSING.clear();
		removeHalos();
	}

	// --- events ----------------------------------------------------------------------

	@EventHandler(priority = EventPriority.LOW)
	public void onUse(PlayerInteractEvent event) {
		if (event.getHand() != EquipmentSlot.HAND
				|| event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
			return;
		}
		String id = idOf(event.getItem());
		if (id == null) {
			return;
		}
		event.setCancelled(true);
		Player player = event.getPlayer();
		if (id.equals(TOGGLE)) {
			toggle(player);
		} else if (!active(player)) {
			player.getInventory().setItemInMainHand(null);
		} else if (id.equals(USE)) {
			choose(player);
		} else if (id.equals(LEAVE)) {
			leave(player);
		} else if (id.startsWith(SKILL) && CHOOSING.containsKey(player.getUniqueId())) {
			useSkill(player, Integer.parseInt(id.substring(SKILL.length())));
		}
	}

	/** While choosing, switching hotbar slots uses that slot's skill instead of moving. */
	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	public void onSwitch(PlayerItemHeldEvent event) {
		Player player = event.getPlayer();
		if (!CHOOSING.containsKey(player.getUniqueId()) || !active(player)) {
			return;
		}
		event.setCancelled(true);
		useSkill(player, event.getNewSlot());
	}

	@EventHandler(ignoreCancelled = true)
	public void onDrop(PlayerDropItemEvent event) {
		if (modeItem(event.getItemDrop().getItemStack())) {
			event.setCancelled(true);
		}
	}

	@EventHandler(ignoreCancelled = true)
	public void onClick(InventoryClickEvent event) {
		if (!(event.getWhoClicked() instanceof Player player) || !active(player)) {
			return;
		}
		boolean hotbarSwap = event.getClick() == ClickType.NUMBER_KEY
				&& modeItem(player.getInventory().getItem(event.getHotbarButton()));
		boolean offhandSwap = event.getClick() == ClickType.SWAP_OFFHAND && modeItem(player.getInventory().getItemInOffHand());
		if (hotbarSwap || offhandSwap || modeItem(event.getCurrentItem()) || modeItem(event.getCursor())) {
			event.setCancelled(true);
		}
	}

	@EventHandler(ignoreCancelled = true)
	public void onDrag(InventoryDragEvent event) {
		if (modeItem(event.getOldCursor())) {
			event.setCancelled(true);
		}
	}

	@EventHandler(ignoreCancelled = true)
	public void onSwapHands(PlayerSwapHandItemsEvent event) {
		if (modeItem(event.getMainHandItem()) || modeItem(event.getOffHandItem())) {
			event.setCancelled(true);
		}
	}

	/** The mode's items never drop; the saved inventory stays saved. */
	@EventHandler
	public void onDeath(PlayerDeathEvent event) {
		event.getDrops().removeIf(BlackGod::modeItem);
		CHOOSING.remove(event.getPlayer().getUniqueId());
	}

	@EventHandler
	public void onRespawn(PlayerRespawnEvent event) {
		Player player = event.getPlayer();
		Bukkit.getScheduler().runTask(IndexPrescriptPlugin.instance(), () -> {
			if (player.isOnline() && active(player)) {
				giveHotbar(player);
			}
		});
	}

	@EventHandler
	public void onJoin(PlayerJoinEvent event) {
		Player player = event.getPlayer();
		if (active(player)) {
			giveHotbar(player);
		} else {
			// Mode items left over from an older session are worth nothing outside the mode.
			for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
				if (modeItem(player.getInventory().getItem(slot))) {
					player.getInventory().setItem(slot, null);
				}
			}
		}
	}
}
