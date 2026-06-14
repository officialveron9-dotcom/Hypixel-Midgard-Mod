package com.midgard.mining;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.midgard.Midgard;
import com.midgard.util.Waypoints.Marker;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;

/**
 * Berechnet die Mining-Wegpunkte (Goblins/Golems, Emissäre) ENTITY-basiert –
 * zuverlässig und ohne geratene Koordinaten. WICHTIG: läuft im Tick (gecacht),
 * NICHT pro Frame, damit volle Höhlen das Spiel nicht ausbremsen. Gleichartige
 * Mobs werden zu EINEM Marker am ungefähren Spawn (Cluster-Mittelpunkt)
 * zusammengefasst, statt jeden einzeln anzuzeigen.
 */
public final class MiningWaypoints {

	private static final int MOB_COLOR = 0xFF5BE36B;
	private static final int EMISSARY_COLOR = 0xFFFFC85C;
	private static final int AREA_COLOR = 0xFF4DA6FF;
	private static final int NUCLEUS_COLOR = 0xFFD06BFF;
	/** Crystal Nucleus liegt IMMER in der Mitte von Crystal Hollows (fest). */
	private static final int[] NUCLEUS = { 513, 125, 513 };
	/** Horizontaler Radius (Blöcke), innerhalb dessen Mobs zu einem Cluster gehören. */
	private static final double CLUSTER_R = 16.0;

	/** Dwarven-Mines-Gebiete: Stichwort im Commission-Namen -> ungefähre Koordinaten. */
	private record Area(String keyword, int x, int y, int z) {
	}

	private static final List<Area> AREAS = List.of(
			new Area("royal", 170, 150, 50),
			new Area("cliffside", 1, 128, 46),
			new Area("lava spring", 41, 201, -24),
			new Area("rampart", -106, 193, -43),
			new Area("upper mines", -130, 201, -32),
			new Area("forge", 0, 145, -20));

	private static volatile List<Marker> cached = List.of();
	/** Manuell gewähltes Navi-Ziel (Navi-Liste); hat Vorrang vor der Auto-Wahl. */
	private static volatile Marker manual;

	private MiningWaypoints() {
	}

	/** Ein wählbares Dwarven-Ziel (Name + Koordinaten; learned = exakt gemerkt). */
	public record NavOption(String name, double x, double y, double z, boolean learned) {
	}

	/** Anzeigename eines Gebiets-Stichworts. */
	private static String areaDisplay(String kw) {
		switch (kw) {
			case "royal":
				return "Royal Mines";
			case "cliffside":
				return "Cliffside";
			case "lava spring":
				return "Lava Springs";
			case "rampart":
				return "Rampart's Quarry";
			case "upper mines":
				return "Upper Mines";
			case "forge":
				return "The Forge";
			default:
				return kw;
		}
	}

	/** Auswählbare Ziele in den Dwarven Mines (Emissär-Gebiete; gemerkt > ungefähr). */
	public static List<NavOption> dwarvenTargets() {
		List<NavOption> out = new ArrayList<>();
		for (Area a : AREAS) {
			List<Integer> learned = Midgard.config != null ? Midgard.config.learnedEmissary.get(a.keyword()) : null;
			if (learned != null && learned.size() == 3) {
				out.add(new NavOption(areaDisplay(a.keyword()), learned.get(0), learned.get(1), learned.get(2), true));
			} else {
				out.add(new NavOption(areaDisplay(a.keyword()), a.x(), a.y(), a.z(), false));
			}
		}
		return out;
	}

	public static void setManual(double x, double y, double z, String name) {
		manual = new Marker(x, y, z, name, EMISSARY_COLOR);
	}

	public static void clearManual() {
		manual = null;
	}

	public static Marker manual() {
		return manual;
	}

	public static boolean hasManual() {
		return manual != null;
	}

