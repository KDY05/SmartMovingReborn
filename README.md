# Smart Moving Reborn

English | [한국어](README.ko.md)

A port of Divisor's **Smart Moving** to Minecraft 1.20.1 (Forge, Fabric) and 1.21.1 (NeoForge, Fabric).
It adds crawling, climbing, ceiling climbing, sliding, wall jumping, charged jumps, diving and more, with the original's animations.

## Requirements

| Minecraft | Loader | Source branch |
|---|---|---|
| 1.20.1 | Forge 47.x, or Fabric Loader with Fabric API | [`master`](https://github.com/kdy05/SmartMovingReborn/tree/master) |
| 1.21.1 | NeoForge 21.1.x, or Fabric Loader with Fabric API | [`1.21.1`](https://github.com/kdy05/SmartMovingReborn/tree/1.21.1) |

- Install the mod on **both the client and the server**. When only one side has it, joining is still allowed, but Smart Moving stays disabled.
- On 1.21.1, Fabric and NeoForge clients and servers can join each other with Smart Moving working.

## Controls

| Action | Keys |
|---|---|
| Grab | **Left Ctrl** (Smart Moving's own key) |
| Sprint (Smart Moving) | Vanilla sprint key, whose default the mod changes to **R** |
| Run (vanilla sprint) | Double-tap forward |
| Turn Smart Moving on/off | **F9** |
| Climb | Hold Grab while facing a wall with holds; forward climbs up, back or no key climbs down |
| Hang on while climbing | Sneak + Grab |
| Ceiling climb | Hold Grab under iron bars or trapdoors |
| Crawl | Sneak + Grab on the ground. You also crawl when forced into a one-block gap |
| Slide | Sneak + Grab while running or sprinting |
| Charged jump | Hold Sneak and Jump while standing, then release Jump |
| Side / back jump | Double-tap left, right or back (left/right + back jumps diagonally) |
| Head jump | Hold Grab while running or sprinting, hold Jump to charge, then release |
| Wall jump | Double-tap Jump in the air and hold it until you hit a wall; also hold Grab for a wall head jump |

Grab and F9 can be changed in the vanilla Controls screen. Left Ctrl is vanilla's default sprint key, so the mod moves vanilla's default sprint key to R.

## Features

- **Free climbing** on wall gaps, slabs, stairs, fences, walls, panes, trapdoors, doors and more.
- **Ceiling climbing** on iron bars and trapdoors. The block list is configurable, and accepts block tags.
- **Crawling** in one-block gaps, replacing vanilla's automatic crawling.
- **Sliding** and **head jumping**, which turn into crawling or gliding.
- **Jumps**: charged jumps, side and back jumps, climb jumps, wall jumps and wall head jumps.
- **Sprinting** (1.5×) on the ground, while climbing, swimming, diving, ceiling climbing and flying, with FOV changes.
- **Swimming, diving and wading** in shallow water, replacing vanilla's swimming.
- **Flying** in the direction you look in creative mode, with dedicated levitating and falling animations.
- **Server-enforced rules**: with `move.server.config` on, the server's movement options override each client's own while connected.

## Configuration

There is no in-game configuration screen. Edit the files in the `config/` folder and restart the game or server:

| File | Contents |
|---|---|
| `smartmovingreborn-client.properties` | The movement options, plus client-only options (perspective and FOV, double-tap timing, toggles, chat messages, debug) |
| `smartmovingreborn-server.properties` | The same movement options, plus `move.server.config` |

Every option is described by a comment in the file. Key names and defaults follow the original's "Easy" preset. Invalid values fall back to their defaults and out-of-range values are clamped; the log names each one.

- A client also creates the server file: singleplayer and LAN worlds run an integrated server, which reads it.
- With `move.server.config=true`, the server's movement options apply to every connected player until they leave. Client-only options always stay each player's own.

## Differences from the original

- Only the **Easy** preset. There is no exhaustion or hunger, and F9 turns Smart Moving on and off instead of cycling presets.
- The default sprint key is **R**, since Left Ctrl is Grab.
- A few original bugs are fixed, mostly in wall jump angles and climbing hold detection.
- On 1.21.1, a player whose scale attribute is not 1 moves like vanilla.
- Not ported: in-game speed keys, presets and per-user rights, lava swimming, and compatibility with mods that no longer exist (Ropes+, Carpenter's Blocks and others).

## Translations

English and Korean. Dutch, Brazilian Portuguese and Russian also use the original's translations (by Dunncann, Cassiobsk8 and _DarKShaM_).

## Credits

- **Divisor**: original Smart Moving (1.2.5 – 1.7.10)
- **JonnyNova**: Smart Moving port to 1.10.2

## License

GPL-3.0-or-later. See [LICENSE](LICENSE).
