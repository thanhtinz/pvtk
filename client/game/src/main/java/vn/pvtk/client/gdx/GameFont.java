package vn.pvtk.client.gdx;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;

/**
 * The in-game text font. The default libGDX bitmap font only covers ASCII, so
 * Vietnamese diacritics and the original data's Chinese item/monster names show
 * up as boxes. This loads a TrueType font from {@code assets/font/} via FreeType
 * with incremental glyph rasterisation, so any Unicode character used at runtime
 * renders on demand.
 *
 * <p>Resolution order (first that exists wins): {@code font/cjk.ttf} →
 * {@code font/game.ttf}. Drop a CJK-capable TTF in as {@code cjk.ttf} to render
 * Chinese names; the bundled {@code game.ttf} already covers Latin + Vietnamese.
 * Falls back to the built-in {@link BitmapFont} if nothing loads.
 */
final class GameFont {

    private GameFont() { }

    /** Builds the HUD/world font, or the default bitmap font if no TTF is available. */
    static BitmapFont load(int size) {
        FileHandle ttf = firstExisting("font/cjk.ttf", "font/game.ttf");
        if (ttf == null) {
            return new BitmapFont();
        }
        try {
            FreeTypeFontGenerator gen = new FreeTypeFontGenerator(ttf);
            FreeTypeFontParameter p = new FreeTypeFontParameter();
            p.size = size;
            p.incremental = true; // rasterise glyphs (incl. CJK) lazily as they appear
            p.minFilter = Texture.TextureFilter.Linear;
            p.magFilter = Texture.TextureFilter.Linear;
            // The generator must stay alive for incremental fonts; libGDX disposes
            // it with the font when the font is disposed.
            return gen.generateFont(p);
        } catch (Exception e) {
            return new BitmapFont();
        }
    }

    private static FileHandle firstExisting(String... rels) {
        for (String rel : rels) {
            FileHandle h = Assets.resolve(rel);
            if (h.exists()) {
                return h;
            }
        }
        return null;
    }
}
