package io.github.kdy05.smartmovingreborn.config;

import java.util.List;

/** A string restricted to a fixed set of choices. */
public final class StringProperty extends Property<String> {
    private final String defaultValue;
    private final List<String> choices;

    public StringProperty(String key, String defaultValue, String... choices) {
        super(key);
        this.defaultValue = defaultValue;
        this.choices = List.of(choices);
        reset();
    }

    public boolean is(String choice) {
        return value.equals(choice);
    }

    @Override
    public String defaultValue() {
        return defaultValue;
    }

    @Override
    public String parse(String raw) {
        for (String choice : choices) {
            if (choice.equalsIgnoreCase(raw)) {
                return choice;
            }
        }
        throw new IllegalArgumentException("expected one of " + String.join(", ", choices));
    }

    @Override
    public String format(String value) {
        return value;
    }
}
