package io.github.comfortforged.tidesofwar.platform;

//? if fabric {
import io.github.comfortforged.tidesofwar.core.TidesOfWar;
import net.fabricmc.api.ClientModInitializer;

public class TidesOfWarFabricClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		TidesOfWar.LOGGER.info("Tides of War client part loaded");
	}
}
//?}
