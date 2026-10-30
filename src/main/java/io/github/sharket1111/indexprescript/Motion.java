package io.github.sharket1111.indexprescript;

import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

final class Motion {
	private Motion() {
	}

	/**
	 * Adds horizontal velocity plus lift. Players own their movement on the
	 * client, so they are sent the new motion explicitly.
	 */
	static void push(Entity entity, Vec3 horizontal, double lift) {
		entity.push(horizontal.x, lift, horizontal.z);
		if (entity instanceof ServerPlayer player) {
			player.connection.send(new ClientboundSetEntityMotionPacket(player));
		}
	}
}
