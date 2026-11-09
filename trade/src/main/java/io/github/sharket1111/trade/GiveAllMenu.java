package io.github.sharket1111.trade;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Admins: paying every player the same amount. The money is new (it does not
 * leave the admin's balance). It goes to the players online now, or to
 * everyone who has ever joined; those away are told on their next join.
 * Sending needs a right-click (a second tap in the touch mode).
 */
final class GiveAllMenu extends Menu {
	private static final int TARGET_SLOT = 4;
	private static final int MESSAGE_SLOT = 6;
	static final String DEFAULT_MESSAGE = "관리자 전체 지급";
	private static final int MESSAGE_MAX = 60;
	private static final int AMOUNT_SLOT = 13;
	private static final int INPUT_SLOT = 14;
	private static final int[] STEP_SLOTS = {9, 10, 11, 15, 16, 17};
	private static final int[] STEPS = MONEY_STEPS;
	private static final int SEND_SLOT = 22;
	private static final long MAX = 100_000_000;
	private long amount = 1000;
	private boolean everyone;
	private String message = DEFAULT_MESSAGE;

	GiveAllMenu(Player player) {
		super(player, "관리 - 전체 지급");
	}

	/** Who would be paid: online players, or everyone who has played here. */
	private Set<UUID> recipients() {
		return recipients(everyone);
	}

	static Set<UUID> recipients(boolean everyone) {
		Set<UUID> ids = new LinkedHashSet<>();
		for (Player online : Bukkit.getOnlinePlayers()) {
			ids.add(online.getUniqueId());
		}
		if (everyone) {
			for (OfflinePlayer known : Bukkit.getOfflinePlayers()) {
				if (known.hasPlayedBefore() || known.isOnline()) {
					ids.add(known.getUniqueId());
				}
			}
		}
		return ids;
	}

	@Override
	void draw() {
		int count = recipients().size();
		inventory.setItem(TARGET_SLOT, TradeItems.glint(TradeItems.button(everyone ? Material.PLAYER_HEAD : Material.LIME_DYE,
				"받는 사람: " + (everyone ? "모든 플레이어" : "접속 중인 플레이어") + " (" + count + "명)", NamedTextColor.YELLOW,
				everyone ? "서버에 한 번이라도 들어온 모든 플레이어입니다." : "지금 접속해 있는 플레이어만 받습니다.",
				everyone ? "접속하지 않은 사람은 다음 접속 때 알림을 받습니다." : "",
				"클릭: " + (everyone ? "접속 중인 플레이어만" : "모든 플레이어") + "(으)로 바꾸기")));
		inventory.setItem(MESSAGE_SLOT, TradeItems.button(Material.WRITABLE_BOOK, "안내 메시지", NamedTextColor.AQUA,
				"\"" + message + "\"", "받는 사람의 채팅과 화면 가운데에 보입니다.",
				"접속하지 않은 사람은 다음 접속 때 내역에 나옵니다.",
				bedrock() ? "누르면 모루에 입력합니다." : "클릭: 채팅으로 입력 ('-'를 입력하면 기본 문구)"));
		inventory.setItem(AMOUNT_SLOT, TradeItems.glint(TradeItems.button(Material.GOLD_INGOT, "한 사람당: " + Money.format(amount),
				NamedTextColor.GOLD, bedrock() ? "클릭: 숫자 키패드로 입력" : "클릭: 채팅으로 직접 입력", "양옆 버튼: 조금씩 바꾸기",
				"합계: " + Money.format(amount * count) + " (" + count + "명)")));
		for (int i = 0; i < STEP_SLOTS.length; i++) {
			inventory.setItem(STEP_SLOTS[i], TradeItems.stepButton(STEPS[i], "한 사람당: " + Money.format(amount)));
		}
		inventory.setItem(INPUT_SLOT, typeButton());
		inventory.setItem(BACK_SLOT, TradeItems.back());
		inventory.setItem(SEND_SLOT, count > 0
				? TradeItems.button(Material.LIME_CONCRETE, "지급하기", NamedTextColor.GREEN,
						count + "명에게 " + Money.format(amount) + "씩 지급합니다.", "안내: \"" + message + "\"", "관리자 잔액에서 빠지지 않습니다.",
						bedrock() ? "두 번 누르면 지급합니다." : "우클릭: 지급")
				: TradeItems.button(Material.GRAY_CONCRETE, "받을 사람이 없습니다", NamedTextColor.GRAY));
	}

	@Override
	void click(int slot, ClickType click) {
		if (!player.hasPermission(AdminMenu.PERMISSION)) {
			player.closeInventory();
			return;
		}
		if (slot == BACK_SLOT) {
			clickSound();
			new AdminMenu(player).open();
			return;
		}
		if (slot == MESSAGE_SLOT) {
			ChatPrompt.ask(player, "전체 지급 안내 메시지를 입력하세요. (1~" + MESSAGE_MAX + "글자, '-'는 기본 문구)", text -> {
				if (text.equals("-")) {
					message = DEFAULT_MESSAGE;
				} else if (text.length() > MESSAGE_MAX) {
					tell(MESSAGE_MAX + "글자까지 쓸 수 있습니다.", NamedTextColor.RED);
				} else {
					message = text.replace("§", "");
				}
				open();
			}, this::open);
			return;
		}
		if (slot == TARGET_SLOT) {
			everyone = !everyone;
			clickSound();
			redraw();
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
		if (slot == AMOUNT_SLOT || slot == INPUT_SLOT) {
			askAmount("한 사람당 지급할 금액을 입력하세요.", 1, MAX, value -> amount = value);
			return;
		}
		if (slot != SEND_SLOT || !confirmed(click)) {
			return;
		}
		Set<UUID> ids = recipients();
		if (ids.isEmpty()) {
			failSound();
			return;
		}
		give(ids, amount, message, player.getName(), everyone);
		tell(ids.size() + "명에게 " + Money.format(amount) + "씩 지급했습니다. (합계 " + Money.format(amount * ids.size()) + ")",
				NamedTextColor.GREEN);
		successSound();
		redraw();
	}

	/** Pays everyone in {@code ids}, posts it on the web page's notices and logs it (from the menu or the web admin page). */
	static void give(Set<UUID> ids, long amount, String message, String admin, boolean everyone) {
		Money.giveAll(ids, amount, message);
		WebNotices.post("전체 지급: " + Money.format(amount), message + " (" + ids.size() + "명)", admin, true);
		TradePlugin.getPlugin(TradePlugin.class).getLogger().info(admin + " gave " + amount + " to " + ids.size()
				+ (everyone ? " players (everyone)" : " online players"));
	}
}
