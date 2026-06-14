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
	/** Horizontaler Radius (Blöcke), innerhalb dessen Mobs zu einem Cluster gehören. */
	private static final double CLUSTER_R = 16.0;

	private static volatile List<Marker> cached = List.of();

	private MiningWaypoints() {
	}

	/** Vom Client-Tick aufgerufen (nicht pro Frame). */
	public static void tick(MinecraftClient mc) {
		if (mc == null || mc.world == null || mc.player == null || Midgard.config == null
				|| !MiningData.INSTANCE.onMiningIsland) {
			cached = List.of();
			return;
		}

		boolean mobs = Midgard.config.miningGoblinWaypoints;
		boolean anyDone = false;
		for (MiningData.Commission c : MiningData.INSTANCE.commissions) {
			if (c.done()) {
				anyDone = true;
				break;
			}
		}
		boolean emissaries = Midgard.config.miningCommissionWaypoints && anyDone;
		if (!mobs && !emissaries) {
			cached = List.of();
			return;
		}

		List<double[]> goblins = new ArrayList<>();
		List<double[]> golems = new ArrayList<>();
		List<Marker> out = new ArrayList<>();

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
			if (mobs && low.contains("goblin") && !low.contains("slayer")) {
				goblins.add(pos);
			} else if (mobs && (low.contains("golem") || low.contains("walker"))) {
				golems.add(pos);
			} else if (emissaries && low.contains("emissary")) {
				// Emissäre sind einzelne, weit verteilte NPCs -> jeder eigen.
				out.add(new Marker(pos[0], pos[1], pos[2], e.getName().getString(), EMISSARY_COLOR));
			}
		}

		// Mobs zu ungefähren Spawn-Markern zusammenfassen.
		cluster(goblins, "Goblins", out);
		cluster(golems, "Golems", out);
		cached = out;
	}

	/** Liefert die gecachte Marker-Liste (im Render-Pfad, billig). */
	public static List<Marker> markers() {
		return cached;
	}

	/**
	 * Der dem Spieler nächste Marker (für die Pfad-Linie) oder null. Sind
	 * Emissäre dabei (= eine Commission ist fertig), wird der NÄCHSTE EMISSÄR
	 * bevorzugt – damit der Weg zur Abgabe gezeigt wird.
	 */
	public static Marker nearest() {
		List<Marker> list = cached;
		MinecraftClient mc = MinecraftClient.getInstance();
		if (list.isEmpty() || mc.player == null) {
			return null;
		}
		double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
		boolean hasEmissary = false;
		for (Marker m : list) {
			if (m.color() == EMISSARY_COLOR) {
				hasEmissary = true;
				break;
			}
		}
		Marker best = null;
		double bestD = Double.MAX_VALUE;
		for (Marker m : list) {
			if (hasEmissary && m.color() != EMISSARY_COLOR) {
				continue; // bei fertiger Commission nur Emissäre als Ziel
			}
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
