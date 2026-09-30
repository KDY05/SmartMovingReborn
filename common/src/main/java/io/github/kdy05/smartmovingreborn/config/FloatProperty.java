package io.github.kdy05.smartmovingreborn.config;

import java.util.function.Supplier;

/** A number with optional lower and upper bounds. Bounds and the default may be computed from other properties. */
public final class FloatProperty extends Property<Float> {
    private final Supplier<Float> defaultValue;
    private Supplier<Float> min;
    private Supplier<Float> max;

    public FloatProperty(String key, Supplier<Float> defaultValue) {
        super(key);
        this.defaultValue = defaultValue;
        reset();
    }

    public FloatProperty(String key, float defaultValue) {
        this(key, () -> defaultValue);
    }

    public FloatProperty min(float min) {
        return min(() -> min);
    }

    public FloatProperty min(Supplier<Float> min) {
        this.min = min;
        return this;
    }

    public FloatProperty max(float max) {
        return max(() -> max);
    }

    public FloatProperty max(Supplier<Float> max) {
        this.max = max;
        return this;
    }

    public FloatProperty range(float min, float max) {
        return min(min).max(max);
    }

    @Override
    public Float defaultValue() {
        return defaultValue.get();
    }

    @Override
    public Float parse(String raw) {
        float parsed;
        try {
            parsed = Float.parseFloat(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("expected a number");
        }
        if (!Float.isFinite(parsed)) {
            throw new IllegalArgumentException("expected a finite number");
        }
        return parsed;
    }

    @Override
    public String format(Float value) {
        return value.toString();
    }

    @Override
    public Float constrain(Float value) {
        float result = value;
        if (min != null) {
            result = Math.max(result, min.get());
        }
        if (max != null) {
            result = Math.min(result, max.get());
        }
        return result;
    }
}
