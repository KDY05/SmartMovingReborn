package io.github.kdy05.smartmovingreborn.logic.fly;

/**
 * Decisions of Smart Moving's flying ({@code handleAlternativeFlying}, {@code standupIfPossible} and the flying
 * part of {@code updateEntityActionState}), as pure functions over measured values.
 */
public final class FlyLogic {
    /** The damping of every axis while flying Smart Moving's way. */
    public static final float DAMPING = 0.91f;
    /** How far below the box's bottom and above its top the ground and ceiling are looked for. */
    public static final double GAP_SEARCH = 1.1;
    /** Below this horizontal speed squared, hovering close to the ground lands. */
    private static final double LANDING_SPEED_SQUARE = 0.003;
    /** Above this vertical motion, hovering close to the ground lands. */
    private static final double LANDING_MOTION_Y = -0.03;
    /** The upward part of the flying direction while jump or sneak is held. */
    private static final float UPWARD = 0.98f;

    private FlyLogic() {
    }

    /** The upward part of the flying direction: up with jump, down with sneak, nothing with both. */
    public static float upward(boolean sneak, boolean jump) {
        return (jump ? UPWARD : 0) - (sneak ? UPWARD : 0);
    }

    /**
     * Whether a Smart Moving flyer hovers slowly enough to try landing ({@code tryLanding}), unless
     * {@code move.fly.ground.close} lets it fly close to the ground.
     */
    public static boolean tryLanding(boolean smartFlying, boolean flyCloseToGround, double horizontalSpeedSquare,
                                     double motionY) {
        return smartFlying && !flyCloseToGround && horizontalSpeedSquare < LANDING_SPEED_SQUARE
                && motionY > LANDING_MOTION_Y;
    }

    /** Whether the ground is close below a small box: less than a block between them. */
    public static boolean groundClose(double gapUnder) {
        return gapUnder < 1;
    }

    /**
     * Whether the standing box fits between the ground and the ceiling. The gap above is measured from the top of
     * the original's 0.8 high small box, so that a block's room is left for the rest of the standing one.
     *
     * @param gapOver the gap above, or -1 when the ground is not close and it was not measured
     */
    public static boolean standUpPossible(double gapUnder, double gapOver) {
        return gapUnder + gapOver >= 1;
    }

    /** What happens to a small box when flying ends ({@code standupIfPossible}). */
    public enum Restore {
        /** Nothing: not ending, or the box is not small. */
        NONE,
        /** The box grows back down by a block, in the air. */
        RESET,
        /** Down on the ground in the small box, crawling or sliding. */
        LIE_DOWN,
        /** Standing on the ground. */
        STAND_UP
    }

    /**
     * What ending a flight in a small box does. Far from the ground the box just grows back, unless sneak is
     * held: since standing up counts as impossible there, the original then lay down in the air too. Close to
     * the ground, the player stands, or lies down where standing does not fit or sneak and grab are held.
     */
    public static Restore restore(boolean restoring, boolean groundClose, boolean standUpPossible, boolean sneak,
                                  boolean grab) {
        if (!restoring) {
            return Restore.NONE;
        }
        if (!groundClose && !sneak) {
            return Restore.RESET;
        }
        if (!standUpPossible || sneak && grab) {
            return Restore.LIE_DOWN;
        }
        return Restore.STAND_UP;
    }

    /**
     * Whether lying down after a flight slides rather than crawls: with sliding on and grab held, or straight
     * out of a head jump.
     */
    public static boolean slideWhenLyingDown(boolean slideEnabled, boolean grab, boolean wasHeadJumping) {
        return slideEnabled && (grab || wasHeadJumping);
    }

    /**
     * Whether flying starts again right after vanilla ended it for touching the ground
     * ({@code move.fly.ground.collide}), unless sneak and grab are both held to land.
     */
    public static boolean flyAgain(boolean flyWhileOnGround, boolean sneak, boolean grab, boolean wasFlying,
                                   boolean flying, boolean onGround) {
        return flyWhileOnGround && (!sneak || !grab) && wasFlying && !flying && onGround;
    }
}
