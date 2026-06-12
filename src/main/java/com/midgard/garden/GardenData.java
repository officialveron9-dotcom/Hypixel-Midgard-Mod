package com.midgard.garden;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.midgard.events.skyblock.ScoreboardReader;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;

/**
 * Liest den Garden-Status (Hypixel SkyBlock) rein lesend aus Tab-Liste und
 * Scoreboard: Besucherliste, Schädlinge (Anzahl + befallene Plots) und den
 * Plot, auf dem der Spieler gerade steht. Best-Effort-Parser mit
 * Diagnose-Logging (alle 15 s), damit das Format bei Bedarf am echten Spiel
 * nachgeschärft werden kann — gleiche Vorgehensweise wie beim
 * Dwarven-Scoreboard.
 */
public class GardenData {

	public static final GardenData INSTANCE = new GardenData();

	/** Maximale Schädlinge im Garten (Anzeige x/8). */
	public static final int MAX_PESTS = 8;

	private static final Pattern PEST_COUNT = Pattern.compile("(?:Pests?|Schädlinge)[:\\s]+(\\d+)");
	private static final Pattern PLOT_LINE = Pattern.compile("Plot\\s*[-–]\\s*(.+)");
	private static final Pattern PLOT_NUMBERS = Pattern.compile("(\\d+)");
	private static final Pattern NEXT_VISITOR = Pattern.compile("Next Visitor:\\s*(.+)");
	private static final Pattern ORGANIC = Pattern.compile("Organic Matter:\\s*(.+)");
	private static final Pattern FUEL = Pattern.compile("Fuel:\\s*(.+)");
	private static final Pattern TIME_LEFT = Pattern.compile("Time Left:\\s*(.+)");
	private static final Pattern COLLECTED = Pattern.compile("Collected:?\\s*([\\d.,]+)");
	private static final Pattern TOP_PCT = Pattern.compile("(?i)Top\\s*(\\d+)%");

	public volatile boolean onGarden = false;
	public volatile String currentPlot = "";
	public volatile int pestCount = 0;
	public volatile List<String> infestedPlots = List.of();
	public volatile List<String> visitors = List.of();
	public volatile String nextVisitor = "";
	public volatile String composterMatter = "";
	public volatile String composterFuel = "";
	public volatile String composterTime = "";
	/** Jacob-Contest live (aus dem Scoreboard): gesammelt + Top-Prozent, -1 = kein Contest. */
	public volatile long jacobCollected = -1;
	public volatile int jacobPercent = -1;

	private long lastDiagMs = 0;

	public void update(MinecraftClient mc) {
		if (mc == null || mc.player == null || mc.getNetworkHandler() == null) {
			onGarden = false;
			return;
		}
		List<String> sidebar = ScoreboardReader.sidebarLines(mc);

		// Garden erkennen: Scoreboard zeigt dort "⏩ Plot - ..." bzw. "The Garden".
		boolean garden = false;
		String plot = "";
		for (String line : sidebar) {
			String l = line == null ? "" : line;
			if (l.contains("Garden")) {
				garden = true;
			}
			Matcher m = PLOT_LINE.matcher(l);
			if (m.find()) {
				garden = true;
				plot = m.group(1).trim();
			}
		}
		onGarden = garden;
		currentPlot = plot;
		if (!garden) {
			return;
		}

		parseTab(mc, sidebar);
		FarmingTracker.INSTANCE.tick(mc);
	}

