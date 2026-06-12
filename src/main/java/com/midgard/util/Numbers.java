package com.midgard.util;

import java.util.Locale;

import com.midgard.Midgard;

/**
 * Zentrale Zahlen-Anzeige für ALLE Stellen im Mod (Tooltips, Garden, Leisten).
 * Standard = volle Zahl mit Tausenderpunkten (500.000.000). Mit dem Schalter
 * "Zahlen kürzen" (Auktion-Tab) wird kompakt angezeigt: 12,3k / 500kk / 1,2B.
 */
public final class Numbers {

	private Numbers() {
	}

	private static boolean compact() {
		return Midgard.config != null && Midgard.config.compactNumbers;
	}

	/** Ganzzahlige Anzeige (Counter, Mana, Milestones ...). */
	public static String format(long v) {
		if (!compact()) {
			return String.format(Locale.GERMAN, "%,d", v);
		}
		if (v >= 1_000_000_000L) {
			return trim(v / 1_000_000_000d) + "B";
		}
		if (v >= 1_000_000L) {
			return trim(v / 1_000_000d) + "kk";
		}
		if (v >= 1_000L) {
			return trim(v / 1_000d) + "k";
		}
		return String.valueOf(v);
	}

	/** Münz-/Preis-Anzeige (kann Nachkommastellen unter 1000 haben). */
	public static String coins(double v) {
		if (v < 1_000d) {
			return String.format(Locale.GERMAN, v % 1 == 0 ? "%.0f" : "%.1f", v);
		}
		return format(Math.round(v));
	}

	/** Eine Nachkommastelle, aber "500" statt "500,0". */
	private static String trim(double v) {
		return v % 1 < 0.05 || v >= 100
				? String.format(Locale.GERMAN, "%.0f", v)
				: String.format(Locale.GERMAN, "%.1f", v);
	}
}
