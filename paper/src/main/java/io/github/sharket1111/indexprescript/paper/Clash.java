package io.github.sharket1111.indexprescript.paper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Display;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import io.papermc.paper.entity.TeleportFlag;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;

/**
 * Coins and clashes, as in Limbus Company.
 *
 * Every weapon skill goes through {@link #request}. Beside its user a panel
 * shows the skill's coins, clash power, name and description.
 *
 * Clash: if the target is a player holding one of the plugin's weapons (or
 * the Black God), they get {@link #WINDOW} ticks (2 s) to answer with a skill.
 * Every round both flip their unbroken coins (heads 50% + Sanity%), power =
 * base + heads x coin power, and the lower side loses its last unbroken coin
 * (a tie loses none), until one side has none left.
 * Sanity: the winner gains 10 (+20% per round from the second), the loser
 * loses 2 (+5% per round from the second).
 * - A plain coin lost is destroyed (it cracks, splits and shatters).
 * - An unbreakable coin (red, or green for executions) is only broken: it
 *   leaves the clash but still strikes, with half the coin power. A green
 *   coin on the winning side destroys a red coin outright.
 * The winner then attacks with its unbroken and broken coins; afterwards the
 * loser strikes with its broken unbreakable coins, if it has any.
 *
 * Attacking: the skill plays its coins in order and calls {@link #coin} at
 * each; a destroyed coin's attack is skipped. Each coin is flipped as it
 * strikes, and the damage of its hits follows the power so far over the
 * power expected so far (half the coins heads), 0.5x to 1.8x (see
 * {@link #scale}). Counters and guards never clash.
 */
final class Clash {
	static final int WINDOW = 40;
	/** A clash dummy answers this many ticks into the window. */
	private static final int DUMMY_ANSWER = 12;
	private static final int SPIN = 8;
	private static final int ROUND = 16;
	private static final int LINGER = 30;
	private static final int MAX_ROUNDS = 40;
	/** The shattering: four frames of two ticks. */
	private static final int BREAK_TICKS = 8;
	private static final int UNBROKEN = 0;
	private static final int BROKEN = 1;
	private static final int DESTROYED = 2;
	private static final Map<UUID, Duel> DUELS = new ConcurrentHashMap<>();
	/** Each player's skill playing its coins now. */
	private static final Map<UUID, Side> RUNS = new ConcurrentHashMap<>();
	private static final List<Lingering> LINGERING = new ArrayList<>();

	/** Where the skill goes. */
	record Outcome(LivingEntity target) {
	}

	private record Lingering(TextDisplay display, long removeAt, Side side) {
	}

	private static final class Side {
		/** The one using the skill: a player, or a clash dummy. */
		final LivingEntity player;
		final SkillCard card;
		final Consumer<Outcome> start;
		LivingEntity target;
		Location anchor;
		final int[] state;
		/** Per coin: 0 not flipped, 1 heads, 2 tails. */
		final int[] faces;
		int power = -1;
		boolean spinning;
		Boolean won;
		TextDisplay panel;
		/** The side view while clashing (Java players). */
		Cinematic camera;
		boolean retired;
		int shattering = -1;
		long shatteredAt = Long.MIN_VALUE / 2;
		// While attacking:
		long until;
		int runPower;
		double runExpected;
		double scale = 1.0;
		final boolean[] struck;
		/** Coins re-used while attacking (Furioso's second coin), shown after the others: {coin index, face}. */
		final java.util.List<int[]> reused = new java.util.ArrayList<>();

		Side(LivingEntity player, SkillCard card, LivingEntity target, Consumer<Outcome> start) {
			this.player = player;
			this.card = card;
			this.target = target;
			this.start = start;
			this.state = new int[card.coins()];
			this.faces = new int[card.coins()];
			this.struck = new boolean[card.coins()];
		}

		int unbroken() {
			int count = 0;
			for (int s : state) {
				count += s == UNBROKEN ? 1 : 0;
			}
			return count;
		}

		boolean hasBroken() {
			for (int s : state) {
				if (s == BROKEN) {
					return true;
				}
			}
			return false;
		}

		boolean hasUnbrokenGreen() {
			for (int i = 0; i < state.length; i++) {
				if (state[i] == UNBROKEN && card.type(i) == SkillCard.COIN_EXECUTION) {
					return true;
				}
			}
			return false;
		}

		/** A clash round: every unbroken coin flipped at once. */
		void flipUnbroken() {
			double chance = Sanity.headsChance(player);
			int heads = 0;
			for (int i = 0; i < state.length; i++) {
				if (state[i] == UNBROKEN) {
					faces[i] = ThreadLocalRandom.current().nextDouble() < chance ? 1 : 2;
					heads += faces[i] == 1 ? 1 : 0;
				}
			}
			power = card.base() + heads * card.coinPower() + (player instanceof Player p ? MdGifts.clashPower(p, target) : 0);
			spinning = false;
		}

		boolean shatteringNow() {
			return CaduceusListener.now() - shatteredAt < BREAK_TICKS;
		}

		int coinPower(int index) {
			return state[index] == BROKEN ? Math.max(1, card.coinPower() / 2) : card.coinPower();
		}
	}

