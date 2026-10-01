package io.github.kdy05.smartmovingreborn.logic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SpeedLogicTest {
    private static final float CRAWL = 0.15f;
    private static final float SNEAK = 0.3f;
    private static final float SPRINT = 1.5f;
    private static final float RUN = 1.3f;

    private static float factor(float item, boolean crawling, boolean slow, boolean fast, boolean running,
                                boolean sprinting) {
        return SpeedLogic.landSpeedFactor(1, item, crawling, CRAWL, slow, SNEAK, fast, SPRINT, running, RUN, sprinting);
    }

    @Test
    void walkingKeepsVanillaSpeed() {
        assertEquals(1, factor(1, false, false, false, false, false), 1e-6);
    }

    @Test
    void crawlingAndSneakingUseTheirFactorsAndCrawlingWins() {
        assertEquals(CRAWL, factor(1, true, false, false, false, false), 1e-6);
        assertEquals(SNEAK, factor(1, false, true, false, false, false), 1e-6);
        assertEquals(CRAWL, factor(1, true, true, false, false, false), 1e-6);
    }

    @Test
    void sprintingReplacesVanillasSprintBonus() {
        // Vanilla's speed already includes its 1.3, so the multiplier leaves 1.5 overall.
        assertEquals(SPRINT, factor(1, false, false, true, false, true) * SpeedLogic.VANILLA_SPRINT_FACTOR, 1e-6);
        assertEquals(SPRINT, factor(1, false, false, true, false, false), 1e-6);
    }

    @Test
    void runningUsesTheRunFactorInsteadOfVanillas() {
        assertEquals(RUN, factor(1, false, false, false, true, true) * SpeedLogic.VANILLA_SPRINT_FACTOR, 1e-6);
        // Vanilla sprinting in the air is not running: the original dropped the sprint bonus there.
        assertEquals(1, factor(1, false, false, false, false, true) * SpeedLogic.VANILLA_SPRINT_FACTOR, 1e-6);
    }

    @Test
    void sprintAndRunAreNotCombined() {
        assertEquals(SPRINT, factor(1, false, false, true, true, false), 1e-6);
    }

    @Test
    void factorsMultiply() {
        assertEquals(2 * 0.2f * CRAWL * SPRINT,
                SpeedLogic.landSpeedFactor(2, 0.2f, true, CRAWL, false, SNEAK, true, SPRINT, false, RUN, false), 1e-6);
        assertEquals(0.2f * SNEAK, factor(0.2f, false, true, false, false, false), 1e-6);
    }

    @Test
    void sneakFollowsTheInputOrTheToggle() {
        assertTrue(SpeedLogic.wouldWantSneak(false, false, true, false, false, false, true, false, false, false));
        assertFalse(SpeedLogic.wouldWantSneak(false, true, false, false, false, false, true, false, false, false));
        assertTrue(SpeedLogic.wouldWantSneak(true, true, false, false, false, false, true, false, false, false));
        assertTrue(SpeedLogic.wouldWantSneak(true, false, true, true, false, false, true, false, false, false));
        assertFalse(SpeedLogic.wouldWantSneak(true, false, true, false, false, false, true, false, false, false));
    }

    @Test
    void crawlingSlidingGrabAndFlyingPreventSneaking() {
        assertFalse(SpeedLogic.wouldWantSneak(false, false, true, false, true, false, true, false, false, false));
        assertFalse(SpeedLogic.wouldWantSneak(false, false, true, false, false, true, true, false, false, false));
        assertFalse(SpeedLogic.wouldWantSneak(false, false, true, false, false, false, true, true, false, false));
        assertTrue(SpeedLogic.wouldWantSneak(false, false, true, false, false, false, false, true, false, false));
        assertFalse(SpeedLogic.wouldWantSneak(false, false, true, false, false, false, true, false, true, false));
        assertFalse(SpeedLogic.wouldWantSneak(false, false, true, false, false, false, true, false, false, true));
    }

    @Test
    void vanillaSneaksOnlyOnTheGroundOrToStopAtEdges() {
        assertTrue(SpeedLogic.shiftKeyDown(true, true, true, true, 0, false, true));
        assertFalse(SpeedLogic.shiftKeyDown(true, false, true, true, 0, false, true));
        assertTrue(SpeedLogic.shiftKeyDown(false, false, true, false, 0, true, false));
        assertFalse(SpeedLogic.shiftKeyDown(false, true, true, false, 0, true, true));
    }

    @Test
    void chargingAJumpCrouchesEvenWithSneakingSwitchedOff() {
        assertTrue(SpeedLogic.shiftKeyDown(false, true, false, true, 1, false, true));
        assertFalse(SpeedLogic.shiftKeyDown(false, true, false, true, 0, false, true));
        assertFalse(SpeedLogic.shiftKeyDown(false, true, true, true, 1, false, true));
    }

    @Test
    void jumpFactorControlsTheAirHeadJumpsAndHoldingJumpWhileSprinting() {
        assertEquals(1, SpeedLogic.jumpFactor(true, false, true, true, 0.8f, 0.5f, false, 0.2f));
        assertEquals(0.8f, SpeedLogic.jumpFactor(true, true, true, true, 0.8f, 0.5f, false, 0.2f));
        assertEquals(1, SpeedLogic.jumpFactor(true, true, true, false, 0.8f, 0.5f, false, 0.2f));
        assertEquals(1, SpeedLogic.jumpFactor(true, true, false, true, 0.8f, 0.5f, false, 0.2f));
        assertEquals(0.5f, SpeedLogic.jumpFactor(false, true, true, true, 0.8f, 0.5f, false, 0.2f));
        assertEquals(0.2f, SpeedLogic.jumpFactor(false, true, true, true, 0.8f, 0.5f, true, 0.2f));
    }

    @Test
    void sprintNeedsTheKeyForwardAndNoSlideRideOrSleep() {
        assertTrue(SpeedLogic.wantSprint(true, true, true, false, false));
        assertFalse(SpeedLogic.wantSprint(false, true, true, false, false));
        assertFalse(SpeedLogic.wantSprint(true, true, false, false, false));
        assertFalse(SpeedLogic.wantSprint(true, true, true, false, true));
        assertFalse(SpeedLogic.wantSprint(true, true, true, true, false));
    }

    @Test
    void groundSprintingStopsForSneakFireItemsWallsAndAir() {
        assertTrue(SpeedLogic.groundSprinting(true, false, false, false, false, 0, true));
        assertFalse(SpeedLogic.groundSprinting(true, true, false, false, false, 0, true));
        assertFalse(SpeedLogic.groundSprinting(true, false, true, false, false, 0, true));
        assertFalse(SpeedLogic.groundSprinting(true, false, false, true, false, 0, true));
        assertTrue(SpeedLogic.groundSprinting(true, false, false, true, true, 0, true));
        assertTrue(SpeedLogic.groundSprinting(true, false, false, false, false, 2, true));
        assertFalse(SpeedLogic.groundSprinting(true, false, false, false, false, 3, true));
        assertFalse(SpeedLogic.groundSprinting(true, false, false, false, false, 0, false));
    }

    @Test
    void perspectiveUsesTheSprintOrRunFactorInsteadOfVanillas() {
        float walk = 0.1f;
        float vanillaSprint = walk * SpeedLogic.VANILLA_SPRINT_FACTOR;
        assertEquals(walk, SpeedLogic.perspectiveSpeed(walk, false, false, false, false, 1.5f, 1), 1e-6);
        assertEquals(walk * 1.5f, SpeedLogic.perspectiveSpeed(vanillaSprint, true, false, false, true, 1.5f, 1), 1e-6);
        assertEquals(walk * 1.5f, SpeedLogic.perspectiveSpeed(walk, false, true, false, false, 1.5f, 1), 1e-6);
        assertEquals(vanillaSprint, SpeedLogic.perspectiveSpeed(vanillaSprint, false, false, true, true, 1.5f, 1), 1e-6);
        assertEquals(walk, SpeedLogic.perspectiveSpeed(vanillaSprint, false, false, true, true, 1.5f, 1 / 1.3f), 1e-6);
    }

    @Test
    void perspectiveFadesTowardsTheTarget() {
        assertEquals(0.1f, SpeedLogic.fadePerspective(-1, 0.15f, 0.5f, 0.1f), 1e-6);
        assertEquals(0.125f, SpeedLogic.fadePerspective(0.1f, 0.15f, 0.5f, 0.1f), 1e-6);
        assertEquals(0.15f, SpeedLogic.fadePerspective(0.1f, 0.15f, 1, 0.1f), 1e-6);
    }
}
