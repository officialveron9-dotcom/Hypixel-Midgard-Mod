package com.midgard.mixin;

import org.joml.Vector2ic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.midgard.util.TooltipScroll;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.TooltipPositioner;

/** Wendet den Tooltip-Scroll-Versatz auf die berechnete Position an. */
@Mixin(DrawContext.class)
public abstract class DrawContextMixin {

	@Redirect(
			method = "drawTooltipImmediately",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/gui/tooltip/TooltipPositioner;getPosition(IIIIII)Lorg/joml/Vector2ic;"))
	private Vector2ic midgard$scrollTooltip(TooltipPositioner positioner,
			int screenW, int screenH, int x, int y, int w, int h) {
		Vector2ic pos = positioner.getPosition(screenW, screenH, x, y, w, h);
		return TooltipScroll.adjust(pos, screenW, screenH, w, h);
	}
}
