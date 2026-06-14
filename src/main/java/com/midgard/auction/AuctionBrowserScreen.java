package com.midgard.auction;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.midgard.Midgard;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.AbstractSignEditScreen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

/**
 * Eigener Auction-Browser im Minecraft-Stil: durchsuchbare Live-Liste echter
 * Auktionen (echte Item-Köpfe/Skins), Sidebar-Filter (Kategorie, Rarität, Typ,
 * Sortierung, Sterne), Seiten-Blättern. Daten aus {@link AuctionData}.
 */
public final class AuctionBrowserScreen extends Screen {

	private static final int BG = 0xFFC6C6C6;
	private static final int HI = 0xFFFFFFFF;
	private static final int SH = 0xFF555555;
	private static final int BLACK = 0xFF000000;
	private static final int SLOT = 0xFF8B8B8B;
	private static final int SLOT_DARK = 0xFF373737;
	private static final int LABEL = 0xFF404040;

	private static final int PER_PAGE = 12;

	// Kategorie- und Raritäts-Zyklen (Anzeige -> API-Wert).
	private static final String[][] CATS = {
			{ "Alle", "" }, { "Waffen", "weapon" }, { "Rüstung", "armor" },
			{ "Accessoires", "accessories" }, { "Verbrauch", "consumables" },
			{ "Blöcke", "blocks" }, { "Sonstiges", "misc" } };
	private static final String[][] RARITIES = {
			{ "Alle", "" }, { "Common", "COMMON" }, { "Uncommon", "UNCOMMON" }, { "Rare", "RARE" },
			{ "Epic", "EPIC" }, { "Legendary", "LEGENDARY" }, { "Mythic", "MYTHIC" },
			{ "Divine", "DIVINE" }, { "Special", "SPECIAL" } };

	private record Clickable(int x1, int y1, int x2, int y2, Runnable action) {
		boolean has(double mx, double my) {
			return mx >= x1 && mx <= x2 && my >= y1 && my <= y2;
		}
	}

	private final AbstractSignEditScreen origin;
	private final AuctionFilter filter = new AuctionFilter();
	private int catIdx = 0;
	private int rarIdx = 0;
	private List<Auction> results = List.of();
	private int page = 0;
	private final List<Clickable> clickables = new ArrayList<>();

	private int px, py, pw, ph;
	private int listX, listW, listTop, listBottom;
	private List<Text> tip;
	private int tipX, tipY;

	public AuctionBrowserScreen(AbstractSignEditScreen origin) {
		super(Text.literal("Auction Browser"));
		this.origin = origin;
	}

	@Override
	protected void init() {
		AuctionData.INSTANCE.setBrowserOpen(true);
		recompute();
	}

	@Override
	public void removed() {
		AuctionData.INSTANCE.setBrowserOpen(false);
		super.removed();
	}

