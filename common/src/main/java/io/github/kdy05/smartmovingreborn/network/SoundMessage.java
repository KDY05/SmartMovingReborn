package io.github.kdy05.smartmovingreborn.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client to server: a sound the sender's movement made, for the players around it (the original's sound packet). */
public record SoundMessage(ResourceLocation sound, float volume, float pitch) implements CustomPacketPayload {
    public static final Type<SoundMessage> TYPE = new Type<>(Network.id("sound"));
    public static final StreamCodec<FriendlyByteBuf, SoundMessage> STREAM_CODEC =
            CustomPacketPayload.codec(SoundMessage::write, SoundMessage::read);

    public void write(FriendlyByteBuf buf) {
        buf.writeResourceLocation(sound);
        buf.writeFloat(volume);
        buf.writeFloat(pitch);
    }

    public static SoundMessage read(FriendlyByteBuf buf) {
        return new SoundMessage(buf.readResourceLocation(), buf.readFloat(), buf.readFloat());
    }

    @Override
    public Type<SoundMessage> type() {
        return TYPE;
    }
}
