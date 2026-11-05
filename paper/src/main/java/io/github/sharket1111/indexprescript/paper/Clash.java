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
	/** The shattering: four frames of two ticks. */
	private static final int BREAK_TICKS = 8;
	private static final Map<UUID, Duel> DUELS = new ConcurrentHashMap<>();
	private static final Map<UUID, double[]> SCALES = new ConcurrentHashMap<>();
	private static final List<Lingering> LINGERING = new ArrayList<>();

	/** Where the skill goes and how hard its coins made it. */
	record Outcome(LivingEntity target, double scale) {
	}

	private record Lingering(TextDisplay display, long removeAt, Side side) {
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
		/** Coins [brokenFrom, brokenTo) are shattering since brokenAt; broken coins are gone after. */
		int brokenFrom;
		int brokenTo;
		long brokenAt = Long.MIN_VALUE / 2;
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

		/** Destroys the coins from {@code left} on, with the shattering motion. */
		void breakTo(int left) {
			if (left >= coins) {
				return;
			}
			brokenFrom = left;
			brokenTo = coins;
			brokenAt = CaduceusListener.now();
			coins = left;
		}

		boolean shattering() {
			return CaduceusListener.now() - brokenAt < BREAK_TICKS;
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
		TextDisplay counter;

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
				} else if (lingering.side() != null && lingering.side().shattering()) {
					draw(lingering.side());
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
		for (Side side : new Side[] {a, b}) {
			if (side.shattering()) {
				draw(side);
			}
		}
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
			showCount(duel, mid.clone().add(0, 1.6, 0));
			mid.getWorld().spawnParticle(Particle.CRIT, mid, 20, 0.3, 0.3, 0.3, 0.4);
			mid.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, mid, 12, 0.2, 0.2, 0.2, 0.2);
			mid.getWorld().playSound(mid, Sound.ITEM_SHIELD_BLOCK, SoundCategory.PLAYERS, 1.2F, 0.7F + ThreadLocalRandom.current().nextFloat() * 0.4F);
			if (a.power != b.power) {
				Side loser = a.power < b.power ? a : b;
				Side winner = loser == a ? b : a;
				winner.won = true;
				loser.won = false;
				loser.breakTo(loser.coins - 1);
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
		loser.breakTo(0);
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
		if (duel.counter != null) {
			synchronized (LINGERING) {
				LINGERING.add(new Lingering(duel.counter, CaduceusListener.now() + LINGER, null));
			}
		}
		retire(duel.attacker);
		if (duel.answer != null) {
			retire(duel.answer);
		}
	}

	private static void retire(Side side) {
		if (side.panel != null) {
			synchronized (LINGERING) {
				LINGERING.add(new Lingering(side.panel, CaduceusListener.now() + LINGER, side));
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

	// The pack's "clash" font (tools/clash_font.py): coins, the badge, big digits, the banner, Sanity circles, spaces.
	private static final Key FONT = Key.key(Items.NAMESPACE, "clash");
	private static final Key COUNT_FONT = Key.key(Items.NAMESPACE, "clash_count");
	private static final char COIN_FRESH = '\ue401';
	private static final char COIN_HEADS = '\ue402';
	private static final char COIN_TAILS = '\ue403';
	private static final char COIN_SPIN = '\ue405';
	private static final char COIN_CRACK = '\ue407';
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

	/**
	 * The panel, after the original's clash UI: the coins over the banner;
	 * the jagged badge in the skill's sin colour with the clash power in big
	 * digits; the banner, in the sin colour, with the skill's name on it, and
	 * the user's Sanity circle after it; then the numbers and the description.
	 */
	private static void draw(Side side) {
		if (side.panel == null || !side.panel.isValid()) {
			return;
		}
		SkillCard card = side.card;
		boolean frame = (CaduceusListener.now() / 2) % 2 == 0;
		// Coins, above the banner.
		StringBuilder coins = new StringBuilder(space(BANNER_START));
		long sinceBreak = CaduceusListener.now() - side.brokenAt;
		for (int i = 0; i < card.coins(); i++) {
			char coin;
			if (i >= side.coins) {
				// A destroyed coin cracks, splits, flies apart and fades, then is gone.
				if (i < side.brokenFrom || i >= side.brokenTo || sinceBreak >= BREAK_TICKS) {
					break;
				}
				coin = (char) (COIN_CRACK + sinceBreak / 2);
			} else if (side.spinning) {
				coin = (char) (COIN_SPIN + ((frame ? 0 : 1) + i) % 2);
			} else if (side.faces[i] == 1) {
				coin = COIN_HEADS;
			} else if (side.faces[i] == 2) {
				coin = COIN_TAILS;
			} else {
				coin = COIN_FRESH;
			}
			coins.append(coin);
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
		Component bannerLine = Component.text(banner.toString(), card.colour()).font(FONT)
				.append(Component.text(space(nameStart - bannerWidth), NamedTextColor.WHITE).font(FONT))
				.append(name)
				.append(Component.text(space(bannerWidth - nameStart - nameWidth + 3)
						+ (char) (SP_FIRST + sanity - Sanity.MIN), NamedTextColor.WHITE).font(FONT));
		int chance = (int) Math.round(Sanity.headsChance(side.player) * 100);
		Component stats = Component.text("기본 " + card.base() + "  코인 +" + card.coinPower() + " ×" + card.coins() + "  앞면 " + chance + "%",
				TextColor.color(0xC8C8C8));
		Component text = Component.empty()
				.append(Component.text(coins.toString(), NamedTextColor.WHITE).font(FONT)).appendNewline()
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
		org.bukkit.Bukkit.getScheduler().runTaskLater(IndexPrescriptPlugin.instance(), () -> {
			if (counter.isValid()) {
				counter.setInterpolationDelay(0);
				counter.setInterpolationDuration(4);
				counter.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.7F, 0.7F, 0.7F), new Quaternionf()));
			}
		}, 1L);
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
