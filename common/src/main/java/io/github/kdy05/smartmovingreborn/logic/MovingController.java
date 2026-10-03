package io.github.kdy05.smartmovingreborn.logic;

import io.github.kdy05.smartmovingreborn.SmartMovingReborn;
import io.github.kdy05.smartmovingreborn.client.SmartMovingClient;
import io.github.kdy05.smartmovingreborn.mixin.server.ServerGamePacketListenerImplAccessor;
import io.github.kdy05.smartmovingreborn.network.Network;
import io.github.kdy05.smartmovingreborn.network.ServerNetworkHandler;
import io.github.kdy05.smartmovingreborn.state.MovingState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Receives every movement hook (the original's {@code SmartMovingSelf} / {@code SmartMovingServer} entry
 * points). Handlers that return {@code false} or {@code null} let the vanilla method run unchanged, which is
 * always the case while Smart Moving is inactive.
 */
public final class MovingController {
    /**
     * The eye height while lying for a Smart Moving move. The original's eyes were 0.62 above the box's bottom,
     * above this port's 0.6 high box; 0.55 keeps them inside it, so that a head jump against a ceiling does not
     * count as suffocating. The view can still look into that ceiling: looking up at a slant, the camera's near
     * plane (0.05 ahead) reaches up to {@code sqrt(0.05² + (0.05 tan(fov / 2))²)} above the eyes, 0.061 at a
     * field of view of 70 and more as it widens.
     */
    private static final float LYING_EYE_HEIGHT = 0.55f;
    /**
     * Where a swimmer's or diver's eyes are for the water, above the box's bottom: the original's
     * ({@code posY + getEyeHeight()}, 1.74 above its feet and its swimming box a block up). The view stays at
     * {@link #LYING_EYE_HEIGHT}, which at the surface a swimmer floats at would count as under water, since vanilla
     * looks for water 0.11 below the eyes.
     */
    private static final double SWIM_FLUID_EYE_HEIGHT = 0.74;

    /**
     * The client's own player, set when it is constructed. Always null on a dedicated server, so the hooks in
     * common classes never reach {@link SmartMovingClient} there.
     */
    private static SelfMoving self;

    private MovingController() {
    }

    /** Client: called from the {@code LocalPlayer} constructor (a new one after respawning or changing dimension). */
    public static void setSelf(Player player) {
        self = new SelfMoving(player, SmartMovingClient.LOCAL_STATE);
    }

    /** Client: forgets the own player's moves, when Smart Moving is inactive or a new connection starts. */
    public static void resetSelf() {
        if (self != null) {
            self.reset();
        }
    }

    /** Whether {@code entity} is the client's own player and Smart Moving is active for it. */
    private static boolean isActiveSelf(Entity entity) {
        return self != null && entity == self.player && SmartMovingClient.isActive();
    }

    /**
     * Whether {@code entity} is the own player and Smart Moving moves it: active, and not gliding with an elytra or
     * spinning with a riptide trident, which vanilla moves alone ({@link SelfMoving#vanillaOverride}).
     */
    private static boolean isMovingSelf(Entity entity) {
        return isActiveSelf(entity) && !self.vanillaOverride();
    }

    /** Whether the server should apply Smart Moving to {@code player}, i.e. its client has the mod. */
    private static boolean isActiveOnServer(ServerPlayer player) {
        return Network.canSendTo(player);
    }

    // Client: the own player

    /** {@code LocalPlayer#aiStep} HEAD (original {@code beforeOnLivingUpdate}). */
    public static void beforeAiStep(Player player) {
        if (!isActiveSelf(player)) {
            return;
        }
        self.beforeTick();
    }

    /** {@code LocalPlayer#aiStep} TAIL (original {@code afterOnLivingUpdate} and {@code afterOnUpdate}). */
    public static void afterAiStep(Player player) {
        if (!isActiveSelf(player)) {
            return;
        }
        self.afterTick(SmartMovingReborn.CLIENT_CONFIG);
    }

    /**
     * Replaces {@code LocalPlayer#serverAiStep} when true (original {@code updateEntityActionState}). The
     * buttons are updated first, also while inactive so that they never carry a stale press.
     */
    public static boolean serverAiStep(Player player) {
        if (self != null && player == self.player) {
            SmartMovingClient.updateButtons(player);
        }
        if (!isActiveSelf(player)) {
            return false;
        }
        self.updateActionState(SmartMovingClient.SNEAK, SmartMovingClient.GRAB, SmartMovingClient.JUMP,
                SmartMovingClient.SPRINT, SmartMovingClient.LEFT, SmartMovingClient.RIGHT, SmartMovingClient.BACK,
                SmartMovingClient.isForwardPressed(player), SmartMovingClient.isJumpPressed(player),
                SmartMovingReborn.CLIENT_CONFIG);
        return false;
    }

