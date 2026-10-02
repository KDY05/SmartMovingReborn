package io.github.kdy05.smartmovingreborn.world;

import io.github.kdy05.smartmovingreborn.SmartMovingReborn;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The blocks players can climb along as a ceiling ({@code move.climb.ceiling.configuration}, the original's
 * {@code supportsCeilingClimbing}). Each entry is a block id or a {@code #}tag id, optionally followed by
 * {@code [property=value,...]}; the {@code minecraft:} namespace may be left out. A block matches an entry when
 * it is that block or in that tag and has every listed property at the listed value.
 */
public final class CeilingBlocks {
    /** One entry of the list. */
    record Rule(ResourceLocation id, boolean tag, Map<String, String> properties) {
        /**
         * @param blockId  the block's id
         * @param inTag    whether the block is in a block tag
         * @param property the block's value of a property by name, null if it has no such property
         */
        boolean matches(ResourceLocation blockId, Predicate<ResourceLocation> inTag,
                        Function<String, String> property) {
            if (tag ? !inTag.test(id) : !id.equals(blockId)) {
                return false;
            }
            for (Map.Entry<String, String> entry : properties.entrySet()) {
                if (!entry.getValue().equals(property.apply(entry.getKey()))) {
                    return false;
                }
            }
            return true;
        }
    }

    private static List<String> cachedEntries;
    private static CeilingBlocks cached;

    private final List<Rule> rules;

    private CeilingBlocks(List<Rule> rules) {
        this.rules = rules;
    }

    /** The parsed list for these config entries, parsed again (and warned about) only when they change. */
    public static CeilingBlocks of(List<String> entries) {
        if (entries != cachedEntries) {
            cached = new CeilingBlocks(parse(entries,
                    warning -> SmartMovingReborn.LOGGER.warn("move.climb.ceiling.configuration: {}", warning)));
            cachedEntries = entries;
        }
        return cached;
    }

    /** The rules of {@code entries}, skipping malformed ones with a warning. */
    static List<Rule> parse(List<String> entries, Consumer<String> warn) {
        List<Rule> rules = new ArrayList<>();
        for (String entry : entries) {
            Rule rule = parseEntry(entry);
            if (rule == null) {
                warn.accept("ignored malformed entry '" + entry + "'");
            } else {
                rules.add(rule);
            }
        }
        return List.copyOf(rules);
    }

    /** One entry, or null if it is malformed. */
    static Rule parseEntry(String entry) {
        String text = entry.trim();
        boolean tag = text.startsWith("#");
        if (tag) {
            text = text.substring(1);
        }
        Map<String, String> properties = new LinkedHashMap<>();
        int open = text.indexOf('[');
        if (open >= 0) {
            if (!text.endsWith("]")) {
                return null;
            }
            for (String pair : text.substring(open + 1, text.length() - 1).split(",")) {
                int equals = pair.indexOf('=');
                if (equals <= 0) {
                    return null;
                }
                String name = pair.substring(0, equals).trim();
                String value = pair.substring(equals + 1).trim();
                if (name.isEmpty() || value.isEmpty()) {
                    return null;
                }
                properties.put(name, value);
            }
            text = text.substring(0, open);
        }
        ResourceLocation id = text.isEmpty() ? null : ResourceLocation.tryParse(text.trim());
        return id == null ? null : new Rule(id, tag, Map.copyOf(properties));
    }

    /** Whether {@code state} can be climbed along as a ceiling. */
    public boolean matches(BlockState state) {
        if (rules.isEmpty() || state.isAir()) {
            return false;
        }
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        for (Rule rule : rules) {
            if (rule.matches(blockId, tag -> state.is(TagKey.create(Registries.BLOCK, tag)),
                    name -> propertyValue(state, name))) {
                return true;
            }
        }
        return false;
    }

    private static String propertyValue(BlockState state, String name) {
        Property<?> property = state.getBlock().getStateDefinition().getProperty(name);
        return property == null ? null : valueName(state, property);
    }

    private static <T extends Comparable<T>> String valueName(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }
}
