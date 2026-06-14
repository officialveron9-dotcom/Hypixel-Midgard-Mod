package com.midgard.auction;

import java.util.ArrayList;
import java.util.List;

import com.midgard.Midgard;
import com.midgard.render.MidgardText;
import com.midgard.render.UIRenderer;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.AbstractSignEditScreen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/**
 * Eigenes Auktions-Such-Menü, das das Hypixel-Such-SCHILD komplett ersetzt.
 * Live-Vorschläge während des Tippens (aus {@link ItemIndex}), Verlauf der
 * letzten Suchen und eine Sterne-Auswahl (lokal). Bestätigen schreibt den Text
 * ins ursprüngliche Schild und schließt es – dadurch führt Hypixel die Suche
 * normal aus (kein eigener Server-Verkehr, anti-cheat-unauffällig).
 */
public final class AuctionSearchScreen extends Screen {

	private static final int PANEL = 0xFF15151B;
	private static final int CARD = 0xFF1E1E26;
	private static final int CARD_HOVER = 0xFF2C2C3A;
	private static final int ACCENT = 0xFFF2772F;
	private static final int TEXT = 0xFFF1F1F4;
	private static final int TEXT_DIM = 0xFF8C8C97;
	private static final int DIM = 0xCC000000;
	private static final int BORDER = 0x33FFFFFF;
	private static final int STAR_GOLD = 0xFFFFAA00;
	private static final int STAR_RED = 0xFFFF5555;
	private static final float FONT = 9f;

	private record Clickable(int x1, int y1, int x2, int y2, Runnable action) {
		boolean has(double mx, double my) {
			return mx >= x1 && mx <= x2 && my >= y1 && my <= y2;
		}
	}

	private final AbstractSignEditScreen origin;
	private String query = "";
	private int stars;
	private List<ItemEntry> suggestions = List.of();
	private double suggScroll = 0;
	private final List<Clickable> clickables = new ArrayList<>();

	private int px, py, pw, ph;
	private int listTop, listBottom, listX, listW;

	public AuctionSearchScreen(AbstractSignEditScreen origin) {
		super(Text.literal("Auktions-Suche"));
		this.origin = origin;
	}

