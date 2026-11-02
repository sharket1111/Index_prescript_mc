package io.github.sharket1111.trade;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Diamond exchange, both ways. Top row: plain to trade diamonds and back,
 * the chosen amount at a time (right-click). Middle row: the amount, 1-64,
 * remembered per player until the server restarts. Bottom row: back, and
 * exchange every diamond of a kind at once.
 */
final class ExchangeMenu extends Menu {
	private static final int TO_TRADE_SLOT = 2;
	private static final int TO_PLAIN_SLOT = 6;
	private static final int AMOUNT_SLOT = 13;
	private static final int[] STEP_SLOTS = {10, 11, 15, 16};
	private static final int[] STEPS = {-10, -1, 1, 10};
	private static final int ALL_TO_TRADE_SLOT = 20;
	private static final int ALL_TO_PLAIN_SLOT = 24;
	private static final int MAX_AMOUNT = 64;
	private static final Map<UUID, Integer> AMOUNTS = new ConcurrentHashMap<>();

	ExchangeMenu(Player player) {
		super(player, "거래 - 다이아 교환");
	}

	private int amount() {
		return AMOUNTS.getOrDefault(player.getUniqueId(), 1);
	}

	@Override
	void draw() {
		int amount = amount();
		inventory.setItem(TO_TRADE_SLOT, TradeItems.button(Material.DIAMOND, amount, "일반 → 거래 전용 (" + amount + "개)",
				NamedTextColor.AQUA, "우클릭: 다이아몬드 " + amount + "개를", "거래 전용 다이아로 바꿉니다."));
		inventory.setItem(TO_PLAIN_SLOT, TradeItems.glint(TradeItems.button(Material.DIAMOND, amount,
				"거래 전용 → 일반 (" + amount + "개)", NamedTextColor.AQUA, "우클릭: 거래 전용 다이아 " + amount + "개를",
				"일반 다이아몬드로 되돌립니다.")));
		inventory.setItem(AMOUNT_SLOT, TradeItems.button(Material.PAPER, amount, "바꿀 개수: " + amount + "개",
				NamedTextColor.YELLOW, "양옆 버튼으로 1~64개 사이에서 정합니다."));
		for (int i = 0; i < STEP_SLOTS.length; i++) {
			inventory.setItem(STEP_SLOTS[i], TradeItems.stepButton(STEPS[i], "현재 개수: " + amount + "개"));
		}
		inventory.setItem(BACK_SLOT, TradeItems.back());
		inventory.setItem(ALL_TO_TRADE_SLOT, TradeItems.button(Material.DIAMOND_BLOCK, "전부 거래 전용으로", NamedTextColor.AQUA,
				"우클릭: 인벤토리의 다이아몬드를 모두", "거래 전용 다이아로 바꿉니다."));
		inventory.setItem(ALL_TO_PLAIN_SLOT, TradeItems.glint(TradeItems.button(Material.DIAMOND_BLOCK, "전부 일반으로",
				NamedTextColor.AQUA, "우클릭: 인벤토리의 거래 전용 다이아를 모두", "일반 다이아몬드로 되돌립니다.")));
	}

	@Override
	void click(int slot, ClickType click) {
		if (slot == BACK_SLOT) {
			clickSound();
			new MainMenu(player).open();
			return;
		}
		for (int i = 0; i < STEP_SLOTS.length; i++) {
			if (slot == STEP_SLOTS[i]) {
				AMOUNTS.put(player.getUniqueId(), Math.max(1, Math.min(MAX_AMOUNT, amount() + STEPS[i])));
				clickSound();
				redraw();
				return;
			}
		}
		if (!confirmed(click)) {
			return;
		}
		switch (slot) {
			case TO_TRADE_SLOT -> toTrade(amount());
			case TO_PLAIN_SLOT -> toPlain(amount());
			case ALL_TO_TRADE_SLOT -> toTrade(Integer.MAX_VALUE);
			case ALL_TO_PLAIN_SLOT -> toPlain(Integer.MAX_VALUE);
			default -> {
			}
		}
	}

	private void toTrade(int wanted) {
		int count = take(TradeItems::isPlainDiamond, wanted);
		if (count == 0) {
			tell("인벤토리에 다이아몬드가 없습니다.", NamedTextColor.RED);
			failSound();
			return;
		}
		giveStacks(TradeItems.tradeDiamond(1), count);
		report(count, wanted, "다이아몬드", "거래 전용 다이아로 바꿨습니다.");
	}

	private void toPlain(int wanted) {
		int count = take(TradeItems::isTradeDiamond, wanted);
		if (count == 0) {
			tell("인벤토리에 거래 전용 다이아가 없습니다.", NamedTextColor.RED);
			failSound();
			return;
		}
		giveStacks(ItemStack.of(Material.DIAMOND), count);
		report(count, wanted, "거래 전용 다이아", "일반 다이아몬드로 되돌렸습니다.");
	}

	private void report(int count, int wanted, String what, String done) {
		if (count < wanted && wanted != Integer.MAX_VALUE) {
			tell(what + "가 " + count + "개뿐이라 " + count + "개만 바꿨습니다.", NamedTextColor.YELLOW);
		} else {
			tell(what + " " + count + "개를 " + done, NamedTextColor.AQUA);
		}
		successSound();
	}
}
