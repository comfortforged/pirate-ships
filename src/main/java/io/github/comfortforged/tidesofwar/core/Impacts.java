package io.github.comfortforged.tidesofwar.core;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** What happens where a cannonball lands. */
final class Impacts {
	private Impacts() {}

	static void hit(ServerLevel level, Cannonball ball, String kind, Vec3 at, Ship ship, Entity entity, BlockHitResult block) {
		switch (kind) {
			case "WATER" -> {
				level.sendParticles(ParticleTypes.SPLASH, at.x, at.y, at.z, 30, 0.4, 0.2, 0.4, 0.2);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 1.2f, 0.8f);
			}
			case "SHIP" -> {
				if (Rules.canHurtShip(ball.source, ball.shooter, ship)) {
					ship.damage(ShipStats.shipDamage(ball.source), level.getGameTime());
				}
				blast(level, at);
			}
			default -> blast(level, at);
		}
	}

	private static void blast(ServerLevel level, Vec3 at) {
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 2, 0.3, 0.3, 0.3, 0);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 8, 0.4, 0.4, 0.4, 0.02);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 1.4f, 0.9f);
	}
}
