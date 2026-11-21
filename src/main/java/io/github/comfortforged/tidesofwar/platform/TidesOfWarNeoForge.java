package io.github.comfortforged.tidesofwar.platform;

//? if neoforge {
/*import io.github.comfortforged.tidesofwar.core.ShipCommands;
import io.github.comfortforged.tidesofwar.core.ShipInteractions;
import io.github.comfortforged.tidesofwar.core.TidesOfWar;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@Mod(TidesOfWar.MOD_ID)
public class TidesOfWarNeoForge {
	public TidesOfWarNeoForge(IEventBus modBus) {
		var bus = NeoForge.EVENT_BUS;
		bus.addListener(ServerStartedEvent.class, e -> TidesOfWar.onServerStarted(e.getServer()));
		bus.addListener(ServerStoppingEvent.class, e -> TidesOfWar.onServerStopping());
		bus.addListener(LevelEvent.Save.class, e -> {
			if (e.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD) TidesOfWar.onSave();
		});
		bus.addListener(LevelTickEvent.Post.class, e -> {
			if (e.getLevel() instanceof ServerLevel level) TidesOfWar.onLevelTick(level);
		});
		bus.addListener(ServerTickEvent.Post.class, e -> TidesOfWar.onServerTick(e.getServer()));
		bus.addListener(EntityJoinLevelEvent.class, e -> {
			if (!e.getLevel().isClientSide()) ShipInteractions.onEntityLoad(e.getEntity());
		});
		bus.addListener(PlayerEvent.PlayerLoggedOutEvent.class, e -> {
			if (e.getEntity() instanceof ServerPlayer p) ShipInteractions.onDisconnect(p);
		});
		bus.addListener(PlayerInteractEvent.RightClickItem.class, e -> {
			InteractionResult r = ShipInteractions.useItem(e.getEntity(), e.getLevel(), e.getHand());
			if (r != InteractionResult.PASS) {
				e.setCancellationResult(r);
				e.setCanceled(true);
			}
		});
		bus.addListener(PlayerInteractEvent.EntityInteract.class, e -> {
			InteractionResult r = ShipInteractions.useEntity(e.getEntity(), e.getLevel(), e.getHand(), e.getTarget());
			if (r != InteractionResult.PASS) {
				e.setCancellationResult(r);
				e.setCanceled(true);
			}
		});
		bus.addListener(RegisterCommandsEvent.class, e -> ShipCommands.register(e.getDispatcher()));
		TidesOfWar.init();
	}
}
*///?}
