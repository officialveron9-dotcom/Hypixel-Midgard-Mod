package com.midgard.auction;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Filter-/Sortier-Zustand des Auction-Browsers (rein lokal). Wird vom Screen
 * verändert; {@link AuctionData#query(AuctionFilter)} wendet ihn an.
 */
public final class AuctionFilter {

	public enum Sort {
		LOWEST("Preis ↑"), HIGHEST("Preis ↓"), ENDING("Endet bald"), BIDS("Gebote");

		public final String label;

		Sort(String l) {
			this.label = l;
		}
	}

	public enum Type {
		ALL("Alle"), BIN("Sofortkauf"), AUCTION("Auktion");

		public final String label;

		Type(String l) {
			this.label = l;
		}
	}

	public String search = "";
	public String category = "";          // "" = alle
	public final Set<String> rarities = new HashSet<>(); // leer = alle
	public int starMin = 0;
	public int starMax = 10;
	public Type type = Type.BIN;          // Default Sofortkauf
	public Sort sort = Sort.LOWEST;
	public long priceMin = 0;
	public long priceMax = 0;             // 0 = unbegrenzt

	public Sort sort() {
		return sort;
	}

	public boolean matches(Auction a) {
		if (type == Type.BIN && !a.bin()) {
			return false;
		}
		if (type == Type.AUCTION && a.bin()) {
			return false;
		}
		if (!category.isEmpty() && !category.equalsIgnoreCase(a.category())) {
			return false;
		}
		if (!rarities.isEmpty() && !rarities.contains(a.tier() == null ? "" : a.tier().toUpperCase(Locale.ROOT))) {
			return false;
		}
		if (a.stars() < starMin || a.stars() > starMax) {
			return false;
		}
		long p = a.price();
		if (priceMin > 0 && p < priceMin) {
			return false;
		}
		if (priceMax > 0 && p > priceMax) {
			return false;
		}
		if (!search.isEmpty()) {
			String q = search.toLowerCase(Locale.ROOT);
			if (!a.cleanName().contains(q) && !a.internalId().toLowerCase(Locale.ROOT).contains(q)) {
				return false;
			}
		}
		return true;
	}
}
