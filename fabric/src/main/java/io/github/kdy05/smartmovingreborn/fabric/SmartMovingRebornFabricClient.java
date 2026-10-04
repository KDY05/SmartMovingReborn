package io.github.kdy05.smartmovingreborn.fabric;

import io.github.kdy05.smartmovingreborn.SmartMovingReborn;
import io.github.kdy05.smartmovingreborn.client.SmartMovingClient;
import io.github.kdy05.smartmovingreborn.input.KeyBindings;
import io.github.kdy05.smartmovingreborn.network.ConfigSyncMessage;
import io.github.kdy05.smartmovingreborn.network.StateRelayMessage;
import io.github.kdy05.smartmovingreborn.render.SmartMovingHud;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class SmartMovingRebornFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        SmartMovingReborn.initClient();
        KeyBindings.ALL.forEach(KeyBindingHelper::registerKeyBinding);
        // Receivers run on the client thread.
        ClientPlayNetworking.registerGlobalReceiver(StateRelayMessage.TYPE,
                (message, context) -> SmartMovingClient.onStateRelay(message));
        ClientPlayNetworking.registerGlobalReceiver(ConfigSyncMessage.TYPE,
                (message, context) -> SmartMovingClient.onConfigSync(message));
        ClientTickEvents.END_CLIENT_TICK.register(SmartMovingClient::tick);
        HudRenderCallback.EVENT.register((graphics, deltaTracker) -> SmartMovingHud.render(graphics));
    }
}
