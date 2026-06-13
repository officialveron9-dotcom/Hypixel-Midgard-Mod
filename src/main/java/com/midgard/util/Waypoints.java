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

	public static void render(DrawContext context, List<Marker> markers) {
		if (markers.isEmpty()) {
			return;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		Camera cam = mc.gameRenderer == null ? null : mc.gameRenderer.getCamera();
		if (cam == null) {
			return;
		}
		Vec3d eye = cam.getCameraPos();
		double yaw = Math.toRadians(cam.getYaw());
		double pitch = Math.toRadians(cam.getPitch());

		// Kamera-Basis (rechtshändig): forward, right = forward x worldUp, up = right x forward.
		double cy = Math.cos(yaw);
		double syw = Math.sin(yaw);
		double cp = Math.cos(pitch);
		double sp = Math.sin(pitch);
		double[] fwd = { -syw * cp, -sp, cy * cp };
		double[] right = norm(cross(fwd, new double[] { 0, 1, 0 }));
		double[] up = cross(right, fwd);

		int w = context.getScaledWindowWidth();
		int h = context.getScaledWindowHeight();
		double fovDeg = mc.options.getFov().getValue();
		if (fovDeg < 30) {
			fovDeg = 70;
		}
		double focal = (h / 2.0) / Math.tan(Math.toRadians(fovDeg) / 2.0);

		for (Marker m : markers) {
			try {
				// Auf Augenhöhe des Ziels projizieren, damit der Text gut sichtbar ist.
				int[] s = project(m.x() + 0.5, m.y() + 1.2, m.z() + 0.5, eye, fwd, right, up, focal, w, h);
				if (s == null || s[0] < -2000 || s[0] > w + 2000 || s[1] < -2000 || s[1] > h + 2000) {
					continue; // hinter der Kamera oder unrealistisch weit weg
				}
				diamond(context, s[0], s[1], m.color());

				double dist = Math.sqrt(sq(m.x() + 0.5 - eye.x) + sq(m.y() - eye.y) + sq(m.z() + 0.5 - eye.z));
				String label = m.label() + " " + Math.round(dist) + "m";
				int tw = textW(label);
				int lx = Math.max(2, Math.min(w - tw - 2, s[0] - tw / 2));
				int ly = s[1] - capH() - 5;
				context.fill(lx - 2, ly - 2, lx + tw + 2, ly + capH() + 2, 0x90000000);
				text(context, label, lx, ly, m.color());
			} catch (Throwable ignored) {
				// einzelner Marker darf nie alles abreißen
			}
		}
	}

	/** Welt -> Bildschirm. {x, y} oder null (hinter der Kamera). */
	private static int[] project(double wx, double wy, double wz, Vec3d eye,
			double[] fwd, double[] right, double[] up, double focal, int w, int h) {
		double dx = wx - eye.x;
		double dy = wy - eye.y;
		double dz = wz - eye.z;
		double depth = dx * fwd[0] + dy * fwd[1] + dz * fwd[2];
		if (depth <= 0.1) {
			return null;
		}
		double rc = dx * right[0] + dy * right[1] + dz * right[2];
		double uc = dx * up[0] + dy * up[1] + dz * up[2];
		int sx = (int) Math.round(w / 2.0 + (rc / depth) * focal);
		int sy = (int) Math.round(h / 2.0 - (uc / depth) * focal);
		return new int[] { sx, sy };
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
