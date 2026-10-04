package io.github.kdy05.smartmovingreborn.mixin.common;

import io.github.kdy05.smartmovingreborn.logic.MovingController;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@code travel} and {@code jumpFromGround} are hooked here rather than in {@code LivingEntity}: {@code Player}
 * overrides both, and the original replaced the player's whole method.
 */
@Mixin(Player.class)
public abstract class PlayerMixin {
    /** Whether the eye height was last refreshed for a Smart Moving lying pose. */
    @Unique
    private boolean smartmovingreborn$lyingEyes;

    private Player smartmovingreborn$self() {
        return (Player) (Object) this;
    }

    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void smartmovingreborn$travel(Vec3 input, CallbackInfo ci) {
        if (MovingController.travel(smartmovingreborn$self(), input)) {
            ci.cancel();
        }
    }

    @Inject(method = "travel", at = @At("TAIL"))
    private void smartmovingreborn$afterTravel(Vec3 input, CallbackInfo ci) {
        MovingController.afterTravel(smartmovingreborn$self());
    }

    @Inject(method = "updateSwimming", at = @At("HEAD"), cancellable = true)
    private void smartmovingreborn$updateSwimming(CallbackInfo ci) {
        if (MovingController.updateSwimming(smartmovingreborn$self())) {
            ci.cancel();
        }
    }

    @Inject(method = "jumpFromGround", at = @At("HEAD"), cancellable = true)
    private void smartmovingreborn$jumpFromGround(CallbackInfo ci) {
        if (MovingController.jumpFromGround(smartmovingreborn$self())) {
            ci.cancel();
        }
    }

    @Inject(method = "updatePlayerPose", at = @At("HEAD"), cancellable = true)
    private void smartmovingreborn$updatePlayerPose(CallbackInfo ci) {
        smartmovingreborn$lyingEyes = MovingController.updateEyeHeight(smartmovingreborn$self(),
                smartmovingreborn$lyingEyes);
        if (MovingController.updatePose(smartmovingreborn$self())) {
            ci.cancel();
        }
    }

    @Inject(method = "getDefaultDimensions", at = @At("RETURN"), cancellable = true)
    private void smartmovingreborn$getDefaultDimensions(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        cir.setReturnValue(MovingController.dimensions(smartmovingreborn$self(), pose, cir.getReturnValue()));
    }
}
