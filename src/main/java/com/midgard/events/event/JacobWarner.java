package com.midgard.events.event;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.midgard.Midgard;
import com.midgard.events.skyblock.SkyblockHook;
import com.midgard.price.PriceApi;
import com.midgard.util.TimeUtil;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Warnt kurz vor dem nächsten Jacob-Contest mit einer LOKALEN Chat-Nachricht
 * (5 Minuten und 1 Minute vorher). Es wird nichts an den Server gesendet —
 * die Nachricht erscheint nur beim Spieler selbst.
 */
public class JacobWarner {

	public static final JacobWarner INSTANCE = new JacobWarner();

	private final Set<Long> warned5 = new HashSet<>();
	private final Set<Long> warned1 = new HashSet<>();

	public void tick(MinecraftClient mc) {
		if (Midgard.config == null || !Midgard.config.jacobWarn) {
			return;
		}
		if (mc == null || mc.player == null || !SkyblockHook.INSTANCE.onSkyblock) {
			return;
		}
		long now = System.currentTimeMillis() / 1000;
		List<Map.Entry<Long, List<String>>> up = PriceApi.INSTANCE.jacobUpcoming(now, 1);
		if (up.isEmpty()) {
			return;
		}
		Map.Entry<Long, List<String>> next = up.get(0);
		long in = next.getKey() - now;
		if (in <= 300 && in > 240 && warned5.add(next.getKey())) {
			send(mc, next, in);
		} else if (in <= 60 && in > 5 && warned1.add(next.getKey())) {
			send(mc, next, in);
		}
	}

	private void send(MinecraftClient mc, Map.Entry<Long, List<String>> contest, long seconds) {
		String crops = String.join(", ", contest.getValue());
		mc.player.sendMessage(Text.literal("[Midgard] ").formatted(Formatting.GOLD)
				.append(Text.literal("Jacob-Contest in " + TimeUtil.format(seconds) + ": ")
						.formatted(Formatting.YELLOW))
				.append(Text.literal(crops).formatted(Formatting.GREEN)), false);
	}
}
