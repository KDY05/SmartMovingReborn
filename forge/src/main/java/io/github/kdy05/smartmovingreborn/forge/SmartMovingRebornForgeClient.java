package io.github.kdy05.smartmovingreborn.forge;

import io.github.kdy05.smartmovingreborn.SmartMovingReborn;
import io.github.kdy05.smartmovingreborn.client.SmartMovingClient;
import io.github.kdy05.smartmovingreborn.input.KeyBindings;
import io.github.kdy05.smartmovingreborn.render.SmartMovingHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.network.event.EventNetworkChannel;

/** Client-only Forge wiring. Never load this class on a dedicated server. */
public final class SmartMovingRebornForgeClient {
    private SmartMovingRebornForgeClient() {
    }

    static void init(IEventBus modBus) {
        SmartMovingReborn.initClient();
        modBus.addListener((RegisterKeyMappingsEvent event) -> KeyBindings.ALL.forEach(event::register));
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                SmartMovingClient.tick(Minecraft.getInstance());
            }
        });
        MinecraftForge.EVENT_BUS.addListener((RenderGuiEvent.Post event) -> SmartMovingHud.render(event.getGuiGraphics()));
    }

    public static boolean isServerPresent() {
        return canSendToServer(SmartMovingRebornForgeNetwork.STATE);
    }

    public static boolean canSendToServer(EventNetworkChannel channel) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection != null && channel.isRemotePresent(connection.getConnection());
    }

    public static void sendToServer(ResourceLocation id, FriendlyByteBuf buf) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            connection.send(new ServerboundCustomPayloadPacket(id, buf));
        }
    }
}
