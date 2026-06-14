package com.midgard.auction;

/**
 * Eine aktive Auktion (schlankes Modell – {@code item_bytes} wird beim Laden
 * einmal dekodiert und sofort verworfen, nur diese Felder bleiben im RAM).
 */
public record Auction(
		String itemName,   // §-formatierter Anzeigename (inkl. Sterne-Symbole)
		String cleanName,  // §-frei, lowercase (für Suche/Sort)
		String internalId, // z. B. "HYPERION" (aus NBT ExtraAttributes.id)
		String category,   // weapon/armor/accessories/... (API-Feld)
		String tier,       // COMMON..DIVINE/SPECIAL (API-Feld)
		long startingBid,
		long highestBid,
		long end,          // Unix-ms; Restzeit = end - now
		boolean bin,       // Sofortkauf
		int stars,         // 0-10
		String skinValue,  // base64 Kopf-Textur (oder leer)
		int bidCount) {

	/** Effektiver Preis: BIN = startingBid, sonst das höchste Gebot (bzw. Startgebot). */
	public long price() {
		return bin ? startingBid : Math.max(startingBid, highestBid);
	}
}
