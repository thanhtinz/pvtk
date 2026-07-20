package vn.pvtk.tools;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.RasterFormatException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import vn.pvtk.protocol.sprite.SprSprite;

/**
 * Offline verification tool for the original {@code sprite_<id>.spr} animation
 * modules. It decodes each module with {@link SprSprite}, composes every frame
 * from its fragment image pieces (honouring per-layer offset and horizontal
 * flip, exactly like the game), and writes a contact sheet PNG per sprite plus
 * a report. Fully headless-verifiable: every output is a standard PNG.
 *
 * <pre>
 *   ./gradlew :tools:run -PmainClass=vn.pvtk.tools.SprAnimationExporter \
 *       --args="assets out/anim"
 * </pre>
 *
 * Sprites whose fragments are runtime paperdoll placeholders (equipment/body
 * pieces not shipped as standalone files — mostly player bodies) render only
 * their resolvable layers; self-contained monster / NPC / effect / skill
 * sprites render in full.
 */
public final class SprAnimationExporter {

    private static final String[] SEARCH = {"ani", "common", "ani/ani2"};

    public static void main(String[] args) throws IOException {
        Path assets = Path.of(args.length > 0 ? args[0] : "assets");
        Path out = Path.of(args.length > 1 ? args[1] : "out/anim");
        Files.createDirectories(out);

        int exact = 0;
        int total = 0;
        int selfContained = 0;
        for (String dir : new String[]{"ani", "ani/ani2"}) {
            Path root = assets.resolve(dir);
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (var stream = Files.walk(root)) {
                for (Path spr : (Iterable<Path>) stream
                        .filter(p -> p.getFileName().toString().startsWith("sprite_"))
                        .filter(p -> p.toString().endsWith(".spr"))::iterator) {
                    total++;
                    byte[] bytes = Files.readAllBytes(spr);
                    SprSprite sprite;
                    try {
                        sprite = SprSprite.parse(bytes);
                    } catch (Exception e) {
                        System.out.println("PARSE FAIL " + spr + ": " + e);
                        continue;
                    }
                    exact++;
                    boolean complete = compose(assets, spr, sprite, out);
                    if (complete) {
                        selfContained++;
                    }
                }
            }
        }
        System.out.printf("SPR decode: %d/%d parsed OK, %d self-contained sheets -> %s%n",
                exact, total, selfContained, out.toAbsolutePath());
    }

    /** Composes each frame onto a canvas and writes a contact sheet. Returns true if every fragment resolved. */
    private static boolean compose(Path assets, Path spr, SprSprite sprite, Path out) throws IOException {
        // Resolve each fragment's sheet image + .fr piece table.
        BufferedImage[] sheets = new BufferedImage[sprite.fragments().size()];
        SprSprite.Fr[] frs = new SprSprite.Fr[sprite.fragments().size()];
        // Map fragment typeId -> index (frames reference fragments by typeId).
        Map<Integer, Integer> byType = new HashMap<>();
        boolean complete = true;
        for (int i = 0; i < sprite.fragments().size(); i++) {
            SprSprite.Fragment f = sprite.fragments().get(i);
            byType.putIfAbsent(f.typeId(), i);
            Path png = find(assets, f.nameId(), "png");
            Path fr = find(assets, f.nameId(), "fr");
            if (png == null || fr == null) {
                complete = false;
                continue;
            }
            sheets[i] = ImageIO.read(png.toFile());
            frs[i] = SprSprite.Fr.parse(Files.readAllBytes(fr));
        }

        List<SprSprite.Frame> frames = sprite.frames();
        if (frames.isEmpty()) {
            return complete;
        }
        int cell = 96;
        int cols = Math.min(8, frames.size());
        int rows = (frames.size() + cols - 1) / cols;
        BufferedImage sheet = new BufferedImage(cols * cell, rows * cell, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = sheet.createGraphics();
        for (int i = 0; i < frames.size(); i++) {
            int ox = (i % cols) * cell + cell / 2;
            int oy = (i / cols) * cell + cell / 2;
            drawFrame(g, frames.get(i), byType, sheets, frs, ox, oy);
        }
        g.dispose();

        String name = spr.getFileName().toString().replaceAll("\\.spr$", "");
        Path dir = out.resolve(assets.relativize(spr).getParent());
        Files.createDirectories(dir);
        ImageIO.write(sheet, "png", dir.resolve(name + ".png").toFile());
        return complete;
    }

    // (getSubimage throws java.awt.image.RasterFormatException on out-of-bounds pieces)

    private static void drawFrame(Graphics2D g, SprSprite.Frame frame, Map<Integer, Integer> byType,
                                  BufferedImage[] sheets, SprSprite.Fr[] frs, int ox, int oy) {
        for (SprSprite.Layer layer : frame.layers()) {
            Integer idx = byType.get(layer.fragTypeId());
            if (idx == null || sheets[idx] == null || frs[idx] == null) {
                continue;
            }
            SprSprite.Fr.Piece piece = frs[idx].byId(layer.subFrameId());
            if (piece == null || piece.w() <= 0 || piece.h() <= 0) {
                continue;
            }
            BufferedImage sub;
            try {
                sub = sheets[idx].getSubimage(piece.x(), piece.y(), piece.w(), piece.h());
            } catch (RasterFormatException e) {
                continue;
            }
            int dx = ox + layer.dx();
            int dy = oy + layer.dy();
            if (layer.flipX()) {
                AffineTransform tx = AffineTransform.getTranslateInstance(dx + piece.w(), dy);
                tx.scale(-1, 1);
                g.drawImage(sub, tx, null);
            } else {
                g.drawImage(sub, dx, dy, null);
            }
        }
    }

    private static Path find(Path assets, int nameId, String ext) {
        for (String dir : SEARCH) {
            Path p = assets.resolve(dir).resolve(nameId + "." + ext);
            if (Files.exists(p)) {
                return p;
            }
        }
        return null;
    }
}
