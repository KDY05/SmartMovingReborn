package io.github.kdy05.smartmovingreborn.config;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * An ordered group of properties backed by one file. Subclasses declare properties as fields through the
 * factory methods below; declaration order is file order and validation order.
 * <p>
 * The factories mirror the original Smart Moving property types: {@link #on}/{@link #off} are switches
 * defaulting to true/false, {@link #factor} is a non-negative factor defaulting to 1, {@link #increasing}
 * is a factor of at least 1, and {@link #decreasing} is a factor between 0 and 1.
 */
public abstract class PropertySet {
    private final List<Property<?>> properties = new ArrayList<>();

    public List<Property<?>> properties() {
        return Collections.unmodifiableList(properties);
    }

    protected abstract List<String> fileHeader();

    /** Loads from {@code path}, rewriting the file when it is missing or had to be corrected. */
    public ConfigFile.LoadResult load(Path path) throws IOException {
        ConfigFile.LoadResult result = ConfigFile.load(path, properties);
        if (result.needsSave()) {
            save(path);
        }
        return result;
    }

    public void save(Path path) throws IOException {
        ConfigFile.save(path, fileHeader(), properties);
    }

    public void reset() {
        properties.forEach(Property::reset);
    }

    private <P extends Property<?>> P add(P property, String comment) {
        property.setComment(comment);
        properties.add(property);
        return property;
    }

    /** Starts a titled group in the written file at {@code property}. */
    protected static <P extends Property<?>> P section(String title, String description, P property) {
        property.setHeader(new Property.Header(title, description));
        return property;
    }

    protected BooleanProperty on(String key, String comment) {
        return add(new BooleanProperty(key, true), comment);
    }

    protected BooleanProperty off(String key, String comment) {
        return add(new BooleanProperty(key, false), comment);
    }

    protected FloatProperty number(String key, float defaultValue, String comment) {
        return add(new FloatProperty(key, defaultValue), comment);
    }

    protected FloatProperty positive(String key, float defaultValue, String comment) {
        return add(new FloatProperty(key, defaultValue).min(0), comment);
    }

    protected FloatProperty positive(String key, Supplier<Float> defaultValue, String comment) {
        return add(new FloatProperty(key, defaultValue).min(0), comment);
    }

    protected FloatProperty factor(String key, String comment) {
        return factor(key, 1, comment);
    }

    protected FloatProperty factor(String key, float defaultValue, String comment) {
        return positive(key, defaultValue, comment);
    }

    protected FloatProperty increasing(String key, String comment) {
        return increasing(key, 1, comment);
    }

    protected FloatProperty increasing(String key, float defaultValue, String comment) {
        return add(new FloatProperty(key, defaultValue).min(1), comment);
    }

    protected FloatProperty decreasing(String key, String comment) {
        return decreasing(key, 1, comment);
    }

    protected FloatProperty decreasing(String key, float defaultValue, String comment) {
        return decreasing(key, () -> defaultValue, comment);
    }

    protected FloatProperty decreasing(String key, Supplier<Float> defaultValue, String comment) {
        return add(new FloatProperty(key, defaultValue).range(0, 1), comment);
    }

    protected StringProperty choice(String key, String defaultValue, String[] choices, String comment) {
        return add(new StringProperty(key, defaultValue, choices), comment);
    }

    protected StringListProperty list(String key, String[] defaultValue, String comment) {
        return add(new StringListProperty(key, defaultValue), comment);
    }
}
