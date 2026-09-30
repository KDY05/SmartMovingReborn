package io.github.kdy05.smartmovingreborn.config;

import java.util.List;

/** The server's file: the shared movement rules plus server management options. */
public final class SmartMovingServerConfig extends SmartMovingConfig {
    public static final String FILE_NAME = "smartmovingreborn-server.properties";

    // The original's separate "move.global.config" switch is merged into this one (no per-user presets).
    public final BooleanProperty serverConfig = section("Server Management", "Below you find the options to manage your server",
            off("move.server.config",
                    "Whether the players on this server should be forced to use the movement configuration in this file"));

    @Override
    protected List<String> fileHeader() {
        return List.of(
                "Smart Moving Reborn server configuration",
                "",
                "Edit the values after '=' and restart the server. Invalid values are",
                "replaced by their defaults and out-of-range values are clamped; the",
                "server log names each one. Defaults follow the original Smart Moving",
                "\"Easy\" preset.",
                "",
                "The movement options below apply to players only while",
                "\"move.server.config\" is true; otherwise each player uses their own",
                "client configuration.");
    }
}
