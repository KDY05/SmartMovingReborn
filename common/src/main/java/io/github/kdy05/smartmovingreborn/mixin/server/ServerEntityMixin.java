package io.github.kdy05.smartmovingreborn.mixin.server;

import io.github.kdy05.smartmovingreborn.network.ServerNetworkHandler;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerEntity.class)
public abstract class ServerEntityMixin {
    @Shadow
    @Final
    private Entity entity;

    /** At the tail, so that the viewer's client knows the entity by the time its state arrives. */
    @Inject(method = "addPairing", at = @At("TAIL"))
    private void smartmovingreborn$addPairing(ServerPlayer player, CallbackInfo ci) {
        ServerNetworkHandler.onStartTracking(entity, player);
    }
}
