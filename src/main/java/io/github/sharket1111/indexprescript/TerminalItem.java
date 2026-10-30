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
 * Rien's terminal. Held in the off hand, it receives prescripts on the action
 * bar (see {@link PrescriptTracker}); right-click prints the current prescript
 * and [Hermes] count to chat.
 */
public class TerminalItem extends Item {
	private static final int COOLDOWN_TICKS = 20;

	public TerminalItem(Properties properties) {
		super(properties);
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
			}
			level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.4F);
			player.getCooldowns().addCooldown(player.getItemInHand(hand), COOLDOWN_TICKS);
		}
		return InteractionResult.SUCCESS;
	}
}
