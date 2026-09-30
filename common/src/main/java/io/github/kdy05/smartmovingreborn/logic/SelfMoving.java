package io.github.kdy05.smartmovingreborn.logic;

import io.github.kdy05.smartmovingreborn.config.SmartMovingClientConfig;
import io.github.kdy05.smartmovingreborn.input.Button;
import io.github.kdy05.smartmovingreborn.logic.crawl.CrawlLogic;
import io.github.kdy05.smartmovingreborn.state.MovingState;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;

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

    SelfMoving(Player player, MovingState state) {
        this.player = player;
        this.state = state;
        reset();
    }

    void reset() {
        state.crawling = false;
        wasCrawling = false;
        toggles.reset();
    }

    /** Once per tick, before vanilla turns the input into movement (the original's {@code updateEntityActionState}). */
    void updateActionState(Button sneak, Button grab, Button jump, SmartMovingClientConfig config) {
        boolean flying = player.getAbilities().flying;
        boolean crawling = state.crawling;

        boolean mustCrawl = CrawlLogic.mustCrawl(crawling, fits(Pose.STANDING), fits(Pose.CROUCHING), flying,
                config.fly.get() || config.levitateSmall.get());
        boolean inputContinueCrawl = CrawlLogic.inputContinueCrawl(config.crawlToggle.get(), toggles.isCrawlToggled(),
                sneak.pressed, config.climbFree.get(), grab.pressed);
        boolean wantCrawl = CrawlLogic.wantCrawl(config.crawl.get(), crawling, flying, inputContinueCrawl,
                grab.startPressed, sneak.pressed, player.onGround());
        boolean canCrawl = !player.isSwimming()
                && player.getFluidHeight(FluidTags.WATER) < CrawlLogic.MAX_WATER_DEPTH
                && player.fallDistance < config.fallDistanceMinimum.get()
                && !player.isPassenger() && !player.isSleeping() && !player.isFallFlying();
        wasCrawling = crawling;
        state.crawling = canCrawl && (wantCrawl || mustCrawl);

        // Later moves (climbing, sliding, ...) are decided here, before the toggles.

        if (config.crawlToggle.get()) {
            toggles.updateCrawl(state.crawling, wasCrawling, sneak, jump);
        }
        toggles.endTick(sneak);
    }

    /** Adjusts the movement input vanilla has just set; jumping and sprinting are off while crawling. */
    void applyInput(SmartMovingClientConfig config) {
        if (!state.crawling) {
            return;
        }
        float strafe = Math.signum(player.xxa);
        float forward = Math.signum(player.zza);
        float scale = CrawlLogic.inputScale(strafe, forward, config.crawlFactor.get());
        player.xxa = strafe * scale;
        player.zza = forward * scale;
        player.setJumping(false);
        player.setSprinting(false);
    }

    /** Whether {@code pose} fits at the player's position, like vanilla's {@code canEnterPose}. */
    private boolean fits(Pose pose) {
        return player.level().noCollision(player,
                player.getDimensions(pose).makeBoundingBox(player.position()).deflate(1.0E-7));
    }
}
