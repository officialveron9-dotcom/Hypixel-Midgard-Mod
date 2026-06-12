package com.midgard.garden;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

/**
 * Beobachtet das Farm-Werkzeug in der Hand (rein lesend): der Zähler im
 * Item-NBT ({@code counter} bzw. {@code farmed_cultivating}) steigt pro
 * geerntetem Crop. Daraus werden Crops/min (60-s-Fenster) und Blöcke/s
 * (5-s-Fenster) berechnet, dazu Cultivating-Level + Fortschritt. Öffnet der
 * Spieler ein Collection-Menü, wird der Fortschritt zum nächsten Rang
 * mitgelesen und live hochgerechnet (ETA über Crops/min).
 */
public class FarmingTracker {

	public static final FarmingTracker INSTANCE = new FarmingTracker();

	/** Cultivating-Schwellen (geerntete Crops je Stufe I..X). */
	private static final long[] CULTIVATING = {
			1_000, 5_000, 25_000, 100_000, 300_000,
			1_500_000, 5_000_000, 20_000_000, 100_000_000, 1_000_000_000 };

	private static final String[] CROPS = {
			"Wheat", "Carrot", "Potato", "Pumpkin", "Melon",
			"Sugar Cane", "Cactus", "Cocoa Beans", "Mushroom", "Nether Wart" };

	private static final Pattern PROGRESS = Pattern.compile("([\\d.,]+)\\s*/\\s*([\\d.,]+)");

	private record Sample(long timeMs, long counter) {
	}

	/** Collection-Stand aus dem Menü: Betrag, Ziel, Werkzeug-Zähler beim Lesen. */
	public record Collection(String crop, long amount, long target, long counterBase) {
	}

	private final ArrayDeque<Sample> samples = new ArrayDeque<>();
	private final ArrayDeque<Long> blockBreaks = new ArrayDeque<>();
	private final Map<String, Collection> collections = new HashMap<>();

	public volatile String toolCrop = "";
	/** Crop, erkannt am zuletzt abgebauten Block (unabhängig vom Werkzeug). */
	public volatile String blockCrop = "";
	public volatile long counter = -1;
	public volatile long cultivating = -1;
	private int lastScreenHash = 0;

	public void tick(MinecraftClient mc) {
		if (mc.player == null) {
			return;
		}
		readTool(mc.player.getMainHandStack());
		readCollectionMenu(mc);
	}

	private void readTool(ItemStack stack) {
		NbtComponent c = stack == null ? null : stack.get(DataComponentTypes.CUSTOM_DATA);
		if (c == null) {
			return;
		}
		NbtCompound n = c.copyNbt();
		long cnt = n.getLong("mined_crops", -1);
		if (cnt < 0) {
			cnt = n.getLong("counter", -1);
		}
		long cult = n.getLong("farmed_cultivating", -1);
		if (cnt < 0 && cult >= 0) {
			cnt = cult; // Dicer & Co. haben nur den Cultivating-Zähler
		}
		cultivating = cult;
		if (cnt < 0) {
			return;
		}

		String id = n.getString("id", "");
		String crop = cropFrom(id);
		long now = System.currentTimeMillis();

		// Werkzeugwechsel (oder anderes Crop): Verlauf neu beginnen.
		if (!samples.isEmpty() && (cnt < samples.peekLast().counter || !crop.equals(toolCrop))) {
			samples.clear();
		}
		toolCrop = crop;
		counter = cnt;
		samples.addLast(new Sample(now, cnt));
		while (!samples.isEmpty() && now - samples.peekFirst().timeMs > 90_000) {
			samples.removeFirst();
		}
	}

	private static String cropFrom(String id) {
		String u = id.toUpperCase(Locale.ROOT);
		for (String crop : CROPS) {
			if (u.contains(crop.toUpperCase(Locale.ROOT).replace(' ', '_'))) {
				return crop;
			}
		}
		if (u.contains("COCO")) {
			return "Cocoa Beans";
		}
		if (u.contains("CANE")) {
			return "Sugar Cane";
		}
		if (u.contains("WART")) {
			return "Nether Wart";
		}
		if (u.contains("FUNGI")) {
			return "Mushroom";
		}
		return "";
	}

