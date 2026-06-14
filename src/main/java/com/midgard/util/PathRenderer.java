package com.midgard.util;

import java.util.List;

import org.joml.Matrix4f;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

/**
 * Zeichnet den aktuellen {@link PathFinder}-Pfad ECHT in der Welt als
 * durchgehende Linie (RenderLayer.lines), kamerarelativ. Wird in
 * WorldRenderEvents.AFTER_ENTITIES aufgerufen. Defensiv (try/catch im Aufrufer)
 * – darf nie crashen.
 */
public final class PathRenderer {

	private static final int R = 0xF2, G = 0x77, B = 0x2F; // Akzent-Orange

	private PathRenderer() {
	}

	public static void render(WorldRenderContext ctx) {
		List<Vec3d> path = PathFinder.currentPath();
		if (path == null || path.size() < 2) {
			return;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.gameRenderer == null || mc.gameRenderer.getCamera() == null) {
			return;
		}
		MatrixStack ms = ctx.matrices();
		VertexConsumerProvider consumers = ctx.consumers();
		if (ms == null || consumers == null) {
			return;
		}
		Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();

		ms.push();
		ms.translate(-cam.x, -cam.y, -cam.z);
		MatrixStack.Entry e = ms.peek();
		Matrix4f mat = e.getPositionMatrix();
		VertexConsumer vc = consumers.getBuffer(RenderLayers.lines());

		for (int i = 0; i + 1 < path.size(); i++) {
			Vec3d a = path.get(i);
			Vec3d b = path.get(i + 1);
			float nx = (float) (b.x - a.x), ny = (float) (b.y - a.y), nz = (float) (b.z - a.z);
			float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
			if (len < 1e-4f) {
				continue;
			}
			nx /= len;
			ny /= len;
			nz /= len;
			vc.vertex(mat, (float) a.x, (float) a.y, (float) a.z).color(R, G, B, 255).normal(e, nx, ny, nz);
			vc.vertex(mat, (float) b.x, (float) b.y, (float) b.z).color(R, G, B, 255).normal(e, nx, ny, nz);
		}
		ms.pop();

		// Sofort zeichnen, damit die Linie sicher erscheint.
		if (consumers instanceof VertexConsumerProvider.Immediate imm) {
			imm.draw(RenderLayers.lines());
		}
	}
}
