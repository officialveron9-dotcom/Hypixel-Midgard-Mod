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
	public static final String KEY_COMPOSTER = "GARDEN_COMPOSTER";
	public static final String KEY_JACOB = "GARDEN_JACOB";

	/** Medaillen-Stufen beim Jacob-Contest (Top-Prozent). */
	private static final int[] BRACKET_PCT = { 2, 5, 10, 30, 60 };
	private static final String[] BRACKET_NAME = { "Diamant", "Platin", "Gold", "Silber", "Bronze" };

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
			String next = preview && g.nextVisitor.isEmpty() ? "4m 30s" : g.nextVisitor;
			if (!vis.isEmpty() || !next.isEmpty()) {
				List<HudRow> rows = new ArrayList<>();
				for (String v : vis) {
					rows.add(new HudRow(List.of(Items.EMERALD), v, WHITE, false));
				}
				if (!next.isEmpty()) {
					rows.add(new HudRow(List.of(), "Nächster: " + next, YELLOW, false));
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

		// 6) Composter: Material/Brennstoff/Restzeit, Warnung wenn leer.
		if (cfg.gardenComposter) {
			String matter = preview && g.composterMatter.isEmpty() ? "43,2k" : g.composterMatter;
			String fuel = preview && g.composterFuel.isEmpty() ? "0" : g.composterFuel;
			String time = preview && g.composterTime.isEmpty() ? "1h 4m" : g.composterTime;
			if (!matter.isEmpty() || !fuel.isEmpty()) {
				List<HudRow> rows = new ArrayList<>();
				if (!matter.isEmpty()) {
					rows.add(new HudRow(List.of(), "Material: " + matter,
							empty(matter) ? RED : WHITE, empty(matter)));
				}
				if (!fuel.isEmpty()) {
					rows.add(new HudRow(List.of(), "Brennstoff: " + fuel,
							empty(fuel) ? RED : WHITE, empty(fuel)));
				}
				if (!time.isEmpty()) {
					rows.add(new HudRow(List.of(), "Fertig in: " + time, GREEN, false));
				}
				out.add(new HudGroup(KEY_COMPOSTER, "Composter", rows));
			}
		}

		// 7) Jacob-Contest live: gesammelt, Platzierung, nächste Medaille, Ende.
		if (cfg.gardenJacob) {
			long collected = g.jacobCollected;
			int pct = g.jacobPercent;
			if (preview && collected < 0) {
				collected = 12_340;
				pct = 7;
			}
			if (collected >= 0 || pct >= 0) {
				List<HudRow> rows = new ArrayList<>();
				if (collected >= 0) {
					rows.add(new HudRow(List.of(), "Gesammelt: " + num(collected), WHITE, false));
				}
				int bracket = bracketOf(pct);
				if (pct >= 0) {
					rows.add(new HudRow(List.of(), "Top " + pct + "%  (" + BRACKET_NAME[bracket] + ")",
							GREEN, false));
					if (bracket > 0) {
						rows.add(new HudRow(List.of(),
								"Nächste: Top " + BRACKET_PCT[bracket - 1] + "% (" + BRACKET_NAME[bracket - 1] + ")",
								YELLOW, false));
					}
				}
				java.util.Map.Entry<Long, java.util.List<String>> act =
						com.midgard.price.PriceApi.INSTANCE.jacobActive(System.currentTimeMillis() / 1000);
				if (act != null) {
					long ends = act.getKey() + com.midgard.price.PriceApi.CONTEST_SECONDS
							- System.currentTimeMillis() / 1000;
					rows.add(new HudRow(List.of(), "Endet in " + TimeUtil.format(ends), YELLOW, false));
				} else if (preview) {
					rows.add(new HudRow(List.of(), "Endet in 12m 20s", YELLOW, false));
				}
				out.add(new HudGroup(KEY_JACOB, "Jacob Live", rows));
			}
		}

		return out;
	}

	/** Stufe (Index in BRACKET_*) zu einer Top-Prozent-Platzierung. */
	private static int bracketOf(int pct) {
		for (int i = 0; i < BRACKET_PCT.length; i++) {
			if (pct >= 0 && pct <= BRACKET_PCT[i]) {
				return i;
			}
		}
		return BRACKET_PCT.length - 1;
	}

	private static boolean empty(String v) {
		return v.startsWith("0") || v.toLowerCase(java.util.Locale.ROOT).contains("empty");
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
