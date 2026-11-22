package io.github.comfortforged.tidesofwar.core;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.Random;
import java.util.UUID;

/** Firing a broadside: checks, ammo, aim and the queue that makes the guns go off one after another. */
public final class Cannons {
	private static final Random RANDOM = new Random();
	/** Ticks between the cannons of one broadside. */
	static final int STAGGER = 3;

	private Cannons() {}

	/** A cannon waiting its turn in a broadside. */
	record Queued(ShipModel.Cannon cannon, int delay, float aimYaw, float elevation, UUID shooter) {
		Queued tick() {
			return new Queued(cannon, delay - 1, aimYaw, elevation, shooter);
		}
	}

	private static void tell(ServerPlayer p, String text, ChatFormatting color) {
		if (p != null) p.sendOverlayMessage(Component.literal(text).withStyle(color));
	}

	static String sideName(int side) {
		return side == ShipModel.PORT ? "Port" : "Starboard";
	}

	/** Tries to fire the whole side. Returns true if the guns were fired. */
	public static boolean tryFire(ServerLevel level, Ship ship, int side, ServerPlayer shooter, float lookYaw, float lookPitch) {
		Config.Cannons cfg = Config.current.cannons;
		if (ship.state != Ship.State.AFLOAT) return false;
		long now = level.getGameTime();
		long wait = ship.reloadTicksLeft(now, side);
		if (wait > 0) {
			tell(shooter, String.format("Reloading %.1fs", wait / 20.0), ChatFormatting.YELLOW);
			return false;
		}
		int guns = ship.type.cannonsPerSide;
		if (cfg.requireAmmo && !takeAmmo(ship, guns)) {
			tell(shooter, "Out of ammo! Put " + ammoName() + " in the cargo hold.", ChatFormatting.RED);
			return false;
		}

		// Aim: look direction, limited to a fan around the line straight out from the hull.
		double perp = ship.yaw + (side == ShipModel.PORT ? -90.0 : 90.0);
		double off = wrap(lookYaw - perp);
		off = Math.max(-cfg.traverse, Math.min(cfg.traverse, off));
		float aimYaw = (float) (perp + off);
		float elevation = (float) Math.max(cfg.minElevation, Math.min(cfg.maxElevation, -lookPitch));

		UUID who = shooter != null ? shooter.getUUID() : null;
		int i = 0;
		for (ShipModel.Cannon c : ship.model().cannons) {
			if (c.side() != side) continue;
			ship.queue.add(new Queued(c, i * STAGGER, aimYaw, elevation, who));
			i++;
		}
		ship.setReload(side, now + ShipStats.reloadTicks(ship) + (long) (guns - 1) * STAGGER);
		return true;
	}

	/** Fires one queued cannon: spawns the ball and the muzzle blast. */
	static void launch(ServerLevel level, Ship ship, Queued q) {
		Config.Cannons cfg = Config.current.cannons;
		ShipModel.Cannon c = q.cannon();
		Vector3f local = new Vector3f(c.x() + c.side() * 0.6f, c.y(), c.z());
		Vec3 muzzle = ship.toWorld(local);

		double yaw = q.aimYaw() + RANDOM.nextGaussian() * cfg.spreadDegrees;
		double elev = q.elevation() + RANDOM.nextGaussian() * cfg.spreadDegrees * 0.5;
		double yr = Math.toRadians(yaw), er = Math.toRadians(elev);
		Vec3 dir = new Vec3(-Math.sin(yr) * Math.cos(er), Math.sin(er), Math.cos(yr) * Math.cos(er));
		double sy = Math.toRadians(ship.yaw);
		Vec3 shipVel = new Vec3(-Math.sin(sy) * ship.speed, 0, Math.cos(sy) * ship.speed);
		Vec3 vel = dir.scale(ShipStats.muzzleSpeed(ship)).add(shipVel);

		ShipManager.get().addBall(new Cannonball(level, ship, q.shooter(), muzzle, vel));

		level.sendParticles(ParticleTypes.EXPLOSION, muzzle.x, muzzle.y, muzzle.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, muzzle.x + dir.x, muzzle.y + dir.y, muzzle.z + dir.z,
				4, 0.1, 0.1, 0.1, 0.01);
		level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 1.6f, 0.6f);
	}

	// ------------------------------------------------------------------ ammo

	private static boolean takeAmmo(Ship ship, int guns) {
		for (Config.Cost cost : Config.current.cannons.ammo) {
			int need = Math.max(1, cost.count) * guns;
			if (ItemMatch.count(ship.cargo, cost.item) >= need) {
				ItemMatch.take(ship.cargo, cost.item, need);
				ShipManager.get().markDirty();
				return true;
			}
		}
		return false;
	}

	/** How many full broadsides the cargo can supply (for the overlay). */
	static int ammoShots(Ship ship) {
		int best = 0;
		for (Config.Cost cost : Config.current.cannons.ammo) {
			best = Math.max(best, ItemMatch.count(ship.cargo, cost.item) / Math.max(1, cost.count));
		}
		return best;
	}

	private static String ammoName() {
		var ammo = Config.current.cannons.ammo;
		if (ammo.isEmpty()) return "ammunition";
		String id = ammo.get(0).item;
		return id.substring(id.indexOf(':') + 1).replace('_', ' ');
	}

	private static double wrap(double a) {
		a %= 360.0;
		if (a >= 180.0) a -= 360.0;
		if (a < -180.0) a += 360.0;
		return a;
	}
}
