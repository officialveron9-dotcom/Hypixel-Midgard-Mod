package com.midgard.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Begehbarer Weg per A* über die Client-Welt: findet den kürzesten Pfad, der
 * NUR über Blöcke geht, auf denen die Spielfigur stehen kann (Boden fest,
 * Kopf+Füße frei). Unsichtbare Wände auf Hypixel sind echte Barrier-Blöcke mit
 * Kollision – die werden dadurch automatisch umgangen. Bounded (begrenzte
 * Knotenzahl) und gedrosselt, damit es nicht ruckelt; das Ergebnis wird
 * gecacht und vom {@link PathRenderer} in der Welt gezeichnet.
 */
public final class PathFinder {

	private static final int MAX_EXPAND = 9000;
	/** Hartes Zeitbudget pro Berechnung – kappt jeden Ruckler. */
	private static final long BUDGET_NS = 7_000_000;
	/** Heuristik-Gewicht (>1 = zielstrebiger, findet einen Weg schneller, ggf. nicht
	 *  der allerkürzeste – aber er erreicht das Ziel eher innerhalb des Budgets). */
	private static final double H_WEIGHT = 1.3;
	/** Mindestabstand (Blöcke), den man sich bewegen muss, bevor neu gerechnet wird. */
	private static final int MOVE_THRESHOLD = 8;
	/** Sonst nur selten neu rechnen (Linie bleibt ruhig stehen). */
	private static final long RECalc_MS = 4000;

	private static volatile List<Vec3d> path = List.of();
	private static BlockPos lastStart;
	private static BlockPos lastGoal;
	private static long lastCalcMs = 0;

	// Diagnose des letzten A*-Laufs (fürs Log).
	private static boolean diagReached;
	private static boolean diagBudgetHit;
	private static int diagExpand;
	private static double diagBestH;

	/** 4 Himmelsrichtungen (für senkrechte Loch-Auf-/Abstiege). */
	private static final int[] HX = { 1, -1, 0, 0 };
	private static final int[] HZ = { 0, 0, 1, -1 };

	/**
	 * Begehbarkeits-Cache pro Berechnung: {@code getCollisionShape} ist teuer und
	 * jeder Block wird als Nachbar mehrfach geprüft. Einmal pro Block je A*-Lauf
	 * abfragen spart den Großteil der Arbeit -> deutlich weniger FPS-Einbruch.
	 * Nur auf dem Client-Thread benutzt, daher kein Threading nötig.
	 */
	private static final Map<Long, Boolean> passCache = new HashMap<>();

	private PathFinder() {
	}

	public static List<Vec3d> currentPath() {
		return path;
	}

	public static void clear() {
		path = List.of();
		lastStart = null;
		lastGoal = null;
	}

