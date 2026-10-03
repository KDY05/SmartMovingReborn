package io.github.kdy05.smartmovingreborn.logic;

import io.github.kdy05.smartmovingreborn.client.SmartMovingClient;
import io.github.kdy05.smartmovingreborn.config.SmartMovingClientConfig;
import io.github.kdy05.smartmovingreborn.logic.swim.SwimLogic;
import io.github.kdy05.smartmovingreborn.logic.swim.SwimSpeed;
import io.github.kdy05.smartmovingreborn.mixin.common.EntityAccessor;
import io.github.kdy05.smartmovingreborn.render.SwimParticles;
import io.github.kdy05.smartmovingreborn.state.MovingState;
import io.github.kdy05.smartmovingreborn.world.ClimbOrientation;
import io.github.kdy05.smartmovingreborn.world.LevelClimbTerrain;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The own player's swimming, diving and dipping ({@code handleSwimming}, {@code fromSwimmingOrDiving}). The
 * decisions are {@link SwimLogic}'s; this class measures the water around the player and moves it.
 * <p>
 * The original replaced vanilla's whole movement. Here vanilla's {@code travel} is cancelled only for the ticks
 * Smart Moving moves the player in water, and for the ticks the original moved the player on land although it
 * was in water (climbing out of it, or crawling in shallow water), which vanilla's water branch cannot do; then a
 * copy of vanilla's land branch moves the player (user decision, 2026-10-03). Forge patches vanilla's water
 * branch, so hooking into it would differ between the loaders.
 * <p>
 * Heights are measured like the original's, which first reset the box to the standing one: a small box's bottom
 * is a block above the original's feet (the swimming box like the head jump's, see {@link BoxLift}, and the
 * crawling box drawn a block above the model).
 */
final class SelfSwimming {
    /** The original's standing box height, which all its water heights are measured with. */
    private static final double STANDING_HEIGHT = 1.8;
    /** The original's small box height. */
    private static final double SMALL_HEIGHT = 0.8;

    private final SelfMoving moving;
    private final Player player;
    private final MovingState state;
    /** The water's surface above the feet, measured in the last tick in water, -1 elsewhere ({@code dippingDepth}). */
    private float dippingDepth;
    /** Ticks in a row swimming or diving ({@code waterMovementTicks}). */
    private int waterMovementTicks;
    private boolean jumpingOutOfWater;
    /** Swimming or diving in water the player could stand in ({@code isShallowDiveOrSwim}). */
    private boolean shallowDiveOrSwim;
    /** Sneaking while swimming in shallow water counts as sneaking, not as diving down. */
    private boolean fakeShallowWaterSneaking;
    /** Crawling goes on without its input after a swim ended below water ({@code contextContinueCrawl}). */
    private boolean continueCrawl;
    /**
     * Stood up from a shallow swim with jump held: that press does not jump while dipping until it is released
     * ({@code isStillSwimmingJump}).
     */
    private boolean stillSwimmingJump;
    /** The distance swum since the last splash sound ({@code distanceSwom}). */
    private float distanceSwum;
    /** Swimming or diving when this tick's travel started ({@code wasShortInWater}). */
    private boolean wasShortInWater;
    /** This tick's travel was the land movement ({@code handleLand}). */
    private boolean landTick;
    /** The motion after vanilla turned the input into movement, before its jump in water. */
    private Vec3 motionBeforeJump;
    /** What vanilla's jump added this tick, undone while Smart Moving moves the player in water. */
    private Vec3 vanillaJump = Vec3.ZERO;
    /**
     * On the ground before this tick's jumps. The original's jumps left the ground state alone, while this port's
     * take the player off the ground at once (see {@code SelfJumping.tryJump}), so a dipping jump at the shore
     * would never start jumping out of the water.
     */
    private boolean onGroundBeforeJump;

    SelfSwimming(SelfMoving moving) {
        this.moving = moving;
        this.player = moving.player;
        this.state = moving.state;
    }

    void reset() {
        resetSwimming();
        waterMovementTicks = 0;
        continueCrawl = false;
        stillSwimmingJump = false;
        distanceSwum = 0;
        wasShortInWater = false;
        landTick = false;
        motionBeforeJump = null;
        vanillaJump = Vec3.ZERO;
    }

