package io.github.kdy05.smartmovingreborn.world;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The eight horizontal directions a player can climb towards, and none ({@link #ZZ}), from the original's
 * {@code Orientation}. The name gives the X and Z steps: {@code P} plus one, {@code N} minus one, {@code Z} zero.
 * Each direction has the yaw (degrees, Minecraft's: 0 is +Z, 90 is -X) a player faces when looking along it.
 */
public enum ClimbOrientation {
    ZZ(0, 0, Float.NaN),
    PZ(1, 0, 270),
    ZP(0, 1, 0),
    NZ(-1, 0, 90),
    ZN(0, -1, 180),
    PP(1, 1, 315),
    NN(-1, -1, 135),
    PN(1, -1, 225),
    NP(-1, 1, 45);

    /** The four axis directions, in the order the original searched them. */
    public static final List<ClimbOrientation> ORTHOGONALS = List.of(PZ, NZ, ZP, ZN);
    /** The four diagonal directions, in the order the original searched them. */
    public static final List<ClimbOrientation> DIAGONALS = List.of(PP, NP, NN, PN);

    public final int x;
    public final int z;
    public final boolean diagonal;
    private final float yaw;

    ClimbOrientation(int x, int z, float yaw) {
        this.x = x;
        this.z = z;
        this.diagonal = x != 0 && z != 0;
        this.yaw = yaw;
    }

    /** The direction with the given steps, or null for steps outside -1 to 1. */
    public static ClimbOrientation of(int x, int z) {
        for (ClimbOrientation orientation : values()) {
            if (orientation.x == x && orientation.z == z) {
                return orientation;
            }
        }
        return null;
    }

    /** This direction turned by {@code angle} degrees, a multiple of 45; positive turns from +X towards +Z. */
    public ClimbOrientation rotate(int angle) {
        if (this == ZZ) {
            throw new IllegalStateException("unrotatable orientation");
        }
        if (angle % 45 != 0) {
            throw new IllegalArgumentException("angle \"" + angle + "\" not supported");
        }
        ClimbOrientation result = this;
        for (int step = Math.floorMod(angle / 45, 8); step > 0; step--) {
            result = of(Integer.signum(result.x - result.z), Integer.signum(result.x + result.z));
        }
        return result;
    }

    /**
     * Whether a player facing {@code yaw} can grab towards this direction ({@code isRotationForClimbing}): the
     * yaw lies within half of the configured grabbing angle on either side. The original used the whole diagonal
     * angle as the half angle; this halves both, like the 1.10.2 version.
     */
    public boolean isRotationForClimbing(float yaw, float orthogonalAngle, float diagonalAngle) {
        if (this == ZZ) {
            return true;
        }
        float halfAngle = (diagonal ? diagonalAngle : orthogonalAngle) / 2;
        return isWithinAngle(normalize(yaw), normalize(this.yaw - halfAngle), normalize(this.yaw + halfAngle));
    }

    /** The directions a player facing {@code yaw} can grab towards ({@code getClimbingOrientations}). */
    public static Set<ClimbOrientation> climbingOrientations(float yaw, boolean orthogonals, boolean diagonals,
                                                             float orthogonalAngle, float diagonalAngle) {
        Set<ClimbOrientation> result = EnumSet.noneOf(ClimbOrientation.class);
        if (orthogonals) {
            addFor(result, ORTHOGONALS, yaw, orthogonalAngle, diagonalAngle);
        }
        if (diagonals) {
            addFor(result, DIAGONALS, yaw, orthogonalAngle, diagonalAngle);
        }
        return result;
    }

    private static void addFor(Set<ClimbOrientation> result, List<ClimbOrientation> candidates, float yaw,
                               float orthogonalAngle, float diagonalAngle) {
        for (ClimbOrientation orientation : candidates) {
            if (orientation.isRotationForClimbing(yaw, orthogonalAngle, diagonalAngle)) {
                result.add(orientation);
            }
        }
    }

    /**
     * The first direction within {@code tolerance} degrees of {@code yaw} ({@code getOrientation}), axis
     * directions first, or null.
     */
    public static ClimbOrientation facing(float yaw, float tolerance, boolean orthogonals, boolean diagonals) {
        float rotation = normalize(yaw);
        float minimum = normalize(rotation - tolerance);
        float maximum = normalize(rotation + tolerance);
        if (orthogonals) {
            for (ClimbOrientation orientation : List.of(NZ, PZ, ZN, ZP)) {
                if (isWithinAngle(orientation.yaw, minimum, maximum)) {
                    return orientation;
                }
            }
        }
        if (diagonals) {
            for (ClimbOrientation orientation : List.of(NP, PN, NN, PP)) {
                if (isWithinAngle(orientation.yaw, minimum, maximum)) {
                    return orientation;
                }
            }
        }
        return null;
    }

    /**
     * How far {@code (x, z)} is from the block border ahead in this axis direction, 0 for the others
     * ({@code getHorizontalBorderGap}). The original took the remainder with Java's {@code %}, which is negative
     * for negative coordinates; this uses the position within the block, so it is the same for both signs.
     */
    public double horizontalBorderGap(double x, double z) {
        return switch (this) {
            case NZ -> x - Math.floor(x);
            case PZ -> 1 - (x - Math.floor(x));
            case ZN -> z - Math.floor(z);
            case ZP -> 1 - (z - Math.floor(z));
            default -> 0;
        };
    }

    private static float normalize(float angle) {
        float result = angle % 360;
        return result < 0 ? result + 360 : result;
    }

    private static boolean isWithinAngle(float rotation, float minimum, float maximum) {
        return minimum > maximum
                ? rotation >= minimum || rotation <= maximum
                : rotation >= minimum && rotation <= maximum;
    }
}
