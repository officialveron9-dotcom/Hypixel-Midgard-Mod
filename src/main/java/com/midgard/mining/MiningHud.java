package com.midgard.mining;

import java.util.ArrayList;
import java.util.List;

import com.midgard.events.config.ModConfig;
import com.midgard.events.hud.EventHud.HudGroup;
import com.midgard.events.hud.EventHud.HudRow;
import com.midgard.util.TimeUtil;

import net.minecraft.item.Items;

/**
 * Baut die Mining-HUD-Gruppen (Commissions, Pickaxe-Ability) aus
 * {@link MiningData}. Nur auf den Mining-Inseln sichtbar (Dwarven Mines,
 * Crystal Hollows, Glacite); die Mining-EVENTS laufen weiterhin über den
 * {@code MiningEventReader} im Event-HUD. Im Editor-Preview Beispieldaten.
 */
public final class MiningHud {

	public static final String KEY_COMMISSIONS = "MINING_COMMISSIONS";
	public static final String KEY_ABILITY = "MINING_ABILITY";

	private static final int WHITE = 0xFFF1F1F4;
	private static final int GREEN = 0xFF5BE36B;
	private static final int YELLOW = 0xFFF2C94C;

	private MiningHud() {
	}

	public static List<HudGroup> groups(ModConfig cfg, boolean preview) {
		MiningData d = MiningData.INSTANCE;
		if (!preview && !d.onMiningIsland) {
			return List.of();
		}
		List<HudGroup> out = new ArrayList<>();

		// 1) Commissions mit Fortschritt; fertige grün.
		if (cfg.miningCommissions) {
			List<MiningData.Commission> coms = d.commissions;
			if (preview && coms.isEmpty()) {
				coms = List.of(
						new MiningData.Commission("Mithril Miner", "32,5%", false),
						new MiningData.Commission("Goblin Slayer", "DONE", true));
			}
			if (!coms.isEmpty()) {
				List<HudRow> rows = new ArrayList<>();
				for (MiningData.Commission c : coms) {
					rows.add(new HudRow(List.of(),
							c.name() + ": " + (c.done() ? "Fertig" : c.progress()),
							c.done() ? GREEN : WHITE, c.done()));
				}
				rows.set(0, new HudRow(List.of(Items.IRON_PICKAXE),
						rows.get(0).text(), rows.get(0).color(), rows.get(0).highlight()));
				out.add(new HudGroup(KEY_COMMISSIONS, "Commissions", rows));
			}
		}

		// 2) Pickaxe-Ability-Cooldown (Pickobulus, Mining Speed Boost, ...).
		if (cfg.miningAbility) {
			String name = d.abilityName;
			long cd = d.abilityCooldownRemaining();
			if (preview && name.isEmpty()) {
				name = "Pickobulus";
				cd = 47;
			}
			if (!name.isEmpty()) {
				List<HudRow> rows = new ArrayList<>();
				rows.add(new HudRow(List.of(Items.DIAMOND_PICKAXE), cd >= 0
						? name + ": " + TimeUtil.format(cd)
						: name + ": Bereit", cd >= 0 ? YELLOW : GREEN, false));
				out.add(new HudGroup(KEY_ABILITY, "Pickaxe", rows));
			}
		}

		return out;
	}
}
