package io.github.kdy05.smartmovingreborn.logic.climb;

import io.github.kdy05.smartmovingreborn.world.FeetClimbing;
import io.github.kdy05.smartmovingreborn.world.HandsClimbing;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClimbLogicTest {
    private static ClimbLogic.Decision up(HandsClimbing hands, FeetClimbing feet, boolean climbGap) {
        return ClimbLogic.decide(hands, feet, true, false, climbGap, false, false, false, false);
    }

    private static ClimbLogic.Decision down(HandsClimbing hands, FeetClimbing feet, boolean holding) {
        return ClimbLogic.decide(hands, feet, false, true, false, false, false, false, holding);
    }

    @Test
    void handsAndFeetOnTheWallClimbStraightUp() {
        ClimbLogic.Decision decision = up(HandsClimbing.UP, FeetClimbing.BASE_WITH_HANDS, false);
        assertEquals(ClimbLogic.STRAIGHT_UP, decision.speed());
        assertEquals(1, decision.handsType());
        assertEquals(1, decision.feetType());
        // With room above, the hands reach over the edge.
        assertEquals(2, up(HandsClimbing.UP, FeetClimbing.BASE_WITH_HANDS, true).handsType());
    }

    @Test
    void feetStepUpFastWithoutHands() {
        ClimbLogic.Decision decision = up(HandsClimbing.NONE, FeetClimbing.FAST_UP, true);
        assertEquals(0.2, decision.speed());
        assertEquals(0, decision.handsType());
        assertEquals(1, decision.feetType());
        // Not from the ground though: there the player just walks on.
        assertFalse(ClimbLogic.decide(HandsClimbing.NONE, FeetClimbing.FAST_UP, true, false, true, false, true,
                false, false).climbing());
    }

    @Test
    void handsReachingOverAnEdgeWithRoomPullUp() {
        assertEquals(0.1, up(HandsClimbing.FAST_UP, FeetClimbing.NONE, true).speed());
        assertEquals(0.2, up(HandsClimbing.FAST_UP, FeetClimbing.BASE_WITH_HANDS, true).speed());
    }

    @Test
    void aTopHoldHangsStillAndCanJump() {
        ClimbLogic.Decision decision = up(HandsClimbing.TOP_HOLD, FeetClimbing.NONE, false);
        assertEquals(ClimbLogic.STILL, decision.speed());
        assertEquals(6, decision.jumpType());
        assertEquals(5, up(HandsClimbing.NONE, FeetClimbing.BASE_HOLD, false).jumpType());
        // Hands and feet both on the wall climb instead.
        assertEquals(ClimbLogic.STRAIGHT_UP, up(HandsClimbing.TOP_HOLD, FeetClimbing.BASE_HOLD, false).speed());
    }

    @Test
    void sinkingHandsSlideDownSlowly() {
        assertEquals(0.05, up(HandsClimbing.SINK, FeetClimbing.NONE, false).speed());
    }

    @Test
    void nothingToHoldIsNoClimbing() {
        assertFalse(up(HandsClimbing.NONE, FeetClimbing.NONE, false).climbing());
        assertFalse(down(HandsClimbing.NONE, FeetClimbing.NONE, false).climbing());
    }

    @Test
    void climbingDownDependsOnTheFeet() {
        assertEquals(0.01, down(HandsClimbing.UP, FeetClimbing.BASE_HOLD, false).speed());
        assertEquals(0.05, down(HandsClimbing.UP, FeetClimbing.BASE_WITH_HANDS, false).speed());
        ClimbLogic.Decision handsOnly = down(HandsClimbing.FAST_UP, FeetClimbing.NONE, false);
        assertEquals(0.05, handsOnly.speed());
        assertEquals(2, handsOnly.handsType());
        assertEquals(0, handsOnly.feetType());
        assertEquals(ClimbLogic.STILL, down(HandsClimbing.BOTTOM_HOLD, FeetClimbing.NONE, false).speed());
    }

    @Test
    void holdingHangsStillButKeepsTheAnimation() {
        ClimbLogic.Decision decision = down(HandsClimbing.FAST_UP, FeetClimbing.NONE, true);
        assertEquals(ClimbLogic.STILL, decision.speed());
        assertEquals(2, decision.handsType());
    }

    @Test
    void speedsScaleAroundStill() {
        assertEquals(ClimbLogic.STILL, ClimbLogic.scale(ClimbLogic.STILL, 3, 1, 2, 2, false, false));
        assertEquals(0.08 + 0.06 * 2 * 1.5, ClimbLogic.scale(0.14, 1.5f, 1, 2, 1, false, false), 1e-6);
        assertEquals(0.08 - 0.03 * 0.5, ClimbLogic.scale(0.05, 1, 1, 1, 0.5f, false, false), 1e-6);
        assertEquals(ClimbLogic.STILL, ClimbLogic.scale(0.2, 1, 1, 1, 1, true, false));
        assertEquals(0.17, ClimbLogic.scale(0.2, 1, 1, 1, 1, false, true), 1e-9);
    }

    @Test
    void onlyOneOrTwoLaddersSpeedUp() {
        assertEquals(1, ClimbLogic.ladderFactor(0, 1.1f, 1.4f));
        assertEquals(1.1f, ClimbLogic.ladderFactor(1, 1.1f, 1.4f));
        assertEquals(1.4f, ClimbLogic.ladderFactor(2, 1.1f, 1.4f));
        assertEquals(1, ClimbLogic.ladderFactor(3, 1.1f, 1.4f));
    }

    @Test
    void baseClimbingModes() {
        assertEquals(0.2, ClimbLogic.simpleSpeed(true, false));
        assertEquals(0.1, ClimbLogic.simpleSpeed(false, true));
        assertEquals(0.2, ClimbLogic.smartSpeed(true, false, true, false));
        assertEquals(0.1, ClimbLogic.smartSpeed(true, false, false, true));
        assertEquals(0.2, ClimbLogic.smartSpeed(false, true, false, true));
        assertEquals(0, ClimbLogic.smartSpeed(false, false, true, true));
    }
}
