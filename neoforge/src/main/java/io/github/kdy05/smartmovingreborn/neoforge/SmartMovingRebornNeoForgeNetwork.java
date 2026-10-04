package io.github.kdy05.smartmovingreborn.neoforge;

import io.github.kdy05.smartmovingreborn.client.SmartMovingClient;
import io.github.kdy05.smartmovingreborn.network.ConfigSyncMessage;
import io.github.kdy05.smartmovingreborn.network.ServerNetworkHandler;
import io.github.kdy05.smartmovingreborn.network.SoundMessage;
import io.github.kdy05.smartmovingreborn.network.StateMessage;
import io.github.kdy05.smartmovingreborn.network.StateRelayMessage;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class SmartMovingRebornNeoForgeNetwork {
    private SmartMovingRebornNeoForgeNetwork() {
    }

    /**
     * Optional payloads accept peers without them (including vanilla) in both directions. Handlers run on the
     * game thread.
     */
    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToServer(StateMessage.TYPE, StateMessage.STREAM_CODEC, (message, context) ->
                ServerNetworkHandler.onState((ServerPlayer) context.player(), message));
        registrar.playToServer(SoundMessage.TYPE, SoundMessage.STREAM_CODEC, (message, context) ->
                ServerNetworkHandler.onSound((ServerPlayer) context.player(), message));
        registrar.playToClient(StateRelayMessage.TYPE, StateRelayMessage.STREAM_CODEC, (message, context) ->
                SmartMovingClient.onStateRelay(message));
        registrar.playToClient(ConfigSyncMessage.TYPE, ConfigSyncMessage.STREAM_CODEC, (message, context) ->
                SmartMovingClient.onConfigSync(message));
    }
}
