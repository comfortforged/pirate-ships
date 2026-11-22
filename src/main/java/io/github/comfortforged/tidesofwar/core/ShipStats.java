package io.github.comfortforged.tidesofwar.core;

/**
 * Every number that changes a ship's performance goes through here: the ship type, the config and the
 * installed upgrades are combined in one place.
 */
final class ShipStats {
	private ShipStats() {}

	static double maxHull(ShipType type, Upgrades up) {
		return Config.current.maxHull(type) * (1.0 + Config.UpgradeBonuses.at(Config.current.upgrades.hullBonusPercent, up.hull, 0) / 100.0);
	}

	static double maxHull(Ship ship) {
		return maxHull(ship.type, ship.upgrades);
	}

	private static double sails(Ship ship) {
		return Config.UpgradeBonuses.at(Config.current.upgrades.sailsSpeedMultiplier, ship.upgrades.sails, 1.0);
	}

	/** Top speed in blocks per tick. */
	static double maxSpeed(Ship ship) {
		return ship.type.maxSpeed * sails(ship);
	}

	static double acceleration(Ship ship) {
		return ship.type.acceleration * sails(ship);
	}

	/** Degrees per tick. */
	static double turnRate(Ship ship) {
		return ship.type.turnRate * Config.UpgradeBonuses.at(Config.current.upgrades.rudderTurnMultiplier, ship.upgrades.rudder, 1.0);
	}

	static int reloadTicks(Ship ship) {
		double mult = Config.UpgradeBonuses.at(Config.current.upgrades.cannonReloadMultiplier, ship.upgrades.cannons, 1.0);
		return (int) Math.round(Config.current.cannons.reloadSeconds * 20.0 * mult);
	}

	private static double cannonPower(Ship ship) {
		return Config.UpgradeBonuses.at(Config.current.upgrades.cannonDamageMultiplier, ship.upgrades.cannons, 1.0);
	}

	static double shipDamage(Ship ship) {
		return Config.current.damage.shipDamage * cannonPower(ship);
	}

	static double entityDamage(Ship ship) {
		return Config.current.damage.entityDamage * cannonPower(ship);
	}

	static double muzzleSpeed(Ship ship) {
		return Config.current.cannons.muzzleSpeed;
	}
}