    /** {@code resetSwimming}. */
    private void resetSwimming() {
        dippingDepth = -1;
        state.dipping = false;
        state.swimming = false;
        state.diving = false;
        state.levitating = false;
        shallowDiveOrSwim = false;
        fakeShallowWaterSneaking = false;
        jumpingOutOfWater = false;
    }

    /** Crawling goes on without its input after a swim ended below water. */
    boolean continueCrawl() {
        return continueCrawl;
    }

    /** Crawling stopped, which ends crawling on from a swim. */
    void stopContinueCrawl() {
        continueCrawl = false;
    }

    /** Standing up from a shallow swim with jump held ({@code isStillSwimmingJump}). */
    boolean stillSwimmingJump() {
        return stillSwimmingJump;
    }

    /** The jump key was released. */
    void stopStillSwimmingJump() {
        stillSwimmingJump = false;
    }

    /** The water's surface above the feet, measured in the last tick in water, -1 elsewhere ({@code dippingDepth}). */
    float dippingDepth() {
        return dippingDepth;
    }

    /** Sneaking while swimming in shallow water counts as sneaking ({@code isFakeShallowWaterSneaking}). */
    boolean fakeShallowWaterSneaking() {
        return fakeShallowWaterSneaking;
    }

    /** Whether vanilla's swimming ({@code Entity.isSwimming}) gives way to Smart Moving's. */
    static boolean replacesVanilla(SmartMovingClientConfig config) {
        return config.swim.get() || config.dive.get();
    }

    /** After vanilla turned the input into movement, before its jump in water. */
    void afterServerAiStep() {
        motionBeforeJump = player.getDeltaMovement();
    }

    /** At the start of {@code travel}, before Smart Moving's jumps: notes what vanilla's jump added. */
    void beforeTravel() {
        vanillaJump = motionBeforeJump == null ? Vec3.ZERO : player.getDeltaMovement().subtract(motionBeforeJump);
        motionBeforeJump = null;
        onGroundBeforeJump = player.onGround();
    }

    /**
     * {@code travel} after the jumps ({@code superMoveEntityWithHeading} 190-201): swimming, then the land movement
     * where vanilla would move the player in water.
     *
     * @return whether the player has been moved and vanilla's {@code travel} is to be cancelled
     */
    boolean travel(Vec3 input, SmartMovingClientConfig config) {
        landTick = false;
        wasShortInWater = state.swimming || state.diving;
        boolean wasSwimming = state.swimming;
        boolean wasDiving = state.diving;
        boolean wasJumpingOutOfWater = jumpingOutOfWater;
        Vec3 start = player.position();
        boolean liquidClimbing = config.climbFree.get() && player.fallDistance <= 3 && moving.wantClimbUp()
                && player.horizontalCollision && !state.diving;
        // Smart Moving's flying goes on in water; vanilla's swims.
        Handled handled = moving.smartFlying() ? Handled.NOT
                : handleSwimming(input, moving.ownSpeedFactor(config), wasSwimming, wasDiving, liquidClimbing,
                wasJumpingOutOfWater, config);
        if (handled == Handled.MOVED) {
            moving.finishTravel(start);
            return true;
        }
        if (handled == Handled.STANDARD) {
            return false;
        }
        resetSwimming();
        if (moving.smartFlying()) {
            // Smart Moving's flying moves the player instead of the land movement.
            return false;
        }
        landTick = true;
        if (replacesVanilla(config) && !inWater() && !player.isInLava() && !moving.jumpedThisTick()) {
            // Vanilla still finds water around a box whose bottom is just under the surface, where 1.7.10 did not
            // and so did not jump in water: rising out of shallow water, that jump would carry the player higher.
            // Lava stays vanilla's, jump included, like the original's handleLava.
            player.setDeltaMovement(player.getDeltaMovement().subtract(vanillaJump));
        }
        if (!moving.grabPressed()) {
            endSwimming();
        }
        if (player.isInWater()) {
            landTravel(input);
            moving.finishTravel(start);
            return true;
        }
        return false;
    }

    /** After {@code travel}: a swim that ended with grab held ({@code landMotionPost}). */
    void afterTravel() {
        if (moving.grabPressed()) {
            if (landTick) {
                endSwimming();
            } else {
                fromSwimmingOrDiving(wasShortInWater);
            }
        }
    }

