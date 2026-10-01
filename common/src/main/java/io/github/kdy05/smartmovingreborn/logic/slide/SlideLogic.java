package io.github.kdy05.smartmovingreborn.logic.slide;

import net.minecraft.util.Mth;

/**
 * The sliding and head jumping decisions of the original's {@code updateEntityActionState}
 * ({@code SmartMovingSelf} 2412-2464) and the sliding friction and steering of {@code landMotion} (757-786), as
 * pure functions of what the caller measured.
 * <p>
 * Like crawling, both moves use vanilla's {@code Pose.SWIMMING} instead of the original's raised box bottom. The
 * position changes that kept the box apart from the model are made by the caller.
 * <p>
 * The original's head-first landing damage ({@code handleCrash}) is not ported: it ran the tick after landing,
 * when vanilla had already reset the fall distance, so it never hurt. Landing head first hurts more only through
 * vanilla's fall damage on the server, which follows the box and so falls a block further.
 */
public final class SlideLogic {
    /** Fall distance after which a slide turns into a head jump (2435). */
    public static final float SLIDE_FALL_DISTANCE = 0.05f;
    /** The horizontal damping in the air while gliding out of a slide ({@code isAerodynamic}, 783). */
    public static final float AERODYNAMIC_DAMPING = 0.999f;

    private SlideLogic() {
    }

    /**
     * Whether a slide starts: sneak pressed while holding grab during Smart Moving's sprint or vanilla's
     * sprint on the ground, out of water and not crawling. The original took running that the sneak press had
     * just stopped; vanilla no longer stops running on sneak, so running itself counts.
     */
    public static boolean startSlide(boolean slideEnabled, boolean grabPressed, boolean groundSprinting,
                                     boolean running, boolean onGround, boolean crawling, boolean sneakStarted,
                                     boolean inWater) {
        return slideEnabled && grabPressed && (groundSprinting || running && onGround) && !crawling
                && sneakStarted && !inWater;
    }

    /** Whether a slide ends in crawling: sneak released, or slower than {@code move.slide.speed.stop.factor}. */
    public static boolean stopSlide(boolean sneakPressed, double horizontalSpeedSquare, float speedStopFactor) {
        return !sneakPressed || horizontalSpeedSquare < speedStopFactor * 0.01;
    }

    /**
     * Whether a head jump goes on: in the air, not swimming or flying, not sinking in water and not in lava.
     *
     * @param swimming swimming or flying, the moves that end a head jump
     */
    public static boolean continueHeadJump(boolean headJumping, boolean onGround, boolean swimming,
                                           boolean sinkingInWater, boolean inLava) {
        return headJumping && !onGround && !swimming && !sinkingInWater && !inLava;
    }

    /**
     * The horizontal damping per tick while sliding on a block. Vanilla damps by {@code friction * 0.91}; a
     * slide keeps much more, more on slippery blocks, and {@code move.slide.glide.factor} scales the loss.
     */
    public static float damping(float friction, float glideFactor) {
        return 1 / ((1 / friction - 1) / 25 * glideFactor + 1) * 0.98f;
    }

    /**
     * The horizontal motion turned by {@code move.slide.control.angle} degrees against the strafe input, keeping
     * its speed. Returns null when there is nothing to turn.
     *
     * @return {x, z} or null
     */
    public static double[] steer(double motionX, double motionZ, float strafe, float controlDegrees) {
        if (strafe == 0 || controlDegrees <= 0) {
            return null;
        }
        double angle = -Math.atan(motionX / motionZ);
        if (Double.isNaN(angle)) {
            return null;
        }
        if (motionZ < 0) {
            angle += Math.PI;
        }
        angle -= controlDegrees * Mth.DEG_TO_RAD * Math.signum(strafe);
        double speed = Math.sqrt(motionX * motionX + motionZ * motionZ);
        return new double[]{speed * -Math.sin(angle), speed * Math.cos(angle)};
    }
}
