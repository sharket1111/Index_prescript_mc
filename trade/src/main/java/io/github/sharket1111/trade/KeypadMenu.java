package io.github.sharket1111.trade;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongConsumer;
import java.util.function.LongFunction;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * A number keypad in a chest (9x6), for the Bedrock (touch) mode where typing
 * in chat is awkward. A phone-style pad in the middle (1-9, 00, 0, ⌫), quick
 * additions on the left, the lowest and highest value and "clear" on the
 * right, and wide cancel and confirm buttons along the bottom. The number
 * typed so far is shown at the top and in the window's title.
 */
final class KeypadMenu extends Menu {
	private static final int DISPLAY_SLOT = 4;
	/** Digits 1-9, row by row. */
	private static final int[] DIGIT_SLOTS = {12, 13, 14, 21, 22, 23, 30, 31, 32};
	private static final int DOUBLE_ZERO_SLOT = 39;
	private static final int ZERO_SLOT = 40;
	private static final int BACKSPACE_SLOT = 41;
	private static final int[] QUICK_SLOTS = {10, 19, 28, 37};
	private static final int MIN_SLOT = 16;
	private static final int MAX_SLOT = 25;
	private static final int CLEAR_SLOT = 34;
	private static final int[] CANCEL_SLOTS = {45, 46, 47};
	private static final int[] CONFIRM_SLOTS = {51, 52, 53};

	private final String question;
	private final long min;
	private final long max;
	private final LongFunction<String> show;
	private final LongConsumer done;
	private final Runnable cancelled;
	private final Runnable abandoned;
	private final long[] quick;
	private long value;
	private boolean finished;

	/**
	 * @param done      gets the value on confirm
	 * @param cancelled runs on the cancel button
	 * @param abandoned runs if the window is closed without either (may be null)
	 */
	KeypadMenu(Player player, String question, long min, long max, LongFunction<String> show, LongConsumer done, Runnable cancelled,
			Runnable abandoned) {
		super(player, title(show, 0), 6);
		this.question = question;
		this.min = min;
		this.max = max;
		this.show = show;
		this.done = done;
		this.cancelled = cancelled;
		this.abandoned = abandoned;
		this.quick = quickSteps(max);
	}

	private static String title(LongFunction<String> show, long value) {
		return "입력: " + show.apply(value);
	}

	/** Up to four quick additions, the largest powers of ten that fit. */
	private static long[] quickSteps(long max) {
		List<Long> steps = new ArrayList<>();
		for (long step = 1; step <= max && step > 0; step *= 10) {
			steps.add(step);
		}
		while (steps.size() > QUICK_SLOTS.length) {
			steps.remove(0);
		}
		long[] out = new long[steps.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = steps.get(i);
		}
		return out;
	}

	private static ItemStack key(Material material, int amount, String name, NamedTextColor colour, String... lore) {
		return TradeItems.button(material, amount, name, colour, lore);
	}