    /**
     * Ends swimming on a tick moved on land: back on land, and a swimming box left over (from a crawler that
     * became a swimmer, moved vanilla's way) stands up.
     */
    private void endSwimming() {
        fromSwimmingOrDiving(wasShortInWater);
        standUpFromSwimming();
    }

    private enum Handled {
        /** Smart Moving moved the player in water. */
        MOVED,
        /** Vanilla's water movement ({@code useStandard}). */
        STANDARD,
        /** Not in water, or left to the land movement. */
        NOT
    }

    /** {@code handleSwimming} 283-611. */
    private Handled handleSwimming(Vec3 input, float speedFactor, boolean wasSwimming, boolean wasDiving,
                                   boolean liquidClimbing, boolean wasJumpingOutOfWater,
                                   SmartMovingClientConfig config) {
        if (liquidClimbing || !inWater() && !(wasSwimming && inLiquid())) {
            return Handled.NOT;
        }
        if (!replacesVanilla(config)) {
            resetSwimming();
            standUpFromSwimming();
            stillSwimmingJump = false;
            if (state.crawling && small()) {
                standUpIfPossible(config);
            }
            return Handled.STANDARD;
        }

        boolean small = small();
        resetSwimming();
        AABB box = player.getBoundingBox();
        double feet = small ? box.minY - 1 : box.minY;
        double top = feet + STANDING_HEIGHT;
        int i = Mth.floor(player.getX());
        int j = Mth.floor(feet);
        int k = Mth.floor(player.getZ());
        double surface = maxLiquidBetween(top - STANDING_HEIGHT, top + 1.2);
        double ceiling = moving.minSolidBetween(top - STANDING_HEIGHT, top + 1.2);
        double realSurface = Math.min(surface, ceiling);
        double floorDepth = surface - moving.maxSolidBetween(surface - 2, surface);
        double realFloorDepth = surface - moving.maxSolidBetween(realSurface - 2, realSurface);
        double depth = surface - feet;

        boolean couldStandUp = depth >= 0 && floorDepth <= 1.5;
        boolean diveUp = moving.vanillaJumping();
        boolean sneak = moving.sneakInput();
        boolean diveDown = sneak && config.diveDownOnSneak.get();
        boolean wantShallowSwim = couldStandUp && (wasSwimming || wasDiving) && !tunnelAhead(i, j, k, config);
        boolean swimDown = SwimLogic.swimDown(sneak, config.swimDownOnSneak.get(), wasSwimming, wantShallowSwim);
        if (wasSwimming && wantShallowSwim && sneak && config.swimDownOnSneak.get()) {
            fakeShallowWaterSneaking = true;
        }
        // The original cancelled jumping and sneaking together while diving, and stood up a crawler in water, but
        // only after resetting the diving flag and the box, so never.
        boolean crawlingLike = state.crawling || moving.climbCrawling() || state.crawlClimbing;
        float pitch = player.getXRot();
        boolean moveSwim = pitch < 0 && input.z > 0 || pitch > 0 && input.z < 0;
        float fastFactor = state.fast ? config.sprintFactor.get() : 1;
        boolean fastSurfaceUp = state.fast && depth < 2.5 && player.level().isEmptyBlock(new BlockPos(i, j + 3, k));
        SwimLogic.Zone zone = SwimLogic.zone(crawlingLike, depth, diveUp, diveDown, swimDown, moveSwim,
                wantShallowSwim, speedFactor, fastFactor, fastSurfaceUp);
        if (zone.rejected()) {
            return Handled.NOT;
        }
        dippingDepth = (float) depth;

        if (state.crawling || state.sliding) {
            // The water above the crawling box's floor, a block above the original's feet once the box is small.
            if ((small ? depth - 1 : depth) < SwimLogic.CRAWL_WATER_DEPTH) {
                return Handled.NOT;
            }
            // The original turned the crawler into a swimmer and still moved it vanilla's way for this tick. The
            // crawling box is where a swimming one would be, so it only becomes one.
            if (wantShallowSwim) {
                player.move(MoverType.SELF, new Vec3(0, 0.1, 0));
            }
            state.crawling = false;
            moving.lift.retag(BoxLift.Reason.SWIM);
            resetSwimming();
            return Handled.STANDARD;
        }

        SwimLogic.Kind kind = zone.kind();
        if (kind == SwimLogic.Kind.SWIMMING && !config.swim.get() || kind == SwimLogic.Kind.DIVING && !config.dive.get()
                || kind == SwimLogic.Kind.DIPPING && !config.swim.get()) {
            kind = SwimLogic.Kind.NONE;
        }
        if (kind == SwimLogic.Kind.NONE) {
            resetSwimming();
            standUpFromSwimming();
            return Handled.STANDARD;
        }

        boolean swimming = kind == SwimLogic.Kind.SWIMMING;
        boolean diving = kind == SwimLogic.Kind.DIVING;
        double motionYDiff = zone.motionYDiff();
        // The original took back the 0.04 of 1.7.10's jump in water while jumping. Vanilla's jump of 1.20.1
        // differs, so all of it is undone instead, which leaves what the original's two steps left. A Smart Moving
        // jump this tick replaced vanilla's, but the original still took the 0.04 off it. Vanilla's sinking on
        // sneak, which 1.7.10 did not have, is undone too.
        Vec3 motion = moving.jumpedThisTick()
                ? player.getDeltaMovement().subtract(0, diveUp ? SwimLogic.VANILLA_LIQUID_JUMP : 0, 0)
                : player.getDeltaMovement().subtract(vanillaJump);
        if (player.isInWater() && SmartMovingClient.isSneakInput(player)) {
            motion = motion.add(0, SwimLogic.VANILLA_LIQUID_JUMP * SwimSpeed.of(player), 0);
        }
        double horizontal = SwimLogic.horizontalDamping(kind);
        motion = new Vec3(motion.x * horizontal, motion.y * SwimLogic.verticalDamping(kind), motion.z * horizontal);

        float strafe = (float) input.x;
        float forward = (float) input.z;
        boolean levitating = diving && !diveUp && !diveDown && strafe == 0 && forward == 0;
        if (diving) {
            speedFactor *= config.diveSpeedFactor.get();
        }
        if (swimming) {
            speedFactor *= config.swimSpeedFactor.get();
        }
        waterMovementTicks = swimming || diving ? waterMovementTicks + 1 : 0;
        jumpingOutOfWater = SwimLogic.jumpOutOfWater(strafe != 0 || forward != 0, player.horizontalCollision,
                diveUp, state.slow, waterMovementTicks, onGroundBeforeJump, wasJumpingOutOfWater);
        float acceleration = 0.02f * speedFactor * SwimLogic.enhancementFactor(player.isSprinting(),
                EnchantmentHelper.getDepthStrider(player), player.onGround(), player.getSpeed(),
                player.hasEffect(MobEffects.DOLPHINS_GRACE), (float) SwimSpeed.of(player));

        boolean moveRelative = true;
        if (diving) {
            if (!diveUp && !diveDown && !levitating) {
                double[] added = SwimLogic.moveFlying((float) motionYDiff, strafe, forward, acceleration,
                        player.getYRot(), pitch, config.diveControlVertical.get());
                motion = motion.add(added[0], added[1], added[2]);
            } else {
                motion = new Vec3(motion.x, (motion.y + motionYDiff) * 0.6, motion.z);
            }
            moveRelative = false;
        } else if (swimming && swimDown) {
            motion = new Vec3(motion.x, (motion.y + motionYDiff) * 0.6, motion.z);
        } else if (jumpingOutOfWater) {
            motion = new Vec3(motion.x, SwimLogic.JUMP_OUT_MOTION, motion.z);
        } else {
            motion = motion.add(0, motionYDiff, 0);
        }
        player.setDeltaMovement(motion);

        state.diving = diving;
        state.levitating = levitating;
        state.swimming = swimming;
        state.dipping = kind == SwimLogic.Kind.DIPPING;
        shallowDiveOrSwim = couldStandUp && (swimming || diving);
        boolean standOnFloor = false;
        if (shallowDiveOrSwim && realFloorDepth < SwimLogic.SHALLOW_STAND_DEPTH) {
            // Too shallow to swim on: crawls when sneaking, otherwise stands on the floor.
            state.diving = false;
            state.swimming = false;
            shallowDiveOrSwim = false;
            state.dipping = true;
            state.crawling = state.slow;
            standOnFloor = !state.slow;
        }
        if (state.swimming || state.diving || state.crawling) {
            // The original's crawling box here is the swimming one.
            toSwimmingBox();
            if (state.crawling) {
                moving.lift.clear(BoxLift.Reason.SWIM);
            }
        } else {
            standUpFromSwimming();
        }
        if (standOnFloor) {
            AABB standing = player.getBoundingBox();
            player.move(MoverType.SELF,
                    new Vec3(0, moving.maxSolidBetween(standing.minY, standing.maxY) - standing.minY, 0));
        }

        if (moveRelative) {
            player.moveRelative(acceleration, new Vec3(strafe, 0, forward));
        }
        player.move(MoverType.SELF, player.getDeltaMovement());
        return Handled.MOVED;
    }

