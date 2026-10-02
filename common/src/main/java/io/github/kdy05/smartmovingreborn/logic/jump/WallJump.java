package io.github.kdy05.smartmovingreborn.logic.jump;

/**
 * The direction of a wall jump ({@code handleWallJumping} 1948-2000), from the wall the player ran into
 * ({@code SmartRenderUtilities.getHorizontalCollisionangle} and {@code getAngle}). Angles are Minecraft yaws in
 * degrees: 0 faces +Z, 90 faces -X.
 */
public final class WallJump {
    private WallJump() {
    }

    /**
     * The direction towards the wall the movement was stopped by, or NaN for none or an ambiguous one. The
     * helper works in plain (x, y) angles; a yaw's x is Z and its y is -X. The original passed +X as +y, which
     * mirrored walls across the Z axis: against an X wall a turning jump went away from it, and corners
     * reflected back into themselves. Here -X is +y.
     *
     * @param positiveX whether moving towards +X was stopped
     */
    public static float collisionAngle(boolean positiveX, boolean negativeX, boolean positiveZ, boolean negativeZ) {
        return horizontalCollisionAngle(positiveZ, negativeZ, negativeX, positiveX);
    }

    /** {@code getHorizontalCollisionangle}, with the original's parameter names. */
    private static float horizontalCollisionAngle(boolean positiveX, boolean negativeX, boolean positiveZ,
                                                  boolean negativeZ) {
        if (positiveX) {
            if (!negativeX) {
                if (positiveZ) {
                    return negativeZ ? 0 : 45;
                }
                return negativeZ ? 315 : 0;
            }
        } else if (negativeX) {
            if (positiveZ) {
                return negativeZ ? 180 : 135;
            }
            return negativeZ ? 225 : 180;
        }
        if (positiveZ) {
            return negativeZ ? Float.NaN : 90;
        }
        return negativeZ ? 270 : Float.NaN;
    }

    /** {@code getAngle}: the angle of (x, y) in degrees, 0 to 360, or NaN for no direction. */
    static float angle(double x, double y) {
        if (x == 0) {
            if (y == 0) {
                return Float.NaN;
            }
            return y < 0 ? 270 : 90;
        }
        if (y == 0) {
            return x < 0 ? 180 : 0;
        }
        float angle = (float) Math.atan(y / x) * (180 / (float) Math.PI);
        if (x < 0) {
            return 180 + angle;
        }
        return y < 0 ? 360 + angle : angle;
    }

    /**
     * The yaw to jump at, or NaN when the player has no horizontal movement to reflect. Against a wall already
     * touched last tick the jump turns towards the wall ({@link JumpType#WALL_UP_TURN}); otherwise it reflects
     * the movement off the wall. Within {@code orthogonalTolerance} degrees of a right angle it snaps to it.
     * Unlike the original, which only wrapped angles above 360, negative angles snap too.
     *
     * @param collisionAngle {@link #collisionAngle}, not NaN
     * @param wasCollided    whether the player touched a wall at the start of the tick
     * @param motionX        the horizontal motion before this tick's movement
     */
    public static float jumpAngle(float collisionAngle, boolean wasCollided, double motionX, double motionZ,
                                  float orthogonalTolerance) {
        float jumpAngle;
        if (!wasCollided) {
            float movementAngle = angle(motionZ, -motionX);
            if (Float.isNaN(movementAngle)) {
                return Float.NaN;
            }
            jumpAngle = collisionAngle * 2 - movementAngle + 180;
        } else {
            jumpAngle = collisionAngle;
        }
        jumpAngle = ((jumpAngle % 360) + 360) % 360;

        if (orthogonalTolerance != 0) {
            float aligned = jumpAngle;
            while (aligned > 45) {
                aligned -= 90;
            }
            if (Math.abs(aligned) < orthogonalTolerance) {
                jumpAngle = Math.round(jumpAngle / 90) * 90;
            }
        }
        return jumpAngle;
    }
}
