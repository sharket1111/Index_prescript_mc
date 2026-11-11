package io.github.sharket1111.indexprescript.paper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.PiglinAbstract;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

/**
 * One battle node: waves of the Theme Pack's enemies (named after the
 * original's, from its pools), stronger with the floor, Hard mode, the party's
 * size and the Mounting Trials taken. A turn is 7 seconds: E.G.O Gifts fire at
 * turn start and end, Burn burns at turn end, and the boss strikes the ground
 * every other turn. The battle is won when the last wave falls, lost when every
 * member is down.
 */
final class MdBattle {
	private static final Map<UUID, Foe> FOES = new HashMap<>();

	/** An enemy's numbers: damage dealt and taken multipliers, its keyword, and debuffs until a tick. */
	static final class Foe {
		final MdBattle battle;
		final String name;
		final double damage;
		final double taken;
		final String keyword;
		final int potency;
		final boolean boss;
		int offenseDown;
		int defenseDown;
		long debuffUntil;

		Foe(MdBattle battle, String name, double damage, double taken, String keyword, int potency, boolean boss) {
			this.battle = battle;
			this.name = name;
			this.damage = damage;
			this.taken = taken;
			this.keyword = keyword;
			this.potency = potency;
			this.boss = boss;
		}
	}

	final MdRun run;
	final MdRun.NodeType type;
	private final String eventFoe;
	private final List<String> eventGifts;
	final List<LivingEntity> mobs = new ArrayList<>();
	private int wave;
	private int waves;
	private long nextTurn;
	private int turn;
	private int tick;
	private BossBar bar;
	private LivingEntity boss;
	private boolean over;

	MdBattle(MdRun run, MdRun.NodeType type, String eventFoe, List<String> eventGifts) {
		this.run = run;
		this.type = type;
		this.eventFoe = eventFoe;
		this.eventGifts = eventGifts;
	}

	static Foe foe(LivingEntity entity) {
		return FOES.get(entity.getUniqueId());
	}

	MdWorld.Room room() {
		return type == MdRun.NodeType.BOSS ? MdWorld.Room.BOSS : MdWorld.Room.BATTLE;
	}

	void start() {
		waves = switch (type) {
			case NORMAL, RISKY -> 2;
			default -> 1;
		};
		bar = org.bukkit.Bukkit.createBossBar(type.ko, type == MdRun.NodeType.BOSS ? BarColor.RED : BarColor.YELLOW, BarStyle.SEGMENTED_10);
		for (Player p : run.players()) {
			bar.addPlayer(p);
		}
		nextTurn = CaduceusListener.now() + 40;
		spawnWave();
	}

	// --- enemies --------------------------------------------------------------------------------

