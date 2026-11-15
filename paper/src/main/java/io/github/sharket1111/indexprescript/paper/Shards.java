package io.github.sharket1111.indexprescript.paper;

import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Egoshards, as in the original: each Sinner's own (for their weapons and E.G.O), and the universal Uptie &
 * Threadspinning only Shard that stands in for any. A Random crate gives 1-3 of a random Sinner's, a
 * Nominable one 1-3 of the Sinner picked.
 */
final class Shards {
	private Shards() {
	}

	/** A Sinner's shards plus the universal ones ({@code sinner} -1: universal only). */
	static int count(Player p, int sinner) {
		return Gacha.count(p, Gacha.Kind.SHARD) + (sinner >= 0 ? Gacha.count(p, Gacha.Kind.shard(sinner)) : 0);
	}

	/** Takes {@code n}: the Sinner's own first, then universal; false (nothing taken) if short. */
	static boolean take(Player p, int sinner, int n) {
		if (n <= 0) {
			return true;
		}
		if (count(p, sinner) < n) {
			return false;
		}
		int own = sinner >= 0 ? Math.min(n, Gacha.count(p, Gacha.Kind.shard(sinner))) : 0;
		if (own > 0) {
			Gacha.take(p, Gacha.Kind.shard(sinner), own);
		}
		return Gacha.take(p, Gacha.Kind.SHARD, n - own);
	}

	static void openRandom(Player p) {
		if (!Gacha.take(p, Gacha.Kind.CRATE_RANDOM, 1)) {
			return;
		}
		int s = ThreadLocalRandom.current().nextInt(Gacha.SINNERS.length);
		int n = 1 + ThreadLocalRandom.current().nextInt(3);
		Gacha.give(p, Gacha.Kind.shard(s), n);
		Hud.message(p, Component.text(Gacha.SINNERS_KO[s] + "의 에고 조각 +" + n, NamedTextColor.LIGHT_PURPLE));
	}

	static void openNominable(Player p) {
		if (Gacha.count(p, Gacha.Kind.CRATE_NOMINABLE) <= 0) {
			return;
		}
		MdGui gui = new MdGui(3, "지정 에고 조각 상자");
		for (int i = 0; i < Gacha.SINNERS.length; i++) {
			int s = i;
			gui.set(i < 6 ? 1 + i : 4 + i, MdGui.item(Material.AMETHYST_SHARD, Gacha.SINNERS_KO[i], NamedTextColor.LIGHT_PURPLE,
					"에고 조각 1~3개", "가진 조각 " + Gacha.count(p, Gacha.Kind.shard(i)) + "개"), () -> {
						if (Gacha.take(p, Gacha.Kind.CRATE_NOMINABLE, 1)) {
							int n = 1 + ThreadLocalRandom.current().nextInt(3);
							Gacha.give(p, Gacha.Kind.shard(s), n);
							Hud.message(p, Component.text(Gacha.SINNERS_KO[s] + "의 에고 조각 +" + n, NamedTextColor.LIGHT_PURPLE));
						}
						if (Gacha.count(p, Gacha.Kind.CRATE_NOMINABLE) > 0) {
							openNominable(p);
						} else {
							p.closeInventory();
						}
					});
		}
		gui.set(22, MdGui.item(Material.BARRIER, "닫기 (상자 " + Gacha.count(p, Gacha.Kind.CRATE_NOMINABLE) + "개)", NamedTextColor.WHITE),
				() -> p.closeInventory());
		gui.fill().open(p);
	}
}
