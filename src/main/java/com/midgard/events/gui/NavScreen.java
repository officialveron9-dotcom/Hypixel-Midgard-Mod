package com.midgard.events.gui;

import java.util.ArrayList;
import java.util.List;

import com.midgard.mining.CrystalNav;
import com.midgard.mining.MiningData;
import com.midgard.mining.MiningWaypoints;
import com.midgard.render.MidgardText;
import com.midgard.render.UIRenderer;
import com.midgard.util.Fonts;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Navi-Ziel wählen – im selben Stil wie das HUD (runde, abgedunkelte Karten,
 * scharfe Schrift, cyan Überschrift / orange Akzent) und kompakt. In Crystal
 * Hollows die Orte, in den Dwarven Mines die Emissär-Gebiete + "Auto" + ein
 * Fadenkreuz-Ziel. Unten: nahe NPCs anzeigen / Navigation abbrechen.
 */
public class NavScreen extends Screen {

	private static final int PANEL = 0xE6121218;
	private static final int BORDER = 0x33FFFFFF;
	private static final int HEADER = 0xFF57D8FF; // cyan, wie HUD-Titel
	private static final int ACCENT = 0xFFF2772F; // orange
	private static final int TEXT = 0xFFF1F1F4;
	private static final int DIM = 0xFF8C8C97;
	private static final int CARD = 0x66242430;
	private static final int CARD_HOVER = 0x99343444;
	private static final int RED = 0xFFE0443C;

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

	private List<NavRow> buildRows() {
		List<NavRow> rows = new ArrayList<>();
		if (MiningData.INSTANCE.onMiningIsland) {
			rows.add(new NavRow("Fadenkreuz-Ziel (wohin ich schaue)", "setzen", true, false, () -> {
				targetLookedAt();
				close();
			}));
		}
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

	/** Setzt das manuelle Navi-Ziel auf den Block, auf den der Spieler schaut. */
	private void targetLookedAt() {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null) {
			return;
		}
		HitResult hr = mc.player.raycast(96, 0f, false);
		if (hr != null && hr.getType() == HitResult.Type.BLOCK) {
			BlockPos p = ((BlockHitResult) hr).getBlockPos();
			MiningWaypoints.setManual(p.getX() + 0.5, p.getY() + 1, p.getZ() + 0.5, "Fadenkreuz-Ziel");
		} else {
			Vec3d eye = mc.player.getEyePos();
			Vec3d dir = mc.player.getRotationVec(1f);
			MiningWaypoints.setManual(eye.x + dir.x * 30, eye.y + dir.y * 30, eye.z + dir.z * 30, "Fadenkreuz-Ziel");
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		clickables.clear();
		context.fill(0, 0, this.width, this.height, 0x99000000);

		List<NavRow> rows = buildRows();
		String title = MiningData.INSTANCE.onCrystalHollows ? "Crystal Hollows – Ziel"
				: MiningData.INSTANCE.onMiningIsland ? "Dwarven Mines – Ziel" : "Navi-Ziel";

		int w = 224;
		int rowH = 15;
		int pad = 8;
		int headH = 20;
		int btnH = 15;
		int footH = btnH * 2 + 4 + pad;
		boolean empty = rows.isEmpty();
		int bodyH = (empty ? 16 : rows.size() * rowH) + 4;
		int h = headH + bodyH + footH;
		int x = (this.width - w) / 2;
		int y = (this.height - h) / 2;

		UIRenderer.fillRoundedRect(context, x - 1, y - 1, w + 2, h + 2, 7, BORDER);
		UIRenderer.fillRoundedRect(context, x, y, w, h, 6, PANEL);
		UIRenderer.fillRoundedRect(context, x + pad, y + 7, 3, headH - 8, 2, ACCENT);
		txt(context, title, x + pad + 8, y + 7, HEADER, true);

		int ry = y + headH;
		if (empty) {
			txt(context, "Nur in Crystal Hollows / den Minen.", x + pad, ry + 5, DIM, false);
		} else {
			for (NavRow r : rows) {
				boolean hover = r.enabled() && mouseX >= x + 4 && mouseX <= x + w - 4 && mouseY >= ry
						&& mouseY <= ry + rowH - 1;
				if (hover) {
					UIRenderer.fillRoundedRect(context, x + 4, ry, w - 8, rowH - 1, 3, CARD_HOVER);
				} else if (r.active()) {
					UIRenderer.fillRoundedRect(context, x + 4, ry, w - 8, rowH - 1, 3, CARD);
				}
				int col = !r.enabled() ? DIM : r.active() ? ACCENT : TEXT;
				int ty = ry + (rowH - capH()) / 2;
				txt(context, r.label(), x + pad + 2, ty, col, false);
				int tagCol = !r.enabled() ? DIM : r.active() ? ACCENT : HEADER;
				txt(context, r.tag(), x + w - pad - 2 - txtW(r.tag(), false), ty, tagCol, false);
				if (r.enabled()) {
					clickables.add(new Clickable(x + 4, ry, x + w - 4, ry + rowH, r.action()));
				}
				ry += rowH;
			}
		}

		// Footer: nahe NPCs anzeigen + Navigation abbrechen.
		int bw = w - pad * 2;
		int bx = x + pad;
		int dy0 = y + h - footH + 2;
		card(context, bx, dy0, bw, btnH, mouseX, mouseY, "Nahe NPCs anzeigen", TEXT, () -> {
			CrystalNav.dumpNearby();
			close();
		});
		boolean anyTarget = CrystalNav.hasTarget() || MiningWaypoints.hasManual();
		int by = dy0 + btnH + 4;
		if (anyTarget) {
			card(context, bx, by, bw, btnH, mouseX, mouseY, "Navigation abbrechen", RED, () -> {
				CrystalNav.cancel();
				MiningWaypoints.clearManual();
				close();
			});
		} else {
			UIRenderer.fillRoundedRect(context, bx, by, bw, btnH, 3, CARD);
			txt(context, "Keine Navigation aktiv", bx + (bw - txtW("Keine Navigation aktiv", false)) / 2,
					by + (btnH - capH()) / 2, DIM, false);
		}
	}

	/** Klickbare, gerundete Karte mit zentriertem Text. */
	private void card(DrawContext c, int x, int y, int w, int h, int mouseX, int mouseY, String label, int col,
			Runnable action) {
		boolean hover = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
		UIRenderer.fillRoundedRect(c, x, y, w, h, 3, hover ? CARD_HOVER : CARD);
		txt(c, label, x + (w - txtW(label, false)) / 2, y + (h - capH()) / 2, col, false);
		clickables.add(new Clickable(x, y, x + w, y + h, action));
	}

	// ---- Text-Hilfen (HUD-Schrift mit Fallback) ---------------------------

	private void txt(DrawContext c, String s, int x, int yTop, int color, boolean bold) {
		if (!MidgardText.draw(c, s, x, yTop, 9f, color, bold)) {
			c.drawText(textRenderer, bold ? Fonts.bold(s) : Fonts.regular(s), x, yTop, color, false);
		}
	}

	private int txtW(String s, boolean bold) {
		int w = MidgardText.width(s, 9f, bold);
		return w >= 0 ? w : textRenderer.getWidth(bold ? Fonts.bold(s) : Fonts.regular(s));
	}

	private int capH() {
		int h = MidgardText.capHeight(9f, false);
		return h > 0 ? h : 7;
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
