package io.github.sharket1111.indexprescript.paper;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

/**
 * The Black God's keywords and passives (fan-made, after Limbus Company; see
 * docs/black_god.md), on a turn of {@link CombatState#TURN_TICKS} ticks.
 *
 * Keywords:
 * - 검은 신의 목소리 (Voice, 0-100): +10 at each turn's end, and by the
 *   Preservation the Black God's skills gain or spend (up to 40 a turn); per
 *   20, one attack level. At 100 it becomes the Authority next turn.
 * - 검은 신의 권능 (Authority, 0-100): per 20, an attack and a defense level;
 *   the great skills (7-9) can only be used with it; some skills spend it. At
 *   0 it becomes the Voice again.
 * - 시지프 형벌의 신부 (Bride, 0-5): per stack +2 attack and +3 defense levels;
 *   at 3+, one more 못[Console] from each infliction. +1 when taking up the mode
 *   and at every Voice/Authority switch.
 * - 시지프 시스템 보존 (System Preservation, 0-25): damage dealt +x%, taken -x%.
 * - 보존 (Preservation): the Renoir hammer's (count and potency, the Unluck icon).
 * - ~ (못[Exception] / [Breakpoint] / [Console]) on foes: one kind at a time, up
 *   to 9; the latest kind applies and counts add up, but Exception and
 *   Breakpoint never turn into Console. Shown over the foe. Exception bursts at
 *   the next turn's start (50% of current health, fixed, and a stagger);
 *   Sisyphus coin IV sets off all of them (Breakpoint: count x 10% of max
 *   health; Console: sinking and stagger damage).
 * - 정지 (Stop) / 박동 (Pulse): each turn a random foe nearby gets each. Stop:
 *   it deals +25% to the Black God. Pulse: it takes half of all the Black
 *   God's damage (Forced Exit included) and is not staggered by Exception.
 *
 * Attack levels are +3% damage each and defense levels -2% damage taken each
 * (with System Preservation, up to -60%).
 *
 * Passives:
 * - 젠장, 그런 글리치는 어떻게 찾아낸 거야.: Stop and Pulse each turn; falling below
 *   half health the first time, the Bride steps in: full health, ailments gone.
 * - 시지프 시스템 유지보수 툴: +5% damage per ~ on the target (max 45%); at a
 *   turn's start at half health or less, every ~ inflicted that turn +1.
 * - '보존의 규율대로': burning, bleeding (wither) and poison deal half; taking
 *   up the mode starts with Voice 0 and Bride 1, Preservation kept.
 * - '심판의 날': a turn with the Authority gives speed; each Voice/Authority
 *   switch heals and adds a Bride stack.
 * - 완벽 보존: every 10 Preservation spent, potency +1 (the hammer's rule);
 *   at potency 10 a turn starting under 10 count adds 5; potency 5/10 raise the
 *   count's cap by 5/10.
 * - 자, 이제 퇴근해 볼까: at a turn's end, potency over 10 becomes System
 *   Preservation (until it is first spent); the first fall to a third of health
 *   spends it all for a lasting shield of (25 + it)% of max health.
 */
final class BlackGodState {
	static final int MAX = 100;
	static final int MAX_BRIDE = 5;
	static final int MAX_SYSTEM = 25;
	static final int MAX_NAILS = 9;
	private static final int TURN = CombatState.TURN_TICKS;
	private static final int VOICE_PER_TURN_FROM_PRESERVATION = 40;
	private static final double FOE_RADIUS = 16.0;
	private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();
	private static final Map<UUID, Nails> NAILS = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> STOP = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> PULSE = new ConcurrentHashMap<>();
	private static final Particle.DustOptions STOP_DUST = new Particle.DustOptions(Color.fromRGB(0xB8B8C8), 1.2F);
	private static final Particle.DustOptions PULSE_DUST = new Particle.DustOptions(Color.fromRGB(0xE03040), 1.2F);

	enum Nail {
		EXCEPTION("못[Exception]", TextColor.color(0xE070E0)),
		BREAKPOINT("못[Breakpoint]", TextColor.color(0xFF5050)),
		CONSOLE("못[Console]", TextColor.color(0xF0D060));

		final String label;
		final TextColor colour;

		Nail(String label, TextColor colour) {
			this.label = label;
			this.colour = colour;
		}
	}

