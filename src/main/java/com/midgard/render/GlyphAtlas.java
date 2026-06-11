package com.midgard.render;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;

import org.joml.Matrix3x2fStack;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

/**
 * Midgards eigener Text-Renderer im Stil von „Modern UI": Jede Glyphe wird mit
 * Javas Schrift-Renderer (Java2D, Grauwert-Antialiasing) in der ECHTEN
 * Geräte-Auflösung gerastert – also Schriftgröße × GUI-Scale – und dann 1:1
 * (pixelgenau) gezeichnet. Es wird NICHTS herunterskaliert, daher gibt es keine
 * Körnigkeit/Verpixelung; das Ergebnis ist so scharf wie Windows-Text.
 *
 * Pro benötigter Geräte-Pixelgröße wird einmalig ein Atlas gebacken und
 * gecacht. Ändert sich der GUI-Scale, entsteht automatisch ein neuer.
 */
public final class GlyphAtlas {

	private static final int FIRST = 32;
	private static final int LAST = 255;
	private static final int PAD = 2;

	static {
		// Offscreen-Schriftrasterung – kein Fenster/Fokus nötig.
		try {
			if (System.getProperty("java.awt.headless") == null) {
				System.setProperty("java.awt.headless", "true");
			}
		} catch (Throwable ignored) {
		}
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

	/** Ein fertig gebackener Atlas für genau eine Geräte-Pixelgröße. */
	private static final class Sized {
		final Identifier id;
		final int dim;
		float capHeight; // Höhe eines Großbuchstabens (Geräte-px) – für vertikale Zentrierung
		final int[] ax = new int[LAST + 1];
		final int[] ay = new int[LAST + 1];
		final int[] gw = new int[LAST + 1];
		final int[] gh = new int[LAST + 1];
		final float[] offX = new float[LAST + 1];
		final float[] offY = new float[LAST + 1];
		final float[] adv = new float[LAST + 1];

		Sized(Identifier id, int dim) {
			this.id = id;
			this.dim = dim;
		}
	}

	private final String texPrefix;
	private Font baseFont;
	private boolean ready = false;
	private final Map<Integer, Sized> bySize = new HashMap<>();

	public GlyphAtlas(String ttfName, String texPrefix) {
		this.texPrefix = texPrefix;
		try (InputStream is = GlyphAtlas.class.getResourceAsStream("/assets/midgard/font/" + ttfName + ".ttf")) {
			if (is == null) {
				throw new IllegalStateException("TTF fehlt: " + ttfName);
			}
			baseFont = Font.createFont(Font.TRUETYPE_FONT, is);
			ready = true;
			System.out.println("[Midgard] GlyphAtlas (device-res) bereit: " + texPrefix);
		} catch (Throwable t) {
			System.err.println("[Midgard] GlyphAtlas '" + texPrefix + "' Fehler: " + t);
			t.printStackTrace();
		}
	}

	public boolean isReady() {
		return ready;
	}

	private static double scaleFactor() {
		return MinecraftClient.getInstance().getWindow().getScaleFactor();
	}

	private void applyHints(Graphics2D g) {
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
	}

	private Sized atlasFor(int deviceSize) {
		deviceSize = Math.max(6, Math.min(256, deviceSize));
		Sized s = bySize.get(deviceSize);
		if (s != null) {
			return s;
		}
		try {
			s = build(deviceSize);
		} catch (Throwable t) {
			System.err.println("[Midgard] Atlas-Build " + texPrefix + "@" + deviceSize + " Fehler: " + t);
			s = null;
		}
		bySize.put(deviceSize, s); // auch null cachen, um Wiederholfehler zu vermeiden
		return s;
	}

	private Sized build(int deviceSize) {
		Font font = baseFont.deriveFont((float) deviceSize);

		// Messen (Scratch-Graphics für korrekten FontRenderContext).
		BufferedImage scratch = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		Graphics2D mg = scratch.createGraphics();
		applyHints(mg);
		mg.setFont(font);
		FontRenderContext frc = mg.getFontRenderContext();

		int[] bx = new int[LAST + 1];
		int[] by = new int[LAST + 1];
		int[] bw = new int[LAST + 1];
		int[] bh = new int[LAST + 1];
		float[] adv = new float[LAST + 1];
		for (int c = FIRST; c <= LAST; c++) {
			GlyphVector gv = font.createGlyphVector(frc, String.valueOf((char) c));
			adv[c] = (float) gv.getGlyphMetrics(0).getAdvanceX();
			Rectangle2D r = gv.getPixelBounds(frc, 0, 0);
			bw[c] = (int) r.getWidth();
			bh[c] = (int) r.getHeight();
			bx[c] = (int) Math.floor(r.getMinX());
			by[c] = (int) Math.floor(r.getMinY());
		}
		mg.dispose();

		// Atlas-Größe wählen, die alle Glyphen aufnimmt.
		int dim = 256;
		int[] px = new int[LAST + 1];
		int[] py = new int[LAST + 1];
		while (dim <= 4096) {
			if (pack(dim, bw, bh, px, py)) {
				break;
			}
			dim *= 2;
		}

		// Rastern.
		BufferedImage img = new BufferedImage(dim, dim, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		applyHints(g);
		g.setColor(Color.WHITE);
		g.setFont(font);

		Sized out = new Sized(Identifier.of("midgard", "font_" + texPrefix + "_" + deviceSize), dim);
		// Cap-Height aus 'H' (für vertikale Zentrierung); Fallback ~0.70·Größe.
		out.capHeight = bh['H'] > 0 ? bh['H'] : Math.round(0.70f * deviceSize);
		for (int c = FIRST; c <= LAST; c++) {
			out.adv[c] = adv[c];
			if (bw[c] <= 0 || bh[c] <= 0) {
				continue;
			}
			g.drawString(String.valueOf((char) c), px[c] - bx[c], py[c] - by[c]);
			out.ax[c] = px[c];
			out.ay[c] = py[c];
			out.gw[c] = bw[c];
			out.gh[c] = bh[c];
			out.offX[c] = bx[c];
			out.offY[c] = by[c];
		}
		g.dispose();

		// In NativeImage übertragen und als linear gefilterte Textur registrieren.
		NativeImage ni = new NativeImage(NativeImage.Format.RGBA, dim, dim, false);
		int[] row = new int[dim];
		for (int y = 0; y < dim; y++) {
			img.getRGB(0, y, dim, 1, row, 0, dim);
			for (int x = 0; x < dim; x++) {
				int argb = row[x];
				int a = (argb >>> 24) & 0xFF;
				int r = (argb >>> 16) & 0xFF;
				int gg = (argb >>> 8) & 0xFF;
				int b = argb & 0xFF;
				ni.setColor(x, y, (a << 24) | (b << 16) | (gg << 8) | r);
			}
		}
		MinecraftClient.getInstance().getTextureManager()
				.registerTexture(out.id, new LinearTexture(() -> "midgard_" + out.id.getPath(), ni));
		return out;
	}

	private boolean pack(int dim, int[] bw, int[] bh, int[] px, int[] py) {
		int penX = PAD, penY = PAD, rowH = 0;
		for (int c = FIRST; c <= LAST; c++) {
			if (bw[c] <= 0 || bh[c] <= 0) {
				continue;
			}
			if (penX + bw[c] + PAD > dim) {
				penX = PAD;
				penY += rowH + PAD;
				rowH = 0;
			}
			if (penY + bh[c] + PAD > dim) {
				return false;
			}
			px[c] = penX;
			py[c] = penY;
			penX += bw[c] + PAD;
			rowH = Math.max(rowH, bh[c]);
		}
		return true;
	}

	private int idx(char c) {
		return (c >= FIRST && c <= LAST) ? c : '?';
	}

	/**
	 * Zeichnet Text. (x, yTop) = linke Kante / OBERKANTE der Großbuchstaben in
	 * GUI-Koordinaten (Cap-Top). Die Grundlinie liegt also bei yTop + Cap-Height,
	 * dadurch ist die vertikale Positionierung exakt vorhersehbar/zentrierbar.
	 */
	public void draw(DrawContext ctx, String text, int x, int yTop, float sizePx, int color) {
		if (!ready) {
			return;
		}
		double gs = scaleFactor();
		int dev = (int) Math.round(sizePx * gs);
		Sized a = atlasFor(dev);
		if (a == null) {
			return;
		}
		float baseDev = (float) (yTop * gs) + a.capHeight;
		float penDev = (float) (x * gs);
		Matrix3x2fStack m = ctx.getMatrices();
		m.pushMatrix();
		m.scale((float) (1.0 / gs), (float) (1.0 / gs)); // 1 Modell-Einheit == 1 Geräte-Pixel
		for (int i = 0; i < text.length(); i++) {
			int c = idx(text.charAt(i));
			if (a.gw[c] > 0) {
				int dx = Math.round(penDev + a.offX[c]);
				int dy = Math.round(baseDev + a.offY[c]);
				m.pushMatrix();
				m.translate(dx, dy);
				ctx.drawTexture(RenderPipelines.GUI_TEXTURED, a.id, 0, 0,
						(float) a.ax[c], (float) a.ay[c], a.gw[c], a.gh[c], a.gw[c], a.gh[c], a.dim, a.dim, color);
				m.popMatrix();
			}
			penDev += a.adv[c];
		}
		m.popMatrix();
	}

	public float width(String text, float sizePx) {
		if (!ready) {
			return -1;
		}
		double gs = scaleFactor();
		int dev = (int) Math.round(sizePx * gs);
		Sized a = atlasFor(dev);
		if (a == null) {
			return -1;
		}
		float w = 0;
		for (int i = 0; i < text.length(); i++) {
			w += a.adv[idx(text.charAt(i))];
		}
		return (float) (w / gs);
	}

	/** Höhe eines Großbuchstabens in GUI-Pixeln (für vertikale Zentrierung). */
	public float capHeight(float sizePx) {
		if (!ready) {
			return -1;
		}
		double gs = scaleFactor();
		Sized a = atlasFor((int) Math.round(sizePx * gs));
		return a == null ? -1 : (float) (a.capHeight / gs);
	}
}
