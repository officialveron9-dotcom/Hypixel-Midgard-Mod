package com.midgard.events.skyblock;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.client.MinecraftClient;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;

/**
 * Liest das Sidebar-Scoreboard aus (Titel + Zeilen). Hypixel rendert dort
 * Datum, Uhrzeit, Aufenthaltsort usw. Wir lesen es genauso aus, wie es
 * Minecraft selbst rendert (Team-Prefix/Suffix der Score-Holder).
 */
public final class ScoreboardReader {

	private ScoreboardReader() {
	}

	/** Titel des Sidebar-Objectives (z. B. "SKYBLOCK"), oder null. */
	public static String title(MinecraftClient mc) {
		ScoreboardObjective obj = sidebarObjective(mc);
		if (obj == null) {
			return null;
		}
		return stripFormatting(obj.getDisplayName().getString());
	}

	/** Alle Sidebar-Zeilen von oben nach unten, ohne Farb-/Formatcodes. */
	public static List<String> sidebarLines(MinecraftClient mc) {
		List<String> out = new ArrayList<>();
		ScoreboardObjective obj = sidebarObjective(mc);
		if (obj == null || mc.world == null) {
			return out;
		}
		Scoreboard scoreboard = mc.world.getScoreboard();

		List<ScoreboardEntry> entries = scoreboard.getScoreboardEntries(obj).stream()
				.filter(e -> e != null && !e.hidden())
				// Sidebar zeigt den höchsten Score oben an.
				.sorted(Comparator.comparingInt(ScoreboardEntry::value).reversed())
				.toList();

		for (ScoreboardEntry entry : entries) {
			Team team = scoreboard.getScoreHolderTeam(entry.owner());
			Text decorated = Team.decorateName(team, entry.name());
			out.add(stripFormatting(decorated.getString()));
		}
		return out;
	}

	/**
	 * Aktueller Gebietsname aus der ⏣-Zeile der Sidebar (ohne Symbol), oder null.
	 * In Crystal Hollows wechselt diese Zeile live mit dem Biom (Jungle, Mines of
	 * Divan, Magma Fields …) – das ist die zuverlässige Quelle (wie SkyHanni).
	 */
	public static String currentArea(MinecraftClient mc) {
		for (String line : sidebarLines(mc)) {
			if (line != null && line.indexOf('⏣') >= 0) {
				return line.replace("⏣", "").trim();
			}
		}
		return null;
	}

	private static ScoreboardObjective sidebarObjective(MinecraftClient mc) {
		if (mc.world == null) {
			return null;
		}
		Scoreboard scoreboard = mc.world.getScoreboard();
		return scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
	}

	/** Entfernt §-Formatcodes, falls welche im Text stehen. */
	public static String stripFormatting(String input) {
		if (input == null) {
			return "";
		}
		return input.replaceAll("§.", "").trim();
	}
}