	private static final class State {
		final Player god;
		final BossBar bar = BossBar.bossBar(Component.empty(), 0F, BossBar.Color.YELLOW, BossBar.Overlay.NOTCHED_10);
		int voice;
		int authority;
		boolean authorityForm;
		int bride = 1;
		int system;
		boolean systemSpent;
		int voiceFromPreservation;
		boolean guardFill;
		boolean bridedHealed;
		boolean shielded;
		boolean mighty;
		int clock;

		State(Player god) {
			this.god = god;
		}
	}

	/** The nails in one foe: one kind at a time. */
	private static final class Nails {
		final LivingEntity foe;
		Nail kind;
		int count;
		TextDisplay label;
		long lastAt;

		Nails(LivingEntity foe) {
			this.foe = foe;
		}
	}

	private BlackGodState() {
	}

	private static State state(Player god) {
		return STATES.get(god.getUniqueId());
	}

	// --- the mode ------------------------------------------------------------------------

	/** Taking up the mode ('보존의 규율대로'): Voice 0, Bride 1; Preservation carries over. */
	static void enter(Player god) {
		State state = new State(god);
		STATES.put(god.getUniqueId(), state);
		god.showBossBar(state.bar);
		refreshBar(state);
	}

	static void leave(Player god) {
		State state = STATES.remove(god.getUniqueId());
		if (state != null) {
			god.hideBossBar(state.bar);
		}
		god.removePotionEffect(PotionEffectType.SPEED);
	}

	static boolean authorityForm(Player god) {
		State state = state(god);
		return state != null && state.authorityForm;
	}

	static int authority(Player god) {
		State state = state(god);
		return state == null || !state.authorityForm ? 0 : state.authority;
	}

	/** Spends up to {@code max} Authority; returns how much. */
	static int spendAuthority(Player god, int max) {
		State state = state(god);
		if (state == null || !state.authorityForm) {
			return 0;
		}
		int spent = Math.min(max, state.authority);
		state.authority -= spent;
		refreshBar(state);
		return spent;
	}

	/** The Black God's skills gained or spent {@code amount} Preservation: the Voice rises by as much (40 a turn). */
	static void preservationChanged(Player god, int amount) {
		State state = state(god);
		if (state == null || state.authorityForm || amount == 0) {
			return;
		}
		int room = VOICE_PER_TURN_FROM_PRESERVATION - state.voiceFromPreservation;
		int gain = Math.min(Math.abs(amount), Math.max(0, room));
		state.voiceFromPreservation += gain;
		state.voice = Math.min(MAX, state.voice + gain);
		refreshBar(state);
	}

	/** '신 앞에 뉘우쳐라'던가?: at this turn's end the Voice fills to 100 (if under). */
	static void guardFill(Player god) {
		State state = state(god);
		if (state != null) {
			state.guardFill = true;
		}
	}

	/** The cap on Preservation's count (완벽 보존: potency 5/10 raise it by 5/10). */
	static int preservationCap(Player god) {
		if (state(god) == null) {
			return RenoirHammer.MAX_PRESERVATION;
		}
		int potency = RenoirHammer.potency(god);
		return RenoirHammer.MAX_PRESERVATION + (potency >= 10 ? 10 : potency >= 5 ? 5 : 0);
	}

	// --- levels and damage -----------------------------------------------------------------

	private static int attackLevels(State state) {
		int form = Math.min(4, (state.authorityForm ? state.authority : state.voice) / 20);
		return form + 2 * state.bride;
	}

	private static int defenseLevels(State state) {
		int form = state.authorityForm ? Math.min(4, state.authority / 20) : 0;
		return form + 3 * state.bride;
	}

	/** What the Black God's hits on {@code foe} are multiplied by: attack levels, System Preservation, the ~ on it. */
	static double attackMultiplier(Player god, LivingEntity foe) {
		State state = state(god);
		if (state == null) {
			return 1.0;
		}
		double bonus = attackLevels(state) * 0.03 + state.system * 0.01 + Math.min(0.45, nails(foe) * 0.05);
		return 1.0 + bonus;
	}

	static boolean pulse(Entity entity) {
		Long until = PULSE.get(entity.getUniqueId());
		return until != null && CaduceusListener.now() < until;
	}

