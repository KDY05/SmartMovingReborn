package io.github.kdy05.smartmovingreborn.world;

import java.util.function.Consumer;

/**
 * Finds what a free climbing player can hold on to around it, from the original's {@code Orientation}
 * ({@code seekClimbGap} and everything below it). Heights are counted in half blocks: the hands look around two
 * blocks above the feet, the feet half a block above them, each a few half blocks up and down. An odd half
 * block holds on to the middle of a block ({@link #hasHalfHold}), an even one to its bottom edge, which is the
 * top of the block below ({@link #hasBottomHold}). Each hold also measures the room above it, telling whether
 * the player could stand or crawl over it.
 * <p>
 * The blocks come from a {@link ClimbTerrain}. Branches for other mods' blocks (Carpenter's Blocks, RedPower,
 * Better Than Wolves, Ropes+, the grappling hook, Ladder Kit) are left out. Not thread safe: one detection at a
 * time.
 */
public final class GrabDetector {
    /** {@link ClimbGap#meta} for a vine held from the front. */
    public static final int VINE_FRONT = 0;
    /** {@link ClimbGap#meta} for a vine held from the side. */
    public static final int VINE_SIDE = 1;

    private static final int NO_GRAB = 0;
    private static final int HALF_GRAB = 1;
    private static final int AROUND_GRAB = 2;
    /** How close (in blocks) to the block border ahead a side vine must be, to hold on to it. */
    private static final double VINE_SIDE_GAP = 0.65;

    /**
     * The options detection depends on.
     *
     * @param freeBaseClimb   ladders and vines are free climbed ({@code isFreeBaseClimb})
     * @param freeFence       {@code move.climb.free.fence}
     * @param orthogonalAngle {@code move.climb.free.direction.orthogonal.angle}
     * @param diagonalAngle   {@code move.climb.free.direction.diagonal.angle}
     * @param holdGap         how far past an edge, in half blocks, the hands still only hold on
     *                        ({@code _handClimbingHoldGap})
     */
    public record Settings(boolean freeBaseClimb, boolean freeFence, float orthogonalAngle, float diagonalAngle,
                           float holdGap) {
        /** The hold gap from the free climbing speed factors, like the original's. */
        public static float holdGap(float upSpeedFactor, float downSpeedFactor) {
            return Math.min(0.25f, 0.06f * Math.max(upSpeedFactor, downSpeedFactor));
        }
    }

    /** What the hands and feet found around the player, from {@code handleClimbing}. */
    public record Result(HandsClimbing hands, FeetClimbing feet, ClimbGap handsGap, ClimbGap feetGap,
                         boolean neighborClimbing, boolean neighborClimbGap, boolean neighborClimbCrawlGap,
                         boolean climbGap, boolean climbCrawlGap) {
    }

    /**
     * One hold found, for debugging: the edge between the player's block and the one ahead at {@code direction},
     * at height {@code y}.
     */
    public record Hold(ClimbOrientation direction, int blockX, int blockZ, double y, boolean hands, int gap) {
    }

    private final ClimbTerrain terrain;
    private final Settings settings;
    private Consumer<Hold> holdListener;

    private final ClimbGap gapTemp = new ClimbGap();
    private final ClimbGap gapOuterTemp = new ClimbGap();

    // The original kept the state of one search in static fields; these are the same.
    private ClimbOrientation o;
    private int baseX;
    private int baseZ;
    private double playerX;
    private double playerZ;
    private int remoteX;
    private int remoteZ;
    private double baseHalves;
    private boolean climbCrawling;
    private boolean crawlClimbing;
    private boolean small;
    private boolean crawl;
    private double halfOffset;
    private int allHalves;
    private boolean searchingHands;
    private int localHalf;
    private int localY;
    private boolean grabRemote;
    private int grabType;
    private int grabX;
    private int grabY;
    private int grabZ;
    private boolean hasGrabBlock;
    private int grabMeta;
    private int vineX;
    private int vineY;
    private int vineZ;

    public GrabDetector(ClimbTerrain terrain, Settings settings) {
        this.terrain = terrain;
        this.settings = settings;
    }

    /** Receives every hold found during {@link #detect}, or nothing for null. */
    public void setHoldListener(Consumer<Hold> holdListener) {
        this.holdListener = holdListener;
    }

    /**
     * Looks around a free climbing player ({@code handleClimbing}, the part that calls {@code seekClimbGap}).
     * Climb crawling, crawl climbing, crawling and sliding search a block lower than the box: a crawling or
     * sliding box lies on the ground and its holds are lower, and a climb crawling one was raised by a block.
     *
     * @param boxBottom the bottom of the player's box
     * @param small     crawling or sliding
     */
    public Result detect(double x, double boxBottom, double z, float yaw, boolean climbCrawling,
                         boolean crawlClimbing, boolean small) {
        this.climbCrawling = climbCrawling;
        this.crawlClimbing = crawlClimbing;
        this.small = small;
        int blockX = (int) Math.floor(x);
        int blockZ = (int) Math.floor(z);
        double bottom = climbCrawling || crawlClimbing || small ? boxBottom - 1 : boxBottom;
        double halves = bottom * 2 + 1;

        HandsClimbing[] hands = {HandsClimbing.NONE};
        FeetClimbing[] feet = {FeetClimbing.NONE};
        ClimbGap handsGap = new ClimbGap();
        ClimbGap feetGap = new ClimbGap();
        for (ClimbOrientation orientation : ClimbOrientation.ORTHOGONALS) {
            seekClimbGap(orientation, yaw, blockX, x, halves, blockZ, z, hands, feet, handsGap, feetGap);
        }
        boolean neighborClimbing = hands[0] != HandsClimbing.NONE || feet[0] != FeetClimbing.NONE;
        boolean neighborClimbGap = handsGap.canStand || feetGap.canStand;
        boolean neighborClimbCrawlGap = handsGap.mustCrawl || feetGap.mustCrawl;
        if (!small) {
            for (ClimbOrientation orientation : ClimbOrientation.DIAGONALS) {
                seekClimbGap(orientation, yaw, blockX, x, halves, blockZ, z, hands, feet, handsGap, feetGap);
            }
        }
        return new Result(hands[0], feet[0], handsGap, feetGap, neighborClimbing, neighborClimbGap,
                neighborClimbCrawlGap, handsGap.canStand || feetGap.canStand,
                handsGap.mustCrawl || feetGap.mustCrawl);
    }

