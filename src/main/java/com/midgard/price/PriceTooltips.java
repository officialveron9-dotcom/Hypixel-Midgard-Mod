package com.midgard.price;

import com.midgard.Midgard;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Hängt Bazaar-/AH-Preise an den Item-Tooltip an (liest die SkyBlock-Item-ID
 * aus den Item-Daten und schlägt sie im Backend-Cache {@link PriceApi} nach).
 * Rein lesend; ohne Backend-Verbindung wird ein Hinweis angezeigt.
 */
public final class PriceTooltips {

	private static boolean debugLogged = false;

	private PriceTooltips() {
	}

	public static void register() {
		ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
			try {
				if (Midgard.config == null || !Midgard.config.showPrices) {
					return;
				}
				String id = skyblockId(stack);
				if (id.isEmpty()) {
					return;
				}
				if (!PriceApi.INSTANCE.isOnline()) {
					separator(lines);
					lines.add(Text.literal("Keine Backend-Verbindung").formatted(Formatting.RED));
					return;
				}
				double[] bz = PriceApi.INSTANCE.bazaar(id);
				double[] ah = PriceApi.INSTANCE.auction(id);
				if (bz == null && ah == null) {
					return;
				}
				separator(lines);
				if (bz != null) {
					lines.add(Text.literal("Bazaar Kauf: ")
							.formatted(Formatting.GRAY)
							.append(Text.literal(coins(bz[0])).formatted(Formatting.GOLD)));
					lines.add(Text.literal("Bazaar Verkauf: ")
							.formatted(Formatting.GRAY)
							.append(Text.literal(coins(bz[1])).formatted(Formatting.GOLD)));
				}
				if (ah != null) {
					lines.add(Text.literal("AH Lowest BIN: ")
							.formatted(Formatting.GRAY)
							.append(Text.literal(coins(ah[3])).formatted(Formatting.AQUA)));
					lines.add(Text.literal("AH Minimum: ")
							.formatted(Formatting.GRAY)
							.append(Text.literal(coins(ah[0])).formatted(Formatting.AQUA)));
					lines.add(Text.literal("AH Durchschnitt: ")
							.formatted(Formatting.GRAY)
							.append(Text.literal(coins(ah[2])).formatted(Formatting.AQUA)));
					lines.add(Text.literal("AH Maximum: ")
							.formatted(Formatting.GRAY)
							.append(Text.literal(coins(ah[1])).formatted(Formatting.AQUA)));
				}
			} catch (Throwable ignored) {
				// Tooltip darf nie crashen
			}
		});
	}

	/**
	 * Trennlinie zwischen Spiel-Tooltip und Preiszeilen: durchgestrichene
	 * Leerzeichen rendert Minecraft als durchgehenden Strich.
	 */
	private static void separator(java.util.List<Text> lines) {
		lines.add(Text.literal(" ".repeat(40))
				.formatted(Formatting.DARK_GRAY, Formatting.STRIKETHROUGH));
	}

	private static String skyblockId(ItemStack stack) {
		NbtComponent c = stack.get(DataComponentTypes.CUSTOM_DATA);
		if (c == null) {
			return "";
		}
		NbtCompound n = c.copyNbt();
		String id = n.getString("id", "");
		if (id.isEmpty()) {
			id = n.getCompoundOrEmpty("ExtraAttributes").getString("id", "");
		}
		if (!debugLogged && !n.isEmpty()) {
			debugLogged = true;
			System.out.println("[Midgard] Tooltip-Item custom_data Keys=" + n.getKeys() + " id='" + id + "'");
		}
		return id;
	}

	/** Kompakte Münz-Anzeige: 1.2B / 3.4M / 12.3k / 123. */
	private static String coins(double v) {
		if (v >= 1_000_000_000d) {
			return String.format("%.2fB", v / 1_000_000_000d);
		}
		if (v >= 1_000_000d) {
			return String.format("%.2fM", v / 1_000_000d);
		}
		if (v >= 1_000d) {
			return String.format("%.1fk", v / 1_000d);
		}
		return String.format("%.1f", v);
	}
}
