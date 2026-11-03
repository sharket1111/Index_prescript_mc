package io.github.sharket1111.trade;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * The shared storage, all 54 slots. Clicking an item in one's own inventory
 * puts it in; clicking a stored item takes it out if allowed. One's own items
 * (or any, for admins) open their settings instead, where who may take them
 * is chosen; shift-click takes them straight out. Everyone looking sees
 * changes at once. Close the storage to leave it.
 */
final class StorageMenu extends Menu {
	private static final Set<StorageMenu> OPEN = ConcurrentHashMap.newKeySet();

	StorageMenu(Player player) {
		super(player, "공용 보관함 (내 아이템을 눌러 넣기)", 6);
	}

	static void redrawAll() {
		for (StorageMenu menu : OPEN) {
			menu.redraw();
		}
	}

	@Override
	void draw() {
		OPEN.add(this);
		for (int slot = 0; slot < SharedStorage.SIZE; slot++) {
			SharedStorage.Entry entry = SharedStorage.get(slot);
			if (entry == null) {
				continue;
			}
			ItemStack shown = entry.item.clone();
			boolean mine = SharedStorage.may(player, entry, true);
			boolean may = SharedStorage.may(player, entry, false);
			shown.editMeta(meta -> {
				List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
				lore.add(Component.empty());
				lore.add(TradeItems.plain("맡긴 사람: " + entry.ownerName, NamedTextColor.YELLOW));
				lore.add(TradeItems.plain("가져갈 수 있음: " + who(entry), NamedTextColor.AQUA));
				if (mine) {
					lore.add(TradeItems.plain(bedrock() ? "터치: 설정/꺼내기" : "클릭: 설정, Shift+클릭: 바로 꺼내기", NamedTextColor.GRAY));
				} else {
					lore.add(TradeItems.plain(may ? "클릭: 가져가기" : "가져갈 권한이 없습니다", may ? NamedTextColor.GREEN : NamedTextColor.RED));
				}
				meta.lore(lore);
			});
			inventory.setItem(slot, shown);
		}
	}

	static String who(SharedStorage.Entry entry) {
		if (entry.everyone()) {
			return "모든 사람";
		}
		if (entry.rules.isEmpty()) {
			return "맡긴 사람만";
		}
		List<String> names = new ArrayList<>();
		for (String rule : entry.rules) {
			names.add(SharedStorage.describe(rule));
		}
		return String.join(", ", names);
	}

	@Override
	void closed() {
		OPEN.remove(this);
	}

	@Override
	void click(int slot, ClickType click) {
		SharedStorage.Entry entry = SharedStorage.get(slot);
		if (entry == null) {
			return;
		}
		boolean mine = SharedStorage.may(player, entry, true);
		if (mine && !(click.isShiftClick() && !bedrock())) {
			clickSound();
			new StorageItemMenu(player, slot).open();
			return;
		}
		if (!SharedStorage.may(player, entry, false)) {
			tell("이 아이템을 가져갈 권한이 없습니다.", NamedTextColor.RED);
			failSound();
			return;
		}
		takeOut(player, slot);
	}

	/** Moves the stored item into {@code player}'s inventory if it fits. */
	static boolean takeOut(Player player, int slot) {
		SharedStorage.Entry entry = SharedStorage.get(slot);
		if (entry == null) {
			return false;
		}
		if (player.getInventory().firstEmpty() < 0) {
			player.sendMessage(Component.text("인벤토리에 빈 칸이 없습니다.", NamedTextColor.RED));
			return false;
		}
		SharedStorage.clear(slot);
		player.getInventory().addItem(entry.item.clone()).values()
				.forEach(rest -> player.getWorld().dropItem(player.getLocation(), rest));
		player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_PICKUP, 0.6F, 1.0F);
		redrawAll();
		return true;
	}

	/** Clicking an item in one's own inventory stores it. */
	@Override
	void clickOwn(InventoryClickEvent event) {
		event.setCancelled(true);
		ItemStack stack = event.getCurrentItem();
		if (stack == null || stack.isEmpty()) {
			return;
		}
		int slot = SharedStorage.put(stack, player);
		if (slot < 0) {
			tell("공용 보관함이 가득 찼습니다.", NamedTextColor.RED);
			failSound();
			return;
		}
		event.getClickedInventory().setItem(event.getSlot(), null);
		tell("보관했습니다. 아이템을 눌러 가져갈 수 있는 사람을 정하세요. (지금은 나만)", NamedTextColor.GREEN);
		successSound();
		redrawAll();
	}
}
