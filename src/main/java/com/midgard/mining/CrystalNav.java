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

	/**
	 * Ein Kristall + sein Gebiet + die Standorte (NPCs), wo man ihn bekommt.
	 * {@code color} ist die Gem-Farbe für das Icon in der Navi.
	 */
	public record CrystalArea(String crystal, int color, String areaKeyword, List<String> locations) {
	}

	/**
	 * Die fünf Crystal-Hollows-Kristalle und ihre Gebiete/NPCs. Reihenfolge =
	 * Anzeige in der Navi. (Topaz/Magma hat keinen festen NPC -> weggelassen.)
	 */
	public static final List<CrystalArea> CRYSTALS = List.of(
			new CrystalArea("Amber Crystal", 0xFFF2A93B, "goblin",
					List.of("King Yolkar")),
			new CrystalArea("Jade Crystal", 0xFF3FD466, "jungle",
					List.of("Kalhuiki Door Guardian")),
			new CrystalArea("Amethyst Crystal", 0xFFB05CFF, "precursor",
					List.of("Professor Robot")),
			new CrystalArea("Sapphire Crystal", 0xFF4F9BFF, "mithril",
					List.of("Keeper of Diamond", "Keeper of Emerald",
							"Keeper of Lapis", "Keeper of Gold")),
			new CrystalArea("Topaz Crystal", 0xFFFFD84D, "khazad",
					List.of("Bal")));

	/** Anpeilbare Orte: Mitte + alle NPCs aus {@link #CRYSTALS}. */
	public static final List<String> LOCATIONS = buildLocations();

	private static List<String> buildLocations() {
		List<String> l = new ArrayList<>();
		l.add("Crystal Nucleus");
		for (CrystalArea c : CRYSTALS) {
			l.addAll(c.locations());
		}
		return List.copyOf(l);
	}

	/**
	 * Kurz-Stichwörter -> Ort (zusätzlich zum vollen Namen, der über
	 * {@link #LOCATIONS} sowieso gematcht wird). Robust gegen Rang-/Farbpräfixe.
	 */
	private static final Map<String, String> NPC_TO_LOCATION = Map.ofEntries(
			Map.entry("yolkar", "King Yolkar"),
			Map.entry("door guardian", "Kalhuiki Door Guardian"),
			Map.entry("professor", "Professor Robot"));

	private static final int[] NUCLEUS = { 513, 125, 513 };

	/**
	 * Ungefähre Gebiets-Mitte je NPC, SOLANGE er noch nicht geladen ist – nur als
	 * Richtungshinweis. Crystal Hollows liegt grob als Kreuz um die Mitte (512):
	 * Mithril = Nord (Z&lt;512), Goblin = Süd (Z&gt;512), Jungle = West (X&lt;512),
	 * Precursor = Ost (X&gt;512). Sobald der NPC in der Nähe lädt, zählt die exakte
	 * Position. (Goblin-Süd bestätigt über Amber-Spawn ~511/703.)
	 */
	private static final Map<String, int[]> APPROX = Map.ofEntries(
			Map.entry("King Yolkar", new int[] { 500, 130, 690 }),            // Süd (Goblin)
			Map.entry("Kalhuiki Door Guardian", new int[] { 330, 130, 512 }), // West (Jungle)
			Map.entry("Professor Robot", new int[] { 700, 130, 512 }),        // Ost (Precursor)
			Map.entry("Keeper of Diamond", new int[] { 480, 130, 330 }),      // Nord (Mithril)
			Map.entry("Keeper of Emerald", new int[] { 520, 130, 330 }),
			Map.entry("Keeper of Lapis", new int[] { 540, 130, 350 }),
			Map.entry("Keeper of Gold", new int[] { 500, 130, 350 }),
			Map.entry("Bal", new int[] { 469, 81, 383 }));                    // Magma (Khazad-dûm)

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
		String area = ScoreboardReader.currentArea(mc);

		// Benannte NPCs in der Nähe erkennen und ihre Position lernen (per Name,
		// nicht per ID – die IDs ändern sich pro Session).
		if (mc.world != null) {
			long now = System.currentTimeMillis();
			boolean diag = now - lastDiagMs > 20_000;
			StringBuilder names = diag ? new StringBuilder() : null;
			int scanned = 0;
			for (Entity e : mc.world.getEntities()) {
				if (scanned++ > 300 || e.getName() == null) {
					continue;
				}
				String en = ScoreboardReader.stripFormatting(e.getName().getString());
				String low = en.toLowerCase(Locale.ROOT);
				int[] pos = { (int) Math.round(e.getX()), (int) Math.round(e.getY()), (int) Math.round(e.getZ()) };
				// voller Ortsname im NPC-Namen (z. B. "Keeper of Diamond"). Kurze
				// Namen (z. B. "Bal") nur als GANZES Wort, sonst trifft es "Blaze"/
				// Spielernamen. Lange Namen normal als Teilstring.
				for (String loc : LOCATIONS) {
					if (loc.equals("Crystal Nucleus")) {
						continue;
					}
					String key = loc.toLowerCase(Locale.ROOT);
					boolean match = loc.length() >= 5
							? low.contains(key)
							: low.matches(".*\\b" + key + "\\b.*");
					if (match) {
						learned.put(loc, pos);
					}
				}
				// Kurz-Stichwörter (yolkar, kalhuiki, professor ...)
				for (Map.Entry<String, String> e2 : NPC_TO_LOCATION.entrySet()) {
					if (low.contains(e2.getKey())) {
						learned.put(e2.getValue(), pos);
					}
				}
				if (diag && en.length() > 2 && en.matches(".*[A-Za-z].*") && !en.startsWith("[")) {
					names.append(" | ").append(en);
				}
			}
			if (diag) {
				lastDiagMs = now;
				System.out.println("[Midgard] CH-Entities (Gebiet=" + area + "):" + names);
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

	/** Ungefähre Gebiets-Position eines Ortes (für Vorab-Anzeige), sonst null. */
	public static int[] approxOf(String name) {
		int[] a = APPROX.get(name);
		return a == null ? null : a.clone();
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

	/** Zielkoordinate {x,y,z}: exakt (gelernt) oder ungefähr (Gebiet), sonst null. */
	public static double[] target() {
		int[] p = targetPos;
		if (p != null) {
			return new double[] { p[0], p[1], p[2] }; // exakt (NPC geladen)
		}
		if (targetName != null) {
			int[] ap = APPROX.get(targetName);
			if (ap != null) {
				// Ungefähr: XZ vom Gebiet, aber Y vom SPIELER -> der Pfad bleibt am
				// Boden Richtung Gebiet, statt zu einem hohen Punkt hochzufliegen.
				MinecraftClient mc = MinecraftClient.getInstance();
				double py = mc.player != null ? mc.player.getY() : ap[1];
				return new double[] { ap[0], py, ap[2] };
			}
		}
		return null;
	}

	/** Ist die Zielposition EXAKT bekannt (NPC geladen)? Sonst nur ungefähr. */
	public static boolean targetExact() {
		return targetPos != null;
	}
}
