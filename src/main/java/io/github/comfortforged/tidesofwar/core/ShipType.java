package io.github.comfortforged.tidesofwar.core;

import java.util.Locale;

/**
 * The ship sizes. All the numbers that make a sloop feel different from a galleon live here.
 *
 * Speeds are in blocks per tick (20 ticks = 1 second), turn rate in degrees per tick.
 */
public enum ShipType {
	//        name               length beam  masts (fraction of length from stern, height)        maxSpeed accel  turn  cargoRows seats cargoAt
	SLOOP("Pirate Sloop", 11, 2.0f, new float[][]{{0.55f, 10f}}, 0.45f, 0.0065f, 3.0f, 3, 3, 0.30f, 1),
	BRIGANTINE("Pirate Brigantine", 16, 3.0f, new float[][]{{0.45f, 14f}, {0.72f, 11.5f}}, 0.38f, 0.0045f, 2.0f, 3, 5, 0.30f, 3),
	GALLEON("Pirate Galleon", 24, 4.0f, new float[][]{{0.30f, 13f}, {0.52f, 18f}, {0.74f, 14.5f}}, 0.30f, 0.0030f, 1.3f, 6, 7, 0.41f, 5);

	public final String displayName;
	public final int length;
	public final float halfBeam;
	/** Each mast: {position along the ship as a fraction of length from the stern, mast height}. */
	public final float[][] masts;
	public final float maxSpeed;
	public final float acceleration;
	public final float turnRate;
	public final int cargoRows;
	public final int passengerSeats;
	public final float cargoAt;
	/** Cannons on each side (so twice this many in total). */
	public final int cannonsPerSide;

	ShipType(String displayName, int length, float halfBeam, float[][] masts, float maxSpeed, float acceleration,
			float turnRate, int cargoRows, int passengerSeats, float cargoAt, int cannonsPerSide) {
		this.displayName = displayName;
		this.length = length;
		this.halfBeam = halfBeam;
		this.masts = masts;
		this.maxSpeed = maxSpeed;
		this.acceleration = acceleration;
		this.turnRate = turnRate;
		this.cargoRows = cargoRows;
		this.passengerSeats = passengerSeats;
		this.cargoAt = cargoAt;
		this.cannonsPerSide = cannonsPerSide;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public int cargoSlots() {
		return cargoRows * 9;
	}

	public static ShipType byId(String id) {
		for (ShipType t : values()) {
			if (t.id().equals(id)) return t;
		}
		return null;
	}
}
