package io.github.comfortforged.pirateships;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PirateShips implements ModInitializer {
	public static final String MOD_ID = "pirateships";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Pirate Ships loading");
	}
}
