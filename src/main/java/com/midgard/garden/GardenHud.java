package com.midgard.garden;

import java.util.ArrayList;
import java.util.List;

import com.midgard.events.config.ModConfig;
import com.midgard.events.event.EventIcons;
import com.midgard.events.hud.EventHud.HudGroup;
import com.midgard.events.hud.EventHud.HudRow;
import com.midgard.util.TimeUtil;

import net.minecraft.item.Item;
import net.minecraft.item.Items;

/**
 * Baut die Garden-HUD-Gruppen (Besucher, Schädlinge, Werkzeug, Farming-Statistik,
 * Collection) aus {@link GardenData} + {@link FarmingTracker}. Jede Gruppe ist
 * einzeln schaltbar und im HUD-Editor einzeln verschieb-/skalierbar. Im
 * Editor-Preview werden Beispieldaten gezeigt, damit man alles anordnen kann.
 */
public final class GardenHud {

	public static final String KEY_VISITORS = "GARDEN_VISITORS";
	public static final String KEY_PESTS = "GARDEN_PESTS";
	public static final String KEY_TOOL = "GARDEN_TOOL";
	public static final String KEY_STATS = "GARDEN_STATS";
	public static final String KEY_COLLECTION = "GARDEN_COLLECTION";

	private static final int WHITE = 0xFFF1F1F4;
	private static final int GREEN = 0xFF5BE36B;
	private static final int YELLOW = 0xFFF2C94C;
	private static final int RED = 0xFFFF5A52;

	private static final String[] ROMAN = {
			"0", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X" };

	private GardenHud() {
	}

	public static List<HudGroup> groups(ModConfig cfg, boolean preview) {
		GardenData g = GardenData.INSTANCE;
		if (!preview && !g.onGarden) {
			return List.of();
		}
		FarmingTracker f = FarmingTracker.INSTANCE;
		List<HudGroup> out = new ArrayList<>();

		// 1) Besucher.
		if (cfg.gardenVisitors) {
			List<String> vis = g.visitors;
			if (preview && vis.isEmpty()) {
				vis = List.of("Jacob", "Anita", "Spaceman");
			}
			if (!vis.isEmpty()) {
				List<HudRow> rows = new ArrayList<>();
				for (String v : vis) {
					rows.add(new HudRow(List.of(Items.EMERALD), v, WHITE, false));
				}
				out.add(new HudGroup(KEY_VISITORS, "Besucher", rows));
			}
		}

		// 2) Schädlinge: Anzahl + befallene Plots; aktueller Plot hervorgehoben.
		if (cfg.gardenPests) {
			int count = g.pestCount;
			List<String> plots = g.infestedPlots;
			boolean sample = preview && plots.isEmpty() && count == 0;
			if (sample) {
				count = 2;
				plots = List.of("2", "5");
			}
			List<HudRow> rows = new ArrayList<>();
			rows.add(new HudRow(List.of(Items.SPIDER_EYE), count + "/" + GardenData.MAX_PESTS,
					count > 0 ? YELLOW : GREEN, false));
			for (String p : plots) {
				boolean here = sample ? p.equals("2") : g.isCurrentPlot(p);
				rows.add(new HudRow(List.of(), "Plot " + p, here ? RED : YELLOW, here));
			}
			out.add(new HudGroup(KEY_PESTS, "Schädlinge", rows));
		}

		// 3) Werkzeug: Cultivating-Level + Fortschritt zum nächsten Level.
		if (cfg.gardenTool) {
			List<HudRow> rows = new ArrayList<>();
			if (f.cultivating >= 0) {
				int lvl = f.cultivatingLevel();
				long next = f.cultivatingNext();
				rows.add(new HudRow(List.of(Items.IRON_HOE),
						"Cultivating " + ROMAN[Math.min(lvl, 10)], WHITE, false));
				rows.add(new HudRow(List.of(), next < 0
						? num(f.cultivating) + " (Max)"
						: num(f.cultivating) + " / " + num(next), YELLOW, false));
			} else if (preview) {
				rows.add(new HudRow(List.of(Items.IRON_HOE), "Cultivating VII", WHITE, false));
				rows.add(new HudRow(List.of(), "5,2M / 20M", YELLOW, false));
			}
			if (!rows.isEmpty()) {
				out.add(new HudGroup(KEY_TOOL, "Werkzeug", rows));
			}
		}

		// 4) Farming-Statistik: nur während aktiv gefarmt wird.
		if (cfg.gardenStats && (preview || f.farming())) {
			String crop = f.crop().isEmpty() ? (preview ? "Wheat" : "Farming") : f.crop();
			Item icon = EventIcons.forCrop(crop);
			List<HudRow> rows = new ArrayList<>();
			double cpm = f.cropsPerMinute();
			double bps = f.blocksPerSecond();
			if (preview && cpm <= 0) {
				cpm = 1180;
				bps = 19.6;
			}
			rows.add(new HudRow(List.of(icon), "Crops/min: " + num(Math.round(cpm)), GREEN, false));
			rows.add(new HudRow(List.of(), String.format("Blöcke/s: %.1f", bps), WHITE, false));
			out.add(new HudGroup(KEY_STATS, crop, rows));
		}

		// 5) Collection: Fortschritt + Zeit bis zum nächsten Rang.
		if (cfg.gardenCollection) {
			FarmingTracker.Collection col = f.collection();
			List<HudRow> rows = new ArrayList<>();
			if (col != null) {
				long est = f.collectionEstimate(col);
				long eta = f.collectionEtaSeconds(col);
				Item icon = EventIcons.forCrop(col.crop());
				rows.add(new HudRow(List.of(icon),
						num(est) + " / " + num(col.target()), WHITE, false));
				rows.add(new HudRow(List.of(), eta < 0
						? "Rang: farme für ETA"
						: "Rang in " + TimeUtil.format(eta), eta < 0 ? YELLOW : GREEN, false));
			} else if (preview) {
				rows.add(new HudRow(List.of(Items.WHEAT), "84,3k / 100k", WHITE, false));
				rows.add(new HudRow(List.of(), "Rang in 1h 12m", GREEN, false));
			} else if (f.farming()) {
				rows.add(new HudRow(List.of(), "Milestone-Menü 1x öffnen", YELLOW, false));
			}
			if (!rows.isEmpty()) {
				out.add(new HudGroup(KEY_COLLECTION, "Collection", rows));
			}
		}

		return out;
	}

	/** Kompakte Zahl: 1,2B / 3,4M / 12,3k / 123. */
	private static String num(long v) {
		if (v >= 1_000_000_000L) {
			return String.format("%.1fB", v / 1_000_000_000d);
		}
		if (v >= 1_000_000L) {
			return String.format("%.1fM", v / 1_000_000d);
		}
		if (v >= 10_000L) {
			return String.format("%.1fk", v / 1_000d);
		}
		return String.valueOf(v);
	}
}