    /**
     * Grab pressed ({@code updateEntityActionState} 2663-2685): a shallow swimmer who would climb stands on the
     * floor, and a dipping player who would crawl in water at least 0.55 deep goes down into it, to swim from 0.6.
     */
    void grabStarted(boolean jumpPressed, boolean wouldWantClimb, boolean wouldWantCrawl,
                     SmartMovingClientConfig config) {
        if (shallowDiveOrSwim && wouldWantClimb) {
            standUpFromSwimming();
            AABB box = player.getBoundingBox();
            player.move(MoverType.SELF, new Vec3(0, moving.maxSolidBetween(box.minY, box.maxY) - box.minY, 0));
            if (jumpPressed) {
                stillSwimmingJump = true;
            }
        } else if (state.dipping && wouldWantCrawl && dippingDepth >= SwimLogic.SHALLOW_STAND_DEPTH) {
            if (dippingDepth >= 0.6) {
                // The small box a block up, moved down until the water's surface is 1.6 above the feet.
                state.crawling = false;
                toSwimmingBox();
                player.move(MoverType.SELF, new Vec3(0, dippingDepth - 1.6, 0));
            } else {
                moving.toCrawling(config);
            }
        }
    }

    /**
     * {@code standupIfPossible}, for a crawler in water with swimming and diving off: down to a floor less than a
     * block below, then standing if there is room. The original slid instead of crawling on with grab held after
     * a head jump; here it crawls on.
     */
    private void standUpIfPossible(SmartMovingClientConfig config) {
        AABB box = player.getBoundingBox();
        double gapBelow = box.minY - moving.maxSolidBetween(box.minY - 1.1, box.minY);
        if (gapBelow >= 1) {
            return;
        }
        double top = box.minY + SMALL_HEIGHT;
        double gapAbove = moving.minSolidBetween(top, top + 1.1) - top;
        player.move(MoverType.SELF, new Vec3(0, -gapBelow, 0));
        if (gapBelow + gapAbove >= 1) {
            state.crawling = false;
            player.setPose(Pose.STANDING);
        } else {
            moving.toCrawling(config);
        }
    }

