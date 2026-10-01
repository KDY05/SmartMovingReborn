package io.github.kdy05.smartmovingreborn.render;

import net.minecraft.util.Mth;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OuterFadeTest {
    @Test
    void fadeMovesAFifthOfTheWayPerTick() {
        assertEquals(0.2f, OuterFade.fade(0, 1, 1), 1e-6);
        assertEquals(0.1f, OuterFade.fade(0, 1, 0.5f), 1e-6);
        assertEquals(1, OuterFade.fade(1, 1, 1));
    }

    @Test
    void fadeTakesTheShortWayRound() {
        // From just below a full turn to just above it: forwards through 2 pi, not backwards.
        float faded = OuterFade.fade(Mth.TWO_PI - 0.1f, 0.1f, 1);
        assertEquals(Mth.TWO_PI - 0.1f + 0.04f, faded, 1e-5);
    }

    @Test
    void tiltEasesInFromStandingAndJumpsAfterAGap() {
        OuterFade outer = new OuterFade();
        outer.update(0, false, 0, false, 10);
        outer.update(Mth.PI / 2, true, 0, true, 11);
        assertEquals(Mth.PI / 2 * 0.2f, outer.xRot, 1e-6);
        outer.update(Mth.PI / 2, true, 0, true, 14);
        assertEquals(Mth.PI / 2, outer.xRot, 1e-6);
        outer.update(1, false, 2, false, 14.5f);
        assertEquals(1, outer.xRot);
        assertEquals(2, outer.yaw);
    }
}
