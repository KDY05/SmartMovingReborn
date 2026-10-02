package io.github.kdy05.smartmovingreborn.logic.climb;

import io.github.kdy05.smartmovingreborn.world.FeetClimbing;
import io.github.kdy05.smartmovingreborn.world.HandsClimbing;

/**
 * Climbing decisions of the original's {@code handleClimbing} and {@code setOnlyShouldClimbSpeed}. A climbing
 * speed is the vertical motion set after the tick's movement, before gravity takes 0.08 and air drag 2 % of it:
 * {@link #STILL} holds the player in place.
 */
public final class ClimbLogic {
    /** The climbing speed that holds still. */
    public static final double STILL = 0.08;
    /** The speed of climbing straight up, which ladders speed up ({@code move.climb.free.ladder.*}). */
    public static final double STRAIGHT_UP = 0.14;
    /** Climb crawling into a gap rises no faster than this. */
    private static final double CLIMB_CRAWL_MAXIMUM = 0.17;

    private ClimbLogic() {
    }

    /**
     * What the hands and feet make of the wall for a free climber wanting up or down.
     *
     * @param speed     the climbing speed before the factors, NaN for not climbing
     * @param handsType the hands' animation: 0 none, 1 hold, 2 reach up
     * @param feetType  the feet's animation: 0 none, 1 step
     * @param jumpType  in a hold to jump up from, the climb jump to try first (5 with the feet on something, 6
     *                  hanging); otherwise 0
     */
    public record Decision(double speed, int handsType, int feetType, int jumpType) {
        public boolean climbing() {
            return !Double.isNaN(speed);
        }
    }

    /**
     * The climbing speed and animation for the holds found ({@code handleClimbing} 1103-1201).
     *
     * @param climbGap      room to stand up above a hold
     * @param climbCrawlGap room to crawl, not stand, above a hold
     * @param feetOnBed     the feet's hold is a bed
     * @param holding       hanging on without climbing down ({@code isClimbHolding})
     */
    public static Decision decide(HandsClimbing hands, FeetClimbing feet, boolean up, boolean down,
                                  boolean climbGap, boolean climbCrawlGap, boolean onGround, boolean feetOnBed,
                                  boolean holding) {
        double speed = Double.NaN;
        int handsType = 0;
        int feetType = 0;
        int jumpType = 0;
        if (up) {
            hands = hands.toUp();
            if (feet == FeetClimbing.FAST_UP && !(hands == HandsClimbing.NONE && onGround && !feetOnBed)) {
                speed = 0.2;
                feetType = 1;
            } else if ((climbGap || climbCrawlGap) && hands == HandsClimbing.FAST_UP
                    && (feet == FeetClimbing.NONE || feet == FeetClimbing.BASE_WITH_HANDS)) {
                speed = feet == FeetClimbing.NONE ? 0.1 : 0.2;
                handsType = 2;
                feetType = 1;
            } else if (feet.isRelevant() && hands.isRelevant()
                    && !(feet == FeetClimbing.BASE_HOLD && hands == HandsClimbing.SINK)
                    && !(hands == HandsClimbing.SINK && feet == FeetClimbing.TOP_WITH_HANDS)
                    && !(hands == HandsClimbing.TOP_HOLD && feet == FeetClimbing.TOP_WITH_HANDS)) {
                speed = STRAIGHT_UP;
                handsType = !climbGap && !climbCrawlGap
                        || hands == HandsClimbing.SINK && feet == FeetClimbing.BASE_WITH_HANDS ? 1 : 2;
                feetType = 1;
            } else if (hands.isUp()) {
                speed = 0.1;
                handsType = 1;
                feetType = 1;
            } else if (hands == HandsClimbing.TOP_HOLD || feet == FeetClimbing.BASE_HOLD
                    || feet == FeetClimbing.SLOW_UP_WITH_HOLD_WITHOUT_HANDS && hands == HandsClimbing.NONE) {
                jumpType = feet != FeetClimbing.NONE ? 5 : 6;
                speed = STILL;
                boolean reach = hands == HandsClimbing.SINK && feet == FeetClimbing.BASE_HOLD
                        || hands == HandsClimbing.TOP_HOLD && feet == FeetClimbing.TOP_WITH_HANDS;
                handsType = reach ? 2 : 1;
                feetType = 1;
            } else if (hands == HandsClimbing.SINK
                    || feet == FeetClimbing.SLOW_UP_WITH_SINK_WITHOUT_HANDS && hands == HandsClimbing.NONE) {
                speed = 0.05;
                handsType = 1;
                feetType = 1;
            }
        } else if (down) {
            hands = hands.toDown();
            handsType = 1;
            feetType = 1;
            if (hands == HandsClimbing.BOTTOM_HOLD && !feet.isIndependentlyRelevant()) {
                speed = STILL;
            } else if (hands.isRelevant()) {
                if (feet == FeetClimbing.FAST_UP) {
                    speed = 0.01;
                    handsType = 0;
                } else if (feet == FeetClimbing.SLOW_UP_WITH_HOLD_WITHOUT_HANDS
                        || feet == FeetClimbing.TOP_WITH_HANDS) {
                    speed = 0.01;
                } else if (feet != FeetClimbing.BASE_WITH_HANDS && feet != FeetClimbing.BASE_HOLD) {
                    speed = 0.05;
                    handsType = hands == HandsClimbing.FAST_UP ? 2 : 1;
                    feetType = 0;
                } else if (hands == HandsClimbing.UP && feet == FeetClimbing.BASE_HOLD) {
                    speed = 0.01;
                } else {
                    speed = hands == HandsClimbing.UP ? 0.05 : 0.01;
                }
            }
            if (Double.isNaN(speed)) {
                handsType = 0;
                feetType = 0;
            }
            if (holding) {
                speed = STILL;
            }
        }
        if (hands == HandsClimbing.NONE) {
            handsType = 0;
        } else if (feet == FeetClimbing.NONE) {
            feetType = 0;
        }
        return new Decision(speed, handsType, feetType, jumpType);
    }

