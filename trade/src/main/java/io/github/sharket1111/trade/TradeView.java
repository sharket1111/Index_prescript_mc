package io.github.sharket1111.trade;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.format.NamedTextColor;

/**
 * One player's view of a direct trade (9x6): their own offer on the left
 * (4x4), the other's on the right, the money each offers in the row below
 * (buttons to change one's own), and the cancel button, their confirmation
 * and the other's. Clicking an item in the inventory below puts
 * it up; clicking one of their offered items takes it back.
 */
final class TradeView extends Menu {
	private static final int CANCEL_SLOT = 45;
	private static final int CONFIRM_SLOT = 48;
	private static final int OTHER_SLOT = 50;
	private static final int INFO_SLOT = 53;
	private static final int[] MONEY_STEP_SLOTS = {36, 37, 38, 39};
	private static final int[] MONEY_STEPS = {-1000, -100, 100, 1000};
	private static final int MY_MONEY_SLOT = 40;
	private static final int THEIR_MONEY_SLOT = 42;
	private static final int TYPE_MONEY_SLOT = 41;
	private final DirectTrade.Session session;
	/** Typing an amount in chat: the window is closed for it, but the trade goes on. */
	private boolean typing;

	TradeView(Player player, DirectTrade.Session session) {
		super(player, "거래 - " + session.other(player).getName() + "님과 직접 거래", 6);
		this.session = session;
	}

	private static int mySlot(int i) {
		return (i / 4) * 9 + i % 4;
	}

	private static int otherSlot(int i) {
		return (i / 4) * 9 + 5 + i % 4;
	}

	@Override
	void draw() {
		ItemStack[] mine = session.offerOf(player);
		ItemStack[] theirs = session.offerOfOther(player);
		for (int i = 0; i < DirectTrade.OFFER_SLOTS; i++) {
			inventory.setItem(mySlot(i), mine[i]);
			inventory.setItem(otherSlot(i), theirs[i]);
		}
		ItemStack divider = TradeItems.button(Material.BLACK_STAINED_GLASS_PANE, " ", NamedTextColor.DARK_GRAY);
		for (int row = 0; row < 5; row++) {
			inventory.setItem(row * 9 + 4, divider);
		}
		ItemStack floor = TradeItems.button(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.DARK_GRAY);
		for (int slot = 36; slot < 45; slot++) {
			inventory.setItem(slot, floor);
		}
		Player other = session.other(player);
		long myMoney = session.moneyOf(player);
		for (int i = 0; i < MONEY_STEP_SLOTS.length; i++) {
			inventory.setItem(MONEY_STEP_SLOTS[i], TradeItems.stepButton(MONEY_STEPS[i], "내가 줄 " + Money.name() + ": " + Money.format(myMoney)));
		}
		inventory.setItem(MY_MONEY_SLOT, TradeItems.glint(TradeItems.button(Material.GOLD_INGOT, "내가 줄 " + Money.name() + ": " + Money.format(myMoney),
				NamedTextColor.GOLD, bedrock() ? "누르면 숫자 키패드로 입력합니다." : "왼쪽 버튼으로 정합니다. 좌클릭 +10 / 우클릭 -10",
				"내 " + Money.name() + ": " + Money.format(Money.balance(player.getUniqueId())), "교환될 때 빠져나갑니다.")));
		inventory.setItem(TYPE_MONEY_SLOT, TradeItems.button(Material.NAME_TAG, "줄 " + Money.name() + " 직접 입력", NamedTextColor.YELLOW,
				bedrock() ? "숫자 키패드로 금액을 입력합니다." : "채팅으로 금액을 입력합니다. (예: 2500, 1.5만)", "입력하는 동안에도 거래는 이어집니다."));
		inventory.setItem(THEIR_MONEY_SLOT, TradeItems.button(Material.GOLD_NUGGET,
				other.getName() + "님이 줄 " + Money.name() + ": " + Money.format(session.moneyOfOther(player)), NamedTextColor.YELLOW));
		inventory.setItem(CANCEL_SLOT, TradeItems.button(Material.BARRIER, "거래 취소", NamedTextColor.RED, "올린 물건은 모두 돌려받습니다."));
		inventory.setItem(CONFIRM_SLOT, session.confirmed(player)
				? TradeItems.button(Material.LIME_CONCRETE, "확인함 (클릭: 확인 취소)", NamedTextColor.GREEN, "상대도 확인하면 교환됩니다.")
				: TradeItems.button(Material.RED_CONCRETE, "확인하기", NamedTextColor.YELLOW, "양쪽이 모두 확인하면 교환됩니다.",
						"어느 쪽이든 물건을 바꾸면 확인이 풀립니다."));
		inventory.setItem(OTHER_SLOT, session.confirmed(other)
				? TradeItems.button(Material.LIME_DYE, other.getName() + ": 확인함", NamedTextColor.GREEN)
				: TradeItems.button(Material.GRAY_DYE, other.getName() + ": 아직 확인 안 함", NamedTextColor.GRAY));
		inventory.setItem(INFO_SLOT, TradeItems.button(Material.BOOK, "사용법", NamedTextColor.GOLD,
				"왼쪽 4x4: 내가 줄 물건 / 오른쪽 4x4: 받을 물건",
				"아래 내 인벤토리의 아이템을 클릭하면 올리고,", "내가 올린 아이템을 클릭하면 되돌립니다.",
				"금괴 줄: 주고받을 " + Money.name()));
	}

