package com.midgard.util;

import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix4f;

import com.midgard.render.MidgardLayers;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

/**
 * Zeichnet den {@link PathFinder}-Pfad als EINE saubere 3D-Linie in der Welt.
 *
 * <p>Es wird ausschließlich weltraum-feste Geometrie (ein dünnes Bändchen)
 * gezeichnet → korrekte Perspektive (weiter weg = kleiner), kein Schwimmen beim
 * Drehen (anders als die {@code LINES}-Layer). Tiefengetestet über
 * {@link MidgardLayers} → wird vom Terrain verdeckt (nicht durch Wände).</p>
 *
 * <p>Der Anfang liegt per Projektion ein Stück VOR dem Spieler auf dem Pfad
 * (nicht „in" ihm) und wandert beim Laufen ruhig mit. Eigener Vertex-Puffer →
 * kann die Engine nie crashen; fällt die Tiefen-Layer aus, wird auf
 * {@code debugQuads} ausgewichen.</p>
 */
public final class PathRenderer {

	private static final int R = 245, G = 130, B = 50; // Akzent-Orange
	private static final int ALPHA = 220;
	private static final double START_AHEAD = 1.3; // Blöcke vor dem Spieler beginnen

	private static BufferAllocator allocator;
	private static VertexConsumerProvider.Immediate immediate;

	private PathRenderer() {
	}