    /**
     * The climbing speed with the speed factors ({@code setOnlyShouldClimbSpeed}): {@link #STILL} stays, faster
     * and slower speeds are scaled by the up or down factor around it.
     *
     * @param speedFactor  the player's speed factor ({@code getSpeedFactor()})
     * @param ladderFactor {@code move.climb.free.ladder.*} for climbing straight up ladders, otherwise 1
     * @param climbingInto climbing into a gap, which holds still ({@code climbIntoCount > 0})
     * @param crawlGap     climb crawling into a gap to crawl through
     */
    public static double scale(double speed, float speedFactor, float ladderFactor, float upFactor,
                               float downFactor, boolean climbingInto, boolean crawlGap) {
        if (climbingInto) {
            return STILL;
        }
        if (speed == STILL) {
            return speed;
        }
        float factor = speedFactor * ladderFactor;
        double result = speed > STILL
                ? (speed - STILL) * upFactor * factor + STILL
                : STILL - (STILL - speed) * downFactor * factor;
        return crawlGap && result > STILL ? Math.min(CLIMB_CRAWL_MAXIMUM, result) : result;
    }

    /** The extra factor for climbing straight up {@code ladders} ladder blocks (only exactly one or two count). */
    public static float ladderFactor(int ladders, float oneUpFactor, float twoUpFactor) {
        return switch (ladders) {
            case 1 -> oneUpFactor;
            case 2 -> twoUpFactor;
            default -> 1;
        };
    }

    /**
     * The "simple" base climbing speed on a ladder or vine against a wall, before the speed factor: from the
     * climbable blocks at the feet and the hands.
     */
    public static double simpleSpeed(boolean feet, boolean hands) {
        return feet ? 0.2 : hands ? 0.1 : 0;
    }

    /**
     * The "smart" base climbing speed: like {@link #simpleSpeed}, but a hold beside the missing half lets the
     * player climb at full speed.
     */
    public static double smartSpeed(boolean feet, boolean hands, boolean handsSubstitute, boolean feetSubstitute) {
        if (feet && hands) {
            return 0.2;
        }
        if (feet) {
            return handsSubstitute ? 0.2 : 0.1;
        }
        if (hands) {
            return feetSubstitute ? 0.2 : 0.1;
        }
        return 0;
    }

    /**
     * The jump off the wall from hanging on ({@code handleClimbing} 1177-1180): a head jump when grab is held,
     * or when it is not with {@code move.jump.climb.back.head.on.grab} off; each with and without the feet on a
     * hold. The original named its flag for the feet case {@code handsOnly}, the other way round.
     *
     * @return 7 or 8 for a jump up and back, 9 or 10 for a head jump, the first of each with the feet on a hold
     */
    public static int backJumpType(boolean feetHold, boolean headOnGrab, boolean grabPressed) {
        boolean head = headOnGrab == grabPressed;
        return head ? (feetHold ? 9 : 10) : (feetHold ? 7 : 8);
    }

    /** The minimum movement per tick that still counts as climb sprinting ({@code isClimbSprintSpeed}). */
    public static double sprintMinimumTickDistance(boolean up, boolean down, float upFactor, float downFactor) {
        return up ? 0.07 * upFactor : down ? 0.11 * downFactor : 0.07;
    }
}
