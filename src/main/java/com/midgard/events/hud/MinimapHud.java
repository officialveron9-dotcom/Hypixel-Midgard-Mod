package com.midgard.events.hud;

import java.util.Map;

import com.midgard.Midgard;
import com.midgard.events.config.ModConfig;
import com.midgard.mining.CrystalNav;
import com.midgard.mining.MiningData;
import com.midgard.render.MidgardText;
import com.midgard.render.UIRenderer;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * Kleine Mini-Karte für Crystal Hollows (oben rechts). CH ist prozedural, daher
 * liegen nur die MITTE (Crystal Nucleus, ~512/512) und der Jungle/Amethyst-
 * Quadrant (X/Z 201–512) fest – die anderen Quadranten wechseln pro Lobby.
 * Gezeigt werden also: Karten-Umriss + Quadranten, der feste Jungle-Bereich
 * (lila), der Nucleus, der Spieler mit Blickrichtung und alle bereits GELERNTEN
 * Punkte (z. B. King Yolkar) in ihrer Farbe – damit man die Richtung kennt.
 */
public final class MinimapHud {

	public static final MinimapHud INSTANCE = new MinimapHud();

	private static final double MIN = 201, RANGE = 622; // CH-Grenzen ~201..823
	private static final int PANEL = 0xE6121218;
	private static final int BORDER = 0x55FFFFFF;
	private static final int GRID = 0x22FFFFFF;
	private static final int JUNGLE = 0x33B050FF; // Amethyst-Quadrant (fest)
	private static final int NUCLEUS = 0xFFD06BFF;
	private static final int AMBER = 0xFFF2A93B;
	private static final int DIM = 0xFF8C8C97;

	private MinimapHud() {
	}

	public void render(DrawContext c) {
		ModConfig cfg = Midgard.config;
		if (cfg == null || !cfg.masterEnabled || !cfg.chMinimap || !MiningData.INSTANCE.onCrystalHollows) {
			return;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null) {
			return;
		}
		int size = 92;
		int sw = mc.getWindow().getScaledWidth();
		int x = sw - size - 6;
		int y = 6;

		UIRenderer.fillRoundedRect(c, x - 1, y - 1, size + 2, size + 2, 4, BORDER);
		UIRenderer.fillRoundedRect(c, x, y, size, size, 3, PANEL);

		// Fester Jungle/Amethyst-Quadrant (NW: kleines X, kleines Z = oben links).
		c.fill(x, y, x + size / 2, y + size / 2, JUNGLE);
		// Quadranten-Linien + Mittelkreuz.
		c.fill(x + size / 2, y, x + size / 2 + 1, y + size, GRID);
		c.fill(x, y + size / 2, x + size, y + size / 2 + 1, GRID);

		// Norden oben (-Z), kleine Beschriftung.
		txt(c, "N", x + size / 2 - 2, y + 1, DIM, mc);

		// Gelernte Punkte (inkl. Nucleus) als farbige Punkte.
		for (Map.Entry<String, int[]> e : CrystalNav.learnedView().entrySet()) {
			int[] p = e.getValue();
			int px = mapX(x, size, p[0]);
			int py = mapZ(y, size, p[2]);
			int col = colorFor(e.getKey());
			c.fill(px - 2, py - 2, px + 3, py + 3, 0xFF000000);
			c.fill(px - 1, py - 1, px + 2, py + 2, col);
		}

		// Spieler + Blickrichtung (weißer Pfeil).
		int psx = mapX(x, size, mc.player.getX());
		int psy = mapZ(y, size, mc.player.getZ());
		double rad = Math.toRadians(mc.player.getYaw());
		double dirx = -Math.sin(rad), dirz = Math.cos(rad); // Welt-Blickrichtung
		int tipx = psx + (int) Math.round(dirx * 7);
		int tipy = psy + (int) Math.round(dirz * 7);
		line(c, psx, psy, tipx, tipy, 0xFFFFFFFF);
		c.fill(psx - 2, psy - 2, psx + 3, psy + 3, 0xFF000000);
		c.fill(psx - 1, psy - 1, psx + 2, psy + 2, 0xFFFFFFFF);
	}

	private static int mapX(int x, int size, double wx) {
		double t = (wx - MIN) / RANGE;
		t = t < 0 ? 0 : t > 1 ? 1 : t;
		return x + (int) Math.round(t * size);
	}

	private static int mapZ(int y, int size, double wz) {
		double t = (wz - MIN) / RANGE;
		t = t < 0 ? 0 : t > 1 ? 1 : t;
		return y + (int) Math.round(t * size);
	}

	private static int colorFor(String name) {
		if (name.equals("Crystal Nucleus")) {
			return NUCLEUS;
		}
		if (name.equals("King Yolkar") || name.equals("Goblin Guard")) {
			return AMBER;
		}
		return 0xFF5BE36B;
	}

	/** Dünne Linie (Bresenham-artig) für den Richtungspfeil. */
	private static void line(DrawContext c, int x1, int y1, int x2, int y2, int color) {
		int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
		if (steps <= 0) {
			return;
		}
		for (int i = 0; i <= steps; i++) {
			int px = x1 + (x2 - x1) * i / steps;
			int py = y1 + (y2 - y1) * i / steps;
			c.fill(px, py, px + 1, py + 1, color);
		}
	}

	private void txt(DrawContext c, String s, int x, int yTop, int color, MinecraftClient mc) {
		if (!MidgardText.draw(c, s, x, yTop, 8f, color, true)) {
			c.drawText(mc.textRenderer, s, x, yTop, color, false);
		}
	}
}
