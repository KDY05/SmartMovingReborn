package io.github.kdy05.smartmovingreborn.fabric;

import io.github.kdy05.smartmovingreborn.SmartMovingReborn;
import io.github.kdy05.smartmovingreborn.client.SmartMovingClient;
import io.github.kdy05.smartmovingreborn.input.KeyBindings;
import io.github.kdy05.smartmovingreborn.network.Network;
import io.github.kdy05.smartmovingreborn.network.StateRelayMessage;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class SmartMovingRebornFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        SmartMovingReborn.initClient();
        KeyBindings.ALL.forEach(KeyBindingHelper::registerKeyBinding);
        ClientPlayNetworking.registerGlobalReceiver(Network.RELAY_ID, (client, handler, buf, responseSender) -> {
            StateRelayMessage message = StateRelayMessage.read(buf);
            client.execute(() -> SmartMovingClient.onStateRelay(message));
        });
        ClientTickEvents.END_CLIENT_TICK.register(SmartMovingClient::tick);
    }
}
