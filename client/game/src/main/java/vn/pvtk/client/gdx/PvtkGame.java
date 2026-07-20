package vn.pvtk.client.gdx;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector3;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import vn.pvtk.client.GameClient;
import vn.pvtk.client.GameClientListener;
import vn.pvtk.client.model.Entity;
import java.util.List;
import vn.pvtk.protocol.message.Messages.ChatBroadcast;
import vn.pvtk.protocol.message.Messages.CombatEvent;
import vn.pvtk.protocol.message.Messages.RedeemEntry;
import vn.pvtk.protocol.message.Messages.RedeemList;

/**
 * The shared libGDX game (identical on PC, Android and iOS). It renders the world
 * snapshot from {@link vn.pvtk.client.model.GameState} over the real map artwork
 * loaded from {@code assets/map/}, draws players and monsters with HP bars, and
 * routes taps to movement or attacks. Networking lives entirely in the
 * platform-neutral {@link GameClient}; callbacks are marshalled to the render thread.
 */
public final class PvtkGame extends ApplicationAdapter {

    private final PvtkConfig config;

    private OrthographicCamera camera;
    private ShapeRenderer shapes;
    private SpriteBatch batch;
    private BitmapFont font;

    private GameClient client;
    private final Deque<String> chatLog = new ArrayDeque<>();
    private volatile String status = "connecting...";

    private Texture mapTexture;
    private int mapTextureId = -1;
    private SpriteAtlas atlas; // real decoded game sprites (null => fallback boxes)
    private float animTime;    // drives frame-cycling animation

    // Real jar-decoded hit/skill effect animation, played on combat events.
    private SprAnimator hitFx;
    private final List<float[]> effects = new java.util.ArrayList<>(); // {worldX, worldY, elapsed}

    // "Gói nạp" (in-game top-up) menu state.
    private volatile List<RedeemEntry> redeemPackages = List.of();
    private boolean redeemOpen;
    // Button / panel geometry (bottom-left origin), recomputed each frame.
    private static final float NAP_W = 120f, NAP_H = 34f;
    private static final float PANEL_W = 380f, ROW_H = 46f, HEAD_H = 42f, FOOT_H = 40f;

    public PvtkGame(PvtkConfig config) {
        this.config = config;
    }

