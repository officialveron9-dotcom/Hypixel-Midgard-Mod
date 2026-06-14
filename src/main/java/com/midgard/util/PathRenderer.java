package com.midgard.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.joml.Matrix4f;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

/**
 * Zeichnet den {@link PathFinder}-Pfad ECHT in der 3D-Welt. Die Knoten werden
 * vorher per Catmull-Rom-Spline zu einer weichen Kurve verdichtet (keine
 * sichtbaren Knicke). Stile (config.pathStyle):
 * <ul>
 *   <li>0 = Linie (Tiefe): tiefengetestet ({@code RenderLayers.LINES}) – von
 *       Blöcken verdeckt, geht NICHT durch Wände.</li>
 *   <li>1 = Bändchen (durch Wände): breites flaches Band, immer sichtbar.</li>
 *   <li>2 = Würfel-Spur: kleine Würfel an den Knoten, immer sichtbar.</li>
 *   <li>3 = Linie (durch Wände): dünnes Band, immer sichtbar.</li>
 *   <li>4 = Boden-Glühen: leuchtendes Band flach auf dem Boden (Standard).</li>
 * </ul>
 * Alle Stile nutzen einen EIGENEN Vertex-Puffer (nicht den geteilten der
 * Engine) – ein Fehler hier kann die Engine daher NIE zum Absturz bringen.
 */
public final class PathRenderer {

	private static final int R = 245, G = 130, B = 50; // Akzent-Orange
	private static final int MAX_SEG = 240;

	private static BufferAllocator allocator;
	private static VertexConsumerProvider.Immediate immediate;

	private PathRenderer() {
	}

