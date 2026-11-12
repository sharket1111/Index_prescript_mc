package io.github.sharket1111.indexprescript.paper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.DyedItemColor;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * How the Mirror Dungeon's enemies look and show their health, after the
 * original's:
 * <ul>
 * <li><b>Bodies.</b> The mob underneath is only a hitbox, invisible; on it
 * stands the plugin's player rig (head, body, arms, legs) in the enemy's
 * outfit (one per faction or kind, tools/md_looks.py), sized and proportioned
 * for its kind (crabs wide, gnomes small, Abnormalities large with long
 * arms), holding its weapon. It breathes, walks, raises and swings its arm on
 * each coin, raises its guard, and slumps when staggered. An Abnormality's
 * parts show as wings or a tail, and a broken arm disappears. An outfit can be
 * replaced with a skin PNG in plugins/IndexPrescript/enemy_skins/
 * ({@link SkinPack}).</li>
 * <li><b>Health bar.</b> Above each enemy (and each part): the HP number in
 * red (in the original's scale), a long red bar with the stagger thresholds
 * marked on it, a round level badge, the name, and its statuses (with the
 * original's own statuses, such as Ember, as named stacks).</li>
 * </ul>
 */
final class MdLooks {
	private static final Map<UUID, Body> BODIES = new ConcurrentHashMap<>();
	/** Models playing their death before they go, with where they fell. */
	private static final Map<MdModels.Inst, Location> DYING = new ConcurrentHashMap<>();
	private static final List<String> BONES = Animations.BONES;
	private static final float RIG_SCALE = 0.9375F;
	private static final Key BAR_FONT = Key.key(Items.NAMESPACE, "md_hpbar");
	private static final char BAR_FIRST = '';
	private static final int BAR_STEPS = 50;
	private static final int BAR_ADVANCE = 83;
	private static final char TICK = '';
	private static final char BADGE = '';
	private static final char PLUS = '';
	private static final char MINUS = '';
	private static final TextColor HP_RED = TextColor.color(0xE0332B);

	/** One enemy's (or part's) displays and animation state. */
	static final class Body {
		final LivingEntity mob;
		final LivingEntity core;
		final String icon;
		final float scale;
		final Map<String, float[]> boneScale;
		final ItemDisplay[] bones = new ItemDisplay[6];
		final boolean[] hidden = new boolean[6];
		final List<ItemDisplay> extras = new ArrayList<>();
		ItemDisplay weapon;
		TextDisplay bar;
		boolean creature;
		double walk;
		double walkAmount;
		Location last;
		long swingAt = -100;
		long hurtAt = -100;
		String shown = "";
		/** A broken part: its wings or tail are not drawn. */
		boolean off;
		/** For a part: what the core's model calls it. */
		String key = "";
		/** Its 3D model, if it has one (else the rig's six bones above). */
		MdModels.Inst model;

		Body(LivingEntity mob, LivingEntity core, String icon, float scale, Map<String, float[]> boneScale) {
			this.mob = mob;
			this.core = core;
			this.icon = icon;
			this.scale = scale;
			this.boneScale = boneScale;
		}
	}

	private MdLooks() {
	}

	/** The size an enemy is drawn at (its hitbox is scaled to match). */
	static float scaleOf(String name, MdBattle.Kind kind) {
		MdData.Look look = MdData.look(name);
		MdModels.Def model = SkinPack.enemyOutfit(name) == null ? MdModels.of(name) : null;
		float base = model != null ? model.scale() : look == null ? 1.0F : look.scale();
		return base * switch (kind) {
			case BOSS -> 1.25F;
			case FOCUSED, ABNO -> 1.1F;
			default -> 1.0F;
		};
	}

	/** Hides the hitbox mob and stands the enemy's body on it. */
	static void dress(LivingEntity mob, MdBattle.Foe foe, MdBattle.Kind kind) {
		MdData.Look look = MdData.look(foe.name);
		float scale = scaleOf(foe.name, kind);
		String custom = SkinPack.enemyOutfit(foe.name);
		MdModels.Def def = custom == null ? MdModels.of(foe.name) : null;
		hideHitbox(mob, def == null ? scale : Math.max(0.3F, scale * def.height() / 32F));
		Body body = new Body(mob, mob, "Body", scale, look == null ? Map.of() : look.bones());
		body.creature = foe.data != null && !foe.data.risk().isEmpty();
		if (def != null) {
			Location spot = flat(mob.getLocation());
			body.model = new MdModels.Inst(def, scale, spot);
			body.bar = spawnBar(spot, kind == MdBattle.Kind.BOSS ? 0.75F : 0.6F);
			BODIES.put(mob.getUniqueId(), body);
			return;
		}
		String model = custom != null ? SkinPack.MODEL : "index_prescript:rig";
		String outfit = custom != null ? custom : "md_" + (look == null ? "named" : look.look());
		Location at = flat(mob.getLocation());
		for (int i = 0; i < BONES.size(); i++) {
			ItemStack stack = modelled(Material.STICK, model, List.of(outfit, BONES.get(i)));
			body.bones[i] = at.getWorld().spawn(at, ItemDisplay.class, d -> setup(d, stack));
		}
		String weapon = look == null ? "iron_sword" : look.weapon();
		Material item = weapon.isEmpty() ? null : Material.matchMaterial(weapon);
		if (item != null && item.isItem()) {
			body.weapon = at.getWorld().spawn(at, ItemDisplay.class, d -> setup(d, new ItemStack(item)));
		}
		body.bar = spawnBar(at, kind == MdBattle.Kind.BOSS ? 0.75F : 0.6F);
		BODIES.put(mob.getUniqueId(), body);
		pose(body, CaduceusListener.now());
	}

	/** A part of an Abnormality ({@code icon}: Back, Tail, Head, Right Arm...): its hitbox follows the core; wings and tails are drawn. */
	static void dressPart(LivingEntity part, LivingEntity core, String name, String icon, int colour) {
		hideHitbox(part, 0.6F);
		Body coreBody = BODIES.get(core.getUniqueId());
		Body body = new Body(part, core, icon, coreBody == null ? 1.0F : coreBody.scale, Map.of());
		Location at = flat(part.getLocation());
		// A model names its parts by the original's part name, or by its icon.
		if (coreBody != null && coreBody.model != null) {
			body.key = coreBody.model.def.parts().containsKey(name) ? name : icon;
		}
		boolean modelled = coreBody != null && coreBody.model != null && coreBody.model.def.parts().containsKey(body.key);
		if (modelled) {
			// The core's model draws this part itself.
		} else if (icon.equals("Back")) {
			for (int i = 0; i < 2; i++) {
				body.extras.add(at.getWorld().spawn(at, ItemDisplay.class, d -> setup(d, dyed("index_prescript:md_wing", colour))));
			}
		} else if (icon.equals("Tail")) {
			body.extras.add(at.getWorld().spawn(at, ItemDisplay.class, d -> setup(d, dyed("index_prescript:md_tail", colour))));
		} else if (icon.endsWith("Eye")) {
			body.extras.add(at.getWorld().spawn(at, ItemDisplay.class, d -> {
				setup(d, new ItemStack(Material.ENDER_EYE));
				d.setGlowing(true);
				d.setGlowColorOverride(Color.fromRGB(colour));
			}));
		}
		body.bar = spawnBar(at, 0.45F);
		BODIES.put(part.getUniqueId(), body);
	}

	/** The core's rig shows or hides the bone a part stands for (a broken arm goes). */
	static void partShown(LivingEntity part, boolean shown) {
		Body body = BODIES.get(part.getUniqueId());
		if (body == null) {
			return;
		}
		body.off = !shown;
		Body core = BODIES.get(body.core.getUniqueId());
		int bone = switch (body.icon) {
			case "Right Arm" -> 2;
			case "Left Arm" -> 3;
			default -> -1;
		};
		if (core != null && bone >= 0) {
			core.hidden[bone] = !shown;
		}
		if (core != null && core.model != null) {
			core.model.show(body.key, shown);
		}
	}

	/** A coin's strike: the attack clip for the skill's kind (slash, pierce, blunt), from the part striking if the model has one for it. */
	static void swing(LivingEntity mob, String type, String part) {
		Body body = BODIES.get(mob.getUniqueId());
		if (body == null) {
			return;
		}
		body.swingAt = CaduceusListener.now();
		if (body.model != null) {
			boolean played = part != null && body.model.play("attack_" + part.toLowerCase(java.util.Locale.ROOT).replace(' ', '_'));
			if (!played && !body.model.play("attack_" + type)) {
				body.model.play("attack_slash");
			}
		}
	}

	/** A new pattern: the model's roar. */
	static void roar(LivingEntity mob) {
		Body body = BODIES.get(mob.getUniqueId());
		if (body != null && body.model != null) {
			body.model.play("roar");
		}
	}

	static void hurt(LivingEntity mob) {
		Body body = BODIES.get(mob.getUniqueId());
		if (body != null) {
			body.hurtAt = CaduceusListener.now();
			if (body.model != null) {
				body.model.hurtAt = body.hurtAt;
			}
		}
	}

	static void remove(LivingEntity mob) {
		Body body = BODIES.remove(mob.getUniqueId());
		if (body == null) {
			return;
		}
		if (mob.isDead()) {
			Location at = mob.getLocation().add(0, mob.getHeight() / 2, 0);
			at.getWorld().spawnParticle(Particle.SQUID_INK, at, 30, 0.4, 0.6, 0.4, 0.05);
		}
		for (ItemDisplay d : body.bones) {
			if (d != null) {
				d.remove();
			}
		}
		body.extras.forEach(ItemDisplay::remove);
		if (body.weapon != null) {
			body.weapon.remove();
		}
		if (body.model != null) {
			if (mob.isDead() && body.model.deathTicks() > 0) {
				// It falls before it goes.
				body.model.dyingAt = CaduceusListener.now();
				DYING.put(body.model, flat(mob.getLocation()));
			} else {
				body.model.remove();
			}
		}
		if (body.bar != null) {
			body.bar.remove();
		}
	}

	static void removeAll() {
		for (Body body : List.copyOf(BODIES.values())) {
			remove(body.mob);
		}
		DYING.keySet().forEach(MdModels.Inst::remove);
		DYING.clear();
	}

	// --- every tick -------------------------------------------------------------------------------

	static void tick() {
		long now = CaduceusListener.now();
		for (Map.Entry<MdModels.Inst, Location> dying : List.copyOf(DYING.entrySet())) {
			MdModels.Inst inst = dying.getKey();
			inst.tick(dying.getValue(), now, false, false);
			if (now - inst.dyingAt > inst.deathTicks()) {
				inst.remove();
				DYING.remove(inst);
			}
		}
		for (Body body : List.copyOf(BODIES.values())) {
			if (!body.mob.isValid() || body.mob.isDead()) {
				remove(body.mob);
				continue;
			}
			if (body.model != null) {
				MdBattle.Foe foe = MdBattle.foe(body.mob);
				body.model.tick(flat(body.mob.getLocation()), now, CombatState.of(body.mob).staggered(now),
						foe != null && foe.guardUntil > now && !foe.evading);
			}
			if (body.mob != body.core) {
				follow(body);
			}
			if (body.bones[0] != null) {
				pose(body, now);
			}
			if (body.bar != null) {
				Location at = flat(body.mob.getLocation()).add(0, body.mob.getHeight() + (body.mob == body.core ? 0.35 : 0.15), 0);
				if (body.bar.getLocation().distanceSquared(at) > 0.0004) {
					body.bar.teleport(at);
				}
				if (now % 4 == 0) {
					Component text = barText(body, now);
					String key = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(text)
							+ text.hashCode();
					if (!key.equals(body.shown)) {
						body.shown = key;
						body.bar.text(text);
					}
				}
			}
		}
	}

	/** A part's hitbox (and its wings or tail) kept where it sits on the core. */
	private static void follow(Body body) {
		LivingEntity core = body.core;
		if (!core.isValid()) {
			return;
		}
		float s = body.scale;
		float[] off = switch (body.icon) {
			case "Head" -> new float[] {0, 1.75F, 0};
			case "Right Arm" -> new float[] {0.5F, 1.15F, 0.1F};
			case "Left Arm" -> new float[] {-0.5F, 1.15F, 0.1F};
			case "Back" -> new float[] {0, 1.3F, -0.45F};
			case "Tail" -> new float[] {0, 0.6F, -0.75F};
			case "Leg" -> new float[] {0, 0.3F, 0.15F};
			case "Right Eye" -> new float[] {0.13F, 1.62F, 0.3F};
			case "Left Eye" -> new float[] {-0.13F, 1.62F, 0.3F};
			default -> new float[] {0, 1.0F, 0.4F};
		};
		Location base = flat(core.getLocation());
		Body coreBody = BODIES.get(core.getUniqueId());
		org.joml.Vector3f onModel = coreBody == null || coreBody.model == null ? null : coreBody.model.partAt(body.key);
		if (onModel != null) {
			off = new float[] {onModel.x / s, onModel.y / s, onModel.z / s};
		}
		Location at = frame(base, off[0] * s, off[1] * s - (float) body.mob.getHeight() / 2, off[2] * s);
		at.setYaw(base.getYaw());
		body.mob.teleport(at);
		long now = CaduceusListener.now();
		for (int i = 0; i < body.extras.size(); i++) {
			ItemDisplay d = body.extras.get(i);
			Location w = frame(base, off[0] * s, off[1] * s, off[2] * s);
			w.setYaw(base.getYaw());
			d.teleport(w);
			Matrix4f m = new Matrix4f();
			if (body.icon.equals("Back")) {
				// Two wings, beating slowly, spread from the back.
				float beat = (float) Math.sin(now / 6.0) * 0.25F;
				float side = i == 0 ? 1 : -1;
				m.rotateY(side * (0.35F + beat)).translate(side * 0.75F * s, 0.2F * s, 0).scale(1.6F * s * side, 1.6F * s, 1.6F * s);
			} else if (body.icon.equals("Tail")) {
				m.rotateY((float) Math.PI / 2 + (float) Math.sin(now / 8.0) * 0.3F).translate(0.5F * s, 0, 0).scale(1.4F * s);
			} else {
				m.scale(0.35F * s);
			}
			if (body.off) {
				m.scale(0);
			}
			d.setInterpolationDelay(0);
			d.setInterpolationDuration(2);
			d.setTransformationMatrix(new Matrix4f().scale(-1, 1, 1).mul(m).scale(-1, 1, 1));
		}
	}

	// --- the rig's pose --------------------------------------------------------------------------------

	private static final float[][] PIVOT = {{0, 24, 0}, {0, 24, 0}, {5, 22, 0}, {-5, 22, 0}, {2, 12, 0}, {-2, 12, 0}};

	private static float[] bs(Body body, String bone) {
		return body.boneScale.getOrDefault(bone, new float[] {1, 1, 1});
	}

	private static void pose(Body body, long now) {
		LivingEntity mob = body.mob;
		Location at = flat(mob.getLocation());
		// Walking: how far it moved since the last tick.
		if (body.last != null && body.last.getWorld() == at.getWorld()) {
			double moved = Math.hypot(at.getX() - body.last.getX(), at.getZ() - body.last.getZ());
			body.walkAmount = body.walkAmount * 0.8 + Math.min(1, moved * 6) * 0.2;
			body.walk += moved * 4;
		}
		body.last = at.clone();
		boolean staggered = CombatState.of(mob).staggered(now);
		MdBattle.Foe foe = MdBattle.foe(mob);
		boolean guard = foe != null && foe.guardUntil > now && !foe.evading;
		float t = now;
		float walk = (float) (Math.sin(body.walk) * body.walkAmount);
		float breathe = (float) Math.sin(t / 12.0);
		float[][] rot = new float[6][3];
		// Idle: arms a little out, the body breathing.
		rot[2][2] = 4 + breathe * 2;
		rot[3][2] = -4 - breathe * 2;
		rot[0][0] = breathe * 2;
		// Walking: legs and arms swing.
		rot[4][0] = 35 * walk;
		rot[5][0] = -35 * walk;
		rot[2][0] = -25 * walk;
		rot[3][0] = 25 * walk;
		if (body.creature) {
			rot[1][0] += 8;
			rot[0][0] += 6;
			rot[2][0] -= 18;
			rot[3][0] -= 18;
		}
		float lower = 0;
		if (staggered) {
			rot[1][0] = 22;
			rot[0][0] = 30;
			rot[2][0] = -10;
			rot[3][0] = -10;
			rot[2][2] = 12;
			rot[3][2] = -12;
			lower = 0.12F;
		} else if (guard) {
			rot[2][0] = -80;
			rot[3][0] = -80;
			rot[2][1] = 25;
			rot[3][1] = -25;
		}
		// A coin's strike: the arm goes up and comes down.
		float p = (now - body.swingAt) / 8F;
		if (p >= 0 && p <= 1) {
			float a = p < 0.35F ? -165 * Animations.ease("out", p / 0.35F) : -165 + 190 * Animations.ease("in", (p - 0.35F) / 0.65F);
			rot[2][0] = a;
			rot[2][2] = 0;
			rot[1][1] = p < 0.35F ? 10 : -15;
		}
		boolean flash = now - body.hurtAt < 4;
		// Proportions: legs, then body, then head and arms on top.
		float[] leg = bs(body, "right_leg");
		float[] trunk = bs(body, "body");
		float legTop = 12 * leg[1];
		float bodyTop = legTop + 12 * trunk[1];
		float[][] pivots = new float[6][];
		pivots[0] = new float[] {0, bodyTop, 0};
		pivots[1] = new float[] {0, bodyTop, 0};
		float armX = 4 * trunk[0] + bs(body, "right_arm")[0];
		pivots[2] = new float[] {armX, bodyTop - 2 * trunk[1], 0};
		pivots[3] = new float[] {-armX, bodyTop - 2 * trunk[1], 0};
		pivots[4] = new float[] {2 * trunk[0], legTop, 0};
		pivots[5] = new float[] {-2 * trunk[0], legTop, 0};
		Matrix4f arm = null;
		for (int i = 0; i < 6; i++) {
			ItemDisplay d = body.bones[i];
			if (d == null) {
				continue;
			}
			Matrix4f m = new Matrix4f().translate(0, -lower, 0).scale(body.scale * RIG_SCALE / 16F).translate(pivots[i][0], pivots[i][1], pivots[i][2])
					.rotateXYZ((float) Math.toRadians(rot[i][0]), (float) Math.toRadians(rot[i][1]), (float) Math.toRadians(rot[i][2]));
			float[] s = bs(body, BONES.get(i));
			m.scale(s[0], s[1], s[2]).scale(16F);
			if (i == 2) {
				arm = new Matrix4f(m);
			}
			if (body.hidden[i]) {
				m.scale(0);
			}
			move(d, at, m, flash);
		}
		if (body.weapon != null && arm != null) {
			// In the right hand, the blade pointing forward.
			Matrix4f w = new Matrix4f(arm).translate(0.03F, -0.62F, 0).rotateY((float) Math.toRadians(-90)).rotateZ((float) Math.toRadians(-45))
					.translate(0.28F, 0.28F, 0);
			if (body.hidden[2]) {
				w.scale(0);
			}
			move(body.weapon, at, w, false);
		}
	}

	private static void move(ItemDisplay d, Location at, Matrix4f studio, boolean flash) {
		if (d.getLocation().distanceSquared(at) > 0.0001 || Math.abs(d.getLocation().getYaw() - at.getYaw()) > 0.5) {
			d.teleport(at);
		}
		d.setInterpolationDelay(0);
		d.setInterpolationDuration(1);
		// The studio's x points to the enemy's right; the entity's own +x is its left.
		d.setTransformationMatrix(new Matrix4f().scale(-1, 1, 1).mul(studio).scale(-1, 1, 1));
		if (flash != d.isGlowing()) {
			d.setGlowing(flash);
			d.setGlowColorOverride(flash ? Color.fromRGB(0xFF4040) : null);
		}
	}

	// --- the health bar ------------------------------------------------------------------------------------

	private static TextDisplay spawnBar(Location at, float size) {
		return at.getWorld().spawn(at, TextDisplay.class, d -> {
			d.setPersistent(false);
			d.setBillboard(Display.Billboard.CENTER);
			d.setShadowed(true);
			d.setSeeThrough(false);
			d.setTeleportDuration(1);
			d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
			d.setBrightness(new Display.Brightness(15, 15));
			d.setLineWidth(400);
			d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(size, size, size), new Quaternionf()));
		});
	}

	private static Component font(String s, TextColor colour) {
		return Component.text(s, colour).font(BAR_FONT);
	}

	/** A run of space glyphs moving the pen by {@code px} pixels. */
	private static Component space(int px) {
		StringBuilder b = new StringBuilder();
		int n = Math.abs(px);
		for (int bit = 7; bit >= 0; bit--) {
			while (n >= (1 << bit)) {
				b.append((char) ((px < 0 ? MINUS : PLUS) + bit));
				n -= 1 << bit;
			}
		}
		return font(b.toString(), NamedTextColor.WHITE);
	}

	private static Component barText(Body body, long now) {
		LivingEntity mob = body.mob;
		MdBattle.Foe foe = MdBattle.foe(mob);
		MdFoes.Ai ai = MdFoes.of(mob);
		AttributeInstance maxAttribute = mob.getAttribute(Attribute.MAX_HEALTH);
		double max = maxAttribute == null ? 20 : maxAttribute.getValue();
		double fraction = Math.max(0, Math.min(1, mob.getHealth() / max));
		boolean broken = ai != null && ai.broken;
		int step = broken ? 0 : (int) Math.ceil(fraction * BAR_STEPS);
		Component line = Component.empty()
				.append(Component.text(broken ? "파괴" : String.valueOf(Math.round(mob.getHealth() * TotalDamage.SCALE)), broken ? NamedTextColor.GRAY : HP_RED)
						.decorate(TextDecoration.BOLD))
				.append(space(3))
				.append(font(String.valueOf((char) (BAR_FIRST + step)), broken ? NamedTextColor.DARK_GRAY : HP_RED));
		// Stagger thresholds marked on the bar.
		if (ai != null && !broken) {
			for (double th : ai.thresholds) {
				int x = 1 + (int) Math.round(th * 80);
				line = line.append(space(x - BAR_ADVANCE)).append(font(String.valueOf(TICK), TextColor.color(0x7FE3FF))).append(space(BAR_ADVANCE - x - 2));
			}
		}
		String level = foe == null ? "?" : String.valueOf(foe.level);
		line = line.append(space(3)).append(font(String.valueOf(BADGE), NamedTextColor.WHITE)).append(space(-15 + (15 - 6 * level.length()) / 2))
				.append(Component.text(level, TextColor.color(0xD8D8E0)))
				.append(space(15 - (15 - 6 * level.length()) / 2 - 6 * level.length()));
		String name = foe == null ? mob.getName() : foe.name;
		Component nameLine = Component.text(name, foe != null && foe.boss ? TextColor.color(0xF0C8C0) : NamedTextColor.WHITE);
		if (CombatState.of(mob).staggered(now)) {
			nameLine = nameLine.append(Component.text("  흐트러짐", NamedTextColor.YELLOW));
		} else if (foe != null && foe.guardUntil > now) {
			nameLine = nameLine.append(Component.text(foe.evading ? "  회피" : "  수비", NamedTextColor.GRAY));
		}
		Component text = line.appendNewline().append(nameLine);
		Component statuses = UnitHud.statuses(mob, now);
		Component stacks = foe == null || foe.stacks.isEmpty() ? null : stacks(foe);
		if (statuses != null || stacks != null) {
			Component row = statuses == null ? Component.empty() : statuses;
			if (stacks != null) {
				row = row.append(statuses == null ? Component.empty() : Component.text("  ")).append(stacks);
			}
			text = text.appendNewline().append(row);
		}
		return text;
	}

	/** The original's own statuses (Ember, Bloodfeast...): name and stack. */
	private static Component stacks(MdBattle.Foe foe) {
		Component out = Component.empty();
		boolean first = true;
		for (Map.Entry<String, Integer> e : foe.stacks.entrySet()) {
			if (e.getValue() <= 0) {
				continue;
			}
			out = out.append(Component.text((first ? "" : " ") + e.getKey() + " ", TextColor.color(0xE8C070)))
					.append(Component.text(String.valueOf(e.getValue()), TextColor.color(0xF0E0C0)));
			first = false;
		}
		return out;
	}

	// --- helpers ----------------------------------------------------------------------------------------------

	private static void hideHitbox(LivingEntity mob, float scale) {
		mob.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, false, false, false));
		mob.setSilent(true);
		mob.setCustomNameVisible(false);
		if (mob.getEquipment() != null) {
			mob.getEquipment().clear();
		}
		AttributeInstance s = mob.getAttribute(Attribute.SCALE);
		if (s != null) {
			s.setBaseValue(scale);
		}
	}

	private static void setup(ItemDisplay d, ItemStack stack) {
		d.setItemStack(stack);
		d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
		d.setPersistent(false);
		d.setTeleportDuration(1);
		d.setViewRange(1.0F);
	}

	private static ItemStack modelled(Material material, String model, List<String> data) {
		ItemStack stack = new ItemStack(material);
		stack.editMeta(meta -> {
			meta.setItemModel(NamespacedKey.fromString(model));
			CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
			cmd.setStrings(data);
			meta.setCustomModelDataComponent(cmd);
		});
		return stack;
	}

	private static ItemStack dyed(String model, int colour) {
		ItemStack stack = new ItemStack(Material.PAPER);
		stack.editMeta(meta -> meta.setItemModel(NamespacedKey.fromString(model)));
		stack.setData(DataComponentTypes.DYED_COLOR, DyedItemColor.dyedItemColor(Color.fromRGB(colour)));
		return stack;
	}

	private static Location flat(Location at) {
		Location l = at.clone();
		l.setPitch(0);
		return l;
	}

	/** A point in a frame facing {@code base}'s yaw: right, up, forward. */
	private static Location frame(Location base, float right, float up, float forward) {
		double yaw = Math.toRadians(base.getYaw());
		double sin = Math.sin(yaw);
		double cos = Math.cos(yaw);
		return base.clone().add(-right * cos - forward * sin, up, -right * sin + forward * cos);
	}
}
