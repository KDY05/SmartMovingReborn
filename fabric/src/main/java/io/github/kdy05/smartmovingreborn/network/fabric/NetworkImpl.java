package io.github.kdy05.smartmovingreborn.network.fabric;

import io.github.kdy05.smartmovingreborn.network.Network;
import io.github.kdy05.smartmovingreborn.network.StateMessage;
import io.github.kdy05.smartmovingreborn.network.StateRelayMessage;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public final class NetworkImpl {
    private NetworkImpl() {
    }

    public static boolean isServerPresent() {
        return ClientPlayNetworking.canSend(Network.STATE_ID);
    }

    public static void sendToServer(StateMessage message) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        message.write(buf);
        ClientPlayNetworking.send(Network.STATE_ID, buf);
    }

    public static boolean canSendTo(ServerPlayer player) {
        return ServerPlayNetworking.canSend(player, Network.RELAY_ID);
    }

    public static void sendTo(ServerPlayer player, StateRelayMessage message) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        message.write(buf);
        ServerPlayNetworking.send(player, Network.RELAY_ID, buf);
    }
}
