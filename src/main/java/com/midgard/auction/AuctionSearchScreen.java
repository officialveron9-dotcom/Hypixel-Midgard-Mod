package com.midgard.auction;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.midgard.Midgard;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.AbstractSignEditScreen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/**
 * Eigenes Auktions-Such-Menü im MINECRAFT-Stil (wie Inventar/Auktionshaus):
 * grauer Bevel-Rahmen, vertiefte Slots, Vanilla-Schrift. Ersetzt das
 * Hypixel-Such-Schild. Live-Vorschläge, Verlauf, Sterne-Auswahl (lokal) und
 * Item-Stats beim Drüberfahren. Bestätigen schickt die Suche direkt als Packet.
 */
public final class AuctionSearchScreen extends Screen {

	// Vanilla-GUI-Farben (Inventar/Container).
	private static final int BG = 0xFFC6C6C6;
	private static final int HI = 0xFFFFFFFF;
	private static final int SH = 0xFF555555;
	private static final int BLACK = 0xFF000000;
	private static final int SLOT = 0xFF8B8B8B;
	private static final int SLOT_DARK = 0xFF373737;
	private static final int LABEL = 0xFF404040;
	private static final int TEXT_IN = 0xFF2A2A2A;
	private static final int STAR_GOLD = 0xFFFFAA00;
	private static final int STAR_RED = 0xFFFF5555;
	private static final int STAR_OFF = 0xFF808080;

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
	private ItemEntry hovered; // für den Tooltip am Frame-Ende
	private int hovMx, hovMy;

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
	public void removed() {
		super.removed();
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
		c.fill(0, 0, this.width, this.height, 0xC0000000);
	}

	@Override
	public void render(DrawContext c, int mouseX, int mouseY, float delta) {
		super.render(c, mouseX, mouseY, delta);
		clickables.clear();
		hovered = null;

		pw = Math.min(this.width - 40, 520);
		ph = Math.min(this.height - 40, 340);
		px = (this.width - pw) / 2;
		py = (this.height - ph) / 2;
		int pad = 8;

		panel(c, px, py, pw, ph);
		c.drawText(this.textRenderer, "Auktions-Suche", px + pad, py + 6, LABEL, false);

		// --- Suchfeld (links) + Sterne (rechts daneben) ---------------------
		int rowY = py + 18;
		int rowH = 18;
		int starsW = 10 * 9 + 28; // Platz für 10 Sterne + Zähler
		int fieldX = px + pad;
		int fieldW = pw - pad * 2 - starsW - 8;
		recessed(c, fieldX, rowY, fieldW, rowH);
		String shown = query.isEmpty() ? "Item-Namen tippen…" : query;
		int qcol = query.isEmpty() ? SH : TEXT_IN;
		String caret = System.currentTimeMillis() % 1000 < 500 ? "_" : "";
		c.drawText(this.textRenderer, shown + caret, fieldX + 4, rowY + 5, qcol, false);
		if (!query.isEmpty()) {
			int xx = fieldX + fieldW - 10;
			c.drawText(this.textRenderer, "x", xx, rowY + 5, SH, false);
			clickables.add(new Clickable(xx - 3, rowY, fieldX + fieldW, rowY + rowH, () -> {
				query = "";
				recompute();
			}));
		}

		// Sterne rechts neben dem Suchfeld
		int starX = fieldX + fieldW + 8;
		c.drawText(this.textRenderer, "Sterne", starX, rowY - 8, LABEL, false);
		for (int i = 0; i < 10; i++) {
			int sx = starX + i * 9;
			boolean on = i < stars;
			int col = on ? (i < 5 ? STAR_GOLD : STAR_RED) : STAR_OFF;
			c.drawText(this.textRenderer, "✪", sx, rowY + 5, col, false);
			final int lvl = i + 1;
			clickables.add(new Clickable(sx, rowY, sx + 9, rowY + rowH, () -> setStars(stars == lvl ? lvl - 1 : lvl)));
		}
		c.drawText(this.textRenderer, stars == 0 ? "0" : String.valueOf(stars),
				starX + 10 * 9 + 4, rowY + 5, LABEL, false);

		// --- Körper: Vorschläge links, Verlauf rechts -----------------------
		int bodyTop = rowY + rowH + 6;
		int bodyBot = py + ph - 16;
		listX = fieldX;
		listW = (int) (pw - pad * 2) * 62 / 100;
		listTop = bodyTop;
		listBottom = bodyBot;
		recessed(c, listX, listTop, listW, bodyBot - bodyTop);
		renderSuggestions(c, mouseX, mouseY);

		int histX = listX + listW + 8;
		int histW = px + pw - pad - histX;
		recessed(c, histX, bodyTop, histW, bodyBot - bodyTop);
		renderHistory(c, mouseX, mouseY, histX, bodyTop, histW, bodyBot);

		// --- Statuszeile ----------------------------------------------------
		String status = suggestions.size() + " Treffer · Enter = Suchen · Esc = Schließen";
		c.drawText(this.textRenderer, status, fieldX, py + ph - 11, LABEL, false);
		if (stars > 0) {
			String hint = "Sterne nur lokal";
			c.drawText(this.textRenderer, hint, px + pw - pad - this.textRenderer.getWidth(hint),
					py + ph - 11, LABEL, false);
		}

		// Tooltip ganz zum Schluss (über allem).
		if (hovered != null) {
			c.drawTooltip(this.textRenderer, tooltip(hovered), hovMx, hovMy);
		}
	}

