package vn.pvtk.protocol.ui;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Decoder for the original game's {@code ui/<id>.ui} screen-layout files,
 * reverse-engineered 1:1 from the client's {@code ee.parseUI} / {@code GWidget}
 * loader in {@code pvtk1v36maxspeed.jar}.
 *
 * <p>A {@code .ui} file is a serialized tree of GUI widgets. The stream is:
 * <ol>
 *   <li>{@code [n:u8]} preamble widgets (a flat vector — templates/roots), then</li>
 *   <li>one root widget.</li>
 * </ol>
 * Each widget is {@code [type:u8]}, a {@code [count:u8]} table of
 * {@code (index:u8, value:i32)} view-model slots, a {@code [count:u8]} list of
 * typed attributes, then {@code [count:u8]} child widgets (recursively).
 *
 * <p>Widget types map to the original classes:
 * {@code 1=eh 2=o 3=bf 4=bh 5=aa 6=ak 7=eu 8=es 50=g 51=bi 52=e 53=bq}. The
 * class hierarchy ({@code aa→bh}, {@code eh→aa}, {@code bi→g}, {@code e/bq→bi})
 * decides how a handful of attributes are laid out, so attribute parsing keys
 * off the widget family, exactly like the {@code instanceof} checks in the
 * original.
 *
 * <p>Validated against all 152 {@code ui/*.ui} files: every file parses to exact
 * EOF. Rendering-relevant fields (bounds, position, text, image references) are
 * lifted onto {@link Widget}; every attribute is still consumed for correctness.
 */
public final class UiScreen {

    // Widget family membership (mirrors the decompiled instanceof hierarchy).
    private static boolean isBh(int t) {
        return t == 4 || t == 5 || t == 1;   // bh, aa, eh
    }

    private static boolean isBi(int t) {
        return t == 51 || t == 52 || t == 53; // bi, e, bq
    }

    private static boolean isE(int t) {
        return t == 52;                       // e
    }

    /** A 9-slice (or stretched) panel background: a {@code common/} sheet + frame indices. */
    public record Background(int sheetId, int[] frames) { }

    /** A single image/icon: a {@code common/} sheet id + frame index. */
    public record Image(int sheetId, int frame) { }

    /** One widget in the layout tree. */
    public static final class Widget {
        public final int type;
        public int x;
        public int y;
        public int w;
        public int h;
        public String text = "";
        public final List<Background> backgrounds = new ArrayList<>(); // panel/frame art
        public final List<Image> images = new ArrayList<>();           // icons / button glyphs
        public final List<Widget> children = new ArrayList<>();

        Widget(int type) {
            this.type = type;
        }

        public String typeName() {
            return switch (type) {
                case 1 -> "eh"; case 2 -> "o"; case 3 -> "bf"; case 4 -> "bh";
                case 5 -> "aa"; case 6 -> "ak"; case 7 -> "eu"; case 8 -> "es";
                case 50 -> "g"; case 51 -> "bi"; case 52 -> "e"; case 53 -> "bq";
                default -> "?" + type;
            };
        }
    }

    private final List<Widget> preamble;
    private final Widget root;

    private UiScreen(List<Widget> preamble, Widget root) {
        this.preamble = preamble;
        this.root = root;
    }

    public List<Widget> preamble() {
        return preamble;
    }

    public Widget root() {
        return root;
    }

    public static UiScreen parse(byte[] bytes) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
        int n = in.readUnsignedByte();
        List<Widget> preamble = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            preamble.add(widget(in));
        }
        Widget root = widget(in);
        return new UiScreen(preamble, root);
    }

    private static Widget widget(DataInputStream in) throws IOException {
        int type = in.readUnsignedByte();
        Widget w = new Widget(type);
        int nSlots = in.readUnsignedByte();
        for (int i = 0; i < nSlots; i++) {
            in.readUnsignedByte(); // slot index
            in.readInt();          // slot value
        }
        int nAttr = in.readUnsignedByte();
        for (int i = 0; i < nAttr; i++) {
            attribute(in, w);
        }
        int nChild = in.readUnsignedByte();
        for (int i = 0; i < nChild; i++) {
            w.children.add(widget(in));
        }
        return w;
    }

    /** Reads {@code [count:u8]} then {@code count} 32-bit ints; returns them. */
    private static int[] intArray(DataInputStream in) throws IOException {
        int n = in.readUnsignedByte();
        int[] a = new int[n];
        for (int i = 0; i < n; i++) {
            a[i] = in.readInt();
        }
        return a;
    }

    private static void attribute(DataInputStream in, Widget w) throws IOException {
        int t = in.readUnsignedByte();
        int type = w.type;
        switch (t) {
            case 0 -> { in.readByte(); in.readByte(); in.readByte(); in.readByte(); }
            case 1 -> { // bounds x,y,w,h
                w.x = in.readInt();
                w.y = in.readInt();
                w.w = in.readInt();
                w.h = in.readInt();
            }
            case 5 -> { w.x = in.readInt(); w.y = in.readInt(); } // set position
            case 3, 42, 44, 43, 36, 37, 38, 39 -> intArray(in);
            case 4 -> in.readByte();
            case 6, 7, 8, 9 -> { in.readInt(); in.readInt(); }
            case 11, 12 -> {
                if (isBi(type)) {
                    int[] frames = intArray(in);
                    int sheet = in.readInt();
                    in.readInt(); in.readInt();
                    w.backgrounds.add(new Background(sheet, frames));
                } else if (isBh(type)) {
                    w.text = in.readUTF(); in.readInt(); in.readInt(); in.readInt();
                }
            }
            case 13, 14 -> {
                if (isE(type)) {
                    int[] frames = intArray(in);
                    int sheet = in.readInt();
                    in.readShort();
                    w.backgrounds.add(new Background(sheet, frames));
                } else if (isBh(type)) {
                    int[] frames = intArray(in);
                    int sheet = in.readInt();
                    in.readShort(); in.readShort();
                    w.backgrounds.add(new Background(sheet, frames));
                }
            }
            case 15, 45, 16 -> { int s = in.readInt(); int f = in.readInt(); in.readInt(); in.readInt(); w.images.add(new Image(s, f)); }
            case 31 -> { int s = in.readInt(); int f = in.readInt(); in.readInt(); in.readInt(); in.readInt(); w.images.add(new Image(s, f)); }
            case 21 -> { int s = in.readInt(); int f = in.readInt(); in.readInt(); w.images.add(new Image(s, f)); }
            case 17 -> in.readByte();
            case 40 -> { int s = in.readInt(); int f = in.readInt(); in.readInt(); w.images.add(new Image(s, f)); }
            case 41 -> w.text = in.readUTF();
            case 18 -> { intArray(in); in.readByte(); }
            case 19 -> { // menu
                in.readInt();
                in.readInt();
                intArray(in);
                int m = in.readUnsignedByte();
                for (int i = 0; i < m; i++) {
                    in.readUTF();
                }
                intArray(in);
            }
            case 2 -> { for (int i = 0; i < 6; i++) in.readInt(); }
            case 22 -> { in.readInt(); in.readByte(); in.readByte(); }
            case 23, 35, 30, 32, 28, 34, 47 -> in.readInt();
            case 24, 25, 46 -> { in.readInt(); in.readInt(); in.readInt(); }
            case 26 -> { in.readInt(); in.readInt(); }
            case 48 -> in.readByte();
            case 27 -> { w.text = in.readUTF(); in.readInt(); in.readInt(); in.readByte(); }
            case 29 -> { in.readByte(); for (int i = 0; i < 4; i++) in.readInt(); }
            case 33 -> in.readByte();
            default -> throw new IOException("unknown .ui attribute type " + t + " on widget " + w.typeName());
        }
    }
}