	public static void render(WorldRenderContext ctx) {
		List<Vec3d> path = PathFinder.currentPath();
		if (path == null || path.size() < 2) {
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

		// Renderpunkte ab einem Punkt VOR dem Spieler (Projektion auf den Pfad).
		List<Vec3d> rp = pointsAhead(path, mc.player.getX(), mc.player.getZ());
		if (rp.size() < 2) {
			return;
		}

		ms.push();
		ms.translate(-cam.x, -cam.y, -cam.z);
		Matrix4f m = ms.peek().getPositionMatrix();

		VertexConsumer vc = fillVc();
		for (int i = 0; i + 1 < rp.size(); i++) {
			tube(vc, m, rp.get(i), rp.get(i + 1), 0.085f, 0.05f, ALPHA);
		}

		ms.pop();
		immediate.draw();
	}

	/**
	 * Zeichnet an jedem Wegpunkt einen senkrechten LEUCHT-STRAHL (wie ein Beacon).
	 * Bewusst OHNE Tiefentest (durch Wände sichtbar) – so sieht man das Ziel und
	 * die Richtung von überall, auch wenn die Bodenlinie verdeckt ist.
	 */
	public static void renderBeams(WorldRenderContext ctx, List<Waypoints.Marker> markers) {
		if (markers == null || markers.isEmpty()) {
			return;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.gameRenderer == null || mc.gameRenderer.getCamera() == null) {
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
		ms.push();
		ms.translate(-cam.x, -cam.y, -cam.z);
		Matrix4f m = ms.peek().getPositionMatrix();
		VertexConsumer vc = immediate.getBuffer(RenderLayers.debugQuads());
		for (Waypoints.Marker mk : markers) {
			beam(vc, m, mk.x() + 0.5, mk.y(), mk.z() + 0.5, 28f, 0.16f, mk.color());
		}
		ms.pop();
		immediate.draw();
	}

	/** Senkrechter Strahl (4-seitige Säule) mit Alpha-Verlauf nach oben. */
	private static void beam(VertexConsumer vc, Matrix4f m, double cx, double by, double cz, float height, float hw,
			int argb) {
		int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
		float x0 = (float) (cx - hw), x1 = (float) (cx + hw);
		float z0 = (float) (cz - hw), z1 = (float) (cz + hw);
		float y0 = (float) by, y1 = (float) (by + height);
		int aB = 150, aT = 6;
		gquad(vc, m, r, g, b, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, aB, aT);
		gquad(vc, m, r, g, b, x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, aB, aT);
		gquad(vc, m, r, g, b, x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, aB, aT);
		gquad(vc, m, r, g, b, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, aB, aT);
	}

	/** Quad mit unten (aB) / oben (aT) unterschiedlicher Transparenz. */
	private static void gquad(VertexConsumer vc, Matrix4f m, int r, int g, int b,
			float x1, float y1, float z1, float x2, float y2, float z2,
			float x3, float y3, float z3, float x4, float y4, float z4, int aB, int aT) {
		vc.vertex(m, x1, y1, z1).color(r, g, b, aB);
		vc.vertex(m, x2, y2, z2).color(r, g, b, aB);
		vc.vertex(m, x3, y3, z3).color(r, g, b, aT);
		vc.vertex(m, x4, y4, z4).color(r, g, b, aT);
	}

	/**
	 * Projiziert den Spieler (nur X/Z) auf den Pfad und liefert die Punkte ab
	 * {@code START_AHEAD} Blöcken davor bis zum Ziel. Smooth (kein Springen),
	 * der Anfang liegt vor dem Spieler statt in ihm.
	 */
	private static List<Vec3d> pointsAhead(List<Vec3d> path, double px, double pz) {
		int n = path.size();
		int seg = 0;
		double segT = 0;
		double best = Double.MAX_VALUE;
		for (int i = 0; i + 1 < n; i++) {
			Vec3d a = path.get(i), b = path.get(i + 1);
			double abx = b.x - a.x, abz = b.z - a.z;
			double ab2 = abx * abx + abz * abz;
			double t = ab2 < 1e-9 ? 0 : ((px - a.x) * abx + (pz - a.z) * abz) / ab2;
			t = t < 0 ? 0 : t > 1 ? 1 : t;
			double cx = a.x + abx * t, cz = a.z + abz * t;
			double dx = px - cx, dz = pz - cz;
			double d = dx * dx + dz * dz;
			if (d < best) {
				best = d;
				seg = i;
				segT = t;
			}
		}
		// Aktueller Punkt auf dem Pfad, dann START_AHEAD Blöcke vorwärts wandern.
		Vec3d cur = lerp(path.get(seg), path.get(seg + 1), segT);
		double ahead = START_AHEAD;
		int si = seg;
		while (ahead > 0 && si < n - 1) {
			Vec3d end = path.get(si + 1);
			double rem = cur.distanceTo(end);
			if (rem >= ahead && rem > 1e-6) {
				cur = lerpDist(cur, end, ahead);
				ahead = 0;
			} else {
				ahead -= rem;
				cur = end;
				si++;
			}
		}
		List<Vec3d> out = new ArrayList<>();
		out.add(cur);
		for (int i = si + 1; i < n; i++) {
			out.add(path.get(i));
		}
		return out;
	}

	private static Vec3d lerp(Vec3d a, Vec3d b, double t) {
		return new Vec3d(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t);
	}

	private static Vec3d lerpDist(Vec3d from, Vec3d to, double dist) {
		double dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
		double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (len < 1e-6) {
			return from;
		}
		double f = dist / len;
		return new Vec3d(from.x + dx * f, from.y + dy * f, from.z + dz * f);
	}

	/** VertexConsumer der tiefengetesteten Füll-Layer (oder debugQuads als Fallback). */
	private static VertexConsumer fillVc() {
		RenderLayer depthLayer = MidgardLayers.depthQuads();
		return immediate.getBuffer(depthLayer != null ? depthLayer : RenderLayers.debugQuads());
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

	private static void quad(VertexConsumer vc, Matrix4f m, float[] p1, float[] p2, float[] p3, float[] p4, int alpha) {
		vc.vertex(m, p1[0], p1[1], p1[2]).color(R, G, B, alpha);
		vc.vertex(m, p2[0], p2[1], p2[2]).color(R, G, B, alpha);
		vc.vertex(m, p3[0], p3[1], p3[2]).color(R, G, B, alpha);
		vc.vertex(m, p4[0], p4[1], p4[2]).color(R, G, B, alpha);
	}
}
