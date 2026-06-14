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
 * Zeichnet den {@link PathFinder}-Pfad ECHT in der 3D-Welt. Mehrere Stile zum
 * Auswählen (config.pathStyle):
 * <ul>
 *   <li>0 = Linie (Tiefe): tiefengetestete Linie ({@code RenderLayers.LINES}),
 *       wird von Blöcken verdeckt – geht NICHT durch Wände.</li>
 *   <li>1 = Bändchen (durch Wände): breites flaches Band, immer sichtbar.</li>
 *   <li>2 = Würfel-Spur: kleine Würfel an jedem Knoten, immer sichtbar.</li>
 *   <li>3 = Linie (durch Wände): dünnes Band, immer sichtbar.</li>
 * </ul>
 * Alle Stile nutzen einen EIGENEN Vertex-Puffer (nicht den geteilten der
 * Engine) – ein Fehler hier kann die Engine daher NIE zum Absturz bringen.
 */
public final class PathRenderer {

	private static final int R = 245, G = 130, B = 50, A = 200; // Akzent-Orange
	private static final int MAX_SEG = 200;

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
		if (style < 0 || style > 3) {
			style = 0;
		}

		ms.push();
		ms.translate(-cam.x, -cam.y, -cam.z);
		MatrixStack.Entry entry = ms.peek();
		Matrix4f m = entry.getPositionMatrix();

		int n = path.size();
		int step = Math.max(1, (n - 1) / MAX_SEG);

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
		Vec3d last = path.get(n - 1);

		if (style == 0) {
			// Tiefengetestete Linie – von Blöcken verdeckt.
			VertexConsumer vc = immediate.getBuffer(RenderLayers.LINES);
			Vec3d prev = feet;
			for (int i = startIdx; i < n; i += step) {
				line(vc, entry, m, prev, path.get(i), 6f);
				prev = path.get(i);
			}
			if (!prev.equals(last)) {
				line(vc, entry, m, prev, last, 6f);
			}
		} else if (style == 2) {
			// Würfel-Spur (durch Wände).
			VertexConsumer vc = immediate.getBuffer(RenderLayers.debugQuads());
			for (int i = startIdx; i < n; i += step) {
				box(vc, m, path.get(i), 0.11f);
			}
			box(vc, m, last, 0.11f);
		} else {
			// Band (durch Wände): breit (1) oder dünn (3).
			float tH = style == 3 ? 0.05f : 0.14f;
			VertexConsumer vc = immediate.getBuffer(RenderLayers.debugQuads());
			Vec3d prev = feet;
			for (int i = startIdx; i < n; i += step) {
				tube(vc, m, prev, path.get(i), tH, 0.04f);
				prev = path.get(i);
			}
			if (!prev.equals(last)) {
				tube(vc, m, prev, last, tH, 0.04f);
			}
		}

		ms.pop();
		immediate.draw();
	}

	/** Eine tiefengetestete Linie a->b über die LINES-Layer (Breite per Vertex). */
	private static void line(VertexConsumer vc, MatrixStack.Entry e, Matrix4f m, Vec3d a, Vec3d b, float w) {
		double dx = b.x - a.x, dy = b.y - a.y, dz = b.z - a.z;
		double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (len < 1e-4) {
			return;
		}
		float nx = (float) (dx / len), ny = (float) (dy / len), nz = (float) (dz / len);
		// Format POSITION_COLOR_NORMAL_LINE_WIDTH -> exakt diese Reihenfolge.
		vc.vertex(m, (float) a.x, (float) a.y, (float) a.z).color(R, G, B, 255).normal(e, nx, ny, nz).lineWidth(w);
		vc.vertex(m, (float) b.x, (float) b.y, (float) b.z).color(R, G, B, 255).normal(e, nx, ny, nz).lineWidth(w);
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
		double px = dy * rz - dz * ry, py = dz * rx - dx * rz, pz = dx * ry - dy * rx;
		double pl = Math.sqrt(px * px + py * py + pz * pz);
		px = px / pl * tV;
		py = py / pl * tV;
		pz = pz / pl * tV;
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

	/** Kleiner Würfel (6 Flächen) um c, Halbkante s. */
	private static void box(VertexConsumer vc, Matrix4f m, Vec3d c, float s) {
		float x0 = (float) (c.x - s), x1 = (float) (c.x + s);
		float y0 = (float) (c.y - s), y1 = (float) (c.y + s);
		float z0 = (float) (c.z - s), z1 = (float) (c.z + s);
		quad(vc, m, new float[] { x0, y0, z0 }, new float[] { x1, y0, z0 }, new float[] { x1, y0, z1 },
				new float[] { x0, y0, z1 });
		quad(vc, m, new float[] { x0, y1, z0 }, new float[] { x0, y1, z1 }, new float[] { x1, y1, z1 },
				new float[] { x1, y1, z0 });
		quad(vc, m, new float[] { x0, y0, z0 }, new float[] { x0, y1, z0 }, new float[] { x1, y1, z0 },
				new float[] { x1, y0, z0 });
		quad(vc, m, new float[] { x0, y0, z1 }, new float[] { x1, y0, z1 }, new float[] { x1, y1, z1 },
				new float[] { x0, y1, z1 });
		quad(vc, m, new float[] { x0, y0, z0 }, new float[] { x0, y0, z1 }, new float[] { x0, y1, z1 },
				new float[] { x0, y1, z0 });
		quad(vc, m, new float[] { x1, y0, z0 }, new float[] { x1, y1, z0 }, new float[] { x1, y1, z1 },
				new float[] { x1, y0, z1 });
	}

	private static void quad(VertexConsumer vc, Matrix4f m, float[] p1, float[] p2, float[] p3, float[] p4) {
		vc.vertex(m, p1[0], p1[1], p1[2]).color(R, G, B, A);
		vc.vertex(m, p2[0], p2[1], p2[2]).color(R, G, B, A);
		vc.vertex(m, p3[0], p3[1], p3[2]).color(R, G, B, A);
		vc.vertex(m, p4[0], p4[1], p4[2]).color(R, G, B, A);
	}
}