	public static void render(WorldRenderContext ctx) {
		List<Vec3d> path = PathFinder.currentPath();
		if (path == null || path.size() < 1) {
			return;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null || mc.gameRenderer == null || mc.gameRenderer.getCamera() == null) {
			return;
		}
		MatrixStack ms = ctx.matrices();
		if (ms == null) {
			return;
		}
		Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();

		if (immediate == null) {
			allocator = new BufferAllocator(1 << 16);
			immediate = VertexConsumerProvider.immediate(allocator);
		}

		int style = 4;
		try {
			if (com.midgard.Midgard.config != null) {
				style = com.midgard.Midgard.config.pathStyle;
			}
		} catch (Throwable ignored) {
		}
		if (style < 0 || style > 5) {
			style = 4;
		}

		// Nur den noch offenen Rest zeichnen: ab dem horizontal nächsten Knoten.
		// Den Startpunkt auf die BODENHÖHE dieses Knotens legen – so bleibt die
		// Linie am Boden, auch wenn man springt/fliegt (geht nicht in die Luft).
		int n = path.size();
		double px = mc.player.getX(), pz = mc.player.getZ();
		int startIdx = 0;
		double bestD = Double.MAX_VALUE;
		for (int i = 0; i < n; i++) {
			double dx = path.get(i).x - px, dz = path.get(i).z - pz;
			double d = dx * dx + dz * dz;
			if (d < bestD) {
				bestD = d;
				startIdx = i;
			}
		}
		Vec3d feet = new Vec3d(px, path.get(startIdx).y, pz);

		// Rohpunkte (Füße + restliche Knoten, ggf. ausgedünnt) -> Spline.
		int step = Math.max(1, (n - startIdx) / MAX_SEG);
		List<Vec3d> raw = new ArrayList<>();
		raw.add(feet);
		for (int i = startIdx; i < n; i += step) {
			raw.add(path.get(i));
		}
		if (raw.get(raw.size() - 1) != path.get(n - 1)) {
			raw.add(path.get(n - 1));
		}

		ms.push();
		ms.translate(-cam.x, -cam.y, -cam.z);
		MatrixStack.Entry entry = ms.peek();
		Matrix4f m = entry.getPositionMatrix();

		// Gerade Segmente direkt zwischen den (bereits vereinfachten) Punkten –
		// kein Spline mehr, dadurch keine Wellen. Punkt-zu-Punkt schnurgerade.
		if (style == 2) {
			// Würfel-Spur an den Knoten.
			VertexConsumer vc = immediate.getBuffer(RenderLayers.debugQuads());
			for (int i = 1; i < raw.size(); i++) {
				box(vc, m, raw.get(i), 0.11f, 200);
			}
		} else if (style == 5) {
			// Leucht-Blöcke: leuchtender Rahmen um den ECHTEN Bodenblock, TIEFEN-
			// getestet (hugt die Blockkanten, wird vom Terrain verdeckt) -> wirkt in
			// der Welt statt wie auf den Bildschirm geklebt. Kein Extra-Teil.
			VertexConsumer frame = immediate.getBuffer(RenderLayers.LINES);
			Set<Long> seen = new HashSet<>();
			for (Vec3d p : densify(raw, 0.4)) {
				int bx = (int) Math.floor(p.x);
				int bz = (int) Math.floor(p.z);
				int floorY = (int) Math.floor(p.y - 0.08) - 1; // fester Block unter den Füßen
				if (!seen.add(net.minecraft.util.math.BlockPos.asLong(bx, floorY, bz))) {
					continue;
				}
				blockFrame(frame, entry, m, bx, floorY, bz, 3.5f);
			}
		} else if (style == 0) {
			// Tiefengetestete Linie – von Blöcken verdeckt.
			VertexConsumer vc = immediate.getBuffer(RenderLayers.LINES);
			for (int i = 0; i + 1 < raw.size(); i++) {
				line(vc, entry, m, raw.get(i), raw.get(i + 1), 6f, 255);
			}
		} else if (style == 4) {
			// Boden-Glühen: Band flach auf dem Boden (zwei Lagen für Glow).
			VertexConsumer vc = immediate.getBuffer(RenderLayers.debugQuads());
			for (int i = 0; i + 1 < raw.size(); i++) {
				floorBand(vc, m, raw.get(i), raw.get(i + 1), 0.46f, 70);
			}
			for (int i = 0; i + 1 < raw.size(); i++) {
				floorBand(vc, m, raw.get(i), raw.get(i + 1), 0.17f, 185);
			}
		} else {
			// Band (durch Wände): breit (1) oder dünn (3).
			float tH = style == 3 ? 0.05f : 0.14f;
			VertexConsumer vc = immediate.getBuffer(RenderLayers.debugQuads());
			for (int i = 0; i + 1 < raw.size(); i++) {
				tube(vc, m, raw.get(i), raw.get(i + 1), tH, 0.04f, 200);
			}
		}

		ms.pop();
		immediate.draw();
	}

	// ---- Glättung ---------------------------------------------------------

	/** Punkte linear entlang der geraden Strecke verdichten (für die Leucht-Blöcke). */
	private static List<Vec3d> densify(List<Vec3d> pts, double stepLen) {
		if (pts.size() < 2) {
			return pts;
		}
		List<Vec3d> out = new ArrayList<>();
		for (int i = 0; i + 1 < pts.size(); i++) {
			Vec3d a = pts.get(i), b = pts.get(i + 1);
			double dist = a.distanceTo(b);
			int steps = Math.max(1, (int) (dist / stepLen));
			for (int s = 0; s < steps; s++) {
				double t = (double) s / steps;
				out.add(new Vec3d(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t));
			}
		}
		out.add(pts.get(pts.size() - 1));
		return out;
	}

	// ---- Primitive --------------------------------------------------------

	/** Eine tiefengetestete Linie a->b über die LINES-Layer (Breite per Vertex). */
	private static void line(VertexConsumer vc, MatrixStack.Entry e, Matrix4f m, Vec3d a, Vec3d b, float w, int alpha) {
		double dx = b.x - a.x, dy = b.y - a.y, dz = b.z - a.z;
		double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (len < 1e-5) {
			return;
		}
		float nx = (float) (dx / len), ny = (float) (dy / len), nz = (float) (dz / len);
		// Format POSITION_COLOR_NORMAL_LINE_WIDTH -> exakt diese Reihenfolge.
		vc.vertex(m, (float) a.x, (float) a.y, (float) a.z).color(R, G, B, alpha).normal(e, nx, ny, nz).lineWidth(w);
		vc.vertex(m, (float) b.x, (float) b.y, (float) b.z).color(R, G, B, alpha).normal(e, nx, ny, nz).lineWidth(w);
	}