	static boolean stop(Entity entity) {
		Long until = STOP.get(entity.getUniqueId());
		return until != null && CaduceusListener.now() < until;
	}

	// --- nails (~) ---------------------------------------------------------------------------

	static int nails(LivingEntity foe) {
		Nails nails = NAILS.get(foe.getUniqueId());
		return nails == null ? 0 : nails.count;
	}

	/**
	 * Inflicts {@code amount} nails of {@code kind} (시지프 시스템 유지보수 툴: +1 in a
	 * turn that started at half health or less; Bride 3+: Console +1).
	 */
	static void inflict(Player god, LivingEntity foe, Nail kind, int amount) {
		State state = state(god);
		if (state == null || !foe.isValid() || foe.isDead()) {
			return;
		}
		if (state.mighty) {
			amount++;
		}
		if (kind == Nail.CONSOLE && state.bride >= 3) {
			amount++;
		}
		Nails nails = NAILS.computeIfAbsent(foe.getUniqueId(), id -> new Nails(foe));
		boolean keepKind = nails.kind != null && nails.count > 0 && nails.kind != Nail.CONSOLE && kind == Nail.CONSOLE;
		if (!keepKind) {
			nails.kind = kind;
		}
		nails.count = Math.min(MAX_NAILS, nails.count + amount);
		nails.lastAt = CaduceusListener.now();
		label(nails);
		Location top = foe.getLocation().add(0, foe.getHeight() + 0.3, 0);
		foe.getWorld().spawnParticle(Particle.DUST, top, 10, 0.25, 0.2, 0.25, 0.0,
				new Particle.DustOptions(Color.fromRGB(nails.kind.colour.value()), 1.2F));
		foe.getWorld().playSound(top, Sound.BLOCK_ANVIL_PLACE, SoundCategory.PLAYERS, 0.5F, 1.8F);
	}

	private static void label(Nails nails) {
		Component text = Component.text(nails.kind.label + " " + nails.count, nails.kind.colour);
		if (nails.label == null || !nails.label.isValid()) {
			LivingEntity foe = nails.foe;
			nails.label = foe.getWorld().spawn(foe.getLocation().add(0, foe.getHeight(), 0), TextDisplay.class, d -> {
				d.setPersistent(false);
				d.setBillboard(Display.Billboard.CENTER);
				d.setBackgroundColor(Color.fromARGB(90, 0, 0, 0));
				d.setTransformation(new Transformation(new Vector3f(0, 0.55F, 0), new Quaternionf(),
						new Vector3f(0.8F, 0.8F, 0.8F), new Quaternionf()));
			});
			if (foe instanceof Player player) {
				// Not over the holder's own camera.
				player.hideEntity(IndexPrescriptPlugin.instance(), nails.label);
			}
			if (!foe.addPassenger(nails.label)) {
				nails.label.setTeleportDuration(2);
			}
		}
		nails.label.text(text);
	}

	private static void clearNails(Nails nails) {
		if (nails.label != null) {
			nails.label.remove();
		}
		NAILS.remove(nails.foe.getUniqueId());
	}

	/** Sisyphus coin IV: sets off and spends every nail in {@code foe}. */
	static void trigger(Player god, LivingEntity foe) {
		Nails nails = NAILS.get(foe.getUniqueId());
		if (nails == null || nails.count <= 0) {
			return;
		}
		clearNails(nails);
		long now = CaduceusListener.now();
		switch (nails.kind) {
			case EXCEPTION -> exception(god, foe);
			case BREAKPOINT -> fixed(god, foe, Math.max(10.0, maxHealth(foe) * nails.count * 0.1));
			case CONSOLE -> {
				CombatState.of(foe).addSinking(nails.count, nails.count, now);
				stagger(foe, nails.count * 2.0F);
			}
		}
		foe.getWorld().spawnParticle(Particle.END_ROD, foe.getLocation().add(0, foe.getHeight() * 0.6, 0), 20, 0.3, 0.4, 0.3, 0.15);
		foe.getWorld().playSound(foe.getLocation(), Sound.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 1.0F, 0.6F);
	}

	/** 못[Exception] bursting: half the foe's current health (fixed) and a stagger; Pulse holds the stagger off and halves it. */
	private static void exception(Player god, LivingEntity foe) {
		fixed(god, foe, foe.getHealth() * 0.5);
		if (!pulse(foe)) {
			stagger(foe, 1000F);
		}
	}

