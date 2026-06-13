package com.midgard.events.gui;

import java.util.ArrayList;
import java.util.List;

import com.midgard.Midgard;
import com.midgard.events.config.ModConfig;
import com.midgard.events.event.EventDisplay;
import com.midgard.events.event.EventManager;
import com.midgard.events.event.EventType;
import com.midgard.events.hud.EventHud;
import com.midgard.events.hud.EventHud.GroupRect;
import com.midgard.util.Fonts;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * "HUD bearbeiten": Jedes HUD-Element (Event-Gruppe) lässt sich einzeln
 * anklicken (leuchtender Rahmen), verschieben (mit Andocken an den Rändern)
 * und über Buttons bzw. das Mausrad vergrößern/verkleinern. "Standardgröße"
 * setzt die Größe zurück, "Andocken" hängt das Element wieder in den Stapel.
 */
public class HudPositionScreen extends Screen {

	private static final int PANEL = 0xF2121419;
	private static final int ACCENT = 0xFFF2772F;
	private static final int TEXT = 0xFFF1F1F4;
	private static final int TEXT_DIM = 0xFF8C8C97;
	private static final int BORDER = 0x33FFFFFF;
	private static final int BTN_BG = 0xFF2E2E38;
	private static final int HOVER_OUTLINE = 0x50FFFFFF;

	/** Abstand zum Bildschirmrand beim Andocken (bewusst klein – keine Lücke). */
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

	private String selected = null;
	private String selectedTitle = "";
	private boolean dragging = false;
	private boolean dragMoved = false;
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

	private List<GroupRect> layout() {
		return EventHud.INSTANCE.layoutPreview(cfg, previewEvents());
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

		// HUD-Vorschau (Elemente einzeln anfassbar).
		EventHud.INSTANCE.renderPreview(context, previewEvents());

		// Rahmen: Hover dezent, Auswahl pulsierend (Leuchten).
		List<GroupRect> rects = layout();
		GroupRect hovered = hit(rects, mouseX, mouseY);
		for (GroupRect r : rects) {
			if (r.key().equals(selected)) {
				float pulse = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 220.0));
				outline(context, r, 2, withAlpha(ACCENT, 140 + (int) (pulse * 115)));
				outline(context, r, 4, withAlpha(ACCENT, 30 + (int) (pulse * 60)));
			} else if (hovered != null && r.key().equals(hovered.key()) && !dragging) {
				outline(context, r, 2, HOVER_OUTLINE);
			}
		}

		drawPanel(context, mouseX, mouseY);
	}

	/** Rechteckiger Rahmen (4 Streifen) im Abstand {@code gap} um die Gruppe. */
	private void outline(DrawContext c, GroupRect r, int gap, int color) {
		int x1 = r.x() - gap;
		int y1 = r.y() - gap;
		int x2 = r.x() + r.w() + gap;
		int y2 = r.y() + r.h() + gap;
		c.fill(x1, y1, x2, y1 + 1, color);
		c.fill(x1, y2 - 1, x2, y2, color);
		c.fill(x1, y1, x1 + 1, y2, color);
		c.fill(x2 - 1, y1, x2, y2, color);
	}

	private static int withAlpha(int color, int alpha) {
		return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0xFFFFFF);
	}

	private GroupRect hit(List<GroupRect> rects, double mx, double my) {
		// Rückwärts: später gezeichnete Elemente liegen oben.
		for (int i = rects.size() - 1; i >= 0; i--) {
			if (rects.get(i).contains(mx, my)) {
				return rects.get(i);
			}
		}
		return null;
	}

	// ---- Unten: nur ein Fertig-Button ---------------------------------------

	private void drawPanel(DrawContext context, int mouseX, int mouseY) {
		panelW = 88;
		panelH = 22;
		panelX = (this.width - panelW) / 2;
		panelY = this.height - panelH - 12;

		boolean hover = mouseX >= panelX && mouseX <= panelX + panelW
				&& mouseY >= panelY && mouseY <= panelY + panelH;
		sprite(context, panelX - 1, panelY - 1, panelW + 2, panelH + 2, BORDER);
		sprite(context, panelX, panelY, panelW, panelH, hover ? 0xFFFF8A45 : ACCENT);
		txt(context, "Fertig", panelX + (panelW - txtW("Fertig", true)) / 2,
				panelY + (panelH - capH() + 1) / 2, 0xFF15151A, true);
		clickables.add(new Clickable(panelX, panelY, panelX + panelW, panelY + panelH, this::close));
	}

	private void resize(float delta) {
		if (selected != null) {
			cfg.setGroupScale(selected, cfg.groupScale(selected) + delta);
			cfg.save();
		}
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
		boolean inPanel = mx >= panelX && mx <= panelX + panelW && my >= panelY && my <= panelY + panelH;
		if (inPanel) {
			return super.mouseClicked(click, doubled);
		}
		GroupRect r = hit(layout(), mx, my);
		if (r != null) {
			selected = r.key();
			selectedTitle = r.title();
			if (doubled) {
				// Doppelklick: Standardgröße + zurück in den Stapel.
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
		selected = null; // daneben geklickt -> abwählen
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

			// Automatisches Andocken an die Ränder (magnetisch, fast bündig).
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

			// Kein Überlappen: erst volle Bewegung versuchen, sonst nur eine Achse.
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
		if (dragging && !dragMoved) {
			// Nur angeklickt (nicht gezogen): Auswahl reicht, Position unverändert.
		}
		dragging = false;
		return super.mouseReleased(click);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (selected != null && verticalAmount != 0) {
			resize(verticalAmount > 0 ? 0.05f : -0.05f);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	private static int clamp(int v, int min, int max) {
		return Math.max(min, Math.min(max, v));
	}

	/** Würde das ausgewählte Element an (nx, ny) ein anderes überlappen? */
	private boolean collides(int nx, int ny, GroupRect self) {
		int gap = 2; // Mindestabstand zwischen Elementen
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
