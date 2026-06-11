package com.midgard.events.event;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Liest das aktuelle Dwarven-Mines-/Crystal-Hollows-Mining-Event direkt aus dem
 * Sidebar-Scoreboard (wie SkyHanni). Für Mining-Events gibt es keine API – sie
 * werden alle 20 min zufällig ausgelöst – daher ist das Scoreboard die einzige
 * zuverlässige Quelle.
 *
 * <p>Best-Effort-Parser: sucht in den Scoreboard-Zeilen nach bekannten
 * Event-Namen und einer Restzeit (mm:ss). Zur Feinjustage wird das
 * Dwarven-Scoreboard gedrosselt geloggt ("[Midgard] Dwarven-Scoreboard:").</p>
 */
public class MiningEventReader {

	public static final MiningEventReader INSTANCE = new MiningEventReader();

	/** Bekannte Mining-Event-Namen (Scoreboard-Schreibweise, lowercase-Vergleich). */
	private static final String[] EVENTS = {
			"2x Powder", "Double Powder", "Goblin Raid", "Mithril Gourmand",
			"Raffle", "Fortunate Freezing", "Sweet Tooth", "Better Together",
			"Mineshaft", "Powder Ghast"
	};

	private static final Pattern TIME = Pattern.compile("(\\d{1,2}):(\\d{2})");

	private volatile String activeEvent = null;
	private volatile long endMillis = 0;
	private long lastDebugLog = 0;

	public void update(List<String> lines) {
		boolean dwarven = false;
		for (String l : lines) {
			String low = l.toLowerCase();
			if (low.contains("dwarven mines") || low.contains("crystal hollows") || low.contains("mineshaft")) {
				dwarven = true;
				break;
			}
		}

		String found = null;
		long remaining = -1;
		outer:
		for (int i = 0; i < lines.size(); i++) {
			String low = lines.get(i).toLowerCase();
			for (String ev : EVENTS) {
				if (low.contains(ev.toLowerCase())) {
					found = ev;
					remaining = parseTime(lines.get(i));
					if (remaining < 0 && i + 1 < lines.size()) {
						remaining = parseTime(lines.get(i + 1));
					}
					break outer;
				}
			}
		}

		if (found != null) {
			activeEvent = normalize(found);
			// Falls keine Zeit erkannt wurde: als laufend mit Standarddauer markieren.
			long ms = remaining > 0 ? remaining * 1000L : 5 * 60_000L;
			endMillis = System.currentTimeMillis() + ms;
		} else {
			activeEvent = null;
		}

		// Diagnose: Dwarven-Scoreboard alle 15s mitloggen, um das echte Format zu sehen.
		if (dwarven) {
			long now = System.currentTimeMillis();
			if (now - lastDebugLog > 15_000) {
				lastDebugLog = now;
				System.out.println("[Midgard] Dwarven-Scoreboard (Format-Diagnose):");
				for (String l : lines) {
					System.out.println("  | " + l);
				}
				System.out.println("[Midgard] erkanntes Mining-Event: " + activeEvent);
			}
		}
	}

	private static long parseTime(String line) {
		Matcher m = TIME.matcher(line);
		if (m.find()) {
			return Integer.parseInt(m.group(1)) * 60L + Integer.parseInt(m.group(2));
		}
		return -1;
	}

	private static String normalize(String ev) {
		if (ev.equalsIgnoreCase("Double Powder")) {
			return "2x Powder";
		}
		return ev;
	}

	/** Name des aktuell laufenden Mining-Events, oder null. */
	public String activeEvent() {
		return (activeEvent != null && endMillis > System.currentTimeMillis()) ? activeEvent : null;
	}

	public double remainingSeconds() {
		return (endMillis - System.currentTimeMillis()) / 1000.0;
	}
}