    /**
     * The smart base climbing mode's check for a hold the hands could use instead of a ladder, in the block
     * towards {@code o} at height {@code y} ({@code isHandsLadderSubstitute}). The original took the player's
     * block and position from the last free climbing search; here they are passed in.
     */
    public boolean isHandsLadderSubstitute(ClimbOrientation o, int x, int y, int z, double playerX,
                                           double playerZ) {
        return substitute(o, x, y, z, playerX, playerZ, 1) || substitute(o, x, y, z, playerX, playerZ, 0)
                || substitute(o, x, y, z, playerX, playerZ, -1);
    }

    /** Like {@link #isHandsLadderSubstitute}, for the feet ({@code isFeetLadderSubstitute}). */
    public boolean isFeetLadderSubstitute(ClimbOrientation o, int x, int y, int z, double playerX,
                                          double playerZ) {
        return substitute(o, x, y, z, playerX, playerZ, 1) || substitute(o, x, y, z, playerX, playerZ, 0);
    }

    private boolean substitute(ClimbOrientation o, int x, int y, int z, double playerX, double playerZ,
                               int halfOffset) {
        this.o = o;
        baseX = x;
        baseZ = z;
        remoteX = x + o.x;
        remoteZ = z + o.z;
        this.playerX = playerX;
        this.playerZ = playerZ;
        climbCrawling = false;
        crawlClimbing = false;
        small = false;
        crawl = false;
        allHalves = 2 * y;
        return ladderSubstitute(halfOffset, null) > 0;
    }

    private void seekClimbGap(ClimbOrientation orientation, float yaw, int blockX, double x, double halves,
                              int blockZ, double z, HandsClimbing[] hands, FeetClimbing[] feet, ClimbGap handsGap,
                              ClimbGap feetGap) {
        if (!orientation.isRotationForClimbing(yaw, settings.orthogonalAngle(), settings.diagonalAngle())) {
            return;
        }
        o = orientation;
        baseX = blockX;
        baseZ = blockZ;
        playerX = x;
        playerZ = z;
        baseHalves = halves;
        remoteX = blockX + orientation.x;
        remoteZ = blockZ + orientation.z;
        hands[0] = hands[0].max(handsClimbing(gapOuterTemp), handsGap, gapOuterTemp);
        feet[0] = feet[0].max(feetClimbing(gapOuterTemp), feetGap, gapOuterTemp);
    }

    private HandsClimbing handsClimbing(ClimbGap out) {
        out.reset();
        gapTemp.reset();
        initializeOffset(3, true);
        HandsClimbing result = HandsClimbing.NONE;
        float holdGap = settings.holdGap();
        if (ladderSubstitute(1, gapTemp) > 0) {
            result = result.max(halfOffset > 1 - holdGap ? HandsClimbing.UP : HandsClimbing.NONE, out, gapTemp);
        }
        if (ladderSubstitute(0, gapTemp) > 0) {
            result = result.max(halfOffset < holdGap ? HandsClimbing.BOTTOM_HOLD : HandsClimbing.UP, out, gapTemp);
        }

        gapTemp.skipGaps = climbCrawling || crawlClimbing;
        int gap = ladderSubstitute(-1, gapTemp);
        if (gap > 0 && (!small || gap <= 1)) {
            HandsClimbing hands;
            if (!climbCrawling && gap > 2 || climbCrawling && gap > 1) {
                hands = HandsClimbing.FAST_UP;
            } else if (halfOffset < holdGap) {
                hands = grabType == AROUND_GRAB ? HandsClimbing.UP : HandsClimbing.TOP_HOLD;
            } else {
                hands = grabType == AROUND_GRAB ? HandsClimbing.TOP_HOLD : HandsClimbing.SINK;
            }
            result = result.max(hands, out, gapTemp);
        }

        gap = ladderSubstitute(-2, gapTemp);
        if (gap > 0 && !small && (gap > 2 && !crawlClimbing || grabType == AROUND_GRAB || gap > 1 && climbCrawling)) {
            HandsClimbing hands;
            if (halfOffset < holdGap && !climbCrawling) {
                hands = HandsClimbing.TOP_HOLD;
            } else if (climbCrawling) {
                hands = HandsClimbing.FAST_UP;
            } else {
                hands = HandsClimbing.SINK;
            }
            result = result.max(hands, out, gapTemp);
        }
        return result;
    }

