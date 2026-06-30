package vn.pvtk.protocol.sprite;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser for the original game's {@code .fr} frame tables. A {@code .fr} file
 * accompanies a sibling {@code .png} sheet and slices it into rectangular frames:
 *
 * <pre>
 *   [count : u8]
 *   count × { id : u8, srcX : u16, srcY : u16, w : u16, h : u16 }   (big-endian)
 * </pre>
 *
 * Each frame is a sub-rectangle of the PNG. This was recovered by inspecting the
 * original {@code common/*.fr} files and verifying every rectangle falls inside
 * its PNG (e.g. {@code common/1.png} is 256×320, sliced into 80 × 32×32 frames).
 *
 * <p>Pure bytes only — the actual pixels are sliced per platform (libGDX
 * {@code TextureRegion} in the client, {@code BufferedImage} in the offline
 * exporter), so this lives in the dependency-free protocol module.
 */
public final class SpriteSheet {

    /** A single frame rectangle within the sheet PNG. */
    public record Frame(int id, int x, int y, int w, int h) { }

    private final List<Frame> frames;

    private SpriteSheet(List<Frame> frames) {
        this.frames = frames;
    }

    public List<Frame> frames() {
        return frames;
    }

    public int size() {
        return frames.size();
    }

    public Frame frame(int index) {
        return frames.get(index);
    }

    public static SpriteSheet parse(InputStream in) throws IOException {
        return parse(in.readAllBytes());
    }

    /**
     * Parses a {@code .fr} byte array. Frames whose rectangle would exceed the
     * sheet are kept as-is here; callers that know the sheet size should validate.
     * Returns an empty sheet if the data is too short to be a valid table.
     */
    public static SpriteSheet parse(byte[] b) {
        List<Frame> frames = new ArrayList<>();
        if (b.length < 1) {
            return new SpriteSheet(frames);
        }
        int count = b[0] & 0xFF;
        int off = 1;
        for (int i = 0; i < count; i++) {
            if (off + 9 > b.length) {
                break; // truncated / variant layout — stop cleanly
            }
            int id = b[off] & 0xFF;
            int x = u16(b, off + 1);
            int y = u16(b, off + 3);
            int w = u16(b, off + 5);
            int h = u16(b, off + 7);
            off += 9;
            frames.add(new Frame(id, x, y, w, h));
        }
        return new SpriteSheet(frames);
    }

    private static int u16(byte[] b, int i) {
        return ((b[i] & 0xFF) << 8) | (b[i + 1] & 0xFF);
    }

    /** True if every frame fits within a sheet of the given dimensions. */
    public boolean fitsWithin(int sheetW, int sheetH) {
        for (Frame f : frames) {
            if (f.x() + f.w() > sheetW || f.y() + f.h() > sheetH || f.w() <= 0 || f.h() <= 0) {
                return false;
            }
        }
        return !frames.isEmpty();
    }
}
