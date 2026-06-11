package com.midgard.render;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.util.Identifier;

/**
 * Optionale, global wirkende Schriftart. Für jede Schrift gibt es ein
 * gebündeltes Resource-Pack (<code>resourcepacks/font_&lt;key&gt;</code>), das
 * die Standard-Schrift {@code minecraft:default} mit der jeweiligen TTF
 * überschreibt (mit Vanilla-Fallback für Sonderzeichen). Es ist immer höchstens
 * EIN Font-Pack aktiv; der gewählte Zustand bleibt über Minecrafts
 * {@code options.txt} erhalten.
 */
public final class MidgardFont {

	public record FontOption(String key, String display, String ttf) {
	}

	/** Auswahl-Reihenfolge (key "" = aus → normale Minecraft-Schrift). ttf = Dateiname für die Vorschau. */
	public static final List<FontOption> FONTS = List.of(
			new FontOption("", "Aus (Minecraft)", ""),
			new FontOption("roboto", "Roboto", "roboto_regular"),
			new FontOption("roboto_bold", "Roboto Fett", "roboto_bold"),
			new FontOption("roboto_light", "Roboto Dünn", "roboto_light"),
			new FontOption("roboto_black", "Roboto Schwarz", "roboto_black"),
			new FontOption("opensans", "Open Sans", "opensans"),
			new FontOption("montserrat", "Montserrat", "montserrat"),
			new FontOption("poppins", "Poppins", "poppins"));

	/** TTF-Dateiname für einen Font-Key (für die Vorschau), oder "". */
	public static String ttf(String key) {
		for (FontOption f : FONTS) {
			if (f.key.equals(key)) {
				return f.ttf;
			}
		}
		return "";
	}

	private MidgardFont() {
	}

	public static String display(String key) {
		for (FontOption f : FONTS) {
			if (f.key.equals(key)) {
				return f.display;
			}
		}
		return FONTS.get(0).display;
	}

	/** Nächste Schrift in der Auswahl (für den Umschalt-Button). */
	public static String next(String key) {
		int idx = 0;
		for (int i = 0; i < FONTS.size(); i++) {
			if (FONTS.get(i).key.equals(key)) {
				idx = i;
				break;
			}
		}
		return FONTS.get((idx + 1) % FONTS.size()).key;
	}

	/** Im Mod-Init: alle Font-Packs bei Minecraft registrieren. */
	public static void register() {
		try {
			FabricLoader.getInstance().getModContainer("midgard").ifPresent(mod -> {
				for (FontOption f : FONTS) {
					if (f.key.isEmpty()) {
						continue;
					}
					ResourceManagerHelper.registerBuiltinResourcePack(
							Identifier.of("midgard", "font_" + f.key), mod, ResourcePackActivationType.NORMAL);
				}
			});
		} catch (Throwable t) {
			System.err.println("[Midgard] Font-Packs registrieren fehlgeschlagen: " + t);
		}
	}

	/** Aktiviert genau die gewählte Schrift (alle anderen aus) und lädt neu. */
	public static void apply(String key) {
		try {
			MinecraftClient client = MinecraftClient.getInstance();
			ResourcePackManager rpm = client.getResourcePackManager();
			rpm.scanPacks();

			// Alle unsere Font-Pack-Ids bestimmen.
			Set<String> ourIds = new HashSet<>();
			for (FontOption f : FONTS) {
				if (f.key.isEmpty()) {
					continue;
				}
				String id = findId(rpm, f.key);
				if (id != null) {
					ourIds.add(id);
				}
			}

			Set<String> enabled = new LinkedHashSet<>(rpm.getEnabledIds());
			enabled.removeAll(ourIds); // erst alle unsere raus
			if (key != null && !key.isEmpty()) {
				String sel = findId(rpm, key);
				if (sel != null) {
					enabled.add(sel);
				}
			}
			rpm.setEnabledProfiles(enabled);

			client.options.resourcePacks.clear();
			for (ResourcePackProfile p : rpm.getEnabledProfiles()) {
				if (!p.isPinned()) {
					client.options.resourcePacks.add(p.getId());
				}
			}
			client.options.write();
			client.reloadResources();
		} catch (Throwable t) {
			System.err.println("[Midgard] Font umschalten fehlgeschlagen: " + t);
		}
	}

	private static String findId(ResourcePackManager rpm, String key) {
		String needle = "font_" + key;
		for (String s : rpm.getIds()) {
			if (s.endsWith(needle)) { // endsWith trennt font_roboto von font_roboto_bold/_light/_black
				return s;
			}
		}
		return null;
	}
}
