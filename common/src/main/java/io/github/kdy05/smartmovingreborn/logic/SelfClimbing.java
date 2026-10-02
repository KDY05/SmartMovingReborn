package io.github.kdy05.smartmovingreborn.logic;

import io.github.kdy05.smartmovingreborn.SmartMovingReborn;
import io.github.kdy05.smartmovingreborn.client.SmartMovingClient;
import io.github.kdy05.smartmovingreborn.config.SmartMovingClientConfig;
import io.github.kdy05.smartmovingreborn.config.SmartMovingConfig;
import io.github.kdy05.smartmovingreborn.input.Button;
import io.github.kdy05.smartmovingreborn.logic.climb.ClimbLogic;
import io.github.kdy05.smartmovingreborn.logic.jump.JumpType;
import io.github.kdy05.smartmovingreborn.state.MovingState;
import io.github.kdy05.smartmovingreborn.world.ClimbGap;
import io.github.kdy05.smartmovingreborn.world.ClimbOrientation;
import io.github.kdy05.smartmovingreborn.world.ClimbTerrain;
import io.github.kdy05.smartmovingreborn.world.FeetClimbing;
import io.github.kdy05.smartmovingreborn.world.GrabDetector;
import io.github.kdy05.smartmovingreborn.world.HandsClimbing;
import io.github.kdy05.smartmovingreborn.world.LadderSearch;
import io.github.kdy05.smartmovingreborn.world.LevelClimbTerrain;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

/**
 * The own player's free and ladder climbing ({@code handleClimbing}, the ladder parts of {@code landMotion} and
 * the climbing input of {@code updateEntityActionState}). {@link SelfMoving} calls it at the original's points of
 * the tick; vanilla still moves the player. Climbing decides the vertical motion after the tick's movement,
 * before gravity, like the original: {@link ClimbLogic#STILL} holds still.
 * <p>
 * Ladders and vines follow the original. Other climbable blocks (twisting vines, scaffolding, ...) keep
 * vanilla's climbing in every mode.
 */
final class SelfClimbing {
    /** Jumps off a hold; {@code angle} is the direction (yaw) of jumps back, NaN for jumps up. */
    interface Jumper {
        boolean jump(JumpType type, float angle, SmartMovingClientConfig config);
    }

    private final Player player;
    private final MovingState state;
    private final Jumper jumper;

    boolean wantClimbUp;
    boolean wantClimbDown;
    /** Grab was pressed to crawl against a wall, not to climb it. */
    private boolean wantCrawlNotClimb;
    private boolean wantClimb;
    private boolean jumpStarted;
    /** Hanging on without climbing ({@code isClimbHolding}). */
    boolean holding;
    /** Would hang on if climbing ({@code wantClimbHolding}). */
    boolean wantHolding;
    /** Climbing into a gap with the box shrunk from below and lifted a block ({@code isClimbCrawling}). */
    boolean climbCrawling;
    /**
     * Ticks left of climbing on into a gap after the gap's room is no longer found, holding still meanwhile
     * ({@code climbIntoCount}): set to 6, counted down to 1, then climb crawling ends.
     */
    int climbIntoCount;
    private boolean neighborClimbing;
    private boolean climbGap;
    private boolean climbCrawlGap;
    /** The blocks the hands and feet last held, for the sounds. */
    private BlockState handsEdge;
    private BlockState feetEdge;
    private int handsEdgeMeta = -1;
    private int feetEdgeMeta = -1;
    /** On a ladder or vine the original's way, measured before this tick's movement. */
    private boolean onLadder;
    private boolean onVine;
    /** Whether this tick's movement went through the land movement that climbing hooks into. */
    private boolean handled;
    /** The push back from a ladder, trap door or wall above, NaN for none. */
    private float pushBack = Float.NaN;
    private float distanceClimbed;
    private int nextClimbDistance;

    SelfClimbing(Player player, MovingState state, Jumper jumper) {
        this.player = player;
        this.state = state;
        this.jumper = jumper;
    }

