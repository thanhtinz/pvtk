# PVTK — Phong Vân Online (Multiplayer Rebuild)

A modern, cross-platform rebuild of the classic **Phong Vân** J2ME MMORPG as an
online multiplayer game. One shared codebase powers clients on **PC, Android,
iOS and a headless Java client**, all talking to an authoritative **Java/Netty
server** over the original game's wire protocol — faithfully reverse-engineered
from the legacy client and re-implemented in clean Java 21.

> Bản dựng lại đa nền tảng của game **Phong Vân** (J2ME) thành game online
> multiplayer hiện đại: client cho **PC, Android, iOS, Java**, server **Netty**,
> dùng lại đúng giao thức mạng gốc đã được dịch ngược.

## Highlights

- 🔌 **Faithful protocol** — the exact `[len][cmd][payload]` framing, 24-bit
  length prefixes and UTF-16BE strings of the original client, with **245
  opcodes** recovered and catalogued. See [`docs/PROTOCOL.md`](docs/PROTOCOL.md)
  and [`docs/OPCODES.md`](docs/OPCODES.md).
- 🖥️ **Authoritative Netty server** — sessions, world/maps, area-of-interest
  broadcast, opcode dispatch.
- ♻️ **Shared client core** — pure-JDK networking + game state reused by every
  platform.
- 🎮 **libGDX game** — single rendering/input codebase → PC + Android + iOS.
- ⚔️ **Gameplay systems** — inventory & equipment (`item.txt`), monster combat
  with rewards/level-ups & respawn (`monster.txt`), skills with MP (`skill.txt`),
  NPC shops (`shop.txt`), quests, achievements, a player marketplace, pet
  companions that follow & auto-fight, friends, mail (with gold/item
  attachments), party/team, map travel, country/guild, **country war**,
  **monster aggro AI**, a **PvP arena**, and **escort missions**.
- ✅ **Verified end-to-end** — integration tests boot the real server and real
  clients and assert login, spawn, movement, chat, inventory, combat and guilds.

## Module layout

| Module | What it is | Builds on |
|--------|------------|-----------|
| `protocol` | Wire codec (`Packet`, `Frame`, `Opcodes`) + typed `Messages` | bare JDK |
| `server` | Authoritative game server | Netty |
| `client/core` | Platform-neutral `GameClient` + `GameState` | bare JDK |
| `client/game` | Shared libGDX game (render + input) | libGDX |
| `client/java` | **Java** headless / console client (no GPU) | bare JDK |
| `client/desktop` | **PC** launcher (Windows/macOS/Linux) | LWJGL3 |
| `client/android` | **Android** launcher | Android SDK |
| `client/ios` | **iOS** launcher | RoboVM (macOS) |

All the cross-platform front-ends live under one `client/` directory: a shared
core (`core` + `game`) plus one thin launcher per platform — the standard libGDX
layout for shipping a single game to PC, Android and iOS.

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the full picture.

## Requirements

- **JDK 21** (the Gradle toolchain will use it).
- Internet access for the first build (Maven Central / Google Maven).
- Android builds additionally need the **Android SDK** (`ANDROID_HOME`).
- iOS builds need **macOS + RoboVM**.

## Quick start

### 1. Build everything

```bash
./gradlew build
```

### 2. Run the server

```bash
./gradlew :server:run                       # listens on 0.0.0.0:30000
# or override:
PVTK_PORT=30000 ./gradlew :server:run
./gradlew :server:run --args="--host 0.0.0.0 --port 30000"
```

### 3. Run the PC client (libGDX desktop)

```bash
./gradlew :client:desktop:run --args="--host 127.0.0.1 --port 30000 --user Alice"
```

Click/tap a tile to move; other players and world chat appear live. Open a second
window with `--user Bob` and watch them see each other move and chat in real time.

You can also drive the game from the headless **Java console client** (no GPU):

```bash
./gradlew :client:java:run --args="--host 127.0.0.1 --port 30000 --user Alice"
# commands:  m <x> <y>   move      s <text>   say (world)   who   quit
```

### 4. Android

```bash
# with ANDROID_HOME set (or a local.properties pointing at the SDK)
./gradlew :client:android:assembleDebug
# install client/android/build/outputs/apk/debug/*.apk
```

### 5. iOS (on macOS)

```bash
# enable the RoboVM plugin in client/ios/build.gradle.kts, then:
PVTK_BUILD_IOS=1 ./gradlew :client:ios:launchIPhoneSimulator
PVTK_BUILD_IOS=1 ./gradlew :client:ios:createIPA
```

## Tests

```bash
./gradlew test
```

`protocol` has byte-level codec tests; `client/core` has a full end-to-end
multiplayer integration test (real server + real clients over loopback).

## How this was built

The original `pvtk1v36maxspeed.jar` is an obfuscated MIDP-1.0/CLDC-1.0 client.
Its networking classes were decompiled to recover the framing, the primitive
encodings and the opcode table; these were then re-implemented from scratch in
idiomatic Java 21. No original bytecode or proprietary art is included in this
repository — only the protocol and game systems were rebuilt. See
[`docs/`](docs/) for the reconstructed specs.

## License & assets

This repository contains original, clean-room game code. The original game's
artwork, audio and content data are **not** redistributed here. Supply your own
assets (or convert the originals you own) before shipping a graphical build.
