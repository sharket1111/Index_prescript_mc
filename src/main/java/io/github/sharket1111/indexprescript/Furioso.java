package io.github.sharket1111.indexprescript;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Furioso-Replica: a nine-coin flurry in which Caduceus reforms into a new
 * weapon for every strike while the target is held in place. Coins 1-8 draw
 * crossing white slashes; the screen cuts to black before the ninth coin, a
 * guaranteed scythe, reaps in a crescent.
 *
 * Skill text mapped to Minecraft:
 * - prescript mark: damage +10% (it is only ever cast through prescripts)
 * - own Poise + target Sinking, per 6: +1 final power (max 2); each power is damage +25%
 * - on use: gain Poise 3 / count 3; no [Hermes] gain this turn
 * - every hit: inflict Sinking 1 or gain Poise 1, alternating
 * - coin 9: scythe, critical damage +90%, Sinking count +3
 * - attack end: Burn 5, Bind 3, Fragile 3 next turn; consume every [Hermes]
 */
final class Furioso {
	private static final int[] COIN_TICKS = {8, 14, 20, 26, 32, 38, 44, 50, 66};
	private static final int WINDUP_TICK = 54;
	private static final int END_TICK = 76;
	private static final int LAST_COIN = COIN_TICKS.length - 1;
	// Slash angles (degrees from horizontal) chosen so consecutive slashes cross.
	private static final double[] SLASH_ANGLES = {35, -35, 75, -60, 10, -15, 55, -80};

	private static final float COIN_RATIO = 0.6F;
	private static final float PRESCRIPT_MARK_BONUS = 1.10F;
	private static final float POWER_DAMAGE_BONUS = 0.25F;
	private static final int POWER_STEP = 6;
	private static final int MAX_POWER = 2;
	private static final float FINAL_CRIT_MULTIPLIER = (1.2F + 0.9F) / 1.2F;

	private static final ResourceKey<DamageType> DAMAGE_TYPE = ResourceKey.create(Registries.DAMAGE_TYPE,
			Identifier.fromNamespaceAndPath(IndexPrescript.MOD_ID, "furioso"));

	private static final Map<UUID, Furioso> ACTIVE = new ConcurrentHashMap<>();

	private final ServerPlayer player;
	private final ServerLevel level;
	private final LivingEntity target;
	private final Vec3 anchor;
	private final boolean hadNoAi;
	private CaduceusForm lastForm;
	private int tick;

	private Furioso(ServerPlayer player, ServerLevel level, LivingEntity target) {
		this.player = player;
		this.level = level;
		this.target = target;
		this.anchor = target.position();
		this.hadNoAi = target instanceof Mob mob && mob.isNoAi();
	}

	static boolean active(Player player) {
		return ACTIVE.containsKey(player.getUUID());
	}

	static void start(ServerPlayer player, ServerLevel level, LivingEntity target) {
		Furioso furioso = new Furioso(player, level, target);
		if (ACTIVE.putIfAbsent(player.getUUID(), furioso) == null) {
			furioso.open();
		}
	}

	static void tickAll() {
		ACTIVE.values().removeIf(furioso -> !furioso.tick());
	}