    /** After the own player's move: a splash every 1.43 blocks swum ({@code afterMoveEntity} 1674-1680). */
    void afterMove(double distance) {
        if (!state.swimming) {
            return;
        }
        distanceSwum += (float) distance;
        if (distanceSwum > 1.4285715f) {
            RandomSource random = player.getRandom();
            SmartMovingClient.playSound(player, SoundEvents.PLAYER_SPLASH, 0.05f,
                    1 + (random.nextFloat() - random.nextFloat()) * 0.4f);
            distanceSwum--;
        }
    }

    /**
     * At the end of the tick ({@code afterOnUpdate}): the swimming particles, and the push of a current taken back
     * while swimming ({@code reverseHandleMaterialAcceleration}).
     */
    void afterTick(SmartMovingClientConfig config) {
        if (!state.swimming) {
            return;
        }
        Vec3 flow = currentFlow();
        if (flow.lengthSqr() > 0) {
            player.setDeltaMovement(player.getDeltaMovement().add(flow.normalize().scale(-0.014)));
        }
        Vec3 motion = player.getDeltaMovement();
        SwimParticles.spawn(player, motion.x, motion.z, config);
    }

    /**
     * The direction water pushes the player in, summed over the water blocks of the original's box shrunk by 0.4 at
     * the top and the bottom, like 1.7.10's push that the original took back.
     */
    private Vec3 currentFlow() {
        AABB box = player.getBoundingBox();
        double top = box.minY + (small() ? SMALL_HEIGHT : STANDING_HEIGHT);
        Vec3 flow = Vec3.ZERO;
        for (int x = Mth.floor(box.minX + 0.001); x <= Mth.floor(box.maxX - 0.001); x++) {
            for (int z = Mth.floor(box.minZ + 0.001); z <= Mth.floor(box.maxZ - 0.001); z++) {
                for (int y = Mth.floor(box.minY + 0.4); y <= Mth.floor(top - 0.4); y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    FluidState fluid = player.level().getFluidState(pos);
                    if (fluid.is(FluidTags.WATER)) {
                        flow = flow.add(fluid.getFlow(player.level(), pos));
                    }
                }
            }
        }
        return flow;
    }

