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
 * Hängt Bazaar-Preise an den Item-Tooltip an (liest die SkyBlock-Item-ID aus
 * den Item-Daten und schlägt sie in {@link BazaarData} nach). Rein lesend.
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
				double[] bz = BazaarData.INSTANCE.get(id);
				if (bz != null) {
					lines.add(Text.literal("Bazaar Kauf: ")
							.formatted(Formatting.GRAY)
							.append(Text.literal(coins(bz[0])).formatted(Formatting.GOLD)));
					lines.add(Text.literal("Bazaar Verkauf: ")
							.formatted(Formatting.GRAY)
							.append(Text.literal(coins(bz[1])).formatted(Formatting.GOLD)));
				}
				double[] ah = PriceApi.INSTANCE.auction(id);
				if (ah == null) {
					ah = AuctionData.INSTANCE.get(id);
				}
				if (ah != null) {
					lines.add(Text.literal("AH Lowest BIN: ")
							.formatted(Formatting.GRAY)
							.append(Text.literal(coins(ah[3])).formatted(Formatting.AQUA)));
					lines.add(Text.literal("AH min/Ø/max: ")
							.formatted(Formatting.GRAY)
							.append(Text.literal(coins(ah[0]) + " / " + coins(ah[2]) + " / " + coins(ah[1]))
									.formatted(Formatting.AQUA)));
				}
			} catch (Throwable ignored) {
				// Tooltip darf nie crashen
			}
		});
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
