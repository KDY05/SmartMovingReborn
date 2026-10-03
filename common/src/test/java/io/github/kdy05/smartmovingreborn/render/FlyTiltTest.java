package io.github.kdy05.smartmovingreborn.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.util.Mth;
import org.junit.jupiter.api.Test;

class FlyTiltTest {
    @Test
    void hoveringStaysUprightAndFullSpeedLiesAlongTheFlight() {
        assertEquals(0, PoseCalculator.flyTilt(false, 0, 0));
        assertEquals(Mth.PI / 2, PoseCalculator.flyTilt(false, 1, 0), 1e-6);
        assertEquals(Mth.PI / 4, PoseCalculator.flyTilt(false, 0.5f, 0), 1e-6);
        assertEquals(Mth.PI / 2 + 0.3f, PoseCalculator.flyTilt(false, 1, -0.3f), 1e-6);
    }

    @Test
    void risingWithJumpHeldTiltsLikeSinking() {
        assertEquals(Mth.PI / 2 - 0.3f, PoseCalculator.flyTilt(true, 1, -0.3f), 1e-6);
        assertEquals(Mth.PI / 2 - 0.3f, PoseCalculator.flyTilt(true, 1, 0.3f), 1e-6);
    }
}
