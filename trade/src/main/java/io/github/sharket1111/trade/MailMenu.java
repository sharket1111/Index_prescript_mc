package io.github.sharket1111.trade;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** The mailbox tab: click a mail to take it, or take everything that fits at once. */
final class MailMenu extends Menu {
	private static final int PAGE_SIZE = 18;
	private static final int PREV_SLOT = 21;
	private static final int ALL_SLOT = 22;
	private static final int NEXT_SLOT = 23;
	private final int page;
	private final Map<Integer, Mailbox.Mail> bySlot = new HashMap<>();
	private int pages = 1;

	MailMenu(Player player, int page) {
		super(player, "거래 - 우편함");
		this.page = page;
	}

	@Override
	void draw() {
		bySlot.clear();
		List<Mailbox.Mail> mail = Mailbox.of(player.getUniqueId());
		pages = Math.max(1, (mail.size() + PAGE_SIZE - 1) / PAGE_SIZE);
		int first = Math.min(page, pages - 1) * PAGE_SIZE;
		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm");
		for (int i = 0; i < PAGE_SIZE && first + i < mail.size(); i++) {
			Mailbox.Mail m = mail.get(first + i);
			ItemStack shown = m.item().clone();
			shown.editMeta(meta -> {
				List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
				lore.add(Component.empty());
				lore.add(TradeItems.plain(m.note(), NamedTextColor.YELLOW));
				lore.add(TradeItems.plain(format.format(new Date(m.time())), NamedTextColor.GRAY));
				lore.add(TradeItems.plain("클릭: 받기", NamedTextColor.GRAY));
				meta.lore(lore);
			});
			inventory.setItem(i, shown);
			bySlot.put(i, m);
		}
		if (mail.isEmpty()) {
			inventory.setItem(13, TradeItems.button(Material.BARRIER, "우편함이 비었습니다", NamedTextColor.GRAY));
		}
		inventory.setItem(BACK_SLOT, TradeItems.back());
		inventory.setItem(ALL_SLOT, TradeItems.button(Material.CHEST_MINECART, "모두 받기 (" + mail.size() + "개)", NamedTextColor.GOLD,
				"인벤토리에 들어가는 만큼 모두 받습니다."));
		if (page > 0) {
			inventory.setItem(PREV_SLOT, TradeItems.button(Material.SPECTRAL_ARROW, "이전 페이지", NamedTextColor.WHITE));
		}
		if (page < pages - 1) {
			inventory.setItem(NEXT_SLOT, TradeItems.button(Material.SPECTRAL_ARROW, "다음 페이지", NamedTextColor.WHITE));
		}
	}

	/** Moves a mail into the inventory if it fits completely. */
	private boolean claim(Mailbox.Mail mail) {
		ItemStack item = mail.item();
		if (!fits(item)) {
			return false;
		}
		if (Mailbox.take(player.getUniqueId(), mail)) {
			player.getInventory().addItem(item.clone());
		}
		return true;
	}

	private boolean fits(ItemStack item) {
		int room = 0;
		for (ItemStack slot : player.getInventory().getStorageContents()) {
			if (slot == null || slot.isEmpty()) {
				room += item.getMaxStackSize();
			} else if (slot.isSimilar(item)) {
				room += slot.getMaxStackSize() - slot.getAmount();
			}
			if (room >= item.getAmount()) {
				return true;
			}
		}
		return false;
	}

	@Override
	void click(int slot, ClickType click) {
		if (slot == BACK_SLOT) {
			clickSound();
			new MainMenu(player).open();
		} else if (slot == PREV_SLOT && page > 0) {
			new MailMenu(player, page - 1).open();
		} else if (slot == NEXT_SLOT && page < pages - 1) {
			new MailMenu(player, page + 1).open();
		} else if (slot == ALL_SLOT) {
			int taken = 0;
			for (Mailbox.Mail mail : Mailbox.of(player.getUniqueId())) {
				if (claim(mail)) {
					taken++;
				}
			}
			if (taken > 0) {
				successSound();
			}
			if (!Mailbox.of(player.getUniqueId()).isEmpty()) {
				tell("인벤토리가 가득 차서 일부는 우편함에 남았습니다.", NamedTextColor.YELLOW);
			}
			redraw();
		} else if (bySlot.containsKey(slot)) {
			if (claim(bySlot.get(slot))) {
				successSound();
			} else {
				tell("인벤토리에 자리가 없습니다.", NamedTextColor.RED);
				failSound();
			}
			redraw();
		}
	}
}
