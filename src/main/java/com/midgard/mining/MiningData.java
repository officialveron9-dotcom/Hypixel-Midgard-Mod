package com.midgard.mining;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.midgard.events.skyblock.ScoreboardReader;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;

/**
 * Liest den Mining-Status (Dwarven Mines / Crystal Hollows / Glacite) rein
 * lesend aus: Commissions aus der Tab-Liste, Pickaxe-Ability-Cooldown aus dem
 * Chat. Best-Effort-Parser mit Diagnose-Logging (alle 15 s), wie beim
 * Garden-Modul — Formate bei Bedarf am echten Spiel nachschärfen.
 */
public class MiningData {

	public static final MiningData INSTANCE = new MiningData();

	/** Eine Commission: Name + Fortschritt ("32,5%" oder "DONE"). */
	public record Commission(String name, String progress, boolean done) {
	}

	private static final Pattern COMMISSION_LINE = Pattern.compile("(.+?):\\s*([\\d.,]+%|DONE)\\s*$");
	private static final Pattern ABILITY_USED = Pattern.compile("You used your (.+?) Pickaxe Ability");
	private static final Pattern ABILITY_READY = Pattern.compile("(.+?) is now available");

	/** Cooldown-Sekunden je Ability (Standard 120, wenn unbekannt). */
	private static final Map<String, Integer> COOLDOWNS = Map.of(
			"Mining Speed Boost", 120,
			"Pickobulus", 110,
			"Maniac Miner", 59,
			"Gemstone Infusion", 140,
			"Hazardous Miner", 140,
			"Vein Seeker", 60);

	public volatile boolean onMiningIsland = false;
	public volatile List<Commission> commissions = List.of();
	/** Zuletzt benutzte Ability + Ablaufzeitpunkt (ms), 0 = nichts bekannt. */
	public volatile String abilityName = "";
	public volatile long abilityReadyMs = 0;

	private long lastDiagMs = 0;

	public void update(MinecraftClient mc) {
		if (mc == null || mc.player == null || mc.getNetworkHandler() == null) {
			onMiningIsland = false;
			return;
		}
		List<String> sidebar = ScoreboardReader.sidebarLines(mc);
		boolean mining = false;
		for (String line : sidebar) {
			String l = line == null ? "" : line.toLowerCase(Locale.ROOT);
			if (l.contains("dwarven") || l.contains("crystal hollows") || l.contains("glacite")
					|| l.contains("mines of divan")) {
				mining = true;
				break;
			}
		}
		onMiningIsland = mining;
		if (!mining) {
			return;
		}

		// Tab-Liste (wie Vanilla sortiert) nach dem Commissions-Abschnitt durchsuchen.
		List<PlayerListEntry> entries = new ArrayList<>(mc.getNetworkHandler().getListedPlayerListEntries());
		entries.sort(Comparator
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

		List<Commission> coms = new ArrayList<>();
		boolean inSection = false;
		for (String raw : tab) {
			String line = raw == null ? "" : raw.trim();
			String lower = line.toLowerCase(Locale.ROOT);
			if (lower.startsWith("commissions")) {
				inSection = true;
				continue;
			}
			if (inSection) {
				Matcher m = COMMISSION_LINE.matcher(line);
				if (line.isEmpty() || !m.find()) {
					inSection = false;
					continue;
				}
				String prog = m.group(2);
				coms.add(new Commission(m.group(1).trim(), prog, prog.equalsIgnoreCase("DONE")));
			}
		}
		commissions = coms;

		long now = System.currentTimeMillis();
		if (now - lastDiagMs > 15_000) {
			lastDiagMs = now;
			StringBuilder sb = new StringBuilder();
			for (String t : tab) {
				String lt = t.toLowerCase(Locale.ROOT);
				if (lt.contains("commission") || lt.contains("event") || lt.contains("powder")) {
					sb.append(" | ").append(t);
				}
			}
			System.out.println("[Midgard] Mining-Tab:" + sb + " || commissions=" + coms.size());
		}
	}

	/** Chat: Ability benutzt / wieder verfügbar (nur lesend). */
	public void onChat(String message) {
		if (message == null) {
			return;
		}
		Matcher used = ABILITY_USED.matcher(message);
		if (used.find()) {
			String name = used.group(1).trim();
			abilityName = name;
			abilityReadyMs = System.currentTimeMillis()
					+ COOLDOWNS.getOrDefault(name, 120) * 1000L;
			return;
		}
		Matcher ready = ABILITY_READY.matcher(message);
		if (ready.find() && !abilityName.isEmpty()
				&& ready.group(1).toLowerCase(Locale.ROOT).contains(abilityName.toLowerCase(Locale.ROOT))) {
			abilityReadyMs = 0;
		}
	}

	/** Restsekunden des Ability-Cooldowns oder -1 (bereit/unbekannt). */
	public long abilityCooldownRemaining() {
		if (abilityReadyMs <= 0) {
			return -1;
		}
		long remain = (abilityReadyMs - System.currentTimeMillis()) / 1000;
		return remain > 0 ? remain : -1;
	}
}