    private FeetClimbing feetClimbing(ClimbGap out) {
        out.reset();
        gapTemp.reset();
        initializeOffset(0, false);
        FeetClimbing result = FeetClimbing.NONE;
        float holdGap = settings.holdGap();
        if (ladderSubstitute(2, gapTemp) > 0) {
            result = result.max(FeetClimbing.NONE, out, gapTemp);
        }

        gapTemp.skipGaps = climbCrawling || crawlClimbing;
        int gap = ladderSubstitute(1, gapTemp);
        if (gap > 0 && !small) {
            FeetClimbing feet;
            if (gap > 3 && !climbCrawling) {
                feet = crawlClimbing ? FeetClimbing.NONE : FeetClimbing.FAST_UP;
            } else if ((climbCrawling || crawlClimbing) && gap > 1) {
                feet = crawlClimbing ? FeetClimbing.BASE_WITH_HANDS : FeetClimbing.FAST_UP;
            } else if (gap > 2) {
                feet = climbCrawling ? FeetClimbing.NONE : FeetClimbing.SLOW_UP_WITH_HOLD_WITHOUT_HANDS;
            } else {
                feet = FeetClimbing.TOP_WITH_HANDS;
            }
            result = result.max(feet, out, gapTemp);
        }

        gap = ladderSubstitute(0, gapTemp);
        if (gap > 0) {
            FeetClimbing feet;
            if (gap > 3 && !small && !crawlClimbing) {
                feet = FeetClimbing.FAST_UP;
            } else if (gap > 2 && !small) {
                if (climbCrawling) {
                    feet = FeetClimbing.NONE;
                } else if (halfOffset < holdGap) {
                    feet = FeetClimbing.SLOW_UP_WITH_HOLD_WITHOUT_HANDS;
                } else {
                    feet = FeetClimbing.SLOW_UP_WITH_SINK_WITHOUT_HANDS;
                }
            } else if (halfOffset < 1 - holdGap) {
                feet = FeetClimbing.BASE_WITH_HANDS;
            } else {
                feet = FeetClimbing.BASE_HOLD;
            }
            result = result.max(feet, out, gapTemp);
        }

        if (ladderSubstitute(-1, gapTemp) > 0) {
            result = result.max(FeetClimbing.NONE, out, gapTemp);
        }
        if (crawlClimbing || small) {
            result = result.max(FeetClimbing.BASE_WITH_HANDS, out, gapTemp);
        }
        return result;
    }

    /** Moves the search height {@code offsetHalves} half blocks above the feet reference ({@code initializeOffset}). */
    private void initializeOffset(double offsetHalves, boolean hands) {
        crawl = climbCrawling || crawlClimbing || small;
        double halves = baseHalves + offsetHalves;
        allHalves = (int) Math.floor(halves);
        halfOffset = halves - allHalves;
        searchingHands = hands;
    }

    /** Looks {@code localOffset} half blocks from the search height ({@code initializeLocal}). */
    private void initializeLocal(int localOffset) {
        int halfIndex = allHalves + localOffset;
        localHalf = Math.floorMod(halfIndex, 2);
        localY = Math.floorDiv(halfIndex, 2);
    }

    /**
     * The room above the hold {@code localOffset} half blocks from the search height, 0 for no hold
     * ({@code isLadderSubstitute}): 1 only to hold on, 2 or 3 to crawl over, 4 or 5 to stand up.
     */
    private int ladderSubstitute(int localOffset, ClimbGap out) {
        initializeLocal(localOffset);
        int gap;
        if (localHalf == 1) {
            if (!hasHalfHold()) {
                gap = 0;
            } else if (!grabRemote) {
                boolean overLadder = isOnLadderOrVine(0) || isOnOpenTrapDoor(0);
                boolean overOverLadder = isOnLadderOrVine(1) || isOnOpenTrapDoor(1);
                boolean overAccessible = isBaseAccessible(1, false, true);
                boolean overFullAccessible = overAccessible && isFullAccessible(1, false);
                boolean overOverFullAccessible = overAccessible && isFullAccessible(2, false);
                if (overLadder) {
                    gap = 1;
                } else if (overAccessible) {
                    if (overFullAccessible) {
                        gap = overOverFullAccessible || !crawl ? 5 : 3;
                    } else {
                        gap = overOverLadder ? 5 : 1;
                    }
                } else {
                    gap = 1;
                }
            } else if (isBaseAccessible(0, false, false)) {
                if (!isUpperHalfFrontEmpty(o, remoteX, y(0), remoteZ)) {
                    gap = 1;
                } else if (!isFullAccessible(1, true)) {
                    gap = 1;
                } else if (isFullAccessible(2, true)) {
                    gap = 5;
                } else if (terrain.isTopHalf(remoteX, y(2), remoteZ, o)) {
                    gap = 4;
                } else {
                    gap = 3;
                }
            } else {
                gap = 0;
            }
        } else if (!hasBottomHold()) {
            gap = 0;
        } else if (!grabRemote) {
            boolean overLadder = isOnLadderOrVine(0) || isOnOpenTrapDoor(0);
            boolean overOverLadder = isOnLadderOrVine(1) || isOnOpenTrapDoor(1);
            boolean overAccessible = isBaseAccessible(0, false, true);
            boolean overOverAccessible = isBaseAccessible(1, false, true);
            boolean overFullAccessible = overAccessible && isFullAccessible(0, false);
            boolean overOverFullAccessible = overAccessible && isFullAccessible(1, false);
            if (overLadder) {
                gap = 1;
            } else if (overAccessible) {
                if (overFullAccessible) {
                    if (overOverAccessible) {
                        gap = overOverFullAccessible || !crawl ? 4 : 2;
                    } else {
                        gap = 2;
                    }
                } else {
                    gap = overOverLadder ? 2 : 1;
                }
            } else {
                gap = 1;
            }
        } else if (isBaseAccessible(0, false, false)) {
            if (!isFullAccessible(0, true)) {
                gap = 1;
            } else {
                gap = isFullAccessible(1, true) ? 4 : 2;
            }
        } else {
            gap = 0;
        }

        if (gap > 0) {
            if (out != null) {
                out.block = hasGrabBlock ? terrain.state(grabX, grabY, grabZ) : null;
                out.meta = grabMeta;
                out.canStand = gap > 3;
                out.mustCrawl = gap > 1 && gap < 4;
                out.direction = o;
            }
            if (holdListener != null && out != null) {
                holdListener.accept(new Hold(o, baseX, baseZ, localY + localHalf * 0.5, searchingHands, gap));
            }
        }
        return gap;
    }

