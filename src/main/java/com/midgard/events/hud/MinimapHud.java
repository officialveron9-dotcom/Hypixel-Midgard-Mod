package com.midgard.events.hud;

import java.util.Map;

import com.midgard.Midgard;
import com.midgard.events.config.ModConfig;
import com.midgard.events.skyblock.ScoreboardReader;
import com.midgard.mining.CrystalMap;
import com.midgard.mining.CrystalNav;
import com.midgard.mining.MiningData;
import com.midgard.render.MidgardText;
import com.midgard.render.UIRenderer;

import net.minecraft.block.MapColor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.map.MapState;

/**
 * Mini-Karte für Crystal Hollows (oben rechts). Hält der Spieler die CH-KARTE,
 * werden die ECHTEN Karten-Farben (Gebiete) gezeichnet ({@link CrystalMap}) plus
 * die Karten-Marker und der Spieler – exakt über die Karten-Mitte/Skalierung.
 * Ohne Karte gibt es eine einfache Ersatz-Ansicht (feste Mitte + Jungle-Quadrant
 * + Spieler + gelernte Punkte) mit dem Hinweis, die Karte zu halten.
 */
public final class MinimapHud {

	public static final MinimapHud INSTANCE = new MinimapHud();

	private static final double MIN = 201, RANGE = 622; // CH-Grenzen ~201..823
	private static final int PANEL = 0xE6121218;
	private static final int BORDER = 0x55FFFFFF;
	private static final int GRID = 0x22FFFFFF;
	private static final int NUCLEUS = 0xFFD06BFF;
	private static final int AMBER = 0xFFF2A93B;
	private static final int DIM = 0xFF8C8C97;
	private static final int SIZE = 96;

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
		int sw = mc.getWindow().getScaledWidth();
		int x = sw - SIZE - 6;
		int y = 6;

		UIRenderer.fillRoundedRect(c, x - 1, y - 1, SIZE + 2, SIZE + 2, 4, BORDER);
		UIRenderer.fillRoundedRect(c, x, y, SIZE, SIZE, 3, PANEL);

