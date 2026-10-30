package io.github.sharket1111.indexprescript;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IndexPrescript implements ModInitializer {
	public static final String MOD_ID = "index_prescript";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModItems.init();
		PrescriptTracker.register();
		LOGGER.info("Index Prescript loaded");
	}
}
