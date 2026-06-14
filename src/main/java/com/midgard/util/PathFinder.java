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

	private static final int MAX_EXPAND = 7000;
	/** Mindestabstand (Blöcke), den man sich bewegen muss, bevor neu gerechnet wird. */
	private static final int MOVE_THRESHOLD = 8;
	/** Sonst nur selten neu rechnen (Linie bleibt ruhig stehen). */
	private static final long RECalc_MS = 4000;

	private static volatile List<Vec3d> path = List.of();
	private static BlockPos lastStart;
	private static BlockPos lastGoal;
	private static long lastCalcMs = 0;

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

		try {
			path = astar(mc.world, start, goal);
		} catch (Throwable t) {
			path = List.of();
		}
	}

	// ---- A* ---------------------------------------------------------------

	private record Node(BlockPos pos, double g, double f) {
	}

	/** Luft-Schritte kosten mehr -> der Pfad bevorzugt den Boden, fliegt nur wenn nötig. */
	private static final double AIR_PENALTY = 2.6;

	private static List<Vec3d> astar(ClientWorld world, BlockPos start, BlockPos goal) {
		if (start.getManhattanDistance(goal) > 600) {
			return straightFallback(start, goal);
		}
		// Start: Boden unter dem Spieler (auch wenn er springt/fliegt), sonst eine
		// freie Zelle (Luft).
		BlockPos s = standableStart(world, start);
		if (s == null) {
			s = nearestPassable(world, start);
		}
		if (s == null) {
			return straightFallback(start, goal);
		}
		// Ziel: begehbarer Block, sonst freie Zelle, sonst Roh-Ziel (Teilweg).
		BlockPos gs = nearestStandable(world, goal);
		if (gs == null) {
			gs = nearestPassable(world, goal);
		}
		BlockPos target = gs != null ? gs : goal;

		PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Double.compare(a.f, b.f));
		Map<Long, Double> best = new HashMap<>();
		Map<Long, BlockPos> came = new HashMap<>();
		open.add(new Node(s, 0, heur(s, target)));
		best.put(s.asLong(), 0.0);
		BlockPos bestNode = s;
		double bestH = heur(s, target);
		int expand = 0;

		while (!open.isEmpty() && expand++ < MAX_EXPAND) {
			Node cur = open.poll();
			BlockPos cp = cur.pos;
			double ch = heur(cp, target);
			if (ch < bestH) {
				bestH = ch;
				bestNode = cp;
			}
			if (cp.isWithinDistance(target, 1.6)) {
				return build(world, came, cp, s);
			}
			Double bg = best.get(cp.asLong());
			if (bg != null && cur.g > bg + 1e-3) {
				continue;
			}
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					for (int dy = -1; dy <= 1; dy++) {
						if (dx == 0 && dy == 0 && dz == 0) {
							continue;
						}
						BlockPos np = cp.add(dx, dy, dz);
						boolean ground = canStand(world, np); // fester Boden drunter
						boolean air = !ground && canFly(world, np); // nur frei (Luft)
						if (!ground && !air) {
							continue;
						}
						// Reine Vertikal-Schritte (Wand hoch/runter, Loch) nur über Luft.
						if (dx == 0 && dz == 0 && !air) {
							continue;
						}
						// Keine Diagonale durch Block-Ecken (waagerecht).
						if (dx != 0 && dz != 0) {
							if (!passable(world, cp.add(dx, 0, 0)) || !passable(world, cp.add(0, 0, dz))) {
								continue;
							}
						}
						// Luft + senkrechte Diagonale: nicht durch die Decke/Boden-Ecke.
						if (air && dy != 0 && (dx != 0 || dz != 0)) {
							if (!passable(world, cp.add(0, dy, 0)) || !passable(world, cp.add(0, dy, 0).up())) {
								continue;
							}
						}
						// Boden günstig, Luft teurer -> Boden wird bevorzugt.
						double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
						double step = ground ? dist : dist * AIR_PENALTY;
						double ng = cur.g + step;
						long key = np.asLong();
						Double old = best.get(key);
						if (old == null || ng < old - 1e-3) {
							best.put(key, ng);
							came.put(key, cp);
							open.add(new Node(np, ng, ng + heur(np, target)));
						}
					}
				}
			}
		}
		// Kein voller Weg gefunden -> Teilweg bis zum nächstgelegenen Punkt
		// (folgt dem Boden). Wenn gar nichts geht: KEINE Linie (lieber nichts als
		// eine gerade Linie durch die Wand).
		return bestNode.equals(s) ? List.of() : build(world, came, bestNode, s);
	}

	private static double heur(BlockPos a, BlockPos b) {
		double dx = a.getX() - b.getX(), dy = a.getY() - b.getY(), dz = a.getZ() - b.getZ();
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
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
			pts.add(new Vec3d(p.getX() + 0.5, p.getY() + 0.08, p.getZ() + 0.5));
		}
		// Douglas-Peucker, aber WAND-BEWUSST: ein Abschnitt wird nur dann gerade
		// gezogen, wenn die Luftlinie wirklich frei ist (sonst bleibt ein
		// Stützpunkt). So bleibt es gerade UND geht nie durch einen Block.
		return simplify(world, pts);
	}

	/** Wie stark vereinfacht wird (Blöcke). Größer = gerader, weniger Stützpunkte. */
	private static final double DP_EPS = 1.3;

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

	/** Block frei begehbar (keine Kollision)? */
	private static boolean passable(ClientWorld world, BlockPos pos) {
		return world.getBlockState(pos).getCollisionShape(world, pos).isEmpty();
	}

	/** Kann die Figur hier stehen (Boden fest, Füße + Kopf frei)? */
	private static boolean canStand(ClientWorld world, BlockPos pos) {
		if (!passable(world, pos) || !passable(world, pos.up())) {
			return false;
		}
		BlockPos below = pos.down();
		return !world.getBlockState(below).getCollisionShape(world, below).isEmpty();
	}

	/** Flug: Füße + Kopf frei (kein Boden nötig) – darf nicht in Wänden enden. */
	private static boolean canFly(ClientWorld world, BlockPos pos) {
		return passable(world, pos) && passable(world, pos.up());
	}

	/** Nächste freie (durchflugbare) Zelle um pos herum (±3). */
	private static BlockPos nearestPassable(ClientWorld world, BlockPos pos) {
		if (canFly(world, pos)) {
			return pos;
		}
		for (int r = 1; r <= 3; r++) {
			for (int dy = -r; dy <= r; dy++) {
				for (int dx = -r; dx <= r; dx++) {
					for (int dz = -r; dz <= r; dz++) {
						BlockPos n = pos.add(dx, dy, dz);
						if (canFly(world, n)) {
							return n;
						}
					}
				}
			}
		}
		return null;
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
