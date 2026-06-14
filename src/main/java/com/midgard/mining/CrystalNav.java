package com.midgard.mining;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.midgard.events.skyblock.ScoreboardReader;

import net.minecraft.client.MinecraftClient;

/**
 * Crystal-Hollows-Navi: Crystal Hollows wird pro Instanz ZUFÄLLIG generiert,
 * darum lernt der Mod die Position jedes Gebiets, sobald man es betritt
 * (Scoreboard-Standort + eigene Position). Der Crystal Nucleus liegt als
 * einziger Ort fest in der Mitte. Über die Navi-Liste wählt man ein Ziel; der
 * Wegfinder/Pfad führt dann dorthin. Gelernte Positionen gelten pro Besuch
 * (werden beim Verlassen von Crystal Hollows zurückgesetzt).
 */
public final class CrystalNav {

	/** Alle anpeilbaren Orte (Reihenfolge = Anzeige in der Liste). */
	public static final List<String> LOCATIONS = List.of(
			"Crystal Nucleus",
			"Jungle",
			"Mithril Deposits",
			"Goblin Holdout",
			"Precursor Remnants",
			"Magma Fields",
			"Fairy Grotto",
			"Khazad-dum",
			"Jungle Temple",
			"Lava Tubes");

	private static final int[] NUCLEUS = { 513, 125, 513 };

	private static final Map<String, int[]> learned = new HashMap<>();
	private static volatile String targetName;
	private static volatile int[] targetPos;
	private static boolean wasInCH = false;

	private CrystalNav() {
	}

	public static void tick(MinecraftClient mc) {
		boolean ch = MiningData.INSTANCE.onCrystalHollows;
		if (!ch) {
			if (wasInCH) { // Crystal Hollows verlassen -> alles zurücksetzen
				learned.clear();
				targetName = null;
				targetPos = null;
				wasInCH = false;
			}
			return;
		}
		wasInCH = true;
		learned.putIfAbsent("Crystal Nucleus", NUCLEUS.clone());
		if (mc.player == null) {
			return;
		}
		String area = detectArea(mc);
		if (area != null) {
			learned.put(area, new int[] {
					(int) Math.round(mc.player.getX()),
					(int) Math.round(mc.player.getY()),
					(int) Math.round(mc.player.getZ()) });
		}
	}

	/** Aktuelles Crystal-Hollows-Gebiet aus dem Scoreboard (oder null). */
	private static String detectArea(MinecraftClient mc) {
		for (String line : ScoreboardReader.sidebarLines(mc)) {
			String l = line == null ? "" : line.toLowerCase(Locale.ROOT);
			for (String loc : LOCATIONS) {
				if (loc.equals("Crystal Nucleus")) {
					continue;
				}
				if (l.contains(loc.toLowerCase(Locale.ROOT))) {
					return loc;
				}
			}
		}
		return null;
	}

	public static boolean isLearned(String name) {
		return learned.containsKey(name);
	}

	public static void setTarget(String name) {
		int[] p = learned.get(name);
		if (p != null) {
			targetName = name;
			targetPos = p;
		}
	}

	public static void cancel() {
		targetName = null;
		targetPos = null;
	}

	public static boolean hasTarget() {
		return targetPos != null;
	}

	public static String targetName() {
		return targetName;
	}

	/** Zielkoordinate {x,y,z} oder null. */
	public static double[] target() {
		int[] p = targetPos;
		return p == null ? null : new double[] { p[0], p[1], p[2] };
	}
}
