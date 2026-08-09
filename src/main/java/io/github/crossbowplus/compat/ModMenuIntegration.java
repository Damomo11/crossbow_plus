package io.github.crossbowplus.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import io.github.crossbowplus.client.gui.CrossbowPlusConfigScreen;

public final class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return CrossbowPlusConfigScreen::new;
	}
}
