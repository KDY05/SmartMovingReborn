package io.github.kdy05.smartmovingreborn.logic.swim;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.world.entity.player.Player;

public final class SwimSpeed {
    private SwimSpeed() {
    }

    /** Forge's swim speed attribute ({@code ForgeMod.SWIM_SPEED}), which scales vanilla's water acceleration; 1 on Fabric. */
    @ExpectPlatform
    public static double of(Player player) {
        throw new AssertionError();
    }
}