	private static final class Duel {
		final Side attacker;
		final Player defender;
		/** A clash dummy that will answer on its own. */
		LivingEntity dummy;
		Side answer;
		boolean waiting;
		int tick;
		int rounds;
		TextDisplay counter;

		Duel(Side attacker, Player defender) {
			this.attacker = attacker;
			this.defender = defender;
			this.waiting = defender != null;
		}
	}

	private Clash() {
	}

	private static boolean present(LivingEntity entity) {
		return entity != null && entity.isValid() && !entity.isDead() && (!(entity instanceof Player player) || player.isOnline());
	}

	/** Where a unit's effects belong: a player's stand-in while a camera runs. */
	private static LivingEntity body(LivingEntity entity) {
		return entity instanceof Player player ? Cinematic.body(player) : entity;
	}

	private static void tell(LivingEntity entity, Component message) {
		if (entity instanceof Player player) {
			Hud.message(player, message);
		}
	}

	// --- asking ----------------------------------------------------------------------------

	/**
	 * A skill wants to go off. {@code start} runs when it does (at once, or
	 * after a clash it won, or after a lost one if it kept broken unbreakable
	 * coins), with its target. Returns false if the user is busy clashing.
	 */
	static boolean request(Player user, LivingEntity target, SkillCard card, Consumer<Outcome> start) {
		if (MimicrySkill.sealed(user)) {
			// 대절단-세로 destroyed a combat page: no skills this scene.
			Hud.message(user, Component.text("전투 책장이 파괴되어 스킬을 쓸 수 없습니다.", NamedTextColor.RED));
			return false;
		}
		Duel asked = DUELS.get(user.getUniqueId());
		if (asked != null && asked.waiting && asked.defender == user) {
			if (!card.clashes()) {
				return false;
			}
			// The answer: this skill meets the one aimed at the user.
			asked.answer = new Side(user, card, asked.attacker.player, start);
			asked.answer.panel = panel(user);
			asked.waiting = false;
			asked.tick = 0;
			beginClash(asked);
			draw(asked.answer);
			return true;
		}
		if (asked != null) {
			Hud.message(user, Component.text("합이 진행 중입니다.", NamedTextColor.GRAY));
			return false;
		}
		Side side = new Side(user, card, target, start);
		side.panel = panel(user);
		Player defender = card.clashes() && target instanceof Player player && canAnswer(player) ? player : null;
		if (defender == null && card.clashes() && answers(target) && !MimicrySkill.sealed(target) && !DUELS.containsKey(target.getUniqueId())
				&& !RUNS.containsKey(target.getUniqueId())) {
			// A clash dummy answers with its fixed skill a moment later.
			Duel duel = new Duel(side, null);
			duel.dummy = target;
			duel.waiting = true;
			DUELS.put(user.getUniqueId(), duel);
			DUELS.put(target.getUniqueId(), duel);
			draw(side);
			return true;
		}
		if (defender == null) {
			// Unopposed: straight to the attack, the coins flipped as they strike.
			launch(side, target);
			return true;
		}
		Duel duel = new Duel(side, defender);
		DUELS.put(user.getUniqueId(), duel);
		DUELS.put(defender.getUniqueId(), duel);
		defender.showTitle(Title.title(Component.text("합!", NamedTextColor.GOLD).decorate(TextDecoration.BOLD),
				Component.text(user.getName() + " — 2초 안에 스킬을 쓰면 합을 겨룹니다", NamedTextColor.YELLOW),
				Title.Times.times(Duration.ZERO, Duration.ofMillis(1600), Duration.ofMillis(300))));
		defender.playSound(defender.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.PLAYERS, 1.0F, 1.6F);
		draw(side);
		return true;
	}

	/** Units that answer a skill on their own: clash dummies, and Mirror Dungeon enemies with a skill ready. */
	private static boolean answers(LivingEntity target) {
		return ClashDummy.is(target) || target != null && MdFoes.canAnswer(target);
	}

	/** The skill card {@code unit} is using now (its attack or clash), or null. */
	static SkillCard cardOf(LivingEntity unit) {
		Side side = RUNS.get(unit.getUniqueId());
		if (side != null) {
			return side.card;
		}
		Duel duel = DUELS.get(unit.getUniqueId());
		if (duel != null) {
			return duel.attacker.player == unit ? duel.attacker.card : duel.answer != null ? duel.answer.card : null;
		}
		return null;
	}

