package com.midgard.events.gui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.midgard.Midgard;
import com.midgard.events.config.ModConfig;
import com.midgard.events.event.EventType;
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
 * Schritt 2: in der Bildschirmmitte eine Liste aller Elemente des Standorts –
 * Häkchen an/aus (Box leuchtet bzw. ist ausgegraut), bei Events ein Stepper
 * für die Anzahl kommender, bei Garten/Mining ein M/W (nur hier / überall).
 * Die HUD-Boxen selbst zieht man frei; Events erscheinen als Geister, damit
 * man Überlappungen sieht (und das Andocken sie meidet). Ganz unten "Zurück".
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
	private int dragOffsetX, dragOffsetY;

	public HudPositionScreen(Screen parent) {
		super(Text.literal("HUD bearbeiten"));
		this.parent = parent;
	}

	// ---- Gruppen / Layout des aktuellen Standorts -------------------------

	private List<HudGroup> editable() {
		// Nur AKTIVIERTE Elemente werden gezeichnet – deaktivierte verschwinden
		// komplett und tauchen beim Einschalten an freier Position wieder auf.
		List<HudGroup> g = new ArrayList<>(EventHud.INSTANCE.editorGroups(cfg, activeLoc));
		g.removeIf(x -> !cfg.isElementEnabled(x.key()));
		return g;
	}

	private List<HudGroup> ghosts() {
		return activeLoc == HudElements.Location.GLOBAL
				? List.of()
				: EventHud.INSTANCE.enabledGlobalGroups(cfg);
	}

	private List<HudGroup> all() {
		List<HudGroup> out = new ArrayList<>(editable());
		out.addAll(ghosts());
		return out;
	}

	private List<GroupRect> layout() {
		return EventHud.INSTANCE.layout(cfg, all());
	}

	private Set<String> editableKeys() {
		Set<String> keys = new HashSet<>();
		for (HudGroup g : editable()) {
			keys.add(g.key());
		}
		return keys;
	}

	private GroupRect rectOf(String key) {
		for (GroupRect r : layout()) {
			if (r.key().equals(key)) {
				return r;
			}
		}
		return null;
	}

	// ---- Render -----------------------------------------------------------

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		clickables.clear();
		context.fill(0, 0, this.width, this.height, 0xAA000000);

		if (activeLoc == null) {
			drawLocationPicker(context, mouseX, mouseY);
		} else {
			drawEditor(context, mouseX, mouseY);
		}
		drawBack(context, mouseX, mouseY);
	}

	private void drawLocationPicker(DrawContext context, int mouseX, int mouseY) {
		String title = "Standort wählen";
		txtScaled(context, title, (this.width - txtW(title, true) * 2) / 2, this.height / 2 - 72, TEXT, true, 2f);
		int bw = 220, bh = 30, gap = 8;
		int y = this.height / 2 - 30;
		for (HudElements.Location loc : HudElements.Location.values()) {
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
	}

	private void drawEditor(DrawContext context, int mouseX, int mouseY) {
		List<HudGroup> editable = editable();
		List<HudGroup> ghosts = ghosts();
		List<HudGroup> all = new ArrayList<>(editable);
		all.addAll(ghosts);
		List<GroupRect> rects = EventHud.INSTANCE.layout(cfg, all);

		List<GroupRect> editRects = rects.subList(0, editable.size());
		List<GroupRect> ghostRects = rects.subList(editable.size(), rects.size());

		// Geister (Events) zuerst, dann editierbare Boxen darüber.
		EventHud.INSTANCE.renderGhostGroups(context, cfg, ghosts, new ArrayList<>(ghostRects));
		EventHud.INSTANCE.renderEditor(context, cfg, editable, new ArrayList<>(editRects));

		GroupRect hovered = hit(editRects, mouseX, mouseY);
		for (GroupRect r : editRects) {
			if (r.key().equals(selected)) {
				float pulse = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 220.0));
				outline(context, r, 2, withAlpha(ACCENT, 150 + (int) (pulse * 105)));
			} else if (hovered != null && r.key().equals(hovered.key()) && !dragging) {
				outline(context, r, 2, HOVER_OUTLINE);
			}
		}

		drawElementList(context, mouseX, mouseY);
	}

	/** Liste in der Mitte: Häkchen an/aus, Anzahl (Events), M/W (Garten/Mining). */
	private void drawElementList(DrawContext context, int mouseX, int mouseY) {
		List<HudElements.Element> els = HudElements.forLocation(activeLoc);
		int rowH = 20;
		int w = 300;
		int headH = 24;
		int legendH = 26;
		int h = headH + els.size() * rowH + legendH;
		int x = (this.width - w) / 2;
		int y = (this.height - h) / 2;

		sprite(context, x - 1, y - 1, w + 2, h + 2, BORDER);
		sprite(context, x, y, w, h, PANEL);
		txtVC(context, "Elemente", x + 12, y, headH, TEXT, true);
		txtVC(context, "Häkchen = an/aus", x + w - 12 - txtW("Häkchen = an/aus", false), y, headH, TEXT_DIM, false);

		int ry = y + headH;
		boolean global = activeLoc == HudElements.Location.GLOBAL;
		for (HudElements.Element el : els) {
			boolean on = cfg.isElementEnabled(el.key());
			boolean rowHover = mouseX >= x && mouseX <= x + w && mouseY >= ry && mouseY <= ry + rowH;
			if (rowHover) {
				context.fill(x, ry, x + w, ry + rowH, 0x18FFFFFF);
			}
			// Häkchen.
			int cb = 12;
			int cx = x + 10;
			int cy = ry + (rowH - cb) / 2;
			sprite(context, cx, cy, cb, cb, CARD);
			if (on) {
				check(context, cx, cy, cb, ON);
			}
			clickables.add(new Clickable(cx, cy, cx + cb, cy + cb, () -> {
				cfg.setElementEnabled(el.key(), !cfg.isElementEnabled(el.key()));
				cfg.save();
			}));
			txt(context, el.name(), cx + cb + 8, ry + (rowH - capH()) / 2, on ? TEXT : TEXT_DIM, false);

			int bs = 16;
			int by = ry + (rowH - bs) / 2;
			int rightX = x + w - 10;

			// Größe-zurücksetzen-Knopf (ganz rechts) – Uhr-Icon.
			int resetX = rightX - bs;
			boolean rh = mouseX >= resetX && mouseX <= resetX + bs && mouseY >= by && mouseY <= by + bs;
			sprite(context, resetX, by, bs, bs, rh ? CARD_HOVER : CARD);
			itemIcon(context, net.minecraft.item.Items.CLOCK, resetX, by, bs);
			clickables.add(new Clickable(resetX, by, resetX + bs, by + bs, () -> {
				cfg.setGroupScale(el.key(), 1f);
				cfg.save();
			}));
			rightX = resetX - 4;

			// Anzahl-Stepper für Events, die "kommende" unterstützen.
			EventType ev = eventOf(el.key());
			if (ev != null && supportsUpcoming(ev)) {
				int plusX = rightX - bs;
				int n = cfg.getUpcomingEvent(ev);
				String num = String.valueOf(n);
				int numW = Math.max(12, txtW(num, true) + 2);
				int valX = plusX - 4 - numW;
				int minusX = valX - 4 - bs;
				sprite(context, minusX, by, bs, bs, CARD);
				txtC(context, "-", minusX + bs / 2, by, bs, TEXT, true);
				txtC(context, num, valX + numW / 2, by, bs, TEXT, true);
				sprite(context, plusX, by, bs, bs, CARD);
				txtC(context, "+", plusX + bs / 2, by, bs, TEXT, true);
				clickables.add(new Clickable(minusX, by, minusX + bs, by + bs, () -> {
					cfg.setUpcomingEvent(ev, cfg.getUpcomingEvent(ev) - 1);
					cfg.save();
				}));
				clickables.add(new Clickable(plusX, by, plusX + bs, by + bs, () -> {
					cfg.setUpcomingEvent(ev, cfg.getUpcomingEvent(ev) + 1);
					cfg.save();
				}));
			} else if (!global) {
				// nur hier (Karte) / überall (Ender-Auge = Welt) – echte Item-Icons.
				boolean gl = cfg.isElementGlobal(el.key());
				int bx = rightX - bs;
				boolean hov = mouseX >= bx && mouseX <= bx + bs && mouseY >= by && mouseY <= by + bs;
				sprite(context, bx, by, bs, bs, hov ? CARD_HOVER : CARD);
				itemIcon(context, gl ? net.minecraft.item.Items.ENDER_EYE
						: net.minecraft.item.Items.FILLED_MAP, bx, by, bs);
				clickables.add(new Clickable(bx, by, bx + bs, by + bs, () -> {
					cfg.setElementGlobal(el.key(), !cfg.isElementGlobal(el.key()));
					cfg.save();
				}));
			}
			ry += rowH;
		}

		// Kompakte Legende unten in der Liste.
		String legend = global
				? "Kasten = an/aus · +/- = Anzahl · Uhr = Größe zurück · Rad = Größe"
				: "Kasten = an/aus · Karte/Auge = nur hier/überall · Uhr = Größe zurück";
		txt(context, legend, x + 12, ry + 8, TEXT_DIM, false);
	}

	private void drawBack(DrawContext context, int mouseX, int mouseY) {
		int w = 100, h = 22;
		int x = (this.width - w) / 2;
		int y = this.height - h - 10;
		boolean hover = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
		sprite(context, x - 1, y - 1, w + 2, h + 2, BORDER);
		sprite(context, x, y, w, h, hover ? 0xFFFF8A45 : ACCENT);
		txtC(context, "Zurück", x + w / 2, y, h, 0xFF15151A, true);
		clickables.add(new Clickable(x, y, x + w, y + h, () -> {
			if (activeLoc != null) {
				activeLoc = null; // zur Standortauswahl
				selected = null;
			} else {
				close(); // Editor schließen
			}
		}));
	}

	// ---- Zeichen-Hilfen ---------------------------------------------------

	/** Häkchen = grüner Kasten im Kästchen. */
	private void check(DrawContext c, int x, int y, int size, int color) {
		c.fill(x + 2, y + 2, x + size - 2, y + size - 2, color);
	}

	/** Echtes Minecraft-Item-Icon (16x16, scharf) zentriert im Kästchen. */
	private void itemIcon(DrawContext c, net.minecraft.item.Item item, int boxX, int boxY, int boxSize) {
		int off = (boxSize - 16) / 2;
		c.drawItem(new net.minecraft.item.ItemStack(item), boxX + off, boxY + off);
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

	private EventType eventOf(String key) {
		for (EventType t : EventType.values()) {
			if (t.name().equals(key)) {
				return t;
			}
		}
		return null;
	}

	private static boolean supportsUpcoming(EventType type) {
		return type == EventType.JACOB_CONTEST || !type.isLiveOnly();
	}

	private void txt(DrawContext c, String s, int x, int yTop, int color, boolean bold) {
		if (!com.midgard.render.MidgardText.draw(c, s, x, yTop, 9f, color, bold)) {
			c.drawText(textRenderer, bold ? Fonts.bold(s) : Fonts.regular(s), x, yTop, color, false);
		}
	}

	private void txtC(DrawContext c, String s, int cx, int yTop, int boxH, int color, boolean bold) {
		txt(c, s, cx - txtW(s, bold) / 2, yTop + (boxH - capH()) / 2, color, bold);
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
		Set<String> keys = editableKeys();
		GroupRect r = null;
		for (GroupRect cand : layout()) {
			if (keys.contains(cand.key()) && cand.contains(mx, my)) {
				r = cand;
			}
		}
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
