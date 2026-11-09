package io.github.sharket1111.trade;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import net.kyori.adventure.text.format.NamedTextColor;

/** Sending money to a picked player; they are told at once or, if away, on their next join. */
final class PayMenu extends Menu {
	private static final int AMOUNT_SLOT = 13;
	private static final int[] STEP_SLOTS = {9, 10, 11, 15, 16, 17};
	private static final int[] STEPS = MONEY_STEPS;
	private static final int INPUT_SLOT = 14;
	private static final int SEND_SLOT = 22;
	private static final long MAX = Money.MAX_BALANCE;
	private final UUID target;
	private final String name;
	private long amount = 100;

	PayMenu(Player player, UUID target, String name) {
		super(player, "거래 - " + name + "에게 송금");
		this.target = target;
		this.name = name;
	}

	static void open(Player player) {
		new PlayerPicker(player, "거래 - 송금할 플레이어", false, false, 0, () -> new MainMenu(player),
				(id, name) -> new PayMenu(player, id, name).open()).open();
	}

	@Override
	void draw() {
		long have = Money.balance(player.getUniqueId());
		inventory.setItem(4, TradeItems.button(Material.PLAYER_HEAD, "받는 사람: " + name, NamedTextColor.YELLOW,
				Bukkit.getPlayer(target) != null ? "접속 중: 바로 받습니다." : "접속 안 함: 다음 접속 때 알려줍니다."));
		inventory.setItem(AMOUNT_SLOT, TradeItems.glint(TradeItems.button(Material.GOLD_INGOT, "보낼 금액: " + Money.format(amount),
				NamedTextColor.GOLD, "클릭: 채팅으로 직접 입력", "양옆 버튼: 조금씩 바꾸기", "내 " + Money.name() + ": " + Money.format(have))));
		for (int i = 0; i < STEP_SLOTS.length; i++) {
			inventory.setItem(STEP_SLOTS[i], TradeItems.stepButton(STEPS[i], "현재 금액: " + Money.format(amount)));
		}
		inventory.setItem(INPUT_SLOT, typeButton());
		inventory.setItem(BACK_SLOT, TradeItems.back());
		inventory.setItem(SEND_SLOT, have >= amount
				? TradeItems.button(Material.LIME_CONCRETE, "보내기", NamedTextColor.GREEN, Money.format(amount) + "을(를) 보냅니다.")
				: TradeItems.button(Material.GRAY_CONCRETE, Money.subject() + " 부족합니다", NamedTextColor.RED));
	}

	@Override
	void click(int slot, ClickType click) {
		if (slot == BACK_SLOT) {
			clickSound();
			open(player);
			return;
		}
		for (int i = 0; i < STEP_SLOTS.length; i++) {
			if (slot == STEP_SLOTS[i]) {
				amount = Math.max(1, Math.min(MAX, amount + STEPS[i]));
				clickSound();
				redraw();
				return;
			}
		}
		if (slot == INPUT_SLOT || slot == AMOUNT_SLOT) {
			askAmount("보낼 금액을 입력하세요.", 1, Math.max(1, Money.balance(player.getUniqueId())), value -> amount = value);
			return;
		}
		if (slot != SEND_SLOT) {
			return;
		}
		if (target.equals(player.getUniqueId())) {
			tell("자신에게는 보낼 수 없습니다.", NamedTextColor.RED);
			failSound();
			return;
		}
		if (!Money.take(player.getUniqueId(), amount)) {
			tell(Money.subject() + " 부족합니다.", NamedTextColor.RED);
			failSound();
			return;
		}
		Money.give(target, amount, net.kyori.adventure.text.Component.text(player.getName() + "님이 " + Money.format(amount) + "을(를) 보냈습니다.",
				NamedTextColor.GREEN), player.getName() + "님의 송금");
		tell(name + "님에게 " + Money.format(amount) + "을(를) 보냈습니다.", NamedTextColor.GREEN);
		successSound();
		new MainMenu(player).open();
	}
}
