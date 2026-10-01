package io.github.kdy05.smartmovingreborn.logic.jump;

/** The original's jump type numbers 0-14, in the same order. */
public enum JumpType {
    UP,
    CHARGE,
    /** Side and back jumps. */
    ANGLE,
    HEAD,
    SLIDE,
    CLIMB_UP,
    CLIMB_UP_HANDS_ONLY,
    CLIMB_BACK_UP,
    CLIMB_BACK_UP_HANDS_ONLY,
    CLIMB_BACK_HEAD,
    CLIMB_BACK_HEAD_HANDS_ONLY,
    WALL_UP,
    WALL_HEAD,
    /** A wall jump while still touching the wall: {@link #WALL_UP} without the vertical motion. */
    WALL_UP_TURN,
    /** {@link #WALL_HEAD} without the vertical motion, like {@link #WALL_UP_TURN}. */
    WALL_HEAD_TURN;

    /** The type whose factors apply ({@code tryJump} maps 13 and 14 to 11 and 12). */
    public JumpType base() {
        return switch (this) {
            case WALL_UP_TURN -> WALL_UP;
            case WALL_HEAD_TURN -> WALL_HEAD;
            default -> this;
        };
    }

    /** Whether the jump keeps the vertical motion as it is. */
    public boolean noVertical() {
        return this == WALL_UP_TURN || this == WALL_HEAD_TURN;
    }

    /** Whether the jump goes up; sliding only pushes forward. */
    boolean up() {
        return this != SLIDE;
    }

    /** Whether the jump starts a head jump. */
    public boolean head() {
        return this == HEAD || this == CLIMB_BACK_HEAD || this == CLIMB_BACK_HEAD_HANDS_ONLY || this == WALL_HEAD;
    }

    boolean climb() {
        return ordinal() >= CLIMB_UP.ordinal() && ordinal() <= CLIMB_BACK_HEAD_HANDS_ONLY.ordinal();
    }

    boolean wall() {
        return this == WALL_UP || this == WALL_HEAD;
    }

    /** Whether the speed the player moved at changes the jump's factors. */
    boolean bySpeed() {
        return this != ANGLE && !climb() && !wall();
    }
}
