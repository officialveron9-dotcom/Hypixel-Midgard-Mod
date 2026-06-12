package com.midgard.events.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntConsumer;

import com.midgard.Midgard;
import com.midgard.events.config.ModConfig;
import com.midgard.events.event.EventCategory;
import com.midgard.events.event.EventIcons;
import com.midgard.events.event.EventType;
import com.midgard.util.Fonts;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2fStack;

/**
 * Modernes Einstellungs-GUI als zentriertes Fenster. Glatte (anti-aliased)
 * Rundungen über 9-Slice-Sprites – das ist schnell (ein Zeichenaufruf pro
 * Element) und nicht verpixelt. Events nutzen Minecraft-Item-Icons.
 */
public class ConfigScreen extends Screen {

	private static final float FONT = 9f;

	// Farben (ARGB).
	private static final int DIM = 0xAA000000;
	private static final int PANEL = 0xFF15151B;
	private static final int DIVIDER = 0xFF272730;
	private static final int CARD = 0xFF1E1E26;
	private static final int CARD_HOVER = 0xFF2C2C3A;
	private static final int TILE = 0xFF121217;
	private static final int SHADOW = 0x40000000;
	private static final int ACCENT = 0xFFF2772F;
	private static final int TEXT = 0xFFF1F1F4;
	private static final int TEXT_DIM = 0xFF8C8C97;
	private static final int SEL_BG = 0xFF262630;
	private static final int HOVER_BG = 0xFF20202A;
	private static final int TOGGLE_ON = 0xFF35B36A;
	private static final int TOGGLE_OFF = 0xFF3C3C47;
	private static final int KNOB = 0xFFF7F7F9;
	private static final int BTN_BG = 0xFF2E2E38;

	private static final int SIDEBAR_W = 138;
	private static final int CARD_H = 44;        // Allgemein-Karten
	private static final int EVENT_H = 30;       // Event-Karten (kompakter)
	private static final int CARD_GAP = 6;
	private static final int EVENT_GAP = 4;
	private static final int TILE_SIZE = 28;
	private static final int EVENT_TILE = 20;

	private record Tab(String label, boolean events, int accent) {
	}

	/** Signaturfarbe pro Event-Kategorie (für den seitlichen Streifen). */
	private static int categoryColor(EventCategory c) {
		return switch (c) {
			case JACOB -> 0xFF5BE36B;    // Farmen – grün
			case MINING -> 0xFF4DA6FF;   // Mining – blau
			case FISHING -> 0xFF36C9D6;  // Fishing – türkis
			case CALENDAR -> 0xFFB57BF2; // Kalender – lila
			case OTHER -> 0xFFBFC2CC;    // Sonstige – hellgrau
		};
	}

	private record Clickable(int x1, int y1, int x2, int y2, Runnable action) {
		boolean contains(double mx, double my) {
			return mx >= x1 && mx <= x2 && my >= y1 && my <= y2;
		}
	}

	private record Row(int height, IntConsumer render) {
	}

	private final Screen parent;
	private final ModConfig cfg = Midgard.config;
	private final List<Tab> tabs = List.of(
			new Tab("Allgemein", false, 0xFFFFFFFF),
			new Tab("Interface", false, 0xFF57D8FF),
			new Tab("Events", true, ACCENT),
			new Tab("Garden", false, 0xFF5BE36B));
	private final List<Clickable> clickables = new ArrayList<>();
	private final Map<String, Float> anim = new HashMap<>();
	private final java.util.Set<EventCategory> expanded = new java.util.HashSet<>();
	private boolean fontExpanded = false;
	private final Map<String, com.midgard.render.GlyphAtlas> fontPrev = new HashMap<>();

	private long lastNanos = 0;
	private float dt;
	private int selectedTab = 0;
	private double scroll = 0;
	private double maxScroll = 0;
	private int px, py, pw, ph;
	private int listTop;
	private int listBottom;

	public ConfigScreen(Screen parent) {
		super(Text.literal("Midgard"));
		this.parent = parent;
	}

	private static boolean fontDiagLogged = false;

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		long now = System.nanoTime();
		dt = lastNanos == 0 ? 0f : Math.min(0.05f, (now - lastNanos) / 1_000_000_000f);
		lastNanos = now;

