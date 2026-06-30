# PVTK Architecture

PVTK is a modern, multi-module rebuild of the original **Phong Vân** J2ME MMORPG
as an online multiplayer game with a shared codebase across **PC, Android, iOS
and a headless Java client**, talking to an authoritative **Netty** server over
the faithfully reconstructed wire protocol (see `PROTOCOL.md`).

## Module graph

```
        ┌────────────┐
        │  protocol  │  wire codec (Packet, Frame, Opcodes) + typed Messages
        └─────┬──────┘  pure JDK, zero deps — shared by EVERYTHING
              │
   ┌──────────┼───────────────────────────┐
   │          │                           │
┌──▼───┐  ┌───▼─────────┐           ┌──────▼───────┐
│server│  │ client/core │           │  (tests use  │
│Netty │  │ GameClient, │           │   server +   │
│world │  │ GameState,  │           │ client/core) │
│ AOI  │  │ GameConn.   │           └──────────────┘
└──────┘  └───┬─────────┘
              │  platform-neutral client (JDK sockets only)
        ┌─────┴───────┐
        │ client/game │  shared libGDX game (render + input)
        └─────┬───────┘
   ┌──────────┼───────────┐
┌──▼─────┐ ┌──▼──────┐ ┌──▼────────┐
│desktop │ │ android │ │   ios     │
│ LWJGL3 │ │ libGDX  │ │  RoboVM   │
│  (PC)  │ │ backend │ │  backend  │
└────────┘ └─────────┘ └───────────┘
   (all under the single client/ directory)
```

## Design principles

1. **One protocol, one source of truth.** The `protocol` module owns the exact
   byte layout. Server and every client encode/decode through the same
   `Packet`/`Messages` code, so they can never drift.

2. **Authoritative server.** Clients send *intent* (`MoveRequest`,
   `ChatRequest`); the server validates, mutates world state, and broadcasts the
   result. Clients render what the server tells them. This is the foundation for
   anti-cheat and consistency.

3. **Platform-neutral client core.** `client/core` uses only the JDK
   (`java.net.Socket`), so the identical networking + game-state code runs on
   desktop JVM, Android (ART) and iOS (RoboVM AOT). Rendering is isolated in
   `client/game` so a single libGDX game targets all three GUI platforms.

4. **Graceful degradation.** The server dispatcher logs and ignores unknown
   opcodes, so partially-implemented features never crash a session — the 245
   recovered opcodes can be filled in incrementally.

## Server runtime

* Netty `NioEventLoopGroup` acceptor + workers.
* Pipeline: `ReadTimeoutHandler → PacketDecoder → PacketEncoder → SessionHandler`.
* `SessionManager` indexes sessions by id and by player id.
* `World` holds `MapInstance`s; broadcasts are **area-of-interest** (per map).
* `PacketDispatcher` routes opcodes to stateless `PacketHandler`s.

## Concurrency model

* Each connection is handled on its Netty worker thread; `writeAndFlush` is
  thread-safe and ordered per channel.
* Shared world structures use `ConcurrentHashMap` / `newKeySet`.
* The client uses a blocking reader thread + an async writer (matching the
  original two-thread design); UI callbacks are marshalled to the render thread
  by each front-end (`Gdx.app.postRunnable`).

## What is implemented vs. roadmap

**Implemented & tested end-to-end:**
* connect, login, world snapshot, spawn / despawn broadcast, authoritative
  movement, multi-channel chat, keep-alive;
* **map travel** (`JUMP_MAP`) between zones;
* **inventory** — bag + equipment loaded from the real `item.txt`; equip/unequip
  changes derived attack/defence (`InventoryHandler`);
* **combat** — monsters spawned from `monster.txt` into the wilderness map, a
  real-time attack model, damage from gear-adjusted stats, death → gold/exp
  reward → level-up, and timed respawn via the world tick (`CombatHandler`,
  `World.attack/tick`); PvP works the same way;
* **country / guild** — create / list / join / leave / info, country-scoped chat
  (`CountryHandler`, `CountryRegistry`);
* **NPC shop** — open a listing built from `shop.txt`, buy with gold, sell items
  back (`ShopHandler`);
* **skills** — known skills loaded from `skill.txt`, granted on login; using a
  skill in combat costs MP and adds bonus damage (`SkillHandler`, `World.attack`);
* **party / team** — invite (auto-accept) / leave with a live roster, team-scoped
  chat (`TeamHandler`, `TeamRegistry`);
* **mail** — send mail with attached gold to any player (online or offline) and
  read the mailbox (`MailHandler`, `MailRegistry`);
* **quests** — a server-defined kill-quest board: list / accept / turn in, with
  progress driven by combat and exp/gold rewards (`QuestHandler`, `Quests`);
* **achievements** — milestone tracking (first kill, 10 kills, level 5, join
  guild, 500 gold) that unlock live as you play (`AchievementHandler`);
* **player marketplace** — consign an item for a price, browse listings, buy;
  the seller is paid by mail so it settles even while offline (`MarketHandler`,
  `MarketRegistry`);
* **mercenary / pet** — hire a companion (derived from `monster.txt`) that adds a
  permanent attack bonus (`MercenaryHandler`).

Each system is covered by an integration test in `client/core` that drives the
real server with real clients (21 tests total).

**Roadmap (opcodes already catalogued in `OPCODES.md`):** turn-based skill
battles matching the original engine, escort missions, country war, item
attachments in mail, deeper pet AI. Each maps to a documented opcode and slots
into the dispatcher as a new `PacketHandler`.

**Combat is a deliberate simplification.** The original game used turn-based
battle opcodes (`12501 EnterLocalBattle`, `12505 BattlePlan`, ...). Because the
original *server* logic was never present in the client jar, this rewrite
implements a clean real-time model over the same opcode space rather than
reproducing the exact turn engine.

## Game assets

The complete original asset set is shipped in `assets/` (`ani/`, `common/`,
`map/`, `mission/`, `ui/` plus the UTF-16LE content tables `item.txt`,
`monster.txt`, `skill.txt`, ...) — see [`../assets/README.md`](../assets/README.md).
Paths are preserved from the original client and resolved through
`vn.pvtk.protocol.data.AssetPaths`:

* the **server** loads the real content database on startup via
  `vn.pvtk.server.data.GameData` (`DataTable` parses the TSV tables);
* the **graphical clients** load sprites/maps/UI through `Gdx.files.internal(...)`.

The build is wired so assets resolve everywhere: desktop and server run from the
repo root, Android bundles `../../assets` into the APK, and iOS includes it via
`robovm.xml`.
