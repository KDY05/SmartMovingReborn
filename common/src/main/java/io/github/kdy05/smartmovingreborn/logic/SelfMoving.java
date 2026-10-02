package io.github.kdy05.smartmovingreborn.logic;

import io.github.kdy05.smartmovingreborn.client.SmartMovingClient;
import io.github.kdy05.smartmovingreborn.config.SmartMovingClientConfig;
import io.github.kdy05.smartmovingreborn.input.Button;
import io.github.kdy05.smartmovingreborn.logic.crawl.CrawlLogic;
import io.github.kdy05.smartmovingreborn.logic.jump.AngleJumpInput;
import io.github.kdy05.smartmovingreborn.logic.jump.JumpEngine;
import io.github.kdy05.smartmovingreborn.logic.jump.JumpSpeed;
import io.github.kdy05.smartmovingreborn.logic.jump.JumpType;
import io.github.kdy05.smartmovingreborn.logic.jump.WallJump;
import io.github.kdy05.smartmovingreborn.logic.jump.WallJumpInput;
import io.github.kdy05.smartmovingreborn.logic.slide.SlideLogic;
import io.github.kdy05.smartmovingreborn.mixin.common.EntityAccessor;
import io.github.kdy05.smartmovingreborn.render.SlideParticles;
import io.github.kdy05.smartmovingreborn.render.SmartMovingRender;
import io.github.kdy05.smartmovingreborn.state.MovingState;
import net.minecraft.tags.FluidTags;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The client's own player's Smart Moving state and its per-tick update, like the original's
 * {@code SmartMovingSelf}. The feature classes ({@code logic/crawl}, ...) only decide; this class measures the
 * player, calls them in the original's order and keeps the results. The shared flags live in {@link #state},
 * which is also what gets sent to the server and read by rendering.
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
    /** Climb crawling lifts the player this much and then moves it up a little more ({@code move(0, 0.05, 0)}). */
    private static final double CLIMB_CRAWL_LIFT = 1;

    final Player player;
    final MovingState state;
    private final ToggleState toggles = new ToggleState();
    private final AngleJumpInput angleJumps = new AngleJumpInput();
    private final WallJumpInput wallJumps = new WallJumpInput();
    private final SelfClimbing climbing;
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
    /** Would sneak if not sprinting, regardless of {@code move.sneak} ({@code wouldIsSneaking}). */
    private boolean wouldSneak;
    private boolean standing;
    /** Vanilla wanted to jump this tick and was stopped; {@link #handleJumping} jumps instead ({@code jumpAvoided}). */
    private boolean jumpAvoided;
    /** After a cancelled charge, no jump until the key is released. */
    private boolean blockJumpTillButtonRelease;
    /** Ticks the jump key has been held for a charged jump. */
    private float jumpCharge;
    /** Ticks the jump key has been held while charging a head jump. */
    private float headJumpCharge;
    /** The horizontal motion when the jumps were decided, before this tick's movement. */
    private double jumpMotionX;
    private double jumpMotionZ;
    private boolean grabPressed;
    /** Gliding out of a slide: a head jump that keeps nearly all of its horizontal speed ({@code isAerodynamic}). */
    private boolean aerodynamic;
    /** The head jump lifted the player a block, standing in for the original's raised box ({@link #tryJump}). */
    private boolean lifted;
    /** Climb crawling started from the standing box and lifted the player ({@link #updateClimbCrawling}). */
    private boolean climbCrawlLifted;
    /**
     * The horizontal damping vanilla applies in this tick's {@code travel}, NaN before it moves the player on
     * land or in the air; with the friction and ground state it came from.
     */
    private float vanillaDamping;
    private float travelFriction;
    private boolean travelOnGround;
    /** Touched a wall at the start of the tick ({@code wasCollidedHorizontally}). */
    private boolean wasCollidedHorizontally;
    /** The direction of the wall the last move ran into while wanting to wall jump, NaN for none. */
    private float horizontalCollisionAngle;
    /** The sneak key, read fresh each tick. */
    private boolean sneakInput;
    /** Where the player's own move started, for the climbing sounds. */
    private Vec3 selfMoveStart;
    /** Where the current move started and how far it was asked to go, NaN while not measuring. */
    private double moveStartX;
    private double moveStartZ;
    private double moveX;
    private double moveZ;

    SelfMoving(Player player, MovingState state) {
        this.player = player;
        this.state = state;
        this.climbing = new SelfClimbing(player, state, this::climbJump);
        reset();
    }

    void reset() {
        state.crawling = false;
        state.sliding = false;
        state.headJumping = false;
        state.slow = false;
        state.fast = false;
        state.angleJumpType = 0;
        state.wallJumping = false;
        wasCrawling = false;
        groundSprinting = false;
        wasRunningWhenSprintStarted = false;
        sprintJump = false;
        collidedHorizontallyTicks = 0;
        fadingPerspectiveSpeed = -1;
        jumpInput = false;
        wouldSneak = false;
        standing = false;
        jumpAvoided = false;
        blockJumpTillButtonRelease = false;
        jumpCharge = 0;
        headJumpCharge = 0;
        jumpMotionX = 0;
        jumpMotionZ = 0;
        grabPressed = false;
        aerodynamic = false;
        lifted = false;
        climbCrawlLifted = false;
        vanillaDamping = Float.NaN;
        wasCollidedHorizontally = false;
        horizontalCollisionAngle = Float.NaN;
        moveStartX = Double.NaN;
        toggles.reset();
        angleJumps.reset();
        wallJumps.reset();
        climbing.reset();
        sneakInput = false;
        selfMoveStart = null;
    }

    /** At the start of the tick ({@code beforeOnUpdate}). */
    void beforeTick() {
        wasCollidedHorizontally = player.horizontalCollision;
    }

    /** Once per tick, before vanilla turns the input into movement (the original's {@code updateEntityActionState}). */
    void updateActionState(Button sneak, Button grab, Button jump, Button sprint, Button left, Button right,
                           Button back, boolean forwardPressed, boolean jumpInput, SmartMovingClientConfig config) {
        this.jumpInput = jumpInput;
        grabPressed = grab.pressed;
        sneakInput = sneak.pressed;
        jumpAvoided = false;
        boolean flying = player.getAbilities().flying;
        boolean smartFlying = flying && config.fly.get();
        boolean disabled = player.isPassenger() || player.isSleeping();
        boolean onGround = player.onGround();

        boolean crawling = state.crawling;
        // Sliding and head jumping keep the small pose on their own.
        boolean mustCrawl = !state.sliding && !state.headJumping && CrawlLogic.mustCrawl(crawling,
                fits(Pose.STANDING), fits(Pose.CROUCHING), flying, config.fly.get() || config.levitateSmall.get());
        boolean inputContinueCrawl = CrawlLogic.inputContinueCrawl(config.crawlToggle.get(), toggles.isCrawlToggled(),
                sneak.pressed, config.climbFree.get(), grab.pressed);
        boolean wantCrawl = CrawlLogic.wantCrawl(config.crawl.get(), crawling, flying, inputContinueCrawl,
                grab.startPressed, sneak.pressed || toggles.isSneakToggled(), onGround);
        boolean canCrawl = !player.isSwimming()
                && player.getFluidHeight(FluidTags.WATER) < CrawlLogic.MAX_WATER_DEPTH
                && !state.climbing
                && player.fallDistance < config.fallDistanceMinimum.get()
                && !player.isPassenger() && !player.isSleeping() && !player.isFallFlying();
        wasCrawling = crawling;
        state.crawling = canCrawl && (wantCrawl || mustCrawl);

        climbing.updateInput(grab, sneak, jump, forwardPressed, wasCrawling, wantCrawl, disabled, config);

        updateSlideAndHeadJump(sneak, grab, flying, onGround, config);

        boolean wouldWantSneak = SpeedLogic.wouldWantSneak(config.sneakToggle.get(), toggles.isSneakToggled(),
                sneak.pressed, sneak.startPressed, wantCrawl, mustCrawl, config.crawl.get(), grab.pressed, smartFlying,
                state.sliding || state.headJumping);
        boolean wantSneak = config.sneak.get() && wouldWantSneak;
        boolean wantSprint = SpeedLogic.wantSprint(config.sprint.get(), sprint.pressed,
                forwardPressed || state.climbing, state.sliding, disabled);

        if (!onGround && state.fast && !state.climbing) {
            sprintJump = true;
        }
        if (onGround || smartFlying || player.isInLava()) {
            sprintJump = false;
        }

        boolean wasGroundSprinting = groundSprinting;
        groundSprinting = SpeedLogic.groundSprinting(wantSprint, wantSneak, player.isOnFire(), player.isUsingItem(),
                config.usageSprint.get(), collidedHorizontallyTicks, onGround && !state.climbing);
        boolean climbSprinting = SpeedLogic.canAnySprint(wantSprint, wantSneak, player.isOnFire(),
                player.isUsingItem(), config.usageSprint.get()) && state.climbing && climbing.sprintSpeed(config);
        state.fast = groundSprinting || climbSprinting;
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
        Vec3 motion = player.getDeltaMovement();
        standing = motion.x * motion.x + motion.z * motion.z < STANDING_SPEED_SQUARE;

        state.wallJumping = false;
        // Smart Moving swimming (step 14) also rules it out; until then any water does.
        boolean canWallJump = config.wallUpJump.get() && !state.headJumping && !player.onGround() && !flying
                && !player.isInWater() && !player.isFallFlying() && !state.climbing;
        wallJumps.update(canWallJump,
                config.wallJumpDoubleClick.get() ? (int) Math.ceil(config.wallJumpDoubleClickTicks.get()) : 0,
                player.onGround() || state.climbing, jump.pressed, jump.startPressed, player.horizontalCollision);

        boolean canAngleJump = !player.isSleeping() && onGround && !state.crawling;
        boolean canSideJump = config.angleJumpSide.get() && canAngleJump;
        boolean canBackJump = config.angleJumpBack.get() && canAngleJump && !forwardPressed
                && !SpeedLogic.standupSprintingOrRunning(state.fast, player.isSprinting(), onGround, state.sliding,
                state.crawling);
        angleJumps.update(config.angleJumpDoubleClickTicks.get().intValue(), canSideJump && !right.pressed,
                left.startPressed, canSideJump && !left.pressed, right.startPressed, canBackJump, back.startPressed);
        if (onGround || player.verticalCollision) {
            state.angleJumpType = 0;
        }

        toggles.update(config.sneakToggle.get(), config.crawlToggle.get(), state.crawling, wasCrawling,
                state.slow, wasSlow, state.fast, wantSneak && wantSprint, false, sneak, jump);
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
            climbCrawlLifted = player.getPose() != Pose.SWIMMING;
            if (climbCrawlLifted) {
                player.setPose(Pose.SWIMMING);
                SmartMovingClient.sendStateNow(player);
                shiftPosition(CLIMB_CRAWL_LIFT);
            }
            boolean collided = player.horizontalCollision;
            player.move(MoverType.SELF, new Vec3(0, 0.05, 0));
            player.horizontalCollision = collided;
        } else if (!now && was) {
            // Standing up keeps the original's box: a lifted box grows back by the lift, a small one keeps its top.
            double standUp = climbCrawlLifted ? -CLIMB_CRAWL_LIFT : -SMALL_TO_STANDING;
            climbCrawlLifted = false;
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

    /** Back to the standing box from a small one, moving the player by {@code dy} without collisions. */
    private void standUpFromSmall(double dy) {
        shiftPosition(dy);
        player.setPose(Pose.STANDING);
    }

    /**
     * Moves the player by {@code dy} without collisions, like the original's box changes; the previous position
     * and the camera's eye height move along so that the view and the model's interpolation stay put.
     */
    private void shiftPosition(double dy) {
        player.setPos(player.getX(), player.getY() + dy, player.getZ());
        player.yo += dy;
        player.yOld += dy;
        SmartMovingClient.offsetCameraEyeHeight(player, (float) dy);
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
    private double maxSolidBetween(double minY, double maxY) {
        AABB box = player.getBoundingBox();
        double result = minY;
        for (VoxelShape shape : player.level().getBlockCollisions(player,
                new AABB(box.minX, minY, box.minZ, box.maxX, maxY, box.maxZ))) {
            result = Math.max(result, shape.max(Direction.Axis.Y));
        }
        return Math.min(result, maxY);
    }

    /** Whether the own player is in a small box for a Smart Moving move ({@link MovingState#smallPose}). */
    boolean smallPose() {
        return state.lying() || state.crawlClimbing || climbing.climbCrawling;
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
        state.headJumping = SlideLogic.continueHeadJump(state.headJumping, onGround, player.isSwimming() || flying,
                player.isInWater() && motion.y < 0, player.isInLava());
        if (!state.headJumping) {
            aerodynamic = false;
            lifted = false;
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
            tryJump(JumpType.SLIDE, Float.NaN, config);
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
        if (lifted) {
            player.move(MoverType.SELF, new Vec3(0, -1, 0));
            lifted = false;
        } else if (!player.onGround()) {
            player.fallDistance += 1;
        }
    }

    /** Another move turns into crawling ({@code toCrawling}), as if it had been crawling already. */
    private void toCrawling(SmartMovingClientConfig config) {
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
        player.setJumping(jumpInput && !state.crawling && !state.sliding
                && (!config.headJump.get() || !grabPressed || !player.isSprinting())
                && (!config.jumpCharge.get() || !wouldSneak || !player.onGround() || !standing)
                && !blockJumpTillButtonRelease);
        if (isRunning() && !config.run.get()) {
            player.setSprinting(false);
        }
        if (state.crawling || state.sliding) {
            player.setSprinting(false);
        }
    }

    /** Vanilla is about to jump from the ground; Smart Moving's jump replaces it in {@link #handleJumping}. */
    void avoidJump() {
        jumpAvoided = true;
    }

    /**
     * Before vanilla moves the player ({@code handleJumping}): the normal jump vanilla asked for, charging and
     * releasing a charged jump or head jump, and side and back jumps; then turns a slide by the strafe input.
     * Jumps in water join in step 14.
     */
    void beforeTravel(SmartMovingClientConfig config) {
        vanillaDamping = Float.NaN;
        handleJumping(config);
        climbing.beforeTravel(player.zza > 0);
        if (state.sliding && player.onGround()) {
            Vec3 motion = player.getDeltaMovement();
            double[] steered = SlideLogic.steer(motion.x, motion.z, player.xxa, config.slideControlAngle.get());
            if (steered != null) {
                player.setDeltaMovement(steered[0], motion.y, steered[1]);
            }
        }
    }

    private void handleJumping(SmartMovingClientConfig config) {
        if (blockJumpTillButtonRelease && !jumpInput) {
            blockJumpTillButtonRelease = false;
        }
        boolean onGround = player.onGround();
        boolean jump = jumpAvoided && onGround;
        Vec3 motion = player.getDeltaMovement();
        jumpMotionX = motion.x;
        jumpMotionZ = motion.z;

        boolean jumpCharging = false;
        if (config.jumpCharge.get()) {
            boolean jumpChargingPossible = onGround && standing;
            jumpCharging = jumpChargingPossible && wouldSneak;
            boolean cancelOnSneakRelease = config.jumpChargeCancelOnSneakRelease.get();
            if (jumpChargingPossible && (!cancelOnSneakRelease || wouldSneak)) {
                if (!jumpInput || !cancelOnSneakRelease && !wouldSneak) {
                    if (jumpCharge > 0) {
                        tryJump(JumpType.CHARGE, Float.NaN, config);
                    }
                    jumpCharge = 0;
                } else {
                    jumpCharge++;
                }
            } else {
                if (jumpCharge > 0) {
                    blockJumpTillButtonRelease = true;
                }
                jumpCharge = 0;
            }
        }

        boolean headJumpCharging = false;
        if (config.headJump.get()) {
            headJumpCharging = grabPressed && (groundSprinting || sprintJump || isRunning() && onGround)
                    && !state.crawling;
            if (headJumpCharging) {
                if (jumpInput) {
                    headJumpCharge++;
                } else {
                    if (headJumpCharge > 0 && onGround) {
                        tryJump(JumpType.HEAD, Float.NaN, config);
                    }
                    headJumpCharge = 0;
                }
            } else {
                if (headJumpCharge > 0) {
                    blockJumpTillButtonRelease = true;
                }
                headJumpCharge = 0;
            }
        }

        boolean vineClimbing = state.handsVineClimbing || state.feetVineClimbing;
        if (jump && !blockJumpTillButtonRelease && !jumpCharging && !headJumpCharging && !vineClimbing) {
            tryJump(JumpType.UP, Float.NaN, config);
        }

        float angle = angleJumps.take();
        if (!Float.isNaN(angle) && tryJump(JumpType.ANGLE, player.getYRot() + angle, config)) {
            state.angleJumpType = AngleJumpInput.animationType(angle);
        }
    }

    /**
     * Jumps if this jump is switched on ({@code tryJump}). The player then moves through the air for the rest of
     * the tick like in the original, instead of vanilla's ground movement on the jump tick.
     *
     * @param angle the jump direction (yaw) of angled jumps, otherwise NaN
     */
    private boolean tryJump(JumpType type, float angle, SmartMovingClientConfig config) {
        JumpSpeed speed = JumpSpeed.of(standing, state.slow, isRunning(), state.fast, !Float.isNaN(angle));
        if (!JumpEngine.enabled(config, speed, type)) {
            return false;
        }
        MobEffectInstance jumpBoost = player.getEffect(MobEffects.JUMP);
        float boostFactor = JumpEngine.jumpBoostFactor(jumpBoost == null ? -1 : jumpBoost.getAmplifier());
        float horizontalFactor = JumpEngine.horizontalFactor(config, speed, type) * boostFactor;
        float verticalFactor = JumpEngine.verticalFactor(config, speed, type) * boostFactor;
        float chargeFactor = type == JumpType.CHARGE ? JumpEngine.chargeFactor(config, jumpCharge) : 1;
        double maxHorizontalMotion = horizontalFactor > 1 && !player.horizontalCollision
                ? JumpEngine.maxHorizontalMotion(config, speed, player.isInWater()) * speedFactor(config)
                : Double.NaN;

        Vec3 motion = player.getDeltaMovement();
        float headJumpFactor = type.base().head() ? JumpEngine.headJumpFactor(config, headJumpCharge) : 1;
        JumpEngine.Motion result = JumpEngine.motion(type, horizontalFactor, verticalFactor, chargeFactor,
                headJumpFactor, maxHorizontalMotion, jumpMotionX, jumpMotionZ, motion.x, motion.z, angle);
        boolean vertical = !Double.isNaN(result.y());
        player.setDeltaMovement(result.x(), vertical ? result.y() : motion.y, result.z());
        if (vertical) {
            sprintJump = state.fast;
        }
        if (type.base().head() && !state.headJumping) {
            state.headJumping = true;
            // The original raised the bottom of the box by a block and left the model where it was. Here the
            // box shrinks from the feet and the model is drawn a block below it, so the player rises a block
            // instead. The small pose applies at once, not at the end of the tick, and the server learns of it
            // before this tick's movement, so neither moves a standing box a block up. The small box a block
            // up lies within the standing one, so it fits wherever the player stood, like the original's.
            player.setPose(Pose.SWIMMING);
            SmartMovingClient.sendStateNow(player);
            if (fits(Pose.SWIMMING, 1)) {
                lifted = true;
                player.setPos(player.getX(), player.getY() + 1, player.getZ());
                // Rendering interpolates from the previous position, which rises too so the model stays put,
                // and the camera eases its eye height, which drops by as much so the view stays put.
                player.yo++;
                player.yOld++;
                SmartMovingClient.offsetCameraEyeHeight(player, 1);
            }
        }
        player.hasImpulse = true;
        player.setOnGround(false);
        return true;
    }

    /**
     * Jumps off a climbing hold. A jump back turns the view around to its direction, keeps up wall jumping
     * unless it is a head jump, and turns the body like the original's {@code onStartClimbBackJump}.
     */
    private boolean climbJump(JumpType type, float angle, SmartMovingClientConfig config) {
        if (!tryJump(type, angle, config)) {
            return false;
        }
        if (!Float.isNaN(angle)) {
            wallJumps.jumped(state.headJumping);
            player.setYRot(angle);
            state.climbBackJumping = true;
            SmartMovingRender.startClimbBackJump(player, state.headJumping);
        }
        return true;
    }

    /** What vanilla sees as the sneak key. */
    boolean shiftKeyDown(SmartMovingClientConfig config) {
        return SpeedLogic.shiftKeyDown(state.slow, player.onGround(), config.sneak.get(), wouldSneak, jumpCharge,
                state.crawling, config.crawlOverEdge.get());
    }

    /** The ticks the jump key has been held for a charged jump, for the charge bar. */
    float jumpCharge() {
        return jumpCharge;
    }

    /** The ticks the jump key has been held for a head jump, for the charge bar. */
    float headJumpCharge() {
        return headJumpCharge;
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
        if (!Float.isNaN(vanillaDamping)) {
            damp(config);
        }
        handleWallJumping(config);
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

    /**
     * Jumps off the wall this tick's movement ran into, if the player wants to and has not fallen too far: a
     * head jump with grab held. Like the original, the view turns to the jump direction and the body faces it
     * at once.
     */
    private void handleWallJumping(SmartMovingClientConfig config) {
        if (!wallJumps.want() || Float.isNaN(horizontalCollisionAngle)) {
            return;
        }
        boolean head = grabPressed;
        float maximumFallDistance = head ? config.wallHeadJumpFallMaximumDistance.get()
                : config.wallUpJumpFallMaximumDistance.get();
        if (player.fallDistance > maximumFallDistance) {
            return;
        }
        JumpType type = head ? (wasCollidedHorizontally ? JumpType.WALL_HEAD_TURN : JumpType.WALL_HEAD)
                : (wasCollidedHorizontally ? JumpType.WALL_UP_TURN : JumpType.WALL_UP);
        float angle = WallJump.jumpAngle(horizontalCollisionAngle, wasCollidedHorizontally, jumpMotionX,
                jumpMotionZ, config.wallUpJumpOrthogonalTolerance.get());
        if (Float.isNaN(angle) || !tryJump(type, angle, config)) {
            return;
        }
        wallJumps.jumped(state.headJumping);
        player.horizontalCollision = false;
        player.setYRot(angle);
        state.wallJumping = true;
        player.fallDistance = 0;
        SmartMovingRender.startWallJump(player, angle);
    }

    /**
     * Before vanilla moves the player: while wanting to wall jump, notes where the move starts and how far it
     * goes after a cobweb's slowdown, which vanilla applies first.
     */
    void beforeMove(MoverType type, Vec3 movement) {
        selfMoveStart = type == MoverType.SELF ? player.position() : null;
        if (type != MoverType.SELF || !wallJumps.want()) {
            moveStartX = Double.NaN;
            horizontalCollisionAngle = Float.NaN;
            return;
        }
        Vec3 stuck = ((EntityAccessor) player).smartmovingreborn$getStuckSpeedMultiplier();
        if (stuck.lengthSqr() > 1.0E-7) {
            movement = movement.multiply(stuck);
        }
        moveStartX = player.getX();
        moveStartZ = player.getZ();
        moveX = movement.x;
        moveZ = movement.z;
    }

    /**
     * After vanilla moved the player: the wall the move ran into, from the directions it fell short in. The
     * original simulated the move beforehand ({@code calculateSeparateCollisions}); vanilla's own result also
     * follows its axis order, which differs from 1.7.10's.
     */
    void afterMove() {
        if (selfMoveStart != null) {
            climbing.afterMove(player.position().distanceTo(selfMoveStart));
            selfMoveStart = null;
        }
        if (Double.isNaN(moveStartX)) {
            return;
        }
        double dx = player.getX() - moveStartX;
        double dz = player.getZ() - moveStartZ;
        moveStartX = Double.NaN;
        boolean shortX = !Mth.equal(moveX, dx);
        boolean shortZ = !Mth.equal(moveZ, dz);
        horizontalCollisionAngle = WallJump.collisionAngle(shortX && moveX > dx, shortX && moveX < dx,
                shortZ && moveZ > dz, shortZ && moveZ < dz);
    }

    /**
     * The multiplier on vanilla's walking speed on land and in the air (the original's {@code getSpeedFactor}).
     * A slide only glides, without walking.
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
                * climbing.horizontalFactor(player.xxa != 0 || player.zza != 0, config);
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
     * gravity, which climbing sets.
     */
    Vec3 afterFrictionMove(Vec3 vanilla, SmartMovingClientConfig config) {
        return climbing.afterMove(vanilla, grabPressed, state.fast, speedFactor(config), config);
    }

    /**
     * The player's speed relative to plain walking, which scales the cap on jump boosts ({@code getSpeedFactor()}):
     * the global factor and the movement speed attribute without vanilla's sprint bonus.
     */
    private float speedFactor(SmartMovingClientConfig config) {
        float speed = config.speedFactor.get() * movementSpeed() * 10;
        return player.isSprinting() ? speed / SpeedLogic.VANILLA_SPRINT_FACTOR : speed;
    }

    /**
     * At the end of the tick (the original's {@code afterOnUpdate}): the wall counter, slide particles and the
     * perspective.
     */
    void afterTick(SmartMovingClientConfig config) {
        collidedHorizontallyTicks = player.horizontalCollision ? collidedHorizontallyTicks + 1 : 0;
        if (state.sliding) {
            Vec3 motion = player.getDeltaMovement();
            SlideParticles.spawn(player, motion.x, motion.z, config);
        }

        float movementSpeed = movementSpeed();
        float target = SpeedLogic.perspectiveSpeed(movementSpeed, state.fast, sprintJump, isRunning(),
                player.isSprinting(), config.perspectiveSprintFactor.get(), config.perspectiveRunFactor.get());
        fadingPerspectiveSpeed = SpeedLogic.fadePerspective(fadingPerspectiveSpeed, target,
                config.perspectiveFadeFactor.get(), movementSpeed);
    }

    /** The movement speed vanilla's field of view should use instead of the current one. */
    float perspectiveSpeed() {
        return fadingPerspectiveSpeed < 0 ? movementSpeed() : fadingPerspectiveSpeed;
    }

    /** Vanilla sprinting without Smart Moving's sprint, on the ground (the original's {@code isRunning}). */
    private boolean isRunning() {
        return player.isSprinting() && !state.fast && player.onGround();
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

    /** The sneaking factor, raised by Swift Sneak like vanilla's (which the original did not know). */
    private float sneakFactor(SmartMovingClientConfig config) {
        return Mth.clamp(config.sneakFactor.get() + EnchantmentHelper.getSneakingSpeedBonus(player), 0, 1);
    }

    /** Whether {@code pose} fits at the player's position, like vanilla's {@code canEnterPose}. */
    private boolean fits(Pose pose) {
        return fits(pose, 0);
    }

    /** Whether {@code pose} fits {@code dy} blocks above the player's position. */
    private boolean fits(Pose pose, double dy) {
        return player.level().noCollision(player,
                player.getDimensions(pose).makeBoundingBox(player.position().add(0, dy, 0)).deflate(1.0E-7));
    }
}
