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
		if (p != null) ClientMods.forget(p.getUUID());
		if (p != null && p.getVehicle() != null && Ship.isPart(p.getVehicle())) {
			p.stopRiding();
		}
	}

	static void handleShipClick(ShipManager m, Ship ship, ServerPlayer player, Entity clicked) {
		if (clicked == ship.tillerButton && ship.state != Ship.State.AFLOAT) {
			wreckClick(ship, player);
			return;
		}
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
			ItemStack held = player.getMainHandItem();
			if (ship.hull < ship.maxHull() && ItemMatch.matches(held, Config.current.hull.repairItem)) {
				repair(ship, player, held);
				return;
			}
			Upgrades.Part upgrade = UpgradeItems.partOf(held);
			if (upgrade != null) {
				applyUpgrade(m, ship, player, held, upgrade);
				return;
			}
			if (!ship.seat(player, 0)) {
				player.sendOverlayMessage(Component.literal("Someone is already at the helm.").withStyle(ChatFormatting.RED));
			}
			return;
		}
		if (clicked == ship.cargoButton) {
			if (ship.state == Ship.State.SINKING) {
				player.sendOverlayMessage(Component.literal("She's going down!").withStyle(ChatFormatting.RED));
			} else if (!Wrecks.mayLoot(player, ship)) {
				player.sendOverlayMessage(Component.literal("Only " + ship.ownerName + " can loot this wreck.").withStyle(ChatFormatting.RED));
			} else {
				m.openCargo(player, ship);
			}
			return;
		}
		if (clicked instanceof Interaction && ship.state != Ship.State.AFLOAT) {
			player.sendOverlayMessage(Component.literal("This ship is wrecked.").withStyle(ChatFormatting.RED));
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

	private static void applyUpgrade(ShipManager m, Ship ship, ServerPlayer player, ItemStack held, Upgrades.Part part) {
		if (!canManage(player, ship)) {
			player.sendOverlayMessage(Component.literal("Only " + ship.ownerName + " can upgrade this ship.").withStyle(ChatFormatting.RED));
			return;
		}
		int tier = UpgradeItems.tierOf(held);
		String error = ship.tryInstall(part, tier, player.level().getGameTime());
		if (error != null) {
			player.sendOverlayMessage(Component.literal(error).withStyle(ChatFormatting.RED));
			return;
		}
		if (!player.isCreative()) held.shrink(1);
		ship.despawn(m); // comes back next tick with the new look
		player.level().playSound(null, ship.x, ship.y, ship.z, net.minecraft.sounds.SoundEvents.ANVIL_USE,
				net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 1.2f);
		String effect = switch (part) {
			case HULL -> String.format("hull now %d points", (int) ship.maxHull());
			case SAILS -> String.format("top speed %.1f blocks/s", ShipStats.maxSpeed(ship) * 20);
			case RUDDER -> String.format("turn rate %.1f", ShipStats.turnRate(ship));
			case CANNONS -> String.format("reload %.1fs, damage %d", ShipStats.reloadTicks(ship) / 20.0, (int) ShipStats.shipDamage(ship));
			case FLIGHT -> "she can take to the air";
		};
		player.sendSystemMessage(Component.literal(part.nameOfTier(tier) + " installed: " + effect + ".").withStyle(ChatFormatting.AQUA));
	}

	private static void repair(Ship ship, ServerPlayer player, ItemStack held) {
		long now = player.level().getGameTime();
		if (ship.inCombat(now)) {
			long left = ship.lastDamagedAt + Config.current.hull.combatLockSeconds * 20L - now;
			player.sendOverlayMessage(Component.literal("Can't repair during battle (" + (left + 19) / 20 + "s).").withStyle(ChatFormatting.RED));
			return;
		}
		int before = ship.hullPercent();
		if (!player.isCreative()) held.shrink(1);
		ship.hull = Math.min(ship.maxHull(), ship.hull + Config.current.hull.repairPerItem);
		ShipManager.get().markDirty();
		player.sendOverlayMessage(Component.literal("Hull " + before + "% \u2192 " + ship.hullPercent() + "%").withStyle(ChatFormatting.GREEN));
	}

	private static void wreckClick(Ship ship, ServerPlayer player) {
		if (ship.state == Ship.State.SINKING) {
			player.sendOverlayMessage(Component.literal("She's going down!").withStyle(ChatFormatting.RED));
			return;
		}
		if (!canManage(player, ship)) {
			player.sendOverlayMessage(Component.literal("The wreck of " + ship.ownerName + "'s " + ship.type.displayName + ".").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (player.isShiftKeyDown() && player.level() instanceof ServerLevel sl) {
			Wrecks.raise(sl, ship, player.getInventory(), player);
		} else {
			boolean has = Wrecks.canAfford(player.getInventory(), ship);
			player.sendSystemMessage(Component.literal("Your wrecked " + ship.type.displayName + " can be raised for " + Wrecks.costText(ship)
					+ (has ? ". You have it all: Shift + right-click the tiller to raise her." : ". You don't have enough yet.")).withStyle(ChatFormatting.GOLD));
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