    /**
     * Back to the land after swimming or diving ({@code fromSwimmingOrDiving} 1481-1508): crawls on where there is
     * no room to stand, or water just above (then without the crawling input), and otherwise stands, on top of a
     * floor higher than the feet; sneaking up a higher floor crawls.
     */
    private void fromSwimmingOrDiving(boolean wasShortInWater) {
        if (!wasShortInWater || state.swimming || state.diving || player.isSleeping()) {
            return;
        }
        boolean small = small();
        double feet = small ? player.getBoundingBox().minY - 1 : player.getBoundingBox().minY;
        double smallTop = feet + 1 + SMALL_HEIGHT;
        double bottom = moving.maxSolidBetween(feet, feet + 1);
        double liquidCeiling = minLiquidBetween(smallTop, smallTop + 1.1);
        double ceiling = moving.minSolidBetween(smallTop, smallTop + 1.1);
        boolean crawl = false;
        double up = 0;
        if (ceiling - bottom < STANDING_HEIGHT) {
            crawl = true;
        } else if (liquidCeiling - bottom < STANDING_HEIGHT) {
            crawl = true;
            continueCrawl = true;
        } else if (bottom > feet) {
            crawl = state.slow && bottom > feet + 0.5;
            up = bottom - feet;
        }
        if (crawl) {
            state.crawling = true;
            state.dipping = false;
        }
        if (crawl && up == 0) {
            // The small box is where the original's crawling one was.
            moving.lift.clear(BoxLift.Reason.SWIM);
            if (!small) {
                player.setPose(Pose.SWIMMING);
            }
            return;
        }
        standUpFromSwimming();
        if (up > 0) {
            player.move(MoverType.SELF, new Vec3(0, up, 0));
        }
        if (crawl) {
            player.setPose(Pose.SWIMMING);
        }
    }

    /** Whether the box is a swimmer's, which keeps it small. */
    boolean swimmingBox() {
        return moving.lift.is(BoxLift.Reason.SWIM);
    }

    /** The box of a swimmer or diver: small and a block up, where the standing box's upper part was. */
    private void toSwimmingBox() {
        if (moving.lift.is(BoxLift.Reason.SWIM)) {
            return;
        }
        if (small()) {
            moving.lift.retag(BoxLift.Reason.SWIM);
            return;
        }
        // Like a head jump's: the small pose applies at once and the server learns of it before the movement.
        player.setPose(Pose.SWIMMING);
        SmartMovingClient.sendStateNow(player);
        if (moving.fits(Pose.SWIMMING, 1)) {
            moving.lift.lift(BoxLift.Reason.SWIM);
        } else {
            moving.lift.retag(BoxLift.Reason.SWIM);
        }
    }

    /** Back to the standing box from a swimmer's, growing down by the block it was raised by. */
    private void standUpFromSwimming() {
        if (moving.lift.is(BoxLift.Reason.SWIM)) {
            moving.lift.clear(BoxLift.Reason.SWIM);
            moving.standUpFromSmall(-1);
        }
    }

    /**
     * Vanilla's land movement ({@code LivingEntity.travel}'s last branch), for the ticks the original moved a player
     * in water on land. Forge's block friction and gravity attribute are not known here; vanilla's values apply.
     */
    private void landTravel(Vec3 input) {
        double gravity = player.getDeltaMovement().y <= 0 && player.hasEffect(MobEffects.SLOW_FALLING) ? 0.01 : 0.08;
        Level level = player.level();
        BlockPos below = ((EntityAccessor) player).smartmovingreborn$getBlockPosBelowThatAffectsMyMovement();
        float friction = level.getBlockState(below).getBlock().getFriction();
        float damping = player.onGround() ? friction * 0.91f : 0.91f;
        Vec3 motion = player.handleRelativeFrictionAndCalculateMovement(input, friction);
        double y = motion.y;
        if (player.hasEffect(MobEffects.LEVITATION)) {
            y += (0.05 * (player.getEffect(MobEffects.LEVITATION).getAmplifier() + 1) - motion.y) * 0.2;
        } else if (level.isClientSide && !level.hasChunkAt(below)) {
            y = player.getY() > level.getMinBuildHeight() ? -0.1 : 0;
        } else if (!player.isNoGravity()) {
            y -= gravity;
        }
        if (player.shouldDiscardFriction()) {
            player.setDeltaMovement(motion.x, y, motion.z);
        } else {
            player.setDeltaMovement(motion.x * damping, y * 0.98f, motion.z * damping);
        }
    }

