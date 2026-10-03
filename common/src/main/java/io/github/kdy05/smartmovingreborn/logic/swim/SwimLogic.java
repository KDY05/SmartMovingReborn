package io.github.kdy05.smartmovingreborn.logic.swim;

import net.minecraft.util.Mth;

/**
 * The swimming decisions of the original's {@code handleSwimming} ({@code SmartMovingSelf} 283-611) and the
 * swimming parts of {@code updateEntityActionState}, as pure functions of what the caller measured, so they can be
 * tested against the original.
 * <p>
 * All heights are the original's, measured with the standing box: the depth is how far the water's surface lies
 * above the feet the original's standing box would have.
 */
public final class SwimLogic {
    /** Swimming, diving or dipping crawlers in water shallower than this (from the floor) go on crawling on land. */
    public static final double CRAWL_WATER_DEPTH = 0.65;
    /** A shallow swim or dive with less water than this above the floor stands up or crawls. */
    public static final double SHALLOW_STAND_DEPTH = 0.55;
    /** Swimming or diving this many ticks in a row lets a swimmer jump out of the water at a wall. */
    public static final int JUMP_OUT_TICKS = 10;
    /** The vertical motion of jumping out of the water. */
    public static final double JUMP_OUT_MOTION = 0.3;
    /** What vanilla's jump in water adds each tick, which the original took back while swimming. */
    public static final double VANILLA_LIQUID_JUMP = 0.04;

    private SwimLogic() {
    }

    /**
     * Which of the three water moves the depth and input make, and the vertical push towards the depth it keeps
     * ({@code handleSwimming} 322-484). Neither of them for crawlers, whose water depth is checked separately, and
     * for a depth below the feet, which the original rejected.
     *
     * @param crawlingLike       crawling, climb crawling or crawl climbing (the original then only set dipping)
     * @param depth              the water's surface above the feet ({@code playerSwimWaterBorder})
     * @param diveUp             jumping
     * @param diveDown           sneaking with {@code move.dive.down.sneak}
     * @param swimDown           sneaking with {@code move.swim.down.sneak}, unless taken as sneaking in shallow water
     * @param moveSwim           looking up and moving forward, or looking down and moving back
     * @param wantShallowSwim    swimming or diving on in water the player could stand in
     * @param speedFactor        the speed factor ({@code getSpeedFactor})
     * @param fastFactor         the sprint factor while sprinting, otherwise 1
     * @param fastSurfaceUp      sprinting up, less than 2.5 under the surface, with air three blocks above the feet
     */
    public static Zone zone(boolean crawlingLike, double depth, boolean diveUp, boolean diveDown, boolean swimDown,
                            boolean moveSwim, boolean wantShallowSwim, float speedFactor, float fastFactor,
                            boolean fastSurfaceUp) {
        if (crawlingLike) {
            return new Zone(Kind.NONE, 0, false);
        }
        if (depth >= 0 && depth <= 2) {
            double offset = depth + 0.1625;
            if (!diveUp && !moveSwim && !wantShallowSwim) {
                if (offset < 1.5) {
                    return new Zone(Kind.DIPPING, -0.02, false);
                }
                return new Zone(Kind.DIVING, diveDown ? 0.01 - 0.1 * speedFactor : diveFloat(offset), false);
            }
            if (offset < 1.4) {
                return new Zone(Kind.DIPPING, offset < 1 ? -0.02 : -0.01, false);
            }
            if (offset < 1.9) {
                // The original's sneaking push down was overwritten by the float right after it.
                return new Zone(Kind.SWIMMING, swimFloat(offset), false);
            }
            double diff;
            if (diveUp) {
                diff = 0.05 * fastFactor;
            } else if (diveDown) {
                diff = 0.01 - 0.1 * speedFactor;
            } else {
                diff = moveSwim ? 0.04 : 0.02;
            }
            return new Zone(Kind.DIVING, diff, false);
        }
        if (depth > 2) {
            double diff;
            if (diveUp) {
                diff = fastSurfaceUp ? 0.11 / fastFactor : 0.01 + 0.1 * speedFactor;
            } else if (diveDown) {
                diff = 0.01 - 0.1 * speedFactor;
            } else {
                diff = 0.01;
            }
            return new Zone(Kind.DIVING, diff, false);
        }
        return new Zone(Kind.NONE, 0, true);
    }

