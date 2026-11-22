package io.github.comfortforged.tidesofwar.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The invisible solid boxes of a ship, in ship coordinates (+z bow, +x port, y = 0 waterline).
 * They follow {@link ShipModel}: hull, rails, raised stern deck, two steps up to it, masts.
 * Sails, cannons, seats, the barrel and the tiller are not solid.
 */
public final class ShipCollision {
	/** An axis-aligned box. */
	public record Box(double x0, double y0, double z0, double x1, double y1, double z1) {
		public double min(int axis) {
			return axis == 0 ? x0 : axis == 1 ? y0 : z0;
		}

		public double max(int axis) {
			return axis == 0 ? x1 : axis == 1 ? y1 : z1;
		}
	}

	public static final double HULL_BOTTOM = -0.9;
	public static final double DECK_TOP = 0.62;
	public static final double RAIL_TOP = 1.62;
	public static final double CASTLE_TOP = 2.12;
	public static final double CASTLE_RAIL_TOP = 3.12;

	private static final Map<ShipType, List<Box>> CACHE = new ConcurrentHashMap<>();

	private ShipCollision() {}

	public static List<Box> of(ShipType type) {
		return CACHE.computeIfAbsent(type, ShipCollision::build);
	}

	/** Length of the raised stern deck. */
	public static float castleLength(ShipType type) {
		return Math.max(3f, Math.round(type.length * 0.22f));
	}

	private static List<Box> build(ShipType type) {
		List<Box> out = new ArrayList<>();
		int len = type.length;
		double stern = -len / 2.0, bow = len / 2.0;

		// Hull body and rails, merging slices of equal width.
		int k = 0;
		while (k < len) {
			double w = ShipModel.halfWidthAt(type, k);
			int end = k + 1;
			while (end < len && ShipModel.halfWidthAt(type, end) == w) end++;
			double z0 = stern + k, z1 = stern + end;
			out.add(new Box(-w, HULL_BOTTOM, z0, w, DECK_TOP, z1));
			out.add(new Box(w - 0.4, DECK_TOP, z0, w, RAIL_TOP, z1));
			out.add(new Box(-w, DECK_TOP, z0, -w + 0.4, RAIL_TOP, z1));
			k = end;
		}
		double wStern = ShipModel.halfWidthAt(type, 0);
		double wBow = ShipModel.halfWidthAt(type, len - 1);
		out.add(new Box(-wStern, HULL_BOTTOM, stern - 0.4, wStern, RAIL_TOP, stern));     // transom
		out.add(new Box(-wBow, HULL_BOTTOM, bow, wBow, RAIL_TOP, bow + 0.4));             // bow plate

		// Raised stern deck, its rails, and two steps up to it.
		double zc = stern + castleLength(type);
		out.add(new Box(-wStern, DECK_TOP, stern, wStern, CASTLE_TOP, zc));
		out.add(new Box(wStern - 0.3, CASTLE_TOP, stern, wStern, CASTLE_RAIL_TOP, zc));
		out.add(new Box(-wStern, CASTLE_TOP, stern, -wStern + 0.3, CASTLE_RAIL_TOP, zc));
		out.add(new Box(-wStern, CASTLE_TOP, stern, wStern, CASTLE_RAIL_TOP, stern + 0.3));
		out.add(new Box(-1.0, DECK_TOP, zc, 1.0, RAIL_TOP, zc + 0.5));
		out.add(new Box(-1.0, DECK_TOP, zc + 0.5, 1.0, 1.12, zc + 1.0));

		// Masts.
		for (float[] mast : type.masts) {
			double mz = stern + mast[0] * len;
			out.add(new Box(-0.3, DECK_TOP, mz - 0.3, 0.3, mast[1], mz + 0.3));
		}
		return List.copyOf(out);
	}

	/** The highest top among the boxes covering the point (x, z), or negative infinity. */
	public static double topAt(List<Box> boxes, double x, double z) {
		double best = Double.NEGATIVE_INFINITY;
		for (Box b : boxes) {
			if (x > b.x0() && x < b.x1() && z > b.z0() && z < b.z1()) best = Math.max(best, b.y1());
		}
		return best;
	}
}
