package io.github.kdy05.smartmovingreborn.fabric;

import io.github.kdy05.smartmovingreborn.SmartMovingReborn;
import net.fabricmc.api.ClientModInitializer;

public final class SmartMovingRebornFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        SmartMovingReborn.initClient();
    }
}
