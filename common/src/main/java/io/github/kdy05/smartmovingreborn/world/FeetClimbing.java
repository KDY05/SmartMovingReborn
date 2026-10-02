package io.github.kdy05.smartmovingreborn.world;

/** What the feet can do at the wall ahead, from worst to best ({@code FeetClimbing}). */
public enum FeetClimbing {
    NONE,
    BASE_HOLD,
    BASE_WITH_HANDS,
    TOP_WITH_HANDS,
    SLOW_UP_WITH_HOLD_WITHOUT_HANDS,
    SLOW_UP_WITH_SINK_WITHOUT_HANDS,
    FAST_UP;

    public boolean isRelevant() {
        return this != NONE;
    }

    public boolean isIndependentlyRelevant() {
        return ordinal() > BASE_WITH_HANDS.ordinal();
    }

    public boolean isUp() {
        return this == SLOW_UP_WITH_HOLD_WITHOUT_HANDS || this == SLOW_UP_WITH_SINK_WITHOUT_HANDS || this == FAST_UP;
    }

    /** The better of the two; {@code gap} gathers {@code otherGap}'s room and takes its grab if it is better. */
    public FeetClimbing max(FeetClimbing other, ClimbGap gap, ClimbGap otherGap) {
        gap.merge(otherGap, ordinal() < other.ordinal());
        return ordinal() < other.ordinal() ? other : this;
    }
}
