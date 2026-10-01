package io.github.kdy05.smartmovingreborn.logic.slide;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SlideLogicTest {
    @Test
    void slideStartsOnSneakWhileSprintingOrRunningWithGrabHeld() {
        assertTrue(SlideLogic.startSlide(true, true, true, false, true, false, true, false));
        assertTrue(SlideLogic.startSlide(true, true, false, true, true, false, true, false));
        assertFalse(SlideLogic.startSlide(true, true, false, true, false, false, true, false));
        assertFalse(SlideLogic.startSlide(true, true, false, false, true, false, true, false));
    }

    @Test
    void slideNeedsGrabAFreshSneakAndNoCrawlingOrWater() {
        assertFalse(SlideLogic.startSlide(false, true, true, false, true, false, true, false));
        assertFalse(SlideLogic.startSlide(true, false, true, false, true, false, true, false));
        assertFalse(SlideLogic.startSlide(true, true, true, false, true, true, true, false));
        assertFalse(SlideLogic.startSlide(true, true, true, false, true, false, false, false));
        assertFalse(SlideLogic.startSlide(true, true, true, false, true, false, true, true));
    }

    @Test
    void slideStopsOnSneakReleaseOrBelowTheStopSpeed() {
        assertFalse(SlideLogic.stopSlide(true, 0.02, 1));
        assertTrue(SlideLogic.stopSlide(false, 0.02, 1));
        assertTrue(SlideLogic.stopSlide(true, 0.009, 1));
        assertFalse(SlideLogic.stopSlide(true, 0.009, 0.5f));
    }

    @Test
    void headJumpEndsOnGroundSwimmingSinkingOrInLava() {
        assertTrue(SlideLogic.continueHeadJump(true, false, false, false, false));
        assertFalse(SlideLogic.continueHeadJump(false, false, false, false, false));
        assertFalse(SlideLogic.continueHeadJump(true, true, false, false, false));
        assertFalse(SlideLogic.continueHeadJump(true, false, true, false, false));
        assertFalse(SlideLogic.continueHeadJump(true, false, false, true, false));
        assertFalse(SlideLogic.continueHeadJump(true, false, false, false, true));
    }

    @Test
    void slideDampingMatchesTheOriginal() {
        // Stone (0.6) and ice (0.98) with the default glide factor, from the original's formula.
        assertEquals(1 / ((1 / 0.6f - 1) / 25 + 1) * 0.98f, SlideLogic.damping(0.6f, 1), 1e-6);
        assertEquals(0.9545, SlideLogic.damping(0.6f, 1), 1e-4);
        assertTrue(SlideLogic.damping(0.98f, 1) > SlideLogic.damping(0.6f, 1));
        assertEquals(0.98f, SlideLogic.damping(0.6f, 0), 1e-6);
    }

    @Test
    void steeringTurnsAgainstTheStrafeInputAndKeepsTheSpeed() {
        // Sliding south (+z), strafing left (+1) turns towards east (+x), like the original.
        double[] turned = SlideLogic.steer(0, 0.3, 1, 90);
        assertEquals(0.3, turned[0], 1e-6);
        assertEquals(0, turned[1], 1e-6);
        turned = SlideLogic.steer(0.1, -0.2, -1, 5);
        assertEquals(Math.sqrt(0.05), Math.hypot(turned[0], turned[1]), 1e-9);
        assertNull(SlideLogic.steer(0.1, 0.2, 0, 5));
        assertNull(SlideLogic.steer(0.1, 0.2, 1, 0));
        assertNull(SlideLogic.steer(0, 0, 1, 5));
    }
}
