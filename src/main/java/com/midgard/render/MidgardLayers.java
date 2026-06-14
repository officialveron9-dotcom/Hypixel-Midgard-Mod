package com.midgard.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.gl.RenderPipelines;
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
				// Aus dem vanilla POSITION_COLOR-Snippet ableiten (bringt Shader +
				// alle nötigen Uniforms mit) und nur Tiefentest/Blend/Format ändern.
				RenderPipeline pipeline = RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
						.withLocation("pipeline/midgard_path_filled")
						.withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
						.withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
						.withDepthWrite(false)
						.withCull(false)
						.withBlend(BlendFunction.TRANSLUCENT)
						.build();
				RenderSetup setup = RenderSetup.builder(pipeline).translucent().build();
				depthQuads = RenderLayer.of("midgard_path_filled_depth", setup);
				System.out.println("[Midgard] Tiefen-Fuell-Layer aktiv (Pfad wird vom Terrain verdeckt).");
			} catch (Throwable t) {
				System.err.println("[Midgard] Tiefen-Fuell-Layer nicht verfuegbar, weiche aus (durch Waende): " + t);
				depthQuads = null;
			}
		}
		return depthQuads;
	}
}
