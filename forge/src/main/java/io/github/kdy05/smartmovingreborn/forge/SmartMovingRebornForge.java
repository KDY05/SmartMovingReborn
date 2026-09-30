package io.github.kdy05.smartmovingreborn.forge;

import io.github.kdy05.smartmovingreborn.SmartMovingReborn;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(SmartMovingReborn.MOD_ID)
public final class SmartMovingRebornForge {
    public SmartMovingRebornForge() {
        SmartMovingReborn.init();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            SmartMovingReborn.initClient();
        }
    }
}