    /** {@code LocalPlayer#serverAiStep} TAIL: adjusts the movement input vanilla has just set. */
    public static void afterServerAiStep(Player player) {
        if (!isMovingSelf(player)) {
            return;
        }
        self.applyInput(SmartMovingReborn.CLIENT_CONFIG);
    }

    /** Replaces {@code LocalPlayer#moveTowardsClosestSpace} when true (original {@code pushOutOfBlocks}). */
    public static boolean moveTowardsClosestSpace(Player player, double x, double z) {
        if (!isActiveSelf(player)) {
            return false;
        }
        return false;
    }

    /** {@code LocalPlayer#move} HEAD (original {@code beforeMoveEntity}). */
    public static void beforeMove(Player player, MoverType type, Vec3 movement) {
        if (!isActiveSelf(player)) {
            return;
        }
        self.beforeMove(type, movement);
    }

    /** {@code LocalPlayer#move} TAIL (original {@code afterMoveEntity}). */
    public static void afterMove(Player player, MoverType type, Vec3 movement) {
        if (!isActiveSelf(player)) {
            return;
        }
        self.afterMove();
    }

    /**
     * Overrides {@code LocalPlayer#isShiftKeyDown} when non-null (original {@code isSneaking}). Vanilla derives
     * the crouching pose, the edge protection and the sneak state sent to the server from it. Riding keeps
     * vanilla's, so that sneak still dismounts.
     */
    public static Boolean isShiftKeyDown(Player player) {
        if (!isMovingSelf(player) || player.isPassenger()) {
            return null;
        }
        return self.shiftKeyDown(SmartMovingReborn.CLIENT_CONFIG);
    }

    /** Whether {@code player} is the own player flying Smart Moving's way ({@code isFlying}). */
    public static boolean smartFlying(Player player) {
        return self != null && player == self.player && SmartMovingClient.isActive() && self.smartFlying();
    }

    /** Whether the own player's box is a flyer's, small and a block up ({@link MovingState#smallFlying}). */
    public static boolean smallFlying() {
        return self != null && SmartMovingClient.isActive() && self.flyingBox();
    }

    /** The own player's jump charge for the charge bar, 0 while Smart Moving is inactive. */
    public static float jumpCharge() {
        return self != null && SmartMovingClient.isActive() ? self.jumpCharge() : 0;
    }

    /** The own player's head jump charge for the charge bar, 0 while Smart Moving is inactive. */
    public static float headJumpCharge() {
        return self != null && SmartMovingClient.isActive() ? self.headJumpCharge() : 0;
    }

    /**
     * The movement speed {@code AbstractClientPlayer#getFieldOfViewModifier} computes with (original
     * {@code getFOVMultiplier}): the faded perspective speed instead of the current one.
     */
    public static double fieldOfViewSpeed(Player player, double movementSpeed) {
        if (!isActiveSelf(player)) {
            return movementSpeed;
        }
        return self.perspectiveSpeed();
    }

    /**
     * {@code LivingEntity#getFrictionInfluencedSpeed}, the speed of walking and of air control: applies the
     * original's speed factor, and notes the friction vanilla is about to damp with. Flying vanilla's way keeps
     * vanilla's speed; Smart Moving's flying does not get here.
     */
    public static float frictionInfluencedSpeed(Entity entity, float friction, float speed) {
        if (!isMovingSelf(entity) || self.player.getAbilities().flying) {
            return speed;
        }
        self.beforeFrictionMove(friction);
        return speed * self.landSpeedFactor(SmartMovingReborn.CLIENT_CONFIG);
    }

    /** Overrides {@code LivingEntity#onClimbable} when non-null (original {@code isOnLadder}). */
    public static Boolean onClimbable(Entity entity) {
        if (!isMovingSelf(entity)) {
            return null;
        }
        return self.onClimbable();
    }

    /** Replaces {@code LivingEntity#handleOnClimbable} when non-null: ladders and vines before moving. */
    public static Vec3 handleOnClimbable(Entity entity, Vec3 motion) {
        if (!isMovingSelf(entity)) {
            return null;
        }
        return self.handleOnClimbable(motion, SmartMovingReborn.CLIENT_CONFIG);
    }

