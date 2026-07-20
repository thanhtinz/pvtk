package vn.pvtk.client.gdx;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import vn.pvtk.protocol.sprite.SpriteSheet;
import vn.pvtk.protocol.ui.UiScreen;

/**
 * Renders an original {@code ui/<id>.ui} screen (decoded by {@link UiScreen})
 * with its real artwork inside the libGDX client — the in-game counterpart of
 * the offline {@code UiLayoutExporter}. Widget backgrounds are drawn as 9-slice
 * panels and icons/buttons as single frames, pulled from the {@code common/}
 * sheets (16-bit {@code .fr} tables via {@link SpriteSheet}). Coordinates are
 * top-left origin (converted to libGDX's y-up on draw).
 *
 * <p>Also exposes hit-testing so tapped buttons (widgets carrying text) can be
 * matched back to their screen rectangle for input handling.
 */
public final class UiStage {

    /** Shared 9-slice skin for widgets that inherit their background (az id ≤ 0). */
    private static final int DEFAULT_SKIN = 701;

    private final Map<Integer, Sheet> sheets = new HashMap<>();
    private final float viewportH;

    /** A resolved tappable region on screen (top-left origin), with its label. */
    public record Hit(String text, float x, float y, float w, float h) { }

    private final List<Hit> hits = new ArrayList<>();

    public UiStage(float viewportH) {
        this.viewportH = viewportH;
    }

    private static final class Sheet {
        final Texture tex;
        final SpriteSheet fr;
        Sheet(Texture tex, SpriteSheet fr) {
            this.tex = tex;
            this.fr = fr;
        }
    }

    private Sheet sheet(int id) {
        if (id <= 0) {
            return null;
        }
        if (sheets.containsKey(id)) {
            return sheets.get(id);
        }
        Sheet s = null;
        FileHandle png = Assets.resolve("common/" + id + ".png");
        FileHandle fr = Assets.resolve("common/" + id + ".fr");
        if (png.exists() && fr.exists()) {
            try {
                Texture tex = new Texture(png);
                SpriteSheet ss = SpriteSheet.parse(fr.readBytes());
                if (ss.fitsWithin(tex.getWidth(), tex.getHeight())) {
                    s = new Sheet(tex, ss);
                } else {
                    tex.dispose();
                }
            } catch (Exception ignored) {
                // leave null
            }
        }
        sheets.put(id, s);
        return s;
    }

    private TextureRegion piece(int sheetId, int frameIndex) {
        Sheet s = sheet(sheetId);
        if (s == null || frameIndex < 0 || frameIndex >= s.fr.size()) {
            return null;
        }
        SpriteSheet.Frame f = s.fr.frame(frameIndex);
        if (f.w() <= 0 || f.h() <= 0) {
            return null;
        }
        TextureRegion r = new TextureRegion(s.tex, f.x(), f.y(), f.w(), f.h());
        r.flip(false, true); // sheets are y-down; flip so upright in y-up world
        return r;
    }

    /** Draws a decoded screen at the given top-left origin. Rebuilds the hit list. */
    public void draw(SpriteBatch batch, BitmapFont font, UiScreen screen, float ox, float oy) {
        hits.clear();
        drawWidget(batch, font, screen.root(), ox, oy);
    }

    private void drawWidget(SpriteBatch batch, BitmapFont font, UiScreen.Widget n, float ox, float oy) {
        float x = ox + n.x;
        float y = oy + n.y;
        float w = n.w > 0 ? n.w : 24;
        float h = n.h > 0 ? n.h : 18;

        for (UiScreen.Background bg : n.backgrounds) {
            nineSlice(batch, bg, x, y, w, h);
        }
        for (UiScreen.Image im : n.images) {
            TextureRegion r = piece(im.sheetId(), im.frame());
            if (r != null) {
                batch.draw(r, x, topY(y, r.getRegionHeight()), r.getRegionWidth(), r.getRegionHeight());
            }
        }
        if (n.text != null && !n.text.isEmpty()) {
            font.setColor(Color.valueOf("fff0c8"));
            GlyphLayout gl = new GlyphLayout(font, n.text);
            font.draw(batch, n.text, x + 4, topY(y + h / 2f + gl.height / 2f, 0));
            hits.add(new Hit(n.text, x, y, w, h));
        }
        for (UiScreen.Widget c : n.children) {
            drawWidget(batch, font, c, x, y);
        }
    }

    /** Converts a top-left y (of a box's top edge) plus box height to a y-up draw y. */
    private float topY(float uiTop, float boxH) {
        return viewportH - uiTop - boxH;
    }

    private void nineSlice(SpriteBatch batch, UiScreen.Background bg, float x, float y, float w, float h) {
        int sheetId = bg.sheetId();
        int[] frames = bg.frames();
        // az ≤ 0 means "inherit the default skin"; drawing a blanket panel for
        // every such widget over-draws badly, so skip until the real inherited
        // skin is resolved. Only explicit sheets are painted.
        if (sheetId <= 0 || sheet(sheetId) == null) {
            return;
        }
        if (frames.length >= 9) {
            TextureRegion[] p = new TextureRegion[9];
            for (int i = 0; i < 9; i++) {
                p[i] = piece(sheetId, frames[i]);
            }
            if (p[0] == null || p[8] == null) {
                return;
            }
            float lw = p[0].getRegionWidth();
            float th = p[0].getRegionHeight();
            float rw = p[8].getRegionWidth();
            float bh = p[8].getRegionHeight();
            float midW = Math.max(1, w - lw - rw);
            float midH = Math.max(1, h - th - bh);
            blit(batch, p[0], x, y, lw, th);
            blit(batch, p[2], x + lw + midW, y, rw, th);
            blit(batch, p[6], x, y + th + midH, lw, bh);
            blit(batch, p[8], x + lw + midW, y + th + midH, rw, bh);
            blit(batch, p[1], x + lw, y, midW, th);
            blit(batch, p[7], x + lw, y + th + midH, midW, bh);
            blit(batch, p[3], x, y + th, lw, midH);
            blit(batch, p[5], x + lw + midW, y + th, rw, midH);
            blit(batch, p[4], x + lw, y + th, midW, midH);
        } else if (frames.length > 0) {
            blit(batch, piece(sheetId, Math.max(0, frames[0])), x, y, w, h);
        }
    }

    /** Draws a region at a top-left (x,y) box of size (w,h). */
    private void blit(SpriteBatch batch, TextureRegion r, float x, float y, float w, float h) {
        if (r != null && w > 0 && h > 0) {
            batch.draw(r, x, topY(y, h), w, h);
        }
    }

    /** Returns the label of the tappable widget under a top-left (x,y), or null. */
    public String hitTest(float x, float y) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hh = hits.get(i);
            if (x >= hh.x && x <= hh.x + hh.w && y >= hh.y && y <= hh.y + hh.h) {
                return hh.text;
            }
        }
        return null;
    }

    public void dispose() {
        for (Sheet s : sheets.values()) {
            if (s != null) {
                s.tex.dispose();
            }
        }
        sheets.clear();
    }
}
