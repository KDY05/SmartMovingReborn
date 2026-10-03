package io.github.kdy05.smartmovingreborn.state;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The part of a player's Smart Moving state that other players need to see, ported from the flags the
 * original sent in {@code SmartMovingSelf.addToSendQueue}. The original's rope sliding flag (Ropes+ support)
 * is out of scope and has been dropped.
 */
public final class MovingState {
    /** Upper bound (exclusive) of {@link #angleJumpType}. */
    public static final int ANGLE_JUMP_TYPES = 8;
    /** Upper bound (exclusive) of {@link #handsClimbType} and {@link #feetClimbType}. */
    public static final int CLIMB_TYPES = 16;

    public boolean sneakButton;
    public boolean wallJumping;
    public boolean fast;
    public boolean slow;
    public boolean climbBackJumping;
    public boolean climbJumping;
    public boolean handsVineClimbing;
    public boolean feetVineClimbing;
    public int angleJumpType;
    public boolean sliding;
    public boolean headJumping;
    public boolean levitating;
    public boolean ceilingClimbing;
    public boolean flyingAnimation;
    public boolean fallingAnimation;
    public boolean small;
    public boolean climbing;
    public boolean crawling;
    public boolean crawlClimbing;
    public boolean swimming;
    public boolean dipping;
    public boolean diving;
    public boolean jumping;
    public int handsClimbType;
    public int feetClimbType;

    public void clear() {
        StatePacketCodec.decode(0, this);
    }

    /** Crawling, sliding or head jumping: the moves that use vanilla's crawling pose, {@code Pose.SWIMMING}. */
    public boolean lying() {
        return crawling || sliding || headJumping;
    }

    /**
     * Climbing into a gap with the box shrunk from below ({@code isClimbCrawling}). The original did not send it;
     * it is the one way of climbing in a small box that is neither crawling nor crawl climbing.
     */
    public boolean climbCrawling() {
        return climbing && small && !crawlClimbing && !lying();
    }

    /** The moves in a small box, {@code Pose.SWIMMING}: lying, crawl climbing, climb crawling, swimming and diving. */
    public boolean smallPose() {
        return lying() || crawlClimbing || climbCrawling() || swimming || diving;
    }

    /** Short human-readable form for the debug screen, e.g. {@code crawling small hands=2}. */
    public String describe() {
        List<String> parts = new ArrayList<>();
        addIf(parts, sneakButton, "sneakButton");
        addIf(parts, wallJumping, "wallJumping");
        addIf(parts, fast, "fast");
        addIf(parts, slow, "slow");
        addIf(parts, climbBackJumping, "climbBackJumping");
        addIf(parts, climbJumping, "climbJumping");
        addIf(parts, handsVineClimbing, "handsVine");
        addIf(parts, feetVineClimbing, "feetVine");
        addIf(parts, angleJumpType != 0, "angleJump=" + angleJumpType);
        addIf(parts, sliding, "sliding");
        addIf(parts, headJumping, "headJumping");
        addIf(parts, levitating, "levitating");
        addIf(parts, ceilingClimbing, "ceilingClimbing");
        addIf(parts, flyingAnimation, "flyingAnim");
        addIf(parts, fallingAnimation, "fallingAnim");
        addIf(parts, small, "small");
        addIf(parts, climbing, "climbing");
        addIf(parts, crawling, "crawling");
        addIf(parts, crawlClimbing, "crawlClimbing");
        addIf(parts, swimming, "swimming");
        addIf(parts, dipping, "dipping");
        addIf(parts, diving, "diving");
        addIf(parts, jumping, "jumping");
        addIf(parts, handsClimbType != 0, "hands=" + handsClimbType);
        addIf(parts, feetClimbType != 0, "feet=" + feetClimbType);
        return parts.isEmpty() ? "-" : String.join(" ", parts);
    }

    private static void addIf(List<String> parts, boolean condition, String part) {
        if (condition) {
            parts.add(part);
        }
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof MovingState other && StatePacketCodec.encode(this) == StatePacketCodec.encode(other);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(StatePacketCodec.encode(this));
    }

    @Override
    public String toString() {
        return "MovingState[" + describe() + "]";
    }
}
