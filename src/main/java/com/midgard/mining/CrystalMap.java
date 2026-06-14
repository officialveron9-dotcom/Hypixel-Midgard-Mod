package com.midgard.mining;

import java.util.ArrayList;
import java.util.List;

import com.midgard.events.skyblock.ScoreboardReader;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.map.MapDecoration;
import net.minecraft.item.map.MapState;

/**
 * Liest die gehaltene Crystal-Hollows-KARTE (vanilla Filled-Map). Die Karte
 * trägt das Bild der Gebiete (Farben) UND – falls Hypixel sie setzt – Marker
 * (Dekorationen) mit Name + Position. Daraus bekommt man die Gebiets-Farben für
 * die Minimap und evtl. die Standorte wichtiger Punkte OHNE in der Nähe zu sein.
 *
 * <p>WICHTIG: Daten gibt es nur, wenn der Spieler die Karte HÄLT/im Inventar hat
 * (der Server synct sie dann). Ein Diagnose-Log zeigt alle 15 s, welche Marker
 * die Karte enthält – damit lässt sich prüfen, ob NPC-Standorte ablesbar sind.</p>
 */
public final class CrystalMap {

	/** Ein Karten-Marker: Name (falls vorhanden), Weltkoordinaten, Typ. */
	public record Deco(String name, double x, double z, String type) {
	}

	private static volatile MapState state;
	private static volatile List<Deco> decos = List.of();
	private static long lastDiagMs = 0;

	private CrystalMap() {
	}

	public static MapState state() {
		return state;
	}

	public static List<Deco> decorations() {
		return decos;
	}

	public static boolean hasMap() {
		return state != null;
	}

	public static void tick(MinecraftClient mc) {
		if (mc.player == null || mc.world == null || !MiningData.INSTANCE.onCrystalHollows) {
			state = null;
			decos = List.of();
			return;
		}
		MapState ms = findMap(mc);
		state = ms;
		if (ms == null) {
			decos = List.of();
			return;
		}
		int sc = 1 << ms.scale;
		List<Deco> out = new ArrayList<>();
		StringBuilder diag = new StringBuilder();
		try {
			for (MapDecoration d : ms.getDecorations()) {
				double wx = ms.centerX + d.x() * sc / 2.0;
				double wz = ms.centerZ + d.z() * sc / 2.0;
				String nm = d.name().map(t -> ScoreboardReader.stripFormatting(t.getString())).orElse("");
				String type;
				try {
					type = d.getAssetId().getPath();
				} catch (Throwable t) {
					type = "?";
				}
				out.add(new Deco(nm, wx, wz, type));
				diag.append(" | ").append(type).append(nm.isEmpty() ? "" : "='" + nm + "'")
						.append("@").append(Math.round(wx)).append("/").append(Math.round(wz));
			}
		} catch (Throwable ignored) {
		}
		decos = out;

		long now = System.currentTimeMillis();
		if (now - lastDiagMs > 15_000) {
			lastDiagMs = now;
			System.out.println("[Midgard] CH-Map: center=" + ms.centerX + "/" + ms.centerZ + " scale=" + ms.scale
					+ " Marker(" + out.size() + "):" + (diag.length() == 0 ? " (keine)" : diag));
		}
	}

	/** Sucht eine gefüllte Karte in Händen/Inventar und liefert ihren MapState. */
	private static MapState findMap(MinecraftClient mc) {
		ItemStack main = mc.player.getMainHandStack();
		MapState ms = mapOf(main, mc);
		if (ms != null) {
			return ms;
		}
		ms = mapOf(mc.player.getOffHandStack(), mc);
		if (ms != null) {
			return ms;
		}
		for (int i = 0; i < mc.player.getInventory().size(); i++) {
			ms = mapOf(mc.player.getInventory().getStack(i), mc);
			if (ms != null) {
				return ms;
			}
		}
		return null;
	}

	private static MapState mapOf(ItemStack st, MinecraftClient mc) {
		if (st == null || !st.isOf(Items.FILLED_MAP)) {
			return null;
		}
		try {
			return FilledMapItem.getMapState(st, mc.world);
		} catch (Throwable t) {
			return null;
		}
	}
}
