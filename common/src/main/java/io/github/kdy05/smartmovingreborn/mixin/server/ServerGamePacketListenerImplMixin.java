package io.github.kdy05.smartmovingreborn.mixin.server;

import io.github.kdy05.smartmovingreborn.logic.MovingController;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
    @Shadow
    public ServerPlayer player;

    /**
     * The packet first arrives on the network thread, where {@code ensureRunningOnSameThread} reschedules it and
     * throws. Injecting after that call runs the hook only once, on the server thread.
     */
    @Inject(method = "handleMovePlayer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
            shift = At.Shift.AFTER))
    private void smartmovingreborn$beforeHandleMovePlayer(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        MovingController.beforeHandleMovePlayer(player);
    }

    @Inject(method = "handleMovePlayer", at = @At("RETURN"))
    private void smartmovingreborn$afterHandleMovePlayer(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        MovingController.afterHandleMovePlayer(player);
    }
}
