package io.github.comfortforged.tidesofwar.core;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Lets ships be stood on: clips an entity's move against every solid ship near it. */
public final class ShipColliders {
	private ShipColliders() {}

	/** Moves the box by {@code move} as far as the solid ships in the level allow. */
	public static Vec3 clip(ServerLevel level, AABB box, Vec3 move, double stepHeight) {
		ShipManager m = ShipManager.get();
		if (m == null || m.ships().isEmpty()) return move;
		String dim = ShipManager.dimensionId(level);
		Vec3 cur = move;
		double size = Math.max(box.getXsize(), box.getZsize());
		for (Ship s : m.ships()) {
			if (!s.isSolid() || !s.dimension.equals(dim)) continue;
			double reach = s.type.length / 2.0 + 3 + size + Math.abs(move.x) + Math.abs(move.z);
			double dx = (box.minX + box.maxX) / 2 - s.x, dz = (box.minZ + box.maxZ) / 2 - s.z;
			if (dx * dx + dz * dz > reach * reach) continue;
			cur = ShipFrame.clip(ShipCollision.of(s.type), s.pose(), box, cur, stepHeight);
		}
		return cur;
	}

	/**
	 * Called at the end of every entity's collision test. Players are left alone here
	 * (the player's own game handles them); everything else is stopped by ships on the server.
	 */
	public static Vec3 adjust(Entity e, Vec3 move, Vec3 vanilla) {
		if (e instanceof Player || !(e.level() instanceof ServerLevel level) || Ship.isPart(e)) return vanilla;
		double step = e.onGround() || move.y < 0 ? e.maxUpStep() : 0;
		Vec3 ship = clip(level, e.getBoundingBox(), move, step);
		if (ship.equals(move)) return vanilla;
		if (ship.y > Math.max(move.y, 0) + 1e-6) return ship;   // stepped up onto something
		return new Vec3(smaller(ship.x, vanilla.x), smaller(ship.y, vanilla.y), smaller(ship.z, vanilla.z));
	}

	private static double smaller(double a, double b) {
		return Math.abs(a) <= Math.abs(b) ? a : b;
	}
}
