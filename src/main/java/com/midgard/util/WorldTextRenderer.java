package com.midgard.util;

import java.util.List;

import com.midgard.util.Waypoints.Marker;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Zeichnet Wegpunkt-Labels (Name + Entfernung) ECHT in der 3D-Welt – wie
 * Skyblocker. WICHTIG für 1.21.11: das geht über das NEUE Submit-System, nicht
 * mehr über einen eigenen Vertex-Puffer. Vanilla rendert Namensschilder so:
 * {@code commandQueue.submitLabel(matrices, pos, bg, text, seeThrough, light,
 * distSq, cameraState)}. Die Engine batcht und zeichnet die Labels selbst (kein
 * eigenes draw()).
 */
public final class WorldTextRenderer {

	private WorldTextRenderer() {
	}

	public static void render(WorldRenderContext ctx, List<Marker> markers) {
		if (markers == null || markers.isEmpty()) {
			return;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		MatrixStack ms = ctx.matrices();
		OrderedRenderCommandQueue queue = ctx.commandQueue();
		if (ms == null || queue == null || mc.gameRenderer == null || mc.gameRenderer.getCamera() == null) {
			return;
		}
		Camera camera = mc.gameRenderer.getCamera();
		Vec3d cam = camera.getCameraPos();

		// Kamera-Zustand selbst befüllen (für das Billboard der Labels).
		CameraRenderState crs = new CameraRenderState();
		crs.pos = cam;
		crs.entityPos = cam;
		crs.orientation = camera.getRotation();
		crs.blockPos = BlockPos.ofFloored(cam);
		crs.initialized = true;

		for (Marker m : markers) {
			try {
				double wx = m.x() + 0.5, wy = m.y() + 1.4, wz = m.z() + 0.5;
				double dx = wx - cam.x, dy = wy - cam.y, dz = wz - cam.z;
				double dsq = dx * dx + dy * dy + dz * dz;
				if (dsq > 500 * 500) {
					continue;
				}
				int dist = (int) Math.round(Math.sqrt(dsq));
				MutableText text = Text.literal(m.label())
						.setStyle(Style.EMPTY.withColor(TextColor.fromRgb(m.color() & 0xFFFFFF)));
				text.append(Text.literal("  " + dist + "m").formatted(Formatting.GRAY));

				// Größe wächst mit der Entfernung -> Label bleibt von weitem lesbar
				// (konstante Bildschirmgröße statt winzig).
				float scale = (float) Math.max(1.6, dist / 9.0);
				ms.push();
				ms.translate(dx, dy, dz);
				ms.scale(scale, scale, scale);
				// matrices an der Marker-Position, pos=ZERO, bg=0, seeThrough=true,
				// light=full bright, distSq, Kamera-Zustand.
				queue.submitLabel(ms, Vec3d.ZERO, 0, text, true, 0xF000F0, dsq, crs);
				ms.pop();
			} catch (Throwable ignored) {
				// ein einzelnes Label darf nie alles abreißen
			}
		}
	}
}
