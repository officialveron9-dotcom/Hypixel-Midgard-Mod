package com.midgard.mining;

import java.util.ArrayList;
import java.util.List;

import com.midgard.events.config.ModConfig;
import com.midgard.events.event.MiningEventReader;
import com.midgard.events.hud.EventHud;
import com.midgard.events.hud.EventHud.HudGroup;
import com.midgard.events.hud.EventHud.HudRow;
import com.midgard.util.TimeUtil;

import net.minecraft.item.Items;

/**
 * Baut die Mining-HUD-Gruppen (Commissions, Pickaxe-Ability, Mining-Event) aus
 * {@link MiningData} + {@link MiningEventReader}. Nur auf den Mining-Inseln
 * sichtbar. Ein eingeschaltetes Element wird IMMER gezeigt – läuft gerade
 * nichts, steht "Bereit" bzw. "inaktiv". Im Editor-Preview Beispieldaten.
 */
public final class MiningHud {

	public static final String KEY_COMMISSIONS = "MINING_COMMISSIONS";
	public static final String KEY_ABILITY = "MINING_ABILITY";
	public static final String KEY_EVENT = "MINING_EVENT";
	public static final String KEY_POWDER = "MINING_POWDER";

	private static final int GREEN = 0xFF5BE36B;
	private static final int DIM = 0xFF9A9AA5;

	private MiningHud() {
	}

	/**
	 * Element sichtbar? In der Vorschau (Editor) IMMER (disabled werden dort
	 * ausgegraut), sonst: aktiviert UND (auf Mining-Insel ODER überall-Modus).
	 */
	private static boolean vis(ModConfig cfg, String key, boolean preview) {
		return preview || (cfg.isElementEnabled(key)
				&& (MiningData.INSTANCE.onMiningIsland || cfg.isElementGlobal(key)));
	}

	public static List<HudGroup> groups(ModConfig cfg, boolean preview) {
		MiningData d = MiningData.INSTANCE;
		List<HudGroup> out = new ArrayList<>();

		// 1) Commissions; fertige grün, leer = Hinweis (immer sichtbar wenn an).
		if (vis(cfg, KEY_COMMISSIONS, preview)) {
			List<MiningData.Commission> coms = d.commissions;
			if (preview && coms.isEmpty()) {
				coms = List.of(
						new MiningData.Commission("Mithril Miner", "32,5%", false),
						new MiningData.Commission("Goblin Slayer", "DONE", true));
			}
			List<HudRow> rows = new ArrayList<>();
			for (MiningData.Commission c : coms) {
				rows.add(new HudRow(c.name(), c.done() ? "Fertig" : c.progress(),
						c.done() ? GREEN : EventHud.VALUE, List.of(), c.done()));
			}
			if (rows.isEmpty()) {
				rows.add(new HudRow("", "keine aktiv", DIM, List.of(), false));
			}
			out.add(new HudGroup(KEY_COMMISSIONS, "Commissions", rows, Items.IRON_PICKAXE));
		}

		// 2) Pickaxe-Ability: immer sichtbar wenn an; ohne Cooldown "Bereit".
		if (vis(cfg, KEY_ABILITY, preview)) {
			String name = d.abilityName.isEmpty() ? "Pickaxe-Ability" : d.abilityName;
			long cd = d.abilityCooldownRemaining();
			if (preview && d.abilityName.isEmpty()) {
				name = "Pickobulus";
				cd = 47;
			}
			List<HudRow> rows = new ArrayList<>();
			rows.add(new HudRow(name, cd >= 0 ? TimeUtil.format(cd) : "Bereit",
					cd >= 0 ? EventHud.VALUE : GREEN, List.of(), false));
			out.add(new HudGroup(KEY_ABILITY, "Pickaxe", rows, Items.DIAMOND_PICKAXE));
		}

		// 3) Powder: Mithril / Gemstone / Glacite (immer sichtbar wenn an).
		if (vis(cfg, KEY_POWDER, preview)) {
			String mi = preview && d.mithril.isEmpty() ? "1.234.567" : d.mithril;
			String ge = preview && d.gemstone.isEmpty() ? "89.012" : d.gemstone;
			String gl = preview && d.glacite.isEmpty() ? "4.560" : d.glacite;
			List<HudRow> rows = new ArrayList<>();
			if (!mi.isEmpty()) {
				rows.add(new HudRow("Mithril", mi, 0xFF6FE3C5, List.of(), false));
			}
			if (!ge.isEmpty()) {
				rows.add(new HudRow("Gemstone", ge, 0xFFE36FD8, List.of(), false));
			}
			if (!gl.isEmpty()) {
				rows.add(new HudRow("Glacite", gl, 0xFF8FC7FF, List.of(), false));
			}
			if (rows.isEmpty()) {
				rows.add(new HudRow("", "kein Powder", DIM, List.of(), false));
			}
			out.add(new HudGroup(KEY_POWDER, "Powder", rows, Items.GLOWSTONE_DUST));
		}

		// 4) Mining-Event: immer sichtbar wenn an; ohne Event "inaktiv".
		if (vis(cfg, KEY_EVENT, preview)) {
			String ev = MiningEventReader.INSTANCE.activeEvent();
			long secs = Math.round(MiningEventReader.INSTANCE.remainingSeconds());
			List<HudRow> rows = new ArrayList<>();
			if (preview && ev == null) {
				ev = "2x Powder";
				secs = 1200;
			}
			if (ev != null) {
				rows.add(new HudRow(ev, secs > 0 ? TimeUtil.format(secs) : "aktiv",
						GREEN, List.of(), false));
			} else {
				rows.add(new HudRow("", "inaktiv", DIM, List.of(), false));
			}
			out.add(new HudGroup(KEY_EVENT, "Mining-Event", rows, Items.GUNPOWDER));
		}

		return out;
	}
}
