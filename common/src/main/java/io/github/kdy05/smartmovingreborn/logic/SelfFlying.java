package io.github.kdy05.smartmovingreborn.logic;

import io.github.kdy05.smartmovingreborn.client.SmartMovingClient;
import io.github.kdy05.smartmovingreborn.config.SmartMovingClientConfig;
import io.github.kdy05.smartmovingreborn.logic.fly.FlyLogic;
import io.github.kdy05.smartmovingreborn.logic.swim.SwimLogic;
import io.github.kdy05.smartmovingreborn.state.MovingState;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The own player's flying: Smart Moving's own flying ({@code move.fly}, the original's {@code isFlying}) and the
 * small box for vanilla's ({@code move.levitate.small}), how a flight lands and stands up, and flying on after
 * touching the ground ({@code move.fly.ground.collide}).
 */
final class SelfFlying {
    /** The height of the original's small box, from whose top the room above is measured. */
    private static final double SMALL_HEIGHT = 0.8;

    private final SelfMoving moving;
    private final Player player;
    private final MovingState state;
    /** Flying Smart Moving's way ({@code isFlying}). */
    private boolean smartFlying;
    /** Flying vanilla's way without Smart Moving's in the last tick ({@code wasLevitating}). */
    private boolean wasLevitating;
    /** Whether vanilla flew at the start of the tick ({@code wasCapabilitiesIsFlying}). */
    private boolean wasAbilityFlying;

    SelfFlying(SelfMoving moving) {
        this.moving = moving;
        this.player = moving.player;
        this.state = moving.state;
    }

    void reset() {
        smartFlying = false;
        wasLevitating = false;
        wasAbilityFlying = false;
    }

    /** Flying Smart Moving's way ({@code isFlying}). */
    boolean smartFlying() {
        return smartFlying;
    }

    /** Whether the box is a flyer's, which keeps it small. */
    boolean flyingBox() {
        return moving.lift.is(BoxLift.Reason.FLY);
    }

    /** Whether Smart Moving's flying is on; a spectator always flies vanilla's way. */
    boolean flyEnabled(SmartMovingClientConfig config) {
        return config.fly.get() && !player.isSpectator();
    }

    /**
     * The flying part of {@code updateEntityActionState} (2395-2432): Smart Moving's flying starts in a small box
     * a block up, as does vanilla's with {@code move.levitate.small}; either ending stands up. A Smart Moving flyer
     * hovering slowly close to the ground lands.
     *
     * @param horizontalSpeedSquare the horizontal speed squared at the start of the tick
     * @param motionY the vertical motion at the start of the tick
     * @param wasHeadJumping whether the player head jumped in the last tick, which lies down sliding on landing
     */
    void updateActionState(boolean sneak, boolean grab, double horizontalSpeedSquare, double motionY,
                           boolean wasHeadJumping, SmartMovingClientConfig config) {
        boolean flying = player.getAbilities().flying;
        boolean levitating = flying && !smartFlying;
        boolean flyEnabled = flyEnabled(config);
        boolean restoring = false;
        boolean wasSmartFlying = smartFlying;
        smartFlying = flyEnabled && flying && !state.swimming && !state.diving;
        if (smartFlying && !wasSmartFlying) {
            toFlyingBox();
        } else if (!smartFlying && wasSmartFlying) {
            restoring = true;
        }
        if (!flyEnabled && config.levitateSmall.get() && !player.isSpectator()) {
            if (levitating && !wasLevitating) {
                toFlyingBox();
            } else if (!levitating && wasLevitating) {
                restoring = true;
            }
        }
        wasLevitating = levitating;

        boolean tryLanding = FlyLogic.tryLanding(smartFlying, config.flyCloseToGround.get(), horizontalSpeedSquare,
                motionY);
        if (restoring || tryLanding) {
            standUpIfPossible(tryLanding, restoring, sneak, grab, wasHeadJumping, config);
        }
    }

    /**
     * The flyer's box: small and a block up, like a swimmer's. The small pose applies at once and the server
     * learns of it before the movement. A box already small, from crawling or a head jump, stays where it is.
     */
    private void toFlyingBox() {
        if (flyingBox()) {
            return;
        }
        if (player.getBbHeight() < 1) {
            moving.lift.retag(BoxLift.Reason.FLY);
        } else {
            player.setPose(Pose.SWIMMING);
            if (moving.fits(Pose.SWIMMING, 1)) {
                moving.lift.lift(BoxLift.Reason.FLY);
            } else {
                moving.lift.retag(BoxLift.Reason.FLY);
            }
        }
        SmartMovingClient.sendStateNow(player);
    }

    /**
     * {@code standupIfPossible} (2161-2183) for a flyer's box. Landing ends vanilla's flying when standing fits
     * close to the ground. Ending a flight, the box grows back down in the air, or on the ground the player
     * stands up or lies down. The original raised its box's bottom a block; this port's box bottom is where the
     * original's was, so the ground and the ceiling are measured the same way.
     */
    private void standUpIfPossible(boolean tryLanding, boolean restoring, boolean sneak, boolean grab,
                                   boolean wasHeadJumping, SmartMovingClientConfig config) {
        if (!flyingBox()) {
            return;
        }
        double bottom = player.getBoundingBox().minY;
        double gapUnder = bottom - moving.maxSolidBetween(bottom - FlyLogic.GAP_SEARCH, bottom);
        boolean groundClose = FlyLogic.groundClose(gapUnder);
        double top = bottom + SMALL_HEIGHT;
        double gapOver = groundClose ? moving.minSolidBetween(top, top + FlyLogic.GAP_SEARCH) - top : -1;
        boolean standUpPossible = FlyLogic.standUpPossible(gapUnder, gapOver);
        if (tryLanding && groundClose && standUpPossible) {
            smartFlying = false;
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
            restoring = true;
        }
        switch (FlyLogic.restore(restoring, groundClose, standUpPossible, sneak, grab)) {
            case RESET -> {
                moving.lift.clear(BoxLift.Reason.FLY);
                moving.standUpFromSmall(-1);
            }
            case LIE_DOWN -> {
                moving.lift.clear(BoxLift.Reason.FLY);
                player.move(MoverType.SELF, new Vec3(0, -gapUnder, 0));
                if (FlyLogic.slideWhenLyingDown(config.slide.get(), grab, wasHeadJumping)) {
                    state.sliding = true;
                } else {
                    moving.toCrawling(config);
                }
            }
            case STAND_UP -> {
                moving.lift.clear(BoxLift.Reason.FLY);
                moving.standUpFromSmall(-gapUnder);
                state.crawling = false;
                state.headJumping = false;
            }
            case NONE -> {
            }
        }
    }

    /**
     * Instead of vanilla's {@code travel} when true ({@code handleAlternativeFlying} 634-659): Smart Moving's
     * flying, towards where the player looks, up with jump and down with sneak, damped alike on every axis and
     * without gravity. Vanilla's own lift for jump and sneak, added just before, is taken back.
     */
    boolean travel(Vec3 input, SmartMovingClientConfig config) {
        if (!smartFlying) {
            return false;
        }
        Abilities abilities = player.getAbilities();
        boolean sneak = SmartMovingClient.isSneakInput(player);
        boolean jump = SmartMovingClient.isJumpPressed(player);
        Vec3 motion = player.getDeltaMovement();
        if (SmartMovingClient.isControlledCamera(player)) {
            int vertical = (jump ? 1 : 0) - (sneak ? 1 : 0);
            motion = motion.subtract(0, vertical * abilities.getFlyingSpeed() * 3.0f, 0);
        }
        float speed = abilities.getFlyingSpeed() * moving.ownSpeedFactor(config) * config.flySpeedFactor.get();
        double[] added = SwimLogic.moveFlying(FlyLogic.upward(sneak, jump), (float) input.x, (float) input.z, speed,
                player.getYRot(), player.getXRot(), config.flyControlVertical.get());
        player.setDeltaMovement(motion.add(added[0], added[1], added[2]));
        player.move(MoverType.SELF, player.getDeltaMovement());
        player.setDeltaMovement(player.getDeltaMovement().scale(FlyLogic.DAMPING));
        // What vanilla's travel does for any flyer.
        player.resetFallDistance();
        player.stopFallFlying();
        moving.finishTravel();
        return true;
    }

    /**
     * Whether to draw the flying pose ({@code doFlyingAnimation}): flying vanilla's way or Smart Moving's, with
     * Smart Moving's flying or {@code move.levitate.animation} on; not for a spectator.
     */
    boolean animation(SmartMovingClientConfig config) {
        return player.getAbilities().flying && (flyEnabled(config)
                || config.levitateAnimation.get() && !player.isSpectator());
    }

    /** At the start of the tick ({@code beforeOnLivingUpdate}). */
    void beforeTick() {
        wasAbilityFlying = player.getAbilities().flying;
    }

    /**
     * At the end of the tick ({@code afterOnLivingUpdate}): with {@code move.fly.ground.collide}, flying goes on
     * where vanilla ended it for touching the ground.
     */
    void afterTick(boolean sneak, boolean grab, SmartMovingClientConfig config) {
        Abilities abilities = player.getAbilities();
        if (FlyLogic.flyAgain(config.flyWhileOnGround.get(), sneak, grab, wasAbilityFlying, abilities.flying,
                player.onGround())) {
            player.bob = 0;
            player.oBob = 0;
            abilities.flying = true;
            player.onUpdateAbilities();
        }
    }
}
