package com.midgard.events.hud;

import java.util.Map;

import com.midgard.Midgard;
import com.midgard.events.config.ModConfig;
import com.midgard.events.skyblock.ScoreboardReader;
import com.midgard.mining.CrystalMap;
import com.midgard.mining.CrystalNav;
import com.midgard.mining.MiningData;
import com.midgard.mining.MiningHud;
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
	private static final int NUCLEUS = 0xFFD06BFF;
	private static final int DIM = 0xFF8C8C97;
	private static final int SIZE = 96;

	// Gebietsfarben wie auf der echten CH-Karte (Füllung halbtransparent).
	private static final int F_JUNGLE = 0x663FD466;   // West  – grün
	private static final int F_MITHRIL = 0x664F9BFF;  // Nord  – blau
	private static final int F_GOBLIN = 0x66A86B33;   // Süd   – braun
	private static final int F_PRECURSOR = 0x66999CA6; // Ost  – grau
	// Punkt-Farben = Gem-Farbe des jeweiligen Kristalls (wie die Navi-Icons).
	private static final int C_AMBER = 0xFFF2A93B;
	private static final int C_JADE = 0xFF3FD466;
	private static final int C_AMETHYST = 0xFFB05CFF;
	private static final int C_SAPPHIRE = 0xFF4F9BFF;

	private MinimapHud() {
	}

	public void render(DrawContext c) {
		ModConfig cfg = Midgard.config;
		if (cfg == null || !cfg.masterEnabled || !cfg.isElementEnabled(MiningHud.KEY_MINIMAP)
				|| !MiningData.INSTANCE.onCrystalHollows) {
			return;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null) {
			return;
		}
		int sw = mc.getWindow().getScaledWidth();
		int sh = mc.getWindow().getScaledHeight();
		// Position: im Editor verschiebbar (Gruppen-Position), sonst oben rechts.
		int x = cfg.hasGroupPos(MiningHud.KEY_MINIMAP) ? cfg.groupX(MiningHud.KEY_MINIMAP) : sw - SIZE - 6;
		int y = cfg.hasGroupPos(MiningHud.KEY_MINIMAP) ? cfg.groupY(MiningHud.KEY_MINIMAP) : 6;
		x = Math.max(2, Math.min(x, sw - SIZE - 2));
		y = Math.max(2, Math.min(y, sh - SIZE - 16));

		UIRenderer.fillRoundedRect(c, x - 1, y - 1, SIZE + 2, SIZE + 2, 4, BORDER);
		UIRenderer.fillRoundedRect(c, x, y, SIZE, SIZE, 3, PANEL);

		MapState ms = CrystalMap.state();
		if (ms != null && ms.colors != null && ms.colors.length >= 128 * 128) {
			renderFromMap(c, ms, x, y, mc);
		} else {
			renderFallback(c, x, y, mc);
		}
		// Aktuelles Biom als eigenes Feld UNTER der Karte (nicht in der Karte).
		String area = ScoreboardReader.currentArea(mc);
		if (area != null && !area.isEmpty()) {
			int ly = y + SIZE + 3;
			UIRenderer.fillRoundedRect(c, x - 1, ly - 1, SIZE + 2, 14, 4, BORDER);
			UIRenderer.fillRoundedRect(c, x, ly, SIZE, 12, 3, PANEL);
			txt(c, area, x + (SIZE - txtW(area, mc)) / 2, ly + 2, 0xFFFFFFFF, mc);
		}
	}

	private int txtW(String s, MinecraftClient mc) {
		int w = MidgardText.width(s, 8f, true);
		return w >= 0 ? w : mc.textRenderer.getWidth(s);
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
		return npcColor(name);
	}

	/** Punktfarbe = Gem-Farbe des Kristalls, zu dem der NPC/Ort gehört. */
	private static int npcColor(String name) {
		String n = name.toLowerCase();
		if (n.contains("nucleus")) {
			return NUCLEUS;
		}
		if (n.contains("yolkar") || n.contains("goblin")) {
			return C_AMBER;
		}
		if (n.contains("kalhuiki") || n.contains("guardian") || n.contains("jungle")) {
			return C_JADE;
		}
		if (n.contains("professor") || n.contains("precursor") || n.contains("automaton")) {
			return C_AMETHYST;
		}
		if (n.contains("keeper") || n.contains("mithril")) {
			return C_SAPPHIRE;
		}
		return 0xFFFFE070;
	}

	// ---- Ersatz OHNE Karte ------------------------------------------------

	private void renderFallback(DrawContext c, int x, int y, MinecraftClient mc) {
		int half = SIZE / 2;
		// CH-Gebiete liegen als Kreuz um die Mitte (512). Diagonal-Split (X-Form):
		// Nord = Mithril (blau), Süd = Goblin (braun), West = Jungle (grün),
		// Ost = Precursor (grau) – wie auf der echten Karte. Sofort sichtbar.
		for (int j = 0; j < SIZE; j += 2) {
			int dz = j + 1 - half;
			for (int i = 0; i < SIZE; i += 2) {
				int dx = i + 1 - half;
				int col;
				if (Math.abs(dx) >= Math.abs(dz)) {
					col = dx < 0 ? F_JUNGLE : F_PRECURSOR; // West / Ost
				} else {
					col = dz < 0 ? F_MITHRIL : F_GOBLIN;   // Nord / Süd
				}
				c.fill(x + i, y + j, x + i + 2, y + j + 2, col);
			}
		}
		// Diagonalen dezent andeuten + Norden markieren.
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
		return npcColor(name);
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
