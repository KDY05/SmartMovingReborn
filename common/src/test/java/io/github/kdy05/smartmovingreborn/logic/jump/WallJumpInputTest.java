package io.github.kdy05.smartmovingreborn.logic.jump;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WallJumpInputTest {
    private static final int TICKS = 3;

    private final WallJumpInput input = new WallJumpInput();
    private boolean pressed;

    /** One tick in the air with jump held or not, away from walls unless {@code collided}. */
    private void tick(boolean jump, boolean collided) {
        boolean started = jump && !pressed;
        pressed = jump;
        input.update(true, TICKS, false, jump, started, collided);
    }

    @Test
    void doubleClickTriggersAndHoldingKeepsIt() {
        tick(true, false);
        assertFalse(input.want());
        tick(false, false);
        tick(true, false);
        assertTrue(input.want());
        tick(true, false);
        assertTrue(input.want());
    }

    @Test
    void slowDoubleClickStartsOver() {
        tick(true, false);
        tick(false, false);
        tick(false, false);
        tick(false, false);
        tick(false, false);
        tick(true, false);
        assertFalse(input.want());
    }

    @Test
    void singleClickModeTriggersAtOnce() {
        input.update(true, 0, false, true, true, false);
        assertTrue(input.want());
    }

    @Test
    void touchingAWallWithoutJumpingEndsIt() {
        tick(true, false);
        tick(false, false);
        tick(true, false);
        tick(true, true);
        assertFalse(input.want());
    }

    @Test
    void releasingJumpEndsIt() {
        tick(true, false);
        tick(false, false);
        tick(true, false);
        tick(false, false);
        assertFalse(input.want());
    }

    @Test
    void wallJumpsChainWhileJumpIsHeld() {
        tick(true, false);
        tick(false, false);
        tick(true, false);
        input.jumped(false);
        tick(true, true);
        assertTrue(input.want());
        tick(false, false);
        assertFalse(input.want());
    }

    @Test
    void headWallJumpsDoNotChain() {
        tick(true, false);
        tick(false, false);
        tick(true, false);
        input.jumped(true);
        tick(true, true);
        assertFalse(input.want());
    }

    @Test
    void landingEndsTheChain() {
        tick(true, false);
        tick(false, false);
        tick(true, false);
        input.jumped(false);
        input.update(false, TICKS, true, true, false, false);
        input.update(true, TICKS, false, true, false, true);
        assertFalse(input.want());
    }

    @Test
    void notBeingAbleForgetsTheFirstClick() {
        tick(true, false);
        tick(false, false);
        input.update(false, TICKS, false, false, false, false);
        tick(true, false);
        assertFalse(input.want());
    }
}
