package io.github.kdy05.smartmovingreborn.client;

import io.github.kdy05.smartmovingreborn.SmartMovingReborn;
import io.github.kdy05.smartmovingreborn.config.SmartMovingClientConfig;
import io.github.kdy05.smartmovingreborn.input.Buttons;
import io.github.kdy05.smartmovingreborn.input.KeyBindings;
import io.github.kdy05.smartmovingreborn.logic.MovingController;
import io.github.kdy05.smartmovingreborn.mixin.client.CameraAccessor;
import io.github.kdy05.smartmovingreborn.network.ConfigSyncMessage;
import io.github.kdy05.smartmovingreborn.network.Network;
import io.github.kdy05.smartmovingreborn.network.SoundMessage;
import io.github.kdy05.smartmovingreborn.network.StateMessage;
import io.github.kdy05.smartmovingreborn.network.StateRelayMessage;
import io.github.kdy05.smartmovingreborn.render.ClimbDebug;
import io.github.kdy05.smartmovingreborn.render.SlideParticles;
import io.github.kdy05.smartmovingreborn.render.SmartMovingRender;
import io.github.kdy05.smartmovingreborn.render.SwimParticles;
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

/**
 * Client-side per-connection bookkeeping: server detection, the server's configuration, the F9 toggle, and state
 * exchange.
 */
public final class SmartMovingClient {
    /** How long to wait after joining for the server's channel before assuming the server lacks the mod. */
    private static final int SERVER_DETECTION_TICKS = 100;

    public static final Buttons BUTTONS = new Buttons();
    /**
     * The own player's state. It outlives connections: the own player's logic holds it from the {@code LocalPlayer}
     * constructor on, which runs before a new connection is noticed here.
     */
    public static final MovingState LOCAL_STATE = new MovingState();

    /** The current connection's bookkeeping, replaced for each new one so that nothing carries over. */
    private static Session session = new Session(null);

    /** One connection's bookkeeping. */
    private static final class Session {
        final ClientPacketListener connection;
        int ticks;
        boolean serverPresent;
        boolean serverAbsenceReported;
        /** Whether the server answered the first state with its configuration ({@link ConfigSyncMessage}). */
        boolean configKnown;
        /** The own movement rules and F9 state while the server's are in force, to restore afterwards; else null. */
        Map<String, String> localRules;
        boolean localEnabled;
        long sentState;
        boolean stateSent;
        int lastPlayerCount;
        int lastPlayerId = -1;
        final Map<Integer, MovingState> otherStates = new HashMap<>();

        Session(ClientPacketListener connection) {
            this.connection = connection;
        }
    }

    private SmartMovingClient() {
    }

    /** At start-up on the physical client, from each loader's client initialization. */
    public static void init() {
        SmartMovingReborn.initClient();
        MovingController.setClient(new SmartMovingClientHooks());
    }

    private static SmartMovingClientConfig config() {
        return SmartMovingReborn.CLIENT_CONFIG;
    }

    /** Whether Smart Moving should act: enabled with F9, supported by the server and configured by it. */
    public static boolean isActive() {
        return config().enabled && session.serverPresent && session.configKnown;
    }