	@Override
	void draw() {
		ItemStack floor = TradeItems.button(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.DARK_GRAY);
		for (int slot = 0; slot < inventory.getSize(); slot++) {
			inventory.setItem(slot, floor);
		}
		for (int d = 1; d <= 9; d++) {
			inventory.setItem(DIGIT_SLOTS[d - 1], key(Material.LIGHT_BLUE_CONCRETE, d, String.valueOf(d), NamedTextColor.AQUA));
		}
		inventory.setItem(ZERO_SLOT, key(Material.WHITE_CONCRETE, 1, "0", NamedTextColor.WHITE));
		inventory.setItem(DOUBLE_ZERO_SLOT, key(Material.LIGHT_GRAY_CONCRETE, 1, "00", NamedTextColor.WHITE));
		inventory.setItem(BACKSPACE_SLOT, key(Material.ORANGE_CONCRETE, 1, "⌫ 한 자리 지우기", NamedTextColor.GOLD));
		for (int i = 0; i < quick.length; i++) {
			inventory.setItem(QUICK_SLOTS[i], key(Material.LIME_STAINED_GLASS, 1, "+" + show.apply(quick[i]), NamedTextColor.GREEN));
		}
		inventory.setItem(MIN_SLOT, key(Material.IRON_NUGGET, 1, "최솟값: " + show.apply(min), NamedTextColor.WHITE));
		inventory.setItem(MAX_SLOT, key(Material.GOLD_NUGGET, 1, "최댓값: " + show.apply(max), NamedTextColor.YELLOW));
		inventory.setItem(CLEAR_SLOT, key(Material.RED_STAINED_GLASS, 1, "C 모두 지우기", NamedTextColor.RED));
		for (int slot : CANCEL_SLOTS) {
			inventory.setItem(slot, key(Material.RED_CONCRETE, 1, "취소", NamedTextColor.RED, "바꾸지 않고 돌아갑니다."));
		}
		for (int slot : CONFIRM_SLOTS) {
			inventory.setItem(slot, key(Material.LIME_CONCRETE, 1, "확인: " + show.apply(clamped()), NamedTextColor.GREEN, "이 값으로 정합니다."));
		}
		drawDisplay();
	}

	private long clamped() {
		return Math.max(min, Math.min(max, value));
	}

	private void drawDisplay() {
		inventory.setItem(DISPLAY_SLOT, TradeItems.glint(key(Material.GOLD_BLOCK, 1, show.apply(value), NamedTextColor.GOLD, question,
				"범위: " + show.apply(min) + " ~ " + show.apply(max))));
		for (int slot : CONFIRM_SLOTS) {
			inventory.setItem(slot, key(Material.LIME_CONCRETE, 1, "확인: " + show.apply(clamped()), NamedTextColor.GREEN, "이 값으로 정합니다."));
		}
		if (player.getOpenInventory().getTopInventory() == inventory) {
			player.getOpenInventory().setTitle(title(show, value));
		}
	}

	private void set(long next) {
		if (next > max || next < 0) {
			player.sendActionBar(Component.text("최대 " + show.apply(max) + "까지입니다.", NamedTextColor.RED));
			failSound();
			return;
		}
		value = next;
		clickSound();
		drawDisplay();
	}

	@Override
	void click(int slot, ClickType click) {
		for (int d = 1; d <= 9; d++) {
			if (slot == DIGIT_SLOTS[d - 1]) {
				set(append(d, 10));
				return;
			}
		}
		for (int i = 0; i < quick.length; i++) {
			if (slot == QUICK_SLOTS[i]) {
				set(Math.min(max, value + quick[i]));
				return;
			}
		}
		for (int s : CANCEL_SLOTS) {
			if (slot == s) {
				finished = true;
				clickSound();
				cancelled.run();
				return;
			}
		}
		for (int s : CONFIRM_SLOTS) {
			if (slot == s) {
				finished = true;
				successSound();
				done.accept(clamped());
				return;
			}
		}
		switch (slot) {
			case ZERO_SLOT -> set(append(0, 10));
			case DOUBLE_ZERO_SLOT -> set(append(0, 100));
			case BACKSPACE_SLOT -> set(value / 10);
			case CLEAR_SLOT -> set(0);
			case MIN_SLOT -> set(min);
			case MAX_SLOT -> set(max);
			default -> {
			}
		}
	}

	/** The value with a digit added on the right ({@code shift} 100 for "00"); -1 when it would not fit. */
	private long append(int digit, long shift) {
		if (value > (Long.MAX_VALUE - digit) / shift) {
			return -1;
		}
		return value * shift + digit;
	}

	/** Nothing moves in the player's own inventory while the keypad is up. */
	@Override
	void clickOwn(InventoryClickEvent event) {
		event.setCancelled(true);
	}

	@Override
	void closed() {
		if (!finished) {
			finished = true;
			if (abandoned != null) {
				abandoned.run();
			}
		}
	}
}