    void reset() {
        resetClimbing();
        wantClimbUp = false;
        wantClimbDown = false;
        wantCrawlNotClimb = false;
        wantClimb = false;
        jumpStarted = false;
        holding = false;
        wantHolding = false;
        climbCrawling = false;
        climbIntoCount = 0;
        climbGap = false;
        climbCrawlGap = false;
        handsEdge = null;
        feetEdge = null;
        handsEdgeMeta = -1;
        feetEdgeMeta = -1;
        onLadder = false;
        onVine = false;
        pushBack = Float.NaN;
        distanceClimbed = 0;
        nextClimbDistance = 0;
    }

    /** {@code resetClimbing}. */
    private void resetClimbing() {
        state.climbing = false;
        state.handsVineClimbing = false;
        state.feetVineClimbing = false;
        state.handsClimbType = 0;
        state.feetClimbType = 0;
        state.ceilingClimbing = false;
        neighborClimbing = false;
    }

    private ClimbTerrain terrain() {
        return new LevelClimbTerrain(player.level(), player);
    }

    private static boolean freeClimbing(SmartMovingConfig config) {
        return config.climbFree.get();
    }

    // Input

    /**
     * Whether the player wants to climb up or down this tick ({@code updateEntityActionState} 2357-2393).
     *
     * @param wantCrawl the player wants to crawl, which climbing down gives way to
     * @param disabled  riding or sleeping
     */
    void updateInput(Button grab, Button sneak, Button jump, boolean forward, boolean wasCrawling,
                     boolean wantCrawl, boolean disabled, SmartMovingClientConfig config) {
        wantCrawlNotClimb = (wantCrawlNotClimb || grab.startPressed && !wasCrawling) && grab.pressed && forward
                && state.crawling && player.horizontalCollision;
        boolean facedToSolidVine = count(1, true, false, true) > 0;
        boolean wouldWantClimb = (grab.pressed || holding && sneak.pressed
                || config.isFreeBaseClimb() && config.climbFreeLadderAuto.get() && count(1, true, true, false) > 0
                || config.isFreeBaseClimb() && config.climbFreeVineAuto.get() && facedToSolidVine)
                && (!state.sliding || grab.pressed && forward)
                && !state.headJumping
                && !wantCrawlNotClimb
                && !disabled;
        wantClimb = freeClimbing(config) && wouldWantClimb;
        jumpStarted = jump.startPressed;
        if (!wantClimb || player.verticalCollision) {
            state.climbJumping = false;
        }
        if (player.horizontalCollision || player.verticalCollision) {
            state.climbBackJumping = false;
        }
        boolean vineClimbing = state.handsVineClimbing || state.feetVineClimbing;
        wantClimbUp = wantClimb && forward
                || vineClimbing && jump.pressed && (!sneak.pressed || !facedToSolidVine)
                && (!state.crawling || player.horizontalCollision) && (!state.sliding || player.horizontalCollision);
        wantClimbDown = wantClimb && !forward && !wantCrawl;
    }

    /**
     * Whether the player hangs on ({@code isClimbHolding}, {@code updateEntityActionState} 2566-2570): while
     * climbing, with sneak (or the crawl toggle) held, or while a screen takes the input.
     */
    void updateHolding(boolean sneakPressed, boolean crawlToggled, boolean inputBlocked) {
        wantHolding = holding && sneakPressed
                || state.climbing && inputBlocked
                || wantClimb && !player.isSwimming() && !state.crawling && (sneakPressed || crawlToggled);
        holding = wantHolding && state.climbing;
    }

    /** Whether a hold was found around the player, in an axis direction ({@code isNeighborClimbing}). */
    boolean neighborClimbing() {
        return neighborClimbing;
    }

    /**
     * Whether to climb crawl this tick ({@code updateEntityActionState} 2617-2627): climbing up while hanging on
     * where there is room to crawl, or to stand while hanging on; and for {@link #climbIntoCount} more ticks
     * once that room is gone.
     */
    boolean updateClimbCrawling() {
        boolean need = climbCrawlGap || climbGap && holding;
        boolean can = wantHolding && wantClimbUp;
        if (climbIntoCount > 1) {
            climbIntoCount--;
        } else if (climbCrawling && !need && climbIntoCount == 0) {
            climbIntoCount = 6;
        }
        climbCrawling = can && (need && climbIntoCount == 0 || climbIntoCount > 1);
        if (!climbCrawling) {
            climbIntoCount = 0;
        }
        return climbCrawling;
    }

