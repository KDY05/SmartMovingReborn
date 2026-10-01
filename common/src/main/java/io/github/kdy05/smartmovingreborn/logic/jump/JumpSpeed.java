package io.github.kdy05.smartmovingreborn.logic.jump;

/** How fast the player moved when jumping: the original's jump speed numbers 0-4, in the same order. */
public enum JumpSpeed {
    SPRINT,
    RUN,
    WALK,
    SNEAK,
    STAND;

    /**
     * The original's {@code getJumpSpeed}. Angled jumps (side, back, wall) never count as sprinting or running.
     *
     * @param running   vanilla sprinting without Smart Moving's sprint (the original's {@code isRunning})
     * @param sprinting Smart Moving's sprint ({@code isFast})
     */
    public static JumpSpeed of(boolean standing, boolean sneaking, boolean running, boolean sprinting,
                               boolean angled) {
        if (sprinting && !angled) {
            return SPRINT;
        }
        if (running && !angled) {
            return RUN;
        }
        if (sneaking) {
            return SNEAK;
        }
        return standing ? STAND : WALK;
    }
}
