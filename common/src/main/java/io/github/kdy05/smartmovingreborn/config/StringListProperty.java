package io.github.kdy05.smartmovingreborn.config;

import java.util.ArrayList;
import java.util.List;

/**
 * A comma-separated list of strings. Commas inside {@code [...]} do not split, so block state
 * predicates like {@code #minecraft:trapdoors[half=bottom,open=false]} stay one entry.
 */
public final class StringListProperty extends Property<List<String>> {
    private final List<String> defaultValue;

    public StringListProperty(String key, String... defaultValue) {
        super(key);
        this.defaultValue = List.of(defaultValue);
        reset();
    }

    @Override
    public List<String> defaultValue() {
        return defaultValue;
    }

    @Override
    public List<String> parse(String raw) {
        List<String> entries = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth = Math.max(0, depth - 1);
            } else if (c == ',' && depth == 0) {
                addEntry(entries, raw.substring(start, i));
                start = i + 1;
            }
        }
        addEntry(entries, raw.substring(start));
        return List.copyOf(entries);
    }

    private static void addEntry(List<String> entries, String entry) {
        String trimmed = entry.trim();
        if (!trimmed.isEmpty()) {
            entries.add(trimmed);
        }
    }

    @Override
    public String format(List<String> value) {
        return String.join(", ", value);
    }
}