	private void open() {
		long now = level.getGameTime();
		if (target instanceof Mob mob) {
			mob.setNoAi(true);
		}
		CombatState.of(player).addPoise(3, 3, now);
		PrescriptTracker.blockHermes(player, now + END_TICK + CombatState.TURN_TICKS);

		player.connection.send(new ClientboundSetTitlesAnimationPacket(4, 30, 10));
		player.connection.send(new ClientboundSetTitleTextPacket(Component.literal("Furioso-Replica").withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC)));
		player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("message.index_prescript.furioso_subtitle").withStyle(ChatFormatting.GRAY)));

		CaduceusEffects.auraBurst(level, player);
		level.playSound(null, player.blockPosition(), SoundEvents.BUCKET_FILL_LAVA, SoundSource.PLAYERS, 1.0F, 0.6F);
		level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 0.5F);
	}

	/** Advances one server tick; returns false once the sequence has ended. */
	private boolean tick() {
		if (!player.isAlive() || player.isRemoved() || player.level() != level || !target.isAlive() || target.isRemoved()) {
			finish();
			return false;
		}
		holdTarget();
		CaduceusItem.keepFormed(player, level.getGameTime(), 30);

		if (tick == WINDUP_TICK) {
			windup();
		}
		for (int coin = 0; coin < COIN_TICKS.length; coin++) {
			if (COIN_TICKS[coin] == tick) {
				strike(coin);
			}
		}
		if (tick >= END_TICK) {
			finish();
			return false;
		}
		tick++;
		return true;
	}

	private void holdTarget() {
		target.teleportTo(anchor.x, anchor.y, anchor.z);
		target.setDeltaMovement(Vec3.ZERO);
		if (tick % 10 == 0) {
			target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20, 9), player);
		}
	}

	/** The screen cuts to black while liquid metal gathers for the final coin. */
	private void windup() {
		player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, COIN_TICKS[LAST_COIN] - WINDUP_TICK + 2));
		CaduceusEffects.gather(level, target);
		level.playSound(null, player.blockPosition(), SoundEvents.BUCKET_FILL_LAVA, SoundSource.PLAYERS, 1.0F, 0.5F);
	}

	private void strike(int coin) {
		long now = level.getGameTime();
		boolean last = coin == LAST_COIN;
		CaduceusForm form = last ? CaduceusForm.SCYTHE : CaduceusForm.random(player.getRandom(), lastForm);
		lastForm = form;

		ItemStack stack = player.getMainHandItem();
		if (stack.getItem() instanceof CaduceusItem) {
			form.applyTo(stack);
		}
		level.getChunkSource().broadcastAndSend(player, new ClientboundAnimatePacket(player, ClientboundAnimatePacket.SWING_MAIN_HAND));

		CombatState self = CombatState.of(player);
		CombatState other = CombatState.of(target);
		int power = Math.min(MAX_POWER, (self.poisePotency(now) + other.sinkingPotency(now)) / POWER_STEP);
		CaduceusForm.Hit hit = form.resolve(level, player, target);
		float damage = hit.damage() * (last ? 1.0F : COIN_RATIO) * PRESCRIPT_MARK_BONUS * (1.0F + power * POWER_DAMAGE_BONUS);
		if (last) {
			damage *= FINAL_CRIT_MULTIPLIER;
		}

		// Coins land faster than the hurt cooldown; this damage type bypasses it (see data/minecraft/tags).
		DamageSource source = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DAMAGE_TYPE), player);
		target.hurtServer(level, source, damage);

		if (coin % 2 == 0) {
			other.addSinking(1, 1, now);
		} else {
			self.addPoise(1, 1, now);
		}

		CaduceusEffects.form(level, player);
		if (last) {
			other.addSinking(0, 3, now);
			CaduceusEffects.reap(level, player, target);
		} else {
			CaduceusEffects.slash(level, player, target, SLASH_ANGLES[coin], 1.0F);
		}
		if (hit.crit()) {
			CaduceusEffects.crit(level, target);
		}
		level.playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.9F, 0.8F + coin * 0.08F);
	}

	private void finish() {
		long now = level.getGameTime();
		if (target instanceof Mob mob) {
			mob.setNoAi(hadNoAi);
		}
		if (target.isAlive()) {
			target.igniteForSeconds(5.0F);
			target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, CombatState.TURN_TICKS * 2, 2), player);
			CombatState.of(target).setFragileAll(3, now, CombatState.TURN_TICKS * 2);
		}
		PrescriptTracker.consumeHermes(player, now + CombatState.TURN_TICKS);
		CaduceusItem.keepFormed(player, now, 20);
	}
}
