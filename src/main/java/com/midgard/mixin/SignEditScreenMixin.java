package com.midgard.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.midgard.auction.AuctionSearchHook;

import net.minecraft.client.gui.screen.ingame.AbstractSignEditScreen;

/**
 * Verhindert, dass das Hypixel-Such-SCHILD beim Ersetzen durch unser eigenes
 * Auktions-Menü automatisch ein (leeres) Sign-Update an den Server schickt –
 * sonst öffnet Hypixel das Schild sofort wieder (Endlosschleife). Das Senden
 * wird NUR unterdrückt, solange unser Hook umschaltet; die echte Suche schickt
 * unser Menü selbst als Packet.
 */
@Mixin(AbstractSignEditScreen.class)
public abstract class SignEditScreenMixin {

	@Inject(method = "removed", at = @At("HEAD"), cancellable = true)
	private void midgard$suppressAutoSend(CallbackInfo ci) {
		if (AuctionSearchHook.suppressSignSend) {
			ci.cancel();
		}
	}
}
