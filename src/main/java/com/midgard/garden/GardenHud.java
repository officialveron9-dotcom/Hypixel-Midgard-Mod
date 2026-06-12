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
	private static final int GOLD = 0xFFFFC85C;
	private static final int DIM = 0xFF9A9AA5;

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
			long cd = g.handinCooldownRemaining();
			if (preview && cd < 0) {
				cd = 43 * 60;
			}
			if (cd >= 0) {
				rows.add(new HudRow(List.of(), "Abgabe in " + TimeUtil.format(cd), DIM, false));
			} else if (g.pestHandinMs > 0) {
				rows.add(new HudRow(List.of(), "Abgabe bereit", GREEN, false));
			}
			out.add(new HudGroup(KEY_PESTS, "Schädlinge", rows));
		}

		// 3-5) EINE kombinierte Farming-Box (wie bei Taunahi): Cultivating,
		// Crops/min, Coins/h, Blöcke/s, Optimal-Speed, Milestone, Yaw/Pitch.
		// Sichtbar mit Farm-Werkzeug in der Hand oder beim aktiven Farmen.
		if ((cfg.gardenTool || cfg.gardenStats || cfg.gardenCollection)
				&& (preview || f.farming() || f.holdingFarmTool())) {
			String crop = f.crop().isEmpty() ? (preview ? "Wheat" : "") : f.crop();
			List<HudRow> rows = new ArrayList<>();

			if (cfg.gardenTool) {
				long cult = preview && f.cultivating < 0 ? 167_924_090L : f.cultivating;
				if (cult >= 0) {
					int lvl = f.cultivatingLevel();
					String lvlTxt = f.cultivating >= 0 ? " (" + ROMAN[Math.min(lvl, 10)] + ")" : " (IX)";
					rows.add(new HudRow(List.of(Items.IRON_HOE),
							"Cultivating: " + full(cult) + lvlTxt, WHITE, false));
				}
			}

			if (cfg.gardenStats) {
				double cpm = f.cropsPerMinute();
				double bps = f.blocksPerSecond();
				if (preview && cpm <= 0) {
					cpm = 52_440;
					bps = 20.0;
				}
				rows.add(new HudRow(List.of(), "Crops/min: " + full(Math.round(cpm)), GREEN, false));
				double sell = bazaarSell(crop);
				if (preview && sell <= 0) {
					sell = 4.2;
				}
				if (sell > 0 && cpm > 0) {
					rows.add(new HudRow(List.of(),
							"Coins/h: " + full(Math.round(cpm * 60 * sell)) + " (Bazaar)", GOLD, false));
				}
				rows.add(new HudRow(List.of(), String.format("Blöcke/s: %.1f", bps), WHITE, false));
				int speed = optimalSpeed(crop);
				if (speed > 0) {
					rows.add(new HudRow(List.of(), "Optimal: " + speed + " Speed", DIM, false));
				}
			}

			if (cfg.gardenCollection) {
				FarmingTracker.Collection col = f.collection();
				if (col != null) {
					long est = f.collectionEstimate(col);
					long eta = f.collectionEtaSeconds(col);
					rows.add(new HudRow(List.of(), "Milestone: " + num(est) + " / " + num(col.target()),
							WHITE, false));
					if (eta >= 0) {
						rows.add(new HudRow(List.of(), "Rang in " + TimeUtil.format(eta), GREEN, false));
					}
				} else if (preview) {
					rows.add(new HudRow(List.of(), "Milestone: 84,3k / 100k", WHITE, false));
					rows.add(new HudRow(List.of(), "Rang in 1h 12m", GREEN, false));
				} else {
					rows.add(new HudRow(List.of(), "Milestone-Menü 1x öffnen", YELLOW, false));
				}
			}

			if (cfg.gardenStats) {
				net.minecraft.client.network.ClientPlayerEntity p =
						net.minecraft.client.MinecraftClient.getInstance().player;
				if (p != null) {
					float yaw = net.minecraft.util.math.MathHelper.wrapDegrees(p.getYaw());
					rows.add(new HudRow(List.of(), String.format("Yaw: %.2f", yaw), DIM, false));
					rows.add(new HudRow(List.of(), String.format("Pitch: %.2f", p.getPitch()), DIM, false));
				}
			}

			if (!rows.isEmpty()) {
				Item icon = crop.isEmpty() ? Items.IRON_HOE : EventIcons.forCrop(crop);
				rows.set(0, new HudRow(List.of(icon), rows.get(0).text(), rows.get(0).color(), false));
				out.add(new HudGroup(KEY_STATS, crop.isEmpty() ? "Farming" : "Farming – " + crop, rows));
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

	/** Bazaar-Sofortverkaufspreis des Roh-Crops (vom Backend) oder 0. */
	private static double bazaarSell(String crop) {
		String id = switch (crop) {
			case "Wheat" -> "WHEAT";
			case "Carrot" -> "CARROT_ITEM";
			case "Potato" -> "POTATO_ITEM";
			case "Pumpkin" -> "PUMPKIN";
			case "Melon" -> "MELON";
			case "Sugar Cane" -> "SUGAR_CANE";
			case "Cactus" -> "CACTUS";
			case "Cocoa Beans" -> "INK_SACK:3";
			case "Mushroom" -> "RED_MUSHROOM";
			case "Nether Wart" -> "NETHER_STALK";
			default -> "";
		};
		if (id.isEmpty()) {
			return 0;
		}
		double[] bz = com.midgard.price.PriceApi.INSTANCE.bazaar(id);
		return bz == null ? 0 : bz[1];
	}

	/** Übliche optimale Lauf-Geschwindigkeit pro Crop (Standard-Farmdesigns). */
	private static int optimalSpeed(String crop) {
		return switch (crop) {
			case "Wheat", "Carrot", "Potato", "Nether Wart" -> 93;
			case "Pumpkin", "Melon" -> 258;
			case "Cocoa Beans" -> 155;
			case "Sugar Cane" -> 328;
			case "Cactus" -> 464;
			case "Mushroom" -> 233;
			default -> 0;
		};
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

	/** Zahlenanzeige: voll oder gekürzt – zentral in Numbers (Auktion-Tab). */
	private static String full(long v) {
		return com.midgard.util.Numbers.format(v);
	}

	private static String num(long v) {
		return com.midgard.util.Numbers.format(v);
	}
}
