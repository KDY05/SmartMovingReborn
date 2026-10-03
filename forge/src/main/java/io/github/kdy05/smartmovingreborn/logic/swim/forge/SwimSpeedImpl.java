package io.github.kdy05.smartmovingreborn.logic.swim.forge;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.ForgeMod;

public final class SwimSpeedImpl {
    private SwimSpeedImpl() {
    }

    public static double of(Player player) {
        return player.getAttributeValue(ForgeMod.SWIM_SPEED.get());
    }
}
