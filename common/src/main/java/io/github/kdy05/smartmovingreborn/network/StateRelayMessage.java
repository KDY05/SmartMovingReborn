package io.github.kdy05.smartmovingreborn.network;

import net.minecraft.network.FriendlyByteBuf;

/** Server to client: another player's encoded state, relayed to the players tracking them. */
public record StateRelayMessage(int entityId, long state) {
    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeLong(state);
    }

    public static StateRelayMessage read(FriendlyByteBuf buf) {
        return new StateRelayMessage(buf.readVarInt(), buf.readLong());
    }
}
