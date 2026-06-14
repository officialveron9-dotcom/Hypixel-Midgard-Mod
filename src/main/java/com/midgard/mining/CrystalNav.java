package com.midgard.mining;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import java.util.ArrayList;

import com.midgard.events.skyblock.ScoreboardReader;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Crystal-Hollows-Navi: Crystal Hollows wird pro Instanz ZUFÄLLIG generiert,
 * darum lernt der Mod die Position jedes Gebiets, sobald man es betritt
 * (Scoreboard-Standort + eigene Position). Der Crystal Nucleus liegt als
 * einziger Ort fest in der Mitte. Über die Navi-Liste wählt man ein Ziel; der
 * Wegfinder/Pfad führt dann dorthin. Gelernte Positionen gelten pro Besuch
 * (werden beim Verlassen von Crystal Hollows zurückgesetzt).
 */
public final class CrystalNav {

	/** Anpeilbare Orte (Test-Set: Mitte + Amber-Crystal-NPCs). Reihenfolge = Anzeige. */
	public static final List<String> LOCATIONS = List.of(
			"Crystal Nucleus",
			"King Yolkar",
			"Goblin Guard");

	/**
	 * Benannte NPCs -> Ort (werden als Entity in der Nähe automatisch gelernt).
	 * Aktuell nur die Amber-Crystal-Ziele zum Testen (King Yolkar = Goblin King,
	 * Goblin Guard). Sobald die Erkennung sicher läuft, kommen die anderen
	 * Kristall-NPCs zurück.
	 */
	private static final Map<String, String> NPC_TO_LOCATION = Map.ofEntries(
			Map.entry("king yolkar", "King Yolkar"),
			Map.entry("yolkar", "King Yolkar"),
			Map.entry("goblin guard", "Goblin Guard"));

	private static final int[] NUCLEUS = { 513, 125, 513 };

	private static final Map<String, int[]> learned = new HashMap<>();
	private static volatile String targetName;
	private static volatile int[] targetPos;
	private static boolean wasInCH = false;
	private static long lastDiagMs = 0;

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
		// Benannte NPCs in der Nähe erkennen und ihre Position lernen.
		if (mc.world != null) {
			long now = System.currentTimeMillis();
			boolean diag = now - lastDiagMs > 20_000;
			StringBuilder names = diag ? new StringBuilder() : null;
			int scanned = 0;
			double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
			double goblinBestD = Double.MAX_VALUE;
			int[] goblinBestPos = null;
			for (Entity e : mc.world.getEntities()) {
				if (scanned++ > 300 || e.getName() == null) {
					continue;
				}
				String en = ScoreboardReader.stripFormatting(e.getName().getString());
				String low = en.toLowerCase(Locale.ROOT);
				int[] pos = { (int) Math.round(e.getX()), (int) Math.round(e.getY()), (int) Math.round(e.getZ()) };
				// direkt nach Ortsnamen im NPC-Namen
				for (String loc : LOCATIONS) {
					if (!loc.equals("Crystal Nucleus") && low.contains(loc.toLowerCase(Locale.ROOT))) {
						learned.put(loc, pos);
					}
				}
				// bekannte NPC-/Boss-Namen -> Ort
				for (Map.Entry<String, String> e2 : NPC_TO_LOCATION.entrySet()) {
					if (low.contains(e2.getKey())) {
						learned.put(e2.getValue(), pos);
					}
				}
				// Jeder Goblin (außer King Yolkar) zählt als "Goblin Guard" – den
				// NÄCHSTEN nehmen, damit der Pfad zum Goblin vor einem führt.
				if (low.contains("goblin") && !low.contains("yolkar") && !low.contains("king")
						&& !low.contains("slayer")) {
					double d = sq(e.getX() - px) + sq(e.getY() - py) + sq(e.getZ() - pz);
					if (d < goblinBestD) {
						goblinBestD = d;
						goblinBestPos = pos;
					}
				}
				if (diag && en.length() > 2 && en.matches(".*[A-Za-z].*") && !en.startsWith("[")) {
					names.append(" | ").append(en);
				}
			}
			if (goblinBestPos != null) {
				learned.put("Goblin Guard", goblinBestPos);
			}
			if (diag) {
				lastDiagMs = now;
				System.out.println("[Midgard] CH-Entities:" + names);
			}
		}
		// Pending-Ziel: sobald der gewählte NPC geladen/gelernt ist, Position
		// setzen -> Pfadfindung startet automatisch (ohne erneutes Auswählen).
		if (targetName != null) {
			int[] p = learned.get(targetName);
			targetPos = p; // null, solange noch nicht gefunden
		}
	}

	/**
	 * Schreibt die Namen + Entity-IDs der benannten NPCs/Mobs in der Nähe in den
	 * lokalen Chat (nur Anzeige) – damit man dem Entwickler die exakten Namen/IDs
	 * nennen kann, um neue Orte/Bosse fest einzutragen.
	 */
	public static void dumpNearby() {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null || mc.world == null) {
			return;
		}
		double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
		List<String> lines = new ArrayList<>();
		for (Entity e : mc.world.getEntities()) {
			if (e.getName() == null) {
				continue;
			}
			String en = ScoreboardReader.stripFormatting(e.getName().getString());
			if (en.length() < 2 || !en.matches(".*[A-Za-z].*") || en.startsWith("[")) {
				continue;
			}
			double dx = e.getX() - px, dy = e.getY() - py, dz = e.getZ() - pz;
			double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (d > 30) {
				continue;
			}
			lines.add(en + "  (id=" + e.getId() + ", " + Math.round(e.getX()) + "/"
					+ Math.round(e.getY()) + "/" + Math.round(e.getZ()) + ", " + Math.round(d) + "m)");
			if (lines.size() >= 25) {
				break;
			}
		}
		mc.player.sendMessage(Text.literal("[Midgard] NPCs/Mobs in der Nähe (" + lines.size() + "):")
				.formatted(Formatting.GOLD), false);
		for (String l : lines) {
			mc.player.sendMessage(Text.literal(" - " + l).formatted(Formatting.YELLOW), false);
		}
		if (lines.isEmpty()) {
			mc.player.sendMessage(Text.literal("  (nichts in der Nähe – näher rangehen)").formatted(Formatting.GRAY),
					false);
		}
	}

	public static boolean isLearned(String name) {
		return learned.containsKey(name);
	}

	/** Kopie der gelernten Punkte (Name -> {x,y,z}) für die Mini-Karte. */
	public static Map<String, int[]> learnedView() {
		return new HashMap<>(learned);
	}

	/** Ziel wählen – auch wenn der NPC noch nicht gefunden ist (Pending). Sobald
	 *  er in der Nähe geladen wird, startet die Navigation automatisch. */
	public static void setTarget(String name) {
		targetName = name;
		targetPos = learned.get(name); // null = wird gesucht
	}

	public static void cancel() {
		targetName = null;
		targetPos = null;
	}

	/** Ein Ziel ist GEWÄHLT (evtl. noch in Suche). */
	public static boolean hasTarget() {
		return targetName != null;
	}

	/** Ziel ist gewählt UND seine Position bekannt (Navigation läuft). */
	public static boolean targetKnown() {
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

	private static double sq(double v) {
		return v * v;
	}
}
