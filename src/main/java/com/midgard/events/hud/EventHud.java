package com.midgard.events.hud;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.midgard.Midgard;
import com.midgard.events.config.ModConfig;
import com.midgard.events.event.EventDisplay;
import com.midgard.events.event.EventIcons;
import com.midgard.events.event.EventManager;
import com.midgard.events.event.EventType;
import com.midgard.events.skyblock.SkyblockHook;
import com.midgard.garden.GardenHud;
import com.midgard.render.MidgardText;
import com.midgard.render.UIRenderer;
import com.midgard.util.Fonts;
import com.midgard.util.TimeUtil;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.joml.Matrix3x2fStack;

/**
 * Zeichnet das HUD im selben Stil wie das Menü: scharfe Schrift (eigene
 * Text-Engine in Geräte-Auflösung), runder, abgedunkelter Hintergrund.
 * Inhalt sind generische Gruppen ({@link HudGroup}) — Events (pro Typ eine
 * Überschrift, darunter Icons + Restzeit) und die Garden-Anzeigen. Jede
 * Gruppe ist im HUD-Editor einzeln verschieb- und skalierbar.
 */
public class EventHud {

	public static final EventHud INSTANCE = new EventHud();

	private static final float HUD_FONT = 9f;
	private static final int PAD = 4;
	private static final int GROUP_GAP = 4;

	private static final int COLOR_HEADER = 0xFF57D8FF;
	private static final int COLOR_ACTIVE = 0xFF5BE36B;
	private static final int COLOR_UPCOMING = 0xFFF2C94C;
	private static final int BG = 0xE6121218;
	private static final int BORDER = 0x33FFFFFF;
	private static final int HIGHLIGHT_BG = 0x59F2772F;

	/** Eine Zeile: optionale Item-Icons + Text; highlight = hervorgehoben. */
	public record HudRow(List<Item> icons, String text, int color, boolean highlight) {
	}

	/** Eine HUD-Box mit Titel; key = Config-Schlüssel (Position/Größe). */
	public record HudGroup(String key, String title, List<HudRow> rows) {
	}

	/** Position + Maße einer HUD-Gruppe (für den HUD-Editor). */
	public record GroupRect(String key, String title, int x, int y, int w, int h) {
		public boolean contains(double mx, double my) {
			return mx >= x - 2 && mx <= x + w + 2 && my >= y - 2 && my <= y + h + 2;
		}
	}

	public void render(DrawContext context) {
		ModConfig cfg = Midgard.config;
		if (!cfg.masterEnabled) {
			return;
		}
		if (cfg.onlyOnSkyblock && !SkyblockHook.INSTANCE.onSkyblock) {
			return;
		}
		List<HudGroup> groups = collect(cfg, EventManager.INSTANCE.get(), false);
		if (groups.isEmpty()) {
			return;
		}
		draw(context, cfg, groups, false);
	}

	public void renderPreview(DrawContext context, List<EventDisplay> events) {
		draw(context, Midgard.config, collect(Midgard.config, events, true), true);
	}

	/** Alle Gruppen für den HUD-Editor (Events-Vorschau + Garden-Vorschau). */
	public List<GroupRect> layoutPreview(ModConfig cfg, List<EventDisplay> events) {
		return layout(cfg, collect(cfg, events, true));
	}

	/** Events + Garden zu einer Gruppenliste zusammensetzen. */
	private List<HudGroup> collect(ModConfig cfg, List<EventDisplay> events, boolean preview) {
		List<HudGroup> out = new ArrayList<>();
		Map<EventType, List<EventDisplay>> byType = new LinkedHashMap<>();
		for (EventDisplay d : events) {
			byType.computeIfAbsent(d.type, k -> new ArrayList<>()).add(d);
		}
		for (Map.Entry<EventType, List<EventDisplay>> g : byType.entrySet()) {
			List<HudRow> rows = new ArrayList<>();
			for (EventDisplay d : g.getValue()) {
				rows.add(new HudRow(rowIcons(d), TimeUtil.format(d.secondsRemaining),
						d.active ? COLOR_ACTIVE : COLOR_UPCOMING, false));
			}
			out.add(new HudGroup(g.getKey().name(), g.getKey().displayName, rows));
		}
		out.addAll(GardenHud.groups(cfg, preview));
		out.addAll(com.midgard.mining.MiningHud.groups(cfg, preview));
		return out;
	}

