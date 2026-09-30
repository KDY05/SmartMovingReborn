package io.github.kdy05.smartmovingreborn.config;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EasyDefaultsTest {
    private final SmartMovingClientConfig config = new SmartMovingClientConfig();

    @Test
    void mediumAndHardOnlySwitchesAreOff() {
        List<BooleanProperty> switches = List.of(
                // Hard only in the original
                config.climbExhaustion, config.ceilingClimbExhaustion, config.runExhaustion,
                config.climbJumpExhaustion, config.standJumpExhaustion, config.sneakJumpExhaustion,
                config.walkJumpExhaustion,
                // Medium and Hard in the original
                config.sprintExhaustion, config.wallJumpExhaustion, config.runJumpExhaustion,
                config.sprintJumpExhaustion, config.jumpChargeExhaustion, config.jumpSlideExhaustion,
                config.hungerGain);
        for (BooleanProperty property : switches) {
            assertFalse(property.get(), property.key());
        }
    }

    @Test
    void easyNumbers() {
        assertEquals(1.2f, config.exhaustionLossFactor.get());
        assertEquals(0.02f, config.exhaustionLossHungerFactor.get());
        assertEquals(0.8f, config.hungerGainFactor.get());
        assertEquals(0f, config.alwaysHungerGain.get());
    }

    @Test
    void jumpSpeedSwitchesAreAllOff() {
        // A jump accumulates exhaustion only if both its type switch and its speed switch are on.
        // Type switches such as the angle jump stay on, but every speed switch is off in Easy.
        assertTrue(config.angleJumpExhaustion.get());
        List<BooleanProperty> speedSwitches = List.of(config.standJumpExhaustion, config.sneakJumpExhaustion,
                config.walkJumpExhaustion, config.runJumpExhaustion, config.sprintJumpExhaustion);
        for (BooleanProperty property : speedSwitches) {
            assertFalse(property.get(), property.key());
        }
    }

    @Test
    void specDefaults() {
        assertEquals(SmartMovingConfig.CLIMB_FREE, config.climbBase.get());
        assertEquals(90f, config.climbFreeOrthogonalAngle.get());
        assertEquals(80f, config.climbFreeDiagonalAngle.get());
        assertEquals(1.0153f, config.climbFreeLadderOneUpSpeedFactor.get());
        assertEquals(1.43f, config.climbFreeLadderTwoUpSpeedFactor.get());
        assertEquals(2f, config.climbFallDamageStartDistance.get());
        assertEquals(3f, config.climbFallMaximumDistance.get());
        assertEquals(0.2f, config.ceilingClimbSpeedFactor.get());
        assertEquals(0.15f, config.crawlFactor.get());
        assertEquals(0.5f, config.slideParticlePeriodFactor.get());
        assertEquals(20f, config.jumpChargeMaximum.get());
        assertEquals(1.3f, config.jumpChargeFactor.get());
        assertEquals(10f, config.headJumpChargeMaximum.get());
        assertEquals(0.3f, config.angleJumpHorizontalFactor.get());
        assertEquals(0.2f, config.angleJumpVerticalFactor.get());
        assertEquals(0.4f, config.wallUpJumpVerticalFactor.get());
        assertEquals(0.15f, config.wallUpJumpHorizontalFactor.get());
        assertEquals(2f, config.wallUpJumpFallMaximumDistance.get());
        assertEquals(1.3f, config.runFactor.get());
        assertEquals(1.5f, config.sprintFactor.get());
        assertEquals(0.3f, config.sneakFactor.get());
        assertEquals(0.2f, config.usageSpeedFactor.get());
        assertEquals(3f, config.fallDistanceMinimum.get());
        assertTrue(config.enabled);
    }

    @Test
    void keysAreUniqueAndServerSharesMovementKeys() {
        Set<String> keys = new HashSet<>();
        for (Property<?> property : config.properties()) {
            assertTrue(keys.add(property.key()), property.key());
        }

        List<Property<?>> server = new SmartMovingServerConfig().properties();
        List<Property<?>> shared = server.subList(0, server.size() - 1);
        for (int i = 0; i < shared.size(); i++) {
            assertEquals(config.properties().get(i).key(), shared.get(i).key());
        }
        assertEquals("move.server.config", server.get(server.size() - 1).key());
        assertFalse(new SmartMovingServerConfig().serverConfig.get());
    }
}
