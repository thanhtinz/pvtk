package vn.pvtk.tools;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import vn.pvtk.protocol.sprite.SpriteSheet;

/**
 * Offline tool that decodes the original {@code .fr} + {@code .png} sprite sheets
 * into individual frame PNGs using our {@link SpriteSheet} parser. It is fully
 * verifiable headlessly: every exported frame is a standard PNG that ImageIO can
 * read back, and the tool reports how many sheets and frames it processed.
 *
 * <pre>
 *   ./gradlew :tools:run --args="assets out/sprites"
 *   ./gradlew :tools:run --args="assets out/sprites common/1 common/1002"
 * </pre>
 *
 * Output layout: {@code out/sprites/&lt;dir&gt;/&lt;name&gt;/&lt;frameId&gt;.png}.
 */
public final class SpriteExporter {

    public static void main(String[] args) throws IOException {
        Path assets = Path.of(args.length > 0 ? args[0] : "assets");
        Path out = Path.of(args.length > 1 ? args[1] : "out/sprites");

        List<Path> frFiles = new ArrayList<>();
        if (args.length > 2) {
            for (int i = 2; i < args.length; i++) {
                frFiles.add(assets.resolve(args[i] + ".fr"));
            }
        } else {
            // Default: every .fr under common/ (icons, effects, fonts, character bits).
            try (var stream = Files.walk(assets.resolve("common"))) {
                stream.filter(p -> p.toString().endsWith(".fr")).forEach(frFiles::add);
            }
        }

        int sheets = 0;
        int frames = 0;
        int skipped = 0;
        for (Path fr : frFiles) {
            Path png = Path.of(fr.toString().replaceAll("\\.fr$", ".png"));
            if (!Files.exists(png)) {
                skipped++;
                continue;
            }
            BufferedImage sheet = ImageIO.read(png.toFile());
            if (sheet == null) {
                skipped++;
                continue;
            }
            SpriteSheet ss = SpriteSheet.parse(Files.readAllBytes(fr));
            if (!ss.fitsWithin(sheet.getWidth(), sheet.getHeight())) {
                skipped++; // a variant layout we don't decode yet
                continue;
            }
            Path dir = out.resolve(assets.relativize(fr).getParent())
                    .resolve(stripExt(fr.getFileName().toString()));
            Files.createDirectories(dir);
            for (SpriteSheet.Frame f : ss.frames()) {
                BufferedImage frame = sheet.getSubimage(f.x(), f.y(), f.w(), f.h());
                ImageIO.write(frame, "png", dir.resolve(f.id() + ".png").toFile());
                frames++;
            }
            sheets++;
        }

        System.out.printf("Sprite export: %d sheets, %d frames -> %s (%d skipped)%n",
                sheets, frames, out.toAbsolutePath(), skipped);
    }

    private static String stripExt(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }
}
