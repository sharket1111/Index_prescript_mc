package io.github.sharket1111.trade;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.format.NamedTextColor;

/** Starting an auction: the item stays in the inventory until the player confirms the start price and length. */
final class AuctionEditor extends Menu {
	private static final int ITEM_SLOT = 4;
	private static final int PRICE_SLOT = 13;
	private static final int[] STEP_SLOTS = {9, 10, 11, 15, 16, 17};
	private static final int INPUT_SLOT = 14;
	private static final int HOURS_SLOT = 20;
	private static final int CONFIRM_SLOT = 22;

	private final int sourceSlot;
	private final ItemStack item;
	private long start = Auctions.MIN_START;
	private int hoursIndex = 4;

	AuctionEditor(Player player, int sourceSlot, ItemStack item) {
		super(player, "거래 - 경매 올리기");
		this.sourceSlot = sourceSlot;
		this.item = item;
	}

	private int hours() {
		return Auctions.HOURS[hoursIndex];
	}

	@Override
	void draw() {
		inventory.setItem(ITEM_SLOT, item.clone());
		inventory.setItem(PRICE_SLOT, TradeItems.glint(TradeItems.button(Material.GOLD_INGOT, "시작가: " + Money.format(start), NamedTextColor.GOLD,
				"클릭: 채팅으로 직접 입력", "양옆 버튼: 조금씩 바꾸기", "첫 입찰은 이 금액부터 받아요.")));
		for (int i = 0; i < STEP_SLOTS.length; i++) {
			inventory.setItem(STEP_SLOTS[i], TradeItems.stepButton(MONEY_STEPS[i], "시작가: " + Money.format(start)));
		}
		inventory.setItem(INPUT_SLOT, typeButton());
		inventory.setItem(HOURS_SLOT, TradeItems.button(Material.CLOCK, "기간: " + hours() + "시간", NamedTextColor.AQUA,
				"클릭: 기간 바꾸기 (1·3·6·12·24·48시간)"));
		inventory.setItem(BACK_SLOT, TradeItems.back());
		inventory.setItem(CONFIRM_SLOT, TradeItems.button(Material.LIME_CONCRETE, "경매 시작", NamedTextColor.GREEN,
				"시작가 " + Money.format(start) + ", " + hours() + "시간 동안", "경매에 올립니다. 아이템은 인벤토리에서 빠지고,",
				"입찰이 없으면 끝난 뒤 우편함으로 돌아와요."));
	}

	@Override
	void click(int slot, ClickType click) {
		if (slot == BACK_SLOT) {
			clickSound();
			new AuctionMenu(player, 0, false).open();
			return;
		}
		for (int i = 0; i < STEP_SLOTS.length; i++) {
			if (slot == STEP_SLOTS[i]) {
				start = Math.max(Auctions.MIN_START, Math.min(Auctions.MAX_PRICE, start + MONEY_STEPS[i]));
				clickSound();
				redraw();
				return;
			}
		}
		if (slot == PRICE_SLOT || slot == INPUT_SLOT) {
			askAmount("시작가를 입력하세요.", Auctions.MIN_START, Auctions.MAX_PRICE, value -> start = value);
		} else if (slot == HOURS_SLOT) {
			hoursIndex = (hoursIndex + 1) % Auctions.HOURS.length;
			clickSound();
			redraw();
		} else if (slot == CONFIRM_SLOT) {
			confirm();
		}
	}

	private void confirm() {
		ItemStack now = player.getInventory().getItem(sourceSlot);
		if (now == null || !now.isSimilar(item) || now.getAmount() != item.getAmount()) {
			tell("인벤토리의 아이템이 바뀌어서 올리지 못했습니다.", NamedTextColor.RED);
			failSound();
			new AuctionMenu(player, 0, false).open();
			return;
		}
		String why = Auctions.refuse(player.getUniqueId(), now);
		if (why != null) {
			tell(why, NamedTextColor.RED);
			failSound();
			new AuctionMenu(player, 0, false).open();
			return;
		}
		player.getInventory().setItem(sourceSlot, null);
		Auctions.start(player.getUniqueId(), player.getName(), item, start, hours());
		tell("경매를 시작했습니다. 시작가 " + Money.format(start) + ", " + hours() + "시간.", NamedTextColor.GREEN);
		successSound();
		new AuctionMenu(player, 0, true).open();
	}
}