	@Override
	protected void init() {
		ItemIndex.INSTANCE.ensureLoaded();
		if (Midgard.config != null) {
			stars = Math.max(0, Math.min(10, Midgard.config.auctionStars));
		}
		recompute();
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	private void recompute() {
		suggestions = ItemIndex.INSTANCE.suggest(query, 100);
		suggScroll = 0;
	}

	// ---- Rendern ----------------------------------------------------------

	@Override
	public void renderBackground(DrawContext c, int mouseX, int mouseY, float delta) {
		c.fill(0, 0, this.width, this.height, DIM);
	}

	@Override
	public void render(DrawContext c, int mouseX, int mouseY, float delta) {
		super.render(c, mouseX, mouseY, delta);
		clickables.clear();

		pw = Math.min(this.width - 60, 560);
		ph = Math.min(this.height - 60, 360);
		px = (this.width - pw) / 2;
		py = (this.height - ph) / 2;
		int pad = 10;

		UIRenderer.fillRoundedRect(c, px - 1, py - 1, pw + 2, ph + 2, 8, BORDER);
		UIRenderer.fillRoundedRect(c, px, py, pw, ph, 7, PANEL);

		txt(c, "Auktions-Suche", px + pad, py + pad, TEXT, true);

		// --- Suchfeld -------------------------------------------------------
		int fieldY = py + pad + 16;
		int fieldH = 20;
		int fieldX = px + pad;
		int fieldW = pw - pad * 2;
		UIRenderer.fillRoundedRect(c, fieldX, fieldY, fieldW, fieldH, 5, CARD);
		String shown = query.isEmpty() ? "Item-Namen tippen…" : query;
		int qcol = query.isEmpty() ? TEXT_DIM : TEXT;
		txt(c, shown + (System.currentTimeMillis() % 1000 < 500 ? "_" : ""), fieldX + 8, fieldY + 6, qcol, false);
		// Clear-X
		if (!query.isEmpty()) {
			int xx = fieldX + fieldW - 16;
			txt(c, "✕", xx, fieldY + 6, TEXT_DIM, true);
			clickables.add(new Clickable(xx - 4, fieldY, fieldX + fieldW, fieldY + fieldH, () -> {
				query = "";
				recompute();
			}));
		}

		// --- Sterne-Reihe ---------------------------------------------------
		int starY = fieldY + fieldH + 8;
		txt(c, "Sterne:", fieldX, starY + 1, TEXT_DIM, false);
		int sx = fieldX + txtW("Sterne:", false) + 8;
		for (int i = 0; i < 10; i++) {
			int cx = sx + i * 14;
			boolean on = i < stars;
			int col = on ? (i < 5 ? STAR_GOLD : STAR_RED) : 0xFF3A3A44;
			star(c, cx, starY, col);
			final int lvl = i + 1;
			clickables.add(new Clickable(cx, starY, cx + 12, starY + 11,
					() -> setStars(stars == lvl ? lvl - 1 : lvl)));
		}
		txt(c, stars == 0 ? "(beliebig)" : (stars + "★"), sx + 10 * 14 + 6, starY + 1, TEXT_DIM, false);

		// --- Körper: links Vorschläge, rechts Verlauf -----------------------
		int bodyTop = starY + 16;
		int bodyBot = py + ph - 22;
		listX = fieldX;
		listW = (int) (fieldW * 0.62);
		listTop = bodyTop;
		listBottom = bodyBot;
		renderSuggestions(c, mouseX, mouseY);

		int histX = listX + listW + 8;
		int histW = fieldX + fieldW - histX;
		renderHistory(c, mouseX, mouseY, histX, bodyTop, histW, bodyBot);

		// --- Statuszeile ----------------------------------------------------
		String status = suggestions.size() + " Treffer · Enter = Suchen · Esc = Schließen";
		txt(c, status, fieldX, py + ph - 14, TEXT_DIM, false);
		if (stars > 0) {
			String hint = "Sterne-Filter wirkt nur lokal";
			txt(c, hint, fieldX + fieldW - txtW(hint, false), py + ph - 14, TEXT_DIM, false);
		}
	}

	private void renderSuggestions(DrawContext c, int mouseX, int mouseY) {
		int rowH = 16;
		int total = suggestions.size() * rowH;
		int visible = listBottom - listTop;
		double maxScroll = Math.max(0, total - visible);
		suggScroll = Math.max(0, Math.min(suggScroll, maxScroll));

		c.enableScissor(listX, listTop, listX + listW, listBottom);
		int y = listTop - (int) suggScroll;
		for (ItemEntry e : suggestions) {
			if (y + rowH > listTop && y < listBottom) {
				boolean hover = mouseX >= listX && mouseX <= listX + listW && mouseY >= y && mouseY <= y + rowH
						&& mouseY >= listTop && mouseY <= listBottom;
				if (hover) {
					UIRenderer.fillRoundedRect(c, listX, y, listW, rowH, 3, CARD_HOVER);
				}
				c.drawItem(new ItemStack(e.icon()), listX + 2, y);
				txt(c, trim(e.name(), listW - 24), listX + 21, y + 4, TEXT, false);
				final String name = e.name();
				clickables.add(new Clickable(listX, Math.max(listTop, y), listX + listW,
						Math.min(listBottom, y + rowH), () -> submit(name)));
			}
			y += rowH;
		}
		c.disableScissor();
		if (suggestions.isEmpty()) {
			String msg = query.isEmpty() ? "Tippe einen Item-Namen…"
					: (ItemIndex.INSTANCE.isLoaded() ? "Keine Treffer" : "Items werden geladen…");
			txt(c, msg, listX + 2, listTop + 4, TEXT_DIM, false);
		}
		// Scrollbalken
		if (maxScroll > 0) {
			int barH = Math.max(16, (int) ((float) visible / total * visible));
			int barY = listTop + (int) ((suggScroll / maxScroll) * (visible - barH));
			UIRenderer.fillRoundedRect(c, listX + listW - 3, barY, 3, barH, 1, ACCENT);
		}
	}

	private void renderHistory(DrawContext c, int mouseX, int mouseY, int x, int top, int w, int bot) {
		txt(c, "Letzte Suchen", x, top, TEXT_DIM, true);
		List<String> hist = Midgard.config != null ? Midgard.config.auctionHistory : List.of();
		int rowH = 14;
		int y = top + 14;
		for (int i = 0; i < hist.size() && y + rowH <= bot; i++) {
			String h = hist.get(i);
			boolean hover = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + rowH;
			if (hover) {
				UIRenderer.fillRoundedRect(c, x, y, w, rowH, 3, CARD_HOVER);
			}
			txt(c, trim(h, w - 16), x + 4, y + 3, TEXT, false);
			// Löschen-X
			int dxx = x + w - 12;
			txt(c, "✕", dxx, y + 3, TEXT_DIM, false);
			final String hv = h;
			clickables.add(new Clickable(dxx - 2, y, x + w, y + rowH, () -> removeHistory(hv)));
			clickables.add(new Clickable(x, y, dxx - 2, y + rowH, () -> submit(hv)));
			y += rowH;
		}
		if (hist.isEmpty()) {
			txt(c, "(noch leer)", x, top + 16, TEXT_DIM, false);
		} else if (y + rowH <= bot) {
			boolean hover = mouseX >= x && mouseX <= x + w && mouseY >= y + 2 && mouseY <= y + rowH + 2;
			txt(c, "Alles löschen", x + 4, y + 4, hover ? ACCENT : TEXT_DIM, false);
			clickables.add(new Clickable(x, y + 2, x + w, y + rowH + 2, this::clearHistory));
		}
	}

	/** Kleines Stern-Symbol (Raute) – die Roboto-Schrift hat ✪ evtl. nicht. */
	private void star(DrawContext c, int x, int y, int color) {
		int[] w = { 1, 3, 5, 3, 1 };
		for (int row = 0; row < 5; row++) {
			int ww = w[row];
			int sx = x + 1 + (5 - ww) / 2;
			c.fill(sx, y + 1 + row, sx + ww, y + 2 + row, color);
		}
	}

	// ---- Eingabe ----------------------------------------------------------

	@Override
	public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
		if (click.button() == 0) {
			for (Clickable cl : clickables) {
				if (cl.has(click.x(), click.y())) {
					cl.action().run();
					return true;
				}
			}
		}
		return super.mouseClicked(click, doubled);
	}