	/** Tab-Liste: Besucher-Abschnitt + Schädlings-Widget. */
	private void parseTab(MinecraftClient mc, List<String> sidebar) {
		// WICHTIG: wie Vanilla sortieren (listOrder, Team, Name) – die Collection
		// aus dem NetworkHandler ist unsortiert, Abschnitte wären sonst zerwürfelt.
		List<PlayerListEntry> entries = new ArrayList<>(mc.getNetworkHandler().getListedPlayerListEntries());
		entries.sort(java.util.Comparator
				.<PlayerListEntry>comparingInt(e -> -e.getListOrder())
				.thenComparing(e -> e.getScoreboardTeam() == null ? "" : e.getScoreboardTeam().getName())
				.thenComparing(e -> e.getProfile() == null ? "" : e.getProfile().name(),
						String.CASE_INSENSITIVE_ORDER));
		List<String> tab = new ArrayList<>();
		for (PlayerListEntry e : entries) {
			if (e.getDisplayName() != null) {
				tab.add(ScoreboardReader.stripFormatting(e.getDisplayName().getString()));
			}
		}

		List<String> vis = new ArrayList<>();
		Set<String> plots = new LinkedHashSet<>();
		int pests = -1;
		String nextVis = "";
		String matter = "";
		String fuel = "";
		String compTime = "";

		boolean inVisitors = false;
		boolean inComposter = false;
		for (String raw : tab) {
			String line = raw == null ? "" : raw.trim();
			String lower = line.toLowerCase(Locale.ROOT);

			if (lower.startsWith("visitors") || lower.startsWith("besucher")) {
				inVisitors = true;
				inComposter = false;
				continue;
			}
			if (lower.startsWith("composter")) {
				inComposter = true;
				inVisitors = false;
				continue;
			}
			if (inVisitors) {
				// Abschnitt endet bei Leerzeile oder der nächsten Widget-Zeile
				// (Überschriften/Infos enthalten ':', Besuchernamen nicht).
				if (line.isEmpty() || line.contains(":")) {
					inVisitors = false;
				} else if (vis.size() < 8) {
					vis.add(line);
					continue;
				}
			}

			Matcher m = NEXT_VISITOR.matcher(line);
			if (m.find()) {
				nextVis = m.group(1).trim();
				continue;
			}
			m = ORGANIC.matcher(line);
			if (m.find()) {
				matter = m.group(1).trim();
				continue;
			}
			m = FUEL.matcher(line);
			if (m.find()) {
				fuel = m.group(1).trim();
				continue;
			}
			if (inComposter) {
				m = TIME_LEFT.matcher(line);
				if (m.find()) {
					compTime = m.group(1).trim();
					continue;
				}
				if (line.isEmpty()) {
					inComposter = false;
				}
			}

			Matcher pm = PEST_COUNT.matcher(line);
			if (pm.find()) {
				try {
					pests = Integer.parseInt(pm.group(1));
				} catch (NumberFormatException ignored) {
				}
			}
			// Befallene Plots: Zeile à la "Infested Plots: 2, 5" / "ൠ Plots: ..."
			if (lower.contains("infested") || (lower.contains("plot") && lower.contains("ൠ"))) {
				Matcher nm = PLOT_NUMBERS.matcher(line);
				while (nm.find()) {
					plots.add(nm.group(1));
				}
			}
		}

		// Scoreboard: Pest-Fallback + Jacob-Contest live (Collected / Top x%).
		long collected = -1;
		int pct = -1;
		for (String line : sidebar) {
			Matcher pm = PEST_COUNT.matcher(line);
			if (pm.find() && pests < 0) {
				try {
					pests = Integer.parseInt(pm.group(1));
				} catch (NumberFormatException ignored) {
				}
			}
			Matcher cm = COLLECTED.matcher(line);
			if (cm.find()) {
				try {
					collected = Long.parseLong(cm.group(1).replace(",", "").replace(".", ""));
				} catch (NumberFormatException ignored) {
				}
			}
			Matcher tm = TOP_PCT.matcher(line);
			if (tm.find()) {
				try {
					pct = Integer.parseInt(tm.group(1));
				} catch (NumberFormatException ignored) {
				}
			}
		}
		jacobCollected = collected;
		jacobPercent = pct;

		visitors = vis;
		nextVisitor = nextVis;
		composterMatter = matter;
		composterFuel = fuel;
		composterTime = compTime;
		infestedPlots = new ArrayList<>(plots);
		pestCount = Math.max(0, pests);

		// Diagnose: alle 15 s die KOMPLETTE Tab-Liste loggen (Garden ist eine
		// Privatinsel, das bleibt überschaubar) – zum Nachschärfen der Parser.
		long now = System.currentTimeMillis();
		if (now - lastDiagMs > 15_000) {
			lastDiagMs = now;
			StringBuilder sb = new StringBuilder();
			int n = 0;
			for (String t : tab) {
				if (n++ >= 60) {
					sb.append(" | ...");
					break;
				}
				sb.append(" | ").append(t);
			}
			System.out.println("[Midgard] Garden-Tab:" + sb);
			System.out.println("[Midgard] Garden-Parsed: plot=" + currentPlot
					+ " pests=" + pestCount + "/" + MAX_PESTS + " infested=" + infestedPlots
					+ " visitors=" + visitors);
		}
	}