	private static void stagger(LivingEntity foe, float amount) {
		long now = CaduceusListener.now();
		float threshold = (float) Math.max(10.0, maxHealth(foe) * 0.5);
		CombatState state = CombatState.of(foe);
		if (state.addStagger(amount, threshold, now)) {
			Stagger.apply(foe, now);
		} else {
			Stagger.gauge(foe, state.staggerBuildUp(now) / threshold);
		}
	}

	private static double maxHealth(LivingEntity entity) {
		AttributeInstance max = entity.getAttribute(Attribute.MAX_HEALTH);
		return max == null ? 20.0 : max.getValue();
	}

	/** Fixed damage past armour (Pulse halves it); enough to kill, kills (credited to the Black God). */
	private static void fixed(Player god, LivingEntity foe, double amount) {
		if (!foe.isValid() || foe.isDead() || amount <= 0) {
			return;
		}
		if (pulse(foe)) {
			amount *= 0.5;
		}
		double before = foe.getHealth() + foe.getAbsorptionAmount();
		if (amount >= before) {
			foe.setNoDamageTicks(0);
			foe.damage(before + 1, DamageSource.builder(DamageType.GENERIC_KILL).withCausingEntity(god).withDirectEntity(god).build());
		} else {
			double absorbed = Math.min(foe.getAbsorptionAmount(), amount);
			foe.setAbsorptionAmount(foe.getAbsorptionAmount() - absorbed);
			foe.setHealth(Math.max(0.5, foe.getHealth() - (amount - absorbed)));
			foe.playHurtAnimation(0);
		}
		double after = foe.isDead() ? 0.0 : foe.getHealth() + foe.getAbsorptionAmount();
		TotalDamage.add(god, before - after);
	}

	// --- turns -------------------------------------------------------------------------------

	static void tick() {
		long now = CaduceusListener.now();
		for (Player player : Bukkit.getOnlinePlayers()) {
			// Still in the mode after a rejoin or a restart: the state comes back fresh.
			if (BlackGod.active(player) && !STATES.containsKey(player.getUniqueId())) {
				enter(player);
			}
		}
		STATES.values().removeIf(state -> {
			if (state.god.isOnline() && BlackGod.active(state.god)) {
				return false;
			}
			state.god.hideBossBar(state.bar);
			return true;
		});
		for (State state : STATES.values()) {
			Player god = state.god;
			state.clock++;
			if (state.clock % TURN == 0) {
				turnEnd(state);
				turnStart(state, now);
			}
			if (state.clock % 10 == 0) {
				refreshBar(state);
			}
		}
		Iterator<Nails> it = NAILS.values().iterator();
		while (it.hasNext()) {
			Nails nails = it.next();
			if (!nails.foe.isValid() || nails.foe.isDead() || now - nails.lastAt > 120 * 20) {
				if (nails.label != null) {
					nails.label.remove();
				}
				it.remove();
			} else if (nails.label != null && nails.label.isValid() && nails.label.getVehicle() == null) {
				nails.label.teleport(nails.foe.getLocation().add(0, nails.foe.getHeight(), 0));
			}
		}
		if (now % 10 == 0) {
			marks(STOP, STOP_DUST, now);
			marks(PULSE, PULSE_DUST, now);
		}
	}

	private static void marks(Map<UUID, Long> marks, Particle.DustOptions dust, long now) {
		marks.entrySet().removeIf(entry -> now >= entry.getValue());
		for (UUID id : marks.keySet()) {
			Entity entity = Bukkit.getEntity(id);
			if (entity instanceof LivingEntity living && living.isValid()) {
				Location top = living.getLocation().add(0, living.getHeight() + 0.4, 0);
				for (int i = 0; i < 8; i++) {
					double a = i * Math.PI / 4 + now * 0.1;
					living.getWorld().spawnParticle(Particle.DUST, top.clone().add(Math.cos(a) * 0.4, 0, Math.sin(a) * 0.4), 1, 0, 0, 0, 0.0, dust);
				}
			}
		}
	}

