package io.github.kdy05.smartmovingreborn.logic;

import io.github.kdy05.smartmovingreborn.client.SmartMovingClient;
import io.github.kdy05.smartmovingreborn.config.SmartMovingClientConfig;
import io.github.kdy05.smartmovingreborn.input.Button;
import io.github.kdy05.smartmovingreborn.logic.crawl.CrawlLogic;
import io.github.kdy05.smartmovingreborn.logic.jump.JumpType;
import io.github.kdy05.smartmovingreborn.logic.slide.SlideLogic;
import io.github.kdy05.smartmovingreborn.logic.swim.SwimLogic;
import io.github.kdy05.smartmovingreborn.render.SlideParticles;
import io.github.kdy05.smartmovingreborn.state.MovingState;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The client's own player's Smart Moving state and its per-tick update, like the original's
 * {@code SmartMovingSelf}. The feature classes ({@code logic/crawl}, ...) only decide; this class measures the
 * player, calls them in the original's order and keeps the results. Jumping ({@link SelfJumping}), climbing
 * ({@link SelfClimbing}), swimming ({@link SelfSwimming}) and flying ({@link SelfFlying}) keep their own state.
 * The shared flags live in {@link #state}, which is also what gets sent to the server and read by rendering.
 */
public final class SelfMoving {
    /** Horizontal speed squared below which the player counts as standing ({@code isStanding}). */
    private static final double STANDING_SPEED_SQUARE = 5.0E-4;
    /**
     * How much taller the standing box is than the small one: what the body grows downwards by when standing up
     * from a small box on a wall. The original's small box was 0.8 high and grew by 1; this port's is vanilla's
     * 0.6.
     */
    private static final double SMALL_TO_STANDING = 1.2;

    final Player player;
    final MovingState state;
    /** The box moved a block up while the model stays put, for a head jump, climb crawling, swimming or flying. */
    final BoxLift lift;
    private final ToggleState toggles = new ToggleState();
    private final SelfJumping jumping;
    private final SelfClimbing climbing;
    private final SelfSwimming swimming;
    private final SelfFlying flying;
    private boolean wasCrawling;
    private boolean groundSprinting;
    private boolean wasRunningWhenSprintStarted;
    /** In the air after sprinting, until back on the ground. */
    private boolean sprintJump;
    private int collidedHorizontallyTicks;
    /** The movement speed the field of view follows, fading towards the current one; negative before the first tick. */
    private float fadingPerspectiveSpeed;
    /** The jump key, read fresh each tick. */
    private boolean jumpInput;
    /** Whether vanilla jumps this tick ({@code isJumping}), which is also what dives up. */
    private boolean vanillaJumping;
    /** Would sneak if not sprinting, regardless of {@code move.sneak} ({@code wouldIsSneaking}). */
    private boolean wouldSneak;
    private boolean standing;
    private boolean grabPressed;
    /** Gliding out of a slide: a head jump that keeps nearly all of its horizontal speed ({@code isAerodynamic}). */
    private boolean aerodynamic;
    /**
     * The horizontal damping vanilla applies in this tick's {@code travel}, NaN before it moves the player on
     * land or in the air; with the friction and ground state it came from.
     */
    private float vanillaDamping;
    private float travelFriction;
    private boolean travelOnGround;
    /** The sneak key, read fresh each tick. */
    private boolean sneakInput;
    /** Where the player's own move started, for the climbing sounds. */
    private Vec3 selfMoveStart;
    /** The vertical motion before this tick's jumps, which vanilla's flying keeps 0.6 of. */
    private double travelStartY;

    SelfMoving(Player player, MovingState state) {
        this.player = player;
        this.state = state;
        this.lift = new BoxLift(player);
        this.jumping = new SelfJumping(this);
        this.climbing = new SelfClimbing(player, state, jumping::climbJump);
        this.swimming = new SelfSwimming(this);
        this.flying = new SelfFlying(this);
        reset();
    }

    void reset() {
        state.crawling = false;
        state.sliding = false;
        state.headJumping = false;
        state.slow = false;
        state.fast = false;
        wasCrawling = false;
        groundSprinting = false;
        wasRunningWhenSprintStarted = false;
        sprintJump = false;
        collidedHorizontallyTicks = 0;
        fadingPerspectiveSpeed = -1;
        jumpInput = false;
        vanillaJumping = false;
        wouldSneak = false;
        standing = false;
        grabPressed = false;
        aerodynamic = false;
        vanillaDamping = Float.NaN;
        lift.reset();
        toggles.reset();
        jumping.reset();
        climbing.reset();
        swimming.reset();
        flying.reset();
        sneakInput = false;
        selfMoveStart = null;
    }

    /** At the start of the tick ({@code beforeOnUpdate}). */
    void beforeTick() {
        jumping.beforeTick();
        flying.beforeTick();
    }

    /** Once per tick, before vanilla turns the input into movement (the original's {@code updateEntityActionState}). */
    void updateActionState(Button sneak, Button grab, Button jump, Button sprint, Button left, Button right,
                           Button back, boolean forwardPressed, boolean jumpInput, SmartMovingClientConfig config) {
        this.jumpInput = jumpInput;
        if (!jumpInput) {
            swimming.stopStillSwimmingJump();
        }
        grabPressed = grab.pressed;
        sneakInput = sneak.pressed;
        jumping.startActionState();
        if (vanillaOverride()) {
            endSmallMoves();
            return;
        }
        Vec3 motion = player.getDeltaMovement();
        double horizontalSpeedSquare = motion.x * motion.x + motion.z * motion.z;
        boolean flying = player.getAbilities().flying;
        boolean disabled = player.isPassenger() || player.isSleeping();
        boolean onGround = player.onGround();

        boolean crawling = state.crawling;
        // Sliding and head jumping keep the small pose on their own.
        boolean mustCrawl = !state.sliding && !state.headJumping && CrawlLogic.mustCrawl(crawling,
                fits(Pose.STANDING), fits(Pose.CROUCHING), flying,
                this.flying.flyEnabled(config) || config.levitateSmall.get() && !player.isSpectator());
        boolean inputContinueCrawl = CrawlLogic.inputContinueCrawl(config.crawlToggle.get(), toggles.isCrawlToggled(),
                sneak.pressed, config.climbFree.get(), grab.pressed);
        boolean wouldWantCrawl = CrawlLogic.wantCrawl(true, crawling, flying,
                inputContinueCrawl || swimming.continueCrawl(), grab.startPressed,
                sneak.pressed || toggles.isSneakToggled(), onGround);
        boolean wantCrawl = config.crawl.get() && wouldWantCrawl;
        // Dipping, the water above the floor of a small box (the feet a block below it) is too deep to crawl.
        boolean canCrawl = !state.swimming && !state.diving
                && (!state.dipping
                || swimming.dippingDepth() + (player.getBbHeight() < 1 ? -1 : 0) < SwimLogic.CRAWL_WATER_DEPTH)
                && !state.climbing
                && player.fallDistance < config.fallDistanceMinimum.get()
                && !player.isPassenger() && !player.isSleeping() && !player.isFallFlying();
        wasCrawling = crawling;
        state.crawling = canCrawl && (wantCrawl || mustCrawl);
        if (!state.crawling) {
            swimming.stopContinueCrawl();
        }
        if (wasCrawling && !state.crawling && flying) {
            jumping.tryJump(JumpType.UP, Float.NaN, config);
        }

        climbing.updateInput(grab, sneak, jump, forwardPressed, wasCrawling, wantCrawl, shiftKeyDown(config), disabled,
                config);

        this.flying.updateActionState(sneak.pressed, grab.pressed, horizontalSpeedSquare, motion.y,
                state.headJumping, config);
        boolean smartFlying = this.flying.smartFlying();
        updateSlideAndHeadJump(sneak, grab, flying, onGround, config);

        boolean inWater = state.swimming || state.diving;
        boolean wouldWantSneak = SpeedLogic.wouldWantSneak(config.sneakToggle.get(), toggles.isSneakToggled(),
                sneak.pressed, sneak.startPressed, wantCrawl, mustCrawl, config.crawl.get(), grab.pressed, smartFlying,
                state.sliding || state.headJumping)
                && SwimLogic.allowsSneak(state.swimming, state.diving, config.swimDownOnSneak.get(),
                config.diveDownOnSneak.get(), swimming.fakeShallowWaterSneaking());
        boolean wantSneak = config.sneak.get() && wouldWantSneak;
        boolean wantSprint = SpeedLogic.wantSprint(config.sprint.get(), sprint.pressed,
                forwardPressed || state.climbing || SwimLogic.sprintInput(state.swimming, state.diving,
                        SmartMovingClient.isMovePressed(player), sneak.pressed, jump.pressed,
                        config.swimDownOnSneak.get(), config.diveDownOnSneak.get())
                        || smartFlying && (SmartMovingClient.isMovePressed(player) || jump.pressed || sneak.pressed),
                state.sliding, disabled);

        if (!onGround && state.fast && !state.climbing && !state.ceilingClimbing && !inWater) {
            sprintJump = true;
        }
        if (onGround || smartFlying || inWater || player.isInLava()) {
            sprintJump = false;
        }

        boolean wasGroundSprinting = groundSprinting;
        groundSprinting = SpeedLogic.groundSprinting(wantSprint, wantSneak, player.isOnFire(), player.isUsingItem(),
                config.usageSprint.get(), collidedHorizontallyTicks, onGround && !inWater && !state.climbing);
        boolean canAnySprint = SpeedLogic.canAnySprint(wantSprint, wantSneak, player.isOnFire(),
                player.isUsingItem(), config.usageSprint.get());
        boolean canHorizontallySprint = canAnySprint
                && collidedHorizontallyTicks < SpeedLogic.SPRINT_COLLISION_TICKS;
        boolean climbSprinting = canAnySprint && state.climbing && climbing.sprintSpeed(config);
        boolean ceilingSprinting = SpeedLogic.ceilingSprinting(wantSprint, wantSneak, player.isOnFire(),
                player.isUsingItem(), config.usageSprint.get(), collidedHorizontallyTicks, state.ceilingClimbing);
        boolean swimSprinting = canHorizontallySprint && state.swimming;
        boolean diveSprinting = canHorizontallySprint && !player.verticalCollision && state.diving;
        boolean flyingSprinting = canHorizontallySprint && !player.verticalCollision && smartFlying;
        state.fast = groundSprinting || climbSprinting || ceilingSprinting || swimSprinting || diveSprinting
                || flyingSprinting;
        if (groundSprinting && !wasGroundSprinting) {
            wasRunningWhenSprintStarted = player.isSprinting();
            player.setSprinting(SpeedLogic.standupSprintingOrRunning(state.fast, player.isSprinting(), onGround,
                    state.sliding, state.crawling));
        } else if (wasGroundSprinting && !groundSprinting) {
            player.setSprinting(wasRunningWhenSprintStarted);
        }

        wouldSneak = wouldWantSneak && !wantSprint && !state.climbing;
        boolean wasSlow = state.slow;
        state.slow = wantSneak && wouldSneak;
        climbing.updateHolding(sneak.pressed, toggles.isCrawlToggled(), SmartMovingClient.isInputBlocked());
        updateCrawlClimbing(sneak, forwardPressed, config);
        updateClimbCrawling(sneak, mustCrawl, config);
        if (grab.startPressed) {
            swimming.grabStarted(jump.pressed, climbing.wouldWantClimb, wouldWantCrawl, config);
        }
        motion = player.getDeltaMovement();
        standing = motion.x * motion.x + motion.z * motion.z < STANDING_SPEED_SQUARE;

        jumping.updateInput(jump, left, right, back, forwardPressed, flying, onGround, config);

        toggles.update(config.sneakToggle.get(), config.crawlToggle.get(), state.crawling, wasCrawling,
                state.slow, wasSlow, state.fast, wantSneak && wantSprint, inWater, sneak, jump);
    }

    /**
     * Crawl climbing ({@code updateEntityActionState} 2571-2615): a crawling player climbing a wall with sneak
     * held and forward stays small. As soon as the body fits below the box, it grows downwards and climbs on
     * standing. Ending it, the player crawls on or stands up at the block's floor.
     */
    private void updateCrawlClimbing(Button sneak, boolean forwardPressed, SmartMovingClientConfig config) {
        boolean toCrawlingInput = sneak.pressed || toggles.isCrawlToggled();
        boolean wasCrawlClimbing = state.crawlClimbing;
        state.crawlClimbing = (wasCrawling || state.crawlClimbing) && state.climbing && climbing.neighborClimbing()
                && toCrawlingInput && forwardPressed;
        if (state.crawlClimbing) {
            double bottom = player.getBoundingBox().minY;
            double below = SMALL_TO_STANDING - (climbing.climbCrawling ? 0.05 : 0);
            if (!solidBetween(bottom - below, bottom)) {
                wasCrawlClimbing = false;
                state.crawlClimbing = false;
                if (!climbing.climbCrawling) {
                    standUpFromSmall(-SMALL_TO_STANDING);
                }
            }
            if (!wasCrawlClimbing) {
                wasCrawling = false;
                state.crawling = false;
            }
        } else if (wasCrawlClimbing) {
            double bottom = player.getBoundingBox().minY;
            if (!state.climbing) {
                toCrawling(config);
                player.move(MoverType.SELF, new Vec3(0, Math.floor(bottom) - bottom, 0));
            } else if (!forwardPressed) {
                wasCrawling = toCrawlingInput;
                state.crawling = toCrawlingInput;
                climbing.cancelClimbWish();
                if (toCrawlingInput) {
                    player.move(MoverType.SELF, new Vec3(0, Math.floor(bottom) - bottom, 0));
                } else {
                    standUpFromSmall(-SMALL_TO_STANDING);
                    player.move(MoverType.SELF, new Vec3(0, Math.floor(bottom) - (bottom - SMALL_TO_STANDING), 0));
                }
            } else if (!toCrawlingInput) {
                standUpFromSmall(-SMALL_TO_STANDING);
                double standing = bottom - SMALL_TO_STANDING;
                player.move(MoverType.SELF, new Vec3(0, Math.ceil(bottom - 1) - standing, 0));
            }
        }
    }

    /**
     * Climb crawling ({@code updateEntityActionState} 2617-2647): from the standing box, the box shrinks from
     * below, lifted a block so that its bottom is where the original's was, and the model is drawn a block lower.
     * A box already small from crawl climbing stays where it is, like the original's, whose height offset was
     * set already. Ending it, the player stands again, or crawls on at the floor of the gap it climbed into.
     */
    private void updateClimbCrawling(Button sneak, boolean mustCrawl, SmartMovingClientConfig config) {
        boolean was = climbing.climbCrawling;
        boolean now = climbing.updateClimbCrawling();
        if (now && !was) {
            if (player.getPose() != Pose.SWIMMING) {
                player.setPose(Pose.SWIMMING);
                SmartMovingClient.sendStateNow(player);
                lift.lift(BoxLift.Reason.CLIMB_CRAWL);
            }
            boolean collided = player.horizontalCollision;
            player.move(MoverType.SELF, new Vec3(0, 0.05, 0));
            player.horizontalCollision = collided;
        } else if (!now && was) {
            // Standing up keeps the original's box: a lifted box grows back by the lift, a small one keeps its top.
            double standUp = lift.is(BoxLift.Reason.CLIMB_CRAWL) ? -1 : -SMALL_TO_STANDING;
            lift.clear(BoxLift.Reason.CLIMB_CRAWL);
            if (!mustCrawl && !sneak.pressed && !toggles.isCrawlToggled()) {
                standUpFromSmall(standUp);
            } else {
                double bottom = player.getBoundingBox().minY;
                double gap = bottom - maxSolidBetween(bottom - 1, bottom);
                if (gap >= 0 && gap < 1) {
                    toCrawling(config);
                    player.move(MoverType.SELF, new Vec3(0, -gap, 0));
                } else {
                    standUpFromSmall(standUp);
                }
            }
        }
    }

    /**
     * Elytra gliding, a riptide spin, spectating or a scale other than 1, which vanilla moves and poses alone:
     * Smart Moving's moves end and wait for it to finish. A spectator passes through blocks, where it would
     * otherwise be made to crawl. Smart Moving's sizes and distances are for a player of scale 1 (the scale
     * attribute is newer than the original; user decision, 2026-10-04).
     */
    boolean vanillaOverride() {
        return player.isFallFlying() || player.isAutoSpinAttack() || player.isSpectator() || player.getScale() != 1;
    }

    /**
     * Ends every move in a small box for {@link #vanillaOverride}. A box a block up goes back down to the
     * model's feet where that fits, so that the player does not appear to jump a block.
     */
    private void endSmallMoves() {
        if (lift.any() && fits(Pose.SWIMMING, -1)) {
            lift.shift(-1);
        }
        lift.reset();
        state.crawling = false;
        state.sliding = false;
        state.headJumping = false;
        state.crawlClimbing = false;
        wasCrawling = false;
        aerodynamic = false;
        climbing.reset();
        swimming.reset();
        flying.reset();
        SmartMovingClient.sendStateNow(player);
    }

    /** Back to the standing box from a small one, moving the player by {@code dy} without collisions. */
    void standUpFromSmall(double dy) {
        lift.shift(dy);
        player.setPose(Pose.STANDING);
    }

    /** Whether any block collides with the player's box between heights {@code minY} and {@code maxY}. */
    private boolean solidBetween(double minY, double maxY) {
        AABB box = player.getBoundingBox();
        return !player.level().noCollision(player, new AABB(box.minX, minY, box.minZ, box.maxX, maxY, box.maxZ));
    }

    /**
     * The highest top of a block collision within the player's box between {@code minY} and {@code maxY}, or
     * {@code minY} for none ({@code getMaxPlayerSolidBetween}).
     */
    double maxSolidBetween(double minY, double maxY) {
        AABB box = player.getBoundingBox();
        double result = minY;
        for (VoxelShape shape : player.level().getBlockCollisions(player,
                new AABB(box.minX, minY, box.minZ, box.maxX, maxY, box.maxZ))) {
            result = Math.max(result, shape.max(Direction.Axis.Y));
        }
        return Math.min(result, maxY);
    }

    /**
     * The lowest bottom of a block collision within the player's box between {@code minY} and {@code maxY}, or
     * {@code maxY} for none ({@code getMinPlayerSolidBetween}).
     */
    double minSolidBetween(double minY, double maxY) {
        AABB box = player.getBoundingBox();
        double result = maxY;
        for (VoxelShape shape : player.level().getBlockCollisions(player,
                new AABB(box.minX, minY, box.minZ, box.maxX, maxY, box.maxZ))) {
            result = Math.min(result, shape.min(Direction.Axis.Y));
        }
        return Math.max(result, minY);
    }

    /** Whether the own player is in a small box for a Smart Moving move ({@link MovingState#smallPose}). */
    boolean smallPose() {
        // A swimmer's or diver's box keeps the pose, which a grab out of a shallow swim ends before the flags.
        return state.lying() || state.crawlClimbing || climbing.climbCrawling || swimming.swimmingBox()
                || flying.flyingBox();
    }

    /**
     * Sliding and head jumping ({@code updateEntityActionState} 2412-2464): a head jump ends on landing, where a
     * slide or crawl goes on if standing does not fit or sneak and grab are held; a slide turns into a gliding
     * head jump when it falls, starts on sneak while sprinting with grab held, and ends in crawling. Landing head
     * first only takes vanilla's fall damage (see {@link SlideLogic}).
     */
    private void updateSlideAndHeadJump(Button sneak, Button grab, boolean flying, boolean onGround,
                                        SmartMovingClientConfig config) {
        Vec3 motion = player.getDeltaMovement();
        boolean wasHeadJumping = state.headJumping;
        state.headJumping = SlideLogic.continueHeadJump(state.headJumping, onGround,
                state.swimming || state.diving || flying,
                player.isInWater() && motion.y < 0, player.isInLava());
        if (!state.headJumping) {
            aerodynamic = false;
            lift.clear(BoxLift.Reason.HEAD_JUMP);
        }
        if (wasHeadJumping && !state.headJumping && onGround
                && (!fits(Pose.STANDING) || sneak.pressed && grab.pressed)) {
            if (config.slide.get()) {
                state.sliding = true;
            } else {
                toCrawling(config);
            }
        }

        if (state.sliding && player.fallDistance > SlideLogic.SLIDE_FALL_DISTANCE) {
            state.sliding = false;
            state.headJumping = true;
            aerodynamic = true;
        }
        if (SlideLogic.startSlide(config.slide.get(), grab.pressed, groundSprinting, isRunning(), onGround,
                state.crawling, sneak.startPressed, player.isInWater())) {
            lowerForSlide();
            jumping.tryJump(JumpType.SLIDE, Float.NaN, config);
            state.sliding = true;
            state.headJumping = false;
            aerodynamic = false;
        }
        if (state.sliding && SlideLogic.stopSlide(sneak.pressed, motion.x * motion.x + motion.z * motion.z,
                config.slideSpeedStopFactor.get())) {
            state.sliding = false;
            toCrawling(config);
        }
    }

    /**
     * The original's {@code setHeightOffset(-1)} and {@code move(0, -1, 0)} when a slide starts: the bottom of the
     * box rises a block, then the player moves a block down. On the ground that changes nothing. In the air it
     * adds a block of fall distance, so that the slide turns into a gliding head jump on the next tick. A head
     * jump's box was raised already, so there the player really moves down, back to the model's feet; vanilla's
     * move handles collisions and the fall distance. The original ignored cobwebs for that move; here a cobweb
     * still slows it.
     */
    private void lowerForSlide() {
        if (lift.is(BoxLift.Reason.HEAD_JUMP)) {
            player.move(MoverType.SELF, new Vec3(0, -1, 0));
            lift.clear(BoxLift.Reason.HEAD_JUMP);
        } else if (!player.onGround()) {
            player.fallDistance += 1;
        }
    }

    /** Another move turns into crawling ({@code toCrawling}), as if it had been crawling already. */
    void toCrawling(SmartMovingClientConfig config) {
        state.crawling = true;
        wasCrawling = true;
        toggles.toCrawling(config.crawlToggle.get());
    }

    /**
     * Adjusts the movement input vanilla has just set. Like the original, vanilla's input scaling (sneaking,
     * item usage) is dropped for the input's signs, and {@link #landSpeedFactor} applies the speed instead.
     */
    void applyInput(SmartMovingClientConfig config) {
        player.xxa = Math.signum(player.xxa);
        player.zza = Math.signum(player.zza);
        vanillaJumping = jumpInput && !state.crawling && !state.sliding
                && (!config.headJump.get() || !grabPressed || !player.isSprinting())
                && (!config.jumpCharge.get() || !wouldSneak || !player.onGround() || !standing)
                && !jumping.blocked();
        player.setJumping(vanillaJumping);
        if (isRunning() && !config.run.get()) {
            player.setSprinting(false);
        }
        if (state.crawling || state.sliding) {
            player.setSprinting(false);
        }
        swimming.afterServerAiStep();
    }

    /** Vanilla is about to jump from the ground; Smart Moving's jump replaces it before the move. */
    void avoidJump() {
        jumping.avoidJump();
    }

    /**
     * Before vanilla moves the player ({@code handleJumping}): the normal jump vanilla asked for, charging and
     * releasing a charged jump or head jump, the jump while dipping, and side and back jumps; then turns a slide
     * by the strafe input.
     */
    void beforeTravel(SmartMovingClientConfig config) {
        vanillaDamping = Float.NaN;
        travelStartY = player.getDeltaMovement().y;
        swimming.beforeTravel();
        jumping.handleJumping(config);
        climbing.beforeTravel(player.zza > 0);
        if (state.sliding && player.onGround()) {
            Vec3 motion = player.getDeltaMovement();
            double[] steered = SlideLogic.steer(motion.x, motion.z, player.xxa, config.slideControlAngle.get());
            if (steered != null) {
                player.setDeltaMovement(steered[0], motion.y, steered[1]);
            }
        }
    }

    /**
     * After the jumps, instead of vanilla's {@code travel} when true: swimming, diving and dipping, and the land
     * movement where vanilla would move the player in water ({@link SelfSwimming#travel}); then Smart Moving's
     * flying ({@link SelfFlying#travel}). Flying vanilla's way with {@code move.fly} off, the original kept
     * vanilla's vertical motion as it was before the move, damped to 0.6, like vanilla's flying does
     * ({@code moveEntityWithHeading} 164-171).
     */
    boolean travel(Vec3 input, SmartMovingClientConfig config) {
        if (!swimming.travel(input, config) && !flying.travel(input, config)) {
            return false;
        }
        if (player.getAbilities().flying && !player.isPassenger() && !flying.flyEnabled(config)) {
            Vec3 motion = player.getDeltaMovement();
            player.setDeltaMovement(motion.x, travelStartY * 0.6, motion.z);
            player.resetFallDistance();
            player.stopFallFlying();
        }
        return true;
    }

    /**
     * What vanilla's {@code travel} does after moving, which a cancelled one skips. The movement statistics are
     * counted by the server from the move packets.
     */
    void finishTravel() {
        player.calculateEntityAnimation(false);
    }

    /** What vanilla sees as the sneak key. */
    boolean shiftKeyDown(SmartMovingClientConfig config) {
        return SpeedLogic.shiftKeyDown(state.slow, player.onGround(), config.sneak.get(), wouldSneak,
                jumping.jumpCharge(), state.crawling, config.crawlOverEdge.get());
    }

    /** The ticks the jump key has been held for a charged jump, for the charge bar. */
    float jumpCharge() {
        return jumping.jumpCharge();
    }

    /** The ticks the jump key has been held for a head jump, for the charge bar. */
    float headJumpCharge() {
        return jumping.headJumpCharge();
    }

    /**
     * Vanilla is about to move the player on land or in the air with {@code friction} from the block below,
     * and will then damp the horizontal motion by {@code friction * 0.91} on the ground or 0.91 in the air.
     */
    void beforeFrictionMove(float friction) {
        travelFriction = friction;
        travelOnGround = player.onGround();
        vanillaDamping = travelOnGround ? friction * 0.91f : 0.91f;
    }

    /**
     * After vanilla's {@code travel}: replaces its horizontal damping with a slide's on the ground, or a gliding
     * head jump's in the air ({@code landMotion} 757-786); then wall jumps ({@code handleWallJumping}).
     */
    void afterTravel(SmartMovingClientConfig config) {
        climbing.afterTravel();
        swimming.afterTravel();
        if (!Float.isNaN(vanillaDamping)) {
            damp(config);
        }
        jumping.handleWallJumping(config);
    }

    private void damp(SmartMovingClientConfig config) {
        float damping = vanillaDamping;
        if (state.sliding && travelOnGround) {
            damping = SlideLogic.damping(travelFriction, config.slideGlideFactor.get());
        } else if (aerodynamic && !travelOnGround) {
            damping = SlideLogic.AERODYNAMIC_DAMPING;
        }
        if (damping != vanillaDamping) {
            Vec3 motion = player.getDeltaMovement();
            float scale = damping / vanillaDamping;
            player.setDeltaMovement(motion.x * scale, motion.y, motion.z * scale);
        }
        vanillaDamping = Float.NaN;
    }

    /** Before vanilla moves the player: notes where the move starts, for the climbing sounds and wall jumps. */
    void beforeMove(MoverType type, Vec3 movement) {
        selfMoveStart = type == MoverType.SELF ? player.position() : null;
        jumping.beforeMove(type, movement);
    }

    /** After vanilla moved the player: the climbing sounds and the wall the move ran into. */
    void afterMove() {
        if (selfMoveStart != null) {
            double distance = player.position().distanceTo(selfMoveStart);
            climbing.afterMove(distance);
            swimming.afterMove(distance);
            selfMoveStart = null;
        }
        jumping.afterMove();
    }

    /**
     * The multiplier on vanilla's walking speed on land and in the air (the original's {@code getSpeedFactor}).
     * A slide only glides, without walking; hanging on a ceiling moves hand over hand, slowly.
     */
    float landSpeedFactor(SmartMovingClientConfig config) {
        if (state.sliding || climbing.pushedBack()) {
            return 0;
        }
        return SpeedLogic.landSpeedFactor(config.speedFactor.get(), itemFactor(config),
                state.crawling || state.crawlClimbing && !climbing.climbCrawling, config.crawlFactor.get(), state.slow, sneakFactor(config),
                state.fast, config.sprintFactor.get(), config.run.get() && isRunning(), config.runFactor.get(),
                player.isSprinting())
                * SpeedLogic.jumpFactor(player.onGround(), jumpInput, state.fast, config.sprintJump.get(),
                config.sprintJumpVerticalFactor.get(), config.jumpControlFactor.get(), state.headJumping,
                config.headJumpControlFactor.get())
                * climbing.horizontalFactor(player.xxa != 0 || player.zza != 0, config)
                * (state.ceilingClimbing ? config.ceilingClimbSpeedFactor.get() : 1);
    }

    /**
     * {@code LivingEntity#onClimbable} for the own player when non-null: the original's ladders and vines
     * ({@code isOnLadder}).
     */
    Boolean onClimbable() {
        return climbing.onClimbable();
    }

    /** Replaces {@code LivingEntity#handleOnClimbable} when non-null: ladder and vine handling before moving. */
    Vec3 handleOnClimbable(Vec3 motion, SmartMovingClientConfig config) {
        return climbing.handleOnClimbable(motion, player.zza > 0, sneakInput, speedFactor(config), config);
    }

    /**
     * {@code LivingEntity#handleRelativeFrictionAndCalculateMovement} RETURN: the motion after moving and before
     * gravity, which climbing sets. Without free climbing, a crawl that just started gives way to a ceiling found
     * a block higher up: the player stands and moves up a block to it ({@code handleCeilingClimbing} 1269-1273).
     */
    Vec3 afterFrictionMove(Vec3 vanilla, SmartMovingClientConfig config) {
        boolean crawlStartConflict = !config.climbFree.get() && state.crawling && !wasCrawling;
        Vec3 motion = climbing.afterMove(vanilla, grabPressed, state.fast, speedFactor(config), crawlStartConflict,
                config);
        if (crawlStartConflict && state.ceilingClimbing) {
            state.crawling = false;
            player.setPose(Pose.STANDING);
            player.move(MoverType.SELF, new Vec3(0, 1, 0));
        }
        return motion;
    }

    /**
     * The speed factor of the original's own movement ({@code getSpeedFactor(moveForward, moveStrafing)}) without
     * its climbing part, which swimming and flying use: the global factor and the movement speed, the item usage,
     * crawling or sneaking, and Smart Moving's sprint.
     */
    float ownSpeedFactor(SmartMovingClientConfig config) {
        return speedFactor(config) * SpeedLogic.landSpeedFactor(1, itemFactor(config),
                state.crawling || state.crawlClimbing && !climbing.climbCrawling, config.crawlFactor.get(), state.slow,
                sneakFactor(config), state.fast, config.sprintFactor.get(), false, 1, false);
    }

    /**
     * The player's speed relative to plain walking, which scales the cap on jump boosts ({@code getSpeedFactor()}):
     * the global factor and the movement speed attribute without vanilla's sprint bonus.
     */
    float speedFactor(SmartMovingClientConfig config) {
        float speed = config.speedFactor.get() * movementSpeed() * 10;
        return player.isSprinting() ? speed / SpeedLogic.VANILLA_SPRINT_FACTOR : speed;
    }

    /**
     * At the end of the tick (the original's {@code afterOnLivingUpdate} and {@code afterOnUpdate}): flying on
     * after touching the ground, the flying and falling animations ({@code doFlyingAnimation},
     * {@code doFallingAnimation}; elytra gliding and riptide spins keep vanilla's), the wall counter, the body turn while small, slide and swim particles, the
     * current taken back while swimming, and the perspective.
     */
    void afterTick(SmartMovingClientConfig config) {
        flying.afterTick(sneakInput, grabPressed, config);
        state.flyingAnimation = flying.animation(config);
        state.fallingAnimation = config.fallAnimation.get() && !player.onGround() && !vanillaOverride()
                && player.fallDistance > config.fallAnimationDistanceMinimum.get();
        collidedHorizontallyTicks = player.horizontalCollision ? collidedHorizontallyTicks + 1 : 0;
        turnBodyWhileSmall(state.swimming || state.diving || state.dipping || state.crawling);
        if (state.sliding) {
            Vec3 motion = player.getDeltaMovement();
            SlideParticles.spawn(player, motion.x, motion.z, config);
        }
        swimming.afterTick(config);

        float movementSpeed = movementSpeed();
        float target = SpeedLogic.perspectiveSpeed(movementSpeed, state.fast, sprintJump, isRunning(),
                player.isSprinting(), config.perspectiveSprintFactor.get(), config.perspectiveRunFactor.get());
        fadingPerspectiveSpeed = SpeedLogic.fadePerspective(fadingPerspectiveSpeed, target,
                config.perspectiveFadeFactor.get(), movementSpeed);
    }

    /**
     * Moving slowly in water or crawling, the body turns towards the movement, at most 75 degrees off the view
     * ({@code correctOnUpdate}); vanilla only turns it when moving faster. While swinging an arm, towards the
     * view.
     */
    private void turnBodyWhileSmall(boolean small) {
        double dx = player.getX() - player.xo;
        double dz = player.getZ() - player.zo;
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (!small || distance >= 0.05 || distance <= 0.02) {
            return;
        }
        float target = player.attackAnim > 0 ? player.getYRot() : (float) Mth.atan2(dz, dx) * Mth.RAD_TO_DEG - 90;
        float body = player.yBodyRot + Mth.wrapDegrees(target - player.yBodyRot) * 0.3f;
        float offView = Mth.clamp(Mth.wrapDegrees(player.getYRot() - body), -75, 75);
        player.yBodyRot = player.getYRot() - offView;
        if (offView * offView > 2500) {
            player.yBodyRot += offView * 0.2f;
        }
        while (player.yBodyRot - player.yBodyRotO < -180) {
            player.yBodyRotO -= 360;
        }
        while (player.yBodyRot - player.yBodyRotO >= 180) {
            player.yBodyRotO += 360;
        }
    }

    /** The movement speed vanilla's field of view should use instead of the current one. */
    float perspectiveSpeed() {
        return fadingPerspectiveSpeed < 0 ? movementSpeed() : fadingPerspectiveSpeed;
    }

    /** Vanilla sprinting without Smart Moving's sprint, on the ground (the original's {@code isRunning}). */
    boolean isRunning() {
        return player.isSprinting() && !state.fast && player.onGround();
    }

    // What jumping reads of the other moves

    /** The jump key, read fresh this tick. */
    boolean jumpInput() {
        return jumpInput;
    }

    /** Hardly moving horizontally ({@code isStanding}). */
    boolean standing() {
        return standing;
    }

    /** Would sneak if not sprinting ({@code wouldIsSneaking}). */
    boolean wouldSneak() {
        return wouldSneak;
    }

    boolean grabPressed() {
        return grabPressed;
    }

    /** The sneak key, read fresh this tick. */
    boolean sneakInput() {
        return sneakInput;
    }

    /** Flying Smart Moving's way ({@code isFlying}). */
    boolean smartFlying() {
        return flying.smartFlying();
    }

    /** Whether the box is a flyer's, small and a block up. */
    boolean flyingBox() {
        return flying.flyingBox();
    }

    /** In water the way 1.7.10 saw it ({@code isInWater}). */
    boolean inWater() {
        return swimming.inWater();
    }

    /** Standing up from a shallow swim with jump held, which keeps that press from jumping. */
    boolean stillSwimmingJump() {
        return swimming.stillSwimmingJump();
    }

    /** Whether a jump set the vertical motion in this tick's jump handling. */
    boolean jumpedThisTick() {
        return jumping.jumpedThisTick();
    }

    /** Whether vanilla jumps this tick ({@code isJumping}). */
    boolean vanillaJumping() {
        return vanillaJumping;
    }

    /** Wanting to climb up a wall ({@code wantClimbUp}). */
    boolean wantClimbUp() {
        return climbing.wantClimbUp;
    }

    /** Climbing into a gap with the box shrunk from below ({@code isClimbCrawling}). */
    boolean climbCrawling() {
        return climbing.climbCrawling;
    }

    /** Smart Moving sprinting on the ground ({@code isGroundSprinting}). */
    boolean groundSprinting() {
        return groundSprinting;
    }

    /** In the air after sprinting ({@code isSprintJump}). */
    boolean sprintJump() {
        return sprintJump;
    }

    void setSprintJump(boolean sprintJump) {
        this.sprintJump = sprintJump;
    }

    private float movementSpeed() {
        return (float) player.getAttributeValue(Attributes.MOVEMENT_SPEED);
    }

    /**
     * The item usage factor. The original told swords (blocking), bows and food apart; here the item's use
     * animation does, so shields block like the old swords and crossbows pull back like bows.
     */
    private float itemFactor(SmartMovingClientConfig config) {
        if (!player.isUsingItem()) {
            return 1;
        }
        return switch (player.getUseItem().getUseAnimation()) {
            case BLOCK -> config.usageSwordSpeedFactor.get();
            case BOW, CROSSBOW -> config.usageBowSpeedFactor.get();
            case EAT -> config.usageFoodSpeedFactor.get();
            default -> config.usageSpeedFactor.get();
        };
    }

    /**
     * The sneaking factor, raised by Swift Sneak like vanilla's (which the original did not know): Swift Sneak adds
     * to the sneaking speed attribute, whose base is vanilla's sneaking factor.
     */
    private float sneakFactor(SmartMovingClientConfig config) {
        double bonus = player.getAttributeValue(Attributes.SNEAKING_SPEED)
                - player.getAttributeBaseValue(Attributes.SNEAKING_SPEED);
        return Mth.clamp(config.sneakFactor.get() + (float) bonus, 0, 1);
    }

    /** Whether {@code pose} fits at the player's position, like vanilla's {@code canEnterPose}. */
    private boolean fits(Pose pose) {
        return fits(pose, 0);
    }

    /** Whether {@code pose} fits {@code dy} blocks above the player's position. */
    boolean fits(Pose pose, double dy) {
        return player.level().noCollision(player,
                player.getDimensions(pose).makeBoundingBox(player.position().add(0, dy, 0)).deflate(1.0E-7));
    }
}
