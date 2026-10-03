package io.github.kdy05.smartmovingreborn.logic;

import io.github.kdy05.smartmovingreborn.client.SmartMovingClient;
import net.minecraft.world.entity.player.Player;

/**
 * Moving the own player's box without collisions while the model stays put, which stands in for the original's
 * box changes ({@code setHeightOffset}): the original could raise the bottom of the box, while 1.20.1 always
 * builds the box from the feet up. The previous position and the camera's eye height move along, so that the
 * model's interpolation and the view stay where they were.
 * <p>
 * A small box raised a block this way is drawn a block below its position (see
 * {@code MovingController.smallPose}); {@link #reason} tells why it was raised and so how it comes down again.
 */
final class BoxLift {
    /** Why the box is a block up. */
    enum Reason {
        /** A head jump raised the bottom of the box ({@code setHeightOffset(-1)} in {@code tryJump}). */
        HEAD_JUMP,
        /** Climb crawling shrank a standing box from below ({@code setHeightOffset(-1)} on climb crawling). */
        CLIMB_CRAWL,
        /**
         * Swimming or diving raised the bottom of the box ({@code setHeightOffset(-1)} in {@code handleSwimming}).
         * A crawling box that turns into a swimming one is already where the original's was, so it only takes
         * this reason without moving.
         */
        SWIM
    }

    private final Player player;
    private Reason reason;

    BoxLift(Player player) {
        this.player = player;
    }

    void reset() {
        reason = null;
    }

    /** Moves the player a block up for {@code reason}. */
    void lift(Reason reason) {
        shift(1);
        this.reason = reason;
    }

    /** Whether the box is a block up for {@code reason}. */
    boolean is(Reason reason) {
        return this.reason == reason;
    }

    /** Notes that the box, small and a block up already, now stays so for {@code reason}. */
    void retag(Reason reason) {
        this.reason = reason;
    }

    /** Forgets that the box is up for {@code reason}, leaving the player where it is. */
    void clear(Reason reason) {
        if (this.reason == reason) {
            this.reason = null;
        }
    }

    /** Moves the player by {@code dy} without collisions, the previous position and the camera along. */
    void shift(double dy) {
        player.setPos(player.getX(), player.getY() + dy, player.getZ());
        player.yo += dy;
        player.yOld += dy;
        SmartMovingClient.offsetCameraEyeHeight(player, (float) dy);
    }
}