    /**
     * {@code LivingEntity#handleRelativeFrictionAndCalculateMovement} RETURN: the motion after moving on land or
     * in the air and before gravity, which climbing sets (original {@code handleClimbing}).
     */
    public static Vec3 afterFrictionMove(Entity entity, Vec3 motion) {
        if (!isMovingSelf(entity)) {
            return motion;
        }
        return self.afterFrictionMove(motion, SmartMovingReborn.CLIENT_CONFIG);
    }

    /**
     * Whether {@code Entity#getMovementEmission} should be none: climbing and diving make no step or swimming
     * sounds or vibrations of vanilla's (original {@code canTriggerWalking}); climbing has its own sounds.
     */
    public static boolean silentMovement(Entity entity) {
        return isMovingSelf(entity) && (self.state.climbing || self.state.diving);
    }

    /**
     * Replaces {@code Player#travel} when true (original {@code moveEntityWithHeading}). Jumps first, then
     * swimming, which replaces vanilla's movement in water, and Smart Moving's flying; the rest stays vanilla's.
     */
    public static boolean travel(Player player, Vec3 input) {
        if (!isMovingSelf(player)) {
            return false;
        }
        self.beforeTravel(SmartMovingReborn.CLIENT_CONFIG);
        if (self.travel(input, SmartMovingReborn.CLIENT_CONFIG)) {
            self.afterTravel(SmartMovingReborn.CLIENT_CONFIG);
            return true;
        }
        return false;
    }

    /**
     * Replaces {@code Player#updateSwimming} when true, keeping vanilla's swimming off: Smart Moving's swimming or
     * diving replaces it, and with it vanilla's sprint swimming pose and its vertical steering.
     */
    public static boolean updateSwimming(Player player) {
        if (!isMovingSelf(player) || !SelfSwimming.replacesVanilla(SmartMovingReborn.CLIENT_CONFIG)) {
            return false;
        }
        player.setSwimming(false);
        return true;
    }

    /** {@code Player#travel} TAIL: the damping of slides and gliding head jumps, then wall jumps. */
    public static void afterTravel(Player player) {
        if (!isMovingSelf(player)) {
            return;
        }
        self.afterTravel(SmartMovingReborn.CLIENT_CONFIG);
    }

    /**
     * Replaces {@code Player#jumpFromGround} when true (original {@code jump}): Smart Moving jumps instead in
     * {@link #travel}, though not in water, where 1.7.10 never jumped off the ground and Smart Moving's jump while
     * dipping replaces it. Jumps in lava (out of scope) and in water with swimming and diving off stay vanilla's.
     */
    public static boolean jumpFromGround(Player player) {
        if (!isMovingSelf(player) || player.isInLava()
                || player.isInWater() && !SelfSwimming.replacesVanilla(SmartMovingReborn.CLIENT_CONFIG)) {
            return false;
        }
        self.avoidJump();
        return true;
    }

    /**
     * Whether another player's name tag should be hidden ({@code move.crawl.name}, {@code move.sneak.name}).
     * The sneaking rule also covers vanilla sneaking, like the original.
     */
    public static boolean hideNameTag(Entity entity) {
        if (!(entity instanceof Player player) || self != null && player == self.player
                || !SmartMovingClient.isActive()) {
            return false;
        }
        MovingState state = stateOf(player);
        if (state != null && state.crawling) {
            return !SmartMovingReborn.CLIENT_CONFIG.crawlNameTag.get();
        }
        return player.isDiscrete() && !SmartMovingReborn.CLIENT_CONFIG.sneakNameTag.get();
    }

    // State of any player

