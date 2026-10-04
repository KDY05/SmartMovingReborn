package io.github.kdy05.smartmovingreborn.config.neoforge;

import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;

public final class ConfigPathsImpl {
    private ConfigPathsImpl() {
    }

    public static Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }
}
