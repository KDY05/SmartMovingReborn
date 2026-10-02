package io.github.kdy05.smartmovingreborn.client;

import io.github.kdy05.smartmovingreborn.SmartMovingReborn;
import io.github.kdy05.smartmovingreborn.config.SmartMovingClientConfig;
import io.github.kdy05.smartmovingreborn.input.Button;
import io.github.kdy05.smartmovingreborn.input.KeyBindings;
import io.github.kdy05.smartmovingreborn.logic.MovingController;
import io.github.kdy05.smartmovingreborn.mixin.client.CameraAccessor;
import io.github.kdy05.smartmovingreborn.network.Network;
import io.github.kdy05.smartmovingreborn.network.SoundMessage;
import io.github.kdy05.smartmovingreborn.network.StateMessage;
import io.github.kdy05.smartmovingreborn.network.StateRelayMessage;
import io.github.kdy05.smartmovingreborn.render.ClimbDebug;
import io.github.kdy05.smartmovingreborn.render.SlideParticles;
import io.github.kdy05.smartmovingreborn.render.SmartMovingRender;
import io.github.kdy05.smartmovingreborn.state.MovingState;
import io.github.kdy05.smartmovingreborn.state.StatePacketCodec;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
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
        if (isActive()) {
            ClimbDebug.tick(player, LOCAL_STATE, config());
        } else {
            ClimbDebug.reset();
        }
        sendState(minecraft);
        OTHER_STATES.keySet().removeIf(id -> minecraft.level.getEntity(id) == null);
        for (AbstractClientPlayer other : minecraft.level.players()) {
            SmartMovingRender.tickPlayer(other);
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

    /**
     * Sends the state right away, in the middle of the own player's tick, when it changed. The movement packet
     * of this tick follows it, so the server already applies the new pose to that movement.
     */
    public static void sendStateNow(Player player) {
        if (!isActive()) {
            return;
        }
        LOCAL_STATE.small = player.getBbHeight() < 1.0f;
        long state = StatePacketCodec.encode(LOCAL_STATE);
        if (!stateSent || state != sentState) {
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

    /**
     * Updates the buttons from this tick's input, at the start of the own player's action update like the
     * original ({@code updateEntityActionState}), so that a press counts in the tick it happens. Vanilla has
     * read the movement input just before.
     */
    public static void updateButtons(Player player) {
        Input input = ((LocalPlayer) player).input;
        GRAB.update(KeyBindings.GRAB.isDown());
        SNEAK.update(input.shiftKeyDown);
        JUMP.update(input.jumping);
        SPRINT.update(Minecraft.getInstance().options.keySprint.isDown());
        LEFT.update(input.left);
        RIGHT.update(input.right);
        BACK.update(input.down);
    }

    /** Whether the own player's current movement input goes forward. */
    public static boolean isForwardPressed(Player player) {
        return ((LocalPlayer) player).input.forwardImpulse > 0;
    }

    /** Whether a screen takes the input, which keeps a climber hanging on. */
    public static boolean isInputBlocked() {
        return Minecraft.getInstance().screen != null;
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

    /**
     * Plays a sound of the own player's movement, and has the server play it to the players around
     * ({@code playSound}).
     */
    public static void playSound(Player player, SoundEvent sound, float volume, float pitch) {
        player.playSound(sound, volume, pitch);
        if (serverPresent) {
            Network.sendSoundToServer(new SoundMessage(sound.getLocation(), volume, pitch));
        }
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

    /** Adds Smart Moving lines to the left side of the F3 screen for {@code move.debug.state} and {@code move.debug.climb}. */
    public static void appendDebugInfo(List<String> lines) {
        if (session == null) {
            return;
        }
        ClimbDebug.appendDebugInfo(lines);
        if (!config().debugState.get()) {
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
