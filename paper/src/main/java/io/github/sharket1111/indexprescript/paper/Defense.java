package io.github.sharket1111.indexprescript.paper;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

/**
 * Defense skills, as in the original: a weapon's Guard, Evade or Counter. Used from the weapon's skill menu,
 * it holds for one turn (7 s).
 * - Guard: the coins are flipped at the first blow; the power (plus 1 for every 3 Defense Levels above the
 *   attacker's Offense Level) becomes a shield that soaks up damage until it is spent or the turn ends.
 * - Evade: every blow is matched against the attacker's power (its skill's, else a plain hit's); while the
 *   evade wins the blow misses entirely; the first loss ends it.
 * - Counter: the first blow is matched the same way; a win stops it and strikes back.
 * Effects: "use" when used, "struck" on each blow while guarding (to the attacker), "evade" on a dodge,
 * "hit" when a counter lands.
 */
final class Defense implements Listener {
	enum Type {
		GUARD("방어", "minecraft:shield"), EVADE("회피", "minecraft:feather"), COUNTER("반격", "minecraft:iron_sword");

		final String ko;
		final String icon;

		Type(String ko, String icon) {
			this.ko = ko;
			this.icon = icon;
		}

		static Type of(String s) {
			return switch (s == null ? "" : s.toLowerCase(java.util.Locale.ROOT)) {
				case "guard" -> GUARD;
				case "evade" -> EVADE;
				default -> COUNTER;
			};
		}
	}

	/** A Defense skill: its kind, numbers, text and effects (the Ego.Skill effect language, coin -1 = the skill). */
	record Spec(String name, Type type, String sin, int power, int coinPower, int coins, List<String> text, JsonArray effects, Bonus bonus) {
		Spec(String name, Type type, String sin, int power, int coinPower, int coins, List<String> text, JsonArray effects) {
			this(name, type, sin, power, coinPower, coins, text, effects, null);
		}

		int min() {
			return power + Math.min(0, coinPower) * coins;
		}

		int max() {
			return power + Math.max(0, coinPower) * coins;
		}

		static Spec of(Ego.Skill s, Type type) {
			return new Spec(s.name(), type, s.sin(), s.power(), s.coinPower(), s.coins(), s.text(), s.effects());
		}
	}

	/** A weapon's own rules on top of the effects: Coin Power and damage by its stacks, and what a use or a counter does. */
	interface Bonus {
		default int coinPower(Player p, LivingEntity foe) {
			return 0;
		}

		default int damagePct(Player p, LivingEntity foe) {
			return 0;
		}

		default void onUse(Player p) {
		}

		default void onCounter(Player p, LivingEntity foe) {
		}
	}

	/** Health a point of Guard power soaks up, and the damage a point of Counter power deals. */
	private static final double GUARD_PER_POWER = 0.5;
	private static final double COUNTER_PER_POWER = 0.3;
	/** A plain blow (no skill behind it) counts as this much power. */
	private static final int PLAIN_POWER = 8;
	/** Ticks between two uses of a Defense skill. */
	static final int WAIT = 8 * 20;

	private static final class State {
		final Spec spec;
		final long until;
		final Consumer<LivingEntity> onFirstBlow;
		double shield = -1;
		boolean blown;
		/** How often each limited effect (by its index) has fired this turn. */
		final Map<Integer, Integer> fired = new java.util.HashMap<>();

		State(Spec spec, long until, Consumer<LivingEntity> onFirstBlow) {
			this.spec = spec;
			this.until = until;
			this.onFirstBlow = onFirstBlow;
		}
	}

	private static final Map<UUID, State> ACTIVE = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> READY = new ConcurrentHashMap<>();

	static boolean active(Player p) {
		State s = ACTIVE.get(p.getUniqueId());
		return s != null && s.until > CaduceusListener.now();
	}

	/** Ticks until {@code p} can use a Defense skill again (0: now). */
	static int waitLeft(Player p) {
		return (int) Math.max(0, READY.getOrDefault(p.getUniqueId(), 0L) - CaduceusListener.now());
	}

