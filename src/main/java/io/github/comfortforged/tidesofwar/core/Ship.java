package io.github.comfortforged.tidesofwar.core;

import com.mojang.math.Transformation;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
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
	}

	public ShipModel model() {
		return ShipModel.of(type);
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
			Display.BlockDisplay d = new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, level);
			d.setBlockState(p.block());
			d.setTransformation(new Transformation(
					new Vector3f(p.x(), p.y(), p.z()),
					new Quaternionf(),
					new Vector3f(p.sx(), p.sy(), p.sz()),
					new Quaternionf()));
			d.setPosRotInterpolationDuration(2);
			d.setViewRange(3.0f);
			parts.add(d);
			add(level, manager, d);
		}
		for (int i = 0; i < m.seats.size(); i++) {
			Display.ItemDisplay seat = new Display.ItemDisplay(EntityTypes.ITEM_DISPLAY, level);
			seat.setPosRotInterpolationDuration(2);
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
		Interaction b = new Interaction(EntityTypes.INTERACTION, level);
		b.setWidth(w);
		b.setHeight(h);
		b.setResponse(true);
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
		ServerPlayer captain = captain();
		Input in = testInput != null ? testInput : (captain != null ? captain.getLastClientInput() : Input.EMPTY);

		float max = type.maxSpeed;
		if (in.forward() && !in.backward()) {
			speed = Math.min(max, speed + type.acceleration);
		} else if (in.backward() && !in.forward()) {
			speed = Math.max(-max * 0.3, speed - type.acceleration * 1.5);
		} else {
			speed *= 0.985;
			if (Math.abs(speed) < 0.002) speed = 0;
		}

		boolean moved = false;
		int turn = (in.right() ? 1 : 0) - (in.left() ? 1 : 0);
		if (turn != 0) {
			double helm = 0.35 + 0.65 * Math.min(1.0, Math.abs(speed) / max);
			float newYaw = wrap(yaw + (float) (turn * type.turnRate * helm));
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

		if (spawned) updateEntities(moved);

		if (captain != null && age % 10 == 0) {
			double bps = speed * 20.0;
			captain.sendOverlayMessage(Component.literal(String.format("⚓ %s  |  Speed %.1f blocks/s  |  W/S sails, A/D steer, Shift to leave",
					type.displayName, bps)).withStyle(ChatFormatting.GOLD));
		}
	}

	private static float wrap(float a) {
		a %= 360f;
		if (a >= 180f) a -= 360f;
		if (a < -180f) a += 360f;
		return a;
	}

	/** Moves every display/seat/button to follow the ship, with a gentle bob on the waves. */
	private void updateEntities(boolean moved) {
		float bob = (float) (Math.sin(age * 0.07) * 0.06);
		if (!moved && Math.abs(bob - lastSentBob) < 0.01f) return;
		lastSentBob = bob;
		double by = y + bob;
		for (Display.BlockDisplay d : parts) d.snapTo(x, by, z, yaw, 0f);
		ShipModel m = model();
		for (int i = 0; i < seats.size(); i++) {
			Vector3f s = m.seats.get(i);
			Vec3 w = toWorld(x, by, z, yaw, s.x, s.y, s.z);
			seats.get(i).snapTo(w.x, w.y, w.z, yaw, 0f);
			if (i > 0 && i - 1 < seatButtons.size()) {
				seatButtons.get(i - 1).snapTo(w.x, w.y - 0.4, w.z, 0f, 0f);
			}
		}
		if (tillerButton != null) {
			Vec3 t = toWorld(x, by, z, yaw, m.tiller.x, m.tiller.y, m.tiller.z);
			tillerButton.snapTo(t.x, t.y - 0.3, t.z, 0f, 0f);
		}
		if (cargoButton != null) {
			Vec3 c = toWorld(x, by, z, yaw, m.cargo.x, m.cargo.y, m.cargo.z);
			cargoButton.snapTo(c.x, c.y - 0.05, c.z, 0f, 0f);
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
		for (int i = 1; i < seats.size(); i++) {
			if (seats.get(i).getPassengers().isEmpty()) return i;
		}
		return -1;
	}
}