    /** A hold in the middle of the block ({@code hasHalfHold}). */
    private boolean hasHalfHold() {
        if (settings.freeBaseClimb()) {
            if (isOnLadder(0) && isOnLadderFront(0)) {
                return setHalfGrabType(AROUND_GRAB, baseX, y(0), baseZ, false);
            }
            if (remoteLadderClimbing(0)) {
                return setHalfGrabType(AROUND_GRAB, remoteX, y(0), remoteZ, true);
            }
        }

        if (isEmpty(baseX, y(0), baseZ) && terrain.isIronBars(remoteX, y(0), remoteZ)
                && headedToFrontWall(o, remoteX, y(0), remoteZ)) {
            return setHalfGrabType(HALF_GRAB, remoteX, y(0), remoteZ, true);
        }
        boolean baseWall = isWallBlock(baseX, y(0), baseZ);
        if (baseWall && terrain.isIronBars(baseX, y(0), baseZ) && headedToBaseWall(0)) {
            return setHalfGrabType(HALF_GRAB, baseX, y(0), baseZ, false);
        }

        if (settings.freeFence()) {
            if (isFence(remoteX, y(0), remoteZ) && headedToFrontWall(o, remoteX, y(0), remoteZ)) {
                if (!isFence(baseX, y(0), baseZ) || headedToFrontSideWall(remoteX, y(0), remoteZ)) {
                    return setHalfGrabType(HALF_GRAB, remoteX, y(0), remoteZ, true);
                }
            }
            // The original read the block below this one at the remote position for a fence gate's state.
            if (isFence(remoteX, y(-1), remoteZ) && headedToFrontWall(o, remoteX, y(-1), remoteZ)) {
                if (!isFence(baseX, y(-1), baseZ) || headedToFrontSideWall(remoteX, y(-1), remoteZ)) {
                    return setHalfGrabType(HALF_GRAB, remoteX, y(0), remoteZ, true);
                }
            }
            if (isFence(baseX, y(0), baseZ) && headedToBaseWall(0)) {
                return setHalfGrabType(HALF_GRAB, baseX, y(0), baseZ, false);
            }
            if (isFence(baseX, y(-1), baseZ) && headedToBaseWall(-1)) {
                return setHalfGrabType(HALF_GRAB, baseX, y(-1), baseZ, false);
            }
            if (terrain.isWall(remoteX, y(0), remoteZ) && !headedToRemoteFlatWall(0)) {
                return setHalfGrabType(HALF_GRAB, remoteX, y(0), remoteZ, true);
            }
            if (terrain.isWall(remoteX, y(-1), remoteZ) && !headedToRemoteFlatWall(-1)) {
                return setHalfGrabType(HALF_GRAB, remoteX, y(-1), remoteZ, true);
            }
        }

        // A bottom slab or a bottom stair ahead, but not the next step of a stair the player stands on.
        boolean ledge = terrain.hasHalfLedge(remoteX, y(0), remoteZ, o)
                && !(terrain.isStair(remoteX, y(0), remoteZ) && terrain.isStair(baseX, y(-1), baseZ)
                && terrain.isBottomHalf(baseX, y(-1), baseZ, o));
        if (ledge) {
            return setHalfGrabType(HALF_GRAB, remoteX, y(0), remoteZ, true);
        }
        if (terrain.isTrapDoor(remoteX, y(0), remoteZ) && terrain.isClosedTrapDoor(remoteX, y(0), remoteZ)) {
            return setHalfGrabType(HALF_GRAB, remoteX, y(0), remoteZ, true);
        }
        if (terrain.isTrapDoor(baseX, y(0), baseZ) && !terrain.isClosedTrapDoor(baseX, y(0), baseZ)) {
            return setHalfGrabType(HALF_GRAB, baseX, y(0), baseZ, false);
        }
        if (settings.freeBaseClimb()) {
            int meta = baseVineClimbing(0);
            if (meta == -1) {
                meta = remoteVineClimbing(0);
            }
            if (meta != -1) {
                return setHalfGrabType(HALF_GRAB, vineX, vineY, vineZ, false, meta);
            }
        }
        return setHalfGrabType(NO_GRAB, 0, 0, 0, true);
    }