	/** Uses {@code spec} for this turn; {@code onFirstBlow} (may be null) runs on the first blow it meets. */
	static boolean use(Player p, Spec spec, TextColor colour, Consumer<LivingEntity> onFirstBlow) {
		long now = CaduceusListener.now();
		if (waitLeft(p) > 0) {
			Hud.message(p, Component.text("수비 스킬 대기 중 (" + (waitLeft(p) + 19) / 20 + "초)", NamedTextColor.GRAY));
			return false;
		}
		if (Clash.blocked(p)) {
			Hud.message(p, Component.text("합이 진행 중입니다.", NamedTextColor.GRAY));
			return false;
		}
		ACTIVE.put(p.getUniqueId(), new State(spec, now + CombatState.TURN_TICKS, onFirstBlow));
		READY.put(p.getUniqueId(), now + WAIT);
		apply(p, null, spec, "use", null);
		if (spec.bonus() != null) {
			spec.bonus().onUse(p);
		}
		World w = p.getWorld();
		Location at = p.getLocation().add(0, 1, 0);
		switch (spec.type()) {
			case GUARD -> {
				w.playSound(at, Sound.ITEM_SHIELD_BLOCK, SoundCategory.PLAYERS, 1.0F, 0.8F);
				LowSpec.particle(w, Particle.ENCHANTED_HIT, at, 20, 0.5, 0.6, 0.5, 0.05);
			}
			case EVADE -> {
				w.playSound(at, Sound.ENTITY_BREEZE_JUMP, SoundCategory.PLAYERS, 0.8F, 1.4F);
				LowSpec.particle(w, Particle.CLOUD, at.clone().add(0, -0.8, 0), 12, 0.4, 0.1, 0.4, 0.02);
			}
			case COUNTER -> {
				w.playSound(at, Sound.ITEM_TRIDENT_RETURN, SoundCategory.PLAYERS, 1.0F, 0.7F);
				LowSpec.particle(w, Particle.CRIT, at, 20, 0.5, 0.6, 0.5, 0.2);
			}
		}
		Hud.message(p, Component.text(spec.type().ko + " · " + spec.name() + " (" + spec.min() + "~" + spec.max() + ", 1턴)", colour));
		return true;
	}

	/** A Defense skill's menu entry: slot {@code n}, its numbers and text, waiting when it is not ready. */
	static SkillMenu.Entry entry(Player p, int n, Spec spec, Runnable use) {
		TextColor colour = TextColor.color(Ego.SIN_RGB[Math.max(0, Ego.index(spec.sin()))]);
		List<Component> lore = new java.util.ArrayList<>();
		lore.add(SkillMenu.line("수비 스킬 · " + spec.type().ko + " · 위력 " + spec.power() + " · 코인 " + (spec.coinPower() >= 0 ? "+" : "")
				+ spec.coinPower() + " ×" + spec.coins(), NamedTextColor.GOLD));
		lore.add(SkillMenu.line(switch (spec.type()) {
			case GUARD -> "1턴 동안 위력만큼 피해를 막아요";
			case EVADE -> "1턴 동안 위력이 이기는 공격을 피해요";
			case COUNTER -> "1턴 안에 처음 받는 공격을 위력으로 맞받아쳐요";
		}, NamedTextColor.GRAY));
		spec.text().forEach(t -> MdGui.wrap(t, 36).forEach(l -> lore.add(SkillMenu.line(l, NamedTextColor.GRAY))));
		lore.add(SkillMenu.line("대기 " + WAIT / 20 + "초", NamedTextColor.DARK_GRAY));
		String name = n + ". " + spec.name() + " (" + spec.type().ko + ")";
		int left = waitLeft(p);
		if (left > 0) {
			return new SkillMenu.Entry(SkillMenu.icon("minecraft:gray_dye", (left + 19) / 20, SkillMenu.line(name, NamedTextColor.GRAY), lore), null);
		}
		return new SkillMenu.Entry(SkillMenu.icon(spec.type().icon, 1, SkillMenu.line(name, colour), lore), use);
	}

