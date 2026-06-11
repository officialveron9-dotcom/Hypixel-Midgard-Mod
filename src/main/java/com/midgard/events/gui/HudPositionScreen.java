package com.midgard.events.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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

/** "HUD bearbeiten": Verschieben per Maus + Größe, Icon-Größe, Hintergrund, Presets. */
public class HudPositionScreen extends Screen {

	private static final int PANEL = 0xF2121419;
	private static final int CARD = 0xFF1F1F28;
	private static final int ACCENT = 0xFFF2772F;
	private static final int TEXT = 0xFFF1F1F4;
	private static final int TEXT_DIM = 0xFF8C8C97;
	private static final int BTN_BG = 0xFF2E2E38;
	private static final int TOGGLE_ON = 0xFF35B36A;
	private static final int TOGGLE_OFF = 0xFF3C3C47;
	private static final int KNOB = 0xFFF7F7F9;
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
			return live;
		}
		List<EventDisplay> sample = new ArrayList<>();
		sample.add(new EventDisplay(EventType.JACOB_CONTEST, "Jacob's Contest", true, 740, List.of("Wheat", "Carrot", "Pumpkin")));
		sample.add(new EventDisplay(EventType.JACOB_CONTEST, "Jacob's Contest", false, 5400, List.of("Melon", "Cactus", "Mushroom")));
		sample.add(new EventDisplay(EventType.SPOOKY_FESTIVAL, "Spooky Festival", false, 86400, null));
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
		panelH = 124;
		panelX = (this.width - panelW) / 2;
		panelY = this.height - panelH - 14;

		sprite(context, panelX - 1, panelY - 1, panelW + 2, panelH + 2, BORDER);
		sprite(context, panelX, panelY, panelW, panelH, PANEL);

		int pad = 14;
		context.drawText(textRenderer, Fonts.bold("HUD bearbeiten"), panelX + pad, panelY + 10, TEXT, false);
		context.drawText(textRenderer, Fonts.regular("Ziehen zum Verschieben  •  dockt an den Rändern an  •  ESC schließt"),
				panelX + pad, panelY + 24, TEXT_DIM, false);

		// Reihe 1: Größe + Icon-Größe.
		int rowY = panelY + 42;
		stepper(context, panelX + pad, rowY, "Größe", String.format(Locale.ROOT, "%.1fx", cfg.hudScale),
				() -> {
					cfg.hudScale = clampF(cfg.hudScale - 0.1f, 0.5f, 2.5f);
					cfg.save();
				},
				() -> {
					cfg.hudScale = clampF(cfg.hudScale + 0.1f, 0.5f, 2.5f);
					cfg.save();
				});
		stepper(context, panelX + panelW / 2 + 6, rowY, "Icon-Größe", String.format(Locale.ROOT, "%.1fx", cfg.hudIconScale),
				() -> {
					cfg.hudIconScale = clampF(cfg.hudIconScale - 0.1f, 0.5f, 2.0f);
					cfg.save();
				},
				() -> {
					cfg.hudIconScale = clampF(cfg.hudIconScale + 0.1f, 0.5f, 2.0f);
					cfg.save();
				});

		// Reihe 2: Hintergrund-Toggle + Fertig.
		int row2 = panelY + 66;
		context.drawText(textRenderer, Fonts.regular("Hintergrund"), panelX + pad, row2 + 4, TEXT, false);
		int togX = panelX + pad + 90;
		drawToggle(context, togX, row2, cfg.showBackground);
		clickables.add(new Clickable(togX, row2, togX + 28, row2 + 14, () -> {
			cfg.showBackground = !cfg.showBackground;
			cfg.save();
		}));
		button(context, panelX + panelW - pad - 70, row2 - 2, 70, 18, ACCENT, "Fertig", 0xFF15151A, this::close);

		// Reihe 3: Presets.
		int row3 = panelY + 94;
		context.drawText(textRenderer, Fonts.regular("Presets:"), panelX + pad, row3 + 5, TEXT_DIM, false);
		int x = panelX + pad + 50;
		x += presetButton(context, x, row3, "Standard", () -> applyPreset("standard"));
		x += presetButton(context, x, row3, "Oben links", () -> applyPreset("tl"));
		x += presetButton(context, x, row3, "Oben rechts", () -> applyPreset("tr"));
		x += presetButton(context, x, row3, "Kompakt", () -> applyPreset("compact"));
		presetButton(context, x, row3, "Groß", () -> applyPreset("large"));
	}

	// ---- Bausteine --------------------------------------------------------

	private void stepper(DrawContext c, int x, int y, String label, String value, Runnable minus, Runnable plus) {
		c.drawText(textRenderer, Fonts.regular(label), x, y + 5, TEXT, false);
		int btn = 16;
		int minusX = x + 78;
		int valX = minusX + btn + 8;
		int plusX = valX + textRenderer.getWidth(Fonts.bold(value)) + 8;
		sprite(c, minusX, y, btn, btn, BTN_BG);
		c.drawText(textRenderer, Fonts.bold("-"), minusX + btn / 2 - 1, y + 4, TEXT, false);
		c.drawText(textRenderer, Fonts.bold(value), valX, y + 4, TEXT, false);
		sprite(c, plusX, y, btn, btn, BTN_BG);
		c.drawText(textRenderer, Fonts.bold("+"), plusX + btn / 2 - 2, y + 4, TEXT, false);
		clickables.add(new Clickable(minusX, y, minusX + btn, y + btn, minus));
		clickables.add(new Clickable(plusX, y, plusX + btn, y + btn, plus));
	}

	private void button(DrawContext c, int x, int y, int w, int h, int bg, String label, int fg, Runnable action) {
		sprite(c, x, y, w, h, bg);
		c.drawText(textRenderer, Fonts.bold(label), x + (w - textRenderer.getWidth(Fonts.bold(label))) / 2, y + (h - 8) / 2, fg, false);
		clickables.add(new Clickable(x, y, x + w, y + h, action));
	}

	private int presetButton(DrawContext c, int x, int y, String label, Runnable action) {
		int w = textRenderer.getWidth(Fonts.regular(label)) + 14;
		int h = 16;
		sprite(c, x, y, w, h, CARD);
		c.drawText(textRenderer, Fonts.regular(label), x + 7, y + 4, TEXT, false);
		clickables.add(new Clickable(x, y, x + w, y + h, action));
		return w + 6;
	}

	private void drawToggle(DrawContext c, int x, int y, boolean on) {
		int w = 28;
		int h = 14;
		sprite(c, x, y, w, h, on ? TOGGLE_ON : TOGGLE_OFF);
		int knob = h - 4;
		int knobX = on ? x + w - knob - 2 : x + 2;
		com.midgard.render.UIRenderer.fillRoundedRect(c, knobX, y + 2, knob, knob, knob / 2, KNOB);
	}

	private void sprite(DrawContext c, int x, int y, int w, int h, int color) {
		com.midgard.render.UIRenderer.fillRoundedRect(c, x, y, w, h, 7, color);
	}

	private static float clampF(float v, float min, float max) {
		return Math.max(min, Math.min(max, Math.round(v * 10) / 10f));
	}

	private void applyPreset(String name) {
		List<EventDisplay> events = previewEvents();
		switch (name) {
			case "standard" -> {
				cfg.hudScale = 1.0f;
				cfg.hudIconScale = 1.0f;
				cfg.showBackground = true;
				cfg.hudX = 5;
				cfg.hudY = 40;
			}
			case "tl" -> {
				cfg.hudX = 6;
				cfg.hudY = 6;
			}
			case "tr" -> {
				cfg.hudX = Math.max(6, this.width - EventHud.INSTANCE.approxWidth(events) - 6);
				cfg.hudY = 6;
			}
			case "compact" -> {
				cfg.hudScale = 0.8f;
				cfg.hudIconScale = 0.8f;
			}
			case "large" -> {
				cfg.hudScale = 1.3f;
				cfg.hudIconScale = 1.2f;
			}
			default -> {
			}
		}
		cfg.save();
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
