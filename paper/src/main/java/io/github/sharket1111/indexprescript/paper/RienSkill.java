package io.github.sharket1111.indexprescript.paper;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import io.papermc.paper.entity.TeleportFlag;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Rien's Caduceus skills on right-click (after the skill sheet and the video
 * the owner sent), chosen by [Hermes]:
 * - 3 or fewer: one of the three Gluttony skills (2/+2, two coins), spending 1 [Hermes]
 * - 4 to 6: one of the three Pride skills (2/+2, three coins), spending 2
 * - 7 or more: one of the three Gloom skills (3/+3, four coins), spending 2
 * Each family has a blunt, a pierce and a slash skill. Every coin forms a
 * random weapon, as Caduceus does; a weapon of the skill's own damage type
 * deals more (Gluttony +20%, Pride +30%, Gloom +50%), any other less
 * (-10%, -15%, -25%). The coins land at the moments measured from the video,
 * and give Poise and Sinking as the sheet lists.
 */
final class RienSkill {
	enum Kind {
		GLUTTONY_BLUNT(0, DamageKind.BLUNT, "무게를 실어 내려쳐, 몸을 무너뜨리고", new int[] {18, 52}, 80),
		GLUTTONY_PIERCE(0, DamageKind.PIERCE, "끝을 세워, 심지까지 밀어 넣고", new int[] {13, 38}, 60),
		GLUTTONY_SLASH(0, DamageKind.SLASH, "날을 눕혀, 숨결이 갈라지듯 잘라내고", new int[] {28, 54}, 80),
		PRIDE_BLUNT(1, DamageKind.BLUNT, "휘둘러 떨어트려, 땅과 하나로 만들어", new int[] {25, 45, 70}, 95),
		PRIDE_PIERCE(1, DamageKind.PIERCE, "점 하나를 겨눠, 안쪽에서 울리며", new int[] {19, 40, 64}, 85),
		PRIDE_SLASH(1, DamageKind.SLASH, "결을 낮게 깔아, 남은 것을 벗겨내고", new int[] {17, 38, 62}, 85),
		GLOOM_BLUNT(2, DamageKind.BLUNT, "소리를 부수고, 생각을 납작하게 짓이겨라", new int[] {23, 49, 72, 95}, 120),
		GLOOM_PIERCE(2, DamageKind.PIERCE, "고요의 중심을 찌르고, 기억을 꿰뚫어라", new int[] {46, 74, 95, 115}, 135),
		GLOOM_SLASH(2, DamageKind.SLASH, "날카롭게 벼린 비밀로, 형태를 베어내라", new int[] {42, 65, 82, 106}, 135);

		final int family;
		final DamageKind type;
		final String name;
		final int[] coins;
		final int length;

		Kind(int family, DamageKind type, String name, int[] coins, int length) {
			this.family = family;
			this.type = type;
			this.name = name;
			this.coins = coins;
			this.length = length;
		}

		SkillCard card() {
			return SkillCard.RIEN[ordinal()];
		}
	}

	private static final Map<UUID, RienSkill> ACTIVE = new ConcurrentHashMap<>();
	private static final double RANGE = 8.0;
	/** Damage of one coin, by family. */
	private static final double[] COIN_DAMAGE = {4.0, 4.5, 5.0};
	private static final double[] MATCH_BONUS = {0.20, 0.30, 0.50};
	private static final double[] MISMATCH_LOSS = {0.10, 0.15, 0.25};
	private static final Color[] FAMILY_COLOUR = {Color.fromRGB(0x7CCB2E), Color.fromRGB(0x3060D0), Color.fromRGB(0x30B0C8)};

	private final Player player;
	private final LivingEntity target;
	private final Kind kind;
	private final Location anchor;
	private final boolean hadAi;
	private Cinematic camera;
	private double cameraSide = Double.NaN;
	private CaduceusForm lastForm;
	private int tick;

	private RienSkill(Player player, LivingEntity target, Kind kind) {
		this.player = player;
		this.target = target;
		this.kind = kind;
		this.anchor = target.getLocation();
		this.hadAi = target.hasAI();
	}

