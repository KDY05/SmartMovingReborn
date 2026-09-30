package io.github.kdy05.smartmovingreborn.mixin.client;

import io.github.kdy05.smartmovingreborn.SmartMovingReborn;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Placeholder that proves the common mixin config is applied on both loaders.
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void smartmovingreborn$onInit(CallbackInfo ci) {
        SmartMovingReborn.LOGGER.info("Smart Moving Reborn mixins applied");
    }
}
