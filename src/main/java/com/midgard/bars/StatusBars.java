package com.midgard.bars;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.midgard.Midgard;
import com.midgard.render.MidgardText;
import com.midgard.render.UIRenderer;
import com.midgard.util.Fonts;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;

/**
 * Eigene Statusleisten statt Vanilla-Herzen/XP-Balken: Leben LINKS, XP MITTE,
 * Mana RECHTS – alle gleich hoch, Werte jeweils IN der Leiste. DARÜBER eine
 * eigene Info-Zeile mit dem aktuellen Werkzeug und den restlichen
 * Actionbar-Werten (Defense, Drill Fuel ...). Die Hypixel-/Vanilla-Anzeigen
 * werden komplett ausgeblendet (Mixins + geleerte Actionbar), solange das
 * Feature an ist.
 */
public final class StatusBars {

	private static final Pattern HEALTH = Pattern.compile("([\\d,.]+)/([\\d,.]+)❤");
	private static final Pattern MANA = Pattern.compile("([\\d,.]+)/([\\d,.]+)✎");

	private static final int BG = 0xC8101016;
	private static final int BORDER = 0x66000000;
	private static final int HEALTH_COLOR = 0xFFE0443C;
	private static final int MANA_COLOR = 0xFF4FA8FF;
	private static final int XP_COLOR = 0xFF7EDC58;
	private static final int TEXT = 0xFFFFFFFF;
	private static final int TOOL_COLOR = 0xFFF2C94C;

	private static final long STALE_MS = 5_000;
	private static final float FONT = 7.5f;
	private static final float INFO_FONT = 10f;

	private static volatile long health = -1;
	private static volatile long healthMax = -1;
	private static volatile long mana = -1;
	private static volatile long manaMax = -1;
	private static volatile String extra = "";
	private static volatile long lastActionBarMs = 0;

	private StatusBars() {
	}

	/** Hypixel-Actionbar mitlesen (overlay=true Chat-Nachrichten). */
	public static void onActionBar(String message) {
		if (message == null) {
			return;
		}
		Matcher h = HEALTH.matcher(message);
		if (h.find()) {
			health = parse(h.group(1));
			healthMax = parse(h.group(2));
		}
		Matcher m = MANA.matcher(message);
		if (m.find()) {
			mana = parse(m.group(1));
			manaMax = parse(m.group(2));
		}
		String rest = stripActionBar(message);
		extra = rest == null ? "" : rest;
		lastActionBarMs = System.currentTimeMillis();
	}

	/**
	 * Entfernt Leben/Mana (zeigen unsere Leisten) aus der Actionbar; der Rest
	 * (Defense, Drill Fuel, Skill-XP ...) bleibt für unsere Info-Zeile. Liefert
	 * null, wenn nichts übrig bleibt.
	 */
	public static String stripActionBar(String message) {
		String s = message
				.replaceAll("[❤♥]?\\s*[\\d.,]+/[\\d.,]+[❤♥]", "")
				.replaceAll("[\\d.,]+/[\\d.,]+✎\\s*(Mana)?", "")
				.replaceAll("\\s{2,}", "   ")
				.trim();
		return s.isEmpty() ? null : s;
	}

