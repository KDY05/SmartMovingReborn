package io.github.kdy05.smartmovingreborn.config;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/** A switch. When any dependency is false, {@link #get()} is false regardless of the stored value. */
public final class BooleanProperty extends Property<Boolean> {
    private final boolean defaultValue;
    private final List<BooleanSupplier> dependencies = new ArrayList<>();

    public BooleanProperty(String key, boolean defaultValue) {
        super(key);
        this.defaultValue = defaultValue;
        reset();
    }

    public BooleanProperty dependsOn(BooleanSupplier... conditions) {
        dependencies.addAll(List.of(conditions));
        return this;
    }

    @Override
    public Boolean get() {
        if (!value) {
            return false;
        }
        for (BooleanSupplier dependency : dependencies) {
            if (!dependency.getAsBoolean()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public Boolean defaultValue() {
        return defaultValue;
    }

    @Override
    public Boolean parse(String raw) {
        if (raw.equalsIgnoreCase("true")) {
            return true;
        }
        if (raw.equalsIgnoreCase("false")) {
            return false;
        }
        throw new IllegalArgumentException("expected true or false");
    }

    @Override
    public String format(Boolean value) {
        return value.toString();
    }
}
