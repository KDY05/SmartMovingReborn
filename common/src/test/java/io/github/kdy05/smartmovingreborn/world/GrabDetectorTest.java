package io.github.kdy05.smartmovingreborn.world;

import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GrabDetectorTest {
    /** Facing +X. */
    private static final float EAST = 270;
    private static final GrabDetector.Settings SETTINGS =
            new GrabDetector.Settings(true, true, 90, 80, GrabDetector.Settings.holdGap(1, 1));

    private enum Cell { AIR, FULL, BOTTOM_SLAB, TOP_SLAB, LADDER }

    /** Full blocks, slabs and ladders fixed to the block at +X; air everywhere else. */
    private static final class Terrain implements ClimbTerrain {
        private final Map<List<Integer>, Cell> cells = new HashMap<>();

        Terrain set(int x, int y, int z, Cell cell) {
            cells.put(List.of(x, y, z), cell);
            return this;
        }

        private Cell cell(int x, int y, int z) {
            return cells.getOrDefault(List.of(x, y, z), Cell.AIR);
        }

        @Override public BlockState state(int x, int y, int z) { return null; }
        @Override public boolean isLadder(int x, int y, int z) { return cell(x, y, z) == Cell.LADDER; }
        @Override public boolean isVine(int x, int y, int z) { return false; }
        @Override public boolean isClimbable(int x, int y, int z) { return isLadder(x, y, z); }
        @Override public boolean hasLadderOrientation(int x, int y, int z, ClimbOrientation o) {
            return isLadder(x, y, z) && o == ClimbOrientation.PZ;
        }
        @Override public boolean hasVineOrientation(int x, int y, int z, ClimbOrientation o) { return false; }
        @Override public boolean isIronBars(int x, int y, int z) { return false; }
        @Override public boolean isPane(int x, int y, int z) { return false; }
        @Override public boolean isFenceBase(int x, int y, int z) { return false; }
        @Override public boolean isWall(int x, int y, int z) { return false; }
        @Override public boolean isFenceGate(int x, int y, int z) { return false; }
        @Override public boolean isOpenFenceGate(int x, int y, int z) { return false; }
        @Override public boolean wallConnects(int x, int y, int z, ClimbOrientation side, ClimbOrientation heading) {
            return false;
        }
        @Override public boolean isTrapDoor(int x, int y, int z) { return false; }
        @Override public boolean isClosedTrapDoor(int x, int y, int z) { return false; }
        @Override public boolean isTrapDoorFront(int x, int y, int z, ClimbOrientation o) { return false; }
        @Override public boolean isDoor(int x, int y, int z) { return false; }
        @Override public boolean isDoorTop(int x, int y, int z) { return false; }
        @Override public boolean isDoorFrontBlocked(int x, int y, int z, ClimbOrientation o) { return true; }
        @Override public boolean isStair(int x, int y, int z) { return false; }
        @Override public boolean isTopStair(int x, int y, int z) { return false; }
        @Override public boolean isFullEmpty(int x, int y, int z) {
            Cell cell = cell(x, y, z);
            return cell == Cell.AIR || cell == Cell.LADDER;
        }
        @Override public boolean isUpperHalfEmpty(int x, int y, int z, ClimbOrientation o) {
            return isFullEmpty(x, y, z) || cell(x, y, z) == Cell.BOTTOM_SLAB;
        }
        @Override public boolean isLowerHalfEmpty(int x, int y, int z, ClimbOrientation o) {
            return isFullEmpty(x, y, z) || cell(x, y, z) == Cell.TOP_SLAB;
        }
        @Override public boolean isUpperHalfSolid(int x, int y, int z, ClimbOrientation o) {
            return cell(x, y, z) == Cell.FULL || cell(x, y, z) == Cell.TOP_SLAB;
        }
        @Override public boolean isBottomHalf(int x, int y, int z, ClimbOrientation o) {
            return cell(x, y, z) == Cell.BOTTOM_SLAB;
        }
        @Override public boolean isTopHalf(int x, int y, int z, ClimbOrientation o) {
            return cell(x, y, z) == Cell.TOP_SLAB;
        }
        @Override public boolean hasHalfLedge(int x, int y, int z, ClimbOrientation o) {
            return cell(x, y, z) == Cell.BOTTOM_SLAB;
        }
        @Override public boolean isLowerHalfFull(int x, int y, int z, ClimbOrientation o) {
            return cell(x, y, z) == Cell.FULL || cell(x, y, z) == Cell.BOTTOM_SLAB;
        }
    }

    /** A player in the middle of block (0, y, 0) with its feet at {@code feetY}. */
    private static GrabDetector.Result detect(Terrain terrain, double feetY, float yaw) {
        return new GrabDetector(terrain, SETTINGS).detect(0.5, feetY, 0.5, yaw, false, false, false);
    }

    @Test
    void nothingAroundHoldsNothing() {
        GrabDetector.Result result = detect(new Terrain(), 0, EAST);
        assertEquals(HandsClimbing.NONE, result.hands());
        assertEquals(FeetClimbing.NONE, result.feet());
    }

    @Test
    void aOneBlockWallIsSteppedOverWithRoomToStand() {
        GrabDetector.Result result = detect(new Terrain().set(1, 0, 0, Cell.FULL), 0, EAST);
        assertEquals(HandsClimbing.TOP_HOLD, result.hands());
        assertEquals(FeetClimbing.FAST_UP, result.feet());
        assertTrue(result.climbGap());
        assertFalse(result.climbCrawlGap());
        assertEquals(ClimbOrientation.PZ, result.feetGap().direction);
    }

    @Test
    void theTopOfATwoBlockWallIsHeldByTheHands() {
        Terrain terrain = new Terrain().set(1, 0, 0, Cell.FULL).set(1, 1, 0, Cell.FULL);
        GrabDetector.Result result = detect(terrain, 0, EAST);
        assertEquals(HandsClimbing.BOTTOM_HOLD, result.hands());
        assertEquals(FeetClimbing.NONE, result.feet());
        assertTrue(result.climbGap());
    }

    @Test
    void aBottomSlabIsHeldAtHalfHeight() {
        GrabDetector.Result result = detect(new Terrain().set(1, 0, 0, Cell.BOTTOM_SLAB), 0, EAST);
        assertEquals(FeetClimbing.FAST_UP, result.feet());
        assertTrue(result.climbGap());
    }

    @Test
    void aOneBlockHoleInTheWallLeavesRoomOnlyToCrawl() {
        Terrain terrain = new Terrain().set(1, 0, 0, Cell.FULL).set(1, 2, 0, Cell.FULL);
        GrabDetector.Result result = detect(terrain, 0, EAST);
        assertEquals(FeetClimbing.TOP_WITH_HANDS, result.feet());
        assertTrue(result.climbCrawlGap());
        assertFalse(result.climbGap());
    }

    @Test
    void aWallBehindTheViewIsNotGrabbed() {
        GrabDetector.Result result = detect(new Terrain().set(1, 0, 0, Cell.FULL), 0, 90);
        assertEquals(HandsClimbing.NONE, result.hands());
        assertEquals(FeetClimbing.NONE, result.feet());
    }

    @Test
    void aLadderInFrontIsHeldAllAround() {
        Terrain terrain = new Terrain();
        for (int y = 0; y < 4; y++) {
            terrain.set(0, y, 0, Cell.LADDER).set(1, y, 0, Cell.FULL);
        }
        GrabDetector.Result result = detect(terrain, 0, EAST);
        assertEquals(HandsClimbing.UP, result.hands());
        assertFalse(result.climbGap());
    }

    @Test
    void holdsAreReportedAtTheirEdge() {
        GrabDetector detector = new GrabDetector(new Terrain().set(1, 0, 0, Cell.FULL), SETTINGS);
        List<GrabDetector.Hold> holds = new ArrayList<>();
        detector.setHoldListener(holds::add);
        detector.detect(0.5, 0, 0.5, EAST, false, false, false);
        assertTrue(holds.stream().anyMatch(hold -> hold.direction() == ClimbOrientation.PZ && hold.y() == 1.0
                && hold.gap() == 4), holds::toString);
    }

    @Test
    void crawlingSearchesABlockLower() {
        // A crawling box lies on the ground: its hands find the top of a one block wall where standing ones
        // reach over it.
        GrabDetector.Result result = new GrabDetector(new Terrain().set(1, 0, 0, Cell.FULL), SETTINGS)
                .detect(0.5, 0, 0.5, EAST, false, false, true);
        assertEquals(HandsClimbing.BOTTOM_HOLD, result.hands());
        assertEquals(FeetClimbing.BASE_WITH_HANDS, result.feet());
    }

    @Test
    void heightsBelowZeroSearchTheSameEdges() {
        GrabDetector.Result result = detect(new Terrain().set(1, -10, 0, Cell.FULL), -10, EAST);
        assertEquals(HandsClimbing.TOP_HOLD, result.hands());
        assertEquals(FeetClimbing.FAST_UP, result.feet());
    }
}
