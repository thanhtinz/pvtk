package vn.pvtk.client.gdx;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import vn.pvtk.protocol.sprite.SpriteSheet;

/**
 * The game's entry screen, laid out to match the original login: a scenic
 * splash background, the Phong Vân dragon logo up top, a right-hand column of
 * round menu buttons (Quản lý / Cài đặt / Hỗ trợ / Update / Back), and a centre
 * form with account + password fields, an error line, a server picker and the
 * <b>Đăng nhập</b> button, plus a <b>Đăng ký tài khoản</b> link for a real
 * registration. Buttons and field frames are real {@code common/} sprite
 * frames; the splash and logo are game art.
 */
public final class LoginUi {

    /** Actions the host reacts to after input. */
    public enum Action { NONE, LOGIN, REGISTER }

    /** Which panel is showing: the entry menu, or the login / register form. */
    public enum Stage { MENU, LOGIN, REGISTER }

    private Stage stage = Stage.MENU;
    private final StringBuilder user = new StringBuilder();
    private final StringBuilder pass = new StringBuilder();
    private final StringBuilder confirm = new StringBuilder();
    private int focus = 0; // 0 = account, 1 = password, 2 = confirm
    private int server = 0; // selected server index
    private String status = "";
    private static final int SERVER_COUNT = 1;

    private Texture bg;
    private Texture logo;
    private Sheet buttons; // common/705: 4 = Đăng nhập
    private Sheet fields;  // common/704: 33 = input field
    private Sheet icons;   // common/206: menu glyphs

    private final float vw;
    private final float vh;
    private final java.util.Map<String, float[]> rects = new java.util.HashMap<>();

    private static final String[] MENU = {"Quản lý", "Cài đặt", "Hỗ trợ", "Update", "Back"};
    // common/206 frames: person, gear, handshake, refresh, back-arrow.
    private static final int[] MENU_ICON = {2, 4, 27, 34, 14};

    public LoginUi(float viewportW, float viewportH) {
        this.vw = viewportW;
        this.vh = viewportH;
        bg = tryTexture("login-bg.jpg");
        logo = tryTexture("logo.png");
        buttons = Sheet.load(705);
        fields = Sheet.load(704);
        icons = Sheet.load(206);
    }

    public String username() {
        return user.toString();
    }

    public String password() {
        return pass.toString();
    }

    public void setStatus(String s) {
        status = s == null ? "" : s;
    }

    // ------------------------------------------------------------------ draw

    public void draw(SpriteBatch batch, BitmapFont font) {
        rects.clear();

        // Scenic splash background (cover the viewport).
        if (bg != null) {
            float scale = Math.max(vw / bg.getWidth(), vh / bg.getHeight());
            float bw = bg.getWidth() * scale;
            float bh = bg.getHeight() * scale;
            batch.draw(new TextureRegion(bg), (vw - bw) / 2f, (vh - bh) / 2f, bw, bh);
        }

        // Dragon logo, centred at the top.
        if (logo != null) {
            float lw = Math.min(vw * 0.56f, 300);
            float lh = logo.getHeight() * (lw / logo.getWidth());
            batch.draw(new TextureRegion(logo), (vw - lw) / 2f, top(24, lh), lw, lh);
        }

        switch (stage) {
            case MENU -> drawMenu(batch);
            case LOGIN -> { drawIcons(batch, font); drawForm(batch, font, false); }
            case REGISTER -> { drawIcons(batch, font); drawForm(batch, font, true); }
        }
    }

    /** Entry menu: two centred buttons, Đăng nhập over Đăng ký. */
    private void drawMenu(SpriteBatch batch) {
        float bwd = Math.min(vw * 0.56f, 240);
        float bht = bwd * 0.30f;
        float bx = (vw - bwd) / 2f;
        float y = vh * 0.54f;
        drawButton(batch, "menu_login", 4, bx, y, bwd, bht);
        drawButton(batch, "menu_register", 3, bx, y + bht + 20, bwd, bht);
    }

