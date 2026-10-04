package io.github.kdy05.smartmovingreborn.logic.swim.neoforge;

import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForgeMod;

public final class SwimSpeedImpl {
    private SwimSpeedImpl() {
    }

    public static double of(Player player) {
        return player.getAttributeValue(NeoForgeMod.SWIM_SPEED);
    }
}
