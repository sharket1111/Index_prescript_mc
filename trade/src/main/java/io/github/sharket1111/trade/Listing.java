package io.github.sharket1111.trade;

import java.util.UUID;

import org.bukkit.inventory.ItemStack;

/** An item a player has put up for sale, held by the market until it is withdrawn. */
final class Listing {
	final UUID id;
	final UUID seller;
	final String sellerName;
	final ItemStack item;
	final long createdAt;
	int price;

	Listing(UUID id, UUID seller, String sellerName, ItemStack item, int price, long createdAt) {
		this.id = id;
		this.seller = seller;
		this.sellerName = sellerName;
		this.item = item;
		this.price = price;
		this.createdAt = createdAt;
	}
}
