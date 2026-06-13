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
		System.out.println("[Midgard] init build=2026-06-13j (Editor: Reset-Knopf, Pin/Globus-Icons, deaktiviert verschwindet, Event-Anzahl-Vorschau)");
		config = ModConfig.load();

		// Optionales globales Roboto-Font-Pack registrieren (Schalter im Menü).
		com.midgard.render.MidgardFont.register();

		// Bazaar-/AH-Preise im Item-Tooltip (ausschließlich vom eigenen Backend).
		com.midgard.price.PriceTooltips.register();

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

		// Abgebaute Blöcke an den Farming-Tracker melden (Blöcke/s + Crop-Erkennung).
		net.fabricmc.fabric.api.event.client.player.ClientPlayerBlockBreakEvents.AFTER.register(
				(world, player, pos, state) -> com.midgard.garden.FarmingTracker.INSTANCE.onBlockBroken(state));

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
				com.midgard.events.event.JacobWarner.INSTANCE.tick(client);
				com.midgard.price.PriceApi.INSTANCE.maybeRefresh();
			}
		});

		// HUD zeichnen. Jeder Teil eigen abgesichert – ein Fehler darf NIE das
		// Spiel zum Absturz bringen (Crash-Schutz in den Höhlen).
		HudRenderCallback.EVENT.register((context, tickCounter) -> {
			try {
				EventHud.INSTANCE.render(context);
			} catch (Throwable t) {
				logOnce("HUD", t);
			}
			try {
				com.midgard.bars.StatusBars.render(context);
			} catch (Throwable t) {
				logOnce("Bars", t);
			}
			try {
				com.midgard.util.Waypoints.render(context, com.midgard.mining.MiningWaypoints.markers());
				net.minecraft.client.MinecraftClient cl = net.minecraft.client.MinecraftClient.getInstance();
				if (config != null && config.miningPathLine && cl.player != null) {
					net.minecraft.util.math.Vec3d feet = new net.minecraft.util.math.Vec3d(
							cl.player.getX(), cl.player.getY(), cl.player.getZ());
					com.midgard.util.Waypoints.renderPath(context, feet,
							com.midgard.mining.MiningWaypoints.nearest(), 0xFFFFFFFF);
				}
			} catch (Throwable t) {
				logOnce("Wegpunkte", t);
			}
		});
	}
}