    /** Forgets this tick's climbing wish, after crawl climbing gave way to crawling or standing. */
    void cancelClimbWish() {
        wantClimbUp = false;
        wantClimbDown = false;
    }

    // Ladders and vines

    /**
     * Ladders and vines touching the box ({@code getOnLadderOrVine}). The standard mode looks only at the block
     * at the feet; the others count ladders around the box and vines in it, from a block lower while climb
     * crawling, whose box is raised.
     *
     * @param faceOnly only ladders and solid vines the player faces
     */
    private int count(int maxResult, boolean faceOnly, boolean ladder, boolean vine) {
        SmartMovingClientConfig config = SmartMovingReborn.CLIENT_CONFIG;
        ClimbTerrain terrain = terrain();
        AABB box = player.getBoundingBox();
        int x = Mth.floor(player.getX());
        int minY = Mth.floor(box.minY);
        int z = Mth.floor(player.getZ());
        if (config.climbBase.is(SmartMovingConfig.CLIMB_STANDARD)) {
            boolean ladderHere = terrain.isLadder(x, minY, z);
            boolean vineHere = terrain.isVine(x, minY, z);
            return ladder && ladderHere || vine && vineHere ? 1 : 0;
        }
        Set<ClimbOrientation> facing = faceOnly
                ? ClimbOrientation.climbingOrientations(player.getYRot(), true, false,
                config.climbFreeOrthogonalAngle.get(), config.climbFreeDiagonalAngle.get())
                : null;
        int maxY = Mth.floor(box.minY + Math.ceil(box.maxY - box.minY)) - 1;
        return LadderSearch.count(terrain, x, climbCrawling ? minY - 1 : minY, maxY, z, facing, ladder, vine,
                maxResult);
    }

    /**
     * Before vanilla's travel: whether the player is on a ladder or vine the original's way, and the push
     * back from a ladder, trap door or wall right above a climber facing it ({@code climbingUpIsBlockedBy*}),
     * which replaces walking for the tick.
     */
    void beforeTravel(boolean forwardInput) {
        handled = false;
        onLadder = count(1, false, true, false) > 0;
        onVine = count(1, false, false, true) > 0;
        pushBack = Float.NaN;
        if (state.climbing && player.horizontalCollision && player.verticalCollision && !player.onGround()
                && forwardInput) {
            ClimbOrientation orientation = ClimbOrientation.facing(player.getYRot(), 20, true, false);
            if (orientation != null) {
                pushBack = LadderSearch.blockedPush(terrain(), orientation, Mth.floor(player.getX()),
                        Mth.floor(player.getBoundingBox().maxY), Mth.floor(player.getZ()));
            }
        }
        if (!Float.isNaN(pushBack)) {
            player.moveRelative(pushBack, new Vec3(0, 0, -1));
        }
    }

    /** Whether walking is replaced by the push back from above this tick. */
    boolean pushedBack() {
        return !Float.isNaN(pushBack);
    }

    /** Overrides {@code LivingEntity#onClimbable} when non-null: true on the original's ladders and vines. */
    Boolean onClimbable() {
        return count(1, false, true, true) > 0 ? Boolean.TRUE : null;
    }

