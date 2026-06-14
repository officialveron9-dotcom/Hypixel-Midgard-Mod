package com.midgard.util;

import java.util.List;

import org.joml.Matrix4f;
import org.joml.Quaternionf;

import com.midgard.util.Waypoints.Marker;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

/**
 * Zeichnet die Wegpunkt-Marker (Name + Entfernung) ECHT in der 3D-Welt als
 * Billboard-Text – so wie SkyHanni/Skyblocker. Anders als die frühere eigene
 * 2D-Bildschirm-Projektion sitzt das Label fest an der Weltposition und wackelt
 * NICHT beim Springen/Ducken/Sprinten (die Engine-Kamera-Matrix erledigt
 * Augenhöhe, Bobbing und FOV korrekt). {@link TextRenderer.TextLayerType#SEE_THROUGH}
 * = durch Wände sichtbar (Wegpunkte sollen immer sichtbar sein). Eigener Puffer
 * -> kann die Engine nie crashen.
 */
public final class WorldMarkers {

	private WorldMarkers() {
	}

	public static void render(WorldRenderContext ctx, List<Marker> markers) {
		if (markers == null || markers.isEmpty()) {
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
		Quaternionf rot = mc.gameRenderer.getCamera().getRotation();
		TextRenderer tr = mc.textRenderer;
		// Entity-Puffer des Spiels nutzen – der zeichnet die Schrift-Layer korrekt
		// (eigener Puffer rendert Text-Layer oft nicht). draw() flusht nur unseres.
		VertexConsumerProvider.Immediate immediate = mc.getBufferBuilders().getEntityVertexConsumers();

		for (Marker m : markers) {
			try {
				double wx = m.x() + 0.5, wy = m.y() + 1.5, wz = m.z() + 0.5;
				double dx = wx - cam.x, dy = wy - cam.y, dz = wz - cam.z;
				double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
				if (dist > 500) {
					continue;
				}
				// Skalierung wächst mit der Entfernung -> Label bleibt lesbar groß.
				float s = 0.025f * (float) Math.max(1.0, dist / 10.0);
				ms.push();
				ms.translate(dx, dy, dz);
				ms.multiply(rot); // Billboard: immer zur Kamera drehen
				ms.scale(-s, -s, s); // Text-Konvention (X/Y gespiegelt)
				Matrix4f mat = ms.peek().getPositionMatrix();
				String name = m.label();
				String distLine = Math.round(dist) + "m";
				int light = 0xF000F0;
				tr.draw(name, -tr.getWidth(name) / 2f, -10f, m.color(), true, mat, immediate,
						TextRenderer.TextLayerType.SEE_THROUGH, 0, light);
				tr.draw(distLine, -tr.getWidth(distLine) / 2f, 0f, 0xFFFFFFFF, true, mat, immediate,
						TextRenderer.TextLayerType.SEE_THROUGH, 0, light);
				ms.pop();
			} catch (Throwable ignored) {
				// einzelner Marker darf nie alles abreißen
			}
		}
		immediate.draw();
	}
}
