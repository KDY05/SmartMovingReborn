package io.github.kdy05.smartmovingreborn.network.forge;

import io.github.kdy05.smartmovingreborn.forge.SmartMovingRebornForgeClient;
import io.github.kdy05.smartmovingreborn.forge.SmartMovingRebornForgeNetwork;
import io.github.kdy05.smartmovingreborn.network.SoundMessage;
import io.github.kdy05.smartmovingreborn.network.StateMessage;
import io.github.kdy05.smartmovingreborn.network.StateRelayMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

public final class NetworkImpl {
    private NetworkImpl() {
    }

    // The client-only halves live in SmartMovingRebornForgeClient so that this class also loads on servers.
    public static boolean isServerPresent() {
        return SmartMovingRebornForgeClient.isServerPresent();
    }

    public static void sendToServer(StateMessage message) {
        SmartMovingRebornForgeClient.sendToServer(message);
    }

    public static void sendSoundToServer(SoundMessage message) {
        SmartMovingRebornForgeClient.sendToServer(message);
    }

    public static boolean canSendTo(ServerPlayer player) {
        return SmartMovingRebornForgeNetwork.CHANNEL.isRemotePresent(player.connection.connection);
    }

    public static void sendTo(ServerPlayer player, StateRelayMessage message) {
        SmartMovingRebornForgeNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }
}