	/**
	 * Aktualisiert den Pfad zum Ziel. EIN vereinheitlichter Weg: bevorzugt den
	 * BODEN (günstig), darf aber bei Bedarf über die LUFT gehen (Aufschlag) –
	 * z. B. eine Wand hoch oder über eine Lücke –, NIE durch solide Blöcke.
	 */
	public static void update(double gx, double gy, double gz) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null || mc.world == null) {
			clear();
			return;
		}
		BlockPos start = mc.player.getBlockPos();
		BlockPos goal = BlockPos.ofFloored(gx, gy, gz);

		long now = System.currentTimeMillis();
		boolean goalChanged = lastGoal == null || !goal.equals(lastGoal);
		boolean movedFar = lastStart == null || start.getManhattanDistance(lastStart) > MOVE_THRESHOLD;
		// Ziel gewechselt -> sofort neu; sonst nur bei großem Schritt ODER nach
		// langer Zeit (kein Neuberechnen wegen ein paar Metern).
		if (!goalChanged && !movedFar && now - lastCalcMs < RECalc_MS && !path.isEmpty()) {
			return;
		}
		lastStart = start;
		lastGoal = goal;
		lastCalcMs = now;

		List<Vec3d> result;
		try {
			result = astar(mc.world, start, goal);
		} catch (Throwable t) {
			result = List.of();
		}
		// Stabil: einen NEUEN gültigen Weg übernehmen. Kommt diesmal nichts heraus
		// (kurzzeitig kein Weg), den alten behalten – außer das Ziel wechselte. So
		// verschwindet die Linie nicht mehr zufällig zwischendurch.
		if (!result.isEmpty()) {
			path = result;
		} else if (goalChanged) {
			path = List.of();
		}
		// Diagnose (nur bei echter Neuberechnung, also gedrosselt): hat A* das Ziel
		// WIRKLICH erreicht oder nur abgebrochen? -> zeigt, ob der Höhen-Umweg fehlt.
		System.out.println("[Midgard] PathCalc Ziel-erreicht=" + diagReached + " Budget-aus=" + diagBudgetHit
				+ " Knoten=" + diagExpand + " RestDistanz=" + Math.round(diagBestH) + " Punkte=" + path.size());
	}

	// ---- A* ---------------------------------------------------------------

	private record Node(BlockPos pos, double g, double f) {
	}

	private static List<Vec3d> astar(ClientWorld world, BlockPos start, BlockPos goal) {
		passCache.clear(); // Begehbarkeits-Cache für genau diesen Lauf
		if (passCache.size() > 200_000) {
			passCache.clear();
		}
		long deadline = System.nanoTime() + BUDGET_NS;
		if (start.getManhattanDistance(goal) > 600) {
			return straightFallback(start, goal);
		}
		// Start: fester Boden unter dem Spieler (auch wenn er springt).
		BlockPos s = standableStart(world, start);
		if (s == null) {
			return List.of(); // kein begehbarer Start -> keine Linie
		}
		// Ziel: nächster begehbarer Block, sonst Roh-Ziel (Teilweg dahin).
		BlockPos gs = nearestStandable(world, goal);
		BlockPos target = gs != null ? gs : goal;

		PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Double.compare(a.f, b.f));
		Map<Long, Double> best = new HashMap<>();
		Map<Long, BlockPos> came = new HashMap<>();
		open.add(new Node(s, 0, H_WEIGHT * heur(s, target)));
		best.put(s.asLong(), 0.0);
		BlockPos bestNode = s;
		double bestH = heur(s, target);
		int expand = 0;
		diagBudgetHit = false;

		while (!open.isEmpty() && expand++ < MAX_EXPAND) {
			// Harte Zeitgrenze: lieber ein Teilweg als ein Frame-Ruckler.
			if ((expand & 255) == 0 && System.nanoTime() > deadline) {
				diagBudgetHit = true;
				break;
			}
			Node cur = open.poll();
			BlockPos cp = cur.pos;
			double ch = heur(cp, target);
			if (ch < bestH) {
				bestH = ch;
				bestNode = cp;
			}
			if (cp.isWithinDistance(target, 1.6)) {
				diagReached = true;
				diagExpand = expand;
				diagBestH = 0;
				return build(world, came, cp, s);
			}
			Double bg = best.get(cp.asLong());
			if (bg != null && cur.g > bg + 1e-3) {
				continue;
			}
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (dx == 0 && dz == 0) {
						continue;
					}
					// Keine Diagonale durch Block-Ecken (sonst klemmt der Spieler).
					if (dx != 0 && dz != 0
							&& (!passable(world, cp.add(dx, 0, 0)) || !passable(world, cp.add(0, 0, dz)))) {
						continue;
					}
					double horiz = Math.sqrt(dx * dx + dz * dz);
					// a) Gehen / 1 hoch (springen) / 1 runter: höchsten begehbaren Block.
					BlockPos step = null;
					int stepDy = 0;
					for (int dy = 1; dy >= -1; dy--) {
						BlockPos np = cp.add(dx, dy, dz);
						if (canStand(world, np)) {
							step = np;
							stepDy = dy;
							break;
						}
					}
					if (step != null) {
						// Hochspringen nur, wenn über dem Start Kopffreiheit ist.
						if (stepDy <= 0 || passable(world, cp.up(2))) {
							relax(open, best, came, cp, cur.g, step, horiz + (stepDy != 0 ? 0.4 : 0), target);
						}
						continue;
					}
					// b) Fallen: Kante frei -> nach unten zum nächsten Landeplatz (bis 12
					// tief, z. B. durch ein Loch im BODEN in die Etage darunter). Immer
					// 2 frei am Landeplatz (canStand) -> man passt durch und kann laufen.
					BlockPos edge = cp.add(dx, 0, dz);
					if (passable(world, edge) && passable(world, edge.up())) {
						for (int d = 2; d <= 12; d++) {
							BlockPos land = cp.add(dx, -d, dz);
							if (canStand(world, land)) {
								relax(open, best, came, cp, cur.g, land, horiz + d * 0.25, target);
								break;
							}
							if (!passable(world, land)) {
								break; // auf einen Block getroffen -> kein Durchfall hier
							}
						}
					}
				}
			}
			// c) Durch ein Loch in der DECKE nach oben: über dem Kopf ein freier
			// Schacht (immer 2 frei), dann seitlich auf den oberen Boden am Lochrand
			// treten. So führt der Weg durch Decken-Löcher in die Etage darüber.
			for (int dy = 1; dy <= 6; dy++) {
				if (!passable(world, cp.up(dy + 1))) {
					break; // Schacht/Decke zu -> nicht weiter hoch
				}
				for (int di = 0; di < 4; di++) {
					BlockPos side = cp.add(HX[di], dy, HZ[di]);
					if (canStand(world, side)) {
						relax(open, best, came, cp, cur.g, side, 1.3 * dy + 1.0, target);
					}
				}
			}
		}
		// Kein voller Weg gefunden -> Teilweg bis zum nächstgelegenen Punkt
		// (folgt dem Boden). Kommt man horizontal NICHT voran (eingeschlossen),
		// nach OBEN auf einen offenen Schacht zeigen, statt durch die Wand.
		diagReached = false;
		diagExpand = expand;
		diagBestH = bestH;
		return bestNode.equals(s) ? verticalEscape(world, s) : build(world, came, bestNode, s);
	}

	/**
	 * Eingeschlossen: gibt es über dem Spieler einen offenen Schacht, zeigt die
	 * Linie senkrecht nach oben (dort ist der Ausweg). Sonst keine Linie – nie
	 * durch einen Block.
	 */
	private static List<Vec3d> verticalEscape(ClientWorld world, BlockPos s) {
		int topDy = 0;
		for (int dy = 1; dy <= 16; dy++) {
			if (!passable(world, s.up(dy))) {
				break;
			}
			topDy = dy;
		}
		if (topDy < 2) {
			return List.of(); // kein Platz nach oben
		}
		List<Vec3d> pts = new ArrayList<>();
		pts.add(new Vec3d(s.getX() + 0.5, s.getY() + 0.5, s.getZ() + 0.5));
		pts.add(new Vec3d(s.getX() + 0.5, s.getY() + topDy + 0.5, s.getZ() + 0.5));
		return pts;
	}

	private static double heur(BlockPos a, BlockPos b) {
		double dx = a.getX() - b.getX(), dy = a.getY() - b.getY(), dz = a.getZ() - b.getZ();
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	/** Nachbar {@code np} in die Open-Liste aufnehmen, wenn der neue Weg kürzer ist. */
	private static void relax(PriorityQueue<Node> open, Map<Long, Double> best, Map<Long, BlockPos> came,
			BlockPos from, double fromG, BlockPos np, double cost, BlockPos target) {
		double ng = fromG + cost;
		long key = np.asLong();
		Double old = best.get(key);
		if (old == null || ng < old - 1e-3) {
			best.put(key, ng);
			came.put(key, from);
			open.add(new Node(np, ng, ng + H_WEIGHT * heur(np, target)));
		}
	}

	private static List<Vec3d> build(ClientWorld world, Map<Long, BlockPos> came, BlockPos end, BlockPos start) {
		List<BlockPos> nodes = new ArrayList<>();
		BlockPos c = end;
		int guard = 0;
		while (c != null && guard++ < 1000) {
			nodes.add(c);
			if (c.equals(start)) {
				break;
			}
			c = came.get(c.asLong());
		}
		Collections.reverse(nodes);
		List<Vec3d> pts = new ArrayList<>();
		for (BlockPos p : nodes) {
			// Einen halben Block ÜBER dem Boden -> besser sichtbar, klebt nicht im
			// Boden/an der Wandkante (p.getY() ist der Fuß-Block, Boden = darunter).
			pts.add(new Vec3d(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5));
		}
		// Douglas-Peucker, aber WAND-BEWUSST: ein Abschnitt wird nur dann gerade
		// gezogen, wenn die Luftlinie wirklich frei ist (sonst bleibt ein
		// Stützpunkt). So bleibt es gerade UND geht nie durch einen Block.
		return simplify(world, pts);
	}

	/** Wie stark vereinfacht wird (Blöcke). Kleiner = folgt enger dem Boden/Stufen. */
	private static final double DP_EPS = 0.55;

	private static List<Vec3d> simplify(ClientWorld world, List<Vec3d> pts) {
		int sz = pts.size();
		if (sz < 3) {
			return pts;
		}
		boolean[] keep = new boolean[sz];
		keep[0] = true;
		keep[sz - 1] = true;
		dp(world, pts, 0, sz - 1, keep);
		List<Vec3d> out = new ArrayList<>();
		for (int i = 0; i < sz; i++) {
			if (keep[i]) {
				out.add(pts.get(i));
			}
		}
		return out;
	}

	/**
	 * Douglas-Peucker mit Wand-Prüfung: behält den am weitesten abweichenden Punkt,
	 * wenn die Abweichung groß ist ODER die direkte Strecke a-b durch einen Block
	 * ginge. Dadurch wird nie eine Wand geschnitten.
	 */
	private static void dp(ClientWorld world, List<Vec3d> pts, int a, int b, boolean[] keep) {
		if (b <= a + 1) {
			return;
		}
		Vec3d pa = pts.get(a), pb = pts.get(b);
		double maxD = -1;
		int idx = -1;
		for (int i = a + 1; i < b; i++) {
			double d = pointSegDist(pts.get(i), pa, pb);
			if (d > maxD) {
				maxD = d;
				idx = i;
			}
		}
		if (idx > a && (maxD > DP_EPS || !clearLine(world, pa, pb))) {
			keep[idx] = true;
			dp(world, pts, a, idx, keep);
			dp(world, pts, idx, b, keep);
		}
	}

	/** Ist die direkte Strecke a-b frei (jede Zelle durchquerbar, keine Wand)? */
	private static boolean clearLine(ClientWorld world, Vec3d a, Vec3d b) {
		double dist = a.distanceTo(b);
		int steps = Math.max(1, (int) (dist / 0.35));
		for (int s = 0; s <= steps; s++) {
			double t = (double) s / steps;
			double x = a.x + (b.x - a.x) * t;
			double y = a.y + (b.y - a.y) * t;
			double z = a.z + (b.z - a.z) * t;
			if (!canFly(world, BlockPos.ofFloored(x, y, z))) {
				return false;
			}
		}
		return true;
	}

	/** Abstand Punkt p zur Strecke a-b (3D). */
	private static double pointSegDist(Vec3d p, Vec3d a, Vec3d b) {
		double abx = b.x - a.x, aby = b.y - a.y, abz = b.z - a.z;
		double ab2 = abx * abx + aby * aby + abz * abz;
		double t = ab2 < 1e-9 ? 0 : ((p.x - a.x) * abx + (p.y - a.y) * aby + (p.z - a.z) * abz) / ab2;
		t = Math.max(0, Math.min(1, t));
		double cx = a.x + abx * t, cy = a.y + aby * t, cz = a.z + abz * t;
		double dx = p.x - cx, dy = p.y - cy, dz = p.z - cz;
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}


	/** Kein begehbarer Weg -> KEINE Linie (nie eine gerade Linie durch Wände). */
	private static List<Vec3d> straightFallback(BlockPos start, BlockPos goal) {
		return List.of();
	}

	// ---- Begehbarkeit -----------------------------------------------------

	/** Block frei begehbar (keine Kollision)? – gecacht pro A*-Lauf. */
	private static boolean passable(ClientWorld world, BlockPos pos) {
		long k = pos.asLong();
		Boolean v = passCache.get(k);
		if (v != null) {
			return v;
		}
		boolean r = world.getBlockState(pos).getCollisionShape(world, pos).isEmpty();
		passCache.put(k, r);
		return r;
	}

	/** Kann die Figur hier stehen (Boden fest, Füße + Kopf frei)? */
	private static boolean canStand(ClientWorld world, BlockPos pos) {
		// Boden fest = unter den Füßen NICHT begehbar (Kollision vorhanden).
		return passable(world, pos) && passable(world, pos.up()) && !passable(world, pos.down());
	}

	/** Füße + Kopf frei (keine Kollision) – für die Sichtlinien-Prüfung. */
	private static boolean canFly(ClientWorld world, BlockPos pos) {
		return passable(world, pos) && passable(world, pos.up());
	}

	/** Sucht vom Startpunkt aus den nächstgelegenen begehbaren Block (±2 vertikal). */
	private static BlockPos standable(ClientWorld world, BlockPos pos) {
		for (int dy = 0; dy <= 2; dy++) {
			if (canStand(world, pos.up(dy))) {
				return pos.up(dy);
			}
			if (canStand(world, pos.down(dy))) {
				return pos.down(dy);
			}
		}
		return null;
	}

	/**
	 * Start-Block: erst normal (±2), dann TIEFER nach unten suchen. So findet die
	 * Wegfindung den Boden auch, wenn der Spieler in der Luft ist (Springen/
	 * Fliegen) – der Pfad bleibt am Boden sichtbar.
	 */
	private static BlockPos standableStart(ClientWorld world, BlockPos pos) {
		BlockPos s = standable(world, pos);
		if (s != null) {
			return s;
		}
		for (int dy = 3; dy <= 30; dy++) {
			if (canStand(world, pos.down(dy))) {
				return pos.down(dy);
			}
		}
		return null;
	}

	private static BlockPos nearestStandable(ClientWorld world, BlockPos goal) {
		BlockPos direct = standable(world, goal);
		if (direct != null) {
			return direct;
		}
		// Ziel steckt in einem NPC/Block – nächsten begehbaren Nachbarn nehmen.
		for (int r = 1; r <= 3; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					BlockPos n = standable(world, goal.add(dx, 0, dz));
					if (n != null) {
						return n;
					}
				}
			}
		}
		return null;
	}
}