	/** Leuchtender Rahmen um einen ganzen Block (12 Kanten, tiefengetestet). */
	private static void blockFrame(VertexConsumer vc, MatrixStack.Entry e, Matrix4f m, int bx, int by, int bz, float w) {
		float o = 0.004f; // minimal außerhalb -> kein Z-Fighting mit der Blockfläche
		double x0 = bx - o, x1 = bx + 1 + o;
		double y0 = by - o, y1 = by + 1 + o;
		double z0 = bz - o, z1 = bz + 1 + o;
		Vec3d c000 = new Vec3d(x0, y0, z0), c100 = new Vec3d(x1, y0, z0);
		Vec3d c101 = new Vec3d(x1, y0, z1), c001 = new Vec3d(x0, y0, z1);
		Vec3d c010 = new Vec3d(x0, y1, z0), c110 = new Vec3d(x1, y1, z0);
		Vec3d c111 = new Vec3d(x1, y1, z1), c011 = new Vec3d(x0, y1, z1);
		// untere Kante
		line(vc, e, m, c000, c100, w, 255);
		line(vc, e, m, c100, c101, w, 255);
		line(vc, e, m, c101, c001, w, 255);
		line(vc, e, m, c001, c000, w, 255);
		// obere Kante
		line(vc, e, m, c010, c110, w, 255);
		line(vc, e, m, c110, c111, w, 255);
		line(vc, e, m, c111, c011, w, 255);
		line(vc, e, m, c011, c010, w, 255);
		// senkrechte Kanten
		line(vc, e, m, c000, c010, w, 255);
		line(vc, e, m, c100, c110, w, 255);
		line(vc, e, m, c101, c111, w, 255);
		line(vc, e, m, c001, c011, w, 255);
	}

	/** Flaches, waagerechtes Band auf dem Boden (für das Boden-Glühen). */
	private static void floorBand(VertexConsumer vc, Matrix4f m, Vec3d a, Vec3d b, float halfW, int alpha) {
		double dx = b.x - a.x, dz = b.z - a.z;
		double len = Math.sqrt(dx * dx + dz * dz);
		if (len < 1e-5) {
			return;
		}
		double px = -dz / len * halfW, pz = dx / len * halfW; // senkrecht in XZ
		float ay = (float) a.y, by = (float) b.y;
		float[] a1 = { (float) (a.x + px), ay, (float) (a.z + pz) };
		float[] a2 = { (float) (a.x - px), ay, (float) (a.z - pz) };
		float[] b1 = { (float) (b.x + px), by, (float) (b.z + pz) };
		float[] b2 = { (float) (b.x - px), by, (float) (b.z - pz) };
		quad(vc, m, a1, a2, b2, b1, alpha);
		quad(vc, m, a1, b1, b2, a2, alpha); // Rückseite, damit von beiden Seiten sichtbar
	}

