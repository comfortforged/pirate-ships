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
		for (String r : new String[]{"pirate_sloop", "pirate_brigantine", "pirate_galleon"}) {
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
			m.save();
			int before = m.ships().size();
			m.load();
			check(m.ships().size() == before, "save/load keeps all " + before + " ships");
			Ship reloaded = m.ships().stream().filter(s -> s.id.equals(g.id)).findFirst().orElse(null);
			check(reloaded != null && reloaded.cargo.getItem(4).getCount() == 37, "cargo survives save/load");
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
			check(java.nio.file.Files.exists(current), "old ships migrated to tidesofwar/ships.json");
			java.nio.file.Files.deleteIfExists(legacy);
			java.nio.file.Files.deleteIfExists(current);
			m.load();
		} catch (java.io.IOException e) {
			check(false, "legacy data check threw " + e);
		}
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
