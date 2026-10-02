package io.github.kdy05.smartmovingreborn.world;

import net.minecraft.world.level.block.state.BlockState;

/**
 * What the best grab found so far holds on to, and the room above the grabs ({@code ClimbGap}). The room is
 * gathered over every grab, the block and direction only from the best one.
 */
public final class ClimbGap {
    /** The grabbed block, or null. */
    public BlockState block;
    /** For a grabbed vine, {@link GrabDetector#VINE_FRONT} or {@link GrabDetector#VINE_SIDE}; otherwise -1. */
    public int meta;
    /** There is room to stand up above a grab. */
    public boolean canStand;
    /** There is room to crawl, but not to stand, above a grab. */
    public boolean mustCrawl;
    public ClimbOrientation direction;
    /** The room of this grab is not gathered into the result (climb crawling past lower grabs). */
    boolean skipGaps;

    public ClimbGap() {
        reset();
    }

    public void reset() {
        block = null;
        meta = -1;
        canStand = false;
        mustCrawl = false;
        direction = null;
        skipGaps = false;
    }

    /** Gathers {@code other}'s room, and takes its grab if {@code takeGrab}. */
    void merge(ClimbGap other, boolean takeGrab) {
        if (!other.skipGaps) {
            canStand |= other.canStand;
            mustCrawl |= other.mustCrawl;
        }
        if (takeGrab) {
            block = other.block;
            meta = other.meta;
            direction = other.direction;
        }
    }
}