    /**
     * Replaces {@code LivingEntity#handleOnClimbable} when non-null ({@code landMotion} 789-831): on a ladder or
     * vine, the horizontal motion is capped, and outside free climbing the fall too, which sneaking stops.
     * Elsewhere, walking forward off the top of a ladder does not drop ({@code move.climb.free.ladder.auto}).
     * Vanilla handles other climbable blocks.
     */
    Vec3 handleOnClimbable(Vec3 motion, boolean forwardInput, boolean sneakInput, float speedFactor,
                           SmartMovingClientConfig config) {
        if (onLadder || onVine) {
            double x = Mth.clamp(motion.x, -0.15, 0.15);
            double z = Mth.clamp(motion.z, -0.15, 0.15);
            double y = motion.y;
            boolean freeBase = config.isFreeBaseClimb();
            boolean notTotalFree = !state.climbing && onLadder && !(freeBase && config.climbFreeBaseLadder.get())
                    || onVine && !(freeBase && config.climbFreeBaseVine.get());
            if (notTotalFree) {
                player.resetFallDistance();
                y = Math.max(y, -0.15 * speedFactor);
            }
            if (freeBase) {
                if (sneakInput && y < 0 && !player.onGround() && notTotalFree) {
                    y = 0;
                }
            } else if (player.isShiftKeyDown() && y < 0) {
                y = 0;
            }
            return new Vec3(x, y, z);
        }
        if (player.onClimbable()) {
            return null;
        }
        if (config.isFreeBaseClimb() && config.climbFreeLadderAuto.get() && forwardInput) {
            double bottom = player.getBoundingBox().minY;
            int y = Mth.floor(bottom);
            if (bottom - y < 0.1 && terrain().isLadder(Mth.floor(player.getX()), y - 1, Mth.floor(player.getZ()))) {
                return new Vec3(motion.x, Math.max(motion.y, 0), motion.z);
            }
        }
        return motion;
    }

    // Climbing

    /**
     * After vanilla moved the player, before gravity ({@code handleClimbing}): the climbing speed of the base
     * climbing mode on a ladder or vine, or of free climbing.
     *
     * @param vanilla what vanilla made of the motion, which climbs vanilla's way on other climbable blocks
     * @param fast    sprinting, which climbs faster
     */
    Vec3 afterMove(Vec3 vanilla, boolean grabPressed, boolean fast, float speedFactor,
                   SmartMovingClientConfig config) {
        handled = true;
        Vec3 motion = player.getDeltaMovement();
        double y = handleClimbing(motion.y, grabPressed, fast, speedFactor, config);
        Vec3 jumped = player.getDeltaMovement();
        if (jumped != motion) {
            return jumped;
        }
        if (!onLadder && !onVine && !state.climbing && player.onClimbable()) {
            return vanilla;
        }
        return new Vec3(motion.x, y, motion.z);
    }

    /** After vanilla's travel: a tick without land movement (water, lava, gliding) ends climbing. */
    void afterTravel() {
        if (!handled) {
            resetClimbing();
        }
    }

    private double handleClimbing(double motionY, boolean grabPressed, boolean fast, float speedFactor,
                                  SmartMovingClientConfig config) {
        resetClimbing();
        boolean onLadderOrVine = onLadder || onVine;
        boolean collided = player.horizontalCollision;
        double x = player.getX();
        double bottom = player.getBoundingBox().minY;
        double z = player.getZ();
        int i = Mth.floor(x);
        int j = Mth.floor(bottom);
        int k = Mth.floor(z);
        ClimbTerrain terrain = terrain();
        boolean standard = config.climbBase.is(SmartMovingConfig.CLIMB_STANDARD);
        boolean simple = config.climbBase.is(SmartMovingConfig.CLIMB_SIMPLE);
        boolean smart = config.climbBase.is(SmartMovingConfig.CLIMB_SMART);
        if (standard && collided && onLadderOrVine) {
            motionY = 0.2 * speedFactor;
        }
        if (simple && collided && onLadderOrVine) {
            motionY = ClimbLogic.simpleSpeed(terrain.isClimbable(i, j, k), terrain.isClimbable(i, j + 1, k))
                    * speedFactor;
        }
        if (smart && collided && onLadderOrVine) {
            GrabDetector detector = detector(terrain, config);
            boolean feet = terrain.isClimbable(i, j, k);
            boolean hands = terrain.isClimbable(i, j + 1, k);
            boolean handsSubstitute = false;
            boolean feetSubstitute = false;
            for (ClimbOrientation orientation : ClimbOrientation.ORTHOGONALS) {
                handsSubstitute |= detector.isHandsLadderSubstitute(orientation, i, j + 1, k, x, z);
            }
            feetSubstitute |= detector.isFeetLadderSubstitute(ClimbOrientation.ZZ, i, j, k, x, z);
            for (ClimbOrientation orientation : ClimbOrientation.ORTHOGONALS) {
                feetSubstitute |= detector.isFeetLadderSubstitute(orientation, i, j, k, x, z);
            }
            motionY = ClimbLogic.smartSpeed(feet, hands, handsSubstitute, feetSubstitute) * speedFactor;
        }

        if (freeClimbing(config) && player.fallDistance <= config.climbFallMaximumDistance.get()
                && (!onLadderOrVine || config.isFreeBaseClimb()) && (wantClimbUp || wantClimbDown)) {
            motionY = freeClimb(motionY, terrain, i, j, k, grabPressed, fast, speedFactor, config);
        }

        boolean vineHands = state.climbing && handsEdge != null && handsEdge.getBlock() instanceof VineBlock;
        boolean vineFeet = state.climbing && feetEdge != null && feetEdge.getBlock() instanceof VineBlock;
        state.handsVineClimbing = vineHands;
        state.feetVineClimbing = vineFeet;
        return motionY;
    }

