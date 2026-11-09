package io.github.sharket1111.trade;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.format.NamedTextColor;

/** Setting a server shop product's price (admins); new products are added, existing ones can be removed. */
final class ProductEditor extends Menu {
	private static final int ITEM_SLOT = 4;
	private static final int PRICE_SLOT = 13;
	private static final int[] STEP_SLOTS = {9, 10, 11, 15, 16, 17};
	private static final int[] STEPS = MONEY_STEPS;
	private static final int INPUT_SLOT = 14;
	private static final int SAVE_SLOT = 22;
	private static final int DELETE_SLOT = 26;
	private final ShopConfig.Product existing;
	private final ItemStack newItem;
	private int price;

	ProductEditor(Player player, ShopConfig.Product existing, ItemStack newItem) {
		super(player, existing == null ? "관리 - 상품 추가" : "관리 - 상품 수정");
		this.existing = existing;
		this.newItem = newItem;
		this.price = existing == null ? 100 : existing.price;
	}

	@Override
	void draw() {
		inventory.setItem(ITEM_SLOT, existing == null ? newItem.clone() : existing.icon());
		ItemStack shown = existing == null ? newItem : existing.item;
		ItemValues.Appraisal appraisal = shown == null ? null : ItemValues.appraise(shown);
		inventory.setItem(PRICE_SLOT, TradeItems.glint(TradeItems.button(Material.GOLD_INGOT, "가격: " + Money.format(price),
				NamedTextColor.GOLD, "양옆 버튼이나 '직접 입력'으로 정합니다.",
				appraisal != null && appraisal.sellable() ? "서버 판매가: " + Money.format((long) Math.floor(appraisal.unit() * shown.getAmount()))
						+ " (이보다 싸게 팔면 되팔기로 돈을 법니다)" : "")));
		for (int i = 0; i < STEP_SLOTS.length; i++) {
			inventory.setItem(STEP_SLOTS[i], TradeItems.stepButton(STEPS[i], "현재 가격: " + Money.format(price)));
		}
		inventory.setItem(INPUT_SLOT, typeButton());
		inventory.setItem(BACK_SLOT, TradeItems.back());
		inventory.setItem(SAVE_SLOT, TradeItems.button(Material.LIME_CONCRETE, existing == null ? "상품 추가" : "가격 저장",
				NamedTextColor.GREEN));
		if (existing != null) {
			inventory.setItem(DELETE_SLOT, TradeItems.button(Material.BARRIER, "상품 삭제", NamedTextColor.RED));
		}
	}

	@Override
	void click(int slot, ClickType click) {
		if (slot == BACK_SLOT) {
			clickSound();
			new ShopEditorMenu(player).open();
			return;
		}
		for (int i = 0; i < STEP_SLOTS.length; i++) {
			if (slot == STEP_SLOTS[i]) {
				price = Math.max(0, Math.min(ShopConfig.MAX_PRICE, price + STEPS[i]));
				clickSound();
				redraw();
				return;
			}
		}
		if (slot == INPUT_SLOT) {
			askAmount("상품 가격을 입력하세요.", 0, ShopConfig.MAX_PRICE, value -> price = (int) value);
			return;
		}
		if (slot == SAVE_SLOT) {
			if (existing == null) {
				ShopConfig.add(newItem, price);
				tell("상품을 추가했습니다.", NamedTextColor.GREEN);
			} else {
				existing.price = price;
				ShopConfig.save();
				tell("가격을 저장했습니다.", NamedTextColor.GREEN);
			}
			successSound();
			new ShopEditorMenu(player).open();
		} else if (slot == DELETE_SLOT && existing != null) {
			ShopConfig.remove(existing);
			tell("상품을 삭제했습니다.", NamedTextColor.YELLOW);
			successSound();
			new ShopEditorMenu(player).open();
		}
	}

	@Override
	void clickOwn(InventoryClickEvent event) {
		event.setCancelled(true);
	}
}
