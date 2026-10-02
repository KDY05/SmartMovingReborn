package io.github.kdy05.smartmovingreborn.mixin.server;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerGamePacketListenerImpl.class)
public interface ServerGamePacketListenerImplAccessor {
    /** The ticks the player has been in the air, after which the server kicks it for flying. */
    @Accessor("aboveGroundTickCount")
    void smartmovingreborn$setAboveGroundTickCount(int ticks);
}
