package com.midgard.events.event;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Lokaler Jacob's-Farming-Contest-Zeitplan. Wird ausschließlich aus dem
 * IN-GAME-Menü "Jacob's Farming Contests" → "Upcoming Contests" gefüllt
 * ({@link JacobCalendarReader}) – keine externe API. Jeder Contest ist über
 * sein SkyBlock-Datum (Monat/Tag) + die drei Crops definiert; die reale
 * Restzeit wird live über {@link com.midgard.events.skyblock.SkyblockCalendar}
 * berechnet. Persistiert nach {@code config/midgard_jacob.json}, damit die
 * Daten Neustarts überdauern. Skaliert auf beliebig viele Nutzer.
 */
public class JacobSchedule {

	public static final JacobSchedule INSTANCE = new JacobSchedule();

	public record Entry(int month, int day, List<String> crops) {
	}

	private final Path file = FabricLoader.getInstance().getConfigDir().resolve("midgard_jacob.json");
	/** key = month*100 + day  ->  Crops */
	private final TreeMap<Integer, List<String>> byDate = new TreeMap<>();
	private boolean dirty = false;

	private JacobSchedule() {
		load();
	}

	public synchronized void put(int month, int day, List<String> crops) {
		if (month < 1 || month > 12 || day < 1 || day > 31 || crops == null || crops.isEmpty()) {
			return;
		}
		int key = month * 100 + day;
		List<String> existing = byDate.get(key);
		List<String> fresh = new ArrayList<>(crops);
		if (!fresh.equals(existing)) {
			byDate.put(key, fresh);
			dirty = true;
		}
	}

	public synchronized List<Entry> entries() {
		List<Entry> out = new ArrayList<>();
		for (Map.Entry<Integer, List<String>> e : byDate.entrySet()) {
			out.add(new Entry(e.getKey() / 100, e.getKey() % 100, e.getValue()));
		}
		return out;
	}

	public synchronized boolean hasData() {
		return !byDate.isEmpty();
	}

	public synchronized void saveIfDirty() {
		if (!dirty) {
			return;
		}
		dirty = false;
		try {
			JsonObject root = new JsonObject();
			for (Map.Entry<Integer, List<String>> e : byDate.entrySet()) {
				JsonArray arr = new JsonArray();
				for (String c : e.getValue()) {
					arr.add(c);
				}
				root.add(Integer.toString(e.getKey()), arr);
			}
			Files.writeString(file, root.toString());
		} catch (Exception ex) {
			System.err.println("[Midgard] Jacob-Schedule speichern fehlgeschlagen: " + ex);
		}
	}

	private void load() {
		try {
			if (!Files.exists(file)) {
				return;
			}
			JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
			for (Map.Entry<String, JsonElement> e : root.entrySet()) {
				int key = Integer.parseInt(e.getKey());
				List<String> crops = new ArrayList<>();
				for (JsonElement c : e.getValue().getAsJsonArray()) {
					crops.add(c.getAsString());
				}
				if (!crops.isEmpty()) {
					byDate.put(key, crops);
				}
			}
		} catch (Exception ex) {
			System.err.println("[Midgard] Jacob-Schedule laden fehlgeschlagen: " + ex);
		}
	}
}