	private void renderSuggestions(DrawContext c, int mouseX, int mouseY) {
		int rowH = 18;
		int total = suggestions.size() * rowH;
		int visible = listBottom - listTop - 4;
		double maxScroll = Math.max(0, total - visible);
		suggScroll = Math.max(0, Math.min(suggScroll, maxScroll));

		c.enableScissor(listX + 1, listTop + 1, listX + listW - 1, listBottom - 1);
		int y = listTop + 2 - (int) suggScroll;
		for (ItemEntry e : suggestions) {
			if (y + rowH > listTop && y < listBottom) {
				boolean hover = mouseX >= listX && mouseX <= listX + listW - 4 && mouseY >= y && mouseY <= y + rowH
						&& mouseY >= listTop && mouseY <= listBottom;
				if (hover) {
					c.fill(listX + 1, Math.max(listTop + 1, y), listX + listW - 1, Math.min(listBottom - 1, y + rowH),
							0x40000000);
					hovered = e;
					hovMx = mouseX;
					hovMy = mouseY;
				}
				ItemStack icon = e.icon();
				c.drawItem(icon, listX + 3, y + 1);
				c.drawText(this.textRenderer, ellipsis(e.name(), listW - 26), listX + 23, y + 5, LABEL, false);
				final String name = e.name();
				clickables.add(new Clickable(listX, Math.max(listTop, y), listX + listW - 4,
						Math.min(listBottom, y + rowH), () -> submit(name)));
			}
			y += rowH;
		}
		c.disableScissor();
		if (suggestions.isEmpty()) {
			String msg = query.isEmpty() ? "Tippe einen Item-Namen…"
					: (ItemIndex.INSTANCE.isLoaded() ? "Keine Treffer" : "Items werden geladen…");
			c.drawText(this.textRenderer, msg, listX + 4, listTop + 5, SH, false);
		}
		if (maxScroll > 0) {
			int barH = Math.max(14, (int) ((float) visible / total * visible));
			int barY = listTop + 2 + (int) ((suggScroll / maxScroll) * (visible - barH));
			c.fill(listX + listW - 3, barY, listX + listW - 1, barY + barH, 0xFF555555);
		}
	}

	private void renderHistory(DrawContext c, int mouseX, int mouseY, int x, int top, int w, int bot) {
		c.drawText(this.textRenderer, "Letzte Suchen", x + 3, top + 3, LABEL, false);
		List<String> hist = Midgard.config != null ? Midgard.config.auctionHistory : List.of();
		int rowH = 13;
		int y = top + 16;
		for (int i = 0; i < hist.size() && y + rowH <= bot; i++) {
			String h = hist.get(i);
			boolean hover = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + rowH;
			if (hover) {
				c.fill(x + 1, y, x + w - 1, y + rowH, 0x40000000);
			}
			c.drawText(this.textRenderer, ellipsis(h, w - 16), x + 4, y + 3, LABEL, false);
			int dxx = x + w - 11;
			c.drawText(this.textRenderer, "x", dxx, y + 3, SH, false);
			final String hv = h;
			clickables.add(new Clickable(dxx - 2, y, x + w, y + rowH, () -> removeHistory(hv)));
			clickables.add(new Clickable(x, y, dxx - 2, y + rowH, () -> submit(hv)));
			y += rowH;
		}
		if (hist.isEmpty()) {
			c.drawText(this.textRenderer, "(leer)", x + 4, top + 16, SH, false);
		} else if (y + rowH <= bot) {
			boolean hover = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + rowH;
			c.drawText(this.textRenderer, "Alles löschen", x + 4, y + 3, hover ? 0xFFAA0000 : SH, false);
			clickables.add(new Clickable(x, y, x + w, y + rowH, this::clearHistory));
		}
	}

	// ---- MC-Stil-Helfer ---------------------------------------------------

