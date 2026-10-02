package io.github.kdy05.smartmovingreborn.world;

/** What the hands can do at the wall ahead, from worst to best ({@code HandsClimbing}). */
public enum HandsClimbing {
    NONE,
    SINK,
    TOP_HOLD,
    BOTTOM_HOLD,
    UP,
    FAST_UP;

    public boolean isRelevant() {
        return this != NONE;
    }

    public boolean isUp() {
        return this == UP || this == FAST_UP;
    }

    public HandsClimbing toUp() {
        return this == BOTTOM_HOLD ? UP : this;
    }

    public HandsClimbing toDown() {
        return this == TOP_HOLD ? SINK : this;
    }

    /** The better of the two; {@code gap} gathers {@code otherGap}'s room and takes its grab if it is better. */
    public HandsClimbing max(HandsClimbing other, ClimbGap gap, ClimbGap otherGap) {
        gap.merge(otherGap, ordinal() < other.ordinal());
        return ordinal() < other.ordinal() ? other : this;
    }
}
