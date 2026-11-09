package io.github.sharket1111.trade;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * Selling items to the server (9x6). The top five rows are a box: items are
 * put in and taken out as in a chest (shift-click works), and the bottom row
 * shows what they are worth ({@link ItemValues}). "판매" sells everything in
 * the box that can be sold and leaves the rest; closing the menu gives back
 * whatever is still in it.
 */
final class SellMenu extends Menu {
	private static final int BOX = 45;
	private static final int BACK = 45;
	private static final int WALLET_SLOT = 47;
	private static final int TOTAL_SLOT = 49;
	private static final int HELP_SLOT = 51;
	private static final int SELL_SLOT = 53;
	private static final int SHOWN_LINES = 12;

	SellMenu(Player player) {
		super(player, "거래 - 아이템 판매", 6);
	}

	@Override
	boolean free(int slot) {
		return slot >= 0 && slot < BOX;
	}

	@Override
	void draw() {
		drawControls();
	}

	@Override
	void freeChanged() {
		if (player.getOpenInventory().getTopInventory() == inventory) {
			drawControls();
		}
	}

	private void drawControls() {
		ItemStack floor = TradeItems.button(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.DARK_GRAY);
		for (int slot = BOX; slot < BOX + 9; slot++) {
			inventory.setItem(slot, floor);
		}
		inventory.setItem(BACK, TradeItems.back());
		inventory.setItem(WALLET_SLOT, Money.wallet(player));
		inventory.setItem(HELP_SLOT, TradeItems.button(Material.BOOK, "사용법", NamedTextColor.GOLD,
				"팔 아이템을 위 칸에 넣으세요. (쉬프트 클릭 가능)", "아래 금괴에 넣은 아이템의 값이 나옵니다.",
				"'판매'를 누르면 팔 수 있는 것만 팔고,", "나머지는 그대로 남습니다.", "메뉴를 닫으면 남은 아이템을 돌려받습니다.",
				"도구·무기·방어구는 재료값의 절반이고,", "닳은 만큼 더 깎입니다."));
		Summary summary = summary();
		List<String> lore = new ArrayList<>(summary.lines);
		if (summary.lines.isEmpty()) {
			lore.add("위 칸에 팔 아이템을 넣으세요.");
		}
		inventory.setItem(TOTAL_SLOT, TradeItems.glint(TradeItems.button(Material.GOLD_BLOCK, "판매 금액: " + Money.format(summary.total()),
				NamedTextColor.GOLD, lore.toArray(String[]::new))));
		inventory.setItem(SELL_SLOT, summary.total() > 0
				? TradeItems.button(Material.LIME_CONCRETE, "판매", NamedTextColor.GREEN, Money.format(summary.total()) + "을(를) 받고 팝니다.",
						summary.unsellable > 0 ? "팔 수 없는 아이템 " + summary.unsellable + "칸은 남습니다." : "")
				: TradeItems.button(Material.GRAY_CONCRETE, "팔 아이템이 없습니다", NamedTextColor.GRAY));
	}

	private record Summary(double value, List<String> lines, int unsellable) {
		long total() {
			return (long) Math.floor(value + 1.0E-6);
		}
	}

	/** What the box is worth, with a line per kind of item. */
	private Summary summary() {
		Map<String, double[]> kinds = new LinkedHashMap<>();
		Map<String, String> refused = new LinkedHashMap<>();
		double value = 0;
		int unsellable = 0;
		for (int slot = 0; slot < BOX; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack == null || stack.isEmpty()) {
				continue;
			}
			String name = PlainTextComponentSerializer.plainText().serialize(stack.effectiveName());
			ItemValues.Appraisal appraisal = ItemValues.appraise(stack);
			if (!appraisal.sellable()) {
				unsellable++;
				refused.put(name, appraisal.reason());
				continue;
			}
			double worth = appraisal.unit() * stack.getAmount();
			value += worth;
			double[] kind = kinds.computeIfAbsent(name, key -> new double[2]);
			kind[0] += stack.getAmount();
			kind[1] += worth;
		}
		List<String> lines = new ArrayList<>();
		for (Map.Entry<String, double[]> entry : kinds.entrySet()) {
			if (lines.size() >= SHOWN_LINES) {
				lines.add("... 외 " + (kinds.size() - SHOWN_LINES) + "종류");
				break;
			}
			lines.add(entry.getKey() + " x" + (int) entry.getValue()[0] + " = " + ItemValues.show(entry.getValue()[1]));
		}
		for (Map.Entry<String, String> entry : refused.entrySet()) {
			lines.add("✕ " + entry.getKey() + " (" + entry.getValue() + ")");
		}
		return new Summary(value, lines, unsellable);
	}

	@Override
	void click(int slot, ClickType click) {
		if (slot == BACK) {
			clickSound();
			new MainMenu(player).open();
		} else if (slot == SELL_SLOT) {
			sell();
		}
	}

	/** Shift-clicking moves items into the box; the controls are full, so nothing lands there. */
	@Override
	void clickOwn(InventoryClickEvent event) {
		if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
			event.setCancelled(true);
		}
	}

	private void sell() {
		double value = 0;
		int items = 0;
		List<Integer> slots = new ArrayList<>();
		for (int slot = 0; slot < BOX; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack == null || stack.isEmpty()) {
				continue;
			}
			ItemValues.Appraisal appraisal = ItemValues.appraise(stack);
			if (appraisal.sellable()) {
				value += appraisal.unit() * stack.getAmount();
				items += stack.getAmount();
				slots.add(slot);
			}
		}
		long total = (long) Math.floor(value + 1.0E-6);
		if (slots.isEmpty() || total <= 0) {
			tell("팔 수 있는 아이템이 없습니다.", NamedTextColor.RED);
			failSound();
			return;
		}
		for (int slot : slots) {
			inventory.setItem(slot, null);
		}
		Money.add(player.getUniqueId(), total);
		player.sendMessage(Component.text("아이템 " + items + "개를 팔아 " + Money.format(total) + "을(를) 받았습니다. (잔액 "
				+ Money.format(Money.balance(player.getUniqueId())) + ")", NamedTextColor.GOLD));
		successSound();
		drawControls();
	}

	/** Whatever is still in the box goes back to the player. */
	@Override
	void closed() {
		for (int slot = 0; slot < BOX; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack != null && !stack.isEmpty()) {
				inventory.setItem(slot, null);
				give(stack);
			}
		}
	}
}
