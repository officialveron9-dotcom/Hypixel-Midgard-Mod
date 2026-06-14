package com.midgard.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexFormats;

/**
 * Eigene Render-Layer der Midgard-Mod. Stellt eine TIEFENGETESTETE, gefüllte
 * POSITION_COLOR-Layer bereit – damit 3D-Geometrie (die Pfad-Bändchen) vom
 * Terrain verdeckt wird und richtig „in der Welt" sitzt, statt wie ein
 * Bildschirm-Overlay zu wirken. {@code RenderLayer.of} ist über den
 * Access-Widener ({@code midgard.accesswidener}) zugänglich gemacht.
 *
 * <p>Defensiv: schlägt der Aufbau fehl, gibt {@link #depthQuads()} {@code null}
 * zurück und der Aufrufer weicht auf eine vorhandene Layer aus – nie ein Crash.</p>
 */
public final class MidgardLayers {

	private static boolean tried;
	private static RenderLayer depthQuads;

	private MidgardLayers() {
	}

	/** Tiefengetestete gefüllte Quad-Layer (POSITION_COLOR) oder {@code null}. */
	public static RenderLayer depthQuads() {
		if (!tried) {
			tried = true;
			try {
				RenderPipeline pipeline = RenderPipeline.builder()
						.withLocation("midgard:pipeline/path_filled_depth")
						.withVertexShader("core/position_color")
						.withFragmentShader("core/position_color")
						.withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
						.withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
						.withDepthWrite(false)
						.withColorWrite(true)
						.withCull(false)
						.withBlend(BlendFunction.TRANSLUCENT)
						.build();
				RenderSetup setup = RenderSetup.builder(pipeline).translucent().build();
				depthQuads = RenderLayer.of("midgard_path_filled_depth", setup);
			} catch (Throwable t) {
				System.err.println("[Midgard] Tiefen-Fuell-Layer nicht verfuegbar, weiche aus: " + t);
				depthQuads = null;
			}
		}
		return depthQuads;
	}
}
