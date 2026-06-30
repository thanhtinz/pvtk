# tools/

Helper utilities for the PVTK rebuild.

## Asset conversion (roadmap)

The original client stores assets in custom formats inside the legacy jar:

| Ext    | Count | Meaning |
|--------|-------|---------|
| `.png` | ~1788 | raw images |
| `.spr` | ~885  | sprite sheets |
| `.fr`  | ~501  | animation frame definitions |
| `.pl`  | ~391  | palettes |
| `.mss` | ~365  | map / mission data |
| `.pd`  | ~82   | packed animation data |
| `.ui`  | ~152  | UI layout descriptors |

A converter here can transcode these into libGDX-friendly `TextureAtlas` +
Tiled `.tmx` maps so the graphical clients can render the original world. The
networking server and protocol do **not** depend on any of this.

> No original art/data is committed to this repository. Run any converter
> against assets you legally own.

## Protocol inspection

The opcode table in [`../docs/OPCODES.md`](../docs/OPCODES.md) was recovered by
decompiling the original networking classes and mapping each obfuscated message
factory back to its debug label. Use any standard Java decompiler (CFR,
Procyon, Fernflower) to cross-reference further opcodes as they are implemented.