	static boolean active(Player player) {
		return ACTIVE.containsKey(player.getUniqueId());
	}

	/** Right-click with Caduceus: the skill [Hermes] picks, at the looked-at foe (or the one to answer). */
	/** The tier of skills the [Hermes] stacks allow: 1-3 Gluttony, 4-6 Pride, 7+ Gloom (-1 if none). */
	private static int family(int hermes) {
		return hermes < 1 ? -1 : hermes <= 3 ? 0 : hermes <= 6 ? 1 : 2;
	}

	/** Uses {@code kind}, of the tier the [Hermes] stacks allow. */
	static void use(Player player, Kind kind) {
		if (active(player) || Furioso.active(player) || Clash.blocked(player)) {
			return;
		}
		int hermes = PrescriptTracker.hermes(player);
		if (family(hermes) != kind.family) {
			return;
		}
		LivingEntity target = Clash.answering(player) != null ? Clash.answering(player) : lookedAt(player);
		if (target == null) {
			Hud.message(player, Component.text(Messages.get(player, "renoir.no_target"), NamedTextColor.GRAY));
			return;
		}
		int cost = hermes <= 3 ? 1 : 2;
		if (!PrescriptTracker.spendHermes(player, cost)) {
			return;
		}
		Clash.request(player, target, kind.card(), outcome -> launch(player, outcome.target(), kind));
	}

	/** The skill menu: the three skills of the tier the [Hermes] stacks allow. */
	private static java.util.List<SkillMenu.Entry> entries(Player player) {
		java.util.List<SkillMenu.Entry> out = new java.util.ArrayList<>();
		int hermes = PrescriptTracker.hermes(player);
		int family = family(hermes);
		int n = 1;
		for (Kind kind : Kind.values()) {
			if (kind.family != Math.max(0, family)) {
				continue;
			}
			SkillCard card = kind.card();
			java.util.List<Component> lore = new java.util.ArrayList<>();
			lore.add(SkillMenu.line("위력 " + card.base() + " · 코인 +" + card.coinPower() + " ×" + card.coins(), NamedTextColor.GOLD));
			for (String l : MdGui.wrap(card.description(), 34)) {
				lore.add(SkillMenu.line(l, NamedTextColor.GRAY));
			}
			lore.add(SkillMenu.line("대행 [헤르메스] " + hermes + " (쓰면 " + (hermes <= 3 ? 1 : 2) + " 소모)", NamedTextColor.DARK_GRAY));
			String name = n++ + ". " + kind.name;
			String model = switch (kind.type) {
				case BLUNT -> "minecraft:mace";
				case PIERCE -> "minecraft:trident";
				default -> "minecraft:iron_sword";
			};
			out.add(family < 0 ? new SkillMenu.Entry(SkillMenu.locked(name, "대행 [헤르메스]가 없어요", lore), null)
					: new SkillMenu.Entry(SkillMenu.icon(model, 1, SkillMenu.line(name, card.colour()), lore), () -> use(player, kind)));
		}
		return out;
	}

	static void openMenu(Player player) {
		if (active(player) || Furioso.active(player)) {
			return;
		}
		if (Clash.blocked(player)) {
			Hud.message(player, Component.text("합이 진행 중입니다.", NamedTextColor.GRAY));
			return;
		}
		SkillMenu.open(player, "weapon", () -> entries(player), Component.text("카두케우스: 1~3 스킬 · 9 취소 ([헤르메스] "
				+ PrescriptTracker.hermes(player) + ")", NamedTextColor.GOLD));
	}

	private static void launch(Player player, LivingEntity target, Kind kind) {
		if (active(player) || !player.isOnline() || !Items.isCaduceus(player.getInventory().getItemInMainHand())) {
			return;
		}
		RienSkill skill = new RienSkill(player, target, kind);
		ACTIVE.put(player.getUniqueId(), skill);
		skill.open();
	}

