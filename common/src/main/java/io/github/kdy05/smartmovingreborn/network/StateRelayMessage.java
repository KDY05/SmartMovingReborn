package io.github.kdy05.smartmovingreborn.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: another player's encoded state, relayed to the players tracking them. */
public record StateRelayMessage(int entityId, long state) implements CustomPacketPayload {
    public static final Type<StateRelayMessage> TYPE = new Type<>(Network.id("relay"));
    public static final StreamCodec<FriendlyByteBuf, StateRelayMessage> STREAM_CODEC =
            CustomPacketPayload.codec(StateRelayMessage::write, StateRelayMessage::read);

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeLong(state);
    }

    public static StateRelayMessage read(FriendlyByteBuf buf) {
        return new StateRelayMessage(buf.readVarInt(), buf.readLong());
    }

    @Override
    public Type<StateRelayMessage> type() {
        return TYPE;
    }
}
