package io.github.comfortforged.tidesofwar.core;

import com.mojang.math.Transformation;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One ship in the world. The ship itself is just data (position, heading, speed, cargo);
 * while players are nearby it shows itself using display entities that follow it every tick.
 */
public class Ship {
	public static final String PART_TAG = "tidesofwar_part";
	/** Entity tag used before the Tides of War rename; such entities are leftovers and get cleaned up. */
	public static final String LEGACY_PART_TAG = "pirateships_part";

	public static boolean isPart(Entity e) {
		var tags = e.entityTags();
		return tags.contains(PART_TAG) || tags.contains(LEGACY_PART_TAG);
	}
	/** How far below the top of the water block the ship's waterline sits. */
	public static final double DRAFT = 0.45;

	public final UUID id;
	public final ShipType type;
	public final String dimension;
	public double x, y, z;
	public float yaw;
	public double speed;
	public UUID owner;
	public String ownerName;
	public final SimpleContainer cargo;

	public enum State { AFLOAT, SINKING, WRECK }

	public Upgrades upgrades = new Upgrades();
	/** Flight (needs a flight core): in the air, or resting on land. */
	public boolean flying;
	public boolean onGround;

	public double hull;
	public State state = State.AFLOAT;
	public int stateTicks;
	public long wreckedAtGameTime;
	/** Game time of the last hit, for the combat lock. */
	public long lastDamagedAt = Long.MIN_VALUE / 2;
	/** Tilt in degrees while sinking or wrecked. */
	public float roll;
	/** Height of the ship once it has sunk, and the water level it sank from. */
	public double wreckY;
	public double waterY;

	/** Cannons waiting to fire (a broadside goes off one gun after another). */
	final List<Cannons.Queued> queue = new ArrayList<>();
	/** Game time at which each side (0 = port, 1 = starboard) has finished reloading. */
	private final long[] reloadUntil = new long[2];
	/** Space was held last tick: captain, port gunner, starboard gunner. */
	private final boolean[] lastJump = new boolean[3];

	/** Fake look pitch for the self-test (used when testInput is set). */
	public float testPitch;
	/** Coming down to land (water or ground). Not saved: a ship that reloads in the air just carries on flying. */
	boolean landing;
	/** Sinking out of the sky: falling until it hits water or ground. */
	boolean falling;
	private double fallSpeed;
	private int flightTicks;
	private boolean lastSprint;

	/** Ticks left of the rising animation after a wreck was raised (not saved). */
	int rising;
	private float appliedRoll = Float.NaN;
	private long lastRollSent = Long.MIN_VALUE;

	/** Used only by the automated self-test to drive the ship without a player. */
	public Input testInput;

	// Live entities, present only while the ship is "spawned" (its chunk is loaded).
	private final List<Display.BlockDisplay> parts = new ArrayList<>();
	final List<Display.ItemDisplay> seats = new ArrayList<>();
	final List<Interaction> seatButtons = new ArrayList<>();
	Interaction tillerButton;
	Interaction cargoButton;
	private boolean spawned;
	private int age;
	private float lastSentBob = Float.NaN;

	public Ship(UUID id, ShipType type, String dimension, double x, double y, double z, float yaw, UUID owner, String ownerName) {
		this.id = id;
		this.type = type;
		this.dimension = dimension;
		this.x = x;
		this.y = y;
		this.z = z;
		this.yaw = yaw;
		this.owner = owner;
		this.ownerName = ownerName;
		this.cargo = new SimpleContainer(type.cargoSlots());
		this.hull = maxHull();
	}

	public double maxHull() {
		return ShipStats.maxHull(this);
	}

	public int hullPercent() {
		return (int) Math.round(100.0 * hull / maxHull());
	}

	/** True shortly after the ship took damage; such a ship can't be packed up, recalled or repaired. */
	public boolean inCombat(long gameTime) {
		return gameTime - lastDamagedAt < Config.current.hull.combatLockSeconds * 20L;
	}

	private static int sideIndex(int side) {
		return side == ShipModel.PORT ? 0 : 1;
	}

	public long reloadTicksLeft(long now, int side) {
		return Math.max(0, reloadUntil[sideIndex(side)] - now);
	}

	void setReload(int side, long until) {
		reloadUntil[sideIndex(side)] = until;
	}