    @Override
    public void create() {
        camera = new OrthographicCamera();
        camera.setToOrtho(false, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        shapes = new ShapeRenderer();
        batch = new SpriteBatch();
        font = GameFont.load(16);
        // Real game sprites decoded from the original common/1.{png,fr} sheet.
        atlas = SpriteAtlas.tryLoad("common/1");
        // A self-contained hit/skill effect .spr, decoded from the original jar data.
        for (int id : new int[]{4006, 4008, 4030, 4003, 4001, 4002}) {
            SprAnimator a = SprAnimator.load(id);
            if (a != null && a.isRenderable()) {
                hitFx = a;
                break;
            }
            if (a != null) {
                a.dispose();
            }
        }

        client = new GameClient(new RenderThreadListener());
        Gdx.input.setInputProcessor(new TapHandler());

        new Thread(() -> {
            try {
                client.connect(config.host, config.port);
                client.login(config.username, "", 0);
                client.requestBag();
            } catch (IOException e) {
                status = "connect failed: " + e.getMessage();
            }
        }, "pvtk-connect").start();
    }

    @Override
    public void render() {
        animTime += Gdx.graphics.getDeltaTime();
        camera.update();
        Gdx.gl.glClearColor(0.07f, 0.08f, 0.10f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        int ts = config.tileSize;
        Entity self = client.state().self();

        drawBackground(self);

        // --- entity bodies: real decoded sprites if available, else colour boxes ---
        if (atlas != null) {
            batch.setProjectionMatrix(camera.combined);
            batch.begin();
            for (Entity e : client.state().others()) {
                batch.draw(atlas.region(spriteIndex(e)), e.x * ts, e.y * ts, ts, ts);
            }
            if (self != null) {
                batch.draw(atlas.region(spriteIndex(self)), self.x * ts, self.y * ts, ts, ts);
            }
            batch.end();
        }

        // --- HP bars (and fallback body boxes) via ShapeRenderer ---
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (Entity e : client.state().others()) {
            Color color = switch (e.kind) {
                case vn.pvtk.protocol.message.Messages.KIND_MONSTER -> Color.FIREBRICK;
                case vn.pvtk.protocol.message.Messages.KIND_PET -> Color.LIME;
                case vn.pvtk.protocol.message.Messages.KIND_NPC -> Color.ORANGE;
                default -> Color.SKY;
            };
            drawEntity(e, color, ts, atlas == null);
        }
        if (self != null) {
            drawEntity(self, Color.GOLD, ts, atlas == null);
        }
        shapes.end();

        // --- real jar-decoded hit/skill effects ---
        drawEffects(ts);

        // --- HUD ---
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        for (Entity e : client.state().others()) {
            font.setColor(e.isMonster() ? Color.SALMON : Color.WHITE);
            font.draw(batch, e.name + " (" + e.hp + "/" + e.maxHp + ")", e.x * ts, e.y * ts + ts + 12);
        }
        if (self != null) {
            font.setColor(Color.GOLD);
            font.draw(batch, self.name + " (you) Lv" + self.level, self.x * ts, self.y * ts + ts + 12);
        }
        font.setColor(Color.LIGHT_GRAY);
        font.draw(batch, "PVTK  " + status
                        + "   gold=" + client.state().gold()
                        + "  tiền nạp=" + client.state().coin()
                        + "  xu=" + client.state().xu()
                        + "  items=" + client.state().inventory().bag().size(),
                8, Gdx.graphics.getHeight() - 8);
        font.draw(batch, "tap monster = battle   tap ground = move", 8, Gdx.graphics.getHeight() - 26);

        int y = 8 + chatLog.size() * 16;
        font.setColor(Color.CYAN);
        for (String msg : chatLog) {
            font.draw(batch, msg, 8, y);
            y -= 16;
        }

        // Turn-battle overlay.
        var battle = client.state().battle();
        if (battle != null) {
            int by = Gdx.graphics.getHeight() - 80;
            font.setColor(Color.YELLOW);
            font.draw(batch, "TRẬN ĐẤU - vòng " + battle.round()
                    + "  (chạm địch để đánh)", 8, by + 18);
            for (var u : battle.combatants()) {
                font.setColor(u.side() == 0 ? Color.GOLD : Color.SALMON);
                font.draw(batch, "[" + u.index() + "] " + u.name()
                        + " " + u.hp() + "/" + u.maxHp(), 8, by);
                by -= 16;
            }
        }
        batch.end();

        // "Gói nạp" button + package menu (drawn on top of everything else).
        drawNapButton();
        if (redeemOpen) {
            drawRedeemOverlay();
        }
    }

    /** Queues a hit/skill effect animation over the given entity (by id). */
    private void spawnHitEffect(int targetId) {
        if (hitFx == null) {
            return;
        }
        int ts = config.tileSize;
        Entity t = null;
        Entity self = client.state().self();
        if (self != null && self.id == targetId) {
            t = self;
        } else {
            for (Entity e : client.state().others()) {
                if (e.id == targetId) {
                    t = e;
                    break;
                }
            }
        }
        if (t == null) {
            return;
        }
        effects.add(new float[]{t.x * ts + ts / 2f, t.y * ts + ts / 2f, 0f});
    }

    /** Advances and draws active effect animations in world space, expiring finished ones. */
    private void drawEffects(int ts) {
        if (hitFx == null || effects.isEmpty()) {
            return;
        }
        float dt = Gdx.graphics.getDeltaTime();
        float scale = ts / 18f;
        int keys = Math.max(1, hitFx.keyCount(0));
        float life = keys / 10f + 0.05f; // one pass of animation 0
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        for (int i = effects.size() - 1; i >= 0; i--) {
            float[] fx = effects.get(i);
            fx[2] += dt;
            if (fx[2] >= life) {
                effects.remove(i);
                continue;
            }
            int frame = hitFx.animationFrame(0, fx[2]);
            hitFx.drawFrame(batch, frame, fx[0], fx[1] + ts * 0.4f, scale, false);
        }
        batch.end();
    }

    /** Bottom-right "NẠP GAME" button that opens the top-up package menu. */
    private void drawNapButton() {
        float w = Gdx.graphics.getWidth();
        float bx = w - NAP_W - 12f, by = 12f;
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.82f, 0.15f, 0.16f, 0.95f);
        shapes.rect(bx, by, NAP_W, NAP_H);
        shapes.end();
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        font.setColor(Color.WHITE);
        font.draw(batch, "NẠP GAME", bx + 18f, by + NAP_H - 10f);
        batch.end();
    }

    /** Modal list of redeem packages; tap a row to redeem, tap the footer to close. */
    private void drawRedeemOverlay() {
        float w = Gdx.graphics.getWidth(), h = Gdx.graphics.getHeight();
        int n = Math.max(1, redeemPackages.size());
        float panelH = HEAD_H + n * ROW_H + FOOT_H;
        float px = (w - PANEL_W) / 2f, py = (h - panelH) / 2f;

        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0f, 0f, 0f, 0.6f);            // dim backdrop
        shapes.rect(0, 0, w, h);
        shapes.setColor(0.11f, 0.12f, 0.16f, 0.98f);  // panel
        shapes.rect(px, py, PANEL_W, panelH);
        shapes.setColor(0.82f, 0.15f, 0.16f, 1f);     // header bar
        shapes.rect(px, py + panelH - HEAD_H, PANEL_W, HEAD_H);
        for (int i = 0; i < redeemPackages.size(); i++) {
            float ry = py + panelH - HEAD_H - (i + 1) * ROW_H;
            shapes.setColor(i % 2 == 0 ? 0.16f : 0.13f, 0.17f, 0.22f, 1f);
            shapes.rect(px + 8f, ry + 4f, PANEL_W - 16f, ROW_H - 8f);
        }
        shapes.setColor(0.30f, 0.32f, 0.38f, 1f);     // footer (close)
        shapes.rect(px + 8f, py + 6f, PANEL_W - 16f, FOOT_H - 12f);
        shapes.end();

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        font.setColor(Color.WHITE);
        font.draw(batch, "GÓI NẠP - đổi Xu lấy Tiền nạp", px + 14f, py + panelH - 14f);
        font.setColor(Color.GOLD);
        font.draw(batch, "Xu hiện có: " + client.state().xu(), px + PANEL_W - 150f, py + panelH - 14f);
        if (redeemPackages.isEmpty()) {
            font.setColor(Color.LIGHT_GRAY);
            font.draw(batch, "Chưa có gói nạp nào (admin cấu hình).",
                    px + 16f, py + panelH - HEAD_H - 24f);
        }
        for (int i = 0; i < redeemPackages.size(); i++) {
            RedeemEntry e = redeemPackages.get(i);
            float ry = py + panelH - HEAD_H - (i + 1) * ROW_H;
            font.setColor(Color.WHITE);
            font.draw(batch, e.name(), px + 16f, ry + ROW_H - 12f);
            font.setColor(Color.SKY);
            String sub = e.costXu() + " Xu  ->  +" + e.coin() + " Tiền nạp"
                    + (e.bonus() != null && !e.bonus().isEmpty() ? "  + " + e.bonus() : "");
            font.draw(batch, sub, px + 16f, ry + 18f);
        }
        font.setColor(Color.WHITE);
        font.draw(batch, "ĐÓNG", px + PANEL_W / 2f - 24f, py + FOOT_H - 12f);
        batch.end();
    }

