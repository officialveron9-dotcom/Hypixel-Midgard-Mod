package com.midgard;

import com.midgard.events.gui.ConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Bindet Midgard in das "Mods"-Menü (ModMenu) ein, sodass die Einstellungen
 * direkt über die Mod-Liste geöffnet werden können.
 */
public class MidgardModMenu implements ModMenuApi {

	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return ConfigScreen::new;
	}
}