	@Override
	void click(int slot, ClickType click) {
		if (session.done) {
			return;
		}
		if (slot == CANCEL_SLOT) {
			session.cancel(player);
			player.closeInventory();
			return;
		}
		if (slot == CONFIRM_SLOT) {
			clickSound();
			session.toggleConfirm(player);
			return;
		}
		for (int i = 0; i < MONEY_STEP_SLOTS.length; i++) {
			if (slot == MONEY_STEP_SLOTS[i]) {
				offerMoney(session.moneyOf(player) + MONEY_STEPS[i]);
				return;
			}
		}
		if (slot == TYPE_MONEY_SLOT) {
			typeMoney();
			return;
		}
		if (slot == MY_MONEY_SLOT) {
			if (bedrock()) {
				// No right-click on a touch screen: a tap opens the keypad.
				typeMoney();
			} else {
				offerMoney(session.moneyOf(player) + (click.isRightClick() ? -10 : 10));
			}
			return;
		}
		ItemStack[] mine = session.offerOf(player);
		for (int i = 0; i < DirectTrade.OFFER_SLOTS; i++) {
			if (mySlot(i) == slot && mine[i] != null) {
				ItemStack back = mine[i];
				mine[i] = null;
				give(back);
				clickSound();
				session.changed();
				return;
			}
		}
	}

	/** Asks for the money to offer (keypad in the touch mode, chat otherwise); the window comes back afterwards, the trade still on. */
	private void typeMoney() {
		typing = true;
		if (bedrock()) {
			new KeypadMenu(player, "줄 " + Money.object() + " 입력하세요.", 0, Money.balance(player.getUniqueId()), Money::format, value -> {
				typing = false;
				if (session.done) {
					tell("거래가 이미 끝났습니다.", NamedTextColor.GRAY);
					return;
				}
				offerMoney(value);
				open();
			}, () -> {
				typing = false;
				if (!session.done) {
					open();
				}
			}, () -> {
				// Closing the keypad closes the trade window too: the trade is called off as usual.
				typing = false;
				session.cancel(player);
			}).open();
			return;
		}
		Runnable back = () -> {
			typing = false;
			if (!session.done && player.isOnline()) {
				open();
			}
		};
		ChatPrompt.ask(player, "줄 " + Money.object() + " 입력하세요. (내 " + Money.name() + ": " + Money.format(Money.balance(player.getUniqueId())) + ")",
				text -> {
					Long value = parseAmount(text);
					if (session.done) {
						typing = false;
						tell("거래가 이미 끝났습니다.", NamedTextColor.GRAY);
						return;
					}
					if (value == null) {
						tell("숫자로 입력하세요: " + text, NamedTextColor.RED);
					} else {
						offerMoney(value);
					}
					back.run();
				}, back, back);
	}

	/** Changes the money offered, between nothing and the whole balance. */
	private void offerMoney(long amount) {
		long clamped = Math.max(0, Math.min(Money.balance(player.getUniqueId()), amount));
		if (clamped == session.moneyOf(player)) {
			failSound();
			return;
		}
		clickSound();
		session.setMoney(player, clamped);
	}

	/** Puts an item from the player's inventory up for the trade. */
	@Override
	void clickOwn(InventoryClickEvent event) {
		event.setCancelled(true);
		if (session.done) {
			return;
		}
		ItemStack picked = event.getCurrentItem();
		if (picked == null || picked.isEmpty()) {
			return;
		}
		if (refuseTool(picked)) {
			return;
		}
		ItemStack[] mine = session.offerOf(player);
		for (int i = 0; i < DirectTrade.OFFER_SLOTS; i++) {
			if (mine[i] == null) {
				mine[i] = picked.clone();
				event.getClickedInventory().setItem(event.getSlot(), null);
				clickSound();
				session.changed();
				return;
			}
		}
		tell("더 올릴 자리가 없습니다.", NamedTextColor.RED);
		failSound();
	}

	/** Closing the window cancels the trade (not when it closed for typing an amount). */
	@Override
	void closed() {
		if (!session.done && !typing) {
			session.cancel(player);
		}
	}
}
