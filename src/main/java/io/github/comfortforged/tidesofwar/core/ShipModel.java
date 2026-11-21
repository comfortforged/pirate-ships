package io.github.comfortforged.tidesofwar.core;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the look of a ship out of scaled block pieces, in the ship's own coordinates:
 * +Z points to the bow (front), +X to the left (port) side, +Y up. Y = 0 is the waterline.
 */
public final class ShipModel {
	/** One scaled block: it fills the box from (x, y, z) with size (sx, sy, sz). */
	public record Part(BlockState block, float x, float y, float z, float sx, float sy, float sz) {}

	public final List<Part> parts = new ArrayList<>();
	/** Seat 0 is the captain's seat at the tiller; the rest are passenger benches. */
	public final List<Vector3f> seats = new ArrayList<>();
	public Vector3f tiller;
	public Vector3f cargo;
	/** Points around the hull at the waterline used to stop the ship running aground. */
	public final List<float[]> footprint = new ArrayList<>();
	public float deckY = 0.62f;

	private static final float BOTTOM = -0.9f;
	private static final float SIDE_TOP = 1.1f;

	private static final ShipModel[] CACHE = new ShipModel[ShipType.values().length];

	public static ShipModel of(ShipType type) {
		ShipModel m = CACHE[type.ordinal()];
		if (m == null) {
			m = new ShipModel(type);
			CACHE[type.ordinal()] = m;
		}
		return m;
	}

	/** Half the width of the hull at slice k (0 = stern). Tapers toward the bow. */
	static float halfWidthAt(ShipType type, int k) {
		float u = (k + 0.5f) / type.length;
		float w;
		if (u < 0.12f) {
			w = type.halfBeam * 0.82f;
		} else if (u < 0.62f) {
			w = type.halfBeam;
		} else {
			float t = (u - 0.62f) / 0.38f;
			w = type.halfBeam * (1f - (float) Math.pow(t, 1.6) * 0.78f);
		}
		w = Math.round(w * 4f) / 4f;
		return Math.max(0.5f, w);
	}

	private void add(BlockState b, float x, float y, float z, float sx, float sy, float sz) {
		parts.add(new Part(b, x, y, z, sx, sy, sz));
	}

