package io.github.kdy05.smartmovingreborn.logic.jump;

import io.github.kdy05.smartmovingreborn.config.SmartMovingConfig;
import net.minecraft.util.Mth;

/**
 * The jump calculation shared by every Smart Moving jump: the original's {@code tryJump} (2002-2117) and
 * {@code getJumpMoving}, with the per type and speed factors of {@code SmartMovingClientConfig} (145-506).
 * Pure functions, so they can be tested against the original. The caller decides when to jump and applies
 * the result. Jump exhaustion joins with exhaustion in step 17.
 */
public final class JumpEngine {
    /** The original's ground speed cap for jump boosts ({@code getMaxHorizontalMotion}). */
    private static final float MAX_HORIZONTAL_MOTION = 0.11785204f;
    private static final float MAX_HORIZONTAL_MOTION_IN_WATER = 0.07839603f;

    private JumpEngine() {
    }

    /** The motion after a jump. {@link #y} is NaN when the jump leaves the vertical motion as it is. */
    public record Motion(double x, double y, double z) {
    }

    /** Whether this jump is switched on ({@code isJumpingEnabled}). */
    public static boolean enabled(SmartMovingConfig config, JumpSpeed speed, JumpType type) {
        return switch (type.base()) {
            case CHARGE -> config.jumpCharge.get();
            case SLIDE -> config.slide.get();
            case CLIMB_UP, CLIMB_UP_HANDS_ONLY -> config.climbUpJump.get();
            case CLIMB_BACK_UP, CLIMB_BACK_UP_HANDS_ONLY -> config.climbBackUpJump.get();
            case CLIMB_BACK_HEAD, CLIMB_BACK_HEAD_HANDS_ONLY -> config.climbBackHeadJump.get();
            case WALL_UP -> config.wallUpJump.get();
            case WALL_HEAD -> config.wallHeadJump.get();
            default -> switch (speed) {
                case SPRINT -> config.sprintJump.get();
                case RUN -> config.runJump.get();
                case WALK -> config.walkJump.get();
                case SNEAK -> config.sneakJump.get();
                case STAND -> config.standJump.get();
            };
        };
    }

    /** The vertical factor relative to a vanilla jump ({@code getJumpVerticalFactor}). */
    public static float verticalFactor(SmartMovingConfig config, JumpSpeed speed, JumpType type) {
        type = type.base();
        float result = config.jumpVerticalFactor.get();
        switch (type) {
            case ANGLE -> result *= config.angleJumpVerticalFactor.get();
            case CLIMB_UP -> result *= config.climbUpJumpVerticalFactor.get();
            case CLIMB_UP_HANDS_ONLY -> result *= config.climbUpJumpVerticalFactor.get()
                    * config.climbUpJumpHandsOnlyVerticalFactor.get();
            case CLIMB_BACK_UP -> result *= config.climbBackUpJumpVerticalFactor.get();
            case CLIMB_BACK_UP_HANDS_ONLY -> result *= config.climbBackUpJumpVerticalFactor.get()
                    * config.climbBackUpJumpHandsOnlyVerticalFactor.get();
            case CLIMB_BACK_HEAD -> result *= config.climbBackHeadJumpVerticalFactor.get();
            case CLIMB_BACK_HEAD_HANDS_ONLY -> result *= config.climbBackHeadJumpVerticalFactor.get()
                    * config.climbBackHeadJumpHandsOnlyVerticalFactor.get();
            case WALL_UP -> result *= config.wallUpJumpVerticalFactor.get();
            case WALL_HEAD -> result *= config.wallUpJumpVerticalFactor.get() * config.wallHeadJumpVerticalFactor.get();
            default -> {
            }
        }
        if (type.bySpeed()) {
            result *= switch (speed) {
                case SPRINT -> config.sprintJumpVerticalFactor.get();
                case RUN -> config.runJumpVerticalFactor.get();
                case WALK -> config.walkJumpVerticalFactor.get();
                case SNEAK -> config.sneakJumpVerticalFactor.get();
                case STAND -> config.standJumpVerticalFactor.get();
            };
        }
        return result;
    }

    /**
     * The horizontal factor relative to the current movement ({@code getJumpHorizontalFactor}). Standing jumps
     * keep no horizontal movement.
     */
    public static float horizontalFactor(SmartMovingConfig config, JumpSpeed speed, JumpType type) {
        type = type.base();
        float result = config.jumpHorizontalFactor.get();
        switch (type) {
            case ANGLE -> result *= config.angleJumpHorizontalFactor.get();
            case CLIMB_BACK_UP -> result *= config.climbBackUpJumpHorizontalFactor.get();
            case CLIMB_BACK_UP_HANDS_ONLY -> result *= config.climbBackUpJumpHorizontalFactor.get()
                    * config.climbBackUpJumpHandsOnlyHorizontalFactor.get();
            case CLIMB_BACK_HEAD -> result *= config.climbBackHeadJumpHorizontalFactor.get();
            case CLIMB_BACK_HEAD_HANDS_ONLY -> result *= config.climbBackHeadJumpHorizontalFactor.get()
                    * config.climbBackHeadJumpHandsOnlyHorizontalFactor.get();
            case WALL_UP -> result *= config.wallUpJumpHorizontalFactor.get();
            case WALL_HEAD -> result *= config.wallHeadJumpHorizontalFactor.get();
            default -> {
            }
        }
        if (type.bySpeed()) {
            result *= switch (speed) {
                case SPRINT -> config.sprintJumpHorizontalFactor.get();
                case RUN -> config.runJumpHorizontalFactor.get();
                case WALK -> config.walkJumpHorizontalFactor.get();
                case SNEAK -> config.sneakJumpHorizontalFactor.get();
                case STAND -> 0;
            };
        }
        return result;
    }

