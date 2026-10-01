package io.github.kdy05.smartmovingreborn.client;

import io.github.kdy05.smartmovingreborn.SmartMovingReborn;
import io.github.kdy05.smartmovingreborn.config.SmartMovingClientConfig;
import io.github.kdy05.smartmovingreborn.input.Button;
import io.github.kdy05.smartmovingreborn.input.KeyBindings;
import io.github.kdy05.smartmovingreborn.logic.MovingController;
import io.github.kdy05.smartmovingreborn.network.Network;
import io.github.kdy05.smartmovingreborn.network.StateMessage;
import io.github.kdy05.smartmovingreborn.network.StateRelayMessage;
import io.github.kdy05.smartmovingreborn.render.SlideParticles;
import io.github.kdy05.smartmovingreborn.state.MovingState;
import io.github.kdy05.smartmovingreborn.state.StatePacketCodec;
import io.github.kdy05.smartmovingreborn.mixin.client.CameraAccessor;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Client-side per-connection bookkeeping: server detection, the F9 toggle, and state exchange. */
public final class SmartMovingClient {
    /** How long to wait after joining for the server's channel before assuming the server lacks the mod. */
    private static final int SERVER_DETECTION_TICKS = 100;

    public static final Button GRAB = new Button();
    public static final Button SNEAK = new Button();
    public static final Button JUMP = new Button();
    public static final Button SPRINT = new Button();
    public static final Button LEFT = new Button();
    public static final Button RIGHT = new Button();
    public static final Button BACK = new Button();
    public static final MovingState LOCAL_STATE = new MovingState();
    private static final Map<Integer, MovingState> OTHER_STATES = new HashMap<>();

    private static ClientPacketListener session;
    private static int sessionTicks;
    private static boolean serverPresent;
    private static boolean serverAbsenceReported;
    private static long sentState;
    private static boolean stateSent;
    private static int lastPlayerCount;
    private static int lastPlayerId;

    private SmartMovingClient() {
    }

    private static SmartMovingClientConfig config() {
        return SmartMovingReborn.CLIENT_CONFIG;
    }

    /** Whether Smart Moving should act: enabled with F9 and supported by the server. */
    public static boolean isActive() {
        return config().enabled && serverPresent;
    }

    /** Called at the end of every client tick. */
    public static void tick(Minecraft minecraft) {
        ClientPacketListener connection = minecraft.getConnection();
        if (connection != session) {
            startSession(connection);
        }
        LocalPlayer player = minecraft.player;
        if (connection == null || player == null) {
            return;
        }
        sessionTicks++;

        while (KeyBindings.TOGGLE.consumeClick()) {
            config().enabled = !config().enabled;
            chat(player, config().enabled ? "enabled" : "disabled");
        }
        GRAB.update(KeyBindings.GRAB.isDown());
        SNEAK.update(player.input.shiftKeyDown);
        JUMP.update(player.input.jumping);
        SPRINT.update(minecraft.options.keySprint.isDown());
        LEFT.update(player.input.left);
        RIGHT.update(player.input.right);
        BACK.update(player.input.down);

        if (!serverPresent) {
            serverPresent = Network.isServerPresent();
            if (!serverPresent) {
                if (!serverAbsenceReported && sessionTicks > SERVER_DETECTION_TICKS) {
                    serverAbsenceReported = true;
                    SmartMovingReborn.LOGGER.info("Server does not have Smart Moving Reborn, staying disabled");
                    chat(player, "server_missing");
                }
                return;
            }
        }

        updateLocalState(minecraft, player);
        sendState(minecraft);
        OTHER_STATES.keySet().removeIf(id -> minecraft.level.getEntity(id) == null);
        for (AbstractClientPlayer other : minecraft.level.players()) {
            MovingState state = OTHER_STATES.get(other.getId());
            if (state != null && state.sliding) {
                SlideParticles.spawn(other, other.getX() - other.xo, other.getZ() - other.zo, config());
            }
        }
    }

