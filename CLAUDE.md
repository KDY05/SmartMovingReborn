# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Smart Moving Reborn: a port of Divisor's Smart Moving (crawl, climb, slide, wall jump, …) to **Minecraft 1.20.1** for **Forge 47.x and Fabric**. The mod ID is `smartmovingreborn`, the package root is `io.github.kdy05.smartmovingreborn`, and the license is GPLv3.

The spec and plan live in the Obsidian vault at `../../../Projects/SmartMoving/`, in `Spec.md`, `Plan.md` and `Record.md`. `Plan.md` is the source of truth for architecture decisions and step order.

## Build

Run Gradle with **JDK 21**, following the workspace convention. Builds on the workspace default JDK 25 have not been tried. The mod itself targets Java 17, and the Java toolchain launches `runClient` on JDK 17.

```bash
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.8.9-hotspot"
./gradlew build              # jars in fabric/build/libs and forge/build/libs
./gradlew :fabric:runClient
./gradlew :forge:runClient
./gradlew :fabric:runServer  # or :forge:runServer
./gradlew :common:test       # JUnit Jupiter 6 tests for pure logic (config, ...)
```

Toolchain: Gradle 9.8, Architectury Loom 1.17-SNAPSHOT, Architectury plugin **3.5**-SNAPSHOT (3.4 breaks with Loom 1.17), and GradleUp Shadow. Mappings are Mojang official mappings plus Parchment. Versions are set in `gradle.properties`.

## Module layout

| Module | Contents |
|---|---|
| `common` | All game logic and **all Mixins** (`smartmovingreborn.mixins.json`). Compiled against vanilla + Fabric Loader only; never reference Forge or Fabric API classes here |
| `fabric` | `SmartMovingRebornFabric` entry point, `fabric.mod.json`, and Fabric-side `@ExpectPlatform` implementations |
| `forge` | `SmartMovingRebornForge` (`@Mod`), `META-INF/mods.toml`, and Forge-side `@ExpectPlatform` implementations. `loom.forge.mixinConfig` puts the mixin config in the jar manifest |

- Loader-specific code is limited to network channels, keybinding registration, config paths and event wiring, and goes through `@ExpectPlatform`. The implementations live in `<package>.fabric` / `<package>.forge`, in a class named `<CommonClass>Impl`.
- The architectury plugin shadows `common` into each platform jar, so there is no Architectury API runtime dependency.
- `forge:runClient` passes `--mixin.config` twice. This is harmless: the config is applied once.
- There is no refmap: Loom remaps the Mixin annotation targets inside the built classes (intermediary names on Fabric, SRG names on Forge).
- Networking: the client sends its encoded `MovingState` (`state/StatePacketCodec`, the original's bit layout) and the server relays it to the players tracking the sender (`network/ServerNetworkHandler`, which reads `ChunkMap` trackers through accessor mixins). Either side may lack the mod: Forge's channel uses `acceptMissingOr`, and `mods.toml` sets `displayTest = "IGNORE_ALL_VERSION"`. Nothing is sent to a peer without the channel. A client that sees no channel within 100 ticks of joining stays disabled.
- `move.debug.state=true` in the client config adds Smart Moving states to the F3 screen.

## Reference material (read-only, outside this repo)

- `../../JonnyNova/SmartMoving/`: 1.10.2 source, with the `SmartRender/` submodule.
- `../../JonnyNova/SmartMoving/smartmoving-artifacts/decompiled/`: decompiled 1.7.10 originals (Smart Moving 15.6, Smart Render 2.1, Player API, Render Player API) with MCP names restored. It is git-excluded in that repo.