	@Override
	public void tick() {
		AuctionData.INSTANCE.maybeRefresh();
		// Während des Ladens die Liste aktuell halten.
		if (results.isEmpty() && AuctionData.INSTANCE.isLoaded()) {
			recompute();
		}
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	private void recompute() {
		filter.category = CATS[catIdx][1];
		filter.rarities.clear();
		if (!RARITIES[rarIdx][1].isEmpty()) {
			filter.rarities.add(RARITIES[rarIdx][1]);
		}
		results = AuctionData.INSTANCE.query(filter);
		page = 0;
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
		tip = null;

		pw = Math.min(this.width - 30, 580);
		ph = Math.min(this.height - 30, 400);
		px = (this.width - pw) / 2;
		py = (this.height - ph) / 2;
		int pad = 8;

		panel(c, px, py, pw, ph);
		c.drawText(this.textRenderer, "Auction Browser", px + pad, py + 6, LABEL, false);
		// Schließen-X
		int clx = px + pw - 14;
		c.drawText(this.textRenderer, "x", clx, py + 6, SH, false);
		clickables.add(new Clickable(clx - 3, py + 4, px + pw - 2, py + 14, this::close));

		// Suchfeld (oben, ueber Sidebar+Liste)
		int searchY = py + 18;
		int searchH = 16;
		recessed(c, px + pad, searchY, pw - pad * 2, searchH);
		String shown = filter.search.isEmpty() ? "Suche…" : filter.search;
		c.drawText(this.textRenderer, shown + (System.currentTimeMillis() % 1000 < 500 ? "_" : ""),
				px + pad + 4, searchY + 4, filter.search.isEmpty() ? SH : 0xFF2A2A2A, false);

		int bodyTop = searchY + searchH + 6;
		int bodyBot = py + ph - 16;

		// --- Sidebar (links) ------------------------------------------------
		int sideX = px + pad;
		int sideW = 116;
		renderSidebar(c, mouseX, mouseY, sideX, bodyTop, sideW, bodyBot);

		// --- Liste (rechts) -------------------------------------------------
		listX = sideX + sideW + 6;
		listW = px + pw - pad - listX;
		listTop = bodyTop;
		listBottom = bodyBot - 12;
		recessed(c, listX, listTop, listW, listBottom - listTop);
		renderList(c, mouseX, mouseY);

		// --- Fußleiste: Seiten + Treffer -----------------------------------
		int totalPages = Math.max(1, (results.size() + PER_PAGE - 1) / PER_PAGE);
		page = Math.max(0, Math.min(page, totalPages - 1));
		int fy = py + ph - 11;
		c.drawText(this.textRenderer, "< Zurück", listX, fy, page > 0 ? LABEL : SH, false);
		clickables.add(new Clickable(listX, fy - 2, listX + 44, fy + 9, () -> {
			if (page > 0) {
				page--;
			}
		}));
		String mid = (page + 1) + "/" + totalPages;
		c.drawText(this.textRenderer, mid, listX + listW / 2 - this.textRenderer.getWidth(mid) / 2, fy, LABEL, false);
		int vx = listX + listW - this.textRenderer.getWidth("Vor >");
		c.drawText(this.textRenderer, "Vor >", vx, fy, page < totalPages - 1 ? LABEL : SH, false);
		clickables.add(new Clickable(vx - 4, fy - 2, listX + listW, fy + 9, () -> {
			if (page < totalPages - 1) {
				page++;
			}
		}));
		String cnt = AuctionData.INSTANCE.isLoaded() ? (results.size() + " Treffer")
				: (AuctionData.INSTANCE.isFetching()
						? ("Lädt… " + Math.round(AuctionData.INSTANCE.progress() * 100) + "%")
						: "Lädt…");
		c.drawText(this.textRenderer, cnt, sideX, fy, LABEL, false);

		if (tip != null) {
			c.drawTooltip(this.textRenderer, tip, tipX, tipY);
		}
	}

	private void renderSidebar(DrawContext c, int mouseX, int mouseY, int x, int top, int w, int bot) {
		int y = top;
		y = cycleRow(c, mouseX, mouseY, x, y, w, "Kategorie", CATS[catIdx][0], () -> {
			catIdx = (catIdx + 1) % CATS.length;
			recompute();
		});
		y = cycleRow(c, mouseX, mouseY, x, y, w, "Rarität", RARITIES[rarIdx][0], () -> {
			rarIdx = (rarIdx + 1) % RARITIES.length;
			recompute();
		});
		y = cycleRow(c, mouseX, mouseY, x, y, w, "Typ", filter.type.label, () -> {
			filter.type = AuctionFilter.Type.values()[(filter.type.ordinal() + 1) % 3];
			recompute();
		});
		y = cycleRow(c, mouseX, mouseY, x, y, w, "Sortierung", filter.sort.label, () -> {
			filter.sort = AuctionFilter.Sort.values()[(filter.sort.ordinal() + 1) % 4];
			recompute();
		});
		// Sterne min/max
		c.drawText(this.textRenderer, "Sterne " + filter.starMin + "–" + filter.starMax, x, y + 2, LABEL, false);
		y += 11;
		y = stepperRow(c, mouseX, mouseY, x, y, w, "Min", filter.starMin, v -> {
			filter.starMin = Math.max(0, Math.min(filter.starMax, filter.starMin + v));
			recompute();
		});
		y = stepperRow(c, mouseX, mouseY, x, y, w, "Max", filter.starMax, v -> {
			filter.starMax = Math.max(filter.starMin, Math.min(10, filter.starMax + v));
			recompute();
		});
		// Reset
		y += 4;
		boolean rh = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + 12;
		button(c, x, y, w, 12, "Filter zurücksetzen", rh);
		clickables.add(new Clickable(x, y, x + w, y + 12, () -> {
			catIdx = 0;
			rarIdx = 0;
			filter.type = AuctionFilter.Type.BIN;
			filter.sort = AuctionFilter.Sort.LOWEST;
			filter.starMin = 0;
			filter.starMax = 10;
			filter.search = "";
			recompute();
		}));
	}

	private int cycleRow(DrawContext c, int mx, int my, int x, int y, int w, String label, String val, Runnable act) {
		c.drawText(this.textRenderer, label, x, y + 2, LABEL, false);
		y += 11;
		boolean h = mx >= x && mx <= x + w && my >= y && my <= y + 13;
		button(c, x, y, w, 13, val, h);
		clickables.add(new Clickable(x, y, x + w, y + 13, act));
		return y + 17;
	}

	private int stepperRow(DrawContext c, int mx, int my, int x, int y, int w, String label, int val,
			java.util.function.IntConsumer step) {
		c.drawText(this.textRenderer, label + ": " + val, x + 22, y + 2, LABEL, false);
		boolean hm = mx >= x && mx <= x + 14 && my >= y && my <= y + 11;
		button(c, x, y, 14, 11, "−", hm);
		clickables.add(new Clickable(x, y, x + 14, y + 11, () -> step.accept(-1)));
		boolean hp = mx >= x + w - 14 && mx <= x + w && my >= y && my <= y + 11;
		button(c, x + w - 14, y, 14, 11, "+", hp);
		clickables.add(new Clickable(x + w - 14, y, x + w, y + 11, () -> step.accept(1)));
		return y + 14;
	}

	private void renderList(DrawContext c, int mouseX, int mouseY) {
		int rowH = (listBottom - listTop - 4) / PER_PAGE;
		int start = page * PER_PAGE;
		c.enableScissor(listX + 1, listTop + 1, listX + listW - 1, listBottom - 1);
		long now = System.currentTimeMillis();
		for (int i = 0; i < PER_PAGE; i++) {
			int idx = start + i;
			if (idx >= results.size()) {
				break;
			}
			Auction a = results.get(idx);
			int y = listTop + 2 + i * rowH;
			boolean hover = mouseX >= listX && mouseX <= listX + listW - 2 && mouseY >= y && mouseY <= y + rowH;
			if (hover) {
				c.fill(listX + 1, y, listX + listW - 1, y + rowH, 0x33000000);
				tip = tooltip(a, now);
				tipX = mouseX;
				tipY = mouseY;
			}
			ItemStack icon = iconFor(a);
			c.drawItem(icon, listX + 3, y + (rowH - 16) / 2);
			String name = (a.stars() > 0 ? a.stars() + "✪ " : "") + AuctionData.strip(a.itemName());
			c.drawText(this.textRenderer, ellipsis(name, listW - 100), listX + 23, y + (rowH - 8) / 2,
					tierColor(a.tier()) & 0xFFFFFF | 0xFF000000, false);
			String price = priceShort(a.price()) + (a.bin() ? "" : "*");
			c.drawText(this.textRenderer, price, listX + listW - 6 - this.textRenderer.getWidth(price),
					y + (rowH - 8) / 2, 0xFF2E5E2E, false);
			final String click = a.cleanName();
			clickables.add(new Clickable(listX, y, listX + listW - 2, y + rowH, () -> openInHypixel(click)));
		}
		c.disableScissor();
		if (results.isEmpty()) {
			String msg = AuctionData.INSTANCE.isLoaded() ? "Keine Treffer" : "Auktionen werden geladen…";
			c.drawText(this.textRenderer, msg, listX + 6, listTop + 6, SH, false);
		}
	}

	private ItemStack iconFor(Auction a) {
		ItemStack head = AuctionItemParser.head(a.skinValue());
		return head != null ? head : new ItemStack(Items.PAPER);
	}

	private List<Text> tooltip(Auction a, long now) {
		List<Text> t = new ArrayList<>();
		t.add(Text.literal(AuctionData.strip(a.itemName())).withColor(tierColor(a.tier()) & 0xFFFFFF));
		t.add(Text.literal((a.bin() ? "Sofortkauf" : "Auktion · " + a.bidCount() + " Gebote"))
				.withColor(0xAAAAAA));
		t.add(Text.literal("Preis: " + priceShort(a.price()) + " Coins").withColor(0x55FF55));
		long left = a.end() - now;
		if (left > 0) {
			t.add(Text.literal("Endet in " + timeShort(left)).withColor(0xFFFF55));
		}
		t.add(Text.literal("Klick: im AH öffnen").withColor(0x888888));
		return t;
	}

	// ---- MC-Stil ----------------------------------------------------------

	private void panel(DrawContext c, int x, int y, int w, int h) {
		c.fill(x - 1, y - 1, x + w + 1, y + h + 1, BLACK);
		c.fill(x, y, x + w, y + h, BG);
		c.fill(x, y, x + w, y + 2, HI);
		c.fill(x, y, x + 2, y + h, HI);
		c.fill(x, y + h - 2, x + w, y + h, SH);
		c.fill(x + w - 2, y, x + w, y + h, SH);
	}

	private void recessed(DrawContext c, int x, int y, int w, int h) {
		c.fill(x, y, x + w, y + h, SLOT);
		c.fill(x, y, x + w, y + 1, SLOT_DARK);
		c.fill(x, y, x + 1, y + h, SLOT_DARK);
		c.fill(x, y + h - 1, x + w, y + h, HI);
		c.fill(x + w - 1, y, x + w, y + h, HI);
	}

	private void button(DrawContext c, int x, int y, int w, int h, String txt, boolean hover) {
		c.fill(x, y, x + w, y + h, hover ? 0xFF6A6A78 : 0xFF565663);
		c.fill(x, y, x + w, y + 1, 0xFF8A8A98);
		c.fill(x, y + h - 1, x + w, y + h, 0xFF2A2A30);
		c.drawText(this.textRenderer, ellipsis(txt, w - 4),
				x + w / 2 - Math.min(this.textRenderer.getWidth(txt), w - 4) / 2, y + (h - 8) / 2, 0xFFEDEDED, false);
	}

	private String ellipsis(String s, int maxW) {
		if (this.textRenderer.getWidth(s) <= maxW) {
			return s;
		}
		return this.textRenderer.trimToWidth(s, maxW - 4) + "…";
	}

	private static int tierColor(String tier) {
		if (tier == null) {
			return 0xFF555555;
		}
		return switch (tier.toUpperCase(Locale.ROOT)) {
			case "UNCOMMON" -> 0xFF1F7A1F;
			case "RARE" -> 0xFF2222CC;
			case "EPIC" -> 0xFF7A1F7A;
			case "LEGENDARY" -> 0xFFB87A00;
			case "MYTHIC" -> 0xFFCC44CC;
			case "DIVINE" -> 0xFF1F8A8A;
			case "SPECIAL", "VERY_SPECIAL" -> 0xFFCC2222;
			default -> 0xFF404040;
		};
	}

	private static String priceShort(long v) {
		if (v >= 1_000_000_000L) {
			return round(v / 1_000_000_000.0) + "b";
		}
		if (v >= 1_000_000L) {
			return round(v / 1_000_000.0) + "m";
		}
		if (v >= 1_000L) {
			return round(v / 1_000.0) + "k";
		}
		return String.valueOf(v);
	}

	private static String round(double d) {
		return (d >= 100 ? String.valueOf((long) d) : String.format(Locale.ROOT, "%.1f", d));
	}

	private static String timeShort(long ms) {
		long s = ms / 1000;
		if (s >= 3600) {
			return (s / 3600) + "h " + ((s % 3600) / 60) + "m";
		}
		if (s >= 60) {
			return (s / 60) + "m";
		}
		return s + "s";
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
			filter.search += input.asString();
			recompute();
			return true;
		}
		return super.charTyped(input);
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		int k = input.key();
		if (k == 259) {
			if (!filter.search.isEmpty()) {
				filter.search = filter.search.substring(0, filter.search.length() - 1);
				recompute();
			}
			return true;
		}
		if (k == 256) {
			close();
			return true;
		}
		if (k == 266) { // Page Up
			if (page > 0) {
				page--;
			}
			return true;
		}
		if (k == 267) { // Page Down
			page++;
			return true;
		}
		return super.keyPressed(input);
	}

	// ---- Aktion: im echten AH oeffnen -------------------------------------

	/** Sendet die Suche an Hypixel (per Such-Schild), damit der Spieler dort kauft. */
	private void openInHypixel(String name) {
		MinecraftClient mc = MinecraftClient.getInstance();
		try {
			net.minecraft.block.entity.SignBlockEntity be = origin.blockEntity;
			if (mc.getNetworkHandler() != null) {
				mc.getNetworkHandler().sendPacket(new net.minecraft.network.packet.c2s.play.UpdateSignC2SPacket(
						be.getPos(), true, name, "", "", ""));
			}
		} catch (Throwable t) {
			System.err.println("[Midgard] AH öffnen fehlgeschlagen: " + t);
		}
		mc.setScreen(null);
	}
}