    /** Right-hand column of real game icons (Back returns to the menu). */
    private void drawIcons(SpriteBatch batch, BitmapFont font) {
        float sz = Math.max(40, vw * 0.10f);
        for (int i = 0; i < MENU.length; i++) {
            float y = vh * 0.30f + i * (sz + 30);
            float x = vw - sz - 16;
            TextureRegion ic = icons != null ? icons.region(MENU_ICON[i]) : null;
            if (ic != null) {
                batch.draw(ic, x, top(y, sz), sz, sz);
            }
            centeredText(batch, font, MENU[i], Color.valueOf("f0ebd2"), x + sz / 2f, y + sz + 12);
            rects.put("menu" + i, new float[]{x, y, sz, sz});
        }
    }

    /** Login (or register) form: labelled input boxes + the matching action button. */
    private void drawForm(SpriteBatch batch, BitmapFont font, boolean register) {
        float fw = Math.min(vw * 0.76f, 330);
        float fx = (vw - fw) / 2f;
        float y0 = vh * 0.46f;
        float rowH = 84;
        field(batch, font, "f_user", 0, "Tài khoản", user.toString(), false, fx, y0, fw);
        field(batch, font, "f_pass", 1, "Mật khẩu", pass.toString(), true, fx, y0 + rowH, fw);
        float rows;
        if (register) {
            field(batch, font, "f_confirm", 2, "Nhập lại mật khẩu", confirm.toString(), true, fx, y0 + 2 * rowH, fw);
            rows = 3;
        } else {
            boxRow(batch, font, "server", "Máy chủ", "Server " + (server + 1) + "  ▸", fx, y0 + 2 * rowH, fw);
            rows = 3;
        }

        float statusY = y0 + rows * rowH - 4;
        if (!status.isEmpty()) {
            font.setColor(Color.valueOf("ff6a6a"));
            font.draw(batch, status, fx + 4, top(statusY, 0));
        }

        float bwd = Math.min(fw, 220);
        float bht = bwd * 0.30f;
        float bx = (vw - bwd) / 2f;
        float by = statusY + 20;
        drawButton(batch, register ? "register" : "login", register ? 3 : 4, bx, by, bwd, bht);
    }

    private void drawButton(SpriteBatch batch, String key, int frame, float x, float y, float w, float h) {
        TextureRegion r = buttons != null ? buttons.region(frame) : null;
        if (r != null) {
            batch.draw(r, x, top(y, h), w, h);
        }
        rects.put(key, new float[]{x, y, w, h});
    }

    /** Draws a labelled input box (label above, text padded inside the frame). */
    private void field(SpriteBatch batch, BitmapFont font, String key, int idx, String label,
                       String value, boolean secret, float x, float y, float w) {
        String shown = secret ? "•".repeat(value.length()) : value;
        if (focus == idx) {
            shown += "|";
        }
        boxRow(batch, font, key, label, shown.isEmpty() ? "" : shown, x, y, w);
    }

    /** A labelled box: label sits clear above the frame; content is padded inside. */
    private void boxRow(SpriteBatch batch, BitmapFont font, String key, String label,
                        String content, float x, float y, float w) {
        float h = 44;
        font.setColor(Color.valueOf("f5e6a8"));
        font.draw(batch, label, x + 6, top(y - 12, 0));
        TextureRegion r = fields != null ? fields.region(33) : null;
        if (r != null) {
            batch.draw(r, x, top(y, h), w, h);
        }
        font.setColor(Color.WHITE);
        font.draw(batch, content, x + 20, top(y + h / 2f - 8, 0));
        rects.put(key, new float[]{x, y, w, h});
    }

    private void centeredText(SpriteBatch batch, BitmapFont font, String s, Color color, float cx, float top) {
        GlyphLayout gl = new GlyphLayout(font, s);
        font.setColor(color);
        font.draw(batch, s, cx - gl.width / 2f, top(top, 0));
    }

