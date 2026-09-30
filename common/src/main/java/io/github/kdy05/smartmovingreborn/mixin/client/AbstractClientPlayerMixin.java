package io.github.kdy05.smartmovingreborn.mixin.client;

import io.github.kdy05.smartmovingreborn.logic.MovingController;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
    @Inject(method = "getFieldOfViewModifier", at = @At("HEAD"), cancellable = true)
    private void smartmovingreborn$fieldOfViewModifier(CallbackInfoReturnable<Float> cir) {
        Float result = MovingController.fieldOfViewModifier((Player) (Object) this);
        if (result != null) {
            cir.setReturnValue(result);
        }
    }
}
