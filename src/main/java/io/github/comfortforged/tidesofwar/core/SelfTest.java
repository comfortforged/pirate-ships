package io.github.comfortforged.tidesofwar.core;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Automated check that runs on a throwaway server in GitHub (never on your real server):
 * it builds a pool, launches each ship, sails it, crashes it into a wall, turns it, packs it up
 * with cargo, relaunches it, and checks saving. Prints PASS or FAIL and shuts the server down.
 */
final class SelfTest {
	private static final Input FORWARD = new Input(true, false, false, false, false, false, false);
	private static final Input FORWARD_LEFT = new Input(true, false, true, false, false, false, false);
	private static final int Y = 100;          // water surface block
	private static final int WALL_Z = 45;

	private static int tick;
	private static final List<Ship> ships = new ArrayList<>();
	private static final List<String> failures = new ArrayList<>();
	private static double[] startZ;
	private static float sloopYawBefore;

	private SelfTest() {}

	private static void check(boolean ok, String what) {
		TidesOfWar.LOGGER.info("SELFTEST {} {}", ok ? "ok  " : "FAIL", what);
		if (!ok) failures.add(what);
	}

	public static void setup(MinecraftServer server) {
		ServerLevel level = server.overworld();
		for (int cx = -5; cx <= 5; cx++) {
			for (int cz = -5; cz <= 5; cz++) level.setChunkForced(cx, cz, true);
		}
		BlockState glass = Blocks.GLASS.defaultBlockState();
		BlockState water = Blocks.WATER.defaultBlockState();
		for (int x = -61; x <= 61; x++) {
			for (int z = -41; z <= 61; z++) {
				boolean rim = x == -61 || x == 61 || z == -41 || z == 61;
				level.setBlock(new BlockPos(x, Y - 2, z), glass, 2);
				level.setBlock(new BlockPos(x, Y - 1, z), rim ? glass : water, 2);
				level.setBlock(new BlockPos(x, Y, z), rim ? glass : water, 2);
			}
		}
		// A wall across the pool for the ships to run into.
		for (int x = -60; x <= 60; x++) {
			for (int y = Y - 1; y <= Y + 2; y++) level.setBlock(new BlockPos(x, y, WALL_Z), glass, 2);
		}

		ShipManager m = ShipManager.get();
		check(m != null, "ship manager started");
		java.util.List<String> recipes = new ArrayList<>(List.of("pirate_sloop", "pirate_brigantine", "pirate_galleon", "upgrade_flight_1"));
		for (String part : new String[]{"hull", "sails", "rudder", "cannons"}) {
			for (int t = 1; t <= 3; t++) recipes.add("upgrade_" + part + "_" + t);
		}
		for (String r : recipes) {
			var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE,
					net.minecraft.resources.Identifier.fromNamespaceAndPath(TidesOfWar.MOD_ID, r));
			check(server.getRecipeManager().byKey(key).isPresent(), "recipe " + r + " loaded");
		}
		ShipType[] types = ShipType.values();
		int[] lanes = {-40, 0, 40};
		startZ = new double[types.length];
		for (int i = 0; i < types.length; i++) {
			Ship s = new Ship(UUID.randomUUID(), types[i], ShipManager.dimensionId(level), lanes[i] + 0.5,
					Y + 1 - Ship.DRAFT, -10.5, 0f, null, "selftest");
			check(s.fits(level, s.x, s.y, s.z, s.yaw), types[i].id() + " fits in open water");
			startZ[i] = s.z;
			ships.add(s);
			m.addShip(s);
		}
		s(types.length);
	}

	private static void s(int n) {
		TidesOfWar.LOGGER.info("SELFTEST launched {} ships", n);
	}

	public static void step(MinecraftServer server) {
		if (ships.isEmpty()) return;
		tick++;
		ServerLevel level = server.overworld();
		ShipManager m = ShipManager.get();

		if (tick == 5) {
			for (Ship s : ships) {
				check(s.isSpawned(), s.type.id() + " spawned its visuals");
				int expected = ShipModel.of(s.type).parts.size();
				check(countParts(level, s) >= expected, s.type.id() + " has " + expected + "+ part entities");
				ShipModel model = ShipModel.of(s.type);
				check(model.cannons.size() == 2 * s.type.cannonsPerSide, s.type.id() + " has " + 2 * s.type.cannonsPerSide + " cannons");
				check(s.seats.size() == s.type.passengerSeats + 3, s.type.id() + " has helm, benches and two gunner seats");
				check(model.gunnerSeat(ShipModel.PORT) == s.type.passengerSeats + 1
						&& model.gunnerSeat(ShipModel.STARBOARD) == s.type.passengerSeats + 2, s.type.id() + " gunner seats come after the benches");
				s.testInput = FORWARD;
			}
		}
		if (tick == 60) {
			for (Ship s : ships) check(s.speed > 0, s.type.id() + " is picking up speed (" + String.format("%.3f", s.speed) + ")");
		}
		if (tick == 200) {
			for (int i = 0; i < ships.size(); i++) {
				Ship s = ships.get(i);
				check(s.z > startZ[i] + 5, s.type.id() + " sailed forward (z " + String.format("%.1f -> %.1f", startZ[i], s.z) + ")");
			}
		}
		if (tick == 1500) {
			for (Ship s : ships) {
				float bow = s.type.length / 2f;
				check(s.z + bow < WALL_Z + 0.5, s.type.id() + " did not sail through the wall (bow at " + String.format("%.1f", s.z + bow) + ")");
				check(s.z + bow > WALL_Z - 4, s.type.id() + " reached the wall");
				s.testInput = null;
			}
			// Turning test with the sloop: back off from the wall area and steer left.
			Ship sloop = ships.get(0);
			sloop.z = 0.5;
			sloop.speed = 0;
			sloopYawBefore = sloop.yaw;
			sloop.testInput = FORWARD_LEFT;
		}
		if (tick == 1560) {
			Ship sloop = ships.get(0);
			check(Math.abs(sloop.yaw - sloopYawBefore) > 20, "sloop turned (yaw " + sloopYawBefore + " -> " + sloop.yaw + ")");
			sloop.testInput = null;
		}
		if (tick == 1600) {
			// Save/load round trip.
			Ship g = ships.get(2);
			g.cargo.setItem(4, new ItemStack(Items.GOLD_INGOT, 37));
			g.hull = 77.5;
			m.save();
			int before = m.ships().size();
			m.load();
			check(m.ships().size() == before, "save/load keeps all " + before + " ships");
			Ship reloaded = m.ships().stream().filter(s -> s.id.equals(g.id)).findFirst().orElse(null);
			check(reloaded != null && reloaded.cargo.getItem(4).getCount() == 37, "cargo survives save/load");
			check(reloaded != null && reloaded.hull == 77.5 && reloaded.state == Ship.State.AFLOAT, "hull survives save/load");
			// The reloaded ships are new objects; the old visuals become leftovers and must get cleaned up.
			for (Ship s : ships) s.despawn(m);
			ships.clear();
			ships.addAll(m.ships());
		}
		if (tick == 1640) {
			for (Ship s : ships) check(s.isSpawned(), s.type.id() + " respawned after reload");
			Ship g = ships.stream().filter(s -> s.type == ShipType.GALLEON).findFirst().orElseThrow();
			ItemStack item = m.packUp(g);
			check(ShipItems.typeOf(item) == ShipType.GALLEON, "packed galleon item knows its type");
			check(ShipItems.hullOf(item, ShipType.GALLEON) == 77.5, "packed item keeps the hull");
			check(ShipItems.hullOf(ShipItems.create(ShipType.GALLEON), ShipType.GALLEON) == Config.current.maxHull(ShipType.GALLEON), "fresh item has full hull");
			List<ItemStack> cargo = ShipItems.readCargo(item);
			check(cargo.size() > 4 && cargo.get(4).is(Items.GOLD_INGOT) && cargo.get(4).getCount() == 37, "packed item keeps the cargo");
			check(countParts(level, g) == 0, "packing removes the ship's entities");
		}
		if (tick == 1660) {
			for (Ship s : new ArrayList<>(m.ships())) m.packUp(s);
			long left = 0;
			for (Entity e : level.getAllEntities()) {
				if (Ship.isPart(e) && !e.isRemoved()) left++;
			}
			check(left == 0, "no ship entities left over (" + left + ")");
			check(ShipItems.typeOf(ShipItems.create(ShipType.SLOOP)) == ShipType.SLOOP, "ship item round trip");
			check(ShipItems.typeOf(new ItemStack(Items.DARK_OAK_BOAT)) == null, "plain boats are not ships");
			checkLegacy(level, m);
			Ship probe = new Ship(java.util.UUID.randomUUID(), ShipType.SLOOP, "minecraft:overworld", 0, 0, 0, 0, null, "t");
			long now = level.getGameTime();
			check(!probe.inCombat(now), "fresh ship is not in combat");
			probe.lastDamagedAt = now;
			check(probe.inCombat(now + 20), "ship is in combat just after a hit");
			check(!probe.inCombat(now + Config.current.hull.combatLockSeconds * 20L + 1), "combat lock expires");

			checkUpgradeData(m);
			net.minecraft.world.entity.item.ItemEntity probeStand = new net.minecraft.world.entity.item.ItemEntity(
					level, -60.5, Y + 20, 60.5, new ItemStack(Items.STONE));
			probeStand.move(net.minecraft.world.entity.MoverType.SELF, new net.minecraft.world.phys.Vec3(0, -0.1, 0));
			check(ShipHooks.collideMixinRan, "the collision mixin is applied");
			checkCollisionMath();
			startCombat(level, m);
		}
		if (tick > COMBAT_START) combatStep(server, level, m, tick - COMBAT_START);
		if (sinkBase > 0 && tick > sinkBase) sinkStep(server, level, m, tick - sinkBase);
		if (upBase > 0 && tick > upBase) upgradeStep(server, level, m, tick - upBase);
		if (flBase > 0 && tick > flBase) flightStep(server, level, m, tick - flBase);
		if (sdBase > 0 && tick > sdBase) solidStep(server, level, m, tick - sdBase);
	}

	// ------------------------------------------------------------------ cannons and ship combat

	private static final int COMBAT_START = 1660;
	private static Ship attacker, target;
	private static double targetHullBefore;
	private static int ammoBefore;

	private static void startCombat(ServerLevel level, ShipManager m) {
		ships.clear();
		Config.current = new Config();
		attacker = new Ship(UUID.randomUUID(), ShipType.BRIGANTINE, ShipManager.dimensionId(level), -10.5, Y + 1 - Ship.DRAFT, 10.5, 0f,
				UUID.randomUUID(), "attacker");
		target = new Ship(UUID.randomUUID(), ShipType.GALLEON, ShipManager.dimensionId(level), 10.5, Y + 1 - Ship.DRAFT, 10.5, 0f,
				UUID.randomUUID(), "target");
		check(attacker.fits(level, attacker.x, attacker.y, attacker.z, 0f) && target.fits(level, target.x, target.y, target.z, 0f),
				"combat ships fit in the pool");
		m.addShip(attacker);
		m.addShip(target);
		ships.add(attacker);
		ships.add(target);
	}

	private static int gunpowder(Ship s) {
		return ItemMatch.count(s.cargo, "minecraft:gunpowder");
	}

	private static void combatStep(MinecraftServer server, ServerLevel level, ShipManager m, int t) {
		final float aim = -90f; // straight out to port, towards the target
		if (t == 10) {
			check(attacker.isSpawned() && target.isSpawned(), "combat ships spawned");
			boolean fired = Cannons.tryFire(level, attacker, ShipModel.PORT, null, aim, 0f);
			check(!fired && attacker.queue.isEmpty(), "no ammo: nothing fires");
			attacker.cargo.setItem(0, new ItemStack(Items.GUNPOWDER, 64));
			targetHullBefore = target.hull;
			ammoBefore = gunpowder(attacker);
			fired = Cannons.tryFire(level, attacker, ShipModel.PORT, null, aim, 0f);
			check(fired, "broadside fires with ammo");
			check(attacker.queue.size() == attacker.type.cannonsPerSide, "broadside queues " + attacker.type.cannonsPerSide + " cannons");
			check(ammoBefore - gunpowder(attacker) == attacker.type.cannonsPerSide, "broadside used one gunpowder per cannon");
			check(!Cannons.tryFire(level, attacker, ShipModel.PORT, null, aim, 0f), "second broadside is refused while reloading");
			check(gunpowder(attacker) == ammoBefore - attacker.type.cannonsPerSide, "refused broadside used no ammo");
		}
		if (t == 90) {
			check(target.hull < targetHullBefore, "broadside hurt the target ship (hull " + targetHullBefore + " -> " + target.hull + ")");
			check(target.inCombat(level.getGameTime()), "hit ship is in combat");
			check(attacker.hull == attacker.maxHull(), "firing ship took no damage");
			check(m.ballCount() == 0, "no cannonballs left in flight");
		}
		if (t == 100) {
			Config.current.damage.shipCombat = false;
			attacker.setReload(ShipModel.PORT, 0);
			targetHullBefore = target.hull;
			check(Cannons.tryFire(level, attacker, ShipModel.PORT, null, aim, 0f), "second broadside fires after reload");
		}
		if (t == 180) {
			check(target.hull == targetHullBefore, "shipCombat=false: no hull damage");
			Config.current.damage.shipCombat = true;
			Config.current.damage.friendlyFire = false;
			target.owner = attacker.owner;
			attacker.setReload(ShipModel.PORT, 0);
			targetHullBefore = target.hull;
			check(Cannons.tryFire(level, attacker, ShipModel.PORT, null, aim, 0f), "third broadside fires");
		}
		if (t == 260) {
			check(target.hull == targetHullBefore, "same owner, friendlyFire=false: no hull damage");
			Config.current = new Config();
			m.packUp(attacker);
			m.packUp(target);
			startImpactTests(level, m);
		}
		if (t >= 265) impactStep(server, level, m, t - 265);
	}

	// ------------------------------------------------------------------ impacts on mobs and blocks

	private static Ship gun;
	private static int tx, cz;
	private static double mz;
	private static net.minecraft.world.entity.decoration.ArmorStand stand;

	private static void startImpactTests(ServerLevel level, ShipManager m) {
		ships.clear();
		gun = new Ship(UUID.randomUUID(), ShipType.SLOOP, ShipManager.dimensionId(level), -10.5, Y + 1 - Ship.DRAFT, 10.5, 0f,
				UUID.randomUUID(), "gun");
		gun.cargo.setItem(0, new ItemStack(Items.GUNPOWDER, 64));
		m.addShip(gun);
		ships.add(gun);
		ShipModel.Cannon c = ShipModel.of(ShipType.SLOOP).cannons.stream().filter(k -> k.side() == ShipModel.PORT).findFirst().orElseThrow();
		net.minecraft.world.phys.Vec3 muzzle = gun.toWorld(new org.joml.Vector3f(c.x() + 0.6f, c.y(), c.z()));
		tx = (int) Math.floor(muzzle.x) + 8;
		cz = (int) Math.floor(muzzle.z);
		mz = muzzle.z;
	}

	private static void fireGun(ServerLevel level) {
		gun.setReload(ShipModel.PORT, 0);
		check(Cannons.tryFire(level, gun, ShipModel.PORT, null, -90f, 0f), "gun fires");
	}

	private static void plate(ServerLevel level, BlockState state) {
		for (int dz = -1; dz <= 1; dz++) {
			for (int y = Y + 1; y <= Y + 2; y++) level.setBlock(new BlockPos(tx, y, cz + dz), state, 2);
		}
	}

	private static void impactStep(MinecraftServer server, ServerLevel level, ShipManager m, int t) {
		BlockState glass = Blocks.GLASS.defaultBlockState();
		BlockState air = Blocks.AIR.defaultBlockState();
		if (t == 10) {
			level.setBlock(new BlockPos(tx, Y, cz), glass, 2);
			stand = new net.minecraft.world.entity.decoration.ArmorStand(level, tx + 0.5, Y + 1, mz);
			level.addFreshEntity(stand);
			fireGun(level);
		}
		if (t == 40) {
			check(!stand.isAlive(), "mobs=true: the target in the line of fire was destroyed");
			stand = new net.minecraft.world.entity.decoration.ArmorStand(level, tx + 0.5, Y + 1, mz);
			level.addFreshEntity(stand);
			Config.current.damage.mobs = false;
			level.setBlock(new BlockPos(tx, Y, cz), glass, 2);
			fireGun(level);
		}
		if (t == 70) {
			check(stand.isAlive(), "mobs=false: the target was left alone");
			stand.discard();
			Config.current.damage.mobs = true;
			plate(level, glass);
			fireGun(level);
		}
		if (t == 100) {
			check(level.getBlockState(new BlockPos(tx, Y + 1, cz)).isAir(), "blockDamage=true: the glass target is gone");
			Config.current.damage.blockDamage = false;
			plate(level, glass);
			fireGun(level);
		}
		if (t == 130) {
			check(level.getBlockState(new BlockPos(tx, Y + 1, cz)).is(Blocks.GLASS), "blockDamage=false: the glass target is intact");
			plate(level, air);
			Config.current = new Config();
			m.packUp(gun);
			ships.clear();
			ships.add(gun);
			startSinkTests(level, m);
		}
	}

	// ------------------------------------------------------------------ sinking, wrecks, raising

	private static int sinkBase;
	private static Ship wreck;
	private static double yBefore;
	private static net.minecraft.world.entity.decoration.ArmorStand rider;
	private static UUID wreckId;

	private static void startSinkTests(ServerLevel level, ShipManager m) {
		sinkBase = tick;
		ships.clear();
		wreck = new Ship(UUID.randomUUID(), ShipType.SLOOP, ShipManager.dimensionId(level), 30.5, Y + 1 - Ship.DRAFT, 10.5, 0f,
				UUID.randomUUID(), "wreck");
		wreck.hull = 1;
		wreckId = wreck.id;
		m.addShip(wreck);
		ships.add(wreck);
	}

	private static void sinkStep(MinecraftServer server, ServerLevel level, ShipManager m, int t) {
		if (t == 10) {
			check(wreck.isSpawned(), "sinking test ship spawned");
			rider = new net.minecraft.world.entity.decoration.ArmorStand(level, wreck.x, wreck.y + 1, wreck.z);
			level.addFreshEntity(rider);
			rider.startRiding(wreck.seats.get(1), true, true);
			check(rider.getVehicle() != null, "rider is seated on the ship");
			yBefore = wreck.y;
			wreck.damage(level, 5);
			check(wreck.state == Ship.State.SINKING && wreck.hull == 0, "zero hull starts the sinking");
			check(rider.getVehicle() == null, "riders are thrown off a sinking ship");
			check(Cannons.tryFire(level, wreck, ShipModel.PORT, null, -90f, 0f) == false, "a sinking ship can't fire");
		}
		if (t == 140) {
			check(wreck.state == Ship.State.WRECK, "ship became a wreck");
			check(wreck.y < yBefore - 0.3, "wreck lies lower (" + String.format("%.2f -> %.2f", yBefore, wreck.y) + ")");
			check(wreck.roll > 10, "wreck is listing (roll " + wreck.roll + ")");
			rider.discard();
			// Not enough in hand: nothing is taken.
			net.minecraft.world.SimpleContainer purse = new net.minecraft.world.SimpleContainer(9);
			purse.setItem(0, new ItemStack(Items.OAK_PLANKS, 16));
			check(!Wrecks.raise(level, wreck, purse, null), "raising without the full cost fails");
			check(purse.getItem(0).getCount() == 16 && wreck.state == Ship.State.WRECK, "failed raise takes nothing");
			// Save and load keep the wreck.
			m.save();
			wreck.despawn(m);
			m.load();
			wreck = m.ships().stream().filter(s -> s.id.equals(wreckId)).findFirst().orElse(null);
			check(wreck != null, "wreck survives save/load");
			if (wreck == null) {
				finish(server);
				return;
			}
			check(wreck.state == Ship.State.WRECK && wreck.roll > 10 && wreck.hull == 0, "wreck state, roll and hull survive save/load");
			ships.clear();
			ships.add(wreck);
		}
		if (t == 160) {
			check(wreck.isSpawned(), "reloaded wreck spawned its visuals");
			net.minecraft.world.SimpleContainer purse = new net.minecraft.world.SimpleContainer(9);
			purse.setItem(0, new ItemStack(Items.OAK_PLANKS, 20));
			purse.setItem(1, new ItemStack(Items.IRON_INGOT, 4));
			check(Wrecks.raise(level, wreck, purse, null), "raising with the full cost works");
			check(purse.getItem(0).getCount() == 4 && purse.getItem(1).isEmpty(), "raise consumed exactly the cost");
			check(wreck.state == Ship.State.AFLOAT, "raised ship is afloat");
			check(Math.abs(wreck.hull - wreck.maxHull() * Config.current.wrecks.raiseHullPercent / 100.0) < 0.01, "raised ship has the configured hull");
		}
		if (t == 240) {
			check(Math.abs(wreck.y - wreck.waterY) < 0.01 && wreck.roll == 0, "raised ship is back on the surface, upright");
			m.packUp(wreck);
			ships.clear();
			startUpgradeTests(level, m);
		}
	}

	// ------------------------------------------------------------------ upgrades

	private static int upBase;
	private static Ship up;

	private static void startUpgradeTests(ServerLevel level, ShipManager m) {
		upBase = tick;
		up = new Ship(UUID.randomUUID(), ShipType.SLOOP, ShipManager.dimensionId(level), -30.5, Y + 1 - Ship.DRAFT, 10.5, 0f,
				UUID.randomUUID(), "upgrader");
		m.addShip(up);
		ships.add(up);
	}

	private static void upgradeStep(MinecraftServer server, ServerLevel level, ShipManager m, int t) {
		long now = level.getGameTime();
		if (t == 5) {
			double speed0 = ShipStats.maxSpeed(up), turn0 = ShipStats.turnRate(up);
			int reload0 = ShipStats.reloadTicks(up);
			double dmg0 = ShipStats.shipDamage(up);
			int stockParts = up.model().parts.size();
			check(up.tryInstall(Upgrades.Part.SAILS, 2, now) != null, "Sails II is refused on a stock ship");
			check(up.tryInstall(Upgrades.Part.SAILS, 1, now) == null, "Sails I installs");
			double speed1 = ShipStats.maxSpeed(up);
			check(speed1 > speed0, "Sails I raises top speed (" + String.format("%.3f -> %.3f", speed0, speed1) + ")");
			check(up.tryInstall(Upgrades.Part.SAILS, 1, now) != null, "Sails I can't be installed twice");
			check(up.tryInstall(Upgrades.Part.SAILS, 2, now) == null && ShipStats.maxSpeed(up) > speed1, "Sails II raises top speed again");
			check(up.tryInstall(Upgrades.Part.RUDDER, 1, now) == null && ShipStats.turnRate(up) > turn0, "Rudder I raises the turn rate");
			check(up.tryInstall(Upgrades.Part.CANNONS, 1, now) == null && ShipStats.reloadTicks(up) < reload0
					&& ShipStats.shipDamage(up) > dmg0, "Cannons I reload faster and hit harder");
			check(up.tryInstall(Upgrades.Part.FLIGHT, 1, now) == null && up.upgrades.flight, "Flight Core installs");
			check(up.tryInstall(Upgrades.Part.FLIGHT, 1, now) != null, "Flight Core can't be installed twice");

			double max0 = up.maxHull();
			up.hull = 30;
			check(up.tryInstall(Upgrades.Part.HULL, 1, now) == null, "Hull plating I installs");
			check(Math.abs(up.maxHull() - max0 * 1.25) < 0.001, "hull plating I raises max hull by 25% (" + up.maxHull() + ")");
			check(Math.abs(up.hull - (30 + max0 * 0.25)) < 0.001, "current hull rose by the same amount (" + up.hull + ")");

			up.lastDamagedAt = now;
			check(up.tryInstall(Upgrades.Part.HULL, 2, now) != null, "no upgrading during a battle");
			up.lastDamagedAt = Long.MIN_VALUE / 2;

			check(up.model().parts.size() > stockParts, "an upgraded ship has extra parts (" + stockParts + " -> " + up.model().parts.size() + ")");
			check(up.model().seats.size() == ShipModel.of(up.type).seats.size()
					&& up.model().cannons.size() == ShipModel.of(up.type).cannons.size(), "upgrades don't change seats or cannons");
			up.despawn(m);
		}
		if (t == 20) {
			check(up.isSpawned() && countParts(level, up) >= up.model().parts.size(), "upgraded ship respawns with its new parts");
			String before = up.upgrades.summary();
			up.damage(level, 100000);
			check(up.state == Ship.State.SINKING, "upgraded ship sinks at zero hull");
			upSummary = before;
		}
		if (t == 150) {
			check(up.state == Ship.State.WRECK && up.upgrades.summary().equals(upSummary), "a wreck keeps its upgrades (" + up.upgrades.summary() + ")");
			net.minecraft.world.SimpleContainer purse = new net.minecraft.world.SimpleContainer(9);
			purse.setItem(0, new ItemStack(Items.OAK_PLANKS, 16));
			purse.setItem(1, new ItemStack(Items.IRON_INGOT, 4));
			check(Wrecks.raise(level, up, purse, null), "upgraded wreck can be raised");
			check(up.upgrades.summary().equals(upSummary) && up.hull <= up.maxHull() && up.hull > 0, "a raised ship keeps its upgrades and a sane hull");
			ItemStack item = m.packUp(up);
			check(ShipItems.upgradesOf(item).summary().equals(upSummary), "packed upgraded ship keeps them");
			check(ShipItems.hullOf(item, ShipType.SLOOP) <= ShipStats.maxHull(ShipType.SLOOP, ShipItems.upgradesOf(item)), "packed hull respects the upgraded maximum");
			ships.clear();
			startFlightTests(level, m);
		}
	}

	// ------------------------------------------------------------------ flight

	private static int flBase;
	private static Ship fl, fl2, fl3;
	private static double flY0, flZ0;

	private static void startFlightTests(ServerLevel level, ShipManager m) {
		flBase = tick;
		fl = new Ship(UUID.randomUUID(), ShipType.SLOOP, ShipManager.dimensionId(level), -30.5, Y + 1 - Ship.DRAFT, 10.5, 0f,
				UUID.randomUUID(), "flier");
		m.addShip(fl);
		ships.add(fl);
		Config.current = new Config();
		fl.testInput = new Input(false, false, false, false, false, false, false);
	}

	private static void flightStep(MinecraftServer server, ServerLevel level, ShipManager m, int t) {
		if (t == 5) {
			check(fl.toggleFlight(level) != null && !fl.flying, "a ship without a Flight Core can't take off");
			fl.upgrades.flight = true;
			Config.current.flight.enabled = false;
			check(fl.toggleFlight(level) != null && !fl.flying, "flying can be disabled in the config");
			Config.current.flight.enabled = true;
			check(fl.toggleFlight(level) == null && fl.flying, "a Flight Core ship takes off");
			flY0 = fl.y;
			flZ0 = fl.z;
			fl.testPitch = -40f;
		}
		if (t == 70) {
			check(fl.y > flY0 + 1.5, "looking up climbs (" + String.format("%.2f -> %.2f", flY0, fl.y) + ")");
			check(Math.abs(fl.z - flZ0) < 0.01, "climbing without throttle doesn't drift");
			fl.testPitch = 0f;
			fl.testInput = new Input(true, false, false, false, false, false, false);
			flY0 = fl.y;
		}
		if (t == 100) {
			check(fl.z - flZ0 > 1.0, "flying ahead moves the ship (" + String.format("%.2f", fl.z - flZ0) + ")");
			check(Math.abs(fl.y - flY0) < 0.01, "level look keeps altitude");
			fl.testInput = new Input(false, false, false, false, false, false, false);
		}
		if (t == 140) {
			check(fl.toggleFlight(level) == null && fl.landing, "landing starts");
		}
		if (t == 260) {
			check(!fl.flying && !fl.onGround, "the ship came down onto the water and sails again");
			check(Math.abs(fl.y - (Y + 1 - Ship.DRAFT)) < 0.1, "landed at the waterline (" + String.format("%.2f", fl.y) + ")");
			m.packUp(fl);
			ships.clear();
			// stone slab above the pool to land on
			for (int sx = -41; sx <= -20; sx++) for (int sz = 0; sz <= 20; sz++) level.setBlock(new net.minecraft.core.BlockPos(sx, (int) Y + 6, sz), Blocks.STONE.defaultBlockState(), 3);
			fl2 = new Ship(UUID.randomUUID(), ShipType.SLOOP, ShipManager.dimensionId(level), -30.5, Y + 10, 10.5, 0f, UUID.randomUUID(), "lander");
			fl2.upgrades.flight = true;
			fl2.flying = true;
			fl2.landing = true;
			fl2.testInput = new Input(false, false, false, false, false, false, false);
			m.addShip(fl2);
			ships.add(fl2);
		}
		if (t == 330) {
			check(fl2.onGround && !fl2.flying, "landing on a block beaches the ship");
			check(fl2.y > Y + 6.5 && fl2.y < Y + 9, "beached on top of the slab (" + String.format("%.2f", fl2.y) + ")");
			flZ0 = fl2.z;
			fl2.testInput = new Input(true, false, false, false, false, false, false);
		}
		if (t == 345) {
			check(Math.abs(fl2.z - flZ0) < 0.01, "a beached ship doesn't sail");
			fl2.testInput = new Input(false, false, false, false, false, false, false);
			check(fl2.toggleFlight(level) == null && fl2.flying && !fl2.onGround, "a beached Flight Core ship takes off again");
		}
		if (t == 350) {
			fl2.damage(level, 100000);
			check(fl2.state == Ship.State.SINKING && fl2.falling, "zero hull in the air starts a fall");
		}
		if (t == 420) {
			check(fl2.state == Ship.State.WRECK && fl2.y > Y + 6.0, "a ship that falls onto land becomes a wreck there (" + String.format("%.2f", fl2.y) + ")");
			check(fl2.canRaise(level), "a Flight Core wreck on land can be raised");
			check(Wrecks.repairFully(level, fl2) && fl2.state == Ship.State.AFLOAT && fl2.onGround, "the raised land wreck is beached");
		}
		if (t == 430) {
			for (int sx = -41; sx <= -20; sx++) for (int sz = 0; sz <= 20; sz++) level.setBlock(new net.minecraft.core.BlockPos(sx, (int) Y + 6, sz), Blocks.AIR.defaultBlockState(), 3);
			m.packUp(fl2);
			ships.clear();
			fl3 = new Ship(UUID.randomUUID(), ShipType.SLOOP, ShipManager.dimensionId(level), -30.5, Y + 14, 10.5, 0f, UUID.randomUUID(), "faller");
			fl3.upgrades.flight = true;
			fl3.flying = true;
			fl3.testInput = new Input(false, false, false, false, false, false, false);
			m.addShip(fl3);
			ships.add(fl3);
		}
		if (t == 435) {
			fl3.damage(level, 100000);
		}
		if (t == 700) {
			check(fl3.state == Ship.State.WRECK && !fl3.flying && fl3.y < fl3.waterY - 0.5, "a ship shot down over water ends as a wreck on the bed (" + String.format("%.2f of %.2f", fl3.y, fl3.waterY) + ")");
			Config.current.flight.fuel.add(new Config.Cost("minecraft:coal", 1));
			Ship fuelShip = new Ship(UUID.randomUUID(), ShipType.SLOOP, ShipManager.dimensionId(level), 0, 0, 0, 0f, null, "f");
			fuelShip.upgrades.flight = true;
			check(fuelShip.toggleFlight(level) != null && !fuelShip.flying, "no fuel, no take-off when fuel is configured");
			ships.clear();
			startSolidTests(level, m);
		}
	}

	// ------------------------------------------------------------------ solid decks (server side)

	private static int sdBase;
	private static Ship sd;
	private static net.minecraft.world.entity.item.ItemEntity sdItem;

	private static net.minecraft.world.entity.item.ItemEntity makeItem(ServerLevel level, net.minecraft.world.phys.Vec3 at) {
		var item = new net.minecraft.world.entity.item.ItemEntity(level, at.x, at.y, at.z, new ItemStack(Items.STONE));
		item.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
		item.setNoGravity(false);
		return item;
	}

	private static void startSolidTests(ServerLevel level, ShipManager m) {
		sdBase = tick;
		Config.current = new Config();
		sd = new Ship(UUID.randomUUID(), ShipType.SLOOP, ShipManager.dimensionId(level), -30.5, Y + 1 - Ship.DRAFT, 10.5, 0f,
				UUID.randomUUID(), "deck");
		sd.testInput = new Input(false, false, false, false, false, false, false);
		m.addShip(sd);
		ships.add(sd);
	}

	private static void solidStep(MinecraftServer server, ServerLevel level, ShipManager m, int t) {
		if (t == 10) {
			check(sd.isSolid(), "a floating ship is solid");
			check(Ship.anchorType(sd.anchor()) == ShipType.SLOOP, "the first model part names the ship type for the client mod");
			check(Ship.anchorType(sd.cargoButton) == null && Ship.anchorType(null) == null, "other entities are not ship anchors");
			UUID who = UUID.randomUUID();
			check(!ClientMods.has(who), "a player is assumed to have no client mod");
			ClientMods.mark(who, "0.4.0");
			check(ClientMods.has(who) && "0.4.0".equals(ClientMods.version(who)), "the hello command records the client mod version");
			ClientMods.forget(who);
			check(!ClientMods.has(who), "leaving forgets the client mod");
			var at = sd.pose();
			net.minecraft.world.phys.Vec3 top = ShipFrame.toWorld(at, 1.0, 4.0, 1.5);
			var item = makeItem(level, top);
			item.move(net.minecraft.world.entity.MoverType.SELF, new net.minecraft.world.phys.Vec3(0, -6, 0));
			double rest = at.y() + ShipCollision.DECK_TOP;
			check(Math.abs(item.getY() - rest) < 0.02, "a dropped item lands on the deck, not in the water (" + String.format("%.3f vs %.3f", item.getY(), rest) + ")");
			net.minecraft.world.phys.Vec3 beside = ShipFrame.toWorld(at, ShipType.SLOOP.halfBeam + 2.0, ShipCollision.DECK_TOP + 0.5, 1.5);
			var out = makeItem(level, beside);
			out.move(net.minecraft.world.entity.MoverType.SELF, ShipFrame.dirToWorld(at.yaw(), new net.minecraft.world.phys.Vec3(-6, 0, 0)));
			net.minecraft.world.phys.Vec3 ol = ShipFrame.toLocal(at, out.position());
			check(ol.x > ShipType.SLOOP.halfBeam - 0.3, "an item pushed at the hull from outside is stopped by it (local x " + String.format("%.2f", ol.x) + ")");

			sdItem = makeItem(level, ShipFrame.toWorld(at, 1.0, ShipCollision.DECK_TOP + 0.05, 1.5));
			level.addFreshEntity(sdItem);
			flY0 = sd.x;
			flZ0 = sd.z;
			sd.testInput = new Input(true, false, false, false, false, false, false);
		}
		if (t == 30) {
			sd.testInput = new Input(true, false, false, true, false, false, false);
		}
		if (t == 60) {
			sd.testInput = new Input(false, false, false, false, false, false, false);
		}
		if (t == 70) {
			double moved = Math.hypot(sd.x - flY0, sd.z - flZ0);
			check(moved > 1.0, "the ship sailed and turned (" + String.format("%.1f blocks, yaw %.0f", moved, sd.yaw) + ")");
			net.minecraft.world.phys.Vec3 l = ShipFrame.toLocal(sd.pose(), sdItem.position());
			check(!sdItem.isRemoved() && Math.abs(l.x - 1.0) < 0.5 && Math.abs(l.z - 1.5) < 0.5 && Math.abs(l.y - ShipCollision.DECK_TOP) < 0.2,
					"an item on the deck is carried along (local " + String.format("%.2f, %.2f, %.2f", l.x, l.y, l.z) + ")");
			sdItem.discard();
			sd.damage(level, 100000);
		}
		if (t == 250) {
			check(sd.state == Ship.State.WRECK && !sd.isSolid(), "a wreck is not solid");
			var at = sd.pose();
			var item = makeItem(level, ShipFrame.toWorld(at, 1.0, 3.0, 1.5));
			item.move(net.minecraft.world.entity.MoverType.SELF, new net.minecraft.world.phys.Vec3(0, -4, 0));
			check(item.getY() < at.y() + ShipCollision.DECK_TOP - 0.3, "things fall through a wreck (" + String.format("%.2f vs deck %.2f", item.getY(), at.y() + ShipCollision.DECK_TOP) + ")");
			m.packUp(sd);
			ships.clear();
			finish(server);
		}
	}

	private static String upSummary;

	private static void finish(MinecraftServer server) {
		{
			if (failures.isEmpty()) {
				TidesOfWar.LOGGER.info("TIDESOFWAR SELFTEST PASS");
			} else {
				TidesOfWar.LOGGER.error("TIDESOFWAR SELFTEST FAIL: {}", failures);
			}
			ships.clear();
			server.halt(false);
		}
	}

	/** Checks that data from before the rename (pirateships) is still understood. */
	private static void checkLegacy(ServerLevel level, ShipManager m) {
		// Old item key
		ItemStack old = new ItemStack(Items.DARK_OAK_BOAT);
		net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
		tag.putString(ShipItems.LEGACY_TYPE_KEY, "brigantine");
		old.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
		check(ShipItems.typeOf(old) == ShipType.BRIGANTINE, "old pirateships_type item still recognised");
		// Old entity tag
		net.minecraft.world.entity.Interaction part = new net.minecraft.world.entity.Interaction(
				Compat.interaction(), level);
		part.addTag(Ship.LEGACY_PART_TAG);
		check(Ship.isPart(part), "old pirateships_part tag still recognised as a leftover");
		// Old data file
		try {
			java.nio.file.Path legacy = m.legacyFile();
			java.nio.file.Path current = m.file();
			java.nio.file.Files.deleteIfExists(current);
			java.nio.file.Files.createDirectories(legacy.getParent());
			String id = java.util.UUID.randomUUID().toString();
			java.nio.file.Files.writeString(legacy, "{\"version\":1,\"ships\":[{\"id\":\"" + id
					+ "\",\"type\":\"sloop\",\"dimension\":\"minecraft:overworld\",\"x\":0.5,\"y\":64.0,\"z\":0.5,"
					+ "\"yaw\":0.0,\"ownerName\":\"Old\",\"cargo\":[]}]}");
			m.load();
			check(m.ships().size() == 1 && m.ships().iterator().next().id.toString().equals(id), "old pirateships/ships.json loaded");
			Ship oldShip = m.ships().iterator().next();
			check(oldShip.state == Ship.State.AFLOAT && oldShip.hull == oldShip.maxHull(), "old ship loads afloat with a full hull");
			check(oldShip.upgrades.isStock() && !oldShip.flying, "old ship loads with no upgrades");
			check(java.nio.file.Files.exists(current), "old ships migrated to tidesofwar/ships.json");
			java.nio.file.Files.deleteIfExists(legacy);
			java.nio.file.Files.deleteIfExists(current);
			m.load();
		} catch (java.io.IOException e) {
			check(false, "legacy data check threw " + e);
		}
	}

	/** Upgrade items, saving and the packed item (phase 4 data). */
	private static void checkCollisionMath() {
		for (ShipType type : ShipType.values()) {
			var boxes = ShipCollision.of(type);
			double stern = -type.length / 2.0;
			double w = ShipModel.halfWidthAt(type, type.length / 2);
			double zMid = stern + type.length * 0.5 + 0.5;
			check(!boxes.isEmpty(), type + ": has collision boxes (" + boxes.size() + ")");
			check(Math.abs(ShipCollision.topAt(boxes, 0, zMid + 3.7) - ShipCollision.DECK_TOP) < 1e-6
							|| Math.abs(ShipCollision.topAt(boxes, 0.5, stern + type.length * 0.45) - ShipCollision.DECK_TOP) < 1e-6,
					type + ": the main deck is at deck height");
			check(Math.abs(ShipCollision.topAt(boxes, w - 0.2, zMid) - ShipCollision.RAIL_TOP) < 1e-6, type + ": the rail reaches rail height");
			check(Math.abs(ShipCollision.topAt(boxes, 0, stern + 1.0) - ShipCollision.CASTLE_TOP) < 1e-6, type + ": the stern deck is raised");
			float[] mast = type.masts[0];
			check(Math.abs(ShipCollision.topAt(boxes, 0, stern + mast[0] * type.length) - mast[1]) < 1e-6, type + ": the mast is solid up to its top");
		}
		// round trips
		for (float yaw : new float[]{0f, 90f, 37f, -123f}) {
			ShipFrame.Pose p = new ShipFrame.Pose(10.5, 100, -4.25, yaw);
			net.minecraft.world.phys.Vec3 w = ShipFrame.toWorld(p, 1.25, 0.5, -3.0);
			net.minecraft.world.phys.Vec3 l = ShipFrame.toLocal(p, w);
			check(Math.abs(l.x - 1.25) < 1e-9 && Math.abs(l.y - 0.5) < 1e-9 && Math.abs(l.z + 3.0) < 1e-9, "local/world round trip at yaw " + yaw);
			net.minecraft.world.phys.Vec3 d = new net.minecraft.world.phys.Vec3(0.3, -0.1, 0.7);
			net.minecraft.world.phys.Vec3 back = ShipFrame.dirToWorld(yaw, ShipFrame.dirToLocal(yaw, d));
			check(back.distanceTo(d) < 1e-9, "direction round trip at yaw " + yaw);
		}
		// walking on a sloop
		var sloop = ShipCollision.of(ShipType.SLOOP);
		double halfW = ShipModel.halfWidthAt(ShipType.SLOOP, 5);
		for (float yaw : new float[]{0f, 37f, 90f}) {
			ShipFrame.Pose p = new ShipFrame.Pose(5, 100, 5, yaw);
			net.minecraft.world.phys.Vec3 feet = ShipFrame.toWorld(p, 0, ShipCollision.DECK_TOP + 0.5, 0.5);
			net.minecraft.world.phys.AABB body = new net.minecraft.world.phys.AABB(feet.x - 0.3, feet.y, feet.z - 0.3, feet.x + 0.3, feet.y + 1.8, feet.z + 0.3);
			net.minecraft.world.phys.Vec3 drop = ShipFrame.clip(sloop, p, body, new net.minecraft.world.phys.Vec3(0, -2, 0), 0);
			check(Math.abs(drop.y + 0.5) < 1e-6, "a falling body lands on the deck at yaw " + yaw + " (" + String.format("%.3f", drop.y) + ")");
			// stand on the deck, shove toward port (+x local): stopped by the rail
			net.minecraft.world.phys.Vec3 standing = ShipFrame.toWorld(p, 0, ShipCollision.DECK_TOP, 0.5);
			net.minecraft.world.phys.AABB onDeck = new net.minecraft.world.phys.AABB(standing.x - 0.3, standing.y, standing.z - 0.3, standing.x + 0.3, standing.y + 1.8, standing.z + 0.3);
			net.minecraft.world.phys.Vec3 push = ShipFrame.dirToWorld(yaw, new net.minecraft.world.phys.Vec3(10, 0, 0));
			net.minecraft.world.phys.Vec3 got = ShipFrame.dirToLocal(yaw, ShipFrame.clip(sloop, p, onDeck, push, 0));
			double expect = halfW - 0.4 - 0.3;
			check(Math.abs(got.x - expect) < 1e-6 && Math.abs(got.z) < 1e-6, "the rail stops sideways movement at yaw " + yaw + " (" + String.format("%.3f vs %.3f", got.x, expect) + ")");
		}
		// stepping up to the stern deck
		ShipFrame.Pose p0 = new ShipFrame.Pose(0, 100, 0, 0f);
		double zc = -ShipType.SLOOP.length / 2.0 + ShipCollision.castleLength(ShipType.SLOOP);
		net.minecraft.world.phys.Vec3 f = ShipFrame.toWorld(p0, 0, ShipCollision.DECK_TOP, zc + 1.0 + 0.3 + 0.01);
		net.minecraft.world.phys.AABB walker = new net.minecraft.world.phys.AABB(f.x - 0.3, f.y, f.z - 0.3, f.x + 0.3, f.y + 1.8, f.z + 0.3);
		net.minecraft.world.phys.Vec3 stepped = ShipFrame.clip(sloop, p0, walker, new net.minecraft.world.phys.Vec3(0, -0.08, -0.3), 0.6);
		check(stepped.y > 0.45 && stepped.z < -0.25, "walking into the first step climbs it (" + String.format("%.2f, %.2f", stepped.y, stepped.z) + ")");
		net.minecraft.world.phys.Vec3 flat = ShipFrame.clip(sloop, p0, walker, new net.minecraft.world.phys.Vec3(0, -0.08, -0.3), 0);
		check(Math.abs(flat.z) < 0.02, "without step height the step blocks (" + String.format("%.2f", flat.z) + ")");
	}

	private static void checkUpgradeData(ShipManager m) {
		for (Upgrades.Part part : Upgrades.Part.values()) {
			ItemStack it = UpgradeItems.create(part, part.maxTier);
			check(UpgradeItems.partOf(it) == part && UpgradeItems.tierOf(it) == part.maxTier, "upgrade item round trip: " + part.id);
			check(it.is(part.baseItem()), "upgrade item for " + part.id + " is built on " + part.baseItem());
		}
		check(UpgradeItems.partOf(new ItemStack(Items.PAPER)) == null, "plain paper is not an upgrade");
		check(UpgradeItems.partOf(ShipItems.create(ShipType.SLOOP)) == null, "a ship item is not an upgrade");

		Ship s = new Ship(UUID.randomUUID(), ShipType.BRIGANTINE, "minecraft:overworld", 0.5, 64, 0.5, 0f, null, "up");
		s.upgrades.set(Upgrades.Part.HULL, 2);
		s.upgrades.set(Upgrades.Part.SAILS, 3);
		s.upgrades.set(Upgrades.Part.FLIGHT, 1);
		s.flying = true;
		m.addShip(s);
		m.load();
		Ship r = m.ships().stream().filter(x -> x.id.equals(s.id)).findFirst().orElse(null);
		check(r != null && r.upgrades.sameAs(s.upgrades) && r.flying && !r.onGround, "upgrades and flight state survive save/load (" + (r == null ? "?" : r.upgrades.summary()) + ")");
		if (r != null) {
			ItemStack item = m.packUp(r);
			check(ShipItems.upgradesOf(item).sameAs(s.upgrades), "packed item keeps the upgrades");
			check(ShipItems.upgradesOf(ShipItems.create(ShipType.SLOOP)).isStock(), "fresh ship item has no upgrades");
		}
		for (Ship left : new ArrayList<>(m.ships())) m.packUp(left);
	}

	private static int countParts(ServerLevel level, Ship ship) {
		int n = 0;
		ShipManager m = ShipManager.get();
		for (Entity e : level.getAllEntities()) {
			if (!e.isRemoved() && m.shipOfPart(e) == ship) n++;
		}
		return n;
	}
}
