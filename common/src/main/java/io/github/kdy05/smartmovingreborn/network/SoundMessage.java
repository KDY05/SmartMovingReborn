package io.github.kdy05.smartmovingreborn.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Client to server: a sound the sender's movement made, for the players around it (the original's sound packet). */
public record SoundMessage(ResourceLocation sound, float volume, float pitch) {
    public void write(FriendlyByteBuf buf) {
        buf.writeResourceLocation(sound);
        buf.writeFloat(volume);
        buf.writeFloat(pitch);
    }

    public static SoundMessage read(FriendlyByteBuf buf) {
        return new SoundMessage(buf.readResourceLocation(), buf.readFloat(), buf.readFloat());
    }
}
