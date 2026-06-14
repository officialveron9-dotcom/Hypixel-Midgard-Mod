package com.midgard.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.midgard.bars.StatusBars;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.bar.Bar;

/** Blendet die Vanilla-XP-Level-Zahl aus, solange die eigenen Leisten an sind. */
@Mixin(Bar.class)
public interface BarMixin {

	@Inject(method = "drawExperienceLevel(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/font/TextRenderer;I)V",
			at = @At("HEAD"), cancellable = true)
	private static void midgard$hideXpLevel(DrawContext context, TextRenderer textRenderer, int level, CallbackInfo ci) {
		if (StatusBars.enabled()) {
			ci.cancel();
		}
	}
}
