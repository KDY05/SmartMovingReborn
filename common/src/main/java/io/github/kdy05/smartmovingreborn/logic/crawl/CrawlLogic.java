package io.github.kdy05.smartmovingreborn.logic.crawl;

/**
 * The crawling decisions of the original's {@code updateEntityActionState} ({@code SmartMovingSelf} 2311-2353),
 * as pure functions of what the caller measured, so they can be tested against the original.
 * <p>
 * The original shrank the bounding box from the top ({@code heightOffset}) and moved the player down or up to
 * match. Here the box follows the pose from the feet up, so no position correction is needed: crawling uses
 * vanilla's crawling pose, {@code Pose.SWIMMING}.
 */
public final class CrawlLogic {
    /**
     * Water deeper than this (in blocks, measured from the feet) prevents crawling, like the original's dipping
     * limit. Step 14 replaces it with the original's condition on the dipping depth.
     */
    public static final double MAX_WATER_DEPTH = 0.65;

    private CrawlLogic() {
    }

    /**
     * Whether the space forces crawling. The original only kept a crawling player down; vanilla additionally
     * forces its own crawl when neither standing nor crouching fits, and that case becomes Smart Moving crawling
     * too. Flying with its own small size ({@code move.fly} or {@code move.levitate.small}) never forces it.
     */
    public static boolean mustCrawl(boolean crawling, boolean fitsStanding, boolean fitsCrouching,
                                    boolean flying, boolean flyingKeepsSmall) {
        if (flying && flyingKeepsSmall) {
            return false;
        }
        return crawling ? !fitsStanding : !fitsStanding && !fitsCrouching;
    }

    /** Whether the input keeps an ongoing crawl: the toggle, or holding sneak (or grab without free climbing). */
    public static boolean inputContinueCrawl(boolean toggleMode, boolean toggled, boolean sneakPressed,
                                             boolean freeClimbing, boolean grabPressed) {
        return toggleMode ? toggled : sneakPressed || !freeClimbing && grabPressed;
    }

    /** Whether the player wants to crawl: keeps crawling, or presses grab while sneaking on the ground. */
    public static boolean wantCrawl(boolean crawlEnabled, boolean crawling, boolean flying, boolean inputContinueCrawl,
                                    boolean grabStarted, boolean sneakPressed, boolean onGround) {
        return crawlEnabled && !flying
                && (crawling && inputContinueCrawl || grabStarted && sneakPressed && onGround);
    }
}
