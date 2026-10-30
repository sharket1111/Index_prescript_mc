package io.github.sharket1111.indexprescript;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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
	private static final int FORM_TICKS = 40;
	// Game time at which each player's formed weapon melts back. Server-side only.
	private static final Map<UUID, Long> REVERT_AT = new ConcurrentHashMap<>();

	public CaduceusItem(Properties properties) {
		super(properties);
	}

	/** AttackEntityCallback: forms a new weapon before the hit is resolved. */
	public static InteractionResult onAttack(Player player, Level level, InteractionHand hand, Entity target, EntityHitResult hitResult) {
		ItemStack stack = player.getItemInHand(hand);
		if (!(level instanceof ServerLevel serverLevel) || !(stack.getItem() instanceof CaduceusItem) || player.isSpectator()) {
			return InteractionResult.PASS;
		}

		CaduceusForm form = CaduceusForm.random(level.getRandom(), CaduceusForm.of(stack).orElse(null));
		form.applyTo(stack);
		REVERT_AT.put(player.getUUID(), level.getGameTime() + FORM_TICKS);

		CaduceusEffects.form(serverLevel, player);
		CaduceusEffects.impact(serverLevel, target);
		if (player instanceof ServerPlayer serverPlayer) {
			serverPlayer.sendOverlayMessage(Component.translatable("message.index_prescript.caduceus_formed", form.displayName())
					.withStyle(ChatFormatting.GOLD));
		}
		return InteractionResult.PASS;
	}

	@Override
	public float getAttackDamageBonus(Entity target, float damage, DamageSource source) {
		if (source.getEntity() instanceof LivingEntity attacker) {
			return CaduceusForm.of(attacker.getMainHandItem()).map(CaduceusForm::bonusDamage).orElse(0.0F);
		}
		return 0.0F;
	}

	@Override
	public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		super.postHurtEnemy(stack, target, attacker);
		if (target.level() instanceof ServerLevel level) {
			CaduceusForm.of(stack).ifPresent(form -> form.onHit(level, attacker, target));
		}
	}

	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
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