    /** A hold at the bottom of the block, on top of the one below ({@code hasBottomHold}). */
    private boolean hasBottomHold() {
        if (settings.freeBaseClimb()) {
            if (isOnLadder(-1) && isOnLadderFront(-1)) {
                return setBottomGrabType(AROUND_GRAB, baseX, y(-1), baseZ, false);
            }
            if (isOnLadder(0) && isOnLadderFront(0)) {
                return setBottomGrabType(AROUND_GRAB, baseX, y(0), baseZ, false);
            }
            if (remoteLadderClimbing(-1)) {
                return setBottomGrabType(AROUND_GRAB, remoteX, y(-1), remoteZ, true);
            }
            if (remoteLadderClimbing(0)) {
                return setBottomGrabType(AROUND_GRAB, remoteX, y(0), remoteZ, true);
            }
        }

        boolean remoteLowerHalfEmpty = isLowerHalfFrontFullEmpty(o, remoteX, y(0), remoteZ);
        if (isEmpty(baseX, y(-1), baseZ) && terrain.isIronBars(remoteX, y(-1), remoteZ)
                && headedToFrontWall(o, remoteX, y(-1), remoteZ)) {
            return setBottomGrabType(HALF_GRAB, remoteX, y(-1), remoteZ, true);
        }

        if (settings.freeFence()) {
            if (isFence(remoteX, y(-1), remoteZ) && headedToFrontWall(o, remoteX, y(-1), remoteZ)) {
                if (!isFence(baseX, y(-1), baseZ) || headedToFrontSideWall(remoteX, y(-1), remoteZ)) {
                    return setBottomGrabType(HALF_GRAB, remoteX, y(-1), remoteZ, true);
                }
            }
            // The original checked these walls with the middle hold's diagonal rule, kept here.
            if (terrain.isWall(remoteX, y(-1), remoteZ) && !headedToRemoteFlatWall(-1)) {
                return setHalfGrabType(HALF_GRAB, remoteX, y(-1), remoteZ, true);
            }
            if (terrain.isWall(remoteX, y(0), remoteZ) && !headedToRemoteFlatWall(0)) {
                return setHalfGrabType(HALF_GRAB, remoteX, y(0), remoteZ, true);
            }
        }

        if (isWallBlock(baseX, y(-1), baseZ)) {
            int backX = baseX - o.x;
            int backZ = baseZ - o.z;
            if (isEmpty(backX, y(0), backZ) && isEmpty(backX, y(-1), backZ)) {
                if (terrain.isIronBars(baseX, y(-1), baseZ) && headedToBaseWall(-1)) {
                    return setBottomGrabType(HALF_GRAB, baseX, y(-1), baseZ, false);
                }
                if (headedToBaseGrabWall(-1)) {
                    return setBottomGrabType(HALF_GRAB, baseX, y(-1), baseZ, false);
                }
            }
            if (settings.freeFence() && isFence(baseX, y(-1), baseZ) && headedToBaseWall(-1)) {
                return setBottomGrabType(HALF_GRAB, baseX, y(-1), baseZ, false);
            }
        }

        boolean noEdge = !remoteLowerHalfEmpty
                || !isBaseAccessible(-1, true, false)
                || !isUpperHalfFrontAnySolid(remoteX, y(-1), remoteZ)
                || terrain.isBottomHalf(remoteX, y(-1), remoteZ, o)
                || terrain.isDoor(remoteX, y(-1), remoteZ) && !terrain.isDoorTop(remoteX, y(-1), remoteZ)
                || terrain.isDoor(baseX, y(0), baseZ) && terrain.isDoorFrontBlocked(baseX, y(0), baseZ, o)
                || !settings.freeFence() && isFence(remoteX, y(-1), remoteZ);
        if (!noEdge) {
            return setBottomGrabType(HALF_GRAB, remoteX, y(-1), remoteZ, true);
        }

        if (terrain.isStair(remoteX, y(0), remoteZ) && terrain.isTopStair(remoteX, y(0), remoteZ)
                && !terrain.isLowerHalfFull(remoteX, y(0), remoteZ, o)
                && terrain.isUpperHalfSolid(remoteX, y(-1), remoteZ, o)) {
            return setBottomGrabType(HALF_GRAB, remoteX, y(-1), remoteZ, true);
        }
        if (terrain.isTrapDoor(baseX, y(-1), baseZ) && !terrain.isClosedTrapDoor(baseX, y(-1), baseZ)) {
            return setBottomGrabType(HALF_GRAB, baseX, y(-1), baseZ, false);
        }
        if (terrain.isDoor(baseX, y(-1), baseZ) && terrain.isDoorTop(baseX, y(-1), baseZ)
                && terrain.isDoorFrontBlocked(baseX, y(-1), baseZ, o) && isBaseAccessible(0, false, false)) {
            return setBottomGrabType(HALF_GRAB, baseX, y(-1), baseZ, false);
        }
        if (settings.freeBaseClimb()) {
            // The original checked these vines with the middle hold's diagonal rule, kept here.
            int meta = baseVineClimbing(-1);
            if (meta == -1) {
                meta = baseVineClimbing(0);
            }
            if (meta == -1) {
                meta = remoteVineClimbing(-1);
            }
            if (meta == -1) {
                meta = remoteVineClimbing(0);
            }
            if (meta != -1) {
                return setHalfGrabType(HALF_GRAB, vineX, vineY, vineZ, false, meta);
            }
        }
        return setBottomGrabType(NO_GRAB, 0, 0, 0, true);
    }

    private boolean setHalfGrabType(int type, int x, int y, int z, boolean remote) {
        return setHalfGrabType(type, x, y, z, remote, -1);
    }

    /** A diagonal hold ahead also needs the upper halves of both blocks beside the corner open. */
    private boolean setHalfGrabType(int type, int x, int y, int z, boolean remote, int meta) {
        boolean hasGrab = type != NO_GRAB;
        if (hasGrab && remote && o.diagonal) {
            hasGrab = isUpperHalfFrontEmpty(o.rotate(90), baseX, y(0), remoteZ)
                    && isUpperHalfFrontEmpty(o.rotate(-90), remoteX, y(0), baseZ);
        }
        return setGrabType(type, x, y, z, remote, hasGrab, meta);
    }

