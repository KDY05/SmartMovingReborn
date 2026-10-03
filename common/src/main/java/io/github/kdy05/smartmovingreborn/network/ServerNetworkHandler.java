package io.github.kdy05.smartmovingreborn.network;

import io.github.kdy05.smartmovingreborn.mixin.server.ChunkMapAccessor;
import io.github.kdy05.smartmovingreborn.mixin.server.TrackedEntityAccessor;
import io.github.kdy05.smartmovingreborn.state.MovingState;
import io.github.kdy05.smartmovingreborn.state.StatePacketCodec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;

import java.util.Map;
import java.util.WeakHashMap;

public final class ServerNetworkHandler {
    /** The last state each player's client sent. Server thread only. */
    private static final Map<ServerPlayer, MovingState> STATES = new WeakHashMap<>();

    private ServerNetworkHandler() {
    }

    /** The last state {@code player}'s client sent, or null. */
    public static MovingState getState(ServerPlayer player) {
        return STATES.get(player);
    }

    /** Runs on the server thread. Stores the sender's state and relays it to every modded client tracking the sender. */
    public static void onState(ServerPlayer sender, StateMessage message) {
        MovingState state = STATES.computeIfAbsent(sender, player -> new MovingState());
        StatePacketCodec.decode(message.state(), state);
        // Shrink right away: the next movement packet may already lead into a one block high gap, and it can
        // be handled before the player's tick updates the pose.
        if (state.smallPose()) {
            sender.setPose(Pose.SWIMMING);
        }

        StateRelayMessage relay = new StateRelayMessage(sender.getId(), message.state());
        Object tracked = ((ChunkMapAccessor) sender.serverLevel().getChunkSource().chunkMap)
                .smartmovingreborn$getEntityMap().get(sender.getId());
        if (tracked == null) {
            return;
        }
        for (ServerPlayerConnection connection : ((TrackedEntityAccessor) tracked).smartmovingreborn$getSeenBy()) {
            ServerPlayer target = connection.getPlayer();
            if (Network.canSendTo(target)) {
                Network.sendTo(target, relay);
            }
        }
    }

    /**
     * Runs on the server thread. Plays a climbing or swimming sound of the sender's to the players around it, but
     * not to the sender, which played it already. Only registered sounds, while the sender climbs, swims or dips,
     * at most at full volume.
     */
    public static void onSound(ServerPlayer sender, SoundMessage message) {
        MovingState state = STATES.get(sender);
        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(message.sound());
        if (state == null || !(state.climbing || state.ceilingClimbing || state.swimming || state.dipping)
                || sound == null) {
            return;
        }
        sender.serverLevel().playSound(sender, sender.getX(), sender.getY(), sender.getZ(), sound,
                SoundSource.PLAYERS, Mth.clamp(message.volume(), 0, 1), Mth.clamp(message.pitch(), 0.5f, 2));
    }
}
