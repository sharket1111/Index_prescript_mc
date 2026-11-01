package io.github.sharket1111.indexprescript.paper;

import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

/**
 * Voice lines from the resource pack (Java) and the Bedrock pack (Geyser),
 * played on the speaker so players nearby hear them too.
 */
final class Voice {
	private static final int PRESCRIPT_LINES = 9;
	private static final float VOLUME = 1.2F;

	private Voice() {
	}

	/**
	 * Rien's line for completing a prescript. Line N goes with reaching N
	 * [Hermes]; a completion that grants none plays a random line.
	 */
	static void prescriptComplete(Player player, int hermes) {
		int line = hermes >= 1 && hermes <= PRESCRIPT_LINES ? hermes : 1 + ThreadLocalRandom.current().nextInt(PRESCRIPT_LINES);
		play(player, "voice.rien.prescript_" + line);
	}

	private static void play(Player player, String key) {
		var speaker = Cinematic.body(player);
		speaker.getWorld().playSound(speaker, Items.NAMESPACE + ":" + key, SoundCategory.VOICE, VOLUME, 1.0F);
	}
}
