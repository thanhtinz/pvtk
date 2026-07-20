package vn.pvtk.client.gdx;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import vn.pvtk.protocol.sprite.SprSprite;

/**
 * libGDX renderer for the original {@code sprite_<id>.spr} animation modules,
 * decoded by {@link SprSprite}. It resolves each fragment's sheet + {@code .fr}
 * piece table from the game assets, then composes any frame by drawing its
 * layers (piece sub-regions at per-layer offsets, with horizontal flip) exactly
 * as the original client does. Runs each declared animation on a frame timer.
 *
 * <p>Self-contained sprites (skill / hit effects, many monsters and NPCs) render
 * in full; player-body sprites whose fragments are runtime paperdoll
 * placeholders are not resolvable here (they need the appearance layer) and are
 * reported via {@link #isRenderable()} so callers can fall back.
 */
public final class SprAnimator {

    private final SprSprite sprite;
    private final Texture[] sheets;      // one per fragment (null if unresolved)
    private final SprSprite.Fr[] frs;    // piece table per fragment
    private final Map<Integer, Integer> byType = new HashMap<>();
    private final boolean renderable;

    private SprAnimator(SprSprite sprite, Texture[] sheets, SprSprite.Fr[] frs, boolean renderable) {
        this.sprite = sprite;
        this.sheets = sheets;
        this.frs = frs;
        this.renderable = renderable;
        for (int i = 0; i < sprite.fragments().size(); i++) {
            byType.putIfAbsent(sprite.fragments().get(i).typeId(), i);
        }
    }

    /**
     * Loads {@code sprite_<id>.spr} and its fragment sheets, or returns
     * {@code null} if the module is missing. Must be called on the GL thread
     * (it creates {@link Texture}s).
     */
    public static SprAnimator load(int spriteId) {
        FileHandle spr = firstExisting(
                "ani/sprite_" + spriteId + ".spr",
                "ani/ani2/sprite_" + spriteId + ".spr");
        if (spr == null) {
            return null;
        }
        SprSprite sprite;
        try {
            sprite = SprSprite.parse(spr.readBytes());
        } catch (Exception e) {
            return null;
        }
        int n = sprite.fragments().size();
        Texture[] sheets = new Texture[n];
        SprSprite.Fr[] frs = new SprSprite.Fr[n];
        boolean complete = n > 0;
        for (int i = 0; i < n; i++) {
            int nameId = sprite.fragments().get(i).nameId();
            FileHandle png = firstExisting(
                    "ani/" + nameId + ".png", "common/" + nameId + ".png", "ani/ani2/" + nameId + ".png");
            FileHandle fr = firstExisting(
                    "ani/" + nameId + ".fr", "common/" + nameId + ".fr", "ani/ani2/" + nameId + ".fr");
            if (png == null || fr == null) {
                complete = false;
                continue;
            }
            try {
                sheets[i] = new Texture(png);
                frs[i] = SprSprite.Fr.parse(fr.readBytes());
            } catch (Exception e) {
                complete = false;
            }
        }
        // Renderable if the first frame has at least one resolvable layer.
        boolean any = false;
        if (!sprite.frames().isEmpty()) {
            for (SprSprite.Layer l : sprite.frames().get(0).layers()) {
                Integer idx = byTypeStatic(sprite, l.fragTypeId());
                if (idx != null && sheets[idx] != null && frs[idx] != null
                        && frs[idx].byId(l.subFrameId()) != null) {
                    any = true;
                    break;
                }
            }
        }
        return new SprAnimator(sprite, sheets, frs, any);
    }

    private static Integer byTypeStatic(SprSprite s, int typeId) {
        for (int i = 0; i < s.fragments().size(); i++) {
            if (s.fragments().get(i).typeId() == typeId) {
                return i;
            }
        }
        return null;
    }

    public boolean isRenderable() {
        return renderable;
    }

    public int frameCount() {
        return sprite.frames().size();
    }

    /** Number of keyframes in animation {@code animIndex}, or 0. */
    public int keyCount(int animIndex) {
        if (animIndex < 0 || animIndex >= sprite.animations().size()) {
            return 0;
        }
        return sprite.animations().get(animIndex).keys().size();
    }

    /**
     * Draws frame {@code frameIndex} centred at ({@code cx},{@code cy}) in world
     * units (y-up). {@code scale} maps sprite pixels to world units.
     */
    public void drawFrame(SpriteBatch batch, int frameIndex, float cx, float cy, float scale, boolean flipX) {
        if (frameIndex < 0 || frameIndex >= sprite.frames().size()) {
            return;
        }
        for (SprSprite.Layer layer : sprite.frames().get(frameIndex).layers()) {
            Integer idx = byType.get(layer.fragTypeId());
            if (idx == null || sheets[idx] == null || frs[idx] == null) {
                continue;
            }
            SprSprite.Fr.Piece piece = frs[idx].byId(layer.subFrameId());
            if (piece == null || piece.w() <= 0 || piece.h() <= 0) {
                continue;
            }
            TextureRegion region = new TextureRegion(sheets[idx], piece.x(), piece.y(), piece.w(), piece.h());
            boolean flip = flipX ^ layer.flipX();
            // The sheet is y-down; flip vertically so it draws upright in y-up world space.
            int lx = flipX ? -(layer.dx() + piece.w()) : layer.dx();
            float drawX = cx + lx * scale;
            float drawY = cy - (layer.dy() + piece.h()) * scale;
            float w = piece.w() * scale;
            float h = piece.h() * scale;
            region.flip(flip, true);
            batch.draw(region, drawX, drawY, w, h);
        }
    }

    /** Frame index to show for animation {@code animIndex} at elapsed time {@code t} (seconds). */
    public int animationFrame(int animIndex, float t) {
        if (animIndex < 0 || animIndex >= sprite.animations().size()) {
            return 0;
        }
        SprSprite.Animation anim = sprite.animations().get(animIndex);
        if (anim.keys().isEmpty()) {
            return 0;
        }
        // Durations are in game ticks (~10/s in the original); approximate.
        float ticks = t * 10f;
        int total = 0;
        for (SprSprite.Key k : anim.keys()) {
            total += Math.max(1, k.duration());
        }
        int at = (int) (ticks % total);
        int acc = 0;
        for (SprSprite.Key k : anim.keys()) {
            acc += Math.max(1, k.duration());
            if (at < acc) {
                return k.frameIndex();
            }
        }
        return anim.keys().get(0).frameIndex();
    }

    public void dispose() {
        for (Texture t : sheets) {
            if (t != null) {
                t.dispose();
            }
        }
    }

    private static FileHandle firstExisting(String... rel) {
        for (String r : rel) {
            FileHandle fh = Assets.resolve(r);
            if (fh != null && fh.exists()) {
                return fh;
            }
        }
        return null;
    }

    // Reserved for future multi-animation playback state.
    @SuppressWarnings("unused")
    private static List<Integer> frameSequence(SprSprite.Animation anim) {
        List<Integer> seq = new ArrayList<>();
        for (SprSprite.Key k : anim.keys()) {
            seq.add(k.frameIndex());
        }
        return seq;
    }
}
