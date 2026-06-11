package com.midgard.events.skyblock;

/**
 * Rechnet mit dem SkyBlock-Kalender.
 *
 * <p>Im SkyBlock dauert ein Tag 20 reale Minuten. Ein Monat hat 31 Tage,
 * ein Jahr 12 Monate (= 372 Tage). Aus dem aktuellen, vom Scoreboard
 * gelesenen Datum + Uhrzeit lässt sich damit ausrechnen, wie viele
 * <i>echte</i> Sekunden es bis zu einem bestimmten Kalendertermin dauert.</p>
 */
public class SkyblockCalendar {

	public static final SkyblockCalendar INSTANCE = new SkyblockCalendar();

	/** 20 reale Minuten = 1 SkyBlock-Tag. */
	public static final double REAL_SECONDS_PER_SB_DAY = 20 * 60;
	public static final int DAYS_PER_MONTH = 31;
	public static final int DAYS_PER_YEAR = DAYS_PER_MONTH * 12; // 372

	private boolean valid = false;
	private int month = 1, day = 1, hour = 0, minute = 0;

	public void update(int month, int day, int hour, int minute) {
		this.month = month;
		this.day = day;
		this.hour = hour;
		this.minute = minute;
		this.valid = true;
	}

	public void invalidate() {
		this.valid = false;
	}

	public boolean isValid() {
		return valid;
	}

	/** Aktuelle Position im Jahr als Kommazahl in SkyBlock-Tagen (0 .. 372). */
	private double currentAbsoluteDays() {
		double dayOfYear = (month - 1) * DAYS_PER_MONTH + (day - 1);
		double fractionOfDay = (hour * 60 + minute) / 1440.0; // 24h * 60min
		return dayOfYear + fractionOfDay;
	}

	/** Reale Sekunden bis zum nächsten Beginn des Termins (Monat/Tag). */
	public double realSecondsUntilStart(int targetMonth, int targetDay) {
		double current = currentAbsoluteDays();
		double target = (targetMonth - 1) * DAYS_PER_MONTH + (targetDay - 1);
		double delta = target - current;
		if (delta < 0) {
			delta += DAYS_PER_YEAR; // erst nächstes Jahr wieder
		}
		return delta * REAL_SECONDS_PER_SB_DAY;
	}

	/**
	 * Reale Sekunden bis zu den nächsten {@code count} Starts dieses Termins
	 * (über Jahresgrenzen hinweg).
	 */
	public java.util.List<Double> upcomingStarts(int targetMonth, int targetDay, int count) {
		java.util.List<Double> out = new java.util.ArrayList<>();
		double current = currentAbsoluteDays();
		double base = (targetMonth - 1) * DAYS_PER_MONTH + (targetDay - 1);
		for (int year = 0; year <= count + 2 && out.size() < count; year++) {
			double delta = base + year * DAYS_PER_YEAR - current;
			if (delta > 0) {
				out.add(delta * REAL_SECONDS_PER_SB_DAY);
			}
		}
		return out;
	}

	/**
	 * Falls der Termin gerade aktiv ist: reale Sekunden bis zum Ende.
	 * Andernfalls -1.
	 */
	public double realSecondsActiveRemaining(int targetMonth, int targetDay, int durationDays) {
		double current = currentAbsoluteDays();
		double start = (targetMonth - 1) * DAYS_PER_MONTH + (targetDay - 1);
		// Sowohl den Termin dieses Jahres als auch den des Vorjahres prüfen,
		// damit ein noch laufendes Event über den Jahreswechsel erkannt wird.
		for (double s : new double[]{start, start - DAYS_PER_YEAR}) {
			if (current >= s && current < s + durationDays) {
				return (s + durationDays - current) * REAL_SECONDS_PER_SB_DAY;
			}
		}
		return -1;
	}
}
