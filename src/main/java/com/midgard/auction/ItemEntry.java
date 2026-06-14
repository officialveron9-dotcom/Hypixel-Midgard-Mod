package com.midgard.auction;

import java.util.Locale;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/**
 * Ein SkyBlock-Item für die Auktions-Suche. Quelle: öffentliche Hypixel-Ressource
 * {@code /resources/skyblock/items} (kein API-Key). {@code skin} (Kopf-Textur)
 * wird mitgeführt für echte Item-Icons (folgt als eigener Schritt); aktuell wird
 * ein Vanilla-Material als Icon genutzt.
 */
public record ItemEntry(String id, String name, String material, String tier,
		String category, String skin, Map<String, Double> stats, double npcSell) {

	/** Anzeige-ItemStack (Vanilla-Material; Texturepack greift bei passendem Item). */
	public ItemStack buildIcon() {
		return new ItemStack(materialItem());
	}

	private Item materialItem() {
		String m = material == null ? "" : material.toLowerCase(Locale.ROOT);
		int colon = m.indexOf(':');
		if (colon >= 0) {
			m = m.substring(0, colon); // Legacy "INK_SACK:4" -> Basisname
		}
		if (m.isEmpty()) {
			return Items.PAPER;
		}
		if (m.contains("skull") || m.contains("player_head")) {
			return Items.PLAYER_HEAD;
		}
		if (m.contains("enchanted_book") || m.equals("book")) {
			return Items.ENCHANTED_BOOK;
		}
		try {
			Item it = Registries.ITEM.get(Identifier.of("minecraft", m));
			return it == Items.AIR ? Items.PAPER : it;
		} catch (Throwable t) {
			return Items.PAPER;
		}
	}

	/** Anzeige-Item (gecacht in {@link ItemIndex}). */
	public ItemStack icon() {
		return ItemIndex.INSTANCE.iconFor(this);
	}
}
