package io.github.sharket1111.trade;

import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Setting a price: for a new listing (the item stays in the player's
 * inventory until they confirm) or for an existing one, which can also be
 * withdrawn, giving the item back.
 */
final class ListingEditor extends Menu {
	private static final int ITEM_SLOT = 4;
	private static final int PRICE_SLOT = 13;
	private static final int[] STEP_SLOTS = {9, 10, 11, 15, 16, 17};
	private static final int[] STEPS = {-64, -10, -1, 1, 10, 64};
	private static final int CONFIRM_SLOT = 22;
	private static final int DELETE_SLOT = 26;

	private final Listing existing;
	private final int sourceSlot;
	private final ItemStack item;
	private int price;

	private ListingEditor(Player player, String title, Listing existing, int sourceSlot, ItemStack item, int price) {
		super(player, title);
		this.existing = existing;
		this.sourceSlot = sourceSlot;
		this.item = item;
		this.price = price;
	}

	/** Listing the item now in {@code sourceSlot} of the player's inventory. */
	static ListingEditor create(Player player, int sourceSlot, ItemStack item) {
		return new ListingEditor(player, "거래 - 물품 등록", null, sourceSlot, item, Market.MIN_PRICE);
	}

	static ListingEditor edit(Player player, Listing listing) {
		return new ListingEditor(player, "거래 - 물품 수정", listing, -1, listing.item, listing.price);
	}

	@Override
	void draw() {
		inventory.setItem(ITEM_SLOT, item.clone());
		inventory.setItem(PRICE_SLOT, TradeItems.glint(TradeItems.button(Material.DIAMOND, price, "가격: 거래 전용 다이아 " + price + "개",
				NamedTextColor.AQUA, "양옆 버튼으로 가격을 정합니다. (" + Market.MIN_PRICE + "~" + Market.MAX_PRICE + ")")));
		for (int i = 0; i < STEP_SLOTS.length; i++) {
			inventory.setItem(STEP_SLOTS[i], TradeItems.stepButton(STEPS[i], "현재 가격: " + price + "개"));
		}
		inventory.setItem(BACK_SLOT, TradeItems.back());
		if (existing == null) {
			inventory.setItem(CONFIRM_SLOT, TradeItems.button(Material.LIME_CONCRETE, "등록하기", NamedTextColor.GREEN,
					"이 아이템을 거래 전용 다이아 " + price + "개에", "판매 물품으로 등록합니다.", "등록한 아이템은 인벤토리에서 빠집니다."));
		} else {
			inventory.setItem(CONFIRM_SLOT, TradeItems.button(Material.LIME_CONCRETE, "가격 저장", NamedTextColor.GREEN,
					"가격을 거래 전용 다이아 " + price + "개로 바꿉니다."));
			inventory.setItem(DELETE_SLOT, TradeItems.button(Material.BARRIER, "삭제", NamedTextColor.RED,
					"등록을 취소하고 아이템을 돌려받습니다."));
		}
	}

	@Override
	void click(int slot, ClickType click) {
		if (slot == BACK_SLOT) {
			clickSound();
			new GoodsMenu(player).open();
			return;
		}
		for (int i = 0; i < STEP_SLOTS.length; i++) {
			if (slot == STEP_SLOTS[i]) {
				price = Math.max(Market.MIN_PRICE, Math.min(Market.MAX_PRICE, price + STEPS[i]));
				clickSound();
				redraw();
				return;
			}
		}
		if (slot == CONFIRM_SLOT) {
			if (existing == null) {
				register();
			} else {
				savePrice();
			}
		} else if (slot == DELETE_SLOT && existing != null) {
			withdraw();
		}
	}

	/** The inventory cannot change while a price is being set. */
	@Override
	void clickOwn(InventoryClickEvent event) {
		event.setCancelled(true);
	}

	private void register() {
		if (Market.of(player.getUniqueId()).size() >= Market.MAX_LISTINGS_PER_PLAYER) {
			tell("물품은 " + Market.MAX_LISTINGS_PER_PLAYER + "개까지 등록할 수 있습니다.", NamedTextColor.RED);
			failSound();
			return;
		}
		ItemStack now = player.getInventory().getItem(sourceSlot);
		if (now == null || !now.isSimilar(item) || now.getAmount() != item.getAmount()) {
			tell("인벤토리의 아이템이 바뀌어서 등록하지 못했습니다.", NamedTextColor.RED);
			failSound();
			new GoodsMenu(player).open();
			return;
		}
		player.getInventory().setItem(sourceSlot, null);
		Market.add(new Listing(UUID.randomUUID(), player.getUniqueId(), player.getName(), item.clone(), price, System.currentTimeMillis()));
		tell("물품을 거래 전용 다이아 " + price + "개에 등록했습니다.", NamedTextColor.GREEN);
		successSound();
		new GoodsMenu(player).open();
	}

	private void savePrice() {
		if (Market.get(existing.id) == null) {
			tell("이미 없어진 물품입니다.", NamedTextColor.RED);
			failSound();
		} else {
			existing.price = price;
			Market.save();
			tell("가격을 거래 전용 다이아 " + price + "개로 바꿨습니다.", NamedTextColor.GREEN);
			successSound();
		}
		new GoodsMenu(player).open();
	}

	private void withdraw() {
		if (Market.get(existing.id) == null) {
			tell("이미 없어진 물품입니다.", NamedTextColor.RED);
			failSound();
		} else {
			Market.remove(existing);
			give(existing.item.clone());
			tell("등록을 취소하고 아이템을 돌려받았습니다.", NamedTextColor.YELLOW);
			successSound();
		}
		new GoodsMenu(player).open();
	}
}
