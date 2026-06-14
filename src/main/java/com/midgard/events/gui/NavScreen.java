package com.midgard.events.gui;

import java.util.ArrayList;
import java.util.List;

import com.midgard.mining.CrystalNav;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Crystal-Hollows-Navi-Liste: zeigt alle Orte. Bereits entdeckte (oder der
 * feste Nucleus) sind anklickbar und starten die Navigation dorthin; noch
 * nicht entdeckte sind ausgegraut ("erst betreten"). Unten ein Knopf, um die
 * laufende Navigation abzubrechen.
 */
public class NavScreen extends Screen {

	private static final int PANEL = 0xF2121419;
	private static final int ACCENT = 0xFFD06BFF;
	private static final int TEXT = 0xFFF1F1F4;
	private static final int DIM = 0xFF6E6E78;
	private static final int CARD = 0xFF1E1E26;
	private static final int CARD_HOVER = 0xFF2C2C3A;
	private static final int BORDER = 0x33FFFFFF;
	private static final int RED = 0xFFE0443C;

	private record Clickable(int x1, int y1, int x2, int y2, Runnable action) {
		boolean contains(double mx, double my) {
			return mx >= x1 && mx <= x2 && my >= y1 && my <= y2;
		}
	}

	private final Screen parent;
	private final List<Clickable> clickables = new ArrayList<>();

	public NavScreen(Screen parent) {
		super(Text.literal("Crystal Hollows Navi"));
		this.parent = parent;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		clickables.clear();
		context.fill(0, 0, this.width, this.height, 0xAA000000);

		int w = 240;
		int rowH = 20;
		int n = CrystalNav.LOCATIONS.size();
		int headH = 26;
		int footH = 30;
		int h = headH + n * rowH + footH;
		int x = (this.width - w) / 2;
		int y = (this.height - h) / 2;

		context.fill(x - 1, y - 1, x + w + 1, y + h + 1, BORDER);
		context.fill(x, y, x + w, y + h, PANEL);
		context.drawText(textRenderer, "Crystal Hollows – Ziel wählen", x + 12, y + 9, TEXT, false);

		int ry = y + headH;
		for (String loc : CrystalNav.LOCATIONS) {
			boolean known = CrystalNav.isLearned(loc);
			boolean isTarget = loc.equals(CrystalNav.targetName());
			boolean hover = mouseX >= x && mouseX <= x + w && mouseY >= ry && mouseY <= ry + rowH;
			if (known && hover) {
				context.fill(x, ry, x + w, ry + rowH, CARD_HOVER);
			} else if (isTarget) {
				context.fill(x, ry, x + w, ry + rowH, CARD);
			}
			int col = !known ? DIM : isTarget ? ACCENT : TEXT;
			context.drawText(textRenderer, loc, x + 12, ry + (rowH - 8) / 2, col, false);
			String tag = !known ? "erst betreten" : isTarget ? "aktiv" : "navigieren";
			int tagW = textRenderer.getWidth(tag);
			context.drawText(textRenderer, tag, x + w - 12 - tagW, ry + (rowH - 8) / 2,
					!known ? DIM : ACCENT, false);
			if (known) {
				clickables.add(new Clickable(x, ry, x + w, ry + rowH, () -> {
					CrystalNav.setTarget(loc);
					close();
				}));
			}
			ry += rowH;
		}

		// Abbrechen-Knopf.
		int bw = w - 24;
		int bh = 20;
		int bx = x + 12;
		int by = y + h - footH + 5;
		boolean chover = mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh;
		context.fill(bx, by, bx + bw, by + bh, chover ? 0xFF7A2A26 : 0xFF40201E);
		String cancel = CrystalNav.hasTarget() ? "Navigation abbrechen (" + CrystalNav.targetName() + ")"
				: "Keine Navigation aktiv";
		int cw = textRenderer.getWidth(cancel);
		context.drawText(textRenderer, cancel, bx + (bw - cw) / 2, by + (bh - 8) / 2,
				CrystalNav.hasTarget() ? RED : DIM, false);
		if (CrystalNav.hasTarget()) {
			clickables.add(new Clickable(bx, by, bx + bw, by + bh, () -> {
				CrystalNav.cancel();
				close();
			}));
		}
	}

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		for (Clickable c : clickables) {
			if (c.contains(click.x(), click.y())) {
				c.action().run();
				return true;
			}
		}
		return super.mouseClicked(click, doubled);
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
	}

	@Override
	public void close() {
		if (client != null) {
			client.setScreen(parent);
		}
	}
}
