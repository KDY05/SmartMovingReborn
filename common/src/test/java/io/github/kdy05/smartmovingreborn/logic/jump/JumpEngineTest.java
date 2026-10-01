package io.github.kdy05.smartmovingreborn.logic.jump;

import io.github.kdy05.smartmovingreborn.config.SmartMovingClientConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Expected values are worked out by hand from the original {@code tryJump} with the Easy defaults. */
class JumpEngineTest {
    private static final double EPSILON = 1e-6;
    /** A vanilla jump's vertical motion, which the original's formula reproduces at factor 1. */
    private static final double VANILLA_JUMP = 0.42;

    private final SmartMovingClientConfig config = new SmartMovingClientConfig();

    /** The motion of a jump of {@code type} at {@code speed}, with the factors from the config. */
    private JumpEngine.Motion jump(JumpType type, JumpSpeed speed, double motionX, double motionZ, float angle) {
        float horizontal = JumpEngine.horizontalFactor(config, speed, type);
        float vertical = JumpEngine.verticalFactor(config, speed, type);
        double max = horizontal > 1 ? JumpEngine.maxHorizontalMotion(config, speed, false) : Double.NaN;
        return JumpEngine.motion(type, horizontal, vertical, 1, 1, max, motionX, motionZ, motionX, motionZ, angle);
    }

    @Test
    void speedPrefersSprintThenRunThenSneak() {
        assertEquals(JumpSpeed.SPRINT, JumpSpeed.of(true, true, true, true, false));
        assertEquals(JumpSpeed.RUN, JumpSpeed.of(true, true, true, false, false));
        assertEquals(JumpSpeed.SNEAK, JumpSpeed.of(true, true, false, false, false));
        assertEquals(JumpSpeed.STAND, JumpSpeed.of(true, false, false, false, false));
        assertEquals(JumpSpeed.WALK, JumpSpeed.of(false, false, false, false, false));
    }

    @Test
    void angledJumpsNeverSprintOrRun() {
        assertEquals(JumpSpeed.WALK, JumpSpeed.of(false, false, true, true, true));
        assertEquals(JumpSpeed.SNEAK, JumpSpeed.of(false, true, true, true, true));
    }

    @Test
    void standingJumpGoesStraightUpAsHighAsVanilla() {
        JumpEngine.Motion motion = jump(JumpType.UP, JumpSpeed.STAND, 0.01, 0, Float.NaN);
        assertEquals(VANILLA_JUMP, motion.y(), EPSILON);
        assertEquals(0, motion.x(), EPSILON);
    }

    @Test
    void walkingJumpKeepsTheMotion() {
        JumpEngine.Motion motion = jump(JumpType.UP, JumpSpeed.WALK, 0.1, -0.05, Float.NaN);
        assertEquals(0.1, motion.x(), EPSILON);
        assertEquals(-0.05, motion.z(), EPSILON);
        assertEquals(VANILLA_JUMP, motion.y(), EPSILON);
    }

    @Test
    void sprintJumpDoublesTheMotionUpToTheCap() {
        double cap = 0.11785204 * 1.5 * 2;
        assertEquals(0.1, jump(JumpType.UP, JumpSpeed.SPRINT, 0.05, 0, Float.NaN).x(), EPSILON);
        assertEquals(cap, jump(JumpType.UP, JumpSpeed.SPRINT, 0.2, 0, Float.NaN).x(), 1e-5);
        // The cap applies to the length, split between the components like the motion.
        JumpEngine.Motion diagonal = jump(JumpType.UP, JumpSpeed.SPRINT, 0.12, -0.16, Float.NaN);
        assertEquals(cap * 0.6, diagonal.x(), 1e-5);
        assertEquals(-cap * 0.8, diagonal.z(), 1e-5);
    }

    @Test
    void chargeRaisesTheJumpLinearlyUpToTheMaximum() {
        assertEquals(1, JumpEngine.chargeFactor(config, 0), EPSILON);
        assertEquals(1.15, JumpEngine.chargeFactor(config, 10), EPSILON);
        assertEquals(1.3, JumpEngine.chargeFactor(config, 20), EPSILON);
        assertEquals(1.3, JumpEngine.chargeFactor(config, 40), EPSILON);

        JumpEngine.Motion motion = JumpEngine.motion(JumpType.CHARGE, 0, 1, 1.3f, 1, Double.NaN, 0, 0, 0, 0, Float.NaN);
        assertEquals(-0.078 + 0.498 * 1.3, motion.y(), EPSILON);
    }