    /** A diagonal hold ahead also needs the lower halves of both blocks beside the corner open. */
    private boolean setBottomGrabType(int type, int x, int y, int z, boolean remote) {
        boolean hasGrab = type != NO_GRAB;
        if (hasGrab && remote && o.diagonal) {
            hasGrab = isLowerHalfFrontFullEmpty(o.rotate(90), baseX, y(0), remoteZ)
                    && isLowerHalfFrontFullEmpty(o.rotate(-90), remoteX, y(0), baseZ);
        }
        return setGrabType(type, x, y, z, remote, hasGrab, -1);
    }

    private boolean setGrabType(int type, int x, int y, int z, boolean remote, boolean hasGrab, int meta) {
        grabRemote = remote;
        grabType = hasGrab ? type : NO_GRAB;
        hasGrabBlock = type != NO_GRAB;
        grabX = x;
        grabY = y;
        grabZ = z;
        grabMeta = meta;
        return hasGrab;
    }

    /** The block height {@code offset} blocks from the current hold's block. */
    private int y(int offset) {
        return localY + offset;
    }

    // Vines and ladders

    /** Holding a vine in the player's block: {@link #VINE_FRONT}, {@link #VINE_SIDE} or -1 ({@code baseVineClimbing}). */
    private int baseVineClimbing(int offset) {
        if (!terrain.isVine(baseX, y(offset), baseZ)) {
            return -1;
        }
        vineX = baseX;
        vineY = y(offset);
        vineZ = baseZ;
        if (terrain.hasVineOrientation(baseX, y(offset), baseZ, o)) {
            return VINE_FRONT;
        }
        for (ClimbOrientation side : ClimbOrientation.ORTHOGONALS) {
            if (side != o && terrain.hasVineOrientation(baseX, y(offset), baseZ, side.rotate(180))
                    && side.horizontalBorderGap(playerX, playerZ) >= VINE_SIDE_GAP) {
                return VINE_SIDE;
            }
        }
        return -1;
    }

    /** Holding a vine in the block ahead or beside: {@link #VINE_FRONT}, {@link #VINE_SIDE} or -1 ({@code remoteVineClimbing}). */
    private int remoteVineClimbing(int offset) {
        if (terrain.isVine(remoteX, y(offset), remoteZ)
                && terrain.hasVineOrientation(remoteX, y(offset), remoteZ, o.rotate(180))) {
            vineX = remoteX;
            vineY = y(offset);
            vineZ = remoteZ;
            return VINE_FRONT;
        }
        for (ClimbOrientation side : ClimbOrientation.ORTHOGONALS) {
            int x = baseX - side.x;
            int z = baseZ - side.z;
            if (side != o && terrain.isVine(x, y(offset), z) && terrain.hasVineOrientation(x, y(offset), z, side)
                    && side.horizontalBorderGap(playerX, playerZ) >= VINE_SIDE_GAP) {
                vineX = x;
                vineY = y(offset);
                vineZ = z;
                return VINE_SIDE;
            }
        }
        return -1;
    }

    private boolean remoteLadderClimbing(int offset) {
        return isBehindLadder(offset)
                && terrain.hasLadderOrientation(remoteX, y(offset), remoteZ, o.rotate(180));
    }

    private boolean isOnLadder(int offset) {
        return isLadderAt(baseX, y(offset), baseZ);
    }

    private boolean isBehindLadder(int offset) {
        return isLadderAt(remoteX, y(offset), remoteZ);
    }

    /** A ladder, or any climbable block but a vine. */
    private boolean isLadderAt(int x, int y, int z) {
        return terrain.isLadder(x, y, z) || !terrain.isVine(x, y, z) && terrain.isClimbable(x, y, z);
    }

    private boolean isOnLadderFront(int offset) {
        return terrain.hasLadderOrientation(baseX, y(offset), baseZ, o);
    }

    /** A ladder or vine in the player's block, or the current hold is a vine. */
    private boolean isOnLadderOrVine(int offset) {
        return terrain.isLadder(baseX, y(offset), baseZ) || terrain.isVine(baseX, y(offset), baseZ)
                || hasGrabBlock && terrain.isVine(grabX, grabY, grabZ);
    }

    private boolean isOnOpenTrapDoor(int offset) {
        return terrain.isTrapDoor(baseX, y(offset), baseZ) && !terrain.isClosedTrapDoor(baseX, y(offset), baseZ);
    }

    // Room

    private boolean isBaseAccessible(int offset, boolean bottom, boolean full) {
        int y = y(offset);
        return isEmpty(baseX, y, baseZ)
                || terrain.isFullEmpty(baseX, y, baseZ)
                || terrain.isTrapDoor(baseX, y, baseZ) && !terrain.isClosedTrapDoor(baseX, y, baseZ)
                || bottom && terrain.isTrapDoor(baseX, y, baseZ) && terrain.isClosedTrapDoor(baseX, y, baseZ)
                || !full && isWallBlock(baseX, y, baseZ)
                || terrain.isDoor(baseX, y, baseZ);
    }

