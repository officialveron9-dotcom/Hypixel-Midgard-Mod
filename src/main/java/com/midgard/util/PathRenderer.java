package com.midgard.util;

import java.util.List;

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
 * Zeichnet den {@link PathFinder}-Pfad ECHT in der 3D-Welt als Reihe kleiner,
 * durchsichtiger Würfel (wie SkyHanni/Skyblocker-Wegpunkte). Nutzt einen
 * EIGENEN Vertex-Puffer (nicht den geteilten der Engine) + die gefüllte
 * Quad-Layer (POSITION_COLOR, kein LineWidth) – dadurch kann ein Fehler hier
 * NIE die Engine zum Absturz bringen (im Gegensatz zur Linien-Layer früher).
 */
public final class PathRenderer {

	private static final int R = 245, G = 130, B = 50, A = 200; // Akzent-Orange, kräftiger
	private static final int MAX_BOXES = 200;

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
		int n = path.size();
		int step = Math.max(1, (n - 1) / MAX_BOXES);

		// Linie beginnt am Spieler und zeigt nur den NOCH offenen Rest -> bleibt
		// ruhig und schrumpft hinter einem, statt komplett neu zu springen.
		Vec3d feet = new Vec3d(mc.player.getX(), mc.player.getY() + 0.1, mc.player.getZ());
		int startIdx = 0;
		double bestD = Double.MAX_VALUE;
		for (int i = 0; i < n; i++) {
			double d = path.get(i).squaredDistanceTo(feet);
			if (d < bestD) {
				bestD = d;
				startIdx = i;
			}
		}

		Vec3d prev = feet;
		for (int i = startIdx; i < n; i += step) {
			Vec3d v = path.get(i);
			tube(vc, m, prev, v, 0.13f, 0.05f);
			prev = v;
		}
		if (!prev.equals(path.get(n - 1))) {
			tube(vc, m, prev, path.get(n - 1), 0.13f, 0.05f);
		}
		ms.pop();

		immediate.draw();
	}

	/** Flaches Bändchen von a nach b (breit + flach) = Linie über dem Boden. */
	private static void tube(VertexConsumer vc, Matrix4f m, Vec3d a, Vec3d b, float tH, float tV) {
		double dx = b.x - a.x, dy = b.y - a.y, dz = b.z - a.z;
		double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (len < 1e-4) {
			return;
		}
		dx /= len;
		dy /= len;
		dz /= len;
		// zwei zur Richtung senkrechte Vektoren
		double ux = 0, uy = 1, uz = 0;
		if (Math.abs(dy) > 0.9) {
			ux = 1;
			uy = 0;
		}
		// right = dir x up (horizontal, breit)
		double rx = dy * uz - dz * uy, ry = dz * ux - dx * uz, rz = dx * uy - dy * ux;
		double rl = Math.sqrt(rx * rx + ry * ry + rz * rz);
		rx = rx / rl * tH;
		ry = ry / rl * tH;
		rz = rz / rl * tH;
		// up2 = dir x right (vertikal, flach)
		double px = dy * rz - dz * ry, py = dz * rx - dx * rz, pz = dx * ry - dy * rx;
		double pl = Math.sqrt(px * px + py * py + pz * pz);
		px = px / pl * tV;
		py = py / pl * tV;
		pz = pz / pl * tV;
		// 4 Eckpunkte an a und b
		float[] a1 = { (float) (a.x + rx + px), (float) (a.y + ry + py), (float) (a.z + rz + pz) };
		float[] a2 = { (float) (a.x + rx - px), (float) (a.y + ry - py), (float) (a.z + rz - pz) };
		float[] a3 = { (float) (a.x - rx - px), (float) (a.y - ry - py), (float) (a.z - rz - pz) };
		float[] a4 = { (float) (a.x - rx + px), (float) (a.y - ry + py), (float) (a.z - rz + pz) };
		float[] b1 = { (float) (b.x + rx + px), (float) (b.y + ry + py), (float) (b.z + rz + pz) };
		float[] b2 = { (float) (b.x + rx - px), (float) (b.y + ry - py), (float) (b.z + rz - pz) };
		float[] b3 = { (float) (b.x - rx - px), (float) (b.y - ry - py), (float) (b.z - rz - pz) };
		float[] b4 = { (float) (b.x - rx + px), (float) (b.y - ry + py), (float) (b.z - rz + pz) };
		quad(vc, m, a1, a2, b2, b1);
		quad(vc, m, a2, a3, b3, b2);
		quad(vc, m, a3, a4, b4, b3);
		quad(vc, m, a4, a1, b1, b4);
	}

	private static void quad(VertexConsumer vc, Matrix4f m, float[] p1, float[] p2, float[] p3, float[] p4) {
		vc.vertex(m, p1[0], p1[1], p1[2]).color(R, G, B, A);
		vc.vertex(m, p2[0], p2[1], p2[2]).color(R, G, B, A);
		vc.vertex(m, p3[0], p3[1], p3[2]).color(R, G, B, A);
		vc.vertex(m, p4[0], p4[1], p4[2]).color(R, G, B, A);
	}
}