	/** Effektives Crop: Werkzeug-ID, sonst der zuletzt abgebaute Block. */
	public String crop() {
		return toolCrop.isEmpty() ? blockCrop : toolCrop;
	}

	/**
	 * Vom Block-Break-Event (Fabric) gemeldet: zählt echte abgebaute Blöcke
	 * (für Blöcke/s) und erkennt das Crop am Block selbst – funktioniert damit
	 * auch mit den einfachen SkyMart-Werkzeugen ohne Crop in der Item-ID.
	 */
	public void onBlockBroken(net.minecraft.block.BlockState state) {
		String crop = cropFromBlock(state);
		if (crop.isEmpty()) {
			return;
		}
		blockCrop = crop;
		long now = System.currentTimeMillis();
		blockBreaks.addLast(now);
		while (!blockBreaks.isEmpty() && now - blockBreaks.peekFirst() > 30_000) {
			blockBreaks.removeFirst();
		}
	}

	private static String cropFromBlock(net.minecraft.block.BlockState state) {
		if (state == null) {
			return "";
		}
		net.minecraft.block.Block b = state.getBlock();
		if (b == net.minecraft.block.Blocks.WHEAT) {
			return "Wheat";
		}
		if (b == net.minecraft.block.Blocks.CARROTS) {
			return "Carrot";
		}
		if (b == net.minecraft.block.Blocks.POTATOES) {
			return "Potato";
		}
		if (b == net.minecraft.block.Blocks.NETHER_WART) {
			return "Nether Wart";
		}
		if (b == net.minecraft.block.Blocks.PUMPKIN) {
			return "Pumpkin";
		}
		if (b == net.minecraft.block.Blocks.MELON) {
			return "Melon";
		}
		if (b == net.minecraft.block.Blocks.SUGAR_CANE) {
			return "Sugar Cane";
		}
		if (b == net.minecraft.block.Blocks.CACTUS) {
			return "Cactus";
		}
		if (b == net.minecraft.block.Blocks.COCOA) {
			return "Cocoa Beans";
		}
		if (b == net.minecraft.block.Blocks.RED_MUSHROOM || b == net.minecraft.block.Blocks.BROWN_MUSHROOM
				|| b == net.minecraft.block.Blocks.RED_MUSHROOM_BLOCK
				|| b == net.minecraft.block.Blocks.BROWN_MUSHROOM_BLOCK
				|| b == net.minecraft.block.Blocks.MUSHROOM_STEM) {
			return "Mushroom";
		}
		return "";
	}

	/** Zuwachs im Zeitfenster {@code windowMs}, hochgerechnet pro {@code perMs}. */
	private double rate(long windowMs, long perMs) {
		if (samples.size() < 2) {
			return 0;
		}
		long now = samples.peekLast().timeMs;
		Sample oldest = null;
		for (Sample s : samples) {
			if (now - s.timeMs <= windowMs) {
				oldest = s;
				break;
			}
		}
		if (oldest == null || oldest == samples.peekLast()) {
			return 0;
		}
		long dt = samples.peekLast().timeMs - oldest.timeMs;
		long dc = samples.peekLast().counter - oldest.counter;
		return dt <= 0 ? 0 : dc * (double) perMs / dt;
	}

	public double cropsPerMinute() {
		return rate(60_000, 60_000);
	}

	/** Echte abgebaute Crop-Blöcke pro Sekunde (5-s-Fenster, vom Break-Event). */
	public double blocksPerSecond() {
		long now = System.currentTimeMillis();
		int n = 0;
		for (long t : blockBreaks) {
			if (now - t <= 5_000) {
				n++;
			}
		}
		return n / 5.0;
	}