	/** Takes hull damage from a hit. Does nothing unless the ship is afloat. Sinks at zero hull. */
	public void damage(ServerLevel level, double amount) {
		if (state != State.AFLOAT) return;
		hull = Math.max(0, hull - amount);
		lastDamagedAt = level.getGameTime();
		ShipManager m = ShipManager.get();
		if (m != null) m.markDirty();
		if (hull <= 0) {
			startSinking(level);
		} else {
			for (Display.ItemDisplay seat : seats) {
				for (Entity e : seat.getPassengers()) {
					if (e instanceof ServerPlayer sp) {
						sp.sendOverlayMessage(Component.literal("Hit! Hull " + hullPercent() + "%").withStyle(ChatFormatting.RED));
					}
				}
			}
		}
	}

	/**
	 * Installs the next tier of an upgrade. Returns null on success, otherwise the reason it can't be done.
	 * (The caller checks who may do it and takes the item.)
	 */
	public String tryInstall(Upgrades.Part part, int tier, long gameTime) {
		if (state != State.AFLOAT) return "Only a ship afloat can be upgraded.";
		if (inCombat(gameTime)) {
			long left = lastDamagedAt + Config.current.hull.combatLockSeconds * 20L - gameTime;
			return "Can't upgrade during battle (" + (left + 19) / 20 + "s).";
		}
		int have = upgrades.get(part);
		if (have >= part.maxTier || tier <= have) return part.nameOfTier(Math.max(1, Math.min(have, part.maxTier))) + " is already installed.";
		if (tier > have + 1) return "This ship needs " + part.nameOfTier(have + 1) + " first.";
		double oldMax = maxHull();
		upgrades.set(part, tier);
		if (part == Upgrades.Part.HULL) hull += maxHull() - oldMax;
		hull = Math.min(hull, maxHull());
		ShipManager m = ShipManager.get();
		if (m != null) m.markDirty();
		return null;
	}

