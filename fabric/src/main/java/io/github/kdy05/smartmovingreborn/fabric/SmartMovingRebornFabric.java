package io.github.kdy05.smartmovingreborn.fabric;

import io.github.kdy05.smartmovingreborn.SmartMovingReborn;
import io.github.kdy05.smartmovingreborn.network.ConfigSyncMessage;
import io.github.kdy05.smartmovingreborn.network.ServerNetworkHandler;
import io.github.kdy05.smartmovingreborn.network.SoundMessage;
import io.github.kdy05.smartmovingreborn.network.StateMessage;
import io.github.kdy05.smartmovingreborn.network.StateRelayMessage;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class SmartMovingRebornFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        SmartMovingReborn.init();
        PayloadTypeRegistry.playC2S().register(StateMessage.TYPE, StateMessage.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(SoundMessage.TYPE, SoundMessage.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(StateRelayMessage.TYPE, StateRelayMessage.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(ConfigSyncMessage.TYPE, ConfigSyncMessage.STREAM_CODEC);
        // Receivers run on the server thread.
        ServerPlayNetworking.registerGlobalReceiver(StateMessage.TYPE,
                (message, context) -> ServerNetworkHandler.onState(context.player(), message));
        ServerPlayNetworking.registerGlobalReceiver(SoundMessage.TYPE,
                (message, context) -> ServerNetworkHandler.onSound(context.player(), message));
    }
}
