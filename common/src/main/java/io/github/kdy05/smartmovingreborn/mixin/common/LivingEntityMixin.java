package io.github.kdy05.smartmovingreborn.mixin.common;

import io.github.kdy05.smartmovingreborn.logic.MovingController;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Shadow
    private float swimAmount;
    @Shadow
    private float swimAmountO;

    @Inject(method = "updateSwimAmount", at = @At("TAIL"))
    private void smartmovingreborn$updateSwimAmount(CallbackInfo ci) {
        if (MovingController.suppressSwimAmount((Entity) (Object) this)) {
            swimAmount = 0;
            swimAmountO = 0;
        }
    }

    @Inject(method = "onClimbable", at = @At("HEAD"), cancellable = true)
    private void smartmovingreborn$onClimbable(CallbackInfoReturnable<Boolean> cir) {
        Boolean result = MovingController.onClimbable((Entity) (Object) this);
        if (result != null) {
            cir.setReturnValue(result);
        }
    }

    @Inject(method = "getFrictionInfluencedSpeed", at = @At("RETURN"), cancellable = true)
    private void smartmovingreborn$frictionInfluencedSpeed(float friction, CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(MovingController.frictionInfluencedSpeed((Entity) (Object) this, friction,
                cir.getReturnValueF()));
    }
}