	private static void turnEnd(State state) {
		Player god = state.god;
		if (!state.authorityForm) {
			state.voice = Math.min(MAX, state.voice + 10);
			if (state.guardFill) {
				state.voice = MAX;
			}
		}
		state.guardFill = false;
		// 자, 이제 퇴근해 볼까: potency past 10 becomes System Preservation.
		int potency = RenoirHammer.potency(god);
		if (potency > 10 && !state.systemSpent) {
			state.system = Math.min(MAX_SYSTEM, state.system + potency - 10);
			RenoirHammer.setPotency(god, 10);
		}
		boolean switched = false;
		if (!state.authorityForm && state.voice >= MAX) {
			state.authorityForm = true;
			state.authority = MAX;
			state.voice = 0;
			switched = true;
		} else if (state.authorityForm && state.authority <= 0) {
			state.authorityForm = false;
			state.voice = 0;
			switched = true;
		}
		if (switched) {
			// '심판의 날': every switch heals and adds a Bride.
			state.bride = Math.min(MAX_BRIDE, state.bride + 1);
			god.setHealth(Math.min(maxHealth(god), god.getHealth() + maxHealth(god) * 0.45));
			Location at = god.getLocation().add(0, 1, 0);
			god.getWorld().spawnParticle(Particle.END_ROD, at, 40, 0.6, 1.0, 0.6, 0.1);
			god.getWorld().playSound(at, Sound.BLOCK_BELL_RESONATE, SoundCategory.PLAYERS, 1.5F, state.authorityForm ? 0.5F : 0.8F);
			god.sendActionBar(Component.text(Messages.get(god, state.authorityForm ? "blackgod.to_authority" : "blackgod.to_voice"),
					TextColor.color(0xF0D070)));
		}
		state.voiceFromPreservation = 0;
	}

