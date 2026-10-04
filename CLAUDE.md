# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Smart Moving Reborn: a port of Divisor's Smart Moving (crawl, climb, slide, wall jump, …) to **Minecraft 1.20.1** for **Forge 47.x and Fabric**. The `1.21.1` branch holds the 1.21.1 NeoForge/Fabric version; fixes needed on both go here first and are cherry-picked there. The mod ID is `smartmovingreborn`, the package root is `io.github.kdy05.smartmovingreborn`, and the license is GPLv3.

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
- Networking: the client sends its encoded `MovingState` (`state/StatePacketCodec`, the original's bit layout) and the server relays it to the players tracking the sender (`network/ServerNetworkHandler`, which reads `ChunkMap` trackers through accessor mixins). Either side may lack the mod: Forge's channel uses `acceptMissingOr`, and `mods.toml` sets `displayTest = "IGNORE_ALL_VERSION"`. Nothing is sent to a peer without the channel. A client that sees no channel within 100 ticks of joining stays disabled. The server answers each connection's first state with `ConfigSyncMessage`: with `move.server.config` on it carries the movement rules (the properties `SmartMovingConfig` itself declares, `movementRules()`), which replace the client's own until the connection ends (F9 still turns Smart Moving on and off). The client stays inactive until that answer arrives.
- Movement hooks: the mixins in `mixin/{client,common,server}` only forward to `logic/MovingController`, where a `false`/`null` result lets vanilla run. Hooks in common classes act only on the client's own player (`MovingController.setSelf`, set from the `LocalPlayer` constructor), so a dedicated server never loads `SmartMovingClient`; the exception is the pose, which the server also sets from the state each client sent (`ServerNetworkHandler.getState`). Read any player's moves through `MovingController.stateOf(player)` (own, relayed or server-side `MovingState`), never from the feature classes.
- Own player logic: `logic/SelfMoving` holds the own player's state and runs one tick in the original's order (decide each move, then the toggles in `ToggleState`). Feature classes such as `logic/crawl/CrawlLogic` are pure decision functions over measured values, so they are unit tested; `SelfMoving` does the measuring and writes the results into `SmartMovingClient.LOCAL_STATE`, which is also what is sent and rendered. Vanilla still moves the player: the movement input is reduced to its signs (dropping vanilla's sneak and item slowdowns), and `logic/SpeedLogic.landSpeedFactor` multiplies the speed returned by `LivingEntity#getFrictionInfluencedSpeed` (walking and air control; creative flying is untouched), taking vanilla's sprint bonus back out. Other players on a client get their pose from the server's entity data, since `RemotePlayer` skips `updatePlayerPose`. Crawling uses vanilla's `Pose.SWIMMING` and its 0.6 size. `travel` and `jumpFromGround` are hooked in `Player` (which overrides them), and `move`/`isShiftKeyDown` in `LocalPlayer`.
- Rendering: `render/PoseCalculator` rebuilds Smart Render's skeleton (extra torso, breast, neck, shoulder and pelvis joints) so the original angles apply unchanged, then writes each joint's composed transform into the flat vanilla `ModelPart`s at `HumanoidModel#setupAnim` TAIL. Armor, sleeves, trousers and hat follow through vanilla's `copyFrom` (`HumanoidArmorLayerMixin` then undoes the limb stretching armor did not take in the original); the cape and elytra layers hang from the posed breast joint. The inventory screen and the first person hand keep vanilla's pose. While crawling, `PlayerRendererMixin` drops vanilla's lying rotation and draws the player one block lower, like the original.
- `move.debug.state=true` in the client config adds Smart Moving states to the F3 screen.

## Reference material (read-only, outside this repo)

- `../../JonnyNova/SmartMoving/`: 1.10.2 source, with the `SmartRender/` submodule.
- `../../JonnyNova/SmartMoving/smartmoving-artifacts/decompiled/`: decompiled 1.7.10 originals (Smart Moving 15.6, Smart Render 2.1, Player API, Render Player API) with MCP names restored. It is git-excluded in that repo.