	private static int roll(Player p, Spec spec, LivingEntity foe) {
		double chance = Sanity.headsChance(p);
		int power = spec.power();
		int coinPower = spec.coinPower() + (spec.bonus() == null ? 0 : spec.bonus().coinPower(p, foe));
		for (int i = 0; i < spec.coins(); i++) {
			if (ThreadLocalRandom.current().nextDouble() < chance) {
				power += coinPower;
			}
		}
		return Math.max(0, power);
	}

	/** +1 for every 3 Defense Levels the defender has above the attacker's Offense Level. */
	private static int levelBonus(Player p, LivingEntity attacker) {
		return Math.max(0, (Upgrade.defenseLevel(p) - Upgrade.offenseLevel(attacker)) / 3);
	}

	private static int attackPower(LivingEntity attacker, Player p) {
		int power = Clash.power(attacker);
		return (power > 0 ? power : PLAIN_POWER) + Upgrade.clashLevelBonus(attacker, p);
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onDamage(EntityDamageByEntityEvent event) {
		if (!(event.getEntity() instanceof Player p)) {
			return;
		}
		State s = ACTIVE.get(p.getUniqueId());
		if (s == null) {
			return;
		}
		long now = CaduceusListener.now();
		if (s.until <= now) {
			ACTIVE.remove(p.getUniqueId());
			return;
		}
		if (Damage.dealing() && !Damage.countsAsHit()) {
			return; // fixed status damage (Bleed, Rupture) is not a blow
		}
		LivingEntity attacker = event.getDamager() instanceof LivingEntity l ? l
				: event.getDamager() instanceof Projectile pr && pr.getShooter() instanceof LivingEntity sh ? sh : null;
		if (attacker == null || attacker == p) {
			return;
		}
		boolean first = !s.blown;
		s.blown = true;
		World w = p.getWorld();
		Location at = p.getLocation().add(0, 1, 0);
		switch (s.spec.type()) {
			case GUARD -> {
				if (s.shield < 0) {
					int power = roll(p, s.spec, attacker) + levelBonus(p, attacker);
					s.shield = power * GUARD_PER_POWER;
					Hud.message(p, Component.text("방어 위력 " + power, NamedTextColor.AQUA));
				}
				double soaked = Math.min(s.shield, event.getDamage());
				s.shield -= soaked;
				event.setDamage(event.getDamage() - soaked);
				w.playSound(at, Sound.ITEM_SHIELD_BLOCK, SoundCategory.PLAYERS, 1.0F, 1.0F);
				LowSpec.particle(w, Particle.ENCHANTED_HIT, at, 10, 0.4, 0.5, 0.4, 0.1);
				apply(p, attacker, s.spec, "struck", s);
				if (s.shield <= 0.01) {
					ACTIVE.remove(p.getUniqueId());
				}
			}
			case EVADE -> {
				int mine = roll(p, s.spec, attacker) + levelBonus(p, attacker);
				int theirs = attackPower(attacker, p);
				if (mine >= theirs) {
					event.setCancelled(true);
					w.playSound(at, Sound.ENTITY_BREEZE_WIND_BURST, SoundCategory.PLAYERS, 0.7F, 1.6F);
					LowSpec.particle(w, Particle.CLOUD, at, 8, 0.4, 0.4, 0.4, 0.05);
					Hud.message(p, Component.text("회피 " + mine + " > " + theirs, NamedTextColor.AQUA));
					apply(p, attacker, s.spec, "evade", s);
				} else {
					Hud.message(p, Component.text("회피 실패 " + mine + " < " + theirs, NamedTextColor.GRAY));
					ACTIVE.remove(p.getUniqueId());
				}
			}
			case COUNTER -> {
				ACTIVE.remove(p.getUniqueId());
				int mine = roll(p, s.spec, attacker) + levelBonus(p, attacker);
				int theirs = attackPower(attacker, p);
				if (mine >= theirs) {
					event.setCancelled(true);
					w.playSound(at, Sound.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1.0F, 0.7F);
					LowSpec.particle(w, Particle.SWEEP_ATTACK, attacker.getLocation().add(0, attacker.getHeight() * 0.6, 0), 2, 0.2, 0.1, 0.2, 0);
					Hud.message(p, Component.text("반격 " + mine + " > " + theirs, NamedTextColor.AQUA));
					p.swingMainHand();
					double bonus = apply(p, attacker, s.spec, "hit", s) + (s.spec.bonus() == null ? 0 : s.spec.bonus().damagePct(p, attacker));
					Damage.deal(attacker, mine * COUNTER_PER_POWER * (1 + bonus / 100.0), p, true);
					if (s.spec.bonus() != null) {
						s.spec.bonus().onCounter(p, attacker);
					}
				} else {
					Hud.message(p, Component.text("반격 실패 " + mine + " < " + theirs, NamedTextColor.GRAY));
				}
			}
		}
		if (first && s.onFirstBlow != null) {
			s.onFirstBlow.accept(attacker);
		}
	}

	/** Plays the skill's effects for {@code moment}; returns +damage %. */
	private static double apply(Player p, LivingEntity foe, Spec spec, String moment, State state) {
		double bonus = 0;
		long now = CaduceusListener.now();
		for (int i = 0; i < spec.effects().size(); i++) {
			JsonObject e = spec.effects().get(i).getAsJsonObject();
			String when = e.get("when").getAsString();
			boolean fires = switch (moment) {
				case "use" -> when.equals("use");
				case "struck" -> when.equals("struck");
				case "evade" -> when.equals("evade");
				case "hit" -> when.equals("hit") || when.equals("heads");
				default -> false;
			};
			if (!fires) {
				continue;
			}
			if (e.has("limit") && state != null) {
				int n = state.fired.merge(i, 1, Integer::sum);
				if (n > e.get("limit").getAsInt()) {
					continue;
				}
			}
			switch (e.get("do").getAsString()) {
				case "inflict" -> {
					if (foe != null) {
						MdGifts.inflictKeyword(foe, e.get("kw").getAsString(), e.get("potency").getAsInt(), e.get("count").getAsInt());
					}
				}
				case "debuff" -> {
					if (foe != null) {
						MdBattle.debuff(foe, e.get("what").getAsString(), e.get("amount").getAsInt());
					}
				}
				case "gain" -> {
					String kw = e.get("kw").getAsString();
					if (kw.equals("poise")) {
						CombatState.of(p).addPoise(e.get("potency").getAsInt(), Math.max(1, e.get("count").getAsInt()), now);
					} else if (kw.equals("charge")) {
						CombatState.of(p).addCharge(Math.max(e.get("count").getAsInt(), e.get("potency").getAsInt()), now);
					}
				}
				case "buff" -> MdGifts.buff(p, e.get("what").getAsString(), e.get("amount").getAsInt());
				case "sp" -> Sanity.add(p, e.get("amount").getAsInt());
				case "damage" -> bonus += e.get("pct").getAsInt();
				default -> {
				}
			}
		}
		return bonus;
	}

	// --- building specs ---------------------------------------------------------------------------------

	static JsonObject fx(String when, String act, String kw, int value, int count) {
		JsonObject o = new JsonObject();
		o.addProperty("when", when);
		o.addProperty("do", act);
		o.addProperty("coin", -1);
		o.addProperty("to", "target");
		switch (act) {
			case "inflict", "gain" -> {
				o.addProperty("kw", kw);
				o.addProperty("potency", value);
				o.addProperty("count", count);
			}
			case "buff", "debuff" -> {
				o.addProperty("what", kw);
				o.addProperty("amount", value);
			}
			case "sp" -> o.addProperty("amount", value);
			case "damage" -> o.addProperty("pct", value);
			default -> {
			}
		}
		return o;
	}

	static JsonArray fxs(JsonObject... list) {
		JsonArray a = new JsonArray();
		for (JsonObject o : list) {
			a.add(o);
		}
		return a;
	}
}
