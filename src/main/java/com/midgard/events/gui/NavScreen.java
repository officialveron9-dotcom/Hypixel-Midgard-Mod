package com.midgard.events.gui;

import java.util.ArrayList;
import java.util.List;

import com.midgard.mining.CrystalNav;
import com.midgard.mining.MiningData;
import com.midgard.mining.MiningWaypoints;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Navi-Ziel wählen. In Crystal Hollows: alle Orte (entdeckte/Nucleus anklickbar,
 * Rest ausgegraut). In den Dwarven Mines: die Emissär-Gebiete plus "Auto"
 * (zurück zur automatischen Commission-Navigation). Unten: nahe NPCs anzeigen
 * und laufende Navigation abbrechen.
 */
public class NavScreen extends Screen {

	private static final int PANEL = 0xF2121419;
	private static final int ACCENT = 0xFFD06BFF;
	private static final int TEXT = 0xFFF1F1F4;
	private static final int DIM = 0xFF6E6E78;
	private static final int CARD = 0xFF1E1E26;
	private static final int CARD_HOVER = 0xFF2C2C3A;
	private static final int BORDER = 0x33FFFFFF;
	private static final int RED = 0xFFE0443C;

	/** Eine Zeile in der Auswahl. */
	private record NavRow(String label, String tag, boolean enabled, boolean active, Runnable action) {
	}

	private record Clickable(int x1, int y1, int x2, int y2, Runnable action) {
		boolean contains(double mx, double my) {
			return mx >= x1 && mx <= x2 && my >= y1 && my <= y2;
		}
	}

	private final Screen parent;
	private final List<Clickable> clickables = new ArrayList<>();

	public NavScreen(Screen parent) {
		super(Text.literal("Navi-Ziel"));
		this.parent = parent;
	}

	/** Baut die Zeilen je nach aktuellem Ort. */
	private List<NavRow> buildRows() {
		List<NavRow> rows = new ArrayList<>();
		if (MiningData.INSTANCE.onCrystalHollows) {
			for (String loc : CrystalNav.LOCATIONS) {
				boolean known = CrystalNav.isLearned(loc);
				boolean active = loc.equals(CrystalNav.targetName());
				String tag = !known ? "erst betreten" : active ? "aktiv" : "navigieren";
				rows.add(new NavRow(loc, tag, known, active, () -> {
					CrystalNav.setTarget(loc);
					close();
				}));
			}
		} else if (MiningData.INSTANCE.onMiningIsland) {
			boolean auto = !MiningWaypoints.hasManual();
			rows.add(new NavRow("Auto (nächste Commission)", auto ? "aktiv" : "automatisch", true, auto, () -> {
				MiningWaypoints.clearManual();
				close();
			}));
			String cur = MiningWaypoints.hasManual() ? MiningWaypoints.manual().label() : null;
			for (MiningWaypoints.NavOption o : MiningWaypoints.dwarvenTargets()) {
				boolean active = o.name().equals(cur);
				String tag = active ? "aktiv" : o.learned() ? "navigieren" : "ungefähr";
				rows.add(new NavRow(o.name(), tag, true, active, () -> {
					MiningWaypoints.setManual(o.x(), o.y(), o.z(), o.name());
					close();
				}));
			}
		}
		return rows;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		clickables.clear();
		context.fill(0, 0, this.width, this.height, 0xAA000000);

		List<NavRow> rows = buildRows();
		String title = MiningData.INSTANCE.onCrystalHollows ? "Crystal Hollows – Ziel wählen"
				: MiningData.INSTANCE.onMiningIsland ? "Dwarven Mines – Ziel wählen"
						: "Navi-Ziel wählen";

		int w = 250;
		int rowH = 20;
		int headH = 26;
		int footH = 56;
		boolean empty = rows.isEmpty();
		int bodyH = empty ? 24 : rows.size() * rowH;
		int h = headH + bodyH + footH;
		int x = (this.width - w) / 2;
		int y = (this.height - h) / 2;

		context.fill(x - 1, y - 1, x + w + 1, y + h + 1, BORDER);
		context.fill(x, y, x + w, y + h, PANEL);
		context.drawText(textRenderer, title, x + 12, y + 9, TEXT, false);

		int ry = y + headH;
		if (empty) {
			context.drawText(textRenderer, "Nur in Crystal Hollows / den Minen nutzbar.",
					x + 12, ry + 8, DIM, false);
			ry += bodyH;
		} else {
			for (NavRow r : rows) {
				boolean hover = r.enabled() && mouseX >= x && mouseX <= x + w && mouseY >= ry && mouseY <= ry + rowH;
				if (hover) {
					context.fill(x, ry, x + w, ry + rowH, CARD_HOVER);
				} else if (r.active()) {
					context.fill(x, ry, x + w, ry + rowH, CARD);
				}
				int col = !r.enabled() ? DIM : r.active() ? ACCENT : TEXT;
				context.drawText(textRenderer, r.label(), x + 12, ry + (rowH - 8) / 2, col, false);
				int tagW = textRenderer.getWidth(r.tag());
				context.drawText(textRenderer, r.tag(), x + w - 12 - tagW, ry + (rowH - 8) / 2,
						!r.enabled() ? DIM : ACCENT, false);
				if (r.enabled()) {
					clickables.add(new Clickable(x, ry, x + w, ry + rowH, r.action()));
				}
				ry += rowH;
			}
		}

		// "Nahe NPCs anzeigen" – schreibt Namen + IDs in den Chat.
		int bw = w - 24;
		int bh = 20;
		int bx = x + 12;
		int dy0 = y + h - footH + 5;
		boolean dhover = mouseX >= bx && mouseX <= bx + bw && mouseY >= dy0 && mouseY <= dy0 + bh;
		context.fill(bx, dy0, bx + bw, dy0 + bh, dhover ? CARD_HOVER : CARD);
		String dump = "Nahe NPCs anzeigen (Namen + IDs in Chat)";
		int dw = textRenderer.getWidth(dump);
		context.drawText(textRenderer, dump, bx + (bw - dw) / 2, dy0 + (bh - 8) / 2, TEXT, false);
		clickables.add(new Clickable(bx, dy0, bx + bw, dy0 + bh, () -> {
			CrystalNav.dumpNearby();
			close();
		}));

		// Abbrechen-Knopf (hebt CH- und manuelles Mining-Ziel auf).
		boolean anyTarget = CrystalNav.hasTarget() || MiningWaypoints.hasManual();
		int by = dy0 + bh + 4;
		boolean chover = anyTarget && mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh;
		context.fill(bx, by, bx + bw, by + bh, chover ? 0xFF7A2A26 : 0xFF40201E);
		String cancel = anyTarget ? "Navigation abbrechen" : "Keine Navigation aktiv";
		int cw = textRenderer.getWidth(cancel);
		context.drawText(textRenderer, cancel, bx + (bw - cw) / 2, by + (bh - 8) / 2, anyTarget ? RED : DIM, false);
		if (anyTarget) {
			clickables.add(new Clickable(bx, by, bx + bw, by + bh, () -> {
				CrystalNav.cancel();
				MiningWaypoints.clearManual();
				close();
			}));
		}
	}

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		for (Clickable c : clickables) {
			if (c.contains(click.x(), click.y())) {
				c.action().run();
				return true;
			}
		}
		return super.mouseClicked(click, doubled);
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
	}

	@Override
	public void close() {
		if (client != null) {
			client.setScreen(parent);
		}
	}
}