    private GrabDetector detector(ClimbTerrain terrain, SmartMovingClientConfig config) {
        return new GrabDetector(terrain, new GrabDetector.Settings(config.isFreeBaseClimb(),
                config.climbFreeFence.get(), config.climbFreeOrthogonalAngle.get(),
                config.climbFreeDiagonalAngle.get(), GrabDetector.Settings.holdGap(
                config.climbFreeUpSpeedFactor.get(), config.climbFreeDownSpeedFactor.get())));
    }

    /** Free climbing ({@code handleClimbing} 903-1219). */
    private double freeClimb(double motionY, ClimbTerrain terrain, int i, int j, int k, boolean grabPressed,
                             boolean fast, float speedFactor, SmartMovingClientConfig config) {
        boolean small = state.crawling || state.sliding;
        GrabDetector.Result result = detector(terrain, config).detect(player.getX(), player.getBoundingBox().minY,
                player.getZ(), player.getYRot(), climbCrawling, state.crawlClimbing, small);
        HandsClimbing hands = result.hands();
        FeetClimbing feet = result.feet();
        neighborClimbing = result.neighborClimbing();
        climbGap = result.climbGap();
        climbCrawlGap = result.climbCrawlGap();

        // A ladder two blocks up is no hold while there is nothing to stand on below it.
        if (hands == HandsClimbing.BOTTOM_HOLD && terrain.isLadder(i, j + 2, k)) {
            ClimbOrientation ladder = LadderSearch.ladderOrientation(terrain, i, j + 2, k);
            if (ladder != null && terrain.isFullEmpty(i + ladder.x, j, k + ladder.z)
                    && terrain.isFullEmpty(i + ladder.x, j + 1, k + ladder.z)) {
                hands = HandsClimbing.NONE;
            }
        }
        // Without grab, hands alone in the open do not hold.
        if (!grabPressed && hands == HandsClimbing.UP && feet == FeetClimbing.NONE && !player.horizontalCollision
                && terrain.state(i, j, k).isAir() && terrain.state(i, j + 1, k).isAir()) {
            hands = HandsClimbing.NONE;
        }
        if (!feet.isRelevant() && !hands.isRelevant()) {
            return motionY;
        }

        if (wantClimbUp && state.sliding && hands.isRelevant()) {
            state.sliding = false;
            state.crawling = true;
        }
        ClimbGap feetGap = result.feetGap();
        boolean feetOnBed = feetGap.block != null && feetGap.block.getBlock() instanceof BedBlock;
        ClimbLogic.Decision decision = ClimbLogic.decide(hands, feet, wantClimbUp, wantClimbDown, climbGap,
                climbCrawlGap, player.onGround(), feetOnBed, holding);
        boolean jumpedUp = false;
        if (decision.jumpType() != 0 && jumpStarted) {
            JumpType type = decision.jumpType() == 5 ? JumpType.CLIMB_UP : JumpType.CLIMB_UP_HANDS_ONLY;
            jumpedUp = jumper.jump(type, Float.NaN, config);
            state.climbJumping = jumpedUp;
        }
        if (decision.climbing() && !jumpedUp) {
            motionY = setClimbSpeed(decision.speed(), motionY, fast, speedFactor, config);
            state.handsClimbType = decision.handsType();
            state.feetClimbType = decision.feetType();
        }
        if (wantClimbDown && holding && jumpStarted) {
            int type = ClimbLogic.backJumpType(feet != FeetClimbing.NONE, config.climbJumpBackHeadOnGrab.get(),
                    grabPressed);
            if (jumper.jump(BACK_JUMPS[type - 7], player.getYRot() + 180, config)) {
                state.climbing = false;
            }
        }
        if (state.climbing) {
            // The original's landing damage never reached the server, which clears the fall while climbing.
            player.resetFallDistance();
        }
        handsEdge = result.handsGap().block;
        handsEdgeMeta = result.handsGap().meta;
        feetEdge = feetGap.block;
        feetEdgeMeta = feetGap.meta;
        return motionY;
    }

