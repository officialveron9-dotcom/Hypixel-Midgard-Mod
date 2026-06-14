package com.midgard.util;

import java.util.List;

import com.midgard.render.MidgardText;
import com.midgard.render.UIRenderer;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;

/**
 * Welt-Wegpunkte: Ein Boden-Punkt wird mit Kamera-Position, Blickrichtung
 * (Yaw/Pitch) und FOV per Standard-Projektion auf den Bildschirm gerechnet und
 * als am Boden verankerter Marker mit kleiner "Beam"-Säule + Name + Entfernung
 * gezeichnet. Rein rechnerisch (kein RenderLayer/Projektionsmatrix), damit es
 * in 1.21.11 stabil bleibt; der Marker klebt an der Weltposition (kein
 * Rand-Klemmen), bleibt durch Wände sichtbar (wie SkyHanni-Wegpunkte).
 */
public final class Waypoints {

	/** Ein Wegpunkt: (x,y,z) = Boden-Block des Ziels. */
	public record Marker(double x, double y, double z, String label, int color) {
	}

	private Waypoints() {
	}

	/**
	 * Wiederverwendbarer Projektor: rechnet Weltpunkte mit der aktuellen Kamera
	 * auf den Bildschirm. Einmal pro Frame bauen, mehrfach nutzen.
	 */
	private static final class Projector {
		final Vec3d eye;
		final double[] fwd, right, up;
		final double focal;
		final int w, h;

		Projector(MinecraftClient mc, Camera cam, int w, int h) {
			this.eye = cam.getCameraPos();
			double yaw = Math.toRadians(cam.getYaw());
			double pitch = Math.toRadians(cam.getPitch());
			double cy = Math.cos(yaw), syw = Math.sin(yaw), cp = Math.cos(pitch), sp = Math.sin(pitch);
			this.fwd = new double[] { -syw * cp, -sp, cy * cp };
			this.right = norm(cross(fwd, new double[] { 0, 1, 0 }));
			this.up = cross(right, fwd);
			double fovDeg = mc.options.getFov().getValue();
			if (fovDeg < 30) {
				fovDeg = 70;
			}
			this.focal = (h / 2.0) / Math.tan(Math.toRadians(fovDeg) / 2.0);
			this.w = w;
			this.h = h;
		}

		/** {x, y} oder null (hinter der Kamera / unrealistisch weit). */
		int[] project(double wx, double wy, double wz) {
			double dx = wx - eye.x, dy = wy - eye.y, dz = wz - eye.z;
			double depth = dx * fwd[0] + dy * fwd[1] + dz * fwd[2];
			if (depth <= 0.1) {
				return null;
			}
			double rc = dx * right[0] + dy * right[1] + dz * right[2];
			double uc = dx * up[0] + dy * up[1] + dz * up[2];
			int sx = (int) Math.round(w / 2.0 + (rc / depth) * focal);
			int sy = (int) Math.round(h / 2.0 - (uc / depth) * focal);
			if (sx < -3000 || sx > w + 3000 || sy < -3000 || sy > h + 3000) {
				return null;
			}
			return new int[] { sx, sy };
		}
	}

	private static Projector projector(DrawContext context) {
		MinecraftClient mc = MinecraftClient.getInstance();
		Camera cam = mc.gameRenderer == null ? null : mc.gameRenderer.getCamera();
		if (cam == null) {
			return null;
		}
		return new Projector(mc, cam, context.getScaledWindowWidth(), context.getScaledWindowHeight());
	}

	public static void render(DrawContext context, List<Marker> markers) {
		if (markers.isEmpty()) {
			return;
		}
		Projector p = projector(context);
		if (p == null) {
			return;
		}
		for (Marker m : markers) {
			try {
				int[] s = p.project(m.x() + 0.5, m.y() + 1.2, m.z() + 0.5);
				if (s == null) {
					continue;
				}
				marker(context, s[0], s[1], m.color());
				double dist = Math.sqrt(sq(m.x() + 0.5 - p.eye.x) + sq(m.y() - p.eye.y) + sq(m.z() + 0.5 - p.eye.z));
				String label = m.label() + "  " + Math.round(dist) + "m";
				int tw = textW(label);
				int lx = Math.max(2, Math.min(p.w - tw - 2, s[0] - tw / 2));
				int ly = s[1] - capH() - 10;
				// Abgerundeter dunkler Hintergrund hinter dem Text.
				UIRenderer.fillRoundedRect(context, lx - 4, ly - 3, tw + 8, capH() + 6, 3, 0xC0000010);
				text(context, label, lx, ly, m.color());
			} catch (Throwable ignored) {
				// einzelner Marker darf nie alles abreißen
			}
		}
	}

