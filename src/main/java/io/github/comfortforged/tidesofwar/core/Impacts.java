package io.github.comfortforged.tidesofwar.core;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** What happens where a cannonball lands. */
final class Impacts {
	private Impacts() {}

	/** Explosions never hurt ship parts or anything the rules protect (players when PvP is off, and so on). */
	private static final ExplosionDamageCalculator CALCULATOR = new ExplosionDamageCalculator() {
		@Override
		public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
			return Rules.canHurtEntity(entity);
		}
	};

	static void hit(ServerLevel level, Cannonball ball, String kind, Vec3 at, Ship ship, Entity entity, BlockHitResult block) {
		if (kind.equals("WATER")) {
			level.sendParticles(ParticleTypes.SPLASH, at.x, at.y, at.z, 30, 0.4, 0.2, 0.4, 0.2);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 1.2f, 0.8f);
			return;
		}
		Config.Damage cfg = Config.current.damage;
		Player attacker = null;
		if (ball.shooter != null) attacker = level.getPlayerByUUID(ball.shooter);
		DamageSource source = level.damageSources().explosion(null, attacker);

		if (kind.equals("SHIP") && Rules.canHurtShip(ball.source, ball.shooter, ship)) {
			ship.damage(ShipStats.shipDamage(ball.source), level.getGameTime());
		}
		if (kind.equals("ENTITY") && entity != null && Rules.canHurtEntity(entity)) {
			entity.hurtServer(level, source, (float) ShipStats.entityDamage(ball.source));
		}

		if (cfg.blockDamage) {
			level.explode(null, source, CALCULATOR, at.x, at.y, at.z, (float) cfg.explosionPower, false,
					Level.ExplosionInteraction.TNT);
		} else {
			blast(level, at);
			double r = cfg.splashRadius;
			if (r > 0) {
				AABB box = new AABB(at, at).inflate(r);
				List<Entity> near = level.getEntities((Entity) null, box, Rules::canHurtEntity);
				for (Entity e : near) {
					if (kind.equals("ENTITY") && e == entity) continue; // already took the direct hit
					double d = e.getBoundingBox().getCenter().distanceTo(at);
					if (d > r) continue;
					e.hurtServer(level, source, (float) (ShipStats.entityDamage(ball.source) * (1.0 - d / r)));
				}
			}
		}
	}

	private static void blast(ServerLevel level, Vec3 at) {
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 2, 0.3, 0.3, 0.3, 0);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 8, 0.4, 0.4, 0.4, 0.02);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 1.4f, 0.9f);
	}
}