    private float top(float uiTop, float boxH) {
        return vh - uiTop - boxH;
    }

    // ----------------------------------------------------------------- input

    public Action tap(float sx, float sy) {
        status = "";
        String hit = hitAt(sx, sy);
        if (hit == null) {
            return Action.NONE;
        }
        // The Back icon (right column, index 4) returns to the entry menu.
        if (hit.equals("menu4") && stage != Stage.MENU) {
            stage = Stage.MENU;
            focus = 0;
            return Action.NONE;
        }
        switch (hit) {
            case "menu_login" -> { stage = Stage.LOGIN; focus = 0; }
            case "menu_register" -> { stage = Stage.REGISTER; focus = 0; }
            case "f_user" -> focus = 0;
            case "f_pass" -> focus = 1;
            case "f_confirm" -> focus = 2;
            case "server" -> server = (server + 1) % SERVER_COUNT;
            case "login" -> {
                return check(false) ? Action.LOGIN : Action.NONE;
            }
            case "register" -> {
                return check(true) ? Action.REGISTER : Action.NONE;
            }
            default -> { }
        }
        return Action.NONE;
    }

    private boolean check(boolean register) {
        if (user.length() == 0 || pass.length() == 0) {
            status = "Nhập tài khoản & mật khẩu";
            return false;
        }
        if (register && !confirm.toString().equals(pass.toString())) {
            status = "Mật khẩu nhập lại không khớp";
            return false;
        }
        return true;
    }

    public void typed(char c) {
        StringBuilder t = focus == 2 ? confirm : focus == 1 ? pass : user;
        int fields = stage == Stage.REGISTER ? 3 : 2;
        if (c == '\b') {
            if (t.length() > 0) {
                t.deleteCharAt(t.length() - 1);
            }
        } else if (c == '\t') {
            focus = (focus + 1) % fields;
        } else if (c >= 32 && c != 127 && t.length() < 24) {
            t.append(c);
        }
    }

    private String hitAt(float x, float y) {
        String best = null;
        for (var e : rects.entrySet()) {
            float[] r = e.getValue();
            if (x >= r[0] && x <= r[0] + r[2] && y >= r[1] && y <= r[1] + r[3]) {
                best = e.getKey();
            }
        }
        return best;
    }

    public void dispose() {
        if (bg != null) {
            bg.dispose();
        }
        if (logo != null) {
            logo.dispose();
        }
        if (buttons != null) {
            buttons.dispose();
        }
        if (fields != null) {
            fields.dispose();
        }
    }

    private static Texture tryTexture(String rel) {
        try {
            FileHandle f = Assets.resolve(rel);
            return f.exists() ? new Texture(f) : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** A common/ sheet (png + 16-bit .fr) exposing flipped frame regions. */
    private static final class Sheet {
        final Texture tex;
        final SpriteSheet fr;

        Sheet(Texture tex, SpriteSheet fr) {
            this.tex = tex;
            this.fr = fr;
        }

        static Sheet load(int id) {
            try {
                FileHandle png = Assets.resolve("common/" + id + ".png");
                FileHandle fr = Assets.resolve("common/" + id + ".fr");
                if (!png.exists() || !fr.exists()) {
                    return null;
                }
                Texture tex = new Texture(png);
                SpriteSheet ss = SpriteSheet.parse(fr.readBytes());
                if (!ss.fitsWithin(tex.getWidth(), tex.getHeight())) {
                    tex.dispose();
                    return null;
                }
                return new Sheet(tex, ss);
            } catch (Exception e) {
                return null;
            }
        }

        TextureRegion region(int index) {
            if (index < 0 || index >= fr.size()) {
                return null;
            }
            SpriteSheet.Frame f = fr.frame(index);
            if (f.w() <= 0 || f.h() <= 0) {
                return null;
            }
            return new TextureRegion(tex, f.x(), f.y(), f.w(), f.h());
        }

        void dispose() {
            tex.dispose();
        }
    }
}