    /**
     * The Smart Moving state of any player on either side, or null when Smart Moving does not apply to them:
     * the own player's while active, another player's last relayed state on a client, and on the server the
     * last state that player's client sent.
     */
    public static MovingState stateOf(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            return isActiveOnServer(serverPlayer) ? ServerNetworkHandler.getState(serverPlayer) : null;
        }
        if (self != null && player == self.player) {
            return SmartMovingClient.isActive() ? self.state : null;
        }
        return player.level().isClientSide() ? SmartMovingClient.getOtherState(player.getId()) : null;
    }

    // Pose and size

    /**
     * Whether {@code LivingEntity#updateSwimAmount} should keep the swim amount at 0: the player lies in
     * {@code Pose.SWIMMING} for a Smart Moving move, whose model lies down by itself. Vanilla would otherwise
     * build the amount up, tilt the model and swing the arms, and fade both out over several ticks after
     * standing up.
     */
    public static boolean suppressSwimAmount(Entity entity) {
        return entity instanceof Player player && smallPose(player);
    }

    /**
     * Whether {@code player} is in {@code Pose.SWIMMING} for a Smart Moving move ({@link MovingState#smallPose}).
     * The own player's climb crawling is known at once, before its state's size flag catches up. Elytra gliding
     * and riptide spins keep vanilla's pose, also before a state that ended the move arrives.
     */
    public static boolean smallPose(Player player) {
        if (player.isFallFlying() || player.isAutoSpinAttack()) {
            return false;
        }
        if (self != null && player == self.player) {
            return SmartMovingClient.isActive() && self.smallPose();
        }
        MovingState state = stateOf(player);
        return state != null && state.smallPose();
    }

    /**
     * Overrides {@code Player#getStandingEyeHeight} when non-null. Lying for a Smart Moving move, the eyes sit
     * {@link #LYING_EYE_HEIGHT} above the box's bottom instead of vanilla's 0.4 for swimming.
     */
    public static Float standingEyeHeight(Player player, Pose pose) {
        return pose == Pose.SWIMMING && smallPose(player) ? LYING_EYE_HEIGHT : null;
    }

    /**
     * {@code Player#updatePlayerPose} HEAD: vanilla caches the eye height and only recomputes it when the size
     * changes, which a switch between vanilla swimming and a Smart Moving move in the same pose does not.
     *
     * @param wasLying whether the cached eye height is a lying move's
     * @return whether the eye height now is a lying move's
     */
    public static boolean updateEyeHeight(Player player, boolean wasLying) {
        boolean lying = smallPose(player);
        if (lying != wasLying) {
            player.refreshDimensions();
        }
        return lying;
    }

    /**
     * Replaces {@code Player#updatePlayerPose} when true, after setting the Smart Moving pose. Other players on
     * a client never get here: {@code RemotePlayer} skips this method and takes the pose the server synchronizes.
     */
    public static boolean updatePose(Player player) {
        if (!smallPose(player)) {
            return false;
        }
        player.setPose(Pose.SWIMMING);
        return true;
    }

    /**
     * The eye height {@code Entity#updateFluidOnEyes} looks for water at, which decides breathing on the server and
     * the underwater state on the client: the original's for a Smart Moving swimmer or diver
     * ({@link #SWIM_FLUID_EYE_HEIGHT}), otherwise vanilla's.
     */
    public static double fluidEyeY(Entity entity, double eyeY) {
        if (!(entity instanceof Player player) || player.getPose() != Pose.SWIMMING) {
            return eyeY;
        }
        MovingState state = stateOf(player);
        return state != null && (state.swimming || state.diving) ? player.getY() + SWIM_FLUID_EYE_HEIGHT : eyeY;
    }

    /** Overrides {@code Player#getDimensions} when non-null. */
    public static EntityDimensions dimensions(Player player, Pose pose) {
        return null;
    }

    // Server

    /** {@code ServerGamePacketListenerImpl#handleMovePlayer}, once on the server thread (the original's core mod). */
    public static void beforeHandleMovePlayer(ServerPlayer player) {
        if (!isActiveOnServer(player)) {
            return;
        }
    }

    /**
     * {@code ServerGamePacketListenerImpl#handleMovePlayer} at every return on the server thread
     * ({@code SmartMovingServer.afterOnUpdate}). Climbing and a wall jump clear the fall distance, and climbing
     * the ticks in the air that get a player kicked for flying. A wall jump's state arrives after the jump
     * tick's movement, so it clears what the following movements add. The original also set the server's
     * vertical motion, which the client's movement packets decide here.
     */
    public static void afterHandleMovePlayer(ServerPlayer player) {
        if (!isActiveOnServer(player)) {
            return;
        }
        MovingState state = ServerNetworkHandler.getState(player);
        if (state == null) {
            return;
        }
        boolean climbing = state.climbing || state.crawlClimbing || state.ceilingClimbing;
        if (climbing || state.wallJumping) {
            player.fallDistance = 0;
        }
        if (climbing) {
            ((ServerGamePacketListenerImplAccessor) player.connection).smartmovingreborn$setAboveGroundTickCount(0);
        }
    }
}
