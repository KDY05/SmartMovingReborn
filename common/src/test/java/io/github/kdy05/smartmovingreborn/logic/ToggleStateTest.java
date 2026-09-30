package io.github.kdy05.smartmovingreborn.logic;

import io.github.kdy05.smartmovingreborn.input.Button;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ToggleStateTest {
    private final ToggleState toggles = new ToggleState();
    private final Button sneak = new Button();
    private final Button jump = new Button();
    private boolean crawling;

    /** One tick: the buttons' new states, then whether the player crawls after the decisions. */
    private void tick(boolean sneakDown, boolean jumpDown, boolean crawlingNow) {
        sneak.update(sneakDown);
        jump.update(jumpDown);
        boolean wasCrawling = crawling;
        crawling = crawlingNow;
        toggles.updateCrawl(crawling, wasCrawling, sneak, jump);
        toggles.endTick(sneak);
    }

    @Test
    void releasingTheSneakThatStartedCrawlingKeepsTheToggle() {
        tick(true, false, true);
        assertTrue(toggles.isCrawlToggled());
        tick(false, false, true);
        assertTrue(toggles.isCrawlToggled());
    }

    @Test
    void pressingAndReleasingSneakAgainEndsTheToggle() {
        tick(true, false, true);
        tick(false, false, true);
        tick(true, false, true);
        assertTrue(toggles.isCrawlToggled());
        tick(false, false, true);
        assertFalse(toggles.isCrawlToggled());
    }

    @Test
    void releasingJumpEndsTheToggle() {
        tick(true, false, true);
        tick(false, true, true);
        tick(false, false, true);
        assertFalse(toggles.isCrawlToggled());
    }

    @Test
    void notCrawlingEndsTheToggle() {
        tick(true, false, true);
        tick(false, false, false);
        assertFalse(toggles.isCrawlToggled());
    }
}
