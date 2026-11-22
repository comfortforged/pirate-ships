package io.github.comfortforged.tidesofwar.mixin;

import io.github.comfortforged.tidesofwar.core.ShipColliders;
import io.github.comfortforged.tidesofwar.core.ShipHooks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Every entity's collision test passes through here; ships clip it so they can be stood on. */
@Mixin(Entity.class)
public abstract class EntityMixin {
	@Inject(method = "collide", at = @At("RETURN"), cancellable = true)
	private void tidesofwar$collide(Vec3 movement, CallbackInfoReturnable<Vec3> cir) {
		ShipHooks.collideMixinRan = true;
		cir.setReturnValue(ShipColliders.adjust((Entity) (Object) this, movement, cir.getReturnValue()));
	}
}