	/** Everyone off, then down she goes. */
	public void startSinking(ServerLevel level) {
		state = State.SINKING;
		stateTicks = 0;
		speed = 0;
		queue.clear();
		boolean air = flying;
		if (air) {
			// Riders drift down gently instead of dying with the ship.
			for (Display.ItemDisplay seat : seats) {
				for (Entity e : seat.getPassengers()) {
					if (e instanceof net.minecraft.world.entity.LivingEntity le) {
						le.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOW_FALLING, 300, 0));
					}
				}
			}
		}
		for (Display.ItemDisplay seat : seats) seat.ejectPassengers();
		waterY = y;
		level.playSound(null, x, y, z, net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE, net.minecraft.sounds.SoundSource.BLOCKS, 1.5f, 0.5f);
		if (air) {
			falling = true;
			fallSpeed = 0;
			wreckY = y;
		} else if (onGround) {
			onGround = false;
			wreckY = y;
		} else {
			wreckY = computeWreckY(level);
		}
		ShipManager m = ShipManager.get();
		if (m != null) m.markDirty();
	}

	/** Where the wreck comes to rest: the seabed under the ship, at most as high as the waterline. */
	private double computeWreckY(ServerLevel level) {
		double best = Double.NEGATIVE_INFINITY;
		int topY = (int) Math.floor(y);
		for (float[] p : model().footprint) {
			Vec3 w = toWorld(x, y, z, yaw, p[0], 0, p[1]);
			for (int dy = 0; dy < 40; dy++) {
				BlockPos bp = BlockPos.containing(w.x, topY - dy, w.z);
				BlockState st = level.getBlockState(bp);
				if (!st.getCollisionShape(level, bp).isEmpty()) {
					best = Math.max(best, bp.getY() + st.getCollisionShape(level, bp).max(net.minecraft.core.Direction.Axis.Y));
					break;
				}
			}
		}
		return best == Double.NEGATIVE_INFINITY ? waterY - 6 : Math.min(waterY, best + 1.0);
	}

	/** True if open water above the wreck lets it rise again. */
	public boolean canRaise(ServerLevel level) {
		if (fits(level, x, waterY, z, yaw)) return true;
		// A wreck lying on dry land can only be lifted by a ship with a Flight Core.
		return upgrades.flight && clearAt(level, x, y, z, yaw);
	}

	/** Like {@link #refloat(double)}, but a wreck on dry land comes up beached. */
	public void refloat(ServerLevel level, double hullFraction) {
		boolean dry = !fits(level, x, waterY, z, yaw);
		refloat(hullFraction);
		onGround = dry;
		flying = false;
		landing = false;
	}

	/** Starts the rise to the surface. The caller has already checked the water and taken any payment. */
	public void refloat(double hullFraction) {
		state = State.AFLOAT;
		hull = Math.max(1, maxHull() * hullFraction);
		rising = 60;
		stateTicks = 0;
		speed = 0;
		lastDamagedAt = Long.MIN_VALUE / 2;
		ShipManager m = ShipManager.get();
		if (m != null) m.markDirty();
	}

	/**
	 * Where along the segment p0..p1 (0..1) the hull is first hit, or -1 for a miss.
	 * The hull is treated as a box around the ship, a little larger than the model.
	 */
	public double hitFraction(Vec3 p0, Vec3 p1) {
		double len = p0.distanceTo(p1);
		int steps = Math.max(1, (int) Math.ceil(len / 0.4));
		double r = Math.toRadians(yaw);
		double cos = Math.cos(r), sin = Math.sin(r);
		double halfLen = type.length / 2.0 + 0.3, halfBeam = type.halfBeam + 0.3;
		for (int i = 0; i <= steps; i++) {
			double t = (double) i / steps;
			double px = p0.x + (p1.x - p0.x) * t;
			double py = p0.y + (p1.y - p0.y) * t;
			double pz = p0.z + (p1.z - p0.z) * t;
			double dx = px - x, dz = pz - z;
			double lx = dx * cos + dz * sin;
			double lz = -dx * sin + dz * cos;
			if (Math.abs(lx) <= halfBeam && Math.abs(lz) <= halfLen && py >= y - 0.9 && py <= y + 1.8) return t;
		}
		return -1;
	}

	public ShipModel model() {
		return ShipModel.of(type, new ShipModel.Look(upgrades.hull, upgrades.sails, upgrades.flight));
	}

	public boolean isSpawned() {
		return spawned;
	}

	public BlockPos blockPos() {
		return BlockPos.containing(x, y, z);
	}

	// ------------------------------------------------------------------ geometry

	/** Turns a point in ship coordinates into world coordinates for the given heading. */
	public static Vec3 toWorld(double ox, double oy, double oz, float yaw, double lx, double ly, double lz) {
		double r = Math.toRadians(yaw);
		double cos = Math.cos(r), sin = Math.sin(r);
		return new Vec3(ox + lx * cos - lz * sin, oy + ly, oz + lx * sin + lz * cos);
	}

	public Vec3 toWorld(Vector3f local) {
		return toWorld(x, y, z, yaw, local.x, local.y, local.z);
	}

	/** True if the whole hull would be floating in open water at this position and heading. */
	public boolean fits(ServerLevel level, double px, double py, double pz, float pyaw) {
		int wy = (int) Math.floor(py);
		for (float[] p : model().footprint) {
			Vec3 w = toWorld(px, py, pz, pyaw, p[0], 0, p[1]);
			BlockPos bp = BlockPos.containing(w.x, wy, w.z);
			if (!level.getFluidState(bp).is(FluidTags.WATER)) return false;
			for (int up = 1; up <= 2; up++) {
				BlockPos above = bp.above(up);
				BlockState st = level.getBlockState(above);
				if (!st.getCollisionShape(level, above).isEmpty()) return false;
			}
		}
		return true;
	}

	// ------------------------------------------------------------------ spawning

	public void spawn(ServerLevel level, ShipManager manager) {
		if (spawned) return;
		spawned = true;
		ShipModel m = model();
		for (ShipModel.Part p : m.parts) {
			Display.BlockDisplay d = new Display.BlockDisplay(Compat.blockDisplay(), level);
			Compat.setBlockState(d, p.block());
			Compat.setTransformation(d, new Transformation(
					new Vector3f(p.x(), p.y(), p.z()),
					new Quaternionf(),
					new Vector3f(p.sx(), p.sy(), p.sz()),
					new Quaternionf()));
			Compat.setPosRotInterpolationDuration(d, 2);
			Compat.setViewRange(d, 3.0f);
			parts.add(d);
			add(level, manager, d);
		}
		for (int i = 0; i < m.seats.size(); i++) {
			Display.ItemDisplay seat = new Display.ItemDisplay(Compat.itemDisplay(), level);
			Compat.setPosRotInterpolationDuration(seat, 2);
			seats.add(seat);
			add(level, manager, seat);
			if (i > 0) {
				Interaction b = button(level, 0.9f, 0.8f);
				seatButtons.add(b);
				add(level, manager, b);
			}
		}
		tillerButton = button(level, 1.3f, 1.7f);
		add(level, manager, tillerButton);
		cargoButton = button(level, 1.15f, 1.15f);
		add(level, manager, cargoButton);
		updateEntities(true);
	}

	private static Interaction button(ServerLevel level, float w, float h) {
		Interaction b = new Interaction(Compat.interaction(), level);
		Compat.setWidth(b, w);
		Compat.setHeight(b, h);
		Compat.setResponse(b, true);
		return b;
	}

	private void add(ServerLevel level, ShipManager manager, Entity e) {
		e.addTag(PART_TAG);
		e.setSilent(true);
		e.setNoGravity(true);
		e.snapTo(x, y, z, yaw, 0f);
		manager.registerPart(e, this);
		level.addFreshEntity(e);
	}

	public void despawn(ShipManager manager) {
		if (!spawned) return;
		spawned = false;
		for (Display.ItemDisplay s : seats) s.ejectPassengers();
		List<Entity> all = new ArrayList<>(parts);
		all.addAll(seats);
		all.addAll(seatButtons);
		if (tillerButton != null) all.add(tillerButton);
		if (cargoButton != null) all.add(cargoButton);
		for (Entity e : all) {
			manager.unregisterPart(e);
			e.discard();
		}
		parts.clear();
		seats.clear();
		seatButtons.clear();
		tillerButton = null;
		cargoButton = null;
		lastSentBob = Float.NaN;
		appliedRoll = Float.NaN;
	}

	// ------------------------------------------------------------------ sailing

	public ServerPlayer captain() {
		if (seats.isEmpty()) return null;
		List<Entity> p = seats.get(0).getPassengers();
		if (!p.isEmpty() && p.get(0) instanceof ServerPlayer sp) return sp;
		return null;
	}

	public void tick(ServerLevel level) {
		age++;
		if (state == State.SINKING) {
			tickSinking(level);
			return;
		}
		if (state == State.WRECK) return;
		if (rising > 0) {
			tickRising();
			return;
		}
		ServerPlayer captain = captain();
		Input in = testInput != null ? testInput : (captain != null ? captain.getLastClientInput() : Input.EMPTY);
		tickGuns(level, captain, in);

		float max = (float) ShipStats.maxSpeed(this) * (hullPercent() < 25 ? 0.6f : 1f);
		if (in.forward() && !in.backward()) {
			speed = Math.min(max, speed + ShipStats.acceleration(this));
		} else if (in.backward() && !in.forward()) {
			speed = Math.max(-max * 0.3, speed - ShipStats.acceleration(this) * 1.5);
		} else {
			speed *= 0.985;
			if (Math.abs(speed) < 0.002) speed = 0;
		}

		boolean sprint = in.sprint();
		if (sprint && !lastSprint && captain != null || sprint && !lastSprint && testInput != null) {
			String error = toggleFlight(level);
			if (error != null && captain != null) {
				captain.sendOverlayMessage(Component.literal(error).withStyle(ChatFormatting.RED));
			}
		}
		lastSprint = sprint;
		checkFlightAllowed();

		boolean moved;
		if (onGround) {
			speed = 0;
			moved = false;
		} else if (flying) {
			burnFuel(captain);
			moved = moveFlying(level, captain, in, max);
		} else {
			moved = moveSailing(level, captain, in, max);
		}

		if (spawned) updateEntities(moved);

		if (captain != null && age % 10 == 0) {
			double bps = speed * 20.0;
			String fire = Config.current.cannons.captainCanFire ? ", Space fires" : "";
			String fly = upgrades.flight ? ", Ctrl " + (flying ? "lands" : "takes off") : "";
			String head;
			if (onGround) {
				head = "Beached on land";
			} else if (flying) {
				head = String.format("✈ %s  |  %s  |  Altitude %.0f", type.displayName, landing ? "Landing" : "Flying",
						groundDistance(level, 99));
			} else {
				head = "⚓ " + type.displayName;
			}
			captain.sendOverlayMessage(Component.literal(String.format("%s  |  Speed %.1f blocks/s  |  Hull %d%%  |  W/S sails, A/D steer%s%s, Shift to leave",
					head, bps, hullPercent(), fire, fly)).withStyle(ChatFormatting.GOLD));
		}
		if (flying && age % 10 == 0) {
			level.sendParticles(ParticleTypes.END_ROD, x, y - 1.0, z, 2, type.halfBeam * 0.4, 0.1, type.length * 0.15, 0.01);
		}
		if (age % 10 == 0 && hullPercent() < 50) {
			level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y + 2.0, z, 3, 0.5, 0.3, 0.5, 0.01);
			if (hullPercent() < 25) level.sendParticles(ParticleTypes.FLAME, x, y + 1.5, z, 2, 0.6, 0.2, 0.6, 0.01);
		}
		if (age % 10 == 0) {
			long now = level.getGameTime();
			for (int side : new int[]{ShipModel.PORT, ShipModel.STARBOARD}) {
				ServerPlayer g = gunner(side);
				if (g == null) continue;
				long wait = reloadTicksLeft(now, side);
				String status = wait > 0 ? String.format("Reloading %.1fs", wait / 20.0) : "READY";
				String ammo = Config.current.cannons.requireAmmo ? String.valueOf(Cannons.ammoShots(this)) : "∞";
				g.sendOverlayMessage(Component.literal(String.format("%s guns: %s  |  Hull %d%%  |  Broadsides left %s  |  Space fires",
						Cannons.sideName(side), status, hullPercent(), ammo)).withStyle(ChatFormatting.GOLD));
			}
		}
	}

	private boolean moveSailing(ServerLevel level, ServerPlayer captain, Input in, float max) {
		boolean moved = false;
		int turn = (in.right() ? 1 : 0) - (in.left() ? 1 : 0);
		if (turn != 0) {
			double helm = 0.35 + 0.65 * Math.min(1.0, Math.abs(speed) / max);
			float newYaw = wrap(yaw + (float) (turn * ShipStats.turnRate(this) * helm));
			if (fits(level, x, y, z, newYaw)) {
				yaw = newYaw;
				moved = true;
			}
		}
		if (speed != 0) {
			double r = Math.toRadians(yaw);
			double nx = x - Math.sin(r) * speed;
			double nz = z + Math.cos(r) * speed;
			if (fits(level, nx, y, nz, yaw)) {
				x = nx;
				z = nz;
				moved = true;
			} else {
				speed = 0;
				if (captain != null) {
					captain.sendOverlayMessage(Component.literal("Land ahoy! The ship can't sail any further this way.")
							.withStyle(ChatFormatting.YELLOW));
				}
			}
		}
		return moved;
	}

	// ------------------------------------------------------------------ flight

	/** Takes off, or starts landing. Returns null on success, otherwise why not. */
	public String toggleFlight(ServerLevel level) {
		if (state != State.AFLOAT || rising > 0) return "Not now.";
		if (flying) {
			landing = !landing;
			return null;
		}
		if (!upgrades.flight) return "This ship has no Flight Core.";
		Config.Flight f = Config.current.flight;
		if (!f.enabled) return "Flying is turned off on this server.";
		if (!f.allowedDimensions.contains(dimension)) return "Ships can't fly in this dimension.";
		if (!f.fuel.isEmpty() && fuelAvailable() == null) return "No fuel in the cargo hold (" + fuelText() + ").";
		flying = true;
		flightTicks = 0;
		onGround = false;
		landing = false;
		speed = 0;
		ShipManager m = ShipManager.get();
		if (m != null) m.markDirty();
		return null;
	}

	private Config.Cost fuelAvailable() {
		for (Config.Cost c : Config.current.flight.fuel) {
			if (ItemMatch.count(cargo, c.item) >= Math.max(1, c.count)) return c;
		}
		return null;
	}

	private String fuelText() {
		StringBuilder sb = new StringBuilder();
		for (Config.Cost c : Config.current.flight.fuel) {
			if (sb.length() > 0) sb.append(" or ");
			sb.append(c.count).append(" ").append(c.item.substring(c.item.indexOf(':') + 1).replace('_', ' '));
		}
		return sb.toString();
	}

	/** Burns one lot of fuel from the cargo every fuelSeconds; with none left the ship glides down. */
	private void burnFuel(ServerPlayer captain) {
		Config.Flight f = Config.current.flight;
		if (f.fuel.isEmpty() || landing) return;
		int every = Math.max(20, (int) (f.fuelSeconds * 20));
		if (flightTicks++ % every != 0) return;
		Config.Cost c = fuelAvailable();
		if (c == null) {
			landing = true;
			if (captain != null) {
				captain.sendOverlayMessage(Component.literal("Out of fuel! Gliding down...").withStyle(ChatFormatting.RED));
			}
		} else {
			ItemMatch.take(cargo, c.item, Math.max(1, c.count));
		}
	}

	/** If flight was switched off in the config (or the dimension removed) while airborne, come down. */
	private void checkFlightAllowed() {
		if (!flying || landing) return;
		Config.Flight f = Config.current.flight;
		if (!f.enabled || !f.allowedDimensions.contains(dimension)) landing = true;
	}

	/** True if the hull, deck and masts at this position and heading touch no solid block. */
	public boolean clearAt(ServerLevel level, double px, double py, double pz, float pyaw) {
		float[] heights = {-0.8f, 0.2f, 1.2f, 2.4f};
		for (float[] p : model().footprint) {
			for (float h : heights) {
				Vec3 w = toWorld(px, py, pz, pyaw, p[0], h, p[1]);
				BlockPos bp = BlockPos.containing(w.x, w.y, w.z);
				if (!level.getBlockState(bp).getCollisionShape(level, bp).isEmpty()) return false;
			}
		}
		float stern = -type.length / 2f;
		for (float[] mast : type.masts) {
			Vec3 w = toWorld(px, py, pz, pyaw, 0, mast[1], stern + mast[0] * type.length);
			BlockPos bp = BlockPos.containing(w.x, w.y, w.z);
			if (!level.getBlockState(bp).getCollisionShape(level, bp).isEmpty()) return false;
		}
		return true;
	}

	/** Blocks of air between the keel and the first solid block or water below (capped). */
	public double groundDistance(ServerLevel level, double cap) {
		for (int d = 0; d <= (int) cap + 1; d++) {
			BlockPos bp = BlockPos.containing(x, y - 0.8 - d, z);
			if (!level.getBlockState(bp).getCollisionShape(level, bp).isEmpty() || !level.getFluidState(bp).isEmpty()) return d;
		}
		return cap + 2;
	}

	private boolean moveFlying(ServerLevel level, ServerPlayer captain, Input in, float max) {
		Config.Flight f = Config.current.flight;
		boolean moved = false;
		float cap = max * (float) f.speedMultiplier;
		// speed is clamped to the flying top speed
		if (speed > cap) speed = cap;

		int turn = (in.right() ? 1 : 0) - (in.left() ? 1 : 0);
		if (turn != 0) {
			double helm = 0.35 + 0.65 * Math.min(1.0, Math.abs(speed) / Math.max(0.01, max));
			float newYaw = wrap(yaw + (float) (turn * ShipStats.turnRate(this) * helm));
			if (clearAt(level, x, y, z, newYaw)) {
				yaw = newYaw;
				moved = true;
			}
		}
		if (speed != 0) {
			double r = Math.toRadians(yaw);
			double dx = -Math.sin(r) * speed, dz = Math.cos(r) * speed;
			if (clearAt(level, x + dx, y, z + dz, yaw)) {
				x += dx;
				z += dz;
				moved = true;
			} else if (clearAt(level, x + dx, y, z, yaw)) {
				x += dx;
				speed *= 0.9;
				moved = true;
			} else if (clearAt(level, x, y, z + dz, yaw)) {
				z += dz;
				speed *= 0.9;
				moved = true;
			} else {
				speed = 0;
				if (captain != null) {
					captain.sendOverlayMessage(Component.literal("Something is in the way!").withStyle(ChatFormatting.YELLOW));
				}
			}
		}

		// Vertical: look up to climb, down to dive. Needs forward speed to climb quickly.
		double vy = 0;
		if (landing) {
			vy = -Math.max(0.05, f.climbRate * 0.6);
		} else if (captain != null || testInput != null) {
			float pitch = testInput != null ? testPitch : captain.getXRot();
			if (Math.abs(pitch) > 5f) {
				double drive = 0.3 + 0.7 * Math.min(1.0, Math.abs(speed) / Math.max(0.01, max));
				vy = -Math.sin(Math.toRadians(pitch)) * f.climbRate * drive;
			}
		}
		if (vy > 0 && (y + vy > f.maxY || groundDistance(level, f.maxHeightAboveGround) >= f.maxHeightAboveGround)) vy = 0;
		if (vy != 0) {
			double ny = y + vy;
			if (landing && fits(level, x, ny, z, yaw)) {
				// touched the water: settle on the surface and sail again
				y = Math.floor(ny) + 1 - DRAFT;
				flying = false;
				landing = false;
				moved = true;
			} else if (clearAt(level, x, ny, z, yaw)) {
				y = ny;
				moved = true;
			} else if (landing) {
				flying = false;
				landing = false;
				onGround = true;
				speed = 0;
			}
			ShipManager m = ShipManager.get();
			if (m != null) m.markDirty();
		}
		return moved;
	}

	private void tickFalling(ServerLevel level) {
		fallSpeed = Math.min(1.5, fallSpeed + 0.04);
		int steps = Math.max(1, (int) Math.ceil(fallSpeed / 0.4));
		double dy = fallSpeed / steps;
		for (int i = 0; i < steps && falling; i++) {
			double ny = y - dy;
			if (fits(level, x, ny, z, yaw)) {
				// hit the water: carry on as a normal sinking
				y = ny;
				waterY = y;
				wreckY = computeWreckY(level);
				falling = false;
				flying = false;
				landing = false;
				stateTicks = 0;
				level.playSound(null, x, y, z, net.minecraft.sounds.SoundEvents.GENERIC_SPLASH, net.minecraft.sounds.SoundSource.BLOCKS, 2.0f, 0.6f);
			} else if (!clearAt(level, x, ny, z, yaw)) {
				// hit the ground: a wreck where it lies
				falling = false;
				flying = false;
				landing = false;
				waterY = wreckY = y;
				roll = SINK_ROLL;
				state = State.WRECK;
				wreckedAtGameTime = level.getGameTime();
				level.playSound(null, x, y, z, net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE, net.minecraft.sounds.SoundSource.BLOCKS, 2.0f, 0.5f);
			} else {
				y = ny;
			}
		}
		if (falling && age % 2 == 0) {
			level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y + 1.0, z, 4, type.halfBeam * 0.5, 0.3, type.length * 0.25, 0.02);
		}
		if (spawned) updateEntities(true);
		ShipManager m = ShipManager.get();
		if (m != null) m.markDirty();
	}

	private void tickSinking(ServerLevel level) {
		if (falling) {
			tickFalling(level);
			return;
		}
		stateTicks++;
		double progress = Math.min(1.0, stateTicks / (double) SINK_TICKS);
		y = waterY + (wreckY - waterY) * progress;
		roll = (float) (SINK_ROLL * progress);
		if (stateTicks % 4 == 0) {
			level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, x, waterY + 0.5, z, 6, type.halfBeam * 0.5, 0.3, type.length * 0.25, 0.05);
		}
		if (stateTicks % 25 == 0) {
			level.playSound(null, x, waterY, z, net.minecraft.sounds.SoundEvents.GENERIC_SPLASH, net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 0.5f);
		}
		if (spawned) updateEntities(true);
		if (stateTicks >= SINK_TICKS) {
			state = State.WRECK;
			flying = false;
			landing = false;
			wreckedAtGameTime = level.getGameTime();
			y = wreckY;
			roll = SINK_ROLL;
			if (spawned) updateEntities(true);
			ShipManager m = ShipManager.get();
			if (m != null) m.markDirty();
		}
	}

	private void tickRising() {
		rising--;
		double left = rising + 1;
		y += (waterY - y) / left;
		roll -= roll / (float) left;
		if (rising == 0) {
			y = waterY;
			roll = 0;
		}
		if (spawned) updateEntities(true);
	}

	public static final int SINK_TICKS = 100;
	public static final float SINK_ROLL = 18f;

	private ServerPlayer gunner(int side) {
		int idx = model().gunnerSeat(side);
		if (idx < 0 || idx >= seats.size()) return null;
		List<Entity> p = seats.get(idx).getPassengers();
		return !p.isEmpty() && p.get(0) instanceof ServerPlayer sp ? sp : null;
	}

	/** Fires on a Space press (not while held) and lets queued cannons go off. */
	private void tickGuns(ServerLevel level, ServerPlayer captain, Input captainInput) {
		if (!queue.isEmpty()) {
			for (int i = queue.size() - 1; i >= 0; i--) {
				Cannons.Queued q = queue.get(i);
				if (q.delay() <= 0) {
					queue.remove(i);
					if (state == State.AFLOAT) Cannons.launch(level, this, q);
				} else {
					queue.set(i, q.tick());
				}
			}
		}
		if (seats.isEmpty() || state != State.AFLOAT) return;
		ServerPlayer port = gunner(ShipModel.PORT), star = gunner(ShipModel.STARBOARD);
		ServerPlayer[] who = {captain, port, star};
		for (int i = 0; i < 3; i++) {
			ServerPlayer p = who[i];
			boolean jump = p != null && (i == 0 ? captainInput.jump() : p.getLastClientInput().jump());
			boolean pressed = jump && !lastJump[i];
			lastJump[i] = jump;
			if (!pressed) continue;
			if (i == 0) {
				if (!Config.current.cannons.captainCanFire) continue;
				int side = wrap(p.getYRot() - yaw) < 0 ? ShipModel.PORT : ShipModel.STARBOARD;
				if (gunner(side) != null) {
					p.sendOverlayMessage(Component.literal("A gunner is manning the " + Cannons.sideName(side).toLowerCase() + " guns.")
							.withStyle(ChatFormatting.YELLOW));
					continue;
				}
				Cannons.tryFire(level, this, side, p, p.getYRot(), p.getXRot());
			} else {
				Cannons.tryFire(level, this, i == 1 ? ShipModel.PORT : ShipModel.STARBOARD, p, p.getYRot(), p.getXRot());
			}
		}
	}

	private static float wrap(float a) {
		a %= 360f;
		if (a >= 180f) a -= 360f;
		if (a < -180f) a += 360f;
		return a;
	}

	/** A point in ship coordinates, tilted by the current roll about the ship's long axis. */
	private Vector3f rolled(Vector3f v) {
		if (roll == 0) return v;
		return new Vector3f(v).rotateZ((float) Math.toRadians(roll));
	}

	/** Moves every display/seat/button to follow the ship, with a gentle bob on the waves. */
	private void updateEntities(boolean moved) {
		float bob = state == State.AFLOAT && rising == 0 ? (float) (Math.sin(age * 0.07) * 0.06) : 0f;
		if (!moved && Math.abs(bob - lastSentBob) < 0.01f) return;
		lastSentBob = bob;
		double by = y + bob;
		for (Display.BlockDisplay d : parts) d.snapTo(x, by, z, yaw, 0f);
		ShipModel m = model();
		if (roll != appliedRoll && (Float.isNaN(appliedRoll) || age - lastRollSent >= 5 || roll == 0 || roll == SINK_ROLL)) {
			applyRoll(m);
		}
		for (int i = 0; i < seats.size(); i++) {
			Vector3f s = rolled(m.seats.get(i));
			Vec3 w = toWorld(x, by, z, yaw, s.x, s.y, s.z);
			seats.get(i).snapTo(w.x, w.y, w.z, yaw, 0f);
			if (i > 0 && i - 1 < seatButtons.size()) {
				seatButtons.get(i - 1).snapTo(w.x, w.y - 0.4, w.z, 0f, 0f);
			}
		}
		if (tillerButton != null) {
			Vector3f tl = rolled(m.tiller);
			Vec3 t = toWorld(x, by, z, yaw, tl.x, tl.y, tl.z);
			tillerButton.snapTo(t.x, t.y - 0.3, t.z, 0f, 0f);
		}
		if (cargoButton != null) {
			Vector3f cg = rolled(m.cargo);
			Vec3 c = toWorld(x, by, z, yaw, cg.x, cg.y, cg.z);
			cargoButton.snapTo(c.x, c.y - 0.05, c.z, 0f, 0f);
		}
	}

	/** Tilts every part about the long axis by re-sending its transformation (the client smooths it). */
	private void applyRoll(ShipModel m) {
		appliedRoll = roll;
		lastRollSent = age;
		Quaternionf q = new Quaternionf().rotateZ((float) Math.toRadians(roll));
		for (int i = 0; i < parts.size() && i < m.parts.size(); i++) {
			ShipModel.Part p = m.parts.get(i);
			Vector3f t = new Vector3f(p.x(), p.y(), p.z()).rotate(q);
			Display.BlockDisplay d = parts.get(i);
			Compat.setTransformationInterpolationDelay(d, 0);
			Compat.setTransformationInterpolationDuration(d, 5);
			Compat.setTransformation(d, new Transformation(t, new Quaternionf(q), new Vector3f(p.sx(), p.sy(), p.sz()), new Quaternionf()));
		}
	}

	/** Seats a player. Seat 0 is the captain's. Returns false if that seat is taken. */
	public boolean seat(ServerPlayer player, int seatIndex) {
		if (seatIndex < 0 || seatIndex >= seats.size()) return false;
		Display.ItemDisplay s = seats.get(seatIndex);
		if (!s.getPassengers().isEmpty()) return false;
		if (player.isPassenger()) player.stopRiding();
		return player.startRiding(s, true, true);
	}

	public int firstFreePassengerSeat() {
		for (int i = 1; i <= type.passengerSeats && i < seats.size(); i++) {
			if (seats.get(i).getPassengers().isEmpty()) return i;
		}
		return -1;
	}
}
