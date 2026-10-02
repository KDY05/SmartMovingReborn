package io.github.kdy05.smartmovingreborn.world;

import java.util.Set;

/** The ladder and vine questions of the original's {@code SmartMovingBase}, over a {@link ClimbTerrain}. */
public final class LadderSearch {
    /** How hard a climber is pushed back from a ladder above ({@code climbingUpIsBlockedByLadder}). */
    public static final float LADDER_PUSH = 0.07f;
    /** How hard a climber is pushed back from an open trap door above. */
    public static final float TRAP_DOOR_PUSH = 0.09f;
    /** How hard a climber is pushed back from a wall's post above. */
    public static final float WALL_PUSH = 0.07f;

    private LadderSearch() {
    }

    /**
     * Ladders and vines a player's box touches, up to {@code maxResult} ({@code getOnLadderOrVine} for the
     * modes but standard): in each block from {@code minY} to {@code maxY}, a ladder there, a ladder in a
     * neighbouring block fixed towards it, and a vine. With {@code facing}, only ladders fixed in a direction
     * it holds count, and only vines in such a direction against a solid block. Other climbable blocks never
     * count.
     *
     * @param facing the directions the player faces, or null for all
     */
    public static int count(ClimbTerrain terrain, int x, int minY, int maxY, int z, Set<ClimbOrientation> facing,
                            boolean ladder, boolean vine, int maxResult) {
        int result = 0;
        for (int y = minY; y <= maxY; y++) {
            if (ladder) {
                ClimbOrientation local = null;
                if (terrain.isLadder(x, y, z)) {
                    local = ladderOrientation(terrain, x, y, z);
                    if (facing == null || facing.contains(local)) {
                        result++;
                    }
                }
                for (ClimbOrientation direction : facing != null ? facing : ClimbOrientation.ORTHOGONALS) {
                    if (result >= maxResult) {
                        return result;
                    }
                    int remoteX = x + direction.x;
                    int remoteZ = z + direction.z;
                    if (direction != local && terrain.isLadder(remoteX, y, remoteZ)
                            && terrain.hasLadderOrientation(remoteX, y, remoteZ, direction.rotate(180))) {
                        result++;
                    }
                }
            }
            if (result >= maxResult) {
                return result;
            }
            if (vine && terrain.isVine(x, y, z)) {
                if (facing == null) {
                    result++;
                } else {
                    for (ClimbOrientation direction : facing) {
                        if (terrain.hasVineOrientation(x, y, z, direction)
                                && !terrain.isFullEmpty(x + direction.x, y, z + direction.z)) {
                            result++;
                            break;
                        }
                    }
                }
            }
            if (result >= maxResult) {
                return result;
            }
        }
        return result;
    }

    /** The direction towards the block a ladder is fixed to, or null ({@code getKnownLadderOrientation}). */
    public static ClimbOrientation ladderOrientation(ClimbTerrain terrain, int x, int y, int z) {
        for (ClimbOrientation orientation : ClimbOrientation.ORTHOGONALS) {
            if (terrain.hasLadderOrientation(x, y, z, orientation)) {
                return orientation;
            }
        }
        return null;
    }

    /**
     * How hard a player climbing towards {@code o} into the block above is pushed back, or NaN for not at all
     * ({@code climbingUpIsBlockedByLadder}, {@code ...TrapDoor}, {@code ...CobbleStoneWall}): a ladder fixed
     * towards {@code o}, a trap door open towards it, or a wall without a connection back towards the player.
     */
    public static float blockedPush(ClimbTerrain terrain, ClimbOrientation o, int x, int y, int z) {
        if (terrain.isLadder(x, y, z)) {
            return ladderOrientation(terrain, x, y, z) == o ? LADDER_PUSH : Float.NaN;
        }
        if (terrain.isTrapDoor(x, y, z)) {
            return !terrain.isClosedTrapDoor(x, y, z) && terrain.isTrapDoorFront(x, y, z, o)
                    ? TRAP_DOOR_PUSH : Float.NaN;
        }
        if (terrain.isWall(x, y, z)) {
            return !terrain.wallConnects(x, y, z, o.rotate(180), o) ? WALL_PUSH : Float.NaN;
        }
        return Float.NaN;
    }
}
