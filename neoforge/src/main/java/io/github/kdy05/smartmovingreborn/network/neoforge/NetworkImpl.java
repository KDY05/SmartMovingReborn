package io.github.kdy05.smartmovingreborn.network.neoforge;

import io.github.kdy05.smartmovingreborn.neoforge.SmartMovingRebornNeoForgeClient;
import io.github.kdy05.smartmovingreborn.network.ConfigSyncMessage;
import io.github.kdy05.smartmovingreborn.network.SoundMessage;
import io.github.kdy05.smartmovingreborn.network.StateMessage;
import io.github.kdy05.smartmovingreborn.network.StateRelayMessage;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class NetworkImpl {
    private NetworkImpl() {
    }

    // The client-only halves live in SmartMovingRebornNeoForgeClient so that this class also loads on servers.
    public static boolean isServerPresent() {
        return SmartMovingRebornNeoForgeClient.isServerPresent();
    }

    public static void sendToServer(StateMessage message) {
        SmartMovingRebornNeoForgeClient.sendToServer(message);
    }

    public static void sendSoundToServer(SoundMessage message) {
        SmartMovingRebornNeoForgeClient.sendToServer(message);
    }

    public static boolean canSendTo(ServerPlayer player) {
        return player.connection.hasChannel(StateRelayMessage.TYPE);
    }

    public static void sendTo(ServerPlayer player, StateRelayMessage message) {
        PacketDistributor.sendToPlayer(player, message);
    }

    public static void sendTo(ServerPlayer player, ConfigSyncMessage message) {
        PacketDistributor.sendToPlayer(player, message);
    }
}
