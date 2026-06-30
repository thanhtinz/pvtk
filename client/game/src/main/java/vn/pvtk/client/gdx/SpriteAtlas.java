package vn.pvtk.client.gdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import java.util.ArrayList;
import java.util.List;
import vn.pvtk.protocol.sprite.SpriteSheet;

/**
 * Loads an original game sprite sheet ({@code .png} + sibling {@code .fr} frame
 * table) into libGDX {@link TextureRegion}s using the shared {@link SpriteSheet}
 * decoder. This lets the client render real decoded game art for entities.
 *
 * <p>Returns {@code null} from {@link #tryLoad} if the assets are missing or the
 * frame table doesn't fit the sheet, so callers fall back to placeholder markers.
 */
public final class SpriteAtlas {

    private final Texture texture;
    private final List<TextureRegion> regions;

    private SpriteAtlas(Texture texture, List<TextureRegion> regions) {
        this.texture = texture;
        this.regions = regions;
    }

    /** Attempts to load {@code <base>.png} + {@code <base>.fr}; null on failure. */
    public static SpriteAtlas tryLoad(String base) {
        try {
            var png = Gdx.files.internal(base + ".png");
            var fr = Gdx.files.internal(base + ".fr");
            if (!png.exists() || !fr.exists()) {
                return null;
            }
            Texture tex = new Texture(png);
            SpriteSheet sheet = SpriteSheet.parse(fr.readBytes());
            if (!sheet.fitsWithin(tex.getWidth(), tex.getHeight())) {
                tex.dispose();
                return null;
            }
            List<TextureRegion> regions = new ArrayList<>(sheet.size());
            for (SpriteSheet.Frame f : sheet.frames()) {
                regions.add(new TextureRegion(tex, f.x(), f.y(), f.w(), f.h()));
            }
            return new SpriteAtlas(tex, regions);
        } catch (Exception e) {
            return null;
        }
    }

    public int size() {
        return regions.size();
    }

    /** A frame by index, wrapping so any id maps to a valid region. */
    public TextureRegion region(int index) {
        return regions.get(((index % regions.size()) + regions.size()) % regions.size());
    }

    public void dispose() {
        texture.dispose();
    }
}
