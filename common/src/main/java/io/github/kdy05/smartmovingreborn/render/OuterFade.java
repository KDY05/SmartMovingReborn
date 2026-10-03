package io.github.kdy05.smartmovingreborn.render;

import net.minecraft.util.Mth;

/**
 * One player's whole-body rotation (Smart Render's outer joint) as last drawn, which some moves ease towards
 * their target instead of jumping to it ({@code ModelRotationRenderer.fadeIntermediate} and {@code fadeStore},
 * with the previous values in {@code RendererData}). Like the original, it is stored on every frame, so a move
 * that fades starts from the pose before it.
 */
final class OuterFade {
    /** The tilt forward (outer {@code rotateAngleX}). */
    float xRot;
    /** The facing as a yaw in radians (outer {@code rotateAngleY}). */
    float yaw;
    /** The time of the last frame in ticks, NaN before the first. */
    float time = Float.NaN;
    /** The direction of the last horizontal movement as a yaw in radians, NaN before any ({@code prevHorizontalAngle}). */
    float horizontalAngle = Float.NaN;
    /** The entity's body yaw (degrees) to put back after drawing, NaN while nothing is replaced. */
    float bodyRot = Float.NaN;
    float bodyRotO;
    /** The view yaw minus the body's target yaw this frame, in radians (the original's pelvis turn in side jumps). */
    float viewOffset;
    /** The head yaw minus the body yaw before easing this frame, in radians ({@code viewHorizontalAngelOffset}). */
    float headOffset;

    /**
     * Moves to this frame's targets and stores them. Faded values ease towards their target by a fifth per tick
     * since the last frame; after a gap of more than two ticks (or going back in time) they jump.
     */
    void update(float xRot, boolean fadeX, float yaw, boolean fadeY, float time) {
        float elapsed = time - this.time;
        boolean recent = elapsed >= 0 && elapsed <= 2;
        this.xRot = recent && fadeX ? fade(this.xRot, xRot, elapsed) : xRot;
        this.yaw = recent && fadeY ? fade(this.yaw, yaw, elapsed) : yaw;
        this.time = time;
    }

    /** {@code GetIntermediateAngle}: eases from {@code previous} towards {@code target} the short way round. */
    static float fade(float previous, float target, float elapsedTicks) {
        if (previous == target) {
            return target;
        }
        previous = Mth.positiveModulo(previous, Mth.TWO_PI);
        target = Mth.positiveModulo(target, Mth.TWO_PI);
        if (target > previous && target - previous > Mth.PI) {
            previous += Mth.TWO_PI;
        }
        if (target < previous && previous - target > Mth.PI) {
            target += Mth.TWO_PI;
        }
        return previous + (target - previous) * elapsedTicks * 0.2f;
    }
}
