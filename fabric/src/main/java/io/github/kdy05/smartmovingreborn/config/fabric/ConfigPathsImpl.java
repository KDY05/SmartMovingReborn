package io.github.kdy05.smartmovingreborn.config.fabric;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

public final class ConfigPathsImpl {
    private ConfigPathsImpl() {
    }

    public static Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }
}
