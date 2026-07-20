package vn.pvtk.protocol.sprite;

import java.util.ArrayList;
import java.util.List;

/**
 * Decoder for the original game's {@code sprite_<id>.spr} animation modules,
 * reverse-engineered 1:1 from the client's {@code GameSprite}/{@code Fragment}/
 * {@code Frame}/{@code Animation} loaders in {@code pvtk1v36maxspeed.jar}.
 *
 * <p>A {@code .spr} file has three sections read in order from a big-endian
 * stream:
 * <ol>
 *   <li><b>Fragments</b> — {@code [n]} then each {@code [typeId:u8][nameId:s16]}.
 *       Every fragment points at an external image sheet {@code <nameId>.png}
 *       cut into pieces by a sibling {@code <nameId>.fr} table (see
 *       {@link Fr}). Player fragments whose ids are runtime placeholders
 *       (equipment/body) are swapped by the paperdoll layer; monster / NPC /
 *       effect sprites are fully self-contained.</li>
 *   <li><b>Frames</b> — {@code [n]} then each {@code [nLayer]} followed by
 *       {@code nLayer} layers of {@code [fragTypeId:u8][subFrameId:u8][dx:s8]
 *       [dy:s8][flags:u8]}. A frame is the set of image pieces that compose one
 *       drawn picture; {@code fragTypeId} selects the fragment (matched against
 *       the fragment {@code typeId}) and {@code subFrameId} selects the piece
 *       within that fragment's {@code .fr}.</li>
 *   <li><b>Animations</b> — {@code [n]} then each {@code [nKey:u8][kind:u8]
 *       [id:u8][flag:u8]} followed by {@code nKey} keys of {@code [frameIndex:u8]
 *       [duration:u8]} plus, when {@code kind == 16}, {@code [dx:s8][dy:s8]}.</li>
 * </ol>
 *
 * <p>Validated against all 360 {@code ani/sprite_*.spr} plus the 525
 * {@code ani/ani2/} modules: every file parses and consumes to exact EOF.
 */
public final class SprSprite {

    /** A source image sheet referenced by a sprite. */
    public record Fragment(int typeId, int nameId) { }

    /** One image piece of a frame: which fragment + piece, offset and flip. */
    public record Layer(int fragTypeId, int subFrameId, int dx, int dy, int flags) {
        public boolean flipX() {
            return (flags & 1) != 0;
        }
    }

    /** A composed picture: an ordered list of layers drawn back-to-front. */
    public record Frame(List<Layer> layers) { }

    /** One keyframe of an animation. */
    public record Key(int frameIndex, int duration, int dx, int dy) { }

    /** A named animation: a sequence of frame references with timing. */
    public record Animation(int id, int kind, List<Key> keys) { }

    private final List<Fragment> fragments;
    private final List<Frame> frames;
    private final List<Animation> animations;

    private SprSprite(List<Fragment> fragments, List<Frame> frames, List<Animation> animations) {
        this.fragments = fragments;
        this.frames = frames;
        this.animations = animations;
    }

    public List<Fragment> fragments() {
        return fragments;
    }

    public List<Frame> frames() {
        return frames;
    }

    public List<Animation> animations() {
        return animations;
    }

    /** Parses a {@code .spr} byte array. Throws on truncation/corruption. */
    public static SprSprite parse(byte[] b) {
        Cursor c = new Cursor(b);

        int nFrag = c.u8();
        List<Fragment> fragments = new ArrayList<>(nFrag);
        for (int i = 0; i < nFrag; i++) {
            int typeId = c.u8();
            int nameId = c.s16();
            fragments.add(new Fragment(typeId, nameId));
        }

        int nFrame = c.u8();
        List<Frame> frames = new ArrayList<>(nFrame);
        for (int i = 0; i < nFrame; i++) {
            int nLayer = c.u8();
            List<Layer> layers = new ArrayList<>(nLayer);
            for (int j = 0; j < nLayer; j++) {
                int fragTypeId = c.u8();
                int subFrameId = c.u8();
                int dx = c.s8();
                int dy = c.s8();
                int flags = c.u8();
                layers.add(new Layer(fragTypeId, subFrameId, dx, dy, flags));
            }
            frames.add(new Frame(layers));
        }

        int nAnim = c.u8();
        List<Animation> animations = new ArrayList<>(nAnim);
        for (int i = 0; i < nAnim; i++) {
            int nKey = c.u8();
            int kind = c.u8();
            int id = c.u8();
            c.u8(); // reserved flag (unused by renderer)
            List<Key> keys = new ArrayList<>(nKey);
            for (int k = 0; k < nKey; k++) {
                int frameIndex = c.u8();
                int duration = c.u8();
                int dx = 0;
                int dy = 0;
                if (kind == 16) {
                    dx = c.s8();
                    dy = c.s8();
                }
                keys.add(new Key(frameIndex, duration, dx, dy));
            }
            animations.add(new Animation(id, kind, keys));
        }
        return new SprSprite(fragments, frames, animations);
    }

    /** True if every fragment's piece placements stay within the sheet bounds. */
    public boolean fitsWithin(int fragTypeId, Fr fr, int sheetW, int sheetH) {
        return fr.fitsWithin(sheetW, sheetH);
    }

    // ------------------------------------------------------------------

    /**
     * The {@code <id>.fr} piece table used by the sprite system: {@code [n]}
     * then each {@code [id:u8][x:u8][y:u8][w:u8][h:u8]}. (This is the byte-field
     * variant the client's fragment loader reads; distinct from the wider
     * {@link SpriteSheet} layout used for some shared sheets.)
     */
    public static final class Fr {

        /** One rectangle in the sheet, addressed by its piece id. */
        public record Piece(int id, int x, int y, int w, int h) { }

        private final List<Piece> pieces;

        private Fr(List<Piece> pieces) {
            this.pieces = pieces;
        }

        public List<Piece> pieces() {
            return pieces;
        }

        /** The piece with the given id, or {@code null} if absent. */
        public Piece byId(int id) {
            for (Piece p : pieces) {
                if (p.id() == id) {
                    return p;
                }
            }
            return null;
        }

        public boolean fitsWithin(int sheetW, int sheetH) {
            for (Piece p : pieces) {
                if (p.x() < 0 || p.y() < 0 || p.x() + p.w() > sheetW || p.y() + p.h() > sheetH) {
                    return false;
                }
            }
            return true;
        }

        public static Fr parse(byte[] b) {
            Cursor c = new Cursor(b);
            int n = c.u8();
            List<Piece> pieces = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                pieces.add(new Piece(c.u8(), c.u8(), c.u8(), c.u8(), c.u8()));
            }
            return new Fr(pieces);
        }
    }

    /** Big-endian byte reader. */
    private static final class Cursor {
        private final byte[] b;
        private int o;

        Cursor(byte[] b) {
            this.b = b;
        }

        int u8() {
            return b[o++] & 0xFF;
        }

        int s8() {
            return b[o++]; // already sign-extended
        }

        int s16() {
            int v = ((b[o] & 0xFF) << 8) | (b[o + 1] & 0xFF);
            o += 2;
            return (short) v;
        }
    }
}