    /** Called at the end of every client tick. */
    public static void tick(Minecraft minecraft) {
        ClientPacketListener connection = minecraft.getConnection();
        if (connection != session.connection) {
            startSession(connection);
        }
        LocalPlayer player = minecraft.player;
        if (connection == null || player == null) {
            return;
        }
        session.ticks++;

        while (KeyBindings.TOGGLE.consumeClick()) {
            config().enabled = !config().enabled;
            chat(player, config().enabled ? "enabled" : "disabled");
        }
        if (!session.serverPresent) {
            session.serverPresent = Network.isServerPresent();
            if (!session.serverPresent) {
                if (!session.serverAbsenceReported && session.ticks > SERVER_DETECTION_TICKS) {
                    session.serverAbsenceReported = true;
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
        session.otherStates.keySet().removeIf(id -> minecraft.level.getEntity(id) == null);
        for (AbstractClientPlayer other : minecraft.level.players()) {
            SmartMovingRender.tickPlayer(other);
            MovingState state = session.otherStates.get(other.getId());
            if (state != null && state.sliding) {
                SlideParticles.spawn(other, other.getX() - other.xo, other.getZ() - other.zo, config());
            }
            if (state != null && state.swimming) {
                SwimParticles.spawn(other, other.getX() - other.xo, other.getZ() - other.zo, config());
            }
        }
    }

    private static void startSession(ClientPacketListener connection) {
        restoreLocalRules();
        session = new Session(connection);
        MovingController.resetSelf();
        LOCAL_STATE.clear();
    }

    /**
     * The moves in {@link #LOCAL_STATE} are kept by the movement logic during the player's tick; only the
     * input and size flags are filled in here.
     */
    private static void updateLocalState(Minecraft minecraft, LocalPlayer player) {
        if (!isActive()) {
            MovingController.disableSelf();
            LOCAL_STATE.clear();
            return;
        }
        LOCAL_STATE.sneakButton = minecraft.options.keyShift.isDown();
        LOCAL_STATE.jumping = player.input.jumping;
        updateSizeFlags(player);
    }

    private static void updateSizeFlags(Player player) {
        LOCAL_STATE.small = player.getBbHeight() < 1.0f;
        LOCAL_STATE.smallFlying = MovingController.smallFlying();
    }

    /**
     * Sends the state when it changed, and also when a player appeared in view so that they learn the
     * current state (the original's heuristic: the player list grew or its last entry changed).
     */
    private static void sendState(Minecraft minecraft) {
        List<AbstractClientPlayer> players = minecraft.level.players();
        int count = players.size();
        int lastId = count == 0 ? -1 : players.get(count - 1).getId();
        boolean newPlayer = count > session.lastPlayerCount
                || (count == session.lastPlayerCount && lastId != session.lastPlayerId);
        session.lastPlayerCount = count;
        session.lastPlayerId = lastId;
        sendState(newPlayer);
    }

    /**
     * Sends the state right away, in the middle of the own player's tick, when it changed. The movement packet
     * of this tick follows it, so the server already applies the new pose to that movement.
     */
    public static void sendStateNow(Player player) {
        if (!isActive()) {
            return;
        }
        updateSizeFlags(player);
        sendState(false);
    }

    /** Sends the state if it changed since it was last sent, or if {@code always}. */
    private static void sendState(boolean always) {
        long state = StatePacketCodec.encode(LOCAL_STATE);
        if (always || !session.stateSent || state != session.sentState) {
            Network.sendToServer(new StateMessage(state));
            session.sentState = state;
            session.stateSent = true;
        }
    }

    /**
     * Runs on the client thread. Puts the server's movement rules in force for this connection, keeping the own
     * ones to restore when it ends, or keeps the own ones.
     */
    public static void onConfigSync(ConfigSyncMessage message) {
        session.configKnown = true;
        if (!message.enforced()) {
            restoreLocalRules();
            return;
        }
        if (session.localRules == null) {
            session.localRules = config().writeMovementRules();
            session.localEnabled = config().enabled;
        }
        config().loadMovementRules(message.rules())
                .forEach(warning -> SmartMovingReborn.LOGGER.warn("Server configuration: {}", warning));
        // The original used the server's switch too, so Smart Moving turned off with F9 is on again (F9 can
        // still turn it off).
        config().enabled = true;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && config().configChatServer.get()) {
            chat(player, "server_config");
        }
    }

    private static void restoreLocalRules() {
        if (session.localRules == null) {
            return;
        }
        config().loadMovementRules(session.localRules);
        config().enabled = session.localEnabled;
        session.localRules = null;
    }

    /** Runs on the client thread. */
    public static void onStateRelay(StateRelayMessage message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && message.entityId() == minecraft.player.getId()) {
            return;
        }
        StatePacketCodec.decode(message.state(),
                session.otherStates.computeIfAbsent(message.entityId(), id -> new MovingState()));
    }

    /**
     * Updates the buttons from this tick's input, at the start of the own player's action update like the
     * original ({@code updateEntityActionState}), so that a press counts in the tick it happens. Vanilla has
     * read the movement input just before.
     */
    public static void updateButtons(Player player) {
        Input input = ((LocalPlayer) player).input;
        BUTTONS.grab.update(KeyBindings.GRAB.isDown());
        BUTTONS.sneak.update(input.shiftKeyDown);
        BUTTONS.jump.update(input.jumping);
        BUTTONS.sprint.update(Minecraft.getInstance().options.keySprint.isDown());
        BUTTONS.left.update(input.left);
        BUTTONS.right.update(input.right);
        BUTTONS.back.update(input.down);
    }

    /** Whether the own player's current movement input goes forward. */
    public static boolean isForwardPressed(Player player) {
        return ((LocalPlayer) player).input.forwardImpulse > 0;
    }

    /** Whether the own player's current movement input moves in any direction. */
    public static boolean isMovePressed(Player player) {
        Input input = ((LocalPlayer) player).input;
        return input.forwardImpulse != 0 || input.leftImpulse != 0;
    }

    /** Whether the own player's current movement input sneaks, which vanilla sinks in water by. */
    public static boolean isSneakInput(Player player) {
        return ((LocalPlayer) player).input.shiftKeyDown;
    }

    /** Whether the camera follows the own player, which vanilla's flying needs to take jump and sneak. */
    public static boolean isControlledCamera(Player player) {
        return Minecraft.getInstance().getCameraEntity() == player;
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
        if (session.serverPresent) {
            Network.sendSoundToServer(new SoundMessage(sound.getLocation(), volume, pitch));
        }
    }

    /** The last state received for another player, or null. */
    public static MovingState getOtherState(int entityId) {
        return session.otherStates.get(entityId);
    }

    private static void chat(LocalPlayer player, String key) {
        if (config().configChat.get()) {
            player.displayClientMessage(Component.translatable("chat." + SmartMovingReborn.MOD_ID + "." + key), false);
        }
    }

    /** Adds Smart Moving lines to the left side of the F3 screen for {@code move.debug.state} and {@code move.debug.climb}. */
    public static void appendDebugInfo(List<String> lines) {
        if (session.connection == null) {
            return;
        }
        ClimbDebug.appendDebugInfo(lines);
        if (!config().debugState.get()) {
            return;
        }
        lines.add("");
        lines.add("[Smart Moving] enabled=" + config().enabled + " server=" + session.serverPresent
                + " config=" + (!session.configKnown ? "pending" : session.localRules != null ? "server" : "local")
                + " grab=" + BUTTONS.grab.pressed);
        lines.add("[Smart Moving] self: " + LOCAL_STATE.describe());
        Entity target = Minecraft.getInstance().crosshairPickEntity;
        if (target instanceof AbstractClientPlayer other) {
            MovingState state = session.otherStates.get(other.getId());
            lines.add("[Smart Moving] " + other.getName().getString() + ": "
                    + (state == null ? "no state received" : state.describe()));
        }
    }
}
