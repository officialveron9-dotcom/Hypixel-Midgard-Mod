package com.midgard.events.skyblock;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.client.MinecraftClient;

/**
 * Erkennt, ob der Spieler gerade in Hypixel SkyBlock ist, und liest das
 * aktuelle SkyBlock-Datum + die Uhrzeit aus dem Scoreboard.
 */
public class SkyblockHook {

	public static final SkyblockHook INSTANCE = new SkyblockHook();

	/** Monatsnamen in Reihenfolge (Index 0 = Monat 1). */
	private static final String[] MONTHS = {
			"Early Spring", "Spring", "Late Spring",
			"Early Summer", "Summer", "Late Summer",
			"Early Autumn", "Autumn", "Late Autumn",
			"Early Winter", "Winter", "Late Winter"
	};

	private static final Pattern DAY = Pattern.compile("(\\d{1,2})(st|nd|rd|th)");
	private static final Pattern TIME =
			Pattern.compile("(\\d{1,2}):(\\d{2})\\s?(am|pm)", Pattern.CASE_INSENSITIVE);

	public boolean onSkyblock = false;
	public boolean dateValid = false;
	public int month = 1, day = 1, hour = 0, minute = 0;

	/** Wird regelmäßig (alle paar Ticks) aufgerufen. */
	public void update(MinecraftClient mc) {
		onSkyblock = false;
		dateValid = false;

		if (mc == null || mc.world == null) {
			SkyblockCalendar.INSTANCE.invalidate();
			return;
		}

		String title = ScoreboardReader.title(mc);
		if (title == null || !title.toUpperCase(Locale.ROOT).contains("SKYBLOCK")) {
			SkyblockCalendar.INSTANCE.invalidate();
			return;
		}
		onSkyblock = true;

		List<String> lines = ScoreboardReader.sidebarLines(mc);
		for (String line : lines) {
			if (!dateValid) {
				int monthIndex = matchMonth(line);
				if (monthIndex > 0) {
					Matcher dm = DAY.matcher(line);
					if (dm.find()) {
						month = monthIndex;
						day = clamp(Integer.parseInt(dm.group(1)), 1, SkyblockCalendar.DAYS_PER_MONTH);
						dateValid = true;
					}
				}
			}

			Matcher tm = TIME.matcher(line);
			if (tm.find()) {
				int h = Integer.parseInt(tm.group(1)) % 12;
				int min = Integer.parseInt(tm.group(2));
				if (tm.group(3).equalsIgnoreCase("pm")) {
					h += 12;
				}
				hour = h;
				minute = min;
			}
		}

		if (dateValid) {
			SkyblockCalendar.INSTANCE.update(month, day, hour, minute);
		} else {
			SkyblockCalendar.INSTANCE.invalidate();
		}

		// Mining-Events live aus dem Scoreboard lesen (keine API möglich).
		com.midgard.events.event.MiningEventReader.INSTANCE.update(lines);
	}

	/** Findet den längsten passenden Monatsnamen (damit "Late Summer" vor "Summer" greift). 0 = nichts. */
	public static int matchMonth(String line) {
		int best = -1;
		int bestLen = 0;
		for (int i = 0; i < MONTHS.length; i++) {
			if (line.contains(MONTHS[i]) && MONTHS[i].length() > bestLen) {
				best = i;
				bestLen = MONTHS[i].length();
			}
		}
		return best + 1; // 0 = nichts gefunden
	}

	private static int clamp(int v, int min, int max) {
		return Math.max(min, Math.min(max, v));
	}
}