    /** Handles a tap while UI (button/overlay) is showing. Returns true if consumed. */
    private boolean handleUiTap(int screenX, int screenY) {
        float w = Gdx.graphics.getWidth(), h = Gdx.graphics.getHeight();
        float gx = screenX, gy = h - screenY; // to bottom-left origin
        if (redeemOpen) {
            int n = Math.max(1, redeemPackages.size());
            float panelH = HEAD_H + n * ROW_H + FOOT_H;
            float px = (w - PANEL_W) / 2f, py = (h - panelH) / 2f;
            // Footer / close (also acts as "tap outside" when outside the panel).
            boolean inPanel = gx >= px && gx <= px + PANEL_W && gy >= py && gy <= py + panelH;
            float fy0 = py + 6f, fy1 = py + FOOT_H - 6f;
            if (!inPanel || (gy >= fy0 && gy <= fy1)) {
                redeemOpen = false;
                return true;
            }
            for (int i = 0; i < redeemPackages.size(); i++) {
                float ry = py + panelH - HEAD_H - (i + 1) * ROW_H;
                if (gy >= ry + 4f && gy <= ry + ROW_H - 4f
                        && gx >= px + 8f && gx <= px + PANEL_W - 8f) {
                    client.redeemPackage(redeemPackages.get(i).id());
                    redeemOpen = false;
                    return true;
                }
            }
            return true; // swallow taps inside the modal
        }
        // "NẠP GAME" button.
        float bx = w - NAP_W - 12f, by = 12f;
        if (gx >= bx && gx <= bx + NAP_W && gy >= by && gy <= by + NAP_H) {
            client.requestRedeemPackages();
            return true;
        }
        return false;
    }

