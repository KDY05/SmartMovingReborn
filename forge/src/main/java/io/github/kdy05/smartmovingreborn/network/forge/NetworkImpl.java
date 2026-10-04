package io.github.kdy05.smartmovingreborn.network.forge;

import io.github.kdy05.smartmovingreborn.forge.SmartMovingRebornForgeClient;
import io.github.kdy05.smartmovingreborn.forge.SmartMovingRebornForgeNetwork;
import io.github.kdy05.smartmovingreborn.network.ConfigSyncMessage;
import io.github.kdy05.smartmovingreborn.network.Network;
import io.github.kdy05.smartmovingreborn.network.SoundMessage;
import io.github.kdy05.smartmovingreborn.network.StateMessage;
import io.github.kdy05.smartmovingreborn.network.StateRelayMessage;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;

public final class NetworkImpl {
    private NetworkImpl() {
    }

    // The client-only halves live in SmartMovingRebornForgeClient so that this class also loads on servers.
    public static boolean isServerPresent() {
        return SmartMovingRebornForgeClient.isServerPresent();
    }

    public static void sendToServer(StateMessage message) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        message.write(buf);
        SmartMovingRebornForgeClient.sendToServer(Network.STATE_ID, buf);
    }

    public static void sendSoundToServer(SoundMessage message) {
        if (!SmartMovingRebornForgeClient.canSendToServer(SmartMovingRebornForgeNetwork.SOUND)) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        message.write(buf);
        SmartMovingRebornForgeClient.sendToServer(Network.SOUND_ID, buf);
    }

    public static boolean canSendTo(ServerPlayer player) {
        return SmartMovingRebornForgeNetwork.RELAY.isRemotePresent(player.connection.connection);
    }

    public static void sendTo(ServerPlayer player, StateRelayMessage message) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        message.write(buf);
        player.connection.send(new ClientboundCustomPayloadPacket(Network.RELAY_ID, buf));
    }

    public static void sendTo(ServerPlayer player, ConfigSyncMessage message) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        message.write(buf);
        player.connection.send(new ClientboundCustomPayloadPacket(Network.CONFIG_ID, buf));
    }
}
