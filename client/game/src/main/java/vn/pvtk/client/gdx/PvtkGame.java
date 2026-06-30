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
import vn.pvtk.protocol.message.Messages.ChatBroadcast;
import vn.pvtk.protocol.message.Messages.CombatEvent;

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

    public PvtkGame(PvtkConfig config) {
        this.config = config;
    }

    @Override
    public void create() {
        camera = new OrthographicCamera();
        camera.setToOrtho(false, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        shapes = new ShapeRenderer();
        batch = new SpriteBatch();
        font = new BitmapFont();

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
        camera.update();
        Gdx.gl.glClearColor(0.07f, 0.08f, 0.10f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        int ts = config.tileSize;
        Entity self = client.state().self();

        drawBackground(self);

        // --- entities (HP bars via ShapeRenderer) ---
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (Entity e : client.state().others()) {
            drawEntity(e, e.isMonster() ? Color.FIREBRICK : Color.SKY, ts);
        }
        if (self != null) {
            drawEntity(self, Color.GOLD, ts);
        }
        shapes.end();

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
                        + "   gold=" + client.state().inventory().gold()
                        + "  items=" + client.state().inventory().bag().size(),
                8, Gdx.graphics.getHeight() - 8);
        font.draw(batch, "tap monster = attack   tap ground = move", 8, Gdx.graphics.getHeight() - 26);

        int y = 8 + chatLog.size() * 16;
        font.setColor(Color.CYAN);
        for (String msg : chatLog) {
            font.draw(batch, msg, 8, y);
            y -= 16;
        }
        batch.end();
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
        var file = Gdx.files.internal("map/" + mapId + ".png");
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

    private void drawEntity(Entity e, Color color, int ts) {
        float bx = e.x * ts + 3;
        float by = e.y * ts + 3;
        shapes.setColor(color);
        shapes.rect(bx, by, ts - 6, ts - 6);
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
                if (e.killed()) {
                    pushChat("entity " + e.targetId() + " defeated!");
                }
            });
        }
        @Override public void onDisconnected(String reason) {
            Gdx.app.postRunnable(() -> status = "disconnected: " + reason);
        }
    }

    /** Tap a monster to attack it; tap empty ground to move there. */
    private final class TapHandler extends InputAdapter {
        @Override public boolean touchDown(int screenX, int screenY, int pointer, int button) {
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
                client.attack(target.id);
            } else {
                client.move(tx, ty, 0);
            }
            return true;
        }
    }
}
