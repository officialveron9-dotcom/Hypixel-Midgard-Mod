package com.midgard.events.gui;

import java.util.ArrayList;
import java.util.List;

import com.midgard.Midgard;
import com.midgard.events.config.ModConfig;
import com.midgard.events.hud.EventHud;
import com.midgard.events.hud.EventHud.GroupRect;
import com.midgard.events.hud.EventHud.HudGroup;
import com.midgard.events.hud.HudElements;
import com.midgard.util.Fonts;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * HUD bearbeiten. Schritt 1: Standort wählen (Garten / Mining / Überall).
 * Schritt 2: alle HUD-Elemente dieses Standorts – per Auge-Icon an/aus
 * (deaktivierte sind ausgegraut), per Welt/Map-Icon überall-oder-nur-hier,
 * ziehen zum Verschieben, Mausrad für die Größe, Doppelklick = Standard.
 * Unten eine Legende, die die Icons erklärt.
 */
public class HudPositionScreen extends Screen {

	private static final int PANEL = 0xF2121419;
	private static final int ACCENT = 0xFFF2772F;
	private static final int TEXT = 0xFFF1F1F4;
	private static final int TEXT_DIM = 0xFF8C8C97;
	private static final int BORDER = 0x33FFFFFF;
	private static final int CARD = 0xFF1E1E26;
	private static final int CARD_HOVER = 0xFF2C2C3A;
	private static final int ON = 0xFF35B36A;
	private static final int OFF = 0xFF60606C;
	private static final int HOVER_OUTLINE = 0x50FFFFFF;

	private static final int EDGE_MARGIN = 2;
	private static final int SNAP_DIST = 14;

	private record Clickable(int x1, int y1, int x2, int y2, Runnable action) {
		boolean contains(double mx, double my) {
			return mx >= x1 && mx <= x2 && my >= y1 && my <= y2;
		}
	}

	private final Screen parent;
	private final ModConfig cfg = Midgard.config;
	private final List<Clickable> clickables = new ArrayList<>();

	private HudElements.Location activeLoc = null;
	private String selected = null;
	private boolean dragging = false;
	private boolean dragMoved = false;
	private int dragOffsetX, dragOffsetY;
	private int doneX, doneY, doneW, doneH;

	public HudPositionScreen(Screen parent) {
		super(Text.literal("HUD bearbeiten"));
		this.parent = parent;
	}

	// ---- Layout des aktuellen Standorts -----------------------------------

	private List<HudGroup> groups() {
		return EventHud.INSTANCE.editorGroups(cfg, activeLoc);
	}

	private List<GroupRect> layout() {
		return EventHud.INSTANCE.layout(cfg, groups());
	}

	private GroupRect rectOf(String key) {
		for (GroupRect r : layout()) {
			if (r.key().equals(key)) {
				return r;
			}
		}
		return null;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		clickables.clear();
		context.fill(0, 0, this.width, this.height, 0xAA000000);

		if (activeLoc == null) {
			drawLocationPicker(context, mouseX, mouseY);
		} else {
			drawEditor(context, mouseX, mouseY);
		}
		drawDone(context, mouseX, mouseY);
	}

	// ---- Schritt 1: Standort wählen ---------------------------------------

	private void drawLocationPicker(DrawContext context, int mouseX, int mouseY) {
		String title = "Standort wählen";
		txtScaled(context, title, (this.width - txtW(title, true) * 2) / 2, this.height / 2 - 70, TEXT, true, 2f);

		HudElements.Location[] locs = HudElements.Location.values();
		int bw = 220, bh = 30, gap = 8;
		int y = this.height / 2 - 30;
		for (HudElements.Location loc : locs) {
			int x = (this.width - bw) / 2;
			boolean hover = mouseX >= x && mouseX <= x + bw && mouseY >= y && mouseY <= y + bh;
			sprite(context, x, y, bw, bh, hover ? CARD_HOVER : CARD);
			sprite(context, x, y + 6, 3, bh - 12, ACCENT);
			txtVC(context, loc.label, x + 14, y, bh, TEXT, true);
			int cnt = HudElements.forLocation(loc).size();
			String info = cnt + (cnt == 1 ? " Element" : " Elemente");
			txtVC(context, info, x + bw - 14 - txtW(info, false), y, bh, TEXT_DIM, false);
			clickables.add(new Clickable(x, y, x + bw, y + bh, () -> {
				activeLoc = loc;
				selected = null;
			}));
			y += bh + gap;
		}

		String hint = "Tipp: Standortelemente erscheinen normal nur an ihrem Ort – mit dem Welt-Icon überall.";
		txt(context, hint, (this.width - txtW(hint, false)) / 2, y + 10, TEXT_DIM, false);
	}