    // Measuring

    /** Whether the box is small now; a small pose decided this tick only applies at its end. */
    private boolean small() {
        return player.getBbHeight() < 1;
    }

    /**
     * Whether the player is in water the way 1.7.10 saw it ({@code isInWater}): a water block in the cells of the
     * box shrunk by 0.4 at the top and the bottom. The box is the original's, which was 0.8 high when small.
     */
    boolean inWater() {
        AABB box = player.getBoundingBox();
        double top = box.minY + (small() ? SMALL_HEIGHT : STANDING_HEIGHT);
        int minY = Mth.floor(box.minY + 0.4);
        int maxY = Mth.floor(top - 0.4);
        for (int x = Mth.floor(box.minX + 0.001); x <= Mth.floor(box.maxX - 0.001); x++) {
            for (int z = Mth.floor(box.minZ + 0.001); z <= Mth.floor(box.maxZ - 0.001); z++) {
                for (int y = minY; y <= maxY; y++) {
                    if (player.level().getFluidState(new BlockPos(x, y, z)).is(FluidTags.WATER)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Whether there is water within the original's box at the player's column ({@code isInLiquid}). */
    private boolean inLiquid() {
        AABB box = player.getBoundingBox();
        double top = box.minY + (small() ? SMALL_HEIGHT : STANDING_HEIGHT);
        return maxLiquidBetween(box.minY, top) != box.minY || minLiquidBetween(box.minY, top) != top;
    }

    /**
     * How high the water in a block reaches ({@code getLiquidBorder}): vanilla's surface height, which is close to
     * the original's (a source block 8/9 high where the original had 0.8875, flowing water by its level).
     */
    private float liquidBorder(int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        FluidState fluid = player.level().getFluidState(pos);
        return fluid.is(FluidTags.WATER) ? fluid.getHeight(player.level(), pos) : 0;
    }

    /** The highest water surface in the player's column between the heights, or {@code minY} ({@code getMaxPlayerLiquidBetween}). */
    private double maxLiquidBetween(double minY, double maxY) {
        int x = Mth.floor(player.getX());
        int z = Mth.floor(player.getZ());
        for (int y = Mth.floor(maxY); y >= Mth.floor(minY); y--) {
            float border = liquidBorder(x, y, z);
            if (border > 0) {
                return y + border;
            }
        }
        return minY;
    }

    /** The lowest water in the player's column between the heights, or {@code maxY} ({@code getMinPlayerLiquidBetween}). */
    private double minLiquidBetween(double minY, double maxY) {
        int x = Mth.floor(player.getX());
        int z = Mth.floor(player.getZ());
        for (int y = Mth.floor(minY); y <= Mth.floor(maxY); y++) {
            float border = liquidBorder(x, y, z);
            if (border > 0) {
                if (y > minY) {
                    return y;
                }
                if (y + border > minY) {
                    return minY;
                }
            }
        }
        return maxY;
    }

    /**
     * Whether a tunnel opens ahead in any direction the player could grab towards ({@code isTunnelAhead}): an empty
     * block a block above the feet with something solid above it, where a shallow swim does not go on.
     */
    private boolean tunnelAhead(int i, int j, int k, SmartMovingClientConfig config) {
        LevelClimbTerrain terrain = new LevelClimbTerrain(player.level(), player);
        for (ClimbOrientation o : ClimbOrientation.climbingOrientations(player.getYRot(), true, true,
                config.climbFreeOrthogonalAngle.get(), config.climbFreeDiagonalAngle.get())) {
            if (terrain.isFullEmpty(i + o.x, j + 1, k + o.z)
                    && player.level().getBlockState(new BlockPos(i + o.x, j + 2, k + o.z)).blocksMotion()) {
                return true;
            }
        }
        return false;
    }
}