    /**
     * The vertical push of a swimmer at the surface: down below the depth of 1.668 (offset) the swimmer floats at,
     * up above it. The original computed {@code swimDown}'s push here and then overwrote it with this.
     */
    private static double swimFloat(double offset) {
        if (offset < 1.6) {
            return -0.01;
        } else if (offset < 1.62) {
            return -0.005;
        } else if (offset < 1.64) {
            return -0.0025;
        } else if (offset < 1.66) {
            return -0.00125;
        } else if (offset < 1.664) {
            return -6.25E-4;
        } else if (offset < 1.668) {
            return 0;
        } else if (offset < 1.672) {
            return 6.25E-4;
        } else if (offset < 1.676) {
            return 0.00125;
        } else if (offset < 1.68) {
            return 0.0025;
        } else if (offset < 1.7) {
            return 0.005;
        } else if (offset < 1.8) {
            return 0.01;
        }
        return 0.02;
    }

    /** The vertical push of a diver resting near the surface, floating at the offset of 1.868. */
    private static double diveFloat(double offset) {
        if (offset < 1.8) {
            return -0.02;
        } else if (offset < 1.82) {
            return -0.01;
        } else if (offset < 1.84) {
            return -0.005;
        } else if (offset < 1.86) {
            return -0.0025;
        } else if (offset < 1.864) {
            return -0.00125;
        } else if (offset < 1.868) {
            return 0;
        } else if (offset < 1.872) {
            return 0.00125;
        } else if (offset < 1.876) {
            return 0.0025;
        } else if (offset < 1.88) {
            return 0.005;
        }
        return 0.01;
    }

    /**
     * Whether a swimmer's sneaking pushes down. Sneaking in water the player could stand in counts as sneaking
     * instead ({@code isFakeShallowWaterSneaking}), which the caller notes.
     */
    public static boolean swimDown(boolean sneak, boolean swimDownOnSneak, boolean wasSwimming,
                                   boolean wantShallowSwim) {
        return sneak && swimDownOnSneak && !(wasSwimming && wantShallowSwim);
    }

    /**
     * Whether a dipping player stands high enough in its block to jump out of the water (1899-1900): the
     * original's {@code posY}, 1.62 above the feet, more than 0.6 (0.37 sneaking) into its block.
     */
    public static boolean dippingJumpHeight(double feet, boolean slow) {
        double y = feet + 1.62;
        return y - Math.floor(y) > (slow ? 0.37 : 0.6);
    }

    /** The horizontal damping of each move; dipping keeps more of the vertical motion. */
    public static double horizontalDamping(Kind kind) {
        return switch (kind) {
            case SWIMMING -> 0.85;
            case DIVING -> 0.83;
            case DIPPING -> 0.8;
            case NONE -> 0.9;
        };
    }

    /** The vertical damping of each move. */
    public static double verticalDamping(Kind kind) {
        return switch (kind) {
            case SWIMMING, NONE -> 0.85;
            case DIVING, DIPPING -> 0.83;
        };
    }

    /**
     * Whether the swimmer jumps out of the water at a wall ({@code isJumpingOutOfWater}): moving into it with jump
     * held and not sneaking, after swimming a while, from the ground, or on from the last tick.
     */
    public static boolean jumpOutOfWater(boolean moving, boolean collidedHorizontally, boolean diveUp, boolean slow,
                                         int waterMovementTicks, boolean onGround, boolean wasJumpingOut) {
        return moving && collidedHorizontally && diveUp && !slow
                && (waterMovementTicks > JUMP_OUT_TICKS || onGround || wasJumpingOut);
    }

