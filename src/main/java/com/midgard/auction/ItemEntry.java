package com.midgard.auction;

import java.util.Locale;

import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/**
 * Ein SkyBlock-Item für die Auktions-Suche: Anzeigename + interne ID + das
 * Vanilla-Material (für ein Icon). Quelle ist die öffentliche Hypixel-Ressource
 * {@code /resources/skyblock/items} (kein API-Key nötig).
 */
public record ItemEntry(String id, String name, String material, String tier) {

	/** Vanilla-Item zum Anzeigen als Icon (bestmögliche Annäherung). */
	public Item icon() {
		String m = material == null ? "" : material.toLowerCase(Locale.ROOT);
		if (m.isEmpty()) {
			return Items.PAPER;
		}
		// Köpfe/Skulls haben kein direktes Vanilla-Item -> Spielerkopf als Platzhalter.
		if (m.contains("skull") || m.contains("player_head")) {
			return Items.PLAYER_HEAD;
		}
		try {
			Item it = Registries.ITEM.get(Identifier.of("minecraft", m));
			return it == Items.AIR ? Items.PAPER : it;
		} catch (Throwable t) {
			return Items.PAPER;
		}
	}
}
