package io.github.kdy05.smartmovingreborn.logic.fly;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.kdy05.smartmovingreborn.logic.fly.FlyLogic.Restore;
import org.junit.jupiter.api.Test;

class FlyLogicTest {
    @Test
    void upwardFollowsJumpAndSneakAndCancelsWithBoth() {
        assertEquals(0.98f, FlyLogic.upward(false, true));
        assertEquals(-0.98f, FlyLogic.upward(true, false));
        assertEquals(0, FlyLogic.upward(true, true));
        assertEquals(0, FlyLogic.upward(false, false));
    }

    @Test
    void landsOnlyWhenHoveringSlowlyWithoutFlyingCloseToTheGround() {
        assertTrue(FlyLogic.tryLanding(true, false, 0.002, 0));
        assertFalse(FlyLogic.tryLanding(false, false, 0.002, 0));
        assertFalse(FlyLogic.tryLanding(true, true, 0.002, 0));
        assertFalse(FlyLogic.tryLanding(true, false, 0.003, 0));
        assertFalse(FlyLogic.tryLanding(true, false, 0.002, -0.03));
    }

    @Test
    void standingNeedsABlockBetweenGroundAndCeilingAroundTheSmallBox() {
        assertTrue(FlyLogic.groundClose(0.99));
        assertFalse(FlyLogic.groundClose(1));
        assertTrue(FlyLogic.standUpPossible(0.4, 0.6));
        assertFalse(FlyLogic.standUpPossible(0.4, 0.5));
        // Far from the ground the room above is not measured, and standing counts as impossible.
        assertFalse(FlyLogic.standUpPossible(1.1, -1));
    }

    @Test
    void endingAFlightResetsInTheAirAndStandsOrLiesDownOnTheGround() {
        assertEquals(Restore.NONE, FlyLogic.restore(false, true, true, false, false));
        assertEquals(Restore.RESET, FlyLogic.restore(true, false, false, false, true));
        assertEquals(Restore.STAND_UP, FlyLogic.restore(true, true, true, false, false));
        assertEquals(Restore.STAND_UP, FlyLogic.restore(true, true, true, true, false));
        assertEquals(Restore.LIE_DOWN, FlyLogic.restore(true, true, true, true, true));
        assertEquals(Restore.LIE_DOWN, FlyLogic.restore(true, true, false, false, false));
    }

    @Test
    void sneakingFarFromTheGroundLiesDownLikeTheOriginal() {
        assertEquals(Restore.LIE_DOWN, FlyLogic.restore(true, false, false, true, false));
    }

    @Test
    void lyingDownSlidesWithGrabOrOutOfAHeadJump() {
        assertTrue(FlyLogic.slideWhenLyingDown(true, true, false));
        assertTrue(FlyLogic.slideWhenLyingDown(true, false, true));
        assertFalse(FlyLogic.slideWhenLyingDown(true, false, false));
        assertFalse(FlyLogic.slideWhenLyingDown(false, true, true));
    }

    @Test
    void fliesAgainAfterVanillaLandsUnlessSneakAndGrabAreHeld() {
        assertTrue(FlyLogic.flyAgain(true, false, false, true, false, true));
        assertTrue(FlyLogic.flyAgain(true, true, false, true, false, true));
        assertFalse(FlyLogic.flyAgain(true, true, true, true, false, true));
        assertFalse(FlyLogic.flyAgain(false, false, false, true, false, true));
        assertFalse(FlyLogic.flyAgain(true, false, false, false, false, true));
        assertFalse(FlyLogic.flyAgain(true, false, false, true, true, true));
        assertFalse(FlyLogic.flyAgain(true, false, false, true, false, false));
    }
}
