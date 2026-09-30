package io.github.kdy05.smartmovingreborn.logic;

import io.github.kdy05.smartmovingreborn.config.SmartMovingClientConfig;
import io.github.kdy05.smartmovingreborn.input.Button;
import io.github.kdy05.smartmovingreborn.logic.crawl.CrawlLogic;
import io.github.kdy05.smartmovingreborn.state.MovingState;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * The client's own player's Smart Moving state and its per-tick update, like the original's
 * {@code SmartMovingSelf}. The feature classes ({@code logic/crawl}, ...) only decide; this class measures the
 * player, calls them in the original's order and keeps the results. The shared flags live in {@link #state},
 * which is also what gets sent to the server and read by rendering.
 */
public final class SelfMoving {
    final Player player;
    final MovingState state;
    private final ToggleState toggles = new ToggleState();
    private boolean wasCrawling;
    private boolean groundSprinting;
    private boolean wasRunningWhenSprintStarted;
    /** In the air after sprinting, until back on the ground. */
    private boolean sprintJump;
    private int collidedHorizontallyTicks;
    /** The movement speed the field of view follows, fading towards the current one; negative before the first tick. */
    private float fadingPerspectiveSpeed;

    SelfMoving(Player player, MovingState state) {
        this.player = player;
        this.state = state;
        reset();
    }

    void reset() {
        state.crawling = false;
        state.slow = false;
        state.fast = false;
        wasCrawling = false;
        groundSprinting = false;
        wasRunningWhenSprintStarted = false;
        sprintJump = false;
        collidedHorizontallyTicks = 0;
        fadingPerspectiveSpeed = -1;
        toggles.reset();
    }

    /** Once per tick, before vanilla turns the input into movement (the original's {@code updateEntityActionState}). */
    void updateActionState(Button sneak, Button grab, Button jump, Button sprint, boolean forwardPressed,
                           SmartMovingClientConfig config) {
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

        boolean wasSlow = state.slow;
        state.slow = wantSneak && !wantSprint;

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
        if (isRunning() && !config.run.get()) {
            player.setSprinting(false);
        }
        if (state.crawling) {
            player.setJumping(false);
            player.setSprinting(false);
        }
    }

    /** The multiplier on vanilla's walking speed on land and in the air (the original's {@code getSpeedFactor}). */
    float landSpeedFactor(SmartMovingClientConfig config) {
        return SpeedLogic.landSpeedFactor(config.speedFactor.get(), itemFactor(config),
                state.crawling, config.crawlFactor.get(), state.slow, sneakFactor(config),
                state.fast, config.sprintFactor.get(), config.run.get() && isRunning(), config.runFactor.get(),
                player.isSprinting());
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
