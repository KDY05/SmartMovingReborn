package io.github.kdy05.smartmovingreborn.world;

import net.minecraft.world.level.block.state.BlockState;

/**
 * The block questions {@link GrabDetector} asks, at block coordinates. The halves of a block are its lower and
 * upper half; "near" is the part on the side a player moving towards {@code o} comes from: the half block deep
 * slab for an axis direction, the quarter column for a diagonal one.
 * <p>
 * Ladders, vines, trap doors, doors, fences, walls, panes and fence gates are judged as whole blocks, like the
 * original's materials (ladders and vines empty, the others solid), with the exceptions it made for them. All
 * other blocks are judged by their collision shape.
 */
public interface ClimbTerrain {
    /** The block state, kept as what a climber holds on to. */
    BlockState state(int x, int y, int z);

    boolean isLadder(int x, int y, int z);

    boolean isVine(int x, int y, int z);

    /** In {@code #minecraft:climbable}, the original's Forge {@code isLadder}. */
    boolean isClimbable(int x, int y, int z);

    /** A ladder fixed to the block towards {@code o}, an axis direction. */
    boolean hasLadderOrientation(int x, int y, int z, ClimbOrientation o);

    /** A vine on the face towards {@code o}, an axis direction. */
    boolean hasVineOrientation(int x, int y, int z, ClimbOrientation o);

    boolean isIronBars(int x, int y, int z);

    /** Glass panes and iron bars ({@code BlockPane}). */
    boolean isPane(int x, int y, int z);

    /** Fences and walls. */
    boolean isFenceBase(int x, int y, int z);

    /** Walls ({@code cobblestone_wall}). */
    boolean isWall(int x, int y, int z);

    boolean isFenceGate(int x, int y, int z);

    boolean isOpenFenceGate(int x, int y, int z);

    /**
     * The original's {@code getWallFlag}: whether the pane, fence or wall at the position connects towards
     * {@code side}; a fence gate counts when closed and set across {@code heading}.
     */
    boolean wallConnects(int x, int y, int z, ClimbOrientation side, ClimbOrientation heading);

    boolean isTrapDoor(int x, int y, int z);

    boolean isClosedTrapDoor(int x, int y, int z);

    /** The trap door's hinge side lies ahead in {@code o} ({@code isTrapDoorFront}). */
    boolean isTrapDoorFront(int x, int y, int z, ClimbOrientation o);

    boolean isDoor(int x, int y, int z);

    boolean isDoorTop(int x, int y, int z);

    /** The door's panel blocks moving towards {@code o} inside its block ({@code isDoorFrontBlocked}). */
    boolean isDoorFrontBlocked(int x, int y, int z, ClimbOrientation o);

    boolean isStair(int x, int y, int z);

    boolean isTopStair(int x, int y, int z);

    /** Nothing to hold or stand on in the whole block ({@code isFullEmpty}). */
    boolean isFullEmpty(int x, int y, int z);

    /** The near upper half is empty: bottom slabs, beds, front bottom stairs, trap doors. */
    boolean isUpperHalfEmpty(int x, int y, int z, ClimbOrientation o);

    /** The near lower half is empty: top slabs, front top stairs. */
    boolean isLowerHalfEmpty(int x, int y, int z, ClimbOrientation o);

    /** The near upper half is solid ({@code isUpperHalfFrontFullSolid}). */
    boolean isUpperHalfSolid(int x, int y, int z, ClimbOrientation o);

    /** Fills only the near lower half: bottom slabs, beds, front bottom stairs ({@code isBottomHalfBlock}). */
    boolean isBottomHalf(int x, int y, int z, ClimbOrientation o);

    /** Fills only the near upper half: top slabs, front top stairs ({@code isTopHalfBlock}). */
    boolean isTopHalf(int x, int y, int z, ClimbOrientation o);

    /**
     * An edge to hold at half height: the near lower half is solid and the near upper half not wholly filled
     * (bottom slabs and every bottom stair but the back ones).
     */
    boolean hasHalfLedge(int x, int y, int z, ClimbOrientation o);

    /** The near lower half is wholly filled: the back of a top stair. */
    boolean isLowerHalfFull(int x, int y, int z, ClimbOrientation o);
}
