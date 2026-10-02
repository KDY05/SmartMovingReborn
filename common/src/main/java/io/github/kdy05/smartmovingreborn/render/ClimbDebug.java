package io.github.kdy05.smartmovingreborn.render;

import io.github.kdy05.smartmovingreborn.config.SmartMovingClientConfig;
import io.github.kdy05.smartmovingreborn.state.MovingState;
import io.github.kdy05.smartmovingreborn.world.GrabDetector;
import io.github.kdy05.smartmovingreborn.world.LevelClimbTerrain;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.entity.player.Player;
import org.joml.Vector3f;

import java.util.List;

/**
 * {@code move.debug.climb}: searches the free climbing holds around the own player every tick, whether climbing
 * or not, and shows them. Each hold is a dust particle on the edge it holds: green to only hang on, yellow with
 * room to crawl over, white with room to stand up. Hand holds are large, feet holds small and a little closer
 * to the player.
 */
public final class ClimbDebug {
    private static final Vector3f HANG = new Vector3f(0.2f, 1, 0.2f);
    private static final Vector3f CRAWL = new Vector3f(1, 1, 0.2f);
    private static final Vector3f STAND = new Vector3f(1, 1, 1);
    /** Ticks between particle bursts; the particles live longer than that. */
    private static final int PERIOD = 4;

    private static GrabDetector.Result last;
    private static int ticks;

    private ClimbDebug() {
    }

    /** Forgets the last search, while Smart Moving is inactive. */
    public static void reset() {
        last = null;
    }

    /** Called once per client tick while Smart Moving is active. */
    public static void tick(Player player, MovingState state, SmartMovingClientConfig config) {
        if (!config.debugClimb.get()) {
            last = null;
            return;
        }
        GrabDetector detector = new GrabDetector(new LevelClimbTerrain(player.level(), player),
                new GrabDetector.Settings(config.isFreeBaseClimb(), config.climbFreeFence.get(),
                        config.climbFreeOrthogonalAngle.get(), config.climbFreeDiagonalAngle.get(),
                        GrabDetector.Settings.holdGap(config.climbFreeUpSpeedFactor.get(),
                                config.climbFreeDownSpeedFactor.get())));
        boolean show = ticks++ % PERIOD == 0;
        if (show) {
            detector.setHoldListener(hold -> spawn(player, hold));
        }
        last = detector.detect(player.getX(), player.getBoundingBox().minY, player.getZ(), player.getYRot(),
                false, false, state.crawling || state.sliding);
    }

    private static void spawn(Player player, GrabDetector.Hold hold) {
        Vector3f color = hold.gap() > 3 ? STAND : hold.gap() > 1 ? CRAWL : HANG;
        double toEdge = hold.hands() ? 0.5 : 0.4;
        player.level().addParticle(new DustParticleOptions(color, hold.hands() ? 1 : 0.6f),
                hold.blockX() + 0.5 + hold.direction().x * toEdge, hold.y(),
                hold.blockZ() + 0.5 + hold.direction().z * toEdge, 0, 0, 0);
    }

    /** Adds the last search's results to the F3 screen. */
    public static void appendDebugInfo(List<String> lines) {
        if (last == null) {
            return;
        }
        lines.add("");
        lines.add("[Smart Moving] climb: hands=" + last.hands() + " feet=" + last.feet()
                + (last.climbGap() ? " stand" : "") + (last.climbCrawlGap() ? " crawl" : "")
                + (last.handsGap().direction != null ? " to=" + last.handsGap().direction : ""));
    }
}
