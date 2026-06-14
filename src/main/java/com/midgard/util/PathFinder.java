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
	 * Aktualisiert den Pfad zum Ziel. {@code straight} = direkter Luftweg
	 * (Teleport-Item), sonst echte Wegfindung über begehbare Blöcke.
	 */
	public static void update(double gx, double gy, double gz, boolean straight) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null || mc.world == null) {
			clear();
			return;
		}
		BlockPos start = mc.player.getBlockPos();
		BlockPos goal = BlockPos.ofFloored(gx, gy, gz);

		if (straight) {
			path = List.of(
					new Vec3d(start.getX() + 0.5, start.getY() + 0.1, start.getZ() + 0.5),
					new Vec3d(goal.getX() + 0.5, goal.getY() + 0.1, goal.getZ() + 0.5));
			return;
		}

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

	private static List<Vec3d> astar(ClientWorld world, BlockPos start, BlockPos goal) {
		if (start.getManhattanDistance(goal) > 600) {
			return straightFallback(start, goal);
		}
		BlockPos s = standable(world, start);
		if (s == null) {
			return straightFallback(start, goal);
		}
		// Begehbares Ziel finden; sonst Richtung Roh-Ziel laufen (Teilweg).
		BlockPos gs = nearestStandable(world, goal);
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
				return build(came, cp, s);
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
					for (int dy = -1; dy <= 1; dy++) {
						BlockPos np = cp.add(dx, dy, dz);
						if (!canStand(world, np)) {
							continue;
						}
						// Keine Diagonale durch Block-Ecken.
						if (dx != 0 && dz != 0) {
							if (!passable(world, cp.add(dx, 0, 0)) || !passable(world, cp.add(0, 0, dz))) {
								continue;
							}
						}
						double step = Math.sqrt(dx * dx + dz * dz) + (dy != 0 ? 0.5 : 0);
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
		// (folgt dem Boden), statt einer geraden Linie durch die Wand.
		return bestNode.equals(s) ? straightFallback(start, goal) : build(came, bestNode, s);
	}

	private static double heur(BlockPos a, BlockPos b) {
		double dx = a.getX() - b.getX(), dy = a.getY() - b.getY(), dz = a.getZ() - b.getZ();
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	private static List<Vec3d> build(Map<Long, BlockPos> came, BlockPos end, BlockPos start) {
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
		List<Vec3d> out = new ArrayList<>();
		for (BlockPos p : nodes) {
			out.add(new Vec3d(p.getX() + 0.5, p.getY() + 0.08, p.getZ() + 0.5));
		}
		return out;
	}

	private static List<Vec3d> straightFallback(BlockPos start, BlockPos goal) {
		return List.of(
				new Vec3d(start.getX() + 0.5, start.getY() + 0.1, start.getZ() + 0.5),
				new Vec3d(goal.getX() + 0.5, goal.getY() + 0.1, goal.getZ() + 0.5));
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
