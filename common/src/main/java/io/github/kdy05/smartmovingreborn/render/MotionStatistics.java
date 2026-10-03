package io.github.kdy05.smartmovingreborn.render;

import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Smart Render's {@code SmartStatistics} for the climbing and diving animations: like vanilla's walking animation, but for
 * the vertical movement and the whole movement. Each tick the speed eases towards four times the distance
 * moved, and the distance adds up the speed. Horizontal movement uses vanilla's walking animation, which is
 * the same for players. Client only.
 */
final class MotionStatistics {
    private static final Map<Player, MotionStatistics> STATISTICS = new WeakHashMap<>();

    private final Axis vertical = new Axis();
    private final Axis all = new Axis();
    /** Ticks in a row the player has been climb jumping. */
    private int climbJumpTicks;

    /** One direction's eased speed and total distance ({@code SmartStatisticsData}). */
    private static final class Axis {
        float previousSpeed;
        float speed;
        float total;

        void update(double distance) {
            previousSpeed = speed;
            speed += ((float) distance * 4 - speed) * 0.4f;
            total += speed;
        }

        float speed(float partialTicks) {
            return Math.min(1, previousSpeed + (speed - previousSpeed) * partialTicks);
        }

        float total(float partialTicks) {
            return total - speed * (1 - partialTicks);
        }
    }

    /** Once per client tick for every player, after it moved ({@code calculateAllStats}). */
    static void update(Player player, boolean climbJumping) {
        MotionStatistics statistics = STATISTICS.computeIfAbsent(player, p -> new MotionStatistics());
        statistics.climbJumpTicks = climbJumping ? statistics.climbJumpTicks + 1 : 0;
        double dx = player.getX() - player.xo;
        double dy = player.getY() - player.yo;
        double dz = player.getZ() - player.zo;
        statistics.vertical.update(Math.abs(dy));
        statistics.all.update(Math.sqrt(dx * dx + dy * dy + dz * dz));
    }

    static MotionStatistics of(Player player) {
        return STATISTICS.computeIfAbsent(player, p -> new MotionStatistics());
    }

    float verticalSpeed(float partialTicks) {
        return vertical.speed(partialTicks);
    }

    float verticalDistance(float partialTicks) {
        return vertical.total(partialTicks);
    }

    float distance(float partialTicks) {
        return all.total(partialTicks);
    }

    /** The eased whole speed ({@code currentSpeed}). */
    float speed(float partialTicks) {
        return all.speed(partialTicks);
    }

    /**
     * Whether to draw the climb jump: only once it lasts two ticks. Letting go of forward while climbing up
     * makes a single tick of it (the climbing speed falls below the rising motion), which the original drew as
     * a twitch of the arms. Not in the original.
     */
    boolean showsClimbJump() {
        return climbJumpTicks >= 2;
    }
}