    /**
     * The acceleration of a diver ({@code SmartMovingBase.moveFlying}): the input direction, tilted up or down by
     * the view when {@code threeDimensional} ({@code move.dive.control.vertical}), plus {@code up}, all scaled
     * to {@code speed}.
     * <p>
     * The original divided by the fourth root of the horizontal length squared plus the vertical length squared
     * (a square root too many); kept, since the diving speed depends on it.
     *
     * @return the change in motion x, y and z
     */
    public static double[] moveFlying(float up, float strafe, float forward, float speed, float yaw, float pitch,
                                      boolean threeDimensional) {
        float strafeX = 0;
        float forwardX = 0;
        float strafeZ = 0;
        float forwardZ = 0;
        float total = Mth.sqrt(strafe * strafe + forward * forward);
        if (total >= 0.01f) {
            if (total < 1) {
                total = 1;
            }
            float strafeFactor = strafe / total;
            float forwardFactor = forward / total;
            float sin = Mth.sin(yaw * Mth.DEG_TO_RAD);
            float cos = Mth.cos(yaw * Mth.DEG_TO_RAD);
            strafeX = strafeFactor * cos;
            forwardX = -forwardFactor * sin;
            strafeZ = strafeFactor * sin;
            forwardZ = forwardFactor * cos;
        }
        float angle = threeDimensional ? pitch * Mth.DEG_TO_RAD : 0;
        float horizontalFactor = Mth.cos(angle);
        float verticalFactor = -Mth.sin(angle) * Math.signum(forward);
        float x = forwardX * horizontalFactor + strafeX;
        float y = Mth.sqrt(forwardX * forwardX + forwardZ * forwardZ) * verticalFactor + up;
        float z = forwardZ * horizontalFactor + strafeZ;
        float length = Mth.sqrt(Mth.sqrt(x * x + z * z) + y * y);
        if (length <= 0.01f) {
            return new double[]{0, 0, 0};
        }
        float factor = speed / length;
        return new double[]{x * factor, y * factor, z * factor};
    }

    /**
     * How much faster vanilla moves in water with Depth Strider, Dolphin's Grace and Forge's swim speed attribute
     * than without them: the ratio of vanilla's top speeds, its acceleration over one minus its damping
     * ({@code LivingEntity.travel}). The original knew none of these; Smart Moving's water acceleration is
     * multiplied by it (user decision, 2026-10-03).
     *
     * @param sprinting     whether vanilla sprints (its damping is then 0.9 instead of 0.8)
     * @param depthStrider  the Depth Strider level
     * @param movementSpeed the movement speed attribute, which Depth Strider accelerates towards
     * @param swimSpeed     Forge's swim speed attribute, 1 on Fabric
     */
    public static float enhancementFactor(boolean sprinting, float depthStrider, boolean onGround,
                                          float movementSpeed, boolean dolphinsGrace, float swimSpeed) {
        float baseDamping = sprinting ? 0.9f : 0.8f;
        float baseAcceleration = 0.02f;
        float damping = baseDamping;
        float acceleration = baseAcceleration;
        float strider = Math.min(depthStrider, 3);
        if (!onGround) {
            strider *= 0.5f;
        }
        if (strider > 0) {
            damping += (0.54600006f - damping) * strider / 3;
            acceleration += (movementSpeed - acceleration) * strider / 3;
        }
        if (dolphinsGrace) {
            damping = 0.96f;
        }
        acceleration *= swimSpeed;
        return acceleration / (1 - damping) / (baseAcceleration / (1 - baseDamping));
    }

    /**
     * The water part of the sprint wish ({@code wantSprint} 2485-2486): swimming with a move key or sneaking down,
     * diving with a move key, jump, or sneaking down; the forward key is not needed.
     */
    public static boolean sprintInput(boolean swimming, boolean diving, boolean movePressed, boolean sneakPressed,
                                      boolean jumpPressed, boolean swimDownOnSneak, boolean diveDownOnSneak) {
        return swimming && (movePressed || sneakPressed && swimDownOnSneak)
                || diving && (movePressed || jumpPressed || sneakPressed && diveDownOnSneak);
    }

    /**
     * Whether sneaking can mean sneaking ({@code wouldWantSneak} 2470-2471): not while diving or swimming where
     * sneak dives down, except sneaking in shallow water.
     */
    public static boolean allowsSneak(boolean swimming, boolean diving, boolean swimDownOnSneak,
                                      boolean diveDownOnSneak, boolean fakeShallowWaterSneaking) {
        return (!diving || !diveDownOnSneak) && (!swimming || !swimDownOnSneak || fakeShallowWaterSneaking);
    }

    /** The water moves. */
    public enum Kind {
        NONE, SWIMMING, DIVING, DIPPING
    }

    /**
     * @param kind        the move the depth makes, {@link Kind#NONE} for crawlers and rejected depths
     * @param motionYDiff the vertical push
     * @param rejected    whether the original left this tick to the land movement
     */
    public record Zone(Kind kind, double motionYDiff, boolean rejected) {
    }
}