    private void drawBackground(Entity self) {
        int mapId = self != null ? self.mapId : 1;
        if (mapTextureId != mapId) {
            loadMapTexture(mapId);
        }
        if (mapTexture != null) {
            batch.setProjectionMatrix(camera.combined);
            batch.begin();
            batch.setColor(1f, 1f, 1f, 0.55f); // dim so entities stand out
            batch.draw(mapTexture, 0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
            batch.setColor(Color.WHITE);
            batch.end();
        } else {
            drawGrid();
        }
    }

    private void loadMapTexture(int mapId) {
        if (mapTexture != null) {
            mapTexture.dispose();
            mapTexture = null;
        }
        mapTextureId = mapId;
        var file = Assets.resolve("map/" + mapId + ".png");
        if (file.exists()) {
            try {
                mapTexture = new Texture(file);
            } catch (Exception ignored) {
                mapTexture = null; // fall back to the grid
            }
        }
    }

    private void drawGrid() {
        int ts = config.tileSize;
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(0.15f, 0.16f, 0.20f, 1f);
        for (int x = 0; x <= Gdx.graphics.getWidth(); x += ts) {
            shapes.line(x, 0, x, Gdx.graphics.getHeight());
        }
        for (int y = 0; y <= Gdx.graphics.getHeight(); y += ts) {
            shapes.line(0, y, Gdx.graphics.getWidth(), y);
        }
        shapes.end();
    }

    private static final int ANIM_FRAMES = 4;
    private static final float ANIM_FPS = 4f;

    /**
     * Maps an entity to a frame in the sprite atlas, cycling through a short run of
     * consecutive decoded frames over time so entities visibly animate. (This plays
     * frames from the real decoded sheet; the original's exact .pd/.spr animation
     * sequences are not yet decoded.)
     */
    private int spriteIndex(Entity e) {
        int step = (int) (animTime * ANIM_FPS) % ANIM_FRAMES;
        int base = switch (e.kind) {
            case vn.pvtk.protocol.message.Messages.KIND_MONSTER -> 8 + (Math.abs(e.id) % 4) * ANIM_FRAMES;
            case vn.pvtk.protocol.message.Messages.KIND_PET -> 4;
            case vn.pvtk.protocol.message.Messages.KIND_NPC -> 16;
            default -> 0;
        };
        return base + step;
    }

    private void drawEntity(Entity e, Color color, int ts, boolean drawBody) {
        float bx = e.x * ts + 3;
        float by = e.y * ts + 3;
        if (drawBody) {
            shapes.setColor(color);
            shapes.rect(bx, by, ts - 6, ts - 6);
        }
        // HP bar above the entity.
        if (e.maxHp > 0) {
            float pct = Math.max(0f, Math.min(1f, e.hp / (float) e.maxHp));
            shapes.setColor(Color.DARK_GRAY);
            shapes.rect(bx, by + ts - 2, ts - 6, 3);
            shapes.setColor(Color.LIME);
            shapes.rect(bx, by + ts - 2, (ts - 6) * pct, 3);
        }
    }

    private void pushChat(String line) {
        chatLog.addFirst(line);
        while (chatLog.size() > 6) {
            chatLog.removeLast();
        }
    }

    @Override
    public void resize(int width, int height) {
        camera.setToOrtho(false, width, height);
    }

    @Override
    public void dispose() {
        if (client != null) {
            client.disconnect();
        }
        if (mapTexture != null) {
            mapTexture.dispose();
        }
        if (atlas != null) {
            atlas.dispose();
        }
        if (hitFx != null) {
            hitFx.dispose();
        }
        shapes.dispose();
        batch.dispose();
        font.dispose();
    }

    /** Marshals network callbacks onto libGDX's render thread. */
    private final class RenderThreadListener implements GameClientListener {
        @Override public void onConnected() {
            Gdx.app.postRunnable(() -> status = "connected");
        }
        @Override public void onLoginResult(boolean ok, String message) {
            Gdx.app.postRunnable(() -> status = ok ? "in game" : ("login failed: " + message));
        }
        @Override public void onChat(ChatBroadcast chat) {
            Gdx.app.postRunnable(() -> pushChat(chat.fromName() + ": " + chat.text()));
        }
        @Override public void onCombat(CombatEvent e) {
            Gdx.app.postRunnable(() -> {
                spawnHitEffect(e.targetId());
                if (e.killed()) {
                    pushChat("entity " + e.targetId() + " defeated!");
                }
            });
        }
        @Override public void onDisconnected(String reason) {
            Gdx.app.postRunnable(() -> status = "disconnected: " + reason);
        }
        @Override public void onRedeemList(RedeemList packages) {
            Gdx.app.postRunnable(() -> {
                redeemPackages = packages.packages();
                redeemOpen = true;
            });
        }
    }

    /**
     * In a turn battle, a tap submits a plan against the first living enemy.
     * Otherwise, tapping a monster starts a turn battle and tapping ground moves.
     */
    private final class TapHandler extends InputAdapter {
        @Override public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            if (handleUiTap(screenX, screenY)) {
                return true;
            }
            var battle = client.state().battle();
            if (battle != null) {
                battle.combatants().stream().filter(u -> u.side() == 1 && u.hp() > 0)
                        .findFirst().ifPresent(enemy ->
                                client.battlePlan(battle.round(), enemy.index(), 0));
                return true;
            }
            Vector3 world = camera.unproject(new Vector3(screenX, screenY, 0));
            int tx = (int) (world.x / config.tileSize);
            int ty = (int) (world.y / config.tileSize);

            Entity target = null;
            for (Entity e : client.state().others()) {
                if (e.isMonster() && Math.abs(e.x - tx) <= 1 && Math.abs(e.y - ty) <= 1) {
                    target = e;
                    break;
                }
            }
            if (target != null) {
                client.enterBattle(target.id); // faithful turn-based combat
            } else {
                client.move(tx, ty, 0);
            }
            return true;
        }
    }
}
