package io.github.kdy05.smartmovingreborn.mixin.client;

import io.github.kdy05.smartmovingreborn.logic.MovingController;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
    private Player smartmovingreborn$self() {
        return (Player) (Object) this;
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void smartmovingreborn$init(CallbackInfo ci) {
        MovingController.setSelf(smartmovingreborn$self());
    }

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void smartmovingreborn$beforeAiStep(CallbackInfo ci) {
        MovingController.beforeAiStep(smartmovingreborn$self());
    }

    @Inject(method = "aiStep", at = @At("TAIL"))
    private void smartmovingreborn$afterAiStep(CallbackInfo ci) {
        MovingController.afterAiStep(smartmovingreborn$self());
    }

    @Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true)
    private void smartmovingreborn$serverAiStep(CallbackInfo ci) {
        if (MovingController.serverAiStep(smartmovingreborn$self())) {
            ci.cancel();
        }
    }

    @Inject(method = "serverAiStep", at = @At("TAIL"))
    private void smartmovingreborn$afterServerAiStep(CallbackInfo ci) {
        MovingController.afterServerAiStep(smartmovingreborn$self());
    }

    @Inject(method = "moveTowardsClosestSpace", at = @At("HEAD"), cancellable = true)
    private void smartmovingreborn$moveTowardsClosestSpace(double x, double z, CallbackInfo ci) {
        if (MovingController.moveTowardsClosestSpace(smartmovingreborn$self(), x, z)) {
            ci.cancel();
        }
    }

    @Inject(method = "move", at = @At("HEAD"))
    private void smartmovingreborn$beforeMove(MoverType type, Vec3 movement, CallbackInfo ci) {
        MovingController.beforeMove(smartmovingreborn$self(), type, movement);
    }

    @Inject(method = "move", at = @At("TAIL"))
    private void smartmovingreborn$afterMove(MoverType type, Vec3 movement, CallbackInfo ci) {
        MovingController.afterMove(smartmovingreborn$self(), type, movement);
    }

    @Inject(method = "isShiftKeyDown", at = @At("HEAD"), cancellable = true)
    private void smartmovingreborn$isShiftKeyDown(CallbackInfoReturnable<Boolean> cir) {
        Boolean result = MovingController.isShiftKeyDown(smartmovingreborn$self());
        if (result != null) {
            cir.setReturnValue(result);
        }
    }
}
