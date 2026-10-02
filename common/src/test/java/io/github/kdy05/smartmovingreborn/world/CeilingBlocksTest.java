package io.github.kdy05.smartmovingreborn.world;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CeilingBlocksTest {
    private static final ResourceLocation IRON_BARS = new ResourceLocation("minecraft", "iron_bars");
    private static final ResourceLocation OAK_TRAPDOOR = new ResourceLocation("minecraft", "oak_trapdoor");
    private static final ResourceLocation TRAPDOORS = new ResourceLocation("minecraft", "trapdoors");

    /** Whether a block with these tags and properties matches {@code entry}. */
    private static boolean matches(String entry, ResourceLocation block, Set<ResourceLocation> tags,
                                   Map<String, String> properties) {
        return CeilingBlocks.parseEntry(entry).matches(block, tags::contains, properties::get);
    }

    @Test
    void blockIdsMatchThatBlockWithOrWithoutTheNamespace() {
        assertTrue(matches("minecraft:iron_bars", IRON_BARS, Set.of(), Map.of()));
        assertTrue(matches("iron_bars", IRON_BARS, Set.of(), Map.of()));
        assertFalse(matches("minecraft:iron_bars", OAK_TRAPDOOR, Set.of(), Map.of()));
    }

    @Test
    void tagsMatchTheirBlocks() {
        assertTrue(matches("#minecraft:trapdoors", OAK_TRAPDOOR, Set.of(TRAPDOORS), Map.of()));
        assertFalse(matches("#minecraft:trapdoors", IRON_BARS, Set.of(), Map.of()));
        // A tag entry does not match a block of the tag's name.
        assertFalse(matches("#minecraft:iron_bars", IRON_BARS, Set.of(), Map.of()));
    }

    @Test
    void propertiesMustAllBePresentWithTheirValues() {
        String entry = "#minecraft:trapdoors[half=bottom, open=false]";
        Set<ResourceLocation> tags = Set.of(TRAPDOORS);
        assertTrue(matches(entry, OAK_TRAPDOOR, tags, Map.of("half", "bottom", "open", "false", "facing", "north")));
        assertFalse(matches(entry, OAK_TRAPDOOR, tags, Map.of("half", "top", "open", "false")));
        assertFalse(matches(entry, OAK_TRAPDOOR, tags, Map.of("half", "bottom", "open", "true")));
        assertFalse(matches(entry, OAK_TRAPDOOR, tags, Map.of("half", "bottom")));
    }

    @Test
    void malformedEntriesAreSkippedWithAWarning() {
        List<String> warnings = new ArrayList<>();
        List<CeilingBlocks.Rule> rules = CeilingBlocks.parse(List.of("minecraft:iron_bars", "Bad Name",
                "#minecraft:trapdoors[half]", "oak_trapdoor[open=false", "#", "stone[=x]"), warnings::add);
        assertEquals(1, rules.size());
        assertEquals(IRON_BARS, rules.get(0).id());
        assertEquals(5, warnings.size());
    }

    @Test
    void theDefaultEntriesParse() {
        List<String> warnings = new ArrayList<>();
        List<CeilingBlocks.Rule> rules = CeilingBlocks.parse(
                List.of("minecraft:iron_bars", "#minecraft:trapdoors[half=bottom,open=false]"), warnings::add);
        assertEquals(2, rules.size());
        assertTrue(warnings.isEmpty());
        assertTrue(rules.get(1).tag());
        assertEquals(Map.of("half", "bottom", "open", "false"), rules.get(1).properties());
    }
}
