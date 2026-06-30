# PVTK Wire Protocol

This document specifies the TCP wire protocol used by the rewritten PVTK
multiplayer stack. It is a faithful reconstruction of the protocol used by the
original **Phong Vân** J2ME client (`pvtk1v36maxspeed.jar`), recovered by
decompiling the obfuscated networking classes:

| Original (obfuscated) | Role | Reimplemented as |
|-----------------------|------|------------------|
| `ew` (`SocketServer`) | socket lifecycle | `server` Netty pipeline / `GameConnection` |
| `bp` | async writer thread | `GameConnection` writer / Netty encoder |
| `fk` | blocking reader thread | `Frame.read` / Netty `PacketDecoder` |
| `bc` | message read/write buffer | `vn.pvtk.protocol.Packet` |
| `dy` (`MsgHandler`) | opcode build/dispatch | `PacketDispatcher` + handlers |
| `ek` (`ServerInfo`) | server/line list | (login flow) |

## Transport

* **TCP**, connection string in the original client: `socket://<host>:30000`.
* Default port: **30000** (`ProtocolConstants.DEFAULT_GAME_PORT`).
* All multi-byte integers are **big-endian**.

## Frame layout

Every message is a single length-prefixed frame:

```
 0        1        2        3        4                      N
 +--------+--------+--------+--------+----------------------+
 |   length (u16)  |  command (u16)  |   payload (length-4) |
 +--------+--------+--------+--------+----------------------+
```

* `length` — total frame size **including** the 4-byte header.
* `command` — the opcode (see `OPCODES.md`).
* A `length` of `0xFFFF` is a **keep-alive** marker: the whole frame is just
  those two bytes, with no command or payload. The original client emitted it
  periodically to hold the GPRS/socket connection open; the server treats it as
  a no-op ping. (`Packet.KEEPALIVE_LENGTH`)

The maximum frame size is **32 767 bytes**, because the original encoder rejects
frames whose length does not fit in a signed `short`.

## Payload primitive encoding

The payload is a flat sequence of fields written/read in order. Field types
(from the original `bc` class, preserved exactly in `Packet`):

| Type     | Encoding |
|----------|----------|
| `bool`   | 1 byte (`0`/`1`) |
| `byte`   | 1 byte |
| `short`  | 2 bytes, big-endian |
| `int`    | 4 bytes, big-endian |
| `long`   | 8 bytes, big-endian |
| `bytes`  | 3-byte big-endian length prefix, then the raw bytes |
| `string` | 3-byte big-endian **char-count** prefix, then UTF-16BE chars (2 bytes each) |

> Note the unusual **24-bit (3-byte)** length prefix used for both byte arrays
> and strings, and that strings are length-prefixed by **character count**, not
> byte count, and encoded as **UTF-16 big-endian**. These quirks are reproduced
> faithfully so the rewrite stays binary-compatible.

## Implemented gameplay messages

The rewrite implements an authoritative, server-driven subset on top of the
faithful codec. Bodies are defined in
`vn.pvtk.protocol.message.Messages`:

### `LOGIN` (10003)
* **C→S** `[byte 0][string user][string pass][short line]`
* **S→C** `[byte 1][bool ok][string message][bool hasSelf][EntityState self?]`

### `WORLD_DATA` (10503) — snapshot
* **S→C** `[short mapId][short count][EntityState × count]`

### `AUTO_MOVE` (10518)
* **C→S** request: `[short x][short y][byte dir]`
* **S→C** update:  `[byte 1][int entityId][short x][short y][byte dir]`

### `GET_SPRITE` (10520) — spawn / despawn
* **S→C** spawn:   `[byte 1][EntityState]`
* **S→C** despawn: `[byte 0][int entityId]`

### `CHAT` (13509)
* **C→S** request:   `[byte channel][string target][string text]`
* **S→C** broadcast: `[byte channel][int fromId][string fromName][string text]`

Channels: `0 WORLD, 1 MAP, 2 TEAM, 3 COUNTRY, 4 PRIVATE, 5 SYSTEM`.

### `EntityState` (shared struct)
```
[int id][string name][short mapId][short x][short y][byte dir]
[int hp][int maxHp][short level]
```

## Login / connection sequence

```
Client                          Server
  | ---- TCP connect :30000 ----> |
  | ---- LOGIN (req) -----------> |   authenticate, create/bind player
  | <--- LOGIN (resp, self) ----- |
  |                               |   enter map, broadcast Spawn to neighbours
  | <--- WORLD_DATA (snapshot) -- |
  | ---- AUTO_MOVE (req) -------> |   clamp, apply, broadcast
  | <--- AUTO_MOVE (update) ----- |   (to everyone on the map)
  | <--- GET_SPRITE (spawn) ----- |   (when another player joins)
  | <--- CHAT (broadcast) ------- |
  | ---- 0xFFFF keep-alive -----> |   (every ~25s)
```
