package io.github.sharket1111.trade;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Direct trades between two online players: a request that the other accepts
 * from their trade menu, then a shared window where each puts items and
 * money up and both confirm. Any change clears both confirmations, so nothing can be
 * swapped in at the last moment; when both confirm the offers change hands.
 * Leaving, closing the window or quitting returns every offer to its owner.
 */
final class DirectTrade {
	static final int OFFER_SLOTS = 16;
	private static final long REQUEST_MS = 60_000;
	/** Requests by target: requester and when it expires. */
	private static final Map<UUID, Map<UUID, Long>> REQUESTS = new ConcurrentHashMap<>();
	private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

	private DirectTrade() {
	}

	static final class Session {
		final Player a;
		final Player b;
		final ItemStack[] offerA = new ItemStack[OFFER_SLOTS];
		final ItemStack[] offerB = new ItemStack[OFFER_SLOTS];
		/** Money each offers; it stays on their balance until the trade completes. */
		long moneyA;
		long moneyB;
		boolean confirmA;
		boolean confirmB;
		boolean done;
		TradeView viewA;
		TradeView viewB;

		Session(Player a, Player b) {
			this.a = a;
			this.b = b;
		}

		ItemStack[] offerOf(Player p) {
			return p == a ? offerA : offerB;
		}

		ItemStack[] offerOfOther(Player p) {
			return p == a ? offerB : offerA;
		}

		long moneyOf(Player p) {
			return p == a ? moneyA : moneyB;
		}

		long moneyOfOther(Player p) {
			return p == a ? moneyB : moneyA;
		}

		void setMoney(Player p, long amount) {
			if (p == a) {
				moneyA = amount;
			} else {
				moneyB = amount;
			}
			changed();
		}

		boolean confirmed(Player p) {
			return p == a ? confirmA : confirmB;
		}

		Player other(Player p) {
			return p == a ? b : a;
		}

		/** Something changed: both have to confirm again. */
		void changed() {
			confirmA = false;
			confirmB = false;
			refresh();
		}

		void refresh() {
			if (viewA != null) {
				viewA.redraw();
			}
			if (viewB != null) {
				viewB.redraw();
			}
		}

		void toggleConfirm(Player p) {
			if (p == a) {
				confirmA = !confirmA;
			} else {
				confirmB = !confirmB;
			}
			if (confirmA && confirmB) {
				complete();
			} else {
				refresh();
			}
		}

		private void complete() {
			// The money offered must still be there; it is taken only now.
			for (Player p : List.of(a, b)) {
				if (!Money.has(p.getUniqueId(), moneyOf(p))) {
					confirmA = false;
					confirmB = false;
					for (Player q : List.of(a, b)) {
						q.sendMessage(Component.text(p.getName() + "님의 " + Money.name() + "가 올린 금액보다 적어 확인이 풀렸습니다.", NamedTextColor.RED));
					}
					refresh();
					return;
				}
			}
			Money.take(a.getUniqueId(), moneyA);
			Money.take(b.getUniqueId(), moneyB);
			Money.add(b.getUniqueId(), moneyA);
			Money.add(a.getUniqueId(), moneyB);
			done = true;
			end();
			hand(offerA, b, a.getName() + "님과의 거래");
			hand(offerB, a, b.getName() + "님과의 거래");
			for (Player p : List.of(a, b)) {
				p.closeInventory();
				p.sendMessage(Component.text(other(p).getName() + "님과 거래했습니다.", NamedTextColor.GREEN));
				p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_YES, 0.8F, 1.0F);
			}
		}

		/** Ends without trading: every offer goes back to its owner. */
		void cancel(Player by) {
			if (done) {
				return;
			}
			done = true;
			end();
			hand(offerA, a, "취소된 거래에서 돌려받은 물건");
			hand(offerB, b, "취소된 거래에서 돌려받은 물건");
			for (Player p : List.of(a, b)) {
				if (p != by && p.isOnline() && p.getOpenInventory().getTopInventory().getHolder(false) instanceof TradeView) {
					p.closeInventory();
				}
				p.sendMessage(Component.text(by == p ? "거래를 취소했습니다." : by.getName() + "님이 거래를 취소했습니다.", NamedTextColor.YELLOW));
			}
		}

