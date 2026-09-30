package io.github.kdy05.smartmovingreborn.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads and writes {@code key=value} files with {@code #} comments.
 * <p>
 * Loading never fails on content: unparsable values fall back to the default and out-of-range values are
 * clamped, each with a warning. Unknown keys are ignored. The file is written in canonical form (the
 * generated comments and every key in declaration order); comments added by users are not kept when it
 * is rewritten.
 */
public final class ConfigFile {
    private static final int WIDTH = 72;

    private ConfigFile() {
    }

    /**
     * @param warnings  problems found in the file, one line each
     * @param needsSave whether the file is missing, lacks keys, or had values that were replaced
     */
    public record LoadResult(List<String> warnings, boolean needsSave) {
    }

    public static LoadResult load(Path path, List<Property<?>> properties) throws IOException {
        List<String> warnings = new ArrayList<>();
        Map<String, String> entries = new LinkedHashMap<>();
        boolean exists = Files.exists(path);
        if (exists) {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int separator = line.indexOf('=');
                if (separator < 0) {
                    warnings.add("line " + (i + 1) + ": ignored, no '=' found: " + line);
                    continue;
                }
                String key = line.substring(0, separator).trim();
                if (entries.put(key, line.substring(separator + 1).trim()) != null) {
                    warnings.add("line " + (i + 1) + ": duplicate key '" + key + "', the last value wins");
                }
            }
        }

        boolean needsSave = !exists;
        for (Property<?> property : properties) {
            needsSave |= loadProperty(property, entries.remove(property.key()), warnings);
        }
        for (String unknown : entries.keySet()) {
            warnings.add("unknown key '" + unknown + "' ignored");
        }
        return new LoadResult(List.copyOf(warnings), needsSave);
    }

    /** Returns whether the stored value differs from what the file said. */
    private static <T> boolean loadProperty(Property<T> property, String raw, List<String> warnings) {
        if (raw == null) {
            // A default below a bound computed from another property is expected, so no warning here.
            property.set(property.constrain(property.defaultValue()));
            return true;
        }
        boolean replaced = false;
        T value;
        try {
            value = property.parse(raw);
        } catch (IllegalArgumentException e) {
            value = property.defaultValue();
            warnings.add(property.key() + ": invalid value '" + raw + "' (" + e.getMessage()
                    + "), using default " + property.format(value));
            replaced = true;
        }
        T constrained = property.constrain(value);
        if (!constrained.equals(value)) {
            warnings.add(property.key() + ": value " + property.format(value) + " is out of range, using "
                    + property.format(constrained));
            replaced = true;
        }
        property.set(constrained);
        return replaced;
    }

    public static void save(Path path, List<String> fileHeader, List<Property<?>> properties) throws IOException {
        List<String> out = new ArrayList<>();
        for (String line : fileHeader) {
            out.add(line.isEmpty() ? "#" : "# " + line);
        }
        for (Property<?> property : properties) {
            out.add("");
            Property.Header header = property.header();
            if (header != null) {
                out.add("# " + "=".repeat(WIDTH - 2));
                out.add("# " + header.title());
                if (header.description() != null) {
                    wrap(header.description(), out);
                }
                out.add("# " + "=".repeat(WIDTH - 2));
                out.add("");
            }
            if (property.comment() != null) {
                wrap(property.comment(), out);
            }
            out.add(property.key() + "=" + formatStored(property));
        }
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(path, out, StandardCharsets.UTF_8);
    }

    private static <T> String formatStored(Property<T> property) {
        return property.format(property.stored());
    }

    private static void wrap(String text, List<String> out) {
        StringBuilder line = new StringBuilder("#");
        for (String word : text.split(" ")) {
            if (line.length() > 1 && line.length() + 1 + word.length() > WIDTH) {
                out.add(line.toString());
                line = new StringBuilder("#");
            }
            line.append(' ').append(word);
        }
        out.add(line.toString());
    }
}
