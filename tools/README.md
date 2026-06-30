# tools/

Offline asset tooling for the PVTK rebuild.

## SpriteExporter — decode original sprite sheets

The original game stores sprites as a `.png` sheet plus a sibling `.fr` frame
table that slices it into rectangles. `SpriteExporter` decodes them (via
`vn.pvtk.protocol.sprite.SpriteSheet`) and writes each frame as a standard PNG.

```bash
# Export every common/*.fr sheet into out/sprites/
./gradlew :tools:run --args="assets out/sprites"

# Or just specific sheets:
./gradlew :tools:run --args="assets out/sprites common/1 common/1002"
```

Output: `out/sprites/<dir>/<name>/<frameId>.png`. On the bundled assets this
decodes **172 sheets → 1754 frames**. Every frame is a normal PNG (re-readable by
any image tool), which is how the decoder is verified without a display.

### `.fr` format (recovered)

```
[count : u8]
count × { id : u8, srcX : u16, srcY : u16, w : u16, h : u16 }   // big-endian
```

Each record is a sub-rectangle of the sibling PNG. Verified against the real
files (e.g. `common/1.png` is 256×320 → 80 frames of 32×32).

## Roadmap

`ani/*.pd` and `ani/*.spr` describe animated, multi-part sprites (frame
sequences + offsets) and `*.pl` are palettes — decoding those would enable full
character animation. The protocol-inspection notes live in
[`../docs/OPCODES.md`](../docs/OPCODES.md).