	/** Ungefähre Gebiets-Koordinaten zum Commission-Namen oder null. */
	private static Area areaFor(String commissionName) {
		String low = commissionName.toLowerCase(Locale.ROOT);
		for (Area a : AREAS) {
			if (low.contains(a.keyword())) {
				return a;
			}
		}
		return null;
	}

	/** Vom Client-Tick aufgerufen (nicht pro Frame). */
	public static void tick(MinecraftClient mc) {
		if (mc == null || mc.world == null || mc.player == null || Midgard.config == null
				|| !MiningData.INSTANCE.onMiningIsland) {
			cached = List.of();
			manual = null; // Mine verlassen -> manuelles Ziel aufheben
			return;
		}

		boolean mobs = Midgard.config.miningGoblinWaypoints;
		boolean comWp = Midgard.config.miningCommissionWaypoints;
		List<Marker> out = new ArrayList<>();

		// Crystal Hollows: gewähltes Navi-Ziel zeigen, sonst die Mitte (Nucleus).
		if (comWp && MiningData.INSTANCE.onCrystalHollows) {
			double[] nav = CrystalNav.target();
			if (nav != null) {
				out.add(new Marker(nav[0], nav[1], nav[2], CrystalNav.targetName(), NUCLEUS_COLOR));
			} else if (!CrystalNav.hasTarget()) {
				out.add(new Marker(NUCLEUS[0], NUCLEUS[1], NUCLEUS[2], "Crystal Nucleus", NUCLEUS_COLOR));
			}
		}

		// Entities einmal scannen: Goblins/Golems (für Mobs) UND Emissäre (um
		// den Commission-Wegpunkt genau auf die Emissär-Person zu legen).
		List<double[]> goblins = new ArrayList<>();
		List<double[]> golems = new ArrayList<>();
		List<double[]> emissaries = new ArrayList<>();
		if (mobs || comWp) {
			int scanned = 0;
			for (Entity e : mc.world.getEntities()) {
				if (scanned++ > 400) {
					break;
				}
				if (e.getName() == null) {
					continue;
				}
				String low = e.getName().getString().toLowerCase(Locale.ROOT);
				double[] pos = { e.getX(), e.getY(), e.getZ() };
				if (comWp && low.contains("emissary")) {
					emissaries.add(pos);
				} else if (mobs && low.contains("goblin") && !low.contains("slayer")) {
					goblins.add(pos);
				} else if (mobs && (low.contains("golem") || low.contains("walker"))) {
					golems.add(pos);
				}
			}
		}

		// Sichtbare Emissäre ihrer Region zuordnen und die exakte Position
		// DAUERHAFT merken (einmal gesehen -> auch von weit weg direkt dahin).
		if (comWp && !emissaries.isEmpty()) {
			boolean changed = false;
			for (double[] em : emissaries) {
				Area a = nearestArea(em, 55);
				if (a == null) {
					continue;
				}
				List<Integer> cur = Midgard.config.learnedEmissary.get(a.keyword());
				int ex = (int) Math.round(em[0]), ey = (int) Math.round(em[1]), ez = (int) Math.round(em[2]);
				if (cur == null || cur.size() != 3 || Math.abs(cur.get(0) - ex) > 2 || Math.abs(cur.get(2) - ez) > 2) {
					Midgard.config.learnedEmissary.put(a.keyword(), List.of(ex, ey, ez));
					changed = true;
				}
			}
			if (changed) {
				Midgard.config.save();
			}
		}

		// Commission-Wegpunkte: Ziel = geladene Emissär-Person -> gemerkte
		// Position -> ungefähres Gebiet (in dieser Reihenfolge).
		if (comWp) {
			for (MiningData.Commission c : MiningData.INSTANCE.commissions) {
				Area a = areaFor(c.name());
				if (a == null) {
					continue;
				}
				double tx, ty, tz;
				double[] live = nearestTo(emissaries, a.x(), a.y(), a.z(), 45);
				List<Integer> learned = Midgard.config.learnedEmissary.get(a.keyword());
				if (live != null) {
					tx = live[0];
					ty = live[1];
					tz = live[2];
				} else if (learned != null && learned.size() == 3) {
					tx = learned.get(0);
					ty = learned.get(1);
					tz = learned.get(2);
				} else {
					tx = a.x();
					ty = a.y();
					tz = a.z();
				}
				if (c.done()) {
					out.add(new Marker(tx, ty, tz, c.name() + " abgeben", EMISSARY_COLOR));
				} else {
					out.add(new Marker(tx, ty, tz, c.name(), AREA_COLOR));
				}
			}
		}

		cluster(goblins, "Goblins", out);
		cluster(golems, "Golems", out);
		if (manual != null) {
			out.add(manual);
		}
		cached = out;
	}