	private static LivingEntity lookedAt(Player player) {
		if (player.getTargetEntity((int) RANGE) instanceof LivingEntity living && !living.isDead() && living != player
				&& !Cinematic.isActor(living)) {
			return living;
		}
		Vector eye = player.getEyeLocation().toVector();
		Vector look = player.getEyeLocation().getDirection();
		LivingEntity best = null;
		double bestDot = 0.93;
		for (Entity entity : player.getNearbyEntities(RANGE, RANGE, RANGE)) {
			if (!(entity instanceof LivingEntity living) || living.isDead() || Cinematic.isActor(living)
					|| entity instanceof org.bukkit.entity.ArmorStand stand && stand.isMarker()) {
				continue;
			}
			Vector to = living.getLocation().add(0, living.getHeight() * 0.5, 0).toVector().subtract(eye);
			double dot = to.clone().normalize().dot(look);
			if (to.length() <= RANGE && dot > bestDot && player.hasLineOfSight(living)) {
				best = living;
				bestDot = dot;
			}
		}
		return best;
	}

	static void tickAll() {
		ACTIVE.values().removeIf(skill -> {
			try {
				return !skill.tick();
			} catch (RuntimeException e) {
				IndexPrescriptPlugin.instance().getLogger().log(Level.WARNING, "Rien skill failed", e);
				skill.finish();
				return true;
			}
		});
	}

	static void endAll() {
		ACTIVE.values().forEach(RienSkill::finish);
		ACTIVE.clear();
	}

	private void open() {
		Animations.playSkill(player, "skill_rien_" + kind.name().toLowerCase(java.util.Locale.ROOT), this::actor);
		if (!(target instanceof Player)) {
			target.setAI(false);
		}
		camera = Cinematic.start(player, player.getLocation().add(0, 2, 0));
		boolean big = kind.family == 2;
		if (big) {
			TotalDamage.start(player);
		}
		SkillOverlay.show(player, big ? SkillOverlay.Size.BIG : SkillOverlay.Size.SMALL);
		long now = CaduceusListener.now();
		CombatState self = CombatState.of(player);
		// On use, as the sheet says.
		switch (kind.family) {
			case 0 -> self.addPoise(0, 1, now);
			case 1 -> self.addPoise(2, 0, now);
			default -> self.addPoise(2, 2, now);
		}
		Effects.aura(actor());
		World world = player.getWorld();
		world.playSound(player.getLocation(), Sound.ITEM_BUCKET_FILL_LAVA, SoundCategory.PLAYERS, 0.8F, 0.6F + kind.family * 0.2F);
	}