	private static final Pattern PEST_CHAT = Pattern.compile(
			"(?i)pest.{0,30}?(?:appeared|spawned).{0,20}?Plot\\s*[-–]?\\s*(\\w+)");
	/** Abgabe von Schädlingen beim Pesthunter (Phillip) – Best-Effort. */
	private static final Pattern PEST_HANDIN = Pattern.compile(
			"(?i)(?:traded|exchanged|gave|handed|abgegeben).{0,40}?pests?|pesthunter");

	/** Cooldown nach der Schädlings-Abgabe (Phillip). */
	public static final long HANDIN_COOLDOWN_SEC = 60 * 60;
	/** Zeitpunkt (ms) der letzten Schädlings-Abgabe, 0 = noch keine gesehen. */
	public volatile long pestHandinMs = 0;

	/**
	 * Chat-Meldung (nur lesend): Hypixel meldet neue Schädlinge sofort im Chat
	 * ("ൠ A Pest has appeared in Plot - 4!") – schneller als die Tab-Liste.
	 */
	public void onChat(String message) {
		if (message == null) {
			return;
		}
		Matcher m = PEST_CHAT.matcher(message);
		if (m.find()) {
			List<String> plots = new ArrayList<>(infestedPlots);
			if (!plots.contains(m.group(1))) {
				plots.add(m.group(1));
				infestedPlots = plots;
			}
			pestCount = Math.max(pestCount + 1, plots.size());
		}
		// Abgabe beim Pesthunter -> Cooldown-Timer starten. Format ist
		// Best-Effort; Phillip-Zeilen werden zur Diagnose geloggt.
		String lower = message.toLowerCase(Locale.ROOT);
		if (lower.contains("phillip") || lower.contains("pesthunter")) {
			System.out.println("[Midgard] Pesthunter-Chat: " + message);
			if (PEST_HANDIN.matcher(message).find()) {
				pestHandinMs = System.currentTimeMillis();
			}
		}
	}

	/** Restsekunden des Abgabe-Cooldowns oder -1 (keine Abgabe gesehen/abgelaufen). */
	public long handinCooldownRemaining() {
		if (pestHandinMs <= 0) {
			return -1;
		}
		long remain = HANDIN_COOLDOWN_SEC - (System.currentTimeMillis() - pestHandinMs) / 1000;
		return remain > 0 ? remain : -1;
	}

	/** Steht der Spieler gerade auf einem befallenen Plot mit dieser Nummer/Namen? */
	public boolean isCurrentPlot(String plot) {
		if (currentPlot.isEmpty() || plot == null || plot.isEmpty()) {
			return false;
		}
		String cur = currentPlot.toLowerCase(Locale.ROOT);
		String p = plot.toLowerCase(Locale.ROOT);
		if (cur.equals(p)) {
			return true;
		}
		// Plot-NUMMER gegen Nummer im aktuellen Plot-Namen vergleichen.
		Matcher m = PLOT_NUMBERS.matcher(cur);
		while (m.find()) {
			if (m.group(1).equals(p)) {
				return true;
			}
		}
		return false;
	}
}
