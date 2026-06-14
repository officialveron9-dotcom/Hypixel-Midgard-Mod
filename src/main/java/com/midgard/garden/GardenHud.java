package com.midgard.garden;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.midgard.events.config.ModConfig;
import com.midgard.events.event.EventIcons;
import com.midgard.events.hud.EventHud;
import com.midgard.events.hud.EventHud.HudGroup;
import com.midgard.events.hud.EventHud.HudRow;
import com.midgard.util.TimeUtil;

import net.minecraft.item.Item;
import net.minecraft.item.Items;

/**
 * Baut die Garden-HUD-Gruppen aus {@link GardenData} + {@link FarmingTracker}.
 * Einheitliches Zeilen-Layout: Beschriftung links (grau), Wert rechts in der
 * Akzentfarbe, Icons ganz rechts; Alarm-Zeilen rot. Nur im Garten sichtbar
 * (auch im HUD-Editor); im Editor-Preview Beispieldaten, falls echte fehlen.
 */
public final class GardenHud {

	public static final String KEY_VISITORS = "GARDEN_VISITORS";
	public static final String KEY_PESTS = "GARDEN_PESTS";
	public static final String KEY_TOOL = "GARDEN_TOOL";
	public static final String KEY_STATS = "GARDEN_STATS";
	public static final String KEY_COLLECTION = "GARDEN_COLLECTION";
	public static final String KEY_COMPOSTER = "GARDEN_COMPOSTER";
	public static final String KEY_JACOB = "GARDEN_JACOB";

	private static final int VALUE = EventHud.VALUE;
	private static final int RED = 0xFFFF5A52;

	/** Medaillen-Stufen beim Jacob-Contest (Top-Prozent). */
	private static final int[] BRACKET_PCT = { 2, 5, 10, 30, 60 };
	private static final String[] BRACKET_NAME = { "Diamant", "Platin", "Gold", "Silber", "Bronze" };