	/** Ziel-Marker: dunkel umrandeter Diamant mit kleinem Glanz – gut sichtbar. */
	private static void marker(DrawContext c, int cx, int cy, int color) {
		// dunkler Rand (eine Stufe größer)
		for (int i = 0; i <= 5; i++) {
			c.fill(cx - (5 - i), cy - i, cx + (5 - i) + 1, cy - i + 1, 0xC0000000);
			c.fill(cx - (5 - i), cy + i, cx + (5 - i) + 1, cy + i + 1, 0xC0000000);
		}
		// farbige Füllung
		for (int i = 0; i <= 4; i++) {
			c.fill(cx - (4 - i), cy - i, cx + (4 - i) + 1, cy - i + 1, color);
			c.fill(cx - (4 - i), cy + i, cx + (4 - i) + 1, cy + i + 1, color);
		}
		// kleiner heller Punkt in der Mitte
		c.fill(cx - 1, cy - 1, cx + 1, cy + 1, 0xFFFFFFFF);
	}

	/**
	 * Pfad-Linie: setzt vom Punkt {@code from} bis zum Ziel {@code target} alle
	 * paar Blöcke einen Punkt (über die Bildschirm-Projektion verbunden). Erste
	 * Version eines „Wegweisers" – noch KEIN Hindernis-Pathfinding (das kommt
	 * für die Dungeons), sondern die direkte Linie über den Boden.
	 */
	public static void renderPath(DrawContext context, Vec3d from, Marker target, int color, boolean groundSnap) {
		if (from == null || target == null) {
			return;
		}
		Projector p = projector(context);
		if (p == null) {
			return;
		}
		double tx = target.x() + 0.5, ty = target.y(), tz = target.z() + 0.5;
		double dist = Math.sqrt(sq(tx - from.x) + sq(ty - from.y) + sq(tz - from.z));
		if (dist < 2 || dist > 120) {
			return; // zu nah / zu weit für eine sinnvolle Linie
		}
		net.minecraft.client.world.ClientWorld world = MinecraftClient.getInstance().world;
		// Dichte Stützpunkte (alle ~1 Block) für eine glatte, verbundene Linie.
		int steps = Math.max(4, Math.min(160, (int) (dist)));
		try {
			int[] prev = null;
			for (int i = 0; i <= steps; i++) {
				double t = i / (double) steps;
				double wx = from.x + (tx - from.x) * t;
				double wy = from.y + (ty - from.y) * t;
				double wz = from.z + (tz - from.z) * t;
				if (groundSnap && world != null) {
					wy = groundY(world, wx, wy, wz);
				}
				int[] s = p.project(wx, wy + 0.1, wz);
				if (s != null && prev != null) {
					thickLine(context, prev[0], prev[1], s[0], s[1], color);
				}
				prev = s;
			}
		} catch (Throwable ignored) {
		}
	}

	/**
	 * Zeichnet einen fertigen Pfad (Liste von Welt-Punkten, z. B. vom A*) als
	 * durchgehende Bildschirm-Linie. Sicher (kein Eingriff in Engine-Puffer).
	 */
	public static void renderPolyline(DrawContext context, java.util.List<Vec3d> pts, int color) {
		if (pts == null || pts.size() < 2) {
			return;
		}
		Projector p = projector(context);
		if (p == null) {
			return;
		}
		int dark = 0xB0000000 | (color & 0xFFFFFF); // dunkler Saum drunter
		try {
			// 1) Verbindungslinien (zwei Lagen: dunkler Saum + Farbe darüber).
			int[] prev = null;
			for (Vec3d v : pts) {
				int[] s = p.project(v.x, v.y + 0.1, v.z);
				if (s != null && prev != null) {
					softLine(context, prev[0], prev[1], s[0], s[1], dark, color);
				}
				prev = s;
			}
			// 2) Knoten-Punkte (kleine Quadrate) – Taunahi-artig.
			for (Vec3d v : pts) {
				int[] s = p.project(v.x, v.y + 0.1, v.z);
				if (s != null) {
					context.fill(s[0] - 2, s[1] - 2, s[0] + 3, s[1] + 3, dark);
					context.fill(s[0] - 1, s[1] - 1, s[0] + 2, s[1] + 2, color);
				}
			}
		} catch (Throwable ignored) {
		}
	}

