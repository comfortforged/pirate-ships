package io.github.comfortforged.tidesofwar.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Keeps track of every ship on the server, saves them, and spawns/despawns their visuals. */
public final class ShipManager {
	private static ShipManager instance;

	private final MinecraftServer server;
	private final Map<UUID, Ship> ships = new LinkedHashMap<>();
	/** Live part entity UUID -> ship. Anything tagged as a part but not in here is a stale leftover. */
	private final Map<UUID, Ship> partOwners = new HashMap<>();
	private final Set<Entity> staleParts = new HashSet<>();
	private boolean dirty;
	private int ticksSinceSave;

	private ShipManager(MinecraftServer server) {
		this.server = server;
	}

	public static ShipManager get() {
		return instance;
	}

	public static void start(MinecraftServer server) {
		instance = new ShipManager(server);
		instance.load();
	}

	public static void stop() {
		if (instance == null) return;
		for (Ship s : instance.ships.values()) s.despawn(instance);
		instance.save();
		instance = null;
	}

	public Collection<Ship> ships() {
		return ships.values();
	}

	// ------------------------------------------------------------------ part bookkeeping

	void registerPart(Entity e, Ship ship) {
		partOwners.put(e.getUUID(), ship);
	}

	void unregisterPart(Entity e) {
		partOwners.remove(e.getUUID());
	}

	public Ship shipOfPart(Entity e) {
		return partOwners.get(e.getUUID());
	}

	/** Called whenever an entity loads; old saved copies of ship parts get cleaned up. */
	public void onEntityLoad(Entity e) {
		if (Ship.isPart(e) && !partOwners.containsKey(e.getUUID())) {
			staleParts.add(e);
		}
	}

	// ------------------------------------------------------------------ ticking

	public void tickLevel(ServerLevel level) {
		if (!staleParts.isEmpty()) {
			for (Entity e : staleParts) {
				if (!e.isRemoved()) {
					e.ejectPassengers();
					e.discard();
				}
			}
			staleParts.clear();
		}
		String dim = dimensionId(level);
		for (Ship ship : ships.values()) {
			if (!ship.dimension.equals(dim)) continue;
			boolean loaded = level.isPositionEntityTicking(ship.blockPos());
			if (loaded) {
				if (!ship.isSpawned()) ship.spawn(level, this);
				double ox = ship.x, oz = ship.z;
				float oyaw = ship.yaw;
				ship.tick(level);
				if (ship.x != ox || ship.z != oz || ship.yaw != oyaw) dirty = true;
			} else if (ship.isSpawned()) {
				ship.despawn(this);
			}
		}
		if (level == server.overworld()) {
			ticksSinceSave++;
			if (dirty && ticksSinceSave > 20 * 60) {
				save();
			}
		}
	}

	public static String dimensionId(ServerLevel level) {
		return level.dimension().identifier().toString();
	}

	// ------------------------------------------------------------------ launching and packing

