package io.github.kdy05.smartmovingreborn.logic.crawl;

import io.github.kdy05.smartmovingreborn.config.SmartMovingClientConfig;
import io.github.kdy05.smartmovingreborn.input.Button;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;

/**
 * Crawling of the client's own player, ported from the original's {@code updateEntityActionState}
 * ({@code SmartMovingSelf} 2311-2360 for the decision, 2805-2895 for the toggle).
 * <p>
 * The original shrank the bounding box from the top ({@code heightOffset}) and moved the player down or up
 * to match. Here the box follows the pose from the feet up, so no position correction is needed: crawling
 * uses vanilla's crawling pose, {@code Pose.SWIMMING}.
 */
public final class CrawlLogic {
    /** Water deeper than this (in blocks, measured from the feet) prevents crawling, like the original's dipping limit. */
    private static final double MAX_WATER_DEPTH = 0.65;

    private static boolean crawling;
    private static boolean wasCrawling;
    private static boolean crawlToggled;
    private static boolean ignoreNextSneakRelease;

    private CrawlLogic() {
    }

    public static boolean isCrawling() {
        return crawling;
    }

    public static void reset() {
        crawling = false;
        wasCrawling = false;
        crawlToggled = false;
        ignoreNextSneakRelease = false;
    }

    /** Once per tick, before vanilla turns the input into movement. */
    public static void update(Player player, Button sneak, Button grab, Button jump, SmartMovingClientConfig config) {
        boolean flying = player.getAbilities().flying;

        // The original only kept a crawling player down. Vanilla additionally forces its own crawl when neither
        // standing nor crouching fits; that case becomes Smart Moving crawling too.
        boolean mustCrawl = crawling
                ? !fits(player, Pose.STANDING)
                : !fits(player, Pose.STANDING) && !fits(player, Pose.CROUCHING);
        if (flying && (config.fly.get() || config.levitateSmall.get())) {
            mustCrawl = false;
        }

        boolean inputContinueCrawl = config.crawlToggle.get()
                ? crawlToggled
                : sneak.pressed || !config.climbFree.get() && grab.pressed;
        boolean wantCrawl = config.crawl.get() && !flying
                && (crawling && inputContinueCrawl || grab.startPressed && sneak.pressed && player.onGround());
        boolean canCrawl = !player.isSwimming()
                && player.getFluidHeight(FluidTags.WATER) < MAX_WATER_DEPTH
                && player.fallDistance < config.fallDistanceMinimum.get()
                && !player.isPassenger() && !player.isSleeping() && !player.isFallFlying();

        wasCrawling = crawling;
        crawling = canCrawl && (wantCrawl || mustCrawl);

        if (config.crawlToggle.get()) {
            updateToggle(sneak, jump);
        }
        if (sneak.stopPressed) {
            ignoreNextSneakRelease = false;
        }
    }

    /** Toggled crawling ends when sneak (pressed after crawling started) or jump is released. */
    private static void updateToggle(Button sneak, Button jump) {
        boolean stopByInput = crawling && (jump.stopPressed || sneak.stopPressed && !ignoreNextSneakRelease);
        if (crawling && !wasCrawling) {
            crawlToggled = true;
            ignoreNextSneakRelease = sneak.pressed;
        }
        if (!crawling || stopByInput) {
            crawlToggled = false;
        }
    }

    /**
     * Replaces the movement input while crawling: the original dropped vanilla's input scaling (for example
     * sneaking's 0.3) and applied the crawl factor to the speed instead. Jumping and sprinting are off.
     */
    public static void applyInput(Player player, SmartMovingClientConfig config) {
        float strafe = Math.signum(player.xxa);
        float forward = Math.signum(player.zza);
        float scale = config.crawlFactor.get() / Math.max(1, Mth.sqrt(strafe * strafe + forward * forward));
        player.xxa = strafe * scale;
        player.zza = forward * scale;
        player.setJumping(false);
        player.setSprinting(false);
    }

    /** Whether {@code pose} fits at the player's position, like vanilla's {@code canEnterPose}. */
    private static boolean fits(Player player, Pose pose) {
        return player.level().noCollision(player,
                player.getDimensions(pose).makeBoundingBox(player.position()).deflate(1.0E-7));
    }
}