	private static long parse(String s) {
		try {
			return Long.parseLong(s.replace(",", "").replace(".", ""));
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	public static boolean enabled() {
		return Midgard.config != null && Midgard.config.customBars;
	}

	public static void render(DrawContext context) {
		if (!enabled()) {
			return;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		ClientPlayerEntity p = mc.player;
		if (p == null || mc.options.hudHidden || p.isSpectator() || p.getAbilities().creativeMode) {
			return;
		}

		int screenW = context.getScaledWindowWidth();
		int screenH = context.getScaledWindowHeight();
		int centerX = screenW / 2;
		int totalW = 182; // Hotbar-Breite
		int left = centerX - totalW / 2;
		int barH = 10;
		int y = screenH - 22 - 5 - barH; // knapp über der Hotbar

		boolean fresh = System.currentTimeMillis() - lastActionBarMs < STALE_MS;

		// --- Info-Zeile(n) ÜBER den Leisten -------------------------------
		int infoH = MidgardText.capHeight(INFO_FONT, true);
		if (infoH <= 0) {
			infoH = 9;
		}
		int lineGap = 3;
		int infoY = y - lineGap - infoH;

		// Restliche Actionbar (Defense, Drill Fuel ...).
		if (fresh && !extra.isEmpty()) {
			infoText(context, extra, centerX, infoY, TEXT);
			infoY -= infoH + lineGap;
		}
		// Aktuelles Werkzeug in der Hand.
		ItemStack hand = p.getMainHandStack();
		if (hand != null && !hand.isEmpty()) {
			infoText(context, hand.getName().getString(), centerX, infoY, TOOL_COLOR);
		}

		// --- Drei gleich hohe Leisten -------------------------------------
		int sideW = 72;
		long hp = fresh && health >= 0 ? health : Math.round(Math.ceil(p.getHealth()));
		long hpMax = fresh && healthMax > 0 ? healthMax : Math.round(p.getMaxHealth());
		bar(context, left, y, sideW, barH, frac(hp, hpMax), HEALTH_COLOR,
				full(hp) + "/" + full(hpMax));

		// XP in der Mitte – gleiche Höhe, Level-Zahl innen.
		int midX = left + sideW + 3;
		int midW = totalW - 2 * (sideW + 3);
		bar(context, midX, y, midW, barH, p.experienceProgress, XP_COLOR,
				String.valueOf(p.experienceLevel));

		// Mana rechts (nur mit SkyBlock-Daten).
		if (fresh && mana >= 0 && manaMax > 0) {
			bar(context, left + totalW - sideW, y, sideW, barH, frac(mana, manaMax), MANA_COLOR,
					full(mana) + "/" + full(manaMax));
		}
	}

	private static float frac(long v, long max) {
		return max <= 0 ? 0f : Math.max(0f, Math.min(1f, v / (float) max));
	}

	private static void bar(DrawContext c, int x, int y, int w, int h, float fill, int color, String label) {
		UIRenderer.fillRoundedRect(c, x - 1, y - 1, w + 2, h + 2, 4, BORDER);
		UIRenderer.fillRoundedRect(c, x, y, w, h, 3, BG);
		int fw = Math.round(w * fill);
		if (fw > 1) {
			UIRenderer.fillRoundedRect(c, x, y, fw, h, 3, color);
		}
		if (!label.isEmpty()) {
			text(c, label, x + (w - textW(label)) / 2, y + (h - capH()) / 2, TEXT);
		}
	}

	/** Zentrierte Info-Zeile (größere Schrift) mit Schatten. */
	private static void infoText(DrawContext c, String s, int centerX, int yTop, int color) {
		int tw = MidgardText.width(s, INFO_FONT, true);
		if (tw < 0) {
			tw = MinecraftClient.getInstance().textRenderer.getWidth(Fonts.bold(s));
		}
		int x = centerX - tw / 2;
		if (!MidgardText.draw(c, s, x + 1, yTop + 1, INFO_FONT, 0xB0000000, true)
				| !MidgardText.draw(c, s, x, yTop, INFO_FONT, color, true)) {
			var tr = MinecraftClient.getInstance().textRenderer;
			c.drawText(tr, Fonts.bold(s), x, yTop, color, true);
		}
	}

	private static void text(DrawContext c, String s, int x, int yTop, int color) {
		if (!MidgardText.draw(c, s, x + 1, yTop + 1, FONT, 0x90000000, true)
				| !MidgardText.draw(c, s, x, yTop, FONT, color, true)) {
			var tr = MinecraftClient.getInstance().textRenderer;
			c.drawText(tr, Fonts.bold(s), x, yTop, color, true);
		}
	}

	private static int textW(String s) {
		int w = MidgardText.width(s, FONT, true);
		return w >= 0 ? w : MinecraftClient.getInstance().textRenderer.getWidth(Fonts.bold(s));
	}

	private static int capH() {
		int h = MidgardText.capHeight(FONT, true);
		return h > 0 ? h : 6;
	}

	/** Zahlenanzeige: voll oder gekürzt – zentral in Numbers (Auktion-Tab). */
	private static String full(long v) {
		return com.midgard.util.Numbers.format(v);
	}
}
