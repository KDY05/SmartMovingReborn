package io.github.kdy05.smartmovingreborn.logic.swim;

import io.github.kdy05.smartmovingreborn.logic.swim.SwimLogic.Kind;
import io.github.kdy05.smartmovingreborn.logic.swim.SwimLogic.Zone;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SwimLogicTest {
    private static final double EPSILON = 1.0E-6;

    private static Zone zone(double depth, boolean diveUp, boolean diveDown, boolean swimDown, boolean moveSwim,
                             boolean wantShallowSwim) {
        return SwimLogic.zone(false, depth, diveUp, diveDown, swimDown, moveSwim, wantShallowSwim, 1, 1, false);
    }

    private static void assertZone(Kind kind, double motionYDiff, Zone zone) {
        assertEquals(kind, zone.kind());
        assertEquals(motionYDiff, zone.motionYDiff(), EPSILON);
        assertFalse(zone.rejected());
    }

    @Test
    void crawlersAreLeftToTheCrawlingWaterCheck() {
        Zone zone = SwimLogic.zone(true, 1.5, true, false, false, false, false, 1, 1, false);
        assertZone(Kind.NONE, 0, zone);
    }

    @Test
    void waterBelowTheFeetIsRejected() {
        assertTrue(zone(-0.1, false, false, false, false, false).rejected());
    }

    @Test
    void restingInWaterDipsWhenShallowAndDivesWhenDeep() {
        assertZone(Kind.DIPPING, -0.02, zone(1.0, false, false, false, false, false));
        assertZone(Kind.DIVING, -0.02, zone(1.5, false, false, false, false, false));
        assertZone(Kind.DIVING, 0, zone(1.704, false, false, false, false, false));
        assertZone(Kind.DIVING, 0.00125, zone(1.706, false, false, false, false, false));
    }

    @Test
    void movingUpSwimsAtTheSurface() {
        assertZone(Kind.SWIMMING, -6.25E-4, zone(1.5, false, false, false, true, false));
        assertZone(Kind.SWIMMING, 0, zone(1.5045, false, false, false, true, false));
        assertZone(Kind.DIPPING, -0.01, zone(1.0, false, false, false, false, true));
        assertZone(Kind.DIPPING, -0.02, zone(0.5, false, false, false, false, true));
    }

    @Test
    void sneakingDownWhileSwimmingIsOverwrittenByTheFloat() {
        assertZone(Kind.SWIMMING, -6.25E-4, zone(1.5, false, false, true, true, false));
    }

    @Test
    void divingUpAndDownNearTheSurface() {
        assertZone(Kind.DIVING, 0.05, zone(1.8, true, false, false, false, false));
        assertZone(Kind.DIVING, 0.05 * 1.5,
                SwimLogic.zone(false, 1.8, true, false, false, false, false, 1, 1.5f, false));
        assertZone(Kind.DIVING, 0.01 - 0.1, zone(1.8, false, true, false, true, false));
        assertZone(Kind.DIVING, 0.04, zone(1.8, false, false, false, true, false));
    }

    @Test
    void deepWaterDives() {
        assertZone(Kind.DIVING, 0.01, zone(3, false, false, false, false, false));
        assertZone(Kind.DIVING, 0.01 + 0.1 * 2, SwimLogic.zone(false, 3, true, false, false, false, false, 2, 1, false));
        assertZone(Kind.DIVING, 0.11 / 1.5, SwimLogic.zone(false, 3, true, false, false, false, false, 2, 1.5f, true));
        assertZone(Kind.DIVING, 0.01 - 0.2, SwimLogic.zone(false, 3, false, true, false, false, false, 2, 1, false));
    }

    @Test
    void sneakingInShallowWaterIsNotSwimmingDown() {
        assertTrue(SwimLogic.swimDown(true, true, false, true));
        assertTrue(SwimLogic.swimDown(true, true, true, false));
        assertFalse(SwimLogic.swimDown(true, true, true, true));
        assertFalse(SwimLogic.swimDown(true, false, false, false));
    }

    @Test
    void dippingJumpsHighEnoughInTheBlock() {
        assertTrue(SwimLogic.dippingJumpHeight(64.0, false));
        assertTrue(SwimLogic.dippingJumpHeight(64.3, false));
        assertFalse(SwimLogic.dippingJumpHeight(64.5, false));
        assertFalse(SwimLogic.dippingJumpHeight(64.8, false));
        assertTrue(SwimLogic.dippingJumpHeight(64.8, true));
        assertFalse(SwimLogic.dippingJumpHeight(64.7, true));
    }

    @Test
    void jumpingOutOfWaterNeedsAWallJumpAndTime() {
        assertTrue(SwimLogic.jumpOutOfWater(true, true, true, false, 11, false, false));
        assertFalse(SwimLogic.jumpOutOfWater(true, true, true, false, 10, false, false));
        assertTrue(SwimLogic.jumpOutOfWater(true, true, true, false, 0, true, false));
        assertTrue(SwimLogic.jumpOutOfWater(true, true, true, false, 0, false, true));
        assertFalse(SwimLogic.jumpOutOfWater(true, true, true, true, 11, false, false));
        assertFalse(SwimLogic.jumpOutOfWater(false, true, true, false, 11, false, false));
    }

    @Test
    void divingMovesAlongTheViewWithTheOriginalsScaling() {
        double[] level = SwimLogic.moveFlying(0, 0, 1, 0.02f, 0, 0, true);
        assertArrayEquals(new double[]{0, 0, 0.02}, level, EPSILON);
        double[] down = SwimLogic.moveFlying(0, 0, 1, 0.02f, 0, 90, true);
        assertEquals(-0.02, down[1], 1.0E-4);
        double[] flat = SwimLogic.moveFlying(0, 0, 1, 0.02f, 0, 90, false);
        assertArrayEquals(new double[]{0, 0, 0.02}, flat, EPSILON);
        // Looking 45 degrees down, the fourth root makes the move slower than the speed.
        double[] slanted = SwimLogic.moveFlying(0, 0, 1, 0.02f, 0, 45, true);
        double half = Math.sqrt(0.5);
        double length = Math.sqrt(Math.sqrt(0.5) + 0.5);
        assertEquals(0.02 * half / length, slanted[2], 1.0E-6);
        assertEquals(-0.02 * half / length, slanted[1], 1.0E-6);
        assertArrayEquals(new double[]{0, 0, 0}, SwimLogic.moveFlying(0.01f, 0, 0, 0.02f, 0, 0, true), EPSILON);
    }

    @Test
    void enhancementsScaleLikeVanillasTopSpeed() {
        assertEquals(1, SwimLogic.enhancementFactor(false, 0, true, 0.1f, false, 1), EPSILON);
        assertEquals(0.1 / 0.454 / 0.1, SwimLogic.enhancementFactor(false, 1, true, 0.1f, false, 1), 1.0E-4);
        assertEquals(0.06 / 0.327 / 0.1, SwimLogic.enhancementFactor(false, 1, false, 0.1f, false, 1), 1.0E-4);
        assertEquals(5, SwimLogic.enhancementFactor(false, 0, true, 0.1f, true, 1), 1.0E-4);
        assertEquals(2.5, SwimLogic.enhancementFactor(true, 0, true, 0.13f, true, 1), 1.0E-4);
        assertEquals(2, SwimLogic.enhancementFactor(false, 0, true, 0.1f, false, 2), EPSILON);
    }

    @Test
    void waterSprintNeedsNoForwardKey() {
        assertTrue(SwimLogic.sprintInput(true, false, true, false, false, true, true));
        assertTrue(SwimLogic.sprintInput(true, false, false, true, false, true, true));
        assertFalse(SwimLogic.sprintInput(true, false, false, false, true, true, true));
        assertTrue(SwimLogic.sprintInput(false, true, false, false, true, true, true));
        assertFalse(SwimLogic.sprintInput(false, true, false, true, false, true, false));
        assertFalse(SwimLogic.sprintInput(false, false, true, true, true, true, true));
    }

    @Test
    void sneakingDownInWaterIsNotSneaking() {
        assertTrue(SwimLogic.allowsSneak(false, false, true, true, false));
        assertFalse(SwimLogic.allowsSneak(false, true, true, true, false));
        assertTrue(SwimLogic.allowsSneak(false, true, true, false, false));
        assertFalse(SwimLogic.allowsSneak(true, false, true, true, false));
        assertTrue(SwimLogic.allowsSneak(true, false, true, true, true));
    }
}
