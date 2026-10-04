package io.github.kdy05.smartmovingreborn.network.fabric;

import io.github.kdy05.smartmovingreborn.network.ConfigSyncMessage;
import io.github.kdy05.smartmovingreborn.network.SoundMessage;
import io.github.kdy05.smartmovingreborn.network.StateMessage;
import io.github.kdy05.smartmovingreborn.network.StateRelayMessage;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

public final class NetworkImpl {
    private NetworkImpl() {
    }

    public static boolean isServerPresent() {
        return ClientPlayNetworking.canSend(StateMessage.TYPE);
    }

    public static void sendToServer(StateMessage message) {
        ClientPlayNetworking.send(message);
    }

    public static void sendSoundToServer(SoundMessage message) {
        if (!ClientPlayNetworking.canSend(SoundMessage.TYPE)) {
            return;
        }
        ClientPlayNetworking.send(message);
    }

    public static boolean canSendTo(ServerPlayer player) {
        return ServerPlayNetworking.canSend(player, StateRelayMessage.TYPE);
    }

    public static void sendTo(ServerPlayer player, StateRelayMessage message) {
        ServerPlayNetworking.send(player, message);
    }

    public static void sendTo(ServerPlayer player, ConfigSyncMessage message) {
        ServerPlayNetworking.send(player, message);
    }
}
