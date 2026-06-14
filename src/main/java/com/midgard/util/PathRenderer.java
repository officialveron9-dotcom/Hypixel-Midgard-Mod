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

	private static final int R = 242, G = 119, B = 47, A = 120; // Akzent-Orange, halbdurchsichtig
	private static final int MAX_BOXES = 90;

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
		int step = Math.max(1, n / MAX_BOXES);
		float h = 0.16f;
		for (int i = 0; i < n; i += step) {
			Vec3d v = path.get(i);
			box(vc, m, v.x, v.y, v.z, h);
		}
		ms.pop();

		immediate.draw();
	}

	/** Kleiner Würfel (6 Quad-Flächen) bei (cx,cy,cz). */
	private static void box(VertexConsumer vc, Matrix4f m, double cx, double cy, double cz, float h) {
		float x0 = (float) (cx - h), x1 = (float) (cx + h);
		float y0 = (float) cy, y1 = (float) (cy + 2 * h);
		float z0 = (float) (cz - h), z1 = (float) (cz + h);
		quad(vc, m, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1); // unten
		quad(vc, m, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0); // oben
		quad(vc, m, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0); // -z
		quad(vc, m, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1); // +z
		quad(vc, m, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0); // -x
		quad(vc, m, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1); // +x
	}

	private static void quad(VertexConsumer vc, Matrix4f m,
			float ax, float ay, float az, float bx, float by, float bz,
			float cx, float cy, float cz, float dx, float dy, float dz) {
		vc.vertex(m, ax, ay, az).color(R, G, B, A);
		vc.vertex(m, bx, by, bz).color(R, G, B, A);
		vc.vertex(m, cx, cy, cz).color(R, G, B, A);
		vc.vertex(m, dx, dy, dz).color(R, G, B, A);
	}
}
