package com.midgard.mining;

import java.util.ArrayList;
import java.util.List;

import com.midgard.events.config.ModConfig;
import com.midgard.events.hud.EventHud;
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

	private static final int GREEN = 0xFF5BE36B;

	private MiningHud() {
	}

	public static List<HudGroup> groups(ModConfig cfg, boolean preview) {
		MiningData d = MiningData.INSTANCE;
		// Mining-Boxen gibt es NUR auf den Mining-Inseln – auch im HUD-Editor.
		if (!d.onMiningIsland) {
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
					rows.add(new HudRow(c.name(), c.done() ? "Fertig" : c.progress(),
							c.done() ? GREEN : EventHud.VALUE, List.of(), c.done()));
				}
				out.add(new HudGroup(KEY_COMMISSIONS, "Commissions", rows, Items.IRON_PICKAXE));
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
				rows.add(new HudRow(name, cd >= 0 ? TimeUtil.format(cd) : "Bereit",
						cd >= 0 ? EventHud.VALUE : GREEN, List.of(), false));
				out.add(new HudGroup(KEY_ABILITY, "Pickaxe", rows, Items.DIAMOND_PICKAXE));
			}
		}

		return out;
	}
}
