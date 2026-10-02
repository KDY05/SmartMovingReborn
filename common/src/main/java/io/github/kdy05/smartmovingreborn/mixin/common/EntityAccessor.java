package io.github.kdy05.smartmovingreborn.mixin.common;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Entity.class)
public interface EntityAccessor {
    /** The slowdown of a cobweb or berry bush, which {@code move} applies to the movement and then clears. */
    @Accessor("stuckSpeedMultiplier")
    Vec3 smartmovingreborn$getStuckSpeedMultiplier();
}
