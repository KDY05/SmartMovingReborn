package io.github.kdy05.smartmovingreborn.config;

import dev.architectury.injectables.annotations.ExpectPlatform;

import java.nio.file.Path;

public final class ConfigPaths {
    private ConfigPaths() {
    }

    /** The loader's config directory, e.g. {@code .minecraft/config}. */
    @ExpectPlatform
    public static Path configDir() {
        throw new AssertionError();
    }
}
