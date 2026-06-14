package com.midgard.events.hud;

import java.util.ArrayList;
import java.util.List;

import com.midgard.Midgard;
import com.midgard.events.config.ModConfig;
import com.midgard.mining.CrystalNav;
import com.midgard.mining.MiningData;
import com.midgard.mining.MiningHud;
import com.midgard.mining.MiningWaypoints;
import com.midgard.render.MidgardText;
import com.midgard.render.UIRenderer;
import com.midgard.util.Fonts;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Navi-Ziel-Auswahl DIREKT im HUD (kein Popup mehr). Zeigt eine kompakte Liste
 * im HUD-Stil; die Zeilen sind anklickbar, sobald der Chat offen ist (dann gibt
 * es einen Mauszeiger). Position rechts am Bildschirm; ein-/ausschaltbar über
 * das HUD-Element "Navi" ({@link MiningHud#KEY_NAV}).
 */
public final class NaviHud {

	public static final NaviHud INSTANCE = new NaviHud();

	private static final int PANEL = 0xE6121218;
	private static final int BORDER = 0x33FFFFFF;
	private static final int HEADER = 0xFF57D8FF;
	private static final int ACCENT = 0xFFF2772F;
	private static final int TEXT = 0xFFF1F1F4;
	private static final int DIM = 0xFF8C8C97;
	private static final int CARD_HOVER = 0x99343444;
	private static final float FS = 8f;

	private record Entry(String label, int color, boolean clickable, Runnable action) {
	}

	private record Rect(int x1, int y1, int x2, int y2, Runnable action) {
		boolean contains(double mx, double my) {
			return mx >= x1 && mx <= x2 && my >= y1 && my <= y2;
		}
	}

	private final List<Rect> rects = new ArrayList<>();

	private NaviHud() {
	}

	private List<Entry> buildEntries() {
		List<Entry> e = new ArrayList<>();
		if (!MiningData.INSTANCE.onMiningIsland) {
			return e;
		}
		// Oben: Umschalter Pfad am Boden / durch die Luft (cfg.pathTeleport).
		boolean air = Midgard.config != null && Midgard.config.pathTeleport;
		e.add(new Entry(air ? "Pfad: ✈ Luft" : "Pfad: ⛏ Boden", HEADER, true, () -> {
			if (Midgard.config != null) {
				Midgard.config.pathTeleport = !Midgard.config.pathTeleport;
				Midgard.config.save();
			}
		}));
		e.add(new Entry("Fadenkreuz-Ziel", TEXT, true, NaviHud::targetLookedAt));
		if (MiningData.INSTANCE.onCrystalHollows) {
			// Mitte (funktioniert) + Amber-Crystal-Test (King Yolkar, Goblin Guard).
			e.add(navEntry("Crystal Nucleus"));
			e.add(new Entry("Amber Crystal", DIM, false, null)); // Überschrift
			e.add(navEntry("King Yolkar"));
			e.add(navEntry("Goblin Guard"));
		} else {
			boolean auto = !MiningWaypoints.hasManual();
			e.add(new Entry("Auto (nächste Commission)", auto ? ACCENT : TEXT, true, MiningWaypoints::clearManual));
			String cur = MiningWaypoints.hasManual() ? MiningWaypoints.manual().label() : null;
			for (MiningWaypoints.NavOption o : MiningWaypoints.dwarvenTargets()) {
				boolean active = o.name().equals(cur);
				e.add(new Entry(o.name(), active ? ACCENT : TEXT, true,
						() -> MiningWaypoints.setManual(o.x(), o.y(), o.z(), o.name())));
			}
		}
		return e;
	}

	/** Crystal-Hollows-Eintrag: orange = gewählt, weiß = gefunden, grau = noch suchen. */
	private Entry navEntry(String name) {
		boolean active = name.equals(CrystalNav.targetName());
		int col = active ? ACCENT : CrystalNav.isLearned(name) ? TEXT : DIM;
		return new Entry(name, col, true, () -> CrystalNav.setTarget(name));
	}

	public void render(DrawContext c) {
		rects.clear();
		ModConfig cfg = Midgard.config;
		if (cfg == null || !cfg.masterEnabled || !cfg.isElementEnabled(MiningHud.KEY_NAV)
				|| !MiningData.INSTANCE.onMiningIsland) {
			return;
		}
		List<Entry> entries = buildEntries();
		if (entries.isEmpty()) {
			return;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		int sw = mc.getWindow().getScaledWidth();
		int sh = mc.getWindow().getScaledHeight();

		int rowH = 11;
		int headH = 13;
		int pad = 5;
		int w = 110;
		// Breite an den längsten Eintrag anpassen (nur Name, kein Tag).
		for (Entry en : entries) {
			w = Math.max(w, txtW(en.label(), mc) + pad * 2 + 4);
		}
		w = Math.min(w, 190);
		int h = headH + entries.size() * rowH + pad;
		int x = cfg.hasGroupPos(MiningHud.KEY_NAV) ? cfg.groupX(MiningHud.KEY_NAV) : sw - w - 6;
		int y = cfg.hasGroupPos(MiningHud.KEY_NAV) ? cfg.groupY(MiningHud.KEY_NAV) : Math.max(40, sh / 2 - h / 2);
		x = Math.max(2, Math.min(x, sw - w - 2));
		y = Math.max(2, Math.min(y, sh - h - 2));

		UIRenderer.fillRoundedRect(c, x - 1, y - 1, w + 2, h + 2, 6, BORDER);
		UIRenderer.fillRoundedRect(c, x, y, w, h, 5, PANEL);
		txt(c, "Navi", x + pad, y + 4, HEADER, true, mc);

		double mx = mc.mouse.getX() * (double) sw / mc.getWindow().getWidth();
		double my = mc.mouse.getY() * (double) sh / mc.getWindow().getHeight();
		boolean cursor = mc.currentScreen != null; // Chat o.ä. offen -> Mauszeiger

		int ry = y + headH;
		for (Entry en : entries) {
			boolean hover = cursor && en.clickable() && mx >= x + 2 && mx <= x + w - 2 && my >= ry && my <= ry + rowH;
			if (hover) {
				UIRenderer.fillRoundedRect(c, x + 2, ry, w - 4, rowH, 3, CARD_HOVER);
			}
			// Überschrift (nicht klickbar) leicht eingerückt, sonst normal.
			int lx = en.clickable() ? x + pad : x + pad - 2;
			txt(c, en.label(), lx, ry + 2, en.color(), false, mc);
			if (en.clickable() && en.action() != null) {
				rects.add(new Rect(x + 2, ry, x + w - 2, ry + rowH, en.action()));
			}
			ry += rowH;
		}
	}

	/** Wird vom Chat-Klick-Handler aufgerufen; true = Klick verbraucht. */
	public boolean clickAt(double mx, double my) {
		for (Rect r : rects) {
			if (r.contains(mx, my)) {
				r.action().run();
				return true;
			}
		}
		return false;
	}

	/** Setzt das manuelle Navi-Ziel auf den Block, auf den der Spieler schaut. */
	private static void targetLookedAt() {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null) {
			return;
		}
		HitResult hr = mc.player.raycast(96, 0f, false);
		if (hr != null && hr.getType() == HitResult.Type.BLOCK) {
			BlockPos p = ((BlockHitResult) hr).getBlockPos();
			MiningWaypoints.setManual(p.getX() + 0.5, p.getY() + 1, p.getZ() + 0.5, "Fadenkreuz-Ziel");
		} else {
			Vec3d eye = mc.player.getEyePos();
			Vec3d dir = mc.player.getRotationVec(1f);
			MiningWaypoints.setManual(eye.x + dir.x * 30, eye.y + dir.y * 30, eye.z + dir.z * 30, "Fadenkreuz-Ziel");
		}
	}

	// ---- Text (HUD-Schrift mit Fallback) ----------------------------------

	private void txt(DrawContext c, String s, int x, int yTop, int color, boolean bold, MinecraftClient mc) {
		if (!MidgardText.draw(c, s, x, yTop, FS, color, bold)) {
			c.drawText(mc.textRenderer, bold ? Fonts.bold(s) : Fonts.regular(s), x, yTop, color, false);
		}
	}

	private int txtW(String s, MinecraftClient mc) {
		int w = MidgardText.width(s, FS, false);
		return w >= 0 ? w : mc.textRenderer.getWidth(Fonts.regular(s));
	}
}
