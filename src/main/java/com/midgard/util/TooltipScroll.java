package com.midgard.util;

import org.joml.Vector2i;
import org.joml.Vector2ic;

import net.minecraft.item.ItemStack;

/**
 * Scrollbare Tooltips: Ist ein Tooltip höher als der Bildschirm (viele
 * Verzauberungen + Preiszeilen), kann man ihn mit dem Mausrad nach oben/unten
 * schieben. Der Versatz wird beim Wechsel auf ein anderes Item zurückgesetzt.
 *
 * <p>Verdrahtung: {@code HandledScreenMixin} füttert Hover + Mausrad,
 * {@code DrawContextMixin} wendet den Versatz auf die Tooltip-Position an.</p>
 */
public final class TooltipScroll {

	/** Pixel pro Mausrad-Raststufe. */
	private static final int STEP = 30;
	/** Abstand zum Bildschirmrand, der beim Klemmen eingehalten wird. */
	private static final int MARGIN = 4;

	private static int offset = 0;
	private static boolean scrollable = false;
	private static ItemStack lastStack = ItemStack.EMPTY;

	private TooltipScroll() {
	}

	/** Vom Screen-Mixin jeden Frame mit dem gerade gehoverten Item aufgerufen. */
	public static void onHover(ItemStack stack) {
		if (!ItemStack.areEqual(stack, lastStack)) {
			offset = 0;
			scrollable = false;
		}
		lastStack = stack;
	}

	/** true, wenn der zuletzt gezeichnete Tooltip nicht komplett auf den Schirm passt. */
	public static boolean isScrollable() {
		return scrollable;
	}

	/** Mausrad: hoch (+1) = Anfang zeigen, runter (-1) = Ende zeigen. */
	public static void onScroll(double verticalAmount) {
		offset += (int) Math.round(verticalAmount * STEP);
	}

	/**
	 * Wendet den Scroll-Versatz auf die vom Vanilla-Positioner berechnete
	 * Tooltip-Position an. Passt der Tooltip auf den Schirm, passiert nichts.
	 */
	public static Vector2ic adjust(Vector2ic pos, int screenW, int screenH, int w, int h) {
		if (h + 2 * MARGIN <= screenH) {
			scrollable = false;
			return pos;
		}
		scrollable = true;
		int base = pos.y();
		int top = MARGIN; // Anfang sichtbar
		int bottom = screenH - h - MARGIN; // Ende sichtbar (negativ)
		offset = Math.max(bottom - base, Math.min(top - base, offset));
		return new Vector2i(pos.x(), base + offset);
	}
}
