package io.github.comfortforged.tidesofwar.core;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/** One place that decides who may hurt what, from the config. */
final class Rules {
	private Rules() {}

	/** The shooter, or the firing ship's owner when nobody is at the guns (self-test). */
	static UUID attacker(Ship source, UUID shooter) {
		return shooter != null ? shooter : source.owner;
	}

	static boolean canHurtShip(Ship source, UUID shooter, Ship target) {
		Config.Damage d = Config.current.damage;
		if (!d.shipCombat || target == source) return false;
		UUID attacker = attacker(source, shooter);
		boolean sameOwner = attacker != null && target.owner != null && attacker.equals(target.owner);
		return !sameOwner || d.friendlyFire;
	}

	static boolean canHurtEntity(Entity target) {
		if (Ship.isPart(target) || !(target instanceof LivingEntity) || !target.isAlive() || target.isSpectator()) return false;
		Config.Damage d = Config.current.damage;
		if (target instanceof Player p) return d.pvp && !p.isCreative();
		return d.mobs;
	}
}
