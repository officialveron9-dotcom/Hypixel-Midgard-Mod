package com.midgard.util;

import org.lwjgl.glfw.GLFW;

import com.midgard.Midgard;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Monitor;
import net.minecraft.client.util.VideoMode;
import net.minecraft.client.util.Window;

/**
 * Borderless-Vollbild: randloses Fenster in voller Monitorgröße statt echtem
 * (exklusivem) Vollbild. Vorteil: Alt-Tab wechselt sofort und ohne Minimieren/
 * Flackern. Wird pro Tick mit der Config abgeglichen — Schalter an = Rahmen
 * weg + Monitorgröße, Schalter aus = vorherige Fenstergröße zurück.
 */
public final class Borderless {

	private static boolean active = false;
	private static int savedX;
	private static int savedY;
	private static int savedW;
	private static int savedH;

	private Borderless() {
	}

	public static void tick(MinecraftClient mc) {
		boolean want = Midgard.config != null && Midgard.config.borderless;
		if (want == active) {
			return;
		}
		Window w = mc.getWindow();
		if (w == null || w.getHandle() == 0) {
			return;
		}
		if (want) {
			enable(w);
		} else {
			disable(w);
		}
	}

	private static void enable(Window w) {
		// Erst echtes Vollbild verlassen, sonst greift die Fenster-Manipulation nicht.
		if (w.isFullscreen()) {
			w.toggleFullscreen();
		}
		savedX = w.getX();
		savedY = w.getY();
		savedW = w.getWidth();
		savedH = w.getHeight();

		long handle = w.getHandle();
		GLFW.glfwSetWindowAttrib(handle, GLFW.GLFW_DECORATED, GLFW.GLFW_FALSE);
		Monitor m = w.getMonitor();
		if (m != null) {
			VideoMode vid = m.getCurrentVideoMode();
			GLFW.glfwSetWindowPos(handle, m.getViewportX(), m.getViewportY());
			GLFW.glfwSetWindowSize(handle, vid.getWidth(), vid.getHeight());
		}
		active = true;
		System.out.println("[Midgard] Borderless-Vollbild AN");
	}

	private static void disable(Window w) {
		long handle = w.getHandle();
		GLFW.glfwSetWindowAttrib(handle, GLFW.GLFW_DECORATED, GLFW.GLFW_TRUE);
		if (savedW > 100 && savedH > 100) {
			GLFW.glfwSetWindowPos(handle, savedX, savedY);
			GLFW.glfwSetWindowSize(handle, savedW, savedH);
		} else {
			GLFW.glfwSetWindowSize(handle, 854, 480);
		}
		active = false;
		System.out.println("[Midgard] Borderless-Vollbild AUS");
	}
}