	// ---- Schritt 2: Elemente eines Standorts ------------------------------

	private void drawEditor(DrawContext context, int mouseX, int mouseY) {
		List<HudGroup> groups = groups();
		List<GroupRect> rects = layout();

		// HUD-Elemente (deaktivierte ausgegraut).
		EventHud.INSTANCE.renderEditor(context, cfg, groups, rects);

		// Rahmen + Icons je Element.
		GroupRect hovered = hit(rects, mouseX, mouseY);
		for (GroupRect r : rects) {
			boolean sel = r.key().equals(selected);
			if (sel) {
				float pulse = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 220.0));
				outline(context, r, 2, withAlpha(ACCENT, 150 + (int) (pulse * 105)));
			} else if (hovered != null && r.key().equals(hovered.key()) && !dragging) {
				outline(context, r, 2, HOVER_OUTLINE);
			}
			drawElementIcons(context, mouseX, mouseY, r);
		}

		// Kopfzeile: Zurück + Standortname.
		int backW = 70, backH = 20;
		int bx = 12, by = 12;
		boolean bh = mouseX >= bx && mouseX <= bx + backW && mouseY >= by && mouseY <= by + backH;
		sprite(context, bx, by, backW, backH, bh ? CARD_HOVER : CARD);
		txtVC(context, "Zurück", bx + (backW - txtW("Zurück", true)) / 2, by, backH, TEXT, true);
		clickables.add(new Clickable(bx, by, bx + backW, by + backH, () -> {
			activeLoc = null;
			selected = null;
		}));
		txtScaled(context, activeLoc.label, bx + backW + 16, by - 1, TEXT, true, 1.6f);

		drawLegend(context);
	}

	/** Auge-Icon (an/aus) und – außer bei Events – Welt/Map-Icon je Element. */
	private void drawElementIcons(DrawContext context, int mouseX, int mouseY, GroupRect r) {
		boolean enabled = cfg.isElementEnabled(r.key());
		int sz = 12;
		int iy = r.y() - sz - 2;
		if (iy < 2) {
			iy = r.y() + 2; // oben kein Platz -> in die Box
		}
		int ix = r.x();

		// Auge: gefüllter Kreis (an) / hohler Kreis (aus).
		boolean h1 = mouseX >= ix && mouseX <= ix + sz && mouseY >= iy && mouseY <= iy + sz;
		sprite(context, ix, iy, sz, sz, h1 ? CARD_HOVER : CARD);
		dot(context, ix + sz / 2, iy + sz / 2, enabled, enabled ? ON : OFF);
		clickables.add(new Clickable(ix, iy, ix + sz, iy + sz, () -> {
			cfg.setElementEnabled(r.key(), !cfg.isElementEnabled(r.key()));
			cfg.save();
		}));

		// Welt/Map nur für Standort-Elemente (Events sind immer überall).
		if (activeLoc != HudElements.Location.GLOBAL) {
			int ix2 = ix + sz + 3;
			boolean global = cfg.isElementGlobal(r.key());
			boolean h2 = mouseX >= ix2 && mouseX <= ix2 + sz && mouseY >= iy && mouseY <= iy + sz;
			sprite(context, ix2, iy, sz, sz, h2 ? CARD_HOVER : CARD);
			txtVC(context, global ? "W" : "M", ix2 + (sz - txtW(global ? "W" : "M", true)) / 2, iy, sz,
					global ? ACCENT : TEXT_DIM, true);
			clickables.add(new Clickable(ix2, iy, ix2 + sz, iy + sz, () -> {
				cfg.setElementGlobal(r.key(), !cfg.isElementGlobal(r.key()));
				cfg.save();
			}));
		}
	}

	private void drawLegend(DrawContext context) {
		int x = 12;
		int y = this.height - 58;
		int w = 250, h = 46;
		sprite(context, x, y, w, h, PANEL);
		sprite(context, x, y, w, 1, BORDER);
		txt(context, "Legende", x + 8, y + 6, TEXT, true);
		dot(context, x + 13, y + 23, true, ON);
		txt(context, "Element an", x + 24, y + 19, TEXT_DIM, false);
		dot(context, x + 13, y + 35, false, OFF);
		txt(context, "Element aus (ausgegraut)", x + 24, y + 31, TEXT_DIM, false);
		txt(context, "M / W", x + 150, y + 19, ACCENT, true);
		txt(context, "nur hier / überall", x + 150, y + 31, TEXT_DIM, false);
	}

	// ---- Gemeinsamer Fertig-Button ----------------------------------------

	private void drawDone(DrawContext context, int mouseX, int mouseY) {
		doneW = 88;
		doneH = 22;
		doneX = this.width - doneW - 12;
		doneY = this.height - doneH - 12;
		boolean hover = mouseX >= doneX && mouseX <= doneX + doneW && mouseY >= doneY && mouseY <= doneY + doneH;
		sprite(context, doneX - 1, doneY - 1, doneW + 2, doneH + 2, BORDER);
		sprite(context, doneX, doneY, doneW, doneH, hover ? 0xFFFF8A45 : ACCENT);
		txt(context, "Fertig", doneX + (doneW - txtW("Fertig", true)) / 2,
				doneY + (doneH - capH() + 1) / 2, 0xFF15151A, true);
		clickables.add(new Clickable(doneX, doneY, doneX + doneW, doneY + doneH, this::close));
	}

	// ---- Hilfen -----------------------------------------------------------

	private void dot(DrawContext c, int cx, int cy, boolean filled, int color) {
		if (filled) {
			c.fill(cx - 3, cy - 2, cx + 3, cy + 2, color);
			c.fill(cx - 2, cy - 3, cx + 2, cy + 3, color);
		} else {
			c.fill(cx - 3, cy - 2, cx + 3, cy - 1, color);
			c.fill(cx - 3, cy + 1, cx + 3, cy + 2, color);
			c.fill(cx - 3, cy - 2, cx - 2, cy + 2, color);
			c.fill(cx + 2, cy - 2, cx + 3, cy + 2, color);
		}
	}

	private void outline(DrawContext c, GroupRect r, int gap, int color) {
		int x1 = r.x() - gap, y1 = r.y() - gap, x2 = r.x() + r.w() + gap, y2 = r.y() + r.h() + gap;
		c.fill(x1, y1, x2, y1 + 1, color);
		c.fill(x1, y2 - 1, x2, y2, color);
		c.fill(x1, y1, x1 + 1, y2, color);
		c.fill(x2 - 1, y1, x2, y2, color);
	}

	private static int withAlpha(int color, int alpha) {
		return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0xFFFFFF);
	}

	private GroupRect hit(List<GroupRect> rects, double mx, double my) {
		for (int i = rects.size() - 1; i >= 0; i--) {
			if (rects.get(i).contains(mx, my)) {
				return rects.get(i);
			}
		}
		return null;
	}

	private void txt(DrawContext c, String s, int x, int yTop, int color, boolean bold) {
		if (!com.midgard.render.MidgardText.draw(c, s, x, yTop, 9f, color, bold)) {
			c.drawText(textRenderer, bold ? Fonts.bold(s) : Fonts.regular(s), x, yTop, color, false);
		}
	}

	private void txtVC(DrawContext c, String s, int x, int yTop, int boxH, int color, boolean bold) {
		txt(c, s, x, yTop + (boxH - capH()) / 2, color, bold);
	}

	private void txtScaled(DrawContext c, String s, int x, int yTop, int color, boolean bold, float scale) {
		var ms = c.getMatrices();
		ms.pushMatrix();
		ms.translate(x, yTop);
		ms.scale(scale, scale);
		txt(c, s, 0, 0, color, bold);
		ms.popMatrix();
	}

	private int txtW(String s, boolean bold) {
		int w = com.midgard.render.MidgardText.width(s, 9f, bold);
		return w >= 0 ? w : textRenderer.getWidth(bold ? Fonts.bold(s) : Fonts.regular(s));
	}

	private int capH() {
		int h = com.midgard.render.MidgardText.capHeight(9f, true);
		return h > 0 ? h : 7;
	}

	private void sprite(DrawContext c, int x, int y, int w, int h, int color) {
		com.midgard.render.UIRenderer.fillRoundedRect(c, x, y, w, h, 5, color);
	}

	// ---- Maus -------------------------------------------------------------

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		double mx = click.x();
		double my = click.y();
		for (Clickable c : clickables) {
			if (c.contains(mx, my)) {
				c.action().run();
				return true;
			}
		}
		if (activeLoc == null) {
			return super.mouseClicked(click, doubled);
		}
		GroupRect r = hit(layout(), mx, my);
		if (r != null) {
			selected = r.key();
			if (doubled) {
				cfg.setGroupScale(selected, 1f);
				cfg.clearGroupPos(selected);
				cfg.save();
				dragging = false;
				return true;
			}
			dragging = true;
			dragMoved = false;
			dragOffsetX = (int) (mx - r.x());
			dragOffsetY = (int) (my - r.y());
			return true;
		}
		selected = null;
		return super.mouseClicked(click, doubled);
	}

	@Override
	public boolean mouseDragged(Click click, double offsetX, double offsetY) {
		if (dragging && selected != null) {
			GroupRect r = rectOf(selected);
			if (r == null) {
				return true;
			}
			dragMoved = true;
			int nx = clamp((int) (click.x() - dragOffsetX), 0, this.width - 10);
			int ny = clamp((int) (click.y() - dragOffsetY), 0, this.height - 10);
			if (nx <= SNAP_DIST) {
				nx = EDGE_MARGIN;
			} else if (nx + r.w() >= this.width - SNAP_DIST) {
				nx = this.width - r.w() - EDGE_MARGIN;
			}
			if (ny <= SNAP_DIST) {
				ny = EDGE_MARGIN;
			} else if (ny + r.h() >= this.height - SNAP_DIST) {
				ny = this.height - r.h() - EDGE_MARGIN;
			}
			if (!collides(nx, ny, r)) {
				cfg.setGroupPos(selected, nx, ny);
			} else if (!collides(nx, r.y(), r)) {
				cfg.setGroupPos(selected, nx, r.y());
			} else if (!collides(r.x(), ny, r)) {
				cfg.setGroupPos(selected, r.x(), ny);
			}
			return true;
		}
		return super.mouseDragged(click, offsetX, offsetY);
	}

	@Override
	public boolean mouseReleased(Click click) {
		dragging = false;
		return super.mouseReleased(click);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (activeLoc != null && selected != null && verticalAmount != 0) {
			cfg.setGroupScale(selected, cfg.groupScale(selected) + (verticalAmount > 0 ? 0.05f : -0.05f));
			cfg.save();
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	private static int clamp(int v, int min, int max) {
		return Math.max(min, Math.min(max, v));
	}

	private boolean collides(int nx, int ny, GroupRect self) {
		int gap = 2;
		for (GroupRect o : layout()) {
			if (o.key().equals(selected)) {
				continue;
			}
			boolean apart = nx + self.w() + gap <= o.x() || o.x() + o.w() + gap <= nx
					|| ny + self.h() + gap <= o.y() || o.y() + o.h() + gap <= ny;
			if (!apart) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
	}

	@Override
	public void close() {
		cfg.save();
		if (client != null) {
			client.setScreen(parent);
		}
	}
}
