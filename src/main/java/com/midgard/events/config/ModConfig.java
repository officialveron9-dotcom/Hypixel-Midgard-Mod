package com.midgard.events.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.midgard.events.event.EventCategory;
import com.midgard.events.event.EventType;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Speicherbare Konfiguration. Wird als JSON unter
 * {@code config/midgardevents.json} abgelegt.
 */
public class ModConfig {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH =
			FabricLoader.getInstance().getConfigDir().resolve("midgard.json");

	// --- gespeicherte Felder ----------------------------------------------
	public boolean masterEnabled = true;
	public boolean onlyOnSkyblock = true;
	public boolean showBackground = true;
	/** Gewählte globale Schriftart (key aus MidgardFont.FONTS; "" = aus). */
	public String globalFontName = "";
	/** Bazaar-Preise im Item-Tooltip anzeigen. */
	public boolean showPrices = true;

	// --- Garden (alles einzeln schaltbar) -----------------------------------
	/** Besucherliste im HUD. */
	public boolean gardenVisitors = true;
	/** Schädlinge: Anzahl + befallene Plots (aktueller Plot hervorgehoben). */
	public boolean gardenPests = true;
	/** Collection: Zeit bis zum nächsten Rang. */
	public boolean gardenCollection = true;
	/** Werkzeug: Cultivating-Level + Fortschritt. */
	public boolean gardenTool = true;
	/** Farming-Statistik (Crops/min, Blöcke/s ...). */
	public boolean gardenStats = true;
	/** Composter-Status (Material/Brennstoff/Restzeit). */
	public boolean gardenComposter = true;
	/** Jacob-Contest live: Score, Platzierung, nächste Medaille, Endzeit. */
	public boolean gardenJacob = true;
	/** Lokale Chat-Warnung 5 Min / 1 Min vor dem nächsten Jacob-Contest. */
	public boolean jacobWarn = true;
	/** URL der prices.json (AH-Statistik + Jacob-Plan). Leer = nur direkte Bazaar-Preise. */
	public String priceApiUrl = "https://officialveron9-dotcom.github.io/Hypixel-Midgard-Mod/prices.json";

	public int hudX = 5;
	public int hudY = 40;
	public float hudScale = 1.0f;
	public float hudIconScale = 1.0f;

	/** Pro Event: an/aus. Schlüssel = EventType.name(). */
	public Map<String, Boolean> eventEnabled = new HashMap<>();
	/** Pro Kategorie: an/aus. Schlüssel = EventCategory.name(). */
	public Map<String, Boolean> categoryEnabled = new HashMap<>();
	/** Pro Kategorie: Anzahl kommender Events, die zusätzlich angezeigt werden. */
	public Map<String, Integer> upcomingPerCategory = new HashMap<>();
	/** Pro Event: Anzahl kommender Events, die zusätzlich angezeigt werden. */
	public Map<String, Integer> upcomingPerEvent = new HashMap<>();
	/** Pro HUD-Gruppe: eigene Position (vom Stapel gelöst). Schlüssel = EventType.name(). */
	public Map<String, Integer> hudGroupX = new HashMap<>();
	public Map<String, Integer> hudGroupY = new HashMap<>();
	/** Pro HUD-Gruppe: Größenfaktor (1.0 = Standard). */
	public Map<String, Float> hudGroupScale = new HashMap<>();

	// --- Logik -------------------------------------------------------------

	public boolean isCategoryEnabled(EventCategory category) {
		return categoryEnabled.getOrDefault(category.name(), true);
	}

	public void setCategoryEnabled(EventCategory category, boolean value) {
		categoryEnabled.put(category.name(), value);
	}

	public boolean isEventEnabled(EventType type) {
		if (!isCategoryEnabled(type.category)) {
			return false;
		}
		return eventEnabled.getOrDefault(type.name(), true);
	}

	public void setEventEnabled(EventType type, boolean value) {
		eventEnabled.put(type.name(), value);
	}

	/** Anzahl kommender Events einer Kategorie (1..10, Standard 1). */
	public int getUpcoming(EventCategory category) {
		return Math.max(1, Math.min(10, upcomingPerCategory.getOrDefault(category.name(), 1)));
	}

	public void setUpcoming(EventCategory category, int value) {
		upcomingPerCategory.put(category.name(), Math.max(1, Math.min(10, value)));
	}

	/** Anzahl kommender Events eines einzelnen Events (1..10, Standard 1). */
	public int getUpcomingEvent(EventType type) {
		return Math.max(1, Math.min(10, upcomingPerEvent.getOrDefault(type.name(), 1)));
	}

	public void setUpcomingEvent(EventType type, int value) {
		upcomingPerEvent.put(type.name(), Math.max(1, Math.min(10, value)));
	}

	// --- HUD-Gruppen (Einzel-Position + Einzel-Größe) ------------------------
	// Schlüssel = EventType.name() für Event-Gruppen bzw. "GARDEN_*" für Garden.

	public boolean hasGroupPos(String key) {
		return hudGroupX.containsKey(key) && hudGroupY.containsKey(key);
	}

	public int groupX(String key) {
		return hudGroupX.getOrDefault(key, hudX);
	}

	public int groupY(String key) {
		return hudGroupY.getOrDefault(key, hudY);
	}

	public void setGroupPos(String key, int x, int y) {
		hudGroupX.put(key, x);
		hudGroupY.put(key, y);
	}

	/** Zurück in den normalen Stapel. */
	public void clearGroupPos(String key) {
		hudGroupX.remove(key);
		hudGroupY.remove(key);
	}

	/** Größenfaktor einer HUD-Gruppe (0.5..2.5, Standard 1.0). */
	public float groupScale(String key) {
		Float f = hudGroupScale.get(key);
		return f == null ? 1f : Math.max(0.5f, Math.min(2.5f, f));
	}

	public void setGroupScale(String key, float value) {
		hudGroupScale.put(key, Math.max(0.5f, Math.min(2.5f, value)));
	}

	// --- Laden / Speichern -------------------------------------------------

	public static ModConfig load() {
		try {
			if (Files.exists(PATH)) {
				String json = Files.readString(PATH);
				ModConfig cfg = GSON.fromJson(json, ModConfig.class);
				if (cfg != null) {
					if (cfg.eventEnabled == null) {
						cfg.eventEnabled = new HashMap<>();
					}
					if (cfg.categoryEnabled == null) {
						cfg.categoryEnabled = new HashMap<>();
					}
					if (cfg.upcomingPerCategory == null) {
						cfg.upcomingPerCategory = new HashMap<>();
					}
					if (cfg.upcomingPerEvent == null) {
						cfg.upcomingPerEvent = new HashMap<>();
					}
					if (cfg.hudGroupX == null) {
						cfg.hudGroupX = new HashMap<>();
					}
					if (cfg.hudGroupY == null) {
						cfg.hudGroupY = new HashMap<>();
					}
					if (cfg.hudGroupScale == null) {
						cfg.hudGroupScale = new HashMap<>();
					}
					if (cfg.globalFontName == null) {
						cfg.globalFontName = "";
					}
					return cfg;
				}
			}
		} catch (Exception e) {
			System.err.println("[Midgard] Konnte Config nicht laden: " + e.getMessage());
		}
		ModConfig fresh = new ModConfig();
		fresh.save();
		return fresh;
	}

	public void save() {
		try {
			Files.createDirectories(PATH.getParent());
			Files.writeString(PATH, GSON.toJson(this));
		} catch (IOException e) {
			System.err.println("[Midgard] Konnte Config nicht speichern: " + e.getMessage());
		}
	}
}
