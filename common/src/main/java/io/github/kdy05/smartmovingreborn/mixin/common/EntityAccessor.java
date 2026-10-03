package io.github.kdy05.smartmovingreborn.mixin.common;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Entity.class)
public interface EntityAccessor {
    /** The slowdown of a cobweb or berry bush, which {@code move} applies to the movement and then clears. */
    @Accessor("stuckSpeedMultiplier")
    Vec3 smartmovingreborn$getStuckSpeedMultiplier();

    /** The block whose friction the land movement uses. */
    @Invoker("getBlockPosBelowThatAffectsMyMovement")
    BlockPos smartmovingreborn$getBlockPosBelowThatAffectsMyMovement();
}
