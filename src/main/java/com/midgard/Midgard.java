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

	@Override
	public void onInitializeClient() {
		System.out.println("[Midgard] init build=2026-06-11 (In-Game-Jacob, device-res Schrift, Font-Pack)");
		config = ModConfig.load();

		// Optionales globales Roboto-Font-Pack registrieren (Schalter im Menü).
		com.midgard.render.MidgardFont.register();

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

		// Chat-Nachrichten an den Live-Event-Tracker weitergeben (nur lesen, nie senden).
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!overlay) {
				LiveEventTracker.INSTANCE.onChat(message.getString());
			}
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

			// Etwa zweimal pro Sekunde reicht völlig.
			if (++tickCounter >= 10) {
				tickCounter = 0;
				SkyblockHook.INSTANCE.update(client);
				EventManager.INSTANCE.update();
			}
		});

		// HUD zeichnen.
		HudRenderCallback.EVENT.register((context, tickCounter) -> EventHud.INSTANCE.render(context));
	}
}
