package io.github.kdy05.smartmovingreborn.network;

import net.minecraft.util.RandomSource;

/**
 * The sounds a client plays for its own player's movement and has the server play to the players around
 * ({@link SoundMessage}). The server lets each through at most as loud as the client plays it.
 */
public final class MovementSounds {
    /** A climbing step: the held block's step sound at this share of its volume ({@code afterMoveEntity}). */
    public static final float CLIMB_STEP_VOLUME = 0.15f;
    /** Vanilla's splash, while swimming and on jumping out of shallow water. */
    public static final float SPLASH_VOLUME = 0.05f;

    private MovementSounds() {
    }

    /** The splash's pitch: 1, up to 0.4 higher or lower. */
    public static float splashPitch(RandomSource random) {
        return 1 + (random.nextFloat() - random.nextFloat()) * 0.4f;
    }
}
