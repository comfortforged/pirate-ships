package io.github.comfortforged.tidesofwar.core;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Player interactions with ships and ship items. Loader code calls these. */
public final class ShipInteractions {
	private ShipInteractions() {}

	/** Right-click with an item in hand. PASS means "not ours". */
	public static InteractionResult useItem(Player player, Level level, InteractionHand hand) {
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
	}

	/** Right-click on an entity. PASS means "not ours". */
	public static InteractionResult useEntity(Player player, Level level, InteractionHand hand, Entity entity) {
		if (level.isClientSide()) return InteractionResult.PASS;
		ShipManager m = ShipManager.get();
		if (m == null || !(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
		Ship ship = m.shipOfPart(entity);
		if (ship == null) return InteractionResult.PASS;
		if (hand != InteractionHand.MAIN_HAND) return InteractionResult.SUCCESS;
		handleShipClick(m, ship, sp, entity);
		return InteractionResult.SUCCESS;
	}

	/** Every entity that loads on the server. Old saved copies of ship parts get cleaned up. */
	public static void onEntityLoad(Entity entity) {
		ShipManager m = ShipManager.get();
		if (m != null) {
			m.onEntityLoad(entity);
		} else if (Ship.isPart(entity)) {
			// Leftovers loading before the manager starts are never live parts.
			entity.discard();
		}
	}

	/** Get players off ship seats before they log out, so the seat isn't saved with them. */
	public static void onDisconnect(ServerPlayer p) {
		if (p != null && p.getVehicle() != null && Ship.isPart(p.getVehicle())) {
			p.stopRiding();
		}
	}

	static void handleShipClick(ShipManager m, Ship ship, ServerPlayer player, Entity clicked) {
		if (clicked == ship.tillerButton) {
			if (player.isShiftKeyDown()) {
				if (!canManage(player, ship)) {
					player.sendOverlayMessage(Component.literal("Only " + ship.ownerName + " can pack up this ship.")
							.withStyle(ChatFormatting.RED));
					return;
				}
				if (ship.state != Ship.State.AFLOAT) {
					player.sendOverlayMessage(Component.literal("A wreck can't be packed up.").withStyle(ChatFormatting.RED));
					return;
				}
				long left = ship.lastDamagedAt + Config.current.hull.combatLockSeconds * 20L - player.level().getGameTime();
				if (left > 0) {
					player.sendOverlayMessage(Component.literal("Can't pack up during battle (" + (left + 19) / 20 + "s).")
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
				boolean gunner = idx + 1 > ship.type.passengerSeats;
				int free = gunner ? -1 : ship.firstFreePassengerSeat();
				if (gunner) {
					player.sendOverlayMessage(Component.literal("Someone is already manning those guns.").withStyle(ChatFormatting.RED));
				} else if (free < 0 || !ship.seat(player, free)) {
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
			Compat.giveBack(player, item);
		}
	}
}
