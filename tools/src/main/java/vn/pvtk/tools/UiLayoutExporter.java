package vn.pvtk.tools;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import vn.pvtk.protocol.ui.UiScreen;

/**
 * Offline verification tool for the original {@code ui/<id>.ui} screen layouts.
 * It decodes each file with {@link UiScreen} and paints the widget tree as a
 * wireframe — each widget a rectangle at its (nested) bounds, colour-coded by
 * type, annotated with its text and image references. This proves the layout
 * decoder end-to-end and gives a viewable picture of every original UI screen
 * without yet resolving the sprite artwork.
 *
 * <pre>
 *   ./gradlew :tools:exportUi --args="assets out/ui"
 * </pre>
 */
public final class UiLayoutExporter {

    public static void main(String[] args) throws IOException {
        Path assets = Path.of(args.length > 0 ? args[0] : "assets");
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
        System.out.printf("UI layout export: %d/%d screens -> %s%n", ok, total, out.toAbsolutePath());
    }

    private static final Color[] PALETTE = {
        new Color(0x4F8DFD), new Color(0xFFB13B), new Color(0x53D06A), new Color(0xE0556B),
        new Color(0xB37DFF), new Color(0x2DD4BF), new Color(0xF472B6), new Color(0xFACC15),
    };

    private static void render(UiScreen screen, Path outFile) throws IOException {
        int[] bounds = {0, 0, 0, 0};
        measure(screen.root(), 0, 0, bounds);
        int w = Math.max(200, Math.min(1600, bounds[2] + 20));
        int h = Math.max(200, Math.min(1600, bounds[3] + 20));

        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0x111827));
        g.fillRect(0, 0, w, h);
        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        draw(g, screen.root(), 10, 10, 0);
        g.dispose();
        ImageIO.write(img, "png", outFile.toFile());
    }

    /** Accumulates the max extent of the tree so we can size the canvas. */
    private static void measure(UiScreen.Widget node, int ox, int oy, int[] box) {
        int x = ox + node.x;
        int y = oy + node.y;
        box[2] = Math.max(box[2], x + Math.max(node.w, 0));
        box[3] = Math.max(box[3], y + Math.max(node.h, 0));
        for (UiScreen.Widget c : node.children) {
            measure(c, x, y, box);
        }
    }

    private static void draw(Graphics2D g, UiScreen.Widget node, int ox, int oy, int depth) {
        int x = ox + node.x;
        int y = oy + node.y;
        int ww = node.w > 0 ? node.w : 20;
        int hh = node.h > 0 ? node.h : 16;
        Color c = PALETTE[node.type % PALETTE.length];

        g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 30));
        g.fillRect(x, y, ww, hh);
        g.setStroke(new BasicStroke(1f));
        g.setColor(c);
        g.drawRect(x, y, ww, hh);

        String tag = node.typeName();
        if (!node.imageRefs.isEmpty()) {
            tag += " img" + node.imageRefs;
        }
        g.setColor(new Color(255, 255, 255, 150));
        g.drawString(tag, x + 2, y + 11);
        if (node.text != null && !node.text.isEmpty()) {
            g.setColor(Color.WHITE);
            g.drawString(node.text, x + 2, y + 24);
        }
        for (UiScreen.Widget child : node.children) {
            draw(g, child, x, y, depth + 1);
        }
    }
}