	/** Flaches Bändchen (Röhre) von a nach b – durch Wände sichtbar. */
	private static void tube(VertexConsumer vc, Matrix4f m, Vec3d a, Vec3d b, float tH, float tV, int alpha) {
		double dx = b.x - a.x, dy = b.y - a.y, dz = b.z - a.z;
		double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (len < 1e-5) {
			return;
		}
		dx /= len;
		dy /= len;
		dz /= len;
		double ux = 0, uy = 1, uz = 0;
		if (Math.abs(dy) > 0.9) {
			ux = 1;
			uy = 0;
		}
		double rx = dy * uz - dz * uy, ry = dz * ux - dx * uz, rz = dx * uy - dy * ux;
		double rl = Math.sqrt(rx * rx + ry * ry + rz * rz);
		rx = rx / rl * tH;
		ry = ry / rl * tH;
		rz = rz / rl * tH;
		double qx = dy * rz - dz * ry, qy = dz * rx - dx * rz, qz = dx * ry - dy * rx;
		double pl = Math.sqrt(qx * qx + qy * qy + qz * qz);
		qx = qx / pl * tV;
		qy = qy / pl * tV;
		qz = qz / pl * tV;
		float[] a1 = { (float) (a.x + rx + qx), (float) (a.y + ry + qy), (float) (a.z + rz + qz) };
		float[] a2 = { (float) (a.x + rx - qx), (float) (a.y + ry - qy), (float) (a.z + rz - qz) };
		float[] a3 = { (float) (a.x - rx - qx), (float) (a.y - ry - qy), (float) (a.z - rz - qz) };
		float[] a4 = { (float) (a.x - rx + qx), (float) (a.y - ry + qy), (float) (a.z - rz + qz) };
		float[] b1 = { (float) (b.x + rx + qx), (float) (b.y + ry + qy), (float) (b.z + rz + qz) };
		float[] b2 = { (float) (b.x + rx - qx), (float) (b.y + ry - qy), (float) (b.z + rz - qz) };
		float[] b3 = { (float) (b.x - rx - qx), (float) (b.y - ry - qy), (float) (b.z - rz - qz) };
		float[] b4 = { (float) (b.x - rx + qx), (float) (b.y - ry + qy), (float) (b.z - rz + qz) };
		quad(vc, m, a1, a2, b2, b1, alpha);
		quad(vc, m, a2, a3, b3, b2, alpha);
		quad(vc, m, a3, a4, b4, b3, alpha);
		quad(vc, m, a4, a1, b1, b4, alpha);
	}

	/** Kleiner Würfel (6 Flächen) um c, Halbkante s. */
	private static void box(VertexConsumer vc, Matrix4f m, Vec3d c, float s, int alpha) {
		float x0 = (float) (c.x - s), x1 = (float) (c.x + s);
		float y0 = (float) (c.y - s), y1 = (float) (c.y + s);
		float z0 = (float) (c.z - s), z1 = (float) (c.z + s);
		quad(vc, m, new float[] { x0, y0, z0 }, new float[] { x1, y0, z0 }, new float[] { x1, y0, z1 },
				new float[] { x0, y0, z1 }, alpha);
		quad(vc, m, new float[] { x0, y1, z0 }, new float[] { x0, y1, z1 }, new float[] { x1, y1, z1 },
				new float[] { x1, y1, z0 }, alpha);
		quad(vc, m, new float[] { x0, y0, z0 }, new float[] { x0, y1, z0 }, new float[] { x1, y1, z0 },
				new float[] { x1, y0, z0 }, alpha);
		quad(vc, m, new float[] { x0, y0, z1 }, new float[] { x1, y0, z1 }, new float[] { x1, y1, z1 },
				new float[] { x0, y1, z1 }, alpha);
		quad(vc, m, new float[] { x0, y0, z0 }, new float[] { x0, y0, z1 }, new float[] { x0, y1, z1 },
				new float[] { x0, y1, z0 }, alpha);
		quad(vc, m, new float[] { x1, y0, z0 }, new float[] { x1, y1, z0 }, new float[] { x1, y1, z1 },
				new float[] { x1, y0, z1 }, alpha);
	}

	private static void quad(VertexConsumer vc, Matrix4f m, float[] p1, float[] p2, float[] p3, float[] p4, int alpha) {
		vc.vertex(m, p1[0], p1[1], p1[2]).color(R, G, B, alpha);
		vc.vertex(m, p2[0], p2[1], p2[2]).color(R, G, B, alpha);
		vc.vertex(m, p3[0], p3[1], p3[2]).color(R, G, B, alpha);
		vc.vertex(m, p4[0], p4[1], p4[2]).color(R, G, B, alpha);
	}
}
