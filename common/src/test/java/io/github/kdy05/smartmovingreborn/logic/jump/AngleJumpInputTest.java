package io.github.kdy05.smartmovingreborn.logic.jump;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AngleJumpInputTest {
    private static final int TICKS = 3;

    private final AngleJumpInput input = new AngleJumpInput();

    private void tick(boolean left, boolean right, boolean back) {
        input.update(TICKS, true, left, true, right, true, back);
    }

    @Test
    void doubleTapWithinTheTicksJumps() {
        tick(true, false, false);
        tick(false, false, false);
        tick(true, false, false);
        assertEquals(270, input.take());
        assertTrue(Float.isNaN(input.take()));
    }

    @Test
    void slowDoubleTapStartsOver() {
        tick(false, true, false);
        for (int i = 0; i < TICKS; i++) {
            tick(false, false, false);
        }
        tick(false, true, false);
        assertTrue(Float.isNaN(input.take()));
        tick(false, true, false);
        assertEquals(90, input.take());
    }

    @Test
    void impossibleDirectionForgetsItsFirstTap() {
        tick(true, false, false);
        input.update(TICKS, false, false, true, false, true, false);
        tick(true, false, false);
        assertTrue(Float.isNaN(input.take()));
    }

    @Test
    void sideWaitsForAPendingBackTapToJumpDiagonally() {
        tick(false, false, true);
        tick(true, false, false);
        tick(true, false, false);
        assertTrue(Float.isNaN(input.take()));
        tick(false, false, true);
        assertEquals(225, input.take());
    }

    @Test
    void backDoubleTap() {
        tick(false, false, true);
        tick(false, false, true);
        assertEquals(180, input.take());
    }

    @Test
    void animationTypeCountsEighthsOfATurnFromTheFront() {
        assertEquals(2, AngleJumpInput.animationType(270));
        assertEquals(3, AngleJumpInput.animationType(225));
        assertEquals(4, AngleJumpInput.animationType(180));
        assertEquals(5, AngleJumpInput.animationType(135));
        assertEquals(6, AngleJumpInput.animationType(90));
    }
}
