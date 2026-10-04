package io.github.kdy05.smartmovingreborn.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: the sender's encoded {@link io.github.kdy05.smartmovingreborn.state.MovingState}. */
public record StateMessage(long state) implements CustomPacketPayload {
    public static final Type<StateMessage> TYPE = new Type<>(Network.id("state"));
    public static final StreamCodec<FriendlyByteBuf, StateMessage> STREAM_CODEC =
            CustomPacketPayload.codec(StateMessage::write, StateMessage::read);

    public void write(FriendlyByteBuf buf) {
        buf.writeLong(state);
    }

    public static StateMessage read(FriendlyByteBuf buf) {
        return new StateMessage(buf.readLong());
    }

    @Override
    public Type<StateMessage> type() {
        return TYPE;
    }
}