	@Override
	public boolean charTyped(CharInput input) {
		if (input.isValidChar()) {
			query += input.asString();
			recompute();
			return true;
		}
		return super.charTyped(input);
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		int k = input.key();
		if (k == 257 || k == 335) { // Enter / Numpad-Enter
			submit(query);
			return true;
		}
		if (k == 259) { // Backspace
			if (!query.isEmpty()) {
				query = query.substring(0, query.length() - 1);
				recompute();
			}
			return true;
		}
		if (k == 256) { // Esc -> Such-Menü schließen (zurück ins Spiel)
			close();
			return true;
		}
		return super.keyPressed(input);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double hor, double ver) {
		if (mx >= listX && mx <= listX + listW && my >= listTop && my <= listBottom) {
			suggScroll = Math.max(0, suggScroll - ver * 24);
			return true;
		}
		return super.mouseScrolled(mx, my, hor, ver);
	}

	// ---- Aktionen ---------------------------------------------------------

	private void setStars(int s) {
		stars = Math.max(0, Math.min(10, s));
		if (Midgard.config != null) {
			Midgard.config.auctionStars = stars;
			Midgard.config.save();
		}
	}

	/** Suche absenden: Text ins Original-Schild schreiben + schließen -> Hypixel sucht. */
	private void submit(String q) {
		if (q == null) {
			return;
		}
		q = q.trim();
		if (q.isEmpty()) {
			return;
		}
		addHistory(q);
		MinecraftClient mc = MinecraftClient.getInstance();
		try {
			String[] msgs = origin.messages;
			if (msgs != null && msgs.length > 0) {
				msgs[0] = q;
				for (int i = 1; i < msgs.length; i++) {
					msgs[i] = "";
				}
			}
			origin.close(); // -> finishEditing() schickt das Sign-Update = die Suche
		} catch (Throwable t) {
			System.err.println("[Midgard] Auktions-Suche absenden fehlgeschlagen: " + t);
			mc.setScreen(origin); // Notfall: das echte Schild zeigen
		}
	}

	private void addHistory(String q) {
		if (Midgard.config == null) {
			return;
		}
		List<String> h = Midgard.config.auctionHistory;
		h.removeIf(s -> s.equalsIgnoreCase(q));
		h.add(0, q);
		while (h.size() > 20) {
			h.remove(h.size() - 1);
		}
		Midgard.config.save();
	}

	private void removeHistory(String q) {
		if (Midgard.config == null) {
			return;
		}
		Midgard.config.auctionHistory.removeIf(s -> s.equalsIgnoreCase(q));
		Midgard.config.save();
	}

	private void clearHistory() {
		if (Midgard.config == null) {
			return;
		}
		Midgard.config.auctionHistory.clear();
		Midgard.config.save();
	}

	// ---- Text-Helfer ------------------------------------------------------

	private void txt(DrawContext c, String s, int x, int y, int color, boolean bold) {
		if (!MidgardText.draw(c, s, x, y, FONT, color, bold)) {
			c.drawText(this.textRenderer, s, x, y, color, false);
		}
	}

	private int txtW(String s, boolean bold) {
		int w = MidgardText.width(s, FONT, bold);
		return w >= 0 ? w : this.textRenderer.getWidth(s);
	}

	/** Kürzt einen Text so, dass er in {@code maxW} Pixel passt (mit …). */
	private String trim(String s, int maxW) {
		if (txtW(s, false) <= maxW) {
			return s;
		}
		while (s.length() > 1 && txtW(s + "…", false) > maxW) {
			s = s.substring(0, s.length() - 1);
		}
		return s + "…";
	}
}
