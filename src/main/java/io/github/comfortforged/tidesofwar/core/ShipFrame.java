package io.github.comfortforged.tidesofwar.core;

import io.github.comfortforged.tidesofwar.core.ShipCollision.Box;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Ship-space maths: turning positions and movements between world and ship coordinates, and clipping a move. */
public final class ShipFrame {
	private static final double EPS = 1e-7;

	/** Where a ship is and which way it faces. */
	public record Pose(double x, double y, double z, float yaw) {}

	private ShipFrame() {}

	public static Vec3 toWorld(Pose p, double lx, double ly, double lz) {
		return Ship.toWorld(p.x(), p.y(), p.z(), p.yaw(), lx, ly, lz);
	}

	public static Vec3 toLocal(Pose p, Vec3 w) {
		double dx = w.x - p.x(), dz = w.z - p.z();
		double r = Math.toRadians(p.yaw());
		double cos = Math.cos(r), sin = Math.sin(r);
		return new Vec3(dx * cos + dz * sin, w.y - p.y(), -dx * sin + dz * cos);
	}

	/** A direction or movement from world to ship axes. */
	public static Vec3 dirToLocal(float yaw, Vec3 v) {
		double r = Math.toRadians(yaw);
		double cos = Math.cos(r), sin = Math.sin(r);
		return new Vec3(v.x * cos + v.z * sin, v.y, -v.x * sin + v.z * cos);
	}

	public static Vec3 dirToWorld(float yaw, Vec3 v) {
		double r = Math.toRadians(yaw);
		double cos = Math.cos(r), sin = Math.sin(r);
		return new Vec3(v.x * cos - v.z * sin, v.y, v.x * sin + v.z * cos);
	}

	/**
	 * How far the box can move by {@code move} (world axes) without entering the ship's boxes.
	 * The entity is treated as a square of its average width in ship space, so it doesn't snag when the ship turns.
	 * With {@code stepHeight} above zero, a blocked sideways move tries again from a raised start, like walking up stairs.
	 */
	public static Vec3 clip(List<Box> boxes, Pose pose, AABB entity, Vec3 move, double stepHeight) {
		double hw = (entity.getXsize() + entity.getZsize()) / 4.0;
		Vec3 c = toLocal(pose, new Vec3((entity.minX + entity.maxX) / 2, entity.minY, (entity.minZ + entity.maxZ) / 2));
		double[] e = {c.x - hw, c.y, c.z - hw, c.x + hw, c.y + entity.getYsize(), c.z + hw};
		Vec3 m = dirToLocal(pose.yaw(), move);

		double[] r = resolve(boxes, e.clone(), m.x, m.y, m.z, 0);
		if (stepHeight > 0 && (r[0] != m.x || r[2] != m.z)) {
			double[] s = resolve(boxes, e.clone(), m.x, m.y, m.z, stepHeight);
			if (s[0] * s[0] + s[2] * s[2] > r[0] * r[0] + r[2] * r[2] + EPS) r = s;
		}
		return dirToWorld(pose.yaw(), new Vec3(r[0], r[1], r[2]));
	}

	/** Moves e (min xyz, max xyz) along y, then the longer horizontal axis, then the other. Returns {dx, dy, dz}. */
	private static double[] resolve(List<Box> boxes, double[] e, double mx, double my, double mz, double step) {
		double dy = clipAxis(boxes, e, 1, my);
		shift(e, 1, dy);
		double up = 0;
		if (step > 0) {
			up = clipAxis(boxes, e, 1, step);
			shift(e, 1, up);
		}
		double dx, dz;
		if (Math.abs(mx) >= Math.abs(mz)) {
			dx = clipAxis(boxes, e, 0, mx);
			shift(e, 0, dx);
			dz = clipAxis(boxes, e, 2, mz);
		} else {
			dz = clipAxis(boxes, e, 2, mz);
			shift(e, 2, dz);
			dx = clipAxis(boxes, e, 0, mx);
		}
		if (step > 0) {
			// settle back down onto whatever we stepped up onto
			double fall = clipAxis(boxes, e, 1, -up);
			dy += up + fall;
		}
		return new double[]{dx, dy, dz};
	}

	private static void shift(double[] e, int axis, double d) {
		e[axis] += d;
		e[axis + 3] += d;
	}

	private static double clipAxis(List<Box> boxes, double[] e, int axis, double d) {
		if (d == 0) return 0;
		int a = (axis + 1) % 3, b = (axis + 2) % 3;
		for (Box box : boxes) {
			if (!(e[a] < box.max(a) - EPS && e[a + 3] > box.min(a) + EPS)) continue;
			if (!(e[b] < box.max(b) - EPS && e[b + 3] > box.min(b) + EPS)) continue;
			if (d > 0 && e[axis + 3] <= box.min(axis) + EPS) {
				d = Math.min(d, box.min(axis) - e[axis + 3]);
			} else if (d < 0 && e[axis] >= box.max(axis) - EPS) {
				d = Math.max(d, box.max(axis) - e[axis]);
			}
		}
		return d;
	}
}
