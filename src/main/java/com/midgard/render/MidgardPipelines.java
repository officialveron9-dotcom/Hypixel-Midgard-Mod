package com.midgard.render;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

/**
 * Eigene Render-Pipelines mit Midgards Fragment-Shader (SDF-Rundung). Sie
 * werden über Minecrafts {@code DrawContext} eingereicht – dadurch sind sie
 * sichtbar (rohes OpenGL war es nicht) und nutzen trotzdem unseren eigenen
 * Shader. Pro Eckradius eine gecachte Pipeline (Radius als Shader-Define).
 */
public final class MidgardPipelines {

	private static final Map<Integer, RenderPipeline> ROUND_CACHE = new HashMap<>();
	private static RenderPipeline textPipeline;

	private MidgardPipelines() {
	}

	/** Pipeline für SDF-Text (eigener Text-Shader). */
	public static RenderPipeline text() {
		if (textPipeline == null) {
			textPipeline = RenderPipeline.builder()
					.withLocation(Identifier.of("midgard", "pipeline/text"))
					.withVertexShader(Identifier.of("minecraft", "core/position_tex_color"))
					.withFragmentShader(Identifier.of("midgard", "core/midgard_text"))
					.withSampler("Sampler0")
					.withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
					.withUniform("Projection", UniformType.UNIFORM_BUFFER)
					.withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
					.withBlend(BlendFunction.TRANSLUCENT)
					.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
					.withCull(false)
					.build();
		}
		return textPipeline;
	}

	public static RenderPipeline round(int radius) {
		return ROUND_CACHE.computeIfAbsent(radius, r -> RenderPipeline.builder()
				.withLocation(Identifier.of("midgard", "pipeline/round_" + r))
				.withVertexShader(Identifier.of("minecraft", "core/position_tex_color"))
				.withFragmentShader(Identifier.of("midgard", "core/midgard_round"))
				.withSampler("Sampler0")
				.withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
				.withUniform("Projection", UniformType.UNIFORM_BUFFER)
				.withShaderDefine("RADIUS", (float) r)
				.withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
				.withBlend(BlendFunction.TRANSLUCENT)
				.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
				.withCull(false)
				.build());
	}
}
