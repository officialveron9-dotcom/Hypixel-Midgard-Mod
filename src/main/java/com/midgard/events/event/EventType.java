package com.midgard.events.event;

import java.util.List;

/**
 * Alle vom HUD unterstützten Events.
 *
 * <p>Es gibt zwei Arten von Events:</p>
 * <ul>
 *     <li><b>Kalender-Events</b> – haben feste Termine im SkyBlock-Kalender
 *         (Monat/Tag). Der Countdown wird aus dem aktuellen SkyBlock-Datum
 *         berechnet, das aus dem Scoreboard ausgelesen wird.</li>
 *     <li><b>Live-/API-Events</b> – haben keinen festen Termin. Sie werden
 *         entweder live im Chat erkannt ({@link LiveEventTracker}) oder über
 *         eine API geladen (Jacob's Contest via elitebot.dev). Diese haben
 *         eine leere Terminliste.</li>
 * </ul>
 */
public enum EventType {

	// --- Live/API erkannte Events (kein fester Termin) --------------------
	JACOB_CONTEST("Jacob's Farming Contest", EventCategory.JACOB,
			"Zeigt den nächsten und laufenden Farming-Contest samt der drei Crops. Daten direkt aus dem SkyBlock-Kalender im Spiel."),
	DARK_AUCTION("Dark Auction", EventCategory.OTHER,
			"Erinnerung, wenn die Dark Auction läuft (im Chat erkannt)."),
	MINING_FIESTA("Mining Fiesta", EventCategory.MINING,
			"Mining-Event mit doppeltem Powder – wird live im Chat erkannt."),
	MINING_EVENT("Mining-Event", EventCategory.MINING,
			"Aktuelles Dwarven-Mines-Event (2× Powder, Goblin Raid …) – live aus dem Scoreboard."),
	FISHING_FESTIVAL("Fishing Festival", EventCategory.FISHING,
			"Angel-Event mit mehr Sea Creatures – wird live im Chat erkannt."),
	MAYOR_ELECTION("Bürgermeister-Wahl", EventCategory.OTHER,
			"Hinweis auf die Wahl bzw. einen neuen Bürgermeister."),

	// --- Kalender-Events (fester Termin: Monat, Tag, Dauer in SkyBlock-Tagen)
	TRAVELING_ZOO("Traveling Zoo", EventCategory.CALENDAR,
			"Der Traveling Zoo besucht den Hub. Erscheint alle paar SkyBlock-Monate.",
			occ(4, 1, 1), occ(10, 1, 1)),
	SPOOKY_FESTIVAL("Spooky Festival", EventCategory.CALENDAR,
			"Das Halloween-Event im SkyBlock-Herbst mit Candy & Spooky-Drops.",
			occ(8, 29, 3)),
	SEASON_OF_JERRY("Season of Jerry", EventCategory.CALENDAR,
			"Jerrys Werkstatt im SkyBlock-Winter (Geschenke & Gifts).",
			occ(12, 1, 13)),
	NEW_YEAR_CELEBRATION("New Year Celebration", EventCategory.CALENDAR,
			"Die Neujahrsfeier – hier gibt es den begehrten New Year Cake.",
			occ(12, 29, 1));

	/** Ein fester Termin eines Kalender-Events. */
	public record Occurrence(int month, int day, int durationDays) {
	}

	public final String displayName;
	public final EventCategory category;
	public final String description;
	public final List<Occurrence> occurrences;

	EventType(String displayName, EventCategory category, String description, Occurrence... occurrences) {
		this.displayName = displayName;
		this.category = category;
		this.description = description;
		this.occurrences = List.of(occurrences);
	}

	/** true, wenn das Event keinen festen Termin hat und live/per API erkannt wird. */
	public boolean isLiveOnly() {
		return occurrences.isEmpty();
	}

	private static Occurrence occ(int month, int day, int durationDays) {
		return new Occurrence(month, day, durationDays);
	}
}
