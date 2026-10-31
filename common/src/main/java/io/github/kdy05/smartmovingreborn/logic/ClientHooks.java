package io.github.kdy05.smartmovingreborn.logic;

import io.github.kdy05.smartmovingreborn.config.SmartMovingConfig;
import io.github.kdy05.smartmovingreborn.input.Buttons;
import io.github.kdy05.smartmovingreborn.state.MovingState;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;

/**
 * What the movement logic asks of the client, which a dedicated server lacks: whether Smart Moving acts, the own
 * player's input and state, other players' states, and what the own player's moves set off on the client. The
 * client sets it at start-up ({@link MovingController#setClient}). The logic reaches the client only through it,
 * so that no class it loads on a dedicated server refers to a client class.
 */
public interface ClientHooks {
    /** Whether Smart Moving should act: enabled with F9, supported by the server and configured by it. */
    boolean isActive();

    /** The own player's state, which is also sent to the server and rendered. */
    MovingState localState();

    /** The last state received for another player, or null. */
    MovingState otherState(int entityId);

    // The own player's input

    /** Updates the buttons from this tick's input and returns them. */
    Buttons updateButtons(Player player);

    /** Whether the current movement input goes forward. */
    boolean isForwardPressed(Player player);

    /** Whether the current movement input moves in any direction. */
    boolean isMovePressed(Player player);

    /** Whether the current movement input sneaks, which vanilla sinks in water by. */
    boolean isSneakInput(Player player);

    /** Whether the current movement input jumps. */
    boolean isJumpPressed(Player player);

    /** Whether the camera follows the player, which vanilla's flying needs to take jump and sneak. */
    boolean isControlledCamera(Player player);

    /** Whether a screen takes the input, which keeps a climber hanging on. */
    boolean isInputBlocked();

    // What the own player's moves set off

    /** Sends the own player's state right away when it changed, ahead of this tick's movement. */
    void sendStateNow(Player player);

    /** Plays a sound of the own player's movement, also to the players around. */
    void playSound(Player player, SoundEvent sound, float volume, float pitch);

    /** Lowers the camera's eased eye height by {@code dy}, so that moving the player up by it keeps the view. */
    void offsetCameraEyeHeight(Player player, float dy);

    /** The player wall jumped towards {@code yaw} (degrees); its body faces it at once. */
    void startWallJump(Player player, float yaw);

    /** The player jumped back off a climbing hold, as a head jump or not; its body starts turning round. */
    void startClimbBackJump(Player player, boolean head);

    /** The particles of a slide for this tick's horizontal motion. */
    void slideParticles(Player player, double motionX, double motionZ, SmartMovingConfig config);

    /** The particles of swimming for this tick's horizontal motion. */
    void swimParticles(Player player, double motionX, double motionZ, SmartMovingConfig config);
}
