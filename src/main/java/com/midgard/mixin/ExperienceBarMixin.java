package com.midgard.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.midgard.bars.StatusBars;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.bar.ExperienceBar;
import net.minecraft.client.render.RenderTickCounter;

/** Blendet den Vanilla-XP-Balken (+ Level-Zahl) aus, solange die eigenen Leisten an sind. */
@Mixin(ExperienceBar.class)
public abstract class ExperienceBarMixin {

	@Inject(method = "renderBar", at = @At("HEAD"), cancellable = true)
	private void midgard$hideBar(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
		if (StatusBars.enabled()) {
			ci.cancel();
		}
	}

	@Inject(method = "renderAddons", at = @At("HEAD"), cancellable = true)
	private void midgard$hideAddons(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
		if (StatusBars.enabled()) {
			ci.cancel();
		}
	}
}