		private void end() {
			SESSIONS.remove(a.getUniqueId());
			SESSIONS.remove(b.getUniqueId());
		}

		private static void hand(ItemStack[] offer, Player to, String note) {
			for (int i = 0; i < offer.length; i++) {
				if (offer[i] != null) {
					Mailbox.deliver(to.getUniqueId(), offer[i], note);
					offer[i] = null;
				}
			}
		}
	}

	static Session session(Player player) {
		return SESSIONS.get(player.getUniqueId());
	}

	/** Asks {@code to} for a trade; they accept from the direct trade tab of their trade menu. */
	static void request(Player from, Player to) {
		if (SESSIONS.containsKey(from.getUniqueId()) || SESSIONS.containsKey(to.getUniqueId())) {
			from.sendMessage(Component.text("이미 거래 중인 플레이어가 있습니다.", NamedTextColor.RED));
			return;
		}
		REQUESTS.computeIfAbsent(to.getUniqueId(), id -> new ConcurrentHashMap<>())
				.put(from.getUniqueId(), System.currentTimeMillis() + REQUEST_MS);
		from.sendMessage(Component.text(to.getName() + "님에게 거래를 요청했습니다. (60초 동안 유효)", NamedTextColor.GREEN));
		to.sendMessage(Component.text(from.getName() + "님이 거래를 요청했습니다. ", NamedTextColor.GOLD)
				.append(Component.text("[거래 메뉴 열기]", NamedTextColor.AQUA).clickEvent(ClickEvent.runCommand("/trade")))
				.append(Component.text(" 직접 거래 탭에서 수락할 수 있습니다.", NamedTextColor.GRAY)));
		to.playSound(to.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BELL, 1.0F, 1.2F);
	}

	/** Online players with a live request to {@code target}. */
	static List<Player> requestsFor(Player target) {
		List<Player> list = new ArrayList<>();
		Map<UUID, Long> mine = REQUESTS.get(target.getUniqueId());
		if (mine == null) {
			return list;
		}
		long now = System.currentTimeMillis();
		mine.entrySet().removeIf(e -> e.getValue() < now || Bukkit.getPlayer(e.getKey()) == null);
		mine.keySet().forEach(id -> list.add(Bukkit.getPlayer(id)));
		return list;
	}

	static void accept(Player target, Player requester) {
		Map<UUID, Long> mine = REQUESTS.get(target.getUniqueId());
		if (mine == null || mine.remove(requester.getUniqueId()) == null || !requester.isOnline()) {
			target.sendMessage(Component.text("만료된 거래 요청입니다.", NamedTextColor.RED));
			return;
		}
		if (SESSIONS.containsKey(target.getUniqueId()) || SESSIONS.containsKey(requester.getUniqueId())) {
			target.sendMessage(Component.text("이미 거래 중입니다.", NamedTextColor.RED));
			return;
		}
		Session session = new Session(requester, target);
		SESSIONS.put(requester.getUniqueId(), session);
		SESSIONS.put(target.getUniqueId(), session);
		session.viewA = new TradeView(requester, session);
		session.viewB = new TradeView(target, session);
		session.viewA.open();
		session.viewB.open();
	}

	static void decline(Player target, Player requester) {
		Map<UUID, Long> mine = REQUESTS.get(target.getUniqueId());
		if (mine != null && mine.remove(requester.getUniqueId()) != null) {
			requester.sendMessage(Component.text(target.getName() + "님이 거래 요청을 거절했습니다.", NamedTextColor.YELLOW));
		}
	}

	/** A player left: their trade is cancelled and their requests dropped. */
	static void leave(Player player) {
		Session session = SESSIONS.get(player.getUniqueId());
		if (session != null) {
			session.cancel(player);
		}
		REQUESTS.remove(player.getUniqueId());
		REQUESTS.values().forEach(map -> map.remove(player.getUniqueId()));
	}

	static void cancelAll() {
		for (Session session : List.copyOf(SESSIONS.values())) {
			session.cancel(session.a);
		}
	}
}
