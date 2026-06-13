package com.midgard.util;

import java.util.List;

import com.midgard.render.MidgardText;

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
				diamond(context, s[0], s[1], m.color());
				double dist = Math.sqrt(sq(m.x() + 0.5 - p.eye.x) + sq(m.y() - p.eye.y) + sq(m.z() + 0.5 - p.eye.z));
				String label = m.label() + " " + Math.round(dist) + "m";
				int tw = textW(label);
				int lx = Math.max(2, Math.min(p.w - tw - 2, s[0] - tw / 2));
				int ly = s[1] - capH() - 5;
				context.fill(lx - 2, ly - 2, lx + tw + 2, ly + capH() + 2, 0x90000000);
				text(context, label, lx, ly, m.color());
			} catch (Throwable ignored) {
				// einzelner Marker darf nie alles abreißen
			}
		}
	}

	/**
	 * Pfad-Linie: setzt vom Punkt {@code from} bis zum Ziel {@code target} alle
	 * paar Blöcke einen Punkt (über die Bildschirm-Projektion verbunden). Erste
	 * Version eines „Wegweisers" – noch KEIN Hindernis-Pathfinding (das kommt
	 * für die Dungeons), sondern die direkte Linie über den Boden.
	 */
	public static void renderPath(DrawContext context, Vec3d from, Marker target, int color) {
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
		int steps = Math.max(2, Math.min(60, (int) (dist / 3)));
		try {
			for (int i = 1; i <= steps; i++) {
				double t = i / (double) steps;
				int[] s = p.project(from.x + (tx - from.x) * t, from.y + (ty - from.y) * t,
						from.z + (tz - from.z) * t);
				if (s == null) {
					continue;
				}
				int r = i == steps ? 3 : 2; // Ziel-Ende etwas größer
				context.fill(s[0] - r, s[1] - r, s[0] + r, s[1] + r, color);
			}
		} catch (Throwable ignored) {
		}
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
