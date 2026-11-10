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
	private static final int ADD_ALL_SLOT = 48;
	private static final int TOTAL_SLOT = 49;
	private static final int HELP_SLOT = 51;
	private static final int SELL_SLOT = 53;
	private static final int SHOWN_LINES = 12;

	SellMenu(Player player) {
		super(player, "거래 - 아이템 판매", 6);
	}

	/** In the Java mode the box works like a chest; in the touch mode a tap moves a whole stack (see {@link #click}, {@link #clickOwn}). */
	@Override
	boolean free(int slot) {
		return !bedrock() && slot >= 0 && slot < BOX;
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
		inventory.setItem(ADD_ALL_SLOT, TradeItems.button(Material.HOPPER, "팔 수 있는 것 모두 넣기", NamedTextColor.YELLOW,
				"인벤토리(단축바 제외)에서 팔 수 있는 아이템을", "모두 위 칸에 넣습니다.", "도구·무기·방어구는 넣지 않습니다."));
		inventory.setItem(HELP_SLOT, TradeItems.button(Material.BOOK, "사용법", NamedTextColor.GOLD,
				bedrock() ? "아래 내 인벤토리의 아이템을 누르면 한 묶음씩 들어가고," : "팔 아이템을 위 칸에 넣으세요. (쉬프트 클릭 가능)",
				bedrock() ? "위 칸의 아이템을 누르면 돌려받습니다." : "",
				"아래 금 블록에 넣은 아이템의 값이 나옵니다.",
				"'판매'를 누르면 팔 수 있는 것만 팔고,", "나머지는 그대로 남습니다.", "메뉴를 닫으면 남은 아이템을 돌려받습니다.",
				"도구·무기·방어구는 재료값의 절반이고,", "닳은 만큼 더 깎입니다.",
				ServerPrices.enabled() ? "같은 아이템을 많이 팔수록 값이 떨어지고," : "",
				ServerPrices.enabled() ? "시간이 지나면(" + ItemValues.show(ServerPrices.recoverHours()) + "시간마다 절반씩) 회복합니다." : ""));
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
		Map<Material, Double> already = new java.util.EnumMap<>(Material.class);
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
			// The price falls as more of the same kind is sold.
			double worth = ServerPrices.worth(stack, appraisal.unit(), already);
			value += worth;
			double[] kind = kinds.computeIfAbsent(name, key -> new double[3]);
			kind[0] += stack.getAmount();
			kind[1] += worth;
			kind[2] += appraisal.unit() * stack.getAmount();
		}
		List<String> lines = new ArrayList<>();
		for (Map.Entry<String, double[]> entry : kinds.entrySet()) {
			if (lines.size() >= SHOWN_LINES) {
				lines.add("... 외 " + (kinds.size() - SHOWN_LINES) + "종류");
				break;
			}
			double[] kind = entry.getValue();
			int drop = kind[2] > 0 ? (int) Math.round(100 * (1 - kind[1] / kind[2])) : 0;
			lines.add(entry.getKey() + " x" + (int) kind[0] + " = " + ItemValues.show(kind[1]) + (drop > 0 ? " (시세 -" + drop + "%)" : ""));
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
		} else if (slot == ADD_ALL_SLOT) {
			addAll();
		} else if (slot >= 0 && slot < BOX) {
			// Touch mode: a tap on the box gives that stack back.
			ItemStack stack = inventory.getItem(slot);
			if (stack != null && !stack.isEmpty()) {
				inventory.setItem(slot, null);
				give(stack);
				clickSound();
				drawControls();
			}
		}
	}

	/** Java mode: shift-click moves items in (the controls are full, so nothing lands there). Touch mode: a tap moves the stack in. */
	@Override
	void clickOwn(InventoryClickEvent event) {
		if (!bedrock()) {
			if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
				event.setCancelled(true);
			}
			return;
		}
		event.setCancelled(true);
		ItemStack picked = event.getCurrentItem();
		if (picked == null || picked.isEmpty()) {
			return;
		}
		if (put(picked.clone())) {
			event.getClickedInventory().setItem(event.getSlot(), null);
			clickSound();
		} else {
			tell("판매 칸이 가득 찼습니다.", NamedTextColor.RED);
			failSound();
		}
		drawControls();
	}

	/** Puts a stack in the box (merging with like stacks); false if it does not all fit (then nothing moves). */
	private boolean put(ItemStack stack) {
		int room = 0;
		for (int slot = 0; slot < BOX; slot++) {
			ItemStack here = inventory.getItem(slot);
			if (here == null || here.isEmpty()) {
				room += stack.getMaxStackSize();
			} else if (here.isSimilar(stack)) {
				room += here.getMaxStackSize() - here.getAmount();
			}
		}
		if (room < stack.getAmount()) {
			return false;
		}
		int left = stack.getAmount();
		for (int slot = 0; slot < BOX && left > 0; slot++) {
			ItemStack here = inventory.getItem(slot);
			if (here != null && here.isSimilar(stack) && here.getAmount() < here.getMaxStackSize()) {
				int add = Math.min(left, here.getMaxStackSize() - here.getAmount());
				here.setAmount(here.getAmount() + add);
				inventory.setItem(slot, here);
				left -= add;
			}
		}
		for (int slot = 0; slot < BOX && left > 0; slot++) {
			ItemStack here = inventory.getItem(slot);
			if (here == null || here.isEmpty()) {
				ItemStack part = stack.clone();
				part.setAmount(Math.min(left, stack.getMaxStackSize()));
				inventory.setItem(slot, part);
				left -= part.getAmount();
			}
		}
		return true;
	}

	/** Every sellable item from the inventory (not the hotbar, armour or gear) into the box. */
	private void addAll() {
		org.bukkit.inventory.PlayerInventory own = player.getInventory();
		int moved = 0;
		for (int slot = 9; slot < 36; slot++) {
			ItemStack stack = own.getItem(slot);
			if (stack == null || stack.isEmpty() || stack.getType().getMaxDurability() > 0 || !ItemValues.appraise(stack).sellable()) {
				continue;
			}
			if (!put(stack.clone())) {
				tell("판매 칸이 가득 차서 일부만 넣었습니다.", NamedTextColor.YELLOW);
				break;
			}
			own.setItem(slot, null);
			moved += stack.getAmount();
		}
		if (moved == 0) {
			tell("넣을 아이템이 없습니다. (단축바, 도구·무기·방어구는 빼고 찾습니다)", NamedTextColor.GRAY);
			failSound();
		} else {
			clickSound();
		}
		drawControls();
	}

	private void sell() {
		double value = 0;
		int items = 0;
		List<Integer> slots = new ArrayList<>();
		Map<Material, Double> already = new java.util.EnumMap<>(Material.class);
		for (int slot = 0; slot < BOX; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack == null || stack.isEmpty()) {
				continue;
			}
			ItemValues.Appraisal appraisal = ItemValues.appraise(stack);
			if (appraisal.sellable()) {
				value += ServerPrices.worth(stack, appraisal.unit(), already);
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
			ServerPrices.sold(inventory.getItem(slot));
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
