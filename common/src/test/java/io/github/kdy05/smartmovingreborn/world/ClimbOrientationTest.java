package io.github.kdy05.smartmovingreborn.world;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static io.github.kdy05.smartmovingreborn.world.ClimbOrientation.*;
import static org.junit.jupiter.api.Assertions.*;

class ClimbOrientationTest {
    @Test
    void rotatingFollowsTheOriginalsTable() {
        List<ClimbOrientation> ring = List.of(PZ, PP, ZP, NP, NZ, NN, ZN, PN);
        for (int i = 0; i < ring.size(); i++) {
            ClimbOrientation orientation = ring.get(i);
            assertEquals(ring.get((i + 1) % 8), orientation.rotate(45), orientation + " + 45");
            assertEquals(ring.get((i + 7) % 8), orientation.rotate(-45), orientation + " - 45");
            assertEquals(ring.get((i + 2) % 8), orientation.rotate(90), orientation + " + 90");
            assertEquals(ring.get((i + 4) % 8), orientation.rotate(180), orientation + " + 180");
            assertEquals(ring.get((i + 4) % 8), orientation.rotate(-180), orientation + " - 180");
            assertEquals(ring.get((i + 3) % 8), orientation.rotate(135), orientation + " + 135");
            assertEquals(ring.get((i + 5) % 8), orientation.rotate(-135), orientation + " - 135");
        }
        assertEquals(ZZ, ZZ.rotate(90));
    }

    @Test
    void lookingStraightAlongAnAxisGrabsOnlyThere() {
        assertEquals(Set.of(ZP), climbingOrientations(0, true, true, 90, 80));
        assertEquals(Set.of(PZ), climbingOrientations(270, true, true, 90, 80));
        assertEquals(Set.of(NZ), climbingOrientations(-270, true, true, 90, 80));
    }

    @Test
    void diagonalsUseHalfTheirAngleLikeTheTenTwoVersion() {
        // 80 degrees grabs 40 to each side of 45: from 5 to 85.
        assertEquals(Set.of(ZP, NP), climbingOrientations(20, true, true, 90, 80));
        assertEquals(Set.of(ZP), climbingOrientations(4, true, true, 90, 80));
        assertEquals(Set.of(NP, NZ), climbingOrientations(80, true, true, 90, 80));
        assertEquals(Set.of(NP), climbingOrientations(80, false, true, 90, 80));
    }

    @Test
    void facingPicksAnAxisDirectionFirst() {
        assertEquals(PZ, facing(285, 20, true, false));
        assertNull(facing(300, 20, true, false));
        assertEquals(PP, facing(300, 20, true, true));
        assertEquals(ZP, facing(355, 20, true, false));
    }

    @Test
    void borderGapIsTheDistanceToTheBlockBorderAhead() {
        assertEquals(0.75, PZ.horizontalBorderGap(2.25, 0), 1e-9);
        assertEquals(0.25, NZ.horizontalBorderGap(2.25, 0), 1e-9);
        // Negative coordinates measure the same; the original's remainder went negative there.
        assertEquals(0.7, NZ.horizontalBorderGap(-0.3, 0), 1e-9);
        assertEquals(0.3, PZ.horizontalBorderGap(-0.3, 0), 1e-9);
        assertEquals(0.4, ZP.horizontalBorderGap(0, 5.6), 1e-9);
        assertEquals(0, PP.horizontalBorderGap(0.5, 0.5));
    }
}
