package com.midgard.render;

import net.minecraft.client.gui.DrawContext;

/**
 * Eigenes Text-Rendering in Geräte-Auflösung (Modern-UI-Ansatz) über
 * {@link GlyphAtlas}: hoch gebackener Atlas + bilineare Filterung +
 * Geräte-Pixel-Einrastung. Lädt faul beim ersten Gebrauch (Render-Thread).
 * Schlägt die Init fehl, melden die Methoden das (false/-1) und der Aufrufer
 * fällt auf die MC-Schrift zurück.
 */
public final class MidgardText {

	private static GlyphAtlas regular;
	private static GlyphAtlas bold;
	private static boolean initialized = false;

	private MidgardText() {
	}

	private static void ensure() {
		if (initialized) {
			return;
		}
		initialized = true;
		try {
			regular = new GlyphAtlas("roboto_regular", "regular");
			bold = new GlyphAtlas("roboto_bold", "bold");
		} catch (Throwable t) {
			System.err.println("[Midgard] MidgardText Init-Fehler: " + t);
		}
	}

	private static GlyphAtlas atlas(boolean boldFont) {
		ensure();
		GlyphAtlas a = boldFont ? bold : regular;
		return (a != null && a.isReady()) ? a : null;
	}

	public static boolean draw(DrawContext ctx, String text, int x, int yTop, float sizePx, int color, boolean boldFont) {
		GlyphAtlas a = atlas(boldFont);
		if (a == null) {
			return false;
		}
		a.draw(ctx, text, x, yTop, sizePx, color);
		return true;
	}

	public static int width(String text, float sizePx, boolean boldFont) {
		GlyphAtlas a = atlas(boldFont);
		if (a == null) {
			return -1;
		}
		return Math.round(a.width(text, sizePx));
	}

	/** Höhe eines Großbuchstabens (GUI-px) für vertikale Zentrierung, sonst -1. */
	public static int capHeight(float sizePx, boolean boldFont) {
		GlyphAtlas a = atlas(boldFont);
		if (a == null) {
			return -1;
		}
		return Math.round(a.capHeight(sizePx));
	}
}
