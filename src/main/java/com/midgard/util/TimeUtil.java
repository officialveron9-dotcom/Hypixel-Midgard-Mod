package com.midgard.util;

/** Hilfsfunktionen zum Formatieren von Zeitspannen für die Anzeige. */
public final class TimeUtil {

	private TimeUtil() {
	}

	/**
	 * Formatiert eine Anzahl Sekunden in eine kurze, lesbare Form, z. B.
	 * "1d 2h", "3h 12m", "12m 30s" oder "45s".
	 */
	public static String format(double seconds) {
		long s = (long) Math.max(0, seconds);
		long days = s / 86400;
		s %= 86400;
		long hours = s / 3600;
		s %= 3600;
		long minutes = s / 60;
		s %= 60;

		if (days > 0) {
			return days + "d " + hours + "h";
		}
		if (hours > 0) {
			return hours + "h " + minutes + "m";
		}
		if (minutes > 0) {
			return minutes + "m " + s + "s";
		}
		return s + "s";
	}
}
