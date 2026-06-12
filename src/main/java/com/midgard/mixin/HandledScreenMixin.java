package com.midgard.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.midgard.util.TooltipScroll;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;

/**
 * Macht zu lange Item-Tooltips scrollbar: merkt sich das gehoverte Item und
 * fängt das Mausrad ab, solange der Tooltip nicht komplett auf den Schirm
 * passt (sonst läuft Vanilla-Verhalten normal weiter).
 */
@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin {

	@Shadow
	protected Slot focusedSlot;

	@Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;IIF)V", at = @At("TAIL"))
	private void midgard$trackHover(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		TooltipScroll.onHover(focusedSlot != null && focusedSlot.hasStack()
				? focusedSlot.getStack()
				: ItemStack.EMPTY);
	}

	@Inject(method = "mouseScrolled(DDDD)Z", at = @At("HEAD"), cancellable = true)
	private void midgard$scrollTooltip(double mouseX, double mouseY, double horizontalAmount,
			double verticalAmount, CallbackInfoReturnable<Boolean> cir) {
		if (focusedSlot != null && focusedSlot.hasStack() && TooltipScroll.isScrollable()) {
			TooltipScroll.onScroll(verticalAmount);
			cir.setReturnValue(true);
		}
	}
}
