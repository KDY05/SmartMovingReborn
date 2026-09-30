package io.github.kdy05.smartmovingreborn;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

public final class SmartMovingReborn {
    public static final String MOD_ID = "smartmovingreborn";
    public static final Logger LOGGER = LogUtils.getLogger();

    private SmartMovingReborn() {
    }

    public static void init() {
        LOGGER.info("Smart Moving Reborn initializing");
    }
}
