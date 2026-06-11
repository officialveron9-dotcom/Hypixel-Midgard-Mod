package com.midgard.events.gui;

import java.util.ArrayList;
import java.util.List;

import com.midgard.Midgard;
import com.midgard.events.config.ModConfig;
import com.midgard.events.event.EventDisplay;
import com.midgard.events.event.EventManager;
import com.midgard.events.event.EventType;
import com.midgard.events.hud.EventHud;
import com.midgard.util.Fonts;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** "HUD bearbeiten": nur Verschieben des HUD per Maus (mit Andocken an den Rändern). */
public class HudPositionScreen extends Screen {

	private static final int PANEL = 0xF2121419;
	private static final int ACCENT = 0xFFF2772F;
	private static final int TEXT = 0xFFF1F1F4;
	private static final int TEXT_DIM = 0xFF8C8C97;
	private static final int BORDER = 0x33FFFFFF;

	private record Clickable(int x1, int y1, int x2, int y2, Runnable action) {
		boolean contains(double mx, double my) {
			return mx >= x1 && mx <= x2 && my >= y1 && my <= y2;
		}
	}

	private final Screen parent;
	private final ModConfig cfg = Midgard.config;
	private final List<Clickable> clickables = new ArrayList<>();

	private boolean dragging = false;
	private int dragOffsetX;
	private int dragOffsetY;
	private int panelX, panelY, panelW, panelH;

	public HudPositionScreen(Screen parent) {
		super(Text.literal("HUD bearbeiten"));
		this.parent = parent;
	}

	private List<EventDisplay> previewEvents() {
		List<EventDisplay> live = EventManager.INSTANCE.get();
		if (!live.isEmpty()) {
			return live; // bereits nach "aktiviert" gefiltert
		}
		// Beispiel-Vorschau – nur Events zeigen, die auch eingeschaltet sind.
		List<EventDisplay> sample = new ArrayList<>();
		sample.add(new EventDisplay(EventType.JACOB_CONTEST, "Jacob's Contest", true, 740, List.of("Wheat", "Carrot", "Pumpkin")));
		sample.add(new EventDisplay(EventType.JACOB_CONTEST, "Jacob's Contest", false, 5400, List.of("Melon", "Cactus", "Mushroom")));
		sample.add(new EventDisplay(EventType.SPOOKY_FESTIVAL, "Spooky Festival", false, 86400, null));
		sample.removeIf(d -> !cfg.isEventEnabled(d.type));
		return sample;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		clickables.clear();
		context.fill(0, 0, this.width, this.height, 0xAA000000);

		// HUD-Vorschau (verschiebbar).
		EventHud.INSTANCE.renderPreview(context, previewEvents());

		drawPanel(context, mouseX, mouseY);
	}

	private void drawPanel(DrawContext context, int mouseX, int mouseY) {
		panelW = 430;
		panelH = 48;
		panelX = (this.width - panelW) / 2;
		panelY = this.height - panelH - 14;

		sprite(context, panelX - 1, panelY - 1, panelW + 2, panelH + 2, BORDER);
		sprite(context, panelX, panelY, panelW, panelH, PANEL);

		int pad = 14;
		txt(context, "HUD bearbeiten", panelX + pad, panelY + 9, TEXT, true);

		int bw = 66;
		int bh = 18;
		int bx = panelX + panelW - pad - bw;
		int by = panelY + 7;
		sprite(context, bx, by, bw, bh, ACCENT);
		txt(context, "Fertig", bx + (bw - txtW("Fertig", true)) / 2, by + (bh - capH() + 1) / 2, 0xFF15151A, true);
		clickables.add(new Clickable(bx, by, bx + bw, by + bh, this::close));

		txt(context, "Ziehen zum Verschieben  -  dockt an den Rändern an  -  ESC schließt",
				panelX + pad, panelY + 28, TEXT_DIM, false);
	}

	// ---- Bausteine (scharfe MidgardText-Engine) ---------------------------

	private void txt(DrawContext c, String s, int x, int yTop, int color, boolean bold) {
		if (!com.midgard.render.MidgardText.draw(c, s, x, yTop, 9f, color, bold)) {
			c.drawText(textRenderer, bold ? Fonts.bold(s) : Fonts.regular(s), x, yTop, color, false);
		}
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
		com.midgard.render.UIRenderer.fillRoundedRect(c, x, y, w, h, 7, color);
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
		// Sonst: HUD anfassen zum Verschieben (nur außerhalb des Panels).
		boolean inPanel = mx >= panelX && mx <= panelX + panelW && my >= panelY && my <= panelY + panelH;
		if (!inPanel) {
			List<EventDisplay> events = previewEvents();
			int w = EventHud.INSTANCE.approxWidth(events);
			int h = EventHud.INSTANCE.approxHeight(events);
			if (mx >= cfg.hudX - 3 && mx <= cfg.hudX + w && my >= cfg.hudY - 3 && my <= cfg.hudY + h) {
				dragging = true;
				dragOffsetX = (int) (mx - cfg.hudX);
				dragOffsetY = (int) (my - cfg.hudY);
				return true;
			}
		}
		return super.mouseClicked(click, doubled);
	}

	@Override
	public boolean mouseDragged(Click click, double offsetX, double offsetY) {
		if (dragging) {
			int nx = clamp((int) (click.x() - dragOffsetX), 0, this.width - 10);
			int ny = clamp((int) (click.y() - dragOffsetY), 0, this.height - 10);

			// Automatisches Andocken an die Ränder (magnetisch).
			List<EventDisplay> events = previewEvents();
			int w = EventHud.INSTANCE.approxWidth(events);
			int h = EventHud.INSTANCE.approxHeight(events);
			int margin = 6;
			int snap = 16;
			if (nx <= snap) {
				nx = margin;
			} else if (nx + w >= this.width - snap) {
				nx = this.width - w - margin;
			}
			if (ny <= snap) {
				ny = margin;
			} else if (ny + h >= this.height - snap) {
				ny = this.height - h - margin;
			}

			cfg.hudX = nx;
			cfg.hudY = ny;
			return true;
		}
		return super.mouseDragged(click, offsetX, offsetY);
	}

	@Override
	public boolean mouseReleased(Click click) {
		dragging = false;
		return super.mouseReleased(click);
	}

	private static int clamp(int v, int min, int max) {
		return Math.max(min, Math.min(max, v));
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		// Eigene Verdunkelung in render().
	}

	@Override
	public void close() {
		cfg.save();
		if (client != null) {
			client.setScreen(parent);
		}
	}
}
