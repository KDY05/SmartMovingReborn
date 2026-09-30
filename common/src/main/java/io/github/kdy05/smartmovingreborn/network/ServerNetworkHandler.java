package io.github.kdy05.smartmovingreborn.network;

import io.github.kdy05.smartmovingreborn.mixin.server.ChunkMapAccessor;
import io.github.kdy05.smartmovingreborn.mixin.server.TrackedEntityAccessor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerPlayerConnection;

public final class ServerNetworkHandler {
    private ServerNetworkHandler() {
    }

    /** Runs on the server thread. Relays the sender's state to every modded client tracking the sender. */
    public static void onState(ServerPlayer sender, StateMessage message) {
        StateRelayMessage relay = new StateRelayMessage(sender.getId(), message.state());
        Object tracked = ((ChunkMapAccessor) sender.serverLevel().getChunkSource().chunkMap)
                .smartmovingreborn$getEntityMap().get(sender.getId());
        if (tracked == null) {
            return;
        }
        for (ServerPlayerConnection connection : ((TrackedEntityAccessor) tracked).smartmovingreborn$getSeenBy()) {
            ServerPlayer target = connection.getPlayer();
            if (Network.canSendTo(target)) {
                Network.sendTo(target, relay);
            }
        }
    }
}
