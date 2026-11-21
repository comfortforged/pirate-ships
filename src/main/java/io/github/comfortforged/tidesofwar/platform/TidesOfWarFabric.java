package io.github.comfortforged.tidesofwar.platform;

//? if fabric {
import io.github.comfortforged.tidesofwar.core.ShipCommands;
import io.github.comfortforged.tidesofwar.core.ShipInteractions;
import io.github.comfortforged.tidesofwar.core.TidesOfWar;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

public class TidesOfWarFabric implements ModInitializer {
	@Override
	public void onInitialize() {
		ServerLifecycleEvents.SERVER_STARTED.register(TidesOfWar::onServerStarted);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> TidesOfWar.onServerStopping());
		ServerLifecycleEvents.BEFORE_SAVE.register((server, flush, force) -> TidesOfWar.onSave());
		ServerTickEvents.END_LEVEL_TICK.register(TidesOfWar::onLevelTick);
		ServerTickEvents.END_SERVER_TICK.register(TidesOfWar::onServerTick);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> ShipInteractions.onEntityLoad(entity));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ShipInteractions.onDisconnect(handler.player));
		UseItemCallback.EVENT.register(ShipInteractions::useItem);
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> ShipInteractions.useEntity(player, level, hand, entity));
		CommandRegistrationCallback.EVENT.register((dispatcher, ctx, selection) -> ShipCommands.register(dispatcher));
		TidesOfWar.init();
	}
}
//?}
