package io.github.sharket1111.indexprescript;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Rien's terminal. Held in the off hand, it receives prescripts on the action
 * bar (see {@link PrescriptTracker}) and its stack size shows the [Hermes]
 * count, glinting at full; right-click prints the current prescript and
 * [Hermes] count to chat.
 */
public class TerminalItem extends Item {
	private static final int COOLDOWN_TICKS = 20;

	public TerminalItem(Properties properties) {
		super(properties);
	}

	/** Keeps the off-hand stack size equal to [Hermes]; terminals anywhere else stay single. */
	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
		int count = 1;
		if (slot == EquipmentSlot.OFFHAND && entity instanceof ServerPlayer player) {
			count = Math.max(1, PrescriptTracker.of(player).hermes());
		}
		if (stack.getCount() != count) {
			stack.setCount(count);
		}
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return stack.getCount() >= PrescriptTracker.MAX_HERMES;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer serverPlayer) {
			PrescriptTracker tracker = PrescriptTracker.of(player);
			serverPlayer.sendSystemMessage(Component.translatable("message.index_prescript.prescript_header")
					.withStyle(ChatFormatting.AQUA));
			if (tracker.prescript() == null) {
				serverPlayer.sendSystemMessage(Component.translatable("message.index_prescript.prescript_none")
						.withStyle(ChatFormatting.GRAY));
			} else {
				serverPlayer.sendSystemMessage(tracker.line());
				serverPlayer.sendSystemMessage(tracker.hermesText());
			}
			level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.4F);
			player.getCooldowns().addCooldown(player.getItemInHand(hand), COOLDOWN_TICKS);
		}
		return InteractionResult.SUCCESS;
	}
}
