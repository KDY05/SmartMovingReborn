package io.github.kdy05.smartmovingreborn.mixin.common;

import io.github.kdy05.smartmovingreborn.logic.MovingController;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "getMovementEmission", at = @At("HEAD"), cancellable = true)
    private void smartmovingreborn$getMovementEmission(CallbackInfoReturnable<Entity.MovementEmission> cir) {
        if (MovingController.silentMovement((Entity) (Object) this)) {
            cir.setReturnValue(Entity.MovementEmission.NONE);
        }
    }

    @Redirect(method = "updateFluidOnEyes",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getEyeY()D"))
    private double smartmovingreborn$fluidEyeY(Entity entity) {
        return MovingController.fluidEyeY(entity, entity.getEyeY());
    }
}
