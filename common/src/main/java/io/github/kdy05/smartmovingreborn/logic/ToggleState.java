package io.github.kdy05.smartmovingreborn.logic;

import io.github.kdy05.smartmovingreborn.input.Button;

/**
 * The toggle bookkeeping that the original ran at the end of {@code updateEntityActionState}
 * ({@code SmartMovingSelf} 2805-2895), after every move was decided. Sneak toggling joins in step 7.
 */
public final class ToggleState {
    boolean crawlToggled;
    /** Set when a toggle started while sneak was held, so that releasing it does not end the toggle at once. */
    boolean ignoreNextSneakRelease;

    public boolean isCrawlToggled() {
        return crawlToggled;
    }

    public void reset() {
        crawlToggled = false;
        ignoreNextSneakRelease = false;
    }

    /** Toggled crawling starts with crawling and ends when sneak (pressed after crawling started) or jump is released. */
    public void updateCrawl(boolean crawling, boolean wasCrawling, Button sneak, Button jump) {
        boolean stopByInput = crawling && (jump.stopPressed || sneak.stopPressed && !ignoreNextSneakRelease);
        if (crawling && !wasCrawling) {
            crawlToggled = true;
            ignoreNextSneakRelease = sneak.pressed;
        }
        if (!crawling || stopByInput) {
            crawlToggled = false;
        }
    }

    /** Last step of the tick, whether or not any toggle mode is on. */
    public void endTick(Button sneak) {
        if (sneak.stopPressed) {
            ignoreNextSneakRelease = false;
        }
    }
}
