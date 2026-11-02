package io.github.sharket1111.trade;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import net.kyori.adventure.text.format.NamedTextColor;

/** Sending trade diamonds to a picked player; they get them at once or, if away, in their mailbox. */
final class PayMenu extends Menu {
	private static final int AMOUNT_SLOT = 13;
	private static final int[] STEP_SLOTS = {9, 10, 11, 15, 16, 17};
	private static final int[] STEPS = {-64, -10, -1, 1, 10, 64};
	private static final int SEND_SLOT = 22;
	private static final int MAX = 9999;
	private final UUID target;
	private final String name;
	private int amount = 1;

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
		int have = count(TradeItems::isTradeDiamond);
		inventory.setItem(4, TradeItems.button(Material.PLAYER_HEAD, "받는 사람: " + name, NamedTextColor.YELLOW,
				Bukkit.getPlayer(target) != null ? "접속 중: 바로 받습니다." : "접속 안 함: 우편함으로 받습니다."));
		inventory.setItem(AMOUNT_SLOT, TradeItems.glint(TradeItems.button(Material.DIAMOND, amount, "보낼 개수: " + amount + "개",
				NamedTextColor.AQUA, "내 거래 전용 다이아: " + have + "개")));
		for (int i = 0; i < STEP_SLOTS.length; i++) {
			inventory.setItem(STEP_SLOTS[i], TradeItems.stepButton(STEPS[i], "현재 개수: " + amount + "개"));
		}
		inventory.setItem(BACK_SLOT, TradeItems.back());
		inventory.setItem(SEND_SLOT, have >= amount
				? TradeItems.button(Material.LIME_CONCRETE, "보내기", NamedTextColor.GREEN, "거래 전용 다이아 " + amount + "개를 보냅니다.")
				: TradeItems.button(Material.GRAY_CONCRETE, "거래 전용 다이아가 부족합니다", NamedTextColor.RED));
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
		if (slot != SEND_SLOT) {
			return;
		}
		if (count(TradeItems::isTradeDiamond) < amount) {
			tell("거래 전용 다이아가 부족합니다.", NamedTextColor.RED);
			failSound();
			return;
		}
		take(TradeItems::isTradeDiamond, amount);
		Mailbox.deliverDiamonds(target, amount, player.getName() + "님의 송금");
		Player online = Bukkit.getPlayer(target);
		if (online != null) {
			online.sendMessage(net.kyori.adventure.text.Component.text(player.getName() + "님이 거래 전용 다이아 " + amount + "개를 보냈습니다.",
					NamedTextColor.GREEN));
		}
		tell(name + "님에게 거래 전용 다이아 " + amount + "개를 보냈습니다.", NamedTextColor.GREEN);
		successSound();
		new MainMenu(player).open();
	}
}
