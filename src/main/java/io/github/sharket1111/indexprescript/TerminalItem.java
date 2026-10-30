package io.github.sharket1111.indexprescript;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * Right-click to receive a random prescript. Prescripts are translation keys
 * {@code message.index_prescript.prescript.<n>} for n in [0, PRESCRIPT_COUNT).
 */
public class TerminalItem extends Item {
	private static final int PRESCRIPT_COUNT = 10;
	private static final int COOLDOWN_TICKS = 100;

	public TerminalItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer serverPlayer) {
			int index = level.getRandom().nextInt(PRESCRIPT_COUNT);
			serverPlayer.sendSystemMessage(Component.translatable("message.index_prescript.prescript_header")
					.withStyle(ChatFormatting.AQUA));
			serverPlayer.sendSystemMessage(Component.translatable("message.index_prescript.prescript." + index)
					.withStyle(ChatFormatting.WHITE));
			level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.4F);
			player.getCooldowns().addCooldown(player.getItemInHand(hand), COOLDOWN_TICKS);
		}
		return InteractionResult.SUCCESS;
	}
}