	/** Grauer Bevel-Rahmen wie Inventar/Container. */
	private void panel(DrawContext c, int x, int y, int w, int h) {
		c.fill(x - 1, y - 1, x + w + 1, y + h + 1, BLACK);
		c.fill(x, y, x + w, y + h, BG);
		c.fill(x, y, x + w, y + 2, HI);
		c.fill(x, y, x + 2, y + h, HI);
		c.fill(x, y + h - 2, x + w, y + h, SH);
		c.fill(x + w - 2, y, x + w, y + h, SH);
	}

	/** Vertiefter Bereich (Slot-Look): dunkel oben/links, hell unten/rechts. */
	private void recessed(DrawContext c, int x, int y, int w, int h) {
		c.fill(x, y, x + w, y + h, SLOT);
		c.fill(x, y, x + w, y + 1, SLOT_DARK);
		c.fill(x, y, x + 1, y + h, SLOT_DARK);
		c.fill(x, y + h - 1, x + w, y + h, HI);
		c.fill(x + w - 1, y, x + w, y + h, HI);
	}

	private String ellipsis(String s, int maxW) {
		if (this.textRenderer.getWidth(s) <= maxW) {
			return s;
		}
		return this.textRenderer.trimToWidth(s, maxW - this.textRenderer.getWidth("…")) + "…";
	}

	// ---- Tooltip ----------------------------------------------------------

	private List<Text> tooltip(ItemEntry e) {
		List<Text> t = new ArrayList<>();
		t.add(Text.literal(e.name()).withColor(tierColor(e.tier()) & 0xFFFFFF));
		String sub = prettyKey(e.category());
		if (e.tier() != null && !e.tier().isEmpty()) {
			sub = (sub.isEmpty() ? "" : sub + " · ") + prettyKey(e.tier());
		}
		if (!sub.isEmpty()) {
			t.add(Text.literal(sub).withColor(0xAAAAAA));
		}
		Map<String, Double> st = e.stats();
		if (st != null && !st.isEmpty()) {
			t.add(Text.literal(""));
			for (Map.Entry<String, Double> s : st.entrySet()) {
				if (s.getValue() == 0) {
					continue;
				}
				String sign = s.getValue() > 0 ? "+" : "";
				t.add(Text.literal(sign + num(s.getValue()) + " " + prettyKey(s.getKey())).withColor(0x55FF55));
			}
		}
		if (e.npcSell() > 0) {
			t.add(Text.literal("NPC: " + num(e.npcSell()) + " Coins").withColor(0xFFFF55));
		}
		return t;
	}

	private static String prettyKey(String k) {
		if (k == null || k.isEmpty()) {
			return "";
		}
		String[] parts = k.toLowerCase(Locale.ROOT).replace('_', ' ').split(" ");
		StringBuilder b = new StringBuilder();
		for (String p : parts) {
			if (p.isEmpty()) {
				continue;
			}
			b.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(' ');
		}
		return b.toString().trim();
	}

	private static String num(double v) {
		if (v == Math.floor(v)) {
			return String.valueOf((long) v);
		}
		return String.valueOf(v);
	}

	private static int tierColor(String tier) {
		if (tier == null) {
			return 0xFFFFFFFF;
		}
		return switch (tier.toUpperCase(Locale.ROOT)) {
			case "UNCOMMON" -> 0xFF55FF55;
			case "RARE" -> 0xFF5555FF;
			case "EPIC" -> 0xFFAA00AA;
			case "LEGENDARY" -> 0xFFFFAA00;
			case "MYTHIC" -> 0xFFFF55FF;
			case "DIVINE" -> 0xFF55FFFF;
			case "SPECIAL", "VERY_SPECIAL" -> 0xFFFF5555;
			case "SUPREME" -> 0xFFAA0000;
			default -> 0xFFFFFFFF;
		};
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
		if (k == 257 || k == 335) {
			submit(query);
			return true;
		}
		if (k == 259) {
			if (!query.isEmpty()) {
				query = query.substring(0, query.length() - 1);
				recompute();
			}
			return true;
		}
		if (k == 256) {
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

	/** Suche absenden: Sign-Update direkt als Packet, dann Menü schließen. */
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
			net.minecraft.block.entity.SignBlockEntity be = origin.blockEntity;
			net.minecraft.util.math.BlockPos pos = be.getPos();
			if (mc.getNetworkHandler() != null) {
				mc.getNetworkHandler().sendPacket(
						new net.minecraft.network.packet.c2s.play.UpdateSignC2SPacket(pos, true, q, "", "", ""));
			}
		} catch (Throwable t) {
			System.err.println("[Midgard] Auktions-Suche absenden fehlgeschlagen: " + t);
		}
		mc.setScreen(null);
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
}
