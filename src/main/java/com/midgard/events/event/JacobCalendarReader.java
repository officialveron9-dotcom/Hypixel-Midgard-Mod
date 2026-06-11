package com.midgard.events.event;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.midgard.events.skyblock.SkyblockHook;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

/**
 * Liest Jacob's-Farming-Contests direkt aus dem SkyBlock-Spiel: sobald der
 * Spieler das Menü "Jacob's Farming Contests" (oder den Kalender) öffnet, wird
 * das "Upcoming Contests"-Item ausgelesen. Dessen Tooltip listet pro Contest
 * ein SkyBlock-Datum (z. B. "Winter 16th") und darunter die drei Crops:
 *
 * <pre>
 *   Schedule
 *   Winter 16th
 *   ○ Nether Wart
 *   ○ Sugar Cane
 *   ☘ Cactus
 *   Winter 19th
 *   ...
 * </pre>
 *
 * Datum + Crops landen in {@link JacobSchedule}; die reale Restzeit berechnet
 * der {@link com.midgard.events.skyblock.SkyblockCalendar}. Nur lesend – kein
 * Netz, keine API, keine Erlaubnis nötig, skaliert auf beliebig viele Nutzer.
 */
public class JacobCalendarReader {

	public static final JacobCalendarReader INSTANCE = new JacobCalendarReader();

	/** Loggt den GUI-Inhalt einmal pro Öffnen (zum Abgleich des Formats). */
	public static boolean DEBUG = false;

	private static final Pattern DAY = Pattern.compile("(\\d{1,2})(st|nd|rd|th)");

	private int lastScreenHash = 0;

	private JacobCalendarReader() {
	}

	public void tick(MinecraftClient mc) {
		Screen s = mc.currentScreen;
		if (!(s instanceof HandledScreen<?> hs)) {
			lastScreenHash = 0;
			return;
		}
		String title = s.getTitle() == null ? "" : s.getTitle().getString();
		String tl = title.toLowerCase();
		boolean relevant = tl.contains("calendar") || tl.contains("kalender")
				|| tl.contains("jacob") || tl.contains("contest");
		if (!relevant) {
			return;
		}

		boolean newScreen = s.hashCode() != lastScreenHash;
		boolean dump = DEBUG && newScreen;
		if (newScreen) {
			lastScreenHash = s.hashCode();
		}

		int found = 0;
		int idx = -1;
		for (Slot slot : hs.getScreenHandler().slots) {
			idx++;
			ItemStack st = slot.getStack();
			if (st == null || st.isEmpty()) {
				continue;
			}
			List<String> lore = lore(st);
			if (dump) {
				String name = st.getName() == null ? "" : st.getName().getString();
				if (!name.isBlank()) {
					System.out.println("[Midgard] GUI-Slot " + idx + " name='" + name + "' lore=" + lore);
				}
			}
			found += parseSchedule(lore);
		}
		if (found > 0) {
			JacobSchedule.INSTANCE.saveIfDirty();
		}
		if (dump) {
			System.out.println("[Midgard] GUI-Dump '" + title + "': " + found + " Contest(s) erkannt");
		}
	}

	private List<String> lore(ItemStack st) {
		List<String> out = new ArrayList<>();
		LoreComponent lc = st.get(DataComponentTypes.LORE);
		if (lc != null) {
			for (Text t : lc.lines()) {
				out.add(t.getString());
			}
		}
		return out;
	}

	/** Findet "<Monat> <Tag>th"-Zeilen und liest die darunter folgenden Crops. */
	private int parseSchedule(List<String> lore) {
		int added = 0;
		for (int i = 0; i < lore.size(); i++) {
			int month = SkyblockHook.matchMonth(lore.get(i));
			int day = parseDay(lore.get(i));
			if (month <= 0 || day <= 0) {
				continue;
			}
			List<String> crops = new ArrayList<>();
			for (int j = i + 1; j < lore.size() && crops.size() < 3; j++) {
				String c = cleanCrop(lore.get(j));
				if (c == null) {
					break; // Leerzeile / Nicht-Crop beendet den Block
				}
				crops.add(c);
			}
			if (!crops.isEmpty()) {
				JacobSchedule.INSTANCE.put(month, day, crops);
				added++;
			}
		}
		return added;
	}

	private int parseDay(String line) {
		Matcher m = DAY.matcher(line);
		return m.find() ? Integer.parseInt(m.group(1)) : 0;
	}

	/** Entfernt führende Symbole (○ ☘ …)/Leerzeichen; gibt den Crop-Namen oder null zurück. */
	private String cleanCrop(String line) {
		String t = line.trim();
		int k = 0;
		while (k < t.length() && !Character.isLetter(t.charAt(k))) {
			k++;
		}
		t = t.substring(k).trim();
		if (t.isEmpty() || t.length() > 24) {
			return null; // Leerzeile oder Beschreibungstext → Block-Ende
		}
		return t;
	}
}
