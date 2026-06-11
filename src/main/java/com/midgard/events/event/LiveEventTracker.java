package com.midgard.events.event;

import java.util.EnumMap;
import java.util.Map;

/**
 * Erkennt Events, die keinen festen Kalendertermin haben, anhand der
 * Chat-Ankündigungen von Hypixel und merkt sich, bis wann sie laufen.
 *
 * <p>Die Dauern sind Richtwerte. Wenn Hypixel eine Ankündigung mit
 * abweichendem Text/Dauer bringt, lässt sich das hier leicht ergänzen –
 * jede Regel ist eine Zeile in {@link #onChat(String)}.</p>
 */
public class LiveEventTracker {

	public static final LiveEventTracker INSTANCE = new LiveEventTracker();

	/** EventType -> Zeitpunkt (System.currentTimeMillis), an dem es endet. */
	private final Map<EventType, Long> activeUntil = new EnumMap<>(EventType.class);

	public void onChat(String rawMessage) {
		if (rawMessage == null || rawMessage.isEmpty()) {
			return;
		}
		String msg = rawMessage.toLowerCase();

		// Hinweis: Jacob's Contest kommt aus der elitebot.dev-API (JacobApi),
		// nicht aus dem Chat, da dort die drei Crops nicht zuverlässig stehen.

		// Mining Fiesta (ca. 1 Stunde).
		if (msg.contains("mining fiesta")) {
			activate(EventType.MINING_FIESTA, 60 * 60_000L);
		}

		// Fishing Festival (ca. 1 SkyBlock-Stunde ~ ebenfalls grob 1 Stunde).
		if (msg.contains("fishing festival")) {
			activate(EventType.FISHING_FESTIVAL, 60 * 60_000L);
		}

		// Dark Auction (ist ca. 5 Minuten offen).
		if (msg.contains("dark auction")) {
			activate(EventType.DARK_AUCTION, 5 * 60_000L);
		}

		// Bürgermeister-Wahl / neuer Bürgermeister.
		if (msg.contains("election") || msg.contains("mayor")) {
			activate(EventType.MAYOR_ELECTION, 10 * 60_000L);
		}
	}

	private void activate(EventType type, long durationMillis) {
		activeUntil.put(type, System.currentTimeMillis() + durationMillis);
	}

	private void clear(EventType type) {
		activeUntil.remove(type);
	}

	/** Aktuell laufende Live-Events (abgelaufene werden aussortiert). */
	public Map<EventType, Long> snapshot() {
		long now = System.currentTimeMillis();
		Map<EventType, Long> out = new EnumMap<>(EventType.class);
		for (Map.Entry<EventType, Long> e : activeUntil.entrySet()) {
			if (e.getValue() > now) {
				out.put(e.getKey(), e.getValue());
			}
		}
		return out;
	}
}