	private boolean tick() {
		if (!player.isOnline() || player.isDead() || !target.isValid() || target.isDead()
				|| !Items.isCaduceus(player.getInventory().getItemInMainHand())) {
			finish();
			return false;
		}
		hold();
		shoot();
		Effects.shin(actor(), tick);
		player.setFallDistance(0.0F);
		CaduceusListener.keepFormed(player, 30);
		for (int i = 0; i < kind.coins.length; i++) {
			if (tick == kind.coins[i] - 3) {
				dashBeside(i);
			}
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

	/** Before a coin: the wielder flashes to the foe's side, alternating sides like the original. */
	private void dashBeside(int coin) {
		if (Clash.destroyed(player, coin)) {
			return;
		}
		LivingEntity actor = actor();
		Vector from = actor.getLocation().toVector();
		Vector toward = target.getLocation().toVector().subtract(from).setY(0);
		if (toward.lengthSquared() < 1.0E-4) {
			toward = new Vector(1, 0, 0);
		}
		toward.normalize();
		Vector side = new Vector(-toward.getZ(), 0, toward.getX()).multiply(coin % 2 == 0 ? 1.2 : -1.2);
		Vector spot = target.getLocation().toVector().subtract(toward.clone().multiply(1.6)).add(side);
		Location destination = new Location(target.getWorld(), spot.getX(), target.getLocation().getY(), spot.getZ());
		destination.setDirection(target.getLocation().toVector().subtract(spot).setY(0));
		actor.teleport(destination);
		Effects.dashTrail(target.getWorld(), from, spot);
	}

	private void strike(int coin) {
		if (!Clash.coin(player, coin)) {
			return;
		}
		CaduceusForm form = CaduceusForm.random(lastForm);
		lastForm = form;
		ItemStack stack = player.getInventory().getItemInMainHand();
		if (Items.isCaduceus(stack)) {
			Items.setForm(stack, form);
		}
		if (camera != null) {
			camera.syncHeld();
			camera.jolt(coin == kind.coins.length - 1 ? 0.9 : 0.45);
		}
		LivingEntity actor = actor();
		actor.swingMainHand();
		boolean match = form.kind() == kind.type;
		double damage = COIN_DAMAGE[kind.family] * (match ? 1.0 + MATCH_BONUS[kind.family] : 1.0 - MISMATCH_LOSS[kind.family]);
		damage *= IndexMark.multiplier(player, target);
		Damage.deal(target, damage, player, true);
		IndexMark.strike(player, target);
		effects(coin);
		visuals(coin, form);
	}

	/** What each coin gives on hit, after the sheet. */
	private void effects(int coin) {
		long now = CaduceusListener.now();
		CombatState self = CombatState.of(player);
		CombatState other = CombatState.of(target);
		switch (kind) {
			case GLUTTONY_BLUNT -> {
				if (coin == 1) {
					self.addPoise(2, 0, now);
				}
			}
			case GLUTTONY_PIERCE -> self.addPoise(1, 0, now);
			case GLUTTONY_SLASH -> self.addPoise(coin == 0 ? 1 : 0, coin == 0 ? 0 : 1, now);
			case PRIDE_BLUNT -> {
				if (coin == 1) {
					other.addSinking(2, 0, now);
				} else if (coin == 2) {
					self.addPoise(0, 1, now);
					other.addSinking(0, 1, now);
				}
			}
			case PRIDE_PIERCE -> {
				if (coin == 0) {
					other.addSinking(2, 0, now);
				} else if (coin == 1) {
					other.addSinking(0, 1, now);
				} else {
					self.addPoise(0, 1, now);
					other.addSinking(0, 1, now);
				}
			}
			case PRIDE_SLASH -> {
				if (coin == 0) {
					other.addSinking(1, 0, now);
				} else if (coin == 1) {
					other.addSinking(2, 0, now);
				} else {
					self.addPoise(0, 1, now);
					other.addSinking(0, 1, now);
				}
			}
			default -> {
				// The Gloom skills: Sinking 2, Sinking count 2, Poise 2, Poise count 2.
				switch (coin) {
					case 0 -> other.addSinking(2, 0, now);
					case 1 -> other.addSinking(0, 2, now);
					case 2 -> self.addPoise(2, 0, now);
					default -> self.addPoise(0, 2, now);
				}
			}
		}
	}

	private void visuals(int coin, CaduceusForm form) {
		LivingEntity actor = actor();
		World world = target.getWorld();
		Location mid = target.getLocation().add(0, target.getHeight() * 0.6, 0);
		boolean last = coin == kind.coins.length - 1;
		switch (kind.type) {
			case BLUNT -> Effects.arc(actor, target, -150, 200, 75);
			case PIERCE -> Effects.slash(actor, target, 0, 3.0);
			default -> Effects.arc(actor, target, coin % 2 == 0 ? -100 : 100, coin % 2 == 0 ? 200 : -200, -10);
		}
		Effects.star(target, last ? 1.6 : 1.0);
		world.spawnParticle(Particle.DUST, mid, last ? 40 : 18, 0.5, 0.5, 0.5, 0.0,
				new Particle.DustOptions(FAMILY_COLOUR[kind.family], last ? 1.8F : 1.3F));
		if (last && kind.family == 2) {
			Effects.finalCut(actor, target);
			Effects.flash(target);
		} else if (last) {
			Effects.burst(target);
		}
		Effects.form(actor);
		world.playSound(mid, Sound.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 0.9F, 0.8F + coin * 0.1F);
		if (form.kind() == kind.type) {
			world.playSound(mid, Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.7F, 1.4F);
		}
	}

	/** A side view square to the line between the two, pulling in for the last coin. */
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
		double distance = tick >= lastCoin - 4 && tick < lastCoin + 10 ? 3.6 : 4.6;
		camera.frame(Cinematic.orbit(focus, cameraSide, distance, 1.4), focus, 0.0);
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

	private void finish() {
		Animations.stopSkill(player);
		if (kind.family == 2) {
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
	}
}
