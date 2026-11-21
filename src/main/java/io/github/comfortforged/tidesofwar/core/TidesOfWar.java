package io.github.comfortforged.tidesofwar.core;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Loader-neutral entry points. Each loader's platform class calls these from its events. */
public final class TidesOfWar {
	public static final String MOD_ID = "tidesofwar";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private TidesOfWar() {}

	public static void init() {
		LOGGER.info("Tides of War ready: {} ship types", ShipType.values().length);
	}

	public static boolean selfTestEnabled() {
		return Boolean.getBoolean("tidesofwar.selftest");
	}

	public static void onServerStarted(MinecraftServer server) {
		ShipManager.start(server);
		if (selfTestEnabled()) SelfTest.setup(server);
	}

	public static void onServerStopping() {
		ShipManager.stop();
	}

	public static void onSave() {
		ShipManager m = ShipManager.get();
		if (m != null) m.save();
	}

	public static void onLevelTick(ServerLevel level) {
		ShipManager m = ShipManager.get();
		if (m != null) m.tickLevel(level);
	}

	public static void onServerTick(MinecraftServer server) {
		if (selfTestEnabled()) SelfTest.step(server);
	}
}
