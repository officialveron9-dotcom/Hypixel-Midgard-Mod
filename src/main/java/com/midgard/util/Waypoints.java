package com.midgard.util;

import java.util.List;

import com.midgard.render.MidgardText;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;

/**
 * Bildschirm-Wegpunkte: Ein Welt-Punkt wird mit Kamera-Position, Blickrichtung
 * (Yaw/Pitch) und FOV per Standard-Projektion auf den Bildschirm gerechnet und
 * im HUD als Marker + Text + Entfernung gezeichnet. Bewusst rein rechnerisch
 * gelöst (kein RenderLayer/Projektionsmatrix) – robust und versionsunabhängig.
 */
public final class Waypoints {

	/** Ein Wegpunkt in Weltkoordinaten. */
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

		// Kamera-Basis (Standard-MC-Konvention).
		double cy = Math.cos(yaw);
		double sy = Math.sin(yaw);
		double cp = Math.cos(pitch);
		double sp = Math.sin(pitch);
		double[] fwd = { -sy * cp, -sp, cy * cp };
		double[] right = { cy, 0, sy };
		double[] up = cross(right, fwd);

		int w = context.getScaledWindowWidth();
		int h = context.getScaledWindowHeight();
		double fovDeg = mc.options.getFov().getValue();
		if (fovDeg < 30) {
			fovDeg = 70;
		}
		double focal = (h / 2.0) / Math.tan(Math.toRadians(fovDeg) / 2.0);

		for (Marker m : markers) {
			double dx = (m.x() + 0.5) - eye.x;
			double dy = (m.y() + 0.5) - eye.y;
			double dz = (m.z() + 0.5) - eye.z;
			double depth = dx * fwd[0] + dy * fwd[1] + dz * fwd[2];
			if (depth <= 0.1) {
				continue; // hinter der Kamera
			}
			double rc = dx * right[0] + dy * right[1] + dz * right[2];
			double uc = dx * up[0] + dy * up[1] + dz * up[2];
			int sx = (int) Math.round(w / 2.0 + (rc / depth) * focal);
			int syp = (int) Math.round(h / 2.0 - (uc / depth) * focal);
			sx = Math.max(6, Math.min(w - 6, sx));
			syp = Math.max(6, Math.min(h - 18, syp));

			int dist = (int) Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz));
			String label = m.label() + " (" + dist + "m)";
			int tw = textW(label);
			int tx = Math.max(2, Math.min(w - tw - 2, sx - tw / 2));

			diamond(context, sx, syp, m.color());
			context.fill(tx - 2, syp + 6, tx + tw + 2, syp + 6 + capH() + 4, 0x90000000);
			text(context, label, tx, syp + 8, m.color());
		}
	}

	private static double[] cross(double[] a, double[] b) {
		return new double[] {
				a[1] * b[2] - a[2] * b[1],
				a[2] * b[0] - a[0] * b[2],
				a[0] * b[1] - a[1] * b[0] };
	}

	private static void diamond(DrawContext c, int cx, int cy, int color) {
		for (int i = 0; i <= 3; i++) {
			c.fill(cx - (3 - i), cy - i, cx + (3 - i) + 1, cy - i + 1, color);
			c.fill(cx - (3 - i), cy + i, cx + (3 - i) + 1, cy + i + 1, color);
		}
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
