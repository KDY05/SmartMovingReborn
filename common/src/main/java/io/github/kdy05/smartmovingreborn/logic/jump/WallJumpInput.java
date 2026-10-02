package io.github.kdy05.smartmovingreborn.logic.jump;

/**
 * Whether the player wants to wall jump ({@code updateEntityActionState} 2689-2723): triggered by a single or
 * double click of jump in the air, and kept while jump is held until a wall is touched. After a wall jump that
 * is not a head jump it stays wanted while jump is held, so the next wall jumps right away.
 */
public final class WallJumpInput {
    private int count;
    private boolean want;
    private boolean continueJumping;

    public void reset() {
        count = 0;
        want = false;
        continueJumping = false;
    }

    /**
     * Once per tick.
     *
     * @param canWallJump      whether a wall jump is possible now (otherwise the first click is forgotten)
     * @param doubleClickTicks the ticks a second click may follow the first, or 0 for single clicks
     * @param onGroundOrClimbing whether the player stands or climbs, which ends a chain of wall jumps
     * @param collided         whether the player touches a wall now
     */
    public void update(boolean canWallJump, int doubleClickTicks, boolean onGroundOrClimbing, boolean jumpPressed,
                       boolean jumpStarted, boolean collided) {
        if (continueJumping && (onGroundOrClimbing || !jumpPressed)) {
            continueJumping = false;
        }

        boolean trigger = false;
        if (doubleClickTicks > 0) {
            if (canWallJump) {
                if (jumpStarted) {
                    if (count == 0) {
                        count = doubleClickTicks;
                    } else {
                        trigger = true;
                        count = 0;
                    }
                } else if (count > 0) {
                    count--;
                }
            } else {
                count = 0;
            }
        } else {
            trigger = jumpStarted;
        }

        want = canWallJump && (trigger || continueJumping || want && jumpPressed && !collided);
    }

    /** Whether the player wants to wall jump this tick ({@code wantWallJumping}). */
    public boolean want() {
        return want;
    }

    /** A wall jump (or a climb back jump) happened: keep wanting the next one unless it was a head jump. */
    public void jumped(boolean headJumping) {
        continueJumping = !headJumping;
    }
}