	// ---- Maße (alles in skalierten GUI-Pixeln) ----------------------------

	/**
	 * Effektive Skalierung der gerade vermessenen/gezeichneten Gruppe:
	 * globale HUD-Größe × Einzel-Größe der Gruppe. Wird vor jeder Gruppe
	 * gesetzt (Rendern läuft nur auf dem Client-Thread).
	 */
	private float scale = 1f;

	private void useScale(ModConfig cfg, String key) {
		scale = cfg.hudScale * (key == null ? 1f : cfg.groupScale(key));
	}

	private float fontSize() {
		return HUD_FONT * scale;
	}

	private int iconSize(ModConfig cfg) {
		return Math.max(8, Math.round(16 * cfg.hudIconScale * scale));
	}

	private int iconStep(ModConfig cfg) {
		return iconSize(cfg) + Math.max(1, Math.round(2 * scale));
	}

	private int sp(float base) {
		return Math.max(1, Math.round(base * scale));
	}

	private int capH(boolean bold) {
		int h = MidgardText.capHeight(fontSize(), bold);
		return h > 0 ? h : Math.round(fontSize() * 0.7f);
	}

	private int rowH(ModConfig cfg, HudRow row) {
		int iconH = row.icons().isEmpty() ? 0 : iconSize(cfg);
		return Math.max(iconH, capH(false) + sp(3));
	}

	private int textW(String s, float size, boolean bold) {
		int w = MidgardText.width(s, size, bold);
		if (w >= 0) {
			return w;
		}
		TextRenderer tr = MinecraftClient.getInstance().textRenderer;
		return tr.getWidth(bold ? Fonts.bold(s) : Fonts.regular(s));
	}

	private void text(DrawContext c, String s, int x, int yTop, float size, int color, boolean bold) {
		if (!MidgardText.draw(c, s, x, yTop, size, color, bold)) {
			TextRenderer tr = MinecraftClient.getInstance().textRenderer;
			c.drawText(tr, bold ? Fonts.bold(s) : Fonts.regular(s), x, yTop, color, true);
		}
	}

	/**
	 * Text mit weichem Schlagschatten (zwei Stufen nach unten rechts) statt
	 * hartem Umriss – wirkt ohne Hintergrund deutlich ruhiger, bleibt aber
	 * auf hellem Untergrund lesbar. Der Schatten wächst mit der Skalierung.
	 */
	private void outlined(DrawContext c, String s, int x, int yTop, float size, int color, boolean bold) {
		int off = Math.max(1, Math.round(scale));
		text(c, s, x + off + 1, yTop + off + 1, size, 0x48000000, bold);
		text(c, s, x + off, yTop + off, size, 0xB4000000, bold);
		text(c, s, x, yTop, size, color, bold);
	}

	private List<Item> rowIcons(EventDisplay d) {
		List<Item> icons = new ArrayList<>();
		if (d.crops != null && !d.crops.isEmpty()) {
			for (String crop : d.crops) {
				icons.add(EventIcons.forCrop(crop));
			}
		} else {
			icons.add(EventIcons.forEvent(d.type));
		}
		return icons;
	}

	private int rowWidth(ModConfig cfg, HudRow row) {
		int w = row.icons().size() * iconStep(cfg);
		if (!row.icons().isEmpty()) {
			w += sp(4);
		}
		return w + textW(row.text(), fontSize(), false);
	}

	/** {frameW, titleH, groupH} – alles in skalierten Pixeln. */
	private int[] groupMetrics(ModConfig cfg, HudGroup g) {
		int pad = sp(PAD);
		int titleH = capH(true);
		int titleW = textW(g.title(), fontSize(), true);
		int rowsW = 0;
		int rowsH = 0;
		for (HudRow row : g.rows()) {
			rowsW = Math.max(rowsW, rowWidth(cfg, row));
			rowsH += rowH(cfg, row);
		}
		int frameW = Math.max(rowsW, titleW) + pad * 2;
		int groupH = pad + titleH + sp(2) + rowsH + pad;
		return new int[] { frameW, titleH, groupH };
	}