	/** Das einer Position nächstgelegene Gebiet innerhalb {@code range} Blöcken, oder null. */
	private static Area nearestArea(double[] pos, double range) {
		Area best = null;
		double bestD = range * range;
		for (Area a : AREAS) {
			double dx = a.x() - pos[0], dy = a.y() - pos[1], dz = a.z() - pos[2];
			double d = dx * dx + dy * dy + dz * dz;
			if (d <= bestD) {
				bestD = d;
				best = a;
			}
		}
		return best;
	}

	/** Nächste Position aus {@code pts} zu (x,y,z) innerhalb {@code range} Blöcken, oder null. */
	private static double[] nearestTo(List<double[]> pts, double x, double y, double z, double range) {
		double[] best = null;
		double bestD = range * range;
		for (double[] p : pts) {
			double dx = p[0] - x, dy = p[1] - y, dz = p[2] - z;
			double d = dx * dx + dy * dy + dz * dz;
			if (d <= bestD) {
				bestD = d;
				best = p;
			}
		}
		return best;
	}

	/** Liefert die gecachte Marker-Liste (im Render-Pfad, billig). */
	public static List<Marker> markers() {
		return cached;
	}

	/** Der dem Spieler nächste Marker (für die Pfad-Linie) oder null. */
	public static Marker nearest() {
		Marker mm = manual; // manuelles Ziel hat Vorrang
		if (mm != null) {
			return mm;
		}
		List<Marker> list = cached;
		MinecraftClient mc = MinecraftClient.getInstance();
		if (list.isEmpty() || mc.player == null) {
			return null;
		}
		double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
		Marker best = null;
		double bestD = Double.MAX_VALUE;
		for (Marker m : list) {
			double dx = m.x() - px, dy = m.y() - py, dz = m.z() - pz;
			double d = dx * dx + dy * dy + dz * dz;
			if (d < bestD) {
				bestD = d;
				best = m;
			}
		}
		return best;
	}

	/** Fasst nahe beieinander liegende Positionen zu Cluster-Mittelpunkten zusammen. */
	private static void cluster(List<double[]> pts, String name, List<Marker> out) {
		List<double[]> centers = new ArrayList<>(); // {sumX,sumY,sumZ,count}
		for (double[] p : pts) {
			double[] hit = null;
			for (double[] c : centers) {
				double cx = c[0] / c[3];
				double cz = c[2] / c[3];
				double dx = p[0] - cx;
				double dz = p[2] - cz;
				if (dx * dx + dz * dz <= CLUSTER_R * CLUSTER_R) {
					hit = c;
					break;
				}
			}
			if (hit == null) {
				centers.add(new double[] { p[0], p[1], p[2], 1 });
			} else {
				hit[0] += p[0];
				hit[1] += p[1];
				hit[2] += p[2];
				hit[3] += 1;
			}
		}
		for (double[] c : centers) {
			int n = (int) c[3];
			String label = n > 1 ? name + " (" + n + ")" : name;
			out.add(new Marker(c[0] / n, c[1] / n, c[2] / n, label, MOB_COLOR));
		}
	}
}
