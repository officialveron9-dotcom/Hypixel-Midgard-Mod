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
 * Zeichnet das Event-HUD im selben Stil wie das Menü: scharfe Schrift (eigene
 * Text-Engine in Geräte-Auflösung), runder, abgedunkelter Hintergrund. Pro
 * Event-Typ eine Überschrift, darunter kompakt nur die Item-Icons + Restzeit
 * (ohne "noch/in" – die Farbe zeigt aktiv/kommend).
 */
public class EventHud {

	public static final EventHud INSTANCE = new EventHud();

	private static final float HUD_FONT = 9f;
	private static final int PAD = 6;
	private static final int GROUP_GAP = 5;

	private static final int COLOR_HEADER = 0xFF57D8FF;
	private static final int COLOR_ACTIVE = 0xFF5BE36B;
	private static final int COLOR_UPCOMING = 0xFFF2C94C;
	private static final int BG = 0xE6121218;
	private static final int BORDER = 0x33FFFFFF;

	public void render(DrawContext context) {
		ModConfig cfg = Midgard.config;
		if (!cfg.masterEnabled) {
			return;
		}
		if (cfg.onlyOnSkyblock && !SkyblockHook.INSTANCE.onSkyblock) {
			return;
		}
		List<EventDisplay> events = EventManager.INSTANCE.get();
		if (events.isEmpty()) {
			return;
		}
		draw(context, cfg, events, false);
	}

	public void renderPreview(DrawContext context, List<EventDisplay> events) {
		draw(context, Midgard.config, events, true);
	}

	private Map<EventType, List<EventDisplay>> group(List<EventDisplay> events) {
		Map<EventType, List<EventDisplay>> groups = new LinkedHashMap<>();
		for (EventDisplay d : events) {
			groups.computeIfAbsent(d.type, k -> new ArrayList<>()).add(d);
		}
		return groups;
	}

	// ---- Maße (alles in skalierten GUI-Pixeln) ----------------------------

	/**
	 * Effektive Skalierung der gerade vermessenen/gezeichneten Gruppe:
	 * globale HUD-Größe × Einzel-Größe der Gruppe. Wird vor jeder Gruppe
	 * gesetzt (Rendern läuft nur auf dem Client-Thread).
	 */
	private float scale = 1f;

	private void useScale(ModConfig cfg, EventType type) {
		scale = cfg.hudScale * (type == null ? 1f : cfg.groupScale(type));
	}

	private float fontSize(ModConfig cfg) {
		return HUD_FONT * scale;
	}

	private int iconSize(ModConfig cfg) {
		return Math.max(8, Math.round(16 * cfg.hudIconScale * scale));
	}

	private int iconStep(ModConfig cfg) {
		return iconSize(cfg) + Math.max(1, Math.round(2 * scale));
	}

	private int sp(ModConfig cfg, float base) {
		return Math.max(1, Math.round(base * scale));
	}

	private int capH(ModConfig cfg, boolean bold) {
		int h = MidgardText.capHeight(fontSize(cfg), bold);
		return h > 0 ? h : Math.round(fontSize(cfg) * 0.7f);
	}