		MapState ms = CrystalMap.state();
		if (ms != null && ms.colors != null && ms.colors.length >= 128 * 128) {
			renderFromMap(c, ms, x, y, mc);
		} else {
			renderFallback(c, x, y, mc);
		}
		// Aktuelles Biom unten anzeigen (aus dem Scoreboard).
		String area = ScoreboardReader.currentArea(mc);
		if (area != null && !area.isEmpty()) {
			c.fill(x, y + SIZE - 12, x + SIZE, y + SIZE, 0xAA000000);
			txt(c, area, x + 4, y + SIZE - 10, 0xFFFFFFFF, mc);
		}
	}

	// ---- Variante MIT echter CH-Karte -------------------------------------

	private void renderFromMap(DrawContext c, MapState ms, int x, int y, MinecraftClient mc) {
		int sc = 1 << ms.scale;
		// Karten-Bild (128x128) in das Quadrat sampeln (2px-Schritte = schnell).
		for (int j = 0; j < SIZE; j += 2) {
			int pz = j * 128 / SIZE;
			for (int i = 0; i < SIZE; i += 2) {
				int px = i * 128 / SIZE;
				int argb = MapColor.getRenderColor(ms.colors[pz * 128 + px] & 0xFF);
				if ((argb >>> 24) == 0) {
					continue; // unerforscht/transparent
				}
				c.fill(x + i, y + j, x + i + 2, y + j + 2, 0xFF000000 | (argb & 0xFFFFFF));
			}
		}
		// Marker der Karte als Punkte (Name -> Farbe).
		for (CrystalMap.Deco d : CrystalMap.decorations()) {
			if (d.type().contains("player")) {
				continue; // eigenen Pfeil zeichnen wir selbst
			}
			int sx = x + clamp(pix(d.x(), ms.centerX, sc)) * SIZE / 128;
			int sy = y + clamp(pix(d.z(), ms.centerZ, sc)) * SIZE / 128;
			dot(c, sx, sy, decoColor(d.name()));
		}
		// Spieler + Blickrichtung über die Karten-Mitte/Skalierung exakt platzieren.
		int psx = x + clamp(pix(mc.player.getX(), ms.centerX, sc)) * SIZE / 128;
		int psy = y + clamp(pix(mc.player.getZ(), ms.centerZ, sc)) * SIZE / 128;
		drawPlayer(c, psx, psy, mc.player.getYaw());
	}

	private static int pix(double world, double center, int sc) {
		return (int) Math.round((world - center) / sc + 64);
	}

	private static int clamp(int v) {
		return v < 0 ? 0 : v > 127 ? 127 : v;
	}

	private static int decoColor(String name) {
		String n = name.toLowerCase();
		if (n.contains("yolkar") || n.contains("goblin")) {
			return AMBER;
		}
		if (n.contains("nucleus")) {
			return NUCLEUS;
		}
		return 0xFFFFE070;
	}

	// ---- Ersatz OHNE Karte ------------------------------------------------

	private void renderFallback(DrawContext c, int x, int y, MinecraftClient mc) {
		int half = SIZE / 2;
		// Feste Gemstone-Quadranten (Lage liegt geometrisch fest, X/Z um 512):
		// Jungle NW, Mithril NE, Goblin SW, Precursor SE – sofort sichtbar.
		c.fill(x, y, x + half, y + half, 0x44B050FF); // NW Jungle (Amethyst lila)
		c.fill(x + half, y, x + SIZE, y + half, 0x4435C46A); // NE Mithril (Jade grün)
		c.fill(x, y + half, x + half, y + SIZE, 0x44F2A93B); // SW Goblin (Amber orange)
		c.fill(x + half, y + half, x + SIZE, y + SIZE, 0x444F9BFF); // SE Precursor (Sapphire blau)
		c.fill(x + half, y, x + half + 1, y + SIZE, GRID);
		c.fill(x, y + half, x + SIZE, y + half + 1, GRID);
		txt(c, "N", x + half - 2, y + 1, DIM, mc);
		for (Map.Entry<String, int[]> e : CrystalNav.learnedView().entrySet()) {
			int[] p = e.getValue();
			dot(c, mapX(x, p[0]), mapZ(y, p[2]), colorFor(e.getKey()));
		}
		drawPlayer(c, mapX(x, mc.player.getX()), mapZ(y, mc.player.getZ()), mc.player.getYaw());
	}

	private static int mapX(int x, double wx) {
		double t = (wx - MIN) / RANGE;
		t = t < 0 ? 0 : t > 1 ? 1 : t;
		return x + (int) Math.round(t * SIZE);
	}

	private static int mapZ(int y, double wz) {
		double t = (wz - MIN) / RANGE;
		t = t < 0 ? 0 : t > 1 ? 1 : t;
		return y + (int) Math.round(t * SIZE);
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

	// ---- gemeinsame Helfer ------------------------------------------------

	private static void dot(DrawContext c, int px, int py, int col) {
		c.fill(px - 2, py - 2, px + 3, py + 3, 0xFF000000);
		c.fill(px - 1, py - 1, px + 2, py + 2, col);
	}

	private void drawPlayer(DrawContext c, int psx, int psy, float yaw) {
		double rad = Math.toRadians(yaw);
		double dirx = -Math.sin(rad), dirz = Math.cos(rad);
		int tipx = psx + (int) Math.round(dirx * 7);
		int tipy = psy + (int) Math.round(dirz * 7);
		line(c, psx, psy, tipx, tipy, 0xFFFFFFFF);
		c.fill(psx - 2, psy - 2, psx + 3, psy + 3, 0xFF000000);
		c.fill(psx - 1, psy - 1, psx + 2, psy + 2, 0xFFFFFFFF);
	}

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
