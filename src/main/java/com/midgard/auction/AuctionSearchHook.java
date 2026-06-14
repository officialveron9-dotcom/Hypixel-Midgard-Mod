package com.midgard.auction;

import java.util.Locale;

import com.midgard.Midgard;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.AbstractSignEditScreen;

/**
 * Fängt das Hypixel-Auktionshaus-Such-SCHILD ab und ersetzt es durch das eigene
 * {@link AuctionSearchScreen}. Wichtig: das Umschalten passiert NICHT während
 * der Screen-Initialisierung (sonst schließt es sich sofort wieder), sondern
 * sauber im nächsten Client-Tick.
 */
public final class AuctionSearchHook {

	private static String lastLoggedKey = "";
	private static volatile AbstractSignEditScreen pending;

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
			if (isAhSearch(lines)) {
				System.out.println("[Midgard] AH-Suche erkannt -> oeffne eigenes Menue im naechsten Tick.");
				pending = sign; // erst nächsten Tick umschalten
			}
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			AbstractSignEditScreen sign = pending;
			if (sign == null) {
				return;
			}
			pending = null;
			// Nur umschalten, wenn noch das Schild (oder gar nichts) offen ist –
			// nicht, wenn Hypixel inzwischen einen anderen Screen geöffnet hat.
			Screen cur = client.currentScreen;
			if (cur == sign || cur == null || cur instanceof AbstractSignEditScreen) {
				System.out.println("[Midgard] -> AuctionSearchScreen wird gesetzt (vorher: "
						+ (cur == null ? "null" : cur.getClass().getSimpleName()) + ")");
				client.setScreen(new AuctionSearchScreen(sign));
			} else {
				System.out.println("[Midgard] -> nicht umgeschaltet, aktueller Screen: "
						+ cur.getClass().getSimpleName());
			}
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
		boolean kw = joined.contains("search") || joined.contains("query")
				|| joined.contains("auction") || joined.contains("suche")
				|| joined.contains("^^^");
		return kw;
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

	static MinecraftClient mc() {
		return MinecraftClient.getInstance();
	}
}
