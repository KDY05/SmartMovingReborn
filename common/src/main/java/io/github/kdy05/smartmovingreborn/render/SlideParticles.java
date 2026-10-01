package io.github.kdy05.smartmovingreborn.render;

import io.github.kdy05.smartmovingreborn.config.SmartMovingConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The block particles a sliding player kicks up ({@code SmartMoving.spawnParticles}), on the client for the own
 * player and for other players alike. More speed spawns more, at {@code move.slide.particle.period.factor}.
 */
public final class SlideParticles {
    /** The squared speed not yet turned into particles, per player. */
    private static final Map<Player, Float> PENDING = new WeakHashMap<>();

    private SlideParticles() {
    }

    /** Called once per tick while {@code player} slides, with its horizontal motion. */
    public static void spawn(Player player, double motionX, double motionZ, SmartMovingConfig config) {
        BlockPos below = BlockPos.containing(player.getX(), player.getBoundingBox().minY - 0.1, player.getZ());
        BlockState block = player.level().getBlockState(below);
        if (block.getRenderShape() == RenderShape.INVISIBLE) {
            return;
        }
        float period = config.slideParticlePeriodFactor.get() * 0.1f;
        float pending = PENDING.getOrDefault(player, 0f) + (float) (motionX * motionX + motionZ * motionZ);
        if (period > 0) {
            BlockParticleOption particle = new BlockParticleOption(ParticleTypes.BLOCK, block);
            RandomSource random = player.getRandom();
            double y = player.getBoundingBox().minY + 0.1;
            for (; pending > period; pending -= period) {
                player.level().addParticle(particle, player.getX() + offset(random, player),
                        y, player.getZ() + offset(random, player), -motionX * 4, 1.5, -motionZ * 4);
            }
        }
        PENDING.put(player, pending);
    }

    private static double offset(RandomSource random, Player player) {
        return (random.nextFloat() - 0.5) * 2 * player.getBbWidth();
    }
}
