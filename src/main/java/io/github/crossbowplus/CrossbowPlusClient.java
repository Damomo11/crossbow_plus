package io.github.crossbowplus;

import io.github.crossbowplus.config.CrossbowPlusConfig;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CrossbowPlusClient implements ClientModInitializer {
	public static final String MOD_ID = "crossbow_plus";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		CrossbowPlusConfig.load();
	}
}
