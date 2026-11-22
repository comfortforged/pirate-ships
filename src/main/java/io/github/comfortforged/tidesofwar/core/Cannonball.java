package io.github.comfortforged.tidesofwar.core;

import com.mojang.math.Transformation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A cannonball in flight. Not an entity type (so vanilla clients need nothing): just a position and a velocity
 * that the ShipManager moves each tick, plus one small block display so players can see it.
 */
final class Cannonball {
	static final String PART_KEY = "cannonball";

	final ServerLevel level;
	final Ship source;
	final UUID shooter;
	Vec3 pos;
	Vec3 vel;
	int age;
	boolean dead;
	private Display.BlockDisplay visual;

	Cannonball(ServerLevel level, Ship source, UUID shooter, Vec3 pos, Vec3 vel) {
		this.level = level;
		this.source = source;
		this.shooter = shooter;
		this.pos = pos;
		this.vel = vel;
		ShipManager m = ShipManager.get();
		visual = new Display.BlockDisplay(Compat.blockDisplay(), level);
		Compat.setBlockState(visual, Blocks.COAL_BLOCK.defaultBlockState());
		Compat.setTransformation(visual, new Transformation(new Vector3f(-0.15f, -0.15f, -0.15f), new Quaternionf(),
				new Vector3f(0.3f, 0.3f, 0.3f), new Quaternionf()));
		Compat.setPosRotInterpolationDuration(visual, 1);
		Compat.setViewRange(visual, 3.0f);
		visual.addTag(Ship.PART_TAG);
		visual.setSilent(true);
		visual.setNoGravity(true);
		visual.snapTo(pos.x, pos.y, pos.z, 0f, 0f);
		m.registerPart(visual, source);
		level.addFreshEntity(visual);
	}

	void remove(ShipManager m) {
		dead = true;
		if (visual != null) {
			m.unregisterPart(visual);
			visual.discard();
			visual = null;
		}
	}

	/** What the ball hit first this tick. */
	private enum Kind { NONE, SHIP, ENTITY, BLOCK, WATER }

	void tick(ShipManager m) {
		Config.Cannons cfg = Config.current.cannons;
		if (++age > cfg.maxFlightTicks || pos.y < level.getMinY() - 8) {
			remove(m);
			return;
		}
		Vec3 p0 = pos, p1 = pos.add(vel);
		double segLen = p0.distanceTo(p1);

		Kind kind = Kind.NONE;
		double bestT = 2.0;
		Ship hitShip = null;
		Entity hitEntity = null;

		// 1. Other ships nearby.
		String dim = ShipManager.dimensionId(level);
		for (Ship s : m.ships()) {
			if (s == source || !s.dimension.equals(dim)) continue;
			if (s.state == Ship.State.WRECK) continue;
			if (Math.abs(s.x - p0.x) > 40 || Math.abs(s.z - p0.z) > 40) continue;
			double t = s.hitFraction(p0, p1);
			if (t >= 0 && t < bestT) {
				bestT = t;
				kind = Kind.SHIP;
				hitShip = s;
			}
		}
		// 2. Living things.
		AABB box = new AABB(p0, p1).inflate(0.5);
		List<Entity> near = level.getEntities((Entity) null, box, this::targetable);
		for (Entity e : near) {
			Optional<Vec3> c = e.getBoundingBox().inflate(0.3).clip(p0, p1);
			if (c.isEmpty()) continue;
			double t = segLen == 0 ? 0 : c.get().distanceTo(p0) / segLen;
			if (t < bestT) {
				bestT = t;
				kind = Kind.ENTITY;
				hitEntity = e;
				hitShip = null;
			}
		}
		// 3. Blocks.
		BlockHitResult br = level.clip(new ClipContext(p0, p1, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
		if (br.getType() == HitResult.Type.BLOCK) {
			double t = segLen == 0 ? 0 : br.getLocation().distanceTo(p0) / segLen;
			if (t < bestT) {
				bestT = t;
				kind = Kind.BLOCK;
				hitShip = null;
				hitEntity = null;
			}
		}
		// 4. Water.
		if (kind == Kind.NONE && level.getFluidState(BlockPos.containing(p1)).is(FluidTags.WATER)) {
			kind = Kind.WATER;
			bestT = 1.0;
		}

		if (kind != Kind.NONE) {
			Vec3 at = p0.add(p1.subtract(p0).scale(Math.min(1.0, bestT)));
			Impacts.hit(level, this, kind.name(), at, hitShip, hitEntity, br);
			remove(m);
			return;
		}

		pos = p1;
		vel = new Vec3(vel.x * 0.99, (vel.y - cfg.gravity) * 0.99, vel.z * 0.99);
		visual.snapTo(pos.x, pos.y, pos.z, 0f, 0f);
		if (age % 2 == 0) level.sendParticles(ParticleTypes.SMOKE, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
	}

	private boolean targetable(Entity e) {
		if (!Rules.canHurtEntity(e)) return false;
		Entity root = e.getRootVehicle();
		if (Ship.isPart(root)) {
			Ship s = ShipManager.get().shipOfPart(root);
			if (s == source) return false;
		}
		return true;
	}
}
