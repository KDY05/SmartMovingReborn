package io.github.kdy05.smartmovingreborn.logic;

import io.github.kdy05.smartmovingreborn.input.Button;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ToggleStateTest {
    private final ToggleState toggles = new ToggleState();
    private final Button sneak = new Button();
    private final Button jump = new Button();
    private boolean sneakMode;
    private boolean crawlMode;
    private boolean crawling;
    private boolean slow;

    /** One crawl-toggle tick: the buttons' new states, then whether the player crawls after the decisions. */
    private void tick(boolean sneakDown, boolean jumpDown, boolean crawlingNow) {
        crawlMode = true;
        tick(sneakDown, jumpDown, crawlingNow, false, false);
    }

    /** One tick with the modes set in the test: the buttons, then the moves decided this tick. */
    private void tick(boolean sneakDown, boolean jumpDown, boolean crawlingNow, boolean slowNow, boolean fast) {
        sneak.update(sneakDown);
        jump.update(jumpDown);
        boolean wasCrawling = crawling;
        crawling = crawlingNow;
        boolean wasSlow = slow;
        slow = slowNow;
        toggles.update(sneakMode, crawlMode, crawling, wasCrawling, slow, wasSlow, fast, false, false, sneak, jump);
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

    @Test
    void sneakingStartsTheSneakToggleAndAnotherPressEndsIt() {
        sneakMode = true;
        tick(true, false, false, true, false);
        assertTrue(toggles.isSneakToggled());
        tick(false, false, false, true, false);
        assertTrue(toggles.isSneakToggled());
        tick(true, false, false, true, false);
        assertFalse(toggles.isSneakToggled());
    }

    @Test
    void releasingJumpEndsTheSneakToggle() {
        sneakMode = true;
        tick(true, false, false, true, false);
        tick(false, true, false, true, false);
        tick(false, false, false, true, false);
        assertFalse(toggles.isSneakToggled());
    }

    @Test
    void releasingSneakWhileSprintingStartsTheSneakToggle() {
        sneakMode = true;
        tick(true, false, false, false, true);
        assertFalse(toggles.isSneakToggled());
        tick(false, false, false, false, true);
        assertTrue(toggles.isSneakToggled());
    }

    @Test
    void endingAToggledCrawlWithSneakStartsTheSneakToggle() {
        sneakMode = true;
        crawlMode = true;
        tick(true, false, true, false, false);
        tick(false, false, true, false, false);
        assertTrue(toggles.isCrawlToggled());
        assertFalse(toggles.isSneakToggled());
        tick(true, false, true, false, false);
        tick(false, false, true, false, false);
        assertFalse(toggles.isCrawlToggled());
        assertTrue(toggles.isSneakToggled());
    }
}
