package io.github.kdy05.smartmovingreborn.neoforge;

import io.github.kdy05.smartmovingreborn.SmartMovingReborn;
import io.github.kdy05.smartmovingreborn.client.SmartMovingClient;
import io.github.kdy05.smartmovingreborn.input.KeyBindings;
import io.github.kdy05.smartmovingreborn.network.SoundMessage;
import io.github.kdy05.smartmovingreborn.network.StateMessage;
import io.github.kdy05.smartmovingreborn.render.SmartMovingHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

/** Client-only NeoForge wiring. Never load this class on a dedicated server. */
public final class SmartMovingRebornNeoForgeClient {
    private SmartMovingRebornNeoForgeClient() {
    }

    static void init(IEventBus modBus) {
        SmartMovingReborn.initClient();
        modBus.addListener((RegisterKeyMappingsEvent event) -> KeyBindings.ALL.forEach(event::register));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> SmartMovingClient.tick(Minecraft.getInstance()));
        NeoForge.EVENT_BUS.addListener((RenderGuiEvent.Post event) -> SmartMovingHud.render(event.getGuiGraphics()));
    }

    public static boolean isServerPresent() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection != null && connection.hasChannel(StateMessage.TYPE);
    }

    public static void sendToServer(StateMessage message) {
        PacketDistributor.sendToServer(message);
    }

    public static void sendToServer(SoundMessage message) {
        PacketDistributor.sendToServer(message);
    }
}
