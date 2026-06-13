package com.midgard.mining;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.midgard.Midgard;
import com.midgard.util.Waypoints.Marker;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;

/**
 * Sammelt die Mining-Wegpunkte für den {@link com.midgard.util.Waypoints}-
 * Renderer. ZUVERLÄSSIG: Goblins werden live aus den Welt-Entities erkannt
 * (kein Raten). Commission-GEBIETE in den Dwarven Mines sind feste Orte – die
 * Koordinaten hier sind aber NUR ungefähr (aus dem Wiki) und müssen am echten
 * Spiel verifiziert werden; Crystal Hollows ist zufällig generiert, dort gibt
 * es keine festen Gebiets-Koordinaten.
 */
public final class MiningWaypoints {

	private static final int GOBLIN_COLOR = 0xFF5BE36B;
	private static final int AREA_COLOR = 0xFF4DA6FF;

	/** Bekannte Dwarven-Mines-Gebiete (Name-Stichwort -> ungefähre Koordinaten). */
	private record Area(String keyword, int x, int y, int z) {
	}

	private static final List<Area> AREAS = List.of(
			new Area("royal", 171, 150, 31),
			new Area("cliffside", 0, 128, 50),
			new Area("lava spring", 41, 201, -24),
			new Area("rampart", -76, 174, -67),
			new Area("upper mines", -100, 188, -30),
			new Area("goblin", 30, 130, 70),
			new Area("aristocrat", -19, 196, 50),
			new Area("hanging court", 0, 77, 70),
			new Area("palace", 0, 110, 120),
			new Area("divan", 41, 154, -100));

	private MiningWaypoints() {
	}

	public static List<Marker> markers() {
		List<Marker> out = new ArrayList<>();
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.world == null || mc.player == null || Midgard.config == null
				|| !MiningData.INSTANCE.onMiningIsland) {
			return out;
		}

		// 1) Goblins live aus den Entities (zuverlässig, keine festen Koordinaten).
		if (Midgard.config.miningGoblinWaypoints) {
			int count = 0;
			for (Entity e : mc.world.getEntities()) {
				if (count >= 30) {
					break;
				}
				String name = e.getName() == null ? "" : e.getName().getString().toLowerCase(Locale.ROOT);
				if (name.contains("goblin") && !name.contains("slayer")) {
					out.add(new Marker(e.getX(), e.getY(), e.getZ(),
							e.getName().getString(), GOBLIN_COLOR));
					count++;
				}
			}
		}

		// 2) Commission-Gebiet (nur Dwarven Mines, ungefähre Koordinaten).
		if (Midgard.config.miningCommissionWaypoints) {
			for (MiningData.Commission c : MiningData.INSTANCE.commissions) {
				if (c.done()) {
					continue;
				}
				String low = c.name().toLowerCase(Locale.ROOT);
				for (Area a : AREAS) {
					if (low.contains(a.keyword())) {
						out.add(new Marker(a.x(), a.y(), a.z(), c.name() + " (ca.)", AREA_COLOR));
						break;
					}
				}
			}
		}

		return out;
	}
}
