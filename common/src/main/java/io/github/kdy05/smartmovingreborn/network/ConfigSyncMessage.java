package io.github.kdy05.smartmovingreborn.network;

import io.github.kdy05.smartmovingreborn.config.SmartMovingServerConfig;
import net.minecraft.network.FriendlyByteBuf;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Server to client, in answer to the first state a client sends: whether the server enforces its movement rules
 * ({@code move.server.config}) and, if so, all of them as {@code key → value} (the original's config content
 * packet). The client stays inactive until it has this answer.
 */
public record ConfigSyncMessage(boolean enforced, Map<String, String> rules) {
    public static ConfigSyncMessage of(SmartMovingServerConfig config) {
        return config.serverConfig.get()
                ? new ConfigSyncMessage(true, config.writeMovementRules())
                : new ConfigSyncMessage(false, Map.of());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(enforced);
        buf.writeMap(rules, FriendlyByteBuf::writeUtf, FriendlyByteBuf::writeUtf);
    }

    public static ConfigSyncMessage read(FriendlyByteBuf buf) {
        boolean enforced = buf.readBoolean();
        return new ConfigSyncMessage(enforced,
                buf.readMap(LinkedHashMap::new, FriendlyByteBuf::readUtf, FriendlyByteBuf::readUtf));
    }
}
