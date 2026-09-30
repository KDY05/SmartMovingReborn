package io.github.kdy05.smartmovingreborn.mixin.common;

import io.github.kdy05.smartmovingreborn.logic.MovingController;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "onClimbable", at = @At("HEAD"), cancellable = true)
    private void smartmovingreborn$onClimbable(CallbackInfoReturnable<Boolean> cir) {
        Boolean result = MovingController.onClimbable((Entity) (Object) this);
        if (result != null) {
            cir.setReturnValue(result);
        }
    }
}
