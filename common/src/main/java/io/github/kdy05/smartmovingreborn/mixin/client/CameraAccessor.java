package io.github.kdy05.smartmovingreborn.mixin.client;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Camera.class)
public interface CameraAccessor {
    /** The eye height the camera is easing towards the entity's, this tick and the last. */
    @Accessor("eyeHeight")
    float smartmovingreborn$getEyeHeight();

    @Accessor("eyeHeight")
    void smartmovingreborn$setEyeHeight(float eyeHeight);

    @Accessor("eyeHeightOld")
    float smartmovingreborn$getEyeHeightOld();

    @Accessor("eyeHeightOld")
    void smartmovingreborn$setEyeHeightOld(float eyeHeightOld);
}