		if (!fontDiagLogged) {
			fontDiagLogged = true;
			int wRoboto = this.textRenderer.getWidth(Fonts.bold("Midgard Test 123"));
			int wDefault = this.textRenderer.getWidth(Text.literal("Midgard Test 123"));
			System.out.println("[Midgard] Font-Diag: roboto=" + wRoboto + " default=" + wDefault
					+ (wRoboto != wDefault ? "  -> Roboto AKTIV" : "  -> Roboto NICHT aktiv (Fallback auf MC-Schrift)"));
		}

		clickables.clear();
		context.fill(0, 0, this.width, this.height, DIM);

		pw = Math.min(this.width - 80, 640);
		ph = Math.min(this.height - 80, 440);
		px = (this.width - pw) / 2;
		py = (this.height - ph) / 2;

		sprite(context, px + 2, py + 4, pw, ph, SHADOW);
		sprite(context, px, py, pw, ph, PANEL);
		context.fill(px + SIDEBAR_W, py + 12, px + SIDEBAR_W + 1, py + ph - 12, DIVIDER);

		drawSidebar(context, mouseX, mouseY);
		drawHeader(context, mouseX, mouseY);
		drawContent(context, mouseX, mouseY);
	}

	// ---- Sidebar ----------------------------------------------------------

	private void drawSidebar(DrawContext context, int mouseX, int mouseY) {
		txtScaled(context, "Midgard", px + 18, py + 24, TEXT, true, 1.7f);

		int y = py + 58;
		for (int i = 0; i < tabs.size(); i++) {
			Tab tab = tabs.get(i);
			boolean selected = i == selectedTab;
			int rowH = 28;
			int x1 = px + 11;
			int x2 = px + SIDEBAR_W - 11;
			boolean hover = mouseX >= x1 && mouseX <= x2 && mouseY >= y && mouseY <= y + rowH;
			float hoverT = animate("tab" + i, hover && !selected, 14f);

			if (selected) {
				sprite(context, x1, y, x2 - x1, rowH, SEL_BG);
				sprite(context, x1 + 1, y + 7, 3, rowH - 14, tab.accent());
			} else if (hoverT > 0.01f) {
				sprite(context, x1, y, x2 - x1, rowH, withAlpha(HOVER_BG, (int) (hoverT * 255)));
			}

			txtVC(context, tab.label(), x1 + 12, y, rowH, selected ? TEXT : TEXT_DIM, selected);

			final int idx = i;
			clickables.add(new Clickable(x1, y, x2, y + rowH, () -> {
				selectedTab = idx;
				scroll = 0;
			}));
			y += rowH + 5;
		}

	}

	private void drawHeader(DrawContext context, int mouseX, int mouseY) {
		Tab tab = tabs.get(selectedTab);
		int contentX = px + SIDEBAR_W + 20;
		txtScaled(context, tab.label(), contentX, py + 19, TEXT, true, 1.45f);
		context.fill(contentX, py + 44, px + pw - 20, py + 45, DIVIDER);

		int s = 20;
		int bx = px + pw - 28;
		int by = py + 13;
		boolean hover = mouseX >= bx && mouseX <= bx + s && mouseY >= by && mouseY <= by + s;
		float hoverT = animate("close", hover, 16f);
		if (hoverT > 0.01f) {
			sprite(context, bx, by, s, s, withAlpha(0x8A3A3A, (int) (hoverT * 255)));
		}
		txtC(context, "X", bx + s / 2, by, s, hover ? TEXT : TEXT_DIM, true);
		clickables.add(new Clickable(bx, by, bx + s, by + s, this::close));
	}

	// ---- Content ----------------------------------------------------------

	private void drawContent(DrawContext context, int mouseX, int mouseY) {
		listTop = py + 54;
		listBottom = py + ph - 12;
		int cardX = px + SIDEBAR_W + 20;
		int cardW = px + pw - cardX - 18;

		List<Row> rows = new ArrayList<>();
		Tab tab = tabs.get(selectedTab);
		if (tab.events()) {
			buildEventRows(rows, context, mouseX, mouseY, cardX, cardW);
		} else if (tab.label().equals("Garden")) {
			buildGardenRows(rows, context, mouseX, mouseY, cardX, cardW);
		} else if (tab.label().equals("Interface")) {
			buildInterfaceRows(rows, context, mouseX, mouseY, cardX, cardW);
		} else {
			buildGeneralRows(rows, context, mouseX, mouseY, cardX, cardW);
		}

		int totalHeight = 0;
		for (Row r : rows) {
			totalHeight += r.height();
		}
		int visibleHeight = listBottom - listTop;
		maxScroll = Math.max(0, totalHeight - visibleHeight + 6);
		scroll = Math.max(0, Math.min(scroll, maxScroll));

		context.enableScissor(px + SIDEBAR_W + 1, listTop, px + pw, listBottom);
		int y = listTop - (int) scroll;
		for (Row row : rows) {
			if (y + row.height() > listTop && y < listBottom) {
				row.render().accept(y);
			}
			y += row.height();
		}
		context.disableScissor();

		if (maxScroll > 0) {
			int trackX = px + pw - 8;
			int barH = Math.max(20, (int) ((float) visibleHeight / totalHeight * visibleHeight));
			int barY = listTop + (int) ((scroll / maxScroll) * (visibleHeight - barH));
			sprite(context, trackX, barY, 3, barH, ACCENT);
		}
	}

	private void buildGeneralRows(List<Row> out, DrawContext context, int mouseX, int mouseY, int cardX, int cardW) {
		out.add(toggleRow(context, mouseX, mouseY, cardX, cardW, pngIcon("hud"),
				"HUD aktiviert", "Schaltet das gesamte Event-HUD ein/aus.",
				() -> cfg.masterEnabled, () -> {
					cfg.masterEnabled = !cfg.masterEnabled;
					cfg.save();
				}));
		out.add(toggleRow(context, mouseX, mouseY, cardX, cardW, pngIcon("background"),
				"Hintergrund", "Box hinter den Events anzeigen.",
				() -> cfg.showBackground, () -> {
					cfg.showBackground = !cfg.showBackground;
					cfg.save();
				}));
		out.add(toggleRow(context, mouseX, mouseY, cardX, cardW, pngIcon("skyblock"),
				"Nur in SkyBlock", "HUD nur in SkyBlock anzeigen.",
				() -> cfg.onlyOnSkyblock, () -> {
					cfg.onlyOnSkyblock = !cfg.onlyOnSkyblock;
					cfg.save();
				}));
		out.add(toggleRow(context, mouseX, mouseY, cardX, cardW, pngIcon("gem"),
				"Bazaar-Preise", "Kauf-/Verkaufspreis im Item-Tooltip anzeigen.",
				() -> cfg.showPrices, () -> {
					cfg.showPrices = !cfg.showPrices;
					cfg.save();
				}));
		out.add(fontHeaderRow(context, mouseX, mouseY, cardX, cardW));
		if (fontExpanded) {
			// Kompaktes Grid statt einer Zeile pro Schrift.
			List<com.midgard.render.MidgardFont.FontOption> fonts = com.midgard.render.MidgardFont.FONTS;
			int cols = Math.max(2, Math.min(3, (cardW - 14) / 150));
			for (int i = 0; i < fonts.size(); i += cols) {
				out.add(fontGridRow(context, mouseX, mouseY, cardX, cardW, fonts, i, cols));
			}
		}
	}

	private void buildInterfaceRows(List<Row> out, DrawContext context, int mouseX, int mouseY, int cardX, int cardW) {
		out.add(buttonRow(context, mouseX, mouseY, cardX, cardW, pngIcon("move"),
				"HUD bearbeiten", "Elemente auswählen, verschieben und skalieren.", "Öffnen",
				() -> {
					if (client != null) {
						client.setScreen(new HudPositionScreen(this));
					}
				}));
		out.add(toggleRow(context, mouseX, mouseY, cardX, cardW, pngIcon("hud"),
				"Eigene Statusleisten", "Leben links, XP mittig, Mana rechts – Werte in der Leiste.",
				() -> cfg.customBars, () -> {
					cfg.customBars = !cfg.customBars;
					cfg.save();
				}));
	}

	private void buildGardenRows(List<Row> out, DrawContext context, int mouseX, int mouseY, int cardX, int cardW) {
		out.add(compactToggleRow(context, mouseX, mouseY, cardX, cardW, "Besucher",
				() -> cfg.gardenVisitors, () -> {
					cfg.gardenVisitors = !cfg.gardenVisitors;
					cfg.save();
				}));
		out.add(compactToggleRow(context, mouseX, mouseY, cardX, cardW, "Schädlinge",
				() -> cfg.gardenPests, () -> {
					cfg.gardenPests = !cfg.gardenPests;
					cfg.save();
				}));
		out.add(compactToggleRow(context, mouseX, mouseY, cardX, cardW, "Milestone-Rang",
				() -> cfg.gardenCollection, () -> {
					cfg.gardenCollection = !cfg.gardenCollection;
					cfg.save();
				}));
		out.add(compactToggleRow(context, mouseX, mouseY, cardX, cardW, "Werkzeug-Level",
				() -> cfg.gardenTool, () -> {
					cfg.gardenTool = !cfg.gardenTool;
					cfg.save();
				}));
		out.add(compactToggleRow(context, mouseX, mouseY, cardX, cardW, "Farming-Statistik",
				() -> cfg.gardenStats, () -> {
					cfg.gardenStats = !cfg.gardenStats;
					cfg.save();
				}));
		out.add(compactToggleRow(context, mouseX, mouseY, cardX, cardW, "Composter",
				() -> cfg.gardenComposter, () -> {
					cfg.gardenComposter = !cfg.gardenComposter;
					cfg.save();
				}));
		out.add(compactToggleRow(context, mouseX, mouseY, cardX, cardW, "Jacob Live",
				() -> cfg.gardenJacob, () -> {
					cfg.gardenJacob = !cfg.gardenJacob;
					cfg.save();
				}));
		out.add(compactToggleRow(context, mouseX, mouseY, cardX, cardW, "Contest-Warnung (Chat)",
				() -> cfg.jacobWarn, () -> {
					cfg.jacobWarn = !cfg.jacobWarn;
					cfg.save();
				}));
	}

	/** Kompakte Schalter-Zeile ohne Icon (Garden-Tab). */
	private Row compactToggleRow(DrawContext context, int mouseX, int mouseY, int cardX, int cardW,
			String title, java.util.function.BooleanSupplier value, Runnable onClick) {
		return new Row(EVENT_H + EVENT_GAP, y -> {
			boolean hover = hovering(mouseX, mouseY, cardX, y, cardW, EVENT_H);
			float hoverT = animate("gd" + title, hover, 14f);
			float togT = animate("gdt" + title, value.getAsBoolean(), 12f);
			sprite(context, cardX, y, cardW, EVENT_H, lerpColor(CARD, CARD_HOVER, hoverT));
			txtVC(context, title, cardX + 12, y, EVENT_H, value.getAsBoolean() ? TEXT : TEXT_DIM, true);
			int togW = 26;
			int togH = 13;
			drawToggle(context, cardX + cardW - 10 - togW, y + (EVENT_H - togH) / 2, togT, togW, togH);
			clickables.add(new Clickable(cardX, y, cardX + cardW, y + EVENT_H, onClick));
		});
	}

	private void buildEventRows(List<Row> out, DrawContext context, int mouseX, int mouseY, int cardX, int cardW) {
		for (EventCategory category : EventCategory.values()) {
			List<EventType> inCat = new ArrayList<>();
			for (EventType t : EventType.values()) {
				if (t.category == category) {
					inCat.add(t);
				}
			}
			if (inCat.isEmpty()) {
				continue;
			}
			boolean exp = expanded.contains(category);
			out.add(categoryBarRow(context, mouseX, mouseY, cardX, cardW, category, inCat.size(), exp));
			if (exp) {
				for (EventType type : inCat) {
					out.add(eventRow(context, mouseX, mouseY, cardX, cardW, type));
				}
			}
		}
	}

	private static boolean supportsUpcoming(EventType type) {
		return type == EventType.JACOB_CONTEST || !type.isLiveOnly();
	}

	// ---- Zeilen -----------------------------------------------------------

	/** Aufklappbarer Kategorie-Balken mit Pfeil. */
	private Row categoryBarRow(DrawContext context, int mouseX, int mouseY, int cardX, int cardW,
			EventCategory category, int count, boolean exp) {
		int barH = 26;
		return new Row(barH + 6, y -> {
				boolean hover = mouseX >= cardX && mouseX <= cardX + cardW && mouseY >= y && mouseY <= y + barH
					&& mouseY >= listTop && mouseY <= listBottom;
			float hoverT = animate("cat" + category.name(), hover || exp, 14f);
			sprite(context, cardX, y, cardW, barH, lerpColor(CARD, CARD_HOVER, hoverT));
			int catColor = categoryColor(category);
			sprite(context, cardX, y + 7, 3, barH - 14, catColor);
			drawCaret(context, cardX + 14, y + barH / 2, exp, exp ? catColor : TEXT_DIM);
			txtVC(context, category.displayName, cardX + 30, y, barH, TEXT, true);
			String info = count + (count == 1 ? " Event" : " Events");
			txtVC(context, info, cardX + cardW - 14 - txtW(info, false), y, barH, TEXT_DIM, false);
			clickables.add(new Clickable(cardX, y, cardX + cardW, y + barH, () -> {
				if (exp) {
					expanded.remove(category);
				} else {
					expanded.add(category);
				}
			}));
		});
	}

	private Row eventRow(DrawContext context, int mouseX, int mouseY, int cardX0, int cardW0, EventType type) {
		int indent = 14;
		int cardX = cardX0 + indent;
		int cardW = cardW0 - indent;
		return new Row(EVENT_H + EVENT_GAP, y -> {
				boolean hover = hovering(mouseX, mouseY, cardX, y, cardW, EVENT_H);
			float hoverT = animate("ev" + type.name(), hover, 14f);
			boolean enabled = cfg.isEventEnabled(type);
			float togT = animate("evt" + type.name(), enabled, 12f);
			int fg = enabled ? TEXT : TEXT_DIM;
			cardFrame(context, cardX, y, cardW, EVENT_H, hoverT, EVENT_TILE);
			drawItemIcon(context, EventIcons.forEvent(type), cardX, y, EVENT_H, EVENT_TILE);
			if (!enabled) {
				// Deaktiviert → Icon ausgrauen.
				int tileY = y + (EVENT_H - EVENT_TILE) / 2;
				sprite(context, cardX + 8, tileY, EVENT_TILE, EVENT_TILE, 0xB0121217);
			}
			int textX = cardX + 8 + EVENT_TILE + 10;
			txtVC(context, type.displayName, textX, y, EVENT_H, fg, true);

			int togW = 26;
			int togH = 13;
			int togX = cardX + cardW - 10 - togW;
			drawToggle(context, togX, y + (EVENT_H - togH) / 2, togT, togW, togH);

			// Pro-Event-Regler "Kommende" (links vom Toggle), falls Vorschau möglich.
			if (supportsUpcoming(type)) {
				int n = cfg.getUpcomingEvent(type);
				int btn = 14;
				int gap = 5;
				int by = y + (EVENT_H - btn) / 2;
				String val = String.valueOf(n);
				int valSlot = Math.max(12, txtW(val, true) + 2);
				int plusX = togX - 10 - btn;
				int valLeft = plusX - gap - valSlot;
				int minusX = valLeft - gap - btn;
				sprite(context, minusX, by, btn, btn, BTN_BG);
				txtC(context, "-", minusX + btn / 2, by, btn, fg, true);
				txtC(context, val, valLeft + valSlot / 2, by, btn, fg, true);
				sprite(context, plusX, by, btn, btn, BTN_BG);
				txtC(context, "+", plusX + btn / 2, by, btn, fg, true);
				clickables.add(new Clickable(minusX, by, minusX + btn, by + btn, () -> {
					cfg.setUpcomingEvent(type, n - 1);
					cfg.save();
				}));
				clickables.add(new Clickable(plusX, by, plusX + btn, by + btn, () -> {
					cfg.setUpcomingEvent(type, n + 1);
					cfg.save();
				}));
			}

			clickables.add(new Clickable(cardX, y, cardX + cardW, y + EVENT_H, () -> {
				cfg.setEventEnabled(type, !cfg.isEventEnabled(type));
				cfg.save();
			}));
		});
	}

	// ---- Schriftart-Dropdown (mit Vorschau, lädt nur bei Auswahl neu) -----

	private Row fontHeaderRow(DrawContext context, int mouseX, int mouseY, int cardX, int cardW) {
		return new Row(CARD_H + CARD_GAP, y -> {
			boolean hover = hovering(mouseX, mouseY, cardX, y, cardW, CARD_H);
			float hoverT = animate("fonthdr", hover, 14f);
			cardFrame(context, cardX, y, cardW, CARD_H, hoverT, TILE_SIZE);
			drawPngIcon(context, "size", cardX, y, CARD_H, TILE_SIZE);
			int textX = cardX + 8 + TILE_SIZE + 10;
			txt(context, "Schriftart", textX, y + 8, TEXT, true);
			// aktuelle Auswahl als Vorschau (in ihrer eigenen Schrift)
			fontPreview(context, cfg.globalFontName,
					com.midgard.render.MidgardFont.display(cfg.globalFontName), textX, y + 23, TEXT_DIM);
			drawCaret(context, cardX + cardW - 22, y + CARD_H / 2, fontExpanded, TEXT_DIM);
			clickables.add(new Clickable(cardX, y, cardX + cardW, y + CARD_H, () -> fontExpanded = !fontExpanded));
		});
	}

	/** Eine Grid-Zeile mit {@code cols} Schriftarten nebeneinander (kompakt). */
	private Row fontGridRow(DrawContext context, int mouseX, int mouseY, int cardX0, int cardW0,
			List<com.midgard.render.MidgardFont.FontOption> fonts, int start, int cols) {
		int indent = 14;
		int gap = EVENT_GAP;
		int cellH = 26;
		int cardX = cardX0 + indent;
		int cardW = cardW0 - indent;
		int cellW = (cardW - (cols - 1) * gap) / cols;
		return new Row(cellH + EVENT_GAP, y -> {
			for (int i = 0; i < cols && start + i < fonts.size(); i++) {
				com.midgard.render.MidgardFont.FontOption f = fonts.get(start + i);
				int x = cardX + i * (cellW + gap);
				boolean selected = f.key().equals(cfg.globalFontName);
				boolean hover = hovering(mouseX, mouseY, x, y, cellW, cellH);
				float hoverT = animate("fopt" + f.key(), hover, 14f);
				sprite(context, x, y, cellW, cellH, lerpColor(CARD, CARD_HOVER, hoverT));
				if (selected) {
					sprite(context, x, y + 5, 3, cellH - 10, ACCENT);
				}
				fontPreview(context, f.key(), f.display(), x + 10, y + (cellH - capH() - 2) / 2,
						selected ? TEXT : TEXT_DIM);
				clickables.add(new Clickable(x, y, x + cellW, y + cellH, () -> {
					cfg.globalFontName = f.key();
					cfg.save();
					com.midgard.render.MidgardFont.apply(f.key()); // NUR hier wird neu geladen
					fontExpanded = false;
				}));
			}
		});
	}

	/** Zeichnet {@code text} in der zu {@code key} gehörenden Schrift (Vorschau, ohne Neuladen). */
	private void fontPreview(DrawContext c, String key, String text, int x, int yTop, int color) {
		if (key == null || key.isEmpty()) {
			txt(c, text, x, yTop, color, false);
			return;
		}
		try {
			com.midgard.render.GlyphAtlas a = fontPrev.computeIfAbsent(key,
					k -> new com.midgard.render.GlyphAtlas(com.midgard.render.MidgardFont.ttf(k), "fontprev_" + k));
			if (a.isReady()) {
				a.draw(c, text, x, yTop, FONT + 2f, color);
				return;
			}
		} catch (Throwable ignored) {
		}
		txt(c, text, x, yTop, color, false);
	}

	private Row toggleRow(DrawContext context, int mouseX, int mouseY, int cardX, int cardW,
			String icon, String title, String desc, java.util.function.BooleanSupplier value, Runnable onClick) {
		return new Row(CARD_H + CARD_GAP, y -> {
			boolean hover = hovering(mouseX, mouseY, cardX, y, cardW, CARD_H);
			float hoverT = animate("g" + title, hover, 14f);
			float togT = animate("gt" + title, value.getAsBoolean(), 12f);
			cardFrame(context, cardX, y, cardW, CARD_H, hoverT, TILE_SIZE);
			drawPngIcon(context, icon, cardX, y, CARD_H, TILE_SIZE);
			textBlock(context, cardX, y, cardW, CARD_H, TILE_SIZE, title, desc);
			drawToggle(context, cardX + cardW - 14 - 28, y + (CARD_H - 14) / 2, togT, 28, 14);
			clickables.add(new Clickable(cardX, y, cardX + cardW, y + CARD_H, onClick));
		});
	}

	private Row buttonRow(DrawContext context, int mouseX, int mouseY, int cardX, int cardW,
			String icon, String title, String desc, String button, Runnable onClick) {
		return new Row(CARD_H + CARD_GAP, y -> {
			boolean hover = hovering(mouseX, mouseY, cardX, y, cardW, CARD_H);
			float hoverT = animate("g" + title, hover, 14f);
			cardFrame(context, cardX, y, cardW, CARD_H, hoverT, TILE_SIZE);
			drawPngIcon(context, icon, cardX, y, CARD_H, TILE_SIZE);
			textBlock(context, cardX, y, cardW, CARD_H, TILE_SIZE, title, desc);

				int bw = txtW(button, true) + 20;
			int bh = 20;
			int bx = cardX + cardW - 14 - bw;
			int by = y + (CARD_H - bh) / 2;
			sprite(context, bx, by, bw, bh, ACCENT);
			txtC(context, button, bx + bw / 2, by, bh, 0xFF15151A, true);

			clickables.add(new Clickable(bx, by, bx + bw, by + bh, onClick));
		});
	}

	// ---- gemeinsame Teile -------------------------------------------------

	private void cardFrame(DrawContext context, int cardX, int cardY, int cardW, int ch, float hoverT, int tileSize) {
		sprite(context, cardX + 1, cardY + 3, cardW, ch, SHADOW);
		sprite(context, cardX, cardY, cardW, ch, lerpColor(CARD, CARD_HOVER, hoverT));
		if (hoverT > 0.01f) {
			// dezente Akzent-Kante links beim Hover
			sprite(context, cardX, cardY + 6, 3, ch - 12, withAlpha(ACCENT, (int) (hoverT * 255)));
		}
		int tileY = cardY + (ch - tileSize) / 2;
		sprite(context, cardX + 8, tileY, tileSize, tileSize, TILE);
	}

	private void drawItemIcon(DrawContext context, Item item, int cardX, int cardY, int ch, int tileSize) {
		// MC-Items sind 16px-Pixelart: nativ in 16px zeichnen (kein krummer Skalierungsfaktor)
		// → scharf statt verwischt/blockig.
		int tileX = cardX + 8;
		int tileY = cardY + (ch - tileSize) / 2;
		int off = (tileSize - 16) / 2;
		context.drawItem(new ItemStack(item), tileX + off, tileY + off);
	}

	private void drawPngIcon(DrawContext context, String name, int cardX, int cardY, int ch, int tileSize) {
		int tileX = cardX + 8;
		int tileY = cardY + (ch - tileSize) / 2;
		int size = tileSize - 8;
		int off = (tileSize - size) / 2;
		drawTextureScaled(context, name, tileX + off, tileY + off, size);
	}

	private void textBlock(DrawContext context, int cardX, int cardY, int cardW, int ch, int tileSize,
			String title, String desc) {
		int textX = cardX + 8 + tileSize + 10;
		int descWidth = cardW - (8 + tileSize + 10) - 64;
		txt(context, title, textX, cardY + 8, TEXT, true);
		List<String> lines = wrap(desc, descWidth, 1);
		if (!lines.isEmpty()) {
			txt(context, lines.get(0), textX, cardY + 24, TEXT_DIM, false);
		}
	}

	private void drawToggle(DrawContext context, int x, int y, float t, int w, int h) {
		sprite(context, x, y, w, h, lerpColor(TOGGLE_OFF, TOGGLE_ON, t));
		int knob = h - 4;
		int knobX = x + 2 + (int) (t * (w - knob - 4));
		circle(context, knobX, y + 2, knob, KNOB);
	}

	private boolean hovering(int mouseX, int mouseY, int cardX, int cardY, int cardW, int ch) {
		return mouseX >= cardX && mouseX <= cardX + cardW && mouseY >= cardY && mouseY <= cardY + ch
				&& mouseY >= listTop && mouseY <= listBottom;
	}

	private String pngIcon(String name) {
		return name;
	}

	// ---- Zeichen-Hilfen (eigener SDF-Shader via UIRenderer) ---------------

	private void sprite(DrawContext c, int x, int y, int w, int h, int color) {
		if (w <= 0 || h <= 0) {
			return;
		}
		com.midgard.render.UIRenderer.fillRoundedRect(c, x, y, w, h, 7, color);
	}

	private void circle(DrawContext c, int x, int y, int d, int color) {
		com.midgard.render.UIRenderer.fillRoundedRect(c, x, y, d, d, d / 2, color);
	}

	private static double scaleFactor() {
		return MinecraftClient.getInstance().getWindow().getScaleFactor();
	}

	private void drawTextureScaled(DrawContext context, String name, int x, int y, int size) {
		double gs = scaleFactor();
		int dev = (int) Math.round(size * gs);
		Identifier id = com.midgard.render.MidgardTextures.deviceIcon(name, dev);
		Matrix3x2fStack m = context.getMatrices();
		m.pushMatrix();
		m.scale((float) (1.0 / gs), (float) (1.0 / gs)); // 1 Modell-Einheit == 1 Geräte-Pixel
		m.translate(Math.round(x * gs), Math.round(y * gs));
		context.drawTexture(RenderPipelines.GUI_TEXTURED, id, 0, 0, 0f, 0f, dev, dev, dev, dev, dev, dev, 0xFFFFFFFF);
		m.popMatrix();
	}

	/** Glattes Caret (AA-Dreieck): nach unten (aufgeklappt) bzw. rechts (zu), zentriert auf centerY. */
	private void drawCaret(DrawContext c, int x, int centerY, boolean expanded, int color) {
		double gs = scaleFactor();
		int sizeLog = 9;
		int dev = (int) Math.round(sizeLog * gs);
		Identifier id = com.midgard.render.MidgardTextures.caret(expanded, dev);
		Matrix3x2fStack m = c.getMatrices();
		m.pushMatrix();
		m.scale((float) (1.0 / gs), (float) (1.0 / gs));
		int dx = (int) Math.round(x * gs);
		int dy = (int) Math.round((centerY - sizeLog / 2.0) * gs);
		m.translate(dx, dy);
		c.drawTexture(RenderPipelines.GUI_TEXTURED, id, 0, 0, 0f, 0f, dev, dev, dev, dev, dev, dev, color);
		m.popMatrix();
	}

	// Eigenes SDF-Text-Rendering mit automatischem Rückfall auf die MC-Schrift.
	private void txt(DrawContext c, String s, int x, int y, int color, boolean bold) {
		if (!com.midgard.render.MidgardText.draw(c, s, x, y, FONT, color, bold)) {
			c.drawText(this.textRenderer, bold ? Fonts.bold(s) : Fonts.regular(s), x, y, color, false);
		}
	}

	private int txtW(String s, boolean bold) {
		int w = com.midgard.render.MidgardText.width(s, FONT, bold);
		return w >= 0 ? w : this.textRenderer.getWidth(bold ? Fonts.bold(s) : Fonts.regular(s));
	}

	/** Höhe eines Großbuchstabens (für vertikale Zentrierung). */
	private int capH() {
		int h = com.midgard.render.MidgardText.capHeight(FONT, true);
		return h > 0 ? h : 7;
	}

	/** Text links bei x, vertikal zentriert in [boxY, boxY+boxH]. */
	private void txtVC(DrawContext c, String s, int x, int boxY, int boxH, int color, boolean bold) {
		txt(c, s, x, boxY + (boxH - capH() + 1) / 2, color, bold);
	}

	/** Text horizontal UND vertikal zentriert um (centerX, boxY+boxH/2). */
	private void txtC(DrawContext c, String s, int centerX, int boxY, int boxH, int color, boolean bold) {
		txt(c, s, centerX - txtW(s, bold) / 2, boxY + (boxH - capH() + 1) / 2, color, bold);
	}

	private void txtScaled(DrawContext c, String s, int x, int y, int color, boolean bold, float scale) {
		if (!com.midgard.render.MidgardText.draw(c, s, x, y, FONT * scale, color, bold)) {
			drawScaledText(c, this.textRenderer, bold ? Fonts.bold(s) : Fonts.regular(s), x, y, color, scale);
		}
	}

	private void drawScaledText(DrawContext context, TextRenderer tr, Text text, int x, int y, int color, float scale) {
		Matrix3x2fStack m = context.getMatrices();
		m.pushMatrix();
		m.translate(x, y);
		m.scale(scale, scale);
		context.drawText(tr, text, 0, 0, color, false);
		m.popMatrix();
	}

	private static int withAlpha(int rgb, int alpha) {
		return ((alpha & 0xFF) << 24) | (rgb & 0xFFFFFF);
	}

	private static int lerpColor(int a, int b, float t) {
		t = Math.max(0, Math.min(1, t));
		int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
		int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
		return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16)
				| ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
	}

	private float animate(String id, boolean target, float speed) {
		float cur = anim.getOrDefault(id, target ? 1f : 0f);
		float goal = target ? 1f : 0f;
		if (cur < goal) {
			cur = Math.min(goal, cur + speed * dt);
		} else if (cur > goal) {
			cur = Math.max(goal, cur - speed * dt);
		}
		anim.put(id, cur);
		return cur;
	}

	private List<String> wrap(String text, int maxWidth, int maxLines) {
		List<String> lines = new ArrayList<>();
		StringBuilder cur = new StringBuilder();
		for (String word : text.split(" ")) {
			String test = cur.length() == 0 ? word : cur + " " + word;
			if (txtW(test, false) > maxWidth && cur.length() > 0) {
				lines.add(cur.toString());
				cur = new StringBuilder(word);
				if (lines.size() == maxLines - 1) {
					break;
				}
			} else {
				cur = new StringBuilder(test);
			}
		}
		if (lines.size() < maxLines && cur.length() > 0) {
			lines.add(cur.toString());
		}
		return lines;
	}

	// ---- Eingaben ---------------------------------------------------------

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
		return super.mouseClicked(click, doubled);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		scroll = Math.max(0, Math.min(scroll - verticalAmount * 22, maxScroll));
		return true;
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
