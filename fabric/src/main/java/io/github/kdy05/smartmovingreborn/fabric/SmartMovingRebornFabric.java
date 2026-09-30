package io.github.kdy05.smartmovingreborn.fabric;

import io.github.kdy05.smartmovingreborn.SmartMovingReborn;
import net.fabricmc.api.ModInitializer;

public final class SmartMovingRebornFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        SmartMovingReborn.init();
    }
}
