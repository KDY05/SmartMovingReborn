package io.github.kdy05.smartmovingreborn.state;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class StatePacketCodecTest {
    /** Each flag, its setter and getter, and the bit the original server/other clients read it from. */
    private record Flag(int bit, Consumer<MovingState> set, Predicate<MovingState> get) {
    }

    private static final Map<String, Flag> FLAGS = new LinkedHashMap<>();

    static {
        FLAGS.put("jumping", new Flag(8, s -> s.jumping = true, s -> s.jumping));
        FLAGS.put("diving", new Flag(9, s -> s.diving = true, s -> s.diving));
        FLAGS.put("dipping", new Flag(10, s -> s.dipping = true, s -> s.dipping));
        FLAGS.put("swimming", new Flag(11, s -> s.swimming = true, s -> s.swimming));
        FLAGS.put("crawlClimbing", new Flag(12, s -> s.crawlClimbing = true, s -> s.crawlClimbing));
        FLAGS.put("crawling", new Flag(13, s -> s.crawling = true, s -> s.crawling));
        FLAGS.put("climbing", new Flag(14, s -> s.climbing = true, s -> s.climbing));
        FLAGS.put("small", new Flag(15, s -> s.small = true, s -> s.small));
        FLAGS.put("fallingAnimation", new Flag(16, s -> s.fallingAnimation = true, s -> s.fallingAnimation));
        FLAGS.put("flyingAnimation", new Flag(17, s -> s.flyingAnimation = true, s -> s.flyingAnimation));
        FLAGS.put("ceilingClimbing", new Flag(18, s -> s.ceilingClimbing = true, s -> s.ceilingClimbing));
        FLAGS.put("levitating", new Flag(19, s -> s.levitating = true, s -> s.levitating));
        FLAGS.put("headJumping", new Flag(20, s -> s.headJumping = true, s -> s.headJumping));
        FLAGS.put("sliding", new Flag(21, s -> s.sliding = true, s -> s.sliding));
        FLAGS.put("feetVineClimbing", new Flag(25, s -> s.feetVineClimbing = true, s -> s.feetVineClimbing));
        FLAGS.put("handsVineClimbing", new Flag(26, s -> s.handsVineClimbing = true, s -> s.handsVineClimbing));
        FLAGS.put("climbJumping", new Flag(27, s -> s.climbJumping = true, s -> s.climbJumping));
        FLAGS.put("climbBackJumping", new Flag(28, s -> s.climbBackJumping = true, s -> s.climbBackJumping));
        FLAGS.put("slow", new Flag(29, s -> s.slow = true, s -> s.slow));
        FLAGS.put("fast", new Flag(30, s -> s.fast = true, s -> s.fast));
        FLAGS.put("wallJumping", new Flag(31, s -> s.wallJumping = true, s -> s.wallJumping));
        FLAGS.put("sneakButton", new Flag(33, s -> s.sneakButton = true, s -> s.sneakButton));
    }

    private static MovingState decode(long state) {
        MovingState s = new MovingState();
        StatePacketCodec.decode(state, s);
        return s;
    }

    @Test
    void eachFlagUsesTheOriginalBitAndRoundTrips() {
        FLAGS.forEach((name, flag) -> {
            MovingState s = new MovingState();
            flag.set().accept(s);
            long encoded = StatePacketCodec.encode(s);
            assertEquals(1L << flag.bit(), encoded, name);

            MovingState decoded = decode(encoded);
            assertTrue(flag.get().test(decoded), name);
            assertEquals(s, decoded, name);
            FLAGS.forEach((other, otherFlag) -> {
                if (!other.equals(name)) {
                    assertFalse(otherFlag.get().test(decoded), name + " leaked into " + other);
                }
            });
        });
    }

    @Test
    void multiBitFieldsUseTheirRanges() {
        MovingState s = new MovingState();
        s.feetClimbType = 15;
        assertEquals(0xFL, StatePacketCodec.encode(s));
        s.feetClimbType = 0;
        s.handsClimbType = 15;
        assertEquals(0xF0L, StatePacketCodec.encode(s));
        s.handsClimbType = 0;
        s.angleJumpType = 7;
        assertEquals(7L << 22, StatePacketCodec.encode(s));
    }

    @Test
    void everyValueRoundTrips() {
        for (int hands = 0; hands < MovingState.CLIMB_TYPES; hands++) {
            for (int feet = 0; feet < MovingState.CLIMB_TYPES; feet++) {
                for (int angle = 0; angle < MovingState.ANGLE_JUMP_TYPES; angle++) {
                    MovingState s = new MovingState();
                    s.handsClimbType = hands;
                    s.feetClimbType = feet;
                    s.angleJumpType = angle;
                    s.crawling = (hands + feet) % 2 == 0;
                    s.sneakButton = angle % 2 == 1;
                    MovingState decoded = decode(StatePacketCodec.encode(s));
                    assertEquals(hands, decoded.handsClimbType);
                    assertEquals(feet, decoded.feetClimbType);
                    assertEquals(angle, decoded.angleJumpType);
                    assertEquals(s.crawling, decoded.crawling);
                    assertEquals(s.sneakButton, decoded.sneakButton);
                }
            }
        }
    }

    @Test
    void allFieldsSetRoundTripAndLeaveTheReservedBitClear() {
        MovingState s = new MovingState();
        FLAGS.values().forEach(flag -> flag.set().accept(s));
        s.handsClimbType = 15;
        s.feetClimbType = 15;
        s.angleJumpType = 7;

        long encoded = StatePacketCodec.encode(s);
        assertEquals((1L << 34) - 1 - (1L << 32), encoded);
        assertEquals(s, decode(encoded));
        assertEquals(encoded, StatePacketCodec.encode(decode(encoded)));
    }

    @Test
    void clearResetsEverything() {
        MovingState s = new MovingState();
        FLAGS.values().forEach(flag -> flag.set().accept(s));
        s.handsClimbType = 3;
        s.clear();
        assertEquals(0L, StatePacketCodec.encode(s));
    }

    @Test
    void outOfRangeValuesAreRejected() {
        MovingState s = new MovingState();
        s.angleJumpType = 8;
        assertThrows(IllegalArgumentException.class, () -> StatePacketCodec.encode(s));
        s.angleJumpType = 0;
        s.handsClimbType = 16;
        assertThrows(IllegalArgumentException.class, () -> StatePacketCodec.encode(s));
        s.handsClimbType = 0;
        s.feetClimbType = -1;
        assertThrows(IllegalArgumentException.class, () -> StatePacketCodec.encode(s));
    }
}
