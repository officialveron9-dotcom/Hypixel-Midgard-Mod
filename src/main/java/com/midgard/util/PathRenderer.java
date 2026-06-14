package com.midgard.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.joml.Matrix4f;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import com.midgard.render.MidgardLayers;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

/**
 * Zeichnet den {@link PathFinder}-Pfad als ECHTE 3D-Geometrie in der Welt.
 *
 * <p>WICHTIG für den „in-world"-Look: Es werden ausschließlich weltraum-feste
 * Dreiecke/Quads gezeichnet (Bändchen, Würfel, Boden-Band). Dadurch wird der
 * Pfad mit Entfernung korrekt KLEINER (Perspektive) und SCHWIMMT NICHT beim
 * Bewegen/Drehen – anders als die {@code LINES}-Layer, die in Bildschirm-Pixeln
 * expandiert (konstante Breite, „verzieht" sich beim Drehen).</p>
 *
 * <p>Die „Tiefe"-Stile nutzen eine selbstgebaute, TIEFENGETESTETE Füll-Layer
 * ({@link MidgardRenderLayers}) – so verdeckt das Terrain den Pfad korrekt.
 * Klappt der Aufbau nicht, wird automatisch auf {@code debugQuads} (ohne Tiefe)
 * ausgewichen. Eigener Vertex-Puffer -> kann die Engine nie crashen.</p>
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

		int style = 0;
		try {
			if (com.midgard.Midgard.config != null) {
				style = com.midgard.Midgard.config.pathStyle;
			}
		} catch (Throwable ignored) {
		}
		if (style < 0 || style > 5) {
			style = 0;
		}

		int n = path.size();
		// Nächsten Knoten NUR horizontal suchen (Y ignorieren) und den Startpunkt
		// auf die BODENHÖHE dieses Knotens legen -> die Linie bleibt am Boden,
		// auch wenn man springt/fliegt (geht nicht mit in die Luft).
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

		// Rohpunkte (Füße am Boden + restliche Knoten, ggf. ausgedünnt).
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
		Matrix4f m = ms.peek().getPositionMatrix();

		// Tiefengetestete Füll-Layer (wird vom Terrain verdeckt). Fällt sie aus,
		// nutzt fillVc() automatisch debugQuads (ohne Tiefe). "thru" = absichtlich
		// durch Wände sichtbar (debugQuads).
		VertexConsumer depth = fillVc();
		VertexConsumer thru = immediate.getBuffer(RenderLayers.debugQuads());

		switch (style) {
			case 1: // Bändchen (durch Wände)
				for (int i = 0; i + 1 < raw.size(); i++) {
					tube(thru, m, raw.get(i), raw.get(i + 1), 0.14f, 0.05f, 200);
				}
				break;
			case 2: // Würfel-Spur (Tiefe)
				for (int i = 1; i < raw.size(); i++) {
					box(depth, m, raw.get(i), 0.11f, 210);
				}
				break;
			case 3: // dünne Linie (durch Wände)
				for (int i = 0; i + 1 < raw.size(); i++) {
					tube(thru, m, raw.get(i), raw.get(i + 1), 0.05f, 0.04f, 200);
				}
				break;
			case 4: // Boden-Glühen (Tiefe): Band flach auf dem Boden, zwei Lagen
				for (int i = 0; i + 1 < raw.size(); i++) {
					floorBand(depth, m, raw.get(i), raw.get(i + 1), 0.46f, 70);
				}
				for (int i = 0; i + 1 < raw.size(); i++) {
					floorBand(depth, m, raw.get(i), raw.get(i + 1), 0.17f, 185);
				}
				break;
			case 5: // Leucht-Blöcke (Tiefe): Rahmen um den echten Bodenblock
				Set<Long> seen = new HashSet<>();
				for (Vec3d p : densify(raw, 0.4)) {
					int bx = (int) Math.floor(p.x);
					int bz = (int) Math.floor(p.z);
					int floorY = (int) Math.floor(p.y - 0.08) - 1;
					if (!seen.add(net.minecraft.util.math.BlockPos.asLong(bx, floorY, bz))) {
						continue;
					}
					blockFrame(depth, m, bx, floorY, bz, 0.03f);
				}
				break;
			default: // 0: 3D-Linie (Tiefe) – Standard, in-world
				for (int i = 0; i + 1 < raw.size(); i++) {
					tube(depth, m, raw.get(i), raw.get(i + 1), 0.09f, 0.05f, 215);
				}
				break;
		}

		ms.pop();
		immediate.draw();
	}

	/** VertexConsumer der tiefengetesteten Füll-Layer (oder debugQuads als Fallback). */
	private static VertexConsumer fillVc() {
		RenderLayer depthLayer = MidgardLayers.depthQuads();
		return immediate.getBuffer(depthLayer != null ? depthLayer : RenderLayers.debugQuads());
	}

	// ---- Geometrie --------------------------------------------------------

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

	/** Rahmen um einen ganzen Block (12 dünne 3D-Kanten). */
	private static void blockFrame(VertexConsumer vc, Matrix4f m, int bx, int by, int bz, float t) {
		double x0 = bx, x1 = bx + 1, y0 = by, y1 = by + 1, z0 = bz, z1 = bz + 1;
		Vec3d c000 = new Vec3d(x0, y0, z0), c100 = new Vec3d(x1, y0, z0);
		Vec3d c101 = new Vec3d(x1, y0, z1), c001 = new Vec3d(x0, y0, z1);
		Vec3d c010 = new Vec3d(x0, y1, z0), c110 = new Vec3d(x1, y1, z0);
		Vec3d c111 = new Vec3d(x1, y1, z1), c011 = new Vec3d(x0, y1, z1);
		// untere 4 Kanten
		tube(vc, m, c000, c100, t, t, 230);
		tube(vc, m, c100, c101, t, t, 230);
		tube(vc, m, c101, c001, t, t, 230);
		tube(vc, m, c001, c000, t, t, 230);
		// obere 4 Kanten
		tube(vc, m, c010, c110, t, t, 230);
		tube(vc, m, c110, c111, t, t, 230);
		tube(vc, m, c111, c011, t, t, 230);
		tube(vc, m, c011, c010, t, t, 230);
		// 4 senkrechte Kanten
		tube(vc, m, c000, c010, t, t, 230);
		tube(vc, m, c100, c110, t, t, 230);
		tube(vc, m, c101, c111, t, t, 230);
		tube(vc, m, c001, c011, t, t, 230);
	}

	/** Flaches, waagerechtes Band auf dem Boden (für das Boden-Glühen). */
	private static void floorBand(VertexConsumer vc, Matrix4f m, Vec3d a, Vec3d b, float halfW, int alpha) {
		double dx = b.x - a.x, dz = b.z - a.z;
		double len = Math.sqrt(dx * dx + dz * dz);
		if (len < 1e-5) {
			return;
		}
		double pxx = -dz / len * halfW, pzz = dx / len * halfW; // senkrecht in XZ
		float ay = (float) a.y, by = (float) b.y;
		float[] a1 = { (float) (a.x + pxx), ay, (float) (a.z + pzz) };
		float[] a2 = { (float) (a.x - pxx), ay, (float) (a.z - pzz) };
		float[] b1 = { (float) (b.x + pxx), by, (float) (b.z + pzz) };
		float[] b2 = { (float) (b.x - pxx), by, (float) (b.z - pzz) };
		quad(vc, m, a1, a2, b2, b1, alpha);
		quad(vc, m, a1, b1, b2, a2, alpha); // Rückseite (von beiden Seiten sichtbar)
	}

	/** 3D-Bändchen (Quader-Röhre) von a nach b – echte Weltgeometrie. */
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
