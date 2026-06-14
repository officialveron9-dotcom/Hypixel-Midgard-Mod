package com.midgard.auction;

import java.util.Locale;

import com.midgard.Midgard;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.AbstractSignEditScreen;

/**
 * Fängt das Hypixel-Auktionshaus-Such-SCHILD ab und ersetzt es durch das eigene
 * {@link AuctionSearchScreen}. Hypixel öffnet für die AH-Suche ein
 * Schild-Bearbeitungs-Fenster; sobald das erkannt wird, schalten wir auf unser
 * Menü um. Defensiv: erkennt nur das Such-Schild (Stichwörter), und nur wenn die
 * Funktion in der Config aktiv ist.
 */
public final class AuctionSearchHook {

	private static String lastLoggedKey = "";

	private AuctionSearchHook() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (!(screen instanceof AbstractSignEditScreen sign)) {
				return;
			}
			String[] lines = readLines(sign);
			diagLog(lines);
			if (Midgard.config == null || !Midgard.config.auctionSearch) {
				return;
			}
			if (!isAhSearch(lines)) {
				return;
			}
			ItemIndex.INSTANCE.ensureLoaded();
			// Nicht mitten in AFTER_INIT umschalten -> auf den nächsten Tick legen.
			client.send(() -> {
				if (client.currentScreen == screen) {
					client.setScreen(new AuctionSearchScreen(sign));
				}
			});
		});
	}

	/** Liest die (privaten, per AccessWidener offenen) Schild-Zeilen aus. */
	private static String[] readLines(AbstractSignEditScreen sign) {
		try {
			String[] m = sign.messages;
			return m != null ? m : new String[0];
		} catch (Throwable t) {
			return new String[0];
		}
	}

	/** Ist das das AH-Such-Schild? Defensiv über Stichwörter in den Zeilen. */
	static boolean isAhSearch(String[] lines) {
		String joined = joinStripped(lines);
		// Hypixel-Such-Schild hat eine Pfeil-Zeile (^^^) plus einen Hinweis wie
		// "Search" / "Query" / "Auction". Defensiv mehrere Varianten matchen.
		boolean arrow = joined.contains("^^^");
		boolean kw = joined.contains("search") || joined.contains("query")
				|| joined.contains("auction") || joined.contains("suche");
		return arrow && kw || kw;
	}

	private static String joinStripped(String[] lines) {
		StringBuilder sb = new StringBuilder();
		for (String l : lines) {
			if (l != null) {
				sb.append(strip(l)).append(' ');
			}
		}
		return sb.toString().toLowerCase(Locale.ROOT);
	}

	/** §-Farbcodes entfernen. */
	private static String strip(String s) {
		return s.replaceAll("(?i)\\u00a7[0-9a-fk-or]", "");
	}

	/** Diagnose: jede neue Schild-Konstellation einmal ins Log (zur Erkennung). */
	private static void diagLog(String[] lines) {
		StringBuilder sb = new StringBuilder();
		for (String l : lines) {
			sb.append('[').append(l == null ? "" : strip(l)).append(']');
		}
		String key = sb.toString();
		if (!key.equals(lastLoggedKey)) {
			lastLoggedKey = key;
			System.out.println("[Midgard] Sign-Screen erkannt, Zeilen=" + key
					+ "  -> AH-Suche? " + isAhSearch(lines));
		}
	}
}