	private void spawnWave() {
		wave++;
		int players = Math.max(1, run.fighters().size());
		switch (type) {
			case NORMAL -> spawnMany("normal", 2 + players, false);
			case RISKY -> spawnMany("risky", 2 + players, false);
			case FOCUSED -> spawn(pick("focused", "normal"), Kind.FOCUSED);
			case ABNO -> spawn(eventFoe != null ? new MdData.Enemy(eventFoe, "waw") : pick("focused", "miniboss"), Kind.ABNO);
			case BOSS -> {
				MdData.Enemy b = run.theme.bosses().isEmpty() ? new MdData.Enemy(run.theme.boss().isBlank() ? "층의 주인" : run.theme.boss(), "human")
						: run.theme.bosses().get(run.random.nextInt(run.theme.bosses().size()));
				boss = spawn(b, Kind.BOSS);
				spawnMany("normal", Math.max(1, players - 1), false);
			}
			default -> {
			}
		}
		for (Player p : run.fighters()) {
			p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, SoundCategory.HOSTILE, 0.6F, 1.4F);
		}
		MdGifts.trigger(run, "combat_start", null, null);
	}

	private void spawnMany(String pool, int count, boolean ignored) {
		for (int i = 0; i < count; i++) {
			spawn(pick(pool, "normal"), type == MdRun.NodeType.RISKY ? Kind.RISKY : Kind.NORMAL);
		}
	}

	private MdData.Enemy pick(String pool, String fallback) {
		List<MdData.Enemy> list = run.theme.enemies().getOrDefault(pool, List.of());
		if (list.isEmpty()) {
			list = run.theme.enemies().getOrDefault(fallback, List.of());
		}
		if (list.isEmpty()) {
			for (List<MdData.Enemy> any : run.theme.enemies().values()) {
				if (!any.isEmpty()) {
					list = any;
					break;
				}
			}
		}
		return list.isEmpty() ? new MdData.Enemy(run.theme.name() + "의 적", "human") : list.get(run.random.nextInt(list.size()));
	}

	enum Kind {
		NORMAL(24, 1.0, 1.0F), RISKY(34, 1.15, 1.0F), FOCUSED(110, 1.25, 1.35F), ABNO(140, 1.3, 1.4F), BOSS(260, 1.4, 1.6F);

		final double health;
		final double damage;
		final float scale;

		Kind(double health, double damage, float scale) {
			this.health = health;
			this.damage = damage;
			this.scale = scale;
		}
	}

	private static final EntityType[] HUMAN = {EntityType.ZOMBIE, EntityType.HUSK, EntityType.VINDICATOR, EntityType.SKELETON, EntityType.PILLAGER,
		EntityType.STRAY, EntityType.WITHER_SKELETON, EntityType.PIGLIN_BRUTE};
	private static final EntityType[] MONSTER = {EntityType.SPIDER, EntityType.CAVE_SPIDER, EntityType.WITCH, EntityType.ZOGLIN, EntityType.BLAZE};

	private LivingEntity spawn(MdData.Enemy enemy, Kind kind) {
		boolean human = enemy.risk().equals("human");
		EntityType entity;
		if (kind == Kind.BOSS) {
			entity = human ? List.of(EntityType.VINDICATOR, EntityType.WITHER_SKELETON, EntityType.PIGLIN_BRUTE).get(run.random.nextInt(3))
					: List.of(EntityType.RAVAGER, EntityType.ZOGLIN).get(run.random.nextInt(2));
		} else if (human) {
			entity = HUMAN[run.random.nextInt(Math.min(HUMAN.length, 3 + run.floor))];
		} else {
			entity = MONSTER[run.random.nextInt(Math.min(MONSTER.length, 2 + run.floor))];
		}
		Location at = MdWorld.spawnSpot(run.slot, room(), run.random);
		if (kind == Kind.BOSS || kind == Kind.FOCUSED || kind == Kind.ABNO) {
			at = MdWorld.centre(run.slot).add(0, 0, room().half / 2.0);
		}
		LivingEntity mob = (LivingEntity) at.getWorld().spawnEntity(at, entity);
		int players = Math.max(1, run.fighters().size());
		double trialHp = MdGifts.trialStat(run, "max_hp") / 100.0;
		double health = kind.health * (1 + 0.45 * (run.floor - 1)) * (run.floorHard ? 1.25 : 1.0) * (1 + 0.6 * (players - 1)) * (1 + trialHp);
		AttributeInstance max = mob.getAttribute(Attribute.MAX_HEALTH);
		if (max != null) {
			max.setBaseValue(Math.min(2048, health));
			mob.setHealth(max.getValue());
		}
		AttributeInstance scale = mob.getAttribute(Attribute.SCALE);
		if (scale != null && kind.scale != 1.0F) {
			scale.setBaseValue(kind.scale);
		}
		AttributeInstance follow = mob.getAttribute(Attribute.FOLLOW_RANGE);
		if (follow != null) {
			follow.setBaseValue(48);
		}
		if (kind == Kind.BOSS) {
			AttributeInstance knock = mob.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
			if (knock != null) {
				knock.setBaseValue(0.8);
			}
		}
		mob.customName(Component.text(enemy.name(), kind == Kind.BOSS ? TextColor.color(0xE5574B) : kind == Kind.NORMAL ? NamedTextColor.WHITE
				: NamedTextColor.GOLD));
		mob.setCustomNameVisible(true);
		mob.setRemoveWhenFarAway(false);
		mob.setPersistent(true);
		mob.setCanPickupItems(false);
		if (mob instanceof PiglinAbstract piglin) {
			piglin.setImmuneToZombification(true);
		}
		EntityEquipment eq = mob.getEquipment();
		if (eq != null) {
			if (entity == EntityType.ZOMBIE || entity == EntityType.HUSK || entity == EntityType.WITHER_SKELETON || entity == EntityType.VINDICATOR) {
				eq.setItemInMainHand(new ItemStack(run.floor >= 4 ? Material.DIAMOND_SWORD : run.floor >= 2 ? Material.IRON_SWORD : Material.STONE_SWORD));
			}
			eq.setItemInMainHandDropChance(0);
			eq.setHelmetDropChance(0);
		}
		double trialDamage = MdGifts.trialStat(run, "damage_dealt") + 2 * MdGifts.trialStat(run, "offense_level") + 5 * (MdGifts.trialStat(run, "clash_power")
				+ MdGifts.trialStat(run, "final_power") + MdGifts.trialStat(run, "base_power") + MdGifts.trialStat(run, "plus_coin_power"));
		double damage = kind.damage * (1 + 0.2 * (run.floor - 1)) * (run.floorHard ? 1.2 : 1.0) * (1 + trialDamage / 100.0) * (1 + 0.02 * run.trials.size());
		double taken = (1 + MdGifts.trialStat(run, "damage_taken") / 100.0) / (1 + 0.03 * MdGifts.trialStat(run, "defense_level"));
		String keyword = run.theme.keyword();
		if (keyword == null || keyword.equals("poise") || keyword.equals("charge")) {
			keyword = List.of("burn", "bleed", "tremor", "rupture", "sinking").get(Math.floorMod(run.theme.id().hashCode() + mobs.size(), 5));
		}
		FOES.put(mob.getUniqueId(), new Foe(this, enemy.name(), damage, taken, keyword, 1 + run.floor / 2 + (kind == Kind.BOSS ? 2 : 0), kind == Kind.BOSS));
		mob.getWorld().spawnParticle(Particle.REVERSE_PORTAL, mob.getLocation().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.05);
		mobs.add(mob);
		return mob;
	}

	// --- running ----------------------------------------------------------------------------------

	void tick() {
		if (over) {
			return;
		}
		tick++;
		long now = CaduceusListener.now();
		mobs.removeIf(m -> !m.isValid() || m.isDead());
		List<Player> fighters = run.fighters();
		if (fighters.isEmpty()) {
			return;
		}
		if (tick % 20 == 0) {
			for (LivingEntity m : mobs) {
				if (m instanceof Mob mob && (mob.getTarget() == null || !(mob.getTarget() instanceof Player t) || run.downed.contains(t.getUniqueId()))) {
					mob.setTarget(nearest(m, fighters));
				}
				if (MdWorld.fell(m.getLocation())) {
					m.teleport(MdWorld.centre(run.slot));
				}
			}
		}
		// Burn on everyone in the fight, as turns pass for them.
		for (LivingEntity m : List.copyOf(mobs)) {
			burn(m, now);
		}
		for (Player p : fighters) {
			burn(p, now);
		}
		if (now >= nextTurn) {
			nextTurn = now + CombatState.TURN_TICKS;
			if (turn > 0) {
				MdGifts.trigger(run, "turn_end", null, null);
			}
			turn++;
			MdGifts.trigger(run, "turn_start", null, null);
			if (boss != null && boss.isValid() && turn % 2 == 0) {
				slam(boss, fighters);
			}
		}
		updateBar();
		if (mobs.isEmpty()) {
			if (wave < waves) {
				spawnWave();
			} else {
				win();
			}
		}
	}

	private void burn(LivingEntity e, long now) {
		int damage = CombatState.of(e).tickBurn(now);
		if (damage > 0 && e.isValid() && !e.isDead()) {
			Damage.dealFixed(e, damage * Rupture.DAMAGE_PER_POTENCY, null);
			e.getWorld().spawnParticle(Particle.FLAME, e.getLocation().add(0, e.getHeight() / 2, 0), 12 + damage, 0.3, 0.5, 0.3, 0.02);
		}
	}

	private static Player nearest(LivingEntity from, List<Player> players) {
		Player best = null;
		double bestD = Double.MAX_VALUE;
		for (Player p : players) {
			double d = p.getLocation().distanceSquared(from.getLocation());
			if (d < bestD) {
				bestD = d;
				best = p;
			}
		}
		return best;
	}

	/** The boss strikes the ground: everyone near is hit and takes the Theme Pack's keyword. */
	private void slam(LivingEntity boss, List<Player> players) {
		Foe foe = foe(boss);
		Location at = boss.getLocation();
		at.getWorld().playSound(at, Sound.ENTITY_RAVAGER_ROAR, SoundCategory.HOSTILE, 1.4F, 0.8F);
		at.getWorld().spawnParticle(Particle.EXPLOSION, at, 6, 2, 0.2, 2, 0);
		at.getWorld().spawnParticle(Particle.DUST, at.clone().add(0, 0.3, 0), 120, 3, 0.1, 3, 0,
				new Particle.DustOptions(org.bukkit.Color.fromRGB(run.theme.color()), 2.0F));
		for (Player p : players) {
			if (p.getLocation().distanceSquared(at) <= 25) {
				Damage.deal(p, 4 * (foe == null ? 1 : foe.damage), boss, true);
				if (foe != null) {
					MdGifts.inflictKeyword(p, foe.keyword, foe.potency + 1, 1);
				}
			}
		}
	}

	private void updateBar() {
		if (bar == null) {
			return;
		}
		if (boss != null && boss.isValid()) {
			AttributeInstance max = boss.getAttribute(Attribute.MAX_HEALTH);
			bar.setTitle(foe(boss) == null ? "보스" : foe(boss).name);
			bar.setProgress(Math.max(0, Math.min(1, boss.getHealth() / (max == null ? 1 : max.getValue()))));
		} else {
			bar.setTitle(type.ko + " · 웨이브 " + wave + "/" + waves + " · 남은 적 " + mobs.size() + " · " + turn + "턴");
			bar.setProgress(Math.max(0, Math.min(1, mobs.size() / 8.0)));
		}
	}

	private void win() {
		over = true;
		stop();
		for (Player p : run.players()) {
			p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7F, 1.2F);
			p.removePotionEffect(PotionEffectType.SLOWNESS);
			p.removePotionEffect(PotionEffectType.WEAKNESS);
		}
		run.battleWon(type, eventGifts);
	}

	void stop() {
		over = true;
		for (LivingEntity m : mobs) {
			FOES.remove(m.getUniqueId());
			m.remove();
		}
		mobs.clear();
		if (bar != null) {
			bar.removeAll();
		}
	}

	/** A debuff from a gift on an enemy: weaker attacks or softer defence until next turn, slowed, or weakened. */
	static void debuff(LivingEntity target, String what, int amount) {
		Foe foe = foe(target);
		long now = CaduceusListener.now();
		switch (what) {
			case "offense_level_down" -> {
				if (foe != null) {
					foe.offenseDown += amount;
					foe.debuffUntil = now + CombatState.TURN_TICKS;
				}
			}
			case "defense_level_down", "fragile" -> {
				if (foe != null) {
					foe.defenseDown += amount;
					foe.debuffUntil = now + CombatState.TURN_TICKS;
				}
			}
			case "bind" -> target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, CombatState.TURN_TICKS, Math.min(3, amount - 1)));
			case "paralysis" -> target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, CombatState.TURN_TICKS, Math.min(3, amount - 1)));
			default -> {
			}
		}
	}

	static void forget(LivingEntity entity) {
		FOES.remove(entity.getUniqueId());
	}
}
