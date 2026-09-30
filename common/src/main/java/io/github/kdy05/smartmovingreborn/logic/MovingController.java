package io.github.kdy05.smartmovingreborn.logic;

import io.github.kdy05.smartmovingreborn.SmartMovingReborn;
import io.github.kdy05.smartmovingreborn.client.SmartMovingClient;
import io.github.kdy05.smartmovingreborn.logic.crawl.CrawlLogic;
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
     * The client's own player, set when it is constructed. Always null on a dedicated server, so the hooks in
     * common classes never reach {@link SmartMovingClient} there.
     */
    private static Player self;

    private MovingController() {
    }

    public static void setSelf(Player player) {
        self = player;
        CrawlLogic.reset();
    }

    /** Whether {@code entity} is the client's own player and Smart Moving is active for it. */
    private static boolean isActiveSelf(Entity entity) {
        return entity == self && SmartMovingClient.isActive();
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
    }

    /** {@code LocalPlayer#aiStep} TAIL (original {@code afterOnLivingUpdate}). */
    public static void afterAiStep(Player player) {
        if (!isActiveSelf(player)) {
            return;
        }
    }

    /** Replaces {@code LocalPlayer#serverAiStep} when true (original {@code updateEntityActionState}). */
    public static boolean serverAiStep(Player player) {
        if (!isActiveSelf(player)) {
            return false;
        }
        CrawlLogic.update(player, SmartMovingClient.SNEAK, SmartMovingClient.GRAB, SmartMovingClient.JUMP,
                SmartMovingReborn.CLIENT_CONFIG);
        return false;
    }

    /** {@code LocalPlayer#serverAiStep} TAIL: adjusts the movement input vanilla has just set. */
    public static void afterServerAiStep(Player player) {
        if (!isActiveSelf(player)) {
            return;
        }
        if (CrawlLogic.isCrawling()) {
            CrawlLogic.applyInput(player, SmartMovingReborn.CLIENT_CONFIG);
        }
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
    }

    /** {@code LocalPlayer#move} TAIL (original {@code afterMoveEntity}). */
    public static void afterMove(Player player, MoverType type, Vec3 movement) {
        if (!isActiveSelf(player)) {
            return;
        }
    }

    /** Overrides {@code LocalPlayer#isShiftKeyDown} when non-null (original {@code isSneaking}). */
    public static Boolean isShiftKeyDown(Player player) {
        if (!isActiveSelf(player)) {
            return null;
        }
        if (CrawlLogic.isCrawling()) {
            // Vanilla keeps sneaking players from walking off edges.
            return !SmartMovingReborn.CLIENT_CONFIG.crawlOverEdge.get();
        }
        return null;
    }

    /** Overrides {@code AbstractClientPlayer#getFieldOfViewModifier} when non-null (original {@code getFOVMultiplier}). */
    public static Float fieldOfViewModifier(Player player) {
        if (!isActiveSelf(player)) {
            return null;
        }
        return null;
    }

    /** Overrides {@code LivingEntity#onClimbable} when non-null (original {@code isOnLadder}). */
    public static Boolean onClimbable(Entity entity) {
        if (!isActiveSelf(entity)) {
            return null;
        }
        return null;
    }

    /** Replaces {@code Player#travel} when true (original {@code moveEntityWithHeading}). */
    public static boolean travel(Player player, Vec3 input) {
        if (!isActiveSelf(player)) {
            return false;
        }
        return false;
    }

    /** Replaces {@code Player#jumpFromGround} when true (original {@code jump}). */
    public static boolean jumpFromGround(Player player) {
        if (!isActiveSelf(player)) {
            return false;
        }
        return false;
    }

    /**
     * Whether another player's name tag should be hidden ({@code move.crawl.name}, {@code move.sneak.name}).
     * The sneaking rule also covers vanilla sneaking, like the original.
     */
    public static boolean hideNameTag(Entity entity) {
        if (entity == self || !(entity instanceof Player player) || !SmartMovingClient.isActive()) {
            return false;
        }
        MovingState state = SmartMovingClient.getOtherState(player.getId());
        if (state != null && state.crawling) {
            return !SmartMovingReborn.CLIENT_CONFIG.crawlNameTag.get();
        }
        return player.isDiscrete() && !SmartMovingReborn.CLIENT_CONFIG.sneakNameTag.get();
    }

    // Pose and size

    /**
     * Replaces {@code Player#updatePlayerPose} when true, after setting the Smart Moving pose. The client's own
     * player uses the local logic and the server uses the state each client sent. Other players on a client
     * need nothing: {@code RemotePlayer} skips this method and takes the pose the server synchronizes.
     */
    public static boolean updatePose(Player player) {
        boolean crawling;
        if (player == self) {
            if (!SmartMovingClient.isActive()) {
                return false;
            }
            crawling = CrawlLogic.isCrawling();
        } else {
            MovingState state = stateOf(player);
            if (state == null) {
                return false;
            }
            crawling = state.crawling;
        }
        if (!crawling) {
            return false;
        }
        player.setPose(Pose.SWIMMING);
        return true;
    }

    /** Server: the last state {@code player}'s client sent, or null when Smart Moving does not apply to them. */
    private static MovingState stateOf(Player player) {
        return player instanceof ServerPlayer serverPlayer && isActiveOnServer(serverPlayer)
                ? ServerNetworkHandler.getState(serverPlayer)
                : null;
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

    /** {@code ServerGamePacketListenerImpl#handleMovePlayer} at every return on the server thread. */
    public static void afterHandleMovePlayer(ServerPlayer player) {
        if (!isActiveOnServer(player)) {
            return;
        }
    }
}
