package com.midgard;

import com.midgard.events.config.ModConfig;
import com.midgard.events.event.EventManager;
import com.midgard.events.event.JacobCalendarReader;
import com.midgard.events.event.LiveEventTracker;
import com.midgard.events.gui.ConfigScreen;
import com.midgard.events.hud.EventHud;
import com.midgard.events.skyblock.SkyblockHook;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Einstiegspunkt der Midgard-Mod (Client-seitig).
 *
 * <p>Midgard ist als Dach für mehrere Features gedacht. Aktuell enthält es das
 * SkyBlock-Event-HUD; weitere Module lassen sich hier einfach anhängen.</p>
 */
public class Midgard implements ClientModInitializer {

	public static final String MOD_ID = "midgard";

	public static ModConfig config;

	private static KeyBinding openConfigKey;
	private static KeyBinding toggleHudKey;
	private static KeyBinding debugDumpKey;

	private int tickCounter = 0;

	private static final java.util.Set<String> loggedErrors = new java.util.HashSet<>();

	/** Loggt einen Render-Fehler pro Bereich nur einmal (kein Log-Spam). */
	private static void logOnce(String area, Throwable t) {
		if (loggedErrors.add(area)) {
			System.err.println("[Midgard] Render-Fehler in " + area + " (abgefangen): " + t);
		}
	}

