package io.github.sharket1111.trade;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** The direct trade tab: requests received (left-click accepts, right-click declines) and sending one. */
final class DirectTradeMenu extends Menu {
	private static final int REQUEST_SLOT = 22;
	private final Map<Integer, Player> bySlot = new HashMap<>();

	DirectTradeMenu(Player player) {
		super(player, "거래 - 직접 거래");
	}

	@Override
	void draw() {
		bySlot.clear();
		List<Player> requests = DirectTrade.requestsFor(player);
		for (int i = 0; i < requests.size() && i < 18; i++) {
			Player from = requests.get(i);
			ItemStack head = ItemStack.of(Material.PLAYER_HEAD);
			head.editMeta(SkullMeta.class, meta -> {
				meta.setOwningPlayer(from);
				meta.itemName(Component.text(from.getName() + "님의 거래 요청", NamedTextColor.GOLD));
				meta.lore(List.of(TradeItems.plain(bedrock() ? "누르면 수락/거절을 고릅니다." : "좌클릭: 수락 / 우클릭: 거절", NamedTextColor.GRAY)));
			});
			inventory.setItem(i, head);
			bySlot.put(i, from);
		}
		if (requests.isEmpty()) {
			inventory.setItem(4, TradeItems.button(Material.PAPER, "받은 거래 요청이 없습니다", NamedTextColor.GRAY));
		}
		inventory.setItem(BACK_SLOT, TradeItems.back());
		inventory.setItem(REQUEST_SLOT, TradeItems.button(Material.EMERALD, "거래 요청 보내기", NamedTextColor.GREEN,
				"접속한 플레이어를 골라 거래를 요청합니다."));
	}

	@Override
	void click(int slot, ClickType click) {
		if (slot == BACK_SLOT) {
			clickSound();
			new MainMenu(player).open();
		} else if (slot == REQUEST_SLOT) {
			clickSound();
			new PlayerPicker(player, "거래 - 거래할 플레이어", true, false, 0, () -> new DirectTradeMenu(player), (id, name) -> {
				Player to = Bukkit.getPlayer(id);
				player.closeInventory();
				if (to != null) {
					DirectTrade.request(player, to);
				}
			}).open();
		} else if (bySlot.containsKey(slot)) {
			Player from = bySlot.get(slot);
			if (bedrock()) {
				// Touch: no right-click, so accepting and declining are two buttons.
				clickSound();
				new ChoiceMenu(player, "거래 - " + from.getName() + "님의 요청", java.util.List.of(
						TradeItems.button(Material.LIME_CONCRETE, "수락", NamedTextColor.GREEN, from.getName() + "님과 직접 거래를 시작합니다."),
						TradeItems.button(Material.RED_CONCRETE, "거절", NamedTextColor.RED, "요청을 거절합니다.")),
						() -> new DirectTradeMenu(player), choice -> {
							if (choice == 0) {
								DirectTrade.accept(player, from);
							} else {
								DirectTrade.decline(player, from);
								new DirectTradeMenu(player).open();
							}
						}).open();
			} else if (click.isRightClick()) {
				DirectTrade.decline(player, from);
				redraw();
			} else {
				DirectTrade.accept(player, from);
			}
		}
	}
}
