package io.github.kdy05.smartmovingreborn.state;

/**
 * Packs a {@link MovingState} into a {@code long} with the original's bit layout:
 * <pre>
 *  0-3  feetClimbType     12 crawlClimbing      20 headJumping        27 climbJumping
 *  4-7  handsClimbType    13 crawling           21 sliding            28 climbBackJumping
 *    8  jumping           14 climbing        22-24 angleJumpType      29 slow
 *    9  diving            15 small              25 feetVineClimbing   30 fast
 *   10  dipping           16 fallingAnimation   26 handsVineClimbing  31 wallJumping
 *   11  swimming          17 flyingAnimation                          32 (reserved, always 0)
 *                         18 ceilingClimbing                          33 sneakButton
 *                         19 levitating
 * </pre>
 * Bit 32 was the original's rope sliding flag (Ropes+ support, out of scope). It stays reserved so that the
 * positions the original server read (crawling 13, climbing 14, small 15, wallJumping 31, sneak 33, ...)
 * are unchanged.
 */
public final class StatePacketCodec {
    private StatePacketCodec() {
    }

    public static long encode(MovingState s) {
        if (s.angleJumpType < 0 || s.angleJumpType >= MovingState.ANGLE_JUMP_TYPES) {
            throw new IllegalArgumentException("angleJumpType out of range: " + s.angleJumpType);
        }
        if (s.handsClimbType < 0 || s.handsClimbType >= MovingState.CLIMB_TYPES) {
            throw new IllegalArgumentException("handsClimbType out of range: " + s.handsClimbType);
        }
        if (s.feetClimbType < 0 || s.feetClimbType >= MovingState.CLIMB_TYPES) {
            throw new IllegalArgumentException("feetClimbType out of range: " + s.feetClimbType);
        }
        long state = 0;
        state = bit(state, s.sneakButton);
        state = bit(state, false); // reserved: rope sliding
        state = bit(state, s.wallJumping);
        state = bit(state, s.fast);
        state = bit(state, s.slow);
        state = bit(state, s.climbBackJumping);
        state = bit(state, s.climbJumping);
        state = bit(state, s.handsVineClimbing);
        state = bit(state, s.feetVineClimbing);
        state = (state << 3) | s.angleJumpType;
        state = bit(state, s.sliding);
        state = bit(state, s.headJumping);
        state = bit(state, s.levitating);
        state = bit(state, s.ceilingClimbing);
        state = bit(state, s.flyingAnimation);
        state = bit(state, s.fallingAnimation);
        state = bit(state, s.small);
        state = bit(state, s.climbing);
        state = bit(state, s.crawling);
        state = bit(state, s.crawlClimbing);
        state = bit(state, s.swimming);
        state = bit(state, s.dipping);
        state = bit(state, s.diving);
        state = bit(state, s.jumping);
        state = (state << 4) | s.handsClimbType;
        state = (state << 4) | s.feetClimbType;
        return state;
    }

    private static long bit(long state, boolean flag) {
        return (state << 1) | (flag ? 1 : 0);
    }

    public static void decode(long state, MovingState s) {
        s.feetClimbType = (int) (state & 15);
        state >>>= 4;
        s.handsClimbType = (int) (state & 15);
        state >>>= 4;
        s.jumping = (state & 1) != 0;
        state >>>= 1;
        s.diving = (state & 1) != 0;
        state >>>= 1;
        s.dipping = (state & 1) != 0;
        state >>>= 1;
        s.swimming = (state & 1) != 0;
        state >>>= 1;
        s.crawlClimbing = (state & 1) != 0;
        state >>>= 1;
        s.crawling = (state & 1) != 0;
        state >>>= 1;
        s.climbing = (state & 1) != 0;
        state >>>= 1;
        s.small = (state & 1) != 0;
        state >>>= 1;
        s.fallingAnimation = (state & 1) != 0;
        state >>>= 1;
        s.flyingAnimation = (state & 1) != 0;
        state >>>= 1;
        s.ceilingClimbing = (state & 1) != 0;
        state >>>= 1;
        s.levitating = (state & 1) != 0;
        state >>>= 1;
        s.headJumping = (state & 1) != 0;
        state >>>= 1;
        s.sliding = (state & 1) != 0;
        state >>>= 1;
        s.angleJumpType = (int) (state & 7);
        state >>>= 3;
        s.feetVineClimbing = (state & 1) != 0;
        state >>>= 1;
        s.handsVineClimbing = (state & 1) != 0;
        state >>>= 1;
        s.climbJumping = (state & 1) != 0;
        state >>>= 1;
        s.climbBackJumping = (state & 1) != 0;
        state >>>= 1;
        s.slow = (state & 1) != 0;
        state >>>= 1;
        s.fast = (state & 1) != 0;
        state >>>= 1;
        s.wallJumping = (state & 1) != 0;
        state >>>= 2; // skip reserved rope sliding bit
        s.sneakButton = (state & 1) != 0;
    }
}
