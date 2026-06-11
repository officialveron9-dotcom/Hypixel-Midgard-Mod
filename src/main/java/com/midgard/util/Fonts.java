package com.midgard.util;

import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Die gebündelten Roboto-Schriften (glatte TTF statt Pixelschrift).
 * "regular" = dünn/normal, "bold" = dick – für eine gut lesbare Misch-Optik.
 */
public final class Fonts {

	public static final Style REGULAR =
			Style.EMPTY.withFont(new StyleSpriteSource.Font(Identifier.of("midgard", "regular")));
	public static final Style BOLD =
			Style.EMPTY.withFont(new StyleSpriteSource.Font(Identifier.of("midgard", "bold")));

	private Fonts() {
	}

	public static MutableText regular(String text) {
		return Text.literal(text).setStyle(REGULAR);
	}

	public static MutableText bold(String text) {
		return Text.literal(text).setStyle(BOLD);
	}
}
