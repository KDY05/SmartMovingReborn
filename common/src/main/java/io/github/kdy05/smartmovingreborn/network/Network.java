package io.github.kdy05.smartmovingreborn.network;

import dev.architectury.injectables.annotations.ExpectPlatform;
import io.github.kdy05.smartmovingreborn.SmartMovingReborn;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Loader-specific packet transport. Sending is skipped whenever the other side lacks the mod's channel,
 * so modded and vanilla clients and servers can connect to each other.
 */
public final class Network {
    private Network() {
    }

    /** A payload's channel id, the same on both loaders so that Fabric and NeoForge peers understand each other. */
    static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(SmartMovingReborn.MOD_ID, path);
    }

    /** Client only: whether the connected server accepts Smart Moving packets. */
    @ExpectPlatform
    public static boolean isServerPresent() {
        throw new AssertionError();
    }

    /** Client only. Call only when {@link #isServerPresent()} is true. */
    @ExpectPlatform
    public static void sendToServer(StateMessage message) {
        throw new AssertionError();
    }

    /** Client only. Call only when {@link #isServerPresent()} is true. */
    @ExpectPlatform
    public static void sendSoundToServer(SoundMessage message) {
        throw new AssertionError();
    }

    /** Whether {@code player}'s client registered the Smart Moving channels. See {@link ServerNetworkHandler#hasMod}. */
    @ExpectPlatform
    public static boolean canSendTo(ServerPlayer player) {
        throw new AssertionError();
    }

    /** Call only when {@link ServerNetworkHandler#hasMod} is true. */
    @ExpectPlatform
    public static void sendTo(ServerPlayer player, StateRelayMessage message) {
        throw new AssertionError();
    }

    /** Call only when {@link ServerNetworkHandler#hasMod} is true. */
    @ExpectPlatform
    public static void sendTo(ServerPlayer player, ConfigSyncMessage message) {
        throw new AssertionError();
    }
}