    /** The climb back jumps by the original's type numbers 7 to 10. */
    private static final JumpType[] BACK_JUMPS = {JumpType.CLIMB_BACK_UP, JumpType.CLIMB_BACK_UP_HANDS_ONLY,
            JumpType.CLIMB_BACK_HEAD, JumpType.CLIMB_BACK_HEAD_HANDS_ONLY};

    /** {@code setOnlyShouldClimbSpeed}: climbs at {@code speed} unless already moving up faster. */
    private double setClimbSpeed(double speed, double motionY, boolean fast, float speedFactor,
                                 SmartMovingClientConfig config) {
        state.climbing = true;
        float factor = fast ? speedFactor * config.sprintFactor.get() : speedFactor;
        float ladderFactor = config.isFreeBaseClimb() && speed == ClimbLogic.STRAIGHT_UP
                ? ClimbLogic.ladderFactor(count(Integer.MAX_VALUE, false, true, false),
                config.climbFreeLadderOneUpSpeedFactor.get(), config.climbFreeLadderTwoUpSpeedFactor.get())
                : 1;
        double value = ClimbLogic.scale(speed, factor, ladderFactor, config.climbFreeUpSpeedFactor.get(),
                config.climbFreeDownSpeedFactor.get(), climbIntoCount > 0, climbCrawlGap && climbCrawling);
        boolean relevant = value < 0 || value > motionY;
        state.climbJumping = !relevant && !holding;
        return relevant ? value : motionY;
    }

    // Other effects

    /** Free climbing speed on the walls' sides ({@code move.climb.free.horizontal.speed.factor}). */
    float horizontalFactor(boolean moveInput, SmartMovingClientConfig config) {
        return state.climbing && moveInput ? config.climbFreeHorizontalSpeedFactor.get() : 1;
    }

    /**
     * Whether climbing is fast enough to count as climb sprinting ({@code isClimbSprintSpeed}): the last tick's
     * movement against a minimum by direction.
     */
    boolean sprintSpeed(SmartMovingClientConfig config) {
        double dx = player.getX() - player.xo;
        double dy = player.getY() - player.yo;
        double dz = player.getZ() - player.zo;
        return Math.sqrt(dx * dx + dy * dy + dz * dz) >= ClimbLogic.sprintMinimumTickDistance(wantClimbUp,
                wantClimbDown, config.climbFreeUpSpeedFactor.get(), config.climbFreeDownSpeedFactor.get());
    }

    /**
     * After vanilla moved the player by its own motion: the climbing sounds, a step of the held blocks every
     * block climbed, hands and feet in turn ({@code afterMoveEntity} 1644-1669).
     */
    void afterMove(double distance) {
        if (!state.climbing) {
            return;
        }
        distanceClimbed += (float) (distance * 1.2);
        if (distanceClimbed <= nextClimbDistance) {
            return;
        }
        BlockState step;
        if (handsEdge == null) {
            step = feetEdge;
        } else if (feetEdge == null) {
            step = handsEdge;
        } else {
            step = nextClimbDistance % 2 != 0 ? feetEdge : handsEdge;
        }
        nextClimbDistance++;
        // The original stepped on cobblestone with nothing held.
        SoundType sound = (step != null ? step : Blocks.COBBLESTONE.defaultBlockState()).getSoundType();
        SmartMovingClient.playSound(player, sound.getStepSound(), sound.getVolume() * 0.15f, sound.getPitch());
    }
}