	@Override
	public void onInitializeClient() {
		System.out.println("[Midgard] init build=2026-06-15ab (Auktions-Suche: Minecraft-Stil, Sterne rechts + echtes Stern-Symbol, Hover-Stats, Vanilla-Schrift)");
		config = ModConfig.load();

		// Optionales globales Roboto-Font-Pack registrieren (Schalter im Menü).
		com.midgard.render.MidgardFont.register();

		// Bazaar-/AH-Preise im Item-Tooltip (ausschließlich vom eigenen Backend).
		com.midgard.price.PriceTooltips.register();

		// Eigene Auktions-Suche: das Hypixel-Such-Schild durch ein eigenes Menü
		// ersetzen (Live-Vorschläge, Verlauf, Sterne). Item-Liste vorwärmen.
		com.midgard.auction.AuctionSearchHook.register();
		com.midgard.auction.ItemIndex.INSTANCE.ensureLoaded();

		// Tastenkürzel. Die Keybind-Kategorie ist seit 1.21.6 ein Objekt.
		KeyBinding.Category category =
				KeyBinding.Category.create(Identifier.of(MOD_ID, "main"));

		openConfigKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.midgard.openconfig",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_RIGHT_SHIFT,
				category));

		toggleHudKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.midgard.togglehud",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_UNKNOWN, // standardmäßig nicht belegt
				category));

		// Debug: nahe Entities (Name/ID/Typ/Position) in den Chat dumpen.
		debugDumpKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.midgard.debugdump",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_M,
				category));

		// Abgebaute Blöcke an den Farming-Tracker melden (Blöcke/s + Crop-Erkennung).
		net.fabricmc.fabric.api.event.client.player.ClientPlayerBlockBreakEvents.AFTER.register(
				(world, player, pos, state) -> com.midgard.garden.FarmingTracker.INSTANCE.onBlockBroken(state));

		// Pfad als durchsichtige 3D-Würfel in der Welt (eigener Puffer -> kann
		// die Engine nicht crashen). Nach den Entities gezeichnet.
		net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents.AFTER_ENTITIES.register(ctx -> {
			try {
				if (config != null && config.miningPathLine) {
					com.midgard.util.PathRenderer.render(ctx);
				}
			} catch (Throwable t) {
				logOnce("Pfad3D", t);
			}
			// Wegpunkt-Labels als 3D-Billboard-Text (wie Skyblocker).
			try {
				com.midgard.util.WorldTextRenderer.render(ctx, com.midgard.mining.MiningWaypoints.markers());
			} catch (Throwable t) {
				logOnce("Text3D", t);
			}
			// Debug: Entity-Namen/IDs über den Köpfen (nur wenn im Menü aktiviert).
			try {
				com.midgard.util.DebugTools.renderLabels(ctx);
			} catch (Throwable t) {
				logOnce("DebugLabels", t);
			}
		});

		// HUD-Elemente bei OFFENEM CHAT anklickbar: dann gibt es einen Mauszeiger
		// und das HUD wird hinter dem Chat gezeichnet. Klick auf das Navi-Element
		// öffnet die Ziel-Auswahl (sonst macht der Klick im Chat-Bereich nichts).
		net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (!(screen instanceof net.minecraft.client.gui.screen.ChatScreen)) {
				return;
			}
			net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents.allowMouseClick(screen)
					.register((scr, click) -> {
						if (click.button() != 0 || config == null) {
							return true;
						}
						try {
							// Klick auf die Navi-Liste im HUD -> Ziel setzen (nicht an den Chat).
							if (com.midgard.events.hud.NaviHud.INSTANCE.clickAt(click.x(), click.y())) {
								return false;
							}
						} catch (Throwable t) {
							logOnce("ChatKlick", t);
						}
						return true;
					});
		});

		// Chat-Nachrichten an den Live-Event-Tracker weitergeben (nur lesen, nie senden).
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!overlay) {
				LiveEventTracker.INSTANCE.onChat(message.getString());
				com.midgard.garden.GardenData.INSTANCE.onChat(message.getString());
				com.midgard.mining.MiningData.INSTANCE.onChat(message.getString());
			} else {
				// Actionbar: Hypixel zeigt dort Leben/Mana (für die eigenen Leisten).
				com.midgard.bars.StatusBars.onActionBar(message.getString());
			}
		});

		// Actionbar kürzen: Leben/Mana zeigen schon unsere eigenen Leisten -
		// damit nichts doppelt steht, aus dem Overlay-Text entfernen (Defense,
		// Drill Fuel u. a. bleiben). MODIFY_GAME läuft VOR GAME, daher hier auch
		// gleich die Werte für die Leisten auslesen. Nur bei aktiven Leisten.
		ClientReceiveMessageEvents.MODIFY_GAME.register((message, overlay) -> {
			if (!overlay || !com.midgard.bars.StatusBars.enabled()) {
				return message;
			}
			// Werte auslesen, dann die Hypixel-Actionbar komplett leeren –
			// Leben/Mana/Defense/Drill Fuel zeigen unsere eigenen Leisten + Zeile.
			com.midgard.bars.StatusBars.onActionBar(message.getString());
			return net.minecraft.text.Text.empty();
		});

		// Pro Client-Tick: Tasten prüfen + periodisch Daten aktualisieren.
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openConfigKey.wasPressed()) {
				client.setScreen(new ConfigScreen(client.currentScreen));
			}
			while (toggleHudKey.wasPressed()) {
				config.masterEnabled = !config.masterEnabled;
				config.save();
			}
			while (debugDumpKey.wasPressed()) {
				com.midgard.util.DebugTools.dumpToChat(client);
			}

			// Jeden Tick: offenes Kalender-/Jacob-GUI auslesen (nur lesend).
			JacobCalendarReader.INSTANCE.tick(client);

			// Borderless-Vollbild mit der Config abgleichen (greift sofort).
			com.midgard.util.Borderless.tick(client);

			// Etwa viermal pro Sekunde – Commissions/Events erscheinen schneller.
			if (++tickCounter >= 5) {
				tickCounter = 0;
				SkyblockHook.INSTANCE.update(client);
				EventManager.INSTANCE.update();
				com.midgard.garden.GardenData.INSTANCE.update(client);
				com.midgard.mining.MiningData.INSTANCE.update(client);
				com.midgard.mining.MiningWaypoints.tick(client);
				com.midgard.mining.CrystalNav.tick(client);
				com.midgard.mining.CrystalData.INSTANCE.update(client);
				com.midgard.mining.CrystalMap.tick(client);
				// Wegfinder: ist in Crystal Hollows ein Ziel GEWÄHLT, zählt NUR dieses
				// (auch wenn es noch gesucht wird -> dann KEIN Pfad, statt zur Mitte).
				// Sonst der nächste automatische Wegpunkt.
				if (config.miningPathLine) {
					if (com.midgard.mining.CrystalNav.hasTarget()) {
						double[] nav = com.midgard.mining.CrystalNav.target();
						if (nav != null) {
							com.midgard.util.PathFinder.update(nav[0], nav[1], nav[2]);
						} else {
							com.midgard.util.PathFinder.clear(); // gewählt, aber noch in Suche
						}
					} else {
						com.midgard.util.Waypoints.Marker tgt = com.midgard.mining.MiningWaypoints.nearest();
						if (tgt != null) {
							com.midgard.util.PathFinder.update(tgt.x(), tgt.y(), tgt.z());
						} else {
							com.midgard.util.PathFinder.clear();
						}
					}
				} else {
					com.midgard.util.PathFinder.clear();
				}
				com.midgard.events.event.JacobWarner.INSTANCE.tick(client);
				com.midgard.price.PriceApi.INSTANCE.maybeRefresh();
			}
		});

		// HUD zeichnen. Jeder Teil eigen abgesichert – ein Fehler darf NIE das
		// Spiel zum Absturz bringen (Crash-Schutz in den Höhlen).
		HudRenderCallback.EVENT.register((context, tickCounter) -> {
			// Im HUD-Editor zeichnet dieser bereits die Vorschau – das Live-HUD
			// dahinter würde sich doppeln (Vorschau "Pickobulus 47s" über echtem
			// "Bereit"). Darum hier auslassen, solange der Editor offen ist.
			boolean inHudEditor = net.minecraft.client.MinecraftClient.getInstance()
					.currentScreen instanceof com.midgard.events.gui.HudPositionScreen;
			if (!inHudEditor) {
				try {
					EventHud.INSTANCE.render(context);
				} catch (Throwable t) {
					logOnce("HUD", t);
				}
				try {
					com.midgard.events.hud.NaviHud.INSTANCE.render(context);
				} catch (Throwable t) {
					logOnce("NaviHud", t);
				}
				try {
					com.midgard.events.hud.MinimapHud.INSTANCE.render(context);
				} catch (Throwable t) {
					logOnce("Minimap", t);
				}
			}
			try {
				com.midgard.bars.StatusBars.render(context);
			} catch (Throwable t) {
				logOnce("Bars", t);
			}
			// (Wegpunkt-Marker werden jetzt in 3D gezeichnet, siehe WorldMarkers.)
		});
	}
}