    private static void startSession(ClientPacketListener connection) {
        session = connection;
        sessionTicks = 0;
        serverPresent = false;
        serverAbsenceReported = false;
        stateSent = false;
        lastPlayerCount = 0;
        lastPlayerId = -1;
        MovingController.resetSelf();
        LOCAL_STATE.clear();
        OTHER_STATES.clear();
    }

    /**
     * The moves in {@link #LOCAL_STATE} are kept by the movement logic during the player's tick; only the
     * input and size flags are filled in here.
     */
    private static void updateLocalState(Minecraft minecraft, LocalPlayer player) {
        if (!isActive()) {
            MovingController.resetSelf();
            LOCAL_STATE.clear();
            return;
        }
        LOCAL_STATE.sneakButton = minecraft.options.keyShift.isDown();
        LOCAL_STATE.jumping = player.input.jumping;
        LOCAL_STATE.small = player.getBbHeight() < 1.0f;
    }

    /**
     * Sends the state when it changed, and also when a player appeared in view so that they learn the
     * current state (the original's heuristic: the player list grew or its last entry changed).
     */
    private static void sendState(Minecraft minecraft) {
        long state = StatePacketCodec.encode(LOCAL_STATE);
        boolean send = !stateSent || state != sentState;

        List<AbstractClientPlayer> players = minecraft.level.players();
        int count = players.size();
        int lastId = count == 0 ? -1 : players.get(count - 1).getId();
        if (count > lastPlayerCount || (count == lastPlayerCount && lastId != lastPlayerId)) {
            send = true;
        }
        lastPlayerCount = count;
        lastPlayerId = lastId;

        if (send) {
            Network.sendToServer(new StateMessage(state));
            sentState = state;
            stateSent = true;
        }
    }

    /** Runs on the client thread. */
    public static void onStateRelay(StateRelayMessage message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && message.entityId() == minecraft.player.getId()) {
            return;
        }
        StatePacketCodec.decode(message.state(), OTHER_STATES.computeIfAbsent(message.entityId(), id -> new MovingState()));
    }

    /** Whether the own player's current movement input goes forward. */
    public static boolean isForwardPressed(Player player) {
        return ((LocalPlayer) player).input.forwardImpulse > 0;
    }

    /** Whether the own player's current movement input jumps. */
    public static boolean isJumpPressed(Player player) {
        return ((LocalPlayer) player).input.jumping;
    }

    /**
     * Lowers the camera's eased eye height by {@code dy} when following {@code player}, so that moving the
     * player up by {@code dy} leaves the view where it was.
     */
    public static void offsetCameraEyeHeight(Player player, float dy) {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        if (camera.getEntity() != player) {
            return;
        }
        CameraAccessor accessor = (CameraAccessor) camera;
        accessor.smartmovingreborn$setEyeHeight(accessor.smartmovingreborn$getEyeHeight() - dy);
        accessor.smartmovingreborn$setEyeHeightOld(accessor.smartmovingreborn$getEyeHeightOld() - dy);
    }

    /** The last state received for another player, or null. */
    public static MovingState getOtherState(int entityId) {
        return OTHER_STATES.get(entityId);
    }

    private static void chat(LocalPlayer player, String key) {
        if (config().configChat.get()) {
            player.displayClientMessage(Component.translatable("chat." + SmartMovingReborn.MOD_ID + "." + key), false);
        }
    }

    /** Adds Smart Moving lines to the left side of the F3 screen when {@code move.debug.state} is on. */
    public static void appendDebugInfo(List<String> lines) {
        if (!config().debugState.get() || session == null) {
            return;
        }
        lines.add("");
        lines.add("[Smart Moving] enabled=" + config().enabled + " server=" + serverPresent
                + " grab=" + GRAB.pressed);
        lines.add("[Smart Moving] self: " + LOCAL_STATE.describe());
        Entity target = Minecraft.getInstance().crosshairPickEntity;
        if (target instanceof AbstractClientPlayer other) {
            MovingState state = OTHER_STATES.get(other.getId());
            lines.add("[Smart Moving] " + other.getName().getString() + ": "
                    + (state == null ? "no state received" : state.describe()));
        }
    }
}
