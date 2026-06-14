package com.midgard.mining;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.midgard.events.skyblock.ScoreboardReader;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;

/**
 * Liest den Status der fünf Crystal-Nucleus-Kristalle (Jade, Amber, Amethyst,
 * Sapphire, Topaz) aus der Crystal-Hollows-Tab-Liste – best effort, mit
 * Diagnose-Log, weil das genaue Tab-Format am echten Spiel verifiziert werden
 * muss. PLACED = bereits im Nucleus abgegeben; FOUND = gefunden, noch nicht
 * abgegeben; MISSING = noch nicht gefunden.
 */
public final class CrystalData {

	public static final CrystalData INSTANCE = new CrystalData();

	public enum State {
		MISSING, FOUND, PLACED, UNKNOWN
	}

	public record Crystal(String name, State state) {
	}

	/** Reihenfolge = Anzeige; Wert = Stichwort für die Tab-Suche. */
	private static final String[] NAMES = { "Jade", "Amber", "Amethyst", "Sapphire", "Topaz" };

	private volatile List<Crystal> crystals = List.of();
	private long lastDiagMs = 0;

	private CrystalData() {
	}

	public List<Crystal> crystals() {
		return crystals;
	}

	public int placedCount() {
		int c = 0;
		for (Crystal x : crystals) {
			if (x.state() == State.PLACED) {
				c++;
			}
		}
		return c;
	}

	public void update(MinecraftClient mc) {
		if (mc == null || mc.getNetworkHandler() == null || !MiningData.INSTANCE.onCrystalHollows) {
			crystals = List.of();
			return;
		}
		List<String> tab = new ArrayList<>();
		for (PlayerListEntry e : mc.getNetworkHandler().getListedPlayerListEntries()) {
			if (e.getDisplayName() != null) {
				tab.add(ScoreboardReader.stripFormatting(e.getDisplayName().getString()));
			}
		}

		List<Crystal> out = new ArrayList<>();
		StringBuilder diag = new StringBuilder();
		for (String name : NAMES) {
			String low = name.toLowerCase(Locale.ROOT);
			State st = State.UNKNOWN;
			for (String t : tab) {
				if (t == null) {
					continue;
				}
				if (t.toLowerCase(Locale.ROOT).contains(low)) {
					diag.append(" | ").append(t.trim());
					st = parseState(t);
					break;
				}
			}
			out.add(new Crystal(name, st));
		}
		crystals = out;

		long now = System.currentTimeMillis();
		if (now - lastDiagMs > 20_000) {
			lastDiagMs = now;
			System.out.println("[Midgard] CH-Crystals (Tab):" + (diag.length() == 0 ? " (nichts gefunden)" : diag));
		}
	}

	/** Status aus dem Tab-Text ableiten (Symbole/Stichwörter, best effort). */
	private static State parseState(String line) {
		String l = line.toLowerCase(Locale.ROOT);
		if (line.contains("✔") || line.contains("✓") || l.contains("placed") || l.contains("abgegeben")) {
			return State.PLACED;
		}
		if (line.contains("✖") || line.contains("✗") || l.contains("not found") || l.contains("missing")) {
			return State.MISSING;
		}
		if (l.contains("found") || l.contains("gefunden")) {
			return State.FOUND;
		}
		return State.UNKNOWN;
	}
}
