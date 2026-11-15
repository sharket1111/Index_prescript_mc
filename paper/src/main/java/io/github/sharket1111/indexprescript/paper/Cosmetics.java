package io.github.sharket1111.indexprescript.paper;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.format.NamedTextColor;

/** The collection of cosmetics from the Pass, Mirror Projection and the Shop (banners, announcers, decorations). */
final class Cosmetics {
	private Cosmetics() {
	}

	static void open(Player p) {
		List<String> all = Season.getList(p.getUniqueId(), "cosmetics");
		MdGui gui = new MdGui(6, "보관함 (" + all.size() + ")");
		int i = 0;
		for (String id : all) {
			if (i >= 45) {
				break;
			}
			gui.set(i++, MdGui.item(Rewards.icon(id), Rewards.name(id), NamedTextColor.WHITE, "원작의 꾸미기 아이템 (모아 두기)"), (Runnable) null);
		}
		if (all.isEmpty()) {
			gui.set(22, MdGui.item(Material.BARRIER, "아직 없어요", NamedTextColor.GRAY, "림버스 패스·거울 투영 보상·상점 패키지에서 얻어요"), (Runnable) null);
		}
		gui.set(49, MdGui.item(Material.ARROW, "뒤로", NamedTextColor.WHITE), () -> Ego.Menu.open(p));
		gui.fill().open(p);
	}
}