    @Test
    void jumpBoostAddsAFifthPerLevel() {
        assertEquals(1, JumpEngine.jumpBoostFactor(-1), EPSILON);
        assertEquals(1.2, JumpEngine.jumpBoostFactor(0), EPSILON);
        assertEquals(1.4, JumpEngine.jumpBoostFactor(1), EPSILON);
        // Close to vanilla's 0.42 + 0.1 for Jump Boost I.
        JumpEngine.Motion motion = JumpEngine.motion(JumpType.UP, 0, 1.2f, 1, 1, Double.NaN, 0, 0, 0, 0, Float.NaN);
        assertEquals(0.5196, motion.y(), EPSILON);
    }

    @Test
    void sideAndBackJumpsPushTowardsTheirAngle() {
        // Yaw 0 looks south (+z); its left is east (+x).
        JumpEngine.Motion left = jump(JumpType.ANGLE, JumpSpeed.WALK, 0, 0, 270);
        assertEquals(0.3, left.x(), EPSILON);
        assertEquals(0, left.z(), EPSILON);
        assertEquals(0.2, left.y(), EPSILON);

        JumpEngine.Motion back = jump(JumpType.ANGLE, JumpSpeed.STAND, 0.05, 0, 180);
        assertEquals(0.05, back.x(), EPSILON);
        assertEquals(-0.3, back.z(), EPSILON);
    }

    @Test
    void wallJumpsReplaceTheMotionInsteadOfAddingToIt() {
        assertEquals(0.4, JumpEngine.moving(0.1, 1, false, 0.3, 0.15f), EPSILON);
        assertEquals(-0.15, JumpEngine.moving(0.1, -1, true, 0.3, 0.15f), EPSILON);
        assertEquals(-0.5, JumpEngine.moving(-0.5, -1, true, 0.3, 0.15f), EPSILON);
        assertEquals(-0.3, JumpEngine.moving(-0.1, -1, true, 0.3, 0.15f), EPSILON);
    }

    @Test
    void flatHeadJumpTurnsTheJumpForward() {
        JumpEngine.Motion motion = JumpEngine.motion(JumpType.HEAD, 1, 1, 1, 0, Double.NaN, 0.3, 0.4, 0.3, 0.4,
                Float.NaN);
        assertEquals(0, motion.y(), EPSILON);
        assertEquals(0.3, motion.x(), EPSILON);
    }

    @Test
    void slidingAndWallTurnsKeepTheVerticalMotion() {
        assertTrue(Double.isNaN(jump(JumpType.SLIDE, JumpSpeed.WALK, 0.1, 0, Float.NaN).y()));
        assertTrue(Double.isNaN(JumpEngine.motion(JumpType.WALL_UP_TURN, 0.15f, 0.4f, 1, 1, Double.NaN, 0.1, 0, 0.1,
                0, 90).y()));
        assertEquals(0.4, JumpEngine.motion(JumpType.WALL_UP, 0.15f, 0.4f, 1, 1, Double.NaN, 0.1, 0, 0.1, 0, 90).y(),
                EPSILON);
    }

    @Test
    void factorsFollowTheTypeAndSpeed() {
        assertEquals(0, JumpEngine.horizontalFactor(config, JumpSpeed.STAND, JumpType.UP));
        assertEquals(2, JumpEngine.horizontalFactor(config, JumpSpeed.SPRINT, JumpType.CHARGE));
        assertEquals(0.3f, JumpEngine.horizontalFactor(config, JumpSpeed.STAND, JumpType.ANGLE), EPSILON);
        assertEquals(0.15f, JumpEngine.horizontalFactor(config, JumpSpeed.SPRINT, JumpType.WALL_UP_TURN), EPSILON);
        assertEquals(0.2f, JumpEngine.verticalFactor(config, JumpSpeed.SPRINT, JumpType.ANGLE), EPSILON);
        assertEquals(0.4f * 0.3f, JumpEngine.verticalFactor(config, JumpSpeed.WALK, JumpType.WALL_HEAD), EPSILON);
        assertEquals(0.8f, JumpEngine.verticalFactor(config, JumpSpeed.WALK, JumpType.CLIMB_UP_HANDS_ONLY), EPSILON);
    }

    @Test
    void switchesFollowTheTypeThenTheSpeed() {
        assertTrue(JumpEngine.enabled(config, JumpSpeed.WALK, JumpType.UP));
        config.walkJump.set(false);
        assertFalse(JumpEngine.enabled(config, JumpSpeed.WALK, JumpType.UP));
        assertFalse(JumpEngine.enabled(config, JumpSpeed.WALK, JumpType.ANGLE));
        assertTrue(JumpEngine.enabled(config, JumpSpeed.SPRINT, JumpType.UP));
        assertTrue(JumpEngine.enabled(config, JumpSpeed.WALK, JumpType.CHARGE));
        config.jump.set(false);
        assertFalse(JumpEngine.enabled(config, JumpSpeed.SPRINT, JumpType.UP));
        assertFalse(JumpEngine.enabled(config, JumpSpeed.STAND, JumpType.CHARGE));
    }
}
