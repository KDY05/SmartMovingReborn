package io.github.kdy05.smartmovingreborn.forge;

import io.github.kdy05.smartmovingreborn.client.SmartMovingClient;
import io.github.kdy05.smartmovingreborn.network.ConfigSyncMessage;
import io.github.kdy05.smartmovingreborn.network.Network;
import io.github.kdy05.smartmovingreborn.network.ServerNetworkHandler;
import io.github.kdy05.smartmovingreborn.network.SoundMessage;
import io.github.kdy05.smartmovingreborn.network.StateMessage;
import io.github.kdy05.smartmovingreborn.network.StateRelayMessage;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.event.EventNetworkChannel;

/**
 * One raw channel per message, under the same ids as Fabric, so Forge and Fabric peers (and non-modded servers that
 * speak the protocol) exchange the same payloads. Each side learns the other's channels from {@code minecraft:register}.
 */
public final class SmartMovingRebornForgeNetwork {
    private static final String PROTOCOL = "1";

    public static final EventNetworkChannel STATE = channel(Network.STATE_ID);
    public static final EventNetworkChannel RELAY = channel(Network.RELAY_ID);
    public static final EventNetworkChannel SOUND = channel(Network.SOUND_ID);
    public static final EventNetworkChannel CONFIG = channel(Network.CONFIG_ID);

    private SmartMovingRebornForgeNetwork() {
    }

    /** Accepts peers without the channel (including vanilla) in both directions. */
    private static EventNetworkChannel channel(ResourceLocation id) {
        return NetworkRegistry.ChannelBuilder.named(id)
                .networkProtocolVersion(() -> PROTOCOL)
                .clientAcceptedVersions(NetworkRegistry.acceptMissingOr(PROTOCOL))
                .serverAcceptedVersions(NetworkRegistry.acceptMissingOr(PROTOCOL))
                .eventNetworkChannel();
    }

    static void register() {
        STATE.addListener((NetworkEvent.ClientCustomPayloadEvent event) -> {
            NetworkEvent.Context context = event.getSource().get();
            ServerPlayer sender = context.getSender();
            if (sender != null) {
                StateMessage message = StateMessage.read(event.getPayload());
                context.enqueueWork(() -> ServerNetworkHandler.onState(sender, message));
            }
            context.setPacketHandled(true);
        });
        SOUND.addListener((NetworkEvent.ClientCustomPayloadEvent event) -> {
            NetworkEvent.Context context = event.getSource().get();
            ServerPlayer sender = context.getSender();
            if (sender != null) {
                SoundMessage message = SoundMessage.read(event.getPayload());
                context.enqueueWork(() -> ServerNetworkHandler.onSound(sender, message));
            }
            context.setPacketHandled(true);
        });
        RELAY.addListener((NetworkEvent.ServerCustomPayloadEvent event) -> {
            NetworkEvent.Context context = event.getSource().get();
            StateRelayMessage message = StateRelayMessage.read(event.getPayload());
            context.enqueueWork(() -> SmartMovingClient.onStateRelay(message));
            context.setPacketHandled(true);
        });
        CONFIG.addListener((NetworkEvent.ServerCustomPayloadEvent event) -> {
            NetworkEvent.Context context = event.getSource().get();
            ConfigSyncMessage message = ConfigSyncMessage.read(event.getPayload());
            context.enqueueWork(() -> SmartMovingClient.onConfigSync(message));
            context.setPacketHandled(true);
        });
    }
}
