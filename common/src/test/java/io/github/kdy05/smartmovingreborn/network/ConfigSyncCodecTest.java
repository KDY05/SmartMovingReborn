package io.github.kdy05.smartmovingreborn.network;

import io.github.kdy05.smartmovingreborn.config.Property;
import io.github.kdy05.smartmovingreborn.config.SmartMovingClientConfig;
import io.github.kdy05.smartmovingreborn.config.SmartMovingConfig;
import io.github.kdy05.smartmovingreborn.config.SmartMovingServerConfig;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConfigSyncCodecTest {
    private static ConfigSyncMessage roundTrip(ConfigSyncMessage message) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        message.write(buf);
        ConfigSyncMessage read = ConfigSyncMessage.read(buf);
        assertEquals(0, buf.readableBytes());
        return read;
    }

    private static SmartMovingServerConfig enforcingServer() {
        SmartMovingServerConfig server = new SmartMovingServerConfig();
        server.serverConfig.set(true);
        server.sprintFactor.set(2f);
        server.climbBase.set(SmartMovingConfig.CLIMB_SMART);
        server.swim.set(false);
        server.ceilingClimbBlocks.set(List.of("minecraft:chain", "#minecraft:trapdoors[open=false]"));
        return server;
    }

    private static Map<String, String> values(List<Property<?>> properties) {
        Map<String, String> values = new LinkedHashMap<>();
        properties.forEach(property -> values.put(property.key(), property.storedString()));
        return values;
    }

    @Test
    void movementRulesAreTheSharedKeysWithoutTheServerSwitch() {
        List<Property<?>> server = new SmartMovingServerConfig().movementRules();
        List<Property<?>> client = new SmartMovingClientConfig().movementRules();
        assertEquals(109, server.size());
        assertEquals(values(server).keySet(), values(client).keySet());
        assertFalse(values(server).containsKey("move.server.config"));
        assertFalse(values(client).containsKey("move.config.chat"));
    }

    @Test
    void enforcedRulesSurviveTheWireAndReplaceOnlyTheClientsMovementRules() {
        SmartMovingServerConfig server = enforcingServer();
        ConfigSyncMessage message = roundTrip(ConfigSyncMessage.of(server));
        assertTrue(message.enforced());
        assertEquals(server.writeMovementRules(), message.rules());

        SmartMovingClientConfig client = new SmartMovingClientConfig();
        client.perspectiveFadeFactor.set(0.3f);
        assertEquals(List.of(), client.loadMovementRules(message.rules()));
        assertEquals(values(server.movementRules()), values(client.movementRules()));
        assertEquals(2f, client.sprintFactor.get());
        assertEquals(SmartMovingConfig.CLIMB_SMART, client.climbBase.get());
        assertFalse(client.swim.get());
        assertEquals(List.of("minecraft:chain", "#minecraft:trapdoors[open=false]"), client.ceilingClimbBlocks.get());
        assertEquals(0.3f, client.perspectiveFadeFactor.get());
    }

    @Test
    void notEnforcingSendsNoRules() {
        ConfigSyncMessage message = roundTrip(ConfigSyncMessage.of(new SmartMovingServerConfig()));
        assertFalse(message.enforced());
        assertTrue(message.rules().isEmpty());
    }

    @Test
    void ownRulesComeBack() {
        SmartMovingClientConfig client = new SmartMovingClientConfig();
        client.crawlFactor.set(0.5f);
        client.dive.set(false);
        Map<String, String> own = client.writeMovementRules();

        client.loadMovementRules(enforcingServer().writeMovementRules());
        assertEquals(0.15f, client.crawlFactor.get());
        client.loadMovementRules(own);
        assertEquals(own, client.writeMovementRules());
        assertEquals(0.5f, client.crawlFactor.get());
        assertFalse(client.dive.get());
    }

    @Test
    void badRulesFallBackLikeAFile() {
        SmartMovingClientConfig client = new SmartMovingClientConfig();
        client.sprintFactor.set(3f);
        client.crawlFactor.set(0.5f);
        Map<String, String> rules = new LinkedHashMap<>(enforcingServer().writeMovementRules());
        rules.remove("move.crawl.factor");
        rules.put("move.sneak.factor", "fast");
        rules.put("move.unknown", "1");

        List<String> warnings = client.loadMovementRules(rules);
        assertEquals(2f, client.sprintFactor.get());
        assertEquals(0.15f, client.crawlFactor.get());
        assertEquals(0.3f, client.sneakFactor.get());
        assertEquals(2, warnings.size(), warnings.toString());
    }
}
