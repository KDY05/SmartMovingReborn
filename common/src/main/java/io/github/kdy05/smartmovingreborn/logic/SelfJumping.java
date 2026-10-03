package io.github.kdy05.smartmovingreborn.logic;

import io.github.kdy05.smartmovingreborn.client.SmartMovingClient;
import io.github.kdy05.smartmovingreborn.config.SmartMovingClientConfig;
import io.github.kdy05.smartmovingreborn.input.Button;
import io.github.kdy05.smartmovingreborn.logic.jump.AngleJumpInput;
import io.github.kdy05.smartmovingreborn.logic.jump.JumpEngine;
import io.github.kdy05.smartmovingreborn.logic.jump.JumpSpeed;
import io.github.kdy05.smartmovingreborn.logic.jump.JumpType;
import io.github.kdy05.smartmovingreborn.logic.jump.WallJump;
import io.github.kdy05.smartmovingreborn.logic.jump.WallJumpInput;
import io.github.kdy05.smartmovingreborn.mixin.common.EntityAccessor;
import io.github.kdy05.smartmovingreborn.render.SmartMovingRender;
import io.github.kdy05.smartmovingreborn.state.MovingState;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The own player's jumps ({@code handleJumping}, {@code tryJump}, {@code handleWallJumping} and the jump input of
 * {@code updateEntityActionState}): normal, charged, head, side and back jumps, wall jumps and the jumps off
 * climbing holds. The calculations are {@code logic/jump}'s; this class measures the player and keeps the jump
 * state. It reads the rest of the player's moves from the {@link SelfMoving} that owns it.
 */
final class SelfJumping {
    private final SelfMoving moving;
    private final Player player;
    private final MovingState state;
    private final AngleJumpInput angleJumps = new AngleJumpInput();
    private final WallJumpInput wallJumps = new WallJumpInput();
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
    /** Touched a wall at the start of the tick ({@code wasCollidedHorizontally}). */
    private boolean wasCollidedHorizontally;
    /** The direction of the wall the last move ran into while wanting to wall jump, NaN for none. */
    private float horizontalCollisionAngle;
    /** Where the current move started and how far it was asked to go, NaN while not measuring. */
    private double moveStartX;
    private double moveStartZ;
    private double moveX;
    private double moveZ;

    SelfJumping(SelfMoving moving) {
        this.moving = moving;
        this.player = moving.player;
        this.state = moving.state;
    }

    void reset() {
        state.angleJumpType = 0;
        state.wallJumping = false;
        jumpAvoided = false;
        blockJumpTillButtonRelease = false;
        jumpCharge = 0;
        headJumpCharge = 0;
        jumpMotionX = 0;
        jumpMotionZ = 0;
        wasCollidedHorizontally = false;
        horizontalCollisionAngle = Float.NaN;
        moveStartX = Double.NaN;
        angleJumps.reset();
        wallJumps.reset();
    }

    /** At the start of the tick ({@code beforeOnUpdate}). */
    void beforeTick() {
        wasCollidedHorizontally = player.horizontalCollision;
    }

    /** At the start of the action state update: vanilla has not tried to jump yet this tick. */
    void startActionState() {
        jumpAvoided = false;
    }

    /**
     * The jump input of {@code updateEntityActionState}: the wall jump trigger (2689-2723) and the side and back
     * jump double taps (2724-2799).
     *
     * @param onGround on the ground at the start of the action state update, which crawl climbing's moves may
     *                 have changed since
     */
    void updateInput(Button jump, Button left, Button right, Button back, boolean forwardPressed, boolean flying,
                     boolean onGround, SmartMovingClientConfig config) {
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
    }

    /** Vanilla is about to jump from the ground; Smart Moving's jump replaces it in {@link #handleJumping}. */
    void avoidJump() {
        jumpAvoided = true;
    }

