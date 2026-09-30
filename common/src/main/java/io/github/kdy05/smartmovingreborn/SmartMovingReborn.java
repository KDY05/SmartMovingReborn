package io.github.kdy05.smartmovingreborn;

import com.mojang.logging.LogUtils;
import io.github.kdy05.smartmovingreborn.config.ConfigPaths;
import io.github.kdy05.smartmovingreborn.config.PropertySet;
import io.github.kdy05.smartmovingreborn.config.SmartMovingClientConfig;
import io.github.kdy05.smartmovingreborn.config.SmartMovingServerConfig;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Path;

public final class SmartMovingReborn {
    public static final String MOD_ID = "smartmovingreborn";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final SmartMovingServerConfig SERVER_CONFIG = new SmartMovingServerConfig();
    /** Loaded only on the physical client. */
    public static final SmartMovingClientConfig CLIENT_CONFIG = new SmartMovingClientConfig();

    private SmartMovingReborn() {
    }

    public static void init() {
        LOGGER.info("Smart Moving Reborn initializing");
        loadConfig(SERVER_CONFIG, SmartMovingServerConfig.FILE_NAME);
    }

    public static void initClient() {
        loadConfig(CLIENT_CONFIG, SmartMovingClientConfig.FILE_NAME);
    }

    private static void loadConfig(PropertySet config, String fileName) {
        Path path = ConfigPaths.configDir().resolve(fileName);
        try {
            config.load(path).warnings().forEach(warning -> LOGGER.warn("{}: {}", fileName, warning));
        } catch (IOException e) {
            LOGGER.error("Could not read or write {}, using defaults", path, e);
            config.reset();
        }
    }
}
