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
    /** Fabric channel ids. Forge multiplexes the messages over one {@code SimpleChannel}. */
    public static final ResourceLocation STATE_ID = new ResourceLocation(SmartMovingReborn.MOD_ID, "state");
    public static final ResourceLocation RELAY_ID = new ResourceLocation(SmartMovingReborn.MOD_ID, "relay");
    public static final ResourceLocation SOUND_ID = new ResourceLocation(SmartMovingReborn.MOD_ID, "sound");
    public static final ResourceLocation CONFIG_ID = new ResourceLocation(SmartMovingReborn.MOD_ID, "config");

    private Network() {
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

    /** Whether {@code player}'s client accepts Smart Moving packets. */
    @ExpectPlatform
    public static boolean canSendTo(ServerPlayer player) {
        throw new AssertionError();
    }

    /** Call only when {@link #canSendTo} is true. */
    @ExpectPlatform
    public static void sendTo(ServerPlayer player, StateRelayMessage message) {
        throw new AssertionError();
    }

    /** Call only when {@link #canSendTo} is true. */
    @ExpectPlatform
    public static void sendTo(ServerPlayer player, ConfigSyncMessage message) {
        throw new AssertionError();
    }
}