	private static void turnStart(State state, long now) {
		Player god = state.god;
		// 완벽 보존: at potency 10, a count under 10 gains 5.
		if (RenoirHammer.potency(god) >= 10 && RenoirHammer.preservation(god) < 10) {
			RenoirHammer.addPreservation(god, 5);
		}
		// 시지프 시스템 유지보수 툴: at half health or less, every ~ this turn +1.
		state.mighty = god.getHealth() <= maxHealth(god) * 0.5;
		// '심판의 날': a turn with the Authority is a fast one.
		if (state.authorityForm) {
			god.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, TURN, 1, false, false, true));
		}
		// 못[Exception] bursts.
		for (Nails nails : List.copyOf(NAILS.values())) {
			if (nails.kind == Nail.EXCEPTION && nails.count > 0 && nails.foe.getWorld() == god.getWorld()
					&& nails.foe.getLocation().distanceSquared(god.getLocation()) < 64 * 64) {
				clearNails(nails);
				exception(god, nails.foe);
			}
		}
		// 젠장, 그런 글리치는 어떻게 찾아낸 거야.: Stop and Pulse go to random foes nearby.
		List<LivingEntity> foes = new ArrayList<>();
		for (Entity entity : god.getNearbyEntities(FOE_RADIUS, FOE_RADIUS, FOE_RADIUS)) {
			if (RenoirHammer.foe(god, entity)) {
				foes.add((LivingEntity) entity);
			}
		}
		if (!foes.isEmpty()) {
			ThreadLocalRandom random = ThreadLocalRandom.current();
			LivingEntity stop = foes.get(random.nextInt(foes.size()));
			LivingEntity pulse = foes.get(random.nextInt(foes.size()));
			STOP.put(stop.getUniqueId(), now + TURN);
			PULSE.put(pulse.getUniqueId(), now + TURN);
			if (stop instanceof Player player) {
				player.sendActionBar(Component.text(Messages.get(player, "blackgod.got_stop"), NamedTextColor.GRAY));
			}
			if (pulse instanceof Player player) {
				player.sendActionBar(Component.text(Messages.get(player, "blackgod.got_pulse"), NamedTextColor.RED));
			}
		}
	}

	private static void refreshBar(State state) {
		Player god = state.god;
		String form = Messages.get(god, state.authorityForm ? "blackgod.authority" : "blackgod.voice");
		int value = state.authorityForm ? state.authority : state.voice;
		String text = form + " " + value + "/" + MAX + "  ·  " + Messages.get(god, "blackgod.bride") + " " + state.bride
				+ "  ·  " + Messages.get(god, "blackgod.system") + " " + state.system;
		state.bar.name(Component.text(text, state.authorityForm ? TextColor.color(0xFFF4D0) : TextColor.color(0xF0C860)));
		state.bar.progress(Math.max(0F, Math.min(1F, value / (float) MAX)));
		state.bar.color(state.authorityForm ? BossBar.Color.WHITE : BossBar.Color.YELLOW);
	}

	static void clearAll() {
		for (State state : STATES.values()) {
			state.god.hideBossBar(state.bar);
		}
		STATES.clear();
		NAILS.values().forEach(n -> {
			if (n.label != null) {
				n.label.remove();
			}
		});
		NAILS.clear();
		STOP.clear();
		PULSE.clear();
	}

	// --- being struck ------------------------------------------------------------------------

	static final class Events implements Listener {
		/** Defense levels, System Preservation, '보존의 규율대로' and Stop shape the damage the Black God takes. */
		@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
		public void onHurt(EntityDamageEvent event) {
			if (!(event.getEntity() instanceof Player god)) {
				return;
			}
			State state = state(god);
			if (state == null || !BlackGod.active(god)) {
				return;
			}
			double multiplier = 1.0 - Math.min(0.6, defenseLevels(state) * 0.02 + state.system * 0.01);
			switch (event.getCause()) {
				case FIRE, FIRE_TICK, POISON, WITHER, MAGIC -> multiplier *= 0.5;
				default -> {
				}
			}
			if (event instanceof EntityDamageByEntityEvent byEntity) {
				Entity damager = byEntity.getDamager();
				if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
					damager = shooter;
				}
				if (stop(damager)) {
					multiplier *= 1.25;
				}
			}
			event.setDamage(event.getDamage() * multiplier);
		}

		/** After the blow: the Bride steps in below half health (once); the first fall to a third raises the shield. */
		@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
		public void afterHurt(EntityDamageEvent event) {
			if (!(event.getEntity() instanceof Player god)) {
				return;
			}
			State state = state(god);
			if (state == null || !BlackGod.active(god)) {
				return;
			}
			double max = maxHealth(god);
			double after = god.getHealth() - event.getFinalDamage();
			if (after <= 0) {
				return;
			}
			if (!state.bridedHealed && after < max * 0.5) {
				state.bridedHealed = true;
				Bukkit.getScheduler().runTask(IndexPrescriptPlugin.instance(), () -> {
					if (!god.isOnline() || god.isDead()) {
						return;
					}
					god.setHealth(maxHealth(god));
					for (PotionEffectType bad : new PotionEffectType[] {PotionEffectType.POISON, PotionEffectType.WITHER,
							PotionEffectType.SLOWNESS, PotionEffectType.WEAKNESS, PotionEffectType.BLINDNESS, PotionEffectType.DARKNESS,
							PotionEffectType.NAUSEA, PotionEffectType.HUNGER, PotionEffectType.MINING_FATIGUE, PotionEffectType.LEVITATION}) {
						god.removePotionEffect(bad);
					}
					god.setFireTicks(0);
					god.getWorld().spawnParticle(Particle.END_ROD, god.getLocation().add(0, 1, 0), 40, 0.5, 1.0, 0.5, 0.1);
					god.getWorld().playSound(god.getLocation(), Sound.ITEM_TOTEM_USE, SoundCategory.PLAYERS, 0.8F, 1.4F);
					god.sendActionBar(Component.text(Messages.get(god, "blackgod.bride_steps_in"), TextColor.color(0xF0D070)));
				});
			} else if (!state.shielded && after <= max / 3.0) {
				state.shielded = true;
				double shield = max * (25 + state.system) / 100.0;
				state.system = 0;
				state.systemSpent = true;
				int amplifier = Math.max(0, (int) Math.ceil(shield / 4.0) - 1);
				Bukkit.getScheduler().runTask(IndexPrescriptPlugin.instance(), () -> {
					if (god.isOnline() && !god.isDead()) {
						god.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, PotionEffect.INFINITE_DURATION, amplifier, false, false, true));
						god.getWorld().playSound(god.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 1.2F, 0.6F);
						god.sendActionBar(Component.text(Messages.get(god, "blackgod.shield"), TextColor.color(0xF0D070)));
					}
				});
			}
		}
	}
}
