package com.midgard.events.hud;

import java.util.ArrayList;
import java.util.List;

import com.midgard.events.event.EventType;
import com.midgard.garden.GardenHud;
import com.midgard.mining.MiningHud;

/**
 * Zentrale Liste ALLER HUD-Elemente mit Standort und Standard-Zustand. Der
 * HUD-Editor gruppiert danach (Garten / Mining / Überall), und Render +
 * Config nutzen denselben Schlüssel. Events sind je Typ ein eigenes Element
 * im Standort "Überall" (sie erscheinen weltweit).
 */
public final class HudElements {

	/** Standort, an dem ein Element standardmäßig erscheint. */
	public enum Location {
		GARDEN("Garten"),
		MINING("Mining"),
		GLOBAL("Überall");

		public final String label;

		Location(String label) {
			this.label = label;
		}
	}

	public record Element(String key, String name, Location location, boolean defaultOn) {
	}

	/** Feste (nicht-Event-) Elemente. */
	private static final List<Element> FIXED = List.of(
			new Element(GardenHud.KEY_VISITORS, "Besucher", Location.GARDEN, true),
			new Element(GardenHud.KEY_PESTS, "Schädlinge", Location.GARDEN, true),
			new Element(GardenHud.KEY_STATS, "Farming", Location.GARDEN, true),
			new Element(GardenHud.KEY_COMPOSTER, "Composter", Location.GARDEN, true),
			new Element(GardenHud.KEY_JACOB, "Jacob Live", Location.GARDEN, true),
			new Element(MiningHud.KEY_COMMISSIONS, "Commissions", Location.MINING, true),
			new Element(MiningHud.KEY_ABILITY, "Pickaxe", Location.MINING, true),
			new Element(MiningHud.KEY_EVENT, "Mining-Event", Location.MINING, true),
			new Element(MiningHud.KEY_POWDER, "Powder", Location.MINING, true));

	private HudElements() {
	}

	/** Alle Elemente (feste + ein Element pro Event-Typ). */
	public static List<Element> all() {
		List<Element> out = new ArrayList<>(FIXED);
		for (EventType t : EventType.values()) {
			if (t == EventType.MINING_EVENT) {
				continue; // Mining-Event hat sein eigenes Element im Mining-Standort
			}
			out.add(new Element(t.name(), t.displayName, Location.GLOBAL, true));
		}
		return out;
	}

	/** Elemente eines Standorts. */
	public static List<Element> forLocation(Location loc) {
		List<Element> out = new ArrayList<>();
		for (Element e : all()) {
			if (e.location() == loc) {
				out.add(e);
			}
		}
		return out;
	}

	public static Element byKey(String key) {
		for (Element e : all()) {
			if (e.key().equals(key)) {
				return e;
			}
		}
		return null;
	}

	public static boolean defaultOn(String key) {
		Element e = byKey(key);
		return e == null || e.defaultOn();
	}

	/** Standort eines Schlüssels (Default GLOBAL, falls unbekannt). */
	public static Location locationOf(String key) {
		Element e = byKey(key);
		return e == null ? Location.GLOBAL : e.location();
	}
}
