package io.github.kdy05.smartmovingreborn.logic;

import io.github.kdy05.smartmovingreborn.config.SmartMovingClientConfig;
import io.github.kdy05.smartmovingreborn.input.Button;
import io.github.kdy05.smartmovingreborn.logic.crawl.CrawlLogic;
import io.github.kdy05.smartmovingreborn.logic.jump.AngleJumpInput;
import io.github.kdy05.smartmovingreborn.logic.jump.JumpEngine;
import io.github.kdy05.smartmovingreborn.logic.jump.JumpSpeed;
import io.github.kdy05.smartmovingreborn.logic.jump.JumpType;
import io.github.kdy05.smartmovingreborn.state.MovingState;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;

/**
 * The client's own player's Smart Moving state and its per-tick update, like the original's
 * {@code SmartMovingSelf}. The feature classes ({@code logic/crawl}, ...) only decide; this class measures the
 * player, calls them in the original's order and keeps the results. The shared flags live in {@link #state},
 * which is also what gets sent to the server and read by rendering.
 */
public final class SelfMoving {
    /** Horizontal speed squared below which the player counts as standing ({@code isStanding}). */
    private static final double STANDING_SPEED_SQUARE = 5.0E-4;

    final Player player;
    final MovingState state;
    private final ToggleState toggles = new ToggleState();
    private final AngleJumpInput angleJumps = new AngleJumpInput();
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
    /** The horizontal motion when the jumps were decided, before this tick's movement. */
    private double jumpMotionX;
    private double jumpMotionZ;

    SelfMoving(Player player, MovingState state) {
        this.player = player;
        this.state = state;
        reset();
    }

    void reset() {
        state.crawling = false;
        state.slow = false;
        state.fast = false;
        state.angleJumpType = 0;
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
        jumpMotionX = 0;
        jumpMotionZ = 0;
        toggles.reset();
        angleJumps.reset();
    }

