package io.github.kdy05.smartmovingreborn.network;

import net.minecraft.network.FriendlyByteBuf;

/** Client to server: the sender's encoded {@link io.github.kdy05.smartmovingreborn.state.MovingState}. */
public record StateMessage(long state) {
    public void write(FriendlyByteBuf buf) {
        buf.writeLong(state);
    }

    public static StateMessage read(FriendlyByteBuf buf) {
        return new StateMessage(buf.readLong());
    }
}
