package io.github.kdy05.smartmovingreborn.logic;

import io.github.kdy05.smartmovingreborn.input.Button;

/**
 * The toggle bookkeeping that the original ran at the end of {@code updateEntityActionState}
 * ({@code SmartMovingSelf} 2805-2895), after every move was decided.
 */
public final class ToggleState {
    boolean sneakToggled;
    boolean crawlToggled;
    /** Set when a toggle started while sneak was held, so that releasing it does not end the toggle at once. */
    boolean ignoreNextSneakRelease;

    public boolean isSneakToggled() {
        return sneakToggled;
    }

    public boolean isCrawlToggled() {
        return crawlToggled;
    }

    /**
     * A move other than the crawl input turned into crawling ({@code toCrawling}): the toggle starts, and the
     * sneak release still to come does not end it.
     */
    public void toCrawling(boolean crawlMode) {
        if (crawlMode) {
            crawlToggled = true;
        }
        ignoreNextSneakRelease = true;
    }

    public void reset() {
        sneakToggled = false;
        crawlToggled = false;
        ignoreNextSneakRelease = false;
    }

    /**
     * Updates both toggles from this tick's decisions. Toggled crawling starts with crawling and ends when sneak
     * (pressed after crawling started) or jump is released, which then starts toggled sneaking. Toggled sneaking
     * starts with sneaking or when sneak is released during Smart Moving's sprint, and ends with crawling,
     * another sneak press, or releasing jump out of water.
     *
     * @param sprintOverSneak wanting to sneak and to sprint at once, where sprinting wins
     * @param inWater         swimming or diving, where jump does not end sneaking
     */
    public void update(boolean sneakMode, boolean crawlMode, boolean crawling, boolean wasCrawling,
                       boolean slow, boolean wasSlow, boolean fast, boolean sprintOverSneak, boolean inWater,
                       Button sneak, Button jump) {
        boolean willStopCrawl = false;
        boolean willStopCrawlStartSneak = false;
        if (sneakMode || crawlMode) {
            willStopCrawlStartSneak = crawling && (jump.stopPressed || sneak.stopPressed && !ignoreNextSneakRelease);
            willStopCrawl = !crawling || willStopCrawlStartSneak;
        }

        boolean willStopSneak = false;
        boolean willStartSneak = false;
        if (sneakMode) {
            willStopSneak = crawling && !willStopCrawlStartSneak
                    || wasSlow && sneak.startPressed
                    || !inWater && jump.stopPressed;
            if (sprintOverSneak && sneak.startPressed && sneakToggled) {
                willStopSneak = true;
                ignoreNextSneakRelease = true;
            }
            willStartSneak = willStopCrawlStartSneak && sneak.stopPressed
                    || fast && sneak.stopPressed && !ignoreNextSneakRelease
                    || slow && !wasSlow;
            if (willStartSneak) {
                sneakToggled = true;
            }
            if (willStopSneak) {
                sneakToggled = false;
            }
        }

        if (crawlMode) {
            if (crawling && !wasCrawling) {
                crawlToggled = true;
                ignoreNextSneakRelease = sneak.pressed;
            }
            if (willStopCrawl) {
                crawlToggled = false;
            }
        }

        if (sneak.stopPressed) {
            ignoreNextSneakRelease = false;
        }
    }
}