	private ShipModel(ShipType type) {
		BlockState hull = Blocks.DARK_OAK_PLANKS.defaultBlockState();
		BlockState deck = Blocks.SPRUCE_PLANKS.defaultBlockState();
		BlockState trim = Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState();
		BlockState spar = Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState();
		BlockState sail = Blocks.WOOL.white().defaultBlockState();

		final int len = type.length;
		final float stern = -len / 2f;
		final float bow = len / 2f;

		// ---- Hull: merge slices with the same width into one long piece each. ----
		int k = 0;
		while (k < len) {
			float w = halfWidthAt(type, k);
			int end = k + 1;
			while (end < len && halfWidthAt(type, end) == w) end++;
			float z0 = stern + k;
			float zl = end - k;
			add(hull, -w + 0.4f, BOTTOM, z0, 2 * w - 0.8f, 0.4f, zl);                 // bottom
			add(hull, w - 0.4f, BOTTOM, z0, 0.4f, SIDE_TOP - BOTTOM, zl);              // port side
			add(hull, -w, BOTTOM, z0, 0.4f, SIDE_TOP - BOTTOM, zl);                    // starboard side
			add(deck, -w + 0.4f, 0.5f, z0, 2 * w - 0.8f, 0.12f, zl);                    // deck
			add(trim, w - 0.45f, SIDE_TOP, z0, 0.5f, 0.25f, zl);                        // port rail
			add(trim, -w - 0.05f, SIDE_TOP, z0, 0.5f, 0.25f, zl);                       // starboard rail
			for (float z = z0 + 0.5f; z < z0 + zl; z += 1f) {
				footprint.add(new float[]{w - 0.2f, z});
				footprint.add(new float[]{-(w - 0.2f), z});
			}
			k = end;
		}
		float wStern = halfWidthAt(type, 0);
		float wBow = halfWidthAt(type, len - 1);
		add(hull, -wStern, BOTTOM, stern - 0.4f, 2 * wStern, SIDE_TOP - BOTTOM + 0.25f, 0.4f);   // transom
		add(hull, -wBow, BOTTOM, bow, 2 * wBow, SIDE_TOP - BOTTOM + 0.25f, 0.4f);                // bow plate
		footprint.add(new float[]{0f, bow + 0.3f});
		footprint.add(new float[]{0f, stern - 0.3f});

		// Bowsprit sticking out of the front.
		float spritLen = Math.max(3f, len * 0.22f);
		add(trim, -0.15f, 1.0f, bow, 0.3f, 0.3f, spritLen);

		// ---- Raised stern deck (quarterdeck) where the captain steers. ----
		float castleLen = Math.max(3f, Math.round(len * 0.22f));
		float castleTop = 2.12f;
		add(hull, -wStern, 0.6f, stern, 2 * wStern, castleTop - 0.12f - 0.6f, castleLen);
		add(deck, -wStern, castleTop - 0.12f, stern, 2 * wStern, 0.12f, castleLen);
		add(trim, -wStern, castleTop, stern, 0.3f, 0.5f, castleLen);
		add(trim, wStern - 0.3f, castleTop, stern, 0.3f, 0.5f, castleLen);
		add(trim, -wStern, castleTop, stern, 2 * wStern, 0.5f, 0.3f);
		BlockState lantern = Blocks.LANTERN.defaultBlockState();
		add(lantern, -wStern - 0.1f, castleTop + 0.5f, stern - 0.1f, 0.7f, 0.7f, 0.7f);
		add(lantern, wStern - 0.6f, castleTop + 0.5f, stern - 0.1f, 0.7f, 0.7f, 0.7f);

		// Captain's seat and the tiller post in front of it.
		seats.add(new Vector3f(0f, castleTop, stern + 0.9f));
		add(Blocks.DARK_OAK_FENCE.defaultBlockState(), -0.5f, castleTop, stern + 1.7f, 1f, 1.1f, 1f);
		add(trim, -0.6f, castleTop + 1.0f, stern + 2.05f, 1.2f, 0.18f, 0.18f);
		tiller = new Vector3f(0f, castleTop, stern + 2.2f);

		// ---- Masts with giant sails. ----
		float tallest = 0;
		int tallestIdx = 0;
		for (int i = 0; i < type.masts.length; i++) {
			if (type.masts[i][1] > tallest) {
				tallest = type.masts[i][1];
				tallestIdx = i;
			}
		}
		for (int i = 0; i < type.masts.length; i++) {
			float mz = stern + type.masts[i][0] * len;
			float h = type.masts[i][1];
			add(spar, -0.3f, 0.5f, mz - 0.3f, 0.6f, h, 0.6f);
			float sw = 2 * type.halfBeam * 1.4f;   // sails are wider than the hull
			if (h >= 11f) {
				float lowTop = 2.6f + h * 0.42f;
				sailWithYards(sail, spar, sw, 2.6f, lowTop, mz);
				sailWithYards(sail, spar, sw * 0.8f, lowTop + 0.7f, h - 1.6f, mz);
			} else {
				sailWithYards(sail, spar, sw, 2.6f, h - 1.4f, mz);
			}
			if (i == tallestIdx) {
				// Crow's nest and a black pirate flag on the tallest mast.
				add(deck, -0.9f, h - 1.3f, mz - 0.9f, 1.8f, 0.15f, 1.8f);
				add(trim, -0.9f, h - 1.15f, mz - 0.9f, 1.8f, 0.45f, 0.12f);
				add(trim, -0.9f, h - 1.15f, mz + 0.78f, 1.8f, 0.45f, 0.12f);
				add(Blocks.WOOL.black().defaultBlockState(), 0.3f, h - 0.1f, mz - 0.05f, 2.0f, 1.2f, 0.1f);
				add(Blocks.CONCRETE.white().defaultBlockState(), 1.05f, h + 0.25f, mz - 0.08f, 0.5f, 0.5f, 0.16f);
			}
		}

		// ---- Cargo barrel on deck. ----
		float cz = stern + type.cargoAt * len;
		add(Blocks.BARREL.defaultBlockState(), -0.5f, deckY, cz - 0.5f, 1f, 1f, 1f);
		cargo = new Vector3f(0f, deckY, cz);

		// ---- Passenger benches along both sides of the main deck. ----
		int benches = type.passengerSeats;
		float zStart = stern + castleLen + 1.2f;
		float zEnd = bow - 2.0f;
		int perSide = (benches + 1) / 2;
		float step = perSide > 1 ? (zEnd - zStart) / (perSide - 1) : 0;
		BlockState bench = Blocks.SPRUCE_SLAB.defaultBlockState();
		int placed = 0;
		for (int i = 0; i < perSide && placed < benches; i++) {
			float z = zStart + step * i;
			if (Math.abs(z - cz) < 1.1f) z += 1.2f;
			int slice = Math.max(0, Math.min(len - 1, (int) Math.floor(z - stern)));
			float x = halfWidthAt(type, slice) - 1.0f;
			for (int side = 0; side < 2 && placed < benches; side++) {
				float sx = side == 0 ? x : -x;
				add(bench, sx - 0.4f, deckY, z - 0.4f, 0.8f, 0.8f, 0.8f);
				seats.add(new Vector3f(sx, deckY + 0.4f, z));
				placed++;
			}
		}
	}

	private void sailWithYards(BlockState sail, BlockState spar, float width, float y0, float y1, float mz) {
		add(sail, -width / 2f, y0, mz + 0.32f, width, y1 - y0, 0.12f);
		add(spar, -width / 2f - 0.25f, y1, mz + 0.22f, width + 0.5f, 0.25f, 0.25f);
		add(spar, -width / 2f - 0.15f, y0 - 0.25f, mz + 0.22f, width + 0.3f, 0.25f, 0.25f);
	}
}
