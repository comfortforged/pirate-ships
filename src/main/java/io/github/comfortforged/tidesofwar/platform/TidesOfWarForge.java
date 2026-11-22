package io.github.comfortforged.tidesofwar.platform;

//? if forge {
/*import io.github.comfortforged.tidesofwar.core.ShipCommands;
import io.github.comfortforged.tidesofwar.core.ShipInteractions;
import io.github.comfortforged.tidesofwar.core.TidesOfWar;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(TidesOfWar.MOD_ID)
public final class TidesOfWarForge {
	public TidesOfWarForge(FMLJavaModLoadingContext context) {
		ServerStartedEvent.BUS.addListener(e -> TidesOfWar.onServerStarted(e.getServer()));
		ServerStoppingEvent.BUS.addListener(e -> TidesOfWar.onServerStopping());
		LevelEvent.Save.BUS.addListener(e -> {
			if (e.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD) TidesOfWar.onSave();
		});
		TickEvent.LevelTickEvent.Post.BUS.addListener(e -> {
			if (e.level() instanceof ServerLevel level) TidesOfWar.onLevelTick(level);
		});
		TickEvent.ServerTickEvent.Post.BUS.addListener(e -> TidesOfWar.onServerTick(e.server()));
		EntityJoinLevelEvent.BUS.addListener(e -> {
			if (!e.getLevel().isClientSide()) ShipInteractions.onEntityLoad(e.getEntity());
		});
		PlayerEvent.PlayerLoggedOutEvent.BUS.addListener(e -> {
			if (e.getEntity() instanceof ServerPlayer p) ShipInteractions.onDisconnect(p);
		});
		PlayerInteractEvent.RightClickItem.BUS.addListener(e -> {
			InteractionResult r = ShipInteractions.useItem(e.getEntity(), e.getLevel(), e.getHand());
			if (r == InteractionResult.PASS) return false;
			e.setCancellationResult(r);
			return true;
		});
		PlayerInteractEvent.EntityInteractSpecific.BUS.addListener(e -> {
			InteractionResult r = ShipInteractions.useEntity(e.getEntity(), e.getLevel(), e.getHand(), e.getTarget());
			if (r == InteractionResult.PASS) return false;
			e.setCancellationResult(r);
			return true;
		});
		RegisterCommandsEvent.BUS.addListener(e -> ShipCommands.register(e.getDispatcher()));
		TidesOfWar.init();
	}
}
*///?}