	/**
	 * A skill from a unit that is not a player (a Mirror Dungeon enemy) aimed at
	 * {@code target}: a target holding one of the plugin's weapons gets the
	 * usual 2 seconds to answer and clash; otherwise it goes off unopposed.
	 */
	static boolean requestFrom(LivingEntity user, Player target, SkillCard card, Consumer<Outcome> start) {
		if (DUELS.containsKey(user.getUniqueId()) || RUNS.containsKey(user.getUniqueId())) {
			return false;
		}
		Side side = new Side(user, card, target, start);
		side.panel = panel(user);
		if (card.clashes() && canAnswer(target)) {
			Duel duel = new Duel(side, target);
			DUELS.put(user.getUniqueId(), duel);
			DUELS.put(target.getUniqueId(), duel);
			Component name = user.customName() != null ? user.customName() : Component.text(user.getName());
			target.showTitle(Title.title(Component.text("합!", NamedTextColor.GOLD).decorate(TextDecoration.BOLD),
					Component.text("2초 안에 스킬을 쓰면 [" + card.name() + "]와 합을 겨룹니다", NamedTextColor.YELLOW),
					Title.Times.times(Duration.ZERO, Duration.ofMillis(1600), Duration.ofMillis(300))));
			target.sendActionBar(name.append(Component.text(" 의 [" + card.name() + "]", NamedTextColor.GOLD)));
			target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.PLAYERS, 1.0F, 1.6F);
			draw(side);
			return true;
		}
		draw(side);
		launch(side, target);
		return true;
	}

	/** The player clashing or answering one now, or about to. */
	static boolean busy(org.bukkit.entity.Entity unit) {
		return DUELS.containsKey(unit.getUniqueId());
	}

	/** Busy with a clash in a way that keeps them from starting a skill (answering one is allowed). */
	static boolean blocked(Player player) {
		return busy(player) && answering(player) == null;
	}

	/** Who aimed a skill at {@code player} that they may answer now, or null. */
	static LivingEntity answering(Player player) {
		Duel duel = DUELS.get(player.getUniqueId());
		return duel != null && duel.waiting && duel.defender == player ? duel.attacker.player : null;
	}

	/**
	 * The skill reaches coin {@code index} (0 = the first): flips it, if not
	 * yet, and sets the damage scale for its hits. Returns false if that coin
	 * was destroyed in a clash, and its attack must be skipped.
	 */
	static boolean coin(LivingEntity player, int index) {
		Side side = RUNS.get(player.getUniqueId());
		if (side == null || index < 0 || index >= side.state.length) {
			return true;
		}
		if (side.state[index] == DESTROYED) {
			return false;
		}
		if (!side.struck[index]) {
			side.struck[index] = true;
			boolean heads = ThreadLocalRandom.current().nextDouble() < Sanity.headsChance(player);
			side.faces[index] = heads ? 1 : 2;
			int coinPower = side.coinPower(index);
			side.runPower += heads ? coinPower : 0;
			side.runExpected += coinPower * 0.5;
			side.power = side.runPower;
			side.scale = Math.max(0.5, Math.min(1.8, side.runPower / Math.max(1.0, side.runExpected)));
			Location at = body(player).getLocation().add(0, 1.6, 0);
			at.getWorld().playSound(at, heads ? Sound.BLOCK_NOTE_BLOCK_CHIME : Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.PLAYERS, 0.7F,
					heads ? 1.6F : 0.8F);
			draw(side);
		}
		return true;
	}

	/** Whether the player's skill lost coin {@code index} for good in a clash (it shows nothing of it). */
	static boolean destroyed(LivingEntity player, int index) {
		Side side = RUNS.get(player.getUniqueId());
		return side != null && index >= 0 && index < side.state.length && side.state[index] == DESTROYED;
	}

	/**
	 * Re-uses coin {@code index} (e.g. Furioso's second coin, up to seven
	 * times): it is flipped again, its power added again, and one more coin
	 * shows on the panel. False if that coin was destroyed in a clash.
	 */
	static boolean reuse(LivingEntity player, int index) {
		Side side = RUNS.get(player.getUniqueId());
		if (side == null || index < 0 || index >= side.state.length) {
			return true;
		}
		if (side.state[index] == DESTROYED) {
			return false;
		}
		boolean heads = ThreadLocalRandom.current().nextDouble() < Sanity.headsChance(player);
		int coinPower = side.coinPower(index);
		side.runPower += heads ? coinPower : 0;
		side.runExpected += coinPower * 0.5;
		side.power = side.runPower;
		side.scale = Math.max(0.5, Math.min(1.8, side.runPower / Math.max(1.0, side.runExpected)));
		side.reused.add(new int[] {index, heads ? 1 : 2});
		Location at = body(player).getLocation().add(0, 1.6, 0);
		at.getWorld().playSound(at, heads ? Sound.BLOCK_NOTE_BLOCK_CHIME : Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.PLAYERS, 0.7F,
				heads ? 1.6F + side.reused.size() * 0.05F : 0.8F);
		draw(side);
		return true;
	}

	/** The damage scale of the coin the player's skill is striking with (1 when none). */
	static double scale(org.bukkit.entity.Entity player) {
		Side side = RUNS.get(player.getUniqueId());
		return side == null || CaduceusListener.now() > side.until ? 1.0 : side.scale;
	}

	/** Whether coin {@code index} of the skill {@code unit} is striking with came up heads. */
	static boolean heads(LivingEntity unit, int index) {
		Side side = RUNS.get(unit.getUniqueId());
		return side != null && index >= 0 && index < side.faces.length && side.faces[index] == 1;
	}

	/** The power of the skill {@code unit} is striking with, so far (0 when none). */
	static int power(LivingEntity unit) {
		Side side = RUNS.get(unit.getUniqueId());
		return side == null ? 0 : side.runPower;
	}

	private static boolean canAnswer(Player player) {
		if (!player.isOnline() || player.isDead() || player.getGameMode() == GameMode.SPECTATOR || DUELS.containsKey(player.getUniqueId())) {
			return false;
		}
		if (LaevateinSkill.active(player) || RenoirSkill.active(player) || Furioso.active(player) || BlackGodSkill.active(player)
				|| RienSkill.active(player) || MimicrySkill.active(player) || MimicrySkill.sealed(player)
				|| Laevatein.busy(player) || BlackGodRevive.reviving(player)) {
			return false;
		}
		return Items.isWeapon(player.getInventory().getItemInMainHand()) || BlackGod.active(player);
	}

	// --- every tick ------------------------------------------------------------------------

	static void tick() {
		long now = CaduceusListener.now();
		for (Duel duel : List.copyOf(new LinkedHashSet<>(DUELS.values()))) {
			try {
				tick(duel);
			} catch (RuntimeException e) {
				IndexPrescriptPlugin.instance().getLogger().log(Level.WARNING, "Clash failed", e);
				end(duel);
				retire(duel.attacker);
				if (duel.answer != null) {
					retire(duel.answer);
				}
			}
		}
		Iterator<Map.Entry<UUID, Side>> runs = RUNS.entrySet().iterator();
		while (runs.hasNext()) {
			Side side = runs.next().getValue();
			if (now >= side.until || !present(side.player)) {
				runs.remove();
				retire(side);
			} else {
				follow(side);
				if (side.shatteringNow()) {
					draw(side);
				}
			}
		}
		synchronized (LINGERING) {
			Iterator<Lingering> it = LINGERING.iterator();
			while (it.hasNext()) {
				Lingering lingering = it.next();
				if (now >= lingering.removeAt() || !lingering.display().isValid()) {
					lingering.display().remove();
					it.remove();
				} else if (lingering.side() != null && lingering.side().shatteringNow()) {
					draw(lingering.side());
				}
			}
		}
	}

	private static void tick(Duel duel) {
		Side a = duel.attacker;
		if (!present(a.player)) {
			if (duel.answer != null && present(duel.answer.player)) {
				win(duel, duel.answer, a);
			} else {
				end(duel);
				retire(a);
				endCamera(a);
				if (duel.answer != null) {
					retire(duel.answer);
					endCamera(duel.answer);
				}
			}
			return;
		}
		follow(a);
		if (duel.waiting && duel.dummy != null) {
			duel.tick++;
			LivingEntity dummy = duel.dummy;
			if (!present(dummy)) {
				end(duel);
				launch(a, a.target);
			} else if (duel.tick >= DUMMY_ANSWER) {
				duel.answer = ClashDummy.is(dummy) ? new Side(dummy, ClashDummy.card(dummy), a.player, outcome -> ClashDummy.attack(dummy, outcome.target()))
						: new Side(dummy, MdFoes.answerCard(dummy), a.player, outcome -> MdFoes.attack(dummy, outcome.target()));
				duel.answer.panel = panel(dummy);
				duel.waiting = false;
				duel.tick = 0;
				beginClash(duel);
				draw(duel.answer);
			}
			return;
		}
		if (duel.waiting) {
			duel.tick++;
			Player d = duel.defender;
			if (d.isOnline() && duel.tick % 10 == 0) {
				int left = (WINDOW - duel.tick + 19) / 20;
				Hud.message(d, Component.text("합! " + left + "초 안에 스킬을 쓰면 " + a.player.getName() + "의 [" + a.card.name() + "]와 합을 겨룹니다",
						NamedTextColor.GOLD));
			}
			if (duel.tick >= WINDOW || !d.isOnline() || d.isDead()) {
				// No answer: the skill goes off unopposed.
				end(duel);
				launch(a, a.target);
			}
			return;
		}
		Side b = duel.answer;
		if (!present(b.player)) {
			win(duel, a, b);
			return;
		}
		follow(b);
		hold(a);
		hold(b);
		frame(a, b);
		round(duel, a, b);
		for (Side side : new Side[] {a, b}) {
			if (side.shatteringNow()) {
				draw(side);
			}
		}
	}

	// --- the clash ---------------------------------------------------------------------------

	private static void beginClash(Duel duel) {
		Side a = duel.attacker;
		Side b = duel.answer;
		a.target = b.player;
		a.anchor = a.player.getLocation();
		b.anchor = b.player.getLocation();
		for (Side side : new Side[] {a, b}) {
			if (side.player instanceof Player viewer) viewer.showTitle(Title.title(Component.text("합", NamedTextColor.GOLD).decorate(TextDecoration.BOLD),
					Component.text(a.card.name() + "  vs  " + b.card.name(), NamedTextColor.GRAY),
					Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(300))));
		}
		Location mid = midpoint(a, b);
		mid.getWorld().playSound(mid, Sound.ITEM_TRIDENT_HIT, SoundCategory.PLAYERS, 1.2F, 0.8F);
		mid.getWorld().playSound(mid, Sound.BLOCK_BELL_USE, SoundCategory.PLAYERS, 1.0F, 1.2F);
		// Both watch the clash from the side, with the skill screen's edges.
		for (Side side : new Side[] {a, b}) {
			if (side.player instanceof Player viewer) {
				side.camera = Cinematic.start(viewer, mid.clone().add(0, 1, 0));
				SkillOverlay.show(viewer, SkillOverlay.Size.SMALL);
			}
		}
		frame(a, b);
	}

	/** The clash's camera: square to the line between the two, both in view, shaking as coins break. */
	private static void frame(Side a, Side b) {
		Vector from = body(a.player).getLocation().toVector();
		Vector to = body(b.player).getLocation().toVector();
		Vector focus = from.clone().midpoint(to).add(new Vector(0, 1.1, 0));
		double angle = Math.atan2(to.getZ() - from.getZ(), to.getX() - from.getX()) + Math.PI / 2;
		double distance = Math.max(4.5, from.distance(to) * 0.9 + 2.5);
		Vector position = Cinematic.orbit(focus, angle, distance, 1.4);
		double shake = a.shatteringNow() || b.shatteringNow() ? 0.25 : 0.0;
		for (Side side : new Side[] {a, b}) {
			if (side.camera != null) {
				side.camera.frame(position, focus, shake);
			}
		}
	}

	private static void endCamera(Side side) {
		if (side.camera != null) {
			side.camera.end();
			side.camera = null;
		}
	}

	private static void round(Duel duel, Side a, Side b) {
		int t = duel.tick++;
		if (t == 0) {
			a.spinning = true;
			b.spinning = true;
			a.won = null;
			b.won = null;
		}
		if (t < SPIN) {
			if (t % 2 == 0) {
				draw(a);
				draw(b);
				coinSound(a.player);
			}
			return;
		}
		if (t == SPIN) {
			a.flipUnbroken();
			b.flipUnbroken();
			duel.rounds++;
			Location mid = midpoint(a, b);
			showCount(duel, mid.clone().add(0, 1.6, 0));
			mid.getWorld().spawnParticle(Particle.CRIT, mid, 20, 0.3, 0.3, 0.3, 0.4);
			mid.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, mid, 12, 0.2, 0.2, 0.2, 0.2);
			mid.getWorld().playSound(mid, Sound.ITEM_SHIELD_BLOCK, SoundCategory.PLAYERS, 1.2F, 0.7F + ThreadLocalRandom.current().nextFloat() * 0.4F);
			if (a.power != b.power) {
				Side loser = a.power < b.power ? a : b;
				Side winner = loser == a ? b : a;
				winner.won = true;
				loser.won = false;
				loseCoin(loser, winner);
			}
			draw(a);
			draw(b);
			return;
		}
		if (t >= ROUND) {
			if (a.unbroken() == 0 || b.unbroken() == 0 || duel.rounds >= MAX_ROUNDS) {
				Side winner = a.unbroken() == b.unbroken() ? (a.power >= b.power ? a : b) : a.unbroken() > b.unbroken() ? a : b;
				win(duel, winner, winner == a ? b : a);
				return;
			}
			duel.tick = 0;
		}
	}

	/** The loser of a round loses its last unbroken coin: destroyed, or only broken if unbreakable. */
	private static void loseCoin(Side loser, Side winner) {
		int index = -1;
		for (int i = loser.state.length - 1; i >= 0; i--) {
			if (loser.state[i] == UNBROKEN) {
				index = i;
				break;
			}
		}
		if (index < 0) {
			return;
		}
		char type = loser.card.type(index);
		Location at = body(loser.player).getLocation().add(0, 1.2, 0);
		boolean destroyed = type == SkillCard.COIN_PLAIN || type == SkillCard.COIN_UNBREAKABLE && winner.hasUnbrokenGreen();
		if (destroyed) {
			loser.state[index] = DESTROYED;
			loser.shattering = index;
			loser.shatteredAt = CaduceusListener.now();
			at.getWorld().spawnParticle(Particle.DUST, at, 10, 0.2, 0.2, 0.2, 0.0, new Particle.DustOptions(Color.fromRGB(0xC02020), 1.2F));
			at.getWorld().playSound(at, Sound.ENTITY_ITEM_BREAK, SoundCategory.PLAYERS, 1.0F, 1.2F);
			if (type == SkillCard.COIN_UNBREAKABLE) {
				tell(loser.player, Component.text("초록 코인에 파괴 불가 코인이 완전히 파괴되었다!", NamedTextColor.RED));
			}
		} else {
			// Unbreakable: out of the clash, but it will still strike, weaker.
			loser.state[index] = BROKEN;
			at.getWorld().playSound(at, Sound.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 0.8F, 1.4F);
			at.getWorld().spawnParticle(Particle.DUST, at, 8, 0.2, 0.2, 0.2, 0.0,
					new Particle.DustOptions(type == SkillCard.COIN_EXECUTION ? Color.fromRGB(0x70D030) : Color.fromRGB(0xD82020), 1.2F));
		}
	}

	private static void win(Duel duel, Side winner, Side loser) {
		MdFoes.clashResult(winner.player, loser.player);
		int rounds = Math.max(1, duel.rounds);
		// The original: a win gives 10, 20% more per round from the second; a loss takes 2, 5% more per round.
		int gain = (int) Math.round(10 * (1 + 0.2 * (rounds - 1)));
		int loss = (int) Math.round(2 * (1 + 0.05 * (rounds - 1)));
		if (winner.player instanceof Player grit && Matthias.superGrit(grit)) {
			// [Super Grit]: rounds x 5 more (up to 15).
			gain += Math.min(15, rounds * 5);
		}
		Sanity.add(winner.player, gain);
		Sanity.lose(loser.player, loss);
		Sanity.fought(winner.player);
		Sanity.fought(loser.player);
		tell(winner.player, Component.text("합 승리! (" + rounds + "합) 정신력 +" + gain, NamedTextColor.GOLD));
		tell(loser.player, Component.text("합 패배 (" + rounds + "합) 정신력 -" + loss, NamedTextColor.RED));
		Location at = body(loser.player).getLocation().add(0, 1, 0);
		at.getWorld().playSound(at, Sound.ITEM_SHIELD_BREAK, SoundCategory.PLAYERS, 1.2F, 0.8F);
		at.getWorld().spawnParticle(Particle.EXPLOSION, at, 1);
		end(duel);
		endCamera(winner);
		endCamera(loser);
		if (!loser.hasBroken() && loser.player instanceof Player viewer) {
			SkillOverlay.hide(viewer);
		}
		if (present(loser.player)) {
			launch(winner, loser.player);
		} else {
			retire(winner);
		}
		// The loser's broken unbreakable coins strike after the winner's attack.
		if (loser.hasBroken() && present(loser.player)) {
			reset(loser);
			draw(loser);
			Bukkit.getScheduler().runTaskLater(IndexPrescriptPlugin.instance(), () -> {
				if (present(loser.player) && present(winner.player) && !busy(loser.player)) {
					launch(loser, winner.player);
				} else {
					retire(loser);
				}
			}, winner.card.ticks() + 10L);
		} else {
			retire(loser);
		}
	}

	/** Clears the clash's flips before the attack flips the coins one by one. */
	private static void reset(Side side) {
		java.util.Arrays.fill(side.faces, 0);
		java.util.Arrays.fill(side.struck, false);
		side.reused.clear();
		side.power = -1;
		side.spinning = false;
		side.won = null;
	}

	private static void launch(Side side, LivingEntity target) {
		if (target == null || !target.isValid() || target.isDead() || !present(side.player)) {
			retire(side);
			return;
		}
		reset(side);
		side.until = CaduceusListener.now() + side.card.ticks() + 10;
		side.runPower = side.card.base();
		side.runExpected = side.card.base();
		side.scale = 1.0;
		Side before = RUNS.put(side.player.getUniqueId(), side);
		if (before != null && before != side) {
			retire(before);
		}
		draw(side);
		side.start.accept(new Outcome(target));
	}

	private static void end(Duel duel) {
		DUELS.values().removeIf(d -> d == duel);
		if (duel.counter != null) {
			synchronized (LINGERING) {
				LINGERING.add(new Lingering(duel.counter, CaduceusListener.now() + LINGER, null));
			}
		}
	}

	private static void retire(Side side) {
		endCamera(side);
		if (side.retired) {
			return;
		}
		side.retired = true;
		if (side.panel != null) {
			synchronized (LINGERING) {
				LINGERING.add(new Lingering(side.panel, CaduceusListener.now() + LINGER, side));
			}
		}
	}

	static void endAll() {
		for (Duel duel : new LinkedHashSet<>(DUELS.values())) {
			end(duel);
			retire(duel.attacker);
			if (duel.answer != null) {
				retire(duel.answer);
			}
		}
		DUELS.clear();
		RUNS.values().forEach(Clash::retire);
		RUNS.clear();
		synchronized (LINGERING) {
			LINGERING.forEach(l -> l.display().remove());
			LINGERING.clear();
		}
	}

	// --- the panel ---------------------------------------------------------------------------

	private static TextDisplay panel(LivingEntity player) {
		return player.getWorld().spawn(sideOf(body(player)), TextDisplay.class, d -> {
			d.setPersistent(false);
			d.setBillboard(Display.Billboard.CENTER);
			d.setLineWidth(240);
			d.setAlignment(TextDisplay.TextAlignment.LEFT);
			d.setShadowed(true);
			d.setTeleportDuration(2);
			d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
			d.setBrightness(new Display.Brightness(15, 15));
			d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.6F, 0.6F, 0.6F), new Quaternionf()));
		});
	}

	/** Beside the player, on their right, about head height. */
	private static Location sideOf(LivingEntity body) {
		Location at = body.getLocation();
		Vector forward = at.getDirection().setY(0);
		if (forward.lengthSquared() < 1.0E-4) {
			forward = new Vector(0, 0, 1);
		}
		forward.normalize();
		Vector right = new Vector(-forward.getZ(), 0, forward.getX());
		Location side = at.clone().add(right.multiply(1.3)).add(0, 1.7, 0);
		side.setYaw(0);
		side.setPitch(0);
		return side;
	}

	private static void follow(Side side) {
		if (side.panel != null && side.panel.isValid()) {
			Location want = sideOf(body(side.player));
			if (want.getWorld() == side.panel.getWorld() && want.distanceSquared(side.panel.getLocation()) > 0.0025) {
				side.panel.teleport(want);
			}
		}
	}

	// The pack's "clash" font (tools/clash_font.py): coins, the badge, big digits, the banner, Sanity circles, spaces.
	private static final Key FONT = Key.key(Items.NAMESPACE, "clash");
	private static final Key COUNT_FONT = Key.key(Items.NAMESPACE, "clash_count");
	private static final Key DEFAULT_FONT = Key.key("minecraft", "default");
	/** Coin shapes, drawn white and tinted: round (plain), octagon (unbreakable), and their cracked forms. */
	private static final char COIN_ROUND = '\ue401';
	private static final char COIN_OCTAGON = '\ue402';
	private static final char COIN_ROUND_CRACKED = '\ue403';
	private static final char COIN_OCTAGON_CRACKED = '\ue404';
	private static final char COIN_SPIN = '\ue405';
	private static final char COIN_SHATTER = '\ue407';
	private static final TextColor PLAIN_COLOUR = TextColor.color(0xE8B040);
	private static final TextColor RED_COLOUR = TextColor.color(0xD82020);
	private static final TextColor GREEN_COLOUR = TextColor.color(0x70D030);
	private static final TextColor HEADS_COLOUR = TextColor.color(0xFFEC6A);
	private static final TextColor TAILS_COLOUR = TextColor.color(0x4A4646);
	private static final char BADGE = '\ue410';
	private static final char DIGIT_ZERO = '\ue420';
	private static final char DIGIT_UNKNOWN = '\ue42a';
	private static final char BANNER_LEFT = '\ue430';
	private static final char BANNER_MIDDLE = '\ue431';
	private static final char BANNER_RIGHT = '\ue432';
	private static final char SP_FIRST = '\ue500';
	private static final char COUNT_FIRST = '\ue600';
	private static final char SPACE_PLUS = '\ue4a0';
	private static final char SPACE_MINUS = '\ue4b0';
	/** Advances (pixels with the gap): badge 33, a big digit 12; banner pieces drawn 6, 8 and 10 wide. */
	private static final int BADGE_ADVANCE = 33;
	private static final int DIGIT_ADVANCE = 12;
	private static final int BANNER_START = BADGE_ADVANCE + 3;

	private static String space(int pixels) {
		return Hud.shift(pixels, SPACE_PLUS, SPACE_MINUS);
	}

	private static Component glyph(char c, TextColor colour) {
		return Component.text(String.valueOf(c), colour).font(FONT);
	}

	/**
	 * The panel, after the original's clash UI: the coins over the banner
	 * (round plain coins, red and green octagons for unbreakable ones,
	 * cracked when broken, shattering when destroyed); the jagged badge in the
	 * skill's sin colour with the clash power in big digits; the banner with
	 * the skill's name and the user's Sanity circle; the numbers and the description.
	 */
	private static void draw(Side side) {
		if (side.panel == null || !side.panel.isValid()) {
			return;
		}
		SkillCard card = side.card;
		boolean frame = (CaduceusListener.now() / 2) % 2 == 0;
		Component coins = Component.text(space(BANNER_START), NamedTextColor.WHITE).font(FONT);
		long sinceShatter = CaduceusListener.now() - side.shatteredAt;
		for (int i = 0; i < card.coins(); i++) {
			char type = card.type(i);
			boolean octagon = type != SkillCard.COIN_PLAIN;
			TextColor base = type == SkillCard.COIN_EXECUTION ? GREEN_COLOUR : type == SkillCard.COIN_UNBREAKABLE ? RED_COLOUR : PLAIN_COLOUR;
			int state = side.state[i];
			if (state == DESTROYED) {
				// A destroyed coin cracks, splits, flies apart and fades, then is gone.
				if (i == side.shattering && sinceShatter < BREAK_TICKS) {
					coins = coins.append(glyph((char) (COIN_SHATTER + sinceShatter / 2), NamedTextColor.WHITE));
				}
				continue;
			}
			if (side.spinning && state == UNBROKEN) {
				coins = coins.append(glyph((char) (COIN_SPIN + ((frame ? 0 : 1) + i) % 2), NamedTextColor.WHITE));
				continue;
			}
			char shape = state == BROKEN ? (octagon ? COIN_OCTAGON_CRACKED : COIN_ROUND_CRACKED) : octagon ? COIN_OCTAGON : COIN_ROUND;
			TextColor colour = side.faces[i] == 1 ? HEADS_COLOUR : side.faces[i] == 2 ? TAILS_COLOUR : base;
			if (state == BROKEN && side.faces[i] == 0) {
				colour = TextColor.color(colour.red() * 6 / 10, colour.green() * 6 / 10, colour.blue() * 6 / 10);
			}
			coins = coins.append(glyph(shape, colour));
		}
		// Re-used coins line up after the others, as they come.
		for (int[] reuse : side.reused) {
			boolean octagon = card.type(reuse[0]) != SkillCard.COIN_PLAIN;
			boolean broken = side.state[reuse[0]] == BROKEN;
			char shape = broken ? (octagon ? COIN_OCTAGON_CRACKED : COIN_ROUND_CRACKED) : octagon ? COIN_OCTAGON : COIN_ROUND;
			coins = coins.append(glyph(shape, reuse[1] == 1 ? HEADS_COLOUR : TAILS_COLOUR));
		}
		// The badge and the power over it, centred.
		String digits = side.power < 0 || side.spinning ? String.valueOf(DIGIT_UNKNOWN) : bigDigits(side.power);
		int digitsWidth = digits.length() * DIGIT_ADVANCE;
		int back = BADGE_ADVANCE / 2 + digitsWidth / 2 + 1;
		TextColor powerColour = side.won == null ? NamedTextColor.WHITE : side.won ? NamedTextColor.GOLD : NamedTextColor.RED;
		Component badge = Component.text(String.valueOf(BADGE), card.colour()).font(FONT)
				.append(Component.text(space(-back), NamedTextColor.WHITE).font(FONT))
				.append(Component.text(digits, powerColour).font(FONT))
				.append(Component.text(space(BANNER_START - (BADGE_ADVANCE - back + digitsWidth)), NamedTextColor.WHITE).font(FONT));
		// The banner, long enough for the name, and the name on it.
		Component name = Component.text(card.name(), NamedTextColor.WHITE).decorate(TextDecoration.BOLD);
		int nameWidth = Hud.width(name);
		int segments = Math.max(2, (nameWidth + 7) / 8);
		StringBuilder banner = new StringBuilder().append(BANNER_LEFT).append(space(-1));
		for (int i = 0; i < segments; i++) {
			banner.append(BANNER_MIDDLE).append(space(-1));
		}
		banner.append(BANNER_RIGHT);
		int bannerWidth = 6 + 8 * segments + 11;
		int nameStart = 6 + (8 * segments - nameWidth) / 2;
		int sanity = Sanity.of(side.player);
		// Siblings under an empty root: a child would inherit the glyph font, which has no Hangul.
		Component bannerLine = Component.empty()
				.append(Component.text(banner.toString(), card.colour()).font(FONT))
				.append(Component.text(space(nameStart - bannerWidth), NamedTextColor.WHITE).font(FONT))
				.append(name.font(DEFAULT_FONT))
				.append(Component.text(space(bannerWidth - nameStart - nameWidth + 3)
						+ (char) (SP_FIRST + sanity - Sanity.MIN), NamedTextColor.WHITE).font(FONT));
		int chance = (int) Math.round(Sanity.headsChance(side.player) * 100);
		StringBuilder numbers = new StringBuilder("기본 " + card.base() + "  코인 +" + card.coinPower() + " ×" + card.coins() + "  앞면 " + chance + "%");
		Component stats = Component.text(numbers.toString(), TextColor.color(0xC8C8C8));
		Component text = Component.empty()
				.append(coins).appendNewline()
				.appendNewline()
				.append(badge).append(bannerLine).appendNewline()
				.appendNewline()
				.append(stats).appendNewline()
				.append(Component.text(card.description(), NamedTextColor.GRAY));
		side.panel.text(text);
	}

	private static String bigDigits(int value) {
		StringBuilder out = new StringBuilder();
		for (char c : String.valueOf(Math.max(0, value)).toCharArray()) {
			out.append((char) (DIGIT_ZERO + (c - '0')));
		}
		return out.toString();
	}

	/** The clash count between the two, after the original: a glowing brush numeral and 합, popping in each round. */
	private static void showCount(Duel duel, Location at) {
		int shown = Math.min(20, Math.max(1, duel.rounds));
		Component glyph = Component.text(String.valueOf((char) (COUNT_FIRST + shown - 1)), NamedTextColor.WHITE).font(COUNT_FONT);
		if (duel.counter == null || !duel.counter.isValid()) {
			duel.counter = at.getWorld().spawn(at, TextDisplay.class, d -> {
				d.setPersistent(false);
				d.setBillboard(Display.Billboard.CENTER);
				d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
				d.setBrightness(new Display.Brightness(15, 15));
			});
		} else {
			duel.counter.teleport(at);
		}
		duel.counter.text(glyph);
		// Pop: big at once, then settling over 4 ticks.
		duel.counter.setInterpolationDuration(0);
		duel.counter.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(1.0F, 1.0F, 1.0F), new Quaternionf()));
		TextDisplay counter = duel.counter;
		Bukkit.getScheduler().runTaskLater(IndexPrescriptPlugin.instance(), () -> {
			if (counter.isValid()) {
				counter.setInterpolationDelay(0);
				counter.setInterpolationDuration(4);
				counter.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.7F, 0.7F, 0.7F), new Quaternionf()));
			}
		}, 1L);
	}

	private static void coinSound(LivingEntity player) {
		player.getWorld().playSound(player.getLocation(), Sound.BLOCK_CHAIN_HIT, SoundCategory.PLAYERS, 0.6F, 1.8F);
	}

	private static Location midpoint(Side a, Side b) {
		Vector mid = body(a.player).getLocation().toVector().midpoint(body(b.player).getLocation().toVector())
				.add(new Vector(0, 1.2, 0));
		return mid.toLocation(a.player.getWorld());
	}

	private static void hold(Side side) {
		if (side.camera != null || side.anchor == null || side.anchor.getWorld() != side.player.getWorld()) {
			return;
		}
		Location here = side.player.getLocation();
		if (here.distanceSquared(side.anchor) > 0.04) {
			Location back = side.anchor.clone();
			back.setYaw(here.getYaw());
			back.setPitch(here.getPitch());
			side.player.teleport(back, TeleportFlag.EntityState.RETAIN_PASSENGERS);
		}
	}
}