	/** Wird gerade aktiv gefarmt (Zähler gestiegen oder Block abgebaut, 4 s)? */
	public boolean farming() {
		long now = System.currentTimeMillis();
		if (!blockBreaks.isEmpty() && now - blockBreaks.peekLast() <= 4_000) {
			return true;
		}
		if (samples.size() < 2) {
			return false;
		}
		long last = samples.peekLast().counter;
		for (Sample s : samples) {
			if (now - s.timeMs <= 4_000) {
				return last > s.counter;
			}
		}
		return false;
	}

	/** Cultivating-Stufe (1..10) zum aktuellen Zähler, 0 = keine Daten. */
	public int cultivatingLevel() {
		if (cultivating < 0) {
			return 0;
		}
		int lvl = 0;
		for (long t : CULTIVATING) {
			if (cultivating >= t) {
				lvl++;
			}
		}
		return lvl;
	}

	/** Schwelle der nächsten Cultivating-Stufe oder -1 (Maximum erreicht). */
	public long cultivatingNext() {
		int lvl = cultivatingLevel();
		return lvl >= CULTIVATING.length ? -1 : CULTIVATING[lvl];
	}

	/** Collection-/Milestone-Stand des aktuellen Crops (live hochgerechnet) oder null. */
	public Collection collection() {
		String crop = crop();
		return crop.isEmpty() ? null : collections.get(crop);
	}

	/** Geschätzter aktueller Collection-Betrag (Menü-Stand + seither gefarmt). */
	public long collectionEstimate(Collection col) {
		long since = counter >= 0 && col.counterBase() >= 0 ? counter - col.counterBase() : 0;
		return col.amount() + Math.max(0, since);
	}

	/** Sekunden bis zum nächsten Collection-Rang (über Crops/min) oder -1. */
	public long collectionEtaSeconds(Collection col) {
		double perMin = cropsPerMinute();
		if (perMin <= 0) {
			return -1;
		}
		long remaining = col.target() - collectionEstimate(col);
		return remaining <= 0 ? 0 : (long) (remaining / perMin * 60);
	}

	// ---- Collection-Menü mitlesen ------------------------------------------

	private void readCollectionMenu(MinecraftClient mc) {
		Screen s = mc.currentScreen;
		if (!(s instanceof HandledScreen<?> hs)) {
			lastScreenHash = 0;
			return;
		}
		String title = s.getTitle() == null ? "" : s.getTitle().getString();
		String tl = title.toLowerCase(Locale.ROOT);
		// Im Garden heißt das Menü "Crop Milestones" (Desk), sonst "… Collection".
		if (!tl.contains("collection") && !tl.contains("milestone")) {
			return;
		}
		if (s.hashCode() == lastScreenHash) {
			return; // pro Öffnen einmal reicht (Inhalt lädt async, daher kein harter Skip)
		}

		int parsed = 0;
		for (Slot slot : hs.getScreenHandler().slots) {
			ItemStack st = slot.getStack();
			if (st == null || st.isEmpty()) {
				continue;
			}
			String name = st.getName() == null ? "" : st.getName().getString();
			String crop = matchCrop(name);
			if (crop.isEmpty()) {
				continue;
			}
			LoreComponent lore = st.get(DataComponentTypes.LORE);
			if (lore == null) {
				continue;
			}
			for (Text line : lore.lines()) {
				Matcher m = PROGRESS.matcher(line.getString());
				if (m.find()) {
					long amount = parseNum(m.group(1));
					long target = parseNum(m.group(2));
					if (target > 0 && amount <= target) {
						collections.put(crop, new Collection(crop, amount, target, counter));
						parsed++;
					}
					break;
				}
			}
		}
		if (parsed > 0) {
			lastScreenHash = s.hashCode();
			System.out.println("[Midgard] Collection-Menü gelesen: " + parsed + " Crops");
		}
	}

	private static String matchCrop(String name) {
		for (String crop : CROPS) {
			if (name.startsWith(crop)) {
				return crop;
			}
		}
		return "";
	}

	private static long parseNum(String s) {
		try {
			return Long.parseLong(s.replace(",", "").replace(".", ""));
		} catch (NumberFormatException e) {
			return -1;
		}
	}
}
