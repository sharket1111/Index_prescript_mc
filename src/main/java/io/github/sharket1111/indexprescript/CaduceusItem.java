package io.github.sharket1111.indexprescript;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Rien's Caduceus: a brass rod that solidifies liquid metal into a random
 * weapon on every attack, then melts back into the rod shortly after.
 */
public class CaduceusItem extends Item {
	private static final Component SEPARATOR = Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY);
	private static final int FORM_TICKS = 40;
	private static final float CHARGED_THRESHOLD = 0.9F;
	private static final int PRUNE_INTERVAL = 1200;
	// Server-side per-player attack bookkeeping, keyed by player UUID.
	private static final Map<UUID, Long> REVERT_AT = new ConcurrentHashMap<>();
	private static final Map<UUID, Boolean> CHARGED = new ConcurrentHashMap<>();
	private static final Map<UUID, Boolean> LAST_CRIT = new ConcurrentHashMap<>();

	public CaduceusItem(Properties properties) {
		super(properties);
	}

	/**
	 * AttackEntityCallback: forms a new weapon before the hit is resolved. Only a
	 * charged swing forms a new weapon and triggers its on-hit effect, so spam
	 * clicking cannot stack statuses.
	 */
	public static InteractionResult onAttack(Player player, Level level, InteractionHand hand, Entity target, EntityHitResult hitResult) {
		ItemStack stack = player.getItemInHand(hand);
		if (!(level instanceof ServerLevel serverLevel) || !(stack.getItem() instanceof CaduceusItem) || player.isSpectator()) {
			return InteractionResult.PASS;
		}

		if (Furioso.active(player)) {
			return InteractionResult.FAIL;
		}
		if (player instanceof ServerPlayer serverPlayer && target instanceof LivingEntity living && living.isAlive()
				&& PrescriptTracker.ready(serverPlayer)) {
			Furioso.start(serverPlayer, serverLevel, living);
			return InteractionResult.FAIL;
		}

		boolean charged = player.getAttackStrengthScale(0.5F) >= CHARGED_THRESHOLD;
		CHARGED.put(player.getUUID(), charged);
		CaduceusForm current = CaduceusForm.of(stack).orElse(null);
		if (charged || current == null) {
			current = CaduceusForm.random(level.getRandom(), current);
			current.applyTo(stack);
			CaduceusEffects.form(serverLevel, player);
		}
		REVERT_AT.put(player.getUUID(), level.getGameTime() + FORM_TICKS);
		CaduceusEffects.impact(serverLevel, target);
		return InteractionResult.PASS;
	}

	/** Keeps the current form from melting back for at least {@code ticks}. */
	static void keepFormed(Player player, long now, int ticks) {
		REVERT_AT.merge(player.getUUID(), now + ticks, Math::max);
	}

	/**
	 * Scales the incoming damage (which already includes attack cooldown,
	 * vanilla crits and enchantments) by the form's damage relative to the bare rod.
	 */
	@Override
	public float getAttackDamageBonus(Entity target, float damage, DamageSource source) {
		if (!(source.getEntity() instanceof LivingEntity attacker) || !(attacker.level() instanceof ServerLevel level)) {
			return 0.0F;
		}
		Optional<CaduceusForm> form = CaduceusForm.of(attacker.getMainHandItem());
		if (form.isEmpty()) {
			return 0.0F;
		}
		CaduceusForm.Hit hit = form.get().resolve(level, attacker, target);
		LAST_CRIT.put(attacker.getUUID(), hit.crit());
		return damage * (hit.damage() / CaduceusForm.ROD_DAMAGE - 1.0F);
	}

	@Override
	public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		super.postHurtEnemy(stack, target, attacker);
		if (!(target.level() instanceof ServerLevel level)) {
			return;
		}
		Optional<CaduceusForm> form = CaduceusForm.of(stack);
		if (form.isEmpty()) {
			return;
		}
		boolean crit = Boolean.TRUE.equals(LAST_CRIT.remove(attacker.getUUID()));
		if (crit) {
			CaduceusEffects.crit(level, target);
		}
		boolean charged = !Boolean.FALSE.equals(CHARGED.get(attacker.getUUID()));
		boolean staggered = charged && form.get().onHit(level, attacker, target);
		if (attacker instanceof ServerPlayer player) {
			long now = level.getGameTime();
			player.sendOverlayMessage(status(form.get(), CombatState.of(player), now, crit, staggered));
			PrescriptTracker.holdOverlay(player, now);
			if (charged) {
				PrescriptTracker.onHit(player, level, form.get(), crit, staggered);
			}
		}
	}

	private static Component status(CaduceusForm form, CombatState self, long now, boolean crit, boolean staggered) {
		MutableComponent line = Component.translatable("message.index_prescript.caduceus_formed", form.displayName())
				.withStyle(ChatFormatting.GOLD);
		if (crit) {
			line.append(SEPARATOR).append(Component.translatable("message.index_prescript.crit").withStyle(ChatFormatting.YELLOW));
		}
		if (staggered) {
			line.append(SEPARATOR).append(Component.translatable("message.index_prescript.staggered").withStyle(ChatFormatting.RED));
		}
		int poise = self.poisePotency(now);
		if (poise > 0) {
			line.append(SEPARATOR).append(Component.translatable("message.index_prescript.poise", poise, self.poiseCount(now))
					.withStyle(ChatFormatting.AQUA));
		}
		return line;
	}

	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
		if (level.getGameTime() % PRUNE_INTERVAL == 0) {
			CombatState.prune(level.getGameTime());
		}
		if (CaduceusForm.of(stack).isEmpty()) {
			return;
		}
		Long revertAt = REVERT_AT.get(entity.getUUID());
		if (revertAt == null || level.getGameTime() >= revertAt) {
			CaduceusForm.clear(stack);
			REVERT_AT.remove(entity.getUUID());
			CaduceusEffects.melt(level, entity);
		}
	}

	@Override
	public Component getName(ItemStack stack) {
		return CaduceusForm.of(stack)
				.<Component>map(form -> Component.translatable("item.index_prescript.caduceus.formed", form.displayName()))
				.orElseGet(() -> super.getName(stack));
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		tooltip.accept(Component.translatable("item.index_prescript.caduceus.desc").withStyle(ChatFormatting.GRAY));
	}
}
