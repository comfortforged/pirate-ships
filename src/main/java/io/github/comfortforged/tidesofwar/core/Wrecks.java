package io.github.comfortforged.tidesofwar.core;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Everything about wrecks: what raising one costs, doing it, and letting old ones go. */
public final class Wrecks {
	private Wrecks() {}

	static List<Config.Cost> cost(Ship ship) {
		List<Config.Cost> c = Config.current.wrecks.raiseCost.get(ship.type.id());
		return c == null ? List.of() : c;
	}

	static boolean canAfford(Container payer, Ship ship) {
		for (Config.Cost c : cost(ship)) {
			if (ItemMatch.count(payer, c.item) < c.count) return false;
		}
		return true;
	}

	static String costText(Ship ship) {
		StringBuilder sb = new StringBuilder();
		for (Config.Cost c : cost(ship)) {
			if (sb.length() > 0) sb.append(", ");
			String id = c.item.startsWith("#") ? "any " + c.item.substring(c.item.indexOf(':') + 1) : c.item.substring(c.item.indexOf(':') + 1);
			sb.append(c.count).append(" ").append(id.replace('_', ' '));
		}
		return sb.length() == 0 ? "nothing" : sb.toString();
	}

	private static void tell(ServerPlayer p, String text, ChatFormatting color) {
		if (p != null) p.sendSystemMessage(Component.literal(text).withStyle(color));
	}

	/** Pays the cost from {@code payer} and raises the wreck. Nothing is taken unless it works. */
	public static boolean raise(ServerLevel level, Ship ship, Container payer, ServerPlayer messageTo) {
		if (ship.state != Ship.State.WRECK) return false;
		if (!ship.canRaise(level)) {
			tell(messageTo, "Not enough open water above the wreck to raise it (a wreck on land needs a Flight Core).", ChatFormatting.RED);
			return false;
		}
		if (!canAfford(payer, ship)) {
			tell(messageTo, "Raising this " + ship.type.displayName + " needs " + costText(ship) + ".", ChatFormatting.RED);
			return false;
		}
		for (Config.Cost c : cost(ship)) ItemMatch.take(payer, c.item, c.count);
		ship.refloat(level, Config.current.wrecks.raiseHullPercent / 100.0);
		tell(messageTo, "The " + ship.type.displayName + " rises from the deep!", ChatFormatting.GOLD);
		return true;
	}

	/** Admin repair: full hull, and a wreck rises for free if the water allows. */
	public static boolean repairFully(ServerLevel level, Ship ship) {
		if (ship.state == Ship.State.WRECK) {
			if (!ship.canRaise(level)) return false;
			ship.refloat(level, 1.0);
		}
		ship.hull = ship.maxHull();
		ShipManager m = ShipManager.get();
		if (m != null) m.markDirty();
		return true;
	}

	static boolean mayLoot(ServerPlayer p, Ship ship) {
		if (ship.state != Ship.State.WRECK) return true;
		return !"owner".equalsIgnoreCase(Config.current.wrecks.lootable) || ShipInteractions.canManage(p, ship);
	}

	/** True if the wreck has been down longer than the configured lifetime. */
	static boolean expired(Ship ship, long gameTime) {
		int days = Config.current.wrecks.lifetimeDays;
		return ship.state == Ship.State.WRECK && days > 0 && gameTime - ship.wreckedAtGameTime > days * 24000L;
	}

	/** Spills the cargo where the wreck lay. The caller removes the ship. */
	static void spillCargo(ServerLevel level, Ship ship) {
		for (int i = 0; i < ship.cargo.getContainerSize(); i++) {
			ItemStack st = ship.cargo.getItem(i);
			if (st.isEmpty()) continue;
			level.addFreshEntity(new ItemEntity(level, ship.x, ship.y + 0.5, ship.z, st.copy()));
			ship.cargo.setItem(i, ItemStack.EMPTY);
		}
	}
}