	/**
	 * Tries to launch a ship from an item the player is holding. Returns true if a ship was launched
	 * (the caller then removes the item).
	 */
	public boolean launchFromItem(ServerPlayer player, ServerLevel level, ItemStack stack, ShipType type) {
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getViewVector(1f).scale(12.0));
		BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, player));
		if (hit.getType() != HitResult.Type.BLOCK || !level.getFluidState(hit.getBlockPos()).is(FluidTags.WATER)) {
			player.sendOverlayMessage(Component.literal("Aim at open water to launch your ship.").withStyle(ChatFormatting.RED));
			return false;
		}
		BlockPos water = hit.getBlockPos();
		while (level.getFluidState(water.above()).is(FluidTags.WATER)) water = water.above();
		Vec3 at = hit.getLocation();
		double sy = water.getY() + 1 - Ship.DRAFT;
		float yaw = player.getYRot();
		// Put the ship's centre a little further out so the stern isn't on top of the player.
		double r = Math.toRadians(yaw);
		double push = type.length * 0.25;
		double sx = at.x - Math.sin(r) * push;
		double sz = at.z + Math.cos(r) * push;

		Ship ship = new Ship(UUID.randomUUID(), type, dimensionId(level), sx, sy, sz, yaw, player.getUUID(),
				player.getScoreboardName());
		if (!ship.fits(level, sx, sy, sz, yaw)) {
			// Try straight under the crosshair before giving up.
			ship.x = at.x;
			ship.z = at.z;
			if (!ship.fits(level, at.x, sy, at.z, yaw)) {
				player.sendOverlayMessage(Component.literal("Not enough open water here for a " + type.displayName + ".")
						.withStyle(ChatFormatting.RED));
				return false;
			}
		}
		List<ItemStack> cargo = ShipItems.readCargo(stack);
		for (int i = 0; i < cargo.size() && i < ship.cargo.getContainerSize(); i++) {
			ship.cargo.setItem(i, cargo.get(i));
		}
		addShip(ship);
		player.sendSystemMessage(Component.literal("Your " + type.displayName + " is launched! Right-click the tiller at the back to take the helm.")
				.withStyle(ChatFormatting.GOLD));
		return true;
	}

	/** Adds a ship to the world (used for launching and by the self-test). */
	public void addShip(Ship ship) {
		ships.put(ship.id, ship);
		save();
	}

	/** Removes the ship from the world and returns it as an item, cargo included. */
	public ItemStack packUp(Ship ship) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (p.containerMenu instanceof ChestMenu cm && cm.getContainer() == ship.cargo) p.closeContainer();
		}
		ship.despawn(this);
		ships.remove(ship.id);
		ItemStack stack = ShipItems.create(ship.type);
		List<ItemStack> items = new ArrayList<>();
		for (int i = 0; i < ship.cargo.getContainerSize(); i++) items.add(ship.cargo.getItem(i).copy());
		ShipItems.storeCargo(stack, items);
		save();
		return stack;
	}

	public void openCargo(ServerPlayer player, Ship ship) {
		Component title = Component.literal(ship.type.displayName + " Cargo Hold");
		if (ship.type.cargoRows >= 6) {
			player.openMenu(new SimpleMenuProvider((id, inv, p) -> ChestMenu.sixRows(id, inv, ship.cargo), title));
		} else {
			player.openMenu(new SimpleMenuProvider((id, inv, p) -> ChestMenu.threeRows(id, inv, ship.cargo), title));
		}
		dirty = true;
	}

	public void markDirty() {
		dirty = true;
	}

	// ------------------------------------------------------------------ saving

	public Path file() {
		return server.getWorldPath(LevelResource.ROOT).resolve("tidesofwar").resolve("ships.json");
	}

	/** Where ships were saved before the rename to Tides of War. */
	public Path legacyFile() {
		return server.getWorldPath(LevelResource.ROOT).resolve("pirateships").resolve("ships.json");
	}

	private DynamicOps<JsonElement> ops() {
		return server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
	}

	public void save() {
		dirty = false;
		ticksSinceSave = 0;
		JsonObject root = new JsonObject();
		root.addProperty("version", 1);
		JsonArray arr = new JsonArray();
		DynamicOps<JsonElement> ops = ops();
		for (Ship s : ships.values()) {
			JsonObject o = new JsonObject();
			o.addProperty("id", s.id.toString());
			o.addProperty("type", s.type.id());
			o.addProperty("dimension", s.dimension);
			o.addProperty("x", s.x);
			o.addProperty("y", s.y);
			o.addProperty("z", s.z);
			o.addProperty("yaw", s.yaw);
			if (s.owner != null) o.addProperty("owner", s.owner.toString());
			if (s.ownerName != null) o.addProperty("ownerName", s.ownerName);
			JsonArray cargo = new JsonArray();
			for (int i = 0; i < s.cargo.getContainerSize(); i++) {
				ItemStack st = s.cargo.getItem(i);
				if (st.isEmpty()) continue;
				var res = ItemStack.CODEC.encodeStart(ops, st);
				int slot = i;
				res.resultOrPartial(err -> TidesOfWar.LOGGER.error("Couldn't save cargo item: {}", err)).ifPresent(json -> {
					JsonObject e = new JsonObject();
					e.addProperty("slot", slot);
					e.add("item", json);
					cargo.add(e);
				});
			}
			o.add("cargo", cargo);
			arr.add(o);
		}
		root.add("ships", arr);
		try {
			Path f = file();
			Files.createDirectories(f.getParent());
			Path tmp = f.resolveSibling("ships.json.tmp");
			Gson gson = new GsonBuilder().setPrettyPrinting().create();
			Files.writeString(tmp, gson.toJson(root), StandardCharsets.UTF_8);
			Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			TidesOfWar.LOGGER.error("Couldn't save ships", e);
		}
	}

	public void load() {
		ships.clear();
		Path f = file();
		boolean migrated = false;
		if (!Files.exists(f)) {
			Path old = legacyFile();
			if (!Files.exists(old)) return;
			f = old;
			migrated = true;
		}
		try {
			JsonObject root = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
			DynamicOps<JsonElement> ops = ops();
			for (JsonElement el : root.getAsJsonArray("ships")) {
				JsonObject o = el.getAsJsonObject();
				ShipType type = ShipType.byId(o.get("type").getAsString());
				if (type == null) continue;
				UUID owner = o.has("owner") ? UUID.fromString(o.get("owner").getAsString()) : null;
				String ownerName = o.has("ownerName") ? o.get("ownerName").getAsString() : "?";
				Ship s = new Ship(UUID.fromString(o.get("id").getAsString()), type, o.get("dimension").getAsString(),
						o.get("x").getAsDouble(), o.get("y").getAsDouble(), o.get("z").getAsDouble(),
						o.get("yaw").getAsFloat(), owner, ownerName);
				if (o.has("cargo")) {
					for (JsonElement ce : o.getAsJsonArray("cargo")) {
						JsonObject c = ce.getAsJsonObject();
						int slot = c.get("slot").getAsInt();
						ItemStack.CODEC.parse(ops, c.get("item"))
								.resultOrPartial(err -> TidesOfWar.LOGGER.error("Couldn't load cargo item: {}", err))
								.ifPresent(st -> {
									if (slot >= 0 && slot < s.cargo.getContainerSize()) s.cargo.setItem(slot, st);
								});
					}
				}
				ships.put(s.id, s);
			}
			TidesOfWar.LOGGER.info("Loaded {} ship(s)", ships.size());
			if (migrated) {
				save();
				TidesOfWar.LOGGER.info("Migrated ships from {} to {}", f, file());
			}
		} catch (Exception e) {
			TidesOfWar.LOGGER.error("Couldn't load ships from " + f, e);
		}
	}
}
