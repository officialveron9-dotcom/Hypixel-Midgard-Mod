package com.midgard.render;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import javax.imageio.ImageIO;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

/**
 * Liefert Texturen für die UI in ECHTER Geräte-Auflösung (wie der Text):
 * <ul>
 *   <li>Icons: die 72px-PNG-Quelle wird mit Java2D (bicubic, hohe Qualität) auf
 *       exakt die benötigte Geräte-Pixelgröße skaliert und 1:1 gezeichnet –
 *       kein GPU-Verkleinern, daher scharf statt körnig.</li>
 *   <li>Carets/Pfeile: als glattes, kantengeglättetes Dreieck gerendert.</li>
 * </ul>
 * Alles wird pro (Name|Größe) einmalig gebacken und gecacht.
 */
public final class MidgardTextures {

	static {
		try {
			if (System.getProperty("java.awt.headless") == null) {
				System.setProperty("java.awt.headless", "true");
			}
		} catch (Throwable ignored) {
		}
	}

	private static final Map<String, BufferedImage> SRC = new HashMap<>();
	private static final Map<String, Identifier> CACHE = new HashMap<>();

	private MidgardTextures() {
	}

	private static final class LinearTexture extends NativeImageBackedTexture {
		private GpuSampler linear;

		LinearTexture(Supplier<String> name, NativeImage image) {
			super(name, image);
		}

		@Override
		public GpuSampler getSampler() {
			if (linear == null) {
				linear = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
			}
			return linear;
		}
	}

	private static Identifier register(String key, BufferedImage img) {
		int w = img.getWidth();
		int h = img.getHeight();
		NativeImage ni = new NativeImage(NativeImage.Format.RGBA, w, h, false);
		int[] row = new int[w];
		for (int y = 0; y < h; y++) {
			img.getRGB(0, y, w, 1, row, 0, w);
			for (int x = 0; x < w; x++) {
				int argb = row[x];
				int a = (argb >>> 24) & 0xFF;
				int r = (argb >>> 16) & 0xFF;
				int g = (argb >>> 8) & 0xFF;
				int b = argb & 0xFF;
				ni.setColor(x, y, (a << 24) | (b << 16) | (g << 8) | r);
			}
		}
		Identifier id = Identifier.of("midgard", "gen_" + key.replaceAll("[^a-z0-9_]", "_"));
		MinecraftClient.getInstance().getTextureManager()
				.registerTexture(id, new LinearTexture(() -> "midgard_" + id.getPath(), ni));
		return id;
	}

	private static Graphics2D quality(BufferedImage img) {
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
		return g;
	}

	/**
	 * Icon-Textur in genau {@code deviceSize} Pixeln (aus der 72px-Quelle bicubic
	 * herunterskaliert). 1:1 gezeichnet ergibt das ein scharfes Bild.
	 */
	public static Identifier deviceIcon(String name, int deviceSize) {
		deviceSize = Math.max(8, Math.min(256, deviceSize));
		String key = "icon_" + name + "_" + deviceSize;
		Identifier cached = CACHE.get(key);
		if (cached != null) {
			return cached;
		}
		try {
			BufferedImage src = SRC.get(name);
			if (src == null) {
				try (InputStream is = MidgardTextures.class.getResourceAsStream(
						"/assets/midgard/textures/gui/icons/" + name + ".png")) {
					if (is == null) {
						throw new IllegalStateException("Icon fehlt: " + name);
					}
					src = ImageIO.read(is);
				}
				SRC.put(name, src);
			}
			BufferedImage dst = new BufferedImage(deviceSize, deviceSize, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = quality(dst);
			g.drawImage(src, 0, 0, deviceSize, deviceSize, null);
			g.dispose();
			Identifier id = register(key, dst);
			CACHE.put(key, id);
			return id;
		} catch (Throwable t) {
			System.err.println("[Midgard] deviceIcon '" + name + "' Fehler: " + t);
			Identifier raw = Identifier.of("midgard", "textures/gui/icons/" + name + ".png");
			CACHE.put(key, raw);
			return raw;
		}
	}

	/**
	 * Glattes, kantengeglättetes Caret-Dreieck in {@code deviceSize} Pixeln –
	 * nach unten (aufgeklappt) bzw. nach rechts (zugeklappt). Weiß, einfärbbar.
	 */
	public static Identifier caret(boolean down, int deviceSize) {
		deviceSize = Math.max(6, Math.min(128, deviceSize));
		String key = "caret_" + (down ? "d" : "r") + "_" + deviceSize;
		Identifier cached = CACHE.get(key);
		if (cached != null) {
			return cached;
		}
		BufferedImage img = new BufferedImage(deviceSize, deviceSize, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		g.setColor(Color.WHITE);
		float m = deviceSize * 0.18f; // Rand
		float s = deviceSize;
		Path2D.Float p = new Path2D.Float();
		if (down) {
			// ▼ – breite Oberkante, Spitze unten mittig
			p.moveTo(m, s * 0.30f);
			p.lineTo(s - m, s * 0.30f);
			p.lineTo(s * 0.5f, s - m);
		} else {
			// ▶ – linke Kante, Spitze rechts mittig
			p.moveTo(s * 0.30f, m);
			p.lineTo(s * 0.30f, s - m);
			p.lineTo(s - m, s * 0.5f);
		}
		p.closePath();
		g.fill(p);
		g.dispose();
		Identifier id = register(key, img);
		CACHE.put(key, id);
		return id;
	}

	/** Liefert die (faul registrierte) linear gefilterte Voll-Icon-Textur-Id. */
	public static Identifier linearIcon(String name) {
		Identifier cached = CACHE.get(name);
		if (cached != null) {
			return cached;
		}
		Identifier raw = Identifier.of("midgard", "textures/gui/icons/" + name + ".png");
		try (InputStream is = MidgardTextures.class.getResourceAsStream(
				"/assets/midgard/textures/gui/icons/" + name + ".png")) {
			if (is == null) {
				CACHE.put(name, raw);
				return raw;
			}
			NativeImage img = NativeImage.read(is);
			Identifier id = Identifier.of("midgard", "icon_lin_" + name);
			MinecraftClient.getInstance().getTextureManager()
					.registerTexture(id, new LinearTexture(() -> "midgard_icon_" + name, img));
			CACHE.put(name, id);
			return id;
		} catch (Exception e) {
			System.err.println("[Midgard] linearIcon '" + name + "' Fehler: " + e);
			CACHE.put(name, raw);
			return raw;
		}
	}
}
