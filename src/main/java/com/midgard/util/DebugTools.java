package com.midgard.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.midgard.Midgard;
import com.midgard.events.config.ModConfig;
import com.midgard.events.skyblock.ScoreboardReader;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Debug-Werkzeuge zum Finden von NPCs/Entities (z. B. den richtigen
 * Mission-NPC-Namen für die Navi). Auf Tastendruck (M) werden alle Entities
 * in der Nähe samt Name, Netz-ID, Typ-Registry-ID, Position und Distanz in den
 * Chat geschrieben; optional schwebt das gleiche Label in der Welt über jedem
 * Entity. Alles über die An/Aus-Schalter der Debug-Abteilung im Menü.
 */
public final class DebugTools {

	private static final double RANGE = 64.0;

	private DebugTools() {
	}

	/** Soll dieses Entity berücksichtigt werden? (named=nur NPCs/Mobs, all=alles) */
	private static boolean interesting(Entity e, boolean includeAll) {
		if (includeAll) {
			return true;
		}
		return e.hasCustomName()
				|| e instanceof MobEntity
				|| e instanceof ArmorStandEntity
				|| e instanceof PlayerEntity
				|| e instanceof DisplayEntity;
	}

	private static String typeId(Entity e) {
		try {
			Identifier id = Registries.ENTITY_TYPE.getId(e.getType());
			return id == null ? "?" : id.toString();
		} catch (Throwable t) {
			return "?";
		}
	}

	/** Schreibt die Entities in der Nähe in den Chat (M-Taste). */
	public static void dumpToChat(MinecraftClient mc) {
		if (mc == null || mc.player == null || mc.world == null) {
			return;
		}
		ModConfig cfg = Midgard.config;
		boolean includeAll = cfg != null && cfg.debugIncludeAll;
		final Entity self = mc.player;

		List<Entity> list = new ArrayList<>();
		for (Entity e : mc.world.getEntities()) {
			if (e == self || e == self.getVehicle()) {
				continue;
			}
			if (!interesting(e, includeAll)) {
				continue;
			}
			if (e.squaredDistanceTo(self) > RANGE * RANGE) {
				continue;
			}
			list.add(e);
		}
		list.sort(Comparator.comparingDouble(e -> e.squaredDistanceTo(self)));

		String area = ScoreboardReader.currentArea(mc);
		send(mc, Text.literal("[Midgard Debug] " + list.size() + " Entities ≤ " + (int) RANGE + "m"
				+ (area != null ? "  ·  Gebiet: " + area : "")).formatted(Formatting.AQUA, Formatting.BOLD));
		if (list.isEmpty()) {
			send(mc, Text.literal("  (nichts in der Nähe – näher rangehen)").formatted(Formatting.GRAY));
			return;
		}

		int max = 25;
		for (int i = 0; i < list.size() && i < max; i++) {
			Entity e = list.get(i);
			String name = ScoreboardReader.stripFormatting(e.getName().getString());
			int d = (int) Math.round(Math.sqrt(e.squaredDistanceTo(self)));
			MutableText line = Text.literal("• ").formatted(Formatting.DARK_GRAY)
					.append(Text.literal(name).formatted(Formatting.WHITE))
					.append(Text.literal("  " + typeId(e)).formatted(Formatting.DARK_AQUA))
					.append(Text.literal("  id=" + e.getId()).formatted(Formatting.GRAY))
					.append(Text.literal("  " + (int) e.getX() + "," + (int) e.getY() + "," + (int) e.getZ()
							+ "  " + d + "m").formatted(Formatting.DARK_GRAY));
			send(mc, line);
		}
		if (list.size() > max) {
			send(mc, Text.literal("  … " + (list.size() - max) + " weitere (näher rangehen / Filter im Menü)")
					.formatted(Formatting.GRAY));
		}
	}

	private static void send(MinecraftClient mc, Text t) {
		if (mc.inGameHud != null) {
			mc.inGameHud.getChatHud().addMessage(t);
		}
	}

	/** Schwebende Name/ID-Labels über den Entities (wenn im Menü aktiviert). */
	public static void renderLabels(WorldRenderContext ctx) {
		ModConfig cfg = Midgard.config;
		if (cfg == null || !cfg.debugEnabled || !cfg.debugEntityLabels) {
			return;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.world == null || mc.player == null || mc.gameRenderer == null
				|| mc.gameRenderer.getCamera() == null) {
			return;
		}
		MatrixStack ms = ctx.matrices();
		OrderedRenderCommandQueue queue = ctx.commandQueue();
		if (ms == null || queue == null) {
			return;
		}
		Camera camera = mc.gameRenderer.getCamera();
		Vec3d cam = camera.getCameraPos();

		CameraRenderState crs = new CameraRenderState();
		crs.pos = cam;
		crs.entityPos = cam;
		crs.orientation = camera.getRotation();
		crs.blockPos = BlockPos.ofFloored(cam);
		crs.initialized = true;

		boolean includeAll = cfg.debugIncludeAll;
		int drawn = 0;
		for (Entity e : mc.world.getEntities()) {
			if (e == mc.player || e == mc.player.getVehicle() || drawn > 120) {
				continue;
			}
			if (!interesting(e, includeAll)) {
				continue;
			}
			try {
				double wx = e.getX(), wy = e.getY() + e.getHeight() + 0.4, wz = e.getZ();
				double dx = wx - cam.x, dy = wy - cam.y, dz = wz - cam.z;
				double dsq = dx * dx + dy * dy + dz * dz;
				if (dsq > RANGE * RANGE) {
					continue;
				}
				String name = ScoreboardReader.stripFormatting(e.getName().getString());
				MutableText text = Text.literal(name)
						.setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0x55FFFF)));
				text.append(Text.literal("  id=" + e.getId()).formatted(Formatting.GRAY));

				int dist = (int) Math.round(Math.sqrt(dsq));
				float scale = (float) Math.max(0.9, dist / 12.0);
				ms.push();
				ms.translate(dx, dy, dz);
				ms.scale(scale, scale, scale);
				queue.submitLabel(ms, Vec3d.ZERO, 0, text, true, 0xF000F0, dsq, crs);
				ms.pop();
				drawn++;
			} catch (Throwable ignored) {
				// ein einzelnes Label darf nie alles abreißen
			}
		}
	}
}
