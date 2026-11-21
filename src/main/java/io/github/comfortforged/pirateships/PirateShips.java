package io.github.comfortforged.pirateships;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collection;

public class PirateShips implements ModInitializer {
	public static final String MOD_ID = "pirateships";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ServerLifecycleEvents.SERVER_STARTED.register(ShipManager::start);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> ShipManager.stop());
		ServerLifecycleEvents.BEFORE_SAVE.register((server, flush, force) -> {
			ShipManager m = ShipManager.get();
			if (m != null) m.save();
		});

		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			ShipManager m = ShipManager.get();
			if (m != null) m.tickLevel(level);
		});

		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			ShipManager m = ShipManager.get();
			if (m != null) {
				m.onEntityLoad(entity);
			} else if (entity.entityTags().contains(Ship.PART_TAG)) {
				// Leftovers loading before the manager starts are never live parts.
				entity.discard();
			}
		});

		// Get players off ship seats before they log out, so the seat isn't saved with them.
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ServerPlayer p = handler.player;
			if (p != null && p.getVehicle() != null && p.getVehicle().entityTags().contains(Ship.PART_TAG)) {
				p.stopRiding();
			}
		});

		UseItemCallback.EVENT.register((player, level, hand) -> {
			ItemStack stack = player.getItemInHand(hand);
			ShipType type = ShipItems.typeOf(stack);
			if (type == null) return InteractionResult.PASS;
			if (level.isClientSide()) return InteractionResult.PASS;
			ShipManager m = ShipManager.get();
			if (m == null || !(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel sl)) {
				return InteractionResult.FAIL;
			}
			if (m.launchFromItem(sp, sl, stack, type)) {
				if (!sp.isCreative()) stack.shrink(1);
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.FAIL;
		});

		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide()) return InteractionResult.PASS;
			ShipManager m = ShipManager.get();
			if (m == null || !(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
			Ship ship = m.shipOfPart(entity);
			if (ship == null) return InteractionResult.PASS;
			if (hand != InteractionHand.MAIN_HAND) return InteractionResult.SUCCESS;
			handleShipClick(m, ship, sp, entity);
			return InteractionResult.SUCCESS;
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, ctx, selection) -> registerCommands(dispatcher));

		if (Boolean.getBoolean("pirateships.selftest")) {
			SelfTest.install();
		}

		LOGGER.info("Pirate Ships ready: {} ship types", ShipType.values().length);
	}

	private static void handleShipClick(ShipManager m, Ship ship, ServerPlayer player, Entity clicked) {
		if (clicked == ship.tillerButton) {
			if (player.isShiftKeyDown()) {
				if (!canManage(player, ship)) {
					player.sendOverlayMessage(Component.literal("Only " + ship.ownerName + " can pack up this ship.")
							.withStyle(ChatFormatting.RED));
					return;
				}
				ItemStack item = m.packUp(ship);
				giveOrDrop(player, item);
				player.sendSystemMessage(Component.literal("Your " + ship.type.displayName + " is packed up, cargo and all.")
						.withStyle(ChatFormatting.GOLD));
				return;
			}
			if (!ship.seat(player, 0)) {
				player.sendOverlayMessage(Component.literal("Someone is already at the helm.").withStyle(ChatFormatting.RED));
			}
			return;
		}
		if (clicked == ship.cargoButton) {
			m.openCargo(player, ship);
			return;
		}
		if (clicked instanceof Interaction) {
			int idx = ship.seatButtons.indexOf(clicked);
			if (idx >= 0 && !ship.seat(player, idx + 1)) {
				int free = ship.firstFreePassengerSeat();
				if (free < 0 || !ship.seat(player, free)) {
					player.sendOverlayMessage(Component.literal("All seats are taken.").withStyle(ChatFormatting.RED));
				}
			}
		}
	}

	static boolean isAdmin(ServerPlayer player) {
		return Commands.<CommandSourceStack>hasPermission(Commands.LEVEL_GAMEMASTERS).test(player.createCommandSourceStack());
	}

	static boolean canManage(ServerPlayer player, Ship ship) {
		return player.getUUID().equals(ship.owner) || isAdmin(player);
	}

	static void giveOrDrop(ServerPlayer player, ItemStack item) {
		{
			player.getInventory().placeItemBackInInventory(item, net.minecraft.util.Prediction.SERVER_ONLY);
		}
	}

	private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
		String[] typeIds = Arrays.stream(ShipType.values()).map(ShipType::id).toArray(String[]::new);
		dispatcher.register(Commands.literal("pirateships")
				.then(Commands.literal("give")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.argument("targets", EntityArgument.players())
								.then(Commands.argument("type", StringArgumentType.word())
										.suggests((c, b) -> SharedSuggestionProvider.suggest(typeIds, b))
										.executes(c -> {
											ShipType type = ShipType.byId(StringArgumentType.getString(c, "type"));
											if (type == null) {
												c.getSource().sendFailure(Component.literal("Unknown ship type. Use: " + String.join(", ", typeIds)));
												return 0;
											}
											Collection<ServerPlayer> targets = EntityArgument.getPlayers(c, "targets");
											for (ServerPlayer p : targets) giveOrDrop(p, ShipItems.create(type));
											c.getSource().sendSuccess(() -> Component.literal("Gave a " + type.displayName + " to "
													+ targets.size() + " player(s)"), true);
											return targets.size();
										}))))
				.then(Commands.literal("list")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.executes(c -> {
							ShipManager m = ShipManager.get();
							if (m == null || m.ships().isEmpty()) {
								c.getSource().sendSuccess(() -> Component.literal("No ships are afloat."), false);
								return 0;
							}
							for (Ship s : m.ships()) {
								c.getSource().sendSuccess(() -> Component.literal(String.format("%s owned by %s at %d %d %d (%s)",
										s.type.displayName, s.ownerName, (int) s.x, (int) s.y, (int) s.z, s.dimension)), false);
							}
							return m.ships().size();
						}))
				.then(Commands.literal("recall")
						.executes(c -> {
							ServerPlayer p = c.getSource().getPlayerOrException();
							ShipManager m = ShipManager.get();
							Ship best = null;
							double bestDist = Double.MAX_VALUE;
							for (Ship s : m.ships()) {
								if (!p.getUUID().equals(s.owner)) continue;
								double d = s.dimension.equals(ShipManager.dimensionId(p.level())) ? p.distanceToSqr(s.x, s.y, s.z) : 1e18;
								if (d < bestDist) {
									bestDist = d;
									best = s;
								}
							}
							if (best == null) {
								c.getSource().sendFailure(Component.literal("You don't have any ships afloat."));
								return 0;
							}
							String name = best.type.displayName;
							giveOrDrop(p, m.packUp(best));
							c.getSource().sendSuccess(() -> Component.literal("Your " + name + " has been returned to you, cargo and all."), false);
							return 1;
						})));
	}
}