	private int rowH(ModConfig cfg) {
		return Math.max(iconSize(cfg), capH(cfg, false) + sp(cfg, 4));
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

	private int rowWidth(ModConfig cfg, EventDisplay d) {
		int icons = rowIcons(d).size();
		return icons * iconStep(cfg) + sp(cfg, 4) + textW(TimeUtil.format(d.secondsRemaining), fontSize(cfg), false);
	}

	/** {frameW, titleH, rowsH, groupH, rowH} – alles in skalierten Pixeln. */
	private int[] groupMetrics(ModConfig cfg, EventType type, List<EventDisplay> ds) {
		int pad = sp(cfg, PAD);
		int titleH = capH(cfg, true);
		int titleW = textW(type.displayName, fontSize(cfg), true);
		int rowsW = 0;
		for (EventDisplay d : ds) {
			rowsW = Math.max(rowsW, rowWidth(cfg, d));
		}
		int rh = rowH(cfg);
		int rowsH = ds.size() * rh;
		int frameW = Math.max(rowsW, titleW) + pad * 2;
		// Kasten: oben Rand, Titel, kleiner Abstand, Zeilen, unten Rand.
		int groupH = pad + titleH + sp(cfg, 3) + rowsH + pad;
		return new int[] { frameW, titleH, rowsH, groupH, rh };
	}

	/** Position + Maße einer HUD-Gruppe (für den HUD-Editor). */
	public record GroupRect(EventType type, int x, int y, int w, int h) {
		public boolean contains(double mx, double my) {
			return mx >= x - 2 && mx <= x + w + 2 && my >= y - 2 && my <= y + h + 2;
		}
	}

	/**
	 * Berechnet die Rechtecke aller Gruppen: Gruppen ohne eigene Position
	 * stapeln sich unter (hudX, hudY); gelöste Gruppen liegen frei.
	 */
	public List<GroupRect> layout(ModConfig cfg, List<EventDisplay> events) {
		Map<EventType, List<EventDisplay>> groups = group(events);
		List<GroupRect> out = new ArrayList<>();
		useScale(cfg, null);
		int gap = sp(cfg, GROUP_GAP);
		int stackY = cfg.hudY;
		for (Map.Entry<EventType, List<EventDisplay>> g : groups.entrySet()) {
			EventType type = g.getKey();
			useScale(cfg, type);
			int[] m = groupMetrics(cfg, type, g.getValue());
			if (cfg.hasGroupPos(type)) {
				out.add(new GroupRect(type, cfg.groupX(type), cfg.groupY(type), m[0], m[3]));
			} else {
				out.add(new GroupRect(type, cfg.hudX, stackY, m[0], m[3]));
				stackY += m[3] + gap;
			}
		}
		return out;
	}

	private void draw(DrawContext context, ModConfig cfg, List<EventDisplay> events, boolean preview) {
		Map<EventType, List<EventDisplay>> groups = group(events);
		for (GroupRect r : layout(cfg, events)) {
			useScale(cfg, r.type());
			drawGroup(context, cfg, r.type(), groups.get(r.type()), r.x(), r.y(), preview);
		}
	}

	/** Eine Gruppe als eigener Rahmen – der Titel ist EINGEBETTET in die obere Rahmenkante. */
	private int drawGroup(DrawContext context, ModConfig cfg, EventType type, List<EventDisplay> ds,
			int ox, int oy, boolean preview) {
		float fs = fontSize(cfg);
		int pad = sp(cfg, PAD);
		int[] m = groupMetrics(cfg, type, ds);
		int frameW = m[0];
		int titleH = m[1];
		int groupH = m[3];
		int rh = m[4];

		boolean bg = cfg.showBackground || preview;
		if (bg) {
			int r = Math.min(Math.max(4, sp(cfg, 7)), Math.min(groupH, frameW) / 2);
			UIRenderer.fillRoundedRect(context, ox - 1, oy - 1, frameW + 2, groupH + 2, r + 1, BORDER);
			UIRenderer.fillRoundedRect(context, ox, oy, frameW, groupH, r, BG);
		}

		// Titel oben IM Kasten, mit Umriss für Lesbarkeit.
		outlined(context, type.displayName, ox + pad, oy + pad, fs, COLOR_HEADER, true);

		int y = oy + pad + titleH + sp(cfg, 3);
		for (EventDisplay d : ds) {
			drawRow(context, cfg, d, ox + pad, y, fs);
			y += rh;
		}
		return groupH;
	}

	private void drawRow(DrawContext context, ModConfig cfg, EventDisplay d, int x, int rowTop, float fs) {
		int size = iconSize(cfg);
		float scale = size / 16f;
		int rh = rowH(cfg);
		int mid = rowTop + rh / 2;
		int iconY = mid - size / 2;
		int cursor = x;
		Matrix3x2fStack m = context.getMatrices();
		for (Item item : rowIcons(d)) {
			m.pushMatrix();
			m.translate(cursor, iconY);
			m.scale(scale, scale);
			context.drawItem(new ItemStack(item), 0, 0);
			m.popMatrix();
			cursor += iconStep(cfg);
		}
		String t = TimeUtil.format(d.secondsRemaining);
		int yTop = mid - capH(cfg, false) / 2;
		outlined(context, t, cursor + sp(cfg, 4), yTop, fs, d.active ? COLOR_ACTIVE : COLOR_UPCOMING, false);
	}

}
