package io.github.kdy05.smartmovingreborn.config.forge;

import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;

public final class ConfigPathsImpl {
    private ConfigPathsImpl() {
    }

    public static Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }
}