	/** Linie mit dunklem Saum (4px) und farbiger Mitte (2px) für besseren Kontrast. */
	private static void softLine(DrawContext c, int x1, int y1, int x2, int y2, int dark, int color) {
		int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
		if (steps <= 0) {
			return;
		}
		for (int i = 0; i <= steps; i++) {
			int px = x1 + (x2 - x1) * i / steps;
			int py = y1 + (y2 - y1) * i / steps;
			c.fill(px - 2, py - 2, px + 2, py + 2, dark);
		}
		for (int i = 0; i <= steps; i++) {
			int px = x1 + (x2 - x1) * i / steps;
			int py = y1 + (y2 - y1) * i / steps;
			c.fill(px - 1, py - 1, px + 1, py + 1, color);
		}
	}

	/** Verbindet zwei Bildschirmpunkte mit einer durchgehenden, dicken Linie. */
	private static void thickLine(DrawContext c, int x1, int y1, int x2, int y2, int color) {
		int dx = Math.abs(x2 - x1), dy = Math.abs(y2 - y1);
		int steps = Math.max(dx, dy);
		if (steps <= 0) {
			c.fill(x1 - 1, y1 - 1, x1 + 2, y1 + 2, color);
			return;
		}
		for (int i = 0; i <= steps; i++) {
			int px = x1 + (x2 - x1) * i / steps;
			int py = y1 + (y2 - y1) * i / steps;
			c.fill(px - 1, py - 1, px + 2, py + 2, color); // 3px breit
		}
	}

	/** Sucht den Boden nahe (x,y,z): vom Startniveau nach unten bis ein fester Block. */
	private static double groundY(net.minecraft.client.world.ClientWorld world, double x, double y, double z) {
		int bx = (int) Math.floor(x);
		int bz = (int) Math.floor(z);
		for (int dy = 2; dy >= -14; dy--) {
			net.minecraft.util.math.BlockPos pos = new net.minecraft.util.math.BlockPos(bx, (int) Math.floor(y) + dy, bz);
			if (!world.getBlockState(pos).isAir()) {
				return pos.getY() + 1;
			}
		}
		return y;
	}

	private static void diamond(DrawContext c, int cx, int cy, int color) {
		for (int i = 0; i <= 3; i++) {
			c.fill(cx - (3 - i), cy - i, cx + (3 - i) + 1, cy - i + 1, color);
			c.fill(cx - (3 - i), cy + i, cx + (3 - i) + 1, cy + i + 1, color);
		}
	}

	private static double[] cross(double[] a, double[] b) {
		return new double[] {
				a[1] * b[2] - a[2] * b[1],
				a[2] * b[0] - a[0] * b[2],
				a[0] * b[1] - a[1] * b[0] };
	}

	private static double[] norm(double[] v) {
		double l = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
		return l == 0 ? v : new double[] { v[0] / l, v[1] / l, v[2] / l };
	}

	private static double sq(double v) {
		return v * v;
	}

	private static void text(DrawContext c, String s, int x, int y, int color) {
		if (!MidgardText.draw(c, s, x + 1, y + 1, 8f, 0xC0000000, true)
				| !MidgardText.draw(c, s, x, y, 8f, color, true)) {
			c.drawText(MinecraftClient.getInstance().textRenderer, s, x, y, color, true);
		}
	}

	private static int textW(String s) {
		int w = MidgardText.width(s, 8f, true);
		return w >= 0 ? w : MinecraftClient.getInstance().textRenderer.getWidth(s);
	}

	private static int capH() {
		int h = MidgardText.capHeight(8f, true);
		return h > 0 ? h : 7;
	}
}
