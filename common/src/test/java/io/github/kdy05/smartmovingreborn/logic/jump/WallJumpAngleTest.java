package io.github.kdy05.smartmovingreborn.logic.jump;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WallJumpAngleTest {
    private static final float TOLERANCE = 5;
    private static final double SPEED = 0.2;

    /** The jump angle when moving at {@code yaw} into a wall at {@code wallAngle}. */
    private static float reflect(float wallAngle, double yaw) {
        double radians = Math.toRadians(yaw);
        return WallJump.jumpAngle(wallAngle, false, -Math.sin(radians) * SPEED, Math.cos(radians) * SPEED, TOLERANCE);
    }

    @Test
    void collisionAnglePointsTowardsTheWall() {
        assertEquals(0, WallJump.collisionAngle(false, false, true, false));
        assertEquals(180, WallJump.collisionAngle(false, false, false, true));
        assertEquals(270, WallJump.collisionAngle(true, false, false, false));
        assertEquals(90, WallJump.collisionAngle(false, true, false, false));
    }

    @Test
    void cornersPointIntoTheCorner() {
        assertEquals(315, WallJump.collisionAngle(true, false, true, false));
        assertEquals(45, WallJump.collisionAngle(false, true, true, false));
        assertEquals(135, WallJump.collisionAngle(false, true, false, true));
        assertEquals(225, WallJump.collisionAngle(true, false, false, true));
    }

    @Test
    void noOrOpposingCollisionsHaveNoAngle() {
        assertTrue(Float.isNaN(WallJump.collisionAngle(false, false, false, false)));
        assertTrue(Float.isNaN(WallJump.collisionAngle(true, true, false, false)));
        assertTrue(Float.isNaN(WallJump.collisionAngle(false, false, true, true)));
    }

    @Test
    void headOnJumpsGoStraightBack() {
        assertEquals(180, reflect(0, 0), 1e-3);
        assertEquals(90, reflect(270, 270), 1e-3);
        assertEquals(0, reflect(180, 180), 1e-3);
        assertEquals(270, reflect(90, 90), 1e-3);
    }

    @Test
    void obliqueJumpsKeepTheMovementAlongTheWall() {
        // Moving +X and +Z (yaw 315) into a +Z wall: Z reverses, X stays (yaw 225).
        assertEquals(225, reflect(0, 315), 1e-3);
        // The same movement into a +X wall: X reverses, Z stays (yaw 45).
        assertEquals(45, reflect(270, 315), 1e-3);
    }

    @Test
    void cornersReflectBackOut() {
        assertEquals(135, reflect(315, 315), 1e-3);
    }

    @Test
    void nearlyOrthogonalJumpsSnapOnBothSides() {
        assertEquals(180, reflect(0, 3), 1e-3);
        // The original left this side unsnapped, since the angle came out negative (-177).
        assertEquals(180, reflect(0, 357), 1e-3);
        assertEquals(90, reflect(270, 273), 1e-3);
    }

    @Test
    void jumpsBeyondTheToleranceKeepTheirAngle() {
        assertEquals(174, reflect(0, 6), 1e-3);
        double radians = Math.toRadians(3);
        assertEquals(177, WallJump.jumpAngle(0, false, -Math.sin(radians), Math.cos(radians), 0), 1e-3);
    }

    @Test
    void touchingTheWallAlreadyTurnsTowardsIt() {
        assertEquals(270, WallJump.jumpAngle(270, true, 0, 0, TOLERANCE));
        assertEquals(315, WallJump.jumpAngle(315, true, 0, 0, TOLERANCE));
    }

    @Test
    void noMovementNoReflection() {
        assertTrue(Float.isNaN(WallJump.jumpAngle(0, false, 0, 0, TOLERANCE)));
    }

    @Test
    void angleFollowsYaw() {
        assertEquals(0, WallJump.angle(1, 0));
        assertEquals(90, WallJump.angle(0, 1));
        assertEquals(180, WallJump.angle(-1, 0));
        assertEquals(270, WallJump.angle(0, -1));
        assertEquals(315, WallJump.angle(1, -1), 1e-3);
        assertTrue(Float.isNaN(WallJump.angle(0, 0)));
    }
}
