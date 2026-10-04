package io.github.kdy05.smartmovingreborn.neoforge;

import io.github.kdy05.smartmovingreborn.SmartMovingReborn;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

@Mod(SmartMovingReborn.MOD_ID)
public final class SmartMovingRebornNeoForge {
    public SmartMovingRebornNeoForge(IEventBus modBus) {
        SmartMovingReborn.init();
        modBus.addListener(SmartMovingRebornNeoForgeNetwork::register);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            SmartMovingRebornNeoForgeClient.init(modBus);
        }
    }
}
