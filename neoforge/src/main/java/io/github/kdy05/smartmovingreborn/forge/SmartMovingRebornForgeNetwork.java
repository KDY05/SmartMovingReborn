package io.github.kdy05.smartmovingreborn.forge;

import io.github.kdy05.smartmovingreborn.SmartMovingReborn;
import io.github.kdy05.smartmovingreborn.client.SmartMovingClient;
import io.github.kdy05.smartmovingreborn.network.ConfigSyncMessage;
import io.github.kdy05.smartmovingreborn.network.ServerNetworkHandler;
import io.github.kdy05.smartmovingreborn.network.SoundMessage;
import io.github.kdy05.smartmovingreborn.network.StateMessage;
import io.github.kdy05.smartmovingreborn.network.StateRelayMessage;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class SmartMovingRebornForgeNetwork {
    private static final String PROTOCOL = "1";

    /** Accepts peers without the channel (including vanilla) in both directions. */
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(SmartMovingReborn.MOD_ID, "main"), () -> PROTOCOL,
            NetworkRegistry.acceptMissingOr(PROTOCOL), NetworkRegistry.acceptMissingOr(PROTOCOL));

    private SmartMovingRebornForgeNetwork() {
    }

    static void register() {
        CHANNEL.messageBuilder(StateMessage.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(StateMessage::write)
                .decoder(StateMessage::read)
                .consumerMainThread((message, context) -> {
                    ServerPlayer sender = context.get().getSender();
                    if (sender != null) {
                        ServerNetworkHandler.onState(sender, message);
                    }
                    context.get().setPacketHandled(true);
                })
                .add();
        CHANNEL.messageBuilder(StateRelayMessage.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(StateRelayMessage::write)
                .decoder(StateRelayMessage::read)
                .consumerMainThread((message, context) -> {
                    SmartMovingClient.onStateRelay(message);
                    context.get().setPacketHandled(true);
                })
                .add();
        CHANNEL.messageBuilder(SoundMessage.class, 2, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SoundMessage::write)
                .decoder(SoundMessage::read)
                .consumerMainThread((message, context) -> {
                    ServerPlayer sender = context.get().getSender();
                    if (sender != null) {
                        ServerNetworkHandler.onSound(sender, message);
                    }
                    context.get().setPacketHandled(true);
                })
                .add();
        CHANNEL.messageBuilder(ConfigSyncMessage.class, 3, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ConfigSyncMessage::write)
                .decoder(ConfigSyncMessage::read)
                .consumerMainThread((message, context) -> {
                    SmartMovingClient.onConfigSync(message);
                    context.get().setPacketHandled(true);
                })
                .add();
    }
}
