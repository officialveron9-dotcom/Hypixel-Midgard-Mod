package com.midgard.util;

import java.util.List;

import org.joml.Matrix4f;

import com.midgard.util.Waypoints.Marker;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

/**
 * Zeichnet Wegpunkt-Labels (Name + Entfernung) als Billboard-Text ECHT in der
 * 3D-Welt – wie Skyblocker, mit Minecrafts eingebautem {@link TextRenderer}
 * (kein externes Font-Mod). Nutzt den GLEICHEN Mechanismus wie die Pfad-Linie:
 * eigener {@link VertexConsumerProvider.Immediate} + selbst {@code draw()}.
 *
 * <p>Wichtige Details (häufige Fehlerquellen): Farbe als ARGB mit Alpha=0xFF
 * (sonst unsichtbar), {@code light=0xF000F0} (full bright), SEE_THROUGH (durch
 * Wände), kamera-relativ, Billboard über die Kamera-Rotation, Skalierung 0.025.</p>
 */
public final class WorldTextRenderer {

	private static BufferAllocator allocator;
	private static VertexConsumerProvider.Immediate immediate;

	private WorldTextRenderer() {
	}

	public static void render(WorldRenderContext ctx, List<Marker> markers) {
		if (markers == null || markers.isEmpty()) {
			return;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		MatrixStack ms = ctx.matrices();
		if (ms == null || mc.gameRenderer == null || mc.gameRenderer.getCamera() == null) {
			return;
		}
		Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
		TextRenderer tr = mc.textRenderer;
		if (immediate == null) {
			allocator = new BufferAllocator(1 << 16);
			immediate = VertexConsumerProvider.immediate(allocator);
		}

		for (Marker m : markers) {
			try {
				double wx = m.x() + 0.5, wy = m.y() + 1.4, wz = m.z() + 0.5;
				double dx = wx - cam.x, dy = wy - cam.y, dz = wz - cam.z;
				double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
				if (dist > 500) {
					continue;
				}
				float s = 0.025f * (float) Math.max(1.0, dist / 12.0); // lesbar mit Entfernung
				String name = m.label();
				String distLine = Math.round(dist) + "m";
				int nameColor = 0xFF000000 | (m.color() & 0xFFFFFF); // Alpha FF erzwingen

				ms.push();
				ms.translate(dx, dy, dz);
				// Billboard: zur Kamera drehen (über die Positionsmatrix – robust).
				ms.multiplyPositionMatrix(new Matrix4f().rotate(mc.gameRenderer.getCamera().getRotation()));
				ms.scale(-s, -s, s);
				Matrix4f mat = ms.peek().getPositionMatrix();
				tr.draw(name, -tr.getWidth(name) / 2f, -9f, nameColor, false, mat, immediate,
						TextRenderer.TextLayerType.SEE_THROUGH, 0, 0xF000F0);
				tr.draw(distLine, -tr.getWidth(distLine) / 2f, 1f, 0xFFFFFFFF, false, mat, immediate,
						TextRenderer.TextLayerType.SEE_THROUGH, 0, 0xF000F0);
				ms.pop();
			} catch (Throwable ignored) {
				// ein einzelnes Label darf nie alles abreißen
			}
		}
		immediate.draw(); // selbst flushen – exakt wie bei der Pfad-Linie
	}
}
