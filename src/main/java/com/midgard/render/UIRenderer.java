package com.midgard.render;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/**
 * Öffentliche Zeichen-API des eigenen Render-Backends (Brief Kap. 5). Zeichnet
 * abgerundete Rechtecke mit Midgards eigenem SDF-Shader, eingereicht über
 * Minecrafts {@code DrawContext} (damit es sichtbar ist). Glatt & skalierbar.
 */
public final class UIRenderer {

	/** Volltonweiße Textur als Sampler-Quelle (Form kommt aus dem Shader). */
	private static final Identifier WHITE = Identifier.of("midgard", "textures/white.png");

	private UIRenderer() {
	}

	/** Abgerundetes, gefülltes Rechteck (Farbe ARGB). */
	public static void fillRoundedRect(DrawContext ctx, int x, int y, int w, int h, int radius, int color) {
		if (w <= 0 || h <= 0) {
			return;
		}
		ctx.drawTexture(MidgardPipelines.round(radius), WHITE,
				x, y, 0f, 0f, w, h, w, h, color);
	}

	/** Abgerundetes Rechteck mit Rahmen (Rahmen = größere Fläche hinter der Füllung). */
	public static void roundedRect(DrawContext ctx, int x, int y, int w, int h, int radius,
			int fillColor, int borderWidth, int borderColor) {
		if (borderWidth > 0) {
			fillRoundedRect(ctx, x, y, w, h, radius, borderColor);
			int b = borderWidth;
			fillRoundedRect(ctx, x + b, y + b, w - 2 * b, h - 2 * b, Math.max(0, radius - b), fillColor);
		} else {
			fillRoundedRect(ctx, x, y, w, h, radius, fillColor);
		}
	}
}
