package io.github.kdy05.smartmovingreborn.fabric;

import io.github.kdy05.smartmovingreborn.SmartMovingReborn;
import io.github.kdy05.smartmovingreborn.network.Network;
import io.github.kdy05.smartmovingreborn.network.ServerNetworkHandler;
import io.github.kdy05.smartmovingreborn.network.StateMessage;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class SmartMovingRebornFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        SmartMovingReborn.init();
        ServerPlayNetworking.registerGlobalReceiver(Network.STATE_ID, (server, player, handler, buf, responseSender) -> {
            StateMessage message = StateMessage.read(buf);
            server.execute(() -> ServerNetworkHandler.onState(player, message));
        });
    }
}