    /**
     * The horizontal speed a jump may boost to, before the player's speed factor ({@code getMaxHorizontalMotion}).
     */
    public static float maxHorizontalMotion(SmartMovingConfig config, JumpSpeed speed, boolean inWater) {
        float result = inWater ? MAX_HORIZONTAL_MOTION_IN_WATER : MAX_HORIZONTAL_MOTION;
        return switch (speed) {
            case SPRINT -> result * config.sprintFactor.get();
            case RUN -> result * config.runFactor.get();
            case SNEAK -> result * config.sneakFactor.get();
            default -> result;
        };
    }

    /** The vertical factor of a charged jump, growing linearly up to the maximum charge ({@code getJumpChargeFactor}). */
    public static float chargeFactor(SmartMovingConfig config, float charge) {
        if (!config.jumpCharge.get()) {
            return 1;
        }
        float maximum = config.jumpChargeMaximum.get();
        return 1 + Math.min(charge, maximum) / maximum * (config.jumpChargeFactor.get() - 1);
    }

    /** How flat a head jump goes, 0 to 1 of the normal jump angle ({@code getHeadJumpFactor}). */
    public static float headJumpFactor(SmartMovingConfig config, float headJumpCharge) {
        if (!config.headJump.get()) {
            return 1;
        }
        float maximum = config.headJumpChargeMaximum.get();
        return (Math.min(headJumpCharge, maximum) - 1) / (maximum - 1);
    }

    /** The vertical factor of the jump boost effect, 0 for none ({@code tryJump} 2041). */
    public static float jumpBoostFactor(int amplifier) {
        return amplifier < 0 ? 1 : 1 + (amplifier + 1) * 0.2f;
    }

    /**
     * The motion after a jump ({@code tryJump} 2045-2099).
     *
     * @param horizontalFactor     {@link #horizontalFactor} times the jump boost factor
     * @param verticalFactor       {@link #verticalFactor} times the jump boost factor
     * @param chargeFactor         {@link #chargeFactor} for a charged jump, otherwise 1
     * @param headJumpFactor       {@link #headJumpFactor} for a head jump
     * @param maxHorizontalMotion  the speed cap ({@link #maxHorizontalMotion} times the player's speed factor),
     *                             or NaN for none
     * @param jumpMotionX          the horizontal motion when the jump was decided
     * @param motionX              the current horizontal motion
     * @param angle                the direction in degrees (Minecraft yaw) for angled jumps, or NaN
     */
    public static Motion motion(JumpType type, float horizontalFactor, float verticalFactor, float chargeFactor,
                                float headJumpFactor, double maxHorizontalMotion,
                                double jumpMotionX, double jumpMotionZ, double motionX, double motionZ,
                                float angle) {
        JumpType base = type.base();
        if (!base.up()) {
            horizontalFactor = Mth.sqrt(horizontalFactor * horizontalFactor + verticalFactor * verticalFactor);
            verticalFactor = 0;
        }

        double horizontalMotion = Math.sqrt(jumpMotionX * jumpMotionX + jumpMotionZ * jumpMotionZ);
        double verticalMotion = -0.078 + 0.498 * verticalFactor * chargeFactor;

        if (base.head()) {
            double normalAngle = Math.atan(verticalMotion / horizontalMotion);
            double totalMotion = Math.sqrt(verticalMotion * verticalMotion + horizontalMotion * horizontalMotion);
            double newAngle = headJumpFactor * normalAngle;
            double newVerticalMotion = totalMotion * Math.sin(newAngle);
            double newHorizontalMotion = totalMotion * Math.cos(newAngle);
            maxHorizontalMotion *= newHorizontalMotion / horizontalMotion;
            verticalMotion = newVerticalMotion;
            horizontalMotion = newHorizontalMotion;
        }

        if (!Float.isNaN(angle)) {
            float jumpAngle = angle * Mth.DEG_TO_RAD;
            boolean reset = base.wall();
            double horizontal = Math.max(horizontalMotion, horizontalFactor);
            motionX = moving(jumpMotionX, -Math.sin(jumpAngle), reset, horizontal, horizontalFactor);
            motionZ = moving(jumpMotionZ, Math.cos(jumpAngle), reset, horizontal, horizontalFactor);
            horizontalMotion = 0;
            verticalMotion = verticalFactor;
        }

        if (horizontalMotion > 0) {
            double absoluteX = Math.abs(motionX) * horizontalFactor;
            double absoluteZ = Math.abs(motionZ) * horizontalFactor;
            if (!Double.isNaN(maxHorizontalMotion)) {
                absoluteX = Math.min(absoluteX, maxHorizontalMotion * horizontalFactor * Math.abs(motionX) / horizontalMotion);
                absoluteZ = Math.min(absoluteZ, maxHorizontalMotion * horizontalFactor * Math.abs(motionZ) / horizontalMotion);
            }
            motionX = Math.signum(motionX) * absoluteX;
            motionZ = Math.signum(motionZ) * absoluteZ;
        }

        return new Motion(motionX, base.up() && !type.noVertical() ? verticalMotion : Double.NaN, motionZ);
    }

    /** One horizontal component of an angled jump ({@code getJumpMoving}). Wall jumps replace the motion. */
    static double moving(double actual, double move, boolean reset, double horizontal, float horizontalFactor) {
        if (!reset) {
            return actual + move * horizontal;
        }
        return Math.signum(actual) != Math.signum(move)
                ? move * horizontalFactor
                : Math.max(Math.abs(actual), Math.abs(move) * horizontal) * Math.signum(move);
    }
}
