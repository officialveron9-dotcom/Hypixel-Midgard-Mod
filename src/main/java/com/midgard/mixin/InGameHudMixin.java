package com.midgard.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.midgard.bars.StatusBars;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;

/** Blendet die Vanilla-Statusleisten aus, solange die eigenen Leisten an sind. */
@Mixin(InGameHud.class)
public abstract class InGameHudMixin {

	@Inject(method = "renderStatusBars(Lnet/minecraft/client/gui/DrawContext;)V",
			at = @At("HEAD"), cancellable = true)
	private void midgard$hideStatusBars(DrawContext context, CallbackInfo ci) {
		if (StatusBars.enabled()) {
			ci.cancel();
		}
	}
}
