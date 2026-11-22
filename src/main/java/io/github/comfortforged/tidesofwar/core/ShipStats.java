package io.github.comfortforged.tidesofwar.core;

/**
 * Every number that changes a ship's performance goes through here, so upgrades (a later phase)
 * can adjust them in one place instead of in the ship code.
 */
final class ShipStats {
	private ShipStats() {}

	static int reloadTicks(Ship ship) {
		return (int) Math.round(Config.current.cannons.reloadSeconds * 20.0);
	}

	static double shipDamage(Ship ship) {
		return Config.current.damage.shipDamage;
	}

	static double entityDamage(Ship ship) {
		return Config.current.damage.entityDamage;
	}

	static double muzzleSpeed(Ship ship) {
		return Config.current.cannons.muzzleSpeed;
	}
}
