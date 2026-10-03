package io.github.kdy05.smartmovingreborn.config;

/**
 * One configuration key. Holds the stored value; subclasses define parsing, formatting and constraints.
 * Constraints may reference properties declared earlier, so properties are always validated in declaration order.
 */
public abstract class Property<T> {
    private final String key;
    private String comment;
    private Header header;
    protected T value;

    protected Property(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public String comment() {
        return comment;
    }

    public Header header() {
        return header;
    }

    void setComment(String comment) {
        this.comment = comment;
    }

    void setHeader(Header header) {
        this.header = header;
    }

    /** The effective value used by game logic. */
    public T get() {
        return value;
    }

    /** The value as stored in the file, before dependency conditions are applied. */
    public T stored() {
        return value;
    }

    /** {@link #stored()} as written to the file. */
    public String storedString() {
        return format(stored());
    }

    public void set(T value) {
        this.value = value;
    }

    public void reset() {
        value = defaultValue();
    }

    public abstract T defaultValue();

    /** @throws IllegalArgumentException if {@code raw} is not a valid value for this property */
    public abstract T parse(String raw);

    public abstract String format(T value);

    /** Returns {@code value} adjusted to satisfy this property's constraints. */
    public T constrain(T value) {
        return value;
    }

    public record Header(String title, String description) {
    }
}
