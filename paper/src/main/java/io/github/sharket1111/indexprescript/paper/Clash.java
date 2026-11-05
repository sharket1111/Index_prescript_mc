package io.github.sharket1111.indexprescript.paper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

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
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;

/**
 * Coins and clashes, as in Limbus Company.
 *
 * Every weapon skill goes through {@link #request}. Beside its user a panel
 * shows the skill's coins, its clash power, its name and description.
 *
 * - If the target is a player holding one of the plugin's weapons (or the
 *   Black God), they get {@link #WINDOW} ticks (2 s) to answer with a skill
 *   of their own. If they do, the two clash: every round both flip their
 *   remaining coins (heads with a chance of 50% + Sanity%), power = base +
 *   heads x coin power, and the lower side loses a coin (a tie loses none).
 *   When one side has no coins left, the other's skill goes off at the
 *   loser with the coins it has left. The winner gains Sanity and the loser
 *   loses some, more the more rounds it took.
 * - Otherwise (or if no answer comes) the coins are flipped once and the
 *   skill goes off unopposed.
 *
 * The coins scale the skill's damage while it plays (see {@link #scale}):
 * its final power over the expected power (base + half the coins heads),
 * from 0.5x to 1.6x, times the share of its coins left after a clash.
 * Counters and guards never clash; their coins are only shown.
 */
final class Clash {
	static final int WINDOW = 40;
	private static final int SPIN = 8;
	private static final int ROUND = 16;
	private static final int LINGER = 30;
	private static final int MAX_ROUNDS = 40;
	private static final Map<UUID, Duel> DUELS = new ConcurrentHashMap<>();
	private static final Map<UUID, double[]> SCALES = new ConcurrentHashMap<>();
	private static final List<Lingering> LINGERING = new ArrayList<>();

	/** Where the skill goes and how hard its coins made it. */
	record Outcome(LivingEntity target, double scale) {
	}

	private record Lingering(TextDisplay display, long removeAt) {
	}

	private static final class Side {
		final Player player;
		final SkillCard card;
		final Consumer<Outcome> start;
		LivingEntity target;
		Location anchor;
		int coins;
		/** Per coin: 0 not flipped, 1 heads, 2 tails. */
		final int[] faces;
		int power = -1;
		boolean spinning;
		Boolean won;
		TextDisplay panel;

		Side(Player player, SkillCard card, LivingEntity target, Consumer<Outcome> start) {
			this.player = player;
			this.card = card;
			this.target = target;
			this.start = start;
			this.coins = card.coins();
			this.faces = new int[card.coins()];
		}

		void flip() {
			double chance = Sanity.headsChance(player);
			int heads = 0;
			for (int i = 0; i < coins; i++) {
				faces[i] = ThreadLocalRandom.current().nextDouble() < chance ? 1 : 2;
				heads += faces[i] == 1 ? 1 : 0;
			}
			power = card.base() + heads * card.coinPower();
			spinning = false;
		}

		double scale() {
			double byPower = Math.max(0.5, Math.min(1.6, power / Math.max(1.0, card.expected())));
			double byCoins = Math.max(0.3, coins / (double) card.coins());
			return byPower * byCoins;
		}
	}

	private static final class Duel {
		final Side attacker;
		final Player defender;
		Side answer;
		boolean waiting;
		int tick;
		int rounds;

		Duel(Side attacker, Player defender) {
			this.attacker = attacker;
			this.defender = defender;
			this.waiting = defender != null;
		}
	}

	private Clash() {
	}

	// --- asking --------------------------------------------------------------------------

