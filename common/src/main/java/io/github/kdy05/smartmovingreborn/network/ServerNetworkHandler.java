package io.github.kdy05.smartmovingreborn.network;

import io.github.kdy05.smartmovingreborn.SmartMovingReborn;
import io.github.kdy05.smartmovingreborn.mixin.server.ChunkMapAccessor;
import io.github.kdy05.smartmovingreborn.mixin.server.TrackedEntityAccessor;
import io.github.kdy05.smartmovingreborn.state.MovingState;
import io.github.kdy05.smartmovingreborn.state.StatePacketCodec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public final class ServerNetworkHandler {
    /** The last state each player's client sent. Server thread only. */
    private static final Map<ServerPlayer, Received> STATES = new WeakHashMap<>();
    /**
     * The connections whose client got the server's configuration. Keyed by connection, not player, because
     * respawning makes a new {@code ServerPlayer} on the same connection. Server thread only.
     */
    private static final Set<ServerGamePacketListenerImpl> CONFIGURED = Collections.newSetFromMap(new WeakHashMap<>());
    /**
     * The sounds a client plays through {@code SmartMovingClient.playSound}, each with the loudest volume it uses:
     * every block state's step sound at a climbing step's volume, and the swimming splash. Built on first use,
     * once the block registry is complete. Server thread only.
     */
    private static Map<ResourceLocation, Float> maxSoundVolumes;

    private ServerNetworkHandler() {
    }

    /**
     * A state a client sent, decoded, and as the client encoded it, for relaying to players that start tracking
     * the sender.
     */
    private static final class Received {
        final MovingState state = new MovingState();
        long encoded;
    }

    /** The last state {@code player}'s client sent, or null. */
    public static MovingState getState(ServerPlayer player) {
        Received received = STATES.get(player);
        return received == null ? null : received.state;
    }

    /**
     * Runs on the server thread. Answers the sender's first state with the server's configuration, stores the
     * state and relays it to every modded client tracking the sender.
     */
    public static void onState(ServerPlayer sender, StateMessage message) {
        // The original answered a client's first state with the server's configuration (initialize).
        if (CONFIGURED.add(sender.connection) && Network.canSendTo(sender)) {
            Network.sendTo(sender, ConfigSyncMessage.of(SmartMovingReborn.SERVER_CONFIG));
        }
        Received received = STATES.computeIfAbsent(sender, player -> new Received());
        StatePacketCodec.decode(message.state(), received.state);
        received.encoded = message.state();
        MovingState state = received.state;
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
     * Runs on the server thread when {@code viewer} starts tracking {@code entity}, after the packets that add it.
     * Sends the last state of a player entity, so that a viewer whose tracking range is not the player's own
     * (1.21.1 tracks by each viewer's view distance) need not wait for that player's next change.
     */
    public static void onStartTracking(Entity entity, ServerPlayer viewer) {
        Received received = entity instanceof ServerPlayer player ? STATES.get(player) : null;
        if (received != null && Network.canSendTo(viewer)) {
            Network.sendTo(viewer, new StateRelayMessage(entity.getId(), received.encoded));
        }
    }

    /**
     * Runs on the server thread. Plays a climbing or swimming sound of the sender's to the players around it, but
     * not to the sender, which played it already. Only the sounds a client plays for these moves, while the sender
     * climbs, swims or dips, at most as loud as the client plays them.
     */
    public static void onSound(ServerPlayer sender, SoundMessage message) {
        MovingState state = getState(sender);
        Float maxVolume = maxSoundVolumes().get(message.sound());
        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(message.sound());
        if (state == null || !(state.climbing || state.ceilingClimbing || state.swimming || state.dipping)
                || maxVolume == null || sound == null) {
            return;
        }
        sender.serverLevel().playSound(sender, sender.getX(), sender.getY(), sender.getZ(), sound,
                SoundSource.PLAYERS, Mth.clamp(message.volume(), 0, maxVolume), Mth.clamp(message.pitch(), 0.5f, 2));
    }

    private static Map<ResourceLocation, Float> maxSoundVolumes() {
        if (maxSoundVolumes == null) {
            Map<ResourceLocation, Float> volumes = new HashMap<>();
            // Every state, not just the default ones: a block may give its states different sound types.
            for (BlockState blockState : Block.BLOCK_STATE_REGISTRY) {
                SoundType type = blockState.getSoundType();
                volumes.merge(type.getStepSound().getLocation(), type.getVolume() * MovementSounds.CLIMB_STEP_VOLUME,
                        Math::max);
            }
            volumes.merge(SoundEvents.PLAYER_SPLASH.getLocation(), MovementSounds.SPLASH_VOLUME, Math::max);
            maxSoundVolumes = volumes;
        }
        return maxSoundVolumes;
    }
}