	/**
	 * Berechnet die Rechtecke aller Gruppen: Gruppen ohne eigene Position
	 * stapeln sich unter (hudX, hudY); gelöste Gruppen liegen frei.
	 */
	public List<GroupRect> layout(ModConfig cfg, List<HudGroup> groups) {
		List<GroupRect> out = new ArrayList<>();
		useScale(cfg, null);
		int gap = sp(GROUP_GAP);
		int stackY = cfg.hudY;
		for (HudGroup g : groups) {
			useScale(cfg, g.key());
			int[] m = groupMetrics(cfg, g);
			if (cfg.hasGroupPos(g.key())) {
				out.add(new GroupRect(g.key(), g.title(), cfg.groupX(g.key()), cfg.groupY(g.key()), m[0], m[2]));
			} else {
				out.add(new GroupRect(g.key(), g.title(), cfg.hudX, stackY, m[0], m[2]));
				stackY += m[2] + gap;
			}
		}
		return out;
	}

	private void draw(DrawContext context, ModConfig cfg, List<HudGroup> groups, boolean preview) {
		List<GroupRect> rects = layout(cfg, groups);
		for (int i = 0; i < groups.size(); i++) {
			useScale(cfg, groups.get(i).key());
			drawGroup(context, cfg, groups.get(i), rects.get(i).x(), rects.get(i).y(), preview);
		}
	}

	/** Eine Gruppe als eigener Rahmen – der Titel ist EINGEBETTET in die obere Rahmenkante. */
	private void drawGroup(DrawContext context, ModConfig cfg, HudGroup g, int ox, int oy, boolean preview) {
		float fs = fontSize();
		int pad = sp(PAD);
		int[] m = groupMetrics(cfg, g);
		int frameW = m[0];
		int titleH = m[1];
		int groupH = m[2];

		boolean bg = cfg.showBackground || preview;
		if (bg) {
			int r = Math.min(Math.max(3, sp(5)), Math.min(groupH, frameW) / 2);
			UIRenderer.fillRoundedRect(context, ox - 1, oy - 1, frameW + 2, groupH + 2, r + 1, BORDER);
			UIRenderer.fillRoundedRect(context, ox, oy, frameW, groupH, r, BG);
		}

		outlined(context, g.title(), ox + pad, oy + pad, fs, COLOR_HEADER, true);

		int y = oy + pad + titleH + sp(2);
		for (HudRow row : g.rows()) {
			drawRow(context, cfg, row, ox + pad, y, fs, frameW - pad * 2);
			y += rowH(cfg, row);
		}
	}

	private void drawRow(DrawContext context, ModConfig cfg, HudRow row, int x, int rowTop, float fs, int innerW) {
		int rh = rowH(cfg, row);
		if (row.highlight()) {
			// Hervorgehobene Zeile (z. B. Schädling auf dem aktuellen Plot).
			UIRenderer.fillRoundedRect(context, x - sp(3), rowTop, innerW + sp(6), rh, sp(4), HIGHLIGHT_BG);
		}
		int size = iconSize(cfg);
		float iconScale = size / 16f;
		int mid = rowTop + rh / 2;
		int cursor = x;
		Matrix3x2fStack m = context.getMatrices();
		for (Item item : row.icons()) {
			m.pushMatrix();
			m.translate(cursor, mid - size / 2);
			m.scale(iconScale, iconScale);
			context.drawItem(new ItemStack(item), 0, 0);
			m.popMatrix();
			cursor += iconStep(cfg);
		}
		if (!row.icons().isEmpty()) {
			cursor += sp(4);
		}
		int yTop = mid - capH(false) / 2;
		outlined(context, row.text(), cursor, yTop, fs, row.color(), false);
	}
}
