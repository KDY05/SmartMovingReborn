package io.github.kdy05.smartmovingreborn.mixin.server;

import net.minecraft.server.network.ServerPlayerConnection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;

@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public interface TrackedEntityAccessor {
    /** The connections of the players that currently see this entity. */
    @Accessor("seenBy")
    Set<ServerPlayerConnection> smartmovingreborn$getSeenBy();
}