	private static final String[] ROMAN = {
			"0", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X" };

	private GardenHud() {
	}

	/**
	 * Element sichtbar? In der Vorschau (Editor) IMMER (disabled werden dort
	 * ausgegraut), sonst: aktiviert UND (im Garten ODER überall-Modus).
	 */
	private static boolean vis(ModConfig cfg, String key, boolean preview) {
		return preview || (cfg.isElementEnabled(key)
				&& (GardenData.INSTANCE.onGarden || cfg.isElementGlobal(key)));
	}

	public static List<HudGroup> groups(ModConfig cfg, boolean preview) {
		GardenData g = GardenData.INSTANCE;
		FarmingTracker f = FarmingTracker.INSTANCE;
		List<HudGroup> out = new ArrayList<>();

		// 1) Besucher: Name links, benötigte Items rechts (aus dem Menü gemerkt).
		if (vis(cfg, KEY_VISITORS, preview)) {
			List<String> vis = g.visitors;
			if (preview && vis.isEmpty()) {
				vis = List.of("Jacob", "Anita", "Spaceman");
			}
			String next = preview && g.nextVisitor.isEmpty() ? "4m 30s" : g.nextVisitor;
			if (!vis.isEmpty() || !next.isEmpty()) {
				List<HudRow> rows = new ArrayList<>();
				for (String v : vis) {
					List<String> items = g.visitorItems.get(v);
					if (items != null && !items.isEmpty()) {
						List<EventHud.Badge> badges = new ArrayList<>();
						for (String it : items) {
							badges.add(parseBadge(it));
						}
						rows.add(new HudRow(v + ":", "", VALUE, List.of(), false, badges));
					} else if (preview) {
						rows.add(new HudRow(v + ":", "", VALUE, List.of(), false,
								List.of(new EventHud.Badge(EventIcons.forCrop("Wheat"), "32"))));
					} else {
						rows.add(new HudRow(v + ":", "öffnen", VALUE, List.of(), false));
					}
				}
				if (!next.isEmpty()) {
					rows.add(new HudRow("Nächster", next));
				}
				out.add(new HudGroup(KEY_VISITORS, "Besucher", rows, Items.EMERALD));
			}
		}

		// 2) Schädlinge: Anzahl + befallene Plots; aktueller Plot rot.
		if (vis(cfg, KEY_PESTS, preview)) {
			int count = g.pestCount;
			List<String> plots = g.infestedPlots;
			boolean sample = preview && plots.isEmpty() && count == 0;
			if (sample) {
				count = 2;
				plots = List.of("2", "5");
			}
			List<HudRow> rows = new ArrayList<>();
			rows.add(new HudRow("Anzahl", count + "/" + GardenData.MAX_PESTS));
			for (String p : plots) {
				boolean here = sample ? p.equals("2") : g.isCurrentPlot(p);
				rows.add(new HudRow("", "Plot " + p, here ? RED : VALUE, List.of(), here));
			}
			long cd = g.handinCooldownRemaining();
			if (preview && cd < 0) {
				cd = 43 * 60;
			}
			if (cd >= 0) {
				rows.add(new HudRow("Abgabe in", TimeUtil.format(cd)));
			} else if (g.pestHandinMs > 0) {
				rows.add(new HudRow("Abgabe", "bereit"));
			}
			out.add(new HudGroup(KEY_PESTS, "Schädlinge", rows, Items.SPIDER_EYE));
		}

		// 3-5) EINE kombinierte Farming-Box: Cultivating, Raten, Milestone, Yaw/Pitch.
		if (vis(cfg, KEY_STATS, preview) && (preview || f.farming() || f.holdingFarmTool())) {
			String crop = f.crop().isEmpty() ? (preview ? "Wheat" : "") : f.crop();
			List<HudRow> rows = new ArrayList<>();

			if (cfg.gardenTool) {
				long cult = preview && f.cultivating < 0 ? 167_924_090L : f.cultivating;
				if (cult >= 0) {
					int lvl = f.cultivatingLevel();
					String lvlTxt = f.cultivating >= 0 ? " (" + ROMAN[Math.min(lvl, 10)] + ")" : " (IX)";
					rows.add(new HudRow("Cultivating", num(cult) + lvlTxt));
				}
			}

			if (cfg.gardenStats) {
				double cpm = f.cropsPerMinute();
				double bps = f.blocksPerSecond();
				if (preview && cpm <= 0) {
					cpm = 52_440;
					bps = 20.0;
				}
				rows.add(new HudRow("Crops/min", num(Math.round(cpm))));
				double sell = bazaarSell(crop);
				if (preview && sell <= 0) {
					sell = 4.2;
				}
				if (sell > 0 && cpm > 0) {
					rows.add(new HudRow("Coins/h", num(Math.round(cpm * 60 * sell))));
				}
				rows.add(new HudRow("Blöcke/s", String.format("%.1f", bps)));
				int speed = optimalSpeed(crop);
				if (speed > 0) {
					rows.add(new HudRow("Optimal", speed + " Speed"));
				}
			}

			if (cfg.gardenCollection) {
				FarmingTracker.Collection col = f.collection();
				if (col != null) {
					long est = f.collectionEstimate(col);
					long eta = f.collectionEtaSeconds(col);
					rows.add(new HudRow("Milestone", num(est) + " / " + num(col.target())));
					if (eta >= 0) {
						rows.add(new HudRow("Rang in", TimeUtil.format(eta)));
					}
				} else if (preview) {
					rows.add(new HudRow("Milestone", "84,3k / 100k"));
					rows.add(new HudRow("Rang in", "1h 12m"));
				} else {
					rows.add(new HudRow("Milestone", "Menü 1x öffnen"));
				}
			}

			if (cfg.gardenStats) {
				net.minecraft.client.network.ClientPlayerEntity p =
						net.minecraft.client.MinecraftClient.getInstance().player;
				if (p != null) {
					float yaw = net.minecraft.util.math.MathHelper.wrapDegrees(p.getYaw());
					rows.add(new HudRow("Yaw", String.format("%.2f", yaw)));
					rows.add(new HudRow("Pitch", String.format("%.2f", p.getPitch())));
				}
			}

			if (!rows.isEmpty()) {
				Item icon = crop.isEmpty() ? Items.IRON_HOE : EventIcons.forCrop(crop);
				out.add(new HudGroup(KEY_STATS, crop.isEmpty() ? "Farming" : crop, rows, icon));
			}
		}

		// 6) Composter: Warnung (rot) wenn leer.
		if (vis(cfg, KEY_COMPOSTER, preview)) {
			String matter = preview && g.composterMatter.isEmpty() ? "43,2k" : g.composterMatter;
			String fuel = preview && g.composterFuel.isEmpty() ? "0" : g.composterFuel;
			String time = preview && g.composterTime.isEmpty() ? "1h 4m" : g.composterTime;
			if (!matter.isEmpty() || !fuel.isEmpty()) {
				List<HudRow> rows = new ArrayList<>();
				if (!matter.isEmpty()) {
					rows.add(new HudRow("Material", matter, empty(matter) ? RED : VALUE,
							List.of(), empty(matter)));
				}
				if (!fuel.isEmpty()) {
					rows.add(new HudRow("Brennstoff", fuel, empty(fuel) ? RED : VALUE,
							List.of(), empty(fuel)));
				}
				if (!time.isEmpty()) {
					rows.add(new HudRow("Fertig in", time));
				}
				out.add(new HudGroup(KEY_COMPOSTER, "Composter", rows));
			}
		}

		// 7) Jacob-Contest live.
		if (vis(cfg, KEY_JACOB, preview)) {
			long collected = g.jacobCollected;
			int pct = g.jacobPercent;
			if (preview && collected < 0) {
				collected = 12_340;
				pct = 7;
			}
			if (collected >= 0 || pct >= 0) {
				List<HudRow> rows = new ArrayList<>();
				if (collected >= 0) {
					rows.add(new HudRow("Gesammelt", num(collected)));
				}
				int bracket = bracketOf(pct);
				if (pct >= 0) {
					rows.add(new HudRow("Platz", "Top " + pct + "% (" + BRACKET_NAME[bracket] + ")"));
					if (bracket > 0) {
						rows.add(new HudRow("Nächste",
								"Top " + BRACKET_PCT[bracket - 1] + "% (" + BRACKET_NAME[bracket - 1] + ")"));
					}
				}
				java.util.Map.Entry<Long, java.util.List<String>> act =
						com.midgard.price.PriceApi.INSTANCE.jacobActive(System.currentTimeMillis() / 1000);
				if (act != null) {
					long ends = act.getKey() + com.midgard.price.PriceApi.CONTEST_SECONDS
							- System.currentTimeMillis() / 1000;
					rows.add(new HudRow("Endet in", TimeUtil.format(ends)));
				} else if (preview) {
					rows.add(new HudRow("Endet in", "12m 20s"));
				}
				out.add(new HudGroup(KEY_JACOB, "Jacob Live", rows));
			}
		}

		return out;
	}

	private static final Pattern AMOUNT = Pattern.compile("(\\d[\\d.,]*)");

	/** "32x Wheat" / "Wheat x32" -> Badge(Item-Icon, "32"). */
	private static EventHud.Badge parseBadge(String s) {
		String count = "";
		Matcher m = AMOUNT.matcher(s);
		if (m.find()) {
			count = m.group(1).replace(".", "").replace(",", "");
		}
		String name = s.replaceAll("(?i)\\d[\\d.,]*\\s*x?", " ").replaceAll("\\s+", " ").trim();
		return new EventHud.Badge(EventIcons.forCrop(name), count);
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
	private static String num(long v) {
		return com.midgard.util.Numbers.format(v);
	}
}
