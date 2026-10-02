package io.github.kdy05.smartmovingreborn.config;

import java.util.List;

/**
 * The client's file: the shared movement rules plus client-only options, ported from the original
 * {@code SmartMovingOptions}. When a server forces its configuration, only the shared part is replaced.
 * <p>
 * Key bindings (grab, F9 toggle) live in the vanilla controls screen instead of this file.
 */
public final class SmartMovingClientConfig extends SmartMovingConfig {
    public static final String FILE_NAME = "smartmovingreborn-client.properties";

    public final FloatProperty perspectiveFadeFactor = section("Viewpoint perspective",
            "Below you find the options to manipulate the viewpoint perspective",
            factor("move.perspective.fade.factor", 0.5f,
                    "Fading speed factor between the different perspectives (>= 0.1, <= 1, set to '1' to switch off)"))
            .range(0.1f, 1);
    public final FloatProperty perspectiveRunFactor = number("move.perspective.run.factor", 1,
            "Standard sprinting perspective (set to '0' to switch off)");
    public final FloatProperty perspectiveSprintFactor = number("move.perspective.sprint.factor", 1.5f,
            "Smart on ground sprinting perspective (set to '0' to switch off)");

    public final FloatProperty angleJumpDoubleClickTicks = section("User interface",
            "Below you find the options to manipulate Smart Moving's user interface",
            positive("move.jump.angle.double.click.ticks", 3,
                    "The maximum number of ticks between two clicks to trigger a side or back jump (>= 2)"))
            .min(2);
    public final BooleanProperty wallJumpDoubleClick = on("move.jump.wall.double.click",
            "Whether wall jumping should be triggered by single or double clicking (and then press and holding) the jump button");
    public final FloatProperty wallJumpDoubleClickTicks = positive("move.jump.wall.double.click.ticks", 3,
            "The maximum number of ticks between two clicks to trigger a wall jump (>= 2, depends on \"move.jump.wall.double.click\")")
            .min(2);
    public final BooleanProperty climbJumpBackHeadOnGrab = on("move.jump.climb.back.head.on.grab",
            "Whether pressing or not pressing the grab button while climb jumping back results in a head jump");
    public final BooleanProperty displayExhaustionBar = on("move.gui.exhaustion.bar",
            "Whether to display the exhaustion bar in the game overlay");
    public final BooleanProperty displayJumpChargeBar = on("move.gui.jump.charge.bar",
            "Whether to display the jump charge bar in the game overlay");
    public final BooleanProperty sneakToggle = off("move.sneak.toggle", "To switch on/off sneak toggling");
    public final BooleanProperty crawlToggle = off("move.crawl.toggle", "To switch on/off crawl toggling");
    public final BooleanProperty flyCloseToGround = off("move.fly.ground.close", "To switch on/off flying close to the ground");
    public final BooleanProperty flyWhileOnGround = off("move.fly.ground.collide",
            "To switch on/off flying while colliding with the ground (Relevant only if \"move.fly.ground.close\" is true)")
            .dependsOn(flyCloseToGround::get);
    public final BooleanProperty flyControlVertical = on("move.fly.control.vertical",
            "Whether flying control also depends on where the player looks vertically.");
    public final BooleanProperty diveControlVertical = on("move.dive.control.vertical",
            "Whether diving control also depends on where the player looks vertically.");

    public final BooleanProperty configChat = section("Message Management",
            "Below you find the options to define in which case Smart Moving should write messages about its current behavior to the ingame chat",
            on("move.config.chat", "To switch on/off option status messages via chat system"));
    public final BooleanProperty configChatInit = on("move.config.chat.init",
            "To switch on/off the initial option status message when starting a game (Relevant only if \"move.config.chat\" is not false)")
            .dependsOn(configChat::get);
    public final BooleanProperty configChatServer = on("move.config.chat.server",
            "To switch on/off the server config overridden status message when joining a multiplayer game (Relevant only if \"move.config.chat\" is not false)")
            .dependsOn(configChat::get);

    public final BooleanProperty debugState = section("Debugging", "Options for developing Smart Moving Reborn",
            off("move.debug.state", "Whether to show the Smart Moving states of yourself and the targeted player on the F3 debug screen"));
    public final BooleanProperty debugClimb = off("move.debug.climb",
            "Whether to show the free climbing holds around yourself as particles and on the F3 debug screen");

    @Override
    protected List<String> fileHeader() {
        return List.of(
                "Smart Moving Reborn client configuration",
                "",
                "Edit the values after '=' and restart the game. Invalid values are",
                "replaced by their defaults and out-of-range values are clamped; the",
                "game log names each one. Defaults follow the original Smart Moving",
                "\"Easy\" preset.",
                "",
                "When a server enforces its configuration (\"move.server.config\" on",
                "the server), the movement options below are replaced by the",
                "server's while connected. The sections from \"Viewpoint perspective\"",
                "onward always stay local.");
    }
}
