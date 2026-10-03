package io.github.kdy05.smartmovingreborn.render;

import io.github.kdy05.smartmovingreborn.config.SmartMovingConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The splashes a swimming player throws up at the surface ({@code SmartMoving.spawnParticles}), on the client for
 * the own player and for other players alike. More speed spawns more, at {@code move.swim.particle.period.factor}.
 * Swimming in lava is out of scope, so there are no lava particles.
 */
public final class SwimParticles {
    /** The squared speed not yet turned into particles, per player. */
    private static final Map<Player, Float> PENDING = new WeakHashMap<>();

    private SwimParticles() {
    }

    /** Called once per tick while {@code player} swims, with its horizontal motion. */
    public static void spawn(Player player, double motionX, double motionZ, SmartMovingConfig config) {
        float period = config.swimParticlePeriodFactor.get() * 0.01f;
        float pending = PENDING.getOrDefault(player, 0f) + (float) (motionX * motionX + motionZ * motionZ);
        if (period > 0) {
            double y = Mth.floor(player.getBoundingBox().minY) + 1.0;
            RandomSource random = player.getRandom();
            for (; pending > period; pending -= period) {
                Particle splash = Minecraft.getInstance().particleEngine.createParticle(ParticleTypes.SPLASH,
                        player.getX() + offset(random, player), y, player.getZ() + offset(random, player), 0, 0, 0);
                if (splash != null) {
                    splash.setParticleSpeed(0, 0.2, 0);
                }
            }
        }
        PENDING.put(player, pending);
    }

    private static double offset(RandomSource random, Player player) {
        return (random.nextFloat() - 0.5) * 2 * player.getBbWidth();
    }
}
