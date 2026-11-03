package io.github.sharket1111.trade;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * The goods tab: the player's own listings (up to 18) in the top two rows.
 * Clicking an item in their inventory below starts listing it; clicking a
 * listing opens it to change its price or withdraw it.
 */
final class GoodsMenu extends Menu {
	private static final int INFO_SLOT = 22;
	private static final int LAND_SLOT = 26;
	private final Map<Integer, Listing> bySlot = new HashMap<>();

	GoodsMenu(Player player) {
		super(player, "거래 - 물품");
	}

	@Override
	void draw() {
		bySlot.clear();
		List<Listing> mine = Market.of(player.getUniqueId());
		for (int i = 0; i < mine.size() && i < Market.MAX_LISTINGS_PER_PLAYER; i++) {
			Listing listing = mine.get(i);
			inventory.setItem(i, display(listing));
			bySlot.put(i, listing);
		}
		inventory.setItem(BACK_SLOT, TradeItems.back());
		Land.Claim here = Land.claimAt(player.getLocation());
		if (here != null && here.owner.equals(player.getUniqueId())) {
			boolean listed = mine.stream().anyMatch(l -> here.key.equals(Land.deedChunk(l.item)));
			inventory.setItem(LAND_SLOT, listed
					? TradeItems.button(Material.MAP, "이 땅은 이미 판매 중입니다", NamedTextColor.GRAY)
					: TradeItems.glint(TradeItems.button(Material.MAP, "서 있는 내 땅 팔기", NamedTextColor.GREEN,
							"지금 서 있는 청크를 판매 물품으로 등록합니다.", "팔릴 때까지는 계속 내 땅입니다.")));
		}
		inventory.setItem(INFO_SLOT, TradeItems.button(Material.BOOK, "물품 등록 (" + mine.size() + "/" + Market.MAX_LISTINGS_PER_PLAYER + ")",
				NamedTextColor.GOLD, "아래 내 인벤토리에서 팔 아이템을 클릭하면", "가격을 정해 등록할 수 있습니다.",
				"등록한 물품을 클릭하면 가격 수정이나 삭제를 합니다."));
	}

	/** The listed item with its price and what a click does added below its own description. */
	static ItemStack display(Listing listing) {
		ItemStack shown = listing.item.clone();
		shown.editMeta(meta -> {
			List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
			lore.add(Component.empty());
			lore.add(TradeItems.plain("가격: 거래 전용 다이아 " + listing.price + "개", NamedTextColor.AQUA));
			lore.add(TradeItems.plain("클릭: 가격 수정 / 삭제", NamedTextColor.GRAY));
			meta.lore(lore);
		});
		return shown;
	}

	@Override
	void click(int slot, ClickType click) {
		if (slot == BACK_SLOT) {
			clickSound();
			new MainMenu(player).open();
			return;
		}
		if (slot == LAND_SLOT) {
			sellLand();
			return;
		}
		Listing listing = bySlot.get(slot);
		if (listing != null && Market.get(listing.id) != null) {
			clickSound();
			ListingEditor.edit(player, listing).open();
		}
	}

	private void sellLand() {
		Land.Claim here = Land.claimAt(player.getLocation());
		if (here == null || !here.owner.equals(player.getUniqueId())) {
			return;
		}
		List<Listing> mine = Market.of(player.getUniqueId());
		if (mine.stream().anyMatch(l -> here.key.equals(Land.deedChunk(l.item)))) {
			return;
		}
		if (mine.size() >= Market.MAX_LISTINGS_PER_PLAYER) {
			tell("물품은 " + Market.MAX_LISTINGS_PER_PLAYER + "개까지 등록할 수 있습니다.", NamedTextColor.RED);
			failSound();
			return;
		}
		clickSound();
		ListingEditor.createLand(player, Land.deed(here)).open();
	}

	/** Picking an item from the player's own inventory to list it. */
	@Override
	void clickOwn(InventoryClickEvent event) {
		event.setCancelled(true);
		ItemStack picked = event.getCurrentItem();
		if (picked == null || picked.isEmpty()) {
			return;
		}
		if (refuseTool(picked)) {
			return;
		}
		if (Market.of(player.getUniqueId()).size() >= Market.MAX_LISTINGS_PER_PLAYER) {
			tell("물품은 " + Market.MAX_LISTINGS_PER_PLAYER + "개까지 등록할 수 있습니다.", NamedTextColor.RED);
			failSound();
			return;
		}
		clickSound();
		ListingEditor.create(player, event.getSlot(), picked.clone()).open();
	}
}