    private boolean isRemoteAccessible(int offset) {
        int y = y(offset);
        boolean accessible = isEmpty(remoteX, y, remoteZ);
        if (accessible) {
            if (terrain.isTrapDoor(baseX, y, baseZ)) {
                accessible = !terrain.isTrapDoorFront(baseX, y, baseZ, o);
            }
            if (accessible && terrain.isDoor(baseX, y, baseZ)) {
                accessible = !terrain.isDoorFrontBlocked(baseX, y, baseZ, o);
            }
            if (remoteLadderClimbing(offset)) {
                accessible = false;
            }
        }
        if (!accessible && terrain.isTrapDoor(remoteX, y, remoteZ)) {
            accessible = terrain.isClosedTrapDoor(remoteX, y, remoteZ);
        }
        if (!accessible) {
            if (isWallBlock(remoteX, y, remoteZ) && !headedToFrontWall(o, remoteX, y, remoteZ)
                    && !isFence(remoteX, y - 1, remoteZ)) {
                accessible = true;
            }
            // The original checked the flat wall one block below the hold instead of below this block.
            if (!accessible && isFence(remoteX, y - 1, remoteZ)
                    && (!headedToFrontWall(o, remoteX, y - 1, remoteZ) || isWallBlock(baseX, y - 1, baseZ))
                    && (!terrain.isWall(remoteX, y - 1, remoteZ) || headedToRemoteFlatWall(offset - 1))) {
                accessible = true;
            }
            if (!accessible && terrain.isDoor(remoteX, y, remoteZ)
                    && !terrain.isDoorFrontBlocked(remoteX, y, remoteZ, o.rotate(180))) {
                accessible = true;
            }
        }
        return accessible;
    }

    /** Moving diagonally also needs both blocks beside the corner empty. */
    private boolean isAccessAccessible(int offset) {
        return !o.diagonal || isEmpty(remoteX, y(offset), baseZ) && isEmpty(baseX, y(offset), remoteZ);
    }

    /** Room to pass at {@code offset}: in the player's block, or into the block ahead for a hold there. */
    private boolean isFullAccessible(int offset, boolean remote) {
        return !remote
                ? isEmpty(baseX, y(offset), baseZ)
                : isBaseAccessible(offset, false, false) && isRemoteAccessible(offset) && isAccessAccessible(offset);
    }

    /** Empty, and not above a fence, which is one and a half blocks high. */
    private boolean isEmpty(int x, int y, int z) {
        return terrain.isFullEmpty(x, y, z) && !isFence(x, y - 1, z);
    }

    private boolean isUpperHalfFrontEmpty(ClimbOrientation o, int x, int y, int z) {
        boolean empty = terrain.isUpperHalfEmpty(x, y, z, o);
        if (!empty && isWallBlock(x, y, z)
                && (!headedToFrontWall(o, x, y, z) || isWallBlock(x - o.x, y, z - o.z))) {
            empty = true;
        }
        return empty;
    }

    private boolean isLowerHalfFrontFullEmpty(ClimbOrientation o, int x, int y, int z) {
        boolean empty = terrain.isLowerHalfEmpty(x, y, z, o);
        if (!empty && isWallBlock(x, y, z) && !headedToFrontWall(o, x, y, z)) {
            empty = true;
        }
        if (!empty && terrain.isDoor(x, y, z) && !terrain.isDoorFrontBlocked(x, y, z, o.rotate(180))) {
            empty = true;
        }
        return empty;
    }

    private boolean isUpperHalfFrontAnySolid(int x, int y, int z) {
        return terrain.isUpperHalfSolid(x, y, z, o) && !(isWallBlock(x, y, z) && !headedToFrontWall(o, x, y, z));
    }

    // Fences, walls and panes

    /** Fences, walls and closed fence gates, all one and a half blocks high. */
    private boolean isFence(int x, int y, int z) {
        return terrain.isFenceBase(x, y, z) || terrain.isFenceGate(x, y, z) && !terrain.isOpenFenceGate(x, y, z);
    }

    private boolean isWallBlock(int x, int y, int z) {
        return terrain.isPane(x, y, z) || isFence(x, y, z);
    }

    /** The four connections of the wall block, all on for a pane connecting nowhere. */
    private boolean[] wallFlags(ClimbOrientation heading, int x, int y, int z) {
        boolean zn = terrain.wallConnects(x, y, z, ClimbOrientation.ZN, heading);
        boolean zp = terrain.wallConnects(x, y, z, ClimbOrientation.ZP, heading);
        boolean nz = terrain.wallConnects(x, y, z, ClimbOrientation.NZ, heading);
        boolean pz = terrain.wallConnects(x, y, z, ClimbOrientation.PZ, heading);
        if (terrain.isPane(x, y, z) && !zn && !zp && !nz && !pz) {
            zn = zp = nz = pz = true;
        }
        return new boolean[] {zn, zp, nz, pz};
    }

    private boolean headedToFrontWall(ClimbOrientation o, int x, int y, int z) {
        boolean[] flags = wallFlags(o, x, y, z);
        boolean zn = flags[0];
        boolean zp = flags[1];
        boolean nz = flags[2];
        boolean pz = flags[3];
        return headedToWall(o, ClimbOrientation.NZ, pz) || headedToWall(o, ClimbOrientation.PZ, nz)
                || headedToWall(o, ClimbOrientation.ZN, zp) || headedToWall(o, ClimbOrientation.ZP, zn);
    }

    private boolean headedToFrontSideWall(int x, int y, int z) {
        boolean[] flags = wallFlags(o, x, y, z);
        boolean zn = flags[0];
        boolean zp = flags[1];
        boolean nz = flags[2];
        boolean pz = flags[3];
        boolean xTop = isTopHalf(playerX);
        boolean zTop = isTopHalf(playerZ);
        boolean alongX = xTop ? pz : nz;
        boolean alongZ = zTop ? zp : zn;
        return headedToWall(o, ClimbOrientation.NZ, alongZ) || headedToWall(o, ClimbOrientation.PZ, alongZ)
                || headedToWall(o, ClimbOrientation.ZN, alongX) || headedToWall(o, ClimbOrientation.ZP, alongX);
    }

