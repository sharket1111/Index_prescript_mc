package io.github.sharket1111.trade;

import java.util.function.Consumer;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.format.NamedTextColor;

/** The server shop: goods sold by the server for trade diamonds (right-click to buy). */
final class ServerShopMenu extends Menu {
	private static final int WALLET_SLOT = 22;

	private record Product(int slot, int price, String name, ItemStack icon, Consumer<Player> deliver) {
	}

	private final Product[] products;

	ServerShopMenu(Player player) {
		super(player, "거래 - 서버 상점");
		products = new Product[] {
			new Product(11, 2, "스폰 귀환권", Tickets.spawnTicket(1), p -> give(Tickets.spawnTicket(1))),
			new Product(13, 4, "커스텀 귀환권", Tickets.customTicket(), p -> give(Tickets.customTicket())),
			new Product(15, 2, "경험치 3레벨",
					TradeItems.button(Material.EXPERIENCE_BOTTLE, 3, "경험치 3레벨", NamedTextColor.GREEN, "바로 경험치 3레벨을 얻습니다."),
					p -> p.giveExpLevels(3)),
		};
	}

	@Override
	void draw() {
		for (Product product : products) {
			ItemStack icon = product.icon().clone();
			icon.editMeta(meta -> {
				var lore = meta.hasLore() ? new java.util.ArrayList<>(meta.lore()) : new java.util.ArrayList<net.kyori.adventure.text.Component>();
				lore.add(net.kyori.adventure.text.Component.empty());
				lore.add(TradeItems.plain("가격: 거래 전용 다이아 " + product.price() + "개", NamedTextColor.AQUA));
				lore.add(TradeItems.plain(bedrock() ? "터치: 구매" : "우클릭: 구매", NamedTextColor.GRAY));
				meta.lore(lore);
			});
			inventory.setItem(product.slot(), icon);
		}
		inventory.setItem(BACK_SLOT, TradeItems.back());
		inventory.setItem(WALLET_SLOT, TradeItems.glint(TradeItems.button(Material.DIAMOND,
				"내 거래 전용 다이아: " + count(TradeItems::isTradeDiamond) + "개", NamedTextColor.AQUA)));
	}

	@Override
	void click(int slot, ClickType click) {
		if (slot == BACK_SLOT) {
			clickSound();
			new MainMenu(player).open();
			return;
		}
		for (Product product : products) {
			if (product.slot() == slot && confirmed(click)) {
				buy(product);
				return;
			}
		}
	}

	private void buy(Product product) {
		int have = count(TradeItems::isTradeDiamond);
		if (have < product.price()) {
			tell("거래 전용 다이아가 부족합니다. (" + (product.price() - have) + "개 더 필요)", NamedTextColor.RED);
			failSound();
			return;
		}
		take(TradeItems::isTradeDiamond, product.price());
		product.deliver().accept(player);
		tell(product.name() + "을(를) 거래 전용 다이아 " + product.price() + "개에 샀습니다.", NamedTextColor.GREEN);
		successSound();
		redraw();
	}
}
