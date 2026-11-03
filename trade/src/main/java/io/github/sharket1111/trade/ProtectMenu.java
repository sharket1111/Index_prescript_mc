package io.github.sharket1111.trade;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Setting protected zones (admins): a square of chunks around where the admin
 * stands, from just that chunk up to 10 chunks out. Players' land inside is
 * left as it is.
 */
final class ProtectMenu extends Menu {
	private static final int MINUS_SLOT = 11;
	private static final int RADIUS_SLOT = 13;
	private static final int PLUS_SLOT = 15;
	private static final int SET_SLOT = 20;
	private static final int BORDER_SLOT = 22;
	private static final int CLEAR_SLOT = 24;
	private static final int MAX_RADIUS = 10;
	private int radius;

	ProtectMenu(Player player, int radius) {
		super(player, "관리 - 보호구역");
		this.radius = radius;
	}

	@Override
	void draw() {
		Location at = player.getLocation();
		int cx = at.getBlockX() >> 4;
		int cz = at.getBlockZ() >> 4;
		int size = radius * 2 + 1;
		inventory.setItem(4, TradeItems.button(Material.GRASS_BLOCK, "현재 청크: " + cx + ", " + cz, NamedTextColor.YELLOW,
				Land.zoneAt(at) ? "보호구역입니다." : Land.claimAt(at) != null ? Land.claimAt(at).ownerName + "님의 땅입니다." : "보호구역이 아닙니다.",
				"전체 보호구역: " + Land.zoneCount() + "청크"));
		inventory.setItem(MINUS_SLOT, TradeItems.stepButton(-1, "범위: 반경 " + radius + "청크"));
		inventory.setItem(RADIUS_SLOT, TradeItems.button(Material.MAP, Math.max(1, radius), "범위: 반경 " + radius + "청크 (" + size + "x" + size + ")",
				NamedTextColor.WHITE, "서 있는 청크를 중심으로 합니다."));
		inventory.setItem(PLUS_SLOT, TradeItems.stepButton(1, "범위: 반경 " + radius + "청크"));
		inventory.setItem(BACK_SLOT, TradeItems.back());
		inventory.setItem(SET_SLOT, TradeItems.button(Material.LIME_CONCRETE, "보호구역 지정", NamedTextColor.GREEN,
				size + "x" + size + "청크를 보호구역으로 만듭니다.", "건축/파괴/상호작용은 관리자만 됩니다.",
				"이미 플레이어 땅인 청크는 건너뜁니다."));
		inventory.setItem(BORDER_SLOT, TradeItems.button(Material.SPYGLASS, "현재 청크 경계 보기", NamedTextColor.WHITE));
		inventory.setItem(CLEAR_SLOT, TradeItems.button(Material.RED_CONCRETE, "보호구역 해제", NamedTextColor.RED,
				size + "x" + size + "청크의 보호구역을 풉니다."));
	}

	@Override
	void click(int slot, ClickType click) {
		if (!Land.admin(player)) {
			player.closeInventory();
			return;
		}
		switch (slot) {
			case BACK_SLOT -> new AdminMenu(player).open();
			case MINUS_SLOT, PLUS_SLOT -> {
				radius = Math.max(0, Math.min(MAX_RADIUS, radius + (slot == PLUS_SLOT ? 1 : -1)));
				clickSound();
				redraw();
			}
			case SET_SLOT, CLEAR_SLOT -> {
				int changed = Land.setZones(player.getLocation(), radius, slot == SET_SLOT);
				tell((slot == SET_SLOT ? "보호구역으로 지정한 청크: " : "보호구역을 푼 청크: ") + changed + "개", NamedTextColor.GREEN);
				successSound();
				redraw();
			}
			case BORDER_SLOT -> {
				player.closeInventory();
				Land.showBorder(player, player.getLocation(), Land.zoneAt(player.getLocation()) ? Color.RED : Color.WHITE);
			}
			default -> {
			}
		}
	}
}