    /** Once per tick, before vanilla turns the input into movement (the original's {@code updateEntityActionState}). */
    void updateActionState(Button sneak, Button grab, Button jump, Button sprint, Button left, Button right,
                           Button back, boolean forwardPressed, boolean jumpInput, SmartMovingClientConfig config) {
        this.jumpInput = jumpInput;
        jumpAvoided = false;
        boolean flying = player.getAbilities().flying;
        boolean smartFlying = flying && config.fly.get();
        boolean disabled = player.isPassenger() || player.isSleeping();
        boolean onGround = player.onGround();

        boolean crawling = state.crawling;
        boolean mustCrawl = CrawlLogic.mustCrawl(crawling, fits(Pose.STANDING), fits(Pose.CROUCHING), flying,
                config.fly.get() || config.levitateSmall.get());
        boolean inputContinueCrawl = CrawlLogic.inputContinueCrawl(config.crawlToggle.get(), toggles.isCrawlToggled(),
                sneak.pressed, config.climbFree.get(), grab.pressed);
        boolean wantCrawl = CrawlLogic.wantCrawl(config.crawl.get(), crawling, flying, inputContinueCrawl,
                grab.startPressed, sneak.pressed || toggles.isSneakToggled(), onGround);
        boolean canCrawl = !player.isSwimming()
                && player.getFluidHeight(FluidTags.WATER) < CrawlLogic.MAX_WATER_DEPTH
                && player.fallDistance < config.fallDistanceMinimum.get()
                && !player.isPassenger() && !player.isSleeping() && !player.isFallFlying();
        wasCrawling = crawling;
        state.crawling = canCrawl && (wantCrawl || mustCrawl);

        // Later moves (climbing, sliding, ...) are decided here, before sneaking and sprinting.

        boolean wouldWantSneak = SpeedLogic.wouldWantSneak(config.sneakToggle.get(), toggles.isSneakToggled(),
                sneak.pressed, sneak.startPressed, wantCrawl, mustCrawl, config.crawl.get(), grab.pressed, smartFlying);
        boolean wantSneak = config.sneak.get() && wouldWantSneak;
        boolean wantSprint = SpeedLogic.wantSprint(config.sprint.get(), sprint.pressed, forwardPressed, disabled);

        if (!onGround && state.fast) {
            sprintJump = true;
        }
        if (onGround || smartFlying || player.isInLava()) {
            sprintJump = false;
        }

        boolean wasGroundSprinting = groundSprinting;
        groundSprinting = SpeedLogic.groundSprinting(wantSprint, wantSneak, player.isOnFire(), player.isUsingItem(),
                config.usageSprint.get(), collidedHorizontallyTicks, onGround);
        state.fast = groundSprinting;
        if (groundSprinting && !wasGroundSprinting) {
            wasRunningWhenSprintStarted = player.isSprinting();
            player.setSprinting(SpeedLogic.standupSprintingOrRunning(state.fast, player.isSprinting(), onGround,
                    false, state.crawling));
        } else if (wasGroundSprinting && !groundSprinting) {
            player.setSprinting(wasRunningWhenSprintStarted);
        }

        wouldSneak = wouldWantSneak && !wantSprint;
        boolean wasSlow = state.slow;
        state.slow = wantSneak && wouldSneak;
        Vec3 motion = player.getDeltaMovement();
        standing = motion.x * motion.x + motion.z * motion.z < STANDING_SPEED_SQUARE;

        boolean canAngleJump = !player.isSleeping() && onGround && !state.crawling;
        boolean canSideJump = config.angleJumpSide.get() && canAngleJump;
        boolean canBackJump = config.angleJumpBack.get() && canAngleJump && !forwardPressed
                && !SpeedLogic.standupSprintingOrRunning(state.fast, player.isSprinting(), onGround, false,
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
     * Adjusts the movement input vanilla has just set. Like the original, vanilla's input scaling (sneaking,
     * item usage) is dropped for the input's signs, and {@link #landSpeedFactor} applies the speed instead.
     */
    void applyInput(SmartMovingClientConfig config) {
        player.xxa = Math.signum(player.xxa);
        player.zza = Math.signum(player.zza);
        player.setJumping(jumpInput && !state.crawling
                && (!config.jumpCharge.get() || !wouldSneak || !player.onGround() || !standing)
                && !blockJumpTillButtonRelease);
        if (isRunning() && !config.run.get()) {
            player.setSprinting(false);
        }
        if (state.crawling) {
            player.setSprinting(false);
        }
    }

    /** Vanilla is about to jump from the ground; Smart Moving's jump replaces it in {@link #handleJumping}. */
    void avoidJump() {
        jumpAvoided = true;
    }

    /**
     * Before vanilla moves the player ({@code handleJumping}): the normal jump vanilla asked for, charging and
     * releasing a charged jump, and side and back jumps. Head jumps join in step 9, jumps in water in step 14.
     */
    void handleJumping(SmartMovingClientConfig config) {
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

        if (jump && !blockJumpTillButtonRelease && !jumpCharging) {
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
        JumpEngine.Motion result = JumpEngine.motion(type, horizontalFactor, verticalFactor, chargeFactor, 1,
                maxHorizontalMotion, jumpMotionX, jumpMotionZ, motion.x, motion.z, angle);
        boolean vertical = !Double.isNaN(result.y());
        player.setDeltaMovement(result.x(), vertical ? result.y() : motion.y, result.z());
        if (vertical) {
            sprintJump = state.fast;
        }
        player.hasImpulse = true;
        player.setOnGround(false);
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

    /** The multiplier on vanilla's walking speed on land and in the air (the original's {@code getSpeedFactor}). */
    float landSpeedFactor(SmartMovingClientConfig config) {
        return SpeedLogic.landSpeedFactor(config.speedFactor.get(), itemFactor(config),
                state.crawling, config.crawlFactor.get(), state.slow, sneakFactor(config),
                state.fast, config.sprintFactor.get(), config.run.get() && isRunning(), config.runFactor.get(),
                player.isSprinting())
                * SpeedLogic.jumpFactor(player.onGround(), jumpInput, state.fast, config.sprintJump.get(),
                config.sprintJumpVerticalFactor.get(), config.jumpControlFactor.get());
    }

    /**
     * The player's speed relative to plain walking, which scales the cap on jump boosts ({@code getSpeedFactor()}):
     * the global factor and the movement speed attribute without vanilla's sprint bonus.
     */
    private float speedFactor(SmartMovingClientConfig config) {
        float speed = config.speedFactor.get() * movementSpeed() * 10;
        return player.isSprinting() ? speed / SpeedLogic.VANILLA_SPRINT_FACTOR : speed;
    }

    /** At the end of the tick (the original's {@code afterOnUpdate}): the wall counter and the perspective. */
    void afterTick(SmartMovingClientConfig config) {
        collidedHorizontallyTicks = player.horizontalCollision ? collidedHorizontallyTicks + 1 : 0;

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
        return player.level().noCollision(player,
                player.getDimensions(pose).makeBoundingBox(player.position()).deflate(1.0E-7));
    }
}
