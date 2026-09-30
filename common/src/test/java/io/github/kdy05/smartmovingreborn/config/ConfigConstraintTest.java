package io.github.kdy05.smartmovingreborn.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ConfigConstraintTest {
    private static final float EPSILON = 1e-6f;

    @TempDir
    Path dir;

    private SmartMovingClientConfig load(String content) throws IOException {
        Path file = dir.resolve("test.properties");
        Files.writeString(file, content, StandardCharsets.UTF_8);
        SmartMovingClientConfig config = new SmartMovingClientConfig();
        config.load(file);
        return config;
    }

    @Test
    void sprintFactorIsAtLeastRunFactorPlusPointOne() throws IOException {
        SmartMovingClientConfig config = load("move.run.factor=1.5\nmove.sprint.factor=1.2\n");
        assertEquals(1.6f, config.sprintFactor.get(), EPSILON);
    }

    @Test
    void sprintFactorMinimumIgnoresRunFactorWhenRunningIsOff() throws IOException {
        assertEquals(1.2f, load("move.run=false\nmove.run.factor=1.5\nmove.sprint.factor=1.2\n").sprintFactor.get(), EPSILON);
        assertEquals(1.1f, load("move.run=false\nmove.sprint.factor=1.0\n").sprintFactor.get(), EPSILON);
    }

    @Test
    void runFactorHasFixedMinimum() throws IOException {
        assertEquals(1.1f, load("move.run.factor=0.5\n").runFactor.get(), EPSILON);
    }

    @Test
    void exhaustionStopIsAtLeastStart() throws IOException {
        SmartMovingClientConfig config = load("move.exhaustion.sprint.start=70\nmove.exhaustion.sprint.stop=20\n"
                + "move.climb.exhaustion.start=90\nmove.climb.ceiling.exhaustion.stop=10\n");
        assertEquals(70f, config.sprintExhaustionStop.get());
        assertEquals(90f, config.climbExhaustionStop.get());
        assertEquals(40f, config.ceilingClimbExhaustionStop.get());
    }

    @Test
    void fallMaximumIsAtLeastDamageStart() throws IOException {
        SmartMovingClientConfig config = load("move.climb.fall.damage.start.distance=3\nmove.climb.fall.maximum.distance=1\n");
        assertEquals(3f, config.climbFallMaximumDistance.get());
    }

    @Test
    void rangesClampBothSides() throws IOException {
        SmartMovingClientConfig config = load("move.climb.fall.damage.start.distance=5\nmove.sneak.factor=1.5\n"
                + "move.crawl.factor=-1\nmove.climb.free.direction.diagonal.angle=10\nmove.perspective.fade.factor=0\n");
        assertEquals(3f, config.climbFallDamageStartDistance.get());
        assertEquals(1f, config.sneakFactor.get());
        assertEquals(0f, config.crawlFactor.get());
        assertEquals(45f, config.climbFreeDiagonalAngle.get());
        assertEquals(0.1f, config.perspectiveFadeFactor.get(), EPSILON);
    }

    @Test
    void jumpExhaustionChainIsOrdered() throws IOException {
        // gain: stand <= sneak <= walk <= run <= sprint; stop: stand >= sneak >= walk >= run >= sprint
        SmartMovingClientConfig config = load("move.jump.stand.exhaustion.gain.factor=50\n"
                + "move.jump.stand.exhaustion.stop.factor=30\n");
        assertEquals(50f, config.sneakJumpExhaustionGainFactor.get());
        assertEquals(50f, config.walkJumpExhaustionGainFactor.get());
        assertEquals(60f, config.runJumpExhaustionGainFactor.get());
        assertEquals(30f, config.sneakJumpExhaustionStopFactor.get());
        assertEquals(30f, config.walkJumpExhaustionStopFactor.get());
        assertEquals(30f, config.runJumpExhaustionStopFactor.get());
        assertEquals(30f, config.sprintJumpExhaustionStopFactor.get());
    }

    @Test
    void standLossFactorIsAtLeastOne() throws IOException {
        SmartMovingClientConfig config = load("move.exhaustion.run.loss.factor=0\nmove.exhaustion.walk.loss.factor=0\n"
                + "move.exhaustion.sneak.loss.factor=0\nmove.exhaustion.stand.loss.factor=0\n");
        assertEquals(1f, config.standExhaustionLossFactor.get());
        assertEquals(2.5f, config.fallExhaustionLossFactor.get());
    }

    @Test
    void referencedDefaultsFollowTheirReference() throws IOException {
        SmartMovingClientConfig config = load("move.fall.distance.minimum=5\nmove.usage.speed.factor=0.5\n");
        assertEquals(5f, config.fallAnimationDistanceMinimum.get());
        assertEquals(0.5f, config.usageSwordSpeedFactor.get());
        assertEquals(0.5f, config.usageBowSpeedFactor.get());
        assertEquals(0.5f, config.usageFoodSpeedFactor.get());
    }

    @Test
    void dependenciesDisableSwitchesWithoutChangingStoredValues() throws IOException {
        SmartMovingClientConfig config = load("move.jump=false\nmove.fly.ground.close=false\nmove.fly.ground.collide=true\n");
        assertFalse(config.standJump.get());
        assertTrue(config.standJump.stored());
        assertFalse(config.jumpCharge.get());
        assertFalse(config.flyWhileOnGround.get());
        assertTrue(config.flyWhileOnGround.stored());

        config.jump.set(true);
        assertTrue(config.standJump.get());
    }

    @Test
    void autoGrabRequiresFreeBaseClimb() throws IOException {
        assertTrue(load("").climbFreeLadderAuto.get());
        assertFalse(load("move.climb.base=smart\n").climbFreeLadderAuto.get());
        assertFalse(load("move.climb.free=false\n").climbFreeVineAuto.get());
    }

    @Test
    void jumpExhaustionSwitchesChain() throws IOException {
        SmartMovingClientConfig config = load("move.jump.climb.exhaustion=true\nmove.jump.exhaustion=false\n");
        assertFalse(config.climbJumpExhaustion.get());
        assertFalse(config.climbJumpUpExhaustion.get());
        config.jumpExhaustion.set(true);
        assertTrue(config.climbJumpUpExhaustion.get());
    }
}