	/**
	 * A skill wants to go off. {@code start} runs when it does (now, after the
	 * coins, or after a clash it won), with its target and damage scale; it
	 * never runs if the skill loses a clash. Returns false if the user is
	 * already busy clashing (nothing happens then).
	 */
	static boolean request(Player user, LivingEntity target, SkillCard card, Consumer<Outcome> start) {
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
			return true;
		}
		if (asked != null) {
			Hud.message(user, Component.text("합이 진행 중입니다.", NamedTextColor.GRAY));
			return false;
		}
		Side side = new Side(user, card, target, start);
		side.panel = panel(user);
		Player defender = card.clashes() && target instanceof Player player && canAnswer(player) ? player : null;
		Duel duel = new Duel(side, defender);
		DUELS.put(user.getUniqueId(), duel);
		if (defender != null) {
			DUELS.put(defender.getUniqueId(), duel);
			defender.showTitle(Title.title(Component.text("합!", NamedTextColor.GOLD).decorate(TextDecoration.BOLD),
					Component.text(user.getName() + " — 2초 안에 스킬을 쓰면 합을 겨룹니다", NamedTextColor.YELLOW),
					Title.Times.times(Duration.ZERO, Duration.ofMillis(1600), Duration.ofMillis(300))));
			defender.playSound(defender.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.PLAYERS, 1.0F, 1.6F);
		} else {
			side.spinning = true;
		}
		draw(side);
		return true;
	}

	/** The player clashing or answering one now, or about to. */
	static boolean busy(Player player) {
		return DUELS.containsKey(player.getUniqueId());
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

	/** The damage scale a player's coins gave the skill they are using (1 when none). */
	static double scale(Player player) {
		double[] entry = SCALES.get(player.getUniqueId());
		if (entry == null) {
			return 1.0;
		}
		if (CaduceusListener.now() > entry[1]) {
			SCALES.remove(player.getUniqueId());
			return 1.0;
		}
		return entry[0];
	}

	private static boolean canAnswer(Player player) {
		if (!player.isOnline() || player.isDead() || player.getGameMode() == GameMode.SPECTATOR || DUELS.containsKey(player.getUniqueId())) {
			return false;
		}
		if (LaevateinSkill.active(player) || RenoirSkill.active(player) || Furioso.active(player) || BlackGodSkill.active(player)
				|| Laevatein.busy(player) || BlackGodRevive.reviving(player)) {
			return false;
		}
		return Items.isWeapon(player.getInventory().getItemInMainHand()) || BlackGod.active(player);
	}

	// --- every tick ----------------------------------------------------------------------

	static void tick() {
		long now = CaduceusListener.now();
		for (Duel duel : List.copyOf(new java.util.LinkedHashSet<>(DUELS.values()))) {
			try {
				tick(duel);
			} catch (RuntimeException e) {
				IndexPrescriptPlugin.instance().getLogger().log(java.util.logging.Level.WARNING, "Clash failed", e);
				end(duel);
			}
		}
		synchronized (LINGERING) {
			Iterator<Lingering> it = LINGERING.iterator();
			while (it.hasNext()) {
				Lingering lingering = it.next();
				if (now >= lingering.removeAt() || !lingering.display().isValid()) {
					lingering.display().remove();
					it.remove();
				}
			}
		}
	}

	private static void tick(Duel duel) {
		Side a = duel.attacker;
		if (!a.player.isOnline() || a.player.isDead()) {
			if (duel.answer != null && duel.answer.player.isOnline()) {
				win(duel, duel.answer, a);
			} else {
				end(duel);
			}
			return;
		}
		follow(a);
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
				DUELS.remove(d.getUniqueId());
				duel.waiting = false;
				duel.tick = 0;
				a.spinning = true;
				draw(a);
			}
			return;
		}
		if (duel.answer == null) {
			unopposed(duel);
			return;
		}
		Side b = duel.answer;
		if (!b.player.isOnline() || b.player.isDead()) {
			win(duel, a, b);
			return;
		}
		follow(b);
		hold(a);
		hold(b);
		round(duel, a, b);
	}

	private static void unopposed(Duel duel) {
		Side a = duel.attacker;
		duel.tick++;
		if (duel.tick < SPIN) {
			if (duel.tick % 2 == 0) {
				draw(a);
				coinSound(a.player);
			}
			return;
		}
		a.flip();
		draw(a);
		a.player.getWorld().playSound(a.player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.PLAYERS, 0.8F, 1.4F);
		launch(a, a.target);
		end(duel);
	}

	// --- the clash -------------------------------------------------------------------------

	private static void beginClash(Duel duel) {
		Side a = duel.attacker;
		Side b = duel.answer;
		a.target = b.player;
		a.anchor = a.player.getLocation();
		b.anchor = b.player.getLocation();
		for (Side side : new Side[] {a, b}) {
			side.player.showTitle(Title.title(Component.text("합", NamedTextColor.GOLD).decorate(TextDecoration.BOLD),
					Component.text(a.card.name() + "  vs  " + b.card.name(), NamedTextColor.GRAY),
					Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(300))));
		}
		Location mid = midpoint(a, b);
		mid.getWorld().playSound(mid, Sound.ITEM_TRIDENT_HIT, SoundCategory.PLAYERS, 1.2F, 0.8F);
		mid.getWorld().playSound(mid, Sound.BLOCK_BELL_USE, SoundCategory.PLAYERS, 1.0F, 1.2F);
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
			a.flip();
			b.flip();
			duel.rounds++;
			Location mid = midpoint(a, b);
			mid.getWorld().spawnParticle(Particle.CRIT, mid, 20, 0.3, 0.3, 0.3, 0.4);
			mid.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, mid, 12, 0.2, 0.2, 0.2, 0.2);
			mid.getWorld().playSound(mid, Sound.ITEM_SHIELD_BLOCK, SoundCategory.PLAYERS, 1.2F, 0.7F + ThreadLocalRandom.current().nextFloat() * 0.4F);
			if (a.power != b.power) {
				Side loser = a.power < b.power ? a : b;
				Side winner = loser == a ? b : a;
				winner.won = true;
				loser.won = false;
				loser.coins--;
				Location at = loser.player.getLocation().add(0, 1.2, 0);
				at.getWorld().spawnParticle(Particle.DUST, at, 10, 0.2, 0.2, 0.2, 0.0, new Particle.DustOptions(Color.fromRGB(0xC02020), 1.2F));
				at.getWorld().playSound(at, Sound.ENTITY_ITEM_BREAK, SoundCategory.PLAYERS, 1.0F, 1.2F);
			}
			draw(a);
			draw(b);
			return;
		}
		if (t >= ROUND) {
			if (a.coins <= 0 || b.coins <= 0 || duel.rounds >= MAX_ROUNDS) {
				Side winner = a.coins == b.coins ? (a.power >= b.power ? a : b) : a.coins > b.coins ? a : b;
				win(duel, winner, winner == a ? b : a);
				return;
			}
			duel.tick = 0;
		}
	}

	private static void win(Duel duel, Side winner, Side loser) {
		int rounds = Math.max(1, duel.rounds);
		Sanity.add(winner.player, Math.min(25, 3 + 3 * rounds));
		Sanity.lose(loser.player, Math.min(20, 2 + 2 * rounds));
		Sanity.fought(winner.player);
		Sanity.fought(loser.player);
		loser.coins = 0;
		winner.won = true;
		loser.won = false;
		if (winner.power < 0) {
			winner.flip();
		}
		draw(winner);
		draw(loser);
		Hud.message(winner.player, Component.text("합 승리! (" + rounds + "합) 정신력 +" + Math.min(25, 3 + 3 * rounds), NamedTextColor.GOLD));
		Hud.message(loser.player, Component.text("합 패배 (" + rounds + "합) 정신력 -" + Math.min(20, 2 + 2 * rounds), NamedTextColor.RED));
		Location at = loser.player.getLocation().add(0, 1, 0);
		at.getWorld().playSound(at, Sound.ITEM_SHIELD_BREAK, SoundCategory.PLAYERS, 1.2F, 0.8F);
		at.getWorld().spawnParticle(Particle.EXPLOSION, at, 1);
		if (loser.player.isOnline()) {
			launch(winner, loser.player);
		}
		end(duel);
	}

	private static void launch(Side side, LivingEntity target) {
		if (target == null || !target.isValid() || target.isDead()) {
			return;
		}
		double scale = side.scale();
		SCALES.put(side.player.getUniqueId(), new double[] {scale, CaduceusListener.now() + side.card.ticks() + 20});
		side.start.accept(new Outcome(target, scale));
	}

	private static void end(Duel duel) {
		DUELS.values().removeIf(d -> d == duel);
		retire(duel.attacker);
		if (duel.answer != null) {
			retire(duel.answer);
		}
	}

	private static void retire(Side side) {
		if (side.panel != null) {
			synchronized (LINGERING) {
				LINGERING.add(new Lingering(side.panel, CaduceusListener.now() + LINGER));
			}
		}
	}

	static void endAll() {
		for (Duel duel : new java.util.LinkedHashSet<>(DUELS.values())) {
			end(duel);
		}
		DUELS.clear();
		synchronized (LINGERING) {
			LINGERING.forEach(l -> l.display().remove());
			LINGERING.clear();
		}
		SCALES.clear();
	}

	// --- the panel -------------------------------------------------------------------------

	private static TextDisplay panel(Player player) {
		return player.getWorld().spawn(sideOf(player), TextDisplay.class, d -> {
			d.setPersistent(false);
			d.setBillboard(Display.Billboard.CENTER);
			d.setLineWidth(170);
			d.setTeleportDuration(2);
			d.setBackgroundColor(Color.fromARGB(150, 10, 8, 8));
			d.setBrightness(new Display.Brightness(15, 15));
			d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.55F, 0.55F, 0.55F), new Quaternionf()));
		});
	}

	/** Beside the player, on their right, about head height. */
	private static Location sideOf(Player player) {
		Location at = player.getLocation();
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
			Location want = sideOf(side.player);
			if (want.getWorld() == side.panel.getWorld() && want.distanceSquared(side.panel.getLocation()) > 0.0025) {
				side.panel.teleport(want);
			}
		}
	}

	private static void draw(Side side) {
		if (side.panel == null || !side.panel.isValid()) {
			return;
		}
		SkillCard card = side.card;
		boolean frame = (CaduceusListener.now() / 2) % 2 == 0;
		Component coins = Component.empty();
		for (int i = 0; i < card.coins(); i++) {
			Component coin;
			if (i >= side.coins) {
				coin = Component.text("✕", NamedTextColor.DARK_RED);
			} else if (side.spinning) {
				coin = Component.text(frame ? "◐" : "◑", NamedTextColor.YELLOW);
			} else if (side.faces[i] == 1) {
				coin = Component.text("●", NamedTextColor.GOLD);
			} else if (side.faces[i] == 2) {
				coin = Component.text("○", NamedTextColor.GRAY);
			} else {
				coin = Component.text("●", NamedTextColor.WHITE);
			}
			coins = coins.append(coin);
		}
		TextColor powerColour = side.won == null ? NamedTextColor.WHITE : side.won ? NamedTextColor.GOLD : NamedTextColor.RED;
		Component power = Component.text("합 위력 ", NamedTextColor.GRAY).append(side.power < 0 || side.spinning
				? Component.text("?", NamedTextColor.WHITE).decorate(TextDecoration.BOLD)
				: Component.text(side.power, powerColour).decorate(TextDecoration.BOLD));
		int chance = (int) Math.round(Sanity.headsChance(side.player) * 100);
		Component stats = Component.text("기본 " + card.base() + "  코인 +" + card.coinPower() + " ×" + card.coins() + "  앞면 " + chance + "%",
				NamedTextColor.DARK_GRAY);
		Component text = Component.empty().append(coins).appendNewline().append(power).appendNewline()
				.append(Component.text(card.name(), card.colour()).decorate(TextDecoration.BOLD)).appendNewline()
				.append(stats).appendNewline()
				.append(Component.text(card.description(), NamedTextColor.GRAY));
		side.panel.text(text);
	}

	private static void coinSound(Player player) {
		player.getWorld().playSound(player.getLocation(), Sound.BLOCK_CHAIN_HIT, SoundCategory.PLAYERS, 0.6F, 1.8F);
	}

	private static Location midpoint(Side a, Side b) {
		Vector mid = a.player.getLocation().toVector().midpoint(b.player.getLocation().toVector()).add(new Vector(0, 1.2, 0));
		return mid.toLocation(a.player.getWorld());
	}

	private static void hold(Side side) {
		if (side.anchor == null || side.anchor.getWorld() != side.player.getWorld()) {
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
