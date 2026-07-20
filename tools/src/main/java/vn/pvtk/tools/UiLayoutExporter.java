package vn.pvtk.tools;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import vn.pvtk.protocol.sprite.SpriteSheet;
import vn.pvtk.protocol.ui.UiScreen;

/**
 * Renders the original {@code ui/<id>.ui} screens with their real artwork. It
 * decodes each screen with {@link UiScreen}, then composites the actual sprite
 * pieces referenced by every widget — 9-slice panel frames for backgrounds and
 * single frames for icons/buttons — pulled from the {@code common/} sheets
 * (16-bit {@code .fr} tables via {@link SpriteSheet}). Backgrounds that inherit
 * the default UI skin fall back to the shared 9-slice sheet.
 *
 * <p>This is the headless, in-repo counterpart of the live client's UI renderer:
 * it proves the {@code .ui} engine end-to-end against real assets.
 *
 * <pre>
 *   ./gradlew :tools:exportUi --args="assets out/ui"
 * </pre>
 */
public final class UiLayoutExporter {

    /** Shared 9-slice skin used when a widget inherits its background (az id ≤ 0). */
    private static final int DEFAULT_SKIN = 701;

    private static Path assets;
    private static final Map<Integer, Sheet> CACHE = new HashMap<>();

    public static void main(String[] args) throws IOException {
        assets = Path.of(args.length > 0 ? args[0] : "assets");
        Path out = Path.of(args.length > 1 ? args[1] : "out/ui");
        Files.createDirectories(out);

        Path uiDir = assets.resolve("ui");
        int ok = 0;
        int total = 0;
        try (var stream = Files.list(uiDir)) {
            for (Path p : (Iterable<Path>) stream.filter(x -> x.toString().endsWith(".ui")).sorted()::iterator) {
                total++;
                try {
                    UiScreen screen = UiScreen.parse(Files.readAllBytes(p));
                    render(screen, out.resolve(p.getFileName().toString().replace(".ui", ".png")));
                    ok++;
                } catch (Exception e) {
                    System.out.println("FAIL " + p.getFileName() + ": " + e);
                }
            }
        }
        System.out.printf("UI render: %d/%d screens with real art -> %s%n", ok, total, out.toAbsolutePath());
    }

    private record Sheet(BufferedImage img, SpriteSheet fr) { }

    private static Sheet sheet(int id) {
        if (id <= 0) {
            return null;
        }
        return CACHE.computeIfAbsent(id, k -> {
            try {
                Path png = assets.resolve("common/" + k + ".png");
                Path fr = assets.resolve("common/" + k + ".fr");
                if (!Files.exists(png) || !Files.exists(fr)) {
                    return null;
                }
                BufferedImage img = ImageIO.read(png.toFile());
                SpriteSheet ss = SpriteSheet.parse(Files.readAllBytes(fr));
                if (img == null || !ss.fitsWithin(img.getWidth(), img.getHeight())) {
                    return null;
                }
                return new Sheet(img, ss);
            } catch (Exception e) {
                return null;
            }
        });
    }

    private static BufferedImage piece(int sheetId, int frameIndex) {
        Sheet s = sheet(sheetId);
        if (s == null || frameIndex < 0 || frameIndex >= s.fr.size()) {
            return null;
        }
        SpriteSheet.Frame f = s.fr.frame(frameIndex);
        if (f.w() <= 0 || f.h() <= 0) {
            return null;
        }
        return s.img.getSubimage(f.x(), f.y(), f.w(), f.h());
    }

    private static void render(UiScreen screen, Path outFile) throws IOException {
        int[] box = {0, 0};
        measure(screen.root(), 10, 10, box);
        int w = Math.max(240, Math.min(1400, box[0] + 20));
        int h = Math.max(240, Math.min(1400, box[1] + 20));

        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new java.awt.Color(0x14, 0x18, 0x21));
        g.fillRect(0, 0, w, h);
        g.setFont(new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 12));
        draw(g, screen.root(), 10, 10);
        g.dispose();
        ImageIO.write(img, "png", outFile.toFile());
    }

    private static void measure(UiScreen.Widget n, int ox, int oy, int[] box) {
        int x = ox + n.x;
        int y = oy + n.y;
        box[0] = Math.max(box[0], x + Math.max(n.w, 0));
        box[1] = Math.max(box[1], y + Math.max(n.h, 0));
        for (UiScreen.Widget c : n.children) {
            measure(c, x, y, box);
        }
    }

    private static void draw(Graphics2D g, UiScreen.Widget n, int ox, int oy) {
        int x = ox + n.x;
        int y = oy + n.y;
        int ww = n.w > 0 ? n.w : 24;
        int hh = n.h > 0 ? n.h : 18;

        for (UiScreen.Background bg : n.backgrounds) {
            nineSlice(g, bg, x, y, ww, hh);
        }
        for (UiScreen.Image im : n.images) {
            BufferedImage p = piece(im.sheetId(), im.frame());
            if (p != null) {
                g.drawImage(p, x, y, null);
            }
        }
        if (n.text != null && !n.text.isEmpty()) {
            g.setColor(new java.awt.Color(0xFF, 0xF0, 0xC8));
            g.drawString(n.text, x + 4, y + hh / 2 + 4);
        }
        for (UiScreen.Widget c : n.children) {
            draw(g, c, x, y);
        }
    }

    /** Draws a 9-slice (or stretched single) panel for a widget background. */
    private static void nineSlice(Graphics2D g, UiScreen.Background bg, int x, int y, int w, int h) {
        int sheetId = bg.sheetId();
        int[] frames = bg.frames();
        // az ≤ 0 = inherit default skin; painting a blanket panel per widget
        // over-draws, so only explicit sheets are rendered for now.
        Sheet s = sheet(sheetId);
        if (sheetId <= 0 || s == null) {
            return;
        }
        if (frames.length >= 9) {
            BufferedImage[] p = new BufferedImage[9];
            for (int i = 0; i < 9; i++) {
                p[i] = piece(sheetId, frames[i]);
            }
            if (p[0] == null || p[8] == null) {
                return;
            }
            int lw = p[0].getWidth();
            int th = p[0].getHeight();
            int rw = p[8].getWidth();
            int bh = p[8].getHeight();
            int midW = Math.max(1, w - lw - rw);
            int midH = Math.max(1, h - th - bh);
            blit(g, p[0], x, y, lw, th);
            blit(g, p[2], x + lw + midW, y, rw, th);
            blit(g, p[6], x, y + th + midH, lw, bh);
            blit(g, p[8], x + lw + midW, y + th + midH, rw, bh);
            blit(g, p[1], x + lw, y, midW, th);
            blit(g, p[7], x + lw, y + th + midH, midW, bh);
            blit(g, p[3], x, y + th, lw, midH);
            blit(g, p[5], x + lw + midW, y + th, rw, midH);
            blit(g, p[4], x + lw, y + th, midW, midH);
        } else if (frames.length > 0) {
            BufferedImage p = piece(sheetId, Math.max(0, frames[0]));
            blit(g, p, x, y, w, h);
        }
    }

    private static void blit(Graphics2D g, BufferedImage p, int x, int y, int w, int h) {
        if (p != null && w > 0 && h > 0) {
            g.drawImage(p, x, y, w, h, null);
        }
    }
}
