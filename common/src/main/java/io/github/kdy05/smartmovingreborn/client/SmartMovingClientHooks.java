package io.github.kdy05.smartmovingreborn.client;

import io.github.kdy05.smartmovingreborn.config.SmartMovingConfig;
import io.github.kdy05.smartmovingreborn.input.Buttons;
import io.github.kdy05.smartmovingreborn.logic.ClientHooks;
import io.github.kdy05.smartmovingreborn.render.SlideParticles;
import io.github.kdy05.smartmovingreborn.render.SmartMovingRender;
import io.github.kdy05.smartmovingreborn.render.SwimParticles;
import io.github.kdy05.smartmovingreborn.state.MovingState;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;

/** The client's side of the movement logic, set at start-up by {@link SmartMovingClient#init}. */
final class SmartMovingClientHooks implements ClientHooks {
    @Override
    public boolean isActive() {
        return SmartMovingClient.isActive();
    }

    @Override
    public MovingState localState() {
        return SmartMovingClient.LOCAL_STATE;
    }

    @Override
    public MovingState otherState(int entityId) {
        return SmartMovingClient.getOtherState(entityId);
    }

    @Override
    public Buttons updateButtons(Player player) {
        SmartMovingClient.updateButtons(player);
        return SmartMovingClient.BUTTONS;
    }

    @Override
    public boolean isForwardPressed(Player player) {
        return SmartMovingClient.isForwardPressed(player);
    }

    @Override
    public boolean isMovePressed(Player player) {
        return SmartMovingClient.isMovePressed(player);
    }

    @Override
    public boolean isSneakInput(Player player) {
        return SmartMovingClient.isSneakInput(player);
    }

    @Override
    public boolean isJumpPressed(Player player) {
        return SmartMovingClient.isJumpPressed(player);
    }

    @Override
    public boolean isControlledCamera(Player player) {
        return SmartMovingClient.isControlledCamera(player);
    }

    @Override
    public boolean isInputBlocked() {
        return SmartMovingClient.isInputBlocked();
    }

    @Override
    public void sendStateNow(Player player) {
        SmartMovingClient.sendStateNow(player);
    }

    @Override
    public void playSound(Player player, SoundEvent sound, float volume, float pitch) {
        SmartMovingClient.playSound(player, sound, volume, pitch);
    }

    @Override
    public void offsetCameraEyeHeight(Player player, float dy) {
        SmartMovingClient.offsetCameraEyeHeight(player, dy);
    }

    @Override
    public void startWallJump(Player player, float yaw) {
        SmartMovingRender.startWallJump(player, yaw);
    }

    @Override
    public void startClimbBackJump(Player player, boolean head) {
        SmartMovingRender.startClimbBackJump(player, head);
    }

    @Override
    public void slideParticles(Player player, double motionX, double motionZ, SmartMovingConfig config) {
        SlideParticles.spawn(player, motionX, motionZ, config);
    }

    @Override
    public void swimParticles(Player player, double motionX, double motionZ, SmartMovingConfig config) {
        SwimParticles.spawn(player, motionX, motionZ, config);
    }
}