    /** {@code result} if {@code o} is {@code base} or next to it. */
    private static boolean headedToWall(ClimbOrientation o, ClimbOrientation base, boolean result) {
        return (o == base || o == base.rotate(45) || o == base.rotate(-45)) && result;
    }

    private boolean headedToBaseWall(int offset) {
        boolean[] flags = wallFlags(o, baseX, y(offset), baseZ);
        boolean zn = flags[0];
        boolean zp = flags[1];
        boolean nz = flags[2];
        boolean pz = flags[3];
        boolean leaf = zn || zp || nz || pz;
        boolean coreOnly = !terrain.isPane(baseX, y(offset), baseZ) && !leaf;
        boolean xTop = isTopHalf(playerX);
        boolean zTop = isTopHalf(playerZ);
        if (xTop) {
            return zTop
                    ? headedToBaseWall(ClimbOrientation.NN, ClimbOrientation.NZ, ClimbOrientation.ZN, zp, nz, pz, zn, coreOnly, leaf)
                    : headedToBaseWall(ClimbOrientation.NP, ClimbOrientation.NZ, ClimbOrientation.ZP, zn, nz, pz, zp, coreOnly, leaf);
        }
        return zTop
                ? headedToBaseWall(ClimbOrientation.PN, ClimbOrientation.PZ, ClimbOrientation.ZN, zp, pz, nz, zn, coreOnly, leaf)
                : headedToBaseWall(ClimbOrientation.PP, ClimbOrientation.PZ, ClimbOrientation.ZP, zn, pz, nz, zp, coreOnly, leaf);
    }

    private boolean headedToBaseWall(ClimbOrientation diagonal, ClimbOrientation left, ClimbOrientation right,
                                     boolean leftFront, boolean rightFrontOpposite, boolean rightFront,
                                     boolean leftFrontOpposite, boolean coreOnly, boolean leaf) {
        if (o == diagonal) {
            return leaf || coreOnly;
        }
        if (o == left) {
            return headedToBaseWall(leftFront, rightFrontOpposite, rightFront, leftFrontOpposite, coreOnly);
        }
        return o == right && headedToBaseWall(rightFront, leftFrontOpposite, leftFront, rightFrontOpposite, coreOnly);
    }

    private static boolean headedToBaseWall(boolean front, boolean sideOpposite, boolean side, boolean frontOpposite,
                                            boolean coreOnly) {
        return front || sideOpposite && !side || frontOpposite && !front && !side || coreOnly;
    }

    /** Whether the wall block below can be held from the player's quarter of the block ({@code headedToBaseGrabWall}). */
    private boolean headedToBaseGrabWall(int offset) {
        boolean[] flags = wallFlags(o, baseX, y(offset), baseZ);
        boolean zn = flags[0];
        boolean zp = flags[1];
        boolean nz = flags[2];
        boolean pz = flags[3];
        int aboveY = y(offset + 1);
        boolean[] above;
        if (terrain.isFullEmpty(baseX, aboveY, baseZ)) {
            above = new boolean[4];
        } else if (isWallBlock(baseX, aboveY, baseZ)) {
            above = wallFlags(o, baseX, aboveY, baseZ);
        } else {
            above = new boolean[] {true, true, true, true};
        }
        boolean azn = above[0];
        boolean azp = above[1];
        boolean anz = above[2];
        boolean apz = above[3];
        boolean xTop = isTopHalf(playerX);
        boolean zTop = isTopHalf(playerZ);
        if (xTop) {
            return zTop
                    ? headedToBaseGrabWall(-o.x, -o.z, zp, pz, nz, zn, azp, apz, anz, azn)
                    : headedToBaseGrabWall(-o.x, o.z, pz, zn, zp, nz, apz, azn, azp, anz);
        }
        return zTop
                ? headedToBaseGrabWall(o.x, -o.z, nz, zp, zn, pz, anz, azp, azn, apz)
                : headedToBaseGrabWall(o.x, o.z, zn, nz, pz, zp, azn, anz, apz, azp);
    }

    private static boolean headedToBaseGrabWall(int i, int k, boolean front, boolean side, boolean frontOpposite,
                                                boolean sideOpposite, boolean aboveFront, boolean aboveSide,
                                                boolean aboveFrontOpposite, boolean aboveSideOpposite) {
        if (sideOpposite && !aboveSideOpposite && !front && !aboveFront && i == 1) {
            return true;
        }
        if (frontOpposite && !aboveFrontOpposite && !side && !aboveSide && k == 1) {
            return true;
        }
        if (side && !aboveSide && k >= 0) {
            return true;
        }
        if (front && !aboveFront && k >= 0) {
            return true;
        }
        if (frontOpposite && !aboveFrontOpposite && !aboveFront && i == 1 && k >= 0) {
            return true;
        }
        return sideOpposite && !aboveSideOpposite && !aboveSide && k == 1 && i >= 0;
    }

    /** A wall ahead running across the way, only to both sides: no round post to hold on to. */
    private boolean headedToRemoteFlatWall(int offset) {
        int y = y(offset);
        return !terrain.wallConnects(remoteX, y, remoteZ, o, o)
                && terrain.wallConnects(remoteX, y, remoteZ, o.rotate(90), o)
                && !terrain.wallConnects(remoteX, y, remoteZ, o.rotate(180), o)
                && terrain.wallConnects(remoteX, y, remoteZ, o.rotate(-90), o);
    }

    /** Whether {@code d} lies in the upper half of its block, along one axis. */
    private static boolean isTopHalf(double d) {
        return (int) Math.abs(Math.floor(d * 2)) % 2 == 1;
    }
}