    /** Whether a cancelled charge keeps the jump key from jumping until it is released. */
    boolean blocked() {
        return blockJumpTillButtonRelease;
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
     * Before vanilla moves the player ({@code handleJumping}): the normal jump vanilla asked for, charging and
     * releasing a charged jump or head jump, and side and back jumps; none while swimming or diving. The jump
     * while dipping joins in step 14-2.
     */
    void handleJumping(SmartMovingClientConfig config) {
        boolean jumpInput = moving.jumpInput();
        if (blockJumpTillButtonRelease && !jumpInput) {
            blockJumpTillButtonRelease = false;
        }
        if (state.swimming || state.diving) {
            return;
        }
        boolean onGround = player.onGround();
        boolean jump = jumpAvoided && onGround;
        Vec3 motion = player.getDeltaMovement();
        jumpMotionX = motion.x;
        jumpMotionZ = motion.z;

        boolean jumpCharging = false;
        if (config.jumpCharge.get()) {
            boolean jumpChargingPossible = onGround && moving.standing();
            jumpCharging = jumpChargingPossible && moving.wouldSneak();
            boolean cancelOnSneakRelease = config.jumpChargeCancelOnSneakRelease.get();
            if (jumpChargingPossible && (!cancelOnSneakRelease || moving.wouldSneak())) {
                if (!jumpInput || !cancelOnSneakRelease && !moving.wouldSneak()) {
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
            headJumpCharging = moving.grabPressed()
                    && (moving.groundSprinting() || moving.sprintJump() || moving.isRunning() && onGround)
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
    boolean tryJump(JumpType type, float angle, SmartMovingClientConfig config) {
        JumpSpeed speed = JumpSpeed.of(moving.standing(), state.slow, moving.isRunning(), state.fast,
                !Float.isNaN(angle));
        if (!JumpEngine.enabled(config, speed, type)) {
            return false;
        }
        MobEffectInstance jumpBoost = player.getEffect(MobEffects.JUMP);
        float boostFactor = JumpEngine.jumpBoostFactor(jumpBoost == null ? -1 : jumpBoost.getAmplifier());
        float horizontalFactor = JumpEngine.horizontalFactor(config, speed, type) * boostFactor;
        float verticalFactor = JumpEngine.verticalFactor(config, speed, type) * boostFactor;
        float chargeFactor = type == JumpType.CHARGE ? JumpEngine.chargeFactor(config, jumpCharge) : 1;
        double maxHorizontalMotion = horizontalFactor > 1 && !player.horizontalCollision
                ? JumpEngine.maxHorizontalMotion(config, speed, player.isInWater()) * moving.speedFactor(config)
                : Double.NaN;

        Vec3 motion = player.getDeltaMovement();
        float headJumpFactor = type.base().head() ? JumpEngine.headJumpFactor(config, headJumpCharge) : 1;
        JumpEngine.Motion result = JumpEngine.motion(type, horizontalFactor, verticalFactor, chargeFactor,
                headJumpFactor, maxHorizontalMotion, jumpMotionX, jumpMotionZ, motion.x, motion.z, angle);
        boolean vertical = !Double.isNaN(result.y());
        player.setDeltaMovement(result.x(), vertical ? result.y() : motion.y, result.z());
        if (vertical) {
            moving.setSprintJump(state.fast);
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
            if (moving.fits(Pose.SWIMMING, 1)) {
                moving.lift.lift(BoxLift.Reason.HEAD_JUMP);
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
    boolean climbJump(JumpType type, float angle, SmartMovingClientConfig config) {
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

    /**
     * Jumps off the wall this tick's movement ran into, if the player wants to and has not fallen too far: a
     * head jump with grab held. Like the original, the view turns to the jump direction and the body faces it
     * at once.
     */
    void handleWallJumping(SmartMovingClientConfig config) {
        if (!wallJumps.want() || Float.isNaN(horizontalCollisionAngle)) {
            return;
        }
        boolean head = moving.grabPressed();
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
}
